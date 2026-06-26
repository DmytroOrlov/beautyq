# Distage HTTP testing

## Purpose

Focused local harness docs for route and service proofs. This is not a full Distage reference and not production deployment docs.

## Testing levels

* pure service/backend tests
* in-process real-socket HTTP via existing test support
* real-resource ES/Qdrant/embedding focused specs
* subprocess/curl is separate and not required unless an operational demo is explicitly requested

## Composition principles

* use selected modules, not whole production graph by default
* keep default/rollback/not-ready/ready states explicit
* give each real-resource graph unique test indices/collections
* do not manually prepare an index that the selected route module already seeds
* manually prepare only seams proven missing by focused tests, e.g. ready lexical ES path from QP18b/QP19b

## Failure attribution

* service fails first -> service/harness issue
* service green but route 500 -> route-layer or Tapir/http mapping issue
* route/service differ only by backend-native floating score from independent calls -> compare ids/order/components, not raw scores
* missing resource -> resource-gated, not failed

## Commands

* QP18:
  `sbt 'bifunctor-tagless/Test/compile' 'bifunctor-tagless/testOnly leaderboard.search.QP18QdrantSupplementImprovementNoWorseningSpec'`
* QP19:
  `sbt 'bifunctor-tagless/Test/compile' 'bifunctor-tagless/testOnly leaderboard.search.QP19QdrantSupplementMeasuredAcceptanceGateSpec'`

## Do not

* do not run full sbt test for docs-only work
* do not use process/curl unless source-confirmed fixed-port convention exists
* do not fake real-resource proof with stubs

## Links

* `docs/BEAUTYQ_QDRANT_SUPPLEMENT_LOCAL_GATE.md`
