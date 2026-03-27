package leaderboard.model

import io.circe.{Codec, Decoder, DecodingFailure, Encoder, JsonObject}
import io.circe.syntax.*
import leaderboard.model.MasterServiceOfferLocationValidationError.{NegativePriceFrom, PriceToLessThanPriceFrom}
import scala.annotation.nowarn

final case class MasterServiceOfferLocation private (
  id: MasterServiceOfferLocationId,
  masterServiceOfferId: MasterServiceOfferId,
  masterLocationId: MasterLocationId,
  priceFrom: BigDecimal,
  priceTo: BigDecimal,
) {
  @nowarn("cat=unused")
  private def copy(
    id: MasterServiceOfferLocationId = this.id,
    masterServiceOfferId: MasterServiceOfferId = this.masterServiceOfferId,
    masterLocationId: MasterLocationId = this.masterLocationId,
    priceFrom: BigDecimal = this.priceFrom,
    priceTo: BigDecimal = this.priceTo,
  ): MasterServiceOfferLocation =
    new MasterServiceOfferLocation(id, masterServiceOfferId, masterLocationId, priceFrom, priceTo)
}

object MasterServiceOfferLocation {
  private def apply(
    id: MasterServiceOfferLocationId,
    masterServiceOfferId: MasterServiceOfferId,
    masterLocationId: MasterLocationId,
    priceFrom: BigDecimal,
    priceTo: BigDecimal,
  ): MasterServiceOfferLocation =
    new MasterServiceOfferLocation(id, masterServiceOfferId, masterLocationId, priceFrom, priceTo)

  def make(
    id: MasterServiceOfferLocationId,
    masterServiceOfferId: MasterServiceOfferId,
    masterLocationId: MasterLocationId,
    priceFrom: BigDecimal,
    priceTo: BigDecimal,
  ): Either[MasterServiceOfferLocationValidationError, MasterServiceOfferLocation] =
    if (priceFrom < 0) {
      Left(NegativePriceFrom(priceFrom))
    } else if (priceTo < priceFrom) {
      Left(PriceToLessThanPriceFrom(priceFrom, priceTo))
    } else {
      Right(apply(id, masterServiceOfferId, masterLocationId, priceFrom, priceTo))
    }

  private val decoder: Decoder[MasterServiceOfferLocation] = Decoder.instance { c =>
    for {
      id                   <- c.get[MasterServiceOfferLocationId]("id")
      masterServiceOfferId <- c.get[MasterServiceOfferId]("masterServiceOfferId")
      masterLocationId     <- c.get[MasterLocationId]("masterLocationId")
      priceFrom            <- c.get[BigDecimal]("priceFrom")
      priceTo              <- c.get[BigDecimal]("priceTo")
      value                <- make(id, masterServiceOfferId, masterLocationId, priceFrom, priceTo)
                                .left
                                .map(error => DecodingFailure(error.message, c.history))
    } yield value
  }

  private val encoder: Encoder.AsObject[MasterServiceOfferLocation] = Encoder.AsObject.instance { value =>
    JsonObject(
      "id"                   -> value.id.asJson,
      "masterServiceOfferId" -> value.masterServiceOfferId.asJson,
      "masterLocationId"     -> value.masterLocationId.asJson,
      "priceFrom"            -> value.priceFrom.asJson,
      "priceTo"              -> value.priceTo.asJson,
    )
  }

  implicit val codec: Codec.AsObject[MasterServiceOfferLocation] = Codec.AsObject.from(decoder, encoder)
}
