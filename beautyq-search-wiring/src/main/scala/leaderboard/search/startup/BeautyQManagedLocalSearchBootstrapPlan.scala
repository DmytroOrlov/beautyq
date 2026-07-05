package leaderboard.search.startup

import io.circe.Json
import leaderboard.search.beautyq.contract.{BeautyQSearchRuntimeContract, BeautyQSearchSourceTextFieldsContract}
import leaderboard.search.document.VariantSearchDocument
import leaderboard.search.dsl.{EmbeddingSpec, SearchField, VectorSearchSpec}
import leaderboard.search.qdrant.{QdrantCollectionIdentity, QdrantCollectionReadinessConfig}

final case class BeautyQManagedLocalSearchBootstrapResult(
  esIndexName: String,
  esDocumentCount: Int,
  esAction: BeautyQManagedLocalSearchBootstrapAction,
  qdrantCollectionName: String,
  qdrantIndexedCount: Int,
  qdrantAction: BeautyQManagedLocalSearchBootstrapAction,
  vectorDimension: Int,
  fingerprint: String,
)

sealed trait BeautyQManagedLocalSearchBootstrapAction extends Product with Serializable {
  def label: String
}

object BeautyQManagedLocalSearchBootstrapAction {
  case object Rebuilt extends BeautyQManagedLocalSearchBootstrapAction {
    override val label: String = "rebuilt"
  }

  case object Reused extends BeautyQManagedLocalSearchBootstrapAction {
    override val label: String = "reused"
  }
}

object BeautyQManagedLocalSearchBootstrapPlan {
  val EmbeddingPreflightOperationName: String = "beautyq-managed-local-embedding-preflight"
  val EmbeddingModelName: String = BeautyQSearchRuntimeContract.ManagedLocalQdrantEmbeddingModelName
  val SourceTextFields: List[SearchField[VariantSearchDocument]] =
    BeautyQSearchSourceTextFieldsContract.qdrantSourceTextFields
  val SourceTextFieldPaths: List[String] = BeautyQSearchSourceTextFieldsContract.qdrantSourceTextFieldPaths

  /**
   * Expected local managed BeautyQ Qdrant vector dimension. It matches the fixed local launcher
   * collection (`..._1024_cosine`, dimension `1024`); the embedding preflight rejects any endpoint
   * that does not return exactly this many components so the Qdrant collection is only ever created
   * for vectors it can actually hold.
   */
  val ExpectedVectorDimension: Int = BeautyQSearchRuntimeContract.ManagedLocalQdrantExpectedVectorDimension

  def embeddingSpec(vectorSearchSpec: VectorSearchSpec, dimension: Int): EmbeddingSpec[VariantSearchDocument] =
    EmbeddingSpec[VariantSearchDocument](
      vectorName = vectorSearchSpec.vectorName,
      modelName = EmbeddingModelName,
      dimension = dimension,
      distance = BeautyQSearchRuntimeContract.ManagedLocalQdrantVectorDistance,
      sourceTextFields = SourceTextFields,
    )

  def readinessConfig(vectorSearchSpec: VectorSearchSpec, dimension: Int): QdrantCollectionReadinessConfig = {
    val spec = embeddingSpec(vectorSearchSpec, dimension)
    QdrantCollectionReadinessConfig(
      collectionName = vectorSearchSpec.collectionName,
      vectorSearchSpec = vectorSearchSpec,
      compatibilityExpectation = QdrantCollectionIdentity.compatibilityExpectation(spec, vectorSearchSpec),
    )
  }

  def qdrantCollectionInfoReusable(
    json: Json,
    expectedDocumentCount: Int,
    fingerprint: BeautyQManagedLocalSearchBootstrapFingerprint,
  ): Boolean =
    observedQdrantPointCount(json).exists(_ >= expectedDocumentCount) &&
      BeautyQManagedLocalSearchBootstrapFingerprint.decodeCollectionMetadataValue(json).contains(fingerprint.value)

  private def observedQdrantPointCount(json: Json): Option[Int] =
    List(
      json.hcursor.downField("result").get[Int]("points_count").toOption,
      json.hcursor.downField("result").get[Int]("indexed_vectors_count").toOption,
      json.hcursor.get[Int]("points_count").toOption,
      json.hcursor.get[Int]("indexed_vectors_count").toOption,
    ).flatten.headOption
}
