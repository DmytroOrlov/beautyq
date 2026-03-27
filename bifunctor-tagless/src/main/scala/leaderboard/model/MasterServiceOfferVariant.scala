package leaderboard.model

import io.circe.{Codec, Decoder, DecodingFailure, Encoder, JsonObject}
import io.circe.syntax.*
import leaderboard.model.MasterServiceOfferVariantValidationError.{NegativePriceFrom, NonPositiveDurationMin, PriceToLessThanPriceFrom}
import scala.annotation.nowarn

final case class MasterServiceOfferVariant private (
  id: MasterServiceOfferVariantId,
  masterServiceOfferId: MasterServiceOfferId,
  masterLocationId: MasterLocationId,
  priceFrom: BigDecimal,
  priceTo: BigDecimal,
  durationMin: Int,
) {
  @nowarn("cat=unused")
  private def copy(
    id: MasterServiceOfferVariantId = this.id,
    masterServiceOfferId: MasterServiceOfferId = this.masterServiceOfferId,
    masterLocationId: MasterLocationId = this.masterLocationId,
    priceFrom: BigDecimal = this.priceFrom,
    priceTo: BigDecimal = this.priceTo,
    durationMin: Int = this.durationMin,
  ): MasterServiceOfferVariant =
    new MasterServiceOfferVariant(id, masterServiceOfferId, masterLocationId, priceFrom, priceTo, durationMin)
}

object MasterServiceOfferVariant {
  private def apply(
    id: MasterServiceOfferVariantId,
    masterServiceOfferId: MasterServiceOfferId,
    masterLocationId: MasterLocationId,
    priceFrom: BigDecimal,
    priceTo: BigDecimal,
    durationMin: Int,
  ): MasterServiceOfferVariant =
    new MasterServiceOfferVariant(id, masterServiceOfferId, masterLocationId, priceFrom, priceTo, durationMin)

  def make(
    id: MasterServiceOfferVariantId,
    masterServiceOfferId: MasterServiceOfferId,
    masterLocationId: MasterLocationId,
    priceFrom: BigDecimal,
    priceTo: BigDecimal,
    durationMin: Int,
  ): Either[MasterServiceOfferVariantValidationError, MasterServiceOfferVariant] =
    if (priceFrom < 0) {
      Left(NegativePriceFrom(priceFrom))
    } else if (priceTo < priceFrom) {
      Left(PriceToLessThanPriceFrom(priceFrom, priceTo))
    } else if (durationMin <= 0) {
      Left(NonPositiveDurationMin(durationMin))
    } else {
      Right(apply(id, masterServiceOfferId, masterLocationId, priceFrom, priceTo, durationMin))
    }

  private val decoder: Decoder[MasterServiceOfferVariant] = Decoder.instance { c =>
    for {
      id                   <- c.get[MasterServiceOfferVariantId]("id")
      masterServiceOfferId <- c.get[MasterServiceOfferId]("masterServiceOfferId")
      masterLocationId     <- c.get[MasterLocationId]("masterLocationId")
      priceFrom            <- c.get[BigDecimal]("priceFrom")
      priceTo              <- c.get[BigDecimal]("priceTo")
      durationMin          <- c.get[Int]("durationMin")
      value                <- make(id, masterServiceOfferId, masterLocationId, priceFrom, priceTo, durationMin)
                                .left
                                .map(error => DecodingFailure(error.message, c.history))
    } yield value
  }

  private val encoder: Encoder.AsObject[MasterServiceOfferVariant] = Encoder.AsObject.instance { value =>
    JsonObject(
      "id"                   -> value.id.asJson,
      "masterServiceOfferId" -> value.masterServiceOfferId.asJson,
      "masterLocationId"     -> value.masterLocationId.asJson,
      "priceFrom"            -> value.priceFrom.asJson,
      "priceTo"              -> value.priceTo.asJson,
      "durationMin"          -> value.durationMin.asJson,
    )
  }

  implicit val codec: Codec.AsObject[MasterServiceOfferVariant] = Codec.AsObject.from(decoder, encoder)
}
