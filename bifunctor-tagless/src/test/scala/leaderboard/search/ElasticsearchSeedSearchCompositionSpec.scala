package leaderboard.search

import io.circe.Json
import leaderboard.model.QueryFailure
import leaderboard.search.document.{BeautySearchCatalogSnapshot, BeautySearchReadyCatalogDocuments, VariantSearchDocumentBuilder}
import leaderboard.search.dsl.BeautySearchSpecV1
import leaderboard.search.elasticsearch.{
  ElasticsearchFreshnessReadiness,
  ElasticsearchJsonClient,
  ElasticsearchLifecycleStatusResponse,
  ElasticsearchOperatorVisibility,
  ElasticsearchProductionReadinessState,
  ElasticsearchRefreshReadiness,
  ElasticsearchReplacementReadiness,
  ElasticsearchRollbackReadiness,
  ElasticsearchSeedSearchComposition,
  ElasticsearchServingReadiness,
  ElasticsearchStartupReadinessTransition,
  ElasticsearchStartupServingDecision,
}
import leaderboard.seed.BeautyQSeedLoader
import org.scalatest.wordspec.AnyWordSpec
import zio.{IO, Runtime, Unsafe, ZIO}

final class ElasticsearchSeedSearchCompositionSpec extends AnyWordSpec {

  private val spec = BeautySearchSpecV1.spec

  private val seedData = loadSeedData()
  private val snapshot = BeautySearchCatalogSnapshot.fromSeedData(seedData)
  private val allDocuments = VariantSearchDocumentBuilder.build(snapshot) match {
    case Right(value) => value
    case Left(error)  => throw new RuntimeException(error.message)
  }
  private val subset = allDocuments.take(2)
  private val ready = BeautySearchReadyCatalogDocuments(source = "seed-resource-loader", documents = subset)

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
    postFn: String => IO[QueryFailure, Json],
    postJsonFn: (String, Json) => IO[QueryFailure, Json],
    postNdjsonFn: (String, String) => IO[QueryFailure, Json],
  ) extends ElasticsearchJsonClient {
    override def putJson(path: String, json: Json): IO[QueryFailure, Json]       = putJsonFn(path, json)
    override def post(path: String): IO[QueryFailure, Json]                      = postFn(path)
    override def postJson(path: String, json: Json): IO[QueryFailure, Json]      = postJsonFn(path, json)
    override def postNdjson(path: String, payload: String): IO[QueryFailure, Json] = postNdjsonFn(path, payload)
    override def getJson(path: String): IO[QueryFailure, Json]                   = ZIO.dieMessage(s"unexpected getJson($path)")
    override def delete(path: String): IO[QueryFailure, Unit]                    = ZIO.dieMessage(s"unexpected delete($path)")
  }

  private val emptySearchResponse: Json =
    Json.obj("hits" -> Json.obj("hits" -> Json.arr()))

  private def succeedingClient: ScriptedElasticsearchJsonClient =
    new ScriptedElasticsearchJsonClient(
      putJsonFn = (_, _) => ZIO.succeed(Json.obj()),
      postFn = (path) =>
        if (path.contains("_refresh")) ZIO.succeed(Json.obj())
        else ZIO.dieMessage(s"unexpected post($path)"),
      postJsonFn = (path, _) =>
        if (path.contains("_search")) ZIO.succeed(emptySearchResponse)
        else ZIO.dieMessage(s"unexpected postJson($path)"),
      postNdjsonFn = (_, _) => ZIO.succeed(Json.obj()),
    )

  "ElasticsearchSeedSearchComposition.build" should {
    "prepare index and return Elasticsearch backend/service composition" in {
      val composition = run(ElasticsearchSeedSearchComposition.build(spec, succeedingClient, ready))

      assert(composition.readiness.indexName == expectedIndexName)
      assert(composition.readiness.source == ready.source)
      assert(composition.readiness.documentCount == ready.documents.size)
    }

    "derive the non-serving production readiness state from composition metadata" in {
      val composition = run(ElasticsearchSeedSearchComposition.build(spec, succeedingClient, ready))
      val state = composition.productionReadinessState

      assert(state.lifecycleMetadata == composition.lifecycleMetadata)
      assert(state.servingReadiness == ElasticsearchServingReadiness.NotEnforced)
      assert(state.replacement == ElasticsearchReplacementReadiness.NotConfigured)
      assert(state.freshness == ElasticsearchFreshnessReadiness.NotTracked)
      assert(state.refresh == ElasticsearchRefreshReadiness.EagerSeedPreparationOnly)
      assert(state.rollback == ElasticsearchRollbackReadiness.NotConfigured)
      assert(state.operatorVisibility == ElasticsearchOperatorVisibility.NotExposed)
    }

    "expose a prepared startup transition that preserves the readiness state" in {
      val composition = run(ElasticsearchSeedSearchComposition.build(spec, succeedingClient, ready))
      val transition = composition.startupReadinessTransition

      transition match {
        case prepared: ElasticsearchStartupReadinessTransition.Prepared =>
          assert(prepared.state == composition.productionReadinessState)
          assert(prepared.servingDecision == ElasticsearchStartupServingDecision.NotEnforced)
          assert(prepared.lifecycleMetadata.contains(composition.lifecycleMetadata))
          val expectedResponse = ElasticsearchLifecycleStatusResponse.from(composition.productionReadinessState)
          assert(prepared.lifecycleStatusResponse.contains(expectedResponse))
          assert(expectedResponse.productionLifecycleComplete == false)
        case other =>
          fail(s"Expected Prepared transition, got $other")
      }
    }

    "propagate readiness failure before returning composition" in {
      val client = succeedingClient
      val result = runEither(ElasticsearchSeedSearchComposition.build(spec, client, ready.copy(source = "   ")))

      result match {
        case Left(QueryFailure.OperationFailure(operationName, message)) =>
          assert(operationName == "elasticsearch-seed-index-readiness")
          assert(message.contains("source is empty"), s"message should contain 'source is empty': $message")
        case other =>
          fail(s"Expected OperationFailure with source empty, got $other")
      }
    }

    "constructed service searches through the Elasticsearch backend" in {
      val searchClient = new ScriptedElasticsearchJsonClient(
        putJsonFn = (_, _) => ZIO.succeed(Json.obj()),
        postFn = (path) =>
          if (path.contains("_refresh")) ZIO.succeed(Json.obj())
          else ZIO.dieMessage(s"unexpected post($path)"),
        postJsonFn = (path, _) =>
          if (path.contains("_search")) ZIO.succeed(emptySearchResponse)
          else ZIO.dieMessage(s"unexpected postJson($path)"),
        postNdjsonFn = (_, _) => ZIO.succeed(Json.obj()),
      )

      val composition = run(ElasticsearchSeedSearchComposition.build(spec, searchClient, ready))
      val input = UserSearchInput("nails", Some(BigDecimal("53.57532")), Some(BigDecimal("10.07672")), limit = 3)
      val response = run(composition.service.search(input))

      assert(response.variantCarousel.isEmpty)
      assert(response.providerCarousel.isEmpty)
      assert(response.serviceIntentCarousel.isEmpty)
    }
  }
}
