# Feature 004 — FUP-08 Declared-Family Counterfactual

This directory is **repository-owned supporting evidence**, not a normative runtime/policy owner. Current
source, tests, and normative documents remain current truth. It does not authorize implementation, Qdrant
defaults/activation/rollout, or any Feature 004 disposition change.

**Process note.** A blocked or negative result is itself decision-critical evidence. Tracked evidence is
not conditional on a successful implementation; this owner exists precisely because the candidate
mitigation was measured and found to require a human product/architecture decision.

## Question

What would happen if semantic supplementation were allowed only when the query/candidate plan has a
declared service/category family that reaches the existing typed hard-constraint path?

## Exact meaning of `DECLARED_FAMILY`

`DECLARED_FAMILY` = some matched intent rule's `hardActions` contains a family-bearing action
(`Service`, `ServiceAny`, or `Category`), which the current canonical path compiles into an enforceable
`serviceCode` / `categoryCode` `PlannedConstraint`:

`BeautyQIntentParserGen2` → `BeautyQSearchPlanCompiler` → `BeautyQCandidatePlanCompiler`
(`CandidatePlan.hardConstraints`) → `QdrantCandidateRequestCompiler` (retrieval filter) +
`CandidateHydrator` (`RequireAll` assertion), with `BeautyQIntentActionCompiler` performing the
action→constraint mapping.

Attribute actions (e.g. `body_area=armpits`, `nail_coating_type=shellac`), `NearUser`, and budget do
**not** count. This is a source-grounded typed definition, not a human semantic guess.

## Why this candidate exists

Current FUP-08 root cause: the trust-breaking query
`q_holdout_hair_armpits_laser_course_001` parses to **no** family-bearing action, so the existing
compatibility enforcement has no query-side family to compare against and cannot reject the wrong-service
Qdrant candidate.

## Counterfactual result

The candidate rule was **measured, not implemented**, over the 84 accepted FUP-06 differing cases
(binary: `NO_DECLARED_FAMILY` → no supplement emitted → baseline-only).

| class (origin) | before | retained | removed/lost |
| --- | ---: | ---: | ---: |
| corpus-certified `STRONG_GAIN` rescues | 57 | **53** | **4** |
| automated heuristic `WEAK_GAIN` | 18 | 14 | 4 |
| `NEUTRAL` (3 automated heuristic + 1 human blind) | 4 | 2 | 2 |
| human blind `WEAK_HARM` | 4 | 0 | 4 |
| human blind `STRONG_HARM` | 1 | **0** | **1** |
| total differing cases | 84 | 69 | 15 |

- `57 corpus-acceptable rescues → 53 retained / 4 lost`
- `1 human-confirmed STRONG_HARM → 0 retained / 1 removed`
- automated weak gains: `18 → 14 retained / 4 lost`
- neutral additions: `2 removed` (1 automated heuristic, 1 human blind)
- weak harms: `4 removed`

## Lost corpus-certified rescues

The four measured cases and why they lack a declared family:

- `q_broad_006` — "beauty near Wandsbek Markt" — no family rule matched (broad discovery + location).
- `q_semantic_001` — "хочу чтобы кожа выглядела свежей и напитанной перед событием" — no family rule
  matched (conversational facial).
- `q_semantic_010` — "быстро убрать волосы в подмышках курсом" — no family rule matched (conversational
  armpit hair removal).
- `q2i7_recovery_065` — "Maniküre mit Shellac-Lack und Ablöse" — only a non-family coating rule (`r022`,
  `nail_coating_type=shellac`) matched; the current vocabulary does not recognize German `Maniküre` as a
  family alias.

Corpus judgments are unchanged by this evidence.

## Pivotal structural finding

`q_semantic_010` is a **corpus-certified useful rescue** and
`q_holdout_hair_armpits_laser_course_001` is the **human-confirmed STRONG_HARM**. They express
materially the **same armpit-hair-removal intent**, both have **no declared family**, and the blunt
declared-family gate treats them identically.

Therefore the gate is **not harm-specific**: it cannot discriminate the trust-breaking mismatch from
useful value for the same intent. This is the key product/architecture result.

## Existing cheaper signal

**NONE FOUND.** The only typed query-side family signal reaching candidate acceptance is the existing
hard-constraint path; the candidate document's own service/category cannot establish compatibility
without a query-side family, and no retrieval score threshold exists (`scoreThreshold = None`). This says
nothing about whether some future signal could exist; none is currently available without introducing a
new semantic policy owner.

## Human product decision (recorded)

The declared-family gate was **measured and rejected**; its measured cost is not worthwhile for the
current product objective.

- BeautyQ search relevance is a **low-stakes relevance-quality** concern, not safety-critical. The
  observed mismatch is a soft relevance/UX cost, not a safety event or a zero-tolerance release blocker.
- Preserving useful semantic retrieval takes priority over eliminating the observed residual relevance
  mismatch.
- The gate is **not implemented**, and **no implementation follows from FUP-08**; no new semantic
  classifier is introduced solely to remove this mismatch.
- The one `STRONG_HARM` label remains **historically valid under the FUP-06 V2 rubric**. Here
  `STRONG_HARM` is a **relevance-quality classification, not a safety classification**.
- The V2 rule "any `STRONG_HARM` ⇒ `HARM`" remains the **historical FUP-06 experiment verdict** but is
  **not the current ongoing product acceptance policy** after this decision.
- Existing true hard invariants — forbidden constraints, baseline loss/reordering, duplicates,
  degradation, baseline-owned component corruption, append-budget violations — are **unchanged**. This
  decision applies to semantic relevance-quality mismatches only, not to those hard gates.
- No numeric error budget is defined, and there is **no requirement that this or any specific bad result
  must later be fixed**. A future narrower precision improvement may be pursued only if it can improve
  relevance without materially sacrificing retrieval value; that direction is explored solely by the
  queued architecture/offline-feasibility follow-up **FUP-09** (generic query-side semantic label
  inference), which authorizes no production implementation and creates no obligation to remove any
  specific result.

**Measured trade-off that informed the decision** (counts unchanged):

- all 57 demonstrated corpus-certified semantic rescues are preserved by not implementing the gate (the
  rejected gate would have lost 4 of them, plus 4 / 18 automated weak gains and 2 neutral additions);
- the gate would have removed the one human-confirmed `STRONG_HARM` and all 4 human `WEAK_HARM`, but
  cannot discriminate the same-intent good/bad armpit pair.

## Provenance

- Read-only reconstruction (gitignored, local): `.evidence-runs/fup-06-qdrant-marginal-value-20260911/`
  `fup-08-counterfactual/` (`README.md`, `counterfactual-summary.json`, `counterfactual-cases.json`,
  plus `scala_rules.py` / `match.py` / `counterfactual.py`).
- Accepted FUP-06 comparison owner:
  `../fup-06-qdrant-marginal-value/README.md`.
- The reconstruction was validated against `BeautyQEvaluationIntentCorrectionSpec` and all 60
  exact-constraint inventory cases; the vocabulary was last changed 2026-08-20, before the accepted run.
- All decision-critical conclusions are contained in this tracked README; raw per-case JSON remains local.
