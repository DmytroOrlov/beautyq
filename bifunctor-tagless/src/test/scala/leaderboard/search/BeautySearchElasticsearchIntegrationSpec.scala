package leaderboard.search

import distage.{DIKey, Mode}
import izumi.distage.model.definition.Activation
import leaderboard.{LeaderboardTest, ProdTest}
import leaderboard.config.ElasticsearchPortCfg
import leaderboard.model.QueryFailure
import leaderboard.repo.{Categories, MasterLocations, MasterServiceOfferVariants, MasterServiceOffers, Masters, ServiceVariantSchemas, Services}
import leaderboard.search.document.{BeautySearchCatalogSnapshotLoader, VariantSearchDocumentBuilder}
import leaderboard.search.dsl.BeautySearchSpecV1
import leaderboard.search.elasticsearch.{ElasticsearchIngestionInterpreter, ElasticsearchMappingInterpreter, ElasticsearchSearchRequestInterpreter, ElasticsearchSearchResponseInterpreter}
import leaderboard.search.eval.{BeautySearchEvalLoader, BeautySearchEvalScorer}
import leaderboard.search.parser.BeautySearchIntentParser
import leaderboard.seed.BeautyQSeedLoader
import zio.{IO, ZIO}

import java.nio.file.Paths
import java.util.UUID

final class BeautySearchElasticsearchIntegrationSpec extends LeaderboardTest with ProdTest {
  override def config = super.config.copy(
    activation = super.config.activation ++ Activation(Mode -> Mode.Test),
    memoizationRoots = super.config.memoizationRoots + DIKey[ElasticsearchPortCfg],
  )

  private val spec = BeautySearchSpecV1.spec
  private val seed = new BeautyQSeedLoader.ResourceLoader().load() match {
    case Right(value) => value
    case Left(error) => throw new RuntimeException(error.message)
  }
  private val evalSuite = BeautySearchEvalLoader.load(Paths.get("beautyq_search_eval_queries_v1.json")) match {
    case Right(value) => value
    case Left(error) => throw new RuntimeException(error.message)
  }
  private val firstMilestoneQueryIds = Set(
    "q_nails_001",
    "q_nails_006",
    "q_nails_009",
    "q_lashes_001",
    "q_lashes_002",
    "q_brows_005",
    "q_pmu_001",
    "q_pmu_005",
    "q_face_001",
    "q_face_002",
    "q_face_004",
    "q_face_008",
  )

  "BeautySearch Elasticsearch integration" should {
    "create the index mapping" in {
      (
        portCfg: ElasticsearchPortCfg,
      ) =>
        withPreparedIndex(portCfg) {
          (client, testSpec) =>
            client.getJson(s"/${testSpec.variantDocument.indexName}").flatMap {
              json =>
                assertIO(json.hcursor.downField(testSpec.variantDocument.indexName).downField("mappings").focus.nonEmpty)
            }
        }
    }

    "ingest exactly one document per seeded variant" in {
      (
        portCfg: ElasticsearchPortCfg,
        categories: Categories[IO],
        services: Services[IO],
        serviceVariantSchemas: ServiceVariantSchemas[IO],
        masters: Masters[IO],
        masterLocations: MasterLocations[IO],
        masterServiceOffers: MasterServiceOffers[IO],
        masterServiceOfferVariants: MasterServiceOfferVariants[IO],
      ) =>
        withPreparedIndex(portCfg) {
          (client, testSpec) =>
            for {
              documents <- loadAndIndexDocuments(testSpec, client, categories, services, serviceVariantSchemas, masters, masterLocations, masterServiceOffers, masterServiceOfferVariants)
              countJson <- client.getJson(s"/${testSpec.variantDocument.indexName}/_count")
              count <- ZIO.fromEither(countJson.hcursor.get[Long]("count").left.map(error => QueryFailure.operation("decode-es-count", error.getMessage)))
              _ <- assertIO(count == documents.size.toLong)
            } yield ()
        }
    }

    "pass the first milestone eval subset" in {
      (
        portCfg: ElasticsearchPortCfg,
        categories: Categories[IO],
        services: Services[IO],
        serviceVariantSchemas: ServiceVariantSchemas[IO],
        masters: Masters[IO],
        masterLocations: MasterLocations[IO],
        masterServiceOffers: MasterServiceOffers[IO],
        masterServiceOfferVariants: MasterServiceOfferVariants[IO],
      ) =>
        withPreparedIndex(portCfg) {
          (client, testSpec) =>
            for {
              _ <- loadAndIndexDocuments(testSpec, client, categories, services, serviceVariantSchemas, masters, masterLocations, masterServiceOffers, masterServiceOfferVariants)
              _ <- ZIO.foreachDiscard(evalSuite.queries.filter(query => firstMilestoneQueryIds.contains(query.id))) {
                query =>
                  for {
                    response <- executeSearch(testSpec, client, query.query)
                    report = BeautySearchEvalScorer.score(query, response)
                    _ <- assertIO(response.variantCarousel.take(3).exists(result => query.expectedVariantCarousel.acceptableVariantIds.contains(result.variantId)))
                    _ <- assertIO(response.providerCarousel.take(5).exists(result => query.expectedProviderCarousel.acceptableProviderLocationIds.contains(result.masterLocationId)))
                    _ <- assertIO(response.serviceIntentCarousel.take(3).exists(result => query.expectedServiceIntentCarousel.acceptableServiceIds.contains(result.serviceId)))
                    _ <- assertIO(report.failedAssertions.isEmpty)
                  } yield ()
              }
            } yield ()
        }
    }

    "return exactly the three UI carousels in the interpreted response" in {
      (
        portCfg: ElasticsearchPortCfg,
        categories: Categories[IO],
        services: Services[IO],
        serviceVariantSchemas: ServiceVariantSchemas[IO],
        masters: Masters[IO],
        masterLocations: MasterLocations[IO],
        masterServiceOffers: MasterServiceOffers[IO],
        masterServiceOfferVariants: MasterServiceOfferVariants[IO],
      ) =>
        withPreparedIndex(portCfg) {
          (client, testSpec) =>
            for {
              _ <- loadAndIndexDocuments(testSpec, client, categories, services, serviceVariantSchemas, masters, masterLocations, masterServiceOffers, masterServiceOfferVariants)
              response <- executeSearch(testSpec, client, "маникюр гель лак")
              _ <- assertIO(response.variantCarousel.nonEmpty)
              _ <- assertIO(response.providerCarousel.nonEmpty)
              _ <- assertIO(response.serviceIntentCarousel.nonEmpty)
            } yield ()
        }
    }
  }

  private def withPreparedIndex[A](
    portCfg: ElasticsearchPortCfg,
  )(use: (ElasticsearchTestClient, leaderboard.search.dsl.BeautySearchSpec) => IO[QueryFailure, A]
  ): IO[QueryFailure, A] = {
    val client = new ElasticsearchTestClient(portCfg.host, portCfg.port)
    val indexName = s"${spec.variantDocument.indexName}_${UUID.randomUUID().toString.replace('-', '_')}"
    val testSpec = spec.copy(variantDocument = spec.variantDocument.copy(indexName = indexName))
    for {
      _ <- client.putJson(s"/${testSpec.variantDocument.indexName}", ElasticsearchMappingInterpreter.mapping(testSpec))
      result <- use(client, testSpec).ensuring(client.deleteIndex(testSpec.variantDocument.indexName).either.unit)
    } yield result
  }

  private def loadAndIndexDocuments(
    spec: leaderboard.search.dsl.BeautySearchSpec,
    client: ElasticsearchTestClient,
    categories: Categories[IO],
    services: Services[IO],
    serviceVariantSchemas: ServiceVariantSchemas[IO],
    masters: Masters[IO],
    masterLocations: MasterLocations[IO],
    masterServiceOffers: MasterServiceOffers[IO],
    masterServiceOfferVariants: MasterServiceOfferVariants[IO],
  ): IO[QueryFailure, List[leaderboard.search.document.VariantSearchDocument]] = {
    val loader = new BeautySearchCatalogSnapshotLoader.SeedScopedFromRepositories[IO](
      seed,
      categories,
      services,
      serviceVariantSchemas,
      masters,
      masterLocations,
      masterServiceOffers,
      masterServiceOfferVariants,
    )

    for {
      snapshot <- loader.load()
      documents <- ZIO.fromEither(VariantSearchDocumentBuilder.build(snapshot))
      payload = ElasticsearchIngestionInterpreter.bulkPayload(spec, documents)
      _ <- client.postNdjson(s"/${spec.variantDocument.indexName}/_bulk", payload)
      _ <- client.post(s"/${spec.variantDocument.indexName}/_refresh")
    } yield documents
  }

  private def executeSearch(
    spec: leaderboard.search.dsl.BeautySearchSpec,
    client: ElasticsearchTestClient,
    query: String,
  ): IO[QueryFailure, BeautySearchResponse] = {
    val input = UserSearchInput(query, Some(evalSuite.testUserLocation.lat), Some(evalSuite.testUserLocation.lon))
    val parser = new BeautySearchIntentParser(spec)
    val intent = parser.parse(input)
    for {
      requestJson <- ZIO.fromEither(ElasticsearchSearchRequestInterpreter.request(spec, input, intent))
      rawResponse <- client.postJson(s"/${spec.variantDocument.indexName}/_search", requestJson)
      interpreted <- ZIO.fromEither(ElasticsearchSearchResponseInterpreter.interpret(spec, input, intent, rawResponse))
    } yield interpreted
  }
}
