package leaderboard.repo

import leaderboard.model.QueryFailure
import leaderboard.repo.RepoOp.ManyByKey
import org.scalatest.wordspec.AnyWordSpec
import zio.{IO, Runtime, Unsafe, ZIO}

// --- selfTree + hasMany fixture ---
final case class LoadedTreeId(value: String)
final case class LoadedTreeNode(id: LoadedTreeId, parentId: LoadedTreeId, name: String)
final case class LoadedTreeChildId(value: String)
final case class LoadedTreeChild(id: LoadedTreeChildId, nodeId: LoadedTreeId, label: String)

// --- rootAll + hasMany fixture ---
final case class LoadedRootId(value: String)
final case class LoadedRootThing(id: LoadedRootId, name: String)
final case class LoadedRootChildId(value: String)
final case class LoadedRootChild(id: LoadedRootChildId, rootId: LoadedRootId, label: String)

// --- rootAll + value edge fixture ---
final case class LoadedValueParentId(value: String)
final case class LoadedValueParent(id: LoadedValueParentId, name: String)
final case class LoadedValueItem(parentId: LoadedValueParentId, label: String)

// --- duplication fixture (two roots sharing one child) ---
final case class LoadedDupRootId(value: String)
final case class LoadedDupRoot(id: LoadedDupRootId, name: String)
final case class LoadedDupChildId(value: String)
final case class LoadedDupChild(id: LoadedDupChildId, rootId: LoadedDupRootId, label: String)

final class CatalogLoadedGraphSpec extends AnyWordSpec {

  "LoadCatalogRelations (generic loaded-graph traversal)" should {
    "load a selfTree root and a hasMany child from it, in preorder + parent order" in {
      val root  = LoadedTreeId("root")
      val a     = LoadedTreeNode(LoadedTreeId("a"), root, "a")
      val b     = LoadedTreeNode(LoadedTreeId("b"), root, "b")
      val aKid  = LoadedTreeNode(LoadedTreeId("a-kid"), a.id, "a-kid")
      val childrenByParent = Map(
        root -> List(a, b),
        a.id -> List(aKid),
      )
      val childItemsByNode: Map[LoadedTreeId, List[LoadedTreeChild]] = Map(
        a.id    -> List(LoadedTreeChild(LoadedTreeChildId("ca1"), a.id, "ca1")),
        b.id    -> List(LoadedTreeChild(LoadedTreeChildId("cb1"), b.id, "cb1")),
        aKid.id -> List(LoadedTreeChild(LoadedTreeChildId("ck1"), aKid.id, "ck1")),
      )

      given CatalogRootTree.Aux[IO, Unit, LoadedTreeNode, LoadedTreeId] =
        CatalogRootTree.fromRepo[IO, Unit, Unit, LoadedTreeNode, LoadedTreeId](identity)(_ =>
          ManyByKey[IO, LoadedTreeId, LoadedTreeNode](key => ZIO.succeed(childrenByParent.getOrElse(key, Nil))))

      given CatalogMany.Aux[IO, Unit, LoadedTreeNode, LoadedTreeChild, LoadedTreeId] =
        CatalogMany.fromRepo[IO, Unit, Unit, LoadedTreeNode, LoadedTreeChild, LoadedTreeId](identity)(_ =>
          ManyByKey[IO, LoadedTreeId, LoadedTreeChild](key => ZIO.succeed(childItemsByNode.getOrElse(key, Nil))))

      val declaration = catalog("loadedTree").branch[LoadedTreeNode].rootTree(_.parentId, root).child[LoadedTreeChild](_.nodeId)
      val materialized = declaration.materialize[IO, Unit](identity)

      val loaded = runIO(materialized.loadAll(()))

      assert(loaded.values[List[LoadedTreeNode]].map(_.id) == List(a.id, aKid.id, b.id))
      assert(loaded.values[List[LoadedTreeChild]].map(_.id) == List(
        LoadedTreeChildId("ca1"),
        LoadedTreeChildId("ck1"),
        LoadedTreeChildId("cb1"),
      ))
    }

    "load a rootAll root and then a hasMany child from those roots" in {
      val r1 = LoadedRootThing(LoadedRootId("r1"), "r1")
      val r2 = LoadedRootThing(LoadedRootId("r2"), "r2")
      val childrenByRoot: Map[LoadedRootId, List[LoadedRootChild]] = Map(
        r1.id -> List(LoadedRootChild(LoadedRootChildId("c1"), r1.id, "c1")),
        r2.id -> List(LoadedRootChild(LoadedRootChildId("c2"), r2.id, "c2"), LoadedRootChild(LoadedRootChildId("c3"), r2.id, "c3")),
      )

      given CatalogRootAll[IO, Unit, LoadedRootThing] =
        CatalogRootAll.fromRepo[IO, Unit, Unit, LoadedRootThing](identity)(_ => RepoOp.AllValues[IO, LoadedRootThing](() => ZIO.succeed(List(r1, r2))))

      given CatalogMany.Aux[IO, Unit, LoadedRootThing, LoadedRootChild, LoadedRootId] =
        CatalogMany.fromRepo[IO, Unit, Unit, LoadedRootThing, LoadedRootChild, LoadedRootId](identity)(_ =>
          ManyByKey[IO, LoadedRootId, LoadedRootChild](key => ZIO.succeed(childrenByRoot.getOrElse(key, Nil))))

      val declaration = catalog("loadedRootAll").branch[LoadedRootThing].rootAll.child[LoadedRootChild](_.rootId)
      val materialized = declaration.materialize[IO, Unit](identity)

      val loaded = runIO(materialized.loadAll(()))

      assert(loaded.values[List[LoadedRootThing]] == List(r1, r2))
      assert(loaded.values[List[LoadedRootChild]].map(_.id) == List(LoadedRootChildId("c1"), LoadedRootChildId("c2"), LoadedRootChildId("c3")))
    }

    "load a value edge's values from already-loaded parents" in {
      val p1 = LoadedValueParent(LoadedValueParentId("p1"), "p1")
      val p2 = LoadedValueParent(LoadedValueParentId("p2"), "p2")
      val valueByParent: Map[LoadedValueParentId, LoadedValueItem] = Map(
        p1.id -> LoadedValueItem(p1.id, "value-p1"),
        p2.id -> LoadedValueItem(p2.id, "value-p2"),
      )

      given CatalogRootAll[IO, Unit, LoadedValueParent] =
        CatalogRootAll.fromRepo[IO, Unit, Unit, LoadedValueParent](identity)(_ => RepoOp.AllValues[IO, LoadedValueParent](() => ZIO.succeed(List(p1, p2))))

      given CatalogValue.Aux[LoadedValueItem, LoadedValueParentId, LoadedValueItem] =
        CatalogValue.from(RepoValueSource[LoadedValueItem, LoadedValueParentId, LoadedValueItem](
          valueModelName = "LoadedValueItem",
          rowSource      = RepoEntity.derived[LoadedValueItem],
          keyField       = RepoField.derived[LoadedValueItem, LoadedValueParentId](_.parentId),
        ))

      given CatalogValueEdge.Aux[IO, Unit, LoadedValueParent, LoadedValueItem, LoadedValueParentId] =
        CatalogValueEdge.fromRepo[IO, Unit, Unit, LoadedValueParent, LoadedValueItem, LoadedValueParentId](identity)(_ =>
          RepoOp.ValueByKey[IO, LoadedValueParentId, LoadedValueItem](key => ZIO.succeed(valueByParent(key))))

      val declaration = catalog("loadedValue").branch[LoadedValueParent].rootAll.value[LoadedValueItem](_.parentId)
      val materialized = declaration.materialize[IO, Unit](identity)

      val loaded = runIO(materialized.loadAll(()))

      assert(loaded.values[List[LoadedValueParent]] == List(p1, p2))
      assert(loaded.values[List[LoadedValueItem]].map(_.label) == List("value-p1", "value-p2"))
    }

    "not deduplicate raw loaded lists, even when the same child is reachable from two parents" in {
      val r1 = LoadedDupRoot(LoadedDupRootId("r1"), "r1")
      val r2 = LoadedDupRoot(LoadedDupRootId("r2"), "r2")
      val shared = LoadedDupChild(LoadedDupChildId("shared"), r1.id, "shared")

      given CatalogRootAll[IO, Unit, LoadedDupRoot] =
        CatalogRootAll.fromRepo[IO, Unit, Unit, LoadedDupRoot](identity)(_ => RepoOp.AllValues[IO, LoadedDupRoot](() => ZIO.succeed(List(r1, r2))))

      given CatalogMany.Aux[IO, Unit, LoadedDupRoot, LoadedDupChild, LoadedDupRootId] =
        CatalogMany.fromRepo[IO, Unit, Unit, LoadedDupRoot, LoadedDupChild, LoadedDupRootId](identity)(_ =>
          ManyByKey[IO, LoadedDupRootId, LoadedDupChild](_ => ZIO.succeed(List(shared))))

      val declaration = catalog("loadedDup").branch[LoadedDupRoot].rootAll.child[LoadedDupChild](_.rootId)
      val materialized = declaration.materialize[IO, Unit](identity)

      val loaded = runIO(materialized.loadAll(()))

      assert(loaded.values[List[LoadedDupChild]] == List(shared, shared))
    }
  }

  private def runIO[A](effect: IO[QueryFailure, A]): A =
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(effect).getOrThrowFiberFailure()
    }
}
