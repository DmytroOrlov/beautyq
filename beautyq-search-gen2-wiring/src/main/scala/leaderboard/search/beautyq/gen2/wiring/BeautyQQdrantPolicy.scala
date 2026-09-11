package leaderboard.search.beautyq.gen2.wiring

import leaderboard.search.beautyq.gen2.contract.BeautyQSearchDeclarations
import leaderboard.search.gen2.qdrant.*

/** BeautyQ's small Qdrant policy surface. Payload/index mechanics are derived by [[QdrantPolicy]] from
  * the canonical variant document declaration; only the embedding source and retrieval/vector choices
  * are visible here. Transport and physical generation lifecycle now exist and are owned by the
  * reusable `search-gen2-qdrant` module (JSON client/transport and `QdrantGenerationLifecycle`),
  * not by this policy surface. */
object BeautyQQdrantPolicy {
  private val declarations = BeautyQSearchDeclarations.variants
  private val fields       = declarations.Fields

  val policy =
    QdrantPolicy.unsafeFrom(
      planContractVersion = declarations.plan.contractVersion,
      declaration = declarations.document,
      identity = fields.variantId,
      embeddingField = fields.allText,
      vectorName = QdrantVectorName.unsafeFrom("variant-semantic"),
      embeddingModel = QdrantEmbeddingModelIdentity(
        provider = "llama.cpp",
        model = "local-llama-cpp-embedding",
        revision = "managed-local-v1",
        dimension = 1024,
        textFormatVersion = "beautyq-variant-all-text-v1",
      ),
      distance = QdrantDistance.Cosine,
      retrieval = QdrantRetrievalPolicy(topK = 20, oversamplingFactor = 1, scoreThreshold = None),
    )
}
