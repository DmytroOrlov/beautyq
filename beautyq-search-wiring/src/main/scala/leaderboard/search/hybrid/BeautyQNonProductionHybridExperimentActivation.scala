package leaderboard.search.hybrid

import izumi.functional.bio.Error2
import leaderboard.model.{MasterServiceOfferVariantId, QueryFailure}
import leaderboard.search.document.VariantSearchDocument
import leaderboard.search.lexical.LexicalDocumentBackend
import leaderboard.search.semantic.{SemanticDocumentBackend, SemanticDocumentLookup}

sealed trait BeautyQNonProductionHybridExperimentActivation extends Product with Serializable

object BeautyQNonProductionHybridExperimentActivation {
  case object Disabled extends BeautyQNonProductionHybridExperimentActivation

  final case class Enabled(
    config: BeautyQNonProductionHybridExperimentConfig,
  ) extends BeautyQNonProductionHybridExperimentActivation

  val default: BeautyQNonProductionHybridExperimentActivation = Disabled

  // Dependencies are intentionally by-name: Disabled must not evaluate experiment/resource dependencies.
  // Do not change this back to by-value before a real Distage/resource-gating design exists.
  // This is a construction-safe library boundary, not the final Distage resource-gating guarantee.
  def buildIfEnabled[F[+_, +_]: Error2](
    activation: BeautyQNonProductionHybridExperimentActivation,
    lexicalBackend: => LexicalDocumentBackend[F, MasterServiceOfferVariantId],
    semanticBackend: => SemanticDocumentBackend[F, MasterServiceOfferVariantId],
    documentLookup: => SemanticDocumentLookup[F, MasterServiceOfferVariantId, VariantSearchDocument],
  ): Option[BeautyQNonProductionHybridResponseExperiment[F]] =
    activation match {
      case Disabled =>
        None
      case Enabled(_) =>
        val builtLexicalBackend = lexicalBackend
        val builtSemanticBackend = semanticBackend
        val builtDocumentLookup = documentLookup

        Some(new BeautyQNonProductionHybridResponseExperiment[F](
          lexicalBackend = builtLexicalBackend,
          semanticBackend = builtSemanticBackend,
          documentLookup = builtDocumentLookup,
        ))
    }
}

final case class BeautyQNonProductionHybridExperimentConfig(
  experimentId: String,
  invocation: BeautyQNonProductionHybridExperimentInvocation,
  routing: BeautyQNonProductionHybridExperimentRouting,
)

object BeautyQNonProductionHybridExperimentConfig {
  def create(
    experimentId: String,
    invocation: BeautyQNonProductionHybridExperimentInvocation,
    routing: BeautyQNonProductionHybridExperimentRouting,
  ): Either[QueryFailure, BeautyQNonProductionHybridExperimentConfig] =
    if (experimentId.trim.isEmpty) {
      Left(QueryFailure.domain("BeautyQ non-production hybrid experimentId must be non-empty"))
    } else {
      Right(BeautyQNonProductionHybridExperimentConfig(
        experimentId = experimentId,
        invocation = invocation,
        routing = routing,
      ))
    }
}

sealed trait BeautyQNonProductionHybridExperimentInvocation extends Product with Serializable

object BeautyQNonProductionHybridExperimentInvocation {
  case object ManualTask extends BeautyQNonProductionHybridExperimentInvocation
  case object TestSetup extends BeautyQNonProductionHybridExperimentInvocation
  case object LocalExperiment extends BeautyQNonProductionHybridExperimentInvocation

  val all: List[BeautyQNonProductionHybridExperimentInvocation] =
    List(ManualTask, TestSetup, LocalExperiment)
}

sealed trait BeautyQNonProductionHybridExperimentRouting extends Product with Serializable

object BeautyQNonProductionHybridExperimentRouting {
  case object ExplicitInvocationOnly extends BeautyQNonProductionHybridExperimentRouting

  val all: List[BeautyQNonProductionHybridExperimentRouting] =
    List(ExplicitInvocationOnly)
}
