# Beauty Search ES Seed Demo Query Inventory

## Scope

Default `POST /beauty-search` is now ES-backed over seed data through
the default ES route composition (`BeautySearchRouteModules.apiElasticsearch`
→ `seedCatalogElasticsearchPortConfigured`). This inventory selects demo
queries for business-facing smoke and demo over the seed-backed route. It does
not make claims about ranking perfection, Qdrant, hybrid, fallback,
reranking, or lifecycle behavior.

All queries and sample variants below come directly from
`beautyq_search_eval_queries_v1.json`.

## Selected demo queries

### 1. `q_nails_001` — маникюр гель лак

| Field | Value |
|---|---|
| Query | `маникюр гель лак` |
| Language | ru |
| Query types | direct, attribute |
| Demo purpose | Core nail search: gel polish manicure, the most common intent |
| Expected behavior | Non-empty variant carousel with manicure + gel\_polish variants |
| Sample variants | |
| — Lilly's Beauty Wandsbek | Маникюр, gel\_polish, 39 EUR, 60 min, 0.11 km |
| — Studio Manana Wandsbek | Маникюр, gel\_polish, 35 EUR, 45 min, 0.31 km |

### 2. `q_nails_006` — педикюр без лака

| Field | Value |
|---|---|
| Query | `педикюр без лака` |
| Language | ru |
| Query types | direct, attribute |
| Demo purpose | Pedicure without coating: negative attribute ("no coating") |
| Expected behavior | Non-empty variant carousel with pedicure + no\_coating variants |
| Sample variants | |
| — Lilly's Beauty Wandsbek | Педикюр, no\_coating, 38 EUR, 50 min, 0.11 km |
| — Studio Manana Wandsbek | Педикюр, no\_coating, 42 EUR, 50 min, 0.31 km |

### 3. `q_nails_003` — shellac entfernen und neu

| Field | Value |
|---|---|
| Query | `shellac entfernen und neu` |
| Language | de |
| Query types | german, attribute\_heavy |
| Demo purpose | German-language shellac manicure with removal |
| Expected behavior | Non-empty variant carousel with shellac + removal variants |
| Sample variants | |
| — Studio Manana Wandsbek | Маникюр, shellac, 45 EUR, 60 min, 0.31 km, with removal |
| — Beauty de Luxe Wandsbek | Маникюр, shellac, 48 EUR, 70 min, 0.35 km, with removal |

### 4. `q_face_001` — aquafacial

| Field | Value |
|---|---|
| Query | `aquafacial` |
| Language | en |
| Query types | direct |
| Demo purpose | Face/cosmetology: English free-text facial treatment |
| Expected behavior | Non-empty variant carousel with Косметология лица variants |
| Sample variants | |
| — Mira Beauty Wandsbek | Косметология лица, 89 EUR, 75 min, 1.11 km |

### 5. `q_brows_001` — брови ламинирование с окрашиванием

| Field | Value |
|---|---|
| Query | `брови ламинирование с окрашиванием` |
| Language | ru |
| Query types | direct, attribute\_heavy |
| Demo purpose | Brows: lamination with tinting, multiple attribute matches |
| Expected behavior | Non-empty variant carousel with brow lamination + tinting |
| Sample variants | |
| — Studio Manana Wandsbek | Брови, lamination, 42 EUR, 45 min, 0.31 km, with tinting |
| — Kolibri Beauty Wandsbek | Брови, lamination, 55 EUR, 60 min, 0.36 km, with tinting |

### 6. `q_lashes_001` — ресницы классика

| Field | Value |
|---|---|
| Query | `ресницы классика` |
| Language | ru |
| Query types | direct, attribute |
| Demo purpose | Lashes: classic 1D extension |
| Expected behavior | Non-empty variant carousel with lash extension + classic1\_d |
| Sample variants | |
| — Lilly's Beauty Wandsbek | Ресницы, extension, classic1\_d, 80 EUR, 120 min, 0.11 km |
| — Studio Manana Wandsbek | Ресницы, extension, classic1\_d, 85 EUR, 120 min, 0.31 km |

### 7. `q_pmu_001` — перманент губы

| Field | Value |
|---|---|
| Query | `перманент губы` |
| Language | ru |
| Query types | direct, attribute |
| Demo purpose | Permanent make-up: lips PMU, high-value service |
| Expected behavior | Non-empty variant carousel with PMU lips variants |
| Sample variants | |
| — Studio Manana Wandsbek | Permanent Make-Up, lips, 320 EUR, 170 min, 0.31 km |
| — Kolibri Beauty Wandsbek | Permanent Make-Up, lips, 340 EUR, 180 min, 0.36 km |

### 8. `q_hair_001` — депиляция верхняя губа воском

| Field | Value |
|---|---|
| Query | `депиляция верхняя губа воском` |
| Language | ru |
| Query types | direct, attribute\_heavy |
| Demo purpose | Hair removal: upper lip waxing |
| Expected behavior | Non-empty variant carousel with wax + upper\_lip variants |
| Sample variants | |
| — Beauty de Luxe Wandsbek | Удаление волос, wax, upper\_lip, 18 EUR, 15 min, 0.35 km |

### 9. `q_hair_004` — laser hair removal armpits

| Field | Value |
|---|---|
| Query | `laser hair removal armpits` |
| Language | en |
| Query types | english, attribute |
| Demo purpose | Hair removal: English, laser, armpits, multi-provider |
| Expected behavior | Non-empty variant carousel with laser armpit variants |
| Sample variants | |
| — Lavi Beauty Wandsbek | Удаление волос, laser, armpits, 39 EUR, 25 min, 0.06 km |
| — Kosmetik by Rima Wandsbek | Удаление волос, laser, armpits, 49 EUR, 25 min, 0.66 km |

### 10. `q_broad_001` — салон красоты wandsbek ногти

| Field | Value |
|---|---|
| Query | `салон красоты wandsbek ногти` |
| Language | ru |
| Query types | broad, location |
| Demo purpose | Broad/free-string: salon exploration with location and service hint |
| Expected behavior | Non-empty variant carousel with nail-related variants near Wandsbek |
| Sample variants | |
| — Lilly's Beauty Wandsbek | Маникюр, 30 EUR, 40 min, 0.11 km |
| — Lilly's Beauty Wandsbek | Педикюр, 38 EUR, 50 min, 0.11 km |

### 11. `q_brows_005` — брови хна

| Field | Value |
|---|---|
| Query | `брови хна` |
| Language | ru |
| Query types | direct, attribute |
| Demo purpose | Brows: henna tinting |
| Expected behavior | Non-empty variant carousel with brow henna variants |
| Sample variants | |
| — Beauty de Luxe Wandsbek | Брови, henna, 39 EUR, 40 min, 0.35 km, with tinting + correction |

### 12. `q_hair_008` — sugaring upper lip рядом

| Field | Value |
|---|---|
| Query | `sugaring upper lip рядом` |
| Language | mixed |
| Query types | english, attribute |
| Demo purpose | Mixed-language hair removal: sugaring with Russian "nearby" |
| Expected behavior | Non-empty variant carousel with sugaring upper\_lip variants |
| Sample variants | |
| — Beauty Life Concept Wandsbek | Удаление волос, sugaring, upper\_lip, 30 EUR, 20 min, 0.72 km |

## How to use this inventory

- Use these queries for the next ES-backed route smoke/demo spec.
- Assert route status/shape and useful non-empty results for selected
  positive demo queries.
- Do not use this doc to define public API contracts.
- Do not use it to justify Qdrant, hybrid, fallback, or reranking.

## Known limitations

- Seed data only; not full production search lifecycle.
- No freshness or live indexing.
- No alias/blue-green.
- No Qdrant or hybrid serving.
- No fallback, reranking, or score fusion.
- Current invalid request behavior is characterized elsewhere and is not
  demo UX.
- Query ordering, ranking quality, and facet completeness are not
  asserted by this inventory; they are future hardening concerns.
