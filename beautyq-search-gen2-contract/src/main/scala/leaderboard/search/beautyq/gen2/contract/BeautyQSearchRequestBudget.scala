package leaderboard.search.beautyq.gen2.contract

/** BeautyQ-owned public request resource policy. Transport and contract layers enforce these values;
  * generic Search Gen2 types deliberately provide no domain maximums. */
object BeautyQSearchRequestBudget {
  val MaxTransportBodyBytes: Int = 65536
  val MaxQueryCodePoints: Int = 512
  val MaxCursorUtf8Bytes: Int = 16384
  val MaxFilters: Int = 16
  val MaxRequestedFacets: Int = 16
  val MaxSorts: Int = 4
  val MaxPageSize: Int = 100
}
