# BeautyQ ES-backed seed search demo

## What this demo shows

* Production HTTP endpoint `POST /beauty-search` exposed through `LeaderboardPlugin` top-level via `modules.apiBase[IO]` plus `BeautySearchRouteModules.apiElasticsearch`.
* Default route uses Elasticsearch over seed catalog (seed resource catalog snapshot indexed into Elasticsearch + `ElasticsearchSearchBackend`).
* Free-string beauty queries return useful seed-backed results.
* Selected demo queries are validated by `BeautySearchElasticsearchBusinessDemoSpec`.

## What this demo does not claim

* No live repository freshness.
* No production indexing lifecycle.
* No alias/blue-green rollout.
* No Qdrant shadow/hybrid serving.
* No fallback.
* No score fusion/reranking.
* No public API contract redesign.

## Verification command

```bash
sbt 'project bifunctor-tagless' Test/compile 'testOnly leaderboard.search.BeautySearchElasticsearchBusinessDemoSpec'
```

Expected result:

* 12 selected demo queries pass;
* each returns non-empty `variantCarousel`;
* each returns at least one acceptable seed variant.

## Demo request shape

```http
POST /beauty-search
Content-Type: application/json

{
  "query": "маникюр гель лак",
  "userLat": 53.58,
  "userLon": 10.08,
  "limit": 5
}
```

## What to point out in the response

* `variantCarousel` — main purchasable/search-result unit (`MasterServiceOfferVariant`);
* `providerCarousel` — provider/location result block (`MasterLocation`);
* `serviceIntentCarousel` — canonical service-intent/navigation block (`Service`);
* `facets` — catalog/spec-derived metadata;
* `inferredFilters` — parsed/domain intent metadata;
* zero-hit ES responses may still include non-empty facets/inferred filters because those are derived from catalog, spec, and parsed intent metadata rather than only from hit lists.

## Demo query set

Full inventory with acceptable variants: `docs/demo/beauty-search-es-seed-demo-queries.md`

| # | Query ID | Query text | Language | Purpose |
|---|---|---|---|---|
| 1 | `q_nails_001` | `маникюр гель лак` | ru | Core nail search: gel polish manicure |
| 2 | `q_nails_006` | `педикюр без лака` | ru | Pedicure without coating (negative attribute) |
| 3 | `q_nails_003` | `shellac entfernen und neu` | de | German-language shellac manicure with removal |
| 4 | `q_face_001` | `aquafacial` | en | Face/cosmetology: English free-text facial treatment |
| 5 | `q_brows_001` | `брови ламинирование с окрашиванием` | ru | Brows: lamination with tinting, multiple attributes |
| 6 | `q_lashes_001` | `ресницы классика` | ru | Lashes: classic 1D extension |
| 7 | `q_pmu_001` | `перманент губы` | ru | Permanent make-up: lips PMU, high-value service |
| 8 | `q_hair_001` | `депиляция верхняя губа воском` | ru | Hair removal: upper lip waxing |
| 9 | `q_hair_004` | `laser hair removal armpits` | en | Hair removal: English, laser, armpits |
| 10 | `q_broad_001` | `салон красоты wandsbek ногти` | ru | Broad/free-string: salon exploration with location |
| 11 | `q_brows_005` | `брови хна` | ru | Brows: henna tinting |
| 12 | `q_hair_008` | `sugaring upper lip рядом` | mixed | Mixed-language hair removal: sugaring with Russian "nearby" |

## Suggested demo flow

1. Start with one Russian nail query (`q_nails_001` — `маникюр гель лак`); show `variantCarousel` results with price, duration, and distance.
2. Show one German/mixed query (`q_nails_003` — `shellac entfernen und neu`); demonstrate cross-language lexical retrieval.
3. Show one face/cosmetology query (`q_face_001` — `aquafacial`); demonstrate non-nail service coverage.
4. Show one broad/local query (`q_broad_001` — `салон красоты wandsbek ногти`); demonstrate free-string exploration with location.
5. Close with limitations and next roadmap items.

## Next checkpoints after demo

* Repeatable demo output snapshots if needed.
* Business feedback on query/result quality.
* Repository freshness / live indexing.
* Operational readiness / observability.
* Qdrant shadow/hybrid later (B-lite eval: ES-native + Qdrant-native benchmark comparison).
