# Qdrant Production Activation Decision Criteria

This document defines the separate approval gate for any future default `POST /beauty-search` switch from the current ES-backed route to a Qdrant-backed route.
It is one decision point in a larger retrieval roadmap; it does not cover hybrid routing, fusion, reranking, or telemetry design. For that broader end-state, see `docs/local/BEAUTYQ_ES_QDRANT_HYBRID_RETRIEVAL_ROADMAP.md`.

Current source-backed baseline:

- `BeautySearchRouteModules.apiQdrantExplicitOptIn` exists and is disabled by default.
- `BeautySearchRouteModules.seedCatalogQdrantExplicitOptIn` and `BeautySearchCatalogBackendModules.seedResourceQdrantExplicitOptIn` implement the matching opt-in backend path.
- Default production `POST /beauty-search` remains ES-backed through `LeaderboardPlugin.modules.apiBase[IO]` plus `BeautySearchRouteModules.apiElasticsearch`.
- Current production backend remains seed-resource catalog snapshot + `ElasticsearchSearchBackend`.
- Production route activation is not approved today.

Current green evidence:

- Option97 full suite green: `1189` run, `1189` succeeded, `0` failed, `0` aborted, `1` canceled, `2` pending.
- Option101 real resource-gated Qdrant smoke green: deterministic smoke `40` succeeded, `0` failed, `0` aborted, `0` pending; resource-gated smoke `7` succeeded, `0` failed, `0` aborted, `0` pending.

Interpret that evidence narrowly:

- It is enough to treat the explicit opt-in Qdrant route as ready for disabled-by-default use.
- It is offline/evidence-based readiness, not production telemetry.
- It does not approve production activation of the default `/beauty-search` route.

Before any future default-route switch can be implemented, all of the following must be true:

- explicit production-route activation approval exists;
- the approved target is a default-route switch, not only explicit opt-in route approval;
- a default graph exposure plan names the exact source change that will expose the new default route;
- a rollback/disable plan exists for the default route;
- observability/status evidence is judged sufficient for production use;
- route exposure tests cover the approved default graph shape;
- full-suite verification is rerun after the implementation;
- no hidden hybrid serving, fallback, score fusion, reranking, shadow serving, or traffic mirroring is being introduced under the activation patch.

Non-requirements for this decision:

- real production traffic;
- shadow serving;
- traffic mirroring.

There is no real production traffic in this project context, so activation remains an explicit approval and implementation decision built from offline evidence plus route/control safety, not a telemetry threshold.

If production activation is explicitly approved later, option103 would be allowed to implement only:

- the approved default graph exposure change for `/beauty-search`;
- the approved rollback/disable control for that default route change;
- the approved observability/status surface tied to that activation decision;
- the route exposure tests required by the approved default graph shape;
- the required post-implementation full-suite verification closeout.

Option103 would still not approve or implement:

- hybrid serving;
- fallback;
- score fusion;
- reranking;
- shadow serving;
- traffic mirroring.
