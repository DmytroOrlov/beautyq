package leaderboard.search.gen2.core.materialization

import leaderboard.search.gen2.contract.{SearchDocumentDeclaration, SearchField}

/** Domain-neutral projected-document fingerprint: encodes every field returned by
  * `declaration.allFields` (which already includes `declaration.identity` as its first element) through
  * that field's own [[SearchField.extract]]/codec, then hashes the canonical token block via
  * [[CanonicalFingerprint]]. Computes each document's token vector exactly once, then orders documents by
  * (canonical identity, complete token block) - a total order even when two documents share the same
  * identity - so caller order, duplicate identities, and repository iteration order never affect the
  * result. Reads no field inventory other than `declaration.allFields` and never inspects field
  * capabilities.
  */
object SearchProjectedDocumentsFingerprint {
  def compute[Document, Id](
    encodingVersion: String,
    projectionFormatVersion: ProjectionFormatVersion,
    declaration: SearchDocumentDeclaration[Document, Id],
    documents: Vector[Document],
  ): ProjectedDocumentsFingerprint = {
    val fields = declaration.allFields

    val documentBlocks: Vector[(String, String, Vector[String])] =
      documents.map {
        document =>
          val identityValue = canonicalValue(declaration.identity, document)
          val documentTokenVector = documentTokens(identityValue, fields, document)
          (identityValue, documentTokenVector.mkString("\n"), documentTokenVector)
      }

    val orderedBlocks = documentBlocks.sortBy { case (identityValue, tokenBlock, _) => (identityValue, tokenBlock) }

    val tokens =
      Vector(
        CanonicalFingerprint.token("encodingVersion", encodingVersion),
        CanonicalFingerprint.token("projectionFormatVersion", projectionFormatVersion.value),
      ) ++ orderedBlocks.flatMap { case (_, _, documentTokenVector) => documentTokenVector }

    ProjectedDocumentsFingerprint(CanonicalFingerprint.sha256HexTokens(tokens))
  }

  private def documentTokens[Document](
    identityValue: String,
    fields: Vector[SearchField[Document, ?]],
    document: Document,
  ): Vector[String] =
    CanonicalFingerprint.token("document.identity", identityValue) +: fields.flatMap(field => fieldTokens(field, document))

  private def fieldTokens[Document, Value](field: SearchField[Document, Value], document: Document): Vector[String] = {
    val header = Vector(
      CanonicalFingerprint.token("field.id", field.id.value),
      CanonicalFingerprint.token("field.typeId", field.codec.typeId.value),
    )

    val presence = field.extract(document) match {
      case Some(value) =>
        Vector(CanonicalFingerprint.token("field.present", "true"), CanonicalFingerprint.token("field.value", field.codec.encodeCanonical(value)))
      case None =>
        Vector(CanonicalFingerprint.token("field.present", "false"))
    }

    header ++ presence
  }

  private def canonicalValue[Document, Value](field: SearchField[Document, Value], document: Document): String =
    field.extract(document) match {
      case Some(value) => field.codec.encodeCanonical(value)
      case None        => ""
    }
}
