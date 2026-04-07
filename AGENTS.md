# Scala project rules

General guidance for any Scala task:
- first understand the current state before changing anything
- read relevant files first, then act
- preserve existing behavior unless the task explicitly asks for behavior changes
- reuse existing code, helpers, naming, code style, and file structure where possible
- prefer the smallest correct change over broad refactoring
- keep shared or reusable elements centralized; do not duplicate them unless the task explicitly requires duplication
- if something is uncertain, make the safest reversible change
- fix only the failing scope first; do not refactor unrelated code while resolving errors
- when compilation fails after a change, compare the changed code against the original source before inventing new abstractions
- do not claim success based on intention; claim only what was actually changed and verified
- do not use /tmp; use only project paths for temporary, intermediate, cache, and log files
- for workflow handoff artifacts, use `.opencode/state/` inside the project and overwrite deterministic filenames
- do not treat `.opencode/state/` handoff artifacts as source changes unless the task explicitly asks for that
- when reusing, moving, or copying Scala code to a new file, keep the full original import block; unused imports are acceptable
- match the existing Scala syntax/dialect already used by the file/project; do not introduce alternative import or declaration syntax unless it is already present in the codebase
- respect actual symbol boundaries in the codebase; do not assume packages, objects, or companions expose members unless confirmed in the source
- prefer extraction refactors over architectural rewrites; use architecture or domain labels only to organize moved code, not to justify changing execution shape, discovery shape or inheritance shape unless the task explicitly requires it
- respect the existing pragmatic hexagonal boundaries in the codebase; do not introduce new ports, adapters, layers splits unless the task explicitly requires it
- after a failed broad verification, capture the first failing file and exact error lines, then keep fixes scoped to that file until the blocker is resolved before rerunning the broad check

General verification:
- verify that the requested outcome is present and that no required existing functionality/content was lost
- run the narrowest relevant checks available for the affected scope
- prefer `sbt module1/test:compile module2/test:compile` checks first, then `sbt module1/test module2/test` for all changed and dependent modules
- if verification was not run, say so explicitly
- if verification failed, report the failure clearly instead of presenting the task as done

General final report:
- what changed
- what was intentionally left unchanged
- what was verified
- what was not verified
- any risks, assumptions, or follow-up items
