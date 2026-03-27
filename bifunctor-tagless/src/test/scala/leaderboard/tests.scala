package leaderboard

import distage.{DIKey, ModuleDef, Scene}
import izumi.distage.model.definition.Activation
import izumi.distage.model.definition.StandardAxis.Repo
import izumi.distage.plugins.PluginConfig
import izumi.distage.testkit.scalatest.{AssertZIO, SpecZIO}
import leaderboard.model.Category.{CategoryId, rootCategoryId}
import leaderboard.model.*
import leaderboard.repo.{Categories, Ladder, MasterLocations, Masters, Profiles, Services}
import leaderboard.services.Ranks
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
final class ServicesTestDummy extends ServicesTest with DummyTest

final class LadderTestPostgres extends LadderTest with ProdTest
final class ProfilesTestPostgres extends ProfilesTest with ProdTest
final class RanksTestPostgres extends RanksTest with ProdTest
final class CategoriesTestPostgres extends CategoriesTest with ProdTest
final class MastersTestPostgres extends MastersTest with ProdTest
final class MasterLocationsTestPostgres extends MasterLocationsTest with ProdTest
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
