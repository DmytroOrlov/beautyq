# Qdrant Production Activation Decision Criteria

This document defines the separate approval gate for any future default `POST /beauty-search` switch from the current ES-backed route to a Qdrant-backed route.
It is one decision point in a larger retrieval roadmap; it does not cover hybrid routing, fusion, reranking, or telemetry design. For that broader end-state, see `docs/codebase-review/BEAUTYQ_ES_QDRANT_HYBRID_RETRIEVAL_ROADMAP.md`.

Current source-backed baseline:

- `BeautySearchRouteModules.apiQdrantExplicitOptIn` exists and is disabled by default.
- `BeautySearchRouteModules.seedCatalogQdrantExplicitOptIn` and `BeautySearchCatalogBackendModules.seedResourceQdrantExplicitOptIn` implement the matching opt-in backend path.
- Default production `POST /beauty-search` remains ES-backed through `LeaderboardPlugin.modules.apiBase[IO]` plus `BeautySearchRouteModules.apiElasticsearch`.
- Current production backend remains seed-resource catalog snapshot + `ElasticsearchSearchBackend`.
- Default route activation is not approved today.

Earlier full-suite and resource-gated Qdrant smoke runs were green; both are superseded by the M17A full-suite checkpoint in `docs/BEAUTYQ_CURRENT_STATE_AND_HANDOFF.md` section 1. The opt-in route readiness conclusion below still holds.

Interpret that evidence narrowly:

- It is enough to treat the explicit opt-in Qdrant route as ready for disabled-by-default use.
- It is offline/evidence-based readiness, not production telemetry.
- It does not approve default route activation of the `/beauty-search` route.

Before any future default-route switch can be implemented, all of the following must be true:

- explicit default-route activation approval exists;
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

There is no real production traffic in this project context, so live/default route activation remains an explicit approval and implementation decision built from offline evidence plus route/control safety, not a telemetry threshold. Activation-grade latency/failure evidence is still required through accepted local/resource/prod-like proof.

If default route activation is explicitly approved later, that approval would allow implementing only:

- the approved default graph exposure change for `/beauty-search`;
- the approved rollback/disable control for that default route change;
- the approved observability/status surface tied to that activation decision;
- the route exposure tests required by the approved default graph shape;
- the required post-implementation full-suite verification closeout.

It would still not approve or implement:

- hybrid serving;
- fallback;
- score fusion;
- reranking;
- shadow serving;
- traffic mirroring.
