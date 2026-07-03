package leaderboard.api

// Narrow, disabled-by-default runtime serving gate for the existing ES-backed `/beauty-search` route.
//
// Semantics:
//   - disabled            -> gate is inert; existing `/beauty-search` behavior is unchanged.
//   - enabled & not ready -> valid `POST /beauty-search` is rejected with HTTP 503.
//   - enabled & ready     -> existing ES-backed `/beauty-search` behavior.
//
// This is an explicit data-only gate. It does not switch the default backend, activate Qdrant,
// introduce hybrid serving / fallback / fusion / reranking, or add request-time telemetry.
final case class BeautySearchServingGate(
  enabled: Boolean,
  servingReady: Boolean,
) {
  // When disabled, the gate never rejects: behavior is identical to the un-gated route.
  // When enabled, the gate rejects serving until serving readiness is satisfied.
  def rejectsServing: Boolean = enabled && !servingReady
}

object BeautySearchServingGate {
  // Default production binding: the gate is disabled, so `/beauty-search` behavior is unchanged.
  val disabled: BeautySearchServingGate =
    BeautySearchServingGate(enabled = false, servingReady = false)

  // Explicitly enabled but serving readiness is not satisfied -> valid requests return HTTP 503.
  val enabledNotReady: BeautySearchServingGate =
    BeautySearchServingGate(enabled = true, servingReady = false)

  // Explicitly enabled and serving readiness satisfied -> existing ES-backed route behavior.
  val enabledReady: BeautySearchServingGate =
    BeautySearchServingGate(enabled = true, servingReady = true)
}
