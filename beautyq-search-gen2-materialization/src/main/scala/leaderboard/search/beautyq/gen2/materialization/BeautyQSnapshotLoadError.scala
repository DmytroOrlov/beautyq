package leaderboard.search.beautyq.gen2.materialization

import leaderboard.model.QueryFailure

/** BeautyQ's own [[leaderboard.search.gen2.core.materialization.SearchSnapshotSource]] load error: wraps
  * repository [[QueryFailure]], so it stays in the BeautyQ package rather than the generic kernel.
  */
sealed trait SnapshotLoadError extends Product with Serializable {
  def message: String
}

object SnapshotLoadError {
  final case class Repository(failure: QueryFailure) extends SnapshotLoadError {
    def message: String = failure.message
  }

  final case class InvalidStoredData(failure: QueryFailure) extends SnapshotLoadError {
    def message: String = failure.message
  }
}
