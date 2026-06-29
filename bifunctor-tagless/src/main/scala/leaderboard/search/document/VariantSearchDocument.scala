package leaderboard.search.document

import io.circe.{Codec, Decoder, Encoder, HCursor}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import izumi.functional.bio.Error2
import leaderboard.model.Category.CategoryId
import leaderboard.model.*
import leaderboard.repo.{BeautyQRepoGraph, Categories, GraphLoading, MasterLocations, MasterServiceOfferVariants, MasterServiceOffers, Masters, ServiceVariantSchemas, Services}
import leaderboard.search.dsl.SearchGeoPoint
import leaderboard.seed.{BeautyQSeedData, BeautyQSeedReady}

import scala.annotation.unused

final case class BeautySearchCatalogSnapshot(
  categories: List[Category],
  services: List[Service],
  serviceVariantSchemas: List[ServiceVariantSchema],
  masters: List[Master],
  masterLocations: List[MasterLocation],
  masterServiceOffers: List[MasterServiceOffer],
  masterServiceOfferVariants: List[MasterServiceOfferVariant],
)

object BeautySearchCatalogSnapshot {
  def fromSeedData(seed: BeautyQSeedData): BeautySearchCatalogSnapshot =
    BeautySearchCatalogSnapshot(
      categories = seed.categories,
      services = seed.services,
      serviceVariantSchemas = seed.serviceVariantSchemas,
      masters = seed.masters,
      masterLocations = seed.masterLocations,
      masterServiceOffers = seed.masterServiceOffers,
      masterServiceOfferVariants = seed.masterServiceOfferVariants,
    )
}

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

object VariantSearchDocumentBuilder {
  private val BuildOperationName = "build-variant-search-documents"

  def build(snapshot: BeautySearchCatalogSnapshot): Either[QueryFailure, List[VariantSearchDocument]] = {
    val categoriesById = snapshot.categories.iterator.map(category => category.id -> category).toMap
    val servicesById = snapshot.services.iterator.map(service => service.id -> service).toMap
    val schemasByServiceId = snapshot.serviceVariantSchemas.iterator.map(schema => schema.serviceId -> schema).toMap
    val mastersById = snapshot.masters.iterator.map(master => master.id -> master).toMap
    val locationsById = snapshot.masterLocations.iterator.map(location => location.id -> location).toMap
    val offersById = snapshot.masterServiceOffers.iterator.map(offer => offer.id -> offer).toMap

    snapshot.masterServiceOfferVariants.foldRight[Either[QueryFailure, List[VariantSearchDocument]]](Right(Nil)) {
      (variant, acc) =>
        for {
          tail <- acc
          next <- buildDocument(
            variant = variant,
            categoriesById = categoriesById,
            servicesById = servicesById,
            schemasByServiceId = schemasByServiceId,
            mastersById = mastersById,
            locationsById = locationsById,
            offersById = offersById,
          )
        } yield next :: tail
    }
  }

  private def buildDocument(
    variant: MasterServiceOfferVariant,
    categoriesById: Map[CategoryId, Category],
    servicesById: Map[ServiceId, Service],
    schemasByServiceId: Map[ServiceId, ServiceVariantSchema],
    mastersById: Map[MasterId, Master],
    locationsById: Map[MasterLocationId, MasterLocation],
    offersById: Map[MasterServiceOfferId, MasterServiceOffer],
  ): Either[QueryFailure, VariantSearchDocument] = {
    for {
      offer <- offersById.get(variant.masterServiceOfferId).toRight(missingJoin("MasterServiceOffer", variant.masterServiceOfferId, variant.id))
      service <- servicesById.get(offer.serviceId).toRight(missingJoin("Service", offer.serviceId, variant.id))
      category <- categoriesById.get(service.categoryId).toRight(missingJoin("Category", service.categoryId, variant.id))
      master <- mastersById.get(offer.masterId).toRight(missingJoin("Master", offer.masterId, variant.id))
      location <- locationsById.get(variant.masterLocationId).toRight(missingJoin("MasterLocation", variant.masterLocationId, variant.id))
      _ <- {
        if (location.masterId == offer.masterId) {
          Right(())
        } else {
          Left(
            QueryFailure.domain(
              s"$BuildOperationName: variant ${variant.id} joins offer ${offer.id} and location ${location.id} from different masters (${offer.masterId} != ${location.masterId})"
            )
          )
        }
      }
      _ <- validateAgainstSchema(variant, service.id, schemasByServiceId.get(service.id))
      enumAttributes = variant.enumAttributes.iterator.collect {
        case (definition: EnumAttributeDefinition[?], value) => definition.code -> value.stringCode
      }.toMap
      booleanAttributes = variant.booleanAttributes.iterator.map {
        case (definition, value) => definition.code -> value
      }.toMap
      intAttributes = variant.intAttributes.iterator.map {
        case (definition, value) => definition.code -> value
      }.toMap
      bigDecimalAttributes = variant.bigDecimalAttributes.iterator.map {
        case (definition, value) => definition.code -> value
      }.toMap
      attributeTokens = makeAttributeTokens(enumAttributes, booleanAttributes, intAttributes, bigDecimalAttributes)
      serviceText = normalizeText(List(service.name, category.name))
      attributeText = normalizeText(attributeTokens)
      providerText = normalizeText(List(master.name, location.name))
      locationText = normalizeText(List(location.name, location.address, category.name))
      allText = normalizeText(List(serviceText, attributeText, providerText, locationText))
    } yield VariantSearchDocument(
      variantId = variant.id,
      masterServiceOfferId = variant.masterServiceOfferId,
      masterLocationId = variant.masterLocationId,
      masterId = master.id,
      serviceId = service.id,
      categoryId = category.id,
      serviceName = service.name,
      categoryName = category.name,
      masterName = master.name,
      locationName = location.name,
      address = location.address,
      location = SearchGeoPoint(location.lat, location.lon),
      lat = location.lat,
      lon = location.lon,
      priceFrom = variant.priceFrom,
      priceTo = variant.priceTo,
      durationMin = variant.durationMin,
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

  private def validateAgainstSchema(
    variant: MasterServiceOfferVariant,
    serviceId: ServiceId,
    schema: Option[ServiceVariantSchema],
  ): Either[QueryFailure, Unit] =
    schema match {
      case Some(value) =>
        value.validate(variant.attributes).left.map {
          error =>
            QueryFailure.domain(s"$BuildOperationName: variant ${variant.id} violates schema for service $serviceId: $error")
        }
      case None =>
        Right(())
    }

  private def missingJoin(entityName: String, id: Any, variantId: MasterServiceOfferVariantId): QueryFailure =
    QueryFailure.domain(s"$BuildOperationName: missing $entityName $id while building document for variant $variantId")

  private def makeAttributeTokens(
    enumAttributes: Map[String, String],
    booleanAttributes: Map[String, Boolean],
    intAttributes: Map[String, Int],
    bigDecimalAttributes: Map[String, BigDecimal],
  ): List[String] = {
    val enumTokens = enumAttributes.toList.flatMap {
      case (code, value) =>
        List(code, value, humanize(code), humanize(value))
    }
    val booleanTokens = booleanAttributes.toList.flatMap {
      case (code, value) =>
        List(code, humanize(code), value.toString)
    }
    val intTokens = intAttributes.toList.flatMap {
      case (code, value) =>
        List(code, humanize(code), value.toString)
    }
    val decimalTokens = bigDecimalAttributes.toList.flatMap {
      case (code, value) =>
        List(code, humanize(code), value.toString())
    }

    enumTokens ++ booleanTokens ++ intTokens ++ decimalTokens
  }

  private def humanize(value: String): String =
    value.replace('_', ' ')

  private def normalizeText(parts: Iterable[String]): String =
    parts.iterator.map(_.trim).filter(_.nonEmpty).mkString(" ")
}

trait BeautySearchCatalogSnapshotLoader[F[_, _]] {
  def load(): F[QueryFailure, BeautySearchCatalogSnapshot]
}

object BeautySearchCatalogSnapshotLoader {

  /** Loads the full BeautyQ catalog by traversing the model-first repo graph.
    *
    * The traversal order, ordering guarantees and first-occurrence dedup are
    * defined by [[BeautyQRepoGraph]] (structure) and [[GraphLoading]] (generic
    * interpreter); this loader only orchestrates the relations and assembles
    * the snapshot.
    */
  final class FromRepositories[F[+_, +_]: Error2](
    categories: Categories[F],
    services: Services[F],
    serviceVariantSchemas: ServiceVariantSchemas[F],
    masters: Masters[F],
    masterLocations: MasterLocations[F],
    masterServiceOffers: MasterServiceOffers[F],
    masterServiceOfferVariants: MasterServiceOfferVariants[F],
  ) extends BeautySearchCatalogSnapshotLoader[F] {

    private val graph = new BeautyQRepoGraph[F](
      categories,
      services,
      serviceVariantSchemas,
      masters,
      masterLocations,
      masterServiceOffers,
      masterServiceOfferVariants,
    )

    override def load(): F[QueryFailure, BeautySearchCatalogSnapshot] =
      for {
        loadedCategories <- GraphLoading.selfTreeFrom(graph.categoryTree, Category.rootCategoryId)
        loadedServices   <- GraphLoading.manyFor(graph.categoryServices, loadedCategories)
        loadedSchemas    <- GraphLoading.valueFor(graph.serviceSchemas, loadedServices)
        loadedMasters    <- GraphLoading.allOf(graph.allMasters)
        loadedLocations  <- GraphLoading.manyFor(graph.masterLocationsByMaster, loadedMasters)
        loadedOffers     <- GraphLoading.manyFor(graph.masterOffersByMaster, loadedMasters)
        loadedVariants   <- GraphLoading.manyFor(graph.offerVariants, loadedOffers)
      } yield BeautySearchCatalogSnapshot(
        categories                 = loadedCategories,
        services                   = GraphLoading.distinctByKey(loadedServices)(_.id),
        serviceVariantSchemas      = GraphLoading.distinctByKey(loadedSchemas)(_.serviceId),
        masters                    = GraphLoading.distinctByKey(loadedMasters)(_.id),
        masterLocations            = GraphLoading.distinctByKey(loadedLocations)(_.id),
        masterServiceOffers        = GraphLoading.distinctByKey(loadedOffers)(_.id),
        masterServiceOfferVariants = GraphLoading.distinctByKey(loadedVariants)(_.id),
      )
  }

  /** Loads exactly the seed-scoped catalog through the shared repo operation
    * layer, failing with the canonical missing-entity message when a seed item
    * is absent.
    */
  final class SeedScopedFromRepositories[F[+_, +_]: Error2](
    @unused seedReady: BeautyQSeedReady,
    seed: BeautyQSeedData,
    categories: Categories[F],
    services: Services[F],
    serviceVariantSchemas: ServiceVariantSchemas[F],
    masters: Masters[F],
    masterLocations: MasterLocations[F],
    masterServiceOffers: MasterServiceOffers[F],
    masterServiceOfferVariants: MasterServiceOfferVariants[F],
  ) extends BeautySearchCatalogSnapshotLoader[F] {

    private val graph = new BeautyQRepoGraph[F](
      categories,
      services,
      serviceVariantSchemas,
      masters,
      masterLocations,
      masterServiceOffers,
      masterServiceOfferVariants,
    )

    override def load(): F[QueryFailure, BeautySearchCatalogSnapshot] =
      for {
        loadedCategories <- GraphLoading.seedRequired(seed.nonRootCategories, graph.categoryEntity.modelName, (_: Category).id, graph.categoryById)
        loadedServices   <- GraphLoading.seedRequired(seed.services, graph.serviceEntity.modelName, (_: Service).id, graph.serviceById)
        loadedSchemas    <- GraphLoading.seedValues(seed.services.map(_.id), graph.schemaByService)
        loadedMasters    <- GraphLoading.seedRequired(seed.masters, graph.masterEntity.modelName, (_: Master).id, graph.masterById)
        loadedLocations  <- GraphLoading.seedRequired(seed.masterLocations, graph.masterLocationEntity.modelName, (_: MasterLocation).id, graph.masterLocationById)
        loadedOffers     <- GraphLoading.seedRequired(seed.masterServiceOffers, graph.masterServiceOfferEntity.modelName, (_: MasterServiceOffer).id, graph.masterServiceOfferById)
        loadedVariants   <- GraphLoading.seedRequired(seed.masterServiceOfferVariants, graph.masterServiceOfferVariantEntity.modelName, (_: MasterServiceOfferVariant).id, graph.masterServiceOfferVariantById)
      } yield BeautySearchCatalogSnapshot(
        categories                 = loadedCategories,
        services                   = loadedServices,
        serviceVariantSchemas      = loadedSchemas,
        masters                    = loadedMasters,
        masterLocations            = loadedLocations,
        masterServiceOffers        = loadedOffers,
        masterServiceOfferVariants = loadedVariants,
      )
  }
}
