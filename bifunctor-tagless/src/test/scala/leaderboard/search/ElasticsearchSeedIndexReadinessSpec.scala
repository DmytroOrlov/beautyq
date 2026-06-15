package leaderboard.search

import io.circe.Json
import leaderboard.model.QueryFailure
import leaderboard.search.document.{BeautySearchCatalogSnapshot, BeautySearchReadyCatalogDocuments, VariantSearchDocumentBuilder}
import leaderboard.search.dsl.BeautySearchSpecV1
import leaderboard.search.elasticsearch.{
  ElasticsearchIngestionInterpreter,
  ElasticsearchJsonClient,
  ElasticsearchMappingInterpreter,
  ElasticsearchSeedIndexInitializer,
  ElasticsearchSeedIndexReadiness,
  ElasticsearchSeedSearchComposition,
  ElasticsearchSeedLifecycleStatus,
  ElasticsearchSeedPreparationMode,
}
import leaderboard.seed.BeautyQSeedLoader
import org.scalatest.wordspec.AnyWordSpec
import zio.{IO, Runtime, Unsafe, ZIO}

final class ElasticsearchSeedIndexReadinessSpec extends AnyWordSpec {

  private val spec = BeautySearchSpecV1.spec

  private val seedData = loadSeedData()
  private val snapshot = BeautySearchCatalogSnapshot.fromSeedData(seedData)
  private val allDocuments = VariantSearchDocumentBuilder.build(snapshot) match {
    case Right(value) => value
    case Left(error)  => throw new RuntimeException(error.message)
  }
  private val subset = allDocuments.take(2)
  private val ready = BeautySearchReadyCatalogDocuments(source = "seed-resource-loader", documents = subset)

  private val expectedMapping: Json = ElasticsearchMappingInterpreter.mapping(spec)
  private val expectedBulkPayload: String = ElasticsearchIngestionInterpreter.bulkPayload(spec, ready.documents)
  private val expectedIndexName: String = spec.variantDocument.indexName

  "ElasticsearchSeedIndexReadiness" should {
    "derive lifecycle metadata from the simple readiness value" in {
      val readiness = ElasticsearchSeedIndexReadiness(
        indexName = "beauty_variant_v1",
        source = "seed-resource-loader",
        documentCount = 2,
      )
      val metadata = readiness.lifecycleMetadata

      assert(metadata.indexName == readiness.indexName)
      assert(metadata.source == readiness.source)
      assert(metadata.documentCount == readiness.documentCount)
      assert(metadata.preparationMode == ElasticsearchSeedPreparationMode.EagerSeedIndexPreparation)
      assert(metadata.lifecycleStatus == ElasticsearchSeedLifecycleStatus.SeedOnlyNotProductionLifecycle)
    }

    "keep existing construction simple and pure" in {
      val readiness = ElasticsearchSeedIndexReadiness(
        indexName = "beauty_variant_v1",
        source = "seed-resource-loader",
        documentCount = 2,
      )

      assert(readiness.indexName == "beauty_variant_v1")
      assert(readiness.source == "seed-resource-loader")
      assert(readiness.documentCount == 2)
    }
  }

  private def run[A](effect: IO[QueryFailure, A]): A =
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(effect).getOrThrowFiberFailure()
    }

  private def runEither[A](effect: IO[QueryFailure, A]): Either[QueryFailure, A] =
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(effect.either).getOrThrowFiberFailure()
    }

  private def loadSeedData() =
    new BeautyQSeedLoader.ResourceLoader().load() match {
      case Right(value) => value
      case Left(error)  => throw new RuntimeException(error.message)
    }

  private final class ScriptedElasticsearchJsonClient(
    putJsonFn: (String, Json) => IO[QueryFailure, Json],
    postFn: String => IO[QueryFailure, Json],
    postNdjsonFn: (String, String) => IO[QueryFailure, Json],
  ) extends ElasticsearchJsonClient {
    override def putJson(path: String, json: Json): IO[QueryFailure, Json]       = putJsonFn(path, json)
    override def post(path: String): IO[QueryFailure, Json]                      = postFn(path)
    override def postJson(path: String, json: Json): IO[QueryFailure, Json]      = ZIO.dieMessage(s"unexpected postJson($path)")
    override def postNdjson(path: String, payload: String): IO[QueryFailure, Json] = postNdjsonFn(path, payload)
    override def getJson(path: String): IO[QueryFailure, Json]                   = ZIO.dieMessage(s"unexpected getJson($path)")
    override def delete(path: String): IO[QueryFailure, Unit]                    = ZIO.dieMessage(s"unexpected delete($path)")
  }

  "ElasticsearchSeedIndexInitializer.prepare" should {
    "expose seed lifecycle metadata through the search composition boundary" in {
      val client = new ScriptedElasticsearchJsonClient(
        putJsonFn = (path, json) => {
          assert(path == s"/$expectedIndexName", s"putJson path mismatch: $path")
          assert(json == expectedMapping, "putJson mapping mismatch")
          ZIO.succeed(Json.obj())
        },
        postFn = (path) => {
          assert(path == s"/$expectedIndexName/_refresh", s"post path mismatch: $path")
          ZIO.succeed(Json.obj())
        },
        postNdjsonFn = (path, payload) => {
          assert(path == s"/$expectedIndexName/_bulk", s"postNdjson path mismatch: $path")
          assert(payload == expectedBulkPayload, "postNdjson payload mismatch")
          ZIO.succeed(Json.obj())
        },
      )
      val composition = run(ElasticsearchSeedSearchComposition.build(spec, client, ready))
      val metadata = composition.lifecycleMetadata

      assert(metadata.indexName == expectedIndexName)
      assert(metadata.source == ready.source)
      assert(metadata.documentCount == ready.documents.size)
      assert(metadata.preparationMode == ElasticsearchSeedPreparationMode.EagerSeedIndexPreparation)
      assert(metadata.lifecycleStatus == ElasticsearchSeedLifecycleStatus.SeedOnlyNotProductionLifecycle)
    }

    "prepare seed index mapping, bulk payload, refresh, and return readiness handle" in {
      val client = new ScriptedElasticsearchJsonClient(
        putJsonFn = (path, json) => {
          assert(path == s"/$expectedIndexName", s"putJson path mismatch: $path")
          assert(json == expectedMapping, "putJson mapping mismatch")
          ZIO.succeed(Json.obj())
        },
        postFn = (path) => {
          assert(path == s"/$expectedIndexName/_refresh", s"post path mismatch: $path")
          ZIO.succeed(Json.obj())
        },
        postNdjsonFn = (path, payload) => {
          assert(path == s"/$expectedIndexName/_bulk", s"postNdjson path mismatch: $path")
          assert(payload == expectedBulkPayload, "postNdjson payload mismatch")
          ZIO.succeed(Json.obj())
        },
      )
      val initializer = new ElasticsearchSeedIndexInitializer(spec, client)
      val result = run(initializer.prepare(ready))

      assert(result.indexName == expectedIndexName)
      assert(result.source == ready.source)
      assert(result.documentCount == ready.documents.size)
    }

    "reject empty ready documents before Elasticsearch calls" in {
      val emptyReady = BeautySearchReadyCatalogDocuments(source = "seed-resource-loader", documents = Nil)
      val client = new ScriptedElasticsearchJsonClient(
        putJsonFn = (_, _) => ZIO.dieMessage("unexpected putJson"),
        postFn = (_) => ZIO.dieMessage("unexpected post"),
        postNdjsonFn = (_, _) => ZIO.dieMessage("unexpected postNdjson"),
      )
      val initializer = new ElasticsearchSeedIndexInitializer(spec, client)
      val result = runEither(initializer.prepare(emptyReady))

      result match {
        case Left(QueryFailure.OperationFailure(operationName, message)) =>
          assert(operationName == "elasticsearch-seed-index-readiness")
          assert(message.contains("documents are empty"), s"message should contain 'documents are empty': $message")
        case other =>
          fail(s"Expected OperationFailure with documents empty, got $other")
      }
    }

    "reject blank source before Elasticsearch calls" in {
      val blankSourceReady = BeautySearchReadyCatalogDocuments(source = "   ", documents = subset)
      val client = new ScriptedElasticsearchJsonClient(
        putJsonFn = (_, _) => ZIO.dieMessage("unexpected putJson"),
        postFn = (_) => ZIO.dieMessage("unexpected post"),
        postNdjsonFn = (_, _) => ZIO.dieMessage("unexpected postNdjson"),
      )
      val initializer = new ElasticsearchSeedIndexInitializer(spec, client)
      val result = runEither(initializer.prepare(blankSourceReady))

      result match {
        case Left(QueryFailure.OperationFailure(operationName, message)) =>
          assert(operationName == "elasticsearch-seed-index-readiness")
          assert(message.contains("source is empty"), s"message should contain 'source is empty': $message")
        case other =>
          fail(s"Expected OperationFailure with source empty, got $other")
      }
    }

    "propagate Elasticsearch client failures unchanged" in {
      val client = new ScriptedElasticsearchJsonClient(
        putJsonFn = (_, _) => ZIO.succeed(Json.obj()),
        postFn = (_) => ZIO.fail(ElasticsearchJsonClient.failure("refresh failed")),
        postNdjsonFn = (_, _) => ZIO.succeed(Json.obj()),
      )
      val initializer = new ElasticsearchSeedIndexInitializer(spec, client)
      val result = runEither(initializer.prepare(ready))

      result match {
        case Left(QueryFailure.OperationFailure(operationName, message)) =>
          assert(operationName == "elasticsearch-json-client")
          assert(message == "refresh failed")
        case other =>
          fail(s"Expected OperationFailure(elasticsearch-json-client, refresh failed), got $other")
      }
    }
  }
}
