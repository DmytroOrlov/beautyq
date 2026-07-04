package leaderboard.search.elasticsearch

import io.circe.Json
import leaderboard.model.{MasterServiceOfferVariantId, QueryFailure}
import leaderboard.search.{BeautySearchResponse, ParsedSearchIntent, UserSearchInput}
import leaderboard.search.document.VariantSearchDocument
import leaderboard.search.dsl.{BeautySearchSpec, SearchConstraint}
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
    spec: BeautySearchSpec,
    input: UserSearchInput,
    intent: ParsedSearchIntent,
  ): Either[QueryFailure, ElasticsearchSearchInput[VariantSearchDocument]] =
    for {
      explicitConstraints <- resolveConstraints(spec, intent.explicitConstraints)
      softBoosts <- resolveConstraints(spec, intent.softBoosts)
    } yield ElasticsearchSearchInput(
        remainingText = intent.remainingText,
        explicitConstraints = explicitConstraints,
        softBoosts = softBoosts,
        userLat = input.userLat,
        userLon = input.userLon,
        limit = input.limit,
      )

  def request(
    spec: BeautySearchSpec,
    input: UserSearchInput,
    intent: ParsedSearchIntent,
  ): Either[QueryFailure, Json] =
    for {
      resolvedInput <- BeautyQElasticsearchInterpreterAdapter.input(spec, input, intent)
      request <- ElasticsearchSearchRequestInterpreter.request(spec.runtimeSpec(Map.empty), resolvedInput)
    } yield request

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

  private def resolveConstraints(
    spec: BeautySearchSpec,
    constraints: List[SearchConstraint],
  ) =
    constraints.foldRight[Either[QueryFailure, List[leaderboard.search.dsl.ResolvedSearchConstraint[VariantSearchDocument]]]](Right(Nil)) {
      (constraint, acc) =>
        for {
          head <- spec.querySchema.resolve(constraint)
          tail <- acc
        } yield head :: tail
    }
}
