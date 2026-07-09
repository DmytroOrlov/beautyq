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

// --- fixture with neither a conventional `id` field nor CatalogValue
// evidence: proves toRawSnapshot needs neither CatalogEntity nor CatalogValue
// evidence, unlike toSnapshot (which would need one or the other to resolve
// a dedup key). ---
final case class SnapAssemblyPlainItem(label: String)
final case class SnapAssemblyPlainSnapshot(items: List[SnapAssemblyPlainItem])

final class CatalogSnapshotAssemblySpec extends AnyWordSpec {

  private val e1 = SnapAssemblyEntity(SnapAssemblyEntityId("e1"), "e1")
  private val e2 = SnapAssemblyEntity(SnapAssemblyEntityId("e2"), "e2")
  private val rawEntities = List(e1, e2, e1)

  private val v1 = SnapAssemblyValueItem(SnapAssemblyValueParentId("p1"), "v1")
  private val v2 = SnapAssemblyValueItem(SnapAssemblyValueParentId("p2"), "v2")
  private val rawValues = List(v1, v2, v1)

  private val p1 = SnapAssemblyPlainItem("p1")
  private val p2 = SnapAssemblyPlainItem("p2")
  private val rawPlainItems = List(p1, p2, p1)

  given CatalogValue.Aux[SnapAssemblyValueItem, SnapAssemblyValueParentId, SnapAssemblyValueItem] =
    CatalogValue.from(RepoValueSource[SnapAssemblyValueItem, SnapAssemblyValueParentId, SnapAssemblyValueItem](
      valueModelName = "SnapAssemblyValueItem",
      rowSource      = RepoEntity.derived[SnapAssemblyValueItem],
      keyField       = RepoField.derived[SnapAssemblyValueItem, SnapAssemblyValueParentId](_.parentId),
    ))

  private val loaded = LoadedCatalog(rawEntities *: rawValues *: EmptyTuple)
  private val plainLoaded = LoadedCatalog(rawPlainItems *: EmptyTuple)

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

  "LoadedCatalog.toRawSnapshot (generic raw snapshot assembly)" should {
    "build a product snapshot case class from a LoadedCatalog, following the snapshot's own field order" in {
      val snapshot = loaded.toRawSnapshot[SnapAssemblySnapshot]

      assert(snapshot.entities == rawEntities)
      assert(snapshot.values == rawValues)
    }

    "preserve entity duplicates instead of deduplicating by conventional id" in {
      val snapshot = loaded.toRawSnapshot[SnapAssemblySnapshot]

      assert(snapshot.entities == List(e1, e2, e1))
      assert(snapshot.entities.size == 3)
    }

    "preserve aggregate value duplicates instead of deduplicating by CatalogValue's key field" in {
      val snapshot = loaded.toRawSnapshot[SnapAssemblySnapshot]

      assert(snapshot.values == List(v1, v2, v1))
      assert(snapshot.values.size == 3)
    }

    "not mutate the original LoadedCatalog raw lists" in {
      loaded.toRawSnapshot[SnapAssemblySnapshot]

      assert(loaded.values[List[SnapAssemblyEntity]] == rawEntities)
      assert(loaded.values[List[SnapAssemblyValueItem]] == rawValues)
    }

    "not require CatalogValue or CatalogEntity evidence for a field with neither" in {
      val snapshot = plainLoaded.toRawSnapshot[SnapAssemblyPlainSnapshot]

      assert(snapshot.items == rawPlainItems)
      assert(snapshot.items.size == 3)
    }

    "not support a non-List[A] snapshot field" in {
      assertDoesNotCompile("loaded.toRawSnapshot[SnapAssemblyBadSnapshot]")
    }
  }

  "toSnapshot vs toRawSnapshot" should {
    "dedup via toSnapshot but preserve every duplicate via toRawSnapshot, from the very same LoadedCatalog" in {
      val deduped = loaded.toSnapshot[SnapAssemblySnapshot]
      val raw     = loaded.toRawSnapshot[SnapAssemblySnapshot]

      assert(deduped.entities == List(e1, e2))
      assert(raw.entities == List(e1, e2, e1))
      assert(deduped.values == List(v1, v2))
      assert(raw.values == List(v1, v2, v1))
    }

    "require CatalogEntity/CatalogValue-derivable evidence for toSnapshot but not for toRawSnapshot, on the same plain field" in {
      val raw = plainLoaded.toRawSnapshot[SnapAssemblyPlainSnapshot]
      assert(raw.items == rawPlainItems)

      assertDoesNotCompile("plainLoaded.toSnapshot[SnapAssemblyPlainSnapshot]")
    }
  }
}
