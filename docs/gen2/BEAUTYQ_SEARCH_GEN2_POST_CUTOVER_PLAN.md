# BeautyQ Search Gen2 — post-cutover quality and operations plan

Status: **Q2 is active. D1 is deferred pending its required product identity and source topology.**

Owner: post-cutover quality, bounded operational hardening, and eval-first second-domain delivery.
The [technical specification](BEAUTYQ_SEARCH_GEN2_TECHNICAL_SPEC.md) remains the owner of current
implemented architecture. This plan owns only the remaining approved work below; source and focused tests
become the authority as each item is implemented.

## Current Q2 state

Recovery source, replenishment, and freeze prerequisites are complete. Q2-B and Q2-C remain open.
Q2-B owns protected acceptance and candidate review; Q2-C owns explicit promotion,
committed-resource verify, and closeout.

Exact revisions, hashes, run outcomes, corpus counts, failed-check codes, and recovery chronology
belong to canonical authorization/evidence artifacts and Git history. Deferred capabilities remain
outside approved current scope; see
[Technical Specification accepted limits](BEAUTYQ_SEARCH_GEN2_TECHNICAL_SPEC.md#accepted-limits).

## Approved milestones

### Q2-B — Protected bootstrap and candidate review

- Begins only after the tracked protected inputs pass canonical catalog validation and a reproducible
  freeze/audit, and the coordinator accepts root evidence for the exact application-source identity being
  evaluated.
- Fresh protected acceptance runs against that identity; bootstrap only after a green protected gate.
- Protected acceptance evidence remains bound to the recorded pre-promotion application-source
  revision. If that revision changes before promotion, Q2-B restarts with fresh protected acceptance
  under the new revision; earlier evidence remains attributed to its original revision.
- A red or blocked protected acceptance stops Q2-B. No bootstrap, candidate review, promotion, or
  verify follows. Any authorized recovery that changes source produces a new pre-promotion revision
  and restarts Q2-B with fresh protected acceptance.
- Bootstrap produces an aggregate-only candidate. Preserve the candidate and stop for separate
  coordinator/operator review; do not promote it in the same delegated task, do not check in the full
  report, and do not hand-copy its counts.
- Review covers application revision, schema and policy versions, corpus and policy fingerprints,
  protected gate pass/fail codes, provenance IDs, ordered aggregate observation keys and counts,
  candidate digest, and absence of protected identity fields. Queries, case/result identities,
  judgments, and metric values remain private.
- Candidate generation does not authorize promotion.

### Q2-C — Explicit promotion, verify, and Q2 closeout

- Begins only after explicit coordinator/operator approval of the Q2-B candidate.
- Phase 1 records the explicit immutable application-source identity. Promotion copies the preserved
  candidate byte-for-byte into the one canonical eval resource, so the worktree then necessarily
  contains that reviewed tracked resource. Promotion is committed as its own tracked Git boundary,
  separately from bootstrap/review and from any unrelated source change. Pre-promotion evidence
  remains attributed to its original source revision; it is never re-attributed to the promotion
  commit.
- No search, evaluation-policy, lifecycle, route or corpus source may change between review and verify.
- Q2-C verifies digest/equality, runs the focused canonical-resource proofs, and performs an independent
  real verify with the same application-revision identity and verified tracked canonical inputs.
- Q2 documentation may close only after green verify. No automated promotion service is introduced.

### D1 — eval-first second-domain vertical

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

Remaining approved boundaries:

- Q2 promotion commit: the explicit commit that copies the reviewed candidate into the canonical
  tracked eval resource. Promotion is the only remaining Q2 source boundary; the committed
  canonical resource is then the input to canonical-resource verify.
- D1 product/evaluation contract;
- D1 backend-rich source;
- D1 acceptance closeout;
- conditional neutral generic-kernel extension, only after a source-confirmed reusable gap.

Protected acceptance, candidate bootstrap, review and canonical-resource verify are evidence/operator
steps on a known source identity; they produce no Git-tracked source change. Evidence produced for a
given source identity must not be attributed to an amended or otherwise changed source revision.

This accounting does not expand the approved scope. Deferred capabilities remain outside approved
current scope; see [Technical Specification accepted limits](BEAUTYQ_SEARCH_GEN2_TECHNICAL_SPEC.md#accepted-limits).
