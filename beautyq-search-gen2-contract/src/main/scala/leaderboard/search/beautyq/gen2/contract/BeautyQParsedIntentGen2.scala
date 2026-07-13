package leaderboard.search.beautyq.gen2.contract

import leaderboard.search.gen2.contract.*

final case class ParsedBeautyIntentGen2(
  normalizedQuery: Option[String],
  hardConstraints: Vector[SourcedConstraint[VariantSearchDocumentGen2]],
  softSignals: Vector[PlannedSignal[VariantSearchDocumentGen2]],
  residualText: Option[String],
  canonicalSemanticLabels: Vector[CanonicalSemanticLabel],
  matchedRuleIds: Vector[IntentRuleId],
)

/** Only errors the implementation can actually produce. A malformed budget token is silently ignored by
  * `extractBudget` (falls through to ordinary phrase matching) rather than rejected, and rule/overlap
  * resolution is fully deterministic (occupied-token protection, no ambiguous state) - so no budget or
  * ambiguity error exists here; adding one back requires a real, deterministic code path that returns
  * it, not speculative vocabulary. */
sealed trait BeautyIntentParseError
object BeautyIntentParseError {
  case object MissingUserLocationForNearUser extends BeautyIntentParseError
}
