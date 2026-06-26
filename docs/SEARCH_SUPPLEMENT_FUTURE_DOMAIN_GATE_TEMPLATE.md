# Search supplement future-domain gate template

## Purpose

- Prevent repeating a long implementation chain before proving local value.
- Force a measured local gate before production/default-route work.

## Before coding production wiring

- Define baseline (current primary backend) behavior.
- Define the supplement candidate source.
- Define a source-confirmed local query set.
- Include at least one expected positive-improvement query.
- Include at least one baseline-preservation query.

## Required measured metrics

- `testedQueries`
- `improvedQueries`
- `unchangedQueries`
- `worsenedQueries`
- `totalSupplementOnlyAppends`
- `duplicateBaselineIds`
- `lostBaselineIds`
- `prefixOrderRegressions`
- `baselineOwnedComponentChanges`
- `appendBudgetViolations`

## Required pass conditions

- `testedQueries >= 2`
- `improvedQueries >= 1`
- `worsenedQueries == 0`
- `lostBaselineIds == 0`
- `duplicateBaselineIds == 0`
- `prefixOrderRegressions == 0`
- `baselineOwnedComponentChanges == 0`
- `appendBudgetViolations == 0`
- Each query appends at most one supplement-only candidate unless a user-approved explicit budget says otherwise.

## User-approved harm budget

- Default is zero worsening.
- "Almost no worsening" must be expressed as explicit metrics before implementation, not vague prose.
- No vague prose budgets.

## Forbidden before gate green

- No production/default route switch.
- No supplement-as-default.
- No fallback.
- No score fusion/reranking.
- No traffic shadowing/mirroring.
- No startup indexing.
- No production collection lifecycle.
- No route JSON/API change.
- No benchmark output as automatic rollout signal.

## Required final report format for agents

- First line must be `STATUS: ...`.
- Final line must repeat `STATUS: ...`.
- If validation fails, the status must be `BLOCKED`/`PARTIAL`/`RESOURCE_GATED`/`SOURCE_INCOMPLETE`, not `CLEARED`.
- The report must not bury failure behind "no production source changed."

## BeautyQ reference (QP19, local example)

- 2 source-confirmed queries.
- 1 improved.
- 1 unchanged.
- 0 worsened.
- 1 supplement-only append.
- 0 baseline regressions.

See `docs/BEAUTYQ_QDRANT_SUPPLEMENT_OPERATOR_CHECKLIST.md` for the full BeautyQ/Qdrant operator checklist this template was extracted from.
