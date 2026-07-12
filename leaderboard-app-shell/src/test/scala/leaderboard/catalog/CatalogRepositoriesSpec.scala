package leaderboard

import leaderboard.model.*
import leaderboard.model.Category.{CategoryId, rootCategoryId}
import leaderboard.repo.{Categories, MasterLocations, MasterServiceOffers, Masters, Services}
import zio.{IO, ZIO}

// AI-NOTE: For distage testkit memoizationRoots, Activation, and BIO patterns used here, see docs/LOCAL_LLM_IZUMI_DISTAGE_BIO_REFERENCE.md
abstract class CategoriesTest extends LeaderboardTest {

  "Categories" should {

    "upsert & get for an ordinary category" in {
      (rnd: Rnd[IO], categories: Categories[IO]) =>
        for {
          parentId <- rnd[CategoryId]
          childId  <- rnd[CategoryId]
          parent    = Category(parentId, testCategoryCode(parentId), rootCategoryId, 0, s"parent-$parentId")
          child     = Category(childId, testCategoryCode(childId), parentId, 1, s"child-$childId")
          _        <- categories.upsertCategory(parent)
          _        <- categories.upsertCategory(child)
          res      <- categories.getCategory(child.id)
          _        <- assertIO(res.contains(child))
        } yield ()
    }

    "allow creating a top-level category with parentId == rootCategoryId" in {
      (rnd: Rnd[IO], categories: Categories[IO]) =>
        for {
          id      <- rnd[CategoryId]
          category = Category(id, testCategoryCode(id), rootCategoryId, 0, s"top-$id")
          _       <- categories.upsertCategory(category)
          res     <- categories.getCategory(category.id)
          _       <- assertIO(res.contains(category))
        } yield ()
    }

    "reject creating a category with id == rootCategoryId" in {
      (rnd: Rnd[IO], categories: Categories[IO]) =>
        for {
          parentId <- rnd[CategoryId]
          result   <- categories.upsertCategory(Category(rootCategoryId, testCategoryCode(rootCategoryId), parentId, 1, "illegal-root")).either
          _        <- assertIO(result.isLeft)
        } yield ()
    }

    "reject creating a category with a missing non-root parent" in {
      (rnd: Rnd[IO], categories: Categories[IO]) =>
        for {
          id       <- rnd[CategoryId]
          parentId <- rnd[CategoryId]
          result   <- categories.upsertCategory(Category(id, testCategoryCode(id), parentId, 1, s"orphan-$id")).either
          _        <- assertIO(result.isLeft)
        } yield ()
    }

    "return only children of the requested parent" in {
      (rnd: Rnd[IO], categories: Categories[IO]) =>
        for {
          parent1Id <- rnd[CategoryId]
          parent2Id <- rnd[CategoryId]
          child1Id  <- rnd[CategoryId]
          child2Id  <- rnd[CategoryId]
          otherId   <- rnd[CategoryId]

          parent1 = Category(parent1Id, testCategoryCode(parent1Id), rootCategoryId, 0, s"parent-a-$parent1Id")
          parent2 = Category(parent2Id, testCategoryCode(parent2Id), rootCategoryId, 0, s"parent-b-$parent2Id")
          child1  = Category(child1Id, testCategoryCode(child1Id), parent1Id, 1, s"child-a-$child1Id")
          child2  = Category(child2Id, testCategoryCode(child2Id), parent1Id, 1, s"child-b-$child2Id")
          other   = Category(otherId, testCategoryCode(otherId), parent2Id, 1, s"child-c-$otherId")

          _   <- categories.upsertCategory(parent1)
          _   <- categories.upsertCategory(parent2)
          _   <- categories.upsertCategory(child1)
          _   <- categories.upsertCategory(child2)
          _   <- categories.upsertCategory(other)
          res <- categories.getChildren(parent1Id)

          _ <- assertIO(res.toSet == Set(child1, child2))
        } yield ()
    }

    "return children sorted by depth asc and then name asc" in {
      (rnd: Rnd[IO], categories: Categories[IO]) =>
        for {
          parentId <- rnd[CategoryId]
          id1      <- rnd[CategoryId]
          id2      <- rnd[CategoryId]
          id3      <- rnd[CategoryId]

          parent = Category(parentId, testCategoryCode(parentId), rootCategoryId, 0, s"parent-sort-$parentId")
          c1     = Category(id1, testCategoryCode(id1), parentId, 1, "beta")
          c2     = Category(id2, testCategoryCode(id2), parentId, 1, "alpha")
          c3     = Category(id3, testCategoryCode(id3), parentId, 2, "aardvark")

          _   <- categories.upsertCategory(parent)
          _   <- categories.upsertCategory(c1)
          _   <- categories.upsertCategory(c2)
          _   <- categories.upsertCategory(c3)
          res <- categories.getChildren(parentId)

          _ <- assertIO(res == List(c2, c1, c3))
        } yield ()
    }

    "round-trip the code field and return the complete category by code" in {
      (rnd: Rnd[IO], categories: Categories[IO]) =>
        for {
          id      <- rnd[CategoryId]
          category = Category(id, testCategoryCode(id), rootCategoryId, 0, s"code-roundtrip-$id")
          _       <- categories.upsertCategory(category)
          byId    <- categories.getCategory(id)
          byCode  <- categories.getCategoryByCode(category.code)
          _       <- assertIO(byId.contains(category))
          _       <- assertIO(byCode.contains(category))
        } yield ()
    }

    "return None for an unknown category code" in {
      (categories: Categories[IO]) =>
        for {
          result <- categories.getCategoryByCode(CategoryCode.unsafeFromString("unknown_category_code_zzz"))
          _      <- assertIO(result.isEmpty)
        } yield ()
    }

    "reject a duplicate category code owned by another id" in {
      (rnd: Rnd[IO], categories: Categories[IO]) =>
        for {
          id1       <- rnd[CategoryId]
          id2       <- rnd[CategoryId]
          sharedCode = testCategoryCode(id1)
          first      = Category(id1, sharedCode, rootCategoryId, 0, s"dup-code-a-$id1")
          second     = Category(id2, sharedCode, rootCategoryId, 0, s"dup-code-b-$id2")
          _         <- categories.upsertCategory(first)
          result    <- categories.upsertCategory(second).either
          _         <- assertIO(result == Left(QueryFailure.domain(s"Category code '${sharedCode.value}' is already used by category $id1")))
        } yield ()
    }

    "reject changing the code of an existing category id" in {
      (rnd: Rnd[IO], categories: Categories[IO]) =>
        for {
          id            <- rnd[CategoryId]
          originalCode   = testCategoryCode(id)
          requestedCode  = CategoryCode.unsafeFromString(s"${originalCode.value}_renamed")
          original       = Category(id, originalCode, rootCategoryId, 0, s"immutable-code-$id")
          requested      = original.copy(code = requestedCode)
          _             <- categories.upsertCategory(original)
          result        <- categories.upsertCategory(requested).either
          _             <- assertIO(result == Left(QueryFailure.domain(s"Category $id code is immutable: existing '${originalCode.value}', requested '${requestedCode.value}'")))
        } yield ()
    }

    "allow updating mutable fields for the same id and same code" in {
      (rnd: Rnd[IO], categories: Categories[IO]) =>
        for {
          parentId <- rnd[CategoryId]
          id       <- rnd[CategoryId]
          parent    = Category(parentId, testCategoryCode(parentId), rootCategoryId, 0, s"same-code-parent-$parentId")
          code      = testCategoryCode(id)
          original  = Category(id, code, rootCategoryId, 0, s"same-code-original-$id")
          updated   = Category(id, code, parentId, 1, s"same-code-updated-$id")
          _        <- categories.upsertCategory(parent)
          _        <- categories.upsertCategory(original)
          _        <- categories.upsertCategory(updated)
          res      <- categories.getCategory(id)
          _        <- assertIO(res.contains(updated))
        } yield ()
    }

  }

}

abstract class ServicesTest extends LeaderboardTest {

  "Services" should {

    "upsert & get for an ordinary service" in {
      (rnd: Rnd[IO], categories: Categories[IO], services: Services[IO]) =>
        for {
          categoryId <- rnd[CategoryId]
          serviceId  <- rnd[ServiceId]
          category    = Category(categoryId, testCategoryCode(categoryId), rootCategoryId, 0, s"service-parent-$categoryId")
          service     = Service(serviceId, testServiceCode(serviceId), categoryId, s"service-$serviceId")
          _          <- categories.upsertCategory(category)
          _          <- services.upsertService(service)
          res        <- services.getService(service.id)
          _          <- assertIO(res.contains(service))
        } yield ()
    }

    "reject creating a service with categoryId == rootCategoryId" in {
      (rnd: Rnd[IO], services: Services[IO]) =>
        for {
          serviceId <- rnd[ServiceId]
          result    <- services.upsertService(Service(serviceId, testServiceCode(serviceId), rootCategoryId, "illegal-root-service")).either
          _         <- assertIO(result.isLeft)
        } yield ()
    }

    "reject creating a service when category does not exist" in {
      (rnd: Rnd[IO], services: Services[IO]) =>
        for {
          serviceId  <- rnd[ServiceId]
          categoryId <- rnd[CategoryId]
          result     <- services.upsertService(Service(serviceId, testServiceCode(serviceId), categoryId, s"orphan-service-$serviceId")).either
          _          <- assertIO(result.isLeft)
        } yield ()
    }

    "allow creating a service for an existing category" in {
      (rnd: Rnd[IO], categories: Categories[IO], services: Services[IO]) =>
        for {
          categoryId <- rnd[CategoryId]
          serviceId  <- rnd[ServiceId]
          category    = Category(categoryId, testCategoryCode(categoryId), rootCategoryId, 0, s"existing-category-$categoryId")
          service     = Service(serviceId, testServiceCode(serviceId), categoryId, s"existing-service-$serviceId")
          _          <- categories.upsertCategory(category)
          _          <- services.upsertService(service)
          res        <- services.getService(service.id)
          _          <- assertIO(res.contains(service))
        } yield ()
    }

    "return only services of the requested category" in {
      (rnd: Rnd[IO], categories: Categories[IO], services: Services[IO]) =>
        for {
          category1Id <- rnd[CategoryId]
          category2Id <- rnd[CategoryId]
          service1Id  <- rnd[ServiceId]
          service2Id  <- rnd[ServiceId]
          otherId     <- rnd[ServiceId]

          category1 = Category(category1Id, testCategoryCode(category1Id), rootCategoryId, 0, s"services-parent-a-$category1Id")
          category2 = Category(category2Id, testCategoryCode(category2Id), rootCategoryId, 0, s"services-parent-b-$category2Id")
          service1  = Service(service1Id, testServiceCode(service1Id), category1Id, s"service-a-$service1Id")
          service2  = Service(service2Id, testServiceCode(service2Id), category1Id, s"service-b-$service2Id")
          other     = Service(otherId, testServiceCode(otherId), category2Id, s"service-c-$otherId")

          _   <- categories.upsertCategory(category1)
          _   <- categories.upsertCategory(category2)
          _   <- services.upsertService(service1)
          _   <- services.upsertService(service2)
          _   <- services.upsertService(other)
          res <- services.getServicesByCategory(category1Id)

          _ <- assertIO(res.toSet == Set(service1, service2))
        } yield ()
    }

    "return services sorted by name asc" in {
      (rnd: Rnd[IO], categories: Categories[IO], services: Services[IO]) =>
        for {
          categoryId <- rnd[CategoryId]
          id1        <- rnd[ServiceId]
          id2        <- rnd[ServiceId]
          id3        <- rnd[ServiceId]

          category = Category(categoryId, testCategoryCode(categoryId), rootCategoryId, 0, s"services-sort-$categoryId")
          s1       = Service(id1, testServiceCode(id1), categoryId, "gamma")
          s2       = Service(id2, testServiceCode(id2), categoryId, "alpha")
          s3       = Service(id3, testServiceCode(id3), categoryId, "beta")

          _   <- categories.upsertCategory(category)
          _   <- services.upsertService(s1)
          _   <- services.upsertService(s2)
          _   <- services.upsertService(s3)
          res <- services.getServicesByCategory(categoryId)

          _ <- assertIO(res == List(s2, s3, s1))
        } yield ()
    }

    "round-trip the code field and return the complete service by code" in {
      (rnd: Rnd[IO], categories: Categories[IO], services: Services[IO]) =>
        for {
          categoryId <- rnd[CategoryId]
          serviceId  <- rnd[ServiceId]
          category    = Category(categoryId, testCategoryCode(categoryId), rootCategoryId, 0, s"service-code-roundtrip-category-$categoryId")
          service     = Service(serviceId, testServiceCode(serviceId), categoryId, s"service-code-roundtrip-$serviceId")
          _          <- categories.upsertCategory(category)
          _          <- services.upsertService(service)
          byId       <- services.getService(serviceId)
          byCode     <- services.getServiceByCode(service.code)
          _          <- assertIO(byId.contains(service))
          _          <- assertIO(byCode.contains(service))
        } yield ()
    }

    "return None for an unknown service code" in {
      (services: Services[IO]) =>
        for {
          result <- services.getServiceByCode(ServiceCode.unsafeFromString("unknown_service_code_zzz"))
          _      <- assertIO(result.isEmpty)
        } yield ()
    }

    "reject a duplicate service code owned by another id" in {
      (rnd: Rnd[IO], categories: Categories[IO], services: Services[IO]) =>
        for {
          categoryId <- rnd[CategoryId]
          id1        <- rnd[ServiceId]
          id2        <- rnd[ServiceId]
          category    = Category(categoryId, testCategoryCode(categoryId), rootCategoryId, 0, s"service-dup-code-category-$categoryId")
          sharedCode  = testServiceCode(id1)
          first       = Service(id1, sharedCode, categoryId, s"dup-code-a-$id1")
          second      = Service(id2, sharedCode, categoryId, s"dup-code-b-$id2")
          _          <- categories.upsertCategory(category)
          _          <- services.upsertService(first)
          result     <- services.upsertService(second).either
          _          <- assertIO(result == Left(QueryFailure.domain(s"Service code '${sharedCode.value}' is already used by service $id1")))
        } yield ()
    }

    "reject changing the code of an existing service id" in {
      (rnd: Rnd[IO], categories: Categories[IO], services: Services[IO]) =>
        for {
          categoryId    <- rnd[CategoryId]
          id            <- rnd[ServiceId]
          category       = Category(categoryId, testCategoryCode(categoryId), rootCategoryId, 0, s"service-immutable-code-category-$categoryId")
          originalCode   = testServiceCode(id)
          requestedCode  = ServiceCode.unsafeFromString(s"${originalCode.value}_renamed")
          original       = Service(id, originalCode, categoryId, s"immutable-code-$id")
          requested      = original.copy(code = requestedCode)
          _             <- categories.upsertCategory(category)
          _             <- services.upsertService(original)
          result        <- services.upsertService(requested).either
          _             <- assertIO(result == Left(QueryFailure.domain(s"Service $id code is immutable: existing '${originalCode.value}', requested '${requestedCode.value}'")))
        } yield ()
    }

    "allow updating mutable fields for the same id and same code" in {
      (rnd: Rnd[IO], categories: Categories[IO], services: Services[IO]) =>
        for {
          category1Id <- rnd[CategoryId]
          category2Id <- rnd[CategoryId]
          id          <- rnd[ServiceId]
          category1    = Category(category1Id, testCategoryCode(category1Id), rootCategoryId, 0, s"service-same-code-category-a-$category1Id")
          category2    = Category(category2Id, testCategoryCode(category2Id), rootCategoryId, 0, s"service-same-code-category-b-$category2Id")
          code         = testServiceCode(id)
          original     = Service(id, code, category1Id, s"same-code-original-$id")
          updated      = Service(id, code, category2Id, s"same-code-updated-$id")
          _           <- categories.upsertCategory(category1)
          _           <- categories.upsertCategory(category2)
          _           <- services.upsertService(original)
          _           <- services.upsertService(updated)
          res         <- services.getService(id)
          _           <- assertIO(res.contains(updated))
        } yield ()
    }

  }

}

abstract class MastersTest extends LeaderboardTest {

  "Masters" should {

    "upsert & get" in {
      (rnd: Rnd[IO], masters: Masters[IO]) =>
        for {
          id    <- rnd[MasterId]
          master = Master(id, s"name-$id")
          _     <- masters.upsertMaster(master)
          res   <- masters.getMaster(master.id)
          _     <- assertIO(res.contains(master))
        } yield ()
    }

    "getMasters returns inserted masters" in {
      (rnd: Rnd[IO], masters: Masters[IO]) =>
        for {
          prefix <- rnd[MasterId].map(id => s"masters-list-$id")
          id1    <- rnd[MasterId]
          id2    <- rnd[MasterId]
          m1      = Master(id1, s"$prefix-a")
          m2      = Master(id2, s"$prefix-b")
          _      <- masters.upsertMaster(m1)
          _      <- masters.upsertMaster(m2)
          res    <- masters.getMasters().map(_.filter(_.name.startsWith(prefix)))
          _      <- assertIO(res.toSet == Set(m1, m2))
        } yield ()
    }

    "getMasters sorted by name asc, then id asc" in {
      (masters: Masters[IO]) =>
        val prefix = s"masters-sort-${java.util.UUID.randomUUID()}"
        val id1    = MasterId.fromString("00000000-0000-0000-0000-000000000002")
        val id2    = MasterId.fromString("00000000-0000-0000-0000-000000000001")
        val id3    = MasterId.fromString("00000000-0000-0000-0000-000000000003")
        val m1     = Master(id1, s"$prefix-beta")
        val m2     = Master(id2, s"$prefix-alpha")
        val m3     = Master(id3, s"$prefix-alpha")
        for {
          _   <- masters.upsertMaster(m1)
          _   <- masters.upsertMaster(m2)
          _   <- masters.upsertMaster(m3)
          res <- masters.getMasters().map(_.filter(_.name.startsWith(prefix)))
          _   <- assertIO(res == List(m2, m3, m1))
        } yield ()
    }

    "upsert overwrites existing master with same id" in {
      (rnd: Rnd[IO], masters: Masters[IO]) =>
        for {
          id     <- rnd[MasterId]
          initial = Master(id, "same-id")
          updated = Master(id, "same-id-updated")
          _      <- masters.upsertMaster(initial)
          _      <- masters.upsertMaster(updated)
          res    <- masters.getMaster(id)
          _      <- assertIO(res.contains(updated))
        } yield ()
    }

  }

}

abstract class MasterLocationsTest extends LeaderboardTest {

  "MasterLocations" should {

    "upsert & get" in {
      (rnd: Rnd[IO], masters: Masters[IO], masterLocations: MasterLocations[IO]) =>
        for {
          masterId   <- rnd[MasterId]
          locationId <- rnd[MasterLocationId]
          master      = Master(masterId, s"master-$masterId")
          location    = MasterLocation(locationId, masterId, s"location-$locationId", s"address-$locationId", BigDecimal("52.5200"), BigDecimal("13.4050"))
          _          <- masters.upsertMaster(master)
          _          <- masterLocations.upsertMasterLocation(location)
          res        <- masterLocations.getMasterLocation(location.id)
          _          <- assertIO(res.contains(location))
        } yield ()
    }

    "reject creating a location when master does not exist" in {
      (rnd: Rnd[IO], masterLocations: MasterLocations[IO]) =>
        for {
          masterId   <- rnd[MasterId]
          locationId <- rnd[MasterLocationId]
          result     <- masterLocations
            .upsertMasterLocation(
              MasterLocation(locationId, masterId, "orphan-location", "missing-master-address", BigDecimal("10.1000"), BigDecimal("20.2000"))
            )
            .either
          _ <- assertIO(result.isLeft)
        } yield ()
    }

    "allow creating several locations for one master" in {
      (rnd: Rnd[IO], masters: Masters[IO], masterLocations: MasterLocations[IO]) =>
        for {
          masterId <- rnd[MasterId]
          id1      <- rnd[MasterLocationId]
          id2      <- rnd[MasterLocationId]
          master    = Master(masterId, s"locations-master-$masterId")
          l1        = MasterLocation(id1, masterId, s"location-a-$id1", s"address-a-$id1", BigDecimal("40.7128"), BigDecimal("-74.0060"))
          l2        = MasterLocation(id2, masterId, s"location-b-$id2", s"address-b-$id2", BigDecimal("34.0522"), BigDecimal("-118.2437"))
          _        <- masters.upsertMaster(master)
          _        <- masterLocations.upsertMasterLocation(l1)
          _        <- masterLocations.upsertMasterLocation(l2)
          res      <- masterLocations.getMasterLocationsByMaster(masterId)
          _        <- assertIO(res.toSet == Set(l1, l2))
        } yield ()
    }

    "return only locations of the requested master" in {
      (rnd: Rnd[IO], masters: Masters[IO], masterLocations: MasterLocations[IO]) =>
        for {
          master1Id   <- rnd[MasterId]
          master2Id   <- rnd[MasterId]
          location1Id <- rnd[MasterLocationId]
          location2Id <- rnd[MasterLocationId]
          otherId     <- rnd[MasterLocationId]

          master1   = Master(master1Id, s"master-a-$master1Id")
          master2   = Master(master2Id, s"master-b-$master2Id")
          location1 = MasterLocation(location1Id, master1Id, s"loc-a-$location1Id", s"addr-a-$location1Id", BigDecimal("51.5074"), BigDecimal("-0.1278"))
          location2 = MasterLocation(location2Id, master1Id, s"loc-b-$location2Id", s"addr-b-$location2Id", BigDecimal("48.8566"), BigDecimal("2.3522"))
          other     = MasterLocation(otherId, master2Id, s"loc-c-$otherId", s"addr-c-$otherId", BigDecimal("35.6762"), BigDecimal("139.6503"))

          _   <- masters.upsertMaster(master1)
          _   <- masters.upsertMaster(master2)
          _   <- masterLocations.upsertMasterLocation(location1)
          _   <- masterLocations.upsertMasterLocation(location2)
          _   <- masterLocations.upsertMasterLocation(other)
          res <- masterLocations.getMasterLocationsByMaster(master1Id)

          _ <- assertIO(res.toSet == Set(location1, location2))
        } yield ()
    }

    "return locations sorted by name asc, then id asc" in {
      (rnd: Rnd[IO], masters: Masters[IO], masterLocations: MasterLocations[IO]) =>
        for {
          masterId <- rnd[MasterId]
          master    = Master(masterId, s"master-sort-$masterId")
          id1       = MasterLocationId.fromString("00000000-0000-0000-0000-000000000002")
          id2       = MasterLocationId.fromString("00000000-0000-0000-0000-000000000001")
          id3       = MasterLocationId.fromString("00000000-0000-0000-0000-000000000003")
          prefix   <- rnd[MasterId].map(id => s"master-locations-sort-$id")
          l1        = MasterLocation(id1, masterId, s"$prefix-beta", s"$prefix-address-2", BigDecimal("1.0000"), BigDecimal("2.0000"))
          l2        = MasterLocation(id2, masterId, s"$prefix-alpha", s"$prefix-address-1", BigDecimal("3.0000"), BigDecimal("4.0000"))
          l3        = MasterLocation(id3, masterId, s"$prefix-alpha", s"$prefix-address-3", BigDecimal("5.0000"), BigDecimal("6.0000"))

          _   <- masters.upsertMaster(master)
          _   <- masterLocations.upsertMasterLocation(l1)
          _   <- masterLocations.upsertMasterLocation(l2)
          _   <- masterLocations.upsertMasterLocation(l3)
          res <- masterLocations.getMasterLocationsByMaster(masterId)

          _ <- assertIO(res == List(l2, l3, l1))
        } yield ()
    }

    "upsert overwrites existing location with same id" in {
      (rnd: Rnd[IO], masters: Masters[IO], masterLocations: MasterLocations[IO]) =>
        for {
          masterId   <- rnd[MasterId]
          locationId <- rnd[MasterLocationId]
          master      = Master(masterId, s"overwrite-master-$masterId")
          initial     = MasterLocation(locationId, masterId, "same-id", "address-initial", BigDecimal("11.1100"), BigDecimal("22.2200"))
          updated     = MasterLocation(locationId, masterId, "same-id-updated", "address-updated", BigDecimal("33.3300"), BigDecimal("44.4400"))
          _          <- masters.upsertMaster(master)
          _          <- masterLocations.upsertMasterLocation(initial)
          _          <- masterLocations.upsertMasterLocation(updated)
          res        <- masterLocations.getMasterLocation(locationId)
          _          <- assertIO(res.contains(updated))
        } yield ()
    }

  }

}

abstract class MasterServiceOffersTest extends LeaderboardTest {

  "MasterServiceOffers" should {

    "upsert & get" in {
      (rnd: Rnd[IO], categories: Categories[IO], masters: Masters[IO], services: Services[IO], offers: MasterServiceOffers[IO]) =>
        for {
          categoryId <- rnd[CategoryId]
          masterId   <- rnd[MasterId]
          serviceId  <- rnd[ServiceId]
          offerId    <- rnd[MasterServiceOfferId]
          category    = Category(categoryId, testCategoryCode(categoryId), rootCategoryId, 0, s"offer-category-$categoryId")
          master      = Master(masterId, s"offer-master-$masterId")
          service     = Service(serviceId, testServiceCode(serviceId), categoryId, s"offer-service-$serviceId")
          offer       = MasterServiceOffer(offerId, masterId, serviceId)
          _          <- categories.upsertCategory(category)
          _          <- masters.upsertMaster(master)
          _          <- services.upsertService(service)
          _          <- offers.upsertMasterServiceOffer(offer)
          res        <- offers.getMasterServiceOffer(offer.id)
          _          <- assertIO(res.contains(offer))
        } yield ()
    }

    "reject creating an offer when master does not exist" in {
      (rnd: Rnd[IO], categories: Categories[IO], services: Services[IO], offers: MasterServiceOffers[IO]) =>
        for {
          categoryId <- rnd[CategoryId]
          masterId   <- rnd[MasterId]
          serviceId  <- rnd[ServiceId]
          offerId    <- rnd[MasterServiceOfferId]
          category    = Category(categoryId, testCategoryCode(categoryId), rootCategoryId, 0, s"missing-master-category-$categoryId")
          service     = Service(serviceId, testServiceCode(serviceId), categoryId, s"missing-master-service-$serviceId")
          _          <- categories.upsertCategory(category)
          _          <- services.upsertService(service)
          result     <- offers.upsertMasterServiceOffer(MasterServiceOffer(offerId, masterId, serviceId)).either
          _          <- assertIO(result.isLeft)
        } yield ()
    }

    "reject creating an offer when service does not exist" in {
      (rnd: Rnd[IO], masters: Masters[IO], offers: MasterServiceOffers[IO]) =>
        for {
          masterId  <- rnd[MasterId]
          serviceId <- rnd[ServiceId]
          offerId   <- rnd[MasterServiceOfferId]
          master     = Master(masterId, s"missing-service-master-$masterId")
          _         <- masters.upsertMaster(master)
          result    <- offers.upsertMasterServiceOffer(MasterServiceOffer(offerId, masterId, serviceId)).either
          _         <- assertIO(result.isLeft)
        } yield ()
    }

    "allow creating several offers for one master" in {
      (rnd: Rnd[IO], categories: Categories[IO], masters: Masters[IO], services: Services[IO], offers: MasterServiceOffers[IO]) =>
        for {
          categoryId <- rnd[CategoryId]
          masterId   <- rnd[MasterId]
          service1Id <- rnd[ServiceId]
          service2Id <- rnd[ServiceId]
          offer1Id   <- rnd[MasterServiceOfferId]
          offer2Id   <- rnd[MasterServiceOfferId]
          category    = Category(categoryId, testCategoryCode(categoryId), rootCategoryId, 0, s"offers-master-category-$categoryId")
          master      = Master(masterId, s"offers-master-$masterId")
          service1    = Service(service1Id, testServiceCode(service1Id), categoryId, s"offers-service-a-$service1Id")
          service2    = Service(service2Id, testServiceCode(service2Id), categoryId, s"offers-service-b-$service2Id")
          offer1      = MasterServiceOffer(offer1Id, masterId, service1Id)
          offer2      = MasterServiceOffer(offer2Id, masterId, service2Id)
          _          <- categories.upsertCategory(category)
          _          <- masters.upsertMaster(master)
          _          <- services.upsertService(service1)
          _          <- services.upsertService(service2)
          _          <- offers.upsertMasterServiceOffer(offer1)
          _          <- offers.upsertMasterServiceOffer(offer2)
          res        <- offers.getMasterServiceOffersByMaster(masterId)
          _          <- assertIO(res.toSet == Set(offer1, offer2))
        } yield ()
    }

    "allow creating offers of different masters for one service" in {
      (rnd: Rnd[IO], categories: Categories[IO], masters: Masters[IO], services: Services[IO], offers: MasterServiceOffers[IO]) =>
        for {
          categoryId <- rnd[CategoryId]
          master1Id  <- rnd[MasterId]
          master2Id  <- rnd[MasterId]
          serviceId  <- rnd[ServiceId]
          offer1Id   <- rnd[MasterServiceOfferId]
          offer2Id   <- rnd[MasterServiceOfferId]
          category    = Category(categoryId, testCategoryCode(categoryId), rootCategoryId, 0, s"offers-service-category-$categoryId")
          master1     = Master(master1Id, s"offers-master-a-$master1Id")
          master2     = Master(master2Id, s"offers-master-b-$master2Id")
          service     = Service(serviceId, testServiceCode(serviceId), categoryId, s"offers-shared-service-$serviceId")
          offer1      = MasterServiceOffer(offer1Id, master1Id, serviceId)
          offer2      = MasterServiceOffer(offer2Id, master2Id, serviceId)
          _          <- categories.upsertCategory(category)
          _          <- masters.upsertMaster(master1)
          _          <- masters.upsertMaster(master2)
          _          <- services.upsertService(service)
          _          <- offers.upsertMasterServiceOffer(offer1)
          _          <- offers.upsertMasterServiceOffer(offer2)
          res        <- offers.getMasterServiceOffersByService(serviceId)
          _          <- assertIO(res.toSet == Set(offer1, offer2))
        } yield ()
    }

    "return only offers of the requested master" in {
      (rnd: Rnd[IO], categories: Categories[IO], masters: Masters[IO], services: Services[IO], offers: MasterServiceOffers[IO]) =>
        for {
          categoryId <- rnd[CategoryId]
          master1Id  <- rnd[MasterId]
          master2Id  <- rnd[MasterId]
          service1Id <- rnd[ServiceId]
          service2Id <- rnd[ServiceId]
          offer1Id   <- rnd[MasterServiceOfferId]
          offer2Id   <- rnd[MasterServiceOfferId]
          otherId    <- rnd[MasterServiceOfferId]
          category    = Category(categoryId, testCategoryCode(categoryId), rootCategoryId, 0, s"offers-by-master-category-$categoryId")
          master1     = Master(master1Id, s"offers-master-filter-a-$master1Id")
          master2     = Master(master2Id, s"offers-master-filter-b-$master2Id")
          service1    = Service(service1Id, testServiceCode(service1Id), categoryId, s"offers-master-filter-service-a-$service1Id")
          service2    = Service(service2Id, testServiceCode(service2Id), categoryId, s"offers-master-filter-service-b-$service2Id")
          offer1      = MasterServiceOffer(offer1Id, master1Id, service1Id)
          offer2      = MasterServiceOffer(offer2Id, master1Id, service2Id)
          other       = MasterServiceOffer(otherId, master2Id, service1Id)
          _          <- categories.upsertCategory(category)
          _          <- masters.upsertMaster(master1)
          _          <- masters.upsertMaster(master2)
          _          <- services.upsertService(service1)
          _          <- services.upsertService(service2)
          _          <- offers.upsertMasterServiceOffer(offer1)
          _          <- offers.upsertMasterServiceOffer(offer2)
          _          <- offers.upsertMasterServiceOffer(other)
          res        <- offers.getMasterServiceOffersByMaster(master1Id)
          _          <- assertIO(res.toSet == Set(offer1, offer2))
        } yield ()
    }

    "return only offers of the requested service" in {
      (rnd: Rnd[IO], categories: Categories[IO], masters: Masters[IO], services: Services[IO], offers: MasterServiceOffers[IO]) =>
        for {
          categoryId <- rnd[CategoryId]
          master1Id  <- rnd[MasterId]
          master2Id  <- rnd[MasterId]
          service1Id <- rnd[ServiceId]
          service2Id <- rnd[ServiceId]
          offer1Id   <- rnd[MasterServiceOfferId]
          offer2Id   <- rnd[MasterServiceOfferId]
          otherId    <- rnd[MasterServiceOfferId]
          category    = Category(categoryId, testCategoryCode(categoryId), rootCategoryId, 0, s"offers-by-service-category-$categoryId")
          master1     = Master(master1Id, s"offers-service-filter-a-$master1Id")
          master2     = Master(master2Id, s"offers-service-filter-b-$master2Id")
          service1    = Service(service1Id, testServiceCode(service1Id), categoryId, s"offers-service-filter-service-a-$service1Id")
          service2    = Service(service2Id, testServiceCode(service2Id), categoryId, s"offers-service-filter-service-b-$service2Id")
          offer1      = MasterServiceOffer(offer1Id, master1Id, service1Id)
          offer2      = MasterServiceOffer(offer2Id, master2Id, service1Id)
          other       = MasterServiceOffer(otherId, master1Id, service2Id)
          _          <- categories.upsertCategory(category)
          _          <- masters.upsertMaster(master1)
          _          <- masters.upsertMaster(master2)
          _          <- services.upsertService(service1)
          _          <- services.upsertService(service2)
          _          <- offers.upsertMasterServiceOffer(offer1)
          _          <- offers.upsertMasterServiceOffer(offer2)
          _          <- offers.upsertMasterServiceOffer(other)
          res        <- offers.getMasterServiceOffersByService(service1Id)
          _          <- assertIO(res.toSet == Set(offer1, offer2))
        } yield ()
    }

    "return offers by master sorted by id asc" in {
      (rnd: Rnd[IO], categories: Categories[IO], masters: Masters[IO], services: Services[IO], offers: MasterServiceOffers[IO]) =>
        for {
          categoryId <- rnd[CategoryId]
          masterId   <- rnd[MasterId]
          service1Id <- rnd[ServiceId]
          service2Id <- rnd[ServiceId]
          service3Id <- rnd[ServiceId]
          category    = Category(categoryId, testCategoryCode(categoryId), rootCategoryId, 0, s"offers-sort-master-category-$categoryId")
          master      = Master(masterId, s"offers-sort-master-$masterId")
          service1    = Service(service1Id, testServiceCode(service1Id), categoryId, s"offers-sort-master-service-a-$service1Id")
          service2    = Service(service2Id, testServiceCode(service2Id), categoryId, s"offers-sort-master-service-b-$service2Id")
          service3    = Service(service3Id, testServiceCode(service3Id), categoryId, s"offers-sort-master-service-c-$service3Id")
          id1         = MasterServiceOfferId.fromString("10000000-0000-0000-0000-000000000002")
          id2         = MasterServiceOfferId.fromString("10000000-0000-0000-0000-000000000001")
          id3         = MasterServiceOfferId.fromString("10000000-0000-0000-0000-000000000003")
          offer1      = MasterServiceOffer(id1, masterId, service1Id)
          offer2      = MasterServiceOffer(id2, masterId, service2Id)
          offer3      = MasterServiceOffer(id3, masterId, service3Id)
          _          <- categories.upsertCategory(category)
          _          <- masters.upsertMaster(master)
          _          <- services.upsertService(service1)
          _          <- services.upsertService(service2)
          _          <- services.upsertService(service3)
          _          <- offers.upsertMasterServiceOffer(offer1)
          _          <- offers.upsertMasterServiceOffer(offer2)
          _          <- offers.upsertMasterServiceOffer(offer3)
          res        <- offers.getMasterServiceOffersByMaster(masterId)
          _          <- assertIO(res == List(offer2, offer1, offer3))
        } yield ()
    }

    "return offers by service sorted by id asc" in {
      (rnd: Rnd[IO], categories: Categories[IO], masters: Masters[IO], services: Services[IO], offers: MasterServiceOffers[IO]) =>
        for {
          categoryId <- rnd[CategoryId]
          master1Id  <- rnd[MasterId]
          master2Id  <- rnd[MasterId]
          master3Id  <- rnd[MasterId]
          serviceId  <- rnd[ServiceId]
          category    = Category(categoryId, testCategoryCode(categoryId), rootCategoryId, 0, s"offers-sort-service-category-$categoryId")
          master1     = Master(master1Id, s"offers-sort-service-master-a-$master1Id")
          master2     = Master(master2Id, s"offers-sort-service-master-b-$master2Id")
          master3     = Master(master3Id, s"offers-sort-service-master-c-$master3Id")
          service     = Service(serviceId, testServiceCode(serviceId), categoryId, s"offers-sort-service-$serviceId")
          id1         = MasterServiceOfferId.fromString("20000000-0000-0000-0000-000000000002")
          id2         = MasterServiceOfferId.fromString("20000000-0000-0000-0000-000000000001")
          id3         = MasterServiceOfferId.fromString("20000000-0000-0000-0000-000000000003")
          offer1      = MasterServiceOffer(id1, master1Id, serviceId)
          offer2      = MasterServiceOffer(id2, master2Id, serviceId)
          offer3      = MasterServiceOffer(id3, master3Id, serviceId)
          _          <- categories.upsertCategory(category)
          _          <- masters.upsertMaster(master1)
          _          <- masters.upsertMaster(master2)
          _          <- masters.upsertMaster(master3)
          _          <- services.upsertService(service)
          _          <- offers.upsertMasterServiceOffer(offer1)
          _          <- offers.upsertMasterServiceOffer(offer2)
          _          <- offers.upsertMasterServiceOffer(offer3)
          res        <- offers.getMasterServiceOffersByService(serviceId)
          _          <- assertIO(res == List(offer2, offer1, offer3))
        } yield ()
    }

    "upsert overwrites existing offer with same id" in {
      (rnd: Rnd[IO], categories: Categories[IO], masters: Masters[IO], services: Services[IO], offers: MasterServiceOffers[IO]) =>
        for {
          categoryId <- rnd[CategoryId]
          master1Id  <- rnd[MasterId]
          master2Id  <- rnd[MasterId]
          service1Id <- rnd[ServiceId]
          service2Id <- rnd[ServiceId]
          offerId    <- rnd[MasterServiceOfferId]
          category    = Category(categoryId, testCategoryCode(categoryId), rootCategoryId, 0, s"offers-overwrite-category-$categoryId")
          master1     = Master(master1Id, s"offers-overwrite-master-a-$master1Id")
          master2     = Master(master2Id, s"offers-overwrite-master-b-$master2Id")
          service1    = Service(service1Id, testServiceCode(service1Id), categoryId, s"offers-overwrite-service-a-$service1Id")
          service2    = Service(service2Id, testServiceCode(service2Id), categoryId, s"offers-overwrite-service-b-$service2Id")
          initial     = MasterServiceOffer(offerId, master1Id, service1Id)
          updated     = MasterServiceOffer(offerId, master2Id, service2Id)
          _          <- categories.upsertCategory(category)
          _          <- masters.upsertMaster(master1)
          _          <- masters.upsertMaster(master2)
          _          <- services.upsertService(service1)
          _          <- services.upsertService(service2)
          _          <- offers.upsertMasterServiceOffer(initial)
          _          <- offers.upsertMasterServiceOffer(updated)
          res        <- offers.getMasterServiceOffer(offerId)
          _          <- assertIO(res.contains(updated))
        } yield ()
    }

  }

}

class CategoriesTestDummy extends CategoriesTest with DummyTest
class MastersTestDummy extends MastersTest with DummyTest
class MasterLocationsTestDummy extends MasterLocationsTest with DummyTest
class MasterServiceOffersTestDummy extends MasterServiceOffersTest with DummyTest
class CategoriesTestPostgres extends CategoriesTest with ProdTest
class MastersTestPostgres extends MastersTest with ProdTest
class MasterLocationsTestPostgres extends MasterLocationsTest with ProdTest
class MasterServiceOffersTestPostgres extends MasterServiceOffersTest with ProdTest
class ServicesTestDummy extends ServicesTest with DummyTest
class ServicesTestPostgres extends ServicesTest with ProdTest
