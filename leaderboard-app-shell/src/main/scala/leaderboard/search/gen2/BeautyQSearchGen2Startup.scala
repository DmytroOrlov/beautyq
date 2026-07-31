package leaderboard.search.gen2

import distage.Lifecycle
import leaderboard.search.beautyq.gen2.wiring.*
import leaderboard.search.beautyq.gen2.wiring.SupplementStartupPolicy.{Disabled, Preferred, Required}
import leaderboard.search.gen2.qdrant.QdrantCandidateService
import logstage.LogIO2
import zio.{IO, ZIO}

final class BeautyQSearchGen2Startup private (
  val bootstrap: BeautyQSearchGen2Bootstrap,
  val application: BeautyQSearchApplication,
  val runtime: BeautyQSearchGen2Runtime,
  val activation: BeautyQSearchGenerationApplication.Activation,
  val status: StartupServingStatus,
)

final class BeautyQSearchGen2StartupFailure(val typed: BeautyQSearchGen2BootstrapError)
  extends RuntimeException(BeautyQSearchGen2Startup.renderStartupError(typed)) {
  initCause(BeautyQSearchGen2Startup.failureCause(typed))
}

object BeautyQSearchGen2Startup {
  def acquire(
    bootstrap: BeautyQSearchGen2Bootstrap,
    qdrantCandidateService: QdrantCandidateService,
    log: LogIO2[IO],
  ): IO[BeautyQSearchGen2BootstrapError, BeautyQSearchGen2Startup] =
    bootstrap.activate
      .flatMap { activation =>
        val policy = bootstrap.supplementStartupPolicy
        val startupResult: Either[BeautyQSearchGen2BootstrapError, (BeautyQSearchGen2Startup, IO[Nothing, Unit])] = policy match {
          case Required =>
            activation.qdrantFailure match {
              case Some(error) => Left(BeautyQSearchGen2BootstrapError.Activation(error))
              case None => Right(buildHealthyStartup(bootstrap, qdrantCandidateService, activation, log))
            }
          case Preferred =>
            activation.qdrantFailure match {
              case Some(error) =>
                BeautyQSupplementPolicy.classifyStartupActivationError(error) match {
                  case deg: BeautyQSupplementPolicy.StartupDegradable =>
                    Right(buildDegradedStartup(bootstrap, activation, deg, log))
                  case _: BeautyQSupplementPolicy.StartupHard =>
                    Left(BeautyQSearchGen2BootstrapError.Activation(error))
                }
              case None => Right(buildHealthyStartup(bootstrap, qdrantCandidateService, activation, log))
            }
          case Disabled =>
            Right(buildLimitedStartup(bootstrap, activation, log))
        }
        startupResult match {
          case Left(error) => ZIO.fail(error)
          case Right((startup, logEffect)) =>
            logEffect.as(startup)
        }
      }

  private def buildHealthyStartup(
    bootstrap: BeautyQSearchGen2Bootstrap,
    qdrantCandidateService: QdrantCandidateService,
    activation: BeautyQSearchGenerationApplication.Activation,
    log: LogIO2[IO],
  ): (BeautyQSearchGen2Startup, IO[Nothing, Unit]) = {
    val qdrantGen = activation.qdrantGeneration.getOrElse(throw new IllegalStateException("healthy activation must have Qdrant generation"))
    val status = StartupServingStatus.healthy(
      bootstrap.supplementStartupPolicy,
      activation.materialized,
      activation.elasticsearchGeneration,
      qdrantGen,
    )
    val startup = constructStartup(bootstrap, qdrantCandidateService, activation, status)
    val startupPolicy = status.policy.stableCode
    val servingMode = status.servingMode.modeCode
    val condition = status.condition
    val restartRequired = status.restartRequired
    val logEffect = log.info(
      s"beauty_search_startup_serving event=beauty_search_startup_serving startupPolicy=$startupPolicy servingMode=$servingMode condition=$condition reasonCode=none restartRequired=$restartRequired"
    )
    (startup, logEffect)
  }

  private def buildDegradedStartup(
    bootstrap: BeautyQSearchGen2Bootstrap,
    activation: BeautyQSearchGenerationApplication.Activation,
    disposition: BeautyQSupplementPolicy.StartupDegradable,
    log: LogIO2[IO],
  ): (BeautyQSearchGen2Startup, IO[Nothing, Unit]) = {
    val status = StartupServingStatus.degraded(
      bootstrap.supplementStartupPolicy,
      activation.materialized,
      activation.elasticsearchGeneration,
      "qdrant_supplement_unavailable",
      "Qdrant supplement was unavailable at startup; the complete Elasticsearch baseline was returned; restart is required",
      sanitizedDetail(disposition.cause),
      disposition.cause,
    )
    val startup = constructBaselineStartup(bootstrap, activation, status)
    val startupPolicy = status.policy.stableCode
    val servingMode = status.servingMode.modeCode
    val condition = status.condition
    val reasonCode = status.reason.map(_.code).getOrElse("none")
    val restartRequired = status.restartRequired
    val logEffect = log.warn(
      s"beauty_search_startup_serving event=beauty_search_startup_serving startupPolicy=$startupPolicy servingMode=$servingMode condition=$condition reasonCode=$reasonCode restartRequired=$restartRequired"
    )
    (startup, logEffect)
  }

  private def buildLimitedStartup(
    bootstrap: BeautyQSearchGen2Bootstrap,
    activation: BeautyQSearchGenerationApplication.Activation,
    log: LogIO2[IO],
  ): (BeautyQSearchGen2Startup, IO[Nothing, Unit]) = {
    val status = StartupServingStatus.limited(
      bootstrap.supplementStartupPolicy,
      activation.materialized,
      activation.elasticsearchGeneration,
      "qdrant_supplement_operator_disabled",
      "Qdrant supplement was disabled by operator policy; the complete Elasticsearch baseline was returned; restart is required",
      "supplement disabled by operator startup policy",
    )
    val startup = constructBaselineStartup(bootstrap, activation, status)
    val startupPolicy = status.policy.stableCode
    val servingMode = status.servingMode.modeCode
    val condition = status.condition
    val reasonCode = status.reason.map(_.code).getOrElse("none")
    val restartRequired = status.restartRequired
    val logEffect = log.warn(
      s"beauty_search_startup_serving event=beauty_search_startup_serving startupPolicy=$startupPolicy servingMode=$servingMode condition=$condition reasonCode=$reasonCode restartRequired=$restartRequired"
    )
    (startup, logEffect)
  }

  private def constructStartup(
    bootstrap: BeautyQSearchGen2Bootstrap,
    qdrantCandidateService: QdrantCandidateService,
    activation: BeautyQSearchGenerationApplication.Activation,
    status: StartupServingStatus,
  ): BeautyQSearchGen2Startup = {
    val application = BeautyQSearchApplication.make(
      activation.materialized,
      bootstrap.elasticsearchService,
      bootstrap.embeddingPort.getOrElse(throw new IllegalStateException("embedding port must be present for full search startup")),
      qdrantCandidateService,
    )
    val runtime = BeautyQSearchGen2Runtime.make(application, status)
    new BeautyQSearchGen2Startup(bootstrap, application, runtime, activation, status)
  }

  private def constructBaselineStartup(
    bootstrap: BeautyQSearchGen2Bootstrap,
    activation: BeautyQSearchGenerationApplication.Activation,
    status: StartupServingStatus,
  ): BeautyQSearchGen2Startup = {
    val application = BeautyQSearchApplication.makeBaselineOnly(
      activation.materialized,
      bootstrap.elasticsearchService,
    )
    val runtime = BeautyQSearchGen2Runtime.make(application, status)
    new BeautyQSearchGen2Startup(bootstrap, application, runtime, activation, status)
  }

  private def sanitizedDetail(error: BeautyQSearchGenerationActivationError): String = error match {
    case BeautyQSearchGenerationActivationError.Qdrant(qdrantError) =>
      s"Qdrant lifecycle error: ${qdrantError.getClass.getSimpleName}"
    case BeautyQSearchGenerationActivationError.Embedding(embeddingError) =>
      s"Embedding error: ${embeddingError.getClass.getSimpleName}"
    case other =>
      s"Activation error: ${other.getClass.getSimpleName}"
  }

  def lifecycle(
    bootstrap: BeautyQSearchGen2Bootstrap,
    qdrantCandidateService: QdrantCandidateService,
    log: LogIO2[IO],
  ): Lifecycle.LiftF[IO[Throwable, _], BeautyQSearchGen2Startup] =
    new Lifecycle.LiftF[IO[Throwable, _], BeautyQSearchGen2Startup](
      acquire(bootstrap, qdrantCandidateService, log)
        .mapError(typed => new BeautyQSearchGen2StartupFailure(typed): Throwable)
    )

  def lifecycleBaselineOnly(
    bootstrap: BeautyQSearchGen2Bootstrap,
    log: LogIO2[IO],
  ): Lifecycle.LiftF[IO[Throwable, _], BeautyQSearchGen2Startup] =
    new Lifecycle.LiftF[IO[Throwable, _], BeautyQSearchGen2Startup](
      acquireBaselineOnly(bootstrap, log)
        .mapError(typed => new BeautyQSearchGen2StartupFailure(typed): Throwable)
    )

  private def acquireBaselineOnly(
    bootstrap: BeautyQSearchGen2Bootstrap,
    log: LogIO2[IO],
  ): IO[BeautyQSearchGen2BootstrapError, BeautyQSearchGen2Startup] =
    bootstrap.activate
      .flatMap { activation =>
        val (startup, logEffect) = buildLimitedStartup(bootstrap, activation, log)
        logEffect.as(startup)
      }

  private[gen2] def renderStartupError(error: BeautyQSearchGen2BootstrapError): String = error match {
    case BeautyQSearchGen2BootstrapError.Materialization(cause) =>
      s"beauty-search startup materialization failed: ${cause.toString}"
    case BeautyQSearchGen2BootstrapError.Activation(cause) =>
      s"beauty-search startup activation failed: ${cause.toString}"
    case BeautyQSearchGen2BootstrapError.Blocking(cause) =>
      s"beauty-search startup blocking failed: ${cause.toString}"
  }

  private[gen2] def failureCause(error: BeautyQSearchGen2BootstrapError): Throwable = error match {
    case BeautyQSearchGen2BootstrapError.Materialization(cause)  => new BootstrapErrorCause(s"materialization: $cause")
    case BeautyQSearchGen2BootstrapError.Activation(cause)       => new BootstrapErrorCause(s"activation: $cause")
    case BeautyQSearchGen2BootstrapError.Blocking(cause)        => cause
  }

  private final class BootstrapErrorCause(message: String) extends RuntimeException(message)
}
