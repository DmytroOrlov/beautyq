package leaderboard.search.qdrant

import leaderboard.model.QueryFailure
import leaderboard.search.eval.BeautySearchEvalQuery

final case class QdrantEmbeddingBenchmarkQuerySubset(
  id: String,
  description: String,
  queryIds: List[String] = Nil,
  queryTypes: List[String] = Nil,
  maxQueries: Option[Int] = None,
)

object QdrantEmbeddingBenchmarkQuerySubset {
  private val OperationName = "qdrant-embedding-benchmark-query-subset"

  val SemanticBroadSmoke: QdrantEmbeddingBenchmarkQuerySubset =
    QdrantEmbeddingBenchmarkQuerySubset(
      id = "semantic-broad-smoke",
      description = "Non-production broad semantic benchmark/eval smoke subset for named embedding comparisons.",
      queryIds = List(
        "q_broad_001",
        "q_broad_002",
        "q_broad_003",
        "q_broad_004",
        "q_broad_005",
        "q_broad_006",
      ),
    )

  def select(
    subset: QdrantEmbeddingBenchmarkQuerySubset,
    queries: List[BeautySearchEvalQuery],
  ): Either[QueryFailure, List[BeautySearchEvalQuery]] = {
    val missingQueryIds = subset.queryIds.distinct.filterNot(requestedId => queries.exists(_.id == requestedId))

    if (missingQueryIds.nonEmpty) {
      Left(
        QueryFailure.operation(
          OperationName,
          s"Subset ${subset.id} references missing query ids: ${missingQueryIds.mkString(", ")}",
        )
      )
    } else {
      val requestedIds = subset.queryIds.toSet
      val requestedTypes = subset.queryTypes.toSet
      val filtered = queries.filter(query => requestedIds(query.id) || query.queryTypes.exists(requestedTypes))
      val deduplicated = deduplicateById(filtered)
      val limited = subset.maxQueries match {
        case Some(value) => deduplicated.take(math.max(value, 0))
        case None        => deduplicated
      }

      limited match {
        case Nil =>
          Left(
            QueryFailure.operation(
              OperationName,
              s"Subset ${subset.id} selected no queries",
            )
          )
        case _ =>
          Right(limited)
      }
    }
  }

  private def deduplicateById(
    queries: List[BeautySearchEvalQuery]
  ): List[BeautySearchEvalQuery] =
    queries.foldLeft((Set.empty[String], List.empty[BeautySearchEvalQuery])) {
      case ((seenIds, acc), query) if seenIds(query.id) =>
        (seenIds, acc)
      case ((seenIds, acc), query) =>
        (seenIds + query.id, acc :+ query)
    }._2
}
