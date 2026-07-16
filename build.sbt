import scala.util.chaining.scalaUtilChainingOps

val V = new {
  val distage       = "1.2.25"
  val logstage      = distage
  val scalatest     = "3.2.19"
  val scalacheck    = "1.19.0"
  val http4s        = "0.23.33"
  val doobie        = "1.0.0-RC11"
  val catsCore      = "2.13.0"
  val zio           = "2.1.22"
  val zioCats       = "23.0.0.8"
  val kindProjector = "0.13.4"
  val circeGeneric  = "0.14.15"
  val graalMetadata = "0.11.3"
  val catsEffect    = "3.5.4"
  val tapir         = "1.10.7"
}

val Deps = new {
  val scalatest  = "org.scalatest" %% "scalatest" % V.scalatest
  val scalacheck = "org.scalacheck" %% "scalacheck" % V.scalacheck

  val distageCore    = "io.7mind.izumi" %% "distage-core" % V.distage
  val distageConfig  = "io.7mind.izumi" %% "distage-extension-config" % V.distage
  val distageRoles   = "io.7mind.izumi" %% "distage-framework" % V.distage
  val distageDocker  = "io.7mind.izumi" %% "distage-framework-docker" % V.distage
  val distageTestkit = "io.7mind.izumi" %% "distage-testkit-scalatest" % V.distage
  val logstageSlf4j  = "io.7mind.izumi" %% "logstage-adapter-slf4j" % V.logstage

  val http4sDsl    = "org.http4s" %% "http4s-dsl" % V.http4s
  val http4sServer = "org.http4s" %% "http4s-ember-server" % V.http4s
  val http4sClient = "org.http4s" %% "http4s-ember-client" % V.http4s
  val http4sCirce  = "org.http4s" %% "http4s-circe" % V.http4s
  val tapirHttp4sServer = "com.softwaremill.sttp.tapir" %% "tapir-http4s-server" % V.tapir
  val tapirJsonCirce = "com.softwaremill.sttp.tapir" %% "tapir-json-circe" % V.tapir

  val circeGeneric = "io.circe" %% "circe-generic" % V.circeGeneric
  val circeParser  = "io.circe" %% "circe-parser" % V.circeGeneric

  val doobie         = "org.tpolecat" %% "doobie-core" % V.doobie
  val doobiePostgres = "org.tpolecat" %% "doobie-postgres" % V.doobie
  val doobieHikari   = "org.tpolecat" %% "doobie-hikari" % V.doobie

  val kindProjector = "org.typelevel" % "kind-projector" % V.kindProjector cross CrossVersion.full

  val zio     = "dev.zio" %% "zio" % V.zio
  val zioCats = "dev.zio" %% "zio-interop-cats" % V.zioCats

  val catsCore = "org.typelevel" %% "cats-core" % V.catsCore

  val graalMetadata = "org.graalvm.buildtools" % "graalvm-reachability-metadata" % V.graalMetadata

  val CoreDeps = Seq(
    distageCore,
    distageRoles,
    distageConfig,
    logstageSlf4j,
    distageDocker,
    distageTestkit % Test,
    scalatest % Test,
    scalacheck % Test,
    http4sDsl,
    http4sServer,
    http4sClient % Test,
    http4sCirce,
    circeGeneric,
    doobie,
    doobiePostgres,
    doobieHikari,
    catsCore,
    graalMetadata,
  )
}

inThisBuild(
  Seq(
    crossScalaVersions := Seq(/*"2.13.18",*/ "3.3.8"),
//    crossScalaVersions := Seq("3.3.8", "2.13.18"), // uncomment to use Scala 3 in IDE
    scalaVersion := crossScalaVersions.value.head,
    version      := "1.0.0",
    organization := "io.7mind",
  )
)

lazy val `leaderboard-core` = project
  .pipe(lightweightSettings(Seq(
    Deps.distageCore,
  )))

lazy val `search-core` = project
  .pipe(lightweightSettings(Seq(
    Deps.circeGeneric,
    Deps.scalatest % Test,
  )))
  .dependsOn(`leaderboard-core`)

lazy val `search-elasticsearch` = project
  .pipe(lightweightSettings(Seq(
    Deps.circeGeneric,
    Deps.circeParser,
    Deps.zio,
    Deps.scalatest % Test,
  )))
  .dependsOn(`leaderboard-core`, `search-core`, searchContractCore)

lazy val `search-qdrant` = project
  .pipe(lightweightSettings(Seq(
    Deps.circeGeneric,
    Deps.circeParser,
    Deps.zio,
    Deps.scalatest % Test,
  )))
  .dependsOn(`leaderboard-core`, `search-core`, searchContractCore)

// --- BeautyQ search module skeletons (Phase 2 of docs/search/BEAUTYQ_SEARCH_CONTRACT_MODULE_SPLIT_PLAN.md) ---
// Module shells only: no existing implementation code has been moved into them yet.
// See the plan doc's "Target 10-module split" / "Dependency DAG" sections for the intended
// ownership and dependency boundaries these projects will grow into.

lazy val repoCore = project
  .in(file("repo-core"))
  .settings(name := "repo-core")
  .pipe(lightweightSettings(Seq(
    Deps.distageCore,
    Deps.zio % Test,
    Deps.scalatest % Test,
  )))
  .dependsOn(`leaderboard-core`)

lazy val searchContractCore = project
  .in(file("search-contract-core"))
  .settings(name := "search-contract-core")
  .pipe(lightweightSettings(Seq(
    Deps.scalatest % Test,
  )))

lazy val beautyqModel = project
  .in(file("beautyq-model"))
  .settings(name := "beautyq-model")
  .pipe(lightweightSettings(Seq(
    Deps.circeGeneric,
    Deps.scalatest % Test,
  )))
  .dependsOn(`leaderboard-core`)

lazy val beautyqSearchContract = project
  .in(file("beautyq-search-contract"))
  .settings(name := "beautyq-search-contract")
  .pipe(lightweightSettings(Seq(
    Deps.circeGeneric,
    Deps.scalatest % Test,
  )))
  .dependsOn(searchContractCore, beautyqModel, repoCore, `search-core`)

lazy val beautyqSearchRepositories = project
  .in(file("beautyq-search-repositories"))
  .settings(name := "beautyq-search-repositories")
  .pipe(lightweightSettings(Seq(
    Deps.distageCore,
    Deps.doobie,
    Deps.doobiePostgres,
    Deps.catsCore,
    Deps.logstageSlf4j,
  )))
  .dependsOn(`leaderboard-core`, repoCore, beautyqModel)

lazy val beautyqSearchMaterialization = project
  .in(file("beautyq-search-materialization"))
  .settings(name := "beautyq-search-materialization")
  .pipe(lightweightSettings(Nil))
  .dependsOn(beautyqSearchContract, beautyqSearchRepositories, repoCore, beautyqModel)

lazy val beautyqSearchWiring = project
  .in(file("beautyq-search-wiring"))
  .settings(name := "beautyq-search-wiring")
  .pipe(lightweightSettings(Seq(
    Deps.scalatest % Test,
  )))
  .dependsOn(beautyqSearchContract, beautyqSearchMaterialization, `search-elasticsearch`, `search-qdrant`)

// --- BeautyQ Search Gen2 (side by side with Gen1; see docs/gen2/BEAUTYQ_SEARCH_GEN2_IMPLEMENTATION_PLAN.md) ---
// Independent module DAG. No Gen2 project may depend on a Gen1 search project; enforced by
// SearchGen2ModuleFirewallSpec. Brick 0 adds only the module graph and firewall, no search behavior.

lazy val searchGen2Contract = project
  .in(file("search-gen2-contract"))
  .settings(name := "search-gen2-contract")
  .pipe(lightweightSettings(Seq(
    Deps.scalatest % Test,
  )))
  .dependsOn(`leaderboard-core`)

lazy val searchGen2Core = project
  .in(file("search-gen2-core"))
  .settings(name := "search-gen2-core")
  .pipe(lightweightSettings(Seq(
    Deps.distageCore,
    Deps.scalatest % Test,
  )))
  .dependsOn(searchGen2Contract)

lazy val searchGen2Elasticsearch = project
  .in(file("search-gen2-elasticsearch"))
  .settings(name := "search-gen2-elasticsearch")
  .pipe(lightweightSettings(Seq(
    Deps.circeGeneric,
    Deps.circeParser,
    Deps.scalatest % Test,
  )))
  .dependsOn(searchGen2Contract, searchGen2Core)

lazy val searchGen2Qdrant = project
  .in(file("search-gen2-qdrant"))
  .settings(name := "search-gen2-qdrant")
  .pipe(lightweightSettings(Nil))
  .dependsOn(searchGen2Contract, searchGen2Core)

lazy val beautyqSearchGen2Contract = project
  .in(file("beautyq-search-gen2-contract"))
  .settings(name := "beautyq-search-gen2-contract")
  .pipe(lightweightSettings(Seq(
    Deps.scalatest % Test,
  )))
  .dependsOn(searchGen2Contract, repoCore, beautyqModel)

lazy val beautyqSearchGen2Materialization = project
  .in(file("beautyq-search-gen2-materialization"))
  .settings(name := "beautyq-search-gen2-materialization")
  .pipe(lightweightSettings(Seq(
    Deps.distageCore,
    Deps.doobie,
    Deps.doobiePostgres,
    Deps.scalatest % Test,
  )))
  .dependsOn(beautyqSearchGen2Contract, searchGen2Core, beautyqSearchRepositories, repoCore, beautyqModel)

lazy val beautyqSearchGen2Wiring = project
  .in(file("beautyq-search-gen2-wiring"))
  .settings(name := "beautyq-search-gen2-wiring")
  .pipe(lightweightSettings(Seq(
    Deps.scalatest % Test,
  )))
  .dependsOn(beautyqSearchGen2Contract, beautyqSearchGen2Materialization, searchGen2Core, searchGen2Elasticsearch, searchGen2Qdrant)

lazy val beautyqSearchGen2Eval = project
  .in(file("beautyq-search-gen2-eval"))
  .settings(name := "beautyq-search-gen2-eval")
  .pipe(lightweightSettings(Nil))
  .dependsOn(beautyqSearchGen2Wiring)

lazy val appServices = project
  .in(file("app-services"))
  .settings(name := "app-services")
  .pipe(lightweightSettings(Seq(
    Deps.distageCore,
    Deps.scalatest % Test,
  )))
  .dependsOn(beautyqSearchRepositories)

lazy val appHttp = project
  .in(file("app-http"))
  .settings(name := "app-http")
  .pipe(lightweightSettings(Seq(
    Deps.circeGeneric,
    Deps.zio,
    Deps.zioCats,
    Deps.http4sDsl,
    Deps.http4sCirce,
    Deps.tapirHttp4sServer,
    Deps.tapirJsonCirce,
    Deps.scalatest % Test,
  )))
  .dependsOn(beautyqSearchWiring, appServices)

lazy val `leaderboard-app-shell` = project
  .pipe(appSettings(Seq(Deps.zio, Deps.zioCats, Deps.tapirHttp4sServer, Deps.tapirJsonCirce)))
  .dependsOn(beautyqSearchWiring, appHttp, appServices)
  .dependsOn(beautyqSearchGen2Materialization % "test->compile")

lazy val `graal-resources` = project
  .in(file("graal-resources"))
  .settings(Compile / resourceDirectory := baseDirectory.value)

lazy val `distage-example` = project
  .in(file("."))
  .aggregate(
    `leaderboard-core`,
    `search-core`,
    `search-elasticsearch`,
    `search-qdrant`,
    repoCore,
    searchContractCore,
    beautyqModel,
    beautyqSearchContract,
    beautyqSearchRepositories,
    beautyqSearchMaterialization,
    beautyqSearchWiring,
    searchGen2Contract,
    searchGen2Core,
    searchGen2Elasticsearch,
    searchGen2Qdrant,
    beautyqSearchGen2Contract,
    beautyqSearchGen2Materialization,
    beautyqSearchGen2Wiring,
    beautyqSearchGen2Eval,
    appHttp,
    appServices,
    `leaderboard-app-shell`,
    `graal-resources`,
  )
  .enablePlugins(GraalVMNativeImagePlugin, UniversalPlugin)

def lightweightSettings(additionalDeps: Seq[ModuleID])(project: Project): Project = {
  project
    .settings(
      libraryDependencies ++= additionalDeps,
      libraryDependencies ++= {
        if (scalaVersion.value.startsWith("2")) {
          Seq(compilerPlugin(Deps.kindProjector))
        } else {
          Seq.empty
        }
      },
      scalacOptions --= Seq("-Xfatal-warnings", "-Ykind-projector", "-Wnonunit-statement"),
      scalacOptions ++= {
        if (scalaVersion.value.startsWith("2")) {
          Seq(
            "-Xsource:3",
            "-P:kind-projector:underscore-placeholders",
            "-Wmacros:after",
          )
        } else {
          Seq(
            "-Ykind-projector:underscores",
            "-Yretain-trees",
          )
        }
      },
      scalacOptions ++= Seq(
        s"-Xmacro-settings:product-name=${name.value}",
        s"-Xmacro-settings:product-version=${version.value}",
        s"-Xmacro-settings:product-group=${organization.value}",
        s"-Xmacro-settings:scala-version=${scalaVersion.value}",
        s"-Xmacro-settings:scala-versions=${crossScalaVersions.value.mkString(":")}",
        s"-Xmacro-settings:sbt-version=${sbtVersion.value}",
        s"-Xmacro-settings:git-repo-clean=${git.gitUncommittedChanges.value}",
        s"-Xmacro-settings:git-branch=${git.gitCurrentBranch.value}",
        s"-Xmacro-settings:git-described-version=${git.gitDescribedVersion.value.getOrElse("")}",
        s"-Xmacro-settings:git-head-commit=${git.gitHeadCommit.value.getOrElse("")}",
      ),
    )
}

def appSettings(additionalDeps: Seq[ModuleID])(project: Project): Project = {
  lightweightSettings(Deps.CoreDeps ++ additionalDeps)(project)
    .settings(
      GraalVMNativeImage / mainClass := Some("leaderboard.GenericLauncher"),
      graalVMNativeImageOptions ++= Seq(
        "--no-fallback",
        "-H:+ReportExceptionStackTraces",
        "--report-unsupported-elements-at-runtime",
        "--enable-https",
        "--enable-http",
        "-J-Xmx8G",
      ),
      graalVMNativeImageGraalVersion := Some("ol9-java17-22.3.1"),
      run / fork                     := true,
    )
    .dependsOn(`graal-resources`)
    .enablePlugins(GraalVMNativeImagePlugin, UniversalPlugin)
}

// for quick experiments with distage snapshots
ThisBuild / resolvers ++= Resolver.sonatypeOssRepos("snapshots")
