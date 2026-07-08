package leaderboard.repo

import leaderboard.model.Category.{CategoryId, rootCategoryId}
import leaderboard.model.*
import leaderboard.search.document.{BeautyQSearchCatalogSnapshotLoader, BeautyQVariantSearchDocumentMaterialization}
import org.scalatest.wordspec.AnyWordSpec
import zio.{IO, Runtime, Unsafe, ZIO}

import java.util.UUID

final class BeautyQRepoGraphLoaderSpec extends AnyWordSpec {

  private def uuid(suffix: String): UUID = UUID.fromString(s"00000000-0000-0000-0000-$suffix")

  // --- deterministic immutable fixture graph ---
  private val categoryA      = Category(uuid("0000000000a1"), rootCategoryId, 0, "Category A")
  private val categoryB      = Category(uuid("0000000000b1"), rootCategoryId, 0, "Category B")
  private val categoryAChild = Category(uuid("0000000000a2"), categoryA.id, 1, "Category A Child")

  private val serviceA = Service(uuid("000000005e01"), categoryAChild.id, "Service A")
  private val serviceB = Service(uuid("000000005e02"), categoryB.id, "Service B")

  private val master   = Master(uuid("00000000a501"), "Master One")
  private val location = MasterLocation(uuid("00000010c001"), master.id, "Location One", "Address One", BigDecimal("52.5"), BigDecimal("13.4"))

  private val offerA = MasterServiceOffer(uuid("00000000ff01"), master.id, serviceA.id)
  private val offerB = MasterServiceOffer(uuid("00000000ff02"), master.id, serviceB.id)

  private val variantA = makeVariant(uuid("00000000aa01"), offerA.id, location.id)
  private val variantB = makeVariant(uuid("00000000aa02"), offerB.id, location.id)

  private def makeVariant(id: UUID, offerId: MasterServiceOfferId, locationId: MasterLocationId): MasterServiceOfferVariant =
    MasterServiceOfferVariant.make(id, offerId, locationId, BigDecimal(10), BigDecimal(20), 30) match {
      case Right(variant) =>
        variant
      case Left(error) =>
        sys.error(error.message)
    }

  private val loader = new BeautyQSearchCatalogSnapshotLoader.FromRepositories[IO](
    new StubCategories,
    new StubServices,
    new StubServiceVariantSchemas,
    new StubMasters,
    new StubMasterLocations,
    new StubMasterServiceOffers,
    new StubMasterServiceOfferVariants,
  )

  private val repositories = BeautyQCatalogGraph.Repositories[IO](
    new StubCategories,
    new StubServices,
    new StubServiceVariantSchemas,
    new StubMasters,
    new StubMasterLocations,
    new StubMasterServiceOffers,
    new StubMasterServiceOfferVariants,
  )

  "BeautyQ graph loader" should {
    val snapshot = runIO(loader.load())

    "load categories in preorder, excluding the synthetic root" in {
      assert(snapshot.categories == List(categoryA, categoryAChild, categoryB))
    }

    "preserve category traversal order for services" in {
      assert(snapshot.services == List(serviceA, serviceB))
    }

    "preserve service order for schemas" in {
      assert(snapshot.serviceVariantSchemas.map(_.serviceId) == List(serviceA.id, serviceB.id))
    }

    "preserve loader order for masters, locations, offers and variants" in {
      assert(snapshot.masters == List(master))
      assert(snapshot.masterLocations == List(location))
      assert(snapshot.masterServiceOffers == List(offerA, offerB))
      assert(snapshot.masterServiceOfferVariants == List(variantA, variantB))
    }

    "expose unchanged relation metadata via BeautyQCatalogGraph.Relations" in {
      val relations = new BeautyQCatalogGraph.Relations[IO](repositories)
      assert(relations.categoryServices.foreignKey.label == "categoryId")
      assert(relations.serviceSchemas.valueKey.label == "serviceId")
      assert(relations.offerVariants.foreignKey.label == "masterServiceOfferId")
    }

    "declare category -> service as a many edge keyed by categoryId, straight from the chain declaration" in {
      val declaration = BeautyQCatalogGraph.graph[IO]
      val relation = declaration
        .relationAs[BeautyQCatalogGraph.Repositories[IO] => Relation.HasMany[IO, Category, CategoryId, Service, ServiceId]]
        .apply(repositories)
      assert(relation.foreignKey.label == "categoryId")
    }

    "declare service -> serviceVariantSchema as a value edge keyed by serviceId, straight from the chain declaration" in {
      val declaration = BeautyQCatalogGraph.graph[IO]
      val relation = declaration
        .relationAs[BeautyQCatalogGraph.Repositories[IO] => Relation.HasValue[IO, Service, ServiceId, ServiceVariantSchema, ServiceId, ServiceVariantSchemaItem]]
        .apply(repositories)
      assert(relation.valueKey.label == "serviceId")
    }

    "declare masterServiceOffer -> masterServiceOfferVariant as a many edge keyed by masterServiceOfferId, straight from the chain declaration" in {
      val declaration = BeautyQCatalogGraph.graph[IO]
      val relation = declaration
        .relationAs[BeautyQCatalogGraph.Repositories[IO] => Relation.HasMany[IO, MasterServiceOffer, MasterServiceOfferId, MasterServiceOfferVariant, MasterServiceOfferVariantId]]
        .apply(repositories)
      assert(relation.foreignKey.label == "masterServiceOfferId")
    }

    "select a materialized relation factory by its exact type via TupleSelect" in {
      val materialized =
        catalog("beautyq")
          .branch[Category]
          .child[Service](_.categoryId)
          .materialize[IO, BeautyQCatalogGraph.Repositories[IO]](identity)

      val factory = materialized.relationAs[BeautyQCatalogGraph.Repositories[IO] => Relation.HasMany[IO, Category, CategoryId, Service, ServiceId]]
      assert(factory(repositories).foreignKey.label == "categoryId")
    }

    "produce a snapshot the document schema can fully project" in {
      BeautyQVariantSearchDocumentMaterialization.project(snapshot) match {
        case Right(documents) =>
          assert(documents.map(_.variantId) == List(variantA.id, variantB.id))
          assert(documents.map(_.serviceId) == List(serviceA.id, serviceB.id))
        case Left(failure) =>
          fail(s"Expected a fully projected snapshot, got: ${failure.message}")
      }
    }
  }

  // --- immutable stub repositories (read-only fixture, no mutable call logs) ---

  private final class StubCategories extends Categories[IO] {
    private val childrenByParent: Map[CategoryId, List[Category]] = Map(
      rootCategoryId  -> List(categoryA, categoryB),
      categoryA.id    -> List(categoryAChild),
    )
    private val byId: Map[CategoryId, Category] =
      List(categoryA, categoryB, categoryAChild).iterator.map(category => category.id -> category).toMap

    def upsertCategory(category: Category): IO[QueryFailure, Unit]          = ZIO.unit
    def getCategory(id: CategoryId): IO[QueryFailure, Option[Category]]     = ZIO.succeed(byId.get(id))
    def getChildren(parentId: CategoryId): IO[QueryFailure, List[Category]] = ZIO.succeed(childrenByParent.getOrElse(parentId, Nil))
  }

  private final class StubServices extends Services[IO] {
    private val byCategory: Map[CategoryId, List[Service]] = Map(
      categoryAChild.id -> List(serviceA),
      categoryB.id      -> List(serviceB),
    )
    private val byId: Map[ServiceId, Service] =
      List(serviceA, serviceB).iterator.map(service => service.id -> service).toMap

    def upsertService(service: Service): IO[QueryFailure, Unit]                       = ZIO.unit
    def getService(id: ServiceId): IO[QueryFailure, Option[Service]]                  = ZIO.succeed(byId.get(id))
    def getServicesByCategory(categoryId: CategoryId): IO[QueryFailure, List[Service]] = ZIO.succeed(byCategory.getOrElse(categoryId, Nil))
  }

  private final class StubServiceVariantSchemas extends ServiceVariantSchemas[IO] {
    def upsertServiceVariantSchema(schema: ServiceVariantSchema): IO[QueryFailure, Unit]    = ZIO.unit
    def getServiceVariantSchema(serviceId: ServiceId): IO[QueryFailure, ServiceVariantSchema] =
      ZIO.succeed(ServiceVariantSchema.empty(serviceId))
  }

  private final class StubMasters extends Masters[IO] {
    def upsertMaster(master: Master): IO[QueryFailure, Unit]        = ZIO.unit
    def getMaster(id: MasterId): IO[QueryFailure, Option[Master]]   = ZIO.succeed(Option.when(id == master.id)(master))
    def getMasters(): IO[QueryFailure, List[Master]]                = ZIO.succeed(List(master))
  }

  private final class StubMasterLocations extends MasterLocations[IO] {
    def upsertMasterLocation(loc: MasterLocation): IO[QueryFailure, Unit]                     = ZIO.unit
    def getMasterLocation(id: MasterLocationId): IO[QueryFailure, Option[MasterLocation]]     = ZIO.succeed(Option.when(id == location.id)(location))
    def getMasterLocationsByMaster(masterId: MasterId): IO[QueryFailure, List[MasterLocation]] =
      ZIO.succeed(if (masterId == master.id) List(location) else Nil)
  }

  private final class StubMasterServiceOffers extends MasterServiceOffers[IO] {
    private val byId: Map[MasterServiceOfferId, MasterServiceOffer] =
      List(offerA, offerB).iterator.map(offer => offer.id -> offer).toMap

    def upsertMasterServiceOffer(offer: MasterServiceOffer): IO[QueryFailure, Unit]                  = ZIO.unit
    def getMasterServiceOffer(id: MasterServiceOfferId): IO[QueryFailure, Option[MasterServiceOffer]] = ZIO.succeed(byId.get(id))
    def getMasterServiceOffersByMaster(masterId: MasterId): IO[QueryFailure, List[MasterServiceOffer]] =
      ZIO.succeed(if (masterId == master.id) List(offerA, offerB) else Nil)
    def getMasterServiceOffersByService(serviceId: ServiceId): IO[QueryFailure, List[MasterServiceOffer]] =
      ZIO.succeed(List(offerA, offerB).filter(_.serviceId == serviceId))
  }

  private final class StubMasterServiceOfferVariants extends MasterServiceOfferVariants[IO] {
    private val byOffer: Map[MasterServiceOfferId, List[MasterServiceOfferVariant]] = Map(
      offerA.id -> List(variantA),
      offerB.id -> List(variantB),
    )
    private val byId: Map[MasterServiceOfferVariantId, MasterServiceOfferVariant] =
      List(variantA, variantB).iterator.map(variant => variant.id -> variant).toMap

    def upsertMasterServiceOfferVariant(variant: MasterServiceOfferVariant): IO[QueryFailure, Unit]                       = ZIO.unit
    def getMasterServiceOfferVariant(id: MasterServiceOfferVariantId): IO[QueryFailure, Option[MasterServiceOfferVariant]] = ZIO.succeed(byId.get(id))
    def getMasterServiceOfferVariantsByOffer(offerId: MasterServiceOfferId): IO[QueryFailure, List[MasterServiceOfferVariant]] =
      ZIO.succeed(byOffer.getOrElse(offerId, Nil))
    def getMasterServiceOfferVariantsByLocation(locationId: MasterLocationId): IO[QueryFailure, List[MasterServiceOfferVariant]] =
      ZIO.succeed(List(variantA, variantB).filter(_.masterLocationId == locationId))
  }

  private def runIO[A](effect: IO[QueryFailure, A]): A =
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(effect).getOrThrowFiberFailure()
    }
}
