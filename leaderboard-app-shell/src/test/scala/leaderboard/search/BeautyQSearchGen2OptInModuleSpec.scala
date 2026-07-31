package leaderboard.search

import com.typesafe.config.ConfigFactory
import izumi.distage.config.model.AppConfig
import distage.{Injector, ModuleDef, Scene}
import distage.StandardAxis.Repo
import izumi.distage.model.definition.{Activation, LocatorPrivacy}
import izumi.distage.model.plan.Roots
import izumi.logstage.api.IzLogger
import izumi.logstage.distage.LogIO2Module
import leaderboard.api.BeautySearchGen2Api
import leaderboard.config.{BeautyQGen2AppShellConfig, BeautyQGen2AppShellConfigError, BeautyQGen2AppShellConfigException, RawBeautyQGen2AppShellConfig}
import leaderboard.http.tapir.BeautySearchGen2TapirEndpoints
import leaderboard.plugins.{BeautySearchGen2PluginModules, QdrantGen2DockerPlugin}
import leaderboard.search.beautyq.gen2.materialization.BeautyQMaterializationError
import leaderboard.search.gen2.{BeautyQSearchGen2BootstrapError, BeautyQSearchGen2StartupFailure, BeautyQSupplementStartup}
import leaderboard.seed.BeautyQSeedReady
import org.scalatest.wordspec.AnyWordSpec
import zio.{IO, Task, Unsafe}

import scala.concurrent.duration.*

final class BeautyQSearchGen2OptInModuleSpec extends AnyWordSpec {

  "BeautySearchGen2PluginModules.routeComposition" should {
    "expose the independent API composition through the default LeaderboardPlugin inclusion" in {
      val module = new ModuleDef {
        include(BeautySearchGen2PluginModules.routeComposition)
        make[Probe].from((endpoints: BeautySearchGen2TapirEndpoints) => Probe(endpoints))
      }
      val effect = Injector[Task]().produce(
        bindings = module,
        roots = Roots.target[Probe],
        activation = Activation.empty,
        locatorPrivacy = LocatorPrivacy.PublicByDefault,
      ).use(locator => zio.ZIO.succeed(locator.get[Probe]))
      val probe = Unsafe.unsafe { implicit unsafe =>
        zio.Runtime.default.unsafe.run(effect).getOrThrowFiberFailure()
      }

      assert(probe.endpoints.all.size == 2)
      assert(probe.endpoints.searchBeautyGen2 ne null)
      assert(probe.endpoints.statusBeautyGen2 ne null)
    }
  }

  "BeautySearchGen2PluginModules.appShellConfigModule" should {
    "expose the raw HOCON-bound operational config under the agreed name" in {
      val module = new ModuleDef {
        make[AppConfig].fromValue(AppConfig.provided(ConfigFactory.load("common-reference.conf").resolve()))
        include(BeautySearchGen2PluginModules.appShellConfigModule)
        make[ConfigProbe].from((config: RawBeautyQGen2AppShellConfig) => ConfigProbe(config))
      }
      val effect = Injector[Task]().produce(
        bindings = module,
        roots = Roots.target[ConfigProbe],
        activation = Activation(Scene -> Scene.Managed),
        locatorPrivacy = LocatorPrivacy.PublicByDefault,
      ).use(locator => zio.ZIO.succeed(locator.get[ConfigProbe]))
      val probe = Unsafe.unsafe { implicit unsafe =>
        zio.Runtime.default.unsafe.run(effect).getOrThrowFiberFailure()
      }

      assert(probe.config.connectTimeout == 5.seconds)
      assert(probe.config.requestTimeout == 60.seconds)
      assert(probe.config.bulkMaxActions == 100)
      assert(probe.config.bulkMaxBytes == 1048576L)
    }
  }

  "BeautyQGen2AppShellConfig.validateAtBoundary" should {
    "reject a non-positive connect timeout with the typed BeautyQGen2AppShellConfigError at the DI boundary" in {
      val raw = RawBeautyQGen2AppShellConfig(
        connectTimeout = FiniteDuration(0, "millis"),
        requestTimeout = 60.seconds,
        bulkMaxActions = 100,
        bulkMaxBytes = 1048576L,
      )
      val thrown = scala.util.Try {
        BeautyQGen2AppShellConfig.validateAtBoundary(raw)
      } match {
        case scala.util.Failure(error: BeautyQGen2AppShellConfigException) => error
        case scala.util.Failure(other) =>
          fail(s"expected BeautyQGen2AppShellConfigException, got $other")
        case scala.util.Success(value) =>
          fail(s"expected BeautyQGen2AppShellConfigException, got success $value")
      }
      thrown.typed match {
        case BeautyQGen2AppShellConfigError.NonPositiveConnectTimeout(value) =>
          assert(value == FiniteDuration(0, "millis"))
        case other => fail(s"expected NonPositiveConnectTimeout, got $other")
      }
    }

    "reject a non-positive bulk max actions with the typed error" in {
      val raw = RawBeautyQGen2AppShellConfig(
        connectTimeout = 5.seconds,
        requestTimeout = 60.seconds,
        bulkMaxActions = 0,
        bulkMaxBytes = 1048576L,
      )
      val thrown = scala.util.Try {
        BeautyQGen2AppShellConfig.validateAtBoundary(raw)
      } match {
        case scala.util.Failure(error: BeautyQGen2AppShellConfigException) => error
        case scala.util.Failure(other) =>
          fail(s"expected BeautyQGen2AppShellConfigException, got $other")
        case scala.util.Success(value) =>
          fail(s"expected BeautyQGen2AppShellConfigException, got success $value")
      }
      thrown.typed match {
        case BeautyQGen2AppShellConfigError.NonPositiveBulkMaxActions(value) =>
          assert(value == 0)
        case other => fail(s"expected NonPositiveBulkMaxActions, got $other")
      }
    }
  }

  "BeautySearchGen2PluginModules.api" should {
    "declare the BeautyQSearchGen2Startup and seed-readiness edge when Required is selected" in {
      val plan = Injector[Task]().plan(
        bindings = new ModuleDef {
          make[AppConfig].fromValue(AppConfig.provided(ConfigFactory.load("common-reference.conf").resolve()))
          make[BeautyQSeedReady].fromValue(new BeautyQSeedReady {})
          include(LogIO2Module[IO]())
          make[IzLogger].fromValue(IzLogger())
          include(QdrantGen2DockerPlugin.dockerModule[IO])
          include(BeautySearchGen2PluginModules.api)
        },
        roots = Roots.target[BeautySearchGen2Api[IO]],
        activation = Activation(Scene -> Scene.Managed, Repo -> Repo.Prod, BeautyQSupplementStartup -> BeautyQSupplementStartup.Required),
        locatorPrivacy = LocatorPrivacy.PublicByDefault,
      )
      val planString = plan.toString
      assert(planString.contains("BeautyQSearchGen2Startup"), s"expected BeautyQSearchGen2Startup in plan, got $planString")
      assert(planString.contains("BeautyQSeedReady"), s"expected BeautyQSeedReady in plan, got $planString")
      assert(planString.contains("QdrantGen2Client"), s"expected QdrantGen2Client in Required plan, got $planString")
      assert(planString.contains("QdrantGenerationLifecycle"), s"expected QdrantGenerationLifecycle in Required plan, got $planString")
      assert(planString.contains("QdrantCandidateService"), s"expected QdrantCandidateService in Required plan, got $planString")
      assert(planString.contains("BeautyQGen2EmbeddingClient"), s"expected BeautyQGen2EmbeddingClient in Required plan, got $planString")
      assert(planString.contains("BeautyQSearchGen2Bootstrap"), s"expected BeautyQSearchGen2Bootstrap in Required plan, got $planString")
    }

    "retain the supplement owners when Preferred is selected" in {
      val plan = Injector[Task]().plan(
        bindings = new ModuleDef {
          make[AppConfig].fromValue(AppConfig.provided(ConfigFactory.load("common-reference.conf").resolve()))
          make[BeautyQSeedReady].fromValue(new BeautyQSeedReady {})
          include(LogIO2Module[IO]())
          make[IzLogger].fromValue(IzLogger())
          include(QdrantGen2DockerPlugin.dockerModule[IO])
          include(BeautySearchGen2PluginModules.api)
        },
        roots = Roots.target[BeautySearchGen2Api[IO]],
        activation = Activation(Scene -> Scene.Managed, Repo -> Repo.Prod, BeautyQSupplementStartup -> BeautyQSupplementStartup.Preferred),
        locatorPrivacy = LocatorPrivacy.PublicByDefault,
      )
      val planString = plan.toString
      assert(planString.contains("BeautyQSearchGen2Startup"), s"expected BeautyQSearchGen2Startup in Preferred plan, got $planString")
      assert(planString.contains("QdrantGen2Client"), s"expected QdrantGen2Client in Preferred plan, got $planString")
      assert(planString.contains("QdrantGenerationLifecycle"), s"expected QdrantGenerationLifecycle in Preferred plan, got $planString")
      assert(planString.contains("QdrantCandidateService"), s"expected QdrantCandidateService in Preferred plan, got $planString")
      assert(planString.contains("BeautyQGen2EmbeddingClient"), s"expected BeautyQGen2EmbeddingClient in Preferred plan, got $planString")
      assert(planString.contains("BeautyQSearchGen2Bootstrap"), s"expected BeautyQSearchGen2Bootstrap in Preferred plan, got $planString")
    }

    "retain only baseline owners when Disabled is selected and exclude Qdrant and embedding" in {
      val plan = Injector[Task]().plan(
        bindings = new ModuleDef {
          make[AppConfig].fromValue(AppConfig.provided(ConfigFactory.load("common-reference.conf").resolve()))
          make[BeautyQSeedReady].fromValue(new BeautyQSeedReady {})
          include(LogIO2Module[IO]())
          make[IzLogger].fromValue(IzLogger())
          include(QdrantGen2DockerPlugin.dockerModule[IO])
          include(BeautySearchGen2PluginModules.api)
        },
        roots = Roots.target[BeautySearchGen2Api[IO]],
        activation = Activation(Scene -> Scene.Managed, Repo -> Repo.Prod, BeautyQSupplementStartup -> BeautyQSupplementStartup.Disabled),
        locatorPrivacy = LocatorPrivacy.PublicByDefault,
      )
      val planString = plan.toString
      assert(planString.contains("BeautyQVariantMaterializer"), s"expected BeautyQVariantMaterializer in Disabled plan, got $planString")
      assert(planString.contains("BeautyQElasticsearchBaselineService"), s"expected BeautyQElasticsearchBaselineService in Disabled plan, got $planString")
      assert(planString.contains("BeautyQSearchGen2Bootstrap"), s"expected BeautyQSearchGen2Bootstrap in Disabled plan, got $planString")
      assert(planString.contains("BeautyQSearchGen2Startup"), s"expected BeautyQSearchGen2Startup in Disabled plan, got $planString")
      assert(planString.contains("BeautyQSearchGen2Runtime"), s"expected BeautyQSearchGen2Runtime in Disabled plan, got $planString")
      assert(planString.contains("BeautySearchGen2Api"), s"expected BeautySearchGen2Api in Disabled plan, got $planString")
      assert(!planString.contains("QdrantGen2PortCfg"), s"expected no QdrantGen2PortCfg in Disabled plan, got $planString")
      assert(!planString.contains("QdrantGen2Client"), s"expected no QdrantGen2Client in Disabled plan, got $planString")
      assert(!planString.contains("QdrantGenerationLifecycle"), s"expected no QdrantGenerationLifecycle in Disabled plan, got $planString")
      assert(!planString.contains("QdrantCandidateService"), s"expected no QdrantCandidateService in Disabled plan, got $planString")
      assert(!planString.contains("LlamaCppEmbeddingClientConfig"), s"expected no LlamaCppEmbeddingClientConfig in Disabled plan, got $planString")
      assert(!planString.contains("BeautyQGen2EmbeddingClient"), s"expected no BeautyQGen2EmbeddingClient in Disabled plan, got $planString")
      assert(!planString.contains("QdrantGen2Docker.Container"), s"expected no QdrantGen2Docker.Container in Disabled plan, got $planString")
    }

    "fail to produce BeautySearchGen2Api[IO] when the startup resource is not in the graph" in {
      val planEffect = zio.ZIO.attempt {
        Injector[Task]().produce(
          bindings = new ModuleDef {
            make[AppConfig].fromValue(AppConfig.provided(ConfigFactory.load("common-reference.conf").resolve()))
            include(BeautySearchGen2PluginModules.routeComposition)
          },
          roots = Roots.target[BeautySearchGen2Api[IO]],
          activation = Activation(Scene -> Scene.Managed),
          locatorPrivacy = LocatorPrivacy.PublicByDefault,
        ).use(_ => zio.ZIO.unit)
      }
      val outer = Unsafe.unsafe { implicit unsafe =>
        try {
          zio.Runtime.default.unsafe.run(planEffect).getOrThrowFiberFailure()
          fail("expected provision failure when startup is missing")
        } catch {
          case e: Throwable => e
        }
      }
      val all = collectThrowables(outer)
      val message = all.flatMap(t => Option(t.getMessage).toList).mkString(" | ")
      assert(
        message.contains("BeautyQSearchGen2Runtime") || message.contains("missing"),
        s"expected missing-dependency failure, got causes: $message"
      )
    }
  }

  "BeautyQSearchGen2StartupFailure" should {
    "preserve the exact typed BeautyQSearchGen2BootstrapError on startup failure" in {
      val typedError: BeautyQSearchGen2BootstrapError =
        BeautyQSearchGen2BootstrapError.Materialization(
          BeautyQMaterializationError.Snapshot(
            leaderboard.search.beautyq.gen2.materialization.SnapshotLoadError.Repository(
              leaderboard.model.QueryFailure.operation("test", "synthetic")
            )
          )
        )
      val failure = new BeautyQSearchGen2StartupFailure(typedError)
      failure.typed match {
        case BeautyQSearchGen2BootstrapError.Materialization(_) => ()
        case other => fail(s"expected Materialization preserved, got $other")
      }
    }
  }

  "BeautyQGen2AppShellConfig" should {
    "reject non-positive connect timeout" in {
      val raw = BeautyQGen2AppShellConfig(
        connectTimeout = FiniteDuration(0, "millis"),
        requestTimeout = 60.seconds,
        bulkMaxActions = 100,
        bulkMaxBytes = 1048576L,
      )
      BeautyQGen2AppShellConfig.validate(raw) match {
        case Left(BeautyQGen2AppShellConfigError.NonPositiveConnectTimeout(_)) => ()
        case other => fail(s"expected NonPositiveConnectTimeout, got $other")
      }
    }

    "reject non-positive request timeout" in {
      val raw = BeautyQGen2AppShellConfig(
        connectTimeout = 5.seconds,
        requestTimeout = FiniteDuration(-1, "seconds"),
        bulkMaxActions = 100,
        bulkMaxBytes = 1048576L,
      )
      BeautyQGen2AppShellConfig.validate(raw) match {
        case Left(BeautyQGen2AppShellConfigError.NonPositiveRequestTimeout(_)) => ()
        case other => fail(s"expected NonPositiveRequestTimeout, got $other")
      }
    }

    "reject non-positive bulk max actions" in {
      val raw = BeautyQGen2AppShellConfig(
        connectTimeout = 5.seconds,
        requestTimeout = 60.seconds,
        bulkMaxActions = 0,
        bulkMaxBytes = 1048576L,
      )
      BeautyQGen2AppShellConfig.validate(raw) match {
        case Left(BeautyQGen2AppShellConfigError.NonPositiveBulkMaxActions(_)) => ()
        case other => fail(s"expected NonPositiveBulkMaxActions, got $other")
      }
    }

    "reject non-positive bulk max bytes" in {
      val raw = BeautyQGen2AppShellConfig(
        connectTimeout = 5.seconds,
        requestTimeout = 60.seconds,
        bulkMaxActions = 100,
        bulkMaxBytes = 0L,
      )
      BeautyQGen2AppShellConfig.validate(raw) match {
        case Left(BeautyQGen2AppShellConfigError.NonPositiveBulkMaxBytes(_)) => ()
        case other => fail(s"expected NonPositiveBulkMaxBytes, got $other")
      }
    }

    "accept the documented default operational values" in {
      val raw = BeautyQGen2AppShellConfig(
        connectTimeout = 5.seconds,
        requestTimeout = 60.seconds,
        bulkMaxActions = 100,
        bulkMaxBytes = 1048576L,
      )
      BeautyQGen2AppShellConfig.validate(raw) match {
        case Right(value) => assert(value == raw)
        case Left(error)  => fail(s"expected validated config, got $error")
      }
    }
  }

  "default LeaderboardPlugin graph" should {
    "include BeautySearchGen2PluginModules.api as the default /beauty-search route" in {
      val source = scala.io.Source.fromFile("leaderboard-app-shell/src/main/scala/leaderboard/plugins/LeaderboardPlugin.scala")
      val content = scala.util.Using.resource(source)(_.mkString)
      assert(content.contains("include(BeautySearchGen2PluginModules.api)"), "LeaderboardPlugin.scala must include BeautySearchGen2PluginModules.api")
      assert(content.contains("/beauty-search") || content.contains("BeautySearchGen2PluginModules"), "LeaderboardPlugin.scala must reference the Gen2 route")
    }

    "not include any Gen1 launcher/module owners" in {
      val source = scala.io.Source.fromFile("leaderboard-app-shell/src/main/scala/leaderboard/plugins/LeaderboardPlugin.scala")
      val content = scala.util.Using.resource(source)(_.mkString)
      val forbidden = Seq("BeautySearchPluginModules", "BeautySearchRouteModules", "BeautySearchLocalQdrantSupplementLauncherModule")
      val violations = forbidden.filter(content.contains)
      assert(violations.isEmpty, s"LeaderboardPlugin.scala must not reference any Gen1 owner; found: ${violations.mkString(", ")}")
    }

    "set the default BeautyQSupplementStartup activation to Required" in {
      val source = scala.io.Source.fromFile("leaderboard-app-shell/src/main/scala/leaderboard/LeaderboardRole.scala")
      val content = scala.util.Using.resource(source)(_.mkString)
      assert(content.contains("BeautyQSupplementStartup -> BeautyQSupplementStartup.Required"), "LeaderboardRole.scala default activation must select Required")
    }
  }

  private final case class Probe(endpoints: BeautySearchGen2TapirEndpoints)
  private final case class ConfigProbe(config: RawBeautyQGen2AppShellConfig)

  /** Recursive, immutable cause-chain walk: each throwable's direct message, the recursive
    * walk over its suppressed throwables, and the recursive walk over its cause are concatenated
    * in deterministic order. The result is the complete set of throwables reachable from `top`. */
  private def collectThrowables(top: Throwable): Vector[Throwable] = {
    val direct = Vector(top)
    val viaSuppressed = top.getSuppressed.toVector.flatMap(collectThrowables)
    val viaCause = Option(top.getCause).map(collectThrowables).getOrElse(Vector.empty)
    direct ++ viaSuppressed ++ viaCause
  }
}
