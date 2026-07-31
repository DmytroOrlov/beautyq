package leaderboard.search.beautyq.gen2.wiring

import leaderboard.search.gen2.core.supplement.AppendOnlySupplementPolicy
import leaderboard.search.gen2.qdrant.*
import leaderboard.search.gen2.transport.Gen2HttpTransportError

/** BeautyQ's supplement policy: one executable owner for append budget, degraded-request classification,
  * startup failure classification, and serving-mode dependencies.
  */
object BeautyQSupplementPolicy {

  sealed trait PipelineFailureDisposition

  final class Hard private[BeautyQSupplementPolicy] (
    val cause: BeautyQQdrantCandidatePipelineError[BeautyQEmbeddingRequestError],
  ) extends PipelineFailureDisposition

  final class Degradable private[BeautyQSupplementPolicy] (
    val cause: BeautyQQdrantCandidatePipelineError[BeautyQEmbeddingRequestError],
    val reason: BeautyQDegradationReason,
  ) extends PipelineFailureDisposition

  sealed trait StartupFailureDisposition

  final class StartupHard private[BeautyQSupplementPolicy] (
    val cause: BeautyQSearchGenerationActivationError,
  ) extends StartupFailureDisposition

  final class StartupDegradable private[BeautyQSupplementPolicy] (
    val cause: BeautyQSearchGenerationActivationError,
    val reason: BeautyQDegradationReason,
  ) extends StartupFailureDisposition

  private[BeautyQSupplementPolicy] def Hard(cause: BeautyQQdrantCandidatePipelineError[BeautyQEmbeddingRequestError]): Hard =
    new Hard(cause)

  private[BeautyQSupplementPolicy] def Degradable(cause: BeautyQQdrantCandidatePipelineError[BeautyQEmbeddingRequestError], reason: BeautyQDegradationReason): Degradable =
    new Degradable(cause, reason)

  private[BeautyQSupplementPolicy] def StartupHard(cause: BeautyQSearchGenerationActivationError): StartupHard =
    new StartupHard(cause)

  private[BeautyQSupplementPolicy] def StartupDegradable(cause: BeautyQSearchGenerationActivationError, reason: BeautyQDegradationReason): StartupDegradable =
    new StartupDegradable(cause, reason)

  val MaxAppended: Int = 1
  val appendOnly: AppendOnlySupplementPolicy = AppendOnlySupplementPolicy.create(MaxAppended).getOrElse(throw new IllegalStateException("maxAppended=1 must be valid"))

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
      case BeautyQEmbeddingRequestError.MalformedResponse(_) =>
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

  def classifyStartupActivationError(
    error: BeautyQSearchGenerationActivationError,
  ): StartupFailureDisposition =
    error match {
      case BeautyQSearchGenerationActivationError.ElasticsearchCompile(_) =>
        StartupHard(error)
      case BeautyQSearchGenerationActivationError.Elasticsearch(_) =>
        StartupHard(error)
      case BeautyQSearchGenerationActivationError.Qdrant(qdrantError) =>
        classifyStartupQdrantError(qdrantError, error)
      case BeautyQSearchGenerationActivationError.Embedding(embeddingError) =>
        classifyStartupEmbeddingError(embeddingError, error)
      case BeautyQSearchGenerationActivationError.Compile(_) =>
        StartupHard(error)
    }

  private def classifyStartupEmbeddingError(
    embeddingError: BeautyQEmbeddingRequestError,
    outerError: BeautyQSearchGenerationActivationError,
  ): StartupFailureDisposition =
    embeddingError match {
      case BeautyQEmbeddingRequestError.Timeout(_) =>
        StartupDegradable(outerError, BeautyQDegradationReason.EmbeddingTimeout)
      case BeautyQEmbeddingRequestError.Unavailable(_) =>
        StartupDegradable(outerError, BeautyQDegradationReason.EmbeddingUnavailable)
      case BeautyQEmbeddingRequestError.Transport(_) =>
        StartupDegradable(outerError, BeautyQDegradationReason.EmbeddingTransport)
      case BeautyQEmbeddingRequestError.MalformedResponse(_) =>
        StartupHard(outerError)
      case BeautyQEmbeddingRequestError.InvalidResult(_) =>
        StartupHard(outerError)
    }

  private def classifyStartupQdrantError(
    qdrantError: QdrantGenerationLifecycleError,
    outerError: BeautyQSearchGenerationActivationError,
  ): StartupFailureDisposition =
    qdrantError match {
      case QdrantGenerationLifecycleError.InvalidPhysicalName(_) =>
        StartupHard(outerError)
      case QdrantGenerationLifecycleError.Malformed(_, _) =>
        StartupHard(outerError)
      case QdrantGenerationLifecycleError.CollectionIncompatible(_, _) =>
        StartupHard(outerError)
      case QdrantGenerationLifecycleError.AliasAmbiguous(_, _) =>
        StartupHard(outerError)
      case QdrantGenerationLifecycleError.MutationRejected(_, _) =>
        StartupHard(outerError)
      case QdrantGenerationLifecycleError.PointCountMismatch(_, _) =>
        StartupHard(outerError)
      case QdrantGenerationLifecycleError.Transport(_, transportError) =>
        classifyStartupQdrantTransportError(transportError, outerError)
    }

  private def classifyStartupQdrantTransportError(
    transportError: Gen2HttpTransportError,
    outerError: BeautyQSearchGenerationActivationError,
  ): StartupFailureDisposition =
    transportError match {
      case Gen2HttpTransportError.ConnectionFailed(_, _, _) =>
        StartupDegradable(outerError, BeautyQDegradationReason.QdrantTransport)
      case Gen2HttpTransportError.RequestFailed(_, _, _) =>
        StartupDegradable(outerError, BeautyQDegradationReason.QdrantTransport)
      case Gen2HttpTransportError.HttpFailure(_, _, status, _) =>
        status match {
          case 408 | 429 =>
            StartupDegradable(outerError, BeautyQDegradationReason.QdrantBackend)
          case s if s >= 500 && s < 600 =>
            StartupDegradable(outerError, BeautyQDegradationReason.QdrantBackend)
          case _ =>
            StartupHard(outerError)
        }
      case Gen2HttpTransportError.InvalidJsonResponse(_, _, _, _, _) =>
        StartupHard(outerError)
      case Gen2HttpTransportError.InvalidRequestPath(_, _, _) =>
        StartupHard(outerError)
    }
}

enum BeautyQDegradationReason(val reasonCode: String) {
  case EmbeddingTimeout extends BeautyQDegradationReason("embedding-timeout")
  case EmbeddingUnavailable extends BeautyQDegradationReason("embedding-unavailable")
  case EmbeddingTransport extends BeautyQDegradationReason("embedding-transport")
  case QdrantUnavailable extends BeautyQDegradationReason("qdrant-unavailable")
  case QdrantTransport extends BeautyQDegradationReason("qdrant-transport")
  case QdrantBackend extends BeautyQDegradationReason("qdrant-backend")
}
