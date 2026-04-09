# General Scala project rules

General guidance for Scala tasks:
- project files are the source of truth for the current state
- before changing anything, first read the relevant project files needed to understand the current state and the task safely
- when a task names explicit project files, start with those project files; read beyond them only when needed for safe progress
- preserve existing behavior unless the task explicitly asks for behavior changes
- prefer the smallest safe, reversible change that fixes only the failing scope; reuse existing code, helpers, naming, code style, package/path, and file layout where possible; keep shared or reusable elements centralized unless the task explicitly requires duplication; do not introduce new packages, layers, ports, adapters, abstractions, or broader file-layout changes unless the task explicitly requires them or reread project files prove they are needed
- when reusing, moving, or copying Scala code to a new file, keep the full original import block; unused imports are acceptable
- match the existing Scala syntax/dialect already used by the project files; do not introduce alternative import or declaration syntax unless it is already present in the project files
- respect actual symbol boundaries in the codebase; do not assume packages, objects, or companions expose members unless confirmed in the project files
- decide package, path, and layout from actual files, package declarations, and task requirements; treat empty directories, labels, and naming patterns as non-authoritative hints only
- prefer extraction refactors over architectural rewrites; when architecture or business-domain labels appear in the task or code, derive them from the touched project files first and use them only to describe existing organization, not to justify new execution shape, discovery shape, inheritance shape, packages, or layout unless the task explicitly requires it
- before making structural claims, first inventory the named units that are in scope in the project files you read
- distinguish confirmed relationships from possible relationships; do not present guesses as confirmed project-file constraints
- verify work appropriate to the current step and changed scope; when implementing, derive verification scope from changed sbt modules and their direct dependent modules, read build.sbt or relevant build files if needed, use testOnly only for narrowing or debugging, and do not claim success based on intention

General final report:
- what changed in project files
- what was verified for project files
- what was not verified for project files
- keep the final report honest to the actual changed project files and verification actually performed
- any remaining risks, assumptions, or follow-up items
