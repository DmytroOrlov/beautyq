package leaderboard.repo

import org.scalatest.wordspec.AnyWordSpec

// --- normal, conventional-id entity fixture ---
final case class SnapAssemblyEntityId(value: String)
final case class SnapAssemblyEntity(id: SnapAssemblyEntityId, name: String)

// --- aggregate/value fixture (keyed by a field other than a conventional id) ---
final case class SnapAssemblyValueParentId(value: String)
final case class SnapAssemblyValueItem(parentId: SnapAssemblyValueParentId, label: String)

// Field order deliberately reversed vs. how the fixtures below store them in
// the LoadedCatalog tuple (entities first, values second), to prove snapshot
// field order follows this constructor, not the loaded tuple's storage order.
final case class SnapAssemblySnapshot(
  values: List[SnapAssemblyValueItem],
  entities: List[SnapAssemblyEntity],
)

final case class SnapAssemblyBadSnapshot(count: Int)

final class CatalogSnapshotAssemblySpec extends AnyWordSpec {

  private val e1 = SnapAssemblyEntity(SnapAssemblyEntityId("e1"), "e1")
  private val e2 = SnapAssemblyEntity(SnapAssemblyEntityId("e2"), "e2")
  private val rawEntities = List(e1, e2, e1)

  private val v1 = SnapAssemblyValueItem(SnapAssemblyValueParentId("p1"), "v1")
  private val v2 = SnapAssemblyValueItem(SnapAssemblyValueParentId("p2"), "v2")
  private val rawValues = List(v1, v2, v1)

  given CatalogValue.Aux[SnapAssemblyValueItem, SnapAssemblyValueParentId, SnapAssemblyValueItem] =
    CatalogValue.from(RepoValueSource[SnapAssemblyValueItem, SnapAssemblyValueParentId, SnapAssemblyValueItem](
      valueModelName = "SnapAssemblyValueItem",
      rowSource      = RepoEntity.derived[SnapAssemblyValueItem],
      keyField       = RepoField.derived[SnapAssemblyValueItem, SnapAssemblyValueParentId](_.parentId),
    ))

  private val loaded = LoadedCatalog(rawEntities *: rawValues *: EmptyTuple)

  "LoadedCatalog.toSnapshot (generic snapshot assembly)" should {
    "build a product snapshot case class from a LoadedCatalog, following the snapshot's own field order" in {
      val snapshot = loaded.toSnapshot[SnapAssemblySnapshot]

      assert(snapshot.entities == List(e1, e2))
      assert(snapshot.values == List(v1, v2))
    }

    "deduplicate a normal entity list by conventional id, preserving first occurrence" in {
      val snapshot = loaded.toSnapshot[SnapAssemblySnapshot]

      assert(snapshot.entities == List(e1, e2))
      assert(snapshot.entities.size == 2)
    }

    "deduplicate an aggregate value list by CatalogValue's key field, preserving first occurrence" in {
      val snapshot = loaded.toSnapshot[SnapAssemblySnapshot]

      assert(snapshot.values == List(v1, v2))
      assert(snapshot.values.size == 2)
    }

    "not mutate or deduplicate the original LoadedCatalog raw lists" in {
      loaded.toSnapshot[SnapAssemblySnapshot]

      assert(loaded.values[List[SnapAssemblyEntity]] == rawEntities)
      assert(loaded.values[List[SnapAssemblyValueItem]] == rawValues)
      assert(loaded.values[List[SnapAssemblyEntity]].size == 3)
      assert(loaded.values[List[SnapAssemblyValueItem]].size == 3)
    }

    "not support a non-List[A] snapshot field" in {
      assertDoesNotCompile("loaded.toSnapshot[SnapAssemblyBadSnapshot]")
    }
  }
}
