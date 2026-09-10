package leaderboard.search.beautyq.gen2.eval

import io.circe.{Json, JsonObject}
import io.circe.parser.parse

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

/** Strict author-only projection for a frozen protected query inventory. */
final class BeautyQProtectedAuthorDraft private (
  val schemaVersion: String,
  val authorPassId: String,
  val cases: Vector[BeautyQProtectedAuthorDraft.Case],
) {
  def toJson: Json = BeautyQProtectedAuthorDraft.encode(this)
}

object BeautyQProtectedAuthorDraft {
  final case class Case(
    id: String,
    query: String,
    language: String,
    primarySlice: String,
    additionalSlices: Vector[String],
    userIntent: String,
    notes: Vector[String],
  )

  val CurrentSchemaVersion = "beautyq-protected-author-draft-v1"
  private val RootFields = Set("schemaVersion", "authorPassId", "cases")
  private val CaseFields = Set("id", "query", "language", "primarySlice", "additionalSlices", "userIntent", "notes")

  def load(path: Path): Either[String, BeautyQProtectedAuthorDraft] =
    if (!Files.isRegularFile(path)) Left("author_draft_missing")
    else {
      try decodeString(Files.readString(path, StandardCharsets.UTF_8))
      catch { case _: java.io.IOException => Left("author_draft_read_failed") }
    }

  def decodeString(raw: String): Either[String, BeautyQProtectedAuthorDraft] =
    parse(raw).left.map(_ => "author_draft_invalid_json").flatMap(decode)

  def decode(json: Json): Either[String, BeautyQProtectedAuthorDraft] = for {
    root <- json.asObject.toRight("author_draft_expected_object")
    _ <- exactFields(root, RootFields, "author_draft")
    schema <- string(root, "schemaVersion")
    _ <- Either.cond(schema == CurrentSchemaVersion, (), "author_draft_schema_mismatch")
    authorPassId <- string(root, "authorPassId")
    _ <- nonBlank(authorPassId, "author_draft_pass_invalid")
    casesJson <- root("cases").flatMap(_.asArray).toRight("author_draft_cases_invalid")
    cases <- casesJson.zipWithIndex.foldLeft[Either[String, Vector[Case]]](Right(Vector.empty)) {
      case (acc, (value, index)) => acc.flatMap(done => decodeCase(value, index).map(done :+ _))
    }
    _ <- Either.cond(cases.nonEmpty, (), "author_draft_cases_empty")
    _ <- Either.cond(cases.map(_.id).distinct.size == cases.size, (), "author_draft_duplicate_case_id")
    _ <- Either.cond(cases.map(_.query).distinct.size == cases.size, (), "author_draft_duplicate_query")
  } yield new BeautyQProtectedAuthorDraft(schema, authorPassId, cases)

  def correspondsTo(
    draft: BeautyQProtectedAuthorDraft,
    protectedCorpus: BeautyQProtectedEvaluationCorpus,
  ): Either[String, Unit] = {
    val expected = protectedCorpus.corpus.cases.map { current =>
      current.caseId.value -> (current.query, current.language, current.slices.map(_.value), current.userIntent, current.notes)
    }
    val actual = draft.cases.map { current =>
      current.id -> (current.query, current.language, current.primarySlice +: current.additionalSlices, current.userIntent, current.notes)
    }
    Either.cond(
      actual == expected,
      (),
      "author_draft_inventory_mismatch",
    )
  }

  private def decodeCase(value: Json, index: Int): Either[String, Case] = for {
    obj <- value.asObject.toRight(s"author_draft_case_${index}_invalid")
    _ <- exactFields(obj, CaseFields, s"author_draft_case_$index")
    id <- string(obj, "id")
    query <- string(obj, "query")
    language <- string(obj, "language")
    primarySlice <- string(obj, "primarySlice")
    _ <- nonBlank(id, s"author_draft_case_${index}_id_invalid")
    _ <- nonBlank(query, s"author_draft_case_${index}_query_invalid")
    _ <- nonBlank(primarySlice, s"author_draft_case_${index}_slice_invalid")
    additionalSlices <- strings(obj, "additionalSlices")
    _ <- Either.cond(!additionalSlices.contains(primarySlice), (), s"author_draft_case_${index}_duplicate_slice")
    userIntent <- string(obj, "userIntent")
    notes <- strings(obj, "notes")
  } yield Case(id, query, language, primarySlice, additionalSlices, userIntent, notes)

  private def exactFields(obj: JsonObject, expected: Set[String], context: String): Either[String, Unit] =
    Either.cond(obj.keys.toSet == expected, (), s"${context}_fields_invalid")

  private def string(obj: JsonObject, name: String): Either[String, String] =
    obj(name).flatMap(_.asString).toRight(s"author_draft_${name}_invalid")

  private def strings(obj: JsonObject, name: String): Either[String, Vector[String]] =
    obj(name).flatMap(_.asArray).toRight(s"author_draft_${name}_invalid").flatMap { values =>
      values.foldLeft[Either[String, Vector[String]]](Right(Vector.empty)) {
        case (acc, value) => acc.flatMap(done => value.asString.toRight(s"author_draft_${name}_invalid").map(done :+ _))
      }
    }

  private def nonBlank(value: String, error: String): Either[String, Unit] =
    Either.cond(value.nonEmpty && value.trim == value, (), error)

  private def encode(value: BeautyQProtectedAuthorDraft): Json =
    Json.obj(
      "schemaVersion" -> Json.fromString(value.schemaVersion),
      "authorPassId" -> Json.fromString(value.authorPassId),
      "cases" -> Json.fromValues(value.cases.map { current =>
        Json.obj(
          "id" -> Json.fromString(current.id),
          "query" -> Json.fromString(current.query),
          "language" -> Json.fromString(current.language),
          "primarySlice" -> Json.fromString(current.primarySlice),
          "additionalSlices" -> Json.fromValues(current.additionalSlices.map(Json.fromString)),
          "userIntent" -> Json.fromString(current.userIntent),
          "notes" -> Json.fromValues(current.notes.map(Json.fromString)),
        )
      }),
    )
}
