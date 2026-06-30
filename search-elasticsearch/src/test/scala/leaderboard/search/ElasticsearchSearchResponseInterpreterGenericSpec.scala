package leaderboard.search

import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import leaderboard.search.dsl.{SearchDocumentSpec, SearchField, SearchFieldKind, SearchValue}
import leaderboard.search.elasticsearch.{ElasticsearchDocumentHit, ElasticsearchSearchResponseInterpreter}
import leaderboard.search.lexical.LexicalDocumentHit
import org.scalatest.wordspec.AnyWordSpec

final class ElasticsearchSearchResponseInterpreterGenericSpec extends AnyWordSpec {
  "ElasticsearchSearchResponseInterpreter.decodeDocumentHits" should {
    "decode non-BeautyQ document hits and preserve score and matched_queries" in {
      val response = searchResponseJson(
        esHitJson(
          source = GenericResponseDocument("doc-1", "Fresh haircut").asJson,
          score = Some(2.75d),
          matchedQueries = List("title"),
        )
      )

      val result = ElasticsearchSearchResponseInterpreter.decodeDocumentHits[GenericResponseDocument](response)

      assert(result == Right(List(ElasticsearchDocumentHit(2.75d, GenericResponseDocument("doc-1", "Fresh haircut"), List("title")))))
    }

    "fall back to _matched_queries" in {
      val response = searchResponseJson(
        esHitJson(
          source = GenericResponseDocument("doc-2", "Short style").asJson,
          score = Some(1.5d),
          legacyMatchedQueries = List("legacy_title"),
        )
      )

      val result = ElasticsearchSearchResponseInterpreter.decodeDocumentHits[GenericResponseDocument](response)

      assert(result == Right(List(ElasticsearchDocumentHit(1.5d, GenericResponseDocument("doc-2", "Short style"), List("legacy_title")))))
    }

    "default missing score to 0.0 and missing matched queries to Nil" in {
      val response = searchResponseJson(
        esHitJson(
          source = GenericResponseDocument("doc-3", "No diagnostics").asJson,
          score = None,
        )
      )

      val result = ElasticsearchSearchResponseInterpreter.decodeDocumentHits[GenericResponseDocument](response)

      assert(result == Right(List(ElasticsearchDocumentHit(0.0d, GenericResponseDocument("doc-3", "No diagnostics"), Nil))))
    }

    "fail on missing or invalid _source" in {
      val missingSourceResponse = searchResponseJson(
        Json.obj(
          "_score" -> Json.fromDoubleOrNull(1.0d),
          "matched_queries" -> Json.arr(Json.fromString("title")),
        )
      )
      val invalidSourceResponse = searchResponseJson(
        Json.obj(
          "_score" -> Json.fromDoubleOrNull(1.0d),
          "matched_queries" -> Json.arr(Json.fromString("title")),
          "_source" -> Json.fromString("not-a-document"),
        )
      )

      val missingSource = ElasticsearchSearchResponseInterpreter.decodeDocumentHits[GenericResponseDocument](missingSourceResponse)
      val invalidSource = ElasticsearchSearchResponseInterpreter.decodeDocumentHits[GenericResponseDocument](invalidSourceResponse)

      assert(missingSource.isLeft)
      assert(invalidSource.isLeft)
    }
  }

  "ElasticsearchSearchResponseInterpreter.lexicalHits" should {
    "return LexicalDocumentHit for a non-BeautyQ document" in {
      val response = searchResponseJson(
        esHitJson(
          source = GenericResponseDocument("doc-4", "Lexical").asJson,
          score = Some(4.25d),
          matchedQueries = List("title", "body"),
        )
      )

      val result = ElasticsearchSearchResponseInterpreter.lexicalHits(documentSpec, response, _.id)

      assert(result == Right(List(LexicalDocumentHit("doc-4", 4.25d, List("title", "body")))))
    }

    "validate non-empty documentSpec.id" in {
      val emptyIdSpec = documentSpec.copy(id = (_: GenericResponseDocument) => " ")
      val response = searchResponseJson(
        esHitJson(
          source = GenericResponseDocument("doc-5", "Empty id").asJson,
          score = Some(1.0d),
        )
      )

      val result = ElasticsearchSearchResponseInterpreter.lexicalHits(emptyIdSpec, response, _.id)

      result match {
        case Left(error) =>
          assert(error.message.contains("empty id"))
        case Right(value) =>
          fail(s"Expected empty id failure, got $value")
      }
    }
  }

  private val titleField: SearchField[GenericResponseDocument] =
    SearchField(
      path = "title",
      kind = SearchFieldKind.Text,
      extract = document => Some(SearchValue.Text(document.title)),
      searchable = true,
    )

  private val documentSpec: SearchDocumentSpec[GenericResponseDocument] =
    SearchDocumentSpec(
      indexName = "generic_response_documents",
      id = _.id,
      fields = List(titleField),
    )

  private def searchResponseJson(hitJson: Json): Json =
    Json.obj(
      "hits" -> Json.obj(
        "hits" -> Json.arr(hitJson)
      )
    )

  private def esHitJson(
    source: Json,
    score: Option[Double],
    matchedQueries: List[String] = Nil,
    legacyMatchedQueries: List[String] = Nil,
  ): Json = {
    val fields = List.newBuilder[(String, Json)]
    score.foreach(value => fields += "_score" -> Json.fromDoubleOrNull(value))
    if (matchedQueries.nonEmpty) {
      fields += "matched_queries" -> Json.arr(matchedQueries.map(Json.fromString): _*)
    }
    if (legacyMatchedQueries.nonEmpty) {
      fields += "_matched_queries" -> Json.arr(legacyMatchedQueries.map(Json.fromString): _*)
    }
    fields += "_source" -> source
    Json.obj(fields.result(): _*)
  }
}

final case class GenericResponseDocument(id: String, title: String)

object GenericResponseDocument {
  implicit val decoder: Decoder[GenericResponseDocument] = deriveDecoder
  implicit val encoder: Encoder.AsObject[GenericResponseDocument] = deriveEncoder
}
