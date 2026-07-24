# Domain Authoring Principles

**Status:** normative repository-wide contract for search-domain authoring.

**Scope:** domain declarations, reusable search framework components, coordinator prompts, and
acceptance reviews. This document owns the general principles; project documents describe their local
application and current implementation state.

## Why this exists

A search domain is physically spread across contract, materialization, wiring, backend, and app
modules. That physical split must not make business policy hard to find or allow generic mechanics to
be copied into every domain. These principles give a coordinator and reviewer one stable answer to
five questions:

1. Where does a business author read the domain?
2. Which choices belong to the domain?
3. Which repeated operations belong to the framework?
4. How is reuse proven?
5. Which declaration owns each decision, and which values are derived views?

This is an ownership and acceptance contract, not a requirement to put every implementation in one
file or module.

## 1. One readable declaration path

Every domain exposes one canonical business-facing entry point. It contains each business declaration
or points directly to the authoritative declaration in its owning module. A business author must be
able to follow the implemented policy in data-flow order without first learning the sbt graph, DI
wiring, or backend internals.

For a search domain the path normally reads:

```text
source topology
→ document shape
→ fields and capabilities
→ projection and text policy
→ public request policy
→ intent policy
→ plan/backend policy when implemented
```

The path must not contain placeholder branches for work that does not exist. A module boundary is
valid when the canonical entry point and its documentation identify the next authoritative owner.

## 2. Business declares policy; the framework derives mechanics

Domain code declares choices that may legitimately differ between domains:

- source topology and identity selection;
- document shape, field selectors, String keyword/text meaning, and capabilities;
- projection joins, invariants, text composition, and domain-specific extraction;
- public names and deliberate operator narrowing;
- facet, group, ranking, backend activation, and response policy;
- stable intent actions, aliases, requirements/exclusions, labels, and action translation.

Reusable components derive or execute repeated mechanics already fixed by those choices:

- lookup and inventory construction;
- capability-to-operator projection;
- standard scalar, collection, range, and geo decoding when the generic API supports that shape;
- validation accumulation, provenance wrapping, and duplicate detection;
- longest-match/contextual matching phases and occupied-token tracking;
- canonical framing, deterministic ordering, generated traces, and materialization orchestration.

The word “mechanics” does not erase policy. Value normalization, text composition, join selection,
error wording, and semantic labels remain domain-owned when their meaning can differ.

If a mechanically equivalent operation appears in a second domain, it is a review red flag. Either
move the neutral operation behind a reusable boundary or record why the two operations are genuinely
different policy. Do not add a generic abstraction merely because two snippets look superficially
similar.

## 3. Compose; do not clone

New domains compose domain-neutral components. BeautyQ is a full-scale reference for declaration
shape, evidence depth, and end-to-end composition; it is not a file template to copy and rename.

A reusable component may be called generic when:

- its public API contains no domain identity, field names, aliases, defaults, or compatibility rules;
- its behavior does not branch on a domain identity;
- its shape is justified by a real domain requirement;
- a neutral or adversarial fixture proves the reusable representation independently of BeautyQ.

A second production domain is valuable evidence but is not required before a pure neutral component
can be introduced. Conversely, a fixture alone does not justify a new production vocabulary or a
claim that every possible domain shape is supported. A single-consumer or tracked-gap status must be
stated when the evidence is narrower.

Adding a new domain must not require copying BeautyQ policy. Adding a new domain must not change the
generic framework unless it introduces a genuinely reusable semantic that the existing contract
cannot express.

## 4. One executable source of truth

Each business decision has one executable owner in domain policy. The owner may be in a contract,
materialization, wiring, or backend module; the canonical entry point must identify it.

The following are derived views and must not become independent authorities:

- registries and inventories;
- rendered trees and traces;
- ledgers, fingerprints, and generated backend descriptions;
- documentation tables and test fixture literals.

Focused tests own observable behavior and invariants. Documentation explains ownership, reading order,
constraints, and current state. Generated views expose or verify executable declarations; they do not
reconstruct policy.

When a declaration, test, and documentation statement disagree, report the mismatch and resolve it
explicitly. Do not silently choose one layer as a substitute for another.

### Stable semantic identity

Public names, typed IDs, reason codes, fingerprint inputs, and policy order that affect requests,
cursors, persistence, backend execution, or public responses must be explicit and stable. Display
labels, incidental declaration layout, unordered iteration, or diagnostic prose must not silently
define protocol identity. When order is semantically meaningful, declare it through an explicit
ordered owner and prove it.

Use ordinary `toString` only for incidental diagnostics. A task-defined stable ID/code or active
subset/order is typed domain policy; all views derive from it, and it must not be replaced with
`toString` or enum inventory. Do not add stable labels to unrelated enums.

### Canonical and supplemental result ownership

A domain must name one canonical result owner. Approximate, semantic, recommendation, or supplemental
backends may enrich the canonical result only through an explicitly declared composition policy.
They must not silently redefine canonical totals, ordering, facets, groups, cursor state, or trusted
provenance.

## Coordinator and reviewer gate

Before issuing a prompt or accepting a patch that changes domain policy or reusable search mechanics,
answer:

```text
Canonical entry point:
  Where will the new business policy be read?

Domain-owned differences:
  Which choices legitimately vary by domain?

Framework-derived mechanics:
  Which repeated operations are reused or extracted?

Reuse proof:
  Which neutral fixture or neutral tracer, or a second unrelated domain shape challenges the reusable boundary?

Executable owner:
  Which declaration owns the policy, and which outputs are derived views?
```

The patch must not be accepted when:

- new policy is not reachable from the canonical entry point;
- domain code copies a reusable lookup, matching loop, validation traversal, reverse lookup, or
  canonical framing operation;
- generic code contains domain concepts;
- a manually maintained inventory duplicates an executable declaration;
- reusable behavior is proven only by BeautyQ without a neutral-shape explanation;
- a generated tree, trace, ledger, fingerprint, or documentation table becomes a second policy owner.
- speculative defensive code is added without a source-confirmed boundary or reachable failure.

These gates apply to domain/framework work. Ordinary bug fixes and unrelated repository work do not
need a synthetic authoring plan.

## Current-state qualification

The principles are normative, but an existing implementation may still have a tracked extraction gap.
Such a gap must name the current owner and next boundary in the technical specification. It must
not be hidden by claiming that an operation is already generic.

For the current Gen2 state, exact supported shapes, lifecycle/runtime ownership, accepted limits,
and delivery closure belong to the
[technical specification](../gen2/BEAUTYQ_SEARCH_GEN2_TECHNICAL_SPEC.md), the practical new-domain
path belongs to [`NEW_DOMAIN_ONBOARDING.md`](NEW_DOMAIN_ONBOARDING.md), and historical Gen1 evidence
belongs to [BEAUTYQ_SEARCH_GEN2_REVIEW.md](../gen2/BEAUTYQ_SEARCH_GEN2_REVIEW.md).
Implementation chronology belongs to Git history.
