package leaderboard.search.beautyq.gen2.wiring

/** Serving mode codes: typed, not strings. */
sealed trait BeautyQServingMode {
  def modeCode: String
}

object BeautyQServingMode {
  case object FullSearch extends BeautyQServingMode {
    val modeCode: String = "full_search"
  }
  case object BaselineOnly extends BeautyQServingMode {
    val modeCode: String = "baseline_only"
  }
}

/** Policy-owned readiness results. No public case-class constructor, no apply, no copy, no subclassing.
  * supplementReady is derived from mode, not independently supplied. */
object BeautyQSupplementReadinessPolicy {

  sealed trait Result

  final class NotServing private[BeautyQSupplementReadinessPolicy] (
    val unavailableRequired: Vector[BeautyQSearchDependency],
  ) extends Result
  object NotServing {
    def unapply(r: NotServing): Option[Vector[BeautyQSearchDependency]] =
      Some(r.unavailableRequired)
  }

  final class Serving private[BeautyQSupplementReadinessPolicy] (
    val mode: BeautyQServingMode,
  ) extends Result {
    def supplementReady: Boolean = mode == BeautyQServingMode.FullSearch
  }
  object Serving {
    def unapply(r: Serving): Option[BeautyQServingMode] =
      Some(r.mode)
  }

  /** Evaluate readiness from one set of unavailable dependencies.
    * Derives unavailableRequired once; nonEmpty is the hasRequired fact. */
  def evaluate(
    unavailable: Set[BeautyQSearchDependency],
  ): Result = {
    val unavailableRequired =
      BeautyQSupplementPolicy.requiredDependencies.filter(unavailable.contains)

    if (unavailableRequired.nonEmpty) {
      new NotServing(unavailableRequired)
    } else if (unavailable.contains(BeautyQSupplementPolicy.supplementDependency)) {
      new Serving(BeautyQServingMode.BaselineOnly)
    } else {
      new Serving(BeautyQServingMode.FullSearch)
    }
  }
}
