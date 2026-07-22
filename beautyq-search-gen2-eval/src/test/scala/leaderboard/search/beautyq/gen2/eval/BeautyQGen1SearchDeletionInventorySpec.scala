package leaderboard.search.beautyq.gen2.eval

import io.circe.Json
import org.scalatest.wordspec.AnyWordSpec

final class BeautyQGen1SearchDeletionInventorySpec extends AnyWordSpec {

  "BeautyQGen1SearchDeletionInventory" should {
    "expose unique stable IDs in explicit declaration order" in {
      val entries = BeautyQGen1SearchDeletionInventory.entries
      val ids = entries.map(_.stableId)
      assert(ids.distinct == ids, s"inventory stable IDs must be unique, got duplicates: ${ids.diff(ids.distinct).distinct}")
      assert(ids == ids.distinct)
      assert(ids.head == "search-core-delete")
      assert(ids.last == "docs-gen1-supplement-architecture-delete")
    }

    "expose a non-blank field for every required entry" in {
      BeautyQGen1SearchDeletionInventory.entries.foreach { entry =>
        assert(entry.stableId.nonEmpty, s"stableId must not be blank for $entry")
        assert(entry.currentOwner.nonEmpty, s"currentOwner must not be blank for ${entry.stableId}")
        assert(entry.replacement.nonEmpty, s"replacement must not be blank for ${entry.stableId}")
        assert(entry.reason.nonEmpty, s"reason must not be blank for ${entry.stableId}")
      }
    }

    "classify every known Gen1 build/route/plugin/config owner exactly once" in {
      val owners = BeautyQGen1SearchDeletionInventory.entries.map(_.currentOwner)
      val mustAppear = Vector(
        "search-core",
        "search-elasticsearch",
        "search-qdrant",
        "beautyq-search-contract",
        "beautyq-search-materialization",
        "beautyq-search-wiring",
        "leaderboard.plugins.BeautySearchPluginModules",
        "leaderboard.plugins.BeautySearchRouteModules",
        "leaderboard.plugins.BeautySearchLocalQdrantSupplementLauncherModule",
        "leaderboard.plugins.BeautySearchQdrantSupplementRuntimeBindingModules",
        "leaderboard.plugins.BeautySearchQdrantSupplementActivationModuleSelector",
        "leaderboard.plugins.BeautySearchCatalogBackendModules",
        "leaderboard.search.hybrid.production.BeautySearchHybridProductionModules",
        "leaderboard.api.BeautySearchApi / BeautySearchTapirEndpoints",
        "leaderboard.config.QdrantPortCfg",
      )
      mustAppear.foreach { owner =>
        val matches = owners.count(_.contains(owner))
        assert(matches == 1, s"expected exactly one inventory entry to match '$owner', got $matches")
      }
    }

    "name the native Gen2 route/runtime/projector owners as the replacement" in {
      val replacements = BeautyQGen1SearchDeletionInventory.entries.map(_.replacement)
      assert(replacements.exists(_.contains("BeautySearchGen2Api")))
      assert(replacements.exists(_.contains("BeautySearchGen2TapirEndpoints")))
      assert(replacements.exists(_.contains("BeautySearchGen2PluginModules")))
      assert(replacements.exists(_.contains("beautyq-search-gen2-contract")))
      assert(replacements.exists(_.contains("beautyq-search-gen2-materialization")))
      assert(replacements.exists(_.contains("beautyq-search-gen2-wiring")))
      assert(replacements.exists(_.contains("BeautyQGen2EmbeddingClient")))
      assert(replacements.exists(_.contains("BeautyQSearchGen2Startup")))
    }

    "classify QdrantPortCfg for Brick 9 removal" in {
      val entry = BeautyQGen1SearchDeletionInventory.entries.find(_.currentOwner == "leaderboard.config.QdrantPortCfg")
        .getOrElse(fail("expected inventory entry for QdrantPortCfg"))
      assert(entry.action == BeautyQGen1DeletionAction.RemoveCompatibilityView)
    }

    "classify QdrantGen2DockerPlugin derived binding for Brick 9 removal" in {
      val entry = BeautyQGen1SearchDeletionInventory.entries.find { candidate =>
        candidate.currentOwner == "QdrantGen2DockerPlugin.make[QdrantPortCfg].from derived binding"
      }.getOrElse(fail("expected inventory entry for QdrantGen2DockerPlugin derived binding"))
      assert(entry.action == BeautyQGen1DeletionAction.RemoveBinding)
    }

    "never classify shared projects for deletion" in {
      val sharedRetained = Vector(
        "search-contract-core",
        "beautyq-search-repositories",
        "beautyq-model",
      )
      sharedRetained.foreach { project =>
        val entry = BeautyQGen1SearchDeletionInventory.entries.find(_.currentOwner == project)
          .getOrElse(fail(s"expected inventory entry for retained shared project '$project'"))
        assert(entry.action == BeautyQGen1DeletionAction.RetainShared, s"$project must be retained-shared, was ${entry.action}")
      }
    }

    "cover every required entry kind" in {
      val kinds = BeautyQGen1SearchDeletionInventory.entries.map(_.kind).toSet
      val required = Set(
        BeautyQGen1DeletionEntryKind.SbtProjectRoot,
        BeautyQGen1DeletionEntryKind.SbtDependencyEdge,
        BeautyQGen1DeletionEntryKind.PublicRouteApi,
        BeautyQGen1DeletionEntryKind.AppShellDiModule,
        BeautyQGen1DeletionEntryKind.Gen1BackendClient,
        BeautyQGen1DeletionEntryKind.CompatibilityConfiguration,
        BeautyQGen1DeletionEntryKind.PhysicalResourceNamespace,
        BeautyQGen1DeletionEntryKind.CrossProjectTest,
        BeautyQGen1DeletionEntryKind.DocumentationOwner,
      )
      assert(required.diff(kinds).isEmpty, s"missing kinds: ${required.diff(kinds)}")
    }

    "cover every required Brick 9 action" in {
      val actions = BeautyQGen1SearchDeletionInventory.entries.map(_.action).toSet
      val required = Set(
        BeautyQGen1DeletionAction.DeleteProject,
        BeautyQGen1DeletionAction.DeleteFileOrOwner,
        BeautyQGen1DeletionAction.EditOwner,
        BeautyQGen1DeletionAction.RemoveRoute,
        BeautyQGen1DeletionAction.RemoveBinding,
        BeautyQGen1DeletionAction.RemoveCompatibilityView,
        BeautyQGen1DeletionAction.RemoveResourceNamespace,
        BeautyQGen1DeletionAction.RetainShared,
      )
      assert(required.diff(actions).isEmpty, s"missing actions: ${required.diff(actions)}")
    }

    "render deterministic declaration-order JSON" in {
      val first = BeautyQGen1SearchDeletionInventory.toJson
      val second = BeautyQGen1SearchDeletionInventory.toJson
      assert(first == second)
      assert(first.asArray.exists(_.length == BeautyQGen1SearchDeletionInventory.entries.length))
      val firstStableId = first.asArray.flatMap(_.headOption).flatMap(_.hcursor.get[String]("stableId").toOption)
      assert(firstStableId.contains("search-core-delete"))
      val lastStableId = first.asArray.flatMap(_.lastOption).flatMap(_.hcursor.get[String]("stableId").toOption)
      assert(lastStableId.contains("docs-gen1-supplement-architecture-delete"))
      first.asArray.toList.flatten.foreach { json =>
        assert(json.hcursor.get[String]("stableId").toOption.nonEmpty)
        assert(json.hcursor.get[String]("kind").toOption.nonEmpty)
        assert(json.hcursor.get[String]("currentOwner").toOption.nonEmpty)
        assert(json.hcursor.get[String]("action").toOption.nonEmpty)
        assert(json.hcursor.get[String]("replacement").toOption.nonEmpty)
        assert(json.hcursor.get[String]("reason").toOption.nonEmpty)
      }
    }
  }
}
