package leaderboard.search

import io.circe.Json
import io.circe.syntax._
import leaderboard.model.QueryFailure
import leaderboard.search.document.{BeautyQSearchCatalogSnapshot, BeautyQVariantSearchDocumentMaterialization, BeautySearchReadyCatalogDocuments}
import leaderboard.search.dsl.BeautySearchSpecV1
import leaderboard.search.elasticsearch.{
  ElasticsearchLifecycleStatusResponse,
  ElasticsearchProductionReadinessState,
  ElasticsearchSeedIndexReadiness,
  ElasticsearchSeedSearchComposition,
  ElasticsearchStartupReadinessStatusResponse,
  ElasticsearchStartupReadinessTransition,
  ElasticsearchStartupServingDecision,
  ElasticsearchJsonClient,
}
import leaderboard.seed.BeautyQSeedLoader
import org.scalatest.wordspec.AnyWordSpec
import zio.{IO, Runtime, Unsafe, ZIO}

final class ElasticsearchReadinessConsistencySpec extends AnyWordSpec {

  private val state: ElasticsearchProductionReadinessState =
    ElasticsearchProductionReadinessState.seedOnly(
      ElasticsearchSeedIndexReadiness(
        indexName = "beautyq_variant_v1",
        source = "seed-resource-loader",
        documentCount = 37,
      ).lifecycleMetadata
    )

  private val lifecycleResponse: ElasticsearchLifecycleStatusResponse =
    ElasticsearchLifecycleStatusResponse.from(state)

  private val preparedTransition: ElasticsearchStartupReadinessTransition.Prepared =
    ElasticsearchStartupReadinessTransition.prepared(state)

  private val preparedStatusProjection: ElasticsearchStartupReadinessStatusResponse =
    ElasticsearchStartupReadinessStatusResponse.from(preparedTransition)

  "Cross-model readiness consistency" should {

    "preserve exact seed-only field values across readiness state, lifecycle response, transition, and status projection" in {
      assert(lifecycleResponse.servingReadiness == "not_enforced")
      assert(lifecycleResponse.replacement == "not_configured")
      assert(lifecycleResponse.freshness == "not_tracked")
      assert(lifecycleResponse.refresh == "eager_seed_preparation_only")
      assert(lifecycleResponse.operatorVisibility == "not_exposed")
      assert(lifecycleResponse.lifecycleStatus == "seed_only_not_production_lifecycle")
      assert(lifecycleResponse.preparationMode == "eager_seed_index_preparation")
      assert(lifecycleResponse.productionLifecycleComplete == false)

      assert(preparedTransition.servingDecision == ElasticsearchStartupServingDecision.NotEnforced)

      assert(preparedStatusProjection.transitionStatus == "prepared")
      assert(preparedStatusProjection.servingDecision == "not_enforced")
      assert(preparedStatusProjection.productionLifecycleComplete == false)
    }

    "derive identical lifecycle status response from transition and from direct state projection" in {
      val transitionDerived = preparedTransition.lifecycleStatusResponse
      assert(transitionDerived.contains(lifecycleResponse))
      assert(transitionDerived == Some(lifecycleResponse))
    }

    "produce identical nested lifecycle status in startup status projection as direct state projection" in {
      preparedStatusProjection match {
        case p: ElasticsearchStartupReadinessStatusResponse.Prepared =>
          assert(p.lifecycleStatus == lifecycleResponse)
        case other =>
          fail(s"Expected Prepared projection, got $other")
      }
    }

    "encode identical lifecycle JSON whether derived directly or through startup status projection" in {
      val directJson = lifecycleResponse.asJson
      val nestedStatusJson = preparedStatusProjection.asJson.hcursor
        .downField("lifecycleStatus")

      assert(nestedStatusJson.focus.contains(directJson))
    }

    "encode exact prepared startup status JSON shape" in {
      val json = preparedStatusProjection.asJson

      assert(
        json == Json.obj(
          "transitionStatus" -> Json.fromString("prepared"),
          "servingDecision" -> Json.fromString("not_enforced"),
          "lifecycleStatus" -> lifecycleResponse.asJson,
          "productionLifecycleComplete" -> Json.False,
        )
      )
    }
  }

  "Failed startup status projection consistency" should {

    "construct PreparationFailed from OperationFailure and preserve operation name and message" in {
      val failure = QueryFailure.operation(
        operationName = "elasticsearch-json-client",
        message = "refresh failed",
      )

      ElasticsearchStartupReadinessTransition.preparationFailed(failure) match {
        case Right(transition) =>
          val projection = ElasticsearchStartupReadinessStatusResponse.from(transition)

          projection match {
            case failed: ElasticsearchStartupReadinessStatusResponse.PreparationFailed =>
              assert(failed.transitionStatus == "preparation_failed")
              assert(failed.servingDecision == "not_enforced")
              assert(failed.operationName == "elasticsearch-json-client")
              assert(failed.message == "refresh failed")
              assert(failed.productionLifecycleComplete == false)
            case other =>
              fail(s"Expected PreparationFailed projection, got $other")
          }
        case Left(unsupported) =>
          fail(s"Expected supported OperationFailure, got $unsupported")
      }
    }

    "encode failed projection JSON without lifecycle metadata or lifecycleStatus fields" in {
      val failure = QueryFailure.operation(
        operationName = "elasticsearch-seed-index-readiness",
        message = "seed index readiness documents are empty",
      )

      ElasticsearchStartupReadinessTransition.preparationFailed(failure) match {
        case Right(transition) =>
          val json = ElasticsearchStartupReadinessStatusResponse.from(transition).asJson

          assert(
            json == Json.obj(
              "transitionStatus" -> Json.fromString("preparation_failed"),
              "servingDecision" -> Json.fromString("not_enforced"),
              "operationName" -> Json.fromString("elasticsearch-seed-index-readiness"),
              "message" -> Json.fromString("seed index readiness documents are empty"),
              "productionLifecycleComplete" -> Json.False,
            )
          )

          val cursor = json.hcursor
          assert(cursor.get[String]("indexName").isLeft, "failed JSON must not contain indexName")
          assert(cursor.get[String]("source").isLeft, "failed JSON must not contain source")
          assert(cursor.get[Int]("documentCount").isLeft, "failed JSON must not contain documentCount")
          assert(cursor.get[String]("preparationMode").isLeft, "failed JSON must not contain preparationMode")
          assert(cursor.downField("lifecycleStatus").focus.isEmpty, "failed JSON must not contain lifecycleStatus field")
        case Left(unsupported) =>
          fail(s"Expected supported OperationFailure, got $unsupported")
      }
    }
  }

  "Unsupported failure coverage" should {

    "leave DomainFailure as unsupported" in {
      val domainFailure = QueryFailure.domain("unsupported readiness failure")

      assert(
        ElasticsearchStartupReadinessTransition.preparationFailed(domainFailure) == Left(
          ElasticsearchStartupReadinessTransition.UnsupportedFailure(domainFailure)
        )
      )
    }

    "leave QueryExecutionFailure as unsupported" in {
      val executionFailure = QueryFailure.fromThrowable(
        queryName = "seed-index-preparation",
        cause = new RuntimeException("client failed"),
      )

      assert(
        ElasticsearchStartupReadinessTransition.preparationFailed(executionFailure) == Left(
          ElasticsearchStartupReadinessTransition.UnsupportedFailure(executionFailure)
        )
      )
    }
  }

  "Composition-derived consistency" should {

    "derive startupReadinessTransition equal to direct prepared(state) from the same readiness state" in {
      val composition = run(buildComposition())
      val compositionTransition = composition.startupReadinessTransition
      val directTransition = ElasticsearchStartupReadinessTransition.prepared(composition.productionReadinessState)

      compositionTransition match {
        case prepared: ElasticsearchStartupReadinessTransition.Prepared =>
          assert(prepared.state == directTransition.state)
          assert(prepared.servingDecision == directTransition.servingDecision)
          assert(prepared.lifecycleMetadata == directTransition.lifecycleMetadata)
          assert(prepared.lifecycleStatusResponse == directTransition.lifecycleStatusResponse)
        case other =>
          fail(s"Expected Prepared composition transition, got $other")
      }
    }

    "derive startup status projection from composition equal to direct projection from the same readiness state" in {
      val composition = run(buildComposition())
      val compositionProjection = ElasticsearchStartupReadinessStatusResponse.from(composition.startupReadinessTransition)
      val directProjection = ElasticsearchStartupReadinessStatusResponse.from(
        ElasticsearchStartupReadinessTransition.prepared(composition.productionReadinessState)
      )

      assert(compositionProjection.transitionStatus == directProjection.transitionStatus)
      assert(compositionProjection.servingDecision == directProjection.servingDecision)
      assert(compositionProjection.productionLifecycleComplete == directProjection.productionLifecycleComplete)

      (compositionProjection, directProjection) match {
        case (
              c: ElasticsearchStartupReadinessStatusResponse.Prepared,
              d: ElasticsearchStartupReadinessStatusResponse.Prepared,
            ) =>
          assert(c.lifecycleStatus == d.lifecycleStatus)
          assert(c.lifecycleStatus.productionLifecycleComplete == false)
        case (c, d) =>
          fail(s"Expected two Prepared projections, got $c and $d")
      }
    }

    "encode composition-derived startup status JSON equal to direct projection JSON" in {
      val composition = run(buildComposition())
      val compositionJson = ElasticsearchStartupReadinessStatusResponse
        .from(composition.startupReadinessTransition)
        .asJson
      val directJson = ElasticsearchStartupReadinessStatusResponse
        .from(ElasticsearchStartupReadinessTransition.prepared(composition.productionReadinessState))
        .asJson

      assert(compositionJson == directJson)
    }
  }

  private val spec = BeautySearchSpecV1.spec

  private def buildComposition(): IO[QueryFailure, ElasticsearchSeedSearchComposition] = {
    val seedData = new BeautyQSeedLoader.ResourceLoader().load() match {
      case Right(value) => value
      case Left(error)  => throw new RuntimeException(error.message)
    }
    val snapshot = BeautyQSearchCatalogSnapshot(
      categories                 = seedData.categories,
      services                   = seedData.services,
      serviceVariantSchemas      = seedData.serviceVariantSchemas,
      masters                    = seedData.masters,
      masterLocations            = seedData.masterLocations,
      masterServiceOffers        = seedData.masterServiceOffers,
      masterServiceOfferVariants = seedData.masterServiceOfferVariants,
    )
    val allDocuments = BeautyQVariantSearchDocumentMaterialization.project(snapshot) match {
      case Right(value) => value
      case Left(error)  => throw new RuntimeException(error.message)
    }
    val subset = allDocuments.take(2)
    val ready = BeautySearchReadyCatalogDocuments(source = "seed-resource-loader", documents = subset)

    val client = new ElasticsearchJsonClient {
      private val emptySearchResponse: Json =
        Json.obj("hits" -> Json.obj("hits" -> Json.arr()))

      override def putJson(path: String, json: Json): IO[QueryFailure, Json]        = ZIO.succeed(Json.obj())
      override def post(path: String): IO[QueryFailure, Json]                       = ZIO.succeed(Json.obj())
      override def postJson(path: String, json: Json): IO[QueryFailure, Json]        = ZIO.succeed(emptySearchResponse)
      override def postNdjson(path: String, payload: String): IO[QueryFailure, Json] = ZIO.succeed(Json.obj())
      override def getJson(path: String): IO[QueryFailure, Json]                    = ZIO.dieMessage(s"unexpected getJson($path)")
      override def delete(path: String): IO[QueryFailure, Unit]                     = ZIO.dieMessage(s"unexpected delete($path)")
    }

    ElasticsearchSeedSearchComposition.build(spec, client, ready)
  }

  private def run[A](effect: IO[QueryFailure, A]): A =
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(effect).getOrThrowFiberFailure()
    }
}
