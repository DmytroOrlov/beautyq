package leaderboard.search

import izumi.functional.bio.*
import leaderboard.model.{MasterServiceOfferVariantId, QueryFailure}
import leaderboard.search.eval.*
import leaderboard.search.lexical.{LexicalDocumentBackend, LexicalDocumentHit}
import leaderboard.search.semantic.{SemanticDocumentBackend, SemanticDocumentHit}
import org.scalatest.wordspec.AnyWordSpec
import zio.{IO, Runtime, Unsafe, ZIO}

import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

final class M18DualEngineOfflineEvalSpec extends AnyWordSpec {

  "M18 dual-engine offline eval runner" should {

    "keep ES and Qdrant candidate rows separate with backend attribution, rank, score, and query id" in {
      val result = run(esLeg = connectedEs(), qdrantLeg = connectedQdrant(), clock = None)

      assert(result.es.isInstanceOf[M18BackendLegOutcome.Executed])
      assert(result.qdrant.isInstanceOf[M18BackendLegOutcome.Executed])

      // separation: ES rows are only ES, Qdrant rows are only Qdrant, and the invariant agrees.
      assert(result.esCandidateRows.forall(_.backend == M18OfflineEvalBackend.Es))
      assert(result.qdrantCandidateRows.forall(_.backend == M18OfflineEvalBackend.Qdrant))
      assert(result.separationViolations.isEmpty)
      assert(result.esCandidateRows.map(_.candidateId).toSet.intersect(Set(variant3.toString)).isEmpty)

      // executed ES leg emits real candidate rows with attribution, rank, native score and query id.
      val q1EsRows = result.esCandidateRows.filter(_.queryId == "q1").sortBy(_.rank)
      assert(q1EsRows.map(_.rank) == List(1, 2))
      assert(q1EsRows.map(_.candidateId) == List(variant1.toString, variant2.toString))
      assert(q1EsRows.head.backendScore.contains(BigDecimal.decimal(12.5)))
      assert(q1EsRows.head.matchedFields == List("service_name"))
      assert(q1EsRows.forall(_.queryId == "q1"))

      // Qdrant leg emits its own native-scored rows (no matched-field detail, which it genuinely lacks).
      val q1QdrantRows = result.qdrantCandidateRows.filter(_.queryId == "q1").sortBy(_.rank)
      assert(q1QdrantRows.map(_.candidateId) == List(variant2.toString, variant1.toString))
      assert(q1QdrantRows.head.backendScore.contains(BigDecimal.decimal(0.91)))
      assert(q1QdrantRows.forall(_.matchedFields.isEmpty))
    }

    "emit an honest resource-gated Qdrant leg with the exact missing prerequisites and no fake candidates" in {
      val prerequisites = M18QdrantLegPrerequisites(
        realBackendOfflineEvalEnabled = false,
        embeddingClientConfigured = false,
        qdrantClientConfigured = false,
        collectionReadinessConfigured = false,
      )
      val gated = M18QdrantLegInput.fromPrerequisites[IO, MasterServiceOfferVariantId](prerequisites) {
        sys.error("must not connect when prerequisites are missing")
      }

      val result = run(esLeg = connectedEs(), qdrantLeg = gated, clock = None)

      assert(result.qdrantExecuted == false)
      result.qdrant match {
        case skipped: M18BackendLegOutcome.Skipped =>
          assert(skipped.skipKind == M18LegSkipKind.ResourceGated)
          assert(skipped.candidateRows.isEmpty)
          assert(skipped.missingPrerequisites == List(
            s"${M18QdrantLegResourceGate.EnablementEnvVar}=1 is required to execute the Qdrant offline eval leg",
            "embedding client (query vectorization) is not configured",
            "Qdrant search client (host/port) is not configured",
            "Qdrant collection readiness/config (collection name + vector spec) is not available",
          ))
          assert(skipped.reason.contains("resource-gated"))
        case other =>
          fail(s"expected resource-gated Qdrant leg, got $other")
      }

      // ES still executed: a real backend leg ran even though Qdrant was gated.
      assert(result.esExecuted)
      assert(result.esCandidateRows.nonEmpty)
    }

    "connect the Qdrant leg only when every prerequisite is satisfied" in {
      val satisfied = M18QdrantLegPrerequisites(
        realBackendOfflineEvalEnabled = true,
        embeddingClientConfigured = true,
        qdrantClientConfigured = true,
        collectionReadinessConfigured = true,
      )
      assert(M18QdrantLegResourceGate.missingPrerequisites(satisfied).isEmpty)

      val input = M18QdrantLegInput.fromPrerequisites[IO, MasterServiceOfferVariantId](satisfied)(connectedQdrant())
      assert(input.isInstanceOf[M18QdrantLegInput.Connected[IO, MasterServiceOfferVariantId]])
    }

    "populate expected/matched flags only where existing expectations support them" in {
      val result = run(esLeg = connectedEs(), qdrantLeg = connectedQdrant(), clock = None)

      // q1 has expectations [variant1]; q2 has no expectations.
      val q1 = result.esCandidateRows.filter(_.queryId == "q1")
      assert(q1.find(_.candidateId == variant1.toString).map(_.expectedMatch).contains(M18ExpectedMatch.Matched))
      assert(q1.find(_.candidateId == variant2.toString).map(_.expectedMatch).contains(M18ExpectedMatch.NotExpected))

      val q2 = result.esCandidateRows.filter(_.queryId == "q2")
      assert(q2.nonEmpty)
      assert(q2.forall(_.expectedMatch == M18ExpectedMatch.ExpectationsUnavailable))
    }

    "preserve missing-lookup evidence from actual hydration and mark it not-evaluated when no lookup is supplied" in {
      val withLookup = run(esLeg = connectedEs(), qdrantLeg = connectedQdrant(), clock = None)
      val q1 = withLookup.esCandidateRows.filter(_.queryId == "q1")
      // lookup knows variant1 (hydrated) but not variant2 (missing lookup preserved, not dropped).
      assert(q1.find(_.candidateId == variant1.toString).map(_.missingLookup).contains(M18MissingLookup.Hydrated))
      assert(q1.find(_.candidateId == variant2.toString).map(_.missingLookup).contains(M18MissingLookup.MissingLookup))
      assert(q1.map(_.candidateId).contains(variant2.toString)) // not silently dropped

      val noLookup = run(esLeg = connectedEs(lookup = None), qdrantLeg = connectedQdrant(), clock = None)
      assert(noLookup.esCandidateRows.forall(_.missingLookup == M18MissingLookup.LookupNotEvaluated))
    }

    "preserve latency where safely measured and make its absence explicit" in {
      val measured = run(esLeg = connectedEs(), qdrantLeg = connectedQdrant(), clock = Some(scriptedClock(stepNanos = 1000L)))
      measured.es match {
        case executed: M18BackendLegOutcome.Executed =>
          assert(executed.queryResults.forall(_.latencyNanos.contains(1000L)))
        case other => fail(s"expected executed ES leg, got $other")
      }

      val unmeasured = run(esLeg = connectedEs(), qdrantLeg = connectedQdrant(), clock = None)
      unmeasured.es match {
        case executed: M18BackendLegOutcome.Executed =>
          assert(executed.queryResults.forall(_.latencyNanos.isEmpty))
        case other => fail(s"expected executed ES leg, got $other")
      }
    }

    "capture a backend leg query failure as data without fabricating candidates" in {
      val failing = new LexicalDocumentBackend[IO, MasterServiceOfferVariantId] {
        override def documentHits(input: UserSearchInput, intent: ParsedSearchIntent): IO[QueryFailure, List[LexicalDocumentHit[MasterServiceOfferVariantId]]] =
          ZIO.fail(QueryFailure.operation("m18-test", "scripted ES failure"))
      }
      val result = run(
        esLeg = M18EsLegInput.Connected(failing, None),
        qdrantLeg = connectedQdrant(),
        clock = None,
      )
      result.es match {
        case executed: M18BackendLegOutcome.Executed =>
          assert(executed.queryResults.forall(_.failure.nonEmpty))
          assert(executed.candidateRows.isEmpty)
        case other => fail(s"expected executed-with-failures ES leg, got $other")
      }
    }
  }

  private val variant1: MasterServiceOfferVariantId = MasterServiceOfferVariantId(UUID.fromString("00000000-0000-0000-0000-000000000001"))
  private val variant2: MasterServiceOfferVariantId = MasterServiceOfferVariantId(UUID.fromString("00000000-0000-0000-0000-000000000002"))
  private val variant3: MasterServiceOfferVariantId = MasterServiceOfferVariantId(UUID.fromString("00000000-0000-0000-0000-000000000003"))

  private val dataset: M9OfflineEvalDataset =
    M9OfflineEvalDataset(
      evalDatasetId = EvalDatasetId("m18-test-dataset"),
      catalogSnapshotId = CatalogSnapshotId("m18-test-snapshot"),
      queries = List(
        M9OfflineEvalDatasetQuery(
          queryId = "q1",
          rawQueryText = "balayage haarschnitt",
          normalizedQueryText = Some("balayage haarschnitt"),
          queryClass = QueryClass.ExactProductNameBrand,
          filters = Nil,
          categories = Nil,
          expectedResults = List(M9OfflineEvalExpectedResult(variant1.toString, None)),
          expectedNotes = Nil,
          negativeOutOfCatalog = false,
        ),
        M9OfflineEvalDatasetQuery(
          queryId = "q2",
          rawQueryText = "something relaxing",
          normalizedQueryText = None,
          queryClass = QueryClass.SemanticDescriptive,
          filters = Nil,
          categories = Nil,
          expectedResults = Nil,
          expectedNotes = Nil,
          negativeOutOfCatalog = false,
        ),
      ),
    )

  private val presentIds: Set[MasterServiceOfferVariantId] = Set(variant1, variant3)

  private val variantLookup: M18CandidateLookup[IO, MasterServiceOfferVariantId] =
    new M18CandidateLookup[IO, MasterServiceOfferVariantId] {
      override def existing(ids: List[MasterServiceOfferVariantId]): IO[QueryFailure, Set[MasterServiceOfferVariantId]] =
        ZIO.succeed(ids.filter(presentIds.contains).toSet)
    }

  private def connectedEs(
    lookup: Option[M18CandidateLookup[IO, MasterServiceOfferVariantId]] = Some(variantLookup)
  ): M18EsLegInput[IO, MasterServiceOfferVariantId] = {
    val backend = new LexicalDocumentBackend[IO, MasterServiceOfferVariantId] {
      override def documentHits(input: UserSearchInput, intent: ParsedSearchIntent): IO[QueryFailure, List[LexicalDocumentHit[MasterServiceOfferVariantId]]] =
        ZIO.succeed(input.query match {
          case "balayage haarschnitt" =>
            List(
              LexicalDocumentHit(variant1, 12.5, matchedFields = List("service_name")),
              LexicalDocumentHit(variant2, 3.0, matchedFields = Nil),
            )
          case _ =>
            List(LexicalDocumentHit(variant1, 1.0, matchedFields = List("all_text")))
        })
    }
    M18EsLegInput.Connected(backend, lookup)
  }

  private def connectedQdrant(): M18QdrantLegInput.Connected[IO, MasterServiceOfferVariantId] = {
    val backend = new SemanticDocumentBackend[IO, MasterServiceOfferVariantId] {
      override def documentHits(input: UserSearchInput, intent: ParsedSearchIntent): IO[QueryFailure, List[SemanticDocumentHit[MasterServiceOfferVariantId]]] =
        ZIO.succeed(input.query match {
          case "balayage haarschnitt" =>
            List(SemanticDocumentHit(variant2, 0.91), SemanticDocumentHit(variant1, 0.74))
          case _ =>
            List(SemanticDocumentHit(variant1, 0.5))
        })
    }
    M18QdrantLegInput.Connected(backend, Some(variantLookup))
  }

  private def scriptedClock(stepNanos: Long): M18OfflineEvalLegClock[IO] =
    new M18OfflineEvalLegClock[IO] {
      private val counter = new AtomicLong(0L)
      override def monotonicNanos: IO[Nothing, Long] =
        ZIO.succeed(counter.getAndIncrement() * stepNanos)
    }

  private def run(
    esLeg: M18EsLegInput[IO, MasterServiceOfferVariantId],
    qdrantLeg: M18QdrantLegInput[IO, MasterServiceOfferVariantId],
    clock: Option[M18OfflineEvalLegClock[IO]],
  ): M18DualEngineOfflineEvalResult = {
    val runner = new M18DualEngineOfflineEvalRunner[IO, MasterServiceOfferVariantId](
      inputBuilder = M18EvalQueryInputBuilder.default,
      renderId = _.toString,
      clock = clock,
    )
    unsafeRun(runner.run(dataset, esLeg, qdrantLeg))
  }

  private def unsafeRun[A](effect: IO[Nothing, A]): A =
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(effect).getOrThrowFiberFailure()
    }
}
