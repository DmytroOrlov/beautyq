package leaderboard.search.beautyq.gen2.wiring

import leaderboard.search.gen2.core.supplement.AppendOnlySupplementPolicy
import leaderboard.search.gen2.qdrant.*
import leaderboard.search.gen2.transport.Gen2HttpTransportError

/** BeautyQ's supplement policy: one executable owner for append budget, degraded-request classification,
  * and serving-mode dependencies.
  *
  * Dependency vocabulary: one enum with explicit required order.
  */
enum BeautyQSearchDependency(val stableId: String) {
  case ElasticsearchBaseline extends BeautyQSearchDependency("elasticsearch-baseline")
  case DocumentLookup extends BeautyQSearchDependency("document-lookup")
  case QdrantSupplement extends BeautyQSearchDependency("qdrant-supplement")
}

/** Stable degradation reason codes. Exact diagnostics remain in the retained typed cause. */
enum BeautyQDegradationReason(val reasonCode: String) {
  case EmbeddingTimeout extends BeautyQDegradationReason("embedding-timeout")
  case EmbeddingUnavailable extends BeautyQDegradationReason("embedding-unavailable")
  case EmbeddingTransport extends BeautyQDegradationReason("embedding-transport")
  case QdrantUnavailable extends BeautyQDegradationReason("qdrant-unavailable")
  case QdrantTransport extends BeautyQDegradationReason("qdrant-transport")
  case QdrantBackend extends BeautyQDegradationReason("qdrant-backend")
}

/** Policy-owned degradation classification results. Both classes are final with private
  * constructors to BeautyQSupplementPolicy. The exact original typed pipeline error is retained. */
object BeautyQSupplementPolicy {

  sealed trait PipelineFailureDisposition

  /** Policy-owned hard disposition: final class, private constructor to BeautyQSupplementPolicy. */
  final class Hard private[BeautyQSupplementPolicy] (
    val cause: BeautyQQdrantCandidatePipelineError[BeautyQEmbeddingRequestError],
  ) extends PipelineFailureDisposition

  /** Policy-owned degradable disposition: final class, private constructor to BeautyQSupplementPolicy. */
  final class Degradable private[BeautyQSupplementPolicy] (
    val cause: BeautyQQdrantCandidatePipelineError[BeautyQEmbeddingRequestError],
    val reason: BeautyQDegradationReason,
  ) extends PipelineFailureDisposition

  private[BeautyQSupplementPolicy] def Hard(cause: BeautyQQdrantCandidatePipelineError[BeautyQEmbeddingRequestError]): Hard =
    new Hard(cause)

  private[BeautyQSupplementPolicy] def Degradable(cause: BeautyQQdrantCandidatePipelineError[BeautyQEmbeddingRequestError], reason: BeautyQDegradationReason): Degradable =
    new Degradable(cause, reason)

  val MaxAppended: Int = 1
  val appendOnly: AppendOnlySupplementPolicy = AppendOnlySupplementPolicy.create(MaxAppended).getOrElse(throw new IllegalStateException("maxAppended=1 must be valid"))

  /** Explicit required dependency order. The declared order is active business order. */
  val requiredDependencies: Vector[BeautyQSearchDependency] = Vector(
    BeautyQSearchDependency.ElasticsearchBaseline,
    BeautyQSearchDependency.DocumentLookup,
  )

  /** The single supplement dependency. Optional-dependencies view derives from this. */
  val supplementDependency: BeautyQSearchDependency = BeautyQSearchDependency.QdrantSupplement

  /** Classify a typed pipeline failure into exact hard cause or degradable with stable reason. */
  def classifyPipelineError(
    error: BeautyQQdrantCandidatePipelineError[BeautyQEmbeddingRequestError],
  ): PipelineFailureDisposition =
    error match {
      case BeautyQQdrantCandidatePipelineError.Qdrant(qdrantError) =>
        classifyQdrantPipelineError(qdrantError, error)
      case BeautyQQdrantCandidatePipelineError.Hydration(hydrationError) =>
        BeautyQSupplementPolicy.Hard(error)
    }

  private def classifyQdrantPipelineError(
    qdrantError: QdrantCandidatePipelineError[BeautyQEmbeddingRequestError],
    outerError: BeautyQQdrantCandidatePipelineError[BeautyQEmbeddingRequestError],
  ): PipelineFailureDisposition =
    qdrantError match {
      case QdrantCandidatePipelineError.Preparation(_) =>
        BeautyQSupplementPolicy.Hard(outerError)
      case QdrantCandidatePipelineError.Embedding(embeddingError) =>
        embeddingError match {
          case BeautyQEmbeddingRequestError.InvalidResult(_) => BeautyQSupplementPolicy.Hard(outerError)
          case requestError => BeautyQSupplementPolicy.Degradable(outerError, classifyEmbeddingError(requestError))
        }
      case QdrantCandidatePipelineError.Completion(_) =>
        BeautyQSupplementPolicy.Hard(outerError)
      case QdrantCandidatePipelineError.Service(serviceError) =>
        classifyServiceError(serviceError, outerError)
    }

  /** Classify a typed embedding request error into its exact degradable reason.
    * Total: every BeautyQEmbeddingRequestError case is a degradable request-time failure. */
  private def classifyEmbeddingError(
    error: BeautyQEmbeddingRequestError,
  ): BeautyQDegradationReason =
    error match {
      case BeautyQEmbeddingRequestError.Timeout(_) =>
        BeautyQDegradationReason.EmbeddingTimeout
      case BeautyQEmbeddingRequestError.Unavailable(_) =>
        BeautyQDegradationReason.EmbeddingUnavailable
      case BeautyQEmbeddingRequestError.Transport(_) =>
        BeautyQDegradationReason.EmbeddingTransport
      case BeautyQEmbeddingRequestError.InvalidResult(_) =>
        BeautyQDegradationReason.QdrantBackend
    }

  private def classifyServiceError(
    serviceError: QdrantCandidateServiceError,
    outerError: BeautyQQdrantCandidatePipelineError[BeautyQEmbeddingRequestError],
  ): PipelineFailureDisposition =
    serviceError match {
      case QdrantCandidateServiceError.ContractFingerprintMismatch(_, _) =>
        BeautyQSupplementPolicy.Hard(outerError)
      case QdrantCandidateServiceError.AliasMissing(_) =>
        BeautyQSupplementPolicy.Degradable(outerError, BeautyQDegradationReason.QdrantUnavailable)
      case QdrantCandidateServiceError.AliasAmbiguous(_, _) =>
        BeautyQSupplementPolicy.Hard(outerError)
      case QdrantCandidateServiceError.Transport(_, transportError) =>
        classifyTransportError(transportError, outerError)
      case QdrantCandidateServiceError.Malformed(_, _) =>
        BeautyQSupplementPolicy.Degradable(outerError, BeautyQDegradationReason.QdrantBackend)
      case QdrantCandidateServiceError.UnauthorizedCollection(_, _) =>
        BeautyQSupplementPolicy.Hard(outerError)
      case QdrantCandidateServiceError.Response(_) =>
        BeautyQSupplementPolicy.Degradable(outerError, BeautyQDegradationReason.QdrantBackend)
    }

  private def classifyTransportError(
    transportError: Gen2HttpTransportError,
    outerError: BeautyQQdrantCandidatePipelineError[BeautyQEmbeddingRequestError],
  ): PipelineFailureDisposition =
    transportError match {
      case Gen2HttpTransportError.ConnectionFailed(_, _, _) =>
        BeautyQSupplementPolicy.Degradable(outerError, BeautyQDegradationReason.QdrantTransport)
      case Gen2HttpTransportError.RequestFailed(_, _, _) =>
        BeautyQSupplementPolicy.Degradable(outerError, BeautyQDegradationReason.QdrantTransport)
      case Gen2HttpTransportError.HttpFailure(_, _, status, _) =>
        status match {
          case 404 =>
            BeautyQSupplementPolicy.Degradable(outerError, BeautyQDegradationReason.QdrantUnavailable)
          case 408 | 429 =>
            BeautyQSupplementPolicy.Degradable(outerError, BeautyQDegradationReason.QdrantBackend)
          case s if s >= 500 && s < 600 =>
            BeautyQSupplementPolicy.Degradable(outerError, BeautyQDegradationReason.QdrantBackend)
          case s if s >= 400 && s < 500 =>
            BeautyQSupplementPolicy.Hard(outerError)
          case _ =>
            BeautyQSupplementPolicy.Hard(outerError)
        }
      case Gen2HttpTransportError.InvalidJsonResponse(_, _, status, _, _) =>
        status match {
          case 404 =>
            BeautyQSupplementPolicy.Degradable(outerError, BeautyQDegradationReason.QdrantUnavailable)
          case 408 | 429 =>
            BeautyQSupplementPolicy.Degradable(outerError, BeautyQDegradationReason.QdrantBackend)
          case s if s >= 500 && s < 600 =>
            BeautyQSupplementPolicy.Degradable(outerError, BeautyQDegradationReason.QdrantBackend)
          case s if s >= 400 && s < 500 =>
            BeautyQSupplementPolicy.Hard(outerError)
          case _ =>
            BeautyQSupplementPolicy.Hard(outerError)
        }
      case Gen2HttpTransportError.InvalidRequestPath(_, _, _) =>
        BeautyQSupplementPolicy.Hard(outerError)
    }
}
