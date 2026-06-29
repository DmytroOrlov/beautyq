package leaderboard.search.document

import io.circe.{Codec, Decoder, Encoder, HCursor}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import izumi.functional.bio.Error2
import leaderboard.model.*
import leaderboard.repo.{BeautyQRepoGraph, Categories, GraphLoading, MasterLocations, MasterServiceOfferVariants, MasterServiceOffers, Masters, ServiceVariantSchemas, Services}
import leaderboard.model.Category.CategoryId
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
  def build(snapshot: BeautySearchCatalogSnapshot): Either[QueryFailure, List[VariantSearchDocument]] =
    BeautyQVariantSearchDocumentSchema.project(snapshot)
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
