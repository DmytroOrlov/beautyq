package leaderboard.search.gen2.core.materialization

import leaderboard.search.gen2.contract.*
import org.scalatest.wordspec.AnyWordSpec

/** Neutral fixture proof: no BeautyQ type appears anywhere in this file. `SampleDocument.internalNote`
  * deliberately has no corresponding declared field, so it can prove the fingerprint reads its field
  * inventory only from `declaration.allFields`.
  */
final class SearchProjectedDocumentsFingerprintSpec extends AnyWordSpec {

  private final case class SampleId(value: String)

  private object SampleId {
    given SearchValueCodec[SampleId] =
      SearchValueCodec.string.imap(SearchValueTypeId("sample-id"))(raw => Right(SampleId(raw)), _.value)
  }

  private final case class SampleDocument(id: SampleId, name: String, score: Option[Int], internalNote: String)

  private val idField: SearchField[SampleDocument, SampleId] =
    field[SampleDocument, SampleId]("id", _.id).keyword

  private val nameField: SearchField[SampleDocument, String] =
    field[SampleDocument, String]("name", _.name).text

  private val scoreField: SearchField[SampleDocument, Int] =
    computedField[SampleDocument, Int]("score", "score")(_.score).integer

  private val declaration: SearchDocumentDeclaration[SampleDocument, SampleId] =
    searchDocument[SampleDocument]("sample").id(idField).field(nameField).field(scoreField).build match {
      case Right(built) => built
      case Left(errors) => throw new IllegalStateException(s"neutral fixture declaration must be valid: ${errors.toVector}")
    }

  private val alternateIdCodec: SearchValueCodec[SampleId] =
    SearchValueCodec.string.imap(SearchValueTypeId("alternate-sample-id"))(raw => Right(SampleId(raw)), _.value)

  private def declarationFor(
    identity: SearchField[SampleDocument, SampleId],
    fields: SearchField[SampleDocument, ?]*,
  ): SearchDocumentDeclaration[SampleDocument, SampleId] = {
    val builder = fields.foldLeft(searchDocument[SampleDocument]("sample").id(identity)) {
      (current, field) => current.field(field)
    }
    builder.build match {
      case Right(built) => built
      case Left(errors) => throw new IllegalStateException(s"neutral fixture declaration must be valid: ${errors.toVector}")
    }
  }

  private val renamedNameField: SearchField[SampleDocument, String] =
    field[SampleDocument, String]("renamed-name", _.name).text

  private val changedFieldIdDeclaration = declarationFor(idField, renamedNameField, scoreField)

  private val changedCodecDeclaration: SearchDocumentDeclaration[SampleDocument, SampleId] = {
    given SearchValueCodec[SampleId] = alternateIdCodec
    val alternateIdField = field[SampleDocument, SampleId]("id", _.id).keyword
    declarationFor(alternateIdField, nameField, scoreField)
  }

  private def fingerprintOf(
    documents: Vector[SampleDocument],
    declaration: SearchDocumentDeclaration[SampleDocument, SampleId] = declaration,
    encodingVersion: String = "sample-v1",
    projectionFormatVersion: ProjectionFormatVersion = ProjectionFormatVersion("sample-projection-v1"),
  ): String =
    SearchProjectedDocumentsFingerprint
      .compute(
        encodingVersion = encodingVersion,
        projectionFormatVersion = projectionFormatVersion,
        declaration = declaration,
        documents = documents,
      )
      .value

  private val documentA = SampleDocument(SampleId("a"), "Alpha", Some(1), internalNote = "a-note")
  private val documentB = SampleDocument(SampleId("b"), "Beta", None, internalNote = "b-note")

  "SearchProjectedDocumentsFingerprint" should {
    "not let caller document order affect the fingerprint" in {
      assert(fingerprintOf(Vector(documentA, documentB)) == fingerprintOf(Vector(documentB, documentA)))
    }

    "produce a total order for two documents sharing the same identity" in {
      val first = documentA.copy(name = "First Duplicate", score = Some(1))
      val second = documentA.copy(name = "Second Duplicate", score = Some(2))

      assert(first.id == second.id)
      assert(fingerprintOf(Vector(first, second)) == fingerprintOf(Vector(second, first)))
      assert(fingerprintOf(Vector(first, second)) != fingerprintOf(Vector(first)))
      assert(fingerprintOf(Vector(first, second)) != fingerprintOf(Vector(second)))
    }

    "change when a declared field changes" in {
      val changed = documentA.copy(name = "Changed Name")
      assert(fingerprintOf(Vector(documentA)) != fingerprintOf(Vector(changed)))
    }

    "change when the encoding version changes" in {
      assert(fingerprintOf(Vector(documentA)) != fingerprintOf(Vector(documentA), encodingVersion = "sample-v2"))
    }

    "change when the projection format version changes" in {
      assert(
        fingerprintOf(Vector(documentA)) !=
          fingerprintOf(Vector(documentA), projectionFormatVersion = ProjectionFormatVersion("sample-projection-v2"))
      )
    }

    "change when a declared field id changes" in {
      assert(fingerprintOf(Vector(documentA)) != fingerprintOf(Vector(documentA), declaration = changedFieldIdDeclaration))
    }

    "change when a declared field codec type id changes" in {
      assert(fingerprintOf(Vector(documentA)) != fingerprintOf(Vector(documentA), declaration = changedCodecDeclaration))
    }

    "distinguish an absent optional value from a present one" in {
      val absent = documentA.copy(score = None)
      val present = documentA.copy(score = Some(1))
      assert(fingerprintOf(Vector(absent)) != fingerprintOf(Vector(present)))
    }

    "include the document identity in the fingerprint" in {
      val changed = documentA.copy(id = SampleId("changed"))
      assert(fingerprintOf(Vector(documentA)) != fingerprintOf(Vector(changed)))
    }

    "only let declaration.allFields drive traversal" in {
      val changedButUndeclared = documentA.copy(internalNote = "a completely different, undeclared note")
      assert(fingerprintOf(Vector(documentA)) == fingerprintOf(Vector(changedButUndeclared)))
    }

    "produce exactly 64 lowercase hexadecimal characters" in {
      val hex = fingerprintOf(Vector(documentA))
      assert(hex.length == 64)
      assert(hex.forall(c => c.isDigit || (c >= 'a' && c <= 'f')))
    }
  }
}
