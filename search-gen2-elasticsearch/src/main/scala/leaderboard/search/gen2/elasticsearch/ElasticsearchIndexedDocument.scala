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
  // decodes the just-encoded canonical string with the standard SearchValueCodec for its declared
  // SearchFieldKind, never the field's own (possibly custom) codec - a field's codec is only guaranteed to
  // round-trip through itself (requireCanonicalRoundTrip), not to produce text compatible with its
  // declared backend kind. Keyword/Text values are stored as-is, since any string is a valid keyword/text
  // value; every other kind's canonical text must independently decode as that kind's standard backend
  // representation; a mismatch is reported as a typed ValueEncoding error rather than thrown.
  private def compileBackend(documentIndex: Int, extracted: ExtractedCanonicalField): Either[ElasticsearchDocumentCompileError, CompiledField] =
    backendJson(documentIndex, extracted.id, extracted.kind, extracted.canonical)
      .map(json => CompiledField(extracted.id, extracted.path, extracted.canonical, json))

  private def backendJson(
    documentIndex: Int,
    fieldId: FieldId,
    kind: SearchFieldKind,
    canonical: String,
  ): Either[ElasticsearchDocumentCompileError, Json] =
    kind match {
      case SearchFieldKind.Keyword | SearchFieldKind.Text =>
        Right(Json.fromString(canonical))
      case SearchFieldKind.Integer =>
        decodeOrTypedError(documentIndex, fieldId, kind, SearchValueCodec.int.decodeCanonical(canonical)).map(Json.fromInt)
      case SearchFieldKind.Long =>
        decodeOrTypedError(documentIndex, fieldId, kind, SearchValueCodec.long.decodeCanonical(canonical)).map(Json.fromLong)
      case SearchFieldKind.Decimal =>
        decodeOrTypedError(documentIndex, fieldId, kind, SearchValueCodec.bigDecimal.decodeCanonical(canonical)).map(Json.fromBigDecimal)
      case SearchFieldKind.Boolean =>
        decodeOrTypedError(documentIndex, fieldId, kind, SearchValueCodec.boolean.decodeCanonical(canonical)).map(Json.fromBoolean)
      case SearchFieldKind.DateTime =>
        // strict_date_optional_time requires a standard ISO-8601 instant; the canonical string itself -
        // not the decoded Instant - is what gets stored, since Instant's own canonical spelling (enforced
        // by requireCanonicalRoundTrip) is exactly that string.
        decodeOrTypedError(documentIndex, fieldId, kind, SearchValueCodec.instant.decodeCanonical(canonical)).map(_ => Json.fromString(canonical))
      case SearchFieldKind.GeoPoint =>
        decodeOrTypedError(documentIndex, fieldId, kind, SearchValueCodec.geoPoint.decodeCanonical(canonical))
          .map(point => Json.obj("lat" -> Json.fromBigDecimal(point.lat), "lon" -> Json.fromBigDecimal(point.lon)))
    }

  private def decodeOrTypedError[A](
    documentIndex: Int,
    fieldId: FieldId,
    kind: SearchFieldKind,
    decoded: Either[SearchValueDecodeError, A],
  ): Either[ElasticsearchDocumentCompileError, A] =
    decoded.left.map(error => ElasticsearchDocumentCompileError.ValueEncoding(documentIndex, fieldId, kind, error))
}
