package leaderboard.search

import distage.{Injector, ModuleDef}
import izumi.distage.model.definition.{Activation, LocatorPrivacy}
import izumi.distage.model.plan.Roots
import leaderboard.model.QueryFailure
import leaderboard.search.dsl.VectorDistance
import leaderboard.search.hybrid.control._
import leaderboard.search.hybrid.production._
import org.scalatest.wordspec.AnyWordSpec
import zio.{IO, Runtime, Unsafe, ZIO}

final class BeautySearchHybridProductionModulesSpec extends AnyWordSpec {

  "BeautySearchHybridProductionModules.disabled" should {
    "expose Disabled activation with a handle whose evaluator is None" in {
      val probe = buildDisabledProbe()

      assert(probe.activation == BeautySearchHybridProductionActivation.Disabled)
      assert(probe.handle.evaluator.isEmpty)
    }
  }

  "BeautySearchHybridProductionModules.enabledControlPlaneOnly" should {
    "build an Enabled activation with a Some(evaluator) handle, never invoking evaluate" in {
      val policy = BeautySearchHybridServingPolicy(
        mode = BeautySearchHybridRuntimeMode.HybridShadow,
        requireReadyForServing = false,
      )
      val probe = buildEnabledProbe(policy)

      probe.activation match {
        case BeautySearchHybridProductionActivation.Enabled(actualPolicy) =>
          assert(actualPolicy == policy)

        case other =>
          fail(s"Expected Enabled activation, got $other")
      }

      assert(probe.handle.evaluator.nonEmpty)
    }
  }

  "Enabled evaluator materialised from BeautySearchHybridProductionModules.enabledControlPlaneOnly" should {
    "delegate to the bound readiness and diagnostics sink and return the served decision" in {
      val snapshot = BeautySearchHybridSnapshotIdentity(
        sourceId = "seed-catalog",
        snapshotVersion = "v1",
        documentCount = 17,
      )
      val collection = BeautySearchHybridCollectionIdentity(
        collectionName = "beauty_hybrid_v1",
        vectorName = "variant-embedding",
        embeddingModelName = "llama-cpp-embedding",
        embeddingDimension = 1024,
        distance = VectorDistance.Cosine,
      )
      val ready = BeautySearchHybridReadinessStatus.Ready(
        snapshot = snapshot,
        collection = collection,
        indexedDocumentCount = 17,
      )
      val policy = BeautySearchHybridServingPolicy(
        mode = BeautySearchHybridRuntimeMode.HybridServe,
        requireReadyForServing = true,
      )
      val readiness = new ConstReadiness(ready)
      val sink = new ExpectingDiagnosticsSink(
        BeautySearchHybridDiagnosticsEvent.DecisionEvaluated(
          policy = policy,
          readiness = ready,
          decision = BeautySearchHybridServingDecision.ServeHybrid(ready),
        )
      )
      val probe = buildEnabledProbe(policy, readiness, sink)
      val evaluator = probe.handle.evaluator match {
        case Some(actual) => actual
        case None => fail("Expected enabled handle with evaluator")
      }

      val result = runIO(evaluator.evaluate())

      result match {
        case BeautySearchHybridServingDecision.ServeHybrid(serveReady) =>
          assert(serveReady == ready)

        case other =>
          fail(s"Expected ServeHybrid, got $other")
      }
    }
  }

  private def buildDisabledProbe(): DisabledProbe = {
    val module = new ModuleDef {
      include(BeautySearchHybridProductionModules.disabled[IO])
      make[DisabledProbe].from {
        (
          activation: BeautySearchHybridProductionActivation,
          handle: BeautySearchHybridProductionHandle[IO],
        ) =>
          DisabledProbe(activation, handle)
      }
    }

    val locator = Injector().produce(
      bindings = module,
      roots = Roots.target[DisabledProbe],
      activation = Activation.empty,
      locatorPrivacy = LocatorPrivacy.PublicByDefault,
    ).unsafeGet()

    locator.get[DisabledProbe]
  }

  private def buildEnabledProbe(
    policy: BeautySearchHybridServingPolicy,
  ): EnabledProbe =
    buildEnabledProbe(
      policy,
      new FailIfCalledReadiness,
      new FailIfCalledDiagnosticsSink,
    )

  private def buildEnabledProbe(
    policy: BeautySearchHybridServingPolicy,
    readiness: BeautySearchHybridReadiness[IO],
    sink: BeautySearchHybridDiagnosticsSink[IO],
  ): EnabledProbe = {
    val module = new ModuleDef {
      include(BeautySearchHybridProductionModules.enabledControlPlaneOnly[IO](policy))
      make[BeautySearchHybridReadiness[IO]].fromValue(readiness)
      make[BeautySearchHybridDiagnosticsSink[IO]].fromValue(sink)
      make[EnabledProbe].from {
        (
          activation: BeautySearchHybridProductionActivation,
          handle: BeautySearchHybridProductionHandle[IO],
        ) =>
          EnabledProbe(activation, handle)
      }
    }

    val locator = Injector().produce(
      bindings = module,
      roots = Roots.target[EnabledProbe],
      activation = Activation.empty,
      locatorPrivacy = LocatorPrivacy.PublicByDefault,
    ).unsafeGet()

    locator.get[EnabledProbe]
  }

  private final case class DisabledProbe(
    activation: BeautySearchHybridProductionActivation,
    handle: BeautySearchHybridProductionHandle[IO],
  )

  private final case class EnabledProbe(
    activation: BeautySearchHybridProductionActivation,
    handle: BeautySearchHybridProductionHandle[IO],
  )

  private final class FailIfCalledReadiness extends BeautySearchHybridReadiness[IO] {
    override def status(): IO[QueryFailure, BeautySearchHybridReadinessStatus] =
      ZIO.suspendSucceed(
        ZIO.fail(QueryFailure.domain("FailIfCalledReadiness.status was unexpectedly called"))
      )
  }

  private final class FailIfCalledDiagnosticsSink extends BeautySearchHybridDiagnosticsSink[IO] {
    override def report(event: BeautySearchHybridDiagnosticsEvent): IO[Nothing, Unit] =
      ZIO.suspendSucceed {
        ZIO.dieMessage(s"FailIfCalledDiagnosticsSink.report was unexpectedly called with $event")
      }
  }

  private final class ConstReadiness(
    constant: BeautySearchHybridReadinessStatus,
  ) extends BeautySearchHybridReadiness[IO] {
    override def status(): IO[QueryFailure, BeautySearchHybridReadinessStatus] =
      ZIO.succeed(constant)
  }

  private final class ExpectingDiagnosticsSink(
    expected: BeautySearchHybridDiagnosticsEvent.DecisionEvaluated,
  ) extends BeautySearchHybridDiagnosticsSink[IO] {
    override def report(event: BeautySearchHybridDiagnosticsEvent): IO[Nothing, Unit] =
      event match {
        case actual: BeautySearchHybridDiagnosticsEvent.DecisionEvaluated if actual == expected =>
          ZIO.unit

        case other =>
          ZIO.dieMessage(s"Unexpected diagnostics event: expected $expected, got $other")
      }
  }

  private def runIO[E, A](effect: IO[E, A]): A =
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(effect).getOrThrowFiberFailure()
    }
}
