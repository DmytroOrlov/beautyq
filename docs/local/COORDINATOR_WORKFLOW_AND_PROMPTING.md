# COORDINATOR_WORKFLOW_AND_PROMPTING.md

Purpose: canonical coordinator guide for source-truth gating, patch acceptance, delegated prompts, review closeout, verification labels, bundle scripts, docs ownership, senior audits, and model recommendations.

`AGENTS.md` is repo/delegated-agent guardrails. This file controls coordinator workflow and closeout. If the two conflict on repo safety, tests, or bounded edit behavior, stop and request a docs clarification patch.

---

# 1. Universal gates

## 1.1 Source-truth gate

Before any patch design, exact read/edit file list, test recipe, or delegated-agent prompt, source-confirm the relevant files, types, functions, fields, constructors, helpers, fixtures, imports, contracts, and validation commands.

Docs, handoff, memory, and previous conclusions do not replace current source anchors.

If required source truth is missing, stop with one of:

- `BLOCKED_NEED_BUNDLE`
- `BLOCKED_NEED_BUNDLE_SCOPE`
- `BLOCKED_NEED_CLOSEOUT_SCOPE`

Do not provide a patch proposal, exact edit recipe, delegated prompt, or test recipe when required anchors are missing.

Do not salvage a source-incomplete task by inventing a source-independent helper, adapter, model API, field mapping, merge rule, or test plan.

Do not rescope a source-incomplete task into an adjacent "safe" patch unless the user explicitly approves that different task after seeing the missing-source report.

Do not use a stronger model to infer missing source truth.

## 1.2 Acceptance gate

A patch is accepted only when its original requested DoD is complete and it is commit-ready as-is.

Required completion work must never become a downstream option. If the patch still needs tests, docs, validation, branch coverage, source-truth checks, edge-case handling, or runbook coverage to satisfy the original task, the decision is not `ACCEPT`.

Use:

- `ACCEPT` — commit-ready as-is.
- `REJECT` — wrong direction, unsafe, or not worth continuing.
- `CONTINUE_SAME_PATCH` — same uncommitted diff must be completed before review can close.
- `CORE_DIRECTION_OK_BUT_NOT_ACCEPTED` — direction is right, required DoD remains.
- `BLOCKED_*` — source truth, evidence, or closeout scope is missing.

Do not write an extended commit message for a patch that is not accepted.

Do not mark a patch commit-ready if known required work remains.

Downstream options are only for new work after the current patch is complete.

A happy-path implementation is not accepted when the original task required branch coverage, failure behavior, cancel behavior, compatibility behavior, or matrix behavior.

## 1.3 Business authoring gate

The repository-wide contract is
[docs/search/DOMAIN_AUTHORING_PRINCIPLES.md](../search/DOMAIN_AUTHORING_PRINCIPLES.md). This
section is the coordinator procedure for applying it; it does not restate the full principles.

For a domain DSL or declaration patch, classify each requested line before writing the prompt:

- `BUSINESS_CHOICE`: topology, identity selection, String keyword/text meaning, capabilities, public
  names, dynamic inventory, projection/invariants, or backend policy;
- `DERIVABLE_EVIDENCE`: facts already fixed by a type, direct selector, or declared inventory.

The canonical domain diff must show the first category and avoid hand-writing the second. Reject or
continue a patch when the business declaration still repeats selector/type/name/path/semantic facts,
maintains parallel ordered field lists, manually folds the document, or owns generic tree mechanics.
Low-level constructors may remain as platform escape hatches, but the onboarding example and golden
domain declaration must use the low-boilerplate authoring surface. Generic mechanics belong in neutral
tests; domain tests should focus on policy and the readable generated structure.

The escape-hatch count in the golden domain declaration is a tracked metric, not a one-time check: note
it (`rowWithSortParts`/`computedField`/`document` used instead of `completeDocument`, and any other
documented low-level constructor) at every accepted patch that touches the golden domain. A count that
increases without a recorded reason in the same patch is a review red flag - it means the low-boilerplate
authoring surface stopped covering a case it used to, or a business author reached for the escape hatch
out of habit rather than necessity.

This gate is about one domain's own declaration. A separate, opposite-direction question applies when
the patch changes the *reusable Gen2 kernel itself* (`search-gen2-contract`/`search-gen2-core`, not a
domain module): which real domain requirement justifies the feature, and which structurally different
tracer/neutral usage challenges its reusable shape? The fixture calibrates representation; it does not
invent production vocabulary. A BeautyQ-only representation without that calibration or an explicit
single-consumer note is narrow-by-extraction rather than narrow-by-design and remains a review red flag.

Every prompt that adds domain policy or reusable search mechanics must answer:

```text
Canonical entry point:
  Where will the new business policy be read?
Domain-owned differences:
  Which choices legitimately vary by domain?
Framework-derived mechanics:
  Which repeated operations are reused or extracted?
Reuse proof:
  Which neutral fixture or neutral tracer, or a second unrelated domain shape challenges the boundary?
Executable owner:
  Which declaration owns the policy, and which outputs are derived views?
```

Return CONTINUE_SAME_PATCH or REJECT when policy is unreachable from the canonical entry point,
generic code contains domain concepts, a domain copies reusable lookup/matching/validation mechanics,
or a generated view becomes a second policy owner.

## 1.4 No fake green

Unavailable external resources may cancel/resource-gate only when the test cannot verify its stated contract another valid way.

Reachable-but-broken resources fail red. Do not hide them behind saved data, fixtures, fallback branches, weaker checks, or saved-only downgrade.

If a selected path uses saved artifacts or fixtures, provenance must say so. Do not call it live coverage.

## 1.5 Protected source-truth invariant

This invariant is intentionally repeated. Do not remove, shorten, soften, or move it into `AGENTS.md`.

Before any patch design or delegated-agent prompt, source-confirm the relevant files, types, functions, fields, contracts, and validation commands.

If required source truth is missing, stop patch planning, delegated prompts, adjacent safe patches, and invented helper/API design.

A partial inventory may list `SOURCE_CONFIRMED` and `DOC_LEVEL_ONLY` facts, but if required anchors are missing it must end with a bundle request, not a patch proposal.

The source-truth gate has higher priority than the requested output shape.

---

# 2. Role split and prompt forms

Coordinator owns architecture, source-truth audit, patch strategy, decomposition, acceptance decisions, commit messages, and downstream planning.

Delegated agents own bounded edits, focused tests, mechanical verification, and narrow claim checks.

Read-only, audit, and source-truth work is coordinator-owned. Do not present it as a reusable delegated prompt.

Do not create reusable read-only prompts by putting `Do not edit files` inside a prompt.

Allowed reusable prompt form:

- `Task:` only;
- delegated edit work only.

Forbidden reusable prompt labels:

- `Coordinator analysis contract:`
- `External high-effort audit:`
- `source-confirm prompt`
- `read-only brief`
- `audit brief`

Do not paste all of `AGENTS.md` into prompts. Inline only task-specific excerpts.

---

# 3. Review closeout

## 3.1 Accepted closeout shape

After an accepted review / closed patch **A**, respond in this order:

1. `current result review` for **A**.
2. Extended commit message for **A**.
3. If the user already selected a source-confirmed follow-up **B**, provide exactly one delegated edit `Task:` prompt for **B**.
4. Provide the model recommendation for **B** as a coordinator note outside the prompt.
5. Provide exactly two unconditional downstream follow-up options after **B**: **C** and **D**.
6. Provide exactly one recommendation between **C** and **D**.
7. A combined post-**B** bundle script (with labeled sections `current result review`,
   `next option 1 source truth`, `next option 2 source truth`) is optional coordinator evidence
   capture, not a mandatory step for every accepted patch. Provide it only when the coordinator or
   user needs captured evidence for the next source-truth check; otherwise state the next
   options' source-truth status inline.

Mandatory review and verification are plumbing, not downstream options.

The prompted task **B** must not be repeated as option `1` or option `2`.

If **B** is not source-confirmed, do not write a delegated prompt. Output only the appropriate `BLOCKED_*` status with an executable bundle script.

A user reply of only `1` or `2` is only task selection. It is never review evidence, source truth, or validation.

If the user already chose the next task, continue that task after review acceptance. Do not re-offer the previously rejected alternative as an equal patch option.

If the next step is evidence-conditional, the coordinator evaluates the condition during review and chooses the branch. Do not present evidence-conditional branches as equal user choices.

## 3.2 Verification labels

Use only these labels:

- `FOCUSED GREEN` — requested focused suite passed; full repo unknown.
- `FULL GREEN` — full requested project test passed.
- `USER-VERIFIED FULL GREEN` — user ran the exact full command and reported green.
- `VERIFICATION BLOCKED` — local permissions/resources blocked verification.

Focused-only is never `FULL GREEN`.

Do not run or ask delegated agents to run full `sbt test` unless explicitly requested.

Default delegated-agent mode:

- focused checks only;
- prompt says `Do not run full sbt test.`;
- report says `Focused result only. Full verification left to coordinator/user.`

The user runs full `sbt test` when the coordinator recommends it.

## 3.3 Commit messages

Delegated agents do not commit.

For accepted non-trivial patches, prepare an extended commit message:

- subject;
- what changed;
- why;
- preserved boundaries/non-goals;
- user-visible effects where relevant;
- verification trailer.

Avoid generic water such as "compile clean" unless it changes trust status or explains a known failure/fix.

Do not prepare an extended commit message for `CONTINUE_SAME_PATCH`, `CORE_DIRECTION_OK_BUT_NOT_ACCEPTED`, `REJECT`, or `BLOCKED_*`.

Do not claim `FULL GREEN` from focused checks.

---

# 4. Delegated prompt rules

## 4.1 Source-truth gate before prompt

Before writing a delegated prompt, source-confirm exact seams. If source truth is missing, request a focused bundle and stop.

The prompt must not ask the agent to compensate with broad repository search unless broad source discovery is explicitly intended.

Do not say "use attached bundle". Inline relevant facts.

## 4.2 Required delegated prompt shape

Every delegated edit prompt must include:

```text
Task: feat(scope): exact small change

Read only:
- exact files

Edit only:
- exact files

Current facts:
- exact package paths, types, constructors, imports, helpers, aliases
- exact fixture/test style
- exact contracts and boundary conditions
- exact metric semantics when relevant

Goal:
- exact behavior/tests/docs wording to add or change

Forbidden:
- exact scope boundaries

Validation:
- focused commands
- Do not run full sbt test unless explicitly requested

Report:
- changed files
- focused validation result
- deviations / compile fixes
- do not claim FULL GREEN from focused checks

Stop:
- NEED_BUNDLE if listed files do not expose the required source truth
```

For high-specificity edits, prefer exact replacement hunk or before/after snippet over prose-only instructions.

Include a read budget and stop condition.

Historical milestone context belongs in coordinator review, not in every delegated prompt.

Do not hand agents broad grep-anchor lists as the implementation map when exact seams are known.

Do not include model recommendations inside delegated prompts.

## 4.3 Metrics, unsafe extraction, and test style

For B-lite / M-ESQ-EVAL prompts, inline metric semantics from current handoff/source. Default metric semantics: distinct variant-id counts unless source-confirmed otherwise.

Delegated prompts must inherit `AGENTS.md` Scala/test-style rules. Inline the unsafe-extraction constraint when a task touches decoded collections, options, Either, or JSON-derived structures.

For pure search/eval model tests, prefer:

- `AnyWordSpec`;
- deterministic UUID fixtures when IDs are needed;
- direct `assert`;
- pattern matching for ADTs/options/either;
- direct equality for case objects;
- no effects, Distage, runtime, Docker, ES, or Qdrant clients.

Avoid unless source-justified:

- `var`;
- `Ref`/`Atomic*` in simple pure tests;
- broad `Recording*`/`Counting*` doubles;
- `assert(true)`;
- empty success branches;
- `Option.get`;
- unsafe `.head` on decoded or data-derived collections;
- `asInstanceOf` / `isInstanceOf` assertions;
- null assertions.

If a suggested param/import/local is unused, remove it unless it is a source-confirmed lifecycle/readiness edge.

---

# 5. Manual/env-gated/cancel-by-default test DoD

If a patch adds or changes env vars, BEGIN/END markers, saved artifacts, manual local-service flows, or cancel/resource-gated behavior, it can be accepted only after its contract is complete.

Required before `ACCEPT`:

- selection/cancel/fail-red behavior covered;
- saved/live/manual inputs documented in code, tests, docs, or runbook as required by the task;
- no required branch coverage deferred as downstream work;
- no hidden fixture fallback unless explicitly requested;
- reachable-but-broken resources fail red;
- unavailable resources cancel/resource-gate only when no valid contract can be checked;
- full-suite cancel count reported if full suite was run.

If any required item remains, use `CONTINUE_SAME_PATCH`.

---

# 6. Bundle rules

Bundles are coordinator evidence capture only. After reading a bundle, the coordinator must inline important facts into the delegated prompt.

If anchors are still missing after review for the selected task, request a focused supplemental bundle and stop.

## 6.0 Delegated-agent bundle restriction

Delegated agents must not create review bundles, zip archives, or grep-report archives by default. Delegated patch prompts should request focused validation and concise reporting only. Review bundles are allowed only when the user/coordinator explicitly requests evidence capture for that task.

Clarifications:

- Source-truth bundles (section 6.1–6.3) may still be requested by the coordinator when anchors are missing — that is coordinator-run evidence gathering, not a delegated-agent action.
- Coordinator-owned bundle scripts are not default delegated patch closeout; see the softened step 7 in 3.1.
- Full `sbt test` remains forbidden for delegated agents unless explicitly requested (section 3.2).
- No `FULL GREEN` claim from focused checks (section 3.2/3.3) — this applies whether or not a bundle was captured.

This does not remove the bundle section below; bundle scripts remain available as optional coordinator evidence capture.

## 6.1 Bundle scripts must be read-only

Allowed:

- `git status`;
- bounded `rg`/`sed`;
- compact diffs;
- relevant source/test/docs anchors;
- untracked manifest/archive;
- truncation;
- zip handoff.

Forbidden:

- `sbt` or tests;
- Docker cleanup/startup;
- network/resource probes;
- package managers;
- deleting `target`;
- heavy or mutating commands.

Do not include full `target`, generated build output, screenshots, stale numbered files, or broad `HEAD~N --patch` unless explicitly requested.

## 6.2 Required bundle shape

When explicitly requested, user-facing bundle scripts must:

- create a repo-local `.review-bundles/beautyq-<topic>-<timestamp>-$RANDOM` workspace;
- use `BASE`, `WORK`, and `BUNDLE_ID`;
- include `BUNDLE_ID` in every internal artifact basename;
- avoid generic internal filenames;
- include task-relevant status, anchors, diffs, and manifests;
- truncate large text outputs;
- zip the bundle directory;
- print `wc -c` for artifacts and zip;
- run `cpf "$ZIP"`;
- print `echo "$ZIP"`.

When explicitly requested, patch-review bundles must include:

- `git diff --binary HEAD --`;
- `git diff --binary --cached`;
- `git diff --binary`;
- NUL-safe untracked-file manifest;
- NUL-safe untracked-file archive.

Include full recent commit bodies only when commit rationale/history is task-relevant.

If `cpf` is unavailable in an agent shell, still create the zip, print `echo "$ZIP"`, and report the deviation honestly. Do not replace `cpf` with `pbcopy` in user-facing snippets.

Artifact handoff commands must print the final workspace path and zip path.

## 6.3 Canonical bundle skeleton

Use this shape unless the task requires a narrower variant:

```bash
BASE=".review-bundles/beautyq-<topic>-$(date -u +%Y%m%d-%H%M%S)-$RANDOM"
WORK="$BASE.dir"
BUNDLE_ID="$(basename "$BASE")"
OUT="$WORK/${BUNDLE_ID}-bundle.txt"

mkdir -p "$WORK"

{
  echo "## status"
  git status --short
  echo

  echo "## relevant anchors"
  rg -n "PatternA|PatternB" AGENTS.md docs leaderboard-app-shell/src/main leaderboard-app-shell/src/test || true
  echo

  echo "## recent commits"
  git --no-pager log -14 --oneline
} > "$OUT" 2>&1

git --no-pager diff --binary HEAD -- > "$WORK/${BUNDLE_ID}-tracked-changes-from-head.patch" 2>&1 || true
git --no-pager diff --binary --cached > "$WORK/${BUNDLE_ID}-staged-tracked-changes.patch" 2>&1 || true
git --no-pager diff --binary > "$WORK/${BUNDLE_ID}-unstaged-tracked-changes.patch" 2>&1 || true
git ls-files --others --exclude-standard -z > "$WORK/${BUNDLE_ID}-untracked-files.nul"

python3 - <<'PY' "$WORK" "$BUNDLE_ID"
import pathlib, sys, tarfile
work = pathlib.Path(sys.argv[1])
bundle_id = sys.argv[2]
repo = pathlib.Path.cwd()
nul = work / f"{bundle_id}-untracked-files.nul"
manifest = work / f"{bundle_id}-untracked-files.manifest.txt"
tar_path = work / f"{bundle_id}-untracked-files.tar.gz"
items = [p for p in nul.read_bytes().split(b"\0") if p]
paths = [p.decode("utf-8", errors="replace") for p in items]
manifest.write_text("\n".join(paths) + ("\n" if paths else ""), encoding="utf-8")
with tarfile.open(tar_path, "w:gz", dereference=False) as tar:
    for rel in paths:
        path = repo / rel
        if path.exists() or path.is_symlink():
            tar.add(path, arcname=rel, recursive=False)
PY

ZIP="$BASE.zip"
rm -f "$ZIP"
(cd "$WORK" && zip -9 -r "../$(basename "$ZIP")" .) >/dev/null

wc -c "$OUT"
wc -c "$WORK/${BUNDLE_ID}-tracked-changes-from-head.patch"
wc -c "$WORK/${BUNDLE_ID}-staged-tracked-changes.patch"
wc -c "$WORK/${BUNDLE_ID}-unstaged-tracked-changes.patch"
wc -c "$WORK/${BUNDLE_ID}-untracked-files.manifest.txt"
wc -c "$WORK/${BUNDLE_ID}-untracked-files.tar.gz"
wc -c "$ZIP"
cpf "$ZIP"
echo "$ZIP"
```

## 6.4 No shell/session hazards

User-facing copy-paste terminal commands must not close, replace, or mutate the user's shell behavior.

Forbidden:

- `exit`;
- `exec`;
- `kill $$`;
- terminating traps;
- `set -e`;
- `set -u`;
- `set -o pipefail`.

Use local flags and conditional branches. On failure, print `BLOCKED` or `MISSING`, skip dependent steps, and still print diagnostics.

## 6.5 No `/tmp` for persisted evidence

`/tmp` is only for disposable scratch or short-lived bundles.

Multi-iteration evidence workspaces must use project-local paths, defaulting to:

```text
./.beautyq-evidence-runs/<run-id>/
```

The path must be gitignored or added to `.git/info/exclude` before use.

Use `$HOME` or external paths only when the user explicitly asks.

---

# 7. Documentation ownership

Before changing docs, identify the canonical owner of the fact.

Keep:

- stable repo behavior in `AGENTS.md`;
- coordinator workflow in this file;
- task-local facts in delegated prompts;
- volatile verification counts in reports, not long-lived docs or commit messages.

Do not add sibling coordinator/runbook files under `docs/local` unless the user explicitly asks or the file is a task-local temporary evidence template.

Do not duplicate long API lists, metric semantics, roadmap state, bundle rules, or prompt-writing rules across docs.

Duplicate only short safety-critical guardrails that must be visible at multiple entrypoints.

When the user asks the coordinator to edit this guide or another text doc, provide a ready replacement file/artifact by default. Do not ask the user to apply a coordinator-authored patch unless the user explicitly asked for a patch.

---

# 8. Model recommendations

Model recommendation blocks are only for non-trivial delegated edit prompts.

Keep them outside the delegated prompt.

Decision order:

1. Source truth.
2. Task type and risk.
3. User preference.
4. Model choice.

Recommend the minimal sufficient model, not a comfortable heavier default. Docs-only or narrow review-fix delegated edits usually use cheaper/local models unless policy or source complexity justifies more.

Do not use a stronger model to invent missing source truth.

Exact model names:

- `Qwen 256/512`
- `Qwen 1024/2048`
- `Qwen 4096`
- `MiMo-V2.5`
- `MiMo-V2.5-Pro`
- `MiniMax-M3`
- `GPT-5.5-medium`
- `GPT-5.5-high`

Compact mapping:

- tiny mechanical docs/code edits: `Qwen 256/512` or `MiMo-V2.5`;
- bounded pure code/docs patches from exact recipe: `Qwen 1024/2048` or `MiMo-V2.5-Pro`;
- larger source-confirmed local work: `Qwen 4096`;
- source-confirmed repo inventory or stronger non-GPT agentic work: `MiniMax-M3`;
- complex code writing when cheaper/local models are likely to waste iterations: `GPT-5.5-medium`;
- very high-risk production lifecycle/runtime/backend migration code: `GPT-5.5-high`.

Recommendation note template:

```text
Task classification:
- Type:
- Source truth:
- Risk:
- Preference:
- Availability:

Run recommendation:
- Cheapest likely to work:
- Faster cloud option:
- Stronger non-GPT option:
- GPT option, only if justified:
- If source truth is missing:
```

---

# 9. Senior audit playbook

Senior audit work is coordinator-owned read-only source-truth work. Run it with bundle scripts, not delegated read-only prompts.

This playbook is not product route truth, not a roadmap, and not a substitute for canonical docs.

Run waves independently as read-only evidence gathering, not as edits.

Each wave ends in exactly one of:

- accepted patch candidate with source-confirmed edit seams;
- no-issue-found evidence;
- `BLOCKED_NEED_BUNDLE`.

A wave finding never skips the source-truth gate.

## 9.1 Audit waves

1. **Docs-cement audit** — stale milestone counts; future plans written as current truth; dummy/backend equivalence wording; benchmark-as-rollout wording; fallback/fusion/rerank ambiguity.
2. **Project values/goals audit** — roadmap memory vs source truth; measured local/test gates before production claims; local/test proof is not production approval; baseline owns hard constraints; supplement stays candidate-only; verification labels stay honest.
3. **Architecture boundary audit** — route/default graph; ES/Qdrant ownership split; frontend provenance contract; benchmark/eval non-goals; local managed vs production boundary.
4. **Dependency/DI/lifecycle audit** — Distage roots; axes/activation; heavy dependency construction; graph garbage collection; startup/readiness ordering through dependency edges.
5. **Scala/FP/BIO audit** — typed errors preserved; resource safety; no swallowed failures; narrow effects; no mutable spy creep; no broad production graph in focused specs.
6. **Test taxonomy audit** — Contractual/Regression/Progression/Benchmark × Blackbox/Effectual/Whitebox × Atomic/Group/Communication coverage gaps; dummy/in-memory tests do not oversell real-backend proof.
7. **Readiness/reuse/state-marker audit** — fingerprints; metadata; sidecars; counts; compatibility checks; markers validated against real resources, not assumed from marker alone.
8. **Eval/golden/report governance audit** — derived counts; golden drift; report/source ownership; no accidental semantic/Qdrant-owned query class creeps into baseline-owned classes.
9. **Decoder/API-shape drift audit** — live external JSON shapes vs unit fixtures; stable payload keys; persisted JSON compatibility.

Accepted wave findings still follow normal closeout: extended commit message, exactly two downstream options, one recommendation, and a combined post-task bundle script.

---

# 10. Pre-send / pre-accept checklist

Before sending a delegated prompt:

- Is source truth sufficient?
- Are read/edit files exact?
- Are required facts inlined?
- Is this edit bounded?
- Have business choices been separated from tautological evidence?
- Does the target domain example show the intended authoring surface rather than platform internals?
- Are validation commands focused unless full test was explicitly requested?
- Does the prompt stop with `NEED_BUNDLE` if listed files are insufficient?
- Are model recommendations outside the prompt?

Before accepting a patch:

- Is the original DoD complete?
- Does the canonical domain declaration contain only business choices, with tautological evidence derived by the reusable layer?
- Would a structurally similar new domain require copying per-type codecs, repeated IDs/paths/semantics, parallel lists, document folds, or renderer mechanics?
- If the patch touches `search-gen2-contract`/`search-gen2-core`: is the feature justified by a real domain need and its reusable shape challenged by a different neutral/tracer usage, or explicitly marked single-consumer?
- Did the escape-hatch count in the golden domain declaration increase without a recorded reason?
- Are required tests/docs/validation/branch coverage done in this same patch?
- If not, am I using `CONTINUE_SAME_PATCH` instead of `ACCEPT`?
- Are downstream options only new work after current patch completion?
- Are verification labels honest?
- Is the commit message only for an accepted patch?
- Does each commit have one cohesive architectural purpose that its subject names? Split independent
  risk layers or unrelated "why" narratives, but do not mechanically split one coherent checkpoint by
  file, test, documentation section or delegated D-item. A planned brick may be one commit or a small
  cohesive series; post-hoc surgery is not a substitute for reviewable intent.
