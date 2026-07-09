package leaderboard.search

import io.circe.Json
import leaderboard.model.{MasterId, MasterLocationId, MasterServiceOfferId, MasterServiceOfferVariantId, QueryFailure, ServiceId}
import leaderboard.model.Category.CategoryId
import leaderboard.search.document.{BeautyQSearchCatalogSnapshot, BeautyQVariantSearchDocumentMaterialization, BeautySearchReadyCatalogDocuments, VariantSearchDocument}
import leaderboard.search.dsl.{BeautySearchSpecV1, SearchGeoPoint}
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
  ElasticsearchIngestionInterpreter,
}
import leaderboard.seed.BeautyQSeedLoader
import org.scalatest.wordspec.AnyWordSpec
import zio.{IO, Runtime, Unsafe, ZIO}

import java.util.UUID

final class ElasticsearchSeedSearchCompositionSpec extends AnyWordSpec {

  private val spec = BeautySearchSpecV1.spec

  private val seedData = loadSeedData()
  private val snapshot = BeautyQSearchCatalogSnapshot(
    categories                 = seedData.categories,
    services                   = seedData.services,
    serviceVariantSchemas      = seedData.serviceVariantSchemas,
    masters                    = seedData.masters,
    masterLocations            = seedData.masterLocations,
    masterServiceOffers        = seedData.masterServiceOffers,
    masterServiceOfferVariants = seedData.masterServiceOfferVariants,
  )
  private val allDocuments = BeautyQVariantSearchDocumentMaterialization.project(snapshot) match {
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

    "derive unchanged source JSON with nested dynamic attributes" in {
      val document = VariantSearchDocument(
        variantId = MasterServiceOfferVariantId(UUID.fromString("00000000-0000-0000-0000-000000000101")),
        masterServiceOfferId = MasterServiceOfferId(UUID.fromString("00000000-0000-0000-0000-000000000202")),
        masterLocationId = MasterLocationId(UUID.fromString("00000000-0000-0000-0000-000000000303")),
        masterId = MasterId(UUID.fromString("00000000-0000-0000-0000-000000000404")),
        serviceId = ServiceId(UUID.fromString("00000000-0000-0000-0000-000000000505")),
        categoryId = CategoryId(UUID.fromString("00000000-0000-0000-0000-000000000606")),
        serviceName = "Manicure",
        categoryName = "Nails",
        masterName = "Beauty Master",
        locationName = "Central Studio",
        address = "Main street 1",
        location = SearchGeoPoint(lat = BigDecimal("52.5200"), lon = BigDecimal("13.4050")),
        lat = BigDecimal("52.5200"),
        lon = BigDecimal("13.4050"),
        priceFrom = BigDecimal("25.00"),
        priceTo = BigDecimal("40.00"),
        durationMin = 45,
        enumAttributes = Map("nail_service_type" -> "manicure"),
        booleanAttributes = Map("with_removal" -> true),
        intAttributes = Map.empty,
        bigDecimalAttributes = Map.empty,
        allText = "manicure nails beauty master central studio",
        serviceText = "manicure nails",
        attributeText = "nail service type manicure with removal true",
        providerText = "beauty master central studio",
        locationText = "central studio main street 1 nails",
      )

      val json = ElasticsearchIngestionInterpreter.sourceJson(spec.variantDocument, document)

      assert(json == Json.obj(
        "variantId" -> Json.fromString("00000000-0000-0000-0000-000000000101"),
        "masterServiceOfferId" -> Json.fromString("00000000-0000-0000-0000-000000000202"),
        "masterLocationId" -> Json.fromString("00000000-0000-0000-0000-000000000303"),
        "masterId" -> Json.fromString("00000000-0000-0000-0000-000000000404"),
        "serviceId" -> Json.fromString("00000000-0000-0000-0000-000000000505"),
        "categoryId" -> Json.fromString("00000000-0000-0000-0000-000000000606"),
        "serviceName" -> Json.fromString("Manicure"),
        "categoryName" -> Json.fromString("Nails"),
        "masterName" -> Json.fromString("Beauty Master"),
        "locationName" -> Json.fromString("Central Studio"),
        "address" -> Json.fromString("Main street 1"),
        "lat" -> Json.fromBigDecimal(BigDecimal("52.5200")),
        "lon" -> Json.fromBigDecimal(BigDecimal("13.4050")),
        "priceFrom" -> Json.fromBigDecimal(BigDecimal("25.00")),
        "priceTo" -> Json.fromBigDecimal(BigDecimal("40.00")),
        "durationMin" -> Json.fromInt(45),
        "location" -> Json.obj(
          "lat" -> Json.fromBigDecimal(BigDecimal("52.5200")),
          "lon" -> Json.fromBigDecimal(BigDecimal("13.4050")),
        ),
        "allText" -> Json.fromString("manicure nails beauty master central studio"),
        "serviceText" -> Json.fromString("manicure nails"),
        "attributeText" -> Json.fromString("nail service type manicure with removal true"),
        "providerText" -> Json.fromString("beauty master central studio"),
        "locationText" -> Json.fromString("central studio main street 1 nails"),
        "enumAttributes" -> Json.obj(
          "nail_service_type" -> Json.fromString("manicure"),
        ),
        "booleanAttributes" -> Json.obj(
          "with_removal" -> Json.True,
        ),
      ))
    }
  }
}
