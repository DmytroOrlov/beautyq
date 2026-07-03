package leaderboard.search.document

import io.circe.{Codec, Decoder, Encoder, HCursor}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import leaderboard.model.*
import leaderboard.model.Category.CategoryId
import leaderboard.search.dsl.SearchGeoPoint

final case class VariantSearchDocument(
  variantId: MasterServiceOfferVariantId,
  masterServiceOfferId: MasterServiceOfferId,
  masterLocationId: MasterLocationId,
  masterId: MasterId,
  serviceId: ServiceId,
  categoryId: CategoryId,
  serviceName: String,
  categoryName: String,
  masterName: String,
  locationName: String,
  address: String,
  location: SearchGeoPoint,
  lat: BigDecimal,
  lon: BigDecimal,
  priceFrom: BigDecimal,
  priceTo: BigDecimal,
  durationMin: Int,
  enumAttributes: Map[String, String],
  booleanAttributes: Map[String, Boolean],
  intAttributes: Map[String, Int],
  bigDecimalAttributes: Map[String, BigDecimal],
  allText: String,
  serviceText: String,
  attributeText: String,
  providerText: String,
  locationText: String,
)

object VariantSearchDocument {
  implicit val searchGeoPointCodec: Codec.AsObject[SearchGeoPoint] = Codec.AsObject.from(deriveDecoder, deriveEncoder)
  implicit val encoder: Encoder.AsObject[VariantSearchDocument] = deriveEncoder
  implicit val decoder: Decoder[VariantSearchDocument] = Decoder.instance {
    c =>
      for {
        variantId <- c.get[MasterServiceOfferVariantId]("variantId")
        masterServiceOfferId <- c.get[MasterServiceOfferId]("masterServiceOfferId")
        masterLocationId <- c.get[MasterLocationId]("masterLocationId")
        masterId <- c.get[MasterId]("masterId")
        serviceId <- c.get[ServiceId]("serviceId")
        categoryId <- c.get[CategoryId]("categoryId")
        serviceName <- c.get[String]("serviceName")
        categoryName <- c.get[String]("categoryName")
        masterName <- c.get[String]("masterName")
        locationName <- c.get[String]("locationName")
        address <- c.get[String]("address")
        location <- c.get[SearchGeoPoint]("location")
        lat <- c.get[BigDecimal]("lat")
        lon <- c.get[BigDecimal]("lon")
        priceFrom <- c.get[BigDecimal]("priceFrom")
        priceTo <- c.get[BigDecimal]("priceTo")
        durationMin <- c.get[Int]("durationMin")
        enumAttributes <- getOrElse(c, "enumAttributes", Map.empty[String, String])
        booleanAttributes <- getOrElse(c, "booleanAttributes", Map.empty[String, Boolean])
        intAttributes <- getOrElse(c, "intAttributes", Map.empty[String, Int])
        bigDecimalAttributes <- getOrElse(c, "bigDecimalAttributes", Map.empty[String, BigDecimal])
        allText <- c.get[String]("allText")
        serviceText <- c.get[String]("serviceText")
        attributeText <- c.get[String]("attributeText")
        providerText <- c.get[String]("providerText")
        locationText <- c.get[String]("locationText")
      } yield VariantSearchDocument(
        variantId = variantId,
        masterServiceOfferId = masterServiceOfferId,
        masterLocationId = masterLocationId,
        masterId = masterId,
        serviceId = serviceId,
        categoryId = categoryId,
        serviceName = serviceName,
        categoryName = categoryName,
        masterName = masterName,
        locationName = locationName,
        address = address,
        location = location,
        lat = lat,
        lon = lon,
        priceFrom = priceFrom,
        priceTo = priceTo,
        durationMin = durationMin,
        enumAttributes = enumAttributes,
        booleanAttributes = booleanAttributes,
        intAttributes = intAttributes,
        bigDecimalAttributes = bigDecimalAttributes,
        allText = allText,
        serviceText = serviceText,
        attributeText = attributeText,
        providerText = providerText,
        locationText = locationText,
      )
  }
  implicit val codec: Codec.AsObject[VariantSearchDocument] = Codec.AsObject.from(decoder, encoder)

  private def getOrElse[A](cursor: HCursor, field: String, default: A)(implicit decoder: Decoder[A]): Decoder.Result[A] =
    cursor.get[Option[A]](field).map(_.getOrElse(default))
}
