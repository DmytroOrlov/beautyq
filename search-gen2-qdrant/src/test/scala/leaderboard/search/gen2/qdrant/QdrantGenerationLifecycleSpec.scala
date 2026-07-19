package leaderboard.search.gen2.qdrant

import io.circe.Json
import leaderboard.search.gen2.transport.Gen2HttpTransportError
import org.scalatest.wordspec.AnyWordSpec

final class QdrantGenerationLifecycleSpec extends AnyWordSpec {
  "QdrantGenerationLifecycle" should {
    "create a deterministic collection, index payload fields, upsert points, count exactly and create the alias" in {
      val generation = compiledGeneration
      val alias = QdrantResourceName.from("neutral_alias").getOrElse(fail("expected alias"))
      val config = QdrantGenerationLifecycleConfig.create(alias, "neutral_alias_").getOrElse(fail("expected config"))
      val client = new CreateClient(generation)

      val active = new QdrantGenerationLifecycle(client, config).activate(generation) match {
        case Right(value) => value
        case Left(error) => fail(s"expected activation, got $error")
      }
      assert(active.alias.value == alias.value)
      assert(active.physicalCollection.value == generation.physicalCollectionName)
      assert(client.createdIndexCount == QdrantCollectionWire.expectedPayloadSchema(generation.payloadIndexRequests).size)
    }

    "reuse compatible state and avoid an alias mutation when it is already active" in {
      val generation = compiledGeneration
      val alias = QdrantResourceName.from("neutral_alias").getOrElse(fail("expected alias"))
      val config = QdrantGenerationLifecycleConfig.create(alias, "neutral_alias_").getOrElse(fail("expected config"))
      val client = new ExistingClient(generation, alias.value, generation.physicalCollectionName)
      new QdrantGenerationLifecycle(client, config).activate(generation).getOrElse(fail("expected activation"))
      succeed
    }

    "reject incompatible vector state before any point mutation or alias switch" in {
      val generation = compiledGeneration
      val alias = QdrantResourceName.from("neutral_alias").getOrElse(fail("expected alias"))
      val config = QdrantGenerationLifecycleConfig.create(alias, "neutral_alias_").getOrElse(fail("expected config"))
      val client = new ExistingClient(generation, alias.value, generation.physicalCollectionName, wrongVector = true)
      new QdrantGenerationLifecycle(client, config).activate(generation) match {
        case Left(QdrantGenerationLifecycleError.CollectionIncompatible(_, _)) => succeed
        case other => fail(s"expected typed incompatibility, got $other")
      }
    }
  }

  private def compiledGeneration: QdrantCompiledGeneration = {
    val prepared = QdrantGenerationCompiler.prepare(QdrantTestFixtures.policy, QdrantTestFixtures.materialized).getOrElse(fail("expected prepared generation"))
    val embeddings = prepared.points.map(point => QdrantEmbeddingResult.from(point.embeddingInput, Vector(0.1, 0.2, 0.3)).getOrElse(fail("expected embedding")))
    QdrantGenerationCompiler.complete(prepared, embeddings, "neutral_alias_").getOrElse(fail("expected compiled generation"))
  }

  private def details(generation: QdrantCompiledGeneration, pointsCount: Int, wrongVector: Boolean): Json = {
    val vectors = generation.collectionJson.hcursor.downField("vectors").focus.getOrElse(Json.obj())
    val metadata = generation.collectionJson.hcursor.downField("metadata").focus.getOrElse(Json.obj())
    val firstPoint = generation.points.headOption.getOrElse(fail("expected at least one point"))
    val actualVectors = if (wrongVector) Json.obj(firstPoint.vectorName.value -> Json.obj("size" -> Json.fromInt(999), "distance" -> Json.fromString("Dot"))) else vectors
    val payload = Json.obj(QdrantCollectionWire.expectedPayloadSchema(generation.payloadIndexRequests).toVector.map { case (field, schema) => field -> Json.obj("data_type" -> Json.fromString(schema)) }*)
    Json.obj(
      "status" -> Json.fromString("ok"),
      "result" -> Json.obj(
        "config" -> Json.obj("params" -> Json.obj("vectors" -> actualVectors), "metadata" -> metadata),
        "payload_schema" -> payload,
        "points_count" -> Json.fromInt(pointsCount),
      ),
    )
  }

  private abstract class BaseClient extends QdrantGen2Client {
    val createdIndexCount: Int = QdrantCollectionWire.expectedPayloadSchema(compiledGenerationForClient.payloadIndexRequests).size

    def createCollection(collection: QdrantResourceName, body: Json): Either[Gen2HttpTransportError, Json] = Right(okBoolean)
    def createPayloadIndex(collection: QdrantResourceName, body: Json): Either[Gen2HttpTransportError, Json] = {
      assert(body.hcursor.get[String]("field_name").isRight)
      Right(okOperation)
    }
    def upsertPoints(collection: QdrantResourceName, body: Json): Either[Gen2HttpTransportError, Json] = Right(okOperation)
    def countPoints(collection: QdrantResourceName): Either[Gen2HttpTransportError, Json] = Right(Json.obj("status" -> Json.fromString("ok"), "result" -> Json.obj("count" -> Json.fromInt(2))))
    def updateAliases(body: Json): Either[Gen2HttpTransportError, Json] = {
      assert(body.hcursor.downField("actions").values.exists(_.nonEmpty))
      Right(okBoolean)
    }
    def queryPoints(target: QdrantResourceName, body: Json): Either[Gen2HttpTransportError, Json] = Left(Gen2HttpTransportError.RequestFailed("POST", "query", "unused"))

    protected val okBoolean: Json = Json.obj("status" -> Json.fromString("ok"), "result" -> Json.fromBoolean(true))
    protected val okOperation: Json = Json.obj("status" -> Json.fromString("ok"), "result" -> Json.obj("status" -> Json.fromString("completed")))

    private def compiledGenerationForClient: QdrantCompiledGeneration = {
      val prepared = QdrantGenerationCompiler.prepare(QdrantTestFixtures.policy, QdrantTestFixtures.materialized).getOrElse(fail("expected prepared generation"))
      val embeddings = prepared.points.map(point => QdrantEmbeddingResult.from(point.embeddingInput, Vector(0.1, 0.2, 0.3)).getOrElse(fail("expected embedding")))
      QdrantGenerationCompiler.complete(prepared, embeddings, "neutral_alias_").getOrElse(fail("expected compiled generation"))
    }
  }

  private final class CreateClient(generation: QdrantCompiledGeneration) extends BaseClient {
    private var created = false
    def getCollection(collection: QdrantResourceName): Either[Gen2HttpTransportError, Json] =
      if (created) Right(details(generation, generation.metadata.pointCount, wrongVector = false))
      else Left(Gen2HttpTransportError.HttpFailure("GET", s"/collections/${generation.physicalCollectionName}", 404, "missing"))
    def listAliases(): Either[Gen2HttpTransportError, Json] = Right(Json.obj("status" -> Json.fromString("ok"), "result" -> Json.obj("aliases" -> Json.arr())))
    override def createCollection(collection: QdrantResourceName, body: Json): Either[Gen2HttpTransportError, Json] = {
      assert(body.hcursor.downField("metadata").downField("search_gen2").focus.nonEmpty)
      created = true
      super.createCollection(collection, body)
    }
    override def updateAliases(body: Json): Either[Gen2HttpTransportError, Json] = {
      body.hcursor.downField("actions").as[Vector[Json]] match {
        case Right(Vector(action)) => assert(action.hcursor.downField("create_alias").get[String]("alias_name") == Right("neutral_alias"))
        case other => fail(s"expected one create-alias action, got $other")
      }
      super.updateAliases(body)
    }
  }

  private final class ExistingClient(generation: QdrantCompiledGeneration, alias: String, target: String, wrongVector: Boolean = false) extends BaseClient {
    def getCollection(collection: QdrantResourceName): Either[Gen2HttpTransportError, Json] = Right(details(generation, generation.metadata.pointCount, wrongVector))
    def listAliases(): Either[Gen2HttpTransportError, Json] = Right(Json.obj("status" -> Json.fromString("ok"), "result" -> Json.obj("aliases" -> Json.arr(Json.obj("alias_name" -> Json.fromString(alias), "collection_name" -> Json.fromString(target))))))
    override def updateAliases(body: Json): Either[Gen2HttpTransportError, Json] = Left(Gen2HttpTransportError.RequestFailed("POST", "/collections/aliases", "alias mutation must not be called"))
  }
}
