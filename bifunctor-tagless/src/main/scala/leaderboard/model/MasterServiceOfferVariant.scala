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
    attributeDefinition: IntAttributeDefinition
  ): Option[Int] =
    attributes.get(attributeDefinition)

  def getAttribute(
    attributeDefinition: BigDecimalAttributeDefinition
  ): Option[BigDecimal] =
    attributes.get(attributeDefinition)

  def intAttributes: AttributeMap[Int] =
    attributes.intValues

  def bigDecimalAttributes: AttributeMap[BigDecimal] =
    attributes.bigDecimalValues

  @nowarn("cat=unused")
  private def copy(
    id: MasterServiceOfferVariantId                 = this.id,
    masterServiceOfferId: MasterServiceOfferId      = this.masterServiceOfferId,
    masterLocationId: MasterLocationId              = this.masterLocationId,
    priceFrom: BigDecimal                           = this.priceFrom,
    priceTo: BigDecimal                             = this.priceTo,
    durationMin: Int                                = this.durationMin,
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

  private def decodeAttributeDefinition[A](
    c: HCursor,
    attributeCode: String,
    fieldName: String,
    decode: String => Option[AttributeDefinition[A]],
  ): Decoder.Result[AttributeDefinition[A]] =
    decode(attributeCode) match {
      case Some(attributeDefinition) =>
        Right(attributeDefinition)
      case None =>
        AttributeDefinition.fromCode(attributeCode) match {
          case Some(_) =>
            Left(DecodingFailure(s"MasterServiceOfferVariant attribute code $attributeCode does not belong in $fieldName", c.history))
          case None =>
            Left(DecodingFailure(s"Unknown MasterServiceOfferVariant attribute code: $attributeCode", c.history))
        }
    }

  private def decodeAttributes[A: Decoder](
    c: HCursor,
    fieldName: String,
    decode: String => Option[AttributeDefinition[A]],
  ): Decoder.Result[AttributeMap[A]] =
    c.get[Option[Map[String, A]]](fieldName).flatMap {
      case Some(raw) =>
        raw.foldLeft[Decoder.Result[AttributeMap[A]]](Right(AttributeMap.empty)) {
          case (acc, (attributeCode, value)) =>
            for {
              current             <- acc
              attributeDefinition <- decodeAttributeDefinition[A](c, attributeCode, fieldName, decode)
            } yield {
              current.updated(attributeDefinition, value)
            }
        }
      case None =>
        Right(AttributeMap.empty)
    }

  private def encodeIntAttributes(value: AttributeMap[Int]) =
    value.iterator
      .map {
        case (attributeDefinition, attributeValue) =>
          attributeDefinition.code -> attributeValue
      }.toMap.asJson

  private def encodeBigDecimalAttributes(value: AttributeMap[BigDecimal]) =
    value.iterator
      .map {
        case (attributeDefinition, attributeValue) =>
          attributeDefinition.code -> attributeValue
      }.toMap.asJson

  private val decoder: Decoder[MasterServiceOfferVariant] = Decoder.instance {
    c =>
      for {
        id                   <- c.get[MasterServiceOfferVariantId]("id")
        masterServiceOfferId <- c.get[MasterServiceOfferId]("masterServiceOfferId")
        masterLocationId     <- c.get[MasterLocationId]("masterLocationId")
        priceFrom            <- c.get[BigDecimal]("priceFrom")
        priceTo              <- c.get[BigDecimal]("priceTo")
        durationMin          <- c.get[Int]("durationMin")
        intAttributes        <- decodeAttributes[Int](
          c,
          "intAttributes",
          AttributeDefinition.fromCodeAsInt,
        )
        bigDecimalAttributes <- decodeAttributes[BigDecimal](
          c,
          "bigDecimalAttributes",
          AttributeDefinition.fromCodeAsBigDecimal,
        )
        attributes = MasterServiceOfferVariantAttributes(intAttributes, bigDecimalAttributes)
        value     <- make(
          id,
          masterServiceOfferId,
          masterLocationId,
          priceFrom,
          priceTo,
          durationMin,
          attributes,
        ).left
          .map(error => DecodingFailure(error.message, c.history))
      } yield value
  }

  private val encoder: Encoder.AsObject[MasterServiceOfferVariant] = Encoder.AsObject.instance {
    value =>
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
