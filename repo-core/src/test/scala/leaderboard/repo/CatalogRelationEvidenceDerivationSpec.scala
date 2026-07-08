package leaderboard.repo

import leaderboard.model.QueryFailure
import org.scalatest.wordspec.AnyWordSpec
import zio.{IO, Runtime, Unsafe, ZIO}

final case class CatalogRelationTreeId(value: String)
final case class CatalogRelationAllId(value: String)
final case class CatalogRelationParentId(value: String)
final case class CatalogRelationChildId(value: String)

final case class CatalogRelationTreeNode(id: CatalogRelationTreeId, parentId: CatalogRelationTreeId, name: String)
final case class CatalogRelationAllRoot(id: CatalogRelationAllId, name: String)
final case class CatalogRelationParent(id: CatalogRelationParentId, name: String)
final case class CatalogRelationChild(id: CatalogRelationChildId, parentId: CatalogRelationParentId, name: String)

trait CatalogRelationTreeRepo[F[_, _]] {
  def children(parentId: CatalogRelationTreeId): F[QueryFailure, List[CatalogRelationTreeNode]]
}

trait CatalogRelationAllRepo[F[_, _]] {
  def all(): F[QueryFailure, List[CatalogRelationAllRoot]]
}

trait CatalogRelationChildRepo[F[_, _]] {
  def children(parentId: CatalogRelationParentId): F[QueryFailure, List[CatalogRelationChild]]
}

final case class CatalogRelationRepositories[F[_, _]](
  treeRepo: CatalogRelationTreeRepo[F],
  allRepo: CatalogRelationAllRepo[F],
  childRepo: CatalogRelationChildRepo[F],
)

final class CatalogRelationEvidenceDerivationSpec extends AnyWordSpec {

  private val treeRoot  = CatalogRelationTreeId("tree-root")
  private val treeChild = CatalogRelationTreeNode(CatalogRelationTreeId("tree-child"), treeRoot, "tree-child")
  private val allRoot   = CatalogRelationAllRoot(CatalogRelationAllId("all-root"), "all-root")
  private val parent    = CatalogRelationParent(CatalogRelationParentId("parent"), "parent")
  private val child     = CatalogRelationChild(CatalogRelationChildId("child"), parent.id, "child")

  private val repositories = CatalogRelationRepositories[IO](
    treeRepo = new CatalogRelationTreeRepo[IO] {
      def children(parentId: CatalogRelationTreeId): IO[QueryFailure, List[CatalogRelationTreeNode]] =
        ZIO.succeed(if (parentId == treeRoot) List(treeChild) else Nil)
    },
    allRepo = new CatalogRelationAllRepo[IO] {
      def all(): IO[QueryFailure, List[CatalogRelationAllRoot]] =
        ZIO.succeed(List(allRoot))
    },
    childRepo = new CatalogRelationChildRepo[IO] {
      def children(parentId: CatalogRelationParentId): IO[QueryFailure, List[CatalogRelationChild]] =
        ZIO.succeed(if (parentId == parent.id) List(child) else Nil)
    },
  )

  "CatalogRootTree automatic derivation from repositories" should {
    "materialize a rootTree declaration and call the matching repo method" in {
      val declaration = catalog("relationTree").branch[CatalogRelationTreeNode].rootTree(_.parentId, treeRoot)

      val materialized = declaration.materialize[IO, CatalogRelationRepositories[IO]](identity)
      val relationFactory = materialized
        .relationAs[CatalogRelationRepositories[IO] => Relation.SelfTree[IO, CatalogRelationTreeNode, CatalogRelationTreeId]]
      val relation = relationFactory(repositories)

      assert(runIO(relation.children.run(treeRoot)) == List(treeChild))
      assert(relation.node.key.label == "id")
      assert(relation.parent.label == "parentId")
    }
  }

  "CatalogRootAll automatic derivation from repositories" should {
    "materialize a rootAll declaration and call the matching repo method" in {
      val declaration = catalog("relationAll").branch[CatalogRelationAllRoot].rootAll

      val materialized = declaration.materialize[IO, CatalogRelationRepositories[IO]](identity)
      val relationFactory = materialized
        .relationAs[CatalogRelationRepositories[IO] => Relation.All[IO, CatalogRelationAllRoot, CatalogRelationAllId]]
      val relation = relationFactory(repositories)

      assert(runIO(relation.load.run()) == List(allRoot))
      assert(relation.node.key.label == "id")
    }
  }

  "CatalogMany automatic derivation from repositories" should {
    "materialize a many-edge declaration and call the matching repo method" in {
      val declaration = catalog("relationMany").branch[CatalogRelationParent].child[CatalogRelationChild](_.parentId)

      val materialized = declaration.materialize[IO, CatalogRelationRepositories[IO]](identity)
      val relationFactory = materialized
        .relationAs[CatalogRelationRepositories[IO] => Relation.HasMany[IO, CatalogRelationParent, CatalogRelationParentId, CatalogRelationChild, CatalogRelationChildId]]
      val relation = relationFactory(repositories)

      assert(runIO(relation.load.run(parent.id)) == List(child))
      assert(relation.parent.key.label == "id")
      assert(relation.child.key.label == "id")
      assert(relation.foreignKey.label == "parentId")
    }
  }

  private def runIO[A](effect: IO[QueryFailure, A]): A =
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(effect).getOrThrowFiberFailure()
    }
}
