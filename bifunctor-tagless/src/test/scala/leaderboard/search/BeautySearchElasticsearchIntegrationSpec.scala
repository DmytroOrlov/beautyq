package leaderboard.search

import distage.{DIKey, Mode}
import izumi.distage.model.definition.Activation
import leaderboard.{LeaderboardTest, ProdTest}
import leaderboard.config.ElasticsearchPortCfg
import leaderboard.model.QueryFailure
import leaderboard.repo.{Categories, MasterLocations, MasterServiceOfferVariants, MasterServiceOffers, Masters, ServiceVariantSchemas, Services}
import leaderboard.search.document.{BeautySearchCatalogSnapshotLoader, VariantSearchDocumentBuilder}
import leaderboard.search.BeautySearchEvalInventory
import leaderboard.search.dsl.BeautySearchSpecV1
import leaderboard.search.elasticsearch.{ElasticsearchIngestionInterpreter, ElasticsearchMappingInterpreter, ElasticsearchSearchRequestInterpreter, ElasticsearchSearchResponseInterpreter}
import io.circe.syntax.*
import leaderboard.search.eval.{BeautySearchEvalReportJson, BeautySearchEvalScorer, EngineEvalReportAssembly, EngineExpectedRole, EngineEvalReportJson}
import leaderboard.search.parser.BeautySearchIntentParser
import leaderboard.search.qdrant.{QdrantEmbeddingBenchmarkQueryResult, QdrantEmbeddingBenchmarkQuerySubset}
import leaderboard.seed.{BeautyQSeedLoader, BeautyQSeedReady}
import zio.{IO, ZIO}

import scala.annotation.unused
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
  private val evalSuite = BeautySearchEvalInventory.evalSuite

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
        seedReady: BeautyQSeedReady,
      ) =>
        withPreparedIndex(portCfg) {
          (client, testSpec) =>
            for {
              documents <- loadAndIndexDocuments(testSpec, client, categories, services, serviceVariantSchemas, masters, masterLocations, masterServiceOffers, masterServiceOfferVariants, seedReady)
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
        seedReady: BeautyQSeedReady,
      ) =>
        withPreparedIndex(portCfg) {
          (client, testSpec) =>
            for {
              _ <- loadAndIndexDocuments(testSpec, client, categories, services, serviceVariantSchemas, masters, masterLocations, masterServiceOffers, masterServiceOfferVariants, seedReady)
              _ <- ZIO.foreachDiscard(evalSuite.queries.filter(query => BeautySearchEvalInventory.firstMilestoneQueryIds.contains(query.id))) {
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
        seedReady: BeautyQSeedReady,
      ) =>
        withPreparedIndex(portCfg) {
          (client, testSpec) =>
            for {
              _ <- loadAndIndexDocuments(testSpec, client, categories, services, serviceVariantSchemas, masters, masterLocations, masterServiceOffers, masterServiceOfferVariants, seedReady)
              _ <- ZIO.foreachDiscard(evalSuite.queries.filter(query => BeautySearchEvalInventory.secondMilestoneQueryIds.contains(query.id))) {
                query =>
                  for {
                    debug <- executeSearchDebug(testSpec, client, query.query)
                    response = debug.response
                    report = BeautySearchEvalScorer.score(query, response)
                    _ <- BeautySearchEvalTestSupport.requireEvalOutcome(
                      query,
                      response,
                      report,
                      "eval",
                      Some(BeautySearchEvalTestSupport.EsDebug(debug.intent, debug.requestJson, debug.rawHitCount)),
                    )
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
        seedReady: BeautyQSeedReady,
      ) =>
        withPreparedIndex(portCfg) {
          (client, testSpec) =>
            for {
              _ <- loadAndIndexDocuments(testSpec, client, categories, services, serviceVariantSchemas, masters, masterLocations, masterServiceOffers, masterServiceOfferVariants, seedReady)
              _ <- ZIO.foreachDiscard(evalSuite.queries.filter(query => BeautySearchEvalInventory.hardNegativeQueryIds.contains(query.id))) {
                query =>
                  for {
                    response <- executeSearch(testSpec, client, query.query)
                    report = BeautySearchEvalScorer.score(query, response)
                    _ <- BeautySearchEvalTestSupport.requireEvalOutcome(query, response, report, "eval")
                  } yield ()
              }
            } yield ()
        }
    }

    "pass the brows/lashes eval subset" in {
      (
        portCfg: ElasticsearchPortCfg,
        categories: Categories[IO],
        services: Services[IO],
        serviceVariantSchemas: ServiceVariantSchemas[IO],
        masters: Masters[IO],
        masterLocations: MasterLocations[IO],
        masterServiceOffers: MasterServiceOffers[IO],
        masterServiceOfferVariants: MasterServiceOfferVariants[IO],
        seedReady: BeautyQSeedReady,
      ) =>
        withPreparedIndex(portCfg) {
          (client, testSpec) =>
            for {
              _ <- loadAndIndexDocuments(testSpec, client, categories, services, serviceVariantSchemas, masters, masterLocations, masterServiceOffers, masterServiceOfferVariants, seedReady)
              _ <- ZIO.foreachDiscard(evalSuite.queries.filter(query => BeautySearchEvalInventory.browsLashesQueryIds.contains(query.id))) {
                query =>
                  for {
                    debug <- executeSearchDebug(testSpec, client, query.query)
                    report = BeautySearchEvalScorer.score(query, debug.response)
                    _ <- BeautySearchEvalTestSupport.requireEvalOutcome(
                      query,
                      debug.response,
                      report,
                      "eval",
                      Some(BeautySearchEvalTestSupport.EsDebug(debug.intent, debug.requestJson, debug.rawHitCount)),
                    )
                  } yield ()
              }
            } yield ()
        }
    }

    "pass the PMU eval subset" in {
      (
        portCfg: ElasticsearchPortCfg,
        categories: Categories[IO],
        services: Services[IO],
        serviceVariantSchemas: ServiceVariantSchemas[IO],
        masters: Masters[IO],
        masterLocations: MasterLocations[IO],
        masterServiceOffers: MasterServiceOffers[IO],
        masterServiceOfferVariants: MasterServiceOfferVariants[IO],
        seedReady: BeautyQSeedReady,
      ) =>
        withPreparedIndex(portCfg) {
          (client, testSpec) =>
            for {
              _ <- loadAndIndexDocuments(testSpec, client, categories, services, serviceVariantSchemas, masters, masterLocations, masterServiceOffers, masterServiceOfferVariants, seedReady)
              _ <- ZIO.foreachDiscard(evalSuite.queries.filter(query => BeautySearchEvalInventory.pmuQueryIds.contains(query.id))) {
                query =>
                  for {
                    debug <- executeSearchDebug(testSpec, client, query.query)
                    report = BeautySearchEvalScorer.score(query, debug.response)
                    _ <- BeautySearchEvalTestSupport.requireEvalOutcome(
                      query,
                      debug.response,
                      report,
                      "eval",
                      Some(BeautySearchEvalTestSupport.EsDebug(debug.intent, debug.requestJson, debug.rawHitCount)),
                    )
                  } yield ()
              }
            } yield ()
        }
    }

    "pass the face eval subset" in {
      (
        portCfg: ElasticsearchPortCfg,
        categories: Categories[IO],
        services: Services[IO],
        serviceVariantSchemas: ServiceVariantSchemas[IO],
        masters: Masters[IO],
        masterLocations: MasterLocations[IO],
        masterServiceOffers: MasterServiceOffers[IO],
        masterServiceOfferVariants: MasterServiceOfferVariants[IO],
        seedReady: BeautyQSeedReady,
      ) =>
        withPreparedIndex(portCfg) {
          (client, testSpec) =>
            for {
              _ <- loadAndIndexDocuments(testSpec, client, categories, services, serviceVariantSchemas, masters, masterLocations, masterServiceOffers, masterServiceOfferVariants, seedReady)
              _ <- ZIO.foreachDiscard(evalSuite.queries.filter(query => BeautySearchEvalInventory.faceQueryIds.contains(query.id))) {
                query =>
                  for {
                    debug <- executeSearchDebug(testSpec, client, query.query)
                    report = BeautySearchEvalScorer.score(query, debug.response)
                    _ <- BeautySearchEvalTestSupport.requireEvalOutcome(
                      query,
                      debug.response,
                      report,
                      "eval",
                      Some(BeautySearchEvalTestSupport.EsDebug(debug.intent, debug.requestJson, debug.rawHitCount)),
                    )
                  } yield ()
              }
            } yield ()
        }
    }

    "pass the nails eval subset" in {
      (
        portCfg: ElasticsearchPortCfg,
        categories: Categories[IO],
        services: Services[IO],
        serviceVariantSchemas: ServiceVariantSchemas[IO],
        masters: Masters[IO],
        masterLocations: MasterLocations[IO],
        masterServiceOffers: MasterServiceOffers[IO],
        masterServiceOfferVariants: MasterServiceOfferVariants[IO],
        seedReady: BeautyQSeedReady,
      ) =>
        withPreparedIndex(portCfg) {
          (client, testSpec) =>
            for {
              _ <- loadAndIndexDocuments(testSpec, client, categories, services, serviceVariantSchemas, masters, masterLocations, masterServiceOffers, masterServiceOfferVariants, seedReady)
              _ <- ZIO.foreachDiscard(evalSuite.queries.filter(query => BeautySearchEvalInventory.nailsQueryIds.contains(query.id))) {
                query =>
                  for {
                    debug <- executeSearchDebug(testSpec, client, query.query)
                    report = BeautySearchEvalScorer.score(query, debug.response)
                    _ <- BeautySearchEvalTestSupport.requireEvalOutcome(
                      query,
                      debug.response,
                      report,
                      "eval",
                      Some(BeautySearchEvalTestSupport.EsDebug(debug.intent, debug.requestJson, debug.rawHitCount)),
                    )
                  } yield ()
              }
            } yield ()
        }
    }

    "pass the remaining hair-removal eval subset" in {
      (
        portCfg: ElasticsearchPortCfg,
        categories: Categories[IO],
        services: Services[IO],
        serviceVariantSchemas: ServiceVariantSchemas[IO],
        masters: Masters[IO],
        masterLocations: MasterLocations[IO],
        masterServiceOffers: MasterServiceOffers[IO],
        masterServiceOfferVariants: MasterServiceOfferVariants[IO],
        seedReady: BeautyQSeedReady,
      ) =>
        withPreparedIndex(portCfg) {
          (client, testSpec) =>
            for {
              _ <- loadAndIndexDocuments(testSpec, client, categories, services, serviceVariantSchemas, masters, masterLocations, masterServiceOffers, masterServiceOfferVariants, seedReady)
              _ <- ZIO.foreachDiscard(evalSuite.queries.filter(query => BeautySearchEvalInventory.hairRemainingQueryIds.contains(query.id))) {
                query =>
                  for {
                    debug <- executeSearchDebug(testSpec, client, query.query)
                    report = BeautySearchEvalScorer.score(query, debug.response)
                    _ <- BeautySearchEvalTestSupport.requireEvalOutcome(
                      query,
                      debug.response,
                      report,
                      "eval",
                      Some(BeautySearchEvalTestSupport.EsDebug(debug.intent, debug.requestJson, debug.rawHitCount)),
                    )
                  } yield ()
              }
            } yield ()
        }
    }

    "pass the home-visit eval subset" in {
      (
        portCfg: ElasticsearchPortCfg,
        categories: Categories[IO],
        services: Services[IO],
        serviceVariantSchemas: ServiceVariantSchemas[IO],
        masters: Masters[IO],
        masterLocations: MasterLocations[IO],
        masterServiceOffers: MasterServiceOffers[IO],
        masterServiceOfferVariants: MasterServiceOfferVariants[IO],
        seedReady: BeautyQSeedReady,
      ) =>
        withPreparedIndex(portCfg) {
          (client, testSpec) =>
            for {
              _ <- loadAndIndexDocuments(testSpec, client, categories, services, serviceVariantSchemas, masters, masterLocations, masterServiceOffers, masterServiceOfferVariants, seedReady)
              _ <- ZIO.foreachDiscard(evalSuite.queries.filter(query => BeautySearchEvalInventory.homeVisitQueryIds.contains(query.id))) {
                query =>
                  for {
                    debug <- executeSearchDebug(testSpec, client, query.query)
                    report = BeautySearchEvalScorer.score(query, debug.response)
                    _ <- BeautySearchEvalTestSupport.requireEvalOutcome(
                      query,
                      debug.response,
                      report,
                      "eval",
                      Some(BeautySearchEvalTestSupport.EsDebug(debug.intent, debug.requestJson, debug.rawHitCount)),
                    )
                  } yield ()
              }
            } yield ()
        }
    }

    "pass the lexical remainder eval subset" in {
      (
        portCfg: ElasticsearchPortCfg,
        categories: Categories[IO],
        services: Services[IO],
        serviceVariantSchemas: ServiceVariantSchemas[IO],
        masters: Masters[IO],
        masterLocations: MasterLocations[IO],
        masterServiceOffers: MasterServiceOffers[IO],
        masterServiceOfferVariants: MasterServiceOfferVariants[IO],
        seedReady: BeautyQSeedReady,
      ) =>
        withPreparedIndex(portCfg) {
          (client, testSpec) =>
            for {
              _ <- loadAndIndexDocuments(testSpec, client, categories, services, serviceVariantSchemas, masters, masterLocations, masterServiceOffers, masterServiceOfferVariants, seedReady)
              _ <- ZIO.foreachDiscard(evalSuite.queries.filter(query => BeautySearchEvalInventory.lexicalRemainderQueryIds.contains(query.id))) {
                query =>
                  for {
                    debug <- executeSearchDebug(testSpec, client, query.query)
                    report = BeautySearchEvalScorer.score(query, debug.response)
                    _ <- BeautySearchEvalTestSupport.requireEvalOutcome(
                      query,
                      debug.response,
                      report,
                      "eval",
                      Some(BeautySearchEvalTestSupport.EsDebug(debug.intent, debug.requestJson, debug.rawHitCount)),
                    )
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
        seedReady: BeautyQSeedReady,
      ) =>
        withPreparedIndex(portCfg) {
          (client, testSpec) =>
            for {
              _ <- loadAndIndexDocuments(testSpec, client, categories, services, serviceVariantSchemas, masters, masterLocations, masterServiceOffers, masterServiceOfferVariants, seedReady)
              response <- executeSearch(testSpec, client, "маникюр гель лак")
              _ <- assertIO(response.variantCarousel.nonEmpty)
              _ <- assertIO(response.providerCarousel.nonEmpty)
              _ <- assertIO(response.serviceIntentCarousel.nonEmpty)
            } yield ()
        }
    }

    "produce ES eval reports consumable by EngineEval assembly" in {
      (
        portCfg: ElasticsearchPortCfg,
        categories: Categories[IO],
        services: Services[IO],
        serviceVariantSchemas: ServiceVariantSchemas[IO],
        masters: Masters[IO],
        masterLocations: MasterLocations[IO],
        masterServiceOffers: MasterServiceOffers[IO],
        masterServiceOfferVariants: MasterServiceOfferVariants[IO],
        seedReady: BeautyQSeedReady,
      ) =>
        withPreparedIndex(portCfg) {
          (client, testSpec) =>
            for {
              _ <- loadAndIndexDocuments(testSpec, client, categories, services, serviceVariantSchemas, masters, masterLocations, masterServiceOffers, masterServiceOfferVariants, seedReady)
              queries = evalSuite.queries.filter(query => BeautySearchEvalInventory.firstMilestoneQueryIds.contains(query.id)).take(2)
              esReports <- executeEvalReports(testSpec, client, queries)
              syntheticQdrantResults = queries.map { query =>
                QdrantEmbeddingBenchmarkQueryResult(
                  candidateId = "synthetic-qdrant",
                  queryId = query.id,
                  queryText = query.query,
                  topVariantIds = query.expectedVariantCarousel.acceptableVariantIds.take(1),
                  topProviderIds = Nil,
                  topServiceIds = Nil,
                  scores = Nil,
                )
              }
              roles = queries.map(query => query.id -> EngineExpectedRole.EsShouldHandle).toMap
              result = EngineEvalReportAssembly.fromOutputs(queries, roles, esReports, syntheticQdrantResults)
              report <- ZIO.fromEither(result)
              _ <- assertIO(report.queryReports.map(_.queryId) == queries.map(_.id))
              _ <- assertIO(report.aggregate.queryCount == queries.size)
              _ <- assertIO(report.queryReports.map(_.es.queryId) == queries.map(_.id))
            } yield ()
        }
    }

    "produce ES eval reports for semantic broad smoke subset" in {
      (
        portCfg: ElasticsearchPortCfg,
        categories: Categories[IO],
        services: Services[IO],
        serviceVariantSchemas: ServiceVariantSchemas[IO],
        masters: Masters[IO],
        masterLocations: MasterLocations[IO],
        masterServiceOffers: MasterServiceOffers[IO],
        masterServiceOfferVariants: MasterServiceOfferVariants[IO],
        seedReady: BeautyQSeedReady,
      ) =>
      val semanticBroadSmokeRoles = Map(
           "q_broad_001" -> EngineExpectedRole.EsShouldHandle,
           "q_broad_002" -> EngineExpectedRole.EsShouldHandle,
           "q_broad_003" -> EngineExpectedRole.QdrantMayComplement,
           "q_broad_004" -> EngineExpectedRole.HybridMayImprove,
           "q_broad_005" -> EngineExpectedRole.EsShouldHandle,
           "q_broad_006" -> EngineExpectedRole.QdrantMayComplement,
         )
         val expectedQueryIds = List(
           "q_broad_001",
           "q_broad_002",
           "q_broad_003",
           "q_broad_004",
           "q_broad_005",
           "q_broad_006",
         )
         val expectedRoleSequence = List(
           EngineExpectedRole.EsShouldHandle,
           EngineExpectedRole.EsShouldHandle,
           EngineExpectedRole.QdrantMayComplement,
           EngineExpectedRole.HybridMayImprove,
           EngineExpectedRole.EsShouldHandle,
           EngineExpectedRole.QdrantMayComplement,
         )
         withPreparedIndex(portCfg) {
          (client, testSpec) =>
            for {
              _ <- loadAndIndexDocuments(testSpec, client, categories, services, serviceVariantSchemas, masters, masterLocations, masterServiceOffers, masterServiceOfferVariants, seedReady)
              selectedQueries <- ZIO.fromEither(
                QdrantEmbeddingBenchmarkQuerySubset.select(
                  QdrantEmbeddingBenchmarkQuerySubset.SemanticBroadSmoke,
                  evalSuite.queries,
                )
              )
              _ <- assertCond(selectedQueries.nonEmpty, "Expected non-empty selected queries")
              esReports <- executeEvalReports(testSpec, client, selectedQueries)
              selectedQueryIds = selectedQueries.map(_.id)
              _ <- assertCond(esReports.map(_.queryId) == selectedQueryIds, "ES report query ids mismatch")
              safeLookup = { (queryId: String) =>
                semanticBroadSmokeRoles.get(queryId).toRight(QueryFailure.operation("assert", s"Missing expected role for query id: $queryId"))
              }
              expectedRolePairs <- ZIO.foreach(selectedQueries) { query =>
                ZIO.fromEither(safeLookup(query.id)).map(role => query.id -> role)
              }
              expectedRoles = expectedRolePairs.toMap
              expectedRolesList = expectedRolePairs.map(_._2)
              _ <- assertCond(
                selectedQueryIds == expectedQueryIds,
                "Selected query ids do not match expected sequence",
              )
              _ <- assertCond(
                expectedRolesList == expectedRoleSequence,
                "Expected role sequence mismatch",
              )
              _ <- assertCond(
                expectedRoles.keySet == selectedQueryIds.toSet,
                "Expected roles key set mismatch",
              )
              printArtifacts = sys.env.get("ENGINE_EVAL_PRINT_ES_ARTIFACTS").exists { value =>
                val normalized = value.trim.toLowerCase
                normalized == "1" || normalized == "true" || normalized == "yes"
              }
              _ <- ZIO.when(printArtifacts) {
                ZIO.succeed {
                  println("BEGIN_ENGINE_EVAL_ES_REPORTS_JSON")
                  println(BeautySearchEvalReportJson.encodeReportsString(esReports))
                  println("END_ENGINE_EVAL_ES_REPORTS_JSON")
                  println("BEGIN_ENGINE_EVAL_EXPECTED_ROLES_JSON")
                  println(expectedRoles.asJson(
                    io.circe.Encoder.encodeMap[String, EngineExpectedRole](io.circe.KeyEncoder.encodeKeyString, EngineEvalReportJson.expectedRoleEncoder)
                  ).noSpaces)
                  println("END_ENGINE_EVAL_EXPECTED_ROLES_JSON")
                }
              }
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
    @unused seedReady: BeautyQSeedReady,
  ): IO[QueryFailure, List[leaderboard.search.document.VariantSearchDocument]] = {
    val loader = new BeautySearchCatalogSnapshotLoader.SeedScopedFromRepositories[IO](
      seedReady,
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

  private def executeEvalReport(
    spec: leaderboard.search.dsl.BeautySearchSpec,
    client: ElasticsearchTestClient,
    query: leaderboard.search.eval.BeautySearchEvalQuery,
  ): IO[QueryFailure, leaderboard.search.eval.BeautySearchEvalReport] =
    executeSearch(spec, client, query.query).map(response => BeautySearchEvalScorer.score(query, response))

  private def executeEvalReports(
    spec: leaderboard.search.dsl.BeautySearchSpec,
    client: ElasticsearchTestClient,
    queries: List[leaderboard.search.eval.BeautySearchEvalQuery],
  ): IO[QueryFailure, List[leaderboard.search.eval.BeautySearchEvalReport]] =
    ZIO.foreach(queries)(query => executeEvalReport(spec, client, query))

  private def assertCond(condition: Boolean, msg: => String): IO[QueryFailure, Unit] =
    if (condition) ZIO.unit
    else ZIO.fail(QueryFailure.operation("assert", msg))

}
