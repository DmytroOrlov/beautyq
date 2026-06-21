# M9 BeautyQ Real-Call Checkpoint

This checkpoint is a resource-gated planning contract. It decides whether the current eval state is eligible to attempt a future offline, resource-gated real ES/Qdrant call. It is not a real backend call, not production activation, and not a route change.

## Decision

- decision: not_eligible_no_explicit_resource_config
- eligible_for_resource_gated_smoke: false
- static_scorecard_ready: true

## Inputs

- es_resource_config_present: false
- qdrant_resource_config_present: false
- operator_approval_granted: false
- production_activation_approved: false

## Reasons

- no_explicit_resource_config
- production_activation_not_approved
- no_production_route_change
- real_backend_call_not_implemented

## Boundary

- Eligible decisions are offline/resource-gated only; they are not production activation.
- Production activation remains not approved and is never output as ready.
- No real ES or Qdrant backend call is implemented or required by this checkpoint.
- No route, plugin, DI, or HTTP source is involved.
- Default /beauty-search remains ES-backed; the Qdrant opt-in route stays disabled by default.
