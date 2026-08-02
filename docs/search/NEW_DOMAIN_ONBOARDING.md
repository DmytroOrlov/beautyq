# New domain onboarding

Owner: new-domain onboarding. The Gen2 [technical specification §7.10](../gen2/BEAUTYQ_SEARCH_GEN2_TECHNICAL_SPEC.md#710-append-only-supplement-selection-and-lifecycle-bound-membership-implemented-brick-7a)
and [§13](../gen2/BEAUTYQ_SEARCH_GEN2_TECHNICAL_SPEC.md#13-baseline-plus-supplement-orchestration)
own the reusable append-only and orchestration mechanics. Each domain's
executable supplement/readiness/evaluation policy owns its measured thresholds,
budgets, degradation classification, acceptance gates, and domain-specific
fixtures.

A concise guide for adding a new domain (nominal ids, a repo layer, a catalog declaration, a full/seed
loader, and a projection). The repository-wide authoring contract is
[DOMAIN_AUTHORING_PRINCIPLES.md](DOMAIN_AUTHORING_PRINCIPLES.md). This guide distinguishes what is
now generic and reusable as-is from what every domain still writes itself as deliberate business
policy.

BeautyQ is this repository's only production consumer of the Search Gen2 declaration/materialization
kernel today, so it is the richest worked example below. Exact supported document/value/snapshot
shapes belong to the
[technical specification](../gen2/BEAUTYQ_SEARCH_GEN2_TECHNICAL_SPEC.md).

This is not a claim that a new domain is zero-code. It still needs nominal ids, model case classes,
repository implementations, a pure catalog declaration, a loader, a seed policy, and a projection -
this guide tells you which of those are now thin (a few lines, generically derived) and which are
full business logic you write yourself.

BeautyQ is the full-scale reference for declaration shape and validation depth, not a code template.
Do not copy its registries, adapters, matching loops, projection traversals, or backend wiring.
Compose the generic components listed below and declare only the new domain's policy.

Before coding, write down:

- one canonical business root and its reading order;
- topology, snapshot policy, document type, and typed fields;
- projection joins, invariants, text policy, and public names;
- deliberate capability narrowing, intent actions/aliases/labels, and backend policy;
- a neutral fixture or neutral tracer, or a second unrelated domain shape that proves any reusable extraction.

A new domain must not implement locally:

- registry lookup maps or reverse field lookup;
- standard decoding and validation mechanics already supported by the generic API;
- generic intent matching phases, canonical framing, or materialization orchestration;
- a copied declaration inventory from another domain.

The application shell is an edge concern: a domain's canonical root may be composed behind one
independent endpoint, but the endpoint should decode the native request and pass it to the root. Do not
recreate lifecycle authorization, membership, supplement selection, readiness, or response projection in
route code; those mechanics belong to the framework/domain composition owners.

## What is generic now

These surfaces need no new derivation code, no macro, and no per-domain adapter body - you either
extend/call something already in `leaderboard-core`/`repo-core`, or a `given` already resolves
automatically once your types have the right shape:

- `UuidBackedId[A]` (`leaderboard-core`) - the trait every UUID-backed opaque id extends.
- Generic Doobie `Meta[A]` for any `UuidBackedId[A]` (`beautyq-search-repositories`'s
  `leaderboard.repo` package object today; a new domain in its own repo module writes the same
  one-line generic `given` once, keyed off `UuidBackedId[A]`, not per id).
- Generic Tapir `Codec[String, A, CodecFormat.TextPlain]`/`Schema[A]` support through
  `leaderboard.http.tapir.UuidBackedIdTapirSupport` (`app-http`) - import
  `UuidBackedIdTapirSupport.given` in any Tapir endpoint file that captures a `UuidBackedId[A]` id.
- Generic Scalacheck `Arbitrary[A]` for any `UuidBackedId[A]` (one `given` in the test tree, e.g.
  `leaderboard-app-shell/src/test/scala/leaderboard/Rnd.scala`).
- `RepoEntity.derived[A]` - model-derived entity metadata (model name, physical source name, column
  list, conventional id column) for any product type, no raw table/column string.
- `RepoField.derived(_.someField)` - a model-derived, typed field (label + column) from a direct
  selector.
- `RepoValueSource.derived[A, K, Row](_.key)` - model-derived value-source metadata (aggregate model
  name, physical row source, key field) for an aggregate keyed by a field other than a conventional
  id (see the example below).
- Repo operation adapter derivation (`OptionalByKey.derived`, `ValueByKey.derived`, and friends) -
  matches a repositories-bundle method by exact signature, never by name.
- Catalog relation/evidence derivation - `CatalogEntity.derivedFromId`, `CatalogRootTree
  .derivedFromRepositories`, `CatalogRootAll.derivedFromRepositories`, `CatalogMany
  .derivedFromRepositories`, `CatalogValueEdge.derivedFromRepositories` all resolve automatically
  once your repositories bundle has exactly one method of the right shape - no method-name fallback,
  compile-time failure on zero or multiple matches.
- Full loader: `declaration.materialize[F, R](identity).loadAll(repositories).map(_.toSnapshot
  [YourSnapshot])` - generic traversal in declaration order, generic dedup (by `CatalogValue`/
  conventional id) into your snapshot case class via `Mirror.ProductOf`.
- `RepoSnapshotProjection` for indexing snapshot rows and required joins in document projection.
- Seed loader: explicit `GraphLoading.seedRequiredById`/`seedValuesByKey` calls (still one call per
  seed-scoped list - see "What a new domain still declares" below) assembled into your snapshot via
  `LoadedCatalog(...).toRawSnapshot[YourSnapshot]` - same generic `Mirror.ProductOf` assembly as
  `toSnapshot`, but with no dedup, matching seed input's already-distinct assumption.
- Public inbound mechanics in `search-gen2-contract`: declare one ordered vector of
  `PublicFilterDeclaration.value`/`ordered`/`intervalOverlap`/`geoDistance` values and build one
  `PublicFilterRegistry`. It derives shape decoding, canonical errors, bounds and geo clauses; the
  domain supplies only public names, typed handles and deliberate operator narrowing. The lower-level
  `PublicInputRegistry` remains an escape hatch for non-standard input shapes. Dynamic names come
  directly from each declared `DynamicFieldFamily.entries`; see the technical specification for exact
  signatures.
- `SearchIntentMatcher` in `search-gen2-contract`: deterministic longest-alias selection, contextual
  fixed-point rounds, semantic overlays, `requires`/`excludes`, occupied-token protection and residual
  token calculation are reusable; a domain supplies only its rule view, action coverage and text
  normalization/tokenization policy.

## What a new domain still declares

These are business-policy surfaces. Nothing above infers, derives, or guesses them for you:

- Nominal ids themselves (which fields are ids, what they're named) - `UuidBackedId[A]` only
  supplies the mechanical `apply`/`fromString`/`.value`/typeclass-registration boilerplate once you
  declare `opaque type FooId = UUID` and `object FooId extends UuidBackedId[FooId]`.
- Domain model case classes and their JSON codecs.
- Repository traits and their SQL/`Dummy`/`Postgres` implementations - what a repository method is
  named, what it queries, what constraints it enforces.
- Each repository companion's `entity`/`itemEntity`-style `val`s (`RepoEntity.derived[A]`) and any
  aggregate `valueSource` `val`s (`RepoValueSource.derived[...]`) - derivation gives you the
  mechanical metadata construction; deciding which type needs one, and what its key/row type is, is
  still yours.
- The pure catalog declaration itself (which roots/edges exist, in what order, keyed by what) - see
  the minimal example below.
- The repositories bundle case class for your domain.
- Your snapshot case class (the `toSnapshot`/`toRawSnapshot` target).
- The full loader call site (one line, but you write it).
- Seed scope (which lists a seed-scoped load needs) and seed loader policy (no-dedup, canonical
  missing-entity message, ordering) - a deliberate, different policy than the full loader's, not
  something to unify without a coordinator decision.
- Projection logic: joins, missing-entity messages, schema/business-invariant validation, text/token
  normalization. Projection owns its own row metadata (built from your repositories' `entity`/
  `valueSource` vals) as local implementation detail - it is not, and should not become, a shared
  catalog-graph facade (see "Nodes/projection boundary cleanup scope" in the handoff doc for why one
  used to exist and was removed).
- Public request policy: caller-facing filter/sort/facet names, any deliberate narrowing of the
  capability-derived operator matrix, typed field/interval/geo choices and domain-specific value
  decoding.
- Intent policy: stable action identities, aliases, `requires`/`excludes`, action-to-constraint/signal
  translation, canonical labels and any domain-specific extraction such as a budget phrase. The
  generic matcher supplies the algorithm; it does not invent business vocabulary.
- API/runtime/search backend policy: route shapes, response shapes, backend routing/activation,
  ranking/fusion.

## Minimal catalog declaration example

A pseudo-example (`CategoryLike`/`ItemLike`/`ItemSchemaLike` stand in for real domain types),
mirroring the real BeautyQ declaration's shape (`category` self-tree -> `service` many-edge ->
`serviceVariantSchema` value-edge on `service`):

```scala
val declaration =
  catalog("myDomain")
    .branch[CategoryLike]
    .rootTree(_.parentId, root = CategoryLike.rootId)   // self-tree root, keyed by parentId
    .child[ItemLike](_.categoryId)                      // CategoryLike -> ItemLike, many-edge
    .branch[ItemLike]                                   // switch focus: edges below are from ItemLike
    .value[ItemSchemaLike](_.itemId)                     // ItemLike -> ItemSchemaLike, value-edge
```

`.child[C](by)` and `.value[V](by)` always attach to the *current* branch - switching which entity
subsequent edges hang off requires an explicit `.branch[NextEntity]` first, exactly like the real
declaration switches from `category` to `service` before declaring the `serviceVariantSchema` value
edge.

Materializing this declaration (`declaration.materialize[F, Repositories[F]](identity)`) needs
`CatalogEntity`/`CatalogRootTree`/`CatalogMany` evidence, all derived automatically from your
repositories bundle by the surfaces listed above - only a genuinely ambiguous edge (two repositories
methods matching the same requested key type) would ever need an explicit `given`, and if your ids
are nominal (`UuidBackedId`-backed, not raw `UUID` aliases), that ambiguity should not arise.

## Value-source example

```scala
val valueSource: RepoValueSource[Aggregate, ParentId, AggregateRow] =
  RepoValueSource.derived[Aggregate, ParentId, AggregateRow](_.parentId)
```

`Row` (`AggregateRow` above) stays an explicit type parameter - it is never inferred or derived from
`Aggregate` - because the aggregate's physical truth and its in-memory shape are allowed to differ.
`ServiceVariantSchema` is the real example: it is keyed by `serviceId` and physically stored as one
row per schema item (`ServiceVariantSchemaItem`), not one row per aggregate. Claiming the aggregate
itself is a `RepoEntity` of its own physical columns would be wrong for exactly this shape; `Row`
lets the value source say "keyed like *this*, but physically sourced from *that*" without guessing.

`CatalogValue` identity evidence (`CatalogValue.from(yourValueSource)`) stays an explicit `given` in
your domain's `Evidence` object, the same way BeautyQ's does - automatic `CatalogValue` discovery
(e.g. searching a repository companion's implicit scope) is deliberately not attempted anywhere in
this codebase: a repository companion is not the aggregate's own companion, so hiding this wiring
behind implicit scope would be clever but harder to debug than one explicit line.

## What not to derive from catalog tree

- Projection joins, text/token normalization, schema/business-invariant validation.
- Runtime routing/backend policy (which search backend serves a query, activation/fallback).
- Response policy (API response shapes, pagination, error envelopes).
- Seed dedup/order policy (the seed loader's no-dedup, order-preserving behavior is a deliberate,
  separate policy from the full loader's dedup - keep them separate, don't unify without a
  coordinator decision).
- Persistence/SQL constraints (foreign keys, uniqueness, check constraints) - these belong in the
  repository's own `Postgres` implementation, not inferred from the catalog declaration.

## Catalog/materialization invariants

The catalog/materialization derivation closeout is complete. A new domain should preserve these
semantic distinctions while reusing the listed helpers:

- the full repository loader traverses the materialized declaration in order and may deduplicate into
  the domain snapshot;
- the seed loader preserves seed order and intentionally does not deduplicate;
- `CatalogValue` identity/value-source evidence remains an explicit domain choice, while ordinary
  repository relation evidence is derived by exact type/signature;
- projection remains the domain's owner of joins, missing-entity errors, stored-data validation,
  normalization, and document-value/text policy.

This is the current contract, not a phase log. The technical specification owns the current
implemented state and delivery closure; source and focused tests own the exact derivation behavior.

## Application composition and source-readiness ordering

A domain's declaration and materialization DSL describes logical data reads. It does not infer
Distage lifecycle ordering from SQL text.

Application composition must make source-data readiness a dependency of search startup before
snapshot materialization and backend activation begin.

A provided/external database may use a no-op readiness implementation, but the dependency must
still remain explicit.

## Proof model and test selection

Four proof layers are used:

1. **Pure policy/compiler behavior** — ordinary unit or property-style tests.
2. **Distage composition and ownership** — plan or graph tests proving required bindings and dependencies.
3. **HTTP boundary behavior** — route-level tests for decoding, status mapping, response encoding, and route ownership.
4. **Real external-resource behavior** — communication tests using actual PostgreSQL, Elasticsearch, Qdrant, or embedding wire paths.

A pure gate and a real-resource communication proof are different proofs; scripted backend JSON does not replace a real communication proof. Focused owner tests should run before the full repository suite. The full clean root suite is a closure proof, not the default validation for every bounded edit. Suites sharing canonical Elasticsearch or Qdrant namespaces must be serialized or otherwise isolated. First-start behavior must be checked from a fresh managed-resource state; a successful second launch over a database prepared by a failed first launch is not sufficient first-start evidence.

For the current BeautyQ owners:

* `BeautyQGen2CutoverGateSpec` — pure readiness-aware cutover gate.
* `BeautyQSearchGen2CutoverCommunicationSpec` — real communication through the current Elasticsearch, Qdrant, and embedding paths.

The declaration/materialization DSL describes logical reads; it does not infer Distage lifecycle ordering from SQL text. Application composition must make source-data readiness a dependency of search startup.

## Search Gen2 domain declaration

`search-gen2-contract` owns a second, narrower low-boilerplate authoring layer for the typed
document/field DSL described in `docs/gen2/BEAUTYQ_SEARCH_GEN2_TECHNICAL_SPEC.md`. It follows the same
split as the catalog-declaration surfaces above: business policy stays explicit, only genuinely
mechanical, tautological evidence is derived.

### Business/domain author writes explicitly

- the denormalized document case class;
- catalog topology (the neutral `repo-core` catalog DSL described above);
- identity selection (which field is the document's own id);
- the keyword-versus-text choice for a raw `String` field;
- field capabilities (filterable/facetable/sortable/groupable/payload-eligible/searchable);
- dynamic attribute definition inventories (which codes exist, in what order);
- projection joins and business validation (owned by the materialization layer, not this DSL).

### Framework derives

- logical codecs for any `UuidBackedId[A]`-backed nominal id, from the same evidence Doobie/Tapir/
  Scalacheck already derive their own adapters from;
- logical codecs for any `CanonicalStringValue[A]`-backed wrapper (a validated canonical `String`,
  such as a stable business code);
- the nominal `SearchValueTypeId` for both of the above, from the wrapper type's own simple source
  name - never a hand-typed string;
- a direct field's `FieldPath`, default `FieldId`, and default `FieldSemantic`, from a direct selector
  (`_.someField`);
- required extraction for direct fields;
- the default `SearchFieldKind` for a non-`String` value type with exactly one unambiguous kind (never
  for `String` itself - keyword versus text stays an explicit author choice); this includes `Int`,
  `Long`, `BigDecimal`, `Boolean`, `java.time.Instant`, and `GeoPoint` out of the box;
- a dynamic map field's path/id/semantic (`<mapPrefix>.<definitionCode>`) and its optional extraction -
  including a dynamic family declared `.text` (searchable), not only `.keyword` or a numeric kind;
- deterministic field order: static declaration order followed by dynamic-family declaration order,
  with both registries derived from those declarations;
- exhaustive document-product coverage (`field`/`dynamicMap`/explicit `ignore`), identity exclusion,
  ordinary-field ordering and validation;
- one deterministic structural tree and renderer.

### Compact example

```scala
object WidgetModel {
  opaque type WidgetId = UUID

  object WidgetId extends UuidBackedId[WidgetId] {
    def apply(value: UUID): WidgetId = value
    def unwrap(id: WidgetId): UUID = id

    given UuidBackedId[WidgetId] = this
  }

  final case class WidgetDocument(
    id: WidgetId,
    name: String,
    tags: Map[String, String],
  )
}

object WidgetSearchDomain {
  import WidgetModel.*

  private val declarations =
    searchFields[WidgetDocument]("widgets")

  val id =
    declarations
      .inferred(_.id)
      .payloadEligible
      .declare

  val name =
    declarations
      .text(_.name)
      .searchable
      .declare

  val tags =
    declarations
      .dynamicMap(_.tags, tagDefinitions)(_.code)
      .keyword
      .filterable(FilterOperator.Equal)
      .declare

  val document =
    declarations.completeDocument(id)
}
```

Opaque model wrappers are declared in the model scope and consumed from a separate search-contract
scope, matching the production module boundary and keeping their nominal typeclass evidence
unambiguous.

### Plan-policy example (Brick 4F)

Combining a domain's already-validated public request and parsed intent into one validated `SearchPlan`
follows the same split: business policy stays explicit, everything mechanical is reused from
`search-gen2-contract`/`search-gen2-core`.

**Business/domain author writes explicitly:**

- constraint-source precedence, as one executable policy value that carries *both* the stable,
  reviewer-readable order *and* the actual higher/lower tier assignment - never a rendered label paired
  with a separately hardcoded compiler argument order;
- the geo-origin source, if the domain has a geo field, likewise as one executable policy value
  carrying both a stable id and the actual `request -> Option[GeoPoint]` resolution (e.g.
  `request.userLocation`);
- facet declarations (one `FacetPlanDeclaration` per facet, holding only its `FacetRequest` - never a
  second vector repeating that request's own field(s));
- group policy (an explicit `Vector.empty` counts as a policy, not an omission);
- the default-browse notice code;
- plan-mode classification (a plain function over already-resolved plan pieces).

**Framework derives/executes:**

- canonical constraint identity and slot derivation (`CanonicalConstraintView`/`ConstraintSlot`);
- constraint precedence resolution, including every duplicate/conflict/ordering rule
  (`ConstraintPrecedenceResolver`), given the complete typed source binding produced by
  (`ConstraintPrecedence`/`ConstraintPriorityTiers`);
- public filter/sort geo-clause resolution against the declared origin (`PublicPlanInputResolver`);
- field-handle derivation for any reviewer/presentation view that needs one (`FacetRequest.fieldHandles`);
- facet-ID validation and one-time requested-facet lookup at the validated public-request boundary
  (`FacetPlanRegistry`); the trusted request carries typed `FacetRequest` values into compilation, so
  the compiler performs no second lookup, plus the framework-owned static
  literal constructors a domain calls instead of hand-rolling its own unwrapping/throwing
  (`FacetSize.unsafeFrom`, `FacetPlanRegistry.unsafeFrom`);
- `SearchPlan` assembly, diagnostic attachment and validation through the opaque prepared-compilation
  value returned by `SearchPlanCompilationKernel.prepare`; a domain may inspect its resolution and call
  `prepared.assemble(finalNotices)` once, but cannot forge a resolution or pair it with another input.

```scala
enum WidgetConstraintSource {
  case PublicRequest
  case ParsedIntent
}

object WidgetSearchPlanPolicy {
  val constraintPrecedence =
    ConstraintPrecedence.unsafeAbove(WidgetConstraintSource.PublicRequest, WidgetConstraintSource.ParsedIntent)

  private val declarations = Vector(
    FacetPlanDeclaration(FacetRequest.Terms(FacetId("tag"), WidgetSearchDomain.tags, FacetSize.unsafeFrom(10), TermsFacetOrder.CountDescThenKeyAsc, FacetCountingPolicy.AllAppliedHardFilters))
  )

  val facetRegistry: FacetPlanRegistry[WidgetDocument] = FacetPlanRegistry.unsafeFrom(declarations)
  val groups: Vector[GroupRequest[WidgetDocument, ?]] = Vector.empty
  val defaultBrowseNotice = PlanDiagnostic(PlanDiagnosticCode("default-browse"), None)
}
```

A domain compiler then composes the reusable mechanics as a thin, sequential gate pipeline: resolve
public geo input through the domain's geo-origin policy, consume the trusted request's already-resolved
facet requests, derive `ConstraintPriorityTiers` from the exhaustive typed function passed to
`constraintPrecedence.tiers`, and
call `SearchPlanCompilationKernel.prepare`. It may classify the plan from
`prepared.resolution.appliedFilters`, then calls `prepared.assemble(finalNotices)` once. It never
assembles or validates a `SearchPlan` by hand, never reimplements precedence, geo resolution or facet
lookup, and never copies or mutates the plan returned by `assemble`.

`ConstraintPrecedence`/`BeautyQGeoOriginPolicy`/`BeautyQSearchPlanPolicy`/
`BeautyQSearchPlanCompiler`/`BeautyQSearchPlanCompilationTrace`
(`beautyq-search-gen2-contract`/`beautyq-search-gen2-wiring`) are the golden reference for this authoring
style at full scale, including geo-origin resolution and a four-facet registry with Gen1-evidence bucket
tables. `BeautyQSearchDeclarations.variants.plan` exposes those same typed policy values - not only
their rendered summaries - as a reviewer-readable branch of the canonical root, derived from
`BeautyQSearchPlanPolicy` rather than restated.

`BeautyQSearchDeclarations.variants.Fields` (`beautyq-search-gen2-contract`) is the golden reference for
this authoring style at full scale: it declares 44 fields (24 direct, 20 dynamic across four map
families) using only selectors, kind choices, and capabilities, with every mechanical piece above
derived by the registry.

For a complete implemented business review, start at `BeautyQSearchGen2` in the wiring module. It links
directly to the canonical contract, inbound input/parser, materialization, plan/candidate, Qdrant and
Elasticsearch owners without copying their policy. Follow its `contract` branch into
`BeautyQSearchDeclarations`, then read `catalog.topology`, `variants.Fields`, `variants.document`,
`variants.request`, `variants.intent`, and `variants.plan`; follow `input` for request/parser and
`qdrant` for candidate policy/runtime/hydration.

Before assuming a value type or document/snapshot shape is supported, check technical specification
§§7.1 and 9.4. Delivery status is recorded in the technical specification.

**Authoring hazard when nesting a `Fields` object under a domain root:** if the enclosing root object
also aliases `Fields.document`, keep every dependency either inside `Fields` or outside the enclosing
root; do not capture a sibling root value. This cyclic shape is unsupported; see technical specification
§7.1.

### Candidate-policy example (Brick 4G-A)

Declare only the domain's reason type, semantic parts, active gate order and each gate's typed outcome.
The framework owns non-blank semantic text, ordered evaluation and first-failure selection. Exact API and
error semantics are in technical specification §§7.8 and 12.1.

```scala
enum WidgetSemanticTextPart {
  case ResidualText

  def rawComponents(residualText: Option[String]): Vector[String] =
    this match {
      case WidgetSemanticTextPart.ResidualText => residualText.toVector
    }
}

enum WidgetCandidateIneligibility {
  case NoSemanticQueryText
  case NotFirstPage
}

enum WidgetCandidateEligibilityGate(val ineligibility: WidgetCandidateIneligibility) {
  case SemanticQueryText
      extends WidgetCandidateEligibilityGate(WidgetCandidateIneligibility.NoSemanticQueryText)
  case FirstPage
      extends WidgetCandidateEligibilityGate(WidgetCandidateIneligibility.NotFirstPage)

  def outcome(semanticText: Either[SemanticQueryTextError, SemanticQueryText], firstPage: Boolean): CandidateGateOutcome[WidgetCandidateIneligibility] =
    this match {
      case WidgetCandidateEligibilityGate.SemanticQueryText if semanticText.isRight => CandidateGateOutcome.Passed
      case WidgetCandidateEligibilityGate.FirstPage if firstPage                    => CandidateGateOutcome.Passed
      case _                                                                         => CandidateGateOutcome.Rejected(ineligibility)
    }
}

object WidgetSemanticCandidatePolicy {
  // Explicit vectors are policy: they select the active vocabulary and its business order.
  val semanticTextParts: Vector[WidgetSemanticTextPart] = Vector(
    WidgetSemanticTextPart.ResidualText,
  )
  val eligibilityGates: Vector[WidgetCandidateEligibilityGate] = Vector(
    WidgetCandidateEligibilityGate.SemanticQueryText,
    WidgetCandidateEligibilityGate.FirstPage,
  )

  def semanticText(residualText: Option[String]): Either[SemanticQueryTextError, SemanticQueryText] =
    SemanticCandidateEvaluation.semanticText[WidgetSemanticTextPart](semanticTextParts, _.rawComponents(residualText), identity)

  def evaluate[Plan](residualText: Option[String], firstPage: Boolean, eligiblePlan: SemanticQueryText => Plan): Either[CandidateEvaluationError, CandidateEvaluation[WidgetCandidateEligibilityGate, Plan, WidgetCandidateIneligibility]] = {
    val text = semanticText(residualText)
    SemanticCandidateEvaluation.evaluate[WidgetCandidateEligibilityGate, Plan, WidgetCandidateIneligibility](
      eligibilityGates,
      text,
      (gate, semanticText) => gate.outcome(semanticText, firstPage),
      eligiblePlan,
    )
  }
}
```

A thin domain compiler derives facts such as first-page/default-sort from its compiled plan, supplies the
eligible `CandidatePlan`, and propagates `CandidateEvaluationError`. Do not copy BeautyQ reasons, cursor
policy or backend retrieval knobs.

### Elasticsearch index-policy example (Brick 5A)

A domain supplies only its already-declared document, one explicit Elasticsearch policy version, and an
framework-known analyzer choice for each of its own declared searchable text fields (BeautyQ uses
`Standard`; arbitrary custom analyzer names are not an initial Gen2 shape):

```scala
val index: ElasticsearchIndexPolicy[WidgetDocument, WidgetId] =
  ElasticsearchIndexPolicy.unsafeFrom(
    declaration = WidgetSearchDomain.document,
    policyVersion = ElasticsearchPolicyVersion("widget-elasticsearch-v1"),
    textFields = Vector(
      ElasticsearchTextFieldMapping(WidgetSearchDomain.name, ElasticsearchAnalyzerName.Standard)
    ),
  )
```

Everything else is derived by `search-gen2-elasticsearch`: the declaration-driven mapping and `_id`/
`_source` compilation (traversing `declaration.allFields` in its deterministic contract order, never a
domain-owned fold),
and the framework-owned `ElasticsearchCompilerVersion`/`ElasticsearchIndexFormatVersion` (always
`.Current` - `ElasticsearchIndexPolicy.unsafeFrom` does not accept either as a parameter, so a domain
cannot substitute one). A domain never recreates a field handle or mapping/source traversal; the
framework normalizes analyzer assignments into the same static-then-dynamic document order - vector
order is not a separate business policy.

### Elasticsearch search-policy example (Brick 5B)

The index policy above is only half of what a domain executes against Elasticsearch. Query-time choices -
which text fields rank the query, how they're weighted, whether geo proximity contributes to score, how
exact totals and default ordering behave - are a second, explicit policy layer binding the index policy to
those choices:

```scala
val policy: ElasticsearchPolicy[WidgetDocument, WidgetId] =
  ElasticsearchPolicy.unsafeFrom(
    planContractVersion = PlanContractVersion("widget-plan-v1"),
    index = index,
    queryTextFields = Vector(ElasticsearchWeightedTextField(WidgetSearchDomain.name, ElasticsearchQueryWeight(3.0))),
    textOperator = ElasticsearchTextOperator.And,
    geoScoringPolicy = Some(
      ElasticsearchGeoScoringPolicy(scale = Distance(BigDecimal(2000)), offset = Distance(BigDecimal(0)), decay = ElasticsearchGeoDecay(0.4), weight = ElasticsearchQueryWeight(1.5))
    ),
    totalHitsPolicy = ElasticsearchTotalHitsPolicy.ExactRequired,
    defaultSortPolicy = ElasticsearchDefaultSortPolicy(relevanceDirection = SortDirection.Desc, identityTieBreakerDirection = SortDirection.Asc),
  )
```

Everything else is derived: `ElasticsearchSearchRequestCompiler.compile(policy, boundPlan)` turns a
cursor-bound plan into a prepared deterministic request JSON (text/terms/range/interval-overlap/geo
compilation, sort and tie-breaker, facet aggregations, cursor `search_after` state - never `from`). A
lifecycle owner resolves the prepared request's active or untrusted generation reference into an
`AuthorizedElasticsearchSearchRequest`; only that authorized value reaches transport and
`ElasticsearchSearchResponseDecoder.decode`, which returns the typed baseline page (hits, exact total,
typed facets, diagnostics and next cursor). Policy construction rejects non-finite weights/decays and
identity kinds that cannot support the emitted value-sort tie-breaker. `policy.contractFingerprint` is the
one complete fingerprint - covering both index and query choices - that a domain's plan compiler, the
generation compiler and the request compiler all consume; a domain never recreates request/response
traversal, an aggregation name, or a second fingerprint. `BeautyQElasticsearchPolicy`/
`BeautyQElasticsearchGeneration`/`BeautyQElasticsearchBaseline` (`beautyq-search-gen2-wiring`) are the
golden reference at full scale. Reuse the generic physical-generation lifecycle and baseline service with
domain resource names; keep domain-specific group/carousel projection at the wiring edge and derive any
public response from the domain's single bound orchestration result rather than separately supplied plan,
baseline and supplement values.

## Qdrant candidate and hydration policy (6A/6C boundary)

After the document declaration and semantic candidate policy exist, a domain supplies only the
identity field, searchable embedding field, named-vector/model identity, distance and retrieval
knobs to its Qdrant policy. The generic policy derives payload fields, payload indexes, typed filter
JSON, deterministic generation metadata and candidate response decoding from that declaration. The
domain also chooses only its stable collection alias and physical prefix; lifecycle, metadata
convergence, alias authorization and target binding remain framework-owned. Do not maintain a second
payload/index list or call Gen1 Qdrant. For an eligible candidate path, provide a typed embedding adapter
and one explicit hydration choice: missing documents fail and hard constraints are asserted after one
generation-consistent lookup. The framework derives the request pipeline, target/metadata binding, ordered
lookup, integrity errors and candidate-only result. If a domain later composes a supplement, pass the
compiler-owned bound baseline and candidate evaluation to the orchestration owner; do not construct or
copy readiness, degradation, membership or final-result values. Exact current API shapes belong to the
technical specification, and delivery closure is recorded in the technical specification.

## Materialization

Reuse from the generic Gen2 modules:

- `VersionedSnapshot` and fingerprint/version types;
- `SearchSnapshotSource`;
- `canonicalRow` for selector-derived direct fields and self-sized nested groups;
- `CanonicalFingerprint` framing and its lower-level `rowWithSortParts` escape hatch;
- `CanonicalSnapshot` for snapshot-product source inventory/traversal, including a
  `CanonicalSnapshot.Single[A]` source
  member for a singleton row alongside sibling `Vector` sources - a snapshot is not required to be an
  all-`Vector` product;
- `CanonicalIndex` for deterministic representatives and duplicate reporting;
- `NonEmptyErrors` for generic non-empty accumulation;
- `SearchProjectedDocumentsFingerprint`;
- `SearchMaterializer`.

Implement in the new domain:

- one consistent source adapter;
- the domain snapshot value;
- source fingerprint field/value selection through `canonicalRow`;
- strict stored-data validation;
- pure projection and business errors;
- projection format and encoding versions.

Do not copy BeautyQ snapshot, joins, text policy or SQL. SQL statements and decoders remain explicit
inside the new domain's one consistent transaction adapter; snapshot traversal/order and catalog
entity-inventory proof come from the generic snapshot kernel rather than a second hand-written list.

## Semantic ownership checklist

A new domain author must explicitly cover these ownership decisions before declaring a search domain
complete:

1. Choose the canonical baseline/result owner.
2. Define whether any secondary backend is candidate-only, advisory, or supplemental.
3. Declare the exact no-harm or composition rule for supplemental results.
4. Assign stable field, filter, sort, facet, group, status, and reason identities where those concepts
   exist.
5. Declare meaningful ordering explicitly instead of relying on map or incidental source order.
6. Decide which declarations participate in plan, cursor, mapping, generation, or persistence
   fingerprints.
7. Prevent public requests from granting themselves trusted provenance.
8. Define malformed-input and incompatible-generation behavior.
9. Add focused proofs for identity mutation, ordering, boundary decoding, cursor/generation binding,
   and canonical-result preservation.

Reuse existing generic Gen2 mechanics before adding another framework abstraction.

## Measured local gate before building a supplement

Before wiring any new-domain search supplement (a candidate source added alongside a baseline
backend) toward production, prove it locally first. Reuse the mechanics from
[technical specification §7.10](../gen2/BEAUTYQ_SEARCH_GEN2_TECHNICAL_SPEC.md#710-append-only-supplement-selection-and-lifecycle-bound-membership-implemented-brick-7a)
and [§13](../gen2/BEAUTYQ_SEARCH_GEN2_TECHNICAL_SPEC.md#13-baseline-plus-supplement-orchestration),
but do not reuse BeautyQ thresholds or query text. Each domain declares and proves its own measured
policy.

A new domain must define:

- baseline owner;
- supplement candidate owner;
- accepted query/eval set (source-confirmed, including at least one expected-improvement query and
  one baseline-preservation query);
- measured gate command/spec owner (the same way BeautyQ's is
  `BeautyQSearchGen2CutoverCommunicationSpec`);
- metric thresholds (a domain-specific accepted harm budget, declared and proved by the new domain);
- failure meanings (a short code per stop condition);
- docs owner for the domain's own concrete local/test gate, if it keeps one;
- a separate approval boundary for any production/default route change - a passing measured gate is
  local/test evidence only, never rollout approval by itself.

Lock or stop: if the gate is green, lock the exact query set, counts, and failure markers; if there
is no improvement, stop; if there is any regression, stop or get an explicit coordinator/user-approved
budget before implementing (never a vague "almost no worsening"); if the source query inventory is
incomplete, report it as source-incomplete rather than inventing queries.

## Eval-first domain delivery

A new domain does not wait for its complete backend/runtime implementation before measuring quality.
Its first vertical slice is:

```text
10–20 independently authored, operator-approved anchor queries
-> hard acceptable/forbidden evidence and optional graded relevance
-> simplest executable baseline
-> first deterministic report
```

Operationally, declare request budgets at the domain HTTP/input boundary, keep generation work
bounded and ordered, and record startup snapshot/generation evidence before serving. Use the canonical
BeautyQ operations runbook's fenced two-observation cleanup procedure as the safety shape; do not invent
automatic retention or a single score threshold from an unseparated sample. A measured gate that is red
must fail the communication proof and produce its artifacts before requesting a product/search-policy
decision.

After that, each declaration, intent, filter, ranking, backend or supplement slice expands and reruns
the same domain-owned evaluation. Keep a development set for policy work, a protected holdout excluded
from ordinary policy tuning and changed only through explicit label review, and a permanent regression
set containing every accepted real bug.

Development/regression reports may name exact cases. Protected-holdout reports expose aggregate/slice
verdicts by default. Revealing a protected case is a break-glass action: move it permanently into the
regression set and replenish the protected holdout with independently authored, unused,
operator-approved cases before using it for acceptance again.

Reuse only neutral evaluation mechanics from `search-gen2-eval`: stable case identity, deterministic run
order, declared-cutoff ranked metric mathematics, ordered aggregate/comparison and report encoding.
Domains must supply their own typed
corpus, policy, and adapters. Query text, request construction, entity expectations, language/intent
slices, thresholds and stop conditions are domain policy and must not be copied from BeautyQ.
For protected acceptance, keep the private corpus and protected-acceptance policy as separate
domain-owned inputs. Decode both strictly with ordered arrays, execute visible and protected cases
through the same startup/application path, redact protected identities from reports, and derive an
accepted manifest only after a green protected gate from a clean committed application revision.
Missing private inputs block protected execution and bootstrap, but do not block the dedicated
pre-execution input-authoring workflow. Machine-assisted authoring is allowed when it is separated from
execution, cannot observe protected results, records provenance, and freezes inputs before use; a
separately hired reviewer is not required. “Synthetic acceptance data” means labels or thresholds
derived from search output, not source-grounded cases authored before execution. Bootstrap and verify
must never author, relabel or modify the frozen inputs, and no default threshold may replace an absent
operator-approved policy. The freeze must also bind every judgment identity to the canonical typed
catalog, require an acceptable variant for each exact-intent case, prove zero exact/normalized query
duplicates both against visible evidence and internally, bind author/judge draft hashes and the
canonical source fingerprint, and compare the declared source revision with the actual clean base.
These are aggregate integrity checks; semantic-similarity duplicate detection is not claimed.
The approved reusable-kernel and BeautyQ corpus migration are implemented in
[Q1 of the post-cutover plan](../gen2/BEAUTYQ_SEARCH_GEN2_POST_CUTOVER_PLAN.md).
