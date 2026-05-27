package leaderboard

import distage.{DIKey, ModuleDef, Scene}
import io.circe.Json
import io.circe.syntax.*
import doobie.implicits.*
import doobie.postgres.implicits.*
import izumi.distage.model.definition.Activation
import izumi.distage.model.definition.StandardAxis.Repo
import izumi.distage.plugins.PluginConfig
import izumi.distage.testkit.scalatest.{AssertZIO, SpecZIO}
import leaderboard.model.Category.{CategoryId, rootCategoryId}
import leaderboard.model.*
import leaderboard.repo.{Categories, Ladder, MasterLocations, MasterServiceOfferVariants, MasterServiceOffers, Masters, Profiles, ServiceVariantSchemas, Services}
import leaderboard.services.Ranks
import leaderboard.sql.SQL
import leaderboard.zioenv.*
import zio.{IO, ZIO}
import leaderboard.model.AttributeMap

// AI-NOTE: For distage testkit memoizationRoots, Activation, and BIO patterns used here, see docs/LOCAL_LLM_IZUMI_DISTAGE_BIO_REFERENCE.md
abstract class LeaderboardTest extends SpecZIO with AssertZIO {
  override def config = super.config.copy(
    pluginConfig    = PluginConfig.cached(packagesEnabled = Seq("leaderboard.plugins")),
    moduleOverrides = super.config.moduleOverrides ++ new ModuleDef {
      make[Rnd[IO]].from[Rnd.Impl[IO]]
    },
    // For testing, set up a docker container with postgres,
    // instead of trying to connect to an external database
    activation = Activation(Scene -> Scene.Managed),
    // Instantiate repos only once per test-run and
    // share them and all their dependencies across all tests.
    // this includes the Postgres Docker container above and table DDLs
    memoizationRoots = Set(
      DIKey[Ladder[IO]],
      DIKey[Profiles[IO]],
      DIKey[Categories[IO]],
      DIKey[Masters[IO]],
      DIKey[MasterLocations[IO]],
      DIKey[MasterServiceOffers[IO]],
      DIKey[ServiceVariantSchemas[IO]],
      DIKey[MasterServiceOfferVariants[IO]],
      DIKey[Services[IO]],
    ),
  )
}

trait DummyTest extends LeaderboardTest {
  override def config = super.config.copy(
    activation = super.config.activation ++ Activation(Repo -> Repo.Dummy)
  )
}

trait ProdTest extends LeaderboardTest {
  override def config = super.config.copy(
    activation = super.config.activation ++ Activation(Repo -> Repo.Prod)
  )
}

class LadderTestDummy extends LadderTest with DummyTest
class ProfilesTestDummy extends ProfilesTest with DummyTest
class RanksTestDummy extends RanksTest with DummyTest
class CategoriesTestDummy extends CategoriesTest with DummyTest
class MastersTestDummy extends MastersTest with DummyTest
class MasterLocationsTestDummy extends MasterLocationsTest with DummyTest
class MasterServiceOffersTestDummy extends MasterServiceOffersTest with DummyTest
class ServiceVariantSchemasTestDummy extends ServiceVariantSchemasTest with DummyTest
class ServiceVariantSchemasStorageValidationTestPostgres extends ServiceVariantSchemasStorageValidationTest with ProdTest
class MasterServiceOfferVariantsTestDummy extends MasterServiceOfferVariantsTest with DummyTest
class MasterServiceOfferVariantsStorageValidationTestPostgres extends MasterServiceOfferVariantsStorageValidationTest with ProdTest
class ServicesTestDummy extends ServicesTest with DummyTest

class LadderTestPostgres extends LadderTest with ProdTest
class ProfilesTestPostgres extends ProfilesTest with ProdTest
class RanksTestPostgres extends RanksTest with ProdTest
class CategoriesTestPostgres extends CategoriesTest with ProdTest
class MastersTestPostgres extends MastersTest with ProdTest
class MasterLocationsTestPostgres extends MasterLocationsTest with ProdTest
class MasterServiceOffersTestPostgres extends MasterServiceOffersTest with ProdTest
class ServiceVariantSchemasTestPostgres extends ServiceVariantSchemasTest with ProdTest
class MasterServiceOfferVariantsTestPostgres extends MasterServiceOfferVariantsTest with ProdTest
class ServicesTestPostgres extends ServicesTest with ProdTest

abstract class LadderTest extends LeaderboardTest {

  "Ladder" should {

    /** this test gets dependencies injected through function arguments */
    "submit & get" in {
      (rnd: Rnd[IO], ladder: Ladder[IO]) =>
        for {
          user  <- rnd[UserId]
          score <- rnd[Score]
          _     <- ladder.submitScore(user, score)
          res   <- ladder.getScores.map(_.find(_._1 == user).map(_._2))
          _     <- assertIO(res contains score)
        } yield ()
    }

    /** this test get dependencies injected via ZIO Env: */
    "assign a higher position in the list to a higher score" in {
      for {
        user1  <- rnd[UserId]
        score1 <- rnd[Score]
        user2  <- rnd[UserId]
        score2 <- rnd[Score]

        _      <- ladder.submitScore(user1, score1)
        _      <- ladder.submitScore(user2, score2)
        scores <- ladder.getScores

        user1Rank = scores.indexWhere(_._1 == user1)
        user2Rank = scores.indexWhere(_._1 == user2)

        _ <-
          if (score1 > score2) {
            assertIO(user1Rank < user2Rank)
          } else if (score2 > score1) {
            assertIO(user2Rank < user1Rank)
          } else ZIO.unit
      } yield ()
    }

  }

}

abstract class ProfilesTest extends LeaderboardTest {

  "Profiles" should {

    /** that's what the ZIO signature looks like for ZIO Env injection: */
    "set & get" in {
      val zioValue: ZIO[Profiles[IO] & Rnd[IO], QueryFailure, Unit] = for {
        user   <- rnd[UserId]
        name   <- rnd[String]
        desc   <- rnd[String]
        profile = UserProfile(name, desc)
        _      <- profiles.setProfile(user, profile)
        res    <- profiles.getProfile(user)
        _      <- assertIO(res contains profile)
      } yield ()
      zioValue
    }

  }

}

abstract class RanksTest extends LeaderboardTest {

  "Ranks" should {

    /** you can use Argument injection and ZIO Env injection at the same time: */
    "return 0 rank for a user with no score" in {
      (ranks: Ranks[IO]) =>
        for {
          user   <- rnd[UserId]
          name   <- rnd[String]
          desc   <- rnd[String]
          profile = UserProfile(name, desc)
          _      <- profiles.setProfile(user, profile)
          res1   <- ranks.getRank(user)
          _      <- assertIO(res1.contains(RankedProfile(name, desc, 0, 0)))
        } yield ()
    }

    "return None for a user with no profile" in {
      for {
        user  <- rnd[UserId]
        score <- rnd[Score]
        _     <- ladder.submitScore(user, score)
        res1  <- ranks.getRank(user)
        _     <- assertIO(res1.isEmpty)
      } yield ()
    }

    "assign a higher rank to a user with more score" in {
      for {
        user1  <- rnd[UserId]
        name1  <- rnd[String]
        desc1  <- rnd[String]
        score1 <- rnd[Score]

        user2  <- rnd[UserId]
        name2  <- rnd[String]
        desc2  <- rnd[String]
        score2 <- rnd[Score]

        _ <- profiles.setProfile(user1, UserProfile(name1, desc1))
        _ <- ladder.submitScore(user1, score1)

        _ <- profiles.setProfile(user2, UserProfile(name2, desc2))
        _ <- ladder.submitScore(user2, score2)

        user1Rank <- ranks.getRank(user1).map(_.get.rank)
        user2Rank <- ranks.getRank(user2).map(_.get.rank)

        _ <-
          if (score1 > score2) {
            assertIO(user1Rank < user2Rank)
          } else if (score2 > score1) {
            assertIO(user2Rank < user1Rank)
          } else ZIO.unit
      } yield ()
    }

  }

}

abstract class CategoriesTest extends LeaderboardTest {

  "Categories" should {

    "upsert & get for an ordinary category" in {
      (rnd: Rnd[IO], categories: Categories[IO]) =>
        for {
          parentId <- rnd[CategoryId]
          childId  <- rnd[CategoryId]
          parent    = Category(parentId, rootCategoryId, 0, s"parent-$parentId")
          child     = Category(childId, parentId, 1, s"child-$childId")
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
          category = Category(id, rootCategoryId, 0, s"top-$id")
          _       <- categories.upsertCategory(category)
          res     <- categories.getCategory(category.id)
          _       <- assertIO(res.contains(category))
        } yield ()
    }

    "reject creating a category with id == rootCategoryId" in {
      (rnd: Rnd[IO], categories: Categories[IO]) =>
        for {
          parentId <- rnd[CategoryId]
          result   <- categories.upsertCategory(Category(rootCategoryId, parentId, 1, "illegal-root")).either
          _        <- assertIO(result.isLeft)
        } yield ()
    }

    "reject creating a category with a missing non-root parent" in {
      (rnd: Rnd[IO], categories: Categories[IO]) =>
        for {
          id       <- rnd[CategoryId]
          parentId <- rnd[CategoryId]
          result   <- categories.upsertCategory(Category(id, parentId, 1, s"orphan-$id")).either
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

          parent1 = Category(parent1Id, rootCategoryId, 0, s"parent-a-$parent1Id")
          parent2 = Category(parent2Id, rootCategoryId, 0, s"parent-b-$parent2Id")
          child1  = Category(child1Id, parent1Id, 1, s"child-a-$child1Id")
          child2  = Category(child2Id, parent1Id, 1, s"child-b-$child2Id")
          other   = Category(otherId, parent2Id, 1, s"child-c-$otherId")

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

          parent = Category(parentId, rootCategoryId, 0, s"parent-sort-$parentId")
          c1     = Category(id1, parentId, 1, "beta")
          c2     = Category(id2, parentId, 1, "alpha")
          c3     = Category(id3, parentId, 2, "aardvark")

          _   <- categories.upsertCategory(parent)
          _   <- categories.upsertCategory(c1)
          _   <- categories.upsertCategory(c2)
          _   <- categories.upsertCategory(c3)
          res <- categories.getChildren(parentId)

          _ <- assertIO(res == List(c2, c1, c3))
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
          category    = Category(categoryId, rootCategoryId, 0, s"service-parent-$categoryId")
          service     = Service(serviceId, categoryId, s"service-$serviceId")
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
          result    <- services.upsertService(Service(serviceId, rootCategoryId, "illegal-root-service")).either
          _         <- assertIO(result.isLeft)
        } yield ()
    }

    "reject creating a service when category does not exist" in {
      (rnd: Rnd[IO], services: Services[IO]) =>
        for {
          serviceId  <- rnd[ServiceId]
          categoryId <- rnd[CategoryId]
          result     <- services.upsertService(Service(serviceId, categoryId, s"orphan-service-$serviceId")).either
          _          <- assertIO(result.isLeft)
        } yield ()
    }

    "allow creating a service for an existing category" in {
      (rnd: Rnd[IO], categories: Categories[IO], services: Services[IO]) =>
        for {
          categoryId <- rnd[CategoryId]
          serviceId  <- rnd[ServiceId]
          category    = Category(categoryId, rootCategoryId, 0, s"existing-category-$categoryId")
          service     = Service(serviceId, categoryId, s"existing-service-$serviceId")
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

          category1 = Category(category1Id, rootCategoryId, 0, s"services-parent-a-$category1Id")
          category2 = Category(category2Id, rootCategoryId, 0, s"services-parent-b-$category2Id")
          service1  = Service(service1Id, category1Id, s"service-a-$service1Id")
          service2  = Service(service2Id, category1Id, s"service-b-$service2Id")
          other     = Service(otherId, category2Id, s"service-c-$otherId")

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

          category = Category(categoryId, rootCategoryId, 0, s"services-sort-$categoryId")
          s1       = Service(id1, categoryId, "gamma")
          s2       = Service(id2, categoryId, "alpha")
          s3       = Service(id3, categoryId, "beta")

          _   <- categories.upsertCategory(category)
          _   <- services.upsertService(s1)
          _   <- services.upsertService(s2)
          _   <- services.upsertService(s3)
          res <- services.getServicesByCategory(categoryId)

          _ <- assertIO(res == List(s2, s3, s1))
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
        val id1    = java.util.UUID.fromString("00000000-0000-0000-0000-000000000002")
        val id2    = java.util.UUID.fromString("00000000-0000-0000-0000-000000000001")
        val id3    = java.util.UUID.fromString("00000000-0000-0000-0000-000000000003")
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
          id1       = java.util.UUID.fromString("00000000-0000-0000-0000-000000000002")
          id2       = java.util.UUID.fromString("00000000-0000-0000-0000-000000000001")
          id3       = java.util.UUID.fromString("00000000-0000-0000-0000-000000000003")
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
          category    = Category(categoryId, rootCategoryId, 0, s"offer-category-$categoryId")
          master      = Master(masterId, s"offer-master-$masterId")
          service     = Service(serviceId, categoryId, s"offer-service-$serviceId")
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
          category    = Category(categoryId, rootCategoryId, 0, s"missing-master-category-$categoryId")
          service     = Service(serviceId, categoryId, s"missing-master-service-$serviceId")
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
          category    = Category(categoryId, rootCategoryId, 0, s"offers-master-category-$categoryId")
          master      = Master(masterId, s"offers-master-$masterId")
          service1    = Service(service1Id, categoryId, s"offers-service-a-$service1Id")
          service2    = Service(service2Id, categoryId, s"offers-service-b-$service2Id")
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
          category    = Category(categoryId, rootCategoryId, 0, s"offers-service-category-$categoryId")
          master1     = Master(master1Id, s"offers-master-a-$master1Id")
          master2     = Master(master2Id, s"offers-master-b-$master2Id")
          service     = Service(serviceId, categoryId, s"offers-shared-service-$serviceId")
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
          category    = Category(categoryId, rootCategoryId, 0, s"offers-by-master-category-$categoryId")
          master1     = Master(master1Id, s"offers-master-filter-a-$master1Id")
          master2     = Master(master2Id, s"offers-master-filter-b-$master2Id")
          service1    = Service(service1Id, categoryId, s"offers-master-filter-service-a-$service1Id")
          service2    = Service(service2Id, categoryId, s"offers-master-filter-service-b-$service2Id")
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
          category    = Category(categoryId, rootCategoryId, 0, s"offers-by-service-category-$categoryId")
          master1     = Master(master1Id, s"offers-service-filter-a-$master1Id")
          master2     = Master(master2Id, s"offers-service-filter-b-$master2Id")
          service1    = Service(service1Id, categoryId, s"offers-service-filter-service-a-$service1Id")
          service2    = Service(service2Id, categoryId, s"offers-service-filter-service-b-$service2Id")
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
          category    = Category(categoryId, rootCategoryId, 0, s"offers-sort-master-category-$categoryId")
          master      = Master(masterId, s"offers-sort-master-$masterId")
          service1    = Service(service1Id, categoryId, s"offers-sort-master-service-a-$service1Id")
          service2    = Service(service2Id, categoryId, s"offers-sort-master-service-b-$service2Id")
          service3    = Service(service3Id, categoryId, s"offers-sort-master-service-c-$service3Id")
          id1         = java.util.UUID.fromString("10000000-0000-0000-0000-000000000002")
          id2         = java.util.UUID.fromString("10000000-0000-0000-0000-000000000001")
          id3         = java.util.UUID.fromString("10000000-0000-0000-0000-000000000003")
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
          category    = Category(categoryId, rootCategoryId, 0, s"offers-sort-service-category-$categoryId")
          master1     = Master(master1Id, s"offers-sort-service-master-a-$master1Id")
          master2     = Master(master2Id, s"offers-sort-service-master-b-$master2Id")
          master3     = Master(master3Id, s"offers-sort-service-master-c-$master3Id")
          service     = Service(serviceId, categoryId, s"offers-sort-service-$serviceId")
          id1         = java.util.UUID.fromString("20000000-0000-0000-0000-000000000002")
          id2         = java.util.UUID.fromString("20000000-0000-0000-0000-000000000001")
          id3         = java.util.UUID.fromString("20000000-0000-0000-0000-000000000003")
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
          category    = Category(categoryId, rootCategoryId, 0, s"offers-overwrite-category-$categoryId")
          master1     = Master(master1Id, s"offers-overwrite-master-a-$master1Id")
          master2     = Master(master2Id, s"offers-overwrite-master-b-$master2Id")
          service1    = Service(service1Id, categoryId, s"offers-overwrite-service-a-$service1Id")
          service2    = Service(service2Id, categoryId, s"offers-overwrite-service-b-$service2Id")
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

abstract class ServiceVariantSchemasTest extends LeaderboardTest {
  private def makeSchema(serviceId: ServiceId, items: ServiceVariantSchemaItem*): ServiceVariantSchema =
    ServiceVariantSchema.fromItems(serviceId, items)

  "ServiceVariantSchemas" should {

    "upsert & get" in {
      (rnd: Rnd[IO], categories: Categories[IO], services: Services[IO], serviceVariantSchemas: ServiceVariantSchemas[IO]) =>
        for {
          categoryId <- rnd[CategoryId]
          serviceId  <- rnd[ServiceId]
          category    = Category(categoryId, rootCategoryId, 0, s"schema-category-$categoryId")
          service     = Service(serviceId, categoryId, s"schema-service-$serviceId")
          schema      = makeSchema(
            serviceId,
            ServiceVariantSchemaItem(AttributeDefinition.MaterialsSurcharge, false),
            ServiceVariantSchemaItem(AttributeDefinition.SessionCount, true),
          )
          _   <- categories.upsertCategory(category)
          _   <- services.upsertService(service)
          _   <- serviceVariantSchemas.upsertServiceVariantSchema(schema)
          res <- serviceVariantSchemas.getServiceVariantSchema(serviceId)
          _   <- assertIO(
            res == ServiceVariantSchema.fromItems(
              serviceId,
              List(
                ServiceVariantSchemaItem(AttributeDefinition.MaterialsSurcharge, false),
                ServiceVariantSchemaItem(AttributeDefinition.SessionCount, true),
              ),
            )
          )
        } yield ()
    }

    "reject schema for missing service" in {
      (rnd: Rnd[IO], serviceVariantSchemas: ServiceVariantSchemas[IO]) =>
        for {
          serviceId <- rnd[ServiceId]
          result    <- serviceVariantSchemas
            .upsertServiceVariantSchema(
              makeSchema(
                serviceId,
                ServiceVariantSchemaItem(AttributeDefinition.SessionCount, true),
              )
            )
            .either
          _ <- assertIO(result.isLeft)
        } yield ()
    }

  }

}

abstract class ServiceVariantSchemasStorageValidationTest extends LeaderboardTest {
  "ServiceVariantSchemas" should {
    "reject unknown attribute code from storage" in {
      (rnd: Rnd[IO], categories: Categories[IO], services: Services[IO], serviceVariantSchemas: ServiceVariantSchemas[IO], db: SQL[IO]) =>
        for {
          categoryId <- rnd[CategoryId]
          serviceId  <- rnd[ServiceId]
          category    = Category(categoryId, rootCategoryId, 0, s"schema-storage-category-$categoryId")
          service     = Service(serviceId, categoryId, s"schema-storage-service-$serviceId")
          _          <- categories.upsertCategory(category)
          _          <- services.upsertService(service)
          _          <- db.execute("insert-invalid-service-variant-schema-item") {
            sql"""insert into service_variant_schema_items (
                 |  service_id,
                 |  attribute_code,
                 |  required
                 |)
                 |values (
                 |  $serviceId,
                 |  ${"unknown_attribute_code"},
                 |  false
                 |)
                 |""".stripMargin.update.run
          }
          result <- serviceVariantSchemas.getServiceVariantSchema(serviceId).either
          _      <- assertIO(result.isLeft)
        } yield ()
    }
  }
}

abstract class MasterServiceOfferVariantsTest extends LeaderboardTest {
  private def enumAttributeMap(
    entries: (EnumAttributeDefinition[?], CodedEnumValue)*
  ): AttributeMap[CodedEnumValue] =
    entries.foldLeft(AttributeMap.Impl[CodedEnumValue, AttributeDefinition[CodedEnumValue]](Map.empty)) {
      case (acc, (definition, value)) =>
        acc.updated(definition.asInstanceOf[AttributeDefinition[CodedEnumValue]], value)
    }

  private def makeVariant(
    id: MasterServiceOfferVariantId,
    masterServiceOfferId: MasterServiceOfferId,
    masterLocationId: MasterLocationId,
    priceFrom: BigDecimal,
    priceTo: BigDecimal,
    durationMin: Int,
    intAttributes: AttributeMap[Int]               = AttributeMap.empty,
    bigDecimalAttributes: AttributeMap[BigDecimal] = AttributeMap.empty,
    enumAttributes: AttributeMap[CodedEnumValue]   = AttributeMap.empty,
  ): IO[QueryFailure, MasterServiceOfferVariant] =
    MasterServiceOfferVariant
      .make(
        id,
        masterServiceOfferId,
        masterLocationId,
        priceFrom,
        priceTo,
        durationMin,
        MasterServiceOfferVariantAttributes(intAttributes, bigDecimalAttributes, enumAttributes),
      ) match {
      case Right(value) =>
        ZIO.succeed(value)
      case Left(error) =>
        ZIO.fail(QueryFailure.operation("make-master-service-offer-variant", error.message))
    }

  private def makeSchema(serviceId: ServiceId, items: ServiceVariantSchemaItem*): ServiceVariantSchema =
    ServiceVariantSchema.fromItems(serviceId, items)

  "MasterServiceOfferVariants" should {

    "upsert & get" in {
      (rnd: Rnd[IO],
        categories: Categories[IO],
        masters: Masters[IO],
        services: Services[IO],
        masterLocations: MasterLocations[IO],
        offers: MasterServiceOffers[IO],
        variants: MasterServiceOfferVariants[IO],
      ) =>
        for {
          categoryId <- rnd[CategoryId]
          masterId   <- rnd[MasterId]
          serviceId  <- rnd[ServiceId]
          offerId    <- rnd[MasterServiceOfferId]
          locationId <- rnd[MasterLocationId]
          variantId  <- rnd[MasterServiceOfferVariantId]
          category    = Category(categoryId, rootCategoryId, 0, s"offer-variant-category-$categoryId")
          master      = Master(masterId, s"offer-variant-master-$masterId")
          service     = Service(serviceId, categoryId, s"offer-variant-service-$serviceId")
          offer       = MasterServiceOffer(offerId, masterId, serviceId)
          location    = MasterLocation(
            locationId,
            masterId,
            s"offer-variant-$locationId",
            s"offer-variant-address-$locationId",
            BigDecimal("12.3400"),
            BigDecimal("56.7800"),
          )
          variant <- makeVariant(variantId, offerId, locationId, BigDecimal("30.0000"), BigDecimal("45.0000"), 60)
          _       <- categories.upsertCategory(category)
          _       <- masters.upsertMaster(master)
          _       <- services.upsertService(service)
          _       <- offers.upsertMasterServiceOffer(offer)
          _       <- masterLocations.upsertMasterLocation(location)
          _       <- variants.upsertMasterServiceOfferVariant(variant)
          res     <- variants.getMasterServiceOfferVariant(variant.id)
          _       <- assertIO(res.contains(variant))
        } yield ()
    }

    "upsert & get with required and optional additional attributes allowed by service schema" in {
      (rnd: Rnd[IO],
        categories: Categories[IO],
        masters: Masters[IO],
        services: Services[IO],
        serviceVariantSchemas: ServiceVariantSchemas[IO],
        masterLocations: MasterLocations[IO],
        offers: MasterServiceOffers[IO],
        variants: MasterServiceOfferVariants[IO],
      ) =>
        for {
          categoryId <- rnd[CategoryId]
          masterId   <- rnd[MasterId]
          serviceId  <- rnd[ServiceId]
          offerId    <- rnd[MasterServiceOfferId]
          locationId <- rnd[MasterLocationId]
          variantId  <- rnd[MasterServiceOfferVariantId]
          category    = Category(categoryId, rootCategoryId, 0, s"offer-variant-attrs-category-$categoryId")
          master      = Master(masterId, s"offer-variant-attrs-master-$masterId")
          service     = Service(serviceId, categoryId, s"offer-variant-attrs-service-$serviceId")
          offer       = MasterServiceOffer(offerId, masterId, serviceId)
          location    = MasterLocation(
            locationId,
            masterId,
            s"offer-variant-attrs-$locationId",
            s"offer-variant-attrs-address-$locationId",
            BigDecimal("13.3400"),
            BigDecimal("57.7800"),
          )
          schema = makeSchema(
            serviceId,
            ServiceVariantSchemaItem(AttributeDefinition.SessionCount, true),
            ServiceVariantSchemaItem(AttributeDefinition.DepositAmount, false),
            ServiceVariantSchemaItem(AttributeDefinition.MaterialsSurcharge, false),
          )
          variant <- makeVariant(
            variantId,
            offerId,
            locationId,
            BigDecimal("30.0000"),
            BigDecimal("45.0000"),
            60,
            intAttributes        = AttributeMap.Impl(Map(AttributeDefinition.SessionCount -> 5)),
            bigDecimalAttributes = AttributeMap.Impl(
              Map(
                AttributeDefinition.DepositAmount      -> BigDecimal("15.0000"),
                AttributeDefinition.MaterialsSurcharge -> BigDecimal("7.5000"),
              )
            ),
          )
          _   <- categories.upsertCategory(category)
          _   <- masters.upsertMaster(master)
          _   <- services.upsertService(service)
          _   <- serviceVariantSchemas.upsertServiceVariantSchema(schema)
          _   <- offers.upsertMasterServiceOffer(offer)
          _   <- masterLocations.upsertMasterLocation(location)
          _   <- variants.upsertMasterServiceOfferVariant(variant)
          res <- variants.getMasterServiceOfferVariant(variant.id)
          _   <- assertIO(res.contains(variant))
        } yield ()
    }

    "reject creating a variant when offer does not exist" in {
      (rnd: Rnd[IO], masters: Masters[IO], masterLocations: MasterLocations[IO], variants: MasterServiceOfferVariants[IO]) =>
        for {
          masterId   <- rnd[MasterId]
          offerId    <- rnd[MasterServiceOfferId]
          locationId <- rnd[MasterLocationId]
          variantId  <- rnd[MasterServiceOfferVariantId]
          master      = Master(masterId, s"missing-offer-master-$masterId")
          location    = MasterLocation(
            locationId,
            masterId,
            s"missing-offer-location-$locationId",
            s"missing-offer-address-$locationId",
            BigDecimal("1.1000"),
            BigDecimal("2.2000"),
          )
          _       <- masters.upsertMaster(master)
          _       <- masterLocations.upsertMasterLocation(location)
          variant <- makeVariant(variantId, offerId, locationId, BigDecimal("30.0000"), BigDecimal("45.0000"), 60)
          result  <- variants.upsertMasterServiceOfferVariant(variant).either
          _       <- assertIO(result.isLeft)
        } yield ()
    }

    "reject creating a variant when location does not exist" in {
      (rnd: Rnd[IO],
        categories: Categories[IO],
        masters: Masters[IO],
        services: Services[IO],
        offers: MasterServiceOffers[IO],
        variants: MasterServiceOfferVariants[IO],
      ) =>
        for {
          categoryId <- rnd[CategoryId]
          masterId   <- rnd[MasterId]
          serviceId  <- rnd[ServiceId]
          offerId    <- rnd[MasterServiceOfferId]
          locationId <- rnd[MasterLocationId]
          variantId  <- rnd[MasterServiceOfferVariantId]
          category    = Category(categoryId, rootCategoryId, 0, s"missing-location-category-$categoryId")
          master      = Master(masterId, s"missing-location-master-$masterId")
          service     = Service(serviceId, categoryId, s"missing-location-service-$serviceId")
          offer       = MasterServiceOffer(offerId, masterId, serviceId)
          _          <- categories.upsertCategory(category)
          _          <- masters.upsertMaster(master)
          _          <- services.upsertService(service)
          _          <- offers.upsertMasterServiceOffer(offer)
          variant    <- makeVariant(variantId, offerId, locationId, BigDecimal("30.0000"), BigDecimal("45.0000"), 60)
          result     <- variants.upsertMasterServiceOfferVariant(variant).either
          _          <- assertIO(result.isLeft)
        } yield ()
    }

    "make rejects negative priceFrom" in {
      (rnd: Rnd[IO]) =>
        for {
          variantId  <- rnd[MasterServiceOfferVariantId]
          offerId    <- rnd[MasterServiceOfferId]
          locationId <- rnd[MasterLocationId]
          result      = MasterServiceOfferVariant.make(variantId, offerId, locationId, BigDecimal("-1.0000"), BigDecimal("10.0000"), 60)
          _          <- assertIO(result == Left(MasterServiceOfferVariantValidationError.NegativePriceFrom(BigDecimal("-1.0000"))))
        } yield ()
    }

    "make rejects priceTo less than priceFrom" in {
      (rnd: Rnd[IO]) =>
        for {
          variantId  <- rnd[MasterServiceOfferVariantId]
          offerId    <- rnd[MasterServiceOfferId]
          locationId <- rnd[MasterLocationId]
          result      = MasterServiceOfferVariant.make(variantId, offerId, locationId, BigDecimal("20.0000"), BigDecimal("10.0000"), 60)
          _          <- assertIO(
            result == Left(
              MasterServiceOfferVariantValidationError.PriceToLessThanPriceFrom(
                BigDecimal("20.0000"),
                BigDecimal("10.0000"),
              )
            )
          )
        } yield ()
    }

    "make rejects non-positive durationMin" in {
      (rnd: Rnd[IO]) =>
        for {
          variantId  <- rnd[MasterServiceOfferVariantId]
          offerId    <- rnd[MasterServiceOfferId]
          locationId <- rnd[MasterLocationId]
          result      = MasterServiceOfferVariant.make(variantId, offerId, locationId, BigDecimal("20.0000"), BigDecimal("30.0000"), 0)
          _          <- assertIO(result == Left(MasterServiceOfferVariantValidationError.NonPositiveDurationMin(0)))
        } yield ()
    }

    "coded enum stringCode derivation is stable" in {
      (rnd: Rnd[IO]) =>
        for {
          _ <- assertIO(HairRemovalMethod.Wax.stringCode == "wax")
          _ <- assertIO(HairRemovalMethod.Sugaring.stringCode == "sugaring")
          _ <- assertIO(HairRemovalMethod.Laser.stringCode == "laser")
          _ <- assertIO(HairRemovalMethod.Threading.stringCode == "threading")
          _ <- assertIO(NailCoatingType.NoCoating.stringCode == "no_coating")
          _ <- assertIO(NailCoatingType.RegularPolish.stringCode == "regular_polish")
          _ <- assertIO(NailCoatingType.GelPolish.stringCode == "gel_polish")
          _ <- assertIO(NailCoatingType.Shellac.stringCode == "shellac")
          _ <- assertIO(NailCoatingType.Gel.stringCode == "gel")
          _ <- assertIO(NailCoatingType.Acrylic.stringCode == "acrylic")
          _ <- assertIO(NailServiceType.Manicure.stringCode == "manicure")
          _ <- assertIO(NailServiceType.Pedicure.stringCode == "pedicure")
          _ <- assertIO(NailServiceType.Extension.stringCode == "extension")
          _ <- assertIO(NailServiceType.Refill.stringCode == "refill")
          _ <- assertIO(NailServiceType.Removal.stringCode == "removal")
          _ <- assertIO(NailServiceType.Repair.stringCode == "repair")
          _ <- assertIO(LashServiceType.Extension.stringCode == "extension")
          _ <- assertIO(LashServiceType.Refill.stringCode == "refill")
          _ <- assertIO(LashServiceType.Lifting.stringCode == "lifting")
          _ <- assertIO(LashServiceType.Tinting.stringCode == "tinting")
          _ <- assertIO(LashServiceType.Removal.stringCode == "removal")
          _ <- assertIO(LashVolume.Classic1D.stringCode == "classic1_d")
          _ <- assertIO(LashVolume.Volume2D.stringCode == "volume2_d")
          _ <- assertIO(LashVolume.Volume3D.stringCode == "volume3_d")
          _ <- assertIO(LashVolume.MegaVolume.stringCode == "mega_volume")
          _ <- assertIO(BrowServiceType.Shaping.stringCode == "shaping")
          _ <- assertIO(BrowServiceType.Tinting.stringCode == "tinting")
          _ <- assertIO(BrowServiceType.Lamination.stringCode == "lamination")
          _ <- assertIO(BrowServiceType.Henna.stringCode == "henna")
          _ <- assertIO(PmuArea.Brows.stringCode == "brows")
          _ <- assertIO(PmuArea.Lips.stringCode == "lips")
          _ <- assertIO(PmuArea.Eyeliner.stringCode == "eyeliner")
          _ <- assertIO(FacialTreatmentType.Classic.stringCode == "classic")
          _ <- assertIO(FacialTreatmentType.Cleansing.stringCode == "cleansing")
          _ <- assertIO(FacialTreatmentType.Hydration.stringCode == "hydration")
          _ <- assertIO(FacialTreatmentType.AntiAging.stringCode == "anti_aging")
          _ <- assertIO(FacialTreatmentType.Peeling.stringCode == "peeling")
          _ <- assertIO(FacialTreatmentType.Microneedling.stringCode == "microneedling")
          _ <- assertIO(FacialTreatmentType.BbGlow.stringCode == "bb_glow")
          _ <- assertIO(FacialTreatmentType.Aquafacial.stringCode == "aquafacial")
          _ <- assertIO(BodyArea.UpperLip.stringCode == "upper_lip")
          _ <- assertIO(BodyArea.Chin.stringCode == "chin")
          _ <- assertIO(BodyArea.Face.stringCode == "face")
          _ <- assertIO(BodyArea.Armpits.stringCode == "armpits")
          _ <- assertIO(BodyArea.Bikini.stringCode == "bikini")
          _ <- assertIO(BodyArea.Brazilian.stringCode == "brazilian")
          _ <- assertIO(BodyArea.LowerLegs.stringCode == "lower_legs")
          _ <- assertIO(BodyArea.FullLegs.stringCode == "full_legs")
          _ <- assertIO(BodyArea.Arms.stringCode == "arms")
          _ <- assertIO(BodyArea.Back.stringCode == "back")
          _ <- assertIO(BodyArea.FaceNeckDecollete.stringCode == "face_neck_decollete")
        } yield ()
    }

    "coded enum intCode decoding works for valid and invalid values" in {
      (rnd: Rnd[IO]) =>
        for {
          _ <- assertIO(HairRemovalMethod.fromIntCode(2).contains(HairRemovalMethod.Sugaring))
          _ <- assertIO(HairRemovalMethod.fromIntCode(999).isEmpty)
          _ <- assertIO(NailCoatingType.fromIntCode(3).contains(NailCoatingType.GelPolish))
          _ <- assertIO(NailCoatingType.fromIntCode(999).isEmpty)
          _ <- assertIO(NailServiceType.fromIntCode(1).contains(NailServiceType.Manicure))
          _ <- assertIO(NailServiceType.fromIntCode(999).isEmpty)
          _ <- assertIO(LashServiceType.fromIntCode(1).contains(LashServiceType.Extension))
          _ <- assertIO(LashServiceType.fromIntCode(999).isEmpty)
          _ <- assertIO(LashVolume.fromIntCode(2).contains(LashVolume.Volume2D))
          _ <- assertIO(LashVolume.fromIntCode(999).isEmpty)
          _ <- assertIO(BrowServiceType.fromIntCode(3).contains(BrowServiceType.Lamination))
          _ <- assertIO(BrowServiceType.fromIntCode(999).isEmpty)
          _ <- assertIO(PmuArea.fromIntCode(1).contains(PmuArea.Brows))
          _ <- assertIO(PmuArea.fromIntCode(999).isEmpty)
          _ <- assertIO(FacialTreatmentType.fromIntCode(6).contains(FacialTreatmentType.Microneedling))
          _ <- assertIO(FacialTreatmentType.fromIntCode(999).isEmpty)
          _ <- assertIO(BodyArea.fromIntCode(1).contains(BodyArea.UpperLip))
          _ <- assertIO(BodyArea.fromIntCode(999).isEmpty)
        } yield ()
    }

    "coded enum stringCode decoding works for valid and invalid values" in {
      (rnd: Rnd[IO]) =>
        for {
          _ <- assertIO(HairRemovalMethod.fromStringCode("sugaring").contains(HairRemovalMethod.Sugaring))
          _ <- assertIO(HairRemovalMethod.fromStringCode("unknown").isEmpty)
          _ <- assertIO(NailCoatingType.fromStringCode("gel_polish").contains(NailCoatingType.GelPolish))
          _ <- assertIO(NailCoatingType.fromStringCode("unknown").isEmpty)
          _ <- assertIO(NailServiceType.fromStringCode("manicure").contains(NailServiceType.Manicure))
          _ <- assertIO(NailServiceType.fromStringCode("unknown").isEmpty)
          _ <- assertIO(LashServiceType.fromStringCode("extension").contains(LashServiceType.Extension))
          _ <- assertIO(LashServiceType.fromStringCode("unknown").isEmpty)
          _ <- assertIO(LashVolume.fromStringCode("volume2_d").contains(LashVolume.Volume2D))
          _ <- assertIO(LashVolume.fromStringCode("unknown").isEmpty)
          _ <- assertIO(BrowServiceType.fromStringCode("lamination").contains(BrowServiceType.Lamination))
          _ <- assertIO(BrowServiceType.fromStringCode("unknown").isEmpty)
          _ <- assertIO(PmuArea.fromStringCode("brows").contains(PmuArea.Brows))
          _ <- assertIO(PmuArea.fromStringCode("unknown").isEmpty)
          _ <- assertIO(FacialTreatmentType.fromStringCode("microneedling").contains(FacialTreatmentType.Microneedling))
          _ <- assertIO(FacialTreatmentType.fromStringCode("unknown").isEmpty)
          _ <- assertIO(BodyArea.fromStringCode("upper_lip").contains(BodyArea.UpperLip))
          _ <- assertIO(BodyArea.fromStringCode("unknown").isEmpty)
        } yield ()
    }

    "attribute definition registry resolves all new enum attribute codes" in {
      (rnd: Rnd[IO]) =>
        for {
          _ <- assertIO(AttributeDefinition.fromCode("nail_service_type").contains(AttributeDefinition.NailServiceTypeAttribute))
          _ <- assertIO(AttributeDefinition.fromCode("lash_service_type").contains(AttributeDefinition.LashServiceTypeAttribute))
          _ <- assertIO(AttributeDefinition.fromCode("lash_volume").contains(AttributeDefinition.LashVolumeAttribute))
          _ <- assertIO(AttributeDefinition.fromCode("brow_service_type").contains(AttributeDefinition.BrowServiceTypeAttribute))
          _ <- assertIO(AttributeDefinition.fromCode("pmu_area").contains(AttributeDefinition.PmuAreaAttribute))
          _ <- assertIO(AttributeDefinition.fromCode("facial_treatment_type").contains(AttributeDefinition.FacialTreatmentTypeAttribute))
          _ <- assertIO(AttributeDefinition.fromCode("body_area").contains(AttributeDefinition.BodyAreaAttribute))
          _ <- assertIO(AttributeDefinition.fromCodeAsEnum("nail_service_type").contains(AttributeDefinition.NailServiceTypeAttribute))
          _ <- assertIO(AttributeDefinition.fromCodeAsEnum("lash_service_type").contains(AttributeDefinition.LashServiceTypeAttribute))
          _ <- assertIO(AttributeDefinition.fromCodeAsEnum("lash_volume").contains(AttributeDefinition.LashVolumeAttribute))
          _ <- assertIO(AttributeDefinition.fromCodeAsEnum("brow_service_type").contains(AttributeDefinition.BrowServiceTypeAttribute))
          _ <- assertIO(AttributeDefinition.fromCodeAsEnum("pmu_area").contains(AttributeDefinition.PmuAreaAttribute))
          _ <- assertIO(AttributeDefinition.fromCodeAsEnum("facial_treatment_type").contains(AttributeDefinition.FacialTreatmentTypeAttribute))
          _ <- assertIO(AttributeDefinition.fromCodeAsEnum("body_area").contains(AttributeDefinition.BodyAreaAttribute))
        } yield ()
    }

    "attributes expose typed access by definition and typed views" in {
      (rnd: Rnd[IO]) =>
        for {
          variantId  <- rnd[MasterServiceOfferVariantId]
          offerId    <- rnd[MasterServiceOfferId]
          locationId <- rnd[MasterLocationId]
          attributes  = MasterServiceOfferVariantAttributes(
            intValues        = AttributeMap.Impl(Map(AttributeDefinition.SessionCount -> 3)),
            bigDecimalValues = AttributeMap.Impl(Map(AttributeDefinition.DepositAmount -> BigDecimal("12.5000"))),
          )
          variant <- ZIO
            .fromEither(
              MasterServiceOfferVariant.make(
                variantId,
                offerId,
                locationId,
                BigDecimal("20.0000"),
                BigDecimal("30.0000"),
                60,
                attributes,
              )
            )
            .mapError(error => QueryFailure.operation("make-master-service-offer-variant", error.message))
          _ <- assertIO(variant.getAttribute(AttributeDefinition.SessionCount).contains(3))
          _ <- assertIO(
            variant.getAttribute(AttributeDefinition.DepositAmount).contains(BigDecimal("12.5000"))
          )
          _ <- assertIO(variant.intAttributes.get(AttributeDefinition.SessionCount).contains(3))
          _ <- assertIO(
            variant.bigDecimalAttributes.get(AttributeDefinition.DepositAmount).contains(BigDecimal("12.5000"))
          )
          _ <- assertIO(variant.intAttributes.keySet == Set(AttributeDefinition.SessionCount))
          _ <- assertIO(
            variant.bigDecimalAttributes.keySet == Set(
              AttributeDefinition.DepositAmount
            )
          )
          _ <- assertIO(variant.enumAttributes.get(AttributeDefinition.HairRemovalMethodAttribute).isEmpty)
        } yield ()
    }

    "schema validate rejects disallowed attribute" in {
      (rnd: Rnd[IO]) =>
        for {
          serviceId <- rnd[ServiceId]
          schema     = makeSchema(serviceId, ServiceVariantSchemaItem(AttributeDefinition.SessionCount, false))
          attributes = MasterServiceOfferVariantAttributes(
            intValues        = AttributeMap.empty,
            bigDecimalValues = AttributeMap.Impl(Map(AttributeDefinition.DepositAmount -> BigDecimal("12.5000"))),
          )
          _ <- assertIO(
            schema.validate(attributes) == Left(
              ServiceVariantSchemaValidationError.DisallowedAttribute(
                AttributeDefinition.DepositAmount
              )
            )
          )
        } yield ()
    }

    "schema validate rejects missing required attribute" in {
      (rnd: Rnd[IO]) =>
        for {
          serviceId <- rnd[ServiceId]
          schema     = makeSchema(serviceId, ServiceVariantSchemaItem(AttributeDefinition.SessionCount, true))
          attributes = MasterServiceOfferVariantAttributes.empty
          _         <- assertIO(
            schema.validate(attributes) == Left(
              ServiceVariantSchemaValidationError.MissingRequiredAttribute(
                AttributeDefinition.SessionCount
              )
            )
          )
        } yield ()
    }

    "schema validate supports allowed enum attributes" in {
      (rnd: Rnd[IO]) =>
        for {
          serviceId <- rnd[ServiceId]
          schema     = makeSchema(serviceId, ServiceVariantSchemaItem(AttributeDefinition.HairRemovalMethodAttribute, false))
          attributes = MasterServiceOfferVariantAttributes(
            intValues        = AttributeMap.empty,
            bigDecimalValues = AttributeMap.empty,
            enumValues = enumAttributeMap(
              AttributeDefinition.HairRemovalMethodAttribute -> HairRemovalMethod.Sugaring
            ),
          )
          _ <- assertIO(schema.validate(attributes) == Right(()))
        } yield ()
    }

    "schema validate supports allowed new enum attributes" in {
      (rnd: Rnd[IO]) =>
        for {
          serviceId <- rnd[ServiceId]
          schema     = makeSchema(serviceId, ServiceVariantSchemaItem(AttributeDefinition.NailServiceTypeAttribute, false))
          attributes = MasterServiceOfferVariantAttributes(
            intValues        = AttributeMap.empty,
            bigDecimalValues = AttributeMap.empty,
            enumValues = enumAttributeMap(
              AttributeDefinition.NailServiceTypeAttribute -> NailServiceType.Manicure
            ),
          )
          _ <- assertIO(schema.validate(attributes) == Right(()))
        } yield ()
    }

    "schema validate rejects disallowed enum attribute" in {
      (rnd: Rnd[IO]) =>
        for {
          serviceId <- rnd[ServiceId]
          schema     = makeSchema(serviceId, ServiceVariantSchemaItem(AttributeDefinition.SessionCount, false))
          attributes = MasterServiceOfferVariantAttributes(
            intValues        = AttributeMap.empty,
            bigDecimalValues = AttributeMap.empty,
            enumValues = enumAttributeMap(
              AttributeDefinition.HairRemovalMethodAttribute -> HairRemovalMethod.Sugaring
            ),
          )
          _ <- assertIO(
            schema.validate(attributes) == Left(
              ServiceVariantSchemaValidationError.DisallowedAttribute(
                AttributeDefinition.HairRemovalMethodAttribute
              )
            )
          )
        } yield ()
    }

    "schema validate rejects disallowed new enum attribute" in {
      (rnd: Rnd[IO]) =>
        for {
          serviceId <- rnd[ServiceId]
          schema     = makeSchema(serviceId, ServiceVariantSchemaItem(AttributeDefinition.SessionCount, false))
          attributes = MasterServiceOfferVariantAttributes(
            intValues        = AttributeMap.empty,
            bigDecimalValues = AttributeMap.empty,
            enumValues = enumAttributeMap(
              AttributeDefinition.NailServiceTypeAttribute -> NailServiceType.Manicure
            ),
          )
          _ <- assertIO(
            schema.validate(attributes) == Left(
              ServiceVariantSchemaValidationError.DisallowedAttribute(
                AttributeDefinition.NailServiceTypeAttribute
              )
            )
          )
        } yield ()
    }

    "schema validate rejects missing required enum attribute" in {
      (rnd: Rnd[IO]) =>
        for {
          serviceId <- rnd[ServiceId]
          schema     = makeSchema(serviceId, ServiceVariantSchemaItem(AttributeDefinition.HairRemovalMethodAttribute, true))
          attributes = MasterServiceOfferVariantAttributes.empty
          _         <- assertIO(
            schema.validate(attributes) == Left(
              ServiceVariantSchemaValidationError.MissingRequiredAttribute(
                AttributeDefinition.HairRemovalMethodAttribute
              )
            )
          )
        } yield ()
    }

    "decode rejects additional attribute code in wrong typed section" in {
      (rnd: Rnd[IO]) =>
        for {
          offerId    <- rnd[MasterServiceOfferId]
          locationId <- rnd[MasterLocationId]
          variantId  <- rnd[MasterServiceOfferVariantId]
          json        = Json.obj(
            "id"                   -> variantId.asJson,
            "masterServiceOfferId" -> offerId.asJson,
            "masterLocationId"     -> locationId.asJson,
            "priceFrom"            -> BigDecimal("30.0000").asJson,
            "priceTo"              -> BigDecimal("45.0000").asJson,
            "durationMin"          -> 60.asJson,
            "intAttributes"        -> Json.obj(
              "deposit_amount" -> 3.asJson
            ),
          )
          result = json.as[MasterServiceOfferVariant]
          _     <- assertIO(result.isLeft)
        } yield ()
    }

    "decode supports enum attributes from string codes" in {
      (rnd: Rnd[IO]) =>
        for {
          offerId    <- rnd[MasterServiceOfferId]
          locationId <- rnd[MasterLocationId]
          variantId  <- rnd[MasterServiceOfferVariantId]
          json        = Json.obj(
            "id"                   -> variantId.asJson,
            "masterServiceOfferId" -> offerId.asJson,
            "masterLocationId"     -> locationId.asJson,
            "priceFrom"            -> BigDecimal("30.0000").asJson,
            "priceTo"              -> BigDecimal("45.0000").asJson,
            "durationMin"          -> 60.asJson,
            "enumAttributes"       -> Json.obj(
              "hair_removal_method" -> "sugaring".asJson,
              "nail_coating_type"   -> "gel_polish".asJson,
            ),
          )
          result = json.as[MasterServiceOfferVariant]
          _ <- assertIO(result.exists(_.enumAttributes.get(AttributeDefinition.HairRemovalMethodAttribute).contains(HairRemovalMethod.Sugaring)))
          _ <- assertIO(result.exists(_.enumAttributes.get(AttributeDefinition.NailCoatingTypeAttribute).contains(NailCoatingType.GelPolish)))
        } yield ()
    }

    "decode and encode supports new enum attributes as strings" in {
      (rnd: Rnd[IO]) =>
        for {
          offerId    <- rnd[MasterServiceOfferId]
          locationId <- rnd[MasterLocationId]
          variantId  <- rnd[MasterServiceOfferVariantId]
          json        = Json.obj(
            "id"                   -> variantId.asJson,
            "masterServiceOfferId" -> offerId.asJson,
            "masterLocationId"     -> locationId.asJson,
            "priceFrom"            -> BigDecimal("30.0000").asJson,
            "priceTo"              -> BigDecimal("45.0000").asJson,
            "durationMin"          -> 60.asJson,
            "enumAttributes"       -> Json.obj(
              "nail_service_type"     -> "manicure".asJson,
              "lash_service_type"     -> "extension".asJson,
              "lash_volume"           -> "volume2_d".asJson,
              "brow_service_type"     -> "lamination".asJson,
              "pmu_area"              -> "brows".asJson,
              "facial_treatment_type" -> "microneedling".asJson,
              "body_area"             -> "upper_lip".asJson,
            ),
          )
          decoded = json.as[MasterServiceOfferVariant]
          _ <- assertIO(decoded.exists(_.enumAttributes.get(AttributeDefinition.NailServiceTypeAttribute).contains(NailServiceType.Manicure)))
          _ <- assertIO(decoded.exists(_.enumAttributes.get(AttributeDefinition.LashServiceTypeAttribute).contains(LashServiceType.Extension)))
          _ <- assertIO(decoded.exists(_.enumAttributes.get(AttributeDefinition.LashVolumeAttribute).contains(LashVolume.Volume2D)))
          _ <- assertIO(decoded.exists(_.enumAttributes.get(AttributeDefinition.BrowServiceTypeAttribute).contains(BrowServiceType.Lamination)))
          _ <- assertIO(decoded.exists(_.enumAttributes.get(AttributeDefinition.PmuAreaAttribute).contains(PmuArea.Brows)))
          _ <- assertIO(decoded.exists(_.enumAttributes.get(AttributeDefinition.FacialTreatmentTypeAttribute).contains(FacialTreatmentType.Microneedling)))
          _ <- assertIO(decoded.exists(_.enumAttributes.get(AttributeDefinition.BodyAreaAttribute).contains(BodyArea.UpperLip)))
          encoded = decoded.toOption.get.asJson
          _ <- assertIO(encoded.hcursor.downField("enumAttributes").downField("nail_service_type").as[String].contains("manicure"))
          _ <- assertIO(encoded.hcursor.downField("enumAttributes").downField("lash_service_type").as[String].contains("extension"))
          _ <- assertIO(encoded.hcursor.downField("enumAttributes").downField("lash_volume").as[String].contains("volume2_d"))
          _ <- assertIO(encoded.hcursor.downField("enumAttributes").downField("brow_service_type").as[String].contains("lamination"))
          _ <- assertIO(encoded.hcursor.downField("enumAttributes").downField("pmu_area").as[String].contains("brows"))
          _ <- assertIO(encoded.hcursor.downField("enumAttributes").downField("facial_treatment_type").as[String].contains("microneedling"))
          _ <- assertIO(encoded.hcursor.downField("enumAttributes").downField("body_area").as[String].contains("upper_lip"))
        } yield ()
    }

    "encode emits enum attributes as string codes" in {
      (rnd: Rnd[IO]) =>
        for {
          offerId    <- rnd[MasterServiceOfferId]
          locationId <- rnd[MasterLocationId]
          variantId  <- rnd[MasterServiceOfferVariantId]
          variant <- makeVariant(
            variantId,
            offerId,
            locationId,
            BigDecimal("30.0000"),
            BigDecimal("45.0000"),
            60,
            enumAttributes = enumAttributeMap(
              AttributeDefinition.HairRemovalMethodAttribute -> HairRemovalMethod.Sugaring,
              AttributeDefinition.NailCoatingTypeAttribute   -> NailCoatingType.GelPolish,
            ),
          )
          json = variant.asJson
          _ <- assertIO(
            json.hcursor.downField("enumAttributes").downField("hair_removal_method").as[String].contains("sugaring")
          )
          _ <- assertIO(
            json.hcursor.downField("enumAttributes").downField("nail_coating_type").as[String].contains("gel_polish")
          )
        } yield ()
    }

    "decode rejects unknown enum string code" in {
      (rnd: Rnd[IO]) =>
        for {
          offerId    <- rnd[MasterServiceOfferId]
          locationId <- rnd[MasterLocationId]
          variantId  <- rnd[MasterServiceOfferVariantId]
          json        = Json.obj(
            "id"                   -> variantId.asJson,
            "masterServiceOfferId" -> offerId.asJson,
            "masterLocationId"     -> locationId.asJson,
            "priceFrom"            -> BigDecimal("30.0000").asJson,
            "priceTo"              -> BigDecimal("45.0000").asJson,
            "durationMin"          -> 60.asJson,
            "enumAttributes"       -> Json.obj(
              "hair_removal_method" -> "unknown_method".asJson
            ),
          )
          result = json.as[MasterServiceOfferVariant]
          _     <- assertIO(result.isLeft)
        } yield ()
    }

    "decode rejects enum attribute in intAttributes group" in {
      (rnd: Rnd[IO]) =>
        for {
          offerId    <- rnd[MasterServiceOfferId]
          locationId <- rnd[MasterLocationId]
          variantId  <- rnd[MasterServiceOfferVariantId]
          json        = Json.obj(
            "id"                   -> variantId.asJson,
            "masterServiceOfferId" -> offerId.asJson,
            "masterLocationId"     -> locationId.asJson,
            "priceFrom"            -> BigDecimal("30.0000").asJson,
            "priceTo"              -> BigDecimal("45.0000").asJson,
            "durationMin"          -> 60.asJson,
            "intAttributes"        -> Json.obj(
              "hair_removal_method" -> 2.asJson
            ),
          )
          result = json.as[MasterServiceOfferVariant]
          _     <- assertIO(result.isLeft)
        } yield ()
    }

    "decode rejects int attribute in enumAttributes group" in {
      (rnd: Rnd[IO]) =>
        for {
          offerId    <- rnd[MasterServiceOfferId]
          locationId <- rnd[MasterLocationId]
          variantId  <- rnd[MasterServiceOfferVariantId]
          json        = Json.obj(
            "id"                   -> variantId.asJson,
            "masterServiceOfferId" -> offerId.asJson,
            "masterLocationId"     -> locationId.asJson,
            "priceFrom"            -> BigDecimal("30.0000").asJson,
            "priceTo"              -> BigDecimal("45.0000").asJson,
            "durationMin"          -> 60.asJson,
            "enumAttributes"       -> Json.obj(
              "session_count" -> "3".asJson
            ),
          )
          result = json.as[MasterServiceOfferVariant]
          _     <- assertIO(result.isLeft)
        } yield ()
    }

    "decode rejects unknown additional attribute code" in {
      (rnd: Rnd[IO]) =>
        for {
          offerId    <- rnd[MasterServiceOfferId]
          locationId <- rnd[MasterLocationId]
          variantId  <- rnd[MasterServiceOfferVariantId]
          json        = Json.obj(
            "id"                   -> variantId.asJson,
            "masterServiceOfferId" -> offerId.asJson,
            "masterLocationId"     -> locationId.asJson,
            "priceFrom"            -> BigDecimal("30.0000").asJson,
            "priceTo"              -> BigDecimal("45.0000").asJson,
            "durationMin"          -> 60.asJson,
            "intAttributes"        -> Json.obj(
              "unknown_attribute_code" -> 3.asJson
            ),
          )
          result = json.as[MasterServiceOfferVariant]
          _     <- assertIO(result.isLeft)
        } yield ()
    }

    "reject additional attribute stored in wrong typed storage" in {
      (rnd: Rnd[IO],
        categories: Categories[IO],
        masters: Masters[IO],
        services: Services[IO],
        serviceVariantSchemas: ServiceVariantSchemas[IO],
        masterLocations: MasterLocations[IO],
        offers: MasterServiceOffers[IO],
      ) =>
        for {
          categoryId <- rnd[CategoryId]
          masterId   <- rnd[MasterId]
          serviceId  <- rnd[ServiceId]
          offerId    <- rnd[MasterServiceOfferId]
          locationId <- rnd[MasterLocationId]
          variantId  <- rnd[MasterServiceOfferVariantId]
          category    = Category(categoryId, rootCategoryId, 0, s"variant-type-category-$categoryId")
          master      = Master(masterId, s"variant-type-master-$masterId")
          service     = Service(serviceId, categoryId, s"variant-type-service-$serviceId")
          offer       = MasterServiceOffer(offerId, masterId, serviceId)
          location    = MasterLocation(
            locationId,
            masterId,
            s"variant-type-location-$locationId",
            s"variant-type-address-$locationId",
            BigDecimal("10.0000"),
            BigDecimal("20.0000"),
          )
          schema = makeSchema(serviceId, ServiceVariantSchemaItem(AttributeDefinition.DepositAmount, false))
          _     <- categories.upsertCategory(category)
          _     <- masters.upsertMaster(master)
          _     <- services.upsertService(service)
          _     <- serviceVariantSchemas.upsertServiceVariantSchema(schema)
          _     <- offers.upsertMasterServiceOffer(offer)
          _     <- masterLocations.upsertMasterLocation(location)
          json   = Json.obj(
            "id"                   -> variantId.asJson,
            "masterServiceOfferId" -> offerId.asJson,
            "masterLocationId"     -> locationId.asJson,
            "priceFrom"            -> BigDecimal("30.0000").asJson,
            "priceTo"              -> BigDecimal("45.0000").asJson,
            "durationMin"          -> 60.asJson,
            "intAttributes"        -> Json.obj(
              "deposit_amount" -> 3.asJson
            ),
          )
          result = json.as[MasterServiceOfferVariant]
          _     <- assertIO(result.isLeft)
        } yield ()
    }

    "upsert rejects disallowed additional attribute by service schema" in {
      (rnd: Rnd[IO],
        categories: Categories[IO],
        masters: Masters[IO],
        services: Services[IO],
        serviceVariantSchemas: ServiceVariantSchemas[IO],
        masterLocations: MasterLocations[IO],
        offers: MasterServiceOffers[IO],
        variants: MasterServiceOfferVariants[IO],
      ) =>
        for {
          categoryId <- rnd[CategoryId]
          masterId   <- rnd[MasterId]
          serviceId  <- rnd[ServiceId]
          offerId    <- rnd[MasterServiceOfferId]
          locationId <- rnd[MasterLocationId]
          variantId  <- rnd[MasterServiceOfferVariantId]
          category    = Category(categoryId, rootCategoryId, 0, s"variant-schema-category-$categoryId")
          master      = Master(masterId, s"variant-schema-master-$masterId")
          service     = Service(serviceId, categoryId, s"variant-schema-service-$serviceId")
          offer       = MasterServiceOffer(offerId, masterId, serviceId)
          location    = MasterLocation(
            locationId,
            masterId,
            s"variant-schema-location-$locationId",
            s"variant-schema-address-$locationId",
            BigDecimal("10.0000"),
            BigDecimal("20.0000"),
          )
          schema   = makeSchema(serviceId, ServiceVariantSchemaItem(AttributeDefinition.SessionCount, false))
          variant <- makeVariant(
            variantId,
            offerId,
            locationId,
            BigDecimal("30.0000"),
            BigDecimal("45.0000"),
            60,
            bigDecimalAttributes = AttributeMap.Impl(Map(AttributeDefinition.DepositAmount -> BigDecimal("8.0000"))),
          )
          _      <- categories.upsertCategory(category)
          _      <- masters.upsertMaster(master)
          _      <- services.upsertService(service)
          _      <- serviceVariantSchemas.upsertServiceVariantSchema(schema)
          _      <- offers.upsertMasterServiceOffer(offer)
          _      <- masterLocations.upsertMasterLocation(location)
          result <- variants.upsertMasterServiceOfferVariant(variant).either
          _      <- assertIO(
            result.left.exists {
              case QueryFailure.OperationFailure(operationName, message) =>
                operationName == "upsert-master-service-offer-variant" &&
                message == s"Service $serviceId does not allow MasterServiceOfferVariant attribute deposit_amount"
              case _ =>
                false
            }
          )
        } yield ()
    }

    "upsert rejects missing required additional attribute by service schema" in {
      (rnd: Rnd[IO],
        categories: Categories[IO],
        masters: Masters[IO],
        services: Services[IO],
        serviceVariantSchemas: ServiceVariantSchemas[IO],
        masterLocations: MasterLocations[IO],
        offers: MasterServiceOffers[IO],
        variants: MasterServiceOfferVariants[IO],
      ) =>
        for {
          categoryId <- rnd[CategoryId]
          masterId   <- rnd[MasterId]
          serviceId  <- rnd[ServiceId]
          offerId    <- rnd[MasterServiceOfferId]
          locationId <- rnd[MasterLocationId]
          variantId  <- rnd[MasterServiceOfferVariantId]
          category    = Category(categoryId, rootCategoryId, 0, s"variant-required-category-$categoryId")
          master      = Master(masterId, s"variant-required-master-$masterId")
          service     = Service(serviceId, categoryId, s"variant-required-service-$serviceId")
          offer       = MasterServiceOffer(offerId, masterId, serviceId)
          location    = MasterLocation(
            locationId,
            masterId,
            s"variant-required-location-$locationId",
            s"variant-required-address-$locationId",
            BigDecimal("10.0000"),
            BigDecimal("20.0000"),
          )
          schema   = makeSchema(serviceId, ServiceVariantSchemaItem(AttributeDefinition.SessionCount, true))
          variant <- makeVariant(variantId, offerId, locationId, BigDecimal("30.0000"), BigDecimal("45.0000"), 60)
          _       <- categories.upsertCategory(category)
          _       <- masters.upsertMaster(master)
          _       <- services.upsertService(service)
          _       <- serviceVariantSchemas.upsertServiceVariantSchema(schema)
          _       <- offers.upsertMasterServiceOffer(offer)
          _       <- masterLocations.upsertMasterLocation(location)
          result  <- variants.upsertMasterServiceOfferVariant(variant).either
          _       <- assertIO(
            result.left.exists {
              case QueryFailure.OperationFailure(operationName, message) =>
                operationName == "upsert-master-service-offer-variant" &&
                message == s"Service $serviceId requires MasterServiceOfferVariant attribute session_count"
              case _ =>
                false
            }
          )
        } yield ()
    }

    "reject enum value that does not belong to enum attribute" in {
      (rnd: Rnd[IO],
        categories: Categories[IO],
        masters: Masters[IO],
        services: Services[IO],
        serviceVariantSchemas: ServiceVariantSchemas[IO],
        masterLocations: MasterLocations[IO],
        offers: MasterServiceOffers[IO],
        variants: MasterServiceOfferVariants[IO],
      ) =>
        for {
          categoryId <- rnd[CategoryId]
          masterId   <- rnd[MasterId]
          serviceId  <- rnd[ServiceId]
          offerId    <- rnd[MasterServiceOfferId]
          locationId <- rnd[MasterLocationId]
          variantId  <- rnd[MasterServiceOfferVariantId]
          category    = Category(categoryId, rootCategoryId, 0, s"variant-enum-mismatch-category-$categoryId")
          master      = Master(masterId, s"variant-enum-mismatch-master-$masterId")
          service     = Service(serviceId, categoryId, s"variant-enum-mismatch-service-$serviceId")
          offer       = MasterServiceOffer(offerId, masterId, serviceId)
          location    = MasterLocation(
            locationId,
            masterId,
            s"variant-enum-mismatch-location-$locationId",
            s"variant-enum-mismatch-address-$locationId",
            BigDecimal("10.0000"),
            BigDecimal("20.0000"),
          )
          schema = makeSchema(serviceId, ServiceVariantSchemaItem(AttributeDefinition.NailCoatingTypeAttribute, false))
          variant <- makeVariant(
            variantId,
            offerId,
            locationId,
            BigDecimal("30.0000"),
            BigDecimal("45.0000"),
            60,
            enumAttributes = enumAttributeMap(
              AttributeDefinition.NailCoatingTypeAttribute -> HairRemovalMethod.Sugaring
            ),
          )
          _      <- categories.upsertCategory(category)
          _      <- masters.upsertMaster(master)
          _      <- services.upsertService(service)
          _      <- serviceVariantSchemas.upsertServiceVariantSchema(schema)
          _      <- offers.upsertMasterServiceOffer(offer)
          _      <- masterLocations.upsertMasterLocation(location)
          result <- variants.upsertMasterServiceOfferVariant(variant).either
          _      <- assertIO(
                      result.left.exists {
                        case QueryFailure.OperationFailure(operationName, message) =>
                          operationName == "upsert-master-service-offer-variant" &&
                          message.contains("nail_coating_type") &&
                          message.contains("sugaring") &&
                          message.contains("does not accept enum value")
                        case _ =>
                          false
                      }
                    )
        } yield ()
    }

    "reject creating a variant when offer and location belong to different masters" in {
      (rnd: Rnd[IO],
        categories: Categories[IO],
        masters: Masters[IO],
        services: Services[IO],
        masterLocations: MasterLocations[IO],
        offers: MasterServiceOffers[IO],
        variants: MasterServiceOfferVariants[IO],
      ) =>
        for {
          categoryId <- rnd[CategoryId]
          master1Id  <- rnd[MasterId]
          master2Id  <- rnd[MasterId]
          serviceId  <- rnd[ServiceId]
          offerId    <- rnd[MasterServiceOfferId]
          locationId <- rnd[MasterLocationId]
          variantId  <- rnd[MasterServiceOfferVariantId]
          category    = Category(categoryId, rootCategoryId, 0, s"mismatch-category-$categoryId")
          master1     = Master(master1Id, s"mismatch-master-a-$master1Id")
          master2     = Master(master2Id, s"mismatch-master-b-$master2Id")
          service     = Service(serviceId, categoryId, s"mismatch-service-$serviceId")
          offer       = MasterServiceOffer(offerId, master1Id, serviceId)
          location    = MasterLocation(
            locationId,
            master2Id,
            s"mismatch-location-$locationId",
            s"mismatch-address-$locationId",
            BigDecimal("17.0000"),
            BigDecimal("27.0000"),
          )
          result <- {
            for {
              _       <- categories.upsertCategory(category)
              _       <- masters.upsertMaster(master1)
              _       <- masters.upsertMaster(master2)
              _       <- services.upsertService(service)
              _       <- offers.upsertMasterServiceOffer(offer)
              _       <- masterLocations.upsertMasterLocation(location)
              variant <- makeVariant(variantId, offerId, locationId, BigDecimal("30.0000"), BigDecimal("45.0000"), 60)
              r       <- variants.upsertMasterServiceOfferVariant(variant).either
            } yield r
          }
          _ <- assertIO(result.isLeft)
        } yield ()
    }

    "allow creating several variants for one offer" in {
      (rnd: Rnd[IO],
        categories: Categories[IO],
        masters: Masters[IO],
        services: Services[IO],
        masterLocations: MasterLocations[IO],
        offers: MasterServiceOffers[IO],
        variants: MasterServiceOfferVariants[IO],
      ) =>
        for {
          categoryId  <- rnd[CategoryId]
          masterId    <- rnd[MasterId]
          serviceId   <- rnd[ServiceId]
          offerId     <- rnd[MasterServiceOfferId]
          location1Id <- rnd[MasterLocationId]
          location2Id <- rnd[MasterLocationId]
          variant1Id  <- rnd[MasterServiceOfferVariantId]
          variant2Id  <- rnd[MasterServiceOfferVariantId]
          category     = Category(categoryId, rootCategoryId, 0, s"variants-by-offer-category-$categoryId")
          master       = Master(masterId, s"variants-by-offer-master-$masterId")
          service      = Service(serviceId, categoryId, s"variants-by-offer-service-$serviceId")
          offer        = MasterServiceOffer(offerId, masterId, serviceId)
          location1    = MasterLocation(
            location1Id,
            masterId,
            s"variants-location-a-$location1Id",
            s"variants-address-a-$location1Id",
            BigDecimal("10.0000"),
            BigDecimal("20.0000"),
          )
          location2 = MasterLocation(
            location2Id,
            masterId,
            s"variants-location-b-$location2Id",
            s"variants-address-b-$location2Id",
            BigDecimal("30.0000"),
            BigDecimal("40.0000"),
          )
          variant1 <- makeVariant(variant1Id, offerId, location1Id, BigDecimal("10.0000"), BigDecimal("20.0000"), 30)
          variant2 <- makeVariant(variant2Id, offerId, location2Id, BigDecimal("30.0000"), BigDecimal("40.0000"), 90)
          _        <- categories.upsertCategory(category)
          _        <- masters.upsertMaster(master)
          _        <- services.upsertService(service)
          _        <- offers.upsertMasterServiceOffer(offer)
          _        <- masterLocations.upsertMasterLocation(location1)
          _        <- masterLocations.upsertMasterLocation(location2)
          _        <- variants.upsertMasterServiceOfferVariant(variant1)
          _        <- variants.upsertMasterServiceOfferVariant(variant2)
          res      <- variants.getMasterServiceOfferVariantsByOffer(offerId)
          _        <- assertIO(res.toSet == Set(variant1, variant2))
        } yield ()
    }

    "allow creating several variants for one location" in {
      (rnd: Rnd[IO],
        categories: Categories[IO],
        masters: Masters[IO],
        services: Services[IO],
        masterLocations: MasterLocations[IO],
        offers: MasterServiceOffers[IO],
        variants: MasterServiceOfferVariants[IO],
      ) =>
        for {
          categoryId <- rnd[CategoryId]
          masterId   <- rnd[MasterId]
          service1Id <- rnd[ServiceId]
          service2Id <- rnd[ServiceId]
          offer1Id   <- rnd[MasterServiceOfferId]
          offer2Id   <- rnd[MasterServiceOfferId]
          locationId <- rnd[MasterLocationId]
          variant1Id <- rnd[MasterServiceOfferVariantId]
          variant2Id <- rnd[MasterServiceOfferVariantId]
          category    = Category(categoryId, rootCategoryId, 0, s"variants-by-location-category-$categoryId")
          master      = Master(masterId, s"variants-by-location-master-$masterId")
          service1    = Service(service1Id, categoryId, s"variants-by-location-service-a-$service1Id")
          service2    = Service(service2Id, categoryId, s"variants-by-location-service-b-$service2Id")
          offer1      = MasterServiceOffer(offer1Id, masterId, service1Id)
          offer2      = MasterServiceOffer(offer2Id, masterId, service2Id)
          location    = MasterLocation(
            locationId,
            masterId,
            s"variants-shared-location-$locationId",
            s"variants-shared-address-$locationId",
            BigDecimal("50.0000"),
            BigDecimal("60.0000"),
          )
          variant1 <- makeVariant(variant1Id, offer1Id, locationId, BigDecimal("15.0000"), BigDecimal("25.0000"), 30)
          variant2 <- makeVariant(variant2Id, offer2Id, locationId, BigDecimal("35.0000"), BigDecimal("45.0000"), 90)
          _        <- categories.upsertCategory(category)
          _        <- masters.upsertMaster(master)
          _        <- services.upsertService(service1)
          _        <- services.upsertService(service2)
          _        <- offers.upsertMasterServiceOffer(offer1)
          _        <- offers.upsertMasterServiceOffer(offer2)
          _        <- masterLocations.upsertMasterLocation(location)
          _        <- variants.upsertMasterServiceOfferVariant(variant1)
          _        <- variants.upsertMasterServiceOfferVariant(variant2)
          res      <- variants.getMasterServiceOfferVariantsByLocation(locationId)
          _        <- assertIO(res.toSet == Set(variant1, variant2))
        } yield ()
    }

    "return only variants of the requested offer" in {
      (rnd: Rnd[IO],
        categories: Categories[IO],
        masters: Masters[IO],
        services: Services[IO],
        masterLocations: MasterLocations[IO],
        offers: MasterServiceOffers[IO],
        variants: MasterServiceOfferVariants[IO],
      ) =>
        for {
          categoryId  <- rnd[CategoryId]
          masterId    <- rnd[MasterId]
          service1Id  <- rnd[ServiceId]
          service2Id  <- rnd[ServiceId]
          offer1Id    <- rnd[MasterServiceOfferId]
          offer2Id    <- rnd[MasterServiceOfferId]
          location1Id <- rnd[MasterLocationId]
          location2Id <- rnd[MasterLocationId]
          otherLocId  <- rnd[MasterLocationId]
          variant1Id  <- rnd[MasterServiceOfferVariantId]
          variant2Id  <- rnd[MasterServiceOfferVariantId]
          otherId     <- rnd[MasterServiceOfferVariantId]
          category     = Category(categoryId, rootCategoryId, 0, s"variants-filter-offer-category-$categoryId")
          master       = Master(masterId, s"variants-filter-offer-master-$masterId")
          service1     = Service(service1Id, categoryId, s"variants-filter-offer-service-a-$service1Id")
          service2     = Service(service2Id, categoryId, s"variants-filter-offer-service-b-$service2Id")
          offer1       = MasterServiceOffer(offer1Id, masterId, service1Id)
          offer2       = MasterServiceOffer(offer2Id, masterId, service2Id)
          location1    = MasterLocation(
            location1Id,
            masterId,
            s"variants-filter-offer-location-a-$location1Id",
            s"variants-filter-offer-address-a-$location1Id",
            BigDecimal("11.0000"),
            BigDecimal("21.0000"),
          )
          location2 = MasterLocation(
            location2Id,
            masterId,
            s"variants-filter-offer-location-b-$location2Id",
            s"variants-filter-offer-address-b-$location2Id",
            BigDecimal("31.0000"),
            BigDecimal("41.0000"),
          )
          otherLoc = MasterLocation(
            otherLocId,
            masterId,
            s"variants-filter-offer-location-c-$otherLocId",
            s"variants-filter-offer-address-c-$otherLocId",
            BigDecimal("51.0000"),
            BigDecimal("61.0000"),
          )
          variant1 <- makeVariant(variant1Id, offer1Id, location1Id, BigDecimal("11.0000"), BigDecimal("21.0000"), 30)
          variant2 <- makeVariant(variant2Id, offer1Id, location2Id, BigDecimal("31.0000"), BigDecimal("41.0000"), 60)
          other    <- makeVariant(otherId, offer2Id, otherLocId, BigDecimal("51.0000"), BigDecimal("61.0000"), 90)
          _        <- categories.upsertCategory(category)
          _        <- masters.upsertMaster(master)
          _        <- services.upsertService(service1)
          _        <- services.upsertService(service2)
          _        <- offers.upsertMasterServiceOffer(offer1)
          _        <- offers.upsertMasterServiceOffer(offer2)
          _        <- masterLocations.upsertMasterLocation(location1)
          _        <- masterLocations.upsertMasterLocation(location2)
          _        <- masterLocations.upsertMasterLocation(otherLoc)
          _        <- variants.upsertMasterServiceOfferVariant(variant1)
          _        <- variants.upsertMasterServiceOfferVariant(variant2)
          _        <- variants.upsertMasterServiceOfferVariant(other)
          res      <- variants.getMasterServiceOfferVariantsByOffer(offer1Id)
          _        <- assertIO(res.toSet == Set(variant1, variant2))
        } yield ()
    }

    "return only variants of the requested location" in {
      (rnd: Rnd[IO],
        categories: Categories[IO],
        masters: Masters[IO],
        services: Services[IO],
        masterLocations: MasterLocations[IO],
        offers: MasterServiceOffers[IO],
        variants: MasterServiceOfferVariants[IO],
      ) =>
        for {
          categoryId  <- rnd[CategoryId]
          masterId    <- rnd[MasterId]
          service1Id  <- rnd[ServiceId]
          service2Id  <- rnd[ServiceId]
          offer1Id    <- rnd[MasterServiceOfferId]
          offer2Id    <- rnd[MasterServiceOfferId]
          location1Id <- rnd[MasterLocationId]
          location2Id <- rnd[MasterLocationId]
          variant1Id  <- rnd[MasterServiceOfferVariantId]
          variant2Id  <- rnd[MasterServiceOfferVariantId]
          otherId     <- rnd[MasterServiceOfferVariantId]
          category     = Category(categoryId, rootCategoryId, 0, s"variants-filter-location-category-$categoryId")
          master       = Master(masterId, s"variants-filter-location-master-$masterId")
          service1     = Service(service1Id, categoryId, s"variants-filter-location-service-a-$service1Id")
          service2     = Service(service2Id, categoryId, s"variants-filter-location-service-b-$service2Id")
          offer1       = MasterServiceOffer(offer1Id, masterId, service1Id)
          offer2       = MasterServiceOffer(offer2Id, masterId, service2Id)
          location1    = MasterLocation(
            location1Id,
            masterId,
            s"variants-filter-location-a-$location1Id",
            s"variants-filter-location-address-a-$location1Id",
            BigDecimal("13.0000"),
            BigDecimal("23.0000"),
          )
          location2 = MasterLocation(
            location2Id,
            masterId,
            s"variants-filter-location-b-$location2Id",
            s"variants-filter-location-address-b-$location2Id",
            BigDecimal("33.0000"),
            BigDecimal("43.0000"),
          )
          variant1 <- makeVariant(variant1Id, offer1Id, location1Id, BigDecimal("13.0000"), BigDecimal("23.0000"), 30)
          variant2 <- makeVariant(variant2Id, offer2Id, location1Id, BigDecimal("33.0000"), BigDecimal("43.0000"), 60)
          other    <- makeVariant(otherId, offer1Id, location2Id, BigDecimal("53.0000"), BigDecimal("63.0000"), 90)
          _        <- categories.upsertCategory(category)
          _        <- masters.upsertMaster(master)
          _        <- services.upsertService(service1)
          _        <- services.upsertService(service2)
          _        <- offers.upsertMasterServiceOffer(offer1)
          _        <- offers.upsertMasterServiceOffer(offer2)
          _        <- masterLocations.upsertMasterLocation(location1)
          _        <- masterLocations.upsertMasterLocation(location2)
          _        <- variants.upsertMasterServiceOfferVariant(variant1)
          _        <- variants.upsertMasterServiceOfferVariant(variant2)
          _        <- variants.upsertMasterServiceOfferVariant(other)
          res      <- variants.getMasterServiceOfferVariantsByLocation(location1Id)
          _        <- assertIO(res.toSet == Set(variant1, variant2))
        } yield ()
    }

    "return variants by offer sorted by id asc" in {
      (rnd: Rnd[IO],
        categories: Categories[IO],
        masters: Masters[IO],
        services: Services[IO],
        masterLocations: MasterLocations[IO],
        offers: MasterServiceOffers[IO],
        variants: MasterServiceOfferVariants[IO],
      ) =>
        for {
          categoryId  <- rnd[CategoryId]
          masterId    <- rnd[MasterId]
          serviceId   <- rnd[ServiceId]
          offerId     <- rnd[MasterServiceOfferId]
          location1Id <- rnd[MasterLocationId]
          location2Id <- rnd[MasterLocationId]
          location3Id <- rnd[MasterLocationId]
          category     = Category(categoryId, rootCategoryId, 0, s"variants-sort-offer-category-$categoryId")
          master       = Master(masterId, s"variants-sort-offer-master-$masterId")
          service      = Service(serviceId, categoryId, s"variants-sort-offer-service-$serviceId")
          offer        = MasterServiceOffer(offerId, masterId, serviceId)
          location1    = MasterLocation(
            location1Id,
            masterId,
            s"variants-sort-offer-location-a-$location1Id",
            s"variants-sort-offer-address-a-$location1Id",
            BigDecimal("14.0000"),
            BigDecimal("24.0000"),
          )
          location2 = MasterLocation(
            location2Id,
            masterId,
            s"variants-sort-offer-location-b-$location2Id",
            s"variants-sort-offer-address-b-$location2Id",
            BigDecimal("34.0000"),
            BigDecimal("44.0000"),
          )
          location3 = MasterLocation(
            location3Id,
            masterId,
            s"variants-sort-offer-location-c-$location3Id",
            s"variants-sort-offer-address-c-$location3Id",
            BigDecimal("54.0000"),
            BigDecimal("64.0000"),
          )
          id1       = java.util.UUID.fromString("30000000-0000-0000-0000-000000000002")
          id2       = java.util.UUID.fromString("30000000-0000-0000-0000-000000000001")
          id3       = java.util.UUID.fromString("30000000-0000-0000-0000-000000000003")
          variant1 <- makeVariant(id1, offerId, location1Id, BigDecimal("14.0000"), BigDecimal("24.0000"), 30)
          variant2 <- makeVariant(id2, offerId, location2Id, BigDecimal("34.0000"), BigDecimal("44.0000"), 60)
          variant3 <- makeVariant(id3, offerId, location3Id, BigDecimal("54.0000"), BigDecimal("64.0000"), 90)
          _        <- categories.upsertCategory(category)
          _        <- masters.upsertMaster(master)
          _        <- services.upsertService(service)
          _        <- offers.upsertMasterServiceOffer(offer)
          _        <- masterLocations.upsertMasterLocation(location1)
          _        <- masterLocations.upsertMasterLocation(location2)
          _        <- masterLocations.upsertMasterLocation(location3)
          _        <- variants.upsertMasterServiceOfferVariant(variant1)
          _        <- variants.upsertMasterServiceOfferVariant(variant2)
          _        <- variants.upsertMasterServiceOfferVariant(variant3)
          res      <- variants.getMasterServiceOfferVariantsByOffer(offerId)
          _        <- assertIO(res == List(variant2, variant1, variant3))
        } yield ()
    }

    "return variants by location sorted by id asc" in {
      (rnd: Rnd[IO],
        categories: Categories[IO],
        masters: Masters[IO],
        services: Services[IO],
        masterLocations: MasterLocations[IO],
        offers: MasterServiceOffers[IO],
        variants: MasterServiceOfferVariants[IO],
      ) =>
        for {
          categoryId <- rnd[CategoryId]
          masterId   <- rnd[MasterId]
          service1Id <- rnd[ServiceId]
          service2Id <- rnd[ServiceId]
          service3Id <- rnd[ServiceId]
          offer1Id   <- rnd[MasterServiceOfferId]
          offer2Id   <- rnd[MasterServiceOfferId]
          offer3Id   <- rnd[MasterServiceOfferId]
          locationId <- rnd[MasterLocationId]
          category    = Category(categoryId, rootCategoryId, 0, s"variants-sort-location-category-$categoryId")
          master      = Master(masterId, s"variants-sort-location-master-$masterId")
          service1    = Service(service1Id, categoryId, s"variants-sort-location-service-a-$service1Id")
          service2    = Service(service2Id, categoryId, s"variants-sort-location-service-b-$service2Id")
          service3    = Service(service3Id, categoryId, s"variants-sort-location-service-c-$service3Id")
          offer1      = MasterServiceOffer(offer1Id, masterId, service1Id)
          offer2      = MasterServiceOffer(offer2Id, masterId, service2Id)
          offer3      = MasterServiceOffer(offer3Id, masterId, service3Id)
          location    = MasterLocation(
            locationId,
            masterId,
            s"variants-sort-location-$locationId",
            s"variants-sort-location-address-$locationId",
            BigDecimal("15.0000"),
            BigDecimal("25.0000"),
          )
          id1       = java.util.UUID.fromString("40000000-0000-0000-0000-000000000002")
          id2       = java.util.UUID.fromString("40000000-0000-0000-0000-000000000001")
          id3       = java.util.UUID.fromString("40000000-0000-0000-0000-000000000003")
          variant1 <- makeVariant(id1, offer1Id, locationId, BigDecimal("15.0000"), BigDecimal("25.0000"), 30)
          variant2 <- makeVariant(id2, offer2Id, locationId, BigDecimal("35.0000"), BigDecimal("45.0000"), 60)
          variant3 <- makeVariant(id3, offer3Id, locationId, BigDecimal("55.0000"), BigDecimal("65.0000"), 90)
          _        <- categories.upsertCategory(category)
          _        <- masters.upsertMaster(master)
          _        <- services.upsertService(service1)
          _        <- services.upsertService(service2)
          _        <- services.upsertService(service3)
          _        <- offers.upsertMasterServiceOffer(offer1)
          _        <- offers.upsertMasterServiceOffer(offer2)
          _        <- offers.upsertMasterServiceOffer(offer3)
          _        <- masterLocations.upsertMasterLocation(location)
          _        <- variants.upsertMasterServiceOfferVariant(variant1)
          _        <- variants.upsertMasterServiceOfferVariant(variant2)
          _        <- variants.upsertMasterServiceOfferVariant(variant3)
          res      <- variants.getMasterServiceOfferVariantsByLocation(locationId)
          _        <- assertIO(res == List(variant2, variant1, variant3))
        } yield ()
    }

    "upsert overwrites existing variant with same id and replaces additional attributes" in {
      (rnd: Rnd[IO],
        categories: Categories[IO],
        masters: Masters[IO],
        services: Services[IO],
        serviceVariantSchemas: ServiceVariantSchemas[IO],
        masterLocations: MasterLocations[IO],
        offers: MasterServiceOffers[IO],
        variants: MasterServiceOfferVariants[IO],
      ) =>
        for {
          categoryId  <- rnd[CategoryId]
          masterId    <- rnd[MasterId]
          service1Id  <- rnd[ServiceId]
          service2Id  <- rnd[ServiceId]
          offer1Id    <- rnd[MasterServiceOfferId]
          offer2Id    <- rnd[MasterServiceOfferId]
          location1Id <- rnd[MasterLocationId]
          location2Id <- rnd[MasterLocationId]
          variantId   <- rnd[MasterServiceOfferVariantId]
          category     = Category(categoryId, rootCategoryId, 0, s"variants-overwrite-category-$categoryId")
          master       = Master(masterId, s"variants-overwrite-master-$masterId")
          service1     = Service(service1Id, categoryId, s"variants-overwrite-service-a-$service1Id")
          service2     = Service(service2Id, categoryId, s"variants-overwrite-service-b-$service2Id")
          offer1       = MasterServiceOffer(offer1Id, masterId, service1Id)
          offer2       = MasterServiceOffer(offer2Id, masterId, service2Id)
          location1    = MasterLocation(
            location1Id,
            masterId,
            s"variants-overwrite-location-a-$location1Id",
            s"variants-overwrite-address-a-$location1Id",
            BigDecimal("16.0000"),
            BigDecimal("26.0000"),
          )
          location2 = MasterLocation(
            location2Id,
            masterId,
            s"variants-overwrite-location-b-$location2Id",
            s"variants-overwrite-address-b-$location2Id",
            BigDecimal("36.0000"),
            BigDecimal("46.0000"),
          )
          initialSchema = makeSchema(
            service1Id,
            ServiceVariantSchemaItem(AttributeDefinition.SessionCount, false),
            ServiceVariantSchemaItem(AttributeDefinition.DepositAmount, false),
          )
          updatedSchema = makeSchema(
            service2Id,
            ServiceVariantSchemaItem(AttributeDefinition.MaterialsSurcharge, false),
            ServiceVariantSchemaItem(AttributeDefinition.SessionCount, false),
          )
          initial <- makeVariant(
            variantId,
            offer1Id,
            location1Id,
            BigDecimal("16.0000"),
            BigDecimal("26.0000"),
            30,
            intAttributes        = AttributeMap.Impl(Map(AttributeDefinition.SessionCount -> 1)),
            bigDecimalAttributes = AttributeMap.Impl(Map(AttributeDefinition.DepositAmount -> BigDecimal("10.0000"))),
          )
          updated <- makeVariant(
            variantId,
            offer2Id,
            location2Id,
            BigDecimal("36.0000"),
            BigDecimal("46.0000"),
            90,
            bigDecimalAttributes = AttributeMap.Impl(
              Map(
                AttributeDefinition.MaterialsSurcharge -> BigDecimal("12.0000")
              )
            ),
          )
          _   <- categories.upsertCategory(category)
          _   <- masters.upsertMaster(master)
          _   <- services.upsertService(service1)
          _   <- services.upsertService(service2)
          _   <- serviceVariantSchemas.upsertServiceVariantSchema(initialSchema)
          _   <- serviceVariantSchemas.upsertServiceVariantSchema(updatedSchema)
          _   <- offers.upsertMasterServiceOffer(offer1)
          _   <- offers.upsertMasterServiceOffer(offer2)
          _   <- masterLocations.upsertMasterLocation(location1)
          _   <- masterLocations.upsertMasterLocation(location2)
          _   <- variants.upsertMasterServiceOfferVariant(initial)
          _   <- variants.upsertMasterServiceOfferVariant(updated)
          res <- variants.getMasterServiceOfferVariant(variantId)
          _   <- assertIO(res.contains(updated))
        } yield ()
    }

  }

}

abstract class MasterServiceOfferVariantsStorageValidationTest extends LeaderboardTest {
  private def makeSchema(serviceId: ServiceId, items: ServiceVariantSchemaItem*): ServiceVariantSchema =
    ServiceVariantSchema.fromItems(serviceId, items)

  private def enumAttributeMap(
    entries: (EnumAttributeDefinition[?], CodedEnumValue)*
  ): AttributeMap[CodedEnumValue] =
    entries.foldLeft(AttributeMap.Impl[CodedEnumValue, AttributeDefinition[CodedEnumValue]](Map.empty)) {
      case (acc, (definition, value)) =>
        acc.updated(definition.asInstanceOf[AttributeDefinition[CodedEnumValue]], value)
    }

  private def makeVariant(
    id: MasterServiceOfferVariantId,
    masterServiceOfferId: MasterServiceOfferId,
    masterLocationId: MasterLocationId,
    intAttributes: AttributeMap[Int]               = AttributeMap.empty,
    bigDecimalAttributes: AttributeMap[BigDecimal] = AttributeMap.empty,
    enumAttributes: AttributeMap[CodedEnumValue]   = AttributeMap.empty,
  ): IO[QueryFailure, MasterServiceOfferVariant] =
    ZIO
      .fromEither(
        MasterServiceOfferVariant.make(
          id,
          masterServiceOfferId,
          masterLocationId,
          BigDecimal("30.0000"),
          BigDecimal("45.0000"),
          60,
          MasterServiceOfferVariantAttributes(intAttributes, bigDecimalAttributes, enumAttributes),
        )
      )
      .mapError(error => QueryFailure.operation("make-master-service-offer-variant", error.message))

  "MasterServiceOfferVariants numeric attribute storage" should {
    "store Int attributes in the numeric table and load them back as typed Int values" in {
      (rnd: Rnd[IO],
        categories: Categories[IO],
        masters: Masters[IO],
        services: Services[IO],
        serviceVariantSchemas: ServiceVariantSchemas[IO],
        masterLocations: MasterLocations[IO],
        offers: MasterServiceOffers[IO],
        variants: MasterServiceOfferVariants[IO],
        db: SQL[IO],
      ) =>
        for {
          categoryId <- rnd[CategoryId]
          masterId   <- rnd[MasterId]
          serviceId  <- rnd[ServiceId]
          offerId    <- rnd[MasterServiceOfferId]
          locationId <- rnd[MasterLocationId]
          variantId  <- rnd[MasterServiceOfferVariantId]
          category    = Category(categoryId, rootCategoryId, 0, s"numeric-int-category-$categoryId")
          master      = Master(masterId, s"numeric-int-master-$masterId")
          service     = Service(serviceId, categoryId, s"numeric-int-service-$serviceId")
          offer       = MasterServiceOffer(offerId, masterId, serviceId)
          location    = MasterLocation(locationId, masterId, s"numeric-int-location-$locationId", s"numeric-int-address-$locationId", BigDecimal("1.0000"), BigDecimal("2.0000"))
          schema      = makeSchema(serviceId, ServiceVariantSchemaItem(AttributeDefinition.SessionCount, false))
          variant     <- makeVariant(variantId, offerId, locationId, intAttributes = AttributeMap.Impl(Map(AttributeDefinition.SessionCount -> 3)))
          _           <- categories.upsertCategory(category)
          _           <- masters.upsertMaster(master)
          _           <- services.upsertService(service)
          _           <- serviceVariantSchemas.upsertServiceVariantSchema(schema)
          _           <- offers.upsertMasterServiceOffer(offer)
          _           <- masterLocations.upsertMasterLocation(location)
          _           <- variants.upsertMasterServiceOfferVariant(variant)
          loaded      <- variants.getMasterServiceOfferVariant(variantId)
          storedRows  <- db.execute("count-master-service-offer-variant-numeric-attributes-int") {
                           sql"""select count(*)
                                from master_service_offer_variant_numeric_attributes
                                where master_service_offer_variant_id = $variantId
                              """.query[Long].unique
                         }
          _ <- assertIO(loaded.flatMap(_.intAttributes.get(AttributeDefinition.SessionCount)).contains(3))
          _ <- assertIO(storedRows == 1L)
        } yield ()
    }

    "store BigDecimal attributes in the numeric table and load them back as typed BigDecimal values" in {
      (rnd: Rnd[IO],
        categories: Categories[IO],
        masters: Masters[IO],
        services: Services[IO],
        serviceVariantSchemas: ServiceVariantSchemas[IO],
        masterLocations: MasterLocations[IO],
        offers: MasterServiceOffers[IO],
        variants: MasterServiceOfferVariants[IO],
        db: SQL[IO],
      ) =>
        for {
          categoryId <- rnd[CategoryId]
          masterId   <- rnd[MasterId]
          serviceId  <- rnd[ServiceId]
          offerId    <- rnd[MasterServiceOfferId]
          locationId <- rnd[MasterLocationId]
          variantId  <- rnd[MasterServiceOfferVariantId]
          category    = Category(categoryId, rootCategoryId, 0, s"numeric-decimal-category-$categoryId")
          master      = Master(masterId, s"numeric-decimal-master-$masterId")
          service     = Service(serviceId, categoryId, s"numeric-decimal-service-$serviceId")
          offer       = MasterServiceOffer(offerId, masterId, serviceId)
          location    = MasterLocation(locationId, masterId, s"numeric-decimal-location-$locationId", s"numeric-decimal-address-$locationId", BigDecimal("3.0000"), BigDecimal("4.0000"))
          schema      = makeSchema(serviceId, ServiceVariantSchemaItem(AttributeDefinition.DepositAmount, false))
          variant     <- makeVariant(
                           variantId,
                           offerId,
                           locationId,
                           bigDecimalAttributes = AttributeMap.Impl(Map(AttributeDefinition.DepositAmount -> BigDecimal("25.5000"))),
                         )
          _          <- categories.upsertCategory(category)
          _          <- masters.upsertMaster(master)
          _          <- services.upsertService(service)
          _          <- serviceVariantSchemas.upsertServiceVariantSchema(schema)
          _          <- offers.upsertMasterServiceOffer(offer)
          _          <- masterLocations.upsertMasterLocation(location)
          _          <- variants.upsertMasterServiceOfferVariant(variant)
          loaded     <- variants.getMasterServiceOfferVariant(variantId)
          storedRows <- db.execute("count-master-service-offer-variant-numeric-attributes-decimal") {
                          sql"""select count(*)
                               from master_service_offer_variant_numeric_attributes
                               where master_service_offer_variant_id = $variantId
                             """.query[Long].unique
                        }
          _ <- assertIO(loaded.flatMap(_.bigDecimalAttributes.get(AttributeDefinition.DepositAmount)).contains(BigDecimal("25.5000")))
          _ <- assertIO(storedRows == 1L)
        } yield ()
    }

    "store mixed Int and BigDecimal attributes in one numeric table and restore separate typed maps" in {
      (rnd: Rnd[IO],
        categories: Categories[IO],
        masters: Masters[IO],
        services: Services[IO],
        serviceVariantSchemas: ServiceVariantSchemas[IO],
        masterLocations: MasterLocations[IO],
        offers: MasterServiceOffers[IO],
        variants: MasterServiceOfferVariants[IO],
        db: SQL[IO],
      ) =>
        for {
          categoryId <- rnd[CategoryId]
          masterId   <- rnd[MasterId]
          serviceId  <- rnd[ServiceId]
          offerId    <- rnd[MasterServiceOfferId]
          locationId <- rnd[MasterLocationId]
          variantId  <- rnd[MasterServiceOfferVariantId]
          category    = Category(categoryId, rootCategoryId, 0, s"numeric-mixed-category-$categoryId")
          master      = Master(masterId, s"numeric-mixed-master-$masterId")
          service     = Service(serviceId, categoryId, s"numeric-mixed-service-$serviceId")
          offer       = MasterServiceOffer(offerId, masterId, serviceId)
          location    = MasterLocation(locationId, masterId, s"numeric-mixed-location-$locationId", s"numeric-mixed-address-$locationId", BigDecimal("5.0000"), BigDecimal("6.0000"))
          schema = makeSchema(
            serviceId,
            ServiceVariantSchemaItem(AttributeDefinition.SessionCount, false),
            ServiceVariantSchemaItem(AttributeDefinition.DepositAmount, false),
          )
          variant <- makeVariant(
            variantId,
            offerId,
            locationId,
            intAttributes        = AttributeMap.Impl(Map(AttributeDefinition.SessionCount -> 3)),
            bigDecimalAttributes = AttributeMap.Impl(Map(AttributeDefinition.DepositAmount -> BigDecimal("25.5000"))),
          )
          _          <- categories.upsertCategory(category)
          _          <- masters.upsertMaster(master)
          _          <- services.upsertService(service)
          _          <- serviceVariantSchemas.upsertServiceVariantSchema(schema)
          _          <- offers.upsertMasterServiceOffer(offer)
          _          <- masterLocations.upsertMasterLocation(location)
          _          <- variants.upsertMasterServiceOfferVariant(variant)
          loaded     <- variants.getMasterServiceOfferVariant(variantId)
          storedRows <- db.execute("select-master-service-offer-variant-numeric-attributes-mixed") {
                          sql"""select attribute_code, value
                               from master_service_offer_variant_numeric_attributes
                               where master_service_offer_variant_id = $variantId
                               order by attribute_code asc
                             """.query[(String, BigDecimal)].to[List]
                        }
          _ <- assertIO(loaded.flatMap(_.intAttributes.get(AttributeDefinition.SessionCount)).contains(3))
          _ <- assertIO(loaded.flatMap(_.bigDecimalAttributes.get(AttributeDefinition.DepositAmount)).contains(BigDecimal("25.5000")))
          _ <- assertIO(
            storedRows == List(
              "deposit_amount" -> BigDecimal("25.5000"),
              "session_count"  -> BigDecimal("3"),
            )
          )
        } yield ()
    }

    "store HairRemovalMethod enum attribute in numeric table and load it back as typed enum value" in {
      (rnd: Rnd[IO],
        categories: Categories[IO],
        masters: Masters[IO],
        services: Services[IO],
        serviceVariantSchemas: ServiceVariantSchemas[IO],
        masterLocations: MasterLocations[IO],
        offers: MasterServiceOffers[IO],
        variants: MasterServiceOfferVariants[IO],
        db: SQL[IO],
      ) =>
        for {
          categoryId <- rnd[CategoryId]
          masterId   <- rnd[MasterId]
          serviceId  <- rnd[ServiceId]
          offerId    <- rnd[MasterServiceOfferId]
          locationId <- rnd[MasterLocationId]
          variantId  <- rnd[MasterServiceOfferVariantId]
          category    = Category(categoryId, rootCategoryId, 0, s"numeric-enum-hair-category-$categoryId")
          master      = Master(masterId, s"numeric-enum-hair-master-$masterId")
          service     = Service(serviceId, categoryId, s"numeric-enum-hair-service-$serviceId")
          offer       = MasterServiceOffer(offerId, masterId, serviceId)
          location    = MasterLocation(locationId, masterId, s"numeric-enum-hair-location-$locationId", s"numeric-enum-hair-address-$locationId", BigDecimal("15.0000"), BigDecimal("16.0000"))
          schema      = makeSchema(serviceId, ServiceVariantSchemaItem(AttributeDefinition.HairRemovalMethodAttribute, false))
          variant     <- makeVariant(
                           variantId,
                           offerId,
                           locationId,
                           enumAttributes = enumAttributeMap(
                             AttributeDefinition.HairRemovalMethodAttribute -> HairRemovalMethod.Sugaring
                           ),
                         )
          _ <- categories.upsertCategory(category)
          _ <- masters.upsertMaster(master)
          _ <- services.upsertService(service)
          _ <- serviceVariantSchemas.upsertServiceVariantSchema(schema)
          _ <- offers.upsertMasterServiceOffer(offer)
          _ <- masterLocations.upsertMasterLocation(location)
          _ <- variants.upsertMasterServiceOfferVariant(variant)
          loaded <- variants.getMasterServiceOfferVariant(variantId)
          storedValue <- db.execute("select-master-service-offer-variant-hair-removal-method") {
                           sql"""select value
                                from master_service_offer_variant_numeric_attributes
                                where master_service_offer_variant_id = $variantId
                                  and attribute_code = ${AttributeDefinition.HairRemovalMethodAttribute.code}
                              """.query[BigDecimal].unique
                         }
          _ <- assertIO(loaded.flatMap(_.enumAttributes.get(AttributeDefinition.HairRemovalMethodAttribute)).contains(HairRemovalMethod.Sugaring))
          _ <- assertIO(storedValue == BigDecimal("2"))
        } yield ()
    }

    "store NailCoatingType enum attribute in numeric table and load it back as typed enum value" in {
      (rnd: Rnd[IO],
        categories: Categories[IO],
        masters: Masters[IO],
        services: Services[IO],
        serviceVariantSchemas: ServiceVariantSchemas[IO],
        masterLocations: MasterLocations[IO],
        offers: MasterServiceOffers[IO],
        variants: MasterServiceOfferVariants[IO],
        db: SQL[IO],
      ) =>
        for {
          categoryId <- rnd[CategoryId]
          masterId   <- rnd[MasterId]
          serviceId  <- rnd[ServiceId]
          offerId    <- rnd[MasterServiceOfferId]
          locationId <- rnd[MasterLocationId]
          variantId  <- rnd[MasterServiceOfferVariantId]
          category    = Category(categoryId, rootCategoryId, 0, s"numeric-enum-nail-category-$categoryId")
          master      = Master(masterId, s"numeric-enum-nail-master-$masterId")
          service     = Service(serviceId, categoryId, s"numeric-enum-nail-service-$serviceId")
          offer       = MasterServiceOffer(offerId, masterId, serviceId)
          location    = MasterLocation(locationId, masterId, s"numeric-enum-nail-location-$locationId", s"numeric-enum-nail-address-$locationId", BigDecimal("17.0000"), BigDecimal("18.0000"))
          schema      = makeSchema(serviceId, ServiceVariantSchemaItem(AttributeDefinition.NailCoatingTypeAttribute, false))
          variant     <- makeVariant(
                           variantId,
                           offerId,
                           locationId,
                           enumAttributes = enumAttributeMap(
                             AttributeDefinition.NailCoatingTypeAttribute -> NailCoatingType.GelPolish
                           ),
                         )
          _ <- categories.upsertCategory(category)
          _ <- masters.upsertMaster(master)
          _ <- services.upsertService(service)
          _ <- serviceVariantSchemas.upsertServiceVariantSchema(schema)
          _ <- offers.upsertMasterServiceOffer(offer)
          _ <- masterLocations.upsertMasterLocation(location)
          _ <- variants.upsertMasterServiceOfferVariant(variant)
          loaded <- variants.getMasterServiceOfferVariant(variantId)
          storedValue <- db.execute("select-master-service-offer-variant-nail-coating-type") {
                           sql"""select value
                                from master_service_offer_variant_numeric_attributes
                                where master_service_offer_variant_id = $variantId
                                  and attribute_code = ${AttributeDefinition.NailCoatingTypeAttribute.code}
                              """.query[BigDecimal].unique
                         }
          _ <- assertIO(loaded.flatMap(_.enumAttributes.get(AttributeDefinition.NailCoatingTypeAttribute)).contains(NailCoatingType.GelPolish))
          _ <- assertIO(storedValue == BigDecimal("3"))
        } yield ()
    }

    "store mixed Int, BigDecimal, and enum attributes in one numeric table and restore typed maps" in {
      (rnd: Rnd[IO],
        categories: Categories[IO],
        masters: Masters[IO],
        services: Services[IO],
        serviceVariantSchemas: ServiceVariantSchemas[IO],
        masterLocations: MasterLocations[IO],
        offers: MasterServiceOffers[IO],
        variants: MasterServiceOfferVariants[IO],
        db: SQL[IO],
      ) =>
        for {
          categoryId <- rnd[CategoryId]
          masterId   <- rnd[MasterId]
          serviceId  <- rnd[ServiceId]
          offerId    <- rnd[MasterServiceOfferId]
          locationId <- rnd[MasterLocationId]
          variantId  <- rnd[MasterServiceOfferVariantId]
          category    = Category(categoryId, rootCategoryId, 0, s"numeric-mixed-all-category-$categoryId")
          master      = Master(masterId, s"numeric-mixed-all-master-$masterId")
          service     = Service(serviceId, categoryId, s"numeric-mixed-all-service-$serviceId")
          offer       = MasterServiceOffer(offerId, masterId, serviceId)
          location    = MasterLocation(locationId, masterId, s"numeric-mixed-all-location-$locationId", s"numeric-mixed-all-address-$locationId", BigDecimal("19.0000"), BigDecimal("20.0000"))
          schema = makeSchema(
            serviceId,
            ServiceVariantSchemaItem(AttributeDefinition.SessionCount, false),
            ServiceVariantSchemaItem(AttributeDefinition.DepositAmount, false),
            ServiceVariantSchemaItem(AttributeDefinition.HairRemovalMethodAttribute, false),
            ServiceVariantSchemaItem(AttributeDefinition.NailCoatingTypeAttribute, false),
          )
          variant <- makeVariant(
            variantId,
            offerId,
            locationId,
            intAttributes        = AttributeMap.Impl(Map(AttributeDefinition.SessionCount -> 3)),
            bigDecimalAttributes = AttributeMap.Impl(Map(AttributeDefinition.DepositAmount -> BigDecimal("25.5000"))),
            enumAttributes = enumAttributeMap(
              AttributeDefinition.HairRemovalMethodAttribute -> HairRemovalMethod.Sugaring,
              AttributeDefinition.NailCoatingTypeAttribute   -> NailCoatingType.GelPolish,
            ),
          )
          _ <- categories.upsertCategory(category)
          _ <- masters.upsertMaster(master)
          _ <- services.upsertService(service)
          _ <- serviceVariantSchemas.upsertServiceVariantSchema(schema)
          _ <- offers.upsertMasterServiceOffer(offer)
          _ <- masterLocations.upsertMasterLocation(location)
          _ <- variants.upsertMasterServiceOfferVariant(variant)
          loaded <- variants.getMasterServiceOfferVariant(variantId)
          storedRows <- db.execute("select-master-service-offer-variant-numeric-attributes-mixed-all") {
                          sql"""select attribute_code, value
                               from master_service_offer_variant_numeric_attributes
                               where master_service_offer_variant_id = $variantId
                               order by attribute_code asc
                             """.query[(String, BigDecimal)].to[List]
                        }
          _ <- assertIO(loaded.flatMap(_.intAttributes.get(AttributeDefinition.SessionCount)).contains(3))
          _ <- assertIO(loaded.flatMap(_.bigDecimalAttributes.get(AttributeDefinition.DepositAmount)).contains(BigDecimal("25.5000")))
          _ <- assertIO(loaded.flatMap(_.enumAttributes.get(AttributeDefinition.HairRemovalMethodAttribute)).contains(HairRemovalMethod.Sugaring))
          _ <- assertIO(loaded.flatMap(_.enumAttributes.get(AttributeDefinition.NailCoatingTypeAttribute)).contains(NailCoatingType.GelPolish))
          _ <- assertIO(
            storedRows == List(
              "deposit_amount"       -> BigDecimal("25.5000"),
              "hair_removal_method"  -> BigDecimal("2"),
              "nail_coating_type"    -> BigDecimal("3"),
              "session_count"        -> BigDecimal("3"),
            )
          )
        } yield ()
    }

    "store and load several new enum attributes through numeric table" in {
      (rnd: Rnd[IO],
        categories: Categories[IO],
        masters: Masters[IO],
        services: Services[IO],
        serviceVariantSchemas: ServiceVariantSchemas[IO],
        masterLocations: MasterLocations[IO],
        offers: MasterServiceOffers[IO],
        variants: MasterServiceOfferVariants[IO],
        db: SQL[IO],
      ) =>
        for {
          categoryId <- rnd[CategoryId]
          masterId   <- rnd[MasterId]
          serviceId  <- rnd[ServiceId]
          offerId    <- rnd[MasterServiceOfferId]
          locationId <- rnd[MasterLocationId]
          variantId  <- rnd[MasterServiceOfferVariantId]
          category    = Category(categoryId, rootCategoryId, 0, s"numeric-new-enums-category-$categoryId")
          master      = Master(masterId, s"numeric-new-enums-master-$masterId")
          service     = Service(serviceId, categoryId, s"numeric-new-enums-service-$serviceId")
          offer       = MasterServiceOffer(offerId, masterId, serviceId)
          location    = MasterLocation(locationId, masterId, s"numeric-new-enums-location-$locationId", s"numeric-new-enums-address-$locationId", BigDecimal("21.0000"), BigDecimal("22.0000"))
          schema = makeSchema(
            serviceId,
            ServiceVariantSchemaItem(AttributeDefinition.NailServiceTypeAttribute, false),
            ServiceVariantSchemaItem(AttributeDefinition.LashServiceTypeAttribute, false),
            ServiceVariantSchemaItem(AttributeDefinition.LashVolumeAttribute, false),
            ServiceVariantSchemaItem(AttributeDefinition.BrowServiceTypeAttribute, false),
            ServiceVariantSchemaItem(AttributeDefinition.PmuAreaAttribute, false),
            ServiceVariantSchemaItem(AttributeDefinition.FacialTreatmentTypeAttribute, false),
            ServiceVariantSchemaItem(AttributeDefinition.BodyAreaAttribute, false),
          )
          variant <- makeVariant(
            variantId,
            offerId,
            locationId,
            enumAttributes = enumAttributeMap(
              AttributeDefinition.NailServiceTypeAttribute     -> NailServiceType.Manicure,
              AttributeDefinition.LashServiceTypeAttribute     -> LashServiceType.Extension,
              AttributeDefinition.LashVolumeAttribute          -> LashVolume.Volume2D,
              AttributeDefinition.BrowServiceTypeAttribute     -> BrowServiceType.Lamination,
              AttributeDefinition.PmuAreaAttribute             -> PmuArea.Brows,
              AttributeDefinition.FacialTreatmentTypeAttribute -> FacialTreatmentType.Microneedling,
              AttributeDefinition.BodyAreaAttribute            -> BodyArea.UpperLip,
            ),
          )
          _ <- categories.upsertCategory(category)
          _ <- masters.upsertMaster(master)
          _ <- services.upsertService(service)
          _ <- serviceVariantSchemas.upsertServiceVariantSchema(schema)
          _ <- offers.upsertMasterServiceOffer(offer)
          _ <- masterLocations.upsertMasterLocation(location)
          _ <- variants.upsertMasterServiceOfferVariant(variant)
          loaded <- variants.getMasterServiceOfferVariant(variantId)
          storedRows <- db.execute("select-master-service-offer-variant-numeric-attributes-new-enums") {
                          sql"""select attribute_code, value
                               from master_service_offer_variant_numeric_attributes
                               where master_service_offer_variant_id = $variantId
                               order by attribute_code asc
                             """.query[(String, BigDecimal)].to[List]
                        }
          _ <- assertIO(loaded.flatMap(_.enumAttributes.get(AttributeDefinition.NailServiceTypeAttribute)).contains(NailServiceType.Manicure))
          _ <- assertIO(loaded.flatMap(_.enumAttributes.get(AttributeDefinition.LashServiceTypeAttribute)).contains(LashServiceType.Extension))
          _ <- assertIO(loaded.flatMap(_.enumAttributes.get(AttributeDefinition.LashVolumeAttribute)).contains(LashVolume.Volume2D))
          _ <- assertIO(loaded.flatMap(_.enumAttributes.get(AttributeDefinition.BrowServiceTypeAttribute)).contains(BrowServiceType.Lamination))
          _ <- assertIO(loaded.flatMap(_.enumAttributes.get(AttributeDefinition.PmuAreaAttribute)).contains(PmuArea.Brows))
          _ <- assertIO(loaded.flatMap(_.enumAttributes.get(AttributeDefinition.FacialTreatmentTypeAttribute)).contains(FacialTreatmentType.Microneedling))
          _ <- assertIO(loaded.flatMap(_.enumAttributes.get(AttributeDefinition.BodyAreaAttribute)).contains(BodyArea.UpperLip))
          _ <- assertIO(
            storedRows == List(
              "body_area"             -> BigDecimal("1"),
              "brow_service_type"     -> BigDecimal("3"),
              "facial_treatment_type" -> BigDecimal("6"),
              "lash_service_type"     -> BigDecimal("1"),
              "lash_volume"           -> BigDecimal("2"),
              "nail_service_type"     -> BigDecimal("1"),
              "pmu_area"              -> BigDecimal("1"),
            )
          )
        } yield ()
    }

    "loadMany keeps numeric attributes grouped by variant id" in {
      (rnd: Rnd[IO],
        categories: Categories[IO],
        masters: Masters[IO],
        services: Services[IO],
        serviceVariantSchemas: ServiceVariantSchemas[IO],
        masterLocations: MasterLocations[IO],
        offers: MasterServiceOffers[IO],
        variants: MasterServiceOfferVariants[IO],
      ) =>
        for {
          categoryId <- rnd[CategoryId]
          masterId   <- rnd[MasterId]
          serviceId  <- rnd[ServiceId]
          offerId    <- rnd[MasterServiceOfferId]
          location1Id <- rnd[MasterLocationId]
          location2Id <- rnd[MasterLocationId]
          variant1Id  <- rnd[MasterServiceOfferVariantId]
          variant2Id  <- rnd[MasterServiceOfferVariantId]
          category     = Category(categoryId, rootCategoryId, 0, s"numeric-loadmany-category-$categoryId")
          master       = Master(masterId, s"numeric-loadmany-master-$masterId")
          service      = Service(serviceId, categoryId, s"numeric-loadmany-service-$serviceId")
          offer        = MasterServiceOffer(offerId, masterId, serviceId)
          location1    = MasterLocation(location1Id, masterId, s"numeric-loadmany-location-a-$location1Id", s"numeric-loadmany-address-a-$location1Id", BigDecimal("7.0000"), BigDecimal("8.0000"))
          location2    = MasterLocation(location2Id, masterId, s"numeric-loadmany-location-b-$location2Id", s"numeric-loadmany-address-b-$location2Id", BigDecimal("9.0000"), BigDecimal("10.0000"))
          schema = makeSchema(
            serviceId,
            ServiceVariantSchemaItem(AttributeDefinition.SessionCount, false),
            ServiceVariantSchemaItem(AttributeDefinition.DepositAmount, false),
          )
          variant1 <- makeVariant(
            variant1Id,
            offerId,
            location1Id,
            intAttributes        = AttributeMap.Impl(Map(AttributeDefinition.SessionCount -> 3)),
            bigDecimalAttributes = AttributeMap.Impl(Map(AttributeDefinition.DepositAmount -> BigDecimal("25.5000"))),
          )
          variant2 <- makeVariant(
            variant2Id,
            offerId,
            location2Id,
            intAttributes        = AttributeMap.Impl(Map(AttributeDefinition.SessionCount -> 7)),
            bigDecimalAttributes = AttributeMap.Impl(Map(AttributeDefinition.DepositAmount -> BigDecimal("41.2500"))),
          )
          _   <- categories.upsertCategory(category)
          _   <- masters.upsertMaster(master)
          _   <- services.upsertService(service)
          _   <- serviceVariantSchemas.upsertServiceVariantSchema(schema)
          _   <- offers.upsertMasterServiceOffer(offer)
          _   <- masterLocations.upsertMasterLocation(location1)
          _   <- masterLocations.upsertMasterLocation(location2)
          _   <- variants.upsertMasterServiceOfferVariant(variant1)
          _   <- variants.upsertMasterServiceOfferVariant(variant2)
          res <- variants.getMasterServiceOfferVariantsByOffer(offerId)
          _   <- assertIO(res.toSet == Set(variant1, variant2))
        } yield ()
    }

    "reject non-integer stored numeric value for an Int attribute definition" in {
      (rnd: Rnd[IO],
        categories: Categories[IO],
        masters: Masters[IO],
        services: Services[IO],
        serviceVariantSchemas: ServiceVariantSchemas[IO],
        masterLocations: MasterLocations[IO],
        offers: MasterServiceOffers[IO],
        variants: MasterServiceOfferVariants[IO],
        db: SQL[IO],
      ) =>
        for {
          categoryId <- rnd[CategoryId]
          masterId   <- rnd[MasterId]
          serviceId  <- rnd[ServiceId]
          offerId    <- rnd[MasterServiceOfferId]
          locationId <- rnd[MasterLocationId]
          variantId  <- rnd[MasterServiceOfferVariantId]
          category    = Category(categoryId, rootCategoryId, 0, s"numeric-invalid-int-category-$categoryId")
          master      = Master(masterId, s"numeric-invalid-int-master-$masterId")
          service     = Service(serviceId, categoryId, s"numeric-invalid-int-service-$serviceId")
          offer       = MasterServiceOffer(offerId, masterId, serviceId)
          location    = MasterLocation(locationId, masterId, s"numeric-invalid-int-location-$locationId", s"numeric-invalid-int-address-$locationId", BigDecimal("11.0000"), BigDecimal("12.0000"))
          schema      = makeSchema(serviceId, ServiceVariantSchemaItem(AttributeDefinition.SessionCount, false))
          variant     <- makeVariant(variantId, offerId, locationId)
          _           <- categories.upsertCategory(category)
          _           <- masters.upsertMaster(master)
          _           <- services.upsertService(service)
          _           <- serviceVariantSchemas.upsertServiceVariantSchema(schema)
          _           <- offers.upsertMasterServiceOffer(offer)
          _           <- masterLocations.upsertMasterLocation(location)
          _           <- variants.upsertMasterServiceOfferVariant(variant)
          _           <- db.execute("insert-invalid-master-service-offer-variant-numeric-attribute") {
                           sql"""insert into master_service_offer_variant_numeric_attributes (
                                |  master_service_offer_variant_id,
                                |  attribute_code,
                                |  value
                                |)
                                |values (
                                |  $variantId,
                                |  ${AttributeDefinition.SessionCount.code},
                                |  ${BigDecimal("3.5")}
                                |)
                                |on conflict (master_service_offer_variant_id, attribute_code) do update set
                                |  value = excluded.value
                                |""".stripMargin.update.run
                         }
          result <- variants.getMasterServiceOfferVariant(variantId).either
          _      <- assertIO(
                      result.left.exists {
                        case QueryFailure.OperationFailure(operationName, message) =>
                          operationName == "load-master-service-offer-variant-attributes" &&
                          message == s"MasterServiceOfferVariant attribute ${AttributeDefinition.SessionCount.code} expected Int-compatible numeric value but got non-integer numeric value: 3.5"
                        case _ =>
                          false
                      }
                    )
        } yield ()
    }

    "fail to load Int attribute when numeric value is outside Int range" in {
      (rnd: Rnd[IO],
        categories: Categories[IO],
        masters: Masters[IO],
        services: Services[IO],
        serviceVariantSchemas: ServiceVariantSchemas[IO],
        masterLocations: MasterLocations[IO],
        offers: MasterServiceOffers[IO],
        variants: MasterServiceOfferVariants[IO],
        db: SQL[IO],
      ) =>
        for {
          categoryId <- rnd[CategoryId]
          masterId   <- rnd[MasterId]
          serviceId  <- rnd[ServiceId]
          offerId    <- rnd[MasterServiceOfferId]
          locationId <- rnd[MasterLocationId]
          variantId  <- rnd[MasterServiceOfferVariantId]
          category    = Category(categoryId, rootCategoryId, 0, s"numeric-out-of-range-category-$categoryId")
          master      = Master(masterId, s"numeric-out-of-range-master-$masterId")
          service     = Service(serviceId, categoryId, s"numeric-out-of-range-service-$serviceId")
          offer       = MasterServiceOffer(offerId, masterId, serviceId)
          location    = MasterLocation(locationId, masterId, s"numeric-out-of-range-location-$locationId", s"numeric-out-of-range-address-$locationId", BigDecimal("13.0000"), BigDecimal("14.0000"))
          schema      = makeSchema(serviceId, ServiceVariantSchemaItem(AttributeDefinition.SessionCount, false))
          variant     <- makeVariant(variantId, offerId, locationId)
          _           <- categories.upsertCategory(category)
          _           <- masters.upsertMaster(master)
          _           <- services.upsertService(service)
          _           <- serviceVariantSchemas.upsertServiceVariantSchema(schema)
          _           <- offers.upsertMasterServiceOffer(offer)
          _           <- masterLocations.upsertMasterLocation(location)
          _           <- variants.upsertMasterServiceOfferVariant(variant)
          _           <- db.execute("insert-out-of-range-master-service-offer-variant-numeric-attribute") {
                           sql"""insert into master_service_offer_variant_numeric_attributes (
                                |  master_service_offer_variant_id,
                                |  attribute_code,
                                |  value
                                |)
                                |values (
                                |  $variantId,
                                |  ${AttributeDefinition.SessionCount.code},
                                |  ${BigDecimal("2147483648")}
                                |)
                                |on conflict (master_service_offer_variant_id, attribute_code) do update set
                                |  value = excluded.value
                                |""".stripMargin.update.run
                         }
          result <- variants.getMasterServiceOfferVariant(variantId).either
          _      <- assertIO(
                      result.left.exists {
                        case QueryFailure.OperationFailure(operationName, message) =>
                          operationName == "load-master-service-offer-variant-attributes" &&
                          message.contains(AttributeDefinition.SessionCount.code) &&
                          message.contains("Int-compatible numeric value") &&
                          message.contains("outside Int range") &&
                          message.contains("2147483648")
                        case _ =>
                          false
                      }
                    )
        } yield ()
    }

    "reject unknown enum int code from numeric storage" in {
      (rnd: Rnd[IO],
        categories: Categories[IO],
        masters: Masters[IO],
        services: Services[IO],
        serviceVariantSchemas: ServiceVariantSchemas[IO],
        masterLocations: MasterLocations[IO],
        offers: MasterServiceOffers[IO],
        variants: MasterServiceOfferVariants[IO],
        db: SQL[IO],
      ) =>
        for {
          categoryId <- rnd[CategoryId]
          masterId   <- rnd[MasterId]
          serviceId  <- rnd[ServiceId]
          offerId    <- rnd[MasterServiceOfferId]
          locationId <- rnd[MasterLocationId]
          variantId  <- rnd[MasterServiceOfferVariantId]
          category    = Category(categoryId, rootCategoryId, 0, s"numeric-unknown-enum-code-category-$categoryId")
          master      = Master(masterId, s"numeric-unknown-enum-code-master-$masterId")
          service     = Service(serviceId, categoryId, s"numeric-unknown-enum-code-service-$serviceId")
          offer       = MasterServiceOffer(offerId, masterId, serviceId)
          location    = MasterLocation(locationId, masterId, s"numeric-unknown-enum-code-location-$locationId", s"numeric-unknown-enum-code-address-$locationId", BigDecimal("21.0000"), BigDecimal("22.0000"))
          schema      = makeSchema(serviceId, ServiceVariantSchemaItem(AttributeDefinition.HairRemovalMethodAttribute, false))
          variant     <- makeVariant(variantId, offerId, locationId)
          _           <- categories.upsertCategory(category)
          _           <- masters.upsertMaster(master)
          _           <- services.upsertService(service)
          _           <- serviceVariantSchemas.upsertServiceVariantSchema(schema)
          _           <- offers.upsertMasterServiceOffer(offer)
          _           <- masterLocations.upsertMasterLocation(location)
          _           <- variants.upsertMasterServiceOfferVariant(variant)
          _           <- db.execute("insert-unknown-enum-code-master-service-offer-variant-numeric-attribute") {
                           sql"""insert into master_service_offer_variant_numeric_attributes (
                                |  master_service_offer_variant_id,
                                |  attribute_code,
                                |  value
                                |)
                                |values (
                                |  $variantId,
                                |  ${AttributeDefinition.HairRemovalMethodAttribute.code},
                                |  ${BigDecimal("999")}
                                |)
                                |on conflict (master_service_offer_variant_id, attribute_code) do update set
                                |  value = excluded.value
                                |""".stripMargin.update.run
                         }
          result <- variants.getMasterServiceOfferVariant(variantId).either
          _      <- assertIO(
                      result.left.exists {
                        case QueryFailure.OperationFailure(operationName, message) =>
                          operationName == "load-master-service-offer-variant-attributes" &&
                          message.contains(AttributeDefinition.HairRemovalMethodAttribute.code) &&
                          message.contains("unknown enum int code") &&
                          message.contains("999")
                        case _ =>
                          false
                      }
                    )
        } yield ()
    }

    "reject non-integer numeric value for enum attribute definition" in {
      (rnd: Rnd[IO],
        categories: Categories[IO],
        masters: Masters[IO],
        services: Services[IO],
        serviceVariantSchemas: ServiceVariantSchemas[IO],
        masterLocations: MasterLocations[IO],
        offers: MasterServiceOffers[IO],
        variants: MasterServiceOfferVariants[IO],
        db: SQL[IO],
      ) =>
        for {
          categoryId <- rnd[CategoryId]
          masterId   <- rnd[MasterId]
          serviceId  <- rnd[ServiceId]
          offerId    <- rnd[MasterServiceOfferId]
          locationId <- rnd[MasterLocationId]
          variantId  <- rnd[MasterServiceOfferVariantId]
          category    = Category(categoryId, rootCategoryId, 0, s"numeric-non-integer-enum-category-$categoryId")
          master      = Master(masterId, s"numeric-non-integer-enum-master-$masterId")
          service     = Service(serviceId, categoryId, s"numeric-non-integer-enum-service-$serviceId")
          offer       = MasterServiceOffer(offerId, masterId, serviceId)
          location    = MasterLocation(locationId, masterId, s"numeric-non-integer-enum-location-$locationId", s"numeric-non-integer-enum-address-$locationId", BigDecimal("23.0000"), BigDecimal("24.0000"))
          schema      = makeSchema(serviceId, ServiceVariantSchemaItem(AttributeDefinition.HairRemovalMethodAttribute, false))
          variant     <- makeVariant(variantId, offerId, locationId)
          _           <- categories.upsertCategory(category)
          _           <- masters.upsertMaster(master)
          _           <- services.upsertService(service)
          _           <- serviceVariantSchemas.upsertServiceVariantSchema(schema)
          _           <- offers.upsertMasterServiceOffer(offer)
          _           <- masterLocations.upsertMasterLocation(location)
          _           <- variants.upsertMasterServiceOfferVariant(variant)
          _           <- db.execute("insert-non-integer-enum-master-service-offer-variant-numeric-attribute") {
                           sql"""insert into master_service_offer_variant_numeric_attributes (
                                |  master_service_offer_variant_id,
                                |  attribute_code,
                                |  value
                                |)
                                |values (
                                |  $variantId,
                                |  ${AttributeDefinition.HairRemovalMethodAttribute.code},
                                |  ${BigDecimal("2.5")}
                                |)
                                |on conflict (master_service_offer_variant_id, attribute_code) do update set
                                |  value = excluded.value
                                |""".stripMargin.update.run
                         }
          result <- variants.getMasterServiceOfferVariant(variantId).either
          _      <- assertIO(
                      result.left.exists {
                        case QueryFailure.OperationFailure(operationName, message) =>
                          operationName == "load-master-service-offer-variant-attributes" &&
                          message.contains(AttributeDefinition.HairRemovalMethodAttribute.code) &&
                          message.contains("Int-compatible numeric value") &&
                          message.contains("non-integer numeric value") &&
                          message.contains("2.5")
                        case _ =>
                          false
                      }
                    )
        } yield ()
    }

    "reject unknown attribute code from numeric storage" in {
      (rnd: Rnd[IO],
        categories: Categories[IO],
        masters: Masters[IO],
        services: Services[IO],
        masterLocations: MasterLocations[IO],
        offers: MasterServiceOffers[IO],
        variants: MasterServiceOfferVariants[IO],
        db: SQL[IO],
      ) =>
        for {
          categoryId <- rnd[CategoryId]
          masterId   <- rnd[MasterId]
          serviceId  <- rnd[ServiceId]
          offerId    <- rnd[MasterServiceOfferId]
          locationId <- rnd[MasterLocationId]
          variantId  <- rnd[MasterServiceOfferVariantId]
          category    = Category(categoryId, rootCategoryId, 0, s"numeric-unknown-category-$categoryId")
          master      = Master(masterId, s"numeric-unknown-master-$masterId")
          service     = Service(serviceId, categoryId, s"numeric-unknown-service-$serviceId")
          offer       = MasterServiceOffer(offerId, masterId, serviceId)
          location    = MasterLocation(locationId, masterId, s"numeric-unknown-location-$locationId", s"numeric-unknown-address-$locationId", BigDecimal("13.0000"), BigDecimal("14.0000"))
          variant     <- makeVariant(variantId, offerId, locationId)
          _           <- categories.upsertCategory(category)
          _           <- masters.upsertMaster(master)
          _           <- services.upsertService(service)
          _           <- offers.upsertMasterServiceOffer(offer)
          _           <- masterLocations.upsertMasterLocation(location)
          _           <- variants.upsertMasterServiceOfferVariant(variant)
          _           <- db.execute("insert-unknown-master-service-offer-variant-numeric-attribute") {
                           sql"""insert into master_service_offer_variant_numeric_attributes (
                                |  master_service_offer_variant_id,
                                |  attribute_code,
                                |  value
                                |)
                                |values (
                                |  $variantId,
                                |  ${"unknown_attribute_code"},
                                |  ${BigDecimal("2.0")}
                                |)
                                |""".stripMargin.update.run
                         }
          result <- variants.getMasterServiceOfferVariant(variantId).either
          _      <- assertIO(
                      result.left.exists {
                        case QueryFailure.OperationFailure(operationName, message) =>
                          operationName == "load-master-service-offer-variant-attributes" &&
                          message == "Unknown MasterServiceOfferVariant attribute code: unknown_attribute_code"
                        case _ =>
                          false
                      }
                    )
        } yield ()
    }

    "create only the numeric attribute table" in {
      (db: SQL[IO]) =>
        for {
          tableNames <- db.execute("list-master-service-offer-variant-attribute-tables") {
                          sql"""select table_name
                               from information_schema.tables
                               where table_schema = 'public'
                                 and table_name like 'master_service_offer_variant_%_attributes'
                               order by table_name asc
                             """.query[String].to[List]
                        }
          _ <- assertIO(
            tableNames == List("master_service_offer_variant_numeric_attributes")
          )
        } yield ()
    }
  }
}
