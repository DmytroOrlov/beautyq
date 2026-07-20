package leaderboard.search.gen2.core.candidate

import leaderboard.search.gen2.contract.{CandidatePlan, SearchDocumentDeclaration}

/** The small neutral hit shape shared by candidate backends and hydration. */
final case class CandidateHit[Id, Score](id: Id, score: Score)

/** Metadata fields required to prove that candidate hits and documents came from one materialized
  * generation. Backend modules adapt their persisted metadata to this contract. */
trait CandidateGenerationMetadata extends SearchGenerationEvidence {
  def pointCount: Int
  final def documentCount: Long = pointCount.toLong
}

/** A candidate execution whose plan, declaration, hits and authorized generation evidence are one
  * bound value. Backend owners construct it; generic hydration consumes only this view. */
trait BoundCandidateExecution[Document, Id, Score, Target, Metadata <: CandidateGenerationMetadata, Diagnostics] {
  def candidatePlan: CandidatePlan[Document]
  def declaration: SearchDocumentDeclaration[Document, Id]
  def hits: Vector[CandidateHit[Id, Score]]
  def target: Target
  def metadata: Metadata
  def diagnostics: Diagnostics
}
