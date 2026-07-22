package leaderboard.search.gen2

import distage.Lifecycle
import leaderboard.search.beautyq.gen2.wiring.*
import leaderboard.search.gen2.qdrant.QdrantCandidateService
import zio.IO

/** Single startup owner for the default native BeautyQ Gen2 composition.
   *
   * This owner composes the existing production components into the application/runtime graph that
   * serves `/beauty-search`. It enforces three non-negotiable sequencing facts:
  *
  *   1. The snapshot source and materializer are called exactly once, by the bootstrap.
  *   2. Elasticsearch and Qdrant generations are activated exactly once, by the bootstrap.
  *   3. The runtime is derived from the exact trusted activation the bootstrap returned, never
  *      from a second readiness computation or a second backend activation.
  *
  * The typed bootstrap/lifecycle/activation error is preserved verbatim as
  * `BeautyQSearchGen2StartupFailure.typed`; the resource never converts it to a generic
  * "Gen2 failed" string. The HTTP route is not constructed until the resource acquire succeeds.
  */
final class BeautyQSearchGen2Startup private (
  val bootstrap: BeautyQSearchGen2Bootstrap,
  val application: BeautyQSearchApplication,
  val runtime: BeautyQSearchGen2Runtime,
  val activation: BeautyQSearchGenerationApplication.Activation,
)

/** Throwable carrier that preserves the exact typed
  * [[BeautyQSearchGen2BootstrapError]] in a typed field; downstream code can pattern-match the
  * original error from the failed startup without parsing a string. */
final class BeautyQSearchGen2StartupFailure(val typed: BeautyQSearchGen2BootstrapError)
  extends RuntimeException(BeautyQSearchGen2Startup.renderStartupError(typed)) {
  initCause(BeautyQSearchGen2Startup.failureCause(typed))
}

object BeautyQSearchGen2Startup {
  def acquire(
    bootstrap: BeautyQSearchGen2Bootstrap,
    qdrantCandidateService: QdrantCandidateService,
  ): IO[BeautyQSearchGen2BootstrapError, BeautyQSearchGen2Startup] =
    bootstrap.activate
      .map { activation =>
        val application = BeautyQSearchApplication.make(
          activation.materialized,
          bootstrap.elasticsearchService,
          bootstrap.embeddingPort,
          qdrantCandidateService,
        )
        val runtime = BeautyQSearchGen2Runtime.make(application, activation.readiness)
        new BeautyQSearchGen2Startup(bootstrap, application, runtime, activation)
      }

  def lifecycle(
    bootstrap: BeautyQSearchGen2Bootstrap,
    qdrantCandidateService: QdrantCandidateService,
  ): Lifecycle.LiftF[IO[Throwable, _], BeautyQSearchGen2Startup] =
    new Lifecycle.LiftF[IO[Throwable, _], BeautyQSearchGen2Startup](
      acquire(bootstrap, qdrantCandidateService)
        .mapError(typed => new BeautyQSearchGen2StartupFailure(typed): Throwable)
    )

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
