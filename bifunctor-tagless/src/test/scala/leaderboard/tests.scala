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
  override final def config = super.config.copy(
    activation = super.config.activation ++ Activation(Repo -> Repo.Dummy)
  )
}

trait ProdTest extends LeaderboardTest {
  override final def config = super.config.copy(
    activation = super.config.activation ++ Activation(Repo -> Repo.Prod)
  )
}

final class LadderTestDummy extends LadderTest with DummyTest
final class ProfilesTestDummy extends ProfilesTest with DummyTest
final class RanksTestDummy extends RanksTest with DummyTest
final class CategoriesTestDummy extends CategoriesTest with DummyTest
final class MastersTestDummy extends MastersTest with DummyTest
final class MasterLocationsTestDummy extends MasterLocationsTest with DummyTest
final class MasterServiceOffersTestDummy extends MasterServiceOffersTest with DummyTest
final class ServiceVariantSchemasTestDummy extends ServiceVariantSchemasTest with DummyTest
final class ServiceVariantSchemasStorageValidationTestPostgres extends ServiceVariantSchemasStorageValidationTest with ProdTest
final class MasterServiceOfferVariantsTestDummy extends MasterServiceOfferVariantsTest with DummyTest
final class ServicesTestDummy extends ServicesTest with DummyTest

final class LadderTestPostgres extends LadderTest with ProdTest
final class ProfilesTestPostgres extends ProfilesTest with ProdTest
final class RanksTestPostgres extends RanksTest with ProdTest
final class CategoriesTestPostgres extends CategoriesTest with ProdTest
final class MastersTestPostgres extends MastersTest with ProdTest
final class MasterLocationsTestPostgres extends MasterLocationsTest with ProdTest
final class MasterServiceOffersTestPostgres extends MasterServiceOffersTest with ProdTest
final class ServiceVariantSchemasTestPostgres extends ServiceVariantSchemasTest with ProdTest
final class MasterServiceOfferVariantsTestPostgres extends MasterServiceOfferVariantsTest with ProdTest
final class ServicesTestPostgres extends ServicesTest with ProdTest

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
          id       <- rnd[CategoryId]
          category  = Category(id, rootCategoryId, 0, s"top-$id")
          _        <- categories.upsertCategory(category)
          res      <- categories.getCategory(category.id)
          _        <- assertIO(res.contains(category))
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
          id      <- rnd[MasterId]
          master   = Master(id, s"name-$id")
          _       <- masters.upsertMaster(master)
          res     <- masters.getMaster(master.id)
          _       <- assertIO(res.contains(master))
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
          id      <- rnd[MasterId]
          initial  = Master(id, "same-id")
          updated  = Master(id, "same-id-updated")
          _       <- masters.upsertMaster(initial)
          _       <- masters.upsertMaster(updated)
          res     <- masters.getMaster(id)
          _       <- assertIO(res.contains(updated))
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
          _          <- assertIO(result.isLeft)
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

          master1  = Master(master1Id, s"master-a-$master1Id")
          master2  = Master(master2Id, s"master-b-$master2Id")
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
                          ServiceVariantSchemaItem(MasterServiceOfferVariantAttributeDefinition.MaterialsSurcharge, false),
                          ServiceVariantSchemaItem(MasterServiceOfferVariantAttributeDefinition.SessionCount, true),
                        )
          _          <- categories.upsertCategory(category)
          _          <- services.upsertService(service)
          _          <- serviceVariantSchemas.upsertServiceVariantSchema(schema)
          res        <- serviceVariantSchemas.getServiceVariantSchema(serviceId)
          _          <- assertIO(
                          res == ServiceVariantSchema.fromItems(
                            serviceId,
                            List(
                              ServiceVariantSchemaItem(MasterServiceOfferVariantAttributeDefinition.MaterialsSurcharge, false),
                              ServiceVariantSchemaItem(MasterServiceOfferVariantAttributeDefinition.SessionCount, true),
                            )
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
                             ServiceVariantSchemaItem(MasterServiceOfferVariantAttributeDefinition.SessionCount, true)
                           )
                         )
                         .either
          _         <- assertIO(result.isLeft)
        } yield ()
    }

  }

}

abstract class ServiceVariantSchemasStorageValidationTest extends LeaderboardTest {
  "ServiceVariantSchemas" should {
    "reject unknown attribute code from storage" in {
      (
        rnd: Rnd[IO],
        categories: Categories[IO],
        services: Services[IO],
        serviceVariantSchemas: ServiceVariantSchemas[IO],
        db: SQL[IO],
      ) =>
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
          result     <- serviceVariantSchemas.getServiceVariantSchema(serviceId).either
          _          <- assertIO(result.isLeft)
        } yield ()
    }
  }
}

abstract class MasterServiceOfferVariantsTest extends LeaderboardTest {
  private def makeVariant(
    id: MasterServiceOfferVariantId,
    masterServiceOfferId: MasterServiceOfferId,
    masterLocationId: MasterLocationId,
    priceFrom: BigDecimal,
    priceTo: BigDecimal,
    durationMin: Int,
    intAttributes: Map[IntAttributeDefinition, Int] = Map.empty,
    bigDecimalAttributes: Map[BigDecimalAttributeDefinition, BigDecimal] = Map.empty,
  ): IO[QueryFailure, MasterServiceOfferVariant] =
    MasterServiceOfferVariant
      .make(
        id,
        masterServiceOfferId,
        masterLocationId,
        priceFrom,
        priceTo,
        durationMin,
        MasterServiceOfferVariantAttributes(intAttributes, bigDecimalAttributes),
      ) match {
      case Right(value) =>
        ZIO.succeed(value)
      case Left(error) =>
        ZIO.fail(QueryFailure("make-master-service-offer-variant", error.asThrowable))
    }

  private def makeSchema(serviceId: ServiceId, items: ServiceVariantSchemaItem*): ServiceVariantSchema =
    ServiceVariantSchema.fromItems(serviceId, items)

  "MasterServiceOfferVariants" should {

    "upsert & get" in {
      (
        rnd: Rnd[IO],
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
          location    = MasterLocation(locationId, masterId, s"offer-variant-$locationId", s"offer-variant-address-$locationId", BigDecimal("12.3400"), BigDecimal("56.7800"))
          variant    <- makeVariant(variantId, offerId, locationId, BigDecimal("30.0000"), BigDecimal("45.0000"), 60)
          _          <- categories.upsertCategory(category)
          _          <- masters.upsertMaster(master)
          _          <- services.upsertService(service)
          _          <- offers.upsertMasterServiceOffer(offer)
          _          <- masterLocations.upsertMasterLocation(location)
          _          <- variants.upsertMasterServiceOfferVariant(variant)
          res        <- variants.getMasterServiceOfferVariant(variant.id)
          _          <- assertIO(res.contains(variant))
        } yield ()
    }

    "upsert & get with required and optional additional attributes allowed by service schema" in {
      (
        rnd: Rnd[IO],
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
          location    = MasterLocation(locationId, masterId, s"offer-variant-attrs-$locationId", s"offer-variant-attrs-address-$locationId", BigDecimal("13.3400"), BigDecimal("57.7800"))
          schema      = makeSchema(
                          serviceId,
                          ServiceVariantSchemaItem(MasterServiceOfferVariantAttributeDefinition.SessionCount, true),
                          ServiceVariantSchemaItem(MasterServiceOfferVariantAttributeDefinition.DepositAmount, false),
                          ServiceVariantSchemaItem(MasterServiceOfferVariantAttributeDefinition.MaterialsSurcharge, false),
                        )
          variant    <- makeVariant(
                          variantId,
                          offerId,
                          locationId,
                          BigDecimal("30.0000"),
                          BigDecimal("45.0000"),
                          60,
                          intAttributes = Map(MasterServiceOfferVariantAttributeDefinition.SessionCount -> 5),
                          bigDecimalAttributes = Map(
                            MasterServiceOfferVariantAttributeDefinition.DepositAmount      -> BigDecimal("15.0000"),
                            MasterServiceOfferVariantAttributeDefinition.MaterialsSurcharge -> BigDecimal("7.5000"),
                          ),
                        )
          _          <- categories.upsertCategory(category)
          _          <- masters.upsertMaster(master)
          _          <- services.upsertService(service)
          _          <- serviceVariantSchemas.upsertServiceVariantSchema(schema)
          _          <- offers.upsertMasterServiceOffer(offer)
          _          <- masterLocations.upsertMasterLocation(location)
          _          <- variants.upsertMasterServiceOfferVariant(variant)
          res        <- variants.getMasterServiceOfferVariant(variant.id)
          _          <- assertIO(res.contains(variant))
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
          location    = MasterLocation(locationId, masterId, s"missing-offer-location-$locationId", s"missing-offer-address-$locationId", BigDecimal("1.1000"), BigDecimal("2.2000"))
          _          <- masters.upsertMaster(master)
          _          <- masterLocations.upsertMasterLocation(location)
          variant    <- makeVariant(variantId, offerId, locationId, BigDecimal("30.0000"), BigDecimal("45.0000"), 60)
          result     <- variants.upsertMasterServiceOfferVariant(variant).either
          _          <- assertIO(result.isLeft)
        } yield ()
    }

    "reject creating a variant when location does not exist" in {
      (
        rnd: Rnd[IO],
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

    "attributes expose typed access by definition and typed views" in {
      (rnd: Rnd[IO]) =>
        for {
          variantId  <- rnd[MasterServiceOfferVariantId]
          offerId    <- rnd[MasterServiceOfferId]
          locationId <- rnd[MasterLocationId]
          attributes  = MasterServiceOfferVariantAttributes(
                          intValues = Map(MasterServiceOfferVariantAttributeDefinition.SessionCount -> 3),
                          bigDecimalValues = Map(MasterServiceOfferVariantAttributeDefinition.DepositAmount -> BigDecimal("12.5000")),
                        )
          variant    <- ZIO
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
                          .mapError(error => QueryFailure("make-master-service-offer-variant", error.asThrowable))
          _          <- assertIO(variant.getAttribute(MasterServiceOfferVariantAttributeDefinition.SessionCount).contains(3))
          _          <- assertIO(
                          variant.getAttribute(MasterServiceOfferVariantAttributeDefinition.DepositAmount).contains(BigDecimal("12.5000"))
                        )
          _          <- assertIO(variant.intAttributes.get(MasterServiceOfferVariantAttributeDefinition.SessionCount).contains(3))
          _          <- assertIO(
                          variant.bigDecimalAttributes.get(MasterServiceOfferVariantAttributeDefinition.DepositAmount).contains(BigDecimal("12.5000"))
                        )
          _          <- assertIO(variant.intAttributes.keySet == Set(MasterServiceOfferVariantAttributeDefinition.SessionCount))
          _          <- assertIO(
                          variant.bigDecimalAttributes.keySet == Set(
                            MasterServiceOfferVariantAttributeDefinition.DepositAmount
                          )
                        )
        } yield ()
    }

    "schema validate rejects disallowed attribute" in {
      (rnd: Rnd[IO]) =>
        for {
          serviceId <- rnd[ServiceId]
          schema     = makeSchema(serviceId, ServiceVariantSchemaItem(MasterServiceOfferVariantAttributeDefinition.SessionCount, false))
          attributes = MasterServiceOfferVariantAttributes(
                         intValues = Map.empty,
                         bigDecimalValues = Map(MasterServiceOfferVariantAttributeDefinition.DepositAmount -> BigDecimal("12.5000")),
                       )
          _         <- assertIO(
                         schema.validate(attributes) == Left(
                           ServiceVariantSchemaValidationError.DisallowedAttribute(
                             MasterServiceOfferVariantAttributeDefinition.DepositAmount
                           )
                         )
                       )
        } yield ()
    }

    "schema validate rejects missing required attribute" in {
      (rnd: Rnd[IO]) =>
        for {
          serviceId <- rnd[ServiceId]
          schema     = makeSchema(serviceId, ServiceVariantSchemaItem(MasterServiceOfferVariantAttributeDefinition.SessionCount, true))
          attributes = MasterServiceOfferVariantAttributes.empty
          _         <- assertIO(
                         schema.validate(attributes) == Left(
                           ServiceVariantSchemaValidationError.MissingRequiredAttribute(
                             MasterServiceOfferVariantAttributeDefinition.SessionCount
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
                          "intAttributes" -> Json.obj(
                            "deposit_amount" -> 3.asJson
                          ),
                        )
          result      = json.as[MasterServiceOfferVariant]
          _          <- assertIO(result.isLeft)
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
                          "intAttributes" -> Json.obj(
                            "unknown_attribute_code" -> 3.asJson
                          ),
                        )
          result      = json.as[MasterServiceOfferVariant]
          _          <- assertIO(result.isLeft)
        } yield ()
    }

    "reject additional attribute stored in wrong typed storage" in {
      (
        rnd: Rnd[IO],
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
          location    = MasterLocation(locationId, masterId, s"variant-type-location-$locationId", s"variant-type-address-$locationId", BigDecimal("10.0000"), BigDecimal("20.0000"))
          schema      = makeSchema(serviceId, ServiceVariantSchemaItem(MasterServiceOfferVariantAttributeDefinition.DepositAmount, false))
          _          <- categories.upsertCategory(category)
          _          <- masters.upsertMaster(master)
          _          <- services.upsertService(service)
          _          <- serviceVariantSchemas.upsertServiceVariantSchema(schema)
          _          <- offers.upsertMasterServiceOffer(offer)
          _          <- masterLocations.upsertMasterLocation(location)
          json        = Json.obj(
                          "id"                   -> variantId.asJson,
                          "masterServiceOfferId" -> offerId.asJson,
                          "masterLocationId"     -> locationId.asJson,
                          "priceFrom"            -> BigDecimal("30.0000").asJson,
                          "priceTo"              -> BigDecimal("45.0000").asJson,
                          "durationMin"          -> 60.asJson,
                          "intAttributes" -> Json.obj(
                            "deposit_amount" -> 3.asJson
                          ),
                        )
          result      = json.as[MasterServiceOfferVariant]
          _          <- assertIO(result.isLeft)
        } yield ()
    }

    "upsert rejects disallowed additional attribute by service schema" in {
      (
        rnd: Rnd[IO],
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
          location    = MasterLocation(locationId, masterId, s"variant-schema-location-$locationId", s"variant-schema-address-$locationId", BigDecimal("10.0000"), BigDecimal("20.0000"))
          schema      = makeSchema(serviceId, ServiceVariantSchemaItem(MasterServiceOfferVariantAttributeDefinition.SessionCount, false))
          variant    <- makeVariant(
                          variantId,
                          offerId,
                          locationId,
                          BigDecimal("30.0000"),
                          BigDecimal("45.0000"),
                          60,
                          bigDecimalAttributes = Map(MasterServiceOfferVariantAttributeDefinition.DepositAmount -> BigDecimal("8.0000")),
                        )
          _          <- categories.upsertCategory(category)
          _          <- masters.upsertMaster(master)
          _          <- services.upsertService(service)
          _          <- serviceVariantSchemas.upsertServiceVariantSchema(schema)
          _          <- offers.upsertMasterServiceOffer(offer)
          _          <- masterLocations.upsertMasterLocation(location)
          result     <- variants.upsertMasterServiceOfferVariant(variant).either
          _          <- assertIO(
                          result.left.exists(
                            failure =>
                              failure.queryName == "upsert-master-service-offer-variant" &&
                                failure.cause.getMessage == s"Service $serviceId does not allow MasterServiceOfferVariant attribute deposit_amount"
                          )
                        )
        } yield ()
    }

    "upsert rejects missing required additional attribute by service schema" in {
      (
        rnd: Rnd[IO],
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
          location    = MasterLocation(locationId, masterId, s"variant-required-location-$locationId", s"variant-required-address-$locationId", BigDecimal("10.0000"), BigDecimal("20.0000"))
          schema      = makeSchema(serviceId, ServiceVariantSchemaItem(MasterServiceOfferVariantAttributeDefinition.SessionCount, true))
          variant    <- makeVariant(variantId, offerId, locationId, BigDecimal("30.0000"), BigDecimal("45.0000"), 60)
          _          <- categories.upsertCategory(category)
          _          <- masters.upsertMaster(master)
          _          <- services.upsertService(service)
          _          <- serviceVariantSchemas.upsertServiceVariantSchema(schema)
          _          <- offers.upsertMasterServiceOffer(offer)
          _          <- masterLocations.upsertMasterLocation(location)
          result     <- variants.upsertMasterServiceOfferVariant(variant).either
          _          <- assertIO(
                          result.left.exists(
                            failure =>
                              failure.queryName == "upsert-master-service-offer-variant" &&
                                failure.cause.getMessage == s"Service $serviceId requires MasterServiceOfferVariant attribute session_count"
                          )
                        )
        } yield ()
    }

    "reject creating a variant when offer and location belong to different masters" in {
      (
        rnd: Rnd[IO],
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
          location    = MasterLocation(locationId, master2Id, s"mismatch-location-$locationId", s"mismatch-address-$locationId", BigDecimal("17.0000"), BigDecimal("27.0000"))
          result     <- {
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
          _          <- assertIO(result.isLeft)
        } yield ()
    }

    "allow creating several variants for one offer" in {
      (
        rnd: Rnd[IO],
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
          location1    = MasterLocation(location1Id, masterId, s"variants-location-a-$location1Id", s"variants-address-a-$location1Id", BigDecimal("10.0000"), BigDecimal("20.0000"))
          location2    = MasterLocation(location2Id, masterId, s"variants-location-b-$location2Id", s"variants-address-b-$location2Id", BigDecimal("30.0000"), BigDecimal("40.0000"))
          variant1    <- makeVariant(variant1Id, offerId, location1Id, BigDecimal("10.0000"), BigDecimal("20.0000"), 30)
          variant2    <- makeVariant(variant2Id, offerId, location2Id, BigDecimal("30.0000"), BigDecimal("40.0000"), 90)
          _           <- categories.upsertCategory(category)
          _           <- masters.upsertMaster(master)
          _           <- services.upsertService(service)
          _           <- offers.upsertMasterServiceOffer(offer)
          _           <- masterLocations.upsertMasterLocation(location1)
          _           <- masterLocations.upsertMasterLocation(location2)
          _           <- variants.upsertMasterServiceOfferVariant(variant1)
          _           <- variants.upsertMasterServiceOfferVariant(variant2)
          res         <- variants.getMasterServiceOfferVariantsByOffer(offerId)
          _           <- assertIO(res.toSet == Set(variant1, variant2))
        } yield ()
    }

    "allow creating several variants for one location" in {
      (
        rnd: Rnd[IO],
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
          location    = MasterLocation(locationId, masterId, s"variants-shared-location-$locationId", s"variants-shared-address-$locationId", BigDecimal("50.0000"), BigDecimal("60.0000"))
          variant1   <- makeVariant(variant1Id, offer1Id, locationId, BigDecimal("15.0000"), BigDecimal("25.0000"), 30)
          variant2   <- makeVariant(variant2Id, offer2Id, locationId, BigDecimal("35.0000"), BigDecimal("45.0000"), 90)
          _          <- categories.upsertCategory(category)
          _          <- masters.upsertMaster(master)
          _          <- services.upsertService(service1)
          _          <- services.upsertService(service2)
          _          <- offers.upsertMasterServiceOffer(offer1)
          _          <- offers.upsertMasterServiceOffer(offer2)
          _          <- masterLocations.upsertMasterLocation(location)
          _          <- variants.upsertMasterServiceOfferVariant(variant1)
          _          <- variants.upsertMasterServiceOfferVariant(variant2)
          res        <- variants.getMasterServiceOfferVariantsByLocation(locationId)
          _          <- assertIO(res.toSet == Set(variant1, variant2))
        } yield ()
    }

    "return only variants of the requested offer" in {
      (
        rnd: Rnd[IO],
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
          location1    = MasterLocation(location1Id, masterId, s"variants-filter-offer-location-a-$location1Id", s"variants-filter-offer-address-a-$location1Id", BigDecimal("11.0000"), BigDecimal("21.0000"))
          location2    = MasterLocation(location2Id, masterId, s"variants-filter-offer-location-b-$location2Id", s"variants-filter-offer-address-b-$location2Id", BigDecimal("31.0000"), BigDecimal("41.0000"))
          otherLoc     = MasterLocation(otherLocId, masterId, s"variants-filter-offer-location-c-$otherLocId", s"variants-filter-offer-address-c-$otherLocId", BigDecimal("51.0000"), BigDecimal("61.0000"))
          variant1    <- makeVariant(variant1Id, offer1Id, location1Id, BigDecimal("11.0000"), BigDecimal("21.0000"), 30)
          variant2    <- makeVariant(variant2Id, offer1Id, location2Id, BigDecimal("31.0000"), BigDecimal("41.0000"), 60)
          other       <- makeVariant(otherId, offer2Id, otherLocId, BigDecimal("51.0000"), BigDecimal("61.0000"), 90)
          _           <- categories.upsertCategory(category)
          _           <- masters.upsertMaster(master)
          _           <- services.upsertService(service1)
          _           <- services.upsertService(service2)
          _           <- offers.upsertMasterServiceOffer(offer1)
          _           <- offers.upsertMasterServiceOffer(offer2)
          _           <- masterLocations.upsertMasterLocation(location1)
          _           <- masterLocations.upsertMasterLocation(location2)
          _           <- masterLocations.upsertMasterLocation(otherLoc)
          _           <- variants.upsertMasterServiceOfferVariant(variant1)
          _           <- variants.upsertMasterServiceOfferVariant(variant2)
          _           <- variants.upsertMasterServiceOfferVariant(other)
          res         <- variants.getMasterServiceOfferVariantsByOffer(offer1Id)
          _           <- assertIO(res.toSet == Set(variant1, variant2))
        } yield ()
    }

    "return only variants of the requested location" in {
      (
        rnd: Rnd[IO],
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
          location1    = MasterLocation(location1Id, masterId, s"variants-filter-location-a-$location1Id", s"variants-filter-location-address-a-$location1Id", BigDecimal("13.0000"), BigDecimal("23.0000"))
          location2    = MasterLocation(location2Id, masterId, s"variants-filter-location-b-$location2Id", s"variants-filter-location-address-b-$location2Id", BigDecimal("33.0000"), BigDecimal("43.0000"))
          variant1    <- makeVariant(variant1Id, offer1Id, location1Id, BigDecimal("13.0000"), BigDecimal("23.0000"), 30)
          variant2    <- makeVariant(variant2Id, offer2Id, location1Id, BigDecimal("33.0000"), BigDecimal("43.0000"), 60)
          other       <- makeVariant(otherId, offer1Id, location2Id, BigDecimal("53.0000"), BigDecimal("63.0000"), 90)
          _           <- categories.upsertCategory(category)
          _           <- masters.upsertMaster(master)
          _           <- services.upsertService(service1)
          _           <- services.upsertService(service2)
          _           <- offers.upsertMasterServiceOffer(offer1)
          _           <- offers.upsertMasterServiceOffer(offer2)
          _           <- masterLocations.upsertMasterLocation(location1)
          _           <- masterLocations.upsertMasterLocation(location2)
          _           <- variants.upsertMasterServiceOfferVariant(variant1)
          _           <- variants.upsertMasterServiceOfferVariant(variant2)
          _           <- variants.upsertMasterServiceOfferVariant(other)
          res         <- variants.getMasterServiceOfferVariantsByLocation(location1Id)
          _           <- assertIO(res.toSet == Set(variant1, variant2))
        } yield ()
    }

    "return variants by offer sorted by id asc" in {
      (
        rnd: Rnd[IO],
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
          location1    = MasterLocation(location1Id, masterId, s"variants-sort-offer-location-a-$location1Id", s"variants-sort-offer-address-a-$location1Id", BigDecimal("14.0000"), BigDecimal("24.0000"))
          location2    = MasterLocation(location2Id, masterId, s"variants-sort-offer-location-b-$location2Id", s"variants-sort-offer-address-b-$location2Id", BigDecimal("34.0000"), BigDecimal("44.0000"))
          location3    = MasterLocation(location3Id, masterId, s"variants-sort-offer-location-c-$location3Id", s"variants-sort-offer-address-c-$location3Id", BigDecimal("54.0000"), BigDecimal("64.0000"))
          id1          = java.util.UUID.fromString("30000000-0000-0000-0000-000000000002")
          id2          = java.util.UUID.fromString("30000000-0000-0000-0000-000000000001")
          id3          = java.util.UUID.fromString("30000000-0000-0000-0000-000000000003")
          variant1    <- makeVariant(id1, offerId, location1Id, BigDecimal("14.0000"), BigDecimal("24.0000"), 30)
          variant2    <- makeVariant(id2, offerId, location2Id, BigDecimal("34.0000"), BigDecimal("44.0000"), 60)
          variant3    <- makeVariant(id3, offerId, location3Id, BigDecimal("54.0000"), BigDecimal("64.0000"), 90)
          _           <- categories.upsertCategory(category)
          _           <- masters.upsertMaster(master)
          _           <- services.upsertService(service)
          _           <- offers.upsertMasterServiceOffer(offer)
          _           <- masterLocations.upsertMasterLocation(location1)
          _           <- masterLocations.upsertMasterLocation(location2)
          _           <- masterLocations.upsertMasterLocation(location3)
          _           <- variants.upsertMasterServiceOfferVariant(variant1)
          _           <- variants.upsertMasterServiceOfferVariant(variant2)
          _           <- variants.upsertMasterServiceOfferVariant(variant3)
          res         <- variants.getMasterServiceOfferVariantsByOffer(offerId)
          _           <- assertIO(res == List(variant2, variant1, variant3))
        } yield ()
    }

    "return variants by location sorted by id asc" in {
      (
        rnd: Rnd[IO],
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
          location    = MasterLocation(locationId, masterId, s"variants-sort-location-$locationId", s"variants-sort-location-address-$locationId", BigDecimal("15.0000"), BigDecimal("25.0000"))
          id1         = java.util.UUID.fromString("40000000-0000-0000-0000-000000000002")
          id2         = java.util.UUID.fromString("40000000-0000-0000-0000-000000000001")
          id3         = java.util.UUID.fromString("40000000-0000-0000-0000-000000000003")
          variant1   <- makeVariant(id1, offer1Id, locationId, BigDecimal("15.0000"), BigDecimal("25.0000"), 30)
          variant2   <- makeVariant(id2, offer2Id, locationId, BigDecimal("35.0000"), BigDecimal("45.0000"), 60)
          variant3   <- makeVariant(id3, offer3Id, locationId, BigDecimal("55.0000"), BigDecimal("65.0000"), 90)
          _          <- categories.upsertCategory(category)
          _          <- masters.upsertMaster(master)
          _          <- services.upsertService(service1)
          _          <- services.upsertService(service2)
          _          <- services.upsertService(service3)
          _          <- offers.upsertMasterServiceOffer(offer1)
          _          <- offers.upsertMasterServiceOffer(offer2)
          _          <- offers.upsertMasterServiceOffer(offer3)
          _          <- masterLocations.upsertMasterLocation(location)
          _          <- variants.upsertMasterServiceOfferVariant(variant1)
          _          <- variants.upsertMasterServiceOfferVariant(variant2)
          _          <- variants.upsertMasterServiceOfferVariant(variant3)
          res        <- variants.getMasterServiceOfferVariantsByLocation(locationId)
          _          <- assertIO(res == List(variant2, variant1, variant3))
        } yield ()
    }

    "upsert overwrites existing variant with same id and replaces additional attributes" in {
      (
        rnd: Rnd[IO],
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
          location1    = MasterLocation(location1Id, masterId, s"variants-overwrite-location-a-$location1Id", s"variants-overwrite-address-a-$location1Id", BigDecimal("16.0000"), BigDecimal("26.0000"))
          location2    = MasterLocation(location2Id, masterId, s"variants-overwrite-location-b-$location2Id", s"variants-overwrite-address-b-$location2Id", BigDecimal("36.0000"), BigDecimal("46.0000"))
          initialSchema = makeSchema(
                            service1Id,
                            ServiceVariantSchemaItem(MasterServiceOfferVariantAttributeDefinition.SessionCount, false),
                            ServiceVariantSchemaItem(MasterServiceOfferVariantAttributeDefinition.DepositAmount, false),
                          )
          updatedSchema = makeSchema(
                            service2Id,
                            ServiceVariantSchemaItem(MasterServiceOfferVariantAttributeDefinition.MaterialsSurcharge, false),
                            ServiceVariantSchemaItem(MasterServiceOfferVariantAttributeDefinition.SessionCount, false),
                          )
          initial      <- makeVariant(
                            variantId,
                            offer1Id,
                            location1Id,
                            BigDecimal("16.0000"),
                            BigDecimal("26.0000"),
                            30,
                            intAttributes = Map(MasterServiceOfferVariantAttributeDefinition.SessionCount -> 1),
                            bigDecimalAttributes = Map(MasterServiceOfferVariantAttributeDefinition.DepositAmount -> BigDecimal("10.0000")),
                          )
          updated      <- makeVariant(
                            variantId,
                            offer2Id,
                            location2Id,
                            BigDecimal("36.0000"),
                            BigDecimal("46.0000"),
                            90,
                            bigDecimalAttributes = Map(
                              MasterServiceOfferVariantAttributeDefinition.MaterialsSurcharge -> BigDecimal("12.0000")
                            ),
                          )
          _            <- categories.upsertCategory(category)
          _            <- masters.upsertMaster(master)
          _            <- services.upsertService(service1)
          _            <- services.upsertService(service2)
          _            <- serviceVariantSchemas.upsertServiceVariantSchema(initialSchema)
          _            <- serviceVariantSchemas.upsertServiceVariantSchema(updatedSchema)
          _            <- offers.upsertMasterServiceOffer(offer1)
          _            <- offers.upsertMasterServiceOffer(offer2)
          _            <- masterLocations.upsertMasterLocation(location1)
          _            <- masterLocations.upsertMasterLocation(location2)
          _            <- variants.upsertMasterServiceOfferVariant(initial)
          _            <- variants.upsertMasterServiceOfferVariant(updated)
          res          <- variants.getMasterServiceOfferVariant(variantId)
          _            <- assertIO(res.contains(updated))
        } yield ()
    }

  }

}
