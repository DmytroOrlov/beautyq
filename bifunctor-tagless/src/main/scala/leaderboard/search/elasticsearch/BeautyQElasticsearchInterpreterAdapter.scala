package leaderboard.search.elasticsearch

import io.circe.Json
import leaderboard.model.{MasterServiceOfferVariantId, QueryFailure}
import leaderboard.search.{BeautySearchResponse, ParsedSearchIntent, UserSearchInput}
import leaderboard.search.document.VariantSearchDocument
import leaderboard.search.dsl.BeautySearchSpec
import leaderboard.search.interpreter.SearchResponseAssembler
import leaderboard.search.interpreter.SearchSpecSupport
import leaderboard.search.interpreter.SearchSpecSupport.ScoredDocument
import leaderboard.search.lexical.LexicalDocumentHit

object BeautyQElasticsearchInterpreterAdapter {
  def mapping(spec: BeautySearchSpec): Json =
    ElasticsearchMappingInterpreter.mapping(spec.variantDocument)

  def bulkPayload(
    spec: BeautySearchSpec,
    documents: List[VariantSearchDocument],
  ): String =
    ElasticsearchIngestionInterpreter.bulkPayload(spec.variantDocument, documents)

  def sourceJson(
    spec: BeautySearchSpec,
    document: VariantSearchDocument,
  ): Json =
    ElasticsearchIngestionInterpreter.sourceJson(spec.variantDocument, document)

  def input(
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

  def request(
    spec: BeautySearchSpec,
    input: UserSearchInput,
    intent: ParsedSearchIntent,
  ): Either[QueryFailure, Json] =
    ElasticsearchSearchRequestInterpreter.request(spec.runtimeSpec(Map.empty), BeautyQElasticsearchInterpreterAdapter.input(input, intent))

  def interpret(
    spec: BeautySearchSpec,
    input: UserSearchInput,
    intent: ParsedSearchIntent,
    response: Json,
  ): Either[QueryFailure, BeautySearchResponse] =
    for {
      hits <- ElasticsearchSearchResponseInterpreter.decodeDocumentHits[VariantSearchDocument](response)
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

  def documentHits(response: Json): Either[QueryFailure, List[LexicalDocumentHit[MasterServiceOfferVariantId]]] =
    ElasticsearchSearchResponseInterpreter.lexicalHits[VariantSearchDocument, MasterServiceOfferVariantId](
      specDocument,
      response,
      _.variantId,
    )

  private val specDocument =
    leaderboard.search.dsl.BeautySearchSpecV1.spec.variantDocument
}
