package leaderboard.search.gen2.elasticsearch

import leaderboard.search.gen2.core.supplement.BaselineMembershipResult
import leaderboard.search.gen2.elasticsearch.lifecycle.*

sealed trait ElasticsearchBaselineServiceError
object ElasticsearchBaselineServiceError {
  final case class Lifecycle(error: ElasticsearchGenerationLifecycleError) extends ElasticsearchBaselineServiceError
  final case class Transport(error: ElasticsearchGen2TransportError) extends ElasticsearchBaselineServiceError
  final case class Response(errors: ElasticsearchSearchResponseErrors) extends ElasticsearchBaselineServiceError
  final case class Groups(error: ElasticsearchGroupExecutionError) extends ElasticsearchBaselineServiceError
}

sealed trait ElasticsearchBaselineMembershipError
object ElasticsearchBaselineMembershipError {
  final case class Compile(error: ElasticsearchBaselineMembershipCompileError) extends ElasticsearchBaselineMembershipError
  final case class Transport(error: ElasticsearchGen2TransportError) extends ElasticsearchBaselineMembershipError
  final case class Response(error: ElasticsearchBaselineMembershipResponseError) extends ElasticsearchBaselineMembershipError
}

final class BoundElasticsearchBaselineResult[Document, Id] private[elasticsearch] (
  private[elasticsearch] val authorizedRequest: AuthorizedElasticsearchSearchRequest[Document, Id],
  val result: ElasticsearchFullSearchResult[Document, Id],
) {
  def target: ElasticsearchSearchTarget = authorizedRequest.target
  def generationReference: ElasticsearchGenerationReference = authorizedRequest.generationReference
}

final class ElasticsearchBaselineService(lifecycle: ElasticsearchGenerationLifecycle) {

  def searchBound[Document, Id](
    prepared: PreparedElasticsearchSearchRequest[Document, Id],
  ): Either[ElasticsearchBaselineServiceError, BoundElasticsearchBaselineResult[Document, Id]] =
    for {
      authorized <- lifecycle.authorize(prepared).left.map(ElasticsearchBaselineServiceError.Lifecycle.apply)
      response <- lifecycle.client.postJson(s"/${authorized.target.value}/_search", authorized.body).left.map(ElasticsearchBaselineServiceError.Transport.apply)
      page <- ElasticsearchSearchResponseDecoder.decode(authorized, response).left.map(ElasticsearchBaselineServiceError.Response.apply)
      groups <- ElasticsearchGroupExecutor.execute(lifecycle.client, authorized).left.map(ElasticsearchBaselineServiceError.Groups.apply)
    } yield new BoundElasticsearchBaselineResult(authorized, new ElasticsearchFullSearchResult(page, groups))

  def search[Document, Id](
    prepared: PreparedElasticsearchSearchRequest[Document, Id],
  ): Either[ElasticsearchBaselineServiceError, ElasticsearchFullSearchResult[Document, Id]] =
    searchBound(prepared).map(_.result)

  def membership[Document, Id](
    baseline: BoundElasticsearchBaselineResult[Document, Id],
    candidateIds: Vector[Id],
  ): Either[ElasticsearchBaselineMembershipError, BaselineMembershipResult[Id]] =
    if (candidateIds.isEmpty)
      Right(BaselineMembershipResult.fromBackend(Vector.empty, Vector.empty))
    else
      for {
        compiled <- ElasticsearchBaselineMembershipCompiler.compile(baseline, candidateIds)
          .left.map(ElasticsearchBaselineMembershipError.Compile.apply)
        response <- lifecycle.client.postJson(s"/${compiled.target.value}/_search", compiled.body)
          .left.map(ElasticsearchBaselineMembershipError.Transport.apply)
        decoded <- ElasticsearchBaselineMembershipDecoder.decode(compiled, response)
          .left.map(ElasticsearchBaselineMembershipError.Response.apply)
      } yield decoded
}
