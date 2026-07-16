package leaderboard.search.beautyq.gen2.wiring

import leaderboard.model.MasterServiceOfferVariantId
import leaderboard.search.beautyq.gen2.contract.*
import leaderboard.search.gen2.contract.*
import leaderboard.search.gen2.core.plan.ContractFingerprint
import leaderboard.search.gen2.elasticsearch.*

/** BeautyQ's compact, complete Elasticsearch policy: which of `variants.Fields`' five searchable text
  * handles use which analyzer and query weight, the accepted `AND` text operator, geo-proximity scoring
  * parameters, the exact-total policy, the default sort/tie-break policy, and BeautyQ's own explicit
  * compatibility version. Declaration traversal, mapping/source/request compilation, response decoding,
  * aggregation naming, cursor-state encoding, contribution derivation and contract-fingerprint composition
  * are entirely framework-owned by `search-gen2-elasticsearch`; this object never recreates a field,
  * traverses `VariantSearchDocumentGen2` manually, builds aggregation names, compiles JSON, encodes cursor
  * state, decodes hits/facets, or computes a fingerprint. */
object BeautyQElasticsearchPolicy {

  private val Fields = BeautyQSearchDeclarations.variants.Fields

  val index: ElasticsearchIndexPolicy[VariantSearchDocumentGen2, MasterServiceOfferVariantId] =
    ElasticsearchIndexPolicy.unsafeFrom(
      declaration = BeautyQSearchDeclarations.variants.document,
      policyVersion = ElasticsearchPolicyVersion("beautyq-elasticsearch-v1"),
      textFields = Vector(
        ElasticsearchTextFieldMapping(Fields.allText, ElasticsearchAnalyzerName.Standard),
        ElasticsearchTextFieldMapping(Fields.serviceText, ElasticsearchAnalyzerName.Standard),
        ElasticsearchTextFieldMapping(Fields.attributeText, ElasticsearchAnalyzerName.Standard),
        ElasticsearchTextFieldMapping(Fields.providerText, ElasticsearchAnalyzerName.Standard),
        ElasticsearchTextFieldMapping(Fields.locationText, ElasticsearchAnalyzerName.Standard),
      ),
    )

  /** The one complete Elasticsearch policy: binds [[index]] to BeautyQ's accepted query-time choices.
    * `contributions`/`contractFingerprint` below always delegate to this value - never to `index` - which
    * carries no independent fingerprint authority. */
  val value: ElasticsearchPolicy[VariantSearchDocumentGen2, MasterServiceOfferVariantId] =
    ElasticsearchPolicy.unsafeFrom(
      planContractVersion = BeautyQSearchDeclarations.variants.plan.contractVersion,
      index = index,
      queryTextFields = Vector(
        ElasticsearchWeightedTextField(Fields.allText, ElasticsearchQueryWeight(4.0)),
        ElasticsearchWeightedTextField(Fields.serviceText, ElasticsearchQueryWeight(5.0)),
        ElasticsearchWeightedTextField(Fields.attributeText, ElasticsearchQueryWeight(4.0)),
        ElasticsearchWeightedTextField(Fields.providerText, ElasticsearchQueryWeight(2.0)),
        ElasticsearchWeightedTextField(Fields.locationText, ElasticsearchQueryWeight(2.5)),
      ),
      textOperator = ElasticsearchTextOperator.And,
      geoScoringPolicy = Some(
        ElasticsearchGeoScoringPolicy(
          scale = Distance(BigDecimal(5000)),
          offset = Distance(BigDecimal(0)),
          decay = ElasticsearchGeoDecay(0.5),
          weight = ElasticsearchQueryWeight(1.25),
        )
      ),
      totalHitsPolicy = ElasticsearchTotalHitsPolicy.ExactRequired,
      defaultSortPolicy = ElasticsearchDefaultSortPolicy(
        relevanceDirection = SortDirection.Desc,
        identityTieBreakerDirection = SortDirection.Asc,
      ),
    )

  def contributions: PlanContractContributions = value.contributions

  def contractFingerprint: ContractFingerprint = value.contractFingerprint
}
