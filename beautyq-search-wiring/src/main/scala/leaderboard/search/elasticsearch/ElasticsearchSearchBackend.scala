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
      request <- ZIO.fromEither(elasticsearchInput(input, intent).flatMap(ElasticsearchSearchRequestInterpreter.request(spec.runtimeSpec(Map.empty), _)))
      rawResponse <- client.postJson(s"/${spec.variantDocument.indexName}/_search", request)
      response <- ZIO.fromEither(interpretResponse(input, intent, rawResponse))
    } yield response

  private def elasticsearchInput(
    input: UserSearchInput,
    intent: ParsedSearchIntent,
  ): Either[QueryFailure, ElasticsearchSearchInput[VariantSearchDocument]] =
    for {
      explicitConstraints <- resolveConstraints(intent.explicitConstraints)
      softBoosts <- resolveConstraints(intent.softBoosts)
    } yield ElasticsearchSearchInput(
        remainingText = intent.remainingText,
        explicitConstraints = explicitConstraints,
        softBoosts = softBoosts,
        userLat = input.userLat,
        userLon = input.userLon,
        limit = input.limit,
      )

  private def resolveConstraints(
    constraints: List[leaderboard.search.dsl.SearchConstraint]
  ): Either[QueryFailure, List[leaderboard.search.dsl.ResolvedSearchConstraint[VariantSearchDocument]]] =
    constraints.foldRight[Either[QueryFailure, List[leaderboard.search.dsl.ResolvedSearchConstraint[VariantSearchDocument]]]](Right(Nil)) {
      (constraint, acc) =>
        for {
          head <- spec.querySchema.resolve(constraint)
          tail <- acc
        } yield head :: tail
    }

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
