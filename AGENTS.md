# General Scala project rules

General guidance for Scala tasks:
- project files = all files outside `.opencode/`
- workflow state artifacts = only the exact files under `.opencode/state/` required by the current workflow step
- project files are the source of truth for the current state
- workflow state artifacts may guide the workflow, but they are never the source of truth for the current state of project files
- before changing anything, first read the relevant project files needed to understand the current state and the task safely
- when a task names explicit project files, start with those project files; read beyond them only when needed for safe progress, and record the reason in workflow state artifacts
- preserve existing behavior unless the task explicitly asks for behavior changes
- reuse existing code, helpers, naming, code style, and file structure where possible
- prefer the smallest correct change over broad refactoring
- keep shared or reusable elements centralized; do not duplicate them unless the task explicitly requires duplication
- if something is uncertain, make the safest reversible change
- fix only the failing scope first; do not refactor unrelated code while resolving errors
- when compilation fails after a change, compare the changed project files against the original project files before inventing new abstractions
- do not claim success based on intention; claim only what was actually changed and verified
- never use /tmp; keep temp/intermediate/cache/log files only under `.opencode/tmp/` inside the project directory
- for workflow state artifacts, use `.opencode/state/` inside the project and overwrite deterministic filenames
- when reusing, moving, or copying Scala code to a new file, keep the full original import block; unused imports are acceptable
- match the existing Scala syntax/dialect already used by the project files; do not introduce alternative import or declaration syntax unless it is already present in the project files
- respect actual symbol boundaries in the codebase; do not assume packages, objects, or companions expose members unless confirmed in the project files
- prefer extraction refactors over architectural rewrites; use architecture or domain labels only to organize moved code, not to justify changing execution shape, discovery shape, inheritance shape unless the task explicitly requires it
- respect the existing pragmatic hexagonal boundaries in the codebase; do not introduce new ports, adapters, layers, or package splits unless the task explicitly requires it
- after a failed broad verification, capture the first failing project file and exact error lines, then keep fixes scoped to that project file until the blocker is resolved before rerunning the broad check
- before making structural claims, first inventory the named units that are in scope in the project files you read
- do not state a count, coverage claim, or completeness claim unless the counted or covered named units are explicitly listed in workflow state artifacts
- treat structured workflow state artifact fields as the source of truth for derivable facts such as inventories, counts, scope mode, classifications, and stage-specific handling
- do not restate or recompute derivable facts in prose fields when those facts are already represented in structured workflow state artifact fields
- use prose fields only for non-derivable facts, concise explanations, uncertainties, and risks that are not already encoded in structured workflow state artifact fields
- distinguish confirmed relationships from possible relationships; do not present possible relationships as confirmed project files constraints
- every named unit listed in workflow state artifacts must have exactly one named unit role
- in reconnaissance, every named unit listed in workflow state artifacts must have exactly one likely named unit handling
- planning, implementation, and audit must refine likely named unit handling into stage-appropriate final named unit handling before project files are changed or judged complete
- named unit role describes what the unit is in the current or final project files
- likely named unit handling in reconnaissance describes whether the unit appears likely touched, likely unchanged, needs planning decision, or is out of scope with explicit reason
- final named unit handling describes what should happen to the unit for the task in planning, implementation, and audit
- do not use named unit role as named unit handling, and do not use named unit handling as named unit role
- in reconnaissance, record observations, likely touched project files, named unit roles, and likely named unit handling, but do not commit to destructive outcomes or exact final layout unless that outcome is explicitly required by the task or forced by project files constraints

General verification:
- verify that the requested outcome is present and that no required existing functionality/content was lost in project files
- run the narrowest relevant checks available for the affected project files scope
- prefer `sbt module1/test:compile module2/test:compile` checks first, then `sbt module1/test module2/test` for all changed and dependent modules
- derive changed and direct dependent module scope from changed project files; if that is not enough, read build.sbt or the relevant build definition files before finalizing verification scope
- if verification was not run, say so explicitly
- if verification failed, write the failure to workflow state artifact file instead of presenting the task as done

General final report:
- changed project files only
- written to disk workflow state artifacts only
- list every workflow state artifact actually written in the current step; do not omit workflow state artifacts
- what was intentionally left unchanged in project files
- what was verified for project files
- keep the final report honest to the actual changed project files, workflow state artifacts written to disk, and verification actually performed in the current step
- what was not verified for project files
- any risks, assumptions, or follow-up items
