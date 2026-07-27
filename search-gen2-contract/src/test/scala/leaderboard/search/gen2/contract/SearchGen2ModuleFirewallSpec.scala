package leaderboard.search.gen2.contract

import org.scalatest.exceptions.TestFailedException
import org.scalatest.wordspec.AnyWordSpec

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import scala.annotation.tailrec
import scala.jdk.CollectionConverters.*
import scala.util.Using

final class SearchGen2ModuleFirewallSpec extends AnyWordSpec {

  private final case class ModuleNode(
    displayName: String,
    sbtId: String,
    packagePrefix: String,
    dependsOn: List[String],
    additionalTestPackagePrefixes: List[String] = Nil,
  )

  private val leaderboardCoreNode           = ModuleNode("leaderboard-core", "`leaderboard-core`", "", Nil)
  private val repoCoreNode                  = ModuleNode("repo-core", "repoCore", "", Nil)
  private val beautyqModelNode              = ModuleNode("beautyq-model", "beautyqModel", "", Nil)
  private val beautyqSearchRepositoriesNode = ModuleNode("beautyq-search-repositories", "beautyqSearchRepositories", "", Nil)

  private val searchGen2ContractNode =
    ModuleNode("search-gen2-contract", "searchGen2Contract", "leaderboard.search.gen2.contract", List("leaderboard-core"))
  private val searchGen2CoreNode =
    ModuleNode("search-gen2-core", "searchGen2Core", "leaderboard.search.gen2.core", List("search-gen2-contract"))
  private val searchGen2TransportNode =
    ModuleNode("search-gen2-transport", "searchGen2Transport", "leaderboard.search.gen2.transport", Nil)
  private val searchGen2ElasticsearchNode =
    ModuleNode(
      "search-gen2-elasticsearch",
      "searchGen2Elasticsearch",
      "leaderboard.search.gen2.elasticsearch",
      List("search-gen2-contract", "search-gen2-core", "search-gen2-transport"),
    )
  private val searchGen2QdrantNode =
    ModuleNode(
      "search-gen2-qdrant",
      "searchGen2Qdrant",
      "leaderboard.search.gen2.qdrant",
      List("search-gen2-contract", "search-gen2-core", "search-gen2-transport"),
    )
  private val searchGen2EvalNode =
    ModuleNode(
      "search-gen2-eval",
      "searchGen2Eval",
      "leaderboard.search.gen2.eval",
      Nil,
    )
  private val beautyqSearchGen2ContractNode =
    ModuleNode(
      "beautyq-search-gen2-contract",
      "beautyqSearchGen2Contract",
      "leaderboard.search.beautyq.gen2.contract",
      List("search-gen2-contract", "repo-core", "beautyq-model"),
    )
  private val beautyqSearchGen2MaterializationNode =
    ModuleNode(
      "beautyq-search-gen2-materialization",
      "beautyqSearchGen2Materialization",
      "leaderboard.search.beautyq.gen2.materialization",
      List("beautyq-search-gen2-contract", "search-gen2-core", "beautyq-search-repositories", "repo-core", "beautyq-model"),
    )
  private val beautyqSearchGen2WiringNode =
    ModuleNode(
      "beautyq-search-gen2-wiring",
      "beautyqSearchGen2Wiring",
      "leaderboard.search.beautyq.gen2.wiring",
      List(
        "beautyq-search-gen2-contract",
        "beautyq-search-gen2-materialization",
        "search-gen2-core",
        "search-gen2-elasticsearch",
        "search-gen2-qdrant",
      ),
      additionalTestPackagePrefixes = List("leaderboard.search.beautyq.gen2.boundary"),
    )
  private val beautyqSearchGen2EvalNode =
    ModuleNode(
      "beautyq-search-gen2-eval",
      "beautyqSearchGen2Eval",
      "leaderboard.search.beautyq.gen2.eval",
      List("search-gen2-eval", "beautyq-search-gen2-wiring"),
    )

  // Accepted module order from the Gen2 technical specification. This one ordered list is the
  // sole source for project checks, package checks, visual rendering, the cycle proof, and the
  // aggregate proof. Dependency order here is a documentation/rendering convenience only - it is
  // never compared against build.sbt as a sequence; see checkDependsOn.
  private val gen2Modules: List[ModuleNode] = List(
    searchGen2ContractNode,
    searchGen2CoreNode,
    searchGen2TransportNode,
    searchGen2ElasticsearchNode,
    searchGen2QdrantNode,
    searchGen2EvalNode,
    beautyqSearchGen2ContractNode,
    beautyqSearchGen2MaterializationNode,
    beautyqSearchGen2WiringNode,
    beautyqSearchGen2EvalNode,
  )

  private val sharedFoundationModules: List[ModuleNode] =
    List(leaderboardCoreNode, repoCoreNode, beautyqModelNode, beautyqSearchRepositoriesNode)

  private val allModules: List[ModuleNode] = gen2Modules ++ sharedFoundationModules

  // Every dependsOn entry above names another node in allModules; the lookup below is total by
  // construction of that fixture list, not by runtime discovery - sbtIdOf still fails with a clear,
  // explained message rather than throwing an unsafe Map.apply if that invariant is ever broken.
  private val moduleByDisplayName: Map[String, ModuleNode] = allModules.map(node => node.displayName -> node).toMap

  private val gen2DependencyGraph: Map[String, List[String]] = allModules.map(node => node.displayName -> node.dependsOn).toMap

  private val servingModules: List[ModuleNode] = gen2Modules.filterNot(_.displayName == beautyqSearchGen2EvalNode.displayName)

  private val genericModuleDirs: List[String] =
    List("search-gen2-contract", "search-gen2-core", "search-gen2-transport", "search-gen2-elasticsearch", "search-gen2-qdrant", "search-gen2-eval")

  private val forbiddenGen1SbtProjectTokens: List[String] = List(
    "search-core",
    "search-elasticsearch",
    "search-qdrant",
    "beautyqSearchContract",
    "beautyqSearchMaterialization",
    "beautyqSearchWiring",
  )

  private val gen1ImportFirewallForbidden: List[String] = List(
    "leaderboard.search.dsl",
    "leaderboard.search.contract",
    "leaderboard.search.document",
    "leaderboard.search.elasticsearch",
    "leaderboard.search.qdrant",
    "leaderboard.search.semantic",
    "leaderboard.search.parser",
    "leaderboard.search.interpreter",
    "leaderboard.search.hybrid",
    "leaderboard.search.startup",
    "leaderboard.search.eval",
    "leaderboard.search.beautyq.contract",
    "leaderboard.api",
    "leaderboard.http",
    "leaderboard.plugins",
    "leaderboard.config",
    "leaderboard.sql",
    "leaderboard.services",
  )

  private val genericDomainForbiddenImports: List[String] = List(
    "leaderboard.repo",
    "leaderboard.search.beautyq.gen2",
    "leaderboard.sql",
    "doobie",
  )

  private val genericDomainForbiddenSourceText: List[String] = List("BeautyQ", "beautyq")

  private val evalPackagePrefix = "leaderboard.search.beautyq.gen2.eval"

  // Brick 3 intentionally owns one consistent PostgreSQL snapshot transaction through
  // `SQL.readOnlyRepeatableRead`, which requires importing the exact infrastructure type
  // `leaderboard.sql.SQL` - permitted only in beautyq-search-gen2-materialization (the one module the
  // accepted project graph already has depending on beautyq-search-repositories), and only for this one
  // exact type, never the broader `leaderboard.sql` package.
  private val beautyqMaterializationAllowedInfrastructureImports: List[String] = List(
    "leaderboard.sql.SQL"
  )

  private def allowedGen1ImportPatterns(node: ModuleNode): List[String] =
    if (node.displayName == beautyqSearchGen2MaterializationNode.displayName) {
      beautyqMaterializationAllowedInfrastructureImports
    } else {
      Nil
    }

  "Gen2 project graph" should {
    "prove every Gen2 sbt project block matches the accepted file location and name" in {
      val violations = gen2Modules.flatMap { node =>
        val block            = buildBlock(node.sbtId)
        val expectedLocation = s".in(file(\"${node.displayName}\"))"
        val expectedName     = s"name := \"${node.displayName}\""

        val locationViolation = if (block.contains(expectedLocation)) Nil else List(s"${node.sbtId}: expected $expectedLocation")
        val nameViolation     = if (block.contains(expectedName)) Nil else List(s"${node.sbtId}: expected $expectedName")

        locationViolation ++ nameViolation
      }

      assertNoViolations("Gen2 build.sbt project location/name violations", violations)
    }

    "prove every Gen2 sbt project block has the exact accepted dependency set, with no duplicate or forbidden Gen1 dependency" in {
      val violations = gen2Modules.flatMap { node =>
        val block = buildBlock(node.sbtId)
        val check = checkDependsOn(node, block)

        val dependsOnViolation =
          if (check.isValid) {
            Nil
          } else {
            List(
              s"${node.sbtId}: dependsOn mismatch - expected [${check.expected.mkString(", ")}], " +
                s"actual [${check.actual.mkString(", ")}], missing [${check.missing.mkString(", ")}], " +
                s"unexpected [${check.unexpected.mkString(", ")}], duplicates [${check.duplicates.mkString(", ")}]"
            )
          }

        val forbiddenViolations = forbiddenGen1SbtProjectTokens.filter(block.contains).map { token =>
          s"${node.sbtId}: build.sbt block unexpectedly references forbidden Gen1 project token '$token'"
        }

        dependsOnViolation ++ forbiddenViolations
      }

      assertNoViolations("Gen2 build.sbt dependsOn violations", violations)
    }

    "prove all ten Gen2 projects are present in the root aggregate" in {
      val aggregateBlock = buildBlock("`distage-example`")
      val violations = gen2Modules.filterNot(node => aggregateBlock.contains(node.sbtId)).map { node =>
        s"${node.sbtId} is missing from the `distage-example` aggregate"
      }

      assertNoViolations("Gen2 aggregate membership violations", violations)
    }

    "prove all retained shared project definitions are present in the root aggregate" in {
      val aggregateBlock = buildBlock("`distage-example`")
      val retainedShared = List("repoCore", "beautyqModel", "beautyqSearchRepositories")
      val violations = retainedShared.filterNot(token => aggregateBlock.contains(token)).map { token =>
        s"$token is missing from the `distage-example` aggregate"
      }

      assertNoViolations("Retained shared project aggregate membership violations", violations)
    }

    "prove the accepted Gen2 module graph is acyclic" in {
      assert(isAcyclic(gen2DependencyGraph))
    }

    "reject a deliberately cyclic synthetic graph" in {
      val cyclicGraph = Map("synthetic-a" -> List("synthetic-b"), "synthetic-b" -> List("synthetic-a"))
      assert(!isAcyclic(cyclicGraph))
    }
  }

  // Acceptance is semantic-edge-set equality, not sequence equality: argument order inside
  // `.dependsOn(...)` is not part of the architecture. checkDependsOn accepts any ordering of the
  // expected unique dependencies and rejects a missing, unexpected, or duplicated one.
  "Gen2 dependsOn edge set" should {
    "accept the exact expected dependencies" in {
      val check = checkDependsOn(searchGen2ElasticsearchNode, "lazy val x = project.dependsOn(searchGen2Contract, searchGen2Core, searchGen2Transport)")
      assert(check.isValid)
    }

    "accept reordered expected dependencies" in {
      val check = checkDependsOn(searchGen2ElasticsearchNode, "lazy val x = project.dependsOn(searchGen2Transport, searchGen2Core, searchGen2Contract)")
      assert(check.isValid)
    }

    "reject a missing dependency" in {
      val check = checkDependsOn(searchGen2ElasticsearchNode, "lazy val x = project.dependsOn(searchGen2Contract)")
      assert(!check.isValid)
      assert(check.missing == List("searchGen2Core", "searchGen2Transport"))
    }

    "reject an unexpected dependency" in {
      val check = checkDependsOn(searchGen2ElasticsearchNode, "lazy val x = project.dependsOn(searchGen2Contract, searchGen2Core, searchGen2Transport, appHttp)")
      assert(!check.isValid)
      assert(check.unexpected == List("appHttp"))
    }

    "reject a duplicate dependency within one dependsOn call" in {
      val check = checkDependsOn(searchGen2ElasticsearchNode, "lazy val x = project.dependsOn(searchGen2Contract, searchGen2Core, searchGen2Transport, searchGen2Core)")
      assert(!check.isValid)
      assert(check.duplicates == List("searchGen2Core"))
    }

    "reject a duplicate dependency added by a second dependsOn call" in {
      val block =
        """lazy val x = project
          |  .dependsOn(searchGen2Contract, searchGen2Core, searchGen2Transport)
          |  .dependsOn(searchGen2Core)
          |""".stripMargin

      val check = checkDependsOn(searchGen2ElasticsearchNode, block)
      assert(!check.isValid)
      assert(check.duplicates == List("searchGen2Core"))
    }

    "reject a forbidden Gen1 dependency token" in {
      val block = "lazy val x = project.dependsOn(searchGen2Contract, searchGen2Core, beautyqSearchWiring)"
      assert(forbiddenGen1SbtProjectTokens.exists(block.contains))
    }

    "detect a dependency added by a whitespace-separated second dependsOn call" in {
      val block =
        """lazy val x = project
          |  .dependsOn(searchGen2Contract)
          |  .dependsOn (repoCore)
          |""".stripMargin

      assert(extractDependsOnIdentifiers(block) == List("searchGen2Contract", "repoCore"))
    }

    "recognize .dependsOn followed by a newline before the opening parenthesis" in {
      val block =
        """lazy val x = project
          |  .dependsOn
          |  (
          |    repoCore,
          |  )
          |""".stripMargin

      assert(extractDependsOnIdentifiers(block) == List("repoCore"))
    }

    "ignore a longer identifier that merely starts with dependsOn" in {
      val block = "lazy val x = project.dependsOnSomethingElse(repoCore)"
      assert(extractDependsOnIdentifiers(block) == Nil)
    }

    "fail closed on unsupported comment-separated dependsOn syntax" in {
      val block = "lazy val x = project.dependsOn /* comment */ (repoCore)"
      intercept[TestFailedException] {
        extractDependsOnIdentifiers(block)
      }
    }

    "fail closed on an unmatched dependsOn invocation" in {
      val block = "lazy val x = project.dependsOn(repoCore"
      intercept[TestFailedException] {
        extractDependsOnIdentifiers(block)
      }
    }
  }

  "Gen2 visual dependency tree" should {
    "render an exact human-readable dependency tree for the ten Gen2 modules" in {
      val expected =
        """search-gen2-contract
          |└── leaderboard-core
          |
          |search-gen2-core
          |└── search-gen2-contract
          |
          |search-gen2-transport
          |
          |search-gen2-elasticsearch
          |├── search-gen2-contract
          |├── search-gen2-core
          |└── search-gen2-transport
          |
          |search-gen2-qdrant
          |├── search-gen2-contract
          |├── search-gen2-core
          |└── search-gen2-transport
          |
          |search-gen2-eval
          |
          |beautyq-search-gen2-contract
          |├── search-gen2-contract
          |├── repo-core
          |└── beautyq-model
          |
          |beautyq-search-gen2-materialization
          |├── beautyq-search-gen2-contract
          |├── search-gen2-core
          |├── beautyq-search-repositories
          |├── repo-core
          |└── beautyq-model
          |
          |beautyq-search-gen2-wiring
          |├── beautyq-search-gen2-contract
          |├── beautyq-search-gen2-materialization
          |├── search-gen2-core
          |├── search-gen2-elasticsearch
          |└── search-gen2-qdrant
          |
          |beautyq-search-gen2-eval
          |├── search-gen2-eval
          |└── beautyq-search-gen2-wiring""".stripMargin

      assert(renderGraph(gen2Modules) == expected)
    }
  }

  "Gen2 package and source layout" should {
    "prove every Gen2 module has a real main source root and consistent main/test package prefixes" in {
      val violations = gen2Modules.flatMap { node =>
        val mainRoot = repoRoot.resolve(s"${node.displayName}/src/main/scala")
        val mainRootViolation =
          if (Files.isDirectory(mainRoot)) Nil
          else List(s"${node.displayName}: expected main source directory src/main/scala to exist")

        val mainViolations = scalaFilesUnder(s"${node.displayName}/src/main/scala").flatMap(path => packagePrefixViolation(path, node.packagePrefix))
        val testViolations = scalaFilesUnder(s"${node.displayName}/src/test/scala").flatMap { path =>
          packagePrefixViolation(path, node.packagePrefix, node.additionalTestPackagePrefixes)
        }

        mainRootViolation ++ mainViolations ++ testViolations
      }

      assertNoViolations("Gen2 package/source layout violations", violations)
    }
  }

  "Gen1 import firewall" should {
    "keep actual Gen2 main and test sources free of Gen1 search/app-layer imports" in {
      val violations = gen2Modules.flatMap { node =>
        val files = scalaFilesUnder(s"${node.displayName}/src/main/scala") ++ scalaFilesUnder(s"${node.displayName}/src/test/scala")
        files.flatMap { path =>
          forbiddenImportViolations(relative(path), read(path), gen1ImportFirewallForbidden, allowedPatterns = allowedGen1ImportPatterns(node))
        }
      }

      assertNoViolations("Gen1 import firewall violations", violations)
    }
  }

  "Generic/domain firewall" should {
    "keep the six generic Gen2 modules free of leaderboard.repo imports, BeautyQ Gen2 imports, and BeautyQ source text" in {
      val violations = genericModuleDirs.flatMap { moduleDir =>
        val files = scalaFilesUnder(s"$moduleDir/src/main/scala")

        val importViolations = files.flatMap { path =>
          forbiddenImportViolations(relative(path), read(path), genericDomainForbiddenImports, allowedPatterns = Nil)
        }
        val sourceViolations = files.flatMap { path =>
          genericDomainForbiddenSourceText.filter(read(path).contains).map { token =>
            s"${relative(path)}: forbidden BeautyQ-specific source text: $token"
          }
        }

        importViolations ++ sourceViolations
      }

      assertNoViolations("Gen2 generic/domain firewall violations", violations)
    }

    // Import-only, not the source-text scan above: test sources legitimately document neutrality by
    // naming BeautyQ in comments (e.g. "no BeautyQ type appears in this file", already established by
    // SearchMaterializerSpec/SearchProjectedDocumentsFingerprintSpec/CanonicalSnapshotSpec/
    // LibraryTracerDomainSpec before this check existed) - that convention stays legitimate. An actual
    // import is a real dependency/coupling signal and is never legitimate in a generic module, main or
    // test, so this closes the one gap the main-only check above left open: nothing previously proved a
    // second/tracer domain living in a generic module's own test tree stays free of the same forbidden
    // imports the module's production code is held to.
    "keep the six generic Gen2 modules' test sources free of the same forbidden imports as their main sources" in {
      val filesByModule = genericModuleDirs.map(moduleDir => moduleDir -> scalaFilesUnder(s"$moduleDir/src/test/scala"))

      // A vacuously green check (no test files found) would prove nothing; at least one generic module
      // must actually have test sources for this assertion to have teeth.
      assert(filesByModule.exists { case (_, files) => files.nonEmpty })

      val violations = filesByModule.flatMap { case (_, files) =>
        files.flatMap { path =>
          forbiddenImportViolations(relative(path), read(path), genericDomainForbiddenImports, allowedPatterns = Nil)
        }
      }

      assertNoViolations("Gen2 generic/domain test-source firewall violations", violations)
    }
  }

  // The generic-import scan above already proves search-gen2-core's *source* is BeautyQ-free; these two
  // direct edge-set assertions additionally pin the *project-graph* facts a reader would otherwise have
  // to infer from the dependsOn fixtures above - the reusable materialization kernel is a real dependency
  // of BeautyQ materialization, and the kernel itself declares no BeautyQ project dependency.
  "Reusable materialization kernel dependency" should {
    "prove beautyq-search-gen2-materialization depends on search-gen2-core" in {
      assert(beautyqSearchGen2MaterializationNode.dependsOn.contains(searchGen2CoreNode.displayName))
    }

    "prove search-gen2-core has no BeautyQ project dependency" in {
      assert(!searchGen2CoreNode.dependsOn.exists(_.toLowerCase.contains("beautyq")))
    }
  }

  "Eval firewall" should {
    "keep every serving Gen2 module free of eval imports and eval build dependencies" in {
      val importViolations = servingModules.flatMap { node =>
        val files = scalaFilesUnder(s"${node.displayName}/src/main/scala") ++ scalaFilesUnder(s"${node.displayName}/src/test/scala")
        files.flatMap { path =>
          forbiddenImportViolations(relative(path), read(path), List(evalPackagePrefix), allowedPatterns = Nil)
        }
      }

      val buildViolations = servingModules
        .filter(node => extractDependsOnIdentifiers(buildBlock(node.sbtId)).contains(beautyqSearchGen2EvalNode.sbtId))
        .map(node => s"${node.sbtId}: build.sbt block unexpectedly depends on ${beautyqSearchGen2EvalNode.sbtId}")

      assertNoViolations("Gen2 eval firewall violations", importViolations ++ buildViolations)
    }

    "keep leaderboard-app-shell production sources free of eval imports and restrict the eval build edge to test scope" in {
      val mainSourceFiles = scalaFilesUnder("leaderboard-app-shell/src/main/scala")
      assert(mainSourceFiles.nonEmpty, "expected leaderboard-app-shell/src/main/scala to contain at least one file")
      val importViolations = mainSourceFiles.flatMap { path =>
        forbiddenImportViolations(relative(path), read(path), List(evalPackagePrefix), allowedPatterns = Nil)
      }

      val appShellBlock = buildBlock("`leaderboard-app-shell`")
      val evalEdges = extractDependsOnMappings(appShellBlock, beautyqSearchGen2EvalNode.sbtId)
      val badEdgeViolations = evalEdges.collect {
        case mapping if mapping.startsWith("compile->") =>
          s"leaderboard-app-shell: eval build edge uses non-test scope mapping '$mapping'"
      }
      val noEdgeViolation =
        if (evalEdges.isEmpty)
          List("leaderboard-app-shell: expected test->test build edge to ${beautyqSearchGen2EvalNode.sbtId}".replace("${beautyqSearchGen2EvalNode.sbtId}", beautyqSearchGen2EvalNode.sbtId))
        else Nil

      assertNoViolations(
        "leaderboard-app-shell eval firewall violations",
        importViolations ++ badEdgeViolations ++ noEdgeViolation,
      )
    }
  }

  "Gen2 diagnostic import scanner" should {
    "prove obvious forbidden imports/source text are rejected and accepted exceptions pass (diagnostic scan, not exhaustive)" in {
      val rejectedDsl =
        forbiddenImportViolations("synthetic/Gen2Generic.scala", "import leaderboard.search.dsl.SearchField", gen1ImportFirewallForbidden, Nil)
      assert(rejectedDsl.nonEmpty)

      val rejectedGroupedGen1 = forbiddenImportViolations(
        "synthetic/Gen2Generic.scala",
        "import leaderboard.search.elasticsearch.{ElasticsearchClient, ElasticsearchJsonClient}",
        gen1ImportFirewallForbidden,
        Nil,
      )
      assert(rejectedGroupedGen1.nonEmpty)

      val multilineGroupedGen1 =
        """import leaderboard.search.elasticsearch.{
          |  ElasticsearchClient,
          |  ElasticsearchJsonClient,
          |}""".stripMargin
      val rejectedMultilineGroupedGen1 =
        forbiddenImportViolations("synthetic/Gen2Generic.scala", multilineGroupedGen1, gen1ImportFirewallForbidden, Nil)
      assert(rejectedMultilineGroupedGen1.nonEmpty)

      val rejectedRootQualified = forbiddenImportViolations(
        "synthetic/Gen2Generic.scala",
        "import _root_.leaderboard.search.dsl.SearchField",
        gen1ImportFirewallForbidden,
        Nil,
      )
      assert(rejectedRootQualified.nonEmpty)

      val acceptedGen2Contract = forbiddenImportViolations(
        "synthetic/Gen2Generic.scala",
        "import leaderboard.search.gen2.contract.SomeContract",
        gen1ImportFirewallForbidden,
        Nil,
      )
      assert(acceptedGen2Contract.isEmpty)

      val acceptedSimilarlyPrefixed = forbiddenImportViolations(
        "synthetic/Gen2Generic.scala",
        "import leaderboard.search.dslish.SomethingElse",
        gen1ImportFirewallForbidden,
        Nil,
      )
      assert(acceptedSimilarlyPrefixed.isEmpty)

      val rejectedBeautyQGen2InGeneric = forbiddenImportViolations(
        "synthetic/Gen2Generic.scala",
        "import leaderboard.search.beautyq.gen2.contract.BeautyQSearchDeclarations",
        genericDomainForbiddenImports,
        Nil,
      )
      assert(rejectedBeautyQGen2InGeneric.nonEmpty)

      val rejectedSourceText = genericDomainForbiddenSourceText.filter("final class BeautyQGenericLeak".contains)
      assert(rejectedSourceText.nonEmpty)

      val acceptedRepoInBeautyQContract = forbiddenImportViolations(
        "synthetic/BeautyqSearchGen2Contract.scala",
        "import leaderboard.repo.GraphLoading",
        gen1ImportFirewallForbidden,
        Nil,
      )
      assert(acceptedRepoInBeautyQContract.isEmpty)

      val rejectedEvalInServing = forbiddenImportViolations(
        "synthetic/BeautyqSearchGen2Wiring.scala",
        "import leaderboard.search.beautyq.gen2.eval.NoHarmReport",
        List(evalPackagePrefix),
        Nil,
      )
      assert(rejectedEvalInServing.nonEmpty)

      val acceptedServingInEval = forbiddenImportViolations(
        "synthetic/BeautyqSearchGen2Eval.scala",
        "import leaderboard.search.beautyq.gen2.wiring.BeautyQSearchGen2ResourceNames",
        List(evalPackagePrefix),
        Nil,
      )
      assert(acceptedServingInEval.isEmpty)

      // The one exact infrastructure import Brick 3 needs for its consistent-snapshot transaction is
      // accepted only in beautyq-search-gen2-materialization, still forbidden everywhere else, and the
      // allow-list never widens to the broader `leaderboard.sql` package.
      val acceptedMaterializationSql = forbiddenImportViolations(
        "synthetic/BeautyQSearchSnapshotSource.scala",
        "import leaderboard.sql.SQL",
        gen1ImportFirewallForbidden,
        beautyqMaterializationAllowedInfrastructureImports,
      )
      assert(acceptedMaterializationSql.isEmpty)

      val rejectedSqlOutsideMaterialization = forbiddenImportViolations(
        "synthetic/SearchGen2Core.scala",
        "import leaderboard.sql.SQL",
        gen1ImportFirewallForbidden,
        Nil,
      )
      assert(rejectedSqlOutsideMaterialization.nonEmpty)

      val rejectedOtherSqlInfrastructure = forbiddenImportViolations(
        "synthetic/BeautyQSearchSnapshotSource.scala",
        "import leaderboard.sql.SomeOtherInfrastructure",
        gen1ImportFirewallForbidden,
        beautyqMaterializationAllowedInfrastructureImports,
      )
      assert(rejectedOtherSqlInfrastructure.nonEmpty)

      assert(!beautyqMaterializationAllowedInfrastructureImports.contains("leaderboard.sql"))
      assert(beautyqMaterializationAllowedInfrastructureImports == List("leaderboard.sql.SQL"))
    }

    "classify a trivial wildcard import by its package target" in {
      val starImport  = forbiddenImportViolations("synthetic/Gen2Generic.scala", "import leaderboard.search.dsl.*", gen1ImportFirewallForbidden, Nil)
      val underImport = forbiddenImportViolations("synthetic/Gen2Generic.scala", "import leaderboard.search.dsl._", gen1ImportFirewallForbidden, Nil)

      assert(starImport.nonEmpty)
      assert(underImport.nonEmpty)
    }
  }

  private def repoRoot: Path = {
    @tailrec
    def loop(path: Path): Path =
      if (Files.isRegularFile(path.resolve("build.sbt"))) {
        path
      } else {
        path.getParent match {
          case parent: Path => loop(parent)
          case null         => fail(s"Could not find repo root from ${Paths.get("").toAbsolutePath}; expected build.sbt")
        }
      }

    loop(Paths.get("").toAbsolutePath)
  }

  private def relative(path: Path): String =
    repoRoot.relativize(path).toString.replace('\\', '/')

  private def read(path: Path): String =
    Files.readString(path, StandardCharsets.UTF_8)

  private def scalaFilesUnder(relativeDir: String): List[Path] = {
    val root = repoRoot.resolve(relativeDir)
    if (!Files.isDirectory(root)) {
      Nil
    } else {
      Using
        .resource(Files.walk(root)) { stream =>
          stream.iterator.asScala.toList.filter { path =>
            val normalized = path.toString.replace('\\', '/')
            Files.isRegularFile(path) && normalized.endsWith(".scala") && !normalized.contains("/target/")
          }
        }
        .sortBy(_.toString)
    }
  }

  private def packagePrefixViolation(path: Path, expectedPrefix: String, additionalPrefixes: List[String] = Nil): Option[String] = {
    val declaredPackage = read(path).linesIterator.map(_.trim).find(_.startsWith("package ")).map(_.stripPrefix("package ").trim)

    val acceptedPrefixes = expectedPrefix :: additionalPrefixes
    declaredPackage match {
      case Some(pkg) if acceptedPrefixes.exists(prefix => pkg == prefix || pkg.startsWith(prefix + ".")) => None
      case Some(pkg) => Some(s"${relative(path)}: expected package prefix '$expectedPrefix' but found '$pkg'")
      case None      => Some(s"${relative(path)}: expected a package declaration with prefix '$expectedPrefix'")
    }
  }

  private def buildBlock(projectId: String): String = {
    val lines      = read(repoRoot.resolve("build.sbt")).linesIterator.toList
    val startIndex = lines.indexWhere(_.trim.startsWith(s"lazy val $projectId "))

    if (startIndex < 0) {
      fail(s"Could not find build.sbt block for lazy val $projectId")
    }

    val remaining  = lines.drop(startIndex + 1)
    val nextIndex  = remaining.indexWhere(_.trim.startsWith("lazy val "))
    val blockLines =
      if (nextIndex < 0) lines.drop(startIndex)
      else lines.slice(startIndex, startIndex + 1 + nextIndex)

    blockLines.mkString("\n")
  }

  private def sbtIdOf(displayName: String): String =
    moduleByDisplayName.get(displayName) match {
      case Some(node) => node.sbtId
      case None       => fail(s"No Gen2 module fixture named '$displayName' - this is a fixture bug, not a source violation")
    }

  private final case class DependsOnCheck(
    expected: List[String],
    actual: List[String],
    missing: List[String],
    unexpected: List[String],
    duplicates: List[String],
  ) {
    def isValid: Boolean = missing.isEmpty && unexpected.isEmpty && duplicates.isEmpty
  }

  private def duplicatesInOrder(items: List[String]): List[String] = {
    @tailrec
    def loop(remaining: List[String], seen: Set[String], reported: Set[String], acc: List[String]): List[String] =
      remaining match {
        case Nil => acc
        case head :: tail =>
          if (!seen.contains(head)) loop(tail, seen + head, reported, acc)
          else if (!reported.contains(head)) loop(tail, seen, reported + head, acc :+ head)
          else loop(tail, seen, reported, acc)
      }

    loop(items, Set.empty, Set.empty, Nil)
  }

  // Acceptance is semantic-edge-set equality plus duplicate-freedom, never sequence equality:
  // missing.isEmpty && unexpected.isEmpty together mean actualSet == expectedSet; duplicates.isEmpty
  // means no repeated edge across one or more `.dependsOn(...)` calls. Argument order is never part
  // of this check.
  private def checkDependsOn(node: ModuleNode, block: String): DependsOnCheck = {
    val expected    = node.dependsOn.map(sbtIdOf)
    val actual      = extractDependsOnIdentifiers(block)
    val expectedSet = expected.toSet
    val actualSet   = actual.toSet

    DependsOnCheck(
      expected   = expected,
      actual     = actual,
      missing    = expected.filterNot(actualSet.contains),
      unexpected = actual.filterNot(expectedSet.contains),
      duplicates = duplicatesInOrder(actual),
    )
  }

  // Finds the index of the ')' matching the '(' located at openIndex.
  private def findMatchingCloseParen(text: String, openIndex: Int): Option[Int] = {
    @tailrec
    def loop(index: Int, depth: Int): Option[Int] =
      if (index >= text.length) {
        None
      } else {
        text.charAt(index) match {
          case '(' => loop(index + 1, depth + 1)
          case ')' => if (depth == 1) Some(index) else loop(index + 1, depth - 1)
          case _   => loop(index + 1, depth)
        }
      }

    loop(openIndex, 0)
  }

  // Splits a `.dependsOn(...)` argument list on top-level commas, respecting parenthesis nesting.
  private def splitTopLevelArgs(text: String): List[String] = {
    @tailrec
    def loop(index: Int, depth: Int, currentStart: Int, acc: List[String]): List[String] =
      if (index >= text.length) {
        acc :+ text.substring(currentStart)
      } else {
        text.charAt(index) match {
          case '('                => loop(index + 1, depth + 1, currentStart, acc)
          case ')'                => loop(index + 1, depth - 1, currentStart, acc)
          case ',' if depth == 0  => loop(index + 1, depth, index + 1, acc :+ text.substring(currentStart, index))
          case _                  => loop(index + 1, depth, currentStart, acc)
        }
      }

    loop(0, 0, 0, Nil).map(_.trim).filter(_.nonEmpty)
  }

  // The sbt project DAG is the authoritative dependency firewall (Gen2 cannot compile against a
  // forbidden Gen1 project it does not depend on), so this extraction stays a small, local
  // paren-balanced scan of build.sbt - not a general Scala parser. It collects every
  // `.dependsOn(...)` call in the block (a second or later chain silently appends more edges, so all
  // are collected), tolerates legal whitespace/newlines between `.dependsOn` and its opening
  // parenthesis so a whitespace-separated chain cannot silently escape the checked edge set, ignores
  // a longer identifier that merely starts with `.dependsOn` (e.g. `.dependsOnSomethingElse(...)`),
  // and fails the test explicitly - rather than silently continuing with a partial dependency list -
  // both on unmatched parentheses and on any other unsupported syntax between a genuine `.dependsOn`
  // token and its opening parenthesis (such as a comment).
  private def extractDependsOnIdentifiers(block: String): List[String] = {
    val marker = ".dependsOn"

    @tailrec
    def loop(fromIndex: Int, acc: List[String]): List[String] = {
      val markerIndex = block.indexOf(marker, fromIndex)
      if (markerIndex < 0) {
        acc
      } else {
        val afterMarker    = markerIndex + marker.length
        val isLongerSymbol = afterMarker < block.length && isIdentifierChar(block.charAt(afterMarker))

        if (isLongerSymbol) {
          loop(afterMarker, acc)
        } else {
          val openIndex = skipWhitespace(block, afterMarker)
          if (openIndex < block.length && block.charAt(openIndex) == '(') {
            findMatchingCloseParen(block, openIndex) match {
              case Some(closeIndex) =>
                val args = block.substring(openIndex + 1, closeIndex)
                val dependencyArguments = splitTopLevelArgs(args).map { argument =>
                  // A test-only configuration mapping is still the same project edge for the
                  // module firewall; the mapping controls classpath visibility, not the serving DAG.
                  argument.trim.takeWhile(character => character.isLetterOrDigit || character == '_' || character == '-' || character == '`')
                }
                loop(closeIndex + 1, acc ++ dependencyArguments)
              case None =>
                fail(s"unmatched '.dependsOn(' opening parenthesis while scanning build.sbt block: $block")
            }
          } else {
            fail(
              s"expected '(' immediately after skipping whitespace following '.dependsOn' at index $afterMarker " +
                s"(comments and other syntax between '.dependsOn' and '(' are not supported) while scanning build.sbt block: $block"
            )
          }
        }
      }
    }

    loop(0, Nil)
  }

  private def isIdentifierChar(ch: Char): Boolean =
    ch.isLetterOrDigit || ch == '_'

  // Returns the raw sbt scope-mapping tokens (e.g. List("test->test")) for every
  // `.dependsOn(...)` occurrence of the named dependency. An argument with no
  // explicit `% "..."` mapping is reported as `List("")`. The list preserves
  // declaration order so the firewall can spot a second, forbidden edge.
  private def extractDependsOnMappings(block: String, dependencyName: String): List[String] = {
    val marker = ".dependsOn"

    @tailrec
    def loop(fromIndex: Int, acc: List[String]): List[String] = {
      val markerIndex = block.indexOf(marker, fromIndex)
      if (markerIndex < 0) {
        acc
      } else {
        val afterMarker    = markerIndex + marker.length
        val isLongerSymbol = afterMarker < block.length && isIdentifierChar(block.charAt(afterMarker))

        if (isLongerSymbol) {
          loop(afterMarker, acc)
        } else {
          val openIndex = skipWhitespace(block, afterMarker)
          if (openIndex < block.length && block.charAt(openIndex) == '(') {
            findMatchingCloseParen(block, openIndex) match {
              case Some(closeIndex) =>
                val args = block.substring(openIndex + 1, closeIndex)
                val mappings = splitTopLevelArgs(args).collect {
                  case argument if argument.contains(dependencyName) =>
                    val percentIndex = argument.indexOf('%')
                    if (percentIndex < 0) ""
                    else {
                      val raw = argument.substring(percentIndex + 1).trim
                      if (raw.startsWith("\"") && raw.endsWith("\"") && raw.length >= 2) raw.substring(1, raw.length - 1)
                      else raw
                    }
                }
                loop(closeIndex + 1, acc ++ mappings)
              case None =>
                fail(s"unmatched '.dependsOn(' opening parenthesis while scanning build.sbt block: $block")
            }
          } else {
            fail(
              s"expected '(' immediately after skipping whitespace following '.dependsOn' at index $afterMarker " +
                s"(comments and other syntax between '.dependsOn' and '(' are not supported) while scanning build.sbt block: $block"
            )
          }
        }
      }
    }

    loop(0, Nil)
  }

  // Skips whitespace (including newlines) starting at fromIndex; returns text.length if only
  // whitespace remains, which the caller treats as "no opening parenthesis found" via a bounds check.
  private def skipWhitespace(text: String, fromIndex: Int): Int = {
    @tailrec
    def loop(index: Int): Int =
      if (index < text.length && text.charAt(index).isWhitespace) loop(index + 1) else index

    loop(fromIndex)
  }

  private def isAcyclic(graph: Map[String, List[String]]): Boolean = {
    def visit(node: String, onStack: Set[String], done: Set[String]): Option[Set[String]] =
      if (onStack.contains(node)) {
        None
      } else if (done.contains(node)) {
        Some(done)
      } else {
        graph
          .getOrElse(node, Nil)
          .foldLeft(Option(done)) { (acc, dep) =>
            acc.flatMap(visit(dep, onStack + node, _))
          }
          .map(_ + node)
      }

    graph.keys
      .foldLeft(Option(Set.empty[String])) { (acc, node) =>
        acc.flatMap(visit(node, Set.empty, _))
      }
      .isDefined
  }

  private def renderGraph(nodes: List[ModuleNode]): String =
    nodes.map(renderModuleBlock).mkString("\n\n")

  private def renderModuleBlock(node: ModuleNode): String = {
    val lastIndex = node.dependsOn.size - 1
    val depLines = node.dependsOn.zipWithIndex.map { case (dep, index) =>
      (if (index == lastIndex) "└── " else "├── ") + dep
    }

    (node.displayName :: depLines).mkString("\n")
  }

  private def matchesPrefix(target: String, pattern: String): Boolean =
    target == pattern || target.startsWith(pattern + ".")

  private def stripRootPrefix(target: String): String =
    target.stripPrefix("_root_.")

  private def stripWildcardSuffix(target: String): String =
    if (target.endsWith(".*") || target.endsWith("._")) target.dropRight(2) else target

  // This scan is diagnostic only. The sbt project DAG and compilation are the
  // authoritative dependency firewall. The scanner intentionally does not
  // implement the complete Scala import grammar.
  private def importTargetsOf(content: String): List[String] =
    content.linesIterator
      .map(_.trim)
      .filter(_.startsWith("import "))
      .map(_.stripPrefix("import ").trim)
      .map(stripWildcardSuffix)
      .map(stripRootPrefix)
      .toList

  private def forbiddenImportViolations(
    displayPath: String,
    content: String,
    forbiddenPatterns: List[String],
    allowedPatterns: List[String],
  ): List[String] =
    importTargetsOf(content)
      .filter(target => forbiddenPatterns.exists(matchesPrefix(target, _)) && !allowedPatterns.exists(matchesPrefix(target, _)))
      .map(target => s"$displayPath: forbidden import: $target")

  private def assertNoViolations(context: String, violations: List[String]): Unit =
    if (violations.nonEmpty) {
      fail((context :: violations.sorted).mkString("\n"))
    }
}
