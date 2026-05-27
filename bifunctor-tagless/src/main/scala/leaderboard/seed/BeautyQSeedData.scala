package leaderboard.seed

import io.circe.generic.semiauto.deriveDecoder
import io.circe.{Decoder, DecodingFailure, HCursor, Json}
import leaderboard.model.*

final case class BeautyQSeedData(
  categories: List[Category],
  services: List[Service],
  serviceVariantSchemas: List[ServiceVariantSchema],
  masters: List[Master],
  masterLocations: List[MasterLocation],
  masterServiceOffers: List[MasterServiceOffer],
  masterServiceOfferVariants: List[MasterServiceOfferVariant],
) {
  def nonRootCategories: List[Category] =
    categories.filterNot(_.id == Category.rootCategoryId)
}

object BeautyQSeedData {
  private final case class SeedServiceVariantSchema(
    serviceId: ServiceId,
    items: List[SeedServiceVariantSchemaItem],
  )

  private object SeedServiceVariantSchema {
    implicit val decoder: Decoder[SeedServiceVariantSchema] = deriveDecoder
  }

  private final case class SeedServiceVariantSchemaItem(
    attributeCode: String,
    required: Boolean,
  )

  private object SeedServiceVariantSchemaItem {
    implicit val decoder: Decoder[SeedServiceVariantSchemaItem] = deriveDecoder
  }

  private def decodeRecordsCursor(c: HCursor): HCursor =
    c.downField("modelRecords").success.getOrElse(c)

  private def nonEmptySkipped(value: Json): Boolean =
    value.fold(
      jsonNull = false,
      jsonBoolean = _ => true,
      jsonNumber = _ => true,
      jsonString = _ => true,
      jsonArray = _.nonEmpty,
      jsonObject = _.values.exists(nonEmptySkipped),
    )

  private def validateNoSkipped(c: HCursor): Decoder.Result[Unit] = {
    val skipped = c.get[Option[Json]]("skippedRecords").map(_.exists(nonEmptySkipped))
    val blocked = c.get[Option[Json]]("blockedRecords").map(_.exists(nonEmptySkipped))

    for {
      hasSkipped <- skipped
      hasBlocked <- blocked
      _ <- {
        if (hasSkipped || hasBlocked) {
          Left(
            DecodingFailure(
              "Seed contains skipped/blocked records; expected empty skippedRecords and blockedRecords",
              c.history,
            )
          )
        } else {
          Right(())
        }
      }
    } yield ()
  }

  private def decodeSchemaItem(
    item: SeedServiceVariantSchemaItem,
    c: HCursor,
  ): Decoder.Result[ServiceVariantSchemaItem] =
    AttributeDefinition.fromCode(item.attributeCode) match {
      case Some(attribute) =>
        Right(ServiceVariantSchemaItem(attribute, item.required))
      case None =>
        Left(
          DecodingFailure(
            s"Unknown ServiceVariantSchema attribute code: ${item.attributeCode}",
            c.history,
          )
        )
    }

  private def decodeSchema(
    schema: SeedServiceVariantSchema,
    c: HCursor,
  ): Decoder.Result[ServiceVariantSchema] = {
    schema.items.foldLeft[Decoder.Result[List[ServiceVariantSchemaItem]]](Right(Nil)) {
      case (acc, next) =>
        for {
          current <- acc
          item    <- decodeSchemaItem(next, c)
        } yield item :: current
    }.map(items => ServiceVariantSchema.fromItems(schema.serviceId, items.reverse))
  }

  implicit val decoder: Decoder[BeautyQSeedData] = Decoder.instance {
    c =>
      val records = decodeRecordsCursor(c)

      for {
        _ <- validateNoSkipped(c)
        categories <- records.get[List[Category]]("categories")
        services <- records.get[List[Service]]("services")
        masters <- records.get[List[Master]]("masters")
        masterLocations <- records.get[List[MasterLocation]]("masterLocations")
        masterServiceOffers <- records.get[List[MasterServiceOffer]]("masterServiceOffers")
        masterServiceOfferVariants <- records.get[List[MasterServiceOfferVariant]]("masterServiceOfferVariants")
        serviceVariantSchemasAtRoot <- c.get[Option[List[SeedServiceVariantSchema]]]("serviceVariantSchemas")
        serviceVariantSchemasAtRecords <- records.get[Option[List[SeedServiceVariantSchema]]]("serviceVariantSchemas")
        serviceVariantSchemaSeed = serviceVariantSchemasAtRecords
          .orElse(serviceVariantSchemasAtRoot)
          .getOrElse(Nil)
        serviceVariantSchemas <- serviceVariantSchemaSeed.foldLeft[Decoder.Result[List[ServiceVariantSchema]]](Right(Nil)) {
          case (acc, schema) =>
            for {
              current <- acc
              decoded <- decodeSchema(schema, c)
            } yield decoded :: current
        }
      } yield BeautyQSeedData(
        categories = categories,
        services = services,
        serviceVariantSchemas = serviceVariantSchemas.reverse,
        masters = masters,
        masterLocations = masterLocations,
        masterServiceOffers = masterServiceOffers,
        masterServiceOfferVariants = masterServiceOfferVariants,
      )
  }
}
