package leaderboard

import leaderboard.model.*
import leaderboard.services.Ranks
import leaderboard.repo.{Ladder, Profiles}
import leaderboard.zioenv.*
import zio.{IO, ZIO}

// AI-NOTE: For distage testkit memoizationRoots, Activation, and BIO patterns used here, see docs/LOCAL_LLM_IZUMI_DISTAGE_BIO_REFERENCE.md
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
        user2  <- rnd[UserId].repeatUntil(_ != user1)
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

        user2  <- rnd[UserId].repeatUntil(_ != user1)
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

class LadderTestDummy extends LadderTest with DummyTest
class ProfilesTestDummy extends ProfilesTest with DummyTest
class RanksTestDummy extends RanksTest with DummyTest
class LadderTestPostgres extends LadderTest with ProdTest
class ProfilesTestPostgres extends ProfilesTest with ProdTest
class RanksTestPostgres extends RanksTest with ProdTest
