package leaderboard.search

import io.circe.Json
import leaderboard.search.beautyq.gen2.wiring.*
import leaderboard.search.gen2.elasticsearch.*
import leaderboard.search.gen2.qdrant.*
import leaderboard.search.gen2.transport.*
import org.scalatest.wordspec.AnyWordSpec

import BeautyQSearchGen2ResourceSupport.*

/** Atomic proofs for the same executable gate used by the managed communication specs. */
final class BeautyQSearchGen2ResourceGateSpec extends AnyWordSpec {
  "the Gen2 resource gate" should {
    "accept an empty Qdrant alias inventory" in {
      assert(decodeQdrantAliases(Json.obj(
        "status" -> Json.fromString("ok"),
        "result" -> Json.obj("aliases" -> Json.arr()),
      )) == Right(Vector.empty))
    }

    "treat a reserved Qdrant alias as occupied regardless of target namespace" in {
      val entries = decodeQdrantAliases(Json.obj(
        "status" -> Json.fromString("ok"),
        "result" -> Json.obj("aliases" -> Json.arr(Json.obj(
          "alias_name" -> Json.fromString(BeautyQSearchGen2ResourceNames.QdrantCollectionAlias),
          "collection_name" -> Json.fromString("foreign-collection"),
        ))),
      )).getOrElse(fail("expected valid alias inventory"))
      assert(reservedQdrantAliasTargets(entries) == Vector("foreign-collection"))
    }

    "treat a reserved Qdrant alias as occupied for a Gen2 target too" in {
      val target = s"${BeautyQSearchGen2ResourceNames.QdrantPhysicalCollectionPrefix}${"a" * 64}"
      assert(reservedQdrantAliasTargets(Vector(QdrantAliasEntry(BeautyQSearchGen2ResourceNames.QdrantCollectionAlias, target))) == Vector(target))
    }

    "allow an unrelated Qdrant alias" in {
      assert(reservedQdrantAliasTargets(Vector(QdrantAliasEntry("other-alias", "foreign-collection"))).isEmpty)
    }

    "reject malformed Qdrant alias inventory instead of dropping entries" in {
      decodeQdrantAliases(Json.obj(
        "status" -> Json.fromString("ok"),
        "result" -> Json.obj("aliases" -> Json.arr(Json.obj("alias_name" -> Json.fromString("broken")))),
      )) match {
        case Left(message) => assert(message.contains("collection_name must be a string"))
        case Right(value) => fail(s"expected malformed alias inventory, got $value")
      }
    }

    "surface Elasticsearch alias read failures" in {
      val failure = ElasticsearchGen2TransportError.HttpFailure("GET", "/_alias/books", 500, "broken")
      assert(readElasticsearchAliasTargets(new ElasticsearchAliasProbeClient(Left(failure)), "books") == Left(failure.toString))
    }

    "surface Qdrant listAliases failures" in {
      val failure = Gen2HttpTransportError.ConnectionFailed("GET", "/aliases", "unavailable")
      assert(readQdrantAliases(new QdrantAliasProbeClient(Left(failure))) == Left(failure.toString))
    }

    "report reachable incompatible resources before unavailable resources" in {
      classifyPreflight(
        Left(ElasticsearchGen2TransportError.ConnectionFailed("GET", "/", "unavailable")),
        Right(Json.obj("version" -> Json.fromString("1.17.0"))),
        Left(BeautyQEmbeddingRequestError.Unavailable("embedding unavailable")),
      ) match {
        case PreflightBroken(resources) => assert(resources == Vector("Qdrant: expected version 1.18.3, got 1.17.0"))
        case other => fail(s"expected broken reachable Qdrant to win, got $other")
      }
    }

    "reject an incompatible Qdrant version when the other resources are compatible" in {
      val input = QdrantEmbeddingInput.from(
        QdrantEmbeddingPurpose.CandidateQuery,
        "preflight",
        "preflight",
        BeautyQQdrantPolicy.policy.embeddingModel,
      ).getOrElse(fail("expected valid embedding input"))
      val embedding = QdrantEmbeddingResult.from(input, Vector.fill(input.modelValue.dimension)(0.0)).getOrElse(fail("expected valid embedding"))
      classifyPreflight(
        Right(Json.obj("name" -> Json.fromString("elasticsearch"))),
        Right(Json.obj("version" -> Json.fromString("1.17.0"))),
        Right(embedding),
      ) match {
        case PreflightBroken(resources) => assert(resources == Vector("Qdrant: expected version 1.18.3, got 1.17.0"))
        case other => fail(s"expected incompatible Qdrant to be broken, got $other")
      }
    }

    "report malformed reachable Qdrant responses before unavailable resources" in {
      classifyPreflight(
        Left(ElasticsearchGen2TransportError.ConnectionFailed("GET", "/", "unavailable")),
        Right(Json.obj("unexpected" -> Json.fromString("shape"))),
        Left(BeautyQEmbeddingRequestError.Unavailable("embedding unavailable")),
      ) match {
        case PreflightBroken(resources) => assert(resources == Vector("Qdrant: missing or malformed version"))
        case other => fail(s"expected malformed Qdrant to win, got $other")
      }
    }

    "report incompatible embedding before unavailable resources" in {
      classifyPreflight(
        Left(ElasticsearchGen2TransportError.ConnectionFailed("GET", "/", "unavailable")),
        Left(Gen2HttpTransportError.ConnectionFailed("GET", "/", "unavailable")),
        Left(BeautyQEmbeddingRequestError.InvalidResult(QdrantEmbeddingError.DimensionMismatch(3, 2))),
      ) match {
        case PreflightBroken(resources) => assert(resources == Vector("embedding: InvalidResult(DimensionMismatch(3,2))"))
        case other => fail(s"expected incompatible embedding to win, got $other")
      }
    }

    "classify all unavailable resources as blocked" in {
      assert(classifyPreflight(
        Left(ElasticsearchGen2TransportError.ConnectionFailed("GET", "/", "unavailable")),
        Left(Gen2HttpTransportError.ConnectionFailed("GET", "/", "unavailable")),
        Left(BeautyQEmbeddingRequestError.Unavailable("unavailable")),
      ) == PreflightBlocked(Vector("Elasticsearch", "Qdrant", "embedding")))
    }

    "classify exact Qdrant 1.18.3 as ready" in {
      val input = QdrantEmbeddingInput.from(
        QdrantEmbeddingPurpose.CandidateQuery,
        "preflight",
        "preflight",
        BeautyQQdrantPolicy.policy.embeddingModel,
      ).getOrElse(fail("expected valid embedding input"))
      val embedding = QdrantEmbeddingResult.from(input, Vector.fill(input.modelValue.dimension)(0.0)).getOrElse(fail("expected valid embedding"))
      assert(classifyPreflight(
        Right(Json.obj("name" -> Json.fromString("elasticsearch"))),
        Right(Json.obj("version" -> Json.fromString("1.18.3"))),
        Right(embedding),
      ) == PreflightReady)
    }
  }

  private final class ElasticsearchAliasProbeClient(response: Either[ElasticsearchGen2TransportError, Json]) extends ElasticsearchGen2JsonClient {
    def putJson(path: String, body: Json) = response
    def post(path: String) = response
    def postJson(path: String, body: Json) = response
    def postNdjson(path: String, body: String) = response
    def getJson(path: String) = response
    def delete(path: String) = Left(ElasticsearchGen2TransportError.RequestFailed("DELETE", path, "unused"))
  }

  private final class QdrantAliasProbeClient(response: Either[Gen2HttpTransportError, Json]) extends QdrantGen2Client {
    def getCollection(collection: QdrantResourceName) = response
    def listAliases() = response
    def createCollection(collection: QdrantResourceName, body: Json) = response
    def createPayloadIndex(collection: QdrantResourceName, body: Json) = response
    def upsertPoints(collection: QdrantResourceName, body: Json) = response
    def countPoints(collection: QdrantResourceName) = response
    def updateAliases(body: Json) = response
    def queryPoints(target: QdrantResourceName, body: Json) = response
  }
}
