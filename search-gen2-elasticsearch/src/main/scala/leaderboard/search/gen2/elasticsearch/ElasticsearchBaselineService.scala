package leaderboard.search.gen2.elasticsearch

import leaderboard.search.gen2.elasticsearch.lifecycle.*

sealed trait ElasticsearchBaselineServiceError
object ElasticsearchBaselineServiceError {
  final case class Lifecycle(error: ElasticsearchGenerationLifecycleError) extends ElasticsearchBaselineServiceError
  final case class Transport(error: ElasticsearchGen2TransportError) extends ElasticsearchBaselineServiceError
  final case class Response(errors: ElasticsearchSearchResponseErrors) extends ElasticsearchBaselineServiceError
}

final class ElasticsearchBaselineService(lifecycle: ElasticsearchGenerationLifecycle) {
  def search[Document, Id](
    prepared: PreparedElasticsearchSearchRequest[Document, Id],
  ): Either[ElasticsearchBaselineServiceError, BaselineSearchPage[Document, Id]] =
    for {
      authorized <- lifecycle.authorize(prepared).left.map(ElasticsearchBaselineServiceError.Lifecycle.apply)
      response <- lifecycle.client.postJson(s"/${authorized.target.value}/_search", authorized.body).left.map(ElasticsearchBaselineServiceError.Transport.apply)
      page <- ElasticsearchSearchResponseDecoder.decode(authorized, response).left.map(ElasticsearchBaselineServiceError.Response.apply)
    } yield page
}
