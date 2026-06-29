package leaderboard.repo

import leaderboard.model.QueryFailure
import leaderboard.repo.RepoOp.{ManyByKey, OptionalByKey, ValueByKey}
import org.scalatest.wordspec.AnyWordSpec
import zio.{IO, Runtime, Unsafe, ZIO}

// Deterministic, immutable fixtures for the generic interpreter. No mutable spies.
final case class GraphTreeNode(id: String, parentId: String)
final case class GraphHolder(id: String)
final case class GraphChild(id: String, parentId: String)
final case class GraphValue(holderId: String, label: String)

final class GraphLoadingSpec extends AnyWordSpec {

  "selfTree relation" should {
    "return each child before its descendants, preserving sibling order" in {
      val a      = GraphTreeNode("a", "root")
      val b      = GraphTreeNode("b", "root")
      val aChild = GraphTreeNode("a-child", "a")
      val childrenByParent = Map(
        "root" -> List(a, b),
        "a"    -> List(aChild),
      )
      val node = RepoEntity.derived[GraphTreeNode].node(_.id)
      val relation = node.selfTree(
        parent   = _.parentId,
        children = ManyByKey[IO, String, GraphTreeNode](key => ZIO.succeed(childrenByParent.getOrElse(key, Nil))),
      )

      val loaded = runIO(GraphLoading.selfTreeFrom(relation, "root"))

      assert(loaded.map(_.id) == List("a", "a-child", "b"))
    }
  }

  "hasMany relation" should {
    "preserve parent order then child order" in {
      val p1 = GraphHolder("p1")
      val p2 = GraphHolder("p2")
      val childrenByParent = Map(
        "p1" -> List(GraphChild("c1", "p1"), GraphChild("c2", "p1")),
        "p2" -> List(GraphChild("c3", "p2")),
      )
      val parentNode = RepoEntity.derived[GraphHolder].node(_.id)
      val childNode  = RepoEntity.derived[GraphChild].node(_.id)
      val relation = parentNode.hasMany(childNode)(
        by   = _.parentId,
        load = ManyByKey[IO, String, GraphChild](key => ZIO.succeed(childrenByParent.getOrElse(key, Nil))),
      )

      val loaded = runIO(GraphLoading.manyFor(relation, List(p1, p2)))

      assert(loaded.map(_.id) == List("c1", "c2", "c3"))
    }
  }

  "hasValue relation" should {
    "load one value per parent, preserving parent order" in {
      val h1 = GraphHolder("h1")
      val h2 = GraphHolder("h2")
      val holderNode = RepoEntity.derived[GraphHolder].node(_.id)
      val valueSource = RepoValueSource[GraphValue, String, GraphValue](
        valueModelName = "GraphValue",
        rowSource      = RepoEntity.derived[GraphValue],
        keyField       = RepoField.derived[GraphValue, String](_.holderId),
      )
      val relation = holderNode.hasValue(valueSource)(
        by   = _.holderId,
        load = ValueByKey[IO, String, GraphValue](key => ZIO.succeed(GraphValue(key, s"value-$key"))),
      )

      val loaded = runIO(GraphLoading.valueFor(relation, List(h1, h2)))

      assert(loaded.map(_.label) == List("value-h1", "value-h2"))
    }
  }

  "distinctByKey" should {
    "keep the first occurrence and preserve output order" in {
      val items = List("k1" -> 1, "k2" -> 2, "k1" -> 3, "k3" -> 4, "k2" -> 5)

      val distinct = GraphLoading.distinctByKey(items)(_._1)

      assert(distinct == List("k1" -> 1, "k2" -> 2, "k3" -> 4))
    }
  }

  "seedRequired" should {
    "fail with the canonical missing-entity message" in {
      val missing  = GraphHolder("missing-holder")
      val load     = OptionalByKey[IO, String, GraphHolder](_ => ZIO.succeed(None))

      val result = runIO(GraphLoading.seedRequired(List(missing), "Widget", (_: GraphHolder).id, load).either)

      result match {
        case Left(failure) =>
          assert(failure.message == s"Seed-scoped search snapshot is missing Widget for seed item $missing")
        case Right(value) =>
          fail(s"Expected a missing-entity failure, got: $value")
      }
    }
  }

  private def runIO[A](effect: IO[QueryFailure, A]): A =
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(effect).getOrThrowFiberFailure()
    }
}
