# General Scala project rules

General guidance for Scala tasks:
- project files are the source of truth for the current state
- before changing anything, first read the relevant project files needed to understand the current state and the task safely
- when a task names explicit project files, start with those project files; read beyond them only when needed for safe progress
- preserve existing behavior unless the task explicitly asks for behavior changes
- prefer the smallest safe, reversible change that fixes only the failing scope
- reuse existing code, helpers, naming, code style, and file structure where possible; keep shared or reusable elements centralized unless the task explicitly requires duplication
- prefer fewer touched files, fewer extracted helpers, and the existing package/path/naming/layout unless the task or reread project files require otherwise
- when reusing, moving, or copying Scala code to a new file, keep the full original import block; unused imports are acceptable
- match the existing Scala syntax/dialect already used by the project files; do not introduce alternative import or declaration syntax unless it is already present in the project files
- respect actual symbol boundaries in the codebase; do not assume packages, objects, or companions expose members unless confirmed in the project files
- treat empty directories as neutral; rely on actual files, package declarations, and task requirements instead
- prefer extraction refactors over architectural rewrites; use architecture or domain labels only to organize moved code, not to justify changing execution shape, discovery shape, inheritance shape unless the task explicitly requires it
- when the task mentions architecture or business-domain labels, derive them from the touched project files first; if unclear, record uncertainty instead of inventing architecture
- do not introduce new packages, layers, ports, adapters, abstractions, or file-layout changes unless the task explicitly requires them or reread project files prove they are needed
- before making structural claims, first inventory the named units that are in scope in the project files you read
- distinguish confirmed relationships from possible relationships; do not present guesses as confirmed project-file constraints
- verify work appropriate to the current step; when implementing, verify the requested outcome in the changed scope; do not claim success based on intention
- derive verification scope from changed sbt modules and their direct dependent modules; if that is not enough, read build.sbt or the relevant build definition files before finalizing verification scope
- use targeted testOnly checks only as a narrowing or debugging aid, not as final verification

General final report:
- what changed in project files
- what was verified for project files
- what was not verified for project files
- keep the final report honest to the actual changed project files and verification actually performed
- any remaining risks, assumptions, or follow-up items
