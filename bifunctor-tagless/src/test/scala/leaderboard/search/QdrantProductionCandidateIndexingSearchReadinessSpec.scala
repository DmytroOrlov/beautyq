package leaderboard.search

import leaderboard.search.qdrant.{
  QdrantProductionCandidateIndexingDecisionStatus,
  QdrantProductionCandidateIndexingEvidence,
  QdrantProductionCandidateIndexingReadiness,
  QdrantProductionCandidateReadiness,
  QdrantProductionCandidateReadinessState,
  QdrantProductionCandidateReadinessStatus,
  QdrantSnapshotIndexingResult,
  QdrantProductionCandidateSearchDecisionStatus,
  QdrantProductionCandidateSearchEvidence,
  QdrantProductionCandidateSearchReadiness,
}
import org.scalatest.wordspec.AnyWordSpec

final class QdrantProductionCandidateIndexingSearchReadinessSpec extends AnyWordSpec {
  import QdrantProductionCandidateReadinessStatus.*

  "QdrantProductionCandidateIndexingReadiness" should {
    "map a missing report to Unknown" in {
      assert(QdrantProductionCandidateIndexingReadiness.readinessStatus(None) == Unknown)
    }

    "block zero expected and prepared document counts deterministically" in {
      val report = evaluateIndexing(completeIndexingEvidence.copy(
        expectedDocumentCount = 0,
        preparedDocumentCount = 0,
        indexedDocumentCount = Some(0),
      ))

      assert(report.decision.status == QdrantProductionCandidateIndexingDecisionStatus.NotReady)
      assert(report.decision.blockingReasons == List(
        "Expected document count must be positive: 0",
        "Prepared document count must be positive: 0",
      ))
    }

    "block an indexed count below the expected count" in {
      val report = evaluateIndexing(completeIndexingEvidence.copy(indexedDocumentCount = Some(9)))

      assert(report.decision.blockingReasons == List("Indexed document count 9 does not match expected 10"))
      assert(QdrantProductionCandidateIndexingReadiness.readinessStatus(Some(report)) == NotReady(report.decision.blockingReasons))
    }

    "block missing indexed-count evidence" in {
      val report = evaluateIndexing(completeIndexingEvidence.copy(indexedDocumentCount = None))

      assert(report.decision.blockingReasons == List("Indexed document count evidence is missing"))
    }

    "block collection incompatibility through collection identity readiness" in {
      val report = evaluateIndexing(completeIndexingEvidence.copy(
        collectionIdentityReadiness = NotReady(List("DimensionMismatch(expected=1024, observed=768)"))
      ))

      assert(report.decision.blockingReasons == List(
        "Collection identity is not ready: DimensionMismatch(expected=1024, observed=768)"
      ))
    }

    "preserve deterministic reason ordering for incomplete indexing evidence" in {
      val report = evaluateIndexing(QdrantProductionCandidateIndexingEvidence(
        expectedDocumentCount = 10,
        preparedDocumentCount = 8,
        indexedDocumentCount = Some(7),
        collectionIdentityReadiness = Unknown,
        embeddingVectorReady = false,
      ))

      assert(report.decision.blockingReasons == List(
        "Prepared document count 8 does not match expected 10",
        "Indexed document count 7 does not match expected 10",
        "Collection identity readiness is unknown",
        "Embedding/vector readiness evidence is missing",
      ))
    }

    "map complete indexing evidence to Ready" in {
      val report = evaluateIndexing(completeIndexingEvidence)

      assert(report.decision.status == QdrantProductionCandidateIndexingDecisionStatus.Ready)
      assert(report.decision.blockingReasons.isEmpty)
      assert(QdrantProductionCandidateIndexingReadiness.readinessStatus(Some(report)) == Ready)
    }

    "adapt the existing snapshot indexing result without running Qdrant" in {
      val report = QdrantProductionCandidateIndexingReadiness.fromSnapshotIndexingResult(
        expectedDocumentCount = 10,
        result = QdrantSnapshotIndexingResult(
          totalDocumentsLoaded = 10,
          totalDocumentsIndexed = 10,
          indexedVariantIds = Nil,
        ),
        collectionIdentityReadiness = Ready,
        embeddingVectorReady = true,
      )

      assert(report.evidence == completeIndexingEvidence)
      assert(report.decision.status == QdrantProductionCandidateIndexingDecisionStatus.Ready)
    }
  }

  "QdrantProductionCandidateSearchReadiness" should {
    "map a missing report to Unknown" in {
      assert(QdrantProductionCandidateSearchReadiness.readinessStatus(None) == Unknown)
    }

    "block missing semantic backend and search contracts" in {
      val report = evaluateSearch(completeSearchEvidence.copy(
        semanticCandidateBackendContractPresent = false,
        semanticCandidateSearchContractPresent = false,
      ))

      assert(report.decision.blockingReasons == List(
        "Semantic candidate backend contract is missing",
        "Semantic candidate search contract is missing",
      ))
    }

    "block missing candidate assembly readiness" in {
      val report = evaluateSearch(completeSearchEvidence.copy(candidateAssemblyReady = false))

      assert(report.decision.blockingReasons == List("Candidate assembly readiness is missing"))
    }

    "block missing response projection readiness" in {
      val report = evaluateSearch(completeSearchEvidence.copy(responseProjectionReady = false))

      assert(report.decision.blockingReasons == List("Response projection readiness is missing"))
    }

    "preserve deterministic reason ordering for incomplete search evidence" in {
      val report = evaluateSearch(QdrantProductionCandidateSearchEvidence(
        semanticCandidateBackendContractPresent = false,
        semanticCandidateSearchContractPresent = false,
        candidateAssemblyReady = false,
        responseProjectionReady = false,
        beautySearchContractParityReady = false,
      ))

      assert(report.decision.status == QdrantProductionCandidateSearchDecisionStatus.NotReady)
      assert(report.decision.blockingReasons == List(
        "Semantic candidate backend contract is missing",
        "Semantic candidate search contract is missing",
        "Candidate assembly readiness is missing",
        "Response projection readiness is missing",
        "BeautySearch contract parity evidence is missing",
      ))
    }

    "map complete search evidence to Ready" in {
      val report = evaluateSearch(completeSearchEvidence)

      assert(report.decision.status == QdrantProductionCandidateSearchDecisionStatus.Ready)
      assert(report.decision.blockingReasons.isEmpty)
      assert(QdrantProductionCandidateSearchReadiness.readinessStatus(Some(report)) == Ready)
    }

    "adapt the existing source search contracts without constructing runtime dependencies" in {
      val report = QdrantProductionCandidateSearchReadiness.fromExistingSourceContracts(
        beautySearchContractParityReady = true
      )

      assert(report.evidence == completeSearchEvidence)
      assert(report.decision.status == QdrantProductionCandidateSearchDecisionStatus.Ready)
    }
  }

  "Qdrant production-candidate readiness integration" should {
    "remain false when indexing is not ready" in {
      val indexingReport = evaluateIndexing(completeIndexingEvidence.copy(indexedDocumentCount = Some(9)))
      val state = QdrantProductionCandidateReadiness.withIndexingReadiness(allReadyState, Some(indexingReport))

      assert(state.indexing == NotReady(indexingReport.decision.blockingReasons))
      assert(!QdrantProductionCandidateReadiness.evaluate(state).productionCandidateReady)
    }

    "remain false when search is not ready" in {
      val searchReport = evaluateSearch(completeSearchEvidence.copy(candidateAssemblyReady = false))
      val state = QdrantProductionCandidateReadiness.withSearchReadiness(allReadyState, Some(searchReport))

      assert(state.search == NotReady(searchReport.decision.blockingReasons))
      assert(!QdrantProductionCandidateReadiness.evaluate(state).productionCandidateReady)
    }

    "become true when indexing, search, and every other category are ready" in {
      val withIndexing = QdrantProductionCandidateReadiness.withIndexingReadiness(
        allReadyState.copy(indexing = Unknown, search = Unknown),
        Some(evaluateIndexing(completeIndexingEvidence)),
      )
      val withSearch = QdrantProductionCandidateReadiness.withSearchReadiness(
        withIndexing,
        Some(evaluateSearch(completeSearchEvidence)),
      )

      assert(withSearch.indexing == Ready)
      assert(withSearch.search == Ready)
      assert(QdrantProductionCandidateReadiness.evaluate(withSearch).productionCandidateReady)
    }

    "keep missing reports conservative" in {
      val withoutIndexing = QdrantProductionCandidateReadiness.withIndexingReadiness(allReadyState, None)
      val withoutSearch = QdrantProductionCandidateReadiness.withSearchReadiness(allReadyState, None)

      assert(withoutIndexing.indexing == Unknown)
      assert(withoutSearch.search == Unknown)
      assert(!QdrantProductionCandidateReadiness.evaluate(withoutIndexing).productionCandidateReady)
      assert(!QdrantProductionCandidateReadiness.evaluate(withoutSearch).productionCandidateReady)
    }

    "contain readiness evidence only and no serving behavior fields" in {
      val fields =
        completeIndexingEvidence.productElementNames.toSet ++
          completeSearchEvidence.productElementNames.toSet ++
          QdrantProductionCandidateReadiness.conservativeDefault.productElementNames.toSet
      val forbiddenTerms = List(
        "shadow",
        "mirror",
        "traffic",
        "route",
        "fallback",
        "fusion",
        "rerank",
        "hybrid",
        "supplement",
      )

      assert(!fields.exists(field => forbiddenTerms.exists(field.toLowerCase.contains)))
    }
  }

  private val completeIndexingEvidence =
    QdrantProductionCandidateIndexingEvidence(
      expectedDocumentCount = 10,
      preparedDocumentCount = 10,
      indexedDocumentCount = Some(10),
      collectionIdentityReadiness = Ready,
      embeddingVectorReady = true,
    )

  private val completeSearchEvidence =
    QdrantProductionCandidateSearchEvidence(
      semanticCandidateBackendContractPresent = true,
      semanticCandidateSearchContractPresent = true,
      candidateAssemblyReady = true,
      responseProjectionReady = true,
      beautySearchContractParityReady = true,
    )

  private def evaluateIndexing(evidence: QdrantProductionCandidateIndexingEvidence) =
    QdrantProductionCandidateIndexingReadiness.evaluate(evidence)

  private def evaluateSearch(evidence: QdrantProductionCandidateSearchEvidence) =
    QdrantProductionCandidateSearchReadiness.evaluate(evidence)

  private val allReadyState =
    QdrantProductionCandidateReadinessState(
      qdrantActive = true,
      collectionIdentity = Ready,
      contractParity = Ready,
      indexing = Ready,
      search = Ready,
      qualityEval = Ready,
      observability = Ready,
      rollbackDisable = Ready,
      activationPolicy = Ready,
    )
}
