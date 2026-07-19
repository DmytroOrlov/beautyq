package leaderboard.search.gen2.qdrant

private[qdrant] object QdrantGenerationNaming {
  def generationId(identity: QdrantGenerationIdentity): String =
    QdrantCanonical.sha256(
      Vector(
        identity.sourceContentFingerprint,
        identity.projectedDocumentsFingerprint,
        identity.collectionContractFingerprint,
        identity.projectionFormatVersion,
        identity.compilerVersion,
        identity.collectionFormatVersion,
        identity.embeddingModel.provider,
        identity.embeddingModel.model,
        identity.embeddingModel.revision,
        identity.embeddingModel.dimension.toString,
        identity.embeddingModel.textFormatVersion,
        identity.embeddedPointsFingerprint,
      )
    )

  def physicalName(prefix: String, identity: QdrantGenerationIdentity): String = s"$prefix${generationId(identity)}"
}
