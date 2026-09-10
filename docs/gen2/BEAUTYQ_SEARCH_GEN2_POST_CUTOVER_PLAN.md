# BeautyQ Search Gen2 — post-cutover quality and operations plan

Status: **transitional source material. Q2 closeout is active as Spec Kit feature
`specs/002-beautyq-q2-closeout`, which owns the current Q2 closeout requirements, gates, and state.
D1 remains deferred and this document does not authorize it as current execution scope.**

This document is retained as transitional/historical source material for post-cutover quality, bounded
operational hardening, and eval-first second-domain delivery until the active features have consumed its
useful information; it is not the current Q2 execution or status owner. The
[technical specification](BEAUTYQ_SEARCH_GEN2_TECHNICAL_SPEC.md) remains the owner of current
implemented architecture; the framework leverage audit is feature 003 and the post-closeout simplification
decision is feature 004, each owning its own feature decisions when executed. Source and focused tests
remain the executable authority as items are implemented.

## Q2 state (recorded before feature 002 was opened)

This plan previously recorded: recovery source, replenishment, and freeze prerequisites complete; Q2-B
(protected acceptance and candidate review) and Q2-C (explicit promotion, committed-resource verify, and
closeout) open. The current requirements, gates, and state of those boundaries belong to
`specs/002-beautyq-q2-closeout`; the milestone material below is source/history context for that feature,
not an alternative execution contract.

Exact revisions, hashes, run outcomes, corpus counts, failed-check codes, and recovery chronology
belong to canonical authorization/evidence artifacts and Git history. Deferred capabilities remain
outside approved current scope; see
[Technical Specification accepted limits](BEAUTYQ_SEARCH_GEN2_TECHNICAL_SPEC.md#accepted-limits).

## Approved milestones

### Q2-B — Protected bootstrap and candidate review

- Begins only after the tracked protected inputs pass canonical catalog validation and a reproducible
  freeze/audit, and the coordinator accepts root evidence for the exact verified tracked canonical inputs
  being evaluated.
- Fresh protected acceptance runs against those inputs; bootstrap only after a green protected gate.
- Protected acceptance evidence remains attributed to the actual evaluated inputs (corpus/policy
  fingerprints, digests). If those inputs change before promotion, Q2-B restarts with fresh protected
  acceptance against the changed inputs; earlier evidence remains attributed to the inputs it evaluated.
- A red or blocked protected acceptance stops Q2-B. No bootstrap, candidate review, promotion, or
  verify follows. Any authorized recovery that changes source invalidates pending acceptance evidence
  and restarts Q2-B with fresh protected acceptance.
- Bootstrap produces an aggregate-only candidate. Preserve the candidate and stop for separate
  coordinator/operator review; do not promote it in the same delegated task, do not check in the full
  report, and do not hand-copy its counts.
- Review covers schema and policy versions, corpus and policy fingerprints,
  protected gate pass/fail codes, provenance IDs, ordered aggregate observation keys and counts,
  candidate digest, and absence of protected identity fields. Queries, case/result identities,
  judgments, and metric values remain private.
- Candidate generation does not authorize promotion.

### Q2-C — Explicit promotion, verify, and Q2 closeout

- Begins only after explicit coordinator/operator approval of the Q2-B candidate.
- Promotion copies the preserved
  candidate byte-for-byte into the one canonical eval resource, so the worktree then necessarily
  contains that reviewed tracked resource. Promotion is committed as its own tracked Git boundary,
  separately from bootstrap/review and from any unrelated source change. Pre-promotion evidence
  remains attributed to the inputs it evaluated; it is never re-attributed to the promotion
  commit.
- No search, evaluation-policy, lifecycle, route or corpus source may change between review and verify.
- Q2-C verifies digest/equality, runs the focused canonical-resource proofs, and performs an independent
  real verify against the committed baseline's evaluation content and verified tracked canonical inputs.
- Q2 documentation may close only after green verify. No automated promotion service is introduced.

### D1 — eval-first second-domain vertical (deferred; not authorized current execution scope)

- Begins only after the product identity and source topology of the second domain are supplied.
- Starts with the second domain's corpus and simplest baseline, not with a copied BeautyQ module tree.
- Every subsequent domain slice carries its own quality evidence.
- Each domain declares and proves its own measured supplement gate; BeautyQ thresholds and query text
  are not reused.
- Request budgets remain domain-owned and are declared/enforced at the domain HTTP/input and
  domain-validation boundaries; no generic budget engine is introduced.
- Bounded ordered generation work and snapshot/generation freshness reuse the existing
  framework-owned kernel where that ownership already exists.
- The second domain does not invent a separate retention or score-threshold shape.

## Git and evidence boundaries

**Q1, O0 and O1 are complete.** Git history owns exact revisions, hashes, run outcomes and recovery
chronology for those completed patches.

Approved boundaries as recorded when this plan carried delivery ownership:

- Q2 promotion commit: the explicit commit that copies the reviewed candidate into the canonical
  tracked eval resource. Promotion is the only Q2 source boundary; the committed
  canonical resource is then the input to canonical-resource verify. Current promotion state is tracked
  by `specs/002-beautyq-q2-closeout`.
- D1 product/evaluation contract;
- D1 backend-rich source;
- D1 acceptance closeout;
- conditional neutral generic-kernel extension, only after a source-confirmed reusable gap.

Protected acceptance, candidate bootstrap, review and canonical-resource verify are evidence/operator
steps on the actual evaluated inputs; they produce no Git-tracked source change. Evidence produced for
a given set of inputs must not be attributed to a later amended or otherwise changed evaluation.

This accounting does not expand the approved scope. Deferred capabilities remain outside approved
current scope; see [Technical Specification accepted limits](BEAUTYQ_SEARCH_GEN2_TECHNICAL_SPEC.md#accepted-limits).
