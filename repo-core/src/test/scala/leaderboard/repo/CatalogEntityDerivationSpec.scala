package leaderboard.repo

import org.scalatest.wordspec.AnyWordSpec

// A plain fixture case class with a conventional `id` field. No `RepoEntity`
// value, repo companion, or hand-written selector is declared for it anywhere
// in this file - `CatalogEntity.Aux` below must resolve purely through the
// automatic given.
final case class CatalogEntityFixtureId(value: String)
final case class CatalogEntityFixture(id: CatalogEntityFixtureId, name: String)

final class CatalogEntityDerivationSpec extends AnyWordSpec {

  "CatalogEntity automatic derivation (derivedFromId)" should {
    "derive a node whose key field label and column are id, from the conventional id field alone" in {
      val entity = summon[CatalogEntity.Aux[CatalogEntityFixture, CatalogEntityFixtureId]]

      assert(entity.node.key.label == "id")
      assert(entity.node.key.column == "id")
    }

    "derive a RepoEntity whose source name is the snake_case model name" in {
      val entity = summon[CatalogEntity.Aux[CatalogEntityFixture, CatalogEntityFixtureId]]

      assert(entity.node.entity.modelName == "CatalogEntityFixture")
      assert(entity.node.entity.sourceName == "catalog_entity_fixture")
    }

    "select the same id value the case class carries" in {
      val entity  = summon[CatalogEntity.Aux[CatalogEntityFixture, CatalogEntityFixtureId]]
      val fixture = CatalogEntityFixture(CatalogEntityFixtureId("f1"), "fixture-name")

      assert(entity.node.key.select(fixture) == CatalogEntityFixtureId("f1"))
    }
  }

  "CatalogEntity.from / CatalogEntity.derived (explicit APIs)" should {
    "remain usable directly, unaffected by the automatic given" in {
      val explicitEntity = RepoEntity.derived[CatalogEntityFixture]
      val viaFrom         = CatalogEntity.from(explicitEntity.node(_.id))
      val viaDerived      = CatalogEntity.derived(explicitEntity)

      assert(viaFrom.node.key.label == "id")
      assert(viaDerived.node.key.label == "id")
    }
  }
}
