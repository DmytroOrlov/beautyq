package leaderboard.config

import scala.concurrent.duration.FiniteDuration

/** App-shell operational runtime mechanics for the opt-in BeautyQ Gen2 composition.
  *
  * This configuration is read exclusively from the app-shell graph: the BeautyQ Gen2 wiring never
  * owns the underlying numeric/duration values. All values are strictly validated; an empty or
  * non-positive duration or non-positive batch limit is rejected with a typed deterministic error.
  *
  * It owns transport timeouts and bulk batching limits used at the application boundary. It does
  * not encode BeautyQ business policy: search-plan mechanics, supplement selection, candidate
  * membership, lifecycle state, and readiness policy remain owned by the BeautyQ Gen2 wiring
  * layer and are out of scope here.
  */
final case class BeautyQGen2AppShellConfig(
  connectTimeout: FiniteDuration,
  requestTimeout: FiniteDuration,
  bulkMaxActions: Int,
  bulkMaxBytes: Long,
)

sealed trait BeautyQGen2AppShellConfigError
object BeautyQGen2AppShellConfigError {
  final case class NonPositiveConnectTimeout(value: FiniteDuration) extends BeautyQGen2AppShellConfigError
  final case class NonPositiveRequestTimeout(value: FiniteDuration) extends BeautyQGen2AppShellConfigError
  final case class NonPositiveBulkMaxActions(value: Int) extends BeautyQGen2AppShellConfigError
  final case class NonPositiveBulkMaxBytes(value: Long) extends BeautyQGen2AppShellConfigError
}

/** Raw HOCON-bound config; reads from the configuration tree before any positive-value validation
  * is applied. Used as the distage-config reader type so validation runs at the DI boundary. */
final case class RawBeautyQGen2AppShellConfig(
  connectTimeout: FiniteDuration,
  requestTimeout: FiniteDuration,
  bulkMaxActions: Int,
  bulkMaxBytes: Long,
) {
  def toValidated: BeautyQGen2AppShellConfig =
    BeautyQGen2AppShellConfig(connectTimeout, requestTimeout, bulkMaxActions, bulkMaxBytes)
}

object BeautyQGen2AppShellConfig {
  def validate(raw: BeautyQGen2AppShellConfig): Either[BeautyQGen2AppShellConfigError, BeautyQGen2AppShellConfig] = {
    if (raw.connectTimeout <= FiniteDuration(0, "millis")) Left(BeautyQGen2AppShellConfigError.NonPositiveConnectTimeout(raw.connectTimeout))
    else if (raw.requestTimeout <= FiniteDuration(0, "millis")) Left(BeautyQGen2AppShellConfigError.NonPositiveRequestTimeout(raw.requestTimeout))
    else if (raw.bulkMaxActions <= 0) Left(BeautyQGen2AppShellConfigError.NonPositiveBulkMaxActions(raw.bulkMaxActions))
    else if (raw.bulkMaxBytes <= 0L) Left(BeautyQGen2AppShellConfigError.NonPositiveBulkMaxBytes(raw.bulkMaxBytes))
    else Right(raw)
  }

  /** Validation invoked at the DI boundary; failure surfaces the typed
    * [[BeautyQGen2AppShellConfigError]] in [[BeautyQGen2AppShellConfigException]] rather than the
    * distage default error. The validated config is the only thing downstream bindings depend on. */
  def validateAtBoundary(raw: RawBeautyQGen2AppShellConfig): BeautyQGen2AppShellConfig =
    validate(raw.toValidated) match {
      case Right(validated) => validated
      case Left(error) => throw new BeautyQGen2AppShellConfigException(error)
    }
}

/** Throwable carrying the exact typed [[BeautyQGen2AppShellConfigError]]; downstream code can
  * pattern-match the cause. */
final class BeautyQGen2AppShellConfigException(val typed: BeautyQGen2AppShellConfigError)
  extends RuntimeException(s"beautyq-gen2-app-shell config invalid: $typed")
