package leaderboard.search.beautyq.gen2.contract

/** BeautyQ's plan-compilation mode, classified by [[BeautyQSearchPlanPolicy.classify]] from a plan's
  * already-resolved pieces. Requested facets alone never produce anything but [[DefaultBrowse]]. */
enum BeautyQSearchPlanMode {
  case SemanticSearch
  case StructuredBrowse
  case DefaultBrowse
}
