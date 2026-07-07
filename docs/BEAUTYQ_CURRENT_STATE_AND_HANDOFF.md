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
* `docs/search/BEAUTYQ_SEARCH_CONTRACT_MODULE_SPLIT_PLAN.md` — BeautyQ search contract/module split closeout status, phase history, guardrails, and anti-scope-drift rules

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

## BeautyQ search eval query changes and dirty catalog profiles

Accepted eval query changes are not simple JSON-only edits. Adding accepted
BeautyQ eval rows changes the canonical query count and usually requires the
known offline eval/count-lock chain to be updated together: M9 dataset/static
rows, M10 classification/readiness, M11 candidate generation scaffolds, M12
fusion/reranking scaffolds, M19 taxonomy/coverage locks, EngineEval query-class
coverage, checked-in generated eval resources, and the runtime ES/Qdrant
scorecard.

Future query additions should use a bounded materialization prompt:
* add only the explicitly listed query ids;
* clone expected/scoring from named anchors when the query is a semantic holdout;
* update the known eval/count-lock chain;
* do not tune ES/Qdrant, parser, vocabulary, seed data, routes, or production
  search behavior;
* stop if runtime relevance fails;
* produce a review bundle only when the coordinator explicitly requests one.

The current 89-query dataset keeps the original parser/regression queries and
adds 15 materialized semantic holdout queries. The holdouts make the eval set
less vocabulary-like by adding user-language phrasings such as outcome-based
PMU, lash, facial, brow, hair-removal, and nail requests.

Y0R dirty catalog profiles are test-local measurement profiles in
`RuntimeEsQdrantScorecardProofSpec`. They do not mutate seed JSON or production
search behavior. They build deterministic dirty variants from the already-loaded
canonical `VariantSearchDocument` list and compare dirty coverage against the
clean threshold-0.62 baseline.

The dirty profiles are:
* `dirty_names_20`: replaces searchable service text for 13 of 66 variants while
  preserving identity, filters, attributes, prices, duration, provider/location,
  and geo fields.
* `dirty_attrs_20`: removes one prioritized enum attribute for 13 of 66 variants
  and recomputes attribute/all text.
* `dirty_mixed_40`: applies mixed-language/noisy service text and one enum
  attribute removal for 26 of 66 variants.

These profiles are measurement-only. They may show worse coverage without
failing the test. The test fails only on structural invariant errors such as
changed variant ids, duplicate ids, wrong dirty counts, query-count drift, or
broken coverage bucket partitions. The profiles help distinguish clean-catalog
exact-match strength from robustness under missing/noisy catalog data; they do
not by themselves justify production Qdrant activation, fallback, fusion,
reranking, or ES/Qdrant tuning.

## Read next

* Use `docs/BEAUTYQ_QDRANT_SUPPLEMENT_LOCAL_GATE.md` for current activation values, preflight, smoke commands, locked query set, failure meanings, and non-goals.
* Use `docs/SEARCH_SUPPLEMENT_ARCHITECTURE.md` for the reusable baseline/supplement model and non-goals.
* Use `docs/SEARCH_DOMAIN_ONBOARDING.md` and `docs/SEARCH_SUPPLEMENT_FUTURE_DOMAIN_GATE_TEMPLATE.md` for the reusable measured-gate method.
* Use `docs/DISTAGE_HTTP_TESTING.md` for focused route/service proof rules.

## Follow-up rule

No follow-up QP unless a new objective is introduced.
