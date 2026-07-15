package leaderboard.search.beautyq.gen2.wiring

import leaderboard.model.MasterServiceOfferVariantId
import leaderboard.search.beautyq.gen2.contract.*
import leaderboard.search.gen2.contract.*
import leaderboard.search.gen2.core.plan.ContractFingerprint
import leaderboard.search.gen2.elasticsearch.*

/** BeautyQ's compact Elasticsearch index policy: which of `variants.Fields`' five searchable text
  * handles use which analyzer, and BeautyQ's own explicit compatibility version. Declaration traversal,
  * mapping/source compilation, contribution derivation and contract-fingerprint composition are entirely
  * framework-owned by `search-gen2-elasticsearch`; this object never recreates a field, traverses
  * `VariantSearchDocumentGen2` manually, or maintains a parallel field inventory. */
object BeautyQElasticsearchPolicy {

  private val Fields = BeautyQSearchDeclarations.variants.Fields

  val index: ElasticsearchIndexPolicy[VariantSearchDocumentGen2, MasterServiceOfferVariantId] =
    ElasticsearchIndexPolicy.unsafeFrom(
      declaration = BeautyQSearchDeclarations.variants.document,
      planContractVersion = BeautyQSearchDeclarations.variants.plan.contractVersion,
      policyVersion = ElasticsearchPolicyVersion("beautyq-elasticsearch-v1"),
      textFields = Vector(
        ElasticsearchTextFieldMapping(Fields.allText, ElasticsearchAnalyzerName.Standard),
        ElasticsearchTextFieldMapping(Fields.serviceText, ElasticsearchAnalyzerName.Standard),
        ElasticsearchTextFieldMapping(Fields.attributeText, ElasticsearchAnalyzerName.Standard),
        ElasticsearchTextFieldMapping(Fields.providerText, ElasticsearchAnalyzerName.Standard),
        ElasticsearchTextFieldMapping(Fields.locationText, ElasticsearchAnalyzerName.Standard),
      ),
    )

  def contributions: PlanContractContributions = index.contributions

  def contractFingerprint: ContractFingerprint = index.contractFingerprint
}
