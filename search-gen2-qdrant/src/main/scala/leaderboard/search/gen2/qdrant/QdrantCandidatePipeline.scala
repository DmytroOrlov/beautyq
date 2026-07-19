package leaderboard.search.gen2.qdrant

import leaderboard.search.gen2.contract.CandidatePlan
import leaderboard.search.gen2.core.candidate.{BoundCandidateExecution, CandidateHit}

trait QdrantQueryEmbeddingPort[+Error] {
  def embed(input: QdrantEmbeddingInput): Either[Error, QdrantEmbeddingResult]
}

sealed trait QdrantCandidatePipelineError[+EmbeddingError]
object QdrantCandidatePipelineError {
  final case class Preparation(error: QdrantCandidateCompileError) extends QdrantCandidatePipelineError[Nothing]
  final case class Embedding[EmbeddingError](error: EmbeddingError) extends QdrantCandidatePipelineError[EmbeddingError]
  final case class Completion(error: QdrantCandidateCompileError) extends QdrantCandidatePipelineError[Nothing]
  final case class Service(error: QdrantCandidateServiceError) extends QdrantCandidatePipelineError[Nothing]
}

/** One bound candidate execution. It is the only value accepted by the generic hydration kernel;
  * plan, declaration, authorized target, metadata and diagnostics cannot be supplied independently. */
final class ExecutedQdrantCandidatePlan[Document, Id] private[qdrant] (
  val candidatePlan: CandidatePlan[Document],
  val policy: QdrantPolicy[Document, Id],
  val request: QdrantCompiledCandidateRequest,
  val authorized: QdrantAuthorizedCandidateResult[Id],
) extends BoundCandidateExecution[Document, Id, Double, QdrantResourceName, QdrantGenerationMetadata, QdrantCandidateDiagnostics] {
  def declaration = policy.declaration
  def hits: Vector[CandidateHit[Id, Double]] = authorized.hits.map(hit => CandidateHit(hit.id, hit.score))
  def target: QdrantResourceName = authorized.target
  def metadata: QdrantGenerationMetadata = authorized.metadata
  def diagnostics: QdrantCandidateDiagnostics = authorized.diagnostics
}

object QdrantCandidatePipeline {
  import QdrantCandidatePipelineError.*

  def execute[Document, Id, EmbeddingError](
    policy: QdrantPolicy[Document, Id],
    candidatePlan: CandidatePlan[Document],
    embeddingPort: QdrantQueryEmbeddingPort[EmbeddingError],
    service: QdrantCandidateService,
  ): Either[QdrantCandidatePipelineError[EmbeddingError], ExecutedQdrantCandidatePlan[Document, Id]] =
    for {
      prepared <- QdrantCandidateRequestCompiler.prepare(policy, candidatePlan).left.map(Preparation.apply)
      embedding <- embeddingPort.embed(prepared.embeddingInput).left.map(Embedding.apply)
      request <- QdrantCandidateRequestCompiler.complete(prepared, embedding).left.map(Completion.apply)
      authorized <- service.execute(policy, request).left.map(Service.apply)
    } yield new ExecutedQdrantCandidatePlan(candidatePlan, policy, request, authorized)
}
