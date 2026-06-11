package leaderboard.search.elasticsearch

import leaderboard.model.QueryFailure
import leaderboard.search.{BeautySearchBackend, BeautySearchResponse, ParsedSearchIntent, UserSearchInput}
import leaderboard.search.dsl.BeautySearchSpec
import zio.{IO, ZIO}

final class ElasticsearchSearchBackend(
  spec: BeautySearchSpec,
  client: ElasticsearchJsonClient,
) extends BeautySearchBackend[IO] {

  override def search(
    input: UserSearchInput,
    intent: ParsedSearchIntent,
  ): IO[QueryFailure, BeautySearchResponse] =
    for {
      request <- ZIO.fromEither(ElasticsearchSearchRequestInterpreter.request(spec, input, intent))
      rawResponse <- client.postJson(s"/${spec.variantDocument.indexName}/_search", request)
      response <- ZIO.fromEither(ElasticsearchSearchResponseInterpreter.interpret(spec, input, intent, rawResponse))
    } yield response
}
