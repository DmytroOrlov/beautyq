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

  private val secondMilestoneQueryIds = Set(
    "q_nails_007",
    "q_nails_012",
    "q_lashes_004",
    "q_hair_002",
    "q_hair_003",
    "q_hair_004",
    "q_hair_006",
    "q_hair_007",
  )

  private val hardNegativeQueryIds = Set(
    "q_nails_011",
    "q_lashes_007",
    "q_noise_003",
    "q_noise_004",
    "q_noise_005",
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

    "pass the second milestone eval subset" in {
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
              _ <- ZIO.foreachDiscard(evalSuite.queries.filter(query => secondMilestoneQueryIds.contains(query.id))) {
                query =>
                  for {
                    debug <- executeSearchDebug(testSpec, client, query.query)
                    response = debug.response
                    report = BeautySearchEvalScorer.score(query, response)
                    _ <- requireCondition(
                      response.variantCarousel.take(3).exists(result => query.expectedVariantCarousel.acceptableVariantIds.contains(result.variantId)),
                      query,
                      response,
                      report,
                      "variant top-3",
                      Some(debug),
                    )
                    _ <- requireCondition(
                      response.providerCarousel.take(5).exists(result => query.expectedProviderCarousel.acceptableProviderLocationIds.contains(result.masterLocationId)),
                      query,
                      response,
                      report,
                      "provider top-5",
                      Some(debug),
                    )
                    _ <- requireCondition(
                      response.serviceIntentCarousel.take(3).exists(result => query.expectedServiceIntentCarousel.acceptableServiceIds.contains(result.serviceId)),
                      query,
                      response,
                      report,
                      "service top-3",
                      Some(debug),
                    )
                    _ <- requireCondition(report.failedAssertions.isEmpty, query, response, report, "scorer", Some(debug))
                  } yield ()
              }
            } yield ()
        }
    }

    "pass the hard-negative eval subset" in {
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
              _ <- ZIO.foreachDiscard(evalSuite.queries.filter(query => hardNegativeQueryIds.contains(query.id))) {
                query =>
                  for {
                    response <- executeSearch(testSpec, client, query.query)
                    report = BeautySearchEvalScorer.score(query, response)
                    _ <- requireCondition(
                      response.variantCarousel.take(3).exists(result => query.expectedVariantCarousel.acceptableVariantIds.contains(result.variantId)),
                      query,
                      response,
                      report,
                      "variant top-3",
                    )
                    _ <- requireCondition(
                      response.providerCarousel.take(5).exists(result => query.expectedProviderCarousel.acceptableProviderLocationIds.contains(result.masterLocationId)),
                      query,
                      response,
                      report,
                      "provider top-5",
                    )
                    _ <- requireCondition(
                      response.serviceIntentCarousel.take(3).exists(result => query.expectedServiceIntentCarousel.acceptableServiceIds.contains(result.serviceId)),
                      query,
                      response,
                      report,
                      "service top-3",
                    )
                    _ <- requireCondition(report.failedAssertions.isEmpty, query, response, report, "scorer")
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

  private final case class SearchDebug(
    intent: ParsedSearchIntent,
    requestJson: io.circe.Json,
    rawHitCount: Option[Long],
    response: BeautySearchResponse,
  )

  private def executeSearchDebug(
    spec: leaderboard.search.dsl.BeautySearchSpec,
    client: ElasticsearchTestClient,
    query: String,
  ): IO[QueryFailure, SearchDebug] = {
    val input = UserSearchInput(query, Some(evalSuite.testUserLocation.lat), Some(evalSuite.testUserLocation.lon))
    val parser = new BeautySearchIntentParser(spec)
    val intent = parser.parse(input)
    for {
      requestJson <- ZIO.fromEither(ElasticsearchSearchRequestInterpreter.request(spec, input, intent))
      rawResponse <- client.postJson(s"/${spec.variantDocument.indexName}/_search", requestJson)
      interpreted <- ZIO.fromEither(ElasticsearchSearchResponseInterpreter.interpret(spec, input, intent, rawResponse))
      rawHits = rawResponse.hcursor.downField("hits").downField("total").as[Long].toOption.orElse(rawResponse.hcursor.downField("hits").downField("total").downField("value").as[Long].toOption)
    } yield SearchDebug(intent, requestJson, rawHits, interpreted)
  }

  private def debugSuffix(debug: Option[SearchDebug]): String =
    debug.fold("")(d =>
      s"intent=${d.intent} explicitConstraints=${d.intent.explicitConstraints} softBoosts=${d.intent.softBoosts} remainingText=${d.intent.remainingText} rawHitCount=${d.rawHitCount} request=${d.requestJson.noSpaces} "
    )

  private def diagnosticMessage(
    query: leaderboard.search.eval.BeautySearchEvalQuery,
    response: BeautySearchResponse,
    report: leaderboard.search.eval.BeautySearchEvalReport,
    check: String,
    debug: Option[SearchDebug] = None,
  ): String =
    s"check=$check queryId=${query.id} query=${query.query} " +
      s"topVariantIds=${response.variantCarousel.take(3).map(_.variantId).mkString("[", ",", "]")} " +
      s"topProviderLocationIds=${response.providerCarousel.take(5).map(_.masterLocationId).mkString("[", ",", "]")} " +
      s"topServiceIds=${response.serviceIntentCarousel.take(3).map(_.serviceId).mkString("[", ",", "]")} " +
      s"failedAssertions=${report.failedAssertions.mkString("[", ",", "]")} " +
      debugSuffix(debug)

  private def requireCondition(
    condition: Boolean,
    query: leaderboard.search.eval.BeautySearchEvalQuery,
    response: BeautySearchResponse,
    report: leaderboard.search.eval.BeautySearchEvalReport,
    check: String,
    debug: Option[SearchDebug] = None,
  ): IO[QueryFailure, Unit] =
    if condition then ZIO.unit
    else ZIO.fail(QueryFailure.operation("beautyq-search-eval", diagnosticMessage(query, response, report, check, debug)))
}
