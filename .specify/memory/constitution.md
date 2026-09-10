<!--
SYNC IMPACT REPORT

Version change: 1.0.2 → 2.0.0
Bump: MAJOR. The governing SCOPE changes: v1.x governed one completed DOC-ONLY forensic
documentation-history review (feature `001-beautyq-doc-history-review`). v2.0.0 governs future
agent-assisted BeautyQ software and documentation DELIVERY work. Every v1 principle leaves the governing
set, so this is a constitutional transition, not a PATCH-level wording cleanup.

Scope transition:
  - Old (frozen, historical): the independent forensic review of BeautyQ Search / Search Gen2
    documentary history and its review harness; research baseline
    789e56674145a45c31e2e25d997c60b359431a48; DOC-ONLY evidence boundary.
  - New (governing): future agent-assisted BeautyQ software and documentation delivery work — specification,
    planning, implementation, documentation, evidence, acceptance and closeout — and the agent/human authority
    boundary inside it. Spec Kit features are explicitly covered; Spec Kit is not the only covered workflow,
    and ordinary human work is not proceduralized by these agent rules.
  - This document does not re-govern, re-open or reinterpret how the completed review was performed.

Compatibility with feature 001 (preserved, NOT amended):
  - `specs/001-beautyq-doc-history-review` was governed by research constitution v1.0.2 and continues to
    be evaluated against v1.0.2. Its evidence contract, findings, dispositions and completion status are
    NOT retroactively changed by v2.0.0.
  - Its artifacts (`spec.md`, `plan.md`, `tasks.md`, `checklists/**`, and the
    `.review-bundles/docs-history-speckit/**` report set) were NOT rewritten to cite this version.
    Pre-existing "v1.0.2" references remain as originally written; that is intentional, not an oversight.
  - Durability precondition checked before writing: v1.0.2 text was present in committed repository history
    and the working copy was byte-identical to `HEAD`, so this overwrite destroyed no sole authoritative
    copy. This is a verification record, NOT a recovery contract: v2.0.0 does not depend on, cite, or
    endorse any specific commit SHA as the permanent place where v1.0.2 must survive, and it makes no claim
    about which history objects are accepted project history — that remains a human decision. If a later
    human-owned history operation relocates or replaces the archive carrying v1.0.2, this document stays
    semantically correct, because the compatibility statement above names a governing VERSION, not an anchor.

Removed sections (v1 research machinery NOT carried forward):
  - §1 frozen baseline/corpus boundary; §2 previous-review isolation and BLIND contamination protocol;
    §3 DOC-ONLY evidence boundary plus the `AGENTS.md` dual-role rule; §4 read-only-Git research
    allow/deny list (its history-authority intent survives, narrowed to human-owned commits and excluded
    agent stashing, in Principle I);
    §5 canonical-content immutability for a reviewer; §6–§8 `COVERAGE.tsv` schema, the four
    documentary dispositions, and exact coverage arithmetic; §9–§10 blind-first derivation and the BLIND
    freeze; §11 CD/RD/ND/CM/HI/RJ provenance classes; §12 retrospective-accuracy classification; §13 the
    absence-search ledger; §14–§15 decision threads before epochs; §16 requirement-strength drift
    tracking; §17 transfer-audit chain; §18 mandatory counter-evidence search; §19 drift taxonomy;
    §20 complexity taxonomy; §21 proposal-vs-current reporting discipline; §22 mandatory positive
    findings; §23–§24 fixed review-artifact set and `WORKING` compression discipline; §25 mechanical
    arithmetic/wording gate; §26 documentary scope lint; §27 review completion conditions; the
    "Required Review Artifacts & Ledger Schema" appendix; the "Verification Gates & Prohibited
    Operations" appendix; and the research supremacy paragraph.
  - Not carried forward as schemas or vocabulary: E-/F-/D-/T-/RC-/X-/A-/P-/L-/B- IDs, provenance
    classes, candidate-corpus accounting, documentary dispositions, the BLIND/challenge protocol,
    documentary-history dispositions, and the historical absence-search field list.

Redefined / carried forward as delivery rules (research evidence, restated independently of it):
  - v1 §21 → Principle VIII (proposal authority becomes a delivery-authorization rule, not a reporting
    rule).
  - v1 §11/§13/§18 evidence discipline → Principles IV, V and XI, with no ID scheme, ledger, severity or
    provenance taxonomy attached; Principle V binds evidence to the exact EVALUATED SOURCE STATE (committed
    revision or uncommitted materialized patch), separates patch-local validation from feature-required
    committed downstream evidence, and makes attribution — not source mobility — the protected object.
  - v1 §17/§20 transfer and layer-separation audits → Principles II and X (ownership and rationale at
    retirement), without the audit-chain format.
  - v1 §16 requirement-strength drift → informs Principles III and VIII; not restated as a tracking
    obligation.
  - v1 §4/§5 Git and immutability constraints → Principle I generalises research read-only discipline into
    exclusive human commit authority, and extends that boundary to the Git index: staging, unstaging and
    stashing are human-only operations, while agents keep read access for review and evidence attribution.
  - v1 §22 positive-findings symmetry → dropped; delivery features inherit their own acceptance
    criteria instead.

Added sections:
  - Core Principles I–XII (project delivery governance).
  - "Excluded Claims and Non-Axioms" — assertions the historical review did NOT establish, research-only
    machinery, and content that must never be hardcoded in this file.
  - "Precedence and Non-Duplication" — constitution vs `AGENTS.md` vs the coordinator workflow vs current
    owner documents vs the active task, the role boundary between delegated and coordinator work, and the
    anti-duplication rule.
  - Governance — rewritten for the delivery lifecycle (amendment, versioning, compliance, Spec Kit
    constitution-check usage, and version effectivity distinct from project ratification).

Affected templates/commands: no template, script or command file was modified by this operation
(`.specify/memory/constitution.md` is the only file this workflow writes). `.specify/templates/plan-template.md`
§"Constitution Check" resolves its gates from this file at runtime, so future plans derive gates from
Principles I–XII instead of the retired research ledger gates. `constitution-template.md`,
`spec-template.md`, `tasks-template.md`, `checklist-template.md` and the `.specify/scripts/**` commands read
this document at runtime and were left untouched.

Companion owner harmonization in the same transition, at ownership level only: `AGENTS.md` carries the
Git-safety guardrail for delegated agents, and the coordinator workflow guide carries coordinator-role
procedure, both aligned with Principles I and V. Feature plans were not edited, and a feature contract remains
free to require a committed immutable identity for its own acceptance evidence.

Deliberately excluded from this file as feature- or plan-owned facts: current milestone names and status,
run or evidence identities, deferred initiative names, case IDs, commands, hashes, counts, run outcomes,
feature-specific acceptance ladders, proposal content, current delivery status, and BeautyQ architecture
detail.

Deferred TODOs: none. `Ratified` keeps the project's original constitution adoption date (2026-09-04, the
v1.0.0 ratification) and is never a version effective date. v2.0.0 becomes effective on 2026-09-08, its own
adoption date, recorded in the footer `Effective` field and governed by the Governance effectivity clause.
-->

# BeautyQ Project Delivery Constitution

*Scope: this constitution governs future agent-assisted BeautyQ software and documentation delivery work —
Spec Kit features and their specifications, plans, patches, evidence and closeout are explicitly included,
but Spec Kit is a covered workflow, not a precondition for these rules. Ordinary human work is not
proceduralized by these agent-workflow rules, and feature-specific acceptance remains owned by that
feature's own spec and plan. It is a set of cross-feature boundaries, not a delivery plan, specification,
runbook, status board, or process manual, and it is not a general constitution for unrelated projects. It
does not govern, re-open, or reinterpret the completed historical review feature
`001-beautyq-doc-history-review`, which remains evaluated under the frozen research constitution v1.0.2.*

## Core Principles

### I. Human-Owned Git History and Index (NON-NEGOTIABLE)

* The Git index and repository history are human-owned boundaries. Agents may inspect them but MUST NOT mutate
  them. The rule applies to every agent role — delegated, coordinating, primary and review-capable sessions
  alike — and to documentation-only and evidence-only work as well as code.
* Only the human creates repository history. An agent MUST NOT run `git commit`, and MUST NOT `amend`,
  squash, rebase, merge, cherry-pick, revert-by-rewrite, push, tag, create or switch branches, or otherwise
  create or alter commits or refs.
* Only the human mutates the index. Agents MUST NOT stage, unstage, or stash, and MUST NOT use index-moving
  commands such as `git restore`, `git checkout`, `git rm --cached`, or `git update-index` against it. A
  task, prompt, or feature contract that asks a patch to be left staged or set aside does not override this:
  the agent makes its authorized edits in the worktree only and states that the index step is the human's.
* Agents preserve human-created staged state exactly as found. Reading `HEAD`, the index and the worktree is
  always permitted, and Principle V requires reporting an index/worktree difference when it bears on what was
  actually evaluated. When `HEAD`, index and worktree differ, the agent reports that state truthfully and does
  not normalize it.
* `PASS`, `ACCEPT`, `approved`, `ready`, `complete`, `ship it`, a review verdict, or a feature contract grants
  an agent neither commit nor index permission. They authorize human review or acceptance consideration only.
* Closeout is role-specific. A delegated agent leaves its work uncommitted, leaves the index untouched, and
  reports its bounded result: branch, `HEAD`, porcelain status distinguishing index from worktree,
  `git diff --check`, validation scope, and any known conflict. The extended commit message for the human
  belongs to human-facing acceptance closeout — prepared once the coordinator or primary has accepted a
  non-trivial change for human commit, in the form owned by the coordinator workflow guide — and is not a
  delegated-agent obligation. An `ACCEPT` issued by a delegated agent is not a human commit boundary, and only
  the human decides whether to stage, unstage, or commit.
* If history was already mutated by an agent action, the agent MUST report it immediately with the exact refs
  involved and MUST NOT "fix" it by rewriting history.

**Rationale:** A commit turns a machine-written claim into durable project truth, and the index is how the
human presents exact patch boundaries for review. Either one mutated by an agent changes what the human is
being asked to accept, so neither is delegable, and no approval word may be read as permission to do it.

**Violation signal:** an agent-created commit, ref change, or history mutation; any agent-created index
change — a staged or unstaged file, or a stash entry — regardless of how the task was phrased; a divergent
`HEAD`/index/worktree state silently normalized by an agent; or a closeout that hands over a change without
stating its actual Git state. Human-created staged state is NOT a violation; agent mutation of it is.

### II. One Canonical Current Owner per Concept

* Each concept has exactly one canonical current owner. Other places MAY navigate to that owner but MUST
  NOT duplicate mutable policy, status, thresholds, or values that can drift.
* These statement types stay distinct, and one sentence MUST NOT silently serve as two of them: current
  architecture or semantic contract; operational procedure; current delivery and remaining-work status;
  run or evidence artifact.
* Derived views — registries, generated tables and trees, traces, fingerprints, documentation summaries,
  fixture literals — expose their owner. They MUST NOT become independent policy authorities.
* When ownership moves, the agent MUST update current navigation, preserve the decision and the rationale
  needed to interpret it, and MUST NOT leave a second standing owner of the same mutable fact.
* A pointer that names the wrong owner is a navigation defect: repair the pointer at its owner, and do not
  treat the repair as an architecture or policy change.

**Rationale:** Duplicated mutable policy diverges silently, and a reader who follows the copy acts on a
stale rule while believing they followed the authority.

**Violation signal:** two live documents state the same mutable rule and can disagree; a generated or
handwritten summary is cited as the reason for a decision; a pointer resolves to a retired owner.

### III. Completion Names Its Object and Gate

* `complete`, `done`, `closed`, `ready`, and `accepted` MUST name or unambiguously identify the object and
  the gate being closed, in the same statement or an adjacent reference that is part of it.
* Closing one layer MUST NOT imply closing another. Foundation, structure, implementation, cutover,
  prerequisites, acceptance, promotion, and verification are separate layers, and each keeps its own gate.
* Open, deferred, blocked, or canceled downstream work MUST remain explicitly stated wherever a completion
  claim appears.
* Where the feature's acceptance contract REQUIRES a proof, that proof does not pass if it was canceled, not
  run, or unavailable, and the gate requiring it stays open. Where the contract does not require a
  verification, it may be unavailable without holding the feature open, and its absence MUST NOT be
  manufactured into an open work item.
* A completion claim inherited from a summary, status line, or earlier session MUST be re-verified against
  the current owner before being repeated.

**Rationale:** A completion verb without its object widens silently across readers and later edits; naming
the gate keeps prerequisite closure from being read as feature closure.

**Violation signal:** a bare "X is complete"; a status line whose meaning grew between edits; prerequisite
closure read as feature closure; a required-but-unavailable proof treated as passing; or an optional
unavailable check converted into an open work item.

### IV. Evidence Is Not Permission

* These states are distinct: observed result; no-harm or safety result; measured product improvement;
  readiness; candidate recommendation; human approval; promotion or activation; independent verification.
* Passing one state MUST NOT authorize a stronger state, and a report MUST NOT describe a state using the
  vocabulary of a stronger one.
* The concrete ladder for a given feature belongs to that feature's own acceptance contract. This
  principle does NOT impose a candidate-review, promotion, or independent-verification ladder, or any other
  multi-stage evaluation machinery, on features whose accepted contract does not contain them.
* Where a stronger state has not been reached, the agent MUST name the achieved state and the missing one
  in the same report.

**Rationale:** Approval pressure concentrates at the moment a result is reported, so the boundary between
"this happened" and "therefore you may act" has to be a rule rather than a judgment call.

**Violation signal:** a no-harm result described as improvement; a green gate described as approval; a
candidate described as promoted; readiness asserted from an approval request.

### V. Evidence Belongs to the Exact Evaluated Source State

* Evidence-bearing evaluation is attributed to the exact source/application STATE that produced it, and MUST
  NOT float onto any other source state.
* A committed revision is one valid state identity when the evaluated state is committed. An UNCOMMITTED
  materialized patch is equally a valid evaluated state: agents normally validate before any human commit, and
  this principle MUST NOT be used to require an agent to commit just to obtain an evidence identity
  (Principle I). Where the state is uncommitted, its identity MUST still be enough, under the feature's
  contract, to distinguish that materialized state from baseline — for example the baseline `HEAD` plus the
  exact working-tree/index state, a patch or diff identity, an application/source identity the feature already
  defines, or another explicit reproducible state identity. This principle mandates no universal manifest,
  hashing scheme, or evidence ledger.
* When staging is material to the evaluated state, a report MUST distinguish staged/index state from
  unstaged/worktree state and MUST NOT describe either loosely as "HEAD".
* Where a feature's acceptance contract already requires an exact immutable application revision, that stricter
  contract continues to apply unchanged; this principle does not relax it.
* Additional identities — input, corpus, policy, dataset, configuration, fixture, or environment — are bound
  ONLY where they are material under the feature's own acceptance or evidence contract. An ordinary
  documentation edit or simple bug fix MUST NOT be forced to invent corpus/policy machinery to satisfy this
  principle (see Principle IX).
* Agents MUST NOT rewrite the identity or attribution of an already evaluated state, and MUST NOT reattribute
  its evidence to a later state. Further authorized edits create a NEW source state: evidence for an earlier
  state stays attached to that state only, and the new state receives whatever fresh validation its governing
  feature contract requires. This rule protects evidence attribution, not source immobility — it does not
  freeze the worktree after the first run. An evidence-bearing COMMITTED revision MUST still never be amended
  or rewritten in place.
* Patch-local validation and downstream acceptance evidence are different roles. Focused compile or tests,
  contract, graph/boundary, and documentation checks used to review an uncommitted patch attach to the exact
  evaluated uncommitted state and MUST NOT require an agent commit. Where a feature contract requires a
  committed immutable application/source identity before some downstream acceptance evidence is produced, that
  contract governs: the accepted patch is handed to the human at acceptance closeout (Principle I), the
  human creates the boundary, the agent never commits it, and the evidence then binds to that committed
  identity. Neither role authorizes the other, and the existence of a stricter committed-evidence contract
  does not turn patch-local validation into a commit requirement.
* Evidence identity survives handoff: a checkpoint, summary, or report that carries evidence MUST state the
  evaluated source state it describes, or state that the state is unknown.

**Rationale:** Attribution is what makes a past result usable; once evidence can float onto other source,
no later reader can tell what was actually tested. Because only the human commits, the identity that matters
is the evaluated state, not whether a commit object happens to exist yet — and where a feature deliberately
requires a committed identity for its strongest evidence, that boundary belongs to the human.

**Violation signal:** evidence quoted against a `HEAD` that does not match the state actually evaluated;
uncommitted work labeled as tested at some commit; index and worktree differences hidden behind one word;
counts or verdicts reused after a source edit; an amended evidence-bearing committed revision described as
already reviewed; a first green run treated as freezing further edits; or downstream evidence captured against
a committed boundary the human never created.

### VI. No Fake Green

* A gate MUST NOT be made green by: relabeling outcomes after seeing them; silently changing acceptance
  criteria; removing failures or cases from the evaluated corpus; reclassifying protected or visible cases
  merely because they failed; attributing evidence to a different source state; or replacing the required
  proof with a weaker one while keeping the original claim.
* A deliberate corpus, policy, or criteria change MUST follow the transition defined by its owner and
  preserve honest failure history where that owner requires it.
* A real red result stays reported as red even when the surrounding patch is accepted.
* A missing or unavailable verification resource or environment is NOT a passing result; it normally means
  the verification is blocked or was not run. That alone does not establish product failure. Where
  availability of that resource or environment is itself part of the accepted product or feature contract,
  the affected requirement may be reported as failed, and the report MUST distinguish product failure from
  inability to perform the required proof.

**Rationale:** Falsified green is the one failure mode that destroys every other control, because the
remaining gates are all justified by earlier green claims.

**Violation signal:** criteria edited in the same change as the outcome; a smaller corpus carrying the older
broader claim; "canceled", "not run", or "saved data" written as live coverage.

### VII. Domain Policy versus Reusable Mechanics

* Reusable and generic layers own domain-neutral mechanics. The domain owns its joins, normalization,
  projection, semantic choices, policy, thresholds, and evaluation criteria, unless an approved contract
  moves them.
* A neutral fixture, a tracer, a test shape, or another modeled domain is not evidence of production
  adoption or of universal business semantics. State the actual evidence width instead.
* A genuinely domain-neutral pure abstraction MUST NOT be blocked solely for lacking a second production
  adopter, unless the relevant feature contract requires that adopter.
* If preserving behavior requires domain names, defaults, or policy inside a reusable layer, that is a
  boundary conflict: report it rather than hiding it behind a general-looking API.
* Search-domain authoring detail stays owned by the current authoring-principles owner; this principle is
  the project-wide boundary statement, not a second copy of that contract.

**Rationale:** Both failure directions are expensive: generic code that secretly knows one product, and
abstractions withheld until a second customer appears for something already neutral.

**Violation signal:** a reusable component branching on a domain identity; fixture evidence reported as
multi-domain support; a business choice relocated into a kernel without a contract change.

### VIII. Proposals Are Not Accepted Architecture

* A proposal, experiment, simplification idea, or deferred item is non-normative until the responsible
  human acceptance boundary approves it. Its own status label governs, and the agent MUST read that label
  rather than infer acceptance from tone, placement, or recency.
* Agents MUST NOT implement a proposal merely because it appears in documentation, and MUST NOT rewrite
  current architecture, status, or evidence statements to make a proposal look already accepted.
* Accepted limits and explicit non-goals are not backlog. They MUST NOT be implemented because a later
  document mentions them.
* A proposal's diagnosis stays attributed to the proposal. It MUST NOT be restated as project fact, and
  current architecture MUST NOT be criticized or defended in this file.

**Rationale:** Documentation outlives decisions, so without a status rule the newest idea silently becomes
the requirement and the accepted boundary becomes optional.

**Violation signal:** source changed to match an unapproved idea; a proposal cited as authority to alter a
live contract; an accepted limit treated as a to-do.

### IX. Minimal Governance with Explicit Rationale

* A rule that constrains architecture or delivery SHOULD state the risk or failure mode it protects
  against and its scope. Where a rule is enforced mechanically — a gate, checklist item, or acceptance
  refusal — it MUST name the concrete risk it closes.
* Before introducing a new ledger, mandatory review, artifact class, coordination stage, or proof layer,
  the agent MUST show that existing machinery cannot cover the identified risk more simply. Otherwise reuse
  an existing owner and raise the addition as a separate decision.
* Governance volume and proof volume are not evidence of correctness, and removing ceremony is not progress
  unless the risk it covered is closed another way. Compliance reporting MUST state the concrete facts the
  relevant principles require (Git state, evaluated source state, validation scope, conflicts); reciting every
  principle as a boilerplate line adds no evidence and is not compliance.
* This principle does not license deleting an existing gate. Changing or retiring one is a contract change
  subject to Principles I and VIII.

**Rationale:** Process grows fastest where it is unfalsifiable, and an ungated rule is indistinguishable
from a habit.

**Violation signal:** a new artifact that duplicates a decision already visible in source; a "required
review" with no named risk; ceremony or document count reported as quality.

### X. Rationale Survives Ownership Retirement

* When deleting or superseding a material current owner, the agent MUST identify the surviving canonical
  owner and preserve the material decision plus the rationale needed to interpret that decision as it now
  stands.
* Current owners MAY retire or delete obsolete live documentation, including historical alternatives, once the
  material rationale needed to interpret the surviving decision has been preserved in the surviving owner or
  another durable destination. This principle preserves rationale; it does NOT mandate permanent live-file
  retention.
* An alternative that still helps a current reader interpret today's decision MAY be retained and MUST then be
  labelled historical rather than left looking like live guidance. Chronology that current interpretation does
  not require MAY live only in Git history.
* A deletion MUST NOT leave a current explanatory pointer dangling: explanatory pointers are checked as well
  as rule pointers, and a surviving document that points into a deleted one is a defect at the pointer's
  owner.

**Rationale:** Retirements are where substance disappears invisibly: the rule often survives while the
reason that would let a future reader question it does not.

**Violation signal:** a rule with no recoverable reason; a live document citing a retired one; deletion
justified only by "it is in Git"; or an obsolete live document treated as undeletable.

### XI. Truthful Validation Scope

* Every validation claim MUST state what actually ran, against which evaluated source state (Principle V), at
  what scope, and what remains unknown.
* A focused run MUST NOT be presented as full-suite proof. Pure or unit proof MUST NOT be presented as
  resource-backed, integration, runtime, CI, or production behavior.
* A named command, script, workflow, or artifact is not evidence that it ran successfully.
* A claim produced by one execution MUST NOT be extended to layers, configurations, or resources that
  execution did not cover.
* Execution mechanics — how sbt is invoked, which scopes delegated agents may run, what counts as a
  broader command — stay owned by `AGENTS.md`. This principle governs only the truthfulness of the claim
  that results.

**Rationale:** Reports are consumed by agents that cannot re-run the work, so an over-scoped validation
sentence becomes a false premise for every later decision.

**Violation signal:** "tests pass" with no scope; a listed spec treated as a green spec; a generic
verification trailer that does not describe the run behind it.

### XII. Smallest Coherent Change

* Implement the smallest coherent change that satisfies the accepted feature requirements.
* Do not opportunistically generalize architecture, introduce future-domain abstractions, activate deferred
  work, perform cleanup unrelated to the feature, or convert accepted limits into backlog, unless that work
  is separately authorized in the same accepted scope.
* When a required fix appears to need wider change, stop at the smallest safe step and report the boundary
  with the diagnostic or source evidence that forces the expansion.
* Patch hygiene stays owned by `AGENTS.md` (atomic patches, no mixed risk layers, bounded edits). This
  principle fixes the delivery *scope* of a feature, not the mechanics of an edit.

**Rationale:** Unplanned expansion is how a reviewed decision stops being reviewable: the diff that gets
judged is no longer the diff that was requested.

**Violation signal:** unrelated refactors in a feature diff; a helper built for a hypothetical second
domain; edits justified by "while I was there".

## Excluded Claims and Non-Axioms

This constitution does NOT assert, and no feature, plan, or review may derive from it, that:

* BeautyQ architecture is objectively overengineered;
* proof, evaluation, or governance effort generally exceeded its justified value;
* process generally caused the architecture or displaced technical reasoning;
* the current source-of-truth arrangement is systemically broken;
* product value is absent;
* rationale preservation is generally broken.

Those were research hypotheses, and the completed review did not establish them as project facts.
Principles II, IV, V, VI, VIII, X and XI are carried forward because each is independently a delivery
boundary, not because a historical finding endorsed them.

Research-only machinery is NOT part of delivery governance and MUST NOT be reintroduced by analogy:
evidence/finding/decision/thread/transfer/absence ID schemes, provenance classes, candidate-corpus
accounting, documentary dispositions, the blind/challenge protocol, historical absence-search ledgers, and
per-run review-artifact schemas. Reusing one of them requires a concrete delivery risk that Principles I–XII
and existing owners cannot cover (Principle IX).

This file MUST NOT hardcode: current delivery or milestone status, case IDs, commands, hashes, counts,
run outcomes, delivery-cycle or prerequisite state, per-feature acceptance ladders, BeautyQ architecture
detail, or any model/vendor names. Such content belongs to the current owners named in the repository entry map,
and an amendment that adds it to this file is out of scope for the constitution workflow.

## Precedence and Non-Duplication

**Order of authority.** For delivery boundaries, this constitution supersedes Spec Kit defaults, templates,
prompts, planning documents, and workflow guides. `AGENTS.md` and the current operations/procedure owners
may impose *stricter* operational rules; for agent operation the more restrictive rule applies. No document,
task prompt, or feature contract may relax Principle I: nothing in it can grant an agent permission to commit,
to alter refs, to stage or unstage, or to stash. Where a conflict cannot be resolved without violating a
principle above, the agent MUST stop and report the conflict instead of choosing silently.

**Ownership by statement type and role.** This constitution names roles, not files, because file ownership
moves:

| Statement type | Canonical current owner role | Read or applied by |
| --- | --- | --- |
| Cross-feature delivery boundaries | this constitution | every agent role |
| Agent operational safety, build/test execution, code conventions, Git safety | the repository agent-guidance file (`AGENTS.md`) | every agent working in the repository |
| Delegated task contract: bounded change, edit frontier, goal, focused validation, report shape | the active task prompt and the feature's own spec/plan/tasks | the delegated agent for that task |
| Coordinator acceptance, delegation, prompt construction, bundle construction, model selection, human-facing closeout including extended commit messages, downstream planning | the coordinator workflow guide | an agent acting in the coordinator or primary review role |
| Current architecture and semantic contract | the current search technical specification | whoever is changing or reviewing that architecture |
| Operator procedures and runbooks | the current operations document | operator and change-authoring work |
| Current remaining delivery work and status | the current delivery/remaining-work plan | status and acceptance work |
| Domain-versus-framework authoring contract and proof selection | the current authoring/onboarding owners | domain and framework change work |
| Where to read any of the above today | the repository entry map (`README.md`) | anyone navigating the docs |

The entry map resolves the current file for each role. This table MUST NOT be used to re-declare that map,
and a role whose owner has moved is corrected in the entry map, not duplicated here (Principle II).

**Role separation.** Owning a document does not assign its duties to every reader. A delegated agent acquires
no coordinator responsibility because the coordinator workflow exists: it does not make acceptance decisions,
construct bundles or prompts, choose model tiers, plan downstream work, or prepare the human's commit message
unless the active task explicitly places it in that human-facing closeout role. Coordinator-specific
reporting rules are not copied into every delegated task, and coordinator-specific duties are not silently
pushed onto delegated agents. Principle I applies unchanged to both roles.

**Compliance gate placement.** Feature-level compliance is checked in that feature's plan Constitution Check
against Principles I–XII. Focused compliance stays focused: the gate does not create a new review stage
(Principle IX), and passing it never authorizes a commit (Principle I).

## Governance

**Amendment procedure.** An amendment is made through the Spec Kit constitution workflow and MUST: state the
reason and the affected principles; add a dated entry to the Sync Impact Report comment at the top of this
file; classify new/modified/removed principles; bump the version; and record the date on which the new
version becomes effective. An amendment MUST NOT be used to record feature progress, close a gate, or change
any feature's status, and MUST NOT retroactively alter a completed feature's evidence contract or findings.

**Versioning policy.** Semantic versioning `MAJOR.MINOR.PATCH`:

* **MAJOR** — removal or backward-incompatible redefinition of a principle, or a change of governing scope
  that leaves previously governed work under its prior version.
* **MINOR** — a new principle or section, or a material expansion of existing guidance.
* **PATCH** — clarification or wording that changes no controlled behavior.

**Effectivity (three distinct dates).** The footer separates three fields that MUST NOT be conflated:
`Ratified` is the project's original constitution adoption date and never moves with a version;
`Effective` is the date THIS version's rules begin to bind; `Last Amended` is the date of the most recent
content change recorded in the Sync Impact Report. A version's principles bind agent-assisted delivery work
performed from that version's `Effective` date onward, and not from `Ratified`. Work performed before the
current `Effective` date — including feature `001-beautyq-doc-history-review` and any patch, review, or
evidence produced under an earlier version — remains assessed under the version in force at the time, and its
artifacts are not rewritten to cite a newer version. Where a newer principle contradicts an older artifact,
the artifact stays as written and current owners carry the newer rule. A version change MUST NOT re-govern
completed work unless an explicit human decision records that it does.

**Compliance review.** All work obeys Principles I–XII; that is an obligation, not a recitation duty. Feature
planning uses the existing plan Constitution Check where applicable, and no separate compliance artifact or
review stage is created by this document. An ordinary closeout MUST NOT be forced to print a twelve-principle
checklist: it reports the material constitutional facts for the work actually done — the exact Git state
wherever a patch or repository state exists (Principle I), the evaluated source state and real scope of any
validation or evidence claim (Principles V and XI), and any known constitutional conflict, exception, or
violation. A violation is always reported, and is never repaired by history rewriting, unauthorized index
changes, or a narrowed claim. Closeout content follows the Principle I role split: a delegated agent reports
its bounded result and the actual Git state, while the human-facing extended commit message belongs to
acceptance closeout in the coordinator or primary role. Full repository conformance reviews and any
production-graph or resource-backed verification remain outside this document and are run only when
separately authorized.

**Superseded version.** Research constitution v1.0.2 and its earlier history remain recoverable from Git and
continue to govern feature `001-beautyq-doc-history-review` as a frozen record. Nothing in v2.0.0 authorizes
rewriting that feature's review artifacts.

**Version**: 2.0.0 | **Ratified (project)**: 2026-09-04 | **Effective (this version)**: 2026-09-08 | **Last Amended**: 2026-09-08
