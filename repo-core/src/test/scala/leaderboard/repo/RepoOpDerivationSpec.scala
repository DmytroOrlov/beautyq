package leaderboard.repo

import leaderboard.model.QueryFailure
import leaderboard.repo.RepoOp.{AllValues, ManyByKey, OptionalByKey, ValueByKey}
import org.scalatest.wordspec.AnyWordSpec
import zio.{IO, Runtime, Unsafe, ZIO}

// Deterministic, immutable fixtures for RepoOpDerivation. No mutable spies.
// OwnerId/GroupId are distinct nominal types (not aliases of the same underlying
// type), so a two-many-method repo proves derivation selects by full key type,
// not by shared element type alone.
final case class RepoOpFixtureItem(
  id: String,
  ownerId: RepoOpFixtureItem.OwnerId,
  groupId: RepoOpFixtureItem.GroupId,
)

object RepoOpFixtureItem {
  final case class OwnerId(value: String)
  final case class GroupId(value: String)
}

final case class RepoOpFixtureLabel(text: String)

trait RepoOpFixtureRepo[F[_, _]] {
  def getById(id: String): F[QueryFailure, Option[RepoOpFixtureItem]]
  def getByOwner(ownerId: RepoOpFixtureItem.OwnerId): F[QueryFailure, List[RepoOpFixtureItem]]
  def getByGroup(groupId: RepoOpFixtureItem.GroupId): F[QueryFailure, List[RepoOpFixtureItem]]
  def getLabel(id: String): F[QueryFailure, RepoOpFixtureLabel]
  def getAll(): F[QueryFailure, List[RepoOpFixtureItem]]
}

final class RepoOpDerivationSpec extends AnyWordSpec {

  import RepoOpFixtureItem.{GroupId, OwnerId}

  private val item1 = RepoOpFixtureItem("i1", OwnerId("owner-a"), GroupId("group-x"))
  private val item2 = RepoOpFixtureItem("i2", OwnerId("owner-a"), GroupId("group-y"))
  private val item3 = RepoOpFixtureItem("i3", OwnerId("owner-b"), GroupId("group-x"))
  private val allItems = List(item1, item2, item3)

  private val repo: RepoOpFixtureRepo[IO] = new RepoOpFixtureRepo[IO] {
    def getById(id: String): IO[QueryFailure, Option[RepoOpFixtureItem]] =
      ZIO.succeed(allItems.find(_.id == id))

    def getByOwner(ownerId: OwnerId): IO[QueryFailure, List[RepoOpFixtureItem]] =
      ZIO.succeed(allItems.filter(_.ownerId == ownerId))

    def getByGroup(groupId: GroupId): IO[QueryFailure, List[RepoOpFixtureItem]] =
      ZIO.succeed(allItems.filter(_.groupId == groupId))

    def getLabel(id: String): IO[QueryFailure, RepoOpFixtureLabel] =
      ZIO.succeed(RepoOpFixtureLabel(s"label-$id"))

    def getAll(): IO[QueryFailure, List[RepoOpFixtureItem]] =
      ZIO.succeed(allItems)
  }

  private def runIO[A](effect: IO[QueryFailure, A]): A =
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(effect).getOrThrowFiberFailure()
    }

  "OptionalByKey.derived" should {
    "call the unique K => F[QueryFailure, Option[A]] method" in {
      val op = OptionalByKey.derived[IO, RepoOpFixtureRepo[IO], String, RepoOpFixtureItem](repo)

      assert(runIO(op.run("i1")).contains(item1))
      assert(runIO(op.run("missing")).isEmpty)
    }
  }

  "ManyByKey.derived" should {
    "select by full key type, not by shared element type alone, when two many methods exist" in {
      val byOwner = ManyByKey.derived[IO, RepoOpFixtureRepo[IO], OwnerId, RepoOpFixtureItem](repo)
      val byGroup = ManyByKey.derived[IO, RepoOpFixtureRepo[IO], GroupId, RepoOpFixtureItem](repo)

      assert(runIO(byOwner.run(OwnerId("owner-a"))).map(_.id) == List("i1", "i2"))
      assert(runIO(byGroup.run(GroupId("group-x"))).map(_.id) == List("i1", "i3"))
    }
  }

  "ValueByKey.derived" should {
    "call the unique K => F[QueryFailure, A] method" in {
      val op = ValueByKey.derived[IO, RepoOpFixtureRepo[IO], String, RepoOpFixtureLabel](repo)

      assert(runIO(op.run("i1")) == RepoOpFixtureLabel("label-i1"))
    }
  }

  "AllValues.derived" should {
    "call the unique no-arg () => F[QueryFailure, List[A]] method" in {
      val op = AllValues.derived[IO, RepoOpFixtureRepo[IO], RepoOpFixtureItem](repo)

      assert(runIO(op.run()).map(_.id) == List("i1", "i2", "i3"))
    }
  }
}
