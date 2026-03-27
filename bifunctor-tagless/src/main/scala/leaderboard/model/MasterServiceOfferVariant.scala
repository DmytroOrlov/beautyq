package leaderboard.model

import io.circe.{Codec, Decoder, DecodingFailure, Encoder, HCursor, JsonObject}
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
  attributes: MasterServiceOfferVariantAttributes,
) {
  def getAttribute(
    attributeDefinition: MasterServiceOfferVariantAttributeDefinition
  ): Option[MasterServiceOfferVariantAttributeValue] =
    attributes.values.get(attributeDefinition)

  def attributesByType(
    valueType: AttributeValueType
  ): Map[MasterServiceOfferVariantAttributeDefinition, MasterServiceOfferVariantAttributeValue] =
    attributes.valuesByType(valueType)

  def intAttributes: Map[MasterServiceOfferVariantAttributeDefinition, Int] =
    attributes.intValues

  def bigDecimalAttributes: Map[MasterServiceOfferVariantAttributeDefinition, BigDecimal] =
    attributes.bigDecimalValues

  @nowarn("cat=unused")
  private def copy(
    id: MasterServiceOfferVariantId = this.id,
    masterServiceOfferId: MasterServiceOfferId = this.masterServiceOfferId,
    masterLocationId: MasterLocationId = this.masterLocationId,
    priceFrom: BigDecimal = this.priceFrom,
    priceTo: BigDecimal = this.priceTo,
    durationMin: Int = this.durationMin,
    attributes: MasterServiceOfferVariantAttributes = this.attributes,
  ): MasterServiceOfferVariant =
    new MasterServiceOfferVariant(
      id,
      masterServiceOfferId,
      masterLocationId,
      priceFrom,
      priceTo,
      durationMin,
      attributes,
    )
}

object MasterServiceOfferVariant {
  private def apply(
    id: MasterServiceOfferVariantId,
    masterServiceOfferId: MasterServiceOfferId,
    masterLocationId: MasterLocationId,
    priceFrom: BigDecimal,
    priceTo: BigDecimal,
    durationMin: Int,
    attributes: MasterServiceOfferVariantAttributes,
  ): MasterServiceOfferVariant =
    new MasterServiceOfferVariant(
      id,
      masterServiceOfferId,
      masterLocationId,
      priceFrom,
      priceTo,
      durationMin,
      attributes,
    )

  def make(
    id: MasterServiceOfferVariantId,
    masterServiceOfferId: MasterServiceOfferId,
    masterLocationId: MasterLocationId,
    priceFrom: BigDecimal,
    priceTo: BigDecimal,
    durationMin: Int,
    attributes: MasterServiceOfferVariantAttributes = MasterServiceOfferVariantAttributes.empty,
  ): Either[MasterServiceOfferVariantValidationError, MasterServiceOfferVariant] =
    if (priceFrom < 0) {
      Left(NegativePriceFrom(priceFrom))
    } else if (priceTo < priceFrom) {
      Left(PriceToLessThanPriceFrom(priceFrom, priceTo))
    } else if (durationMin <= 0) {
      Left(NonPositiveDurationMin(durationMin))
    } else {
      Right(
        apply(
          id,
          masterServiceOfferId,
          masterLocationId,
          priceFrom,
          priceTo,
          durationMin,
          attributes,
        )
      )
    }

  private def decodeIntAttributes(c: HCursor): Decoder.Result[Map[MasterServiceOfferVariantAttributeDefinition, Int]] =
    c.get[Option[Map[String, Int]]]("intAttributes").flatMap {
      case Some(raw) =>
        raw.foldLeft[Decoder.Result[Map[MasterServiceOfferVariantAttributeDefinition, Int]]](Right(Map.empty)) {
          case (acc, (attributeCode, value)) =>
            acc.flatMap {
              current =>
                MasterServiceOfferVariantAttributeDefinition.fromCode(attributeCode) match {
                  case Some(attributeDefinition) =>
                    Right(current + (attributeDefinition -> value))
                  case None =>
                    Left(DecodingFailure(s"Unknown MasterServiceOfferVariant attribute code: $attributeCode", c.history))
                }
            }
        }
      case None =>
        Right(Map.empty)
    }

  private def decodeBigDecimalAttributes(
    c: HCursor
  ): Decoder.Result[Map[MasterServiceOfferVariantAttributeDefinition, BigDecimal]] =
    c.get[Option[Map[String, BigDecimal]]]("bigDecimalAttributes").flatMap {
      case Some(raw) =>
        raw.foldLeft[Decoder.Result[Map[MasterServiceOfferVariantAttributeDefinition, BigDecimal]]](Right(Map.empty)) {
          case (acc, (attributeCode, value)) =>
            acc.flatMap {
              current =>
                MasterServiceOfferVariantAttributeDefinition.fromCode(attributeCode) match {
                  case Some(attributeDefinition) =>
                    Right(current + (attributeDefinition -> value))
                  case None =>
                    Left(DecodingFailure(s"Unknown MasterServiceOfferVariant attribute code: $attributeCode", c.history))
                }
            }
        }
      case None =>
        Right(Map.empty)
    }

  private def encodeIntAttributes(value: Map[MasterServiceOfferVariantAttributeDefinition, Int]) =
    value.iterator.map {
      case (attributeDefinition, attributeValue) =>
        attributeDefinition.code -> attributeValue
    }.toMap.asJson

  private def encodeBigDecimalAttributes(value: Map[MasterServiceOfferVariantAttributeDefinition, BigDecimal]) =
    value.iterator.map {
      case (attributeDefinition, attributeValue) =>
        attributeDefinition.code -> attributeValue
    }.toMap.asJson

  private val decoder: Decoder[MasterServiceOfferVariant] = Decoder.instance { c =>
    for {
      id                   <- c.get[MasterServiceOfferVariantId]("id")
      masterServiceOfferId <- c.get[MasterServiceOfferId]("masterServiceOfferId")
      masterLocationId     <- c.get[MasterLocationId]("masterLocationId")
      priceFrom            <- c.get[BigDecimal]("priceFrom")
      priceTo              <- c.get[BigDecimal]("priceTo")
      durationMin          <- c.get[Int]("durationMin")
      intAttributes        <- decodeIntAttributes(c)
      bigDecimalAttributes <- decodeBigDecimalAttributes(c)
      attributes          <- MasterServiceOfferVariantAttributes
                               .make(intAttributes, bigDecimalAttributes)
                               .left
                               .map(error => DecodingFailure(error.message, c.history))
      value                <- make(
                                id,
                                masterServiceOfferId,
                                masterLocationId,
                                priceFrom,
                                priceTo,
                                durationMin,
                                attributes,
                              )
                                .left
                                .map(error => DecodingFailure(error.message, c.history))
    } yield value
  }

  private val encoder: Encoder.AsObject[MasterServiceOfferVariant] = Encoder.AsObject.instance { value =>
    JsonObject.fromIterable(
      List(
        "id"                   -> value.id.asJson,
        "masterServiceOfferId" -> value.masterServiceOfferId.asJson,
        "masterLocationId"     -> value.masterLocationId.asJson,
        "priceFrom"            -> value.priceFrom.asJson,
        "priceTo"              -> value.priceTo.asJson,
        "durationMin"          -> value.durationMin.asJson,
      ) ++ Option.when(value.attributes.intValues.nonEmpty)("intAttributes" -> encodeIntAttributes(value.attributes.intValues)) ++ Option.when(
        value.attributes.bigDecimalValues.nonEmpty
      )("bigDecimalAttributes" -> encodeBigDecimalAttributes(value.attributes.bigDecimalValues))
    )
  }

  implicit val codec: Codec.AsObject[MasterServiceOfferVariant] = Codec.AsObject.from(decoder, encoder)
}
