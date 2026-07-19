package leaderboard.search.gen2.qdrant

import io.circe.Json
import leaderboard.search.gen2.transport.Gen2HttpTransportError
import org.scalatest.wordspec.AnyWordSpec

final class QdrantCandidateServiceSpec extends AnyWordSpec {
  private val semanticText = leaderboard.search.gen2.contract.SemanticQueryText.from("alpha").getOrElse(fail("expected semantic text"))
  private val prepared = QdrantCandidateRequestCompiler.prepare(QdrantTestFixtures.policy, leaderboard.search.gen2.contract.CandidatePlan(semanticText, Vector.empty)).getOrElse(fail("expected prepared query"))
  private val embedding = QdrantEmbeddingResult.from(prepared.embeddingInput, Vector(0.1, 0.2, 0.3)).getOrElse(fail("expected embedding"))
  private val request = QdrantCandidateRequestCompiler.complete(prepared, embedding).getOrElse(fail("expected request"))

  "QdrantCandidateService" should {
    "authorize the alias target and query that exact physical collection" in {
      val generation = compiledGeneration
      val alias = QdrantResourceName.from("neutral_alias").getOrElse(fail("expected alias"))
      val config = QdrantCandidateServiceConfig.create(alias, "neutral_alias_").getOrElse(fail("expected config"))
      val client = new AuthorizedClient(generation, alias.value)
      val result = new QdrantCandidateService(client, config).execute(QdrantTestFixtures.policy, request).getOrElse(fail("expected candidate result"))
      assert(result.hits.map(_.id).nonEmpty)
    }

    "reject a candidate fingerprint mismatch before resolving the alias" in {
      val alias = QdrantResourceName.from("neutral_alias").getOrElse(fail("expected alias"))
      val config = QdrantCandidateServiceConfig.create(alias, "neutral_alias_").getOrElse(fail("expected config"))
      val wrong = new QdrantCompiledCandidateRequest(request.body, request.limit, "wrong")
      val result = new QdrantCandidateService(new NoCallClient, config).execute(QdrantTestFixtures.policy, wrong)
      assert(result.isLeft)
    }

    "reject a missing alias as a typed authorization failure" in {
      val alias = QdrantResourceName.from("neutral_alias").getOrElse(fail("expected alias"))
      val config = QdrantCandidateServiceConfig.create(alias, "neutral_alias_").getOrElse(fail("expected config"))
      new QdrantCandidateService(new MissingAliasClient, config).execute(QdrantTestFixtures.policy, request) match {
        case Left(QdrantCandidateServiceError.AliasMissing(value)) => assert(value == alias.value)
        case other => fail(s"expected missing alias, got $other")
      }
    }

    "reject a malformed alias target before reading or querying it" in {
      val alias = QdrantResourceName.from("neutral_alias").getOrElse(fail("expected alias"))
      val config = QdrantCandidateServiceConfig.create(alias, "neutral_alias_").getOrElse(fail("expected config"))
      new QdrantCandidateService(new MalformedTargetClient(alias.value), config).execute(QdrantTestFixtures.policy, request) match {
        case Left(QdrantCandidateServiceError.UnauthorizedCollection(target, reason)) =>
          assert(target == "neutral_alias_not-a-generation")
          assert(reason.contains("64 lowercase hexadecimal"))
        case other => fail(s"expected malformed target rejection, got $other")
      }
    }
  }

  private def compiledGeneration: QdrantCompiledGeneration = {
    val prepared = QdrantGenerationCompiler.prepare(QdrantTestFixtures.policy, QdrantTestFixtures.materialized).getOrElse(fail("expected prepared generation"))
    val embeddings = prepared.points.map(point => QdrantEmbeddingResult.from(point.embeddingInput, Vector(0.1, 0.2, 0.3)).getOrElse(fail("expected embedding")))
    QdrantGenerationCompiler.complete(prepared, embeddings, "neutral_alias_").getOrElse(fail("expected compiled generation"))
  }

  private def details(generation: QdrantCompiledGeneration): Json = {
    val vectors = generation.collectionJson.hcursor.downField("vectors").focus.getOrElse(Json.obj())
    val metadata = generation.collectionJson.hcursor.downField("metadata").focus.getOrElse(Json.obj())
    val payload = Json.obj(QdrantCollectionWire.expectedPayloadSchema(generation.payloadIndexRequests).toVector.map { case (field, schema) => field -> Json.obj("data_type" -> Json.fromString(schema)) }*)
    Json.obj("status" -> Json.fromString("ok"), "result" -> Json.obj(
      "config" -> Json.obj("params" -> Json.obj("vectors" -> vectors), "metadata" -> metadata),
      "payload_schema" -> payload,
      "points_count" -> Json.fromInt(generation.metadata.pointCount),
    ))
  }

  private class NoCallClient extends QdrantGen2Client {
    def getCollection(collection: QdrantResourceName) = Left(Gen2HttpTransportError.RequestFailed("GET", "unused", "must not be called"))
    def listAliases() = Left(Gen2HttpTransportError.RequestFailed("GET", "unused", "must not be called"))
    def createCollection(collection: QdrantResourceName, body: Json) = Left(Gen2HttpTransportError.RequestFailed("PUT", "unused", "must not be called"))
    def createPayloadIndex(collection: QdrantResourceName, body: Json) = Left(Gen2HttpTransportError.RequestFailed("PUT", "unused", "must not be called"))
    def upsertPoints(collection: QdrantResourceName, body: Json) = Left(Gen2HttpTransportError.RequestFailed("PUT", "unused", "must not be called"))
    def countPoints(collection: QdrantResourceName) = Left(Gen2HttpTransportError.RequestFailed("POST", "unused", "must not be called"))
    def updateAliases(body: Json) = Left(Gen2HttpTransportError.RequestFailed("POST", "unused", "must not be called"))
    def queryPoints(target: QdrantResourceName, body: Json) = Left(Gen2HttpTransportError.RequestFailed("POST", "unused", "must not be called"))
  }

  private final class MissingAliasClient extends NoCallClient {
    override def listAliases() = Right(Json.obj("status" -> Json.fromString("ok"), "result" -> Json.obj("aliases" -> Json.arr())))
  }

  private final class MalformedTargetClient(alias: String) extends NoCallClient {
    override def listAliases() = Right(Json.obj("status" -> Json.fromString("ok"), "result" -> Json.obj(
      "aliases" -> Json.arr(Json.obj(
        "alias_name" -> Json.fromString(alias),
        "collection_name" -> Json.fromString("neutral_alias_not-a-generation"),
      ))
    )))
  }

  private final class AuthorizedClient(generation: QdrantCompiledGeneration, alias: String) extends NoCallClient {
    override def listAliases() = Right(Json.obj("status" -> Json.fromString("ok"), "result" -> Json.obj("aliases" -> Json.arr(Json.obj("alias_name" -> Json.fromString(alias), "collection_name" -> Json.fromString(generation.physicalCollectionName))))))
    override def getCollection(collection: QdrantResourceName) = Right(details(generation))
    override def queryPoints(target: QdrantResourceName, body: Json) = {
      assert(target.value == generation.physicalCollectionName)
      val id = QdrantPointId.json(generation.points.headOption.getOrElse(fail("expected point")).id)
      Right(Json.obj("status" -> Json.fromString("ok"), "result" -> Json.obj("points" -> Json.arr(Json.obj("id" -> id, "score" -> Json.fromBigDecimal(BigDecimal("0.9")))))))
    }
  }
}
