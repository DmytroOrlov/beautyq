package leaderboard.search

import org.scalatest.wordspec.AnyWordSpec

final class QdrantProductionCandidatePostM7NoServingGuardrailSpec extends AnyWordSpec {
  "post-M7 Qdrant evidence" should {
    "remain explicit-opt-in only and imply no production route activation after scoped implementation approval" in {
      val evidence = postM7Evidence

      assert(evidence.m7CloseoutSpec == classOf[QdrantProductionCandidateM7CloseoutSpec].getSimpleName)
      assert(evidence.offlineEvalEvidenceSpec == classOf[QdrantProductionCandidateOfflineEvalEvidenceSpec].getSimpleName)
      assert(evidence.decisionBundle == Option72DecisionBundle(
        acceptedAsCaptureOnlyEvidence = true,
        supportsAskingForFutureExplicitOptInApproval = true,
        approvesDisabledByDefaultExplicitOptInRouteImplementation = true,
        approvesProductionRouteActivation = false,
      ))
      assert(evidence.routeBoundarySpecs == Set(
        classOf[BeautySearchProductionRouteExposureSpec].getSimpleName,
        classOf[BeautySearchElasticsearchRouteModuleSpec].getSimpleName,
        classOf[BeautySearchOptInRouteModuleSpec].getSimpleName,
        classOf[BeautySearchOptInHttpApiModuleSpec].getSimpleName,
      ))

      assert(evidence.noServingClaims == NoServingClaims(
        productionActivationApproved = false,
        qdrantDefaultServingRouteExists = false,
        qdrantOptInServingRouteImplemented = true,
        routeSwitchExists = false,
        beautySearchBehaviorChanged = false,
        hybridServingExists = false,
        shadowServingExists = false,
        productionTrafficMirroringExists = false,
      ))
    }

    "keep active route-boundary evidence separate from pending Qdrant opt-in expectations" in {
      val evidence = postM7Evidence

      assert(evidence.activeRouteBoundary == ActiveRouteBoundary(
        productionRoute = "POST /beauty-search",
        graph = "LeaderboardPlugin.modules.apiBase[IO] + BeautySearchRouteModules.apiElasticsearch",
        backend = "seed-resource catalog snapshot + ElasticsearchSearchBackend",
      ))
      assert(evidence.pendingQdrantBoundary == PendingQdrantBoundary(
        explicitOptInRouteImplementationApproved = true,
        productionRouteActivationApproved = false,
        qdrantExplicitOptInRouteAdded = true,
        routeSwitchAdded = false,
      ))
    }
  }

  private final case class PostM7NoServingEvidence(
    m7CloseoutSpec: String,
    offlineEvalEvidenceSpec: String,
    decisionBundle: Option72DecisionBundle,
    routeBoundarySpecs: Set[String],
    activeRouteBoundary: ActiveRouteBoundary,
    pendingQdrantBoundary: PendingQdrantBoundary,
    noServingClaims: NoServingClaims,
  )

  private final case class Option72DecisionBundle(
    acceptedAsCaptureOnlyEvidence: Boolean,
    supportsAskingForFutureExplicitOptInApproval: Boolean,
    approvesDisabledByDefaultExplicitOptInRouteImplementation: Boolean,
    approvesProductionRouteActivation: Boolean,
  )

  private final case class ActiveRouteBoundary(
    productionRoute: String,
    graph: String,
    backend: String,
  )

  private final case class PendingQdrantBoundary(
    explicitOptInRouteImplementationApproved: Boolean,
    productionRouteActivationApproved: Boolean,
    qdrantExplicitOptInRouteAdded: Boolean,
    routeSwitchAdded: Boolean,
  )

  private final case class NoServingClaims(
    productionActivationApproved: Boolean,
    qdrantDefaultServingRouteExists: Boolean,
    qdrantOptInServingRouteImplemented: Boolean,
    routeSwitchExists: Boolean,
    beautySearchBehaviorChanged: Boolean,
    hybridServingExists: Boolean,
    shadowServingExists: Boolean,
    productionTrafficMirroringExists: Boolean,
  )

  private val postM7Evidence =
    PostM7NoServingEvidence(
      m7CloseoutSpec = classOf[QdrantProductionCandidateM7CloseoutSpec].getSimpleName,
      offlineEvalEvidenceSpec = classOf[QdrantProductionCandidateOfflineEvalEvidenceSpec].getSimpleName,
      decisionBundle = Option72DecisionBundle(
        acceptedAsCaptureOnlyEvidence = true,
        supportsAskingForFutureExplicitOptInApproval = true,
        approvesDisabledByDefaultExplicitOptInRouteImplementation = true,
        approvesProductionRouteActivation = false,
      ),
      routeBoundarySpecs = Set(
        classOf[BeautySearchProductionRouteExposureSpec].getSimpleName,
        classOf[BeautySearchElasticsearchRouteModuleSpec].getSimpleName,
        classOf[BeautySearchOptInRouteModuleSpec].getSimpleName,
        classOf[BeautySearchOptInHttpApiModuleSpec].getSimpleName,
      ),
      activeRouteBoundary = ActiveRouteBoundary(
        productionRoute = "POST /beauty-search",
        graph = "LeaderboardPlugin.modules.apiBase[IO] + BeautySearchRouteModules.apiElasticsearch",
        backend = "seed-resource catalog snapshot + ElasticsearchSearchBackend",
      ),
      pendingQdrantBoundary = PendingQdrantBoundary(
        explicitOptInRouteImplementationApproved = true,
        productionRouteActivationApproved = false,
        qdrantExplicitOptInRouteAdded = true,
        routeSwitchAdded = false,
      ),
      noServingClaims = NoServingClaims(
        productionActivationApproved = false,
        qdrantDefaultServingRouteExists = false,
        qdrantOptInServingRouteImplemented = true,
        routeSwitchExists = false,
        beautySearchBehaviorChanged = false,
        hybridServingExists = false,
        shadowServingExists = false,
        productionTrafficMirroringExists = false,
      ),
    )
}
