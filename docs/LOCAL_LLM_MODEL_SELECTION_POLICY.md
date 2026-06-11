AI-ENTRYPOINT
LOCAL LLM REFERENCE
BEAUTYQ MODEL SELECTION POLICY
Read this file before choosing an LLM model for BeautyQ development tasks

# BeautyQ Model Selection Policy

This document defines operational guidance for choosing an LLM model during
local development and review loops in the BeautyQ repository.

It is not a benchmark. It is not product architecture. It is not runtime routing.
It is a practical repo-local heuristic for developers who need to pick a model
from the currently available endpoints.

Model choice follows task type and risk, not GPT availability. See section 9 for
the current BeautyQ decision order. The main operational constraint is that GPT
usage can be exhausted for more than half of the week; therefore GPT should be
reserved for complex code writing and very high-risk production work, not routine
review or token-heavy exploration.

## 1. Model Capability Table

| Model | Blended price USD / 1M tokens | Intelligence Index | Coding Index | Agentic Index |
|---|------------------------------:|---:|---:|---:|
| GPT-5.5 xhigh |                          4.35 | 60.2 | 59.1 | 74.1 |
| GPT-5.5 high |                          4.35 | 58.9 | 58.5 | 72.0 |
| GPT-5.5 medium |                          4.35 | 56.7 | 56.2 | 69.4 |
| MiniMax-M3 |                          0.22 | 54.7 | 43.4 | 68.6 |
| MiMo-V2.5-Pro |                          0.18 | 53.8 | 45.5 | 67.4 |
| MiMo-V2.5 |                          0.06 | 49.0 | 42.1 | 65.5 |
| Qwen3.6 35B A3B |                           0.0 | 43.5 | 35.2 | 58.3 |

Notes:

- Prices are blended USD per 1M tokens. Lower is better.
- Intelligence Index: higher is better. General reasoning / benchmark-composite signal.
- Coding Index: higher is better. Code-writing / code-editing signal.
- Agentic Index: higher is better. Multi-step autonomous execution signal.
- Intelligence Index is not a BeautyQ-specific architecture benchmark. It is a
  general reasoning signal that helps with architecture review, risk assessment,
  lifecycle/boundary judgment, and avoiding overclaims.
- Architecture work in this repository needs all three signals:
  - Intelligence for conceptual reasoning and risk judgment.
  - Coding for Scala/ZIO/Distage/Tapir/Postgres correctness.
  - Agentic capability for repo audit, evidence collection, multi-file planning,
    and validation loops.
- These indexes are operational selection hints, not benchmark truth for BeautyQ.

## 2. Derived Formulas

These formulas are repo-local heuristics. They are not scientific proof and not
official BeautyQ benchmarks.

```text
coding_value = CodingIndex / BlendedPrice
agentic_value = AgenticIndex / BlendedPrice
balanced_value = (0.55 * CodingIndex + 0.45 * AgenticIndex) / BlendedPrice
````

Use:

* `coding_value` for cheap code-writing / code-editing.
* `agentic_value` for cheap multi-step autonomous work.
* `balanced_value` for normal bounded development tasks.

Mode-specific fit:

```text
gpt_saving_fit = 0.45 * normalized(CodingIndex)
               + 0.35 * normalized(AgenticIndex)
               + 0.20 * normalized(1 / BlendedPrice)

no_gpt_fit = 0.50 * normalized(CodingIndex)
           + 0.35 * normalized(AgenticIndex)
           + 0.15 * normalized(1 / BlendedPrice)
```

Use:

* `gpt_saving_fit` when GPT is still available but must be conserved. Cheap
  models can do more draft work because GPT can still do final review.
* `no_gpt_fit` when Weekly usage limit is 0% remaining. It penalizes weak coding
  ability more strongly because GPT will not rescue final quality.

Architecture-specific fit:

```text
architecture_fit = 0.40 * normalized(IntelligenceIndex)
                 + 0.35 * normalized(AgenticIndex)
                 + 0.25 * normalized(CodingIndex)

architecture_code_fit = 0.35 * normalized(IntelligenceIndex)
                      + 0.30 * normalized(AgenticIndex)
                      + 0.35 * normalized(CodingIndex)
```

Use:

* `architecture_fit` for design review, lifecycle/boundary decisions,
  production-risk reasoning, and avoiding incorrect claims.
* `architecture_code_fit` for architecture tasks that also require real code
  changes.
* `normalized(...)` is min-max normalization over this corrected 7-model set only.

## 3. Practical Model Selection

### Default rule

Model choice is driven by task type, source-truth status, and risk, not by
GPT availability mode. See section 9 for the BeautyQ decision order.

Use cheaper models for:

* repository search;
* file inventory;
* first-pass patch planning;
* documentation drafts;
* changelogs and summaries;
* mechanical refactors under a strict plan;
* collecting evidence for review.

Use GPT for:

* final architecture review;
* production-risk reasoning;
* lifecycle and boundary decisions;
* dangerous migration/design decisions;
* reviewing narrow high-risk diffs;
* validating test strategy for risky changes.

## 4. GPT-Saving Mode (reference)

These model-specific notes are reference material, not the primary decision flow.
Primary decision flow is in section 9.

GPT is still available, but should be conserved.

### GPT-5.5 xhigh

Use only for highest-risk final review:

* lifecycle/boundary decisions;
* production-risk reasoning;
* dangerous migration/design decisions;
* final review of narrow high-risk diffs.

Do not use for grep, repo inventory, boilerplate, simple docs, or long exploratory
sessions.

### GPT-5.5 high

Use as the strong architecture/code review model when xhigh should be saved:

* architecture review with code-level understanding;
* complex code review;
* checking test strategy;
* validating patch plans prepared by cheaper models.

### GPT-5.5 medium

Use for normal GPT-side review and sanity checks:

* final sanity checks;
* moderate-risk design review;
* reviewing summaries and narrow diffs prepared by cheaper models.

Do not make GPT-5.5 medium the default worker for all tasks. It is strong, but
using it for everything is exactly how GPT becomes unavailable for the rest of
the week.

### MiniMax-M3

Main cost-efficient agentic model and best cheap non-GPT architecture-draft /
repo-audit model.

Use for:

* repository search;
* patch planning;
* documentation updates;
* multi-step mechanical refactors under a strict plan;
* preparing summaries/evidence for GPT review;
* architecture drafting when GPT should be conserved.

Reason: Intelligence 54.7 and Agentic 68.6 at $0.22 / 1M tokens.

### MiMo-V2.5-Pro

Best cheap balanced bounded code/doc worker.

Use for:

* local documentation updates;
* small Scala edits;
* test fixes;
* bounded code/doc patches;
* follow-ups after review.

Reason: Intelligence 53.8, Coding 45.5, Agentic 67.4, price $0.18.

### MiMo-V2.5

Ultra-cheap mechanical worker.

Use for:

* grep/summarize;
* changelog;
* release notes;
* simple docs;
* file classification;
* checklist preparation.

Do not use as the only author for complex Scala or architecture changes.

### Qwen3.6 35B A3B

Qwen3.6 35B A3B is the free local slow fallback for strong-cost-saving scenarios.

Use it when conserving GPT or paid endpoint usage matters more than latency.

Use for:

* documentation updates from an already defined plan;
* policy formalization;
* summarization;
* simple bounded patch proposals;
* terminology consistency checks;
* repeated instruction checks in No-GPT mode.

Do not use it as the primary model for complex Scala architecture without review.
Its Intelligence Index 43.5 and Coding Index 35.2 are the lowest in this set.

## 5. No-GPT Mode (reference)

These model-specific notes are reference material, not the primary decision flow.
Primary decision flow is in section 9.

Weekly usage limit = 0% remaining. Work must run without GPT.

### Main stack

1. **MiniMax-M3** — repo audit, agentic planning, architecture drafts,
   multi-step patch orchestration.
2. **MiMo-V2.5-Pro** — bounded code/doc worker for specific changes.
3. **Qwen3.6 35B A3B** — free slow local fallback for docs, policies, summaries,
   repeated instruction checks, and strong-cost-saving runs where latency is
   acceptable.
4. **MiMo-V2.5** — ultra-cheap mechanical helper.

### Required loop for complex work

Use a loop, not one model:

```text
1. MiniMax-M3: repo audit and patch plan.
2. MiMo-V2.5-Pro or Qwen3.6: bounded patch/docs update.
3. MiniMax-M3: adversarial review.
4. Run focused compile/tests.
5. Fix only confirmed issues.
6. If confidence is insufficient, leave a TODO/ADR note instead of making a
   production claim.
```

### Avoid without GPT

Without GPT, avoid or prohibit:

* making production lifecycle decisions in one pass;
* changing search/hybrid routing semantics without tests;
* changing DB/FK/repository constructor dependencies without source inspection;
* deleting `@unused` dependencies without proof;
* declaring non-production hybrid production-ready;
* making large Scala refactors without a focused compile/test gate.

## 6. Decision Matrix

| Task                           | GPT-Saving Mode                                                  | No-GPT Mode                                                   |
| ------------------------------ | ---------------------------------------------------------------- | ------------------------------------------------------------- |
| Repo search / inventory        | MiniMax-M3 or MiMo-V2.5                                          | MiniMax-M3                                                    |
| Simple docs                    | MiMo-V2.5 / Qwen3.6                                              | Qwen3.6 / MiMo-V2.5                                           |
| Architecture docs              | MiniMax-M3 draft + GPT-5.5 medium/high review                    | MiniMax-M3 + Qwen3.6 cross-check                              |
| Architecture with code changes | MiMo-V2.5-Pro or MiniMax-M3 draft + GPT-5.5 high review if risky | MiMo-V2.5-Pro + MiniMax-M3 adversarial review + focused tests |
| Search/hybrid semantics        | MiniMax-M3 plan + GPT-5.5 high/xhigh review                      | Avoid unless bounded; MiniMax-M3 + focused tests              |
| Production lifecycle/routing   | GPT-5.5 xhigh/high only for decision                             | Defer or document open question                               |
| Test failure triage            | MiniMax-M3                                                       | MiniMax-M3 + MiMo-V2.5-Pro                                    |
| Final PR summary               | MiMo-V2.5 or Qwen3.6                                             | Qwen3.6                                                       |

## 7. Cost-Aware Conclusions

* Best absolute architecture-review models: GPT-5.5 xhigh, GPT-5.5 high,
  GPT-5.5 medium.
* Best cheap agentic workers: MiMo-V2.5, MiMo-V2.5-Pro, MiniMax-M3.
* Best cheap non-GPT architecture-draft / repo-audit model: MiniMax-M3.
* Best cheap balanced bounded code/doc worker: MiMo-V2.5-Pro.
* Best ultra-cheap mechanical helper: MiMo-V2.5.
* Qwen3.6 35B A3B is local and slow. It is useful mainly under strong cost
  pressure for docs, policies, summaries, and terminology checks. It is not the
  primary architecture model.
* GPT should be spent on high-leverage review, not token-heavy repository
  reading.
* In GPT-saving mode, cheap models prepare narrow diffs plus evidence; GPT checks
  final risk.
* In No-GPT mode, quality comes from the loop: plan -> bounded patch ->
  adversarial review -> tests.

## 8. Scope and Limitations

* This policy is operational guidance for local development and review loops.
* It is not product architecture.
* It does not affect runtime model selection, hybrid routing, benchmark
  decisions, or production serving.
* It does not change B-lite, non-production, hybrid response adapter, normalized
  EngineEvalResult, or Qdrant/semantic resources disabled mode behavior.
* Do not treat these indexes as truth or as the project's official benchmark.
* Do not use this policy to justify production model switching.

## 9. Current BeautyQ model policy

The generic model selection policy above is subordinate to the current BeautyQ
coordinator workflow documented in `docs/local/COORDINATOR_PROMPTING_REMINDER.md`.

### Decision order

Model choice must follow this order:

1. Source truth:

   * If required source facts are missing, ask for a focused bundle and stop.
   * Do not use a stronger model to invent missing APIs, signatures, fields, or tests.

2. Task type and risk:

   * mechanical docs/code cleanup;
   * bounded docs/code patch from exact recipe;
   * source-confirmed inventory / narrow verification;
   * complex Scala/Distage/ZIO/resource code;
   * production route/lifecycle/runtime/backend migration.

3. User preference:

   * cost-saving;
   * time-saving / avoid slow local;
   * quality/safety-first.

4. Model choice.

### Model roles

Use exact model/tier names:

* `Qwen 256/512`: tiny mechanical docs/code edits.
* `Qwen 1024/2048`: bounded pure code or docs patches with exact recipe.
* `Qwen 4096`: larger but still source-confirmed local work; not broad architecture/design.
* `MiMo-V2.5`: cheap cloud worker for bounded mechanical edits, simple docs, simple tests.
* `MiMo-V2.5-Pro`: stronger cloud worker for bounded medium-risk docs/code patches and applying coordinator-designed recipes.
* `MiniMax-M3`: preferred stronger non-GPT option for source-confirmed coding/agentic repo work, source-confirmed inventory, and narrow verification when GPT should be saved.
* `GPT-5.5-medium`: complex code writer when cheaper/local models are likely to waste iterations.
* `GPT-5.5-high`: very high-risk/complex production, lifecycle, runtime, Distage graph, or backend-migration code.

Do not use vague names like `Qwen`, `MiMo`, or `GPT` when the recommendation is meant to be actionable.

### Coordinator role

Review, audit, architecture, and design are coordinator responsibilities. The coordinator should ask the user for focused bundles when source truth is missing. Do not spend GPT-agent runs on review that the coordinator can perform from a bundle.

### Examples

* Exact docs wording replace:
  `Qwen 256/512` or `MiMo-V2.5`. GPT not needed.

* Docs dedup/handoff:
  Coordinator designs ownership. `MiMo-V2.5-Pro` applies exact edits. `Qwen 2048/4096` only if the prompt is fully mechanical.

* Pure EngineEval-style metric patch:
  `MiMo-V2.5-Pro` or `Qwen 1024/2048` from exact recipe. Coordinator reviews patch from bundle. `GPT-5.5-medium` only if Scala/API complexity is expected.

* Source-confirmed seam inventory:
  Coordinator does review/design from bundle. `MiniMax-M3` may do narrow source-confirmed inventory with exact file list and complete bundle. `Qwen 4096` may do narrow local inventory, not final architecture/design.

* Complex Scala/Distage/resource code:
  `GPT-5.5-medium` for code writing. `MiniMax-M3` or `MiMo-V2.5-Pro` only if the recipe is exact and production risk is controlled. `GPT-5.5-high` only if production/lifecycle risk is high.

* Production route/lifecycle/runtime search behavior:
  `GPT-5.5-high` if implementing. If GPT is unavailable, reduce to source-confirmed inventory/open questions or wait.
