# BeautyQ Current State and Handoff

Use this as the BeautyQ entry point. It records the current route truth and points to the canonical local/test gate docs.

## Current route truth

* Production / non-managed default `POST /beauty-search` remains ES-backed.
* The local managed launcher path uses the ES baseline plus a constrained Qdrant supplement when local resources are available.
* Qdrant supplement remains local/test constrained supplement only.
* Local/test provenance, measured gates, and benchmark reports are local/test evidence only; they do not approve a production/default route switch.
* Qdrant is not approved for the default route.

## Canonical docs

* `docs/BEAUTYQ_QDRANT_SUPPLEMENT_LOCAL_GATE.md`
* `docs/SEARCH_SUPPLEMENT_ARCHITECTURE.md`
* `docs/SEARCH_DOMAIN_ONBOARDING.md`
* `docs/DISTAGE_HTTP_TESTING.md`
* `docs/SEARCH_SUPPLEMENT_FUTURE_DOMAIN_GATE_TEMPLATE.md`

## QP24 stop-state

* Stopped at QP24.
* Commit: `805edaa48724e16a36dca2ab8955f11c52cef3b2`.
* Locked local/test measured gate:
  * `testedQueries=4`
  * `improvedQueries=1`
  * `unchangedQueries=3`
  * `worsenedQueries=0`
  * `totalQdrantOnlyAppends=1`
  * `duplicateEsIds=0`
  * `lostEsIds=0`
  * `prefixOrderRegressions=0`
  * `esOwnedComponentChanges=0`
  * `appendBudgetViolations=0`

## Read next

* Use `docs/BEAUTYQ_QDRANT_SUPPLEMENT_LOCAL_GATE.md` for current activation values, preflight, smoke commands, locked query set, failure meanings, and non-goals.
* Use `docs/SEARCH_SUPPLEMENT_ARCHITECTURE.md` for the reusable baseline/supplement model and non-goals.
* Use `docs/SEARCH_DOMAIN_ONBOARDING.md` and `docs/SEARCH_SUPPLEMENT_FUTURE_DOMAIN_GATE_TEMPLATE.md` for the reusable measured-gate method.
* Use `docs/DISTAGE_HTTP_TESTING.md` for focused route/service proof rules.

## Follow-up rule

No follow-up QP unless a new objective is introduced.
