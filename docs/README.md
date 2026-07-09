# Documentation index

## Start here

* `../README.md` — coordinator entry point and current BeautyQ route truth.
* `BEAUTYQ_QDRANT_SUPPLEMENT_LOCAL_GATE.md` — locked BeautyQ local/test Qdrant supplement gate.
* `SEARCH_SUPPLEMENT_ARCHITECTURE.md` — reusable baseline-plus-supplement architecture.
* `search/NEW_DOMAIN_ONBOARDING.md` — new-domain onboarding owner.
* `DISTAGE_HTTP_TESTING.md` — focused local route/service/real-resource testing model.
* `search/CATALOG_DECLARATION_DERIVATION_HANDOFF.md` — catalog/materialization derivation closeout owner; no active derivation blocker.

## BeautyQ search architecture

* `beautyq-search-dsl-v1.md` — current BeautyQ search implementation notes: layer map,
  module ownership, repo graph loading, schema-owned document projection, SearchDocumentSpec /
  SearchField handles, intent vocabulary, SearchRuntimeSpec / fingerprint, generic ES/Qdrant
  interpreters, BeautyQ app-side adapters, and testing standard. This is the current architecture
  and module ownership owner; see the catalog/materialization closeout, new-domain onboarding, and
  coordinator workflow entries above for their respective owners.

## Coordinator workflow

* `local/COORDINATOR_WORKFLOW_AND_PROMPTING.md`

## Removed historical layers

Old review/report layers, activation runbooks, platform reference dumps, macOS metadata, the
historical BeautyQ module-split phase log, and the standalone future-domain supplement gate
template were removed. Use the current docs above instead of looking for removed history.
