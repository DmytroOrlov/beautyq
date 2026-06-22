# M15A BeautyQ Serving-Readiness Manual Smoke Checklist

This is an operator/developer manual smoke checklist for the accepted M14 route-gate and
local/dev-only fallback state. It is operational/manual smoke planning only — it is **not**
production activation and **not** serving approval. No `sbt run` is required for this
docs-only milestone.

## 0. Source truth (compact)

* M14A (route-gate / serving-readiness design contract), M14B (disabled-by-default runtime
  route gate), M14C (local/dev-only fallback policy contract), and M14D (docs closeout) are
  accepted.
* `BeautySearchServingGate` states: `disabled`, `enabledNotReady`, `enabledReady`;
  `rejectsServing = enabled && !servingReady`.
* Default DI binding uses `BeautySearchServingGate.disabled`.
* Default `POST /beauty-search` remains ES-backed.
* Gate `disabled` preserves existing `/beauty-search` behavior.
* Gate `enabledNotReady` + valid request -> HTTP `503`.
* Gate `enabledReady` + valid request -> existing ES-backed route behavior.
* Invalid requests still return HTTP `400` before the gate is evaluated.
* `BeautySearchLocalDevOnlyFallbackPolicy`: local/dev-only fallback is a future local/dev
  policy only. Production fallback is not enabled. Runtime serving fallback is not enabled
  by default.
* Qdrant remains disabled-by-default and is **not** a fallback target. Explicit Qdrant
  opt-in remains disabled by default. Qdrant production activation is not approved.
* No default route switch is approved.
* No hybrid serving, score fusion, reranking execution, production telemetry,
  quality-green claim, retrieval-quality claim, production-readiness claim,
  route-activation claim, or serving-approval claim is approved.
* `ElasticsearchOperatorVisibilityEndpointPolicySpec` has zero pending tests after M14C.
* Any recursive `graal-resources/target/.../classes/target/...` cleanup observed earlier is
  build-artifact cleanup only, not a source/config change.

## 1. Scope and non-goals

Scope:

* Manually exercise the accepted M14 gate states and local/dev-only fallback boundary
  against `POST /beauty-search`.
* Confirm operator visibility/status remains additive and unchanged.
* Record evidence in a reproducible, reviewable format.

Non-goals:

* No production activation.
* No default route switch.
* No Qdrant activation.
* No production fallback.
* No runtime serving fallback by default.
* No hybrid serving, score fusion, or reranking execution.
* No production telemetry.
* No production-readiness, route-activation, or serving-approval claim.

## 2. Manual preflight

| # | Preflight check |
|---|---|
| 1 | Confirm current branch / commit under test. |
| 2 | Confirm full suite status is known before running manual smoke (record pass/fail counts and source, do not re-run unless asked). |
| 3 | Confirm no production activation or route switch is being attempted. |
| 4 | Confirm default `/beauty-search` is ES-backed. |
| 5 | Confirm Qdrant explicit opt-in remains disabled-by-default. |
| 6 | Confirm Qdrant production activation remains not approved. |

## 3. Smoke cases

| # | Gate state | Request | Expected result |
|---|---|---|---|
| 1 | `disabled` | valid `/beauty-search` | existing ES-backed success behavior |
| 2 | `enabledNotReady` | valid `/beauty-search` | HTTP `503` |
| 3 | `enabledReady` | valid `/beauty-search` | existing ES-backed success behavior |
| 4 | `enabledNotReady` | invalid `/beauty-search` | HTTP `400` before the gate is evaluated |
| 5 | any (default) | operator visibility/status endpoint | additive and unchanged; no behavior change |
| 6 | any (default) | operator visibility/status endpoint | no request-time ES call added unless already part of the tested route path |

## 4. Boundary checks

| # | Boundary |
|---|---|
| 1 | No default route switch. |
| 2 | No Qdrant activation. |
| 3 | No production fallback. |
| 4 | No runtime serving fallback by default. |
| 5 | No hybrid/fusion/reranking/telemetry behavior. |
| 6 | No production-readiness, route-activation, or serving-approval claim. |

## 5. Evidence fields to capture manually

For each smoke case run, record:

* command or test entrypoint used;
* environment / profile;
* gate state;
* request shape;
* response status;
* response body shape;
* operator visibility response if checked;
* observed backend target;
* deviations;
* cleanup performed.

Suggested record format:

```text
Command/entrypoint:
<exact command or test entrypoint>

Environment/profile:
<env or profile>

Gate state:
<disabled | enabledNotReady | enabledReady>

Request shape:
<summary of request>

Response status:
<HTTP status>

Response body shape:
<summary of body shape>

Operator visibility response (if checked):
<summary or "not checked">

Observed backend target:
<ES-backed | other>

Deviations:
<none, or describe>

Cleanup performed:
<none, or describe>
```

## 6. Stop conditions

Stop immediately and record the deviation if any of the following is true:

* Default `/beauty-search` behavior changes from existing ES-backed behavior.
* The gate does not return HTTP `503` for `enabledNotReady` + valid request.
* Invalid requests stop returning HTTP `400` before the gate.
* Operator visibility/status adds a request-time ES call outside the tested route path.
* Anything would require production activation or route-switch approval that has not been
  granted.

## 7. Forbidden without separate approval

This checklist is not approval for:

* production route activation;
* a default route switch;
* Qdrant production activation;
* production fallback;
* runtime serving fallback by default;
* hybrid serving, score fusion, or reranking;
* any production-readiness, route-activation, or serving-approval claim.
