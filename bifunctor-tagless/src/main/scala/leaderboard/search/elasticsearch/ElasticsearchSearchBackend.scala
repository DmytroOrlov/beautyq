package leaderboard.search.elasticsearch

import leaderboard.model.QueryFailure
import leaderboard.search.{BeautySearchBackend, BeautySearchResponse, ParsedSearchIntent, UserSearchInput}
import leaderboard.search.document.VariantSearchDocument
import leaderboard.search.dsl.BeautySearchSpec
import leaderboard.search.interpreter.SearchResponseAssembler
import leaderboard.search.interpreter.SearchSpecSupport
import leaderboard.search.interpreter.SearchSpecSupport.ScoredDocument
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
      request <- ZIO.fromEither(ElasticsearchSearchRequestInterpreter.request(spec.runtimeSpec(Map.empty), elasticsearchInput(input, intent)))
      rawResponse <- client.postJson(s"/${spec.variantDocument.indexName}/_search", request)
      response <- ZIO.fromEither(interpretResponse(input, intent, rawResponse))
    } yield response

  private def elasticsearchInput(
    input: UserSearchInput,
    intent: ParsedSearchIntent,
  ): ElasticsearchSearchInput =
    ElasticsearchSearchInput(
      remainingText = intent.remainingText,
      explicitConstraints = intent.explicitConstraints,
      softBoosts = intent.softBoosts,
      userLat = input.userLat,
      userLon = input.userLon,
      limit = input.limit,
    )

  private def interpretResponse(
    input: UserSearchInput,
    intent: ParsedSearchIntent,
    rawResponse: io.circe.Json,
  ): Either[QueryFailure, BeautySearchResponse] =
    for {
      hits <- ElasticsearchSearchResponseInterpreter.decodeDocumentHits[VariantSearchDocument](rawResponse)
      scoredDocuments = hits.map { hit =>
        ScoredDocument(
          document = hit.source,
          textScore = hit.score,
          boostScore = 0.0d,
          distanceKm = SearchSpecSupport.computeDistanceKm(input, hit.source),
        )
      }
      assembled <- SearchResponseAssembler.assemble(spec, input, intent, scoredDocuments)
    } yield assembled
}
