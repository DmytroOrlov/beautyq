package leaderboard.search.beautyq.gen2.eval

import io.circe.Json

/** Post-cutover completion evidence: the fixed set of owners, paths, and modules
  * that were removed or edited by the Brick 9 atomic cutover.  Each entry is an
  * immutable record of a removed Gen1 owner, its action, its Gen2 replacement,
  * and the reason.  Active order is explicit and is not derived from enum
  * inventory.
  *
  * After cutover, this inventory is a static artifact — owners named here no
  * longer exist in the repository.  The inventory is retained as canonical
  * completion evidence alongside the machine-readable cutover report. */
enum BeautyQGen1DeletionEntryKind(val stableId: String) {
  case SbtProjectRoot extends BeautyQGen1DeletionEntryKind("sbt-project-root")
  case SbtDependencyEdge extends BeautyQGen1DeletionEntryKind("sbt-dependency-edge")
  case PublicRouteApi extends BeautyQGen1DeletionEntryKind("public-route-api")
  case AppShellDiModule extends BeautyQGen1DeletionEntryKind("app-shell-di-module")
  case Gen1BackendClient extends BeautyQGen1DeletionEntryKind("gen1-backend-client")
  case CompatibilityConfiguration extends BeautyQGen1DeletionEntryKind("compatibility-configuration")
  case PhysicalResourceNamespace extends BeautyQGen1DeletionEntryKind("physical-resource-namespace")
  case CrossProjectTest extends BeautyQGen1DeletionEntryKind("cross-project-test")
  case DocumentationOwner extends BeautyQGen1DeletionEntryKind("documentation-owner")
}

enum BeautyQGen1DeletionAction(val stableId: String) {
  case DeleteProject extends BeautyQGen1DeletionAction("delete-project")
  case DeleteFileOrOwner extends BeautyQGen1DeletionAction("delete-file-or-owner")
  case EditOwner extends BeautyQGen1DeletionAction("edit-owner")
  case RemoveRoute extends BeautyQGen1DeletionAction("remove-route")
  case RemoveBinding extends BeautyQGen1DeletionAction("remove-binding")
  case RemoveCompatibilityView extends BeautyQGen1DeletionAction("remove-compatibility-view")
  case RemoveResourceNamespace extends BeautyQGen1DeletionAction("remove-resource-namespace")
  case RetainShared extends BeautyQGen1DeletionAction("retain-shared")
}

final class BeautyQGen1DeletionEntry private[eval] (
  val stableId: String,
  val kind: BeautyQGen1DeletionEntryKind,
  val currentOwner: String,
  val action: BeautyQGen1DeletionAction,
  val replacement: String,
  val reason: String,
)

object BeautyQGen1SearchDeletionInventory {

  /** Explicit active order; this is not enum inventory order. */
  val entries: Vector[BeautyQGen1DeletionEntry] = Vector(
    // Build/project candidates
    entry(
      "search-core-delete",
      BeautyQGen1DeletionEntryKind.SbtProjectRoot,
      "search-core",
      BeautyQGen1DeletionAction.DeleteProject,
      "search-gen2-core + search-gen2-contract",
      "Generic Gen1 field/document kernel is replaced by search-gen2-core + search-gen2-contract; the Gen2 field algebra is the single typed handle owner.",
    ),
    entry(
      "search-elasticsearch-delete",
      BeautyQGen1DeletionEntryKind.SbtProjectRoot,
      "search-elasticsearch",
      BeautyQGen1DeletionAction.DeleteProject,
      "search-gen2-elasticsearch",
      "Gen1 Elasticsearch interpreter is replaced by search-gen2-elasticsearch; Elasticsearch is the Gen2 full-result owner behind a closed compiled request.",
    ),
    entry(
      "search-qdrant-delete",
      BeautyQGen1DeletionEntryKind.SbtProjectRoot,
      "search-qdrant",
      BeautyQGen1DeletionAction.DeleteProject,
      "search-gen2-qdrant",
      "Gen1 Qdrant interpreter is replaced by search-gen2-qdrant; the Gen2 client owns one Qdrant 1.18.3 process end to end.",
    ),
    entry(
      "beautyq-search-contract-delete",
      BeautyQGen1DeletionEntryKind.SbtProjectRoot,
      "beautyq-search-contract",
      BeautyQGen1DeletionAction.DeleteProject,
      "beautyq-search-gen2-contract",
      "BeautyQ Gen1 contract is replaced by the Gen2 BeautyQ declaration root; document, intent, request, plan, facets and groups all live there now.",
    ),
    entry(
      "beautyq-search-materialization-delete",
      BeautyQGen1DeletionEntryKind.SbtProjectRoot,
      "beautyq-search-materialization",
      BeautyQGen1DeletionAction.DeleteProject,
      "beautyq-search-gen2-materialization",
      "Gen1 materialization is replaced by the Gen2 materialization; the Gen2 source owns repeatable-read snapshots, explicit projection and content fingerprints.",
    ),
    entry(
      "beautyq-search-wiring-delete",
      BeautyQGen1DeletionEntryKind.SbtProjectRoot,
      "beautyq-search-wiring",
      BeautyQGen1DeletionAction.DeleteProject,
      "beautyq-search-gen2-wiring + BeautyQSearchGen2Startup",
      "Gen1 wiring is replaced by beautyq-search-gen2-wiring and the application graph; the pipeline keeps a single trusted activation.",
    ),
    // Shared foundations retained (not classified for deletion)
    entry(
      "beautyq-search-repositories-retain",
      BeautyQGen1DeletionEntryKind.SbtProjectRoot,
      "beautyq-search-repositories",
      BeautyQGen1DeletionAction.RetainShared,
      "beautyq-search-gen2-materialization",
      "Repositories remain a shared foundation; Gen2 materialization reads the same domain/persistence models without depending on any Gen1 search module.",
    ),
    entry(
      "beautyq-model-retain",
      BeautyQGen1DeletionEntryKind.SbtProjectRoot,
      "beautyq-model",
      BeautyQGen1DeletionAction.RetainShared,
      "beautyq-search-gen2-contract + beautyq-search-gen2-materialization",
      "BeautyQ domain models are shared by Gen2 contract and materialization; deletion would break Gen2.",
    ),
    // sbt dependency edges
    entry(
      "leaderboard-app-shell-gen2-wiring-edge-retain",
      BeautyQGen1DeletionEntryKind.SbtDependencyEdge,
      "leaderboard-app-shell.dependsOn(beautyqSearchGen2Wiring % \"compile->compile;test->test\")",
      BeautyQGen1DeletionAction.RetainShared,
      "stays as the Gen2 application graph is the canonical runtime owner",
      "BeautyQSearchGen2Startup owns the application graph; deleting the edge would break /beauty-search.",
    ),
    entry(
      "leaderboard-app-shell-eval-edge-test",
      BeautyQGen1DeletionEntryKind.SbtDependencyEdge,
      "leaderboard-app-shell.dependsOn(beautyqSearchGen2Eval % \"test->test\")",
      BeautyQGen1DeletionAction.RetainShared,
      "stays in app-shell test scope only (the firewall forbids any wider scope)",
      "Eval is the canonical Brick 8B cutover runner owner; the test-scope edge is the only permitted build edge to eval.",
    ),
    entry(
      "app-http-beautyq-search-wiring-edge-delete",
      BeautyQGen1DeletionEntryKind.SbtDependencyEdge,
      "app-http.dependsOn(beautyqSearchWiring)",
      BeautyQGen1DeletionAction.EditOwner,
      "app-http.dependsOn(beautyqSearchGen2Wiring % \"compile->compile;test->test\")",
      "app-http no longer needs the Gen1 wiring; only the Gen2 wiring is required for the /beauty-search route and shared types.",
    ),
    entry(
      "leaderboard-app-shell-beautyq-search-wiring-edge-delete",
      BeautyQGen1DeletionEntryKind.SbtDependencyEdge,
      "leaderboard-app-shell.dependsOn(beautyqSearchWiring)",
      BeautyQGen1DeletionAction.EditOwner,
      "remove the Gen1 wiring edge; Gen2 startup owns the runtime graph",
      "Gen1 wiring is deleted with the beautyq-search-wiring sbt project; the Gen2 wiring supplies the application/runtime.",
    ),
    // Public route / API owners
    entry(
      "beauty-search-api-delete",
      BeautyQGen1DeletionEntryKind.PublicRouteApi,
      "leaderboard.api.BeautySearchApi / BeautySearchTapirEndpoints",
      BeautyQGen1DeletionAction.RemoveRoute,
      "BeautySearchGen2Api / BeautySearchGen2TapirEndpoints",
      "/beauty-search is the unversioned native Gen2 contract; the Gen1 route adapter and Tapir contract are removed.",
    ),
    entry(
      "beauty-search-api-impl-delete",
      BeautyQGen1DeletionEntryKind.PublicRouteApi,
      "app-http/src/main/scala/leaderboard/api/BeautySearchApi.scala",
      BeautyQGen1DeletionAction.DeleteFileOrOwner,
      "BeautySearchGen2Api",
      "The /beauty-search handler is the Gen2 application; the V1 BeautySearchApi is replaced by BeautySearchGen2Api without a compatibility adapter.",
    ),
    entry(
      "beauty-search-tapir-endpoints-delete",
      BeautyQGen1DeletionEntryKind.PublicRouteApi,
      "app-http/src/main/scala/leaderboard/http/tapir/BeautySearchTapirEndpoints.scala",
      BeautyQGen1DeletionAction.DeleteFileOrOwner,
      "BeautySearchGen2TapirEndpoints",
      "The Gen1 Tapir contract is replaced by the Gen2 contract mounted at /beauty-search.",
    ),
    // app-shell DI module owners
    entry(
      "beauty-search-plugin-modules-delete",
      BeautyQGen1DeletionEntryKind.AppShellDiModule,
      "leaderboard.plugins.BeautySearchPluginModules",
      BeautyQGen1DeletionAction.RemoveBinding,
      "BeautySearchGen2PluginModules",
      "BeautySearchPluginModules binds the Gen1 BeautySearchApi; Brick 9 removes the Gen1 binding so /beauty-search serves the Gen2 contract.",
    ),
    entry(
      "beauty-search-route-modules-delete",
      BeautyQGen1DeletionEntryKind.AppShellDiModule,
      "leaderboard.plugins.BeautySearchRouteModules",
      BeautyQGen1DeletionAction.RemoveBinding,
      "BeautySearchGen2PluginModules.routeComposition",
      "The Gen1 route assembly disappears with the Gen1 API; only the Gen2 route composition remains.",
    ),
    entry(
      "beauty-search-local-qdrant-supplement-launcher-delete",
      BeautyQGen1DeletionEntryKind.AppShellDiModule,
      "leaderboard.plugins.BeautySearchLocalQdrantSupplementLauncherModule",
      BeautyQGen1DeletionAction.RemoveBinding,
      "BeautySearchGen2PluginModules.appShellGraph",
      "The Gen1 local Qdrant supplement launcher is replaced by the Gen2 appShellGraph; the Gen1 Qdrant runtime bindings are deleted.",
    ),
    entry(
      "beauty-search-qdrant-supplement-runtime-bindings-delete",
      BeautyQGen1DeletionEntryKind.AppShellDiModule,
      "leaderboard.plugins.BeautySearchQdrantSupplementRuntimeBindingModules",
      BeautyQGen1DeletionAction.RemoveBinding,
      "BeautySearchGen2PluginModules routes the embedding through the typed Qdrant runtime directly",
      "The Gen1 supplement runtime binding plan and module are deleted with beautyq-search-wiring; the Gen2 client owns collection/candidate execution.",
    ),
    entry(
      "beauty-search-qdrant-supplement-activation-selector-delete",
      BeautyQGen1DeletionEntryKind.AppShellDiModule,
      "leaderboard.plugins.BeautySearchQdrantSupplementActivationModuleSelector",
      BeautyQGen1DeletionAction.RemoveBinding,
      "BeautySearchGen2PluginModules.appShellGraph",
      "The Gen1 route-selection policy is replaced by the Gen2 startup-derived readiness; no separate module selection is needed.",
    ),
    entry(
      "beauty-search-catalog-backend-modules-delete",
      BeautyQGen1DeletionEntryKind.AppShellDiModule,
      "leaderboard.plugins.BeautySearchCatalogBackendModules",
      BeautyQGen1DeletionAction.RemoveBinding,
      "BeautySearchGen2PluginModules",
      "The Gen1 catalog backend factory is replaced by the Gen2 materializer inside the Gen2 startup.",
    ),
    entry(
      "beauty-search-hybrid-production-modules-delete",
      BeautyQGen1DeletionEntryKind.AppShellDiModule,
      "leaderboard.search.hybrid.production.BeautySearchHybridProductionModules",
      BeautyQGen1DeletionAction.RemoveBinding,
      "BeautySearchGen2PluginModules",
      "The Gen1 hybrid production module is replaced by the Gen2 opt-in module; the Gen1 hybrid execution path is removed.",
    ),
    entry(
      "leaderboard-plugin-search-include-edit",
      BeautyQGen1DeletionEntryKind.AppShellDiModule,
      "LeaderboardPlugin include(BeautySearchLocalQdrantSupplementLauncherModule.managedLocalDefault)",
      BeautyQGen1DeletionAction.EditOwner,
      "LeaderboardPlugin remove the Gen1 include; /beauty-search is served by the Gen2 application",
      "The default route binding switches to the Gen2 application; the Gen1 launcher include is removed.",
    ),
    // Gen1 backend / client / runtime owners
    entry(
      "gen1-elasticsearch-client-delete",
      BeautyQGen1DeletionEntryKind.Gen1BackendClient,
      "leaderboard.search.elasticsearch.ElasticsearchClient / ElasticsearchJsonClient / ElasticsearchMappingInterpreter / ElasticsearchSearchRequestInterpreter / ElasticsearchSearchResponseInterpreter",
      BeautyQGen1DeletionAction.DeleteFileOrOwner,
      "search-gen2-elasticsearch.ElasticsearchGen2JsonClient + ElasticsearchGenerationCompiler / ElasticsearchSearchRequestCompiler / ElasticsearchSearchResponseDecoder",
      "The Gen1 ES client and interpreters are replaced by the Gen2 ES client and compilers; the Gen1 ES interpreter paths are deleted.",
    ),
    entry(
      "gen1-qdrant-client-delete",
      BeautyQGen1DeletionEntryKind.Gen1BackendClient,
      "leaderboard.search.qdrant.QdrantClient / QdrantJsonInterpreter / QdrantDocumentPointBuilder / QdrantCollectionCompatibilityValidator",
      BeautyQGen1DeletionAction.DeleteFileOrOwner,
      "search-gen2-qdrant.QdrantGen2Client / QdrantGenerationCompiler / QdrantCandidateRequestCompiler / QdrantCandidateService",
      "The Gen1 Qdrant client and interpreters are replaced by the Gen2 Qdrant client and compilers; the Gen1 wire paths are deleted.",
    ),
    entry(
      "gen1-embedding-client-delete",
      BeautyQGen1DeletionEntryKind.Gen1BackendClient,
      "leaderboard.search.embedding.LlamaCppEmbeddingClient (Gen1 transport owner)",
      BeautyQGen1DeletionAction.DeleteFileOrOwner,
      "leaderboard.search.gen2.BeautyQGen2EmbeddingClient",
      "The Gen1 embedding client and its result decoder are replaced by the Gen2 BeautyQGen2EmbeddingClient; the Gen1 client is removed.",
    ),
    entry(
      "gen1-hybrid-runner-delete",
      BeautyQGen1DeletionEntryKind.Gen1BackendClient,
      "leaderboard.search.hybrid.* (QdrantVariantSupplementPolicy, BeautyQNonProductionHybridRunner*, BeautyQHybrid*)",
      BeautyQGen1DeletionAction.DeleteFileOrOwner,
      "beautyq-search-gen2-wiring.BeautyQSearchOrchestrator + BeautyQSearchGen2Startup",
      "The Gen1 hybrid pipeline is replaced by the Gen2 orchestrator and the explicit opt-in startup; Gen1 hybrid owners are deleted.",
    ),
    // Compatibility configuration / bindings
    entry(
      "qdrant-port-cfg-delete",
      BeautyQGen1DeletionEntryKind.CompatibilityConfiguration,
      "leaderboard.config.QdrantPortCfg",
      BeautyQGen1DeletionAction.RemoveCompatibilityView,
      "QdrantGen2PortCfg (canonical) + BeautyQGen2AppShellConfig",
      "QdrantPortCfg is the legacy Gen1 DTO; Brick 9 deletes it while keeping internal Gen2 type/module names stable.",
    ),
    entry(
      "qdrant-gen2-docker-plugin-derive-delete",
      BeautyQGen1DeletionEntryKind.CompatibilityConfiguration,
      "QdrantGen2DockerPlugin.make[QdrantPortCfg].from derived binding",
      BeautyQGen1DeletionAction.RemoveBinding,
      "QdrantGen2PortCfg",
      "The derived Gen1 compatibility binding inside the Gen2 plugin is removed with the legacy DTO.",
    ),
    // Physical resource namespaces
    entry(
      "gen1-elasticsearch-alias-remove",
      BeautyQGen1DeletionEntryKind.PhysicalResourceNamespace,
      "Gen1 Elasticsearch index alias / physical namespace reserved by beautyq-search-wiring",
      BeautyQGen1DeletionAction.RemoveResourceNamespace,
      "Gen2 Elasticsearch alias and physical index prefix from BeautyQSearchGen2ResourceNames",
      "The Gen1 ES alias and physical namespace are deleted with the Gen1 wiring; only the Gen2 reserved alias and prefix remain.",
    ),
    entry(
      "gen1-qdrant-collection-remove",
      BeautyQGen1DeletionEntryKind.PhysicalResourceNamespace,
      "Gen1 Qdrant collection / alias reserved by beautyq-search-wiring",
      BeautyQGen1DeletionAction.RemoveResourceNamespace,
      "Gen2 Qdrant collection alias and physical prefix from BeautyQSearchGen2ResourceNames",
      "The Gen1 Qdrant collection and alias are deleted; only the Gen2 reserved alias and physical prefix remain.",
    ),
    // Cross-project tests / fixtures
    entry(
      "gen1-comparison-fixture-edit",
      BeautyQGen1DeletionEntryKind.CrossProjectTest,
      "search-gen2-contract fixture module that imports Gen1 search source for evidence",
      BeautyQGen1DeletionAction.EditOwner,
      "remove the Gen1 evidence-only comparison fixture; the Gen2 firewall + tracer fixtures remain",
      "The isolated Gen1 comparison fixture is removed with the Gen1 projects; no Gen2 source compares against Gen1 after Brick 9.",
    ),
    entry(
      "gen1-serving-eval-scaffold-delete",
      BeautyQGen1DeletionEntryKind.CrossProjectTest,
      "leaderboard-app-shell M8–M21 evaluation scaffolding specs",
      BeautyQGen1DeletionAction.DeleteFileOrOwner,
      "BeautyQSearchGen2CutoverCommunicationSpec (already owns the cutover evidence)",
      "The M8–M21 evaluation scaffolding is removed with the Gen1 search runtime; only the Gen2 cutover runner remains.",
    ),
    // Documentation
    entry(
      "docs-gen1-beautyq-search-plan-delete",
      BeautyQGen1DeletionEntryKind.DocumentationOwner,
      "docs/search/BEAUTYQ_SEARCH_*.md (Gen1 contract, supplement, eval scaffolding)",
      BeautyQGen1DeletionAction.DeleteFileOrOwner,
      "docs/gen2/BEAUTYQ_SEARCH_GEN2_IMPLEMENTATION_PLAN.md + BEAUTYQ_SEARCH_GEN2_TECHNICAL_SPEC.md",
      "The Gen1 search documentation is removed; the Gen2 plan and technical spec are the only search architecture documents.",
    ),
    entry(
      "docs-gen1-supplement-architecture-delete",
      BeautyQGen1DeletionEntryKind.DocumentationOwner,
      "docs/BEAUTYQ_QDRANT_SUPPLEMENT_LOCAL_GATE.md and related Gen1 supplement docs",
      BeautyQGen1DeletionAction.DeleteFileOrOwner,
      "Brick 8 cutover report in target/search-gen2/beautyq-cutover-report.json",
      "The Gen1 supplement gate docs are removed; the Brick 8 cutover report is the canonical machine-readable evidence.",
    ),
  )

  /** Deterministic JSON view of the inventory, in declaration order. */
  def toJson: Json =
    Json.fromValues(entries.map { entry =>
      Json.obj(
        "stableId" -> Json.fromString(entry.stableId),
        "kind" -> Json.fromString(entry.kind.stableId),
        "currentOwner" -> Json.fromString(entry.currentOwner),
        "action" -> Json.fromString(entry.action.stableId),
        "replacement" -> Json.fromString(entry.replacement),
        "reason" -> Json.fromString(entry.reason),
      )
    })

  private def entry(
    stableId: String,
    kind: BeautyQGen1DeletionEntryKind,
    currentOwner: String,
    action: BeautyQGen1DeletionAction,
    replacement: String,
    reason: String,
  ): BeautyQGen1DeletionEntry = {
    assert(stableId.nonEmpty, s"inventory entry stableId must not be blank: $currentOwner")
    assert(currentOwner.nonEmpty, s"inventory entry currentOwner must not be blank: $stableId")
    assert(replacement.nonEmpty, s"inventory entry replacement must not be blank: $stableId")
    assert(reason.nonEmpty, s"inventory entry reason must not be blank: $stableId")
    new BeautyQGen1DeletionEntry(stableId, kind, currentOwner, action, replacement, reason)
  }
}
