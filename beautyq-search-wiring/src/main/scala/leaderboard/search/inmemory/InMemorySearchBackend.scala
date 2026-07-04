package leaderboard.search.inmemory

import izumi.functional.bio.{Error2, F}
import leaderboard.model.QueryFailure
import leaderboard.search.*
import leaderboard.search.dsl.{BeautyQSearchPresentation, BeautySearchSpec}
import leaderboard.search.document.VariantSearchDocument
import leaderboard.search.interpreter.{SearchResponseAssembler, SearchSpecSupport}
import leaderboard.search.interpreter.SearchSpecSupport.ScoredDocument

final class InMemorySearchBackend[F[+_, +_]: Error2](
  spec: BeautySearchSpec,
  documents: List[VariantSearchDocument],
) extends BeautySearchBackend[F] {

  override def search(input: UserSearchInput, intent: ParsedSearchIntent): F[QueryFailure, BeautySearchResponse] =
    filterAndScore(input, intent) match {
      case Right(scoredDocuments) =>
        SearchResponseAssembler.assemble(spec, input, intent, scoredDocuments) match {
          case Right(value) => F.pure(value)
          case Left(error) => F.fail(error)
        }
      case Left(error) =>
        F.fail(error)
    }

  private def filterAndScore(
    input: UserSearchInput,
    intent: ParsedSearchIntent,
  ): Either[QueryFailure, List[ScoredDocument]] =
    for {
      textScoreWeight <- BeautyQSearchPresentation.textScoreWeight(spec.carouselSpec.ranking)
      providerDistanceWeight <- BeautyQSearchPresentation.providerDistanceWeight(spec.carouselSpec.ranking)
      scored <- sequence(documents.map(scoreDocument(input, intent, textScoreWeight, providerDistanceWeight, _)))
    } yield scored.flatten

  private def scoreDocument(
    input: UserSearchInput,
    intent: ParsedSearchIntent,
    textScoreWeight: Double,
    providerDistanceWeight: Double,
    document: VariantSearchDocument,
  ): Either[QueryFailure, Option[ScoredDocument]] = {
    val explicitMatches = intent.explicitConstraints.foldLeft[Either[QueryFailure, Boolean]](Right(true)) {
      case (acc, constraint) =>
        for {
          alreadyMatches <- acc
          nextMatches <- SearchSpecSupport.matchesConstraint(spec, document, constraint)
        } yield alreadyMatches && nextMatches
    }

    if (!explicitMatches.toOption.exists(identity)) {
      Right(None)
    } else {
      softBoostScore(document, intent).map { boostScore =>
        val textScore = SearchSpecSupport.textScore(spec, document, intent.remainingText)
        val distanceKm = SearchSpecSupport.computeDistanceKm(input, document)
        val distanceBoost = distanceKm.map(distance => 1.0d / (1.0d + distance.toDouble) * providerDistanceWeight).getOrElse(0.0d)
        val finalTextScore = textScore * textScoreWeight + distanceBoost
        if (intent.remainingText.nonEmpty && textScore <= 0.0d && intent.explicitConstraints.isEmpty) {
          None
        } else {
          Some(
            ScoredDocument(
              document = document,
              textScore = finalTextScore,
              boostScore = boostScore,
              distanceKm = distanceKm,
            )
          )
        }
      }
    }
  }

  private def softBoostScore(
    document: VariantSearchDocument,
    intent: ParsedSearchIntent,
  ): Either[QueryFailure, Double] =
    intent.softBoosts.foldLeft[Either[QueryFailure, Double]](Right(0.0d)) {
      case (acc, constraint) =>
        for {
          score <- acc
          matches <- SearchSpecSupport.matchesConstraint(spec, document, constraint)
          next <- if (matches) SearchSpecSupport.constraintBoostWeight(spec, constraint).map(weight => score + weight * 0.5d)
                  else Right(score)
        } yield next
    }

  private def sequence[A](values: List[Either[QueryFailure, A]]): Either[QueryFailure, List[A]] =
    values.foldRight[Either[QueryFailure, List[A]]](Right(Nil)) {
      case (value, acc) =>
        for {
          tail <- acc
          head <- value
        } yield head :: tail
    }
}
