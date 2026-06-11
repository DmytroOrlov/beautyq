package leaderboard.search.eval

import leaderboard.model.QueryFailure
import leaderboard.search.qdrant.{QdrantEmbeddingBenchmarkQueryResult, QdrantEmbeddingBenchmarkRunOutput}

object EngineEvalReportAssembly {

  def fromOutputs(
    queries: List[BeautySearchEvalQuery],
    expectedRolesByQueryId: Map[String, EngineExpectedRole],
    esReports: List[BeautySearchEvalReport],
    qdrantResults: List[QdrantEmbeddingBenchmarkQueryResult],
  ): Either[QueryFailure, EngineEvalAggregateReport] =
    for {
      esIndex      <- indexByQueryId("esReports", esReports, _.queryId)
      qdrantIndex  <- indexByQueryId("qdrantResults", qdrantResults, _.queryId)
      queryDups     = duplicateIds(queries.map(_.id))
      _            <- Left(failure(s"queries contains duplicate query id(s): ${queryDups.mkString(", ")}")).when(queryDups.nonEmpty)
      queryReports <- queries.traverse { query =>
        for {
          role     <- expectedRolesByQueryId.get(query.id).toRight(failure(s"missing expected role for query id: ${query.id}"))
          esReport <- esIndex.get(query.id).toRight(failure(s"missing ES report for query id: ${query.id}"))
          qResult  <- qdrantIndex.get(query.id).toRight(failure(s"missing Qdrant result for query id: ${query.id}"))
        } yield {
          val es            = EngineEvalResult.fromElasticsearchEvalReport(esReport)
          val qdrant        = EngineEvalResult.fromQdrantBenchmarkResult(qResult)
          val expectedIds   = query.expectedVariantCarousel.acceptableVariantIds.toSet
          EngineEvalQueryReport.from(role, expectedIds, es, qdrant)
        }
      }
    } yield EngineEvalAggregateReport.from(queryReports)

  def fromOutputsForQdrantCandidate(
    queries: List[BeautySearchEvalQuery],
    expectedRolesByQueryId: Map[String, EngineExpectedRole],
    esReports: List[BeautySearchEvalReport],
    qdrantRunOutput: QdrantEmbeddingBenchmarkRunOutput,
    qdrantCandidateId: String,
  ): Either[QueryFailure, EngineEvalAggregateReport] =
    qdrantRunOutput.queryResultsByCandidateId.get(qdrantCandidateId) match {
      case Some(qdrantResults) => fromOutputs(queries, expectedRolesByQueryId, esReports, qdrantResults)
      case None                => Left(failure(s"missing Qdrant benchmark results for candidate id: $qdrantCandidateId"))
    }

  private val OperationName = "engine-eval-report-assembly"

  private def indexByQueryId[A](
    label: String,
    values: List[A],
    queryId: A => String,
  ): Either[QueryFailure, Map[String, A]] = {
    val duplicates = duplicateIds(values.map(queryId))
    if (duplicates.nonEmpty) {
      Left(failure(s"$label contains duplicate query id(s): ${duplicates.mkString(", ")}"))
    } else {
      Right(values.map(value => queryId(value) -> value).toMap)
    }
  }

  private def duplicateIds(ids: List[String]): List[String] =
    ids.groupBy(identity).collect { case (id, matching) if matching.size > 1 => id }.toList.sorted

  private def failure(message: String): QueryFailure =
    QueryFailure.operation(OperationName, message)

  private implicit class EitherOps[L](private val either: Either[L, Unit]) extends AnyVal {
    def when(condition: Boolean): Either[L, Unit] =
      if (condition) either else Right(())
  }

  private implicit class ListTraverseOps[A](private val list: List[A]) extends AnyVal {
    def traverse[L, B](f: A => Either[L, B]): Either[L, List[B]] = {
      val builder = List.newBuilder[B]
      val iter = list.iterator
      while (iter.hasNext) {
        f(iter.next()) match {
          case Left(value)  => return Left(value)
          case Right(value) => builder += value
        }
      }
      Right(builder.result())
    }
  }
}
