package leaderboard.search

import cats.effect.Async
import cats.syntax.all.*
import distage.{Injector, ModuleDef}
import fs2.text
import io.circe.parser.parse
import izumi.distage.model.definition.{Activation, LocatorPrivacy}
import izumi.distage.model.plan.Roots
import leaderboard.api.{BeautySearchApi, HttpApi}
import leaderboard.model.{MasterServiceOfferVariantId, QueryFailure}
import leaderboard.plugins.BeautySearchRouteModules
import leaderboard.search.document.BeautySearchReadyCatalogDocuments
import leaderboard.search.qdrant.{
  QdrantProductionCandidateActivationApprovalStatus,
  QdrantProductionCandidateActivationConfigApproval,
  QdrantProductionCandidateActivationConfigApprovalStatus,
  QdrantProductionCandidateActivationConfigGate,
  QdrantProductionCandidateActivationPlanning,
  QdrantProductionCandidateActivationPlanningStatus,
  QdrantProductionCandidateActivationPolicy,
  QdrantProductionCandidateActivationPrerequisites,
  QdrantProductionCandidateActivationRequirementStatus,
  QdrantProductionCandidateActivationScope,
  QdrantProductionCandidateActivationTargetScope,
  QdrantProductionCandidateNoRegressionApproval,
  QdrantProductionCandidateReadiness,
  QdrantProductionCandidateReadinessReport,
  QdrantProductionCandidateReadinessState,
  QdrantProductionCandidateReadinessStatus,
  QdrantExplicitOptInRoutePrerequisites,
}
import leaderboard.search.semantic.{SemanticCandidateBackend, SemanticCandidateHit}
import leaderboard.{HttpContractTestSupport, ObservedResponse}
import org.http4s.{HttpApp, Request, Status}
import org.scalatest.wordspec.AnyWordSpec
import zio.interop.catz.*
import zio.{IO, Runtime, Task, Unsafe, ZIO}

final class BeautySearchOptInRouteModuleSpec extends AnyWordSpec with HttpContractTestSupport {
  "BeautySearchRouteModules.seedCatalogInMemory" should {
    "contribute the opt-in BeautySearchApi and serve a seed-catalog in-memory search route" in {
      val probe = buildProbe()
      val apis  = probe.allHttpApis

      assert(apis.size == 1)
      assert(apis.collect { case api: BeautySearchApi[IO] => api }.size == 1)

      val response = runIO(
        observeRoute(
          apis,
          postJson("/beauty-search", """{"query":"nails","userLat":53.58,"userLon":10.08,"limit":3}"""),
        )
      )

      assert(response.status == Status.Ok)

      val json = parse(response.body).getOrElse(fail(s"invalid Beauty search response JSON: ${response.body}"))
      assert(json.hcursor.downField("variantCarousel").focus.exists(_.asArray.exists(_.nonEmpty)))
    }
  }

  "A future explicit Qdrant opt-in route" should {
    "remain a separate module outside default apiElasticsearch" in {
      val probe = buildQdrantProbe()
      val apis  = probe.allHttpApis

      assert(apis.size == 1)
      assert(apis.collect { case _: BeautySearchApi[IO] => () }.size == 1)
      assert(probe.prerequisites.planningDecision.targetScope == QdrantProductionCandidateActivationTargetScope.ExplicitOptInRoute)
      assert(probe.prerequisites.planningDecision.status == QdrantProductionCandidateActivationPlanningStatus.ReadyForSeparateImplementationDecision)

      val response = runIO(
        observeRoute(
          apis,
          postJson("/beauty-search", """{"query":"soft natural manicure","userLat":53.58,"userLon":10.08,"limit":2}"""),
        )
      )

      assert(response.status == Status.Ok)
      val json = parse(response.body).getOrElse(fail(s"invalid Qdrant opt-in Beauty search response JSON: ${response.body}"))
      assert(json.hcursor.downField("variantCarousel").focus.exists(_.asArray.exists(_.nonEmpty)))
    }

    "require M6 productionCandidateReady and activation-policy readiness before route wiring" in {
      val notReadyReadiness = QdrantProductionCandidateReadiness.evaluate(
        allReadyState.copy(collectionIdentity = QdrantProductionCandidateReadinessStatus.Unknown)
      )
      val notReadyActivation = QdrantProductionCandidateActivationPolicy.evaluate(
        explicitOptInPolicy.copy(observability = QdrantProductionCandidateActivationRequirementStatus.Missing)
      )

      assert(
        QdrantExplicitOptInRoutePrerequisites
          .fromReports(notReadyReadiness, explicitOptInActivationReport, approvedConfigReport)
          .isLeft
      )
      assert(
        QdrantExplicitOptInRoutePrerequisites
          .fromReports(allReadyReadinessReport, notReadyActivation, approvedConfigReport)
          .isLeft
      )
      assert(
        QdrantExplicitOptInRoutePrerequisites
          .fromReports(allReadyReadinessReport, explicitOptInActivationReport, approvedConfigReport)
          .exists(_.readinessReport.productionCandidateReady)
      )
    }

    "consume the disabled-by-default config gate and approved no-regression evidence through the M7 config report" in {
      import QdrantProductionCandidateActivationApprovalStatus.*
      import QdrantProductionCandidateActivationPlanningStatus.Blocked
      import QdrantProductionCandidateActivationRequirementStatus.*
      import QdrantProductionCandidateActivationTargetScope.ExplicitOptInRoute

      val configReport =
        QdrantProductionCandidateActivationConfigApproval.evaluate(
          QdrantProductionCandidateActivationConfigApproval.conservativeDefault
        )
      val prerequisites =
        QdrantProductionCandidateActivationConfigApproval.applyToPlanningPrerequisites(
          configReport,
          completeOptInPrerequisites,
        )
      val decision =
        QdrantProductionCandidateActivationPlanning.evaluate(ExplicitOptInRoute, prerequisites)

      assert(configReport.config.configGate == QdrantProductionCandidateActivationConfigGate.Disabled)
      assert(configReport.planningConfigGate == Missing)
      assert(configReport.planningNoRegressionEvidence == Unknown)
      assert(configReport.config.noRegression.approval == NotApproved)
      assert(decision.status == Blocked)
      assert(decision.blockingReasons == List(
        "Config gate is missing",
        "No-regression evidence status is unknown",
      ))
      assert(
        QdrantExplicitOptInRoutePrerequisites
          .fromReports(allReadyReadinessReport, explicitOptInActivationReport, configReport)
          .isLeft
      )
      assert(approvedConfigReport.config.configGate == QdrantProductionCandidateActivationConfigGate.Enabled)
      assert(approvedConfigReport.planningConfigGate == Satisfied)
      assert(approvedConfigReport.planningNoRegressionEvidence == Satisfied)
      assert(approvedConfigReport.decision.status == QdrantProductionCandidateActivationConfigApprovalStatus.ReadyForPlanning)
    }

    "require observability/status evidence and rollback/disable control" in {
      val missingObservability = QdrantProductionCandidateActivationPolicy.evaluate(
        explicitOptInPolicy.copy(observability = QdrantProductionCandidateActivationRequirementStatus.Missing)
      )
      val missingRollback = QdrantProductionCandidateActivationPolicy.evaluate(
        explicitOptInPolicy.copy(rollbackDisableControls = QdrantProductionCandidateActivationRequirementStatus.Missing)
      )

      assert(
        QdrantExplicitOptInRoutePrerequisites
          .fromReports(allReadyReadinessReport, missingObservability, approvedConfigReport)
          .isLeft
      )
      assert(
        QdrantExplicitOptInRoutePrerequisites
          .fromReports(allReadyReadinessReport, missingRollback, approvedConfigReport)
          .isLeft
      )
      assert(
        QdrantExplicitOptInRoutePrerequisites
          .fromReports(allReadyReadinessReport, explicitOptInActivationReport, approvedConfigReport)
          .exists(_.activationReport.policy.rollbackDisableControls == QdrantProductionCandidateActivationRequirementStatus.Satisfied)
      )
    }

    "require separate route/serving approval without approving production-route activation" in {
      val missingRouteServingApproval = QdrantProductionCandidateActivationPolicy.evaluate(
        explicitOptInPolicy.copy(routeServingApproval = QdrantProductionCandidateActivationApprovalStatus.NotApproved)
      )
      val productionRouteActivationPolicy = QdrantProductionCandidateActivationPolicy.evaluate(
        explicitOptInPolicy.copy(scope = QdrantProductionCandidateActivationScope.FutureProductionRouteNotApprovedHere)
      )

      assert(
        QdrantExplicitOptInRoutePrerequisites
          .fromReports(allReadyReadinessReport, missingRouteServingApproval, approvedConfigReport)
          .isLeft
      )
      assert(
        QdrantExplicitOptInRoutePrerequisites
          .fromReports(allReadyReadinessReport, productionRouteActivationPolicy, approvedConfigReport)
          .isLeft
      )
      assert(
        QdrantExplicitOptInRoutePrerequisites
          .fromReports(allReadyReadinessReport, explicitOptInActivationReport, approvedConfigReport)
          .exists(_.activationReport.policy.scope == QdrantProductionCandidateActivationScope.FutureExplicitOptInRouteOnly)
      )
    }

    "keep readiness evidence independent of shadow serving, mirroring, and production traffic" in {
      val prerequisites =
        QdrantExplicitOptInRoutePrerequisites
          .fromReports(allReadyReadinessReport, explicitOptInActivationReport, approvedConfigReport)

      prerequisites match {
        case Right(value) =>
          val fields = value.productElementNames.toSet
          val forbiddenTerms = List("shadow", "mirror", "traffic", "telemetry", "production")

          assert(fields == Set(
            "readinessReport",
            "activationReport",
            "configApprovalReport",
            "planningDecision",
          ))
          assert(!fields.exists(field => forbiddenTerms.exists(field.toLowerCase.contains)))
        case Left(failure) =>
          fail(s"expected ready explicit opt-in prerequisites, got $failure")
      }
    }
  }

  private val allReadyState =
    QdrantProductionCandidateReadinessState(
      qdrantActive = true,
      collectionIdentity = QdrantProductionCandidateReadinessStatus.Ready,
      contractParity = QdrantProductionCandidateReadinessStatus.Ready,
      indexing = QdrantProductionCandidateReadinessStatus.Ready,
      search = QdrantProductionCandidateReadinessStatus.Ready,
      qualityEval = QdrantProductionCandidateReadinessStatus.Ready,
      observability = QdrantProductionCandidateReadinessStatus.Ready,
      rollbackDisable = QdrantProductionCandidateReadinessStatus.Ready,
      activationPolicy = QdrantProductionCandidateReadinessStatus.Ready,
    )

  private val allReadyReadinessReport: QdrantProductionCandidateReadinessReport =
    QdrantProductionCandidateReadiness.evaluate(allReadyState)

  private val explicitOptInPolicy =
    QdrantProductionCandidateActivationPolicy(
      explicitlyApproved = true,
      scope = QdrantProductionCandidateActivationScope.FutureExplicitOptInRouteOnly,
      routeServingApproval = QdrantProductionCandidateActivationApprovalStatus.Approved,
      rollbackDisableControls = QdrantProductionCandidateActivationRequirementStatus.Satisfied,
      noRegressionEvidence = QdrantProductionCandidateActivationRequirementStatus.Satisfied,
      observability = QdrantProductionCandidateActivationRequirementStatus.Satisfied,
    )

  private val explicitOptInActivationReport =
    QdrantProductionCandidateActivationPolicy.evaluate(explicitOptInPolicy)

  private val approvedConfigReport =
    QdrantProductionCandidateActivationConfigApproval.evaluate(
      QdrantProductionCandidateActivationConfigApproval(
        configGate = QdrantProductionCandidateActivationConfigGate.Enabled,
        noRegression = QdrantProductionCandidateNoRegressionApproval(
          evidence = QdrantProductionCandidateActivationRequirementStatus.Satisfied,
          approval = QdrantProductionCandidateActivationApprovalStatus.Approved,
        ),
      )
    )

  private val completeOptInPrerequisites: QdrantProductionCandidateActivationPrerequisites = {
    import QdrantProductionCandidateActivationApprovalStatus.Approved
    import QdrantProductionCandidateActivationRequirementStatus.Satisfied
    import QdrantProductionCandidateReadinessStatus.Ready

    val activationPolicy =
      QdrantProductionCandidateActivationPolicy(
        explicitlyApproved = true,
        scope = QdrantProductionCandidateActivationScope.FutureExplicitOptInRouteOnly,
        routeServingApproval = Approved,
        rollbackDisableControls = Satisfied,
        noRegressionEvidence = Satisfied,
        observability = Satisfied,
      )
    val readinessState =
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

    QdrantProductionCandidateActivationPrerequisites(
      m6ReadinessReport = Some(QdrantProductionCandidateReadiness.evaluate(readinessState)),
      activationPolicyReport = Some(QdrantProductionCandidateActivationPolicy.evaluate(activationPolicy)),
      configGate = Satisfied,
      noRegressionEvidence = Satisfied,
      observabilityStatus = Satisfied,
      rollbackDisableControl = Satisfied,
      servingApproval = Approved,
    )
  }

  private def buildProbe(): BeautySearchOptInRouteModuleProbe = {
    val module = new ModuleDef {
      include(BeautySearchRouteModules.seedCatalogInMemory[IO])
      make[Async[Task]].fromValue(Async[Task])
      make[BeautySearchOptInRouteModuleProbe].from {
        (
          beautySearchApi: BeautySearchApi[IO],
          allHttpApis: Set[HttpApi[IO]],
        ) =>
          val _ = beautySearchApi
          BeautySearchOptInRouteModuleProbe(allHttpApis)
      }
    }

    val locator = Injector().produce(
      bindings = module,
      roots = Roots.target[BeautySearchOptInRouteModuleProbe],
      activation = Activation.empty,
      locatorPrivacy = LocatorPrivacy.PublicByDefault,
    ).unsafeGet()

    locator.get[BeautySearchOptInRouteModuleProbe]
  }

  private def buildQdrantProbe(): BeautySearchOptInQdrantRouteModuleProbe = {
    val module = new ModuleDef {
      include(BeautySearchRouteModules.apiQdrantExplicitOptIn)
      make[Async[Task]].fromValue(Async[Task])
      make[QdrantProductionCandidateReadinessReport].fromValue(allReadyReadinessReport)
      make[leaderboard.search.qdrant.QdrantProductionCandidateActivationReport].fromValue(explicitOptInActivationReport)
      make[leaderboard.search.qdrant.QdrantProductionCandidateActivationConfigApprovalReport].fromValue(approvedConfigReport)
      make[SemanticCandidateBackend[IO]].from {
        (ready: BeautySearchReadyCatalogDocuments) =>
          new StubSemanticCandidateBackend(ready.documents.map(_.variantId).take(2))
      }
      make[BeautySearchOptInQdrantRouteModuleProbe].from {
        (
          beautySearchApi: BeautySearchApi[IO],
          allHttpApis: Set[HttpApi[IO]],
          prerequisites: QdrantExplicitOptInRoutePrerequisites,
        ) =>
          val _ = beautySearchApi
          BeautySearchOptInQdrantRouteModuleProbe(allHttpApis, prerequisites)
      }
    }

    val locator = Injector().produce(
      bindings = module,
      roots = Roots.target[BeautySearchOptInQdrantRouteModuleProbe],
      activation = Activation.empty,
      locatorPrivacy = LocatorPrivacy.PublicByDefault,
    ).unsafeGet()

    locator.get[BeautySearchOptInQdrantRouteModuleProbe]
  }

  private def observeRoute(
    apis: Set[HttpApi[IO]],
    request: Request[Task],
  ): Task[ObservedResponse] = {
    val app: HttpApp[Task] = apis.map(_.http).toList.foldK.orNotFound

    app.run(request).flatMap {
      response =>
        response.body
          .through(text.utf8.decode)
          .compile
          .string
          .map(body => ObservedResponse(response.status, body))
    }
  }

  private final case class BeautySearchOptInRouteModuleProbe(
    allHttpApis: Set[HttpApi[IO]]
  )

  private final case class BeautySearchOptInQdrantRouteModuleProbe(
    allHttpApis: Set[HttpApi[IO]],
    prerequisites: QdrantExplicitOptInRoutePrerequisites,
  )

  private final class StubSemanticCandidateBackend(
    variantIds: List[MasterServiceOfferVariantId]
  ) extends SemanticCandidateBackend[IO] {
    override def candidates(input: UserSearchInput, intent: ParsedSearchIntent): IO[QueryFailure, List[SemanticCandidateHit]] =
      ZIO.succeed(variantIds.zipWithIndex.map {
        case (variantId, index) =>
          SemanticCandidateHit(variantId, 1.0d - (index.toDouble * 0.01d))
      })
  }

  private def runIO[E, A](effect: ZIO[Any, E, A]): A =
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(effect).getOrThrowFiberFailure()
    }
}
