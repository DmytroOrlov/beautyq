package leaderboard.search.gen2.core.candidate

/** Common evidence that two backend results claim to represent the same materialized generation.
  * Backend-specific formats (compiler versions, mapping/collection format versions, contract
  * fingerprints) are intentionally excluded: ES and Qdrant legitimately own different backend
  * identities. */
trait SearchGenerationEvidence {
  def sourceContentFingerprint: String
  def projectedDocumentsFingerprint: String
  def projectionFormatVersion: String
  def documentCount: Long
}
