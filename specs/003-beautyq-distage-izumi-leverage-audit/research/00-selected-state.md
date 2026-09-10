# Selected Research State

Run opener (T001) output for feature `003-beautyq-distage-izumi-leverage-audit`.
Every later finding binds to the source state and the resolved framework version below.

## BeautyQ source state

Original T001 SourceStateRecord, the canonical evaluated source state selected when
this research run opened:

- headSha: `50e794584d68768403d5c45d6d7fc21c2db87f6c`
- worktreeClean: true
- statusPorcelain: empty
- diffStat: empty
- cachedDiffStat: empty
- stateId: `50e7945-clean`
- recordedAt: `2026-09-10T16:19:19Z`

Exact commands and observed results at run start:

```text
$ git rev-parse HEAD
50e794584d68768403d5c45d6d7fc21c2db87f6c
$ git status --short
(empty)
$ git diff --stat
(empty)
$ git diff --cached --stat
(empty)
```

The source/build worktree was clean at run open, so the evaluated BeautyQ
source/build state is exactly committed `HEAD`
(`50e794584d68768403d5c45d6d7fc21c2db87f6c`). These values describe the source
state selected when the run opened and are not replaced by Git status created by
later research artifacts.

## Execution bookkeeping after run opener

- Later repository HEAD may have advanced due to commits containing feature-local
  `research/*` artifacts.
- Such research-only movement is NOT a change to the selected BeautyQ source/build
  snapshot.
- Current execution artifacts are expected deltas from the original run-start record.
- A real production/test/build/project/framework source delta WOULD require
  supersession/recheck.

## Distage / Izumi dependency

Declared (from `build.sbt`):

- groupId: `io.7mind.izumi`
- version: `1.2.25` (`build.sbt:4` `V.distage = "1.2.25"`; `build.sbt:5` `V.logstage = distage`)
- artifacts (`build.sbt:24-29`):
  - `distage-core` (`build.sbt:24`)
  - `distage-extension-config` (`build.sbt:25`)
  - `distage-framework` (`build.sbt:26`)
  - `distage-framework-docker` (`build.sbt:27`)
  - `distage-testkit-scalatest` (`build.sbt:28`, `% Test`)
  - `logstage-adapter-slf4j` (`build.sbt:29`, version `V.logstage`)
- No version override present in `project/*.sbt`; `sbt.version=1.12.11` (`project/build.properties`);
  `project/plugins.sbt` adds only `sbt-tpolecat`, `sbt-git`, `sbt-native-packager`, `sbt-scalafmt`.

### Actually resolved (verification)

Resolved: **`1.2.25`** for both the compile/runtime `io.7mind.izumi` artifacts and the
Test-scope `distage-testkit-scalatest`, as reported by sbt's own resolved classpath for the
`leaderboard-app-shell` project (the narrowest app scope that actually resolves the BeautyQ
dependency graph).

Exact command (one focused, read-only sbt observation; no build edits, no lockfile):

```text
mkdir -p target/codex-sbt/ivy2
sbt --batch --no-global -Dsbt.server=false -Dsbt.server.forcestart=true \
  -Dsbt.ivy.home=target/codex-sbt/ivy2 \
  'show leaderboard-app-shell/dependencyClasspath' \
  'show leaderboard-app-shell/Test/dependencyClasspath'
```

Observed result (`[success]` for both scopes; sbt-attributed resolved jars under the local
Coursier cache, `scala-3.3.8`):

```text
io/7mind/izumi/distage-core_3/1.2.25/distage-core_3-1.2.25.jar
io/7mind/izumi/distage-framework_3/1.2.25/distage-framework_3-1.2.25.jar
io/7mind/izumi/distage-extension-config_3/1.2.25/distage-extension-config_3-1.2.25.jar
io/7mind/izumi/logstage-adapter-slf4j_3/1.2.25/logstage-adapter-slf4j_3-1.2.25.jar
io/7mind/izumi/distage-framework-docker_3/1.2.25/distage-framework-docker_3-1.2.25.jar
io/7mind/izumi/distage-testkit-scalatest_3/1.2.25/distage-testkit-scalatest_3-1.2.25.jar   # Test scope
io/7mind/izumi/distage-testkit-core_3/1.2.25/distage-testkit-core_3-1.2.25.jar             # Test scope, transitive
io/7mind/izumi/distage-core-api_3/1.2.25/*.jar
io/7mind/izumi/distage-framework-api_3/1.2.25/*.jar
io/7mind/izumi/distage-extension-logstage_3/1.2.25/*.jar
io/7mind/izumi/distage-extension-plugins_3/1.2.25/*.jar
io/7mind/izumi/distage-core-proxy-bytebuddy_3/1.2.25/*.jar
io/7mind/izumi/logstage-core_3/1.2.25/*.jar
io/7mind/izumi/logstage-rendering-circe_3/1.2.25/*.jar
io/7mind/izumi/fundamentals-*_3/1.2.25/*.jar
```

Agreement/discrepancy:

- Compile/runtime scope: declared `1.2.25` == resolved `1.2.25`.
- Test scope (`distage-testkit-scalatest`): declared `1.2.25` == resolved `1.2.25`.
- **AGREE** across both scopes. Framework version is **VERIFIED at `1.2.25`**.
- Scope of claim: the declared Distage/Izumi compile/runtime artifacts plus the Test-scope
  `distage-testkit-scalatest` (and its transitive `distage-testkit-core`). Other transitive
  Izumi artifacts are not individually enumerated as independently verified.

verifiedAt: `2026-09-10T16:27:06Z`

## Framework source evidence order

Later B0 framework inspection MUST use, in order (stop as soon as the claim is established):

1. Current build coordinates/resolution evidence in this file (declared + resolved `1.2.25`).
2. Local source jars/artifacts for exactly `1.2.25` if available (e.g. Coursier/Ivy cache
   `-sources.jar` for `io.7mind.izumi` `1.2.25`), then class jars for `1.2.25`.
3. Matching upstream release/tag source/docs/examples for `1.2.25` only if local source is insufficient.
4. Never latest/`master` as a substitute authority.

No framework audit is performed in T001; this only fixes the exact version B0 is allowed to inspect.

## Run boundaries

- no Git/index/ref/history mutation
- no product/framework/build/source mutation
- later findings must bind to this `stateId` (`50e7945-clean`) and resolved framework version (`1.2.25`)
- absence of evidence is `BLOCKED_NEED_EVIDENCE`, never a passing conclusion
