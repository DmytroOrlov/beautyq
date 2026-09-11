# Feature 003 — B0 Framework Capability Map

Pinned-framework broad inventory (read-only) for the feature's Candidate Research
Areas and known framework themes. This is **B0 only**: it inventories surfaces
provided by the actually resolved Distage/Izumi version, independent of BeautyQ
candidate IDs. It performs no BeautyQ comparison, no classification, and no
recommendation.

## Binding and method

- `stateId`: `50e7945-clean` (see `00-selected-state.md`)
- Framework version for every surface: **`io.7mind.izumi` `1.2.25`** (declared == resolved == verified in T001).
- Testkit rows bind to the independently verified **Test-scope** `distage-testkit-scalatest_3 1.2.25`
  and its transitive `distage-testkit-core_3 1.2.25`.
- `matchedNeedIds` are intentionally left empty/unresolved at B0. B1 performs the first A↔B reconciliation.
- A B0 search miss is not a `MISSING_GENERIC_PRIMITIVE` claim; negative observations are recorded as
  bounded search basis plus residual uncertainty.

### Evidence legend

All positive evidence below is from **local exact-version source jars** already resolved for this build
(no latest/master). Cache root:

`CS = ~/Library/Caches/Coursier/v1/https/repo1.maven.org/maven2`

For an artifact `A` (e.g. `distage-core`), the evidence anchor form is:

`CS/io/7mind/izumi/A_3/1.2.25/A_3-1.2.25-sources.jar :: <scala-source-path>`

A generated, inspectable extraction (not committed, teardown-safe) was made at
`target/b0-src/A_3/<scala-source-path>`. Source-jar anchors are the durable reference.

Artifact short names used in evidence: `core`, `core-api`, `framework`, `framework-api`,
`extension-config`, `extension-plugins`, `framework-docker`, `testkit-core`,
`testkit-scalatest`, `fundamentals-bio`, `fundamentals-platform`.

The `versionRef` column is `io.7mind.izumi 1.2.25` for every row (testkit rows: verified
Test scope at the same version); each surface's `matchedNeedIds` is reconciled and populated by the B1 A↔B join
below (a surface may legitimately have none).

---

## B0a — Composition / Activation

Surface count: **9**.

| surfaceId | capability | apiNames | surfaceEvidence | limits / conditions relevant later | versionRef | matchedNeedIds |
| --- | --- | --- | --- | --- | --- | --- |
| b0a.module-compose | Combine modules/plugins into a larger module; merge/override binding sets | `ModuleBase`, `Module.make/from/empty`, `++`, `.merge`, `.overrideLeft`, `overriddenBy`, `ModuleBase.make/overrideImpl` | `core-api :: izumi/distage/model/definition/Module.scala`; `core-api :: izumi/distage/model/definition/ModuleBase.scala` | Module merge is a binding-set fold; override semantics are left/right explicit. Not a runtime graph operation. | 1.2.25 | N-C01-01 |
| b0a.include-tags | Compose a module into another with explicit tag/axis merge policy | `IncludesDSL#include`, `IncludesDSL.TagMergePolicy` (`MergePreferInner/Outer`, `UseOnlyInner/Outer`, `ReplaceWith`, `MergePreferNewWith/ExistingWith`) | `core-api :: izumi/distage/model/definition/dsl/IncludesDSL.scala` | Outer axis tags propagate into included bindings by default (1.2.9+); policies control only tag overlap behavior. | 1.2.25 | N-C01-01, N-C07-01 |
| b0a.binding-dsl | Author singleton/set/mutator bindings; name, reference, modify | `make`, `makeTrait`, `makeFactory`, `modify`, `modifyBy`, `many`, `add`, `ref`, `refSet`, `.named`, `.using`, `.from`, `Bindings.*` | `core-api :: izumi/distage/model/definition/ModuleDef.scala` (scaladoc catalog); `core-api :: izumi/distage/model/definition/dsl/ModuleDefDSL.scala`; `core-api :: izumi/distage/model/definition/dsl/AbstractBindingDefDSL.scala`; `core-api :: izumi/distage/model/definition/Bindings.scala` | DSL is the canonical authoring surface; raw constructors in `Bindings` are escape hatches. Modifier application order is unspecified. | 1.2.25 | N-C14-01, N-C15-01, N-C18-01 |
| b0a.auto-construct | Derive constructors/proxies for classes, traits, factories; structural `@impl` | `ClassConstructor`, `TraitConstructor`, `FactoryConstructor`, `With`, `@impl`, `impl` | `core-api :: izumi/distage/constructors/constructors.scala`; `core-api :: izumi/distage/model/definition/With.scala`; `core-api :: izumi/distage/model/definition/impl.scala` | `@With`/`@impl` select a more specific impl than the return type; compile-time macro-derived. | 1.2.25 | ∅ |
| b0a.plugin | Classpath plugin discovery, static loading, merge/override of plugin modules | `PluginBase`, `PluginDef`, `BootstrapPlugin`, `PluginConfig` (`cached/packages/const/…`), `PluginLoader`, `LoadedPlugins`, `PluginMergeStrategy`, `StaticPluginLoader` | `extension-plugins :: izumi/distage/plugins/PluginBase.scala`, `PluginDef.scala`, `PluginConfig.scala`, `merge/PluginMergeStrategy.scala`, `load/PluginLoader.scala`, `load/LoadedPlugins.scala`; `extension-plugins :: distage/plugins/DistagePlugins.scala` | `PluginDef` is an abstract class (compile-time checks re-run on change); `PluginBase` is a trait. `LoadedPlugins.result` = `(loaded ++ merges).merge` then `.overrideLeft` overrides. | 1.2.25 | N-C01-01 |
| b0a.role | Role-based app composition/launch; role registration and tagging | `RoleAppMain`, `RoleAppMain.LauncherBIO/LauncherCats/LauncherIdentity`, `RoleDescriptor`, `RoleModuleDef.makeRole`, `RoleTag`, `RoleService`, `RoleTask`, `RoleAppBootModule` | `framework :: izumi/distage/roles/RoleAppMain.scala`, `RoleAppBootModule.scala`; `framework-api :: izumi/distage/roles/model/RoleDescriptor.scala`, `model/AbstractRole.scala`, `model/definition/RoleModuleDef.scala`, `model/definition/RoleTag.scala` | Roles are GC roots + `RoleTag`-tagged bindings; app-specific names/descriptors live at the edge. Launcher creates boot graph under `Identity`, app under `F`. | 1.2.25 | N-C01-01 |
| b0a.activation-axis | Select implementations along named axes; standard axes and activations | `Axis`, `AxisChoice`, `Activation`, `Activation.++/+`, `StandardAxis.{Mode,Repo,World,Scene}`, `prodActivation`/`testProdActivation`/`testDummyActivation`, `AxisPoint` | `core-api :: izumi/distage/model/definition/Axis.scala`, `Activation.scala`, `StandardAxis.scala`; `core-api :: izumi/distage/model/planning/AxisPoint.scala`, `planning/ActivationChoices.scala` | Axis names/values default from class names but standard axes override. Activation is a total map Axis→choice. | 1.2.25 | N-C01-01, N-C03-01, N-C07-01, N-C13-01 |
| b0a.binding-tag | Attach arbitrary tags to bindings; confined/exposed visibility | `BindingTag`, `BindingTag.AxisTag/Confined/Exposed`, `Tagging#tagged/confined/exposed`, `TagsDSL` | `core-api :: izumi/distage/model/definition/BindingTag.scala`; `core-api :: izumi/distage/model/definition/dsl/Tagging.scala`; `core-api :: izumi/distage/model/definition/dsl/TagsDSL.scala` | Tags must be immutable products. `Confined`/`Exposed` interact with `LocatorPrivacy`; `AxisTag` drives activation. | 1.2.25 | N-C01-01, N-C07-01, N-C13-01 |
| b0a.bootstrap | Separate bootstrap module/locator for injector self-configuration | `BootstrapModule`, `BootstrapModuleDef`, `BootstrapPlugin`, `BootstrapLocator`, `BootstrapContextModule`, `BootstrapRootsMode` | `core-api :: izumi/distage/model/definition/BootstrapModule.scala`, `BootstrapModuleDef.scala`, `BootstrapContextModule.scala`; `core :: izumi/distage/bootstrap/BootstrapLocator.scala`, `bootstrap/BootstrapRootsMode.scala` | Bootstrap graph is produced before the app graph; customizable without touching the app module. | 1.2.25 | ∅ |

Bounded search basis for B0a: files enumerated under `distage-core-api`, `distage-framework(-api)`,
`distage-extension-plugins`, plus `distage-core` bootstrap/planning package listings.
Residual uncertainty: `distage-extension-plugins` classpath-scanning internals were not exhaustively read.

---

## B0b — Lifecycle / Resource Construction

Surface count: **8**.

| surfaceId | capability | apiNames | surfaceEvidence | limits / conditions relevant later | versionRef | matchedNeedIds |
| --- | --- | --- | --- | --- | --- | --- |
| b0b.lifecycle-core | Effectful acquire/release lifecycle with combinators | `Lifecycle`, `Lifecycle.make`, `make_`, `makeSimple`, `makeSimpleInit`, `makePair`, `liftF`, `pure`, `map`, `flatMap`, `evalMap`, `redeem`, `use`, `useEffect`, `unsafeAllocate`, `toEffect` | `fundamentals-bio :: izumi/functional/lifecycle/Lifecycle.scala`; `core :: distage/Distage.scala` (alias `type Lifecycle`) | Finalization runs when the owning `Locator` is released. `use` needs `QuasiPrimitives`. | 1.2.25 | N-C03-01, N-C05-01, N-C11-01, N-C12-01 |
| b0b.lifecycle-adapters | Convert foreign resource types into `Lifecycle` | `Lifecycle.fromCats`, `fromZIO`, `fromZManaged`, `fromZLayer`, `fromZLayerZEnv`, `Lifecycle.FromCats`, `Lifecycle.FromZIO` | `fundamentals-bio :: izumi/functional/lifecycle/Lifecycle.scala` | `fromCats` needs `cats.effect.Sync[F]`; ZIO scoped/resource forms supported. | 1.2.25 | ∅ |
| b0b.from-resource | Bind a value from a `Lifecycle`/`Resource` in `ModuleDef` | `.fromResource(...)` overloads (instance, `Functoid`, class-constructor), `LifecycleAdapters.AdaptFunctoid`, `LifecycleTag` | `core-api :: izumi/distage/model/definition/dsl/ModuleDefDSL.scala:259-318`; `core-api :: izumi/distage/model/definition/dsl/LifecycleAdapters.scala` | Cats `Resource`, ZIO scoped `ZIO[Scope,…]`, `ZManaged`, `ZLayer` are adapted implicitly. Adds `Sync[F]`/effect deps. | 1.2.25 | N-C03-01, N-C04-01, N-C05-01, N-C11-01, N-C12-01 |
| b0b.from-effect | Bind a value constructed by a pure/effectful function | `.fromEffect[F,I](F[I])`, `make[X].fromEffect(X.create[F])`, `ImplDef.EffectImpl` | `core-api :: izumi/distage/model/definition/dsl/ModuleDefDSL.scala:216-222`; `core-api :: izumi/distage/model/definition/dsl/AbstractBindingDefDSL.scala` | Requires an `Injector[F]` matching the effect HK type or a compatible subtype (`ProvisionerIssue.IncompatibleEffectTypes`). | 1.2.25 | N-C03-01, N-C11-01 |
| b0b.ref-resource | Reference an existing resource/effect binding, acquiring a fresh instance | `.refResource`, `.refEffect` | `core-api :: izumi/distage/model/definition/dsl/ModuleDefDSL.scala:246-257, 305-318` | Creates a NEW acquisition per reference; distinct from sharing the singleton. | 1.2.25 | N-C05-01 |
| b0b.role-lifecycle | Role-level start/shutdown lifecycle; daemon forking | `RoleService.start: Lifecycle[F, Unit]`, `RoleTask.start: F[Unit]`, `Lifecycle.fork_`, `LifecycleAggregator` | `framework-api :: izumi/distage/roles/model/AbstractRole.scala`; `fundamentals-bio :: izumi/functional/lifecycle/Lifecycle.scala:360`; `fundamentals-bio :: izumi/functional/lifecycle/LifecycleAggregator.scala` | Service acquire must be finite; shutdown belongs in the finalizer. | 1.2.25 | ∅ |
| b0b.resource-strategy | Provider strategies distinguishing instance/provider/effect/resource/reference | `ImplDef.{InstanceImpl,ProviderImpl,EffectImpl,ResourceImpl,ReferenceImpl,ContextImpl}`, `ResourceStrategy`, `EffectStrategy`, `InstanceStrategy`, `ProviderStrategy`, `ProxyStrategy`, `SetStrategy`, `SubcontextStrategy` | `core-api :: izumi/distage/model/definition/ImplDef.scala`; `core-api :: izumi/distage/model/provisioning/strategies/*.scala`; `core :: izumi/distage/provisioning/strategies/ResourceStrategyDefaultImpl.scala` | `ReferenceImpl.weak=true` is the weak-set marker (see B0c). Strategies are overridable via bootstrap module. | 1.2.25 | ∅ |
| b0b.optional-set | Optional/absent dependencies via (possibly empty) injected sets and TODOs | `many[T]`, `Bindings.emptySet`, `Set[T]` injection, `Bindings.todo`, `Functoid` | `core-api :: izumi/distage/model/definition/Bindings.scala:46-73`; `core-api :: izumi/distage/model/definition/dsl/ModuleDefDSL.scala` (`SetDSLBase`) | Empty set bindings permit zero contributions; `todo` defers failure to runtime. Weak sets are B0c. | 1.2.25 | ∅ |

Bounded search basis for B0b: `Lifecycle.scala`, `LifecycleAdapters.scala`, `ModuleDefDSL` `from*`/`ref*`
methods, `ImplDef.scala`, strategy package listings, `AbstractRole.scala`.
Residual uncertainty: some `Lifecycle` combinator/cats-instance method set was sampled, not fully read.

---

## B0c — Graph / Roots / Boundaries / Readiness

Surface count: **10**. Surface class is marked per row: **runtime** / **testkit** / **diagnostic**.

| surfaceId | class | capability | apiNames | surfaceEvidence | limits / conditions relevant later | versionRef | matchedNeedIds |
| --- | --- | --- | --- | --- | --- | --- | --- |
| b0c.roots-gc | Designate GC roots; prune non-dependent bindings | `Roots`, `Roots.Of`, `Roots.Everything`, `Roots.target`, `PlannerInput`, `Injector.produce*`, `produceGet/produceRun/produceEval` | `core-api :: izumi/distage/model/plan/Roots.scala`; `core-api :: izumi/distage/model/PlannerInput.scala`; `core :: izumi/distage/model/Injector.scala:159-230` | `Roots.Everything` disables GC and may be unsound with cycles. Empty `PlannerInput` roots means no GC. | 1.2.25 | ∅ |
| b0c.weak-set | Optional set contributions retained only when a retained component depends on the set | `.weakSet[I]`, `ImplDef.ReferenceImpl(weak=true)`, `WeakEdge`, `GraphQueries.getSetElementWeakEdges/findWeakSetMembers`, `PlanSolver` weak-edge handling | `core-api :: izumi/distage/model/definition/dsl/ModuleDefDSL.scala:468-481`; `core :: izumi/distage/planning/solver/GraphQueries.scala:80-112`; `core :: izumi/distage/planning/solver/PlanSolver.scala:35,100-102` | Weak members are dropped unless the set (or an element) is reachable from roots; used for optional/plugin-style contributions. | 1.2.25 | ∅ |
| b0c.topology-reachability | Inspect graph dependencies/reachability; trace reachable keys | `PlanTopology`, `PlanTopology.transitiveDependencies/transitiveDependees`, `DependencyGraph`, `GraphQueries`, `PlanVerifier.traceReachables` | `core-api :: izumi/distage/model/plan/topology/PlanTopology.scala`, `topology/DependencyGraph.scala`, `topology/DepTreeNode.scala`; `core :: izumi/distage/planning/solver/GraphQueries.scala`; `core :: izumi/distage/planning/solver/PlanVerifier.scala:101-137` | Reachability tracing is the raw mechanism behind GC/plan checks; transitive queries are documented as relatively expensive. | 1.2.25 | ∅ |
| b0c.plan-verify | Runtime graph/construction validation without instantiating | `Injector.verify`, `Injector.assert`, `PlanVerifier.verify`, `PlanVerifierResult.{Correct,Incorrect}`, `PlanIssue`, `PlanVerificationException` | `core :: izumi/distage/model/Injector.scala:252-302`; `core :: izumi/distage/planning/solver/PlanVerifier.scala`; `core-api :: izumi/distage/model/planning/PlanIssue.scala`; `core-api :: izumi/distage/model/exceptions/PlanVerificationException.scala` | Detects missing imports, unsaturated/shadowed/conflicting/duplicate activations, incompatible effect types, unsolvable conflicts. Does not execute provisioning. | 1.2.25 | ∅ |
| b0c.plan-check | Compile-time (macro) and runtime full-app wiring check | `PlanCheck`, `PlanCheck.Main`, `PlanCheck.runtime.assertApp/checkApp`, `PlanCheckMaterializer`, `CheckableApp`, `CoreCheckableApp(Simple)`, `RoleCheckableApp`, `PlanCheckConfig`, `PlanCheckResult` | `framework :: izumi/distage/framework/PlanCheck.scala`, `PlanCheckMaterializer.scala`, `CheckableApp.scala`, `PlanCheckConfig.scala`, `model/PlanCheckInput.scala`, `model/PlanCheckResult.scala`; `framework :: izumi/distage/framework/model/exceptions/PlanCheckException.scala` | Materializer drives compile-time check; runtime re-check available. Role apps inherit `RoleCheckableApp`; core apps implement `CoreCheckableApp(Simple)`. | 1.2.25 | ∅ |
| b0c.integration-check | Construction-boundary proof for external resources; readiness gating | `IntegrationCheck`, `AbstractCheck`, `IntegrationCheck.resourcesAvailable(): F[ResourceCheck]`, `ResourceCheck` (`Success`/`Failure`), `IntegrationCheckException`, `ProvisionerIssue.IntegrationCheckFailure`, `NonCriticalIntegrationFailure` | `core-api :: izumi/distage/model/provisioning/IntegrationCheck.scala`, `provisioning/AbstractCheck.scala`; `core-api :: izumi/distage/model/exceptions/runtime/IntegrationCheckException.scala`, `runtime/NonCriticalIntegrationFailure.scala`; `fundamentals-platform :: izumi/fundamentals/platform/integration/ResourceCheck.scala`; `core-api :: izumi/distage/model/definition/errors/ProvisionerIssue.scala` | Producer runs checks; a `Failure` gates startup (role launcher) or skips the test (testkit). Method must not throw. | 1.2.25 | N-C03-01, N-C09-01 |
| b0c.sanity-cycles | Planner sanity checks and circular-dependency handling | `SanityChecker`, `SanityCheckerDefaultImpl`, `PlanCircularDependencyCheck`, `Cycles` axis (`Proxy`/`Byname`/`Disable`), `CycleTools`, `FwdrefLoopBreaker` | `core-api :: izumi/distage/model/planning/SanityChecker.scala`; `core :: izumi/distage/planning/sequential/SanityCheckerDefaultImpl.scala`, `sequential/CycleTools.scala`, `sequential/FwdrefLoopBreaker.scala`; `framework :: izumi/distage/framework/services/PlanCircularDependencyCheck.scala`; `core :: izumi/distage/bootstrap/Cycles.scala` | `Cycles` axis selects by-name vs bytebuddy proxy cycle resolution; `Disable` fails fast. | 1.2.25 | ∅ |
| b0c.locator-introspect | Inspect a produced object graph and its finalizers | `Locator`, `Locator.instances/index/allInstances/finalizers`, `lookupRef*`, `run/runOption`, `LocatorMeta`, `OpStatus` | `core-api :: izumi/distage/model/Locator.scala`; `core-api :: izumi/distage/model/provisioning/ProvisioningFailure.scala:10-20` | `instances/index` are local-only; `allInstances` walks parents. `run` fills a `Functoid` from the graph. | 1.2.25 | ∅ |
| b0c.diagnostic | Graph dump/plan rendering and debug configuration | `GraphDumpBootstrapModule`, `GraphDumpObserver`, `PlanningObserver`, `PlanningHook`, `DepTreeRenderer`, `DIPlanCompactFormatter`, `DIRendering`, `locatorPrivacy`/`LocatorPrivacy` | `core :: izumi/distage/planning/extensions/GraphDumpBootstrapModule.scala`, `extensions/GraphDumpObserver.scala`; `core-api :: izumi/distage/model/planning/PlanningObserver.scala`, `planning/PlanningHook.scala`; `core-api :: izumi/distage/model/plan/repr/DepTreeRenderer.scala`, `repr/DIPlanCompactFormatter.scala`, `repr/DIRendering.scala`; `core-api :: izumi/distage/model/definition/LocatorPrivacy.scala` | Diagnostic surface; `LocatorPrivacy` also controls confined/exposed visibility at runtime. | 1.2.25 | ∅ |
| b0c.testkit-roots | Testkit surface selecting per-test roots and shared roots | `TestConfig.memoizationRoots: PriorAxisDIKeys`, `forcedRoots: AxisDIKeys`, `AxisDIKeys.getActiveKeys`, `DistageTest`, `TestEnvironment`, `TestConfig.activation/pluginConfig` | `testkit-core :: izumi/distage/testkit/model/TestConfig.scala`; `testkit-core :: izumi/distage/testkit/model/TestEnvironment.scala`, `model/DistageTest.scala` | **Testkit surface**, Test scope 1.2.25. Memoization roots are created once per memoization environment; forced roots are retained even without dependents. Test parameters are implicit per-test GC roots. | 1.2.25 (Test) | ∅ |

Bounded search basis for B0c: `Roots.scala`, `PlanVerifier.scala`, `GraphQueries.scala`, `PlanSolver.scala`,
`IntegrationCheck.scala`, `PlanCheck*.scala`, `CheckableApp.scala`, `Locator.scala`, `TestConfig.scala`,
plus recursive greps for `WeakSet`/`weak` (only weak-set DSL/plan-edges found).
Residual uncertainty: `GenericSemigraphTraverse` and traversal internals were not fully read; no
dedicated "generic status/degradation" framework trait was found (only `IntegrationCheck`/`ResourceCheck`
and `NonCriticalIntegrationFailure`), recorded as a bounded miss, not an absence claim.

---

## B0d — Config / Docker / Testkit / Effect / Ownership

Surface count: **9**.

| surfaceId | capability | apiNames | surfaceEvidence | limits / conditions relevant later | versionRef | matchedNeedIds |
| --- | --- | --- | --- | --- | --- | --- |
| b0d.config-def | Bind typed config values into the graph | `ConfigModuleDef`, `makeConfig`, `makeConfigNamed`, `makeConfigWithDefault`, `wireConfig`, `wireConfigWithDefault`, `.fromConfig`, `ConfTag`, `AppConfig`, `DIConfigReader`, `DIConfigMeta` | `extension-config :: izumi/distage/config/ConfigModuleDef.scala`, `model/ConfTag.scala`, `model/AppConfig.scala`, `codec/DIConfigReader.scala`, `codec/DIConfigMeta.scala`; `extension-config :: distage/config/DistageConfig.scala` | Config bindings are tagged (`ConfTag`) so `PlanCheck` can validate them. Reader/meta typeclasses drive decoding. | 1.2.25 | N-C02-01 |
| b0d.config-loader | Load/merge config from CLI, files, and reference resources | `ConfigLoader`, `AbstractConfigLoader`, `ConfigArgsProvider`, `ConfigMerger`, `ConfigFilteringStrategy`, `ConfigLocationProvider`, `AppConfigModule` | `framework :: izumi/distage/framework/services/ConfigLoader.scala`, `services/ConfigArgsProvider.scala`, `services/ConfigMerger.scala`, `services/ConfigFilteringStrategy.scala`, `services/ConfigLocationProvider.scala`, `services/AbstractConfigLoader.scala`; `extension-config :: izumi/distage/config/AppConfigModule.scala` | Default resource names (`application.conf`, `common.conf`, `*-reference[,-dev].conf`, role-name configs) and precedence are documented on `ConfigLoader`. | 1.2.25 | ∅ |
| b0d.docker-container | Declare and bind Docker containers as managed resources | `ContainerDef`, `ContainerDef.make[F]`, `ContainerDef#Container/Config`, `DockerContainer.resource`, `ContainerResource`, `DockerSupportModule`, `Docker.ContainerConfig`, `DockerReusePolicy` | `framework-docker :: izumi/distage/docker/ContainerDef.scala`, `DockerContainer.scala:38-*`, `impl/ContainerResource.scala`, `modules/DockerSupportModule.scala`, `model/Docker.scala` | `ContainerDef`s must be top-level objects. `ContainerResource` extends `Lifecycle.Basic`, so acquisition starts the container and release stops it. Requires `QuasiIO`/`QuasiAsync`/`QuasiTemporal`. | 1.2.25 | N-C04-01 |
| b0d.docker-health | Container readiness health checks | `ContainerHealthCheck`, `HealthCheckResult`, `ContainerHealthCheck.portCheck/httpGetCheck/httpsGetCheck/postgreSqlProtocolCheck/exitCodeCheck/succeed`, `ContainerHealthCheckBase`, bundled checks | `framework-docker :: izumi/distage/docker/healthcheck/ContainerHealthCheck.scala`, `healthcheck/ContainerHealthCheckBase.scala`, `healthcheck/TCPContainerHealthCheck.scala`, `healthcheck/HttpGetCheck.scala`, `healthcheck/PostgreSqlProtocolCheck.scala`, `healthcheck/ExitSuccessCheck.scala` | `exitCodeCheck` requires `autoRemove=false`. Health checks gate container-resource acquisition. | 1.2.25 | N-C04-01 |
| b0d.testkit-config | Configure test environments, memoization, parallelism, activation | `TestConfig`, `Parallelism`, `AxisDIKeys`, `PriorAxisDIKeys`, `TestActivationStrategy`, `TestEnvironment`, `TestConfigLoader`, `BootstrapFactory` | `testkit-core :: izumi/distage/testkit/model/TestConfig.scala`, `model/TestActivationStrategy.scala`, `runner/impl/services/TestConfigLoader.scala`, `runner/impl/services/BootstrapFactory.scala` | **Testkit surface**, Test scope 1.2.25. Distinct `TestConfig` (plugins/activation/roots) creates a distinct memoization environment. | 1.2.25 (Test) | ∅ |
| b0d.testkit-scalatest | Scalatest integration: effect specs and wiring specs | `Spec1/Spec2/SpecZIO/SpecIdentity`, `AbstractDistageSpec`, `TestRegistration`, `TestConfiguration`, `SpecWiring`, `WiringAssertions`, `DistageScalatestTestSuiteRunner`, `AssertZIO/AssertCIO/AssertIO2` | `testkit-scalatest :: izumi/distage/testkit/scalatest/SpecWiring.scala`, `Spec1.scala`, `Spec2.scala`, `SpecZIO.scala`, `SpecIdentity.scala`, `WiringAssertions.scala`; `testkit-scalatest :: org/scalatest/distage/DistageScalatestTestSuiteRunner.scala`; `testkit-core :: izumi/distage/testkit/spec/AbstractDistageSpec.scala`, `spec/TestRegistration.scala`, `spec/TestConfiguration.scala` | **Testkit surface**, Test scope 1.2.25. `SpecWiring` asserts `PlanCheckMaterializer` at compile-time and optionally re-checks at runtime. | 1.2.25 (Test) | ∅ |
| b0d.bio-effect | Bifunctor effect hierarchy and quasi typeclasses | `IO2`, `Async2`, `Concurrent2`, `Bracket2`, `Panic2`, `Temporal2`, `Fork2`, `Primitives2`, `QuasiIO`, `QuasiPrimitives`, `QuasiAsync`, `QuasiTemporal`, `QuasiApplicative`, `Root` summoner | `fundamentals-bio :: izumi/functional/bio/IO2.scala`, `Async2.scala`, `Concurrent2.scala`, `Bracket2.scala`, `Panic2.scala`, `Temporal2.scala`, `Root.scala`, `package.scala`; `fundamentals-bio :: izumi/functional/quasi/QuasiIO.scala`, `quasi/QuasiAsync.scala` | `BIO` hierarchy is bifunctor (`F[+_,+_]`); quasi typeclasses adapt `Identity`, cats-effect, ZIO, Monix. Distage provisioning uses `QuasiIO[F]`. | 1.2.25 | ∅ |
| b0d.default-module | Framework-provided default effect/environment bindings | `DefaultModule[F]`, `DefaultModule2[F]`, `DefaultModule3[F]`, platform support modules (`ZIOSupportModule`, `CatsIOSupportModule`, `MonixBIOSupportModule`, `IdentitySupportModule`) | `core :: izumi/distage/modules/DefaultModule.scala`; `core :: izumi/distage/modules/support/*.scala`, `modules/platform/*.scala`; `core :: distage/Distage.scala` | Supplies effect instances/lifecycles by effect type; role launcher takes a `DefaultModule[F]`. | 1.2.25 | ∅ |
| b0d.ownership-aliases | Curated public aliases, naming, visibility, module aggregation | `distage.Distage`/`distage` package object aliases, `distage.config.DistageConfig`, `distage.plugins.DistagePlugins`, `Id`, `@Id`, `.named`, `.confined/.exposed`, `LocatorPrivacy.{PublicByDefault,PrivateByDefault,PublicRoots}`, `DefaultModule*` | `core :: distage/Distage.scala`, `distage/package.scala`; `extension-config :: distage/config/DistageConfig.scala`; `extension-plugins :: distage/plugins/DistagePlugins.scala`; `core-api :: izumi/distage/model/definition/Id.scala`, `dsl/Tagging.scala`, `LocatorPrivacy.scala` | These are stable public entry points/aliases; app-specific names/defaults belong to the app layer. `LocatorPrivacy` affects whether non-root bindings are visible in the locator. | 1.2.25 | ∅ |

Bounded search basis for B0d: config/framework service listings and files, docker file listing and key
files, testkit-core/testkit-scalatest file listings and key spec files, `fundamentals-bio` listing,
`DefaultModule.scala`, and `distage` package aliases.
Residual uncertainty: bundled docker image defs and some BIO algebras (RetryPolicy, Temporal, Fork
implementations) were only enumerated by name, not read in depth.

---

## B0 negative observations (bounded)

- **No dedicated generic runtime "status/degradation" trait** found in the inspected 1.2.25 surfaces;
  the closest framework mechanisms are `IntegrationCheck`/`ResourceCheck` (readiness gate) and
  `NonCriticalIntegrationFailure` (non-fatal integration error marker). Basis: recursive grep over the
  extracted `core`, `core-api`, `framework`, `framework-docker`, `testkit-core` sources for
  status/degradation-like symbols plus manual reads of the listed files. Residual uncertainty: a
  differently named mechanism could exist in an uninspected module; this is **not** recorded as an
  absence/missing-primitive claim.
- **`weakSet` is the only explicit "weak" DSL surface** found; the underlying marker is
  `ImplDef.ReferenceImpl(weak=true)`. Basis: recursive grep for `WeakSet`/`weak` across `core-api`/`core`.

All positive claims above bind to `io.7mind.izumi 1.2.25` (Testkit rows additionally to verified
Test-scope 1.2.25). No latest/master source was used. `matchedNeedIds` were left empty at B0 and
are populated by the B1 A↔B join in the next section.

---

## B1 — A Need Reconciliation / Focused Gap Fill

B1 performs the first A↔B join. It reconciles **every** Need defined in
`01-beautyq-candidate-map.md` against the 36 B0 surfaces above and records only the focused
exact-version lookups that B0 could not answer. This is reconciliation only: no Phase-C/E label,
no verdict, no recommendation, and no discoverability judgment appears here. The values
`MATCHED` / `SEARCH_BOUNDED_ABSENCE` / `BLOCKED_NEED_EVIDENCE` are B1 table semantics only, not
Phase-E classification labels.

For `MATCHED` rows, a `coverage` field distinguishes `FULL` (the referenced surface(s) materially
represent the neutral capability described by the Need as a whole) from `PARTIAL` (the surface(s)
genuinely cover a meaningful sub-capability while one or more material parts of the Need remain
domain-owned/uncovered). Every `PARTIAL` row records that remainder in `uncoveredRemainder`, which
is known semantic non-coverage, not evidence uncertainty; `residualEvidenceGap` is reserved for
actual evidence uncertainty where a further lookup could change the mapping. Binding an arbitrary
Scala value through DI is not by itself a `FULL` match for a Need whose material semantics are
policy/declaration behavior, fingerprinting, domain evaluation outcome, status algebra, public
response projection, build-ownership rule, or product-specific error classification.

Binding for every row below: `stateId` = `50e7945-clean`; `versionRef` = `io.7mind.izumi` `1.2.25`.

### B1 gap-fill surfaces

Need-driven discoveries only (a real 1.2.25 surface missing from B0 that an actual Need required).
No broad framework reinventory was performed. Artifact short names and `CS` cache root are as in the
Evidence legend above.

| surfaceId | capability | apiNames | exact version evidence | limits / conditions | versionRef | matchedNeedIds |
| --- | --- | --- | --- | --- | --- | --- |
| b1.lifecycle.dependency-edge | Add an explicit DI dependency/ordering edge to a binding without a constructor parameter | `AbstractBindingDefDSL.AddDependencyDSL#addDependency[B]`, `addDependency(key: DIKey)`, `addDependencies(keys)`, `ModifyDSLBase#addDependency`, `ModifyTaggingDSL#addDependency`; contrast `.using`/`.ref` | `CS/io/7mind/izumi/distage-core-api_3/1.2.25/distage-core-api_3-1.2.25-sources.jar :: izumi/distage/model/definition/dsl/AbstractBindingDefDSL.scala:236-248, 306-329`; `core-api :: izumi/distage/model/definition/dsl/ModuleDefDSL.scala:202-206` | Adds a key as a dependency of the modified binding; unlike `.using` it does not rebind the target key, so it expresses a pure ordering/readiness edge. The framework itself uses a `@unused` constructor parameter as an intentional `IntegrationCheck` edge (`distage-framework-docker :: izumi/distage/docker/impl/DockerClientWrapper.scala:117`). | 1.2.25 | N-C03-01, N-C05-01 |
| b1.config.typed-decode-error | Typed error surfaced when config decoding fails at a construction boundary | `DIConfigReadException`, `DIConfigReader.decodeConfig/decodeConfigWithDefault` (private `unpackResult`), `ConfigModuleDef.makeConfig/.fromConfig` | `CS/io/7mind/izumi/distage-extension-config_3/1.2.25/distage-extension-config_3-1.2.25-sources.jar :: izumi/distage/config/codec/DIConfigReader.scala:85-100`; `extension-config :: izumi/distage/config/model/exceptions/DIConfigReadException.scala:5`; `extension-config :: izumi/distage/config/ConfigModuleDef.scala:16-31,45-56` | A failed decode is wrapped in `DIConfigReadException` (`extends DIException`). This is a decode error, not a general domain-validation hook. | 1.2.25 | N-C02-01 |

### Reconciliation rows

Exactly one row per Need. `matchBasis` is required for `MATCHED`; `coverage` (`FULL`/`PARTIAL`) is
required for `MATCHED`, and `uncoveredRemainder` is required for `PARTIAL`; `absenceBasis` is
recorded when no matching surface was established; `residualEvidenceGap` is recorded only for
actual evidence uncertainty that a further lookup could resolve.

| needId | candidateId | evidenceState | coverage | matchedSurfaceIds | matchBasis | uncoveredRemainder | absenceBasis | residualEvidenceGap |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| N-C01-01 | C-01 | MATCHED | FULL | b0a.module-compose, b0a.include-tags, b0a.activation-axis, b0a.binding-tag, b0a.plugin, b0a.role | Module/plugin/role composition plus `Activation`/`Axis` and `AxisTag` binding tags combine a feature's modules into an application graph and select a variant along an activation axis. | — | — | — |
| N-C02-01 | C-02 | MATCHED | PARTIAL | b0d.config-def, b1.config.typed-decode-error | Config bindings decode through `DIConfigReader`/`ConfigModuleDef`, whose failure surfaces a typed `DIConfigReadException` at the construction boundary. | Arbitrary typed domain validation (validation policy/derivation) and its domain-specific typed error classification remain domain-owned; `DIConfigReadException` is a config-decode error, not a general domain-validation analogue. | — | — |
| N-C03-01 | C-03 | MATCHED | FULL | b0b.lifecycle-core, b0b.from-resource, b0b.from-effect, b0a.activation-axis, b0c.integration-check, b1.lifecycle.dependency-edge | A `Lifecycle`/`.fromResource` binding provides readiness-dependent resource construction; `Axis`/`Activation` select mode/scene variants; `IntegrationCheck` gates readiness; `addDependency` expresses the ordering/readiness edge. | — | — | — |
| N-C04-01 | C-04 | MATCHED | FULL | b0d.docker-container, b0d.docker-health, b0b.from-resource | `ContainerDef`/`ContainerResource` (extends `Lifecycle.Basic`) binds started containers; `DockerContainer.availablePorts` exposes started-container ports; `ContainerHealthCheck` gates acquisition. | — | — | — |
| N-C05-01 | C-05 | MATCHED | FULL | b0b.lifecycle-core, b0b.from-resource, b0b.ref-resource, b1.lifecycle.dependency-edge | DI dependency edges order construction; `addDependency`/`addDependencies` add an ordering edge to a binding without a constructor parameter, and the framework itself carries an intentional `@unused` integration-check edge. | — | — | — |
| N-C06-01 | C-06 | SEARCH_BOUNDED_ABSENCE | — | — | — | — | No dedicated 1.2.25 runtime serving/degradation-state or immutable-startup-evidence surface was established. Focused exact-version grep over `core`, `core-api`, `framework`, `framework-docker`, `testkit-core` for status/degradation/readiness symbols found only test-runner `TestStatus` and provisioning `OpStatus`. The nearest framework mechanism is b0c.integration-check (a readiness gate), a different abstraction from application-level serving status/evidence projection. | A differently named framework mechanism could still represent app serving status; unresolved. |
| N-C07-01 | C-07 | MATCHED | PARTIAL | b0a.activation-axis, b0a.binding-tag, b0a.include-tags | `Axis`/`AxisChoice`/`Activation` select typed alternatives along a named axis, and `AxisTag`/`IncludesDSL` tag-merge policy carries the policy/dispatch variation through module composition. | The stable-code vocabulary, explicit ordering, default selection, and strict parser semantics (rejecting empty/whitespace/unknown codes) remain domain-owned; the framework covers only typed axis/variant selection and composition. | — | — |
| N-C08-01 | C-08 | SEARCH_BOUNDED_ABSENCE | — | — | — | — | No dedicated 1.2.25 runtime serving-status aggregation surface was established; nearest is b0c.integration-check. Aggregating policy + mode + condition + typed reason + source identity into a serving status is not a Distage mechanism. | Same residual uncertainty as N-C06-01. |
| N-C09-01 | C-09 | MATCHED | PARTIAL | b0c.integration-check | b0c.integration-check includes `NonCriticalIntegrationFailure` (and `IntegrationCheckException`), the framework's hard-vs-non-critical failure distinction; `distage-framework-docker` mixes `NonCriticalIntegrationFailure` into `DockerException`. | The general runtime/startup failure classifier, the stable degradation-code vocabulary, and the append budget remain domain-owned; the framework marker distinguishes integration/readiness failures only. | — | — |
| N-C10-01 | C-10 | SEARCH_BOUNDED_ABSENCE | — | — | — | — | No 1.2.25 framework surface translates reserved resource names into typed lifecycle/service configurations with typed config errors. `.fromResource` (b0b.from-resource) only binds resource acquisition, and config decoding (b0d.config-def, b1.config.typed-decode-error) surfaces HOCON decode errors (`DIConfigReadException`), not local typed config-construction errors; those nearest surfaces are abstraction-mismatched. | A differently named surface that abstracts resource-name→typed-config translation could exist in an uninspected module; unresolved. |
| N-C11-01 | C-11 | MATCHED | PARTIAL | b0b.lifecycle-core, b0b.from-resource, b0b.from-effect | `Lifecycle` combinators plus `fromResource`/`fromEffect` bindings compose effectful resource construction and release, covering the resource/effect-composition portion. | Aggregating multi-backend activation into one aggregate with optional components (an optional field/result, not an injected empty/set contribution) and the typed partial-failure outcome remain domain-owned. | — | — |
| N-C12-01 | C-12 | MATCHED | PARTIAL | b0b.lifecycle-core, b0b.from-resource | `Lifecycle`/`.fromResource` builds and owns a reusable resource lazily, covering the resource-ownership/lifecycle shape; the Docker-specific `b0d.docker-container` is not counted because the baseline service is not Docker-backed. | Resource-name→config translation, request batching, and the single BeautyQ-owned execution path remain domain-owned. | — | — |
| N-C13-01 | C-13 | MATCHED | PARTIAL | b0a.activation-axis, b0a.binding-tag | `Activation`/`Axis` selects between alternative implementations at construction time, and binding tags carry the selection. | Routing execution between the full and reduced capability paths and the private capability ADT remain domain-owned; the local mechanism does not use injected set optionality, so `b0b.optional-set` is not counted. | — | — |
| N-C14-01 | C-14 | MATCHED | PARTIAL | b0a.binding-dsl | The `ModuleDef` binding DSL is the canonical surface for binding a complete policy value into the graph. | The executable-declaration semantics, its derived contributions, and the contract fingerprint remain domain-owned (the generic `search-gen2-*` kernel is repository-owned, not a Distage surface). | — | — |
| N-C15-01 | C-15 | MATCHED | PARTIAL | b0a.binding-dsl | The `ModuleDef` binding DSL binds a vector-store policy value as an injectable declaration. | The executable-declaration semantics of the policy (declaration-owned identity/contributions/views) remain domain-owned; the framework covers only that the policy object is bindable/injectable. | — | — |
| N-C16-01 | C-16 | SEARCH_BOUNDED_ABSENCE | — | — | — | — | No 1.2.25 framework surface for owning a namespace of external physical/virtual resource names was established. b0d.ownership-aliases (`Id`, `.named`) concerns DI identifiers/visibility, a different abstraction. | A differently named framework surface for resource-name derivation is unresolved. |
| N-C17-01 | C-17 | SEARCH_BOUNDED_ABSENCE | — | — | — | — | Distage provides no generic backend request compiler/response decoder; the generic `search-gen2-*` kernel is repository-owned. b0a.binding-dsl can bind such a compiler as a value but does not itself compile/decode. | — |
| N-C18-01 | C-18 | MATCHED | PARTIAL | b0a.binding-dsl | The binding DSL binds the candidate pipeline to policy/provenance values into the DI graph. | Coupling evaluation to an eligible/ineligible domain outcome remains domain-owned; the framework covers DI assembly only. | — | — |
| N-C19-01 | C-19 | SEARCH_BOUNDED_ABSENCE | — | — | — | — | Distage plan surfaces (b0c.plan-verify, b0c.plan-check) address DI graph construction/verification, not domain search-plan compilation. No framework analogue for the local plan-compilation composition was established. | — |
| N-C20-01 | C-20 | SEARCH_BOUNDED_ABSENCE | — | — | — | — | No generic 1.2.25 framework surface for projecting a domain execution result into a public response was established. The nearest framework surface is b0c.diagnostic (`DepTreeRenderer`/`DIPlanCompactFormatter`/`DIRendering`), which renders DI-graph diagnostics, a different abstraction/domain. | A differently named framework surface could represent the diagnostic-trace portion; unresolved. |
| N-C21-01 | C-21 | SEARCH_BOUNDED_ABSENCE | — | — | — | — | `ModuleDef` is an executable DI declaration, but no framework surface for a product-domain executable contract/declaration tree with derived structure/views was established (the `search-gen2-*` declaration DSL is repository-owned). | — |
| N-C22-01 | C-22 | SEARCH_BOUNDED_ABSENCE | — | — | — | — | No framework surface for source-table snapshot transactions, referential validation, or fingerprinting was established; b0b.from-resource only binds resource acquisition. | — |
| N-C23-01 | C-23 | SEARCH_BOUNDED_ABSENCE | — | — | — | — | No framework surface for order-independent canonical content fingerprinting of domain values was established. | — |
| N-C24-01 | C-24 | SEARCH_BOUNDED_ABSENCE | — | — | — | — | No framework surface for projected-document fingerprinting or materialization was established; the materializer is the repository-owned `search-gen2-*` kernel. | — |
| N-C25-01 | C-25 | SEARCH_BOUNDED_ABSENCE | — | — | — | — | No framework surface for domain join/invariant/schema error accumulation without fail-fast was established. The nearest framework aggregation (`DIError.aggregateErrors`, `ProvisioningFailure.AggregateFailure`) aggregates DI planning/provisioning errors, a different domain. | — |
| N-C26-01 | C-26 | SEARCH_BOUNDED_ABSENCE | — | — | — | — | No framework surface for a canonical navigation entry point binding typed domain handlers to a declaration was established; b0a.module-compose/b0a.binding-dsl compose DI bindings, not a domain navigation facade. | — |
| N-C27-01 | C-27 | SEARCH_BOUNDED_ABSENCE | — | — | — | — | No framework surface expresses sbt build/module dependency-DAG ownership edges. b0c.plan-check/plan-verify address DI-graph correctness, not build dependency policy; `fundamentals-platform` `BuildAttributes` only reads build metadata. | — |
| N-C28-01 | C-28 | SEARCH_BOUNDED_ABSENCE | — | — | — | — | No framework surface enforces main-source neutrality against domain-name leakage; this is a repository-owned test mechanic. | — |
| N-C29-01 | C-29 | SEARCH_BOUNDED_ABSENCE | — | — | — | — | No framework surface confines a storage/SQL capability to a single module. b0c.diagnostic `LocatorPrivacy` governs DI runtime visibility, not source/module confinement. | — |
| N-C30-01 | C-30 | SEARCH_BOUNDED_ABSENCE | — | — | — | — | No framework surface enforces package ownership; this is a repository-owned test mechanic. | — |
| N-C31-01 | C-31 | SEARCH_BOUNDED_ABSENCE | — | — | — | — | No framework surface enforces build-dependency isolation of evaluation/test modules; this is a repository build/test mechanic. | — |
| N-C32-01 | C-32 | SEARCH_BOUNDED_ABSENCE | — | — | — | — | No framework surface enforces root aggregate coverage of retained modules; this is a repository build/test mechanic. | — |

### Focused gap fills (actual bounded lookups)

Each entry is an actual Need-driven exact-version lookup performed because B0 did not adequately
answer the Need. No broad theme inventory was reopened; only `io.7mind.izumi` `1.2.25` local
source-jar extractions were inspected.

| lookup | Need(s) | bounded search scope | result | exact 1.2.25 evidence |
| --- | --- | --- | --- | --- |
| LF-1 | N-C05-01, N-C03-01 | `distage-core-api` DSL: grep for ordering/dependency methods (`addDependency`, `dependsOn`, `after`, `order`) and read `ModuleDefDSL`/`AbstractBindingDefDSL`; read `DockerClientWrapper.scala` | Found `addDependency`/`addDependencies` (`AddDependencyDSL`) as a dedicated ordering-edge mechanism, and a framework-internal `@unused` integration-check edge. Added `b1.lifecycle.dependency-edge`. | `core-api :: .../dsl/AbstractBindingDefDSL.scala:236-248,306-329`; `framework-docker :: .../impl/DockerClientWrapper.scala:117` |
| LF-2 | N-C02-01, N-C10-01 | `distage-extension-config` config codec + exceptions: read `DIConfigReader.scala`, `ConfigModuleDef.scala`, `DIConfigReadException.scala` | Found typed decode error `DIConfigReadException` surfaced by `DIConfigReader.unpackResult`. Added `b1.config.typed-decode-error`. | `extension-config :: .../codec/DIConfigReader.scala:85-100`; `.../config/model/exceptions/DIConfigReadException.scala:5` |
| LF-3 | N-C06-01, N-C08-01, N-C09-01, N-C20-01 | grep `Degrad|ServingStatus|HealthStatus|Readiness|readiness` and `(class|trait|object|enum) *Status*` over extracted `core`, `core-api`, `framework`, `framework-docker`, `testkit-core` | No generic runtime serving/degradation/readiness-state trait. Only test-runner `TestStatus`, provisioning `OpStatus`, docker `ContainerState`, platform `BuildStatus`/`GitStatus`. Nearest is b0c.integration-check. | `testkit-core :: .../model/TestStatus.scala:12`; `core-api :: .../provisioning/ProvisioningFailure.scala:10` |
| LF-4 | N-C04-01 | grep `availablePorts|ContainerResource|IntegrationCheck` over `distage-framework-docker` | Confirms B0d: `DockerContainer.availablePorts` (from started container), `ContainerResource extends Lifecycle.Basic`, `DockerIntegrationCheck` readiness gate, bundled health checks. No new surface added. | `framework-docker :: .../DockerContainer.scala:23,50`; `.../impl/ContainerResource.scala:32`; `.../impl/DockerClientWrapper.scala:96-110` |
| LF-5 | N-C21-01, N-C22-01, N-C23-01, N-C24-01, N-C25-01, N-C26-01 | grep `fingerprint|materializ|snapshot|canonical|projection|provenance|degrad` over all extracted 1.2.25 sources | No domain fingerprint/snapshot/projection/materialization surface; matches were macro `CodePositionMaterializer` and DI error aggregation (`DIError.aggregateErrors`). Recorded as bounded absence. | `core-api :: .../definition/errors/DIError.scala:20-42` (nearest aggregation only) |
| LF-6 | N-C27-01, N-C28-01, N-C29-01, N-C30-01, N-C31-01, N-C32-01 | grep `sbt|build.sbt|module ?dag|aggregate|firewall|package ownership|dependency isolation|neutrality` over all extracted 1.2.25 sources | No build/module ownership-enforcement surface; matches were testkit sbt-runner heuristics and `fundamentals-platform` build-metadata readers (`BuildAttributes`). PlanCheck is DI-graph-only. Recorded as bounded absence. | `fundamentals-platform :: .../build/BuildAttributes.scala:25`; `testkit-scalatest :: org/scalatest/distage/DistageScalatestTestSuiteRunner.scala` |

### B1 bounded non-match evidence (not Phase-E missing-primitive conclusions)

- B1 records **bounded non-match evidence**: for a Need marked `SEARCH_BOUNDED_ABSENCE`, no adequate
  framework surface was established within the bounded exact-version search. This is consistent with
  those rows and does **not** assert that Distage is missing a generic primitive; that would be a
  Phase-E conclusion and is out of scope here.
- No generic 1.2.25 runtime status/degradation/readiness-state trait was established beyond
  `IntegrationCheck`/`ResourceCheck` and `NonCriticalIntegrationFailure` (consistent with the B0
  negative observation). This is a bounded search miss, not an absence claim.
- `addDependency` is the only explicit ordering-edge DSL found beyond declaring a dependency via a
  constructor parameter, `.using`/`.ref` (which rebind), or a framework-internal `@unused` edge.

### Join integrity check (mechanical)

- Unique Need IDs defined in `research/01`: **32** (`N-C01-01`..`N-C32-01`).
- Unique Need IDs represented in the B1 reconciliation table: **32**.
- Need rows missing from B1: **0**.
- Need IDs appearing in B1/`matchedNeedIds` but absent from `research/01`: **0**.
- `matchedSurfaceId`s referenced by B1 all resolve to a defined B0 or B1 surface: **0 dangling**.
- All `matchedNeedIds` values on surface rows resolve to a Need in `research/01`: **0 dangling**.
- Every `MATCHED` row carries `coverage` (`FULL` or `PARTIAL`); every `PARTIAL` row has a
  non-empty `uncoveredRemainder`; every `SEARCH_BOUNDED_ABSENCE` row has empty `matchedSurfaceIds`.
- B0-only matched Needs (entire match set is B0 surfaces, no gap-fill surface): **10** —
  `N-C01-01`, `N-C04-01`, `N-C07-01`, `N-C09-01`, `N-C11-01`, `N-C12-01`, `N-C13-01`, `N-C14-01`,
  `N-C15-01`, `N-C18-01`.
- Needs requiring focused B1 lookup: **22** = 19 `SEARCH_BOUNDED_ABSENCE` + 3 `MATCHED` assisted by a
  B1 surface (`N-C02-01`, `N-C03-01`, `N-C05-01`).
- B1 gap-fill surfaces added: **2** (`b1.lifecycle.dependency-edge`, `b1.config.typed-decode-error`).
- Needs with `SEARCH_BOUNDED_ABSENCE`: **19**.
- Needs with `BLOCKED_NEED_EVIDENCE`: **0**.
- Needs `MATCHED`: **13** = **4** `FULL` (`N-C01-01`, `N-C03-01`, `N-C04-01`, `N-C05-01`) + **9**
  `PARTIAL` (`N-C02-01`, `N-C07-01`, `N-C09-01`, `N-C11-01`, `N-C12-01`, `N-C13-01`, `N-C14-01`,
  `N-C15-01`, `N-C18-01`).
- B0 surfaces with non-empty `matchedNeedIds`: **15**; B1 surfaces with non-empty `matchedNeedIds`:
  **2**; B1 surfaces retained but currently unmatched: **0**.

Cross-check: 4 `FULL` + 9 `PARTIAL` + 19 `SEARCH_BOUNDED_ABSENCE` + 0 `BLOCKED_NEED_EVIDENCE` = 32.
