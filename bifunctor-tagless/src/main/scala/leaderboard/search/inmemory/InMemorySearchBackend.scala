package leaderboard.search.inmemory

import izumi.functional.bio.{Error2, F}
import leaderboard.model.QueryFailure
import leaderboard.search.*
import leaderboard.search.dsl.BeautySearchSpec
import leaderboard.search.document.VariantSearchDocument
import leaderboard.search.interpreter.{SearchResponseAssembler, SearchSpecSupport}
import leaderboard.search.interpreter.SearchSpecSupport.ScoredDocument

final class InMemorySearchBackend[F[+_, +_]: Error2](
  spec: BeautySearchSpec,
  documents: List[VariantSearchDocument],
) extends BeautySearchBackend[F] {

  override def search(input: UserSearchInput, intent: ParsedSearchIntent): F[QueryFailure, BeautySearchResponse] =
    SearchResponseAssembler.assemble(spec, input, intent, filterAndScore(input, intent)) match {
      case Right(value) => F.pure(value)
      case Left(error) => F.fail(error)
    }

  private def filterAndScore(
    input: UserSearchInput,
    intent: ParsedSearchIntent,
  ): List[ScoredDocument] =
    documents.flatMap { document =>
      val explicitMatches = intent.explicitConstraints.foldLeft[Either[QueryFailure, Boolean]](Right(true)) {
        case (acc, constraint) =>
          for {
            alreadyMatches <- acc
            nextMatches <- SearchSpecSupport.matchesConstraint(spec, document, constraint)
          } yield alreadyMatches && nextMatches
      }

      explicitMatches.toOption.filter(identity).flatMap { _ =>
        val textScore = SearchSpecSupport.textScore(spec, document, intent.remainingText)
        val softBoostScore = intent.softBoosts.foldLeft(0.0d) {
          (score, constraint) =>
            SearchSpecSupport.matchesConstraint(spec, document, constraint) match {
              case Right(true) =>
                SearchSpecSupport.constraintBoostWeight(spec, constraint) match {
                  case Right(weight) => score + weight * 0.5d
                  case Left(_) => score
                }
              case _ => score
            }
        }
        val distanceKm = SearchSpecSupport.computeDistanceKm(input, document)
        val distanceBoost = distanceKm.map(distance => 1.0d / (1.0d + distance.toDouble) * spec.carouselSpec.ranking.providerDistanceWeight).getOrElse(0.0d)
        val finalTextScore = textScore * spec.carouselSpec.ranking.textScoreWeight + distanceBoost
        if (intent.remainingText.nonEmpty && textScore <= 0.0d && intent.explicitConstraints.isEmpty) {
          None
        } else {
          Some(
            ScoredDocument(
              document = document,
              textScore = finalTextScore,
              boostScore = softBoostScore,
              distanceKm = distanceKm,
            )
          )
        }
      }
    }
}
