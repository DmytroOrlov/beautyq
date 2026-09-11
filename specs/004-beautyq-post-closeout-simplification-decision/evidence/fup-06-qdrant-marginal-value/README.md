# Feature 004 — FUP-06 Current Gen2 Qdrant Marginal-Value Assessment

This directory is **repository-owned supporting evidence**, not a normative product/source owner. Current
source, tests, and normative documents remain current truth.

- It **answers the FUP-06 evidence question** with the accepted same-execution comparison and the actual
  human blind calibration.
- It **does not reopen, change, or supersede any Feature 004 disposition**, and it **authorizes no**
  production serving change, Qdrant default/activation, Required-mode policy, rollout, or implementation
  follow-up.
- It preserves the decision-critical conclusions so a later review does not depend on the gitignored local
  run, an agent transcript, or another review bundle.

**Subsequent product decision (supersedes for ongoing acceptance).** FUP-08 measured and rejected the
declared-family mitigation and recorded that the V2 `HARM` verdict below is a **historical
relevance-quality result, not a current acceptance rule or safety blocker**. The measurements and
frozen V2 verdict below are retained unchanged; see
`../fup-08-declared-family-counterfactual/README.md`.

## Question answered

Does Qdrant supplementation add enough useful search value to say it has demonstrated marginal relevance
value over the Elasticsearch baseline?

## Evaluated comparison shape

- **Same bound execution, two arms.** FullSearch result from the accepted Real FullSearch run vs its
  **baseline-only projection** produced by `BeautyQNoHarmSupplementEvidence.fromExecution`
  (`baselineOnly(result.baseline, result.evaluation)`), i.e. supplement-origin hits removed with baseline
  order preserved.
- **Why valid / no second run needed.** Both arms share application state, source snapshot, ES generation,
  request, corpus judgment, and page policy by construction. Source confirms the projection is faithful:
  baseline-prefix and baseline-owned-component violation counts are 0, and full-arm ranking metrics
  reproduce exactly from the projection plus appended IDs. The runner never needs a second BaselineOnly
  execution.
- **Run:** Required/FullSearch, canonical corpus `beautyq-wandsbek-hamburg-v1` (180 cases), 1 warmup + 3
  measured passes, deterministic. ES 8.14.3, Qdrant 1.18.3, embedding `llama.cpp` 1024d,
  evaluation policy `beautyq-evaluation-policy-v1`.

## Hard gates (never tradeable) — PASS

All zero: forbidden hits, request degradation, duplicate identity, baseline result loss/reorder,
baseline-owned component changes, append-budget violations. No formal hard constraint is violated.

## Automatic metric summary (variants surface, cutoffs 1/3/5/10)

All 180 cases use partial judgments, so precision/recall/standard NDCG are not applicable. Applicable
metrics (baseline sum → FullSearch sum):

| cutoff | success | MRR | judged-precision | pooled-NDCG | improved / regressed cases |
| ---: | --- | --- | --- | --- | --- |
| 1 | 90 → 147 | 90.000 → 147.000 | 90.000 → 147.000 | 90.000 → 147.000 | 57 / 0 |
| 3 | 96 → 153 | 92.833 → 149.833 | 96.000 → 153.000 | 84.235 → 128.907 | 57 / 0 |
| 5 | 96 → 153 | 92.833 → 149.833 | 96.000 → 153.000 | 84.106 → 128.331 | 57 / 0 |
| 10 | 97 → 154 | 93.000 → 150.000 | 97.000 → 154.000 | 84.106 → 128.175 | 57 / 0 |

No metric regresses; `unjudged_rate` rises because of empty-baseline unjudged additions.

## Case-level aggregate summary (84 differing cases)

Evidence layers are kept distinct:

- **Corpus judgment** — tracked `acceptable`/`forbidden`/`neutral`/unjudged labels. These are corpus
  judgments, not human calibration.
- **Automatic evidence** — baseline IDs, appended IDs, positions, ranking metrics, gates.
- **Human blind judgment** — the actual A/B preferences collected during FUP-06 calibration.

| class | count | source |
| --- | ---: | --- |
| STRONG_GAIN (corpus-acceptable added over empty baseline) | 57 | corpus judgment |
| WEAK_GAIN (on-family unjudged addition) | 18 | automated heuristic |
| NEUTRAL | 4 | 3 automated heuristic + 1 human blind |
| WEAK_HARM | 4 | human blind |
| STRONG_HARM | 1 | human blind |
| **total** | **84** | |

The 57 gains are corpus-certified `acceptable` results added where the ES baseline returned nothing; they
are not human-calibration results.

## Human blind calibration of the pivotal six

Each judgment is bound to the FullSearch arm through the blinded A/B card mapping (each card had exactly
one non-empty arm).

| case | query meaning | addition | human preference | class |
| --- | --- | --- | --- | --- |
| `q_semantic_003` | remove upper-lip fuzz without laser | hair_removal/laser/armpits | empty slightly better | WEAK_HARM |
| `q_semantic_008` | home-care / home-visit facial context | pmu/brows | empty slightly better | WEAK_HARM |
| `q_holdout_face_aquafacial_001` | deep cleanse/refresh face without injections | pmu/lips | empty slightly better | WEAK_HARM |
| `q_holdout_face_anti_age_decollete_001` | anti-age face / neck / décolleté | pmu/brows | empty slightly better | WEAK_HARM |
| `q_holdout_hair_armpits_laser_course_001` | course to keep armpits smooth longer | pmu/lips | empty clearly better | STRONG_HARM |
| `q2i7_recovery_067` | 2D lash extension / added volume | pmu/eyeliner | same | NEUTRAL |

The automated heuristic had classified all six as STRONG_HARM; the actual human calibration corrects four
to WEAK_HARM, one to NEUTRAL, and confirms one as STRONG_HARM.

## Verdict (frozen V2 rule, not weakened after the result)

**HARM.** All hard constraints pass and 57 corpus-certified strong gains exist, but one human-confirmed
STRONG_HARM remains (`q_holdout_hair_armpits_laser_course_001`): a permanent-makeup-lips result is shown as
the sole result of an empty page for an armpit-hair-removal-course intent, and the reviewer stated it would
reduce trust in search. Under V2 one or more STRONG_HARM implies HARM.

## Crucial interpretation (do not collapse)

- **Marginal relevance value exists** — substantial evidence that Qdrant retrieves useful results the ES
  baseline misses (57 empty-baseline rescues; success/MRR +57; zero regressions).
- **Current acceptance verdict is HARM** — the same supplementation also produces at least one
  human-confirmed trust-breaking prominent mismatch, which fails the frozen V2 product-value acceptance
  rule.

This is **not** "Qdrant has no value."

## Limitations

- Protected per-hit supplement observations remain redacted by design; the separate protected acceptance
  run (24 cases, policy v8) is green on hard no-harm but cannot be used to compute protected marginal value.
- Only the pivotal six additions received explicit human blind judgments. The remaining 78 classifications
  are corpus-judgment/automatic evidence and are labeled as such.
- This assessment establishes (or refutes) marginal relevance value only.

## Authorization boundary

No runtime, Qdrant default, activation, Required-mode, rollout, corpus-judgment, or Feature 004 disposition
change is authorized here. A possible future mitigation (filtering trust-breaking semantic mismatches) is
not queued or authorized by this evidence; it is reported to the coordinator.

## Provenance

- Tracked source: this file.
- Local raw run (gitignored, for reproducibility): `.evidence-runs/fup-06-qdrant-marginal-value-20260911/`
  (`blinded-ab-cards.json`, `human-blind-calibration.json`, `reconciled-classification.json`,
  `case-level-evidence.json`).
- Accepted artifacts (gitignored): `target/search-gen2/beautyq-evaluation-*.json`.
- Fingerprints: source-content `c94327bc85913d44edfa7eb3cc255193a71e1e48382704ba2645ae07bd8b9c81`;
  projected-documents `4ebbdef04f97b48b96040a3e6d9c745bcc452e8735cae437168d2153b988ff67`;
  ES generation `beautyq_variant_gen2_0fa527942a4e20d5ada920c188493794343ef2837a7cb35106ec868665c7605d`;
  Qdrant generation `9765b28a9257940a3d22e74f4ce95c5e16a25bdf8c778224ad026d7e9476bf24`.
