# U real-ES route 500 investigation

## Status

AP1 is not cleared.

U materially advanced the blocker investigation, but it did not prove the default `/beauty-search` route against real Elasticsearch with a non-empty HTTP response.

## Goal

Prove the default Beauty search route/module graph against real Elasticsearch:

* real Elasticsearch;
* seeded/indexed Beauty search data;
* actual `POST /beauty-search` through the http4s/Tapir route;
* `200 OK`;
* non-empty `variantCarousel`;
* latency evidence;
* no Qdrant, fallback, score fusion, reranking, shadow/mirror, or route switch.

## Confirmed working

Record the following confirmed facts:

* real Elasticsearch index is created and seeded;
* direct ES request returns hits;
* ES request + response interpreter pipeline returns non-empty results;
* `BeautySearchService.search` returns non-empty results;
* Circe/Tapir encoding of populated `BeautySearchResponse` works;
* a custom route over the same service can return `200 OK`.

## Failing path

Record the failing path:

* actual graph `BeautySearchApi` `/beauty-search` route returns `500 Internal Server Error` with an empty body.

## Ruled out

Record these rejected hypotheses:

* missing Elasticsearch data;
* Elasticsearch interpreter failure;
* service-level search failure;
* response JSON encoding failure.

## Current suspected area

Record the suspected area without overclaiming:

* actual `BeautySearchApi` route wiring;
* Tapir `serverLogic` path;
* graph-injected `Async[Task]` / effect instance;
* DI-provided API instance versus manually constructed route over the same service.

## Next task

The next task is not more open-ended debugging.

The next task is a minimal failing regression spec:

* build the same real-ES route graph;
* prove `BeautySearchService.search(input)` returns a non-empty successful response;
* prove actual graph `BeautySearchApi` route `POST /beauty-search` currently returns `500`;
* keep the spec minimal;
* then fix the route path until the same spec returns `200 OK` with non-empty `variantCarousel`.

## Notes

* Do not proceed to runtime hybrid execution until this route blocker is reduced and fixed.
* Do not commit the large diagnostic U patch as-is.
* This document is an investigation handoff, not an activation approval.
