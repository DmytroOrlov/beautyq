package leaderboard.search.gen2.elasticsearch

import leaderboard.search.gen2.contract.*

import io.circe.{Json, JsonObject}

/** One compiled Elasticsearch document ready for indexing: `id` becomes `_id`, `source` becomes
  * `_source`. Carries no bulk/NDJSON transport framing - that remains a later transport concern. */
final case class ElasticsearchIndexedDocument(id: String, source: Json)

sealed trait ElasticsearchDocumentCompileError

object ElasticsearchDocumentCompileError {
  final case class EmptyDocumentId(documentIndex: Int, fieldId: FieldId) extends ElasticsearchDocumentCompileError
  final case class ValueEncoding(documentIndex: Int, fieldId: FieldId, kind: SearchFieldKind, error: SearchValueDecodeError) extends ElasticsearchDocumentCompileError
  final case class SourcePathConflict(documentIndex: Int, conflict: ElasticsearchPathConflict) extends ElasticsearchDocumentCompileError
}

/** Pure, declaration-driven `_id`/`_source` compilation: every declared field is extracted and canonically
  * encoded exactly once per document, through the shared [[extractCanonical]]/[[compileBackend]] stages
  * both `_id` and `_source` derive from. `_id` derives from the declaration's exact identity field; that
  * one compiled value is reused for the identity's `_source` entry, never re-extracted or re-encoded.
  *
  * This module does not report a "missing required value" error: [[FieldExtraction.Required]] always
  * extracts a value by construction, so that state is unreachable through any exposed API. It does report a
  * typed [[ElasticsearchDocumentCompileError.ValueEncoding]] error when a field's own (possibly custom)
  * [[SearchValueCodec]] produces canonical text that the standard codec for its declared [[SearchFieldKind]]
  * cannot decode - for example an `Int`-kind field whose codec's `encodeCanonical` returns `"one"`. For the
  * identity field specifically, an empty canonical value is checked before backend-kind decoding runs, so
  * an empty structured identity is always reported as [[ElasticsearchDocumentCompileError.EmptyDocumentId]],
  * never as `ValueEncoding`.
  */
object ElasticsearchDocumentCompiler {

  // Extraction and canonical encoding only - the field's own codec, run exactly once per field/document.
  // No backend-kind decoding happens here, so the identity path can inspect `canonical` (for emptiness)
  // before ever reaching that step.
  private final case class ExtractedCanonicalField(id: FieldId, path: FieldPath, kind: SearchFieldKind, canonical: String)

  private final case class CompiledField(id: FieldId, path: FieldPath, canonical: String, json: Json)

  def compile[Document, Id](
    policy: ElasticsearchIndexPolicy[Document, Id],
    documents: Vector[Document],
  ): Either[ElasticsearchDocumentCompileError, Vector[ElasticsearchIndexedDocument]] =
    documents.zipWithIndex.foldLeft[Either[ElasticsearchDocumentCompileError, Vector[ElasticsearchIndexedDocument]]](Right(Vector.empty)) {
      case (acc, (document, documentIndex)) =>
        acc.flatMap(compiled => compileOne(policy.declaration, document, documentIndex).map(compiled :+ _))
    }

  private def compileOne[Document, Id](
    declaration: SearchDocumentDeclaration[Document, Id],
    document: Document,
    documentIndex: Int,
  ): Either[ElasticsearchDocumentCompileError, ElasticsearchIndexedDocument] =
    for {
      identity       <- compileIdentity(declaration.identity, document, documentIndex)
      ordinaryFields <- compileOrdinaryFields(declaration.fields, document, documentIndex)
      sourceObj      <- ElasticsearchDottedPathTree
                          .build((identity.id, identity.path, identity.json) +: ordinaryFields.map(f => (f.id, f.path, f.json)), wrapChild, unwrapChild)
                          .left.map(conflict => ElasticsearchDocumentCompileError.SourcePathConflict(documentIndex, conflict))
    } yield ElasticsearchIndexedDocument(identity.canonical, Json.fromJsonObject(sourceObj))

  private def wrapChild(child: JsonObject): Json = Json.fromJsonObject(child)

  private def unwrapChild(value: Json): Option[JsonObject] = value.asObject

  // The identity field is always FieldExtraction.Required (SearchDocumentDeclaration's own
  // IdentityFieldMustBeRequired invariant), so extractCanonical below always returns Some for it; this
  // documents and fails loudly on that source invariant rather than adding an unreachable typed error for
  // the None branch. Emptiness is checked on the extracted canonical value directly, before
  // compileBackend's backend-kind decoding ever runs - so an empty identity is always EmptyDocumentId,
  // never ValueEncoding, regardless of the identity field's declared kind. The one extracted/compiled
  // value is reused for both `_id` and its `_source` entry - never re-extracted or re-encoded.
  private def compileIdentity[Document, Id](
    field: SearchField[Document, Id],
    document: Document,
    documentIndex: Int,
  ): Either[ElasticsearchDocumentCompileError, CompiledField] = {
    val extracted = extractCanonical(field, document).getOrElse(
      throw new IllegalStateException(s"declared identity field '${field.id.value}' produced no value despite being required")
    )
    if (extracted.canonical.isEmpty) Left(ElasticsearchDocumentCompileError.EmptyDocumentId(documentIndex, field.id))
    else compileBackend(documentIndex, extracted)
  }

  private def compileOrdinaryFields[Document](
    fields: Vector[SearchField[Document, ?]],
    document: Document,
    documentIndex: Int,
  ): Either[ElasticsearchDocumentCompileError, Vector[CompiledField]] =
    fields.foldLeft[Either[ElasticsearchDocumentCompileError, Vector[CompiledField]]](Right(Vector.empty)) { (acc, field) =>
      acc.flatMap { compiled =>
        extractCanonical(field, document) match {
          case None            => Right(compiled)
          case Some(extracted) => compileBackend(documentIndex, extracted).map(compiled :+ _)
        }
      }
    }

  // The one extraction/encoding path every _id and _source value passes through: field extraction and
  // canonical encoding via the field's own codec happen exactly once per field/document, whether the
  // field is the document's identity or an ordinary declared field.
  private def extractCanonical[Document, Value](
    field: SearchField[Document, Value],
    document: Document,
  ): Option[ExtractedCanonicalField] =
    field.extract(document).map(value => ExtractedCanonicalField(field.id, field.path, field.kind, field.codec.encodeCanonical(value)))

  // The one backend-kind conversion path every _id and _source value passes through after extraction:
  // delegates to the shared ElasticsearchScalarCompiler - the same mechanic query-value compilation
  // uses - and wraps its neutral typed decode error with this compiler's own document/field context.
  private def compileBackend(documentIndex: Int, extracted: ExtractedCanonicalField): Either[ElasticsearchDocumentCompileError, CompiledField] =
    ElasticsearchScalarCompiler.toBackendJson(extracted.kind, extracted.canonical) match {
      case Left(error) => Left(ElasticsearchDocumentCompileError.ValueEncoding(documentIndex, extracted.id, extracted.kind, error))
      case Right(json) => Right(CompiledField(extracted.id, extracted.path, extracted.canonical, json))
    }
}
