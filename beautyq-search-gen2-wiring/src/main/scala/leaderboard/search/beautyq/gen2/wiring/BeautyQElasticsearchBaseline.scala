package leaderboard.search.beautyq.gen2.wiring

import leaderboard.model.MasterServiceOfferVariantId
import leaderboard.search.beautyq.gen2.contract.VariantSearchDocumentGen2
import leaderboard.search.gen2.elasticsearch.*
import leaderboard.search.gen2.elasticsearch.lifecycle.AuthorizedElasticsearchSearchRequest

import io.circe.Json

type CompiledBeautyQElasticsearchSearchRequest = PreparedElasticsearchSearchRequest[VariantSearchDocumentGen2, MasterServiceOfferVariantId]
type AuthorizedBeautyQElasticsearchSearchRequest = AuthorizedElasticsearchSearchRequest[VariantSearchDocumentGen2, MasterServiceOfferVariantId]
type BeautyQBaselineSearchPage = BaselineSearchPage[VariantSearchDocumentGen2, MasterServiceOfferVariantId]

/** Thin BeautyQ binding over the generic [[ElasticsearchSearchRequestCompiler]]/
  * [[ElasticsearchSearchResponseDecoder]]: supplies only `compiledPlan.boundPlan`,
  * [[BeautyQElasticsearchPolicy.value]]. The prepared request carries only a lifecycle generation
  * requirement; it never accepts or exposes a caller-supplied executable target. Never
  * inspects `VariantSearchDocumentGen2` fields, builds request JSON, derives aggregation names, encodes
  * cursor state, or decodes hits/facets itself - every mechanic is delegated to the generic compiler/
  * decoder. Brick 5C supplies the lifecycle-authorized request to `decodeResponse`. */
object BeautyQElasticsearchBaseline {

  def compileRequest(compiledPlan: CompiledBeautyQSearchPlan): Either[ElasticsearchSearchRequestCompileError, CompiledBeautyQElasticsearchSearchRequest] =
    ElasticsearchSearchRequestCompiler.compile(BeautyQElasticsearchPolicy.value, compiledPlan.boundPlan)

  def decodeResponse(
    compiledRequest: AuthorizedBeautyQElasticsearchSearchRequest,
    response: Json,
  ): Either[ElasticsearchSearchResponseErrors, BeautyQBaselineSearchPage] =
    ElasticsearchSearchResponseDecoder.decode(compiledRequest, response)
}
