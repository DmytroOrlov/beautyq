package leaderboard.search

import io.circe.Json
import leaderboard.model.QueryFailure
import leaderboard.search.document.{BeautySearchCatalogSnapshot, BeautySearchReadyCatalogDocuments, VariantSearchDocumentBuilder}
import leaderboard.search.dsl.BeautySearchSpecV1
import leaderboard.search.elasticsearch.{ElasticsearchIngestionInterpreter, ElasticsearchJsonClient, ElasticsearchMappingInterpreter, ElasticsearchSeedIndexInitializer}
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
    postJsonFn: (String, Json) => IO[QueryFailure, Json],
    postNdjsonFn: (String, String) => IO[QueryFailure, Json],
  ) extends ElasticsearchJsonClient {
    override def putJson(path: String, json: Json): IO[QueryFailure, Json]       = putJsonFn(path, json)
    override def postJson(path: String, json: Json): IO[QueryFailure, Json]      = postJsonFn(path, json)
    override def postNdjson(path: String, payload: String): IO[QueryFailure, Json] = postNdjsonFn(path, payload)
    override def getJson(path: String): IO[QueryFailure, Json]                   = ZIO.dieMessage(s"unexpected getJson($path)")
    override def delete(path: String): IO[QueryFailure, Unit]                    = ZIO.dieMessage(s"unexpected delete($path)")
  }

  "ElasticsearchSeedIndexInitializer.prepare" should {
    "prepare seed index mapping, bulk payload, refresh, and return readiness handle" in {
      val client = new ScriptedElasticsearchJsonClient(
        putJsonFn = (path, json) => {
          assert(path == s"/$expectedIndexName", s"putJson path mismatch: $path")
          assert(json == expectedMapping, "putJson mapping mismatch")
          ZIO.succeed(Json.obj())
        },
        postJsonFn = (path, json) => {
          assert(path == s"/$expectedIndexName/_refresh", s"postJson path mismatch: $path")
          assert(json == Json.obj(), "postJson body mismatch")
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
        postJsonFn = (_, _) => ZIO.dieMessage("unexpected postJson"),
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
        postJsonFn = (_, _) => ZIO.dieMessage("unexpected postJson"),
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
        postJsonFn = (_, _) => ZIO.succeed(Json.obj()),
        postNdjsonFn = (_, _) => ZIO.fail(ElasticsearchJsonClient.failure("bulk failed")),
      )
      val initializer = new ElasticsearchSeedIndexInitializer(spec, client)
      val result = runEither(initializer.prepare(ready))

      result match {
        case Left(QueryFailure.OperationFailure(operationName, message)) =>
          assert(operationName == "elasticsearch-json-client")
          assert(message == "bulk failed")
        case other =>
          fail(s"Expected OperationFailure(elasticsearch-json-client, bulk failed), got $other")
      }
    }
  }
}
