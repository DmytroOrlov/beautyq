package leaderboard.search

import distage.{DIKey, Mode}
import izumi.distage.model.definition.Activation
import leaderboard.{LeaderboardTest, ProdTest}
import leaderboard.config.ElasticsearchPortCfg
import leaderboard.model.{MasterId, MasterLocationId, MasterServiceOfferId, MasterServiceOfferVariantId, QueryFailure, ServiceId}
import leaderboard.model.Category.CategoryId
import leaderboard.search.document.VariantSearchDocument
import leaderboard.search.dsl.{BeautySearchSpecV1, SearchGeoPoint}
import leaderboard.search.elasticsearch.BeautyQElasticsearchInterpreterAdapter
import leaderboard.search.eval.*
import leaderboard.search.lexical.{LexicalDocumentBackend, LexicalDocumentHit}
import zio.{IO, Runtime, Unsafe, ZIO}

import java.util.UUID

/**
 * M18A2: proves the ES leg of [[M18DualEngineOfflineEvalRunner]] executes against a real,
 * repo-local Elasticsearch instance (the same docker-managed ES used by
 * [[BeautySearchElasticsearchIntegrationSpec]]), not a scripted fake.
 *
 * The Qdrant leg is left honestly resource-gated: this spec does not wire a real embedding client
 * or Qdrant client, so it must not emit any Qdrant rows. Only the ES leg is asserted as executed.
 */
final class M18DualEngineOfflineEvalEsRealLegSpec extends LeaderboardTest with ProdTest {
  override def config = super.config.copy(
    activation = super.config.activation ++ Activation(Mode -> Mode.Test),
    memoizationRoots = super.config.memoizationRoots + DIKey[ElasticsearchPortCfg],
  )

  private val spec = BeautySearchSpecV1.spec

  private val variantId: MasterServiceOfferVariantId = MasterServiceOfferVariantId(UUID.randomUUID())
  private val otherVariantId: MasterServiceOfferVariantId = MasterServiceOfferVariantId(UUID.randomUUID())

  "M18 dual-engine offline eval ES leg against real Elasticsearch" should {
    "execute the ES leg via ElasticsearchSearchBackend's documentHits path and emit non-empty real candidate rows, while Qdrant stays honestly resource-gated" in {
      (
        portCfg: ElasticsearchPortCfg,
      ) =>
        withPreparedIndex(portCfg) {
          (client, testSpec) =>
            for {
              _ <- indexSyntheticDocuments(testSpec, client)
              result = unsafeRun(runM18(testSpec, client))
              _ <- ZIO.succeed {
                assert(result.esExecuted, s"expected ES leg to execute, got ${result.es}")
                assert(result.esCandidateRows.nonEmpty, "expected non-empty real ES candidate rows")
                assert(result.esCandidateRows.forall(_.backend == M18OfflineEvalBackend.Es))
                assert(result.esCandidateRows.exists(_.candidateId == variantId.toString))

                assert(!result.qdrantExecuted, "Qdrant leg must not execute: no real Qdrant resource is wired in this spec")
                assert(result.qdrantCandidateRows.isEmpty, "no fake Qdrant rows may be emitted")
                result.qdrant match {
                  case skipped: M18BackendLegOutcome.Skipped =>
                    assert(skipped.skipKind == M18LegSkipKind.ResourceGated)
                    assert(skipped.missingPrerequisites.nonEmpty)
                  case other =>
                    fail(s"expected resource-gated Qdrant leg, got $other")
                }

                assert(result.separationViolations.isEmpty)
              }
            } yield ()
        }
    }
  }

  private def runM18(
    testSpec: leaderboard.search.dsl.BeautySearchSpec,
    client: ElasticsearchTestClient,
  ): IO[Nothing, M18DualEngineOfflineEvalResult] = {
    val esBackend = new LexicalDocumentBackend[IO, MasterServiceOfferVariantId] {
      override def documentHits(
        input: UserSearchInput,
        intent: ParsedSearchIntent,
      ): IO[QueryFailure, List[LexicalDocumentHit[MasterServiceOfferVariantId]]] =
        for {
          requestJson <- ZIO.fromEither(BeautyQElasticsearchInterpreterAdapter.request(testSpec, input, intent))
          rawResponse <- client.postJson(s"/${testSpec.variantDocument.indexName}/_search", requestJson)
          hits <- ZIO.fromEither(BeautyQElasticsearchInterpreterAdapter.documentHits(rawResponse))
        } yield hits
    }

    val qdrantLeg = M18QdrantLegInput.fromPrerequisites[IO, MasterServiceOfferVariantId](
      M18QdrantLegPrerequisites(
        realBackendOfflineEvalEnabled = false,
        embeddingClientConfigured = false,
        qdrantClientConfigured = false,
        collectionReadinessConfigured = false,
      )
    ) {
      sys.error("must not connect Qdrant: this spec proves the ES leg only")
    }

    val runner = new M18DualEngineOfflineEvalRunner[IO, MasterServiceOfferVariantId](
      inputBuilder = M18EvalQueryInputBuilder.default,
      renderId = _.toString,
      clock = None,
    )

    runner.run(
      dataset = dataset,
      esLeg = M18EsLegInput.Connected(esBackend, lookup = None),
      qdrantLeg = qdrantLeg,
    )
  }

  private val dataset: M9OfflineEvalDataset =
    M9OfflineEvalDataset(
      evalDatasetId = EvalDatasetId("m18a2-real-es-leg-dataset"),
      catalogSnapshotId = CatalogSnapshotId("m18a2-real-es-leg-snapshot"),
      queries = List(
        M9OfflineEvalDatasetQuery(
          queryId = "q1",
          rawQueryText = "balayage haircut",
          normalizedQueryText = Some("balayage haircut"),
          queryClass = QueryClass.ExactProductNameBrand,
          filters = Nil,
          categories = Nil,
          expectedResults = List(M9OfflineEvalExpectedResult(variantId.toString, None)),
          expectedNotes = Nil,
          negativeOutOfCatalog = false,
        )
      ),
    )

  private def withPreparedIndex[A](
    portCfg: ElasticsearchPortCfg,
  )(use: (ElasticsearchTestClient, leaderboard.search.dsl.BeautySearchSpec) => IO[QueryFailure, A]
  ): IO[QueryFailure, A] = {
    val client = new ElasticsearchTestClient(portCfg.host, portCfg.port)
    val indexName = s"${spec.variantDocument.indexName}_m18a2_${UUID.randomUUID().toString.replace('-', '_')}"
    val testSpec = spec.copy(variantDocument = spec.variantDocument.copy(indexName = indexName))
    for {
      _ <- client.putJson(s"/${testSpec.variantDocument.indexName}", BeautyQElasticsearchInterpreterAdapter.mapping(testSpec))
      result <- use(client, testSpec).ensuring(client.deleteIndex(testSpec.variantDocument.indexName).either.unit)
    } yield result
  }

  private def indexSyntheticDocuments(
    testSpec: leaderboard.search.dsl.BeautySearchSpec,
    client: ElasticsearchTestClient,
  ): IO[QueryFailure, Unit] = {
    val documents = List(
      syntheticDocument(variantId, "balayage haircut", "service_name"),
      syntheticDocument(otherVariantId, "manicure gel polish", "other"),
    )
    val payload = BeautyQElasticsearchInterpreterAdapter.bulkPayload(testSpec, documents)
    for {
      _ <- client.postNdjson(s"/${testSpec.variantDocument.indexName}/_bulk", payload)
      _ <- client.post(s"/${testSpec.variantDocument.indexName}/_refresh")
    } yield ()
  }

  private def syntheticDocument(id: MasterServiceOfferVariantId, serviceName: String, tag: String): VariantSearchDocument = {
    val masterServiceOfferId: MasterServiceOfferId = MasterServiceOfferId(UUID.randomUUID())
    val masterLocationId: MasterLocationId = MasterLocationId(UUID.randomUUID())
    val masterId: MasterId = MasterId(UUID.randomUUID())
    val serviceId: ServiceId = ServiceId(UUID.randomUUID())
    val categoryId: CategoryId = CategoryId(UUID.randomUUID())
    VariantSearchDocument(
      variantId = id,
      masterServiceOfferId = masterServiceOfferId,
      masterLocationId = masterLocationId,
      masterId = masterId,
      serviceId = serviceId,
      categoryId = categoryId,
      serviceName = serviceName,
      categoryName = "hair",
      masterName = "m18a2-master",
      locationName = "m18a2-location",
      address = "m18a2-address",
      location = SearchGeoPoint(BigDecimal("53.57532"), BigDecimal("10.07672")),
      lat = BigDecimal("53.57532"),
      lon = BigDecimal("10.07672"),
      priceFrom = BigDecimal("30.0000"),
      priceTo = BigDecimal("45.0000"),
      durationMin = 60,
      enumAttributes = Map.empty,
      booleanAttributes = Map.empty,
      intAttributes = Map.empty,
      bigDecimalAttributes = Map.empty,
      allText = s"$serviceName $tag",
      serviceText = serviceName,
      attributeText = tag,
      providerText = "m18a2-master m18a2-location",
      locationText = "m18a2-location m18a2-address hair",
    )
  }

  private def unsafeRun[A](effect: IO[Nothing, A]): A =
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(effect).getOrThrowFiberFailure()
    }
}
