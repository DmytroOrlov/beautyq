package leaderboard.http

import io.circe.Codec
import io.circe.generic.semiauto
import izumi.functional.bio.Error2
import leaderboard.model.QueryFailure

sealed trait HttpApiFailure extends Product with Serializable

object HttpApiFailure {
  case object InternalServerError extends HttpApiFailure
  final case class NotFound(code: String, message: String) extends HttpApiFailure

  object NotFound {
    def category(id: leaderboard.model.Category.CategoryId): NotFound =
      NotFound(code = "not_found", message = s"Category '$id' was not found")

    def service(id: leaderboard.model.ServiceId): NotFound =
      NotFound(code = "not_found", message = s"Service '$id' was not found")

    def master(id: leaderboard.model.MasterId): NotFound =
      NotFound(code = "not_found", message = s"Master '$id' was not found")

    def masterLocation(id: leaderboard.model.MasterLocationId): NotFound =
      NotFound(code = "not_found", message = s"Master location '$id' was not found")

    def masterServiceOffer(id: leaderboard.model.MasterServiceOfferId): NotFound =
      NotFound(code = "not_found", message = s"Master service offer '$id' was not found")

    def masterServiceOfferVariant(id: leaderboard.model.MasterServiceOfferVariantId): NotFound =
      NotFound(code = "not_found", message = s"Master service offer variant '$id' was not found")
  }

  implicit val notFoundCodec: Codec.AsObject[NotFound] = semiauto.deriveCodec

  def fromQueryFailure(error: QueryFailure): HttpApiFailure =
    InternalServerError

  def fromQueryEffect[F[+_, +_]: Error2, A](effect: F[QueryFailure, A]): F[Nothing, Either[HttpApiFailure, A]] =
    effect.attempt.map(_.left.map(fromQueryFailure))

  def fromEither[E, A](value: Either[E, A]): Either[HttpApiFailure, A] =
    value.left.map(_ => InternalServerError)
}
