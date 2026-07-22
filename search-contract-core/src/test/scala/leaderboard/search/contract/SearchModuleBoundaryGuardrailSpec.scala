package leaderboard.search.contract

import org.scalatest.wordspec.AnyWordSpec

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import scala.jdk.CollectionConverters.*
import scala.util.Using

final class SearchModuleBoundaryGuardrailSpec extends AnyWordSpec {

  private val appHttpForbiddenImports = List(
    "leaderboard.repo.BeautyQCatalogGraph",
    "BeautyQVariantSearchDocumentMaterialization",
    "BeautyQSearchCatalogSnapshot",
    "BeautyQSearchCatalogSnapshotLoader",
    "SearchDocumentProjection",
    "leaderboard.search.qdrant",
    "leaderboard.search.hybrid",
    "leaderboard.search.elasticsearch",
    "leaderboard.plugins",
    "leaderboard.config",
    "leaderboard.sql",
    "leaderboard.runtime",
    "leaderboard.search.routing",
    "leaderboard.search.interpreter",
    "leaderboard.search.parser",
    "leaderboard.search.eval",
    "leaderboard.search.startup",
    "leaderboard.repo.BeautyQSearchCatalogSnapshot",
    "leaderboard.repo.BeautyQSearchCatalogSnapshotLoader",
    "SearchBackendRouter",
    "SearchResponseAssembler",
    "BeautySearchPluginModules",
    "BeautySearchRouteModules",
    "BeautySearchCatalogBackendModules",
    "BeautySearchLocalQdrantSupplementLauncherModule",
    "BeautySearchQdrantSupplementRuntimeBindingModules",
    "BeautySearchQdrantSupplementActivationModuleSelector",
    "ElasticsearchClient",
    "QdrantClient",
    "LlamaCppEmbeddingClient",
    "LlamaCppEmbeddingClientConfig",
    "QdrantEmbeddingBenchmarkCandidateExecutor",
    "BeautyQNonProductionHybridRunnerRealClientInputs",
    "PostgresCfg",
    "QdrantPortCfg",
    "ElasticsearchPortCfg",
    "Transactor",
    "ModuleDef",
    "Plugin",
    "Resource",
    "HttpServer",
    "Docker",
    "doobie",
  )

  private val appHttpAllowedImports = List(
    "leaderboard.search.elasticsearch.ElasticsearchStartupReadinessStatusResponse",
    "leaderboard.search.elasticsearch.ElasticsearchStartupReadinessTransition",
  )

  private val searchContractCoreForbiddenImports = List(
    "leaderboard.model",
    "leaderboard.repo",
    "leaderboard.api",
    "leaderboard.http",
    "leaderboard.plugins",
    "leaderboard.sql",
    "leaderboard.config",
    "leaderboard.search.beautyq",
    "leaderboard.search.elasticsearch",
    "leaderboard.search.qdrant",
    "leaderboard.search.hybrid",
    "leaderboard.search.startup",
    "leaderboard.search.embedding",
    "beautyq",
    "BeautyQ",
    "ModuleDef",
    "Lifecycle",
    "Tapir",
    "HttpApi",
    "doobie",
    "http4s",
  )

  private val repoCoreForbiddenImports = List(
    "leaderboard.search",
    "leaderboard.api",
    "leaderboard.http",
    "leaderboard.plugins",
    "leaderboard.config",
    "leaderboard.sql",
    "leaderboard.services",
    "leaderboard.repo.Categories",
    "leaderboard.repo.Services",
    "leaderboard.repo.Masters",
    "leaderboard.repo.MasterLocations",
    "leaderboard.repo.MasterServiceOffers",
    "leaderboard.repo.MasterServiceOfferVariants",
    "leaderboard.repo.ServiceVariantSchemas",
    "leaderboard.repo.BeautyQCatalogGraph",
    "doobie",
    "http4s",
    "tapir",
    "zio",
    "cats.effect",
    "ModuleDef",
    "Lifecycle",
    "ElasticsearchClient",
    "QdrantClient",
  )

  private val repoCoreForbiddenSourceText = List(
    "BeautyQ",
    "beautyq",
  )

  private val beautyqModelForbiddenImports = List(
    "leaderboard.repo",
    "leaderboard.search",
    "leaderboard.api",
    "leaderboard.http",
    "leaderboard.plugins",
    "leaderboard.config",
    "leaderboard.sql",
    "leaderboard.services",
    "doobie",
    "http4s",
    "tapir",
    "zio",
    "cats.effect",
    "izumi.functional.bio",
    "ModuleDef",
    "Lifecycle",
    "ElasticsearchClient",
    "QdrantClient",
  )

  private val beautyqRepositoriesForbiddenImports = List(
    "leaderboard.search",
    "leaderboard.api",
    "leaderboard.http",
    "leaderboard.plugins",
    "leaderboard.config",
    "leaderboard.services",
    "leaderboard.repo.BeautyQCatalogGraph",
    "leaderboard.repo.BeautyQSearchCatalogSnapshot",
    "leaderboard.repo.BeautyQSearchCatalogSnapshotLoader",
    "leaderboard.search.document.BeautyQVariantSearchDocumentMaterialization",
    "leaderboard.search.document.SearchDocumentProjection",
    "SearchDomainSpec",
    "SearchField",
    "SearchRuntimeDeclaration",
    "ModuleDef",
    "HttpRoutes",
    "ServerEndpoint",
    "Tapir",
    "tapir",
    "http4s",
    "ElasticsearchClient",
    "QdrantClient",
  )

  private val appServicesForbiddenImports = List(
    "leaderboard.api",
    "leaderboard.http",
    "leaderboard.plugins",
    "leaderboard.config",
    "leaderboard.sql",
    "leaderboard.runtime",
    "leaderboard.search",
    "leaderboard.repo.BeautyQCatalogGraph",
    "leaderboard.repo.BeautyQSearchCatalogSnapshot",
    "leaderboard.repo.BeautyQSearchCatalogSnapshotLoader",
    "BeautyQVariantSearchDocumentMaterialization",
    "SearchDocumentProjection",
    "BeautyQSearchCatalogSnapshot",
    "BeautyQSearchCatalogSnapshotLoader",
    "ElasticsearchClient",
    "QdrantClient",
    "LlamaCppEmbeddingClient",
    "LlamaCppEmbeddingClientConfig",
    "BeautyQNonProductionHybridRunnerRealClientInputs",
    "QdrantEmbeddingBenchmarkCandidateExecutor",
    "ModuleDef",
    "Lifecycle",
    "Resource",
    "Transactor",
    "PostgresCfg",
    "QdrantPortCfg",
    "ElasticsearchPortCfg",
    "HttpRoutes",
    "ServerEndpoint",
    "HttpServer",
    "Tapir",
    "tapir",
    "http4s",
    "doobie",
    "cats.effect",
    "zio",
  )

  "BeautyQ search module boundaries" should {
    "keep app-http main sources free of materialization, repo internals, and backend semantics except ES lifecycle DTOs" in {
      val violations = scalaMainFiles("app-http/src/main/scala")
        .flatMap(path => containsForbiddenImport(path, appHttpForbiddenImports, appHttpAllowedImports))

      assertNoViolations("app-http boundary violations", violations)
    }

    "keep search-contract-core main sources generic and free of domain, app, runtime, and resource imports" in {
      val violations = scalaMainFiles("search-contract-core/src/main/scala")
        .flatMap(path => containsForbiddenImport(path, searchContractCoreForbiddenImports, allowedPatterns = Nil))

      assertNoViolations("search-contract-core boundary violations", violations)
    }

    "keep repo-core main sources generic and free of domain, search, app, runtime, and resource imports" in {
      val files = scalaMainFiles("repo-core/src/main/scala")
      val importViolations = files.flatMap(path => containsForbiddenImport(path, repoCoreForbiddenImports, allowedPatterns = Nil))
      val sourceViolations = files.flatMap { path =>
        repoCoreForbiddenSourceText.filter(read(path).contains).map { forbiddenText =>
          s"${relative(path)}: forbidden BeautyQ-specific source text: $forbiddenText"
        }
      }

      assertNoViolations("repo-core boundary violations", importViolations ++ sourceViolations)
    }

    "keep beautyq-model main sources pure model/data/codecs/validation only" in {
      val violations = scalaMainFiles("beautyq-model/src/main/scala")
        .flatMap(path => containsForbiddenImport(path, beautyqModelForbiddenImports, allowedPatterns = Nil))

      assertNoViolations("beautyq-model boundary violations", violations)
    }

    "keep beautyq-search-repositories main sources limited to data access and repository-local SQL" in {
      val violations = scalaMainFiles("beautyq-search-repositories/src/main/scala")
        .flatMap(path => containsForbiddenImport(path, beautyqRepositoriesForbiddenImports, allowedPatterns = Nil))

      assertNoViolations("beautyq-search-repositories boundary violations", violations)
    }

    "keep app-services main sources free of HTTP, shell, config, SQL, concrete client, and search runtime imports" in {
      val violations = scalaMainFiles("app-services/src/main/scala")
        .flatMap(path => containsForbiddenImport(path, appServicesForbiddenImports, allowedPatterns = Nil))

      assertNoViolations("app-services boundary violations", violations)
    }

    "prove no build block exists for any deleted Gen1 project root" in {
      val deleted = Vector("search-core", "search-elasticsearch", "search-qdrant", "beautyq-search-contract", "beautyq-search-materialization", "beautyq-search-wiring")
      deleted.foreach { name =>
        assert(!Files.isDirectory(repoRoot.resolve(name)), s"deleted project directory still exists: $name")
      }
    }

    "keep build.sbt in the post-cutover DAG shape" in {
      assertBlockContains("beautyqSearchRepositories", ".dependsOn(`leaderboard-core`, repoCore, beautyqModel)")
      assertBlockContains("appServices", ".dependsOn(beautyqSearchRepositories)")
      assertBlockContains("appHttp", ".dependsOn(beautyqSearchGen2Wiring % \"compile->compile;test->test\", appServices)")
      assertBlockNotContains("appHttp", "beautyqSearchWiring")
      assertBlockContains("`leaderboard-app-shell`", ".dependsOn(")
      assertBlockContains("`leaderboard-app-shell`", "beautyqSearchGen2Wiring % \"compile->compile;test->test\"")
      assertBlockContains("`leaderboard-app-shell`", "beautyqSearchGen2Eval % \"test->test\"")
      assertBlockContains("`leaderboard-app-shell`", "beautyqSearchGen2Materialization")
      assertBlockContains("`leaderboard-app-shell`", "searchGen2Elasticsearch % \"test->test\"")
      assertBlockContains("`leaderboard-app-shell`", "searchGen2Qdrant % \"test->test\"")
      assertBlockContains("`leaderboard-app-shell`", "appHttp")
      assertBlockContains("`leaderboard-app-shell`", "appServices")
      assertBlockNotContains("`leaderboard-app-shell`", "beautyqSearchWiring")
    }

    "prove forbidden imports fail and allowed exceptions pass" in {
val forbiddenAppHttpImport = importLineViolations(
        displayPath       = "synthetic/AppHttp.scala",
        lines             = List("import leaderboard.search.qdrant.QdrantClient"),
        forbiddenPatterns = appHttpForbiddenImports,
        allowedPatterns   = appHttpAllowedImports,
      )
      assert(forbiddenAppHttpImport.nonEmpty)

      val allowedDirectAppHttpImport = importLineViolations(
        displayPath       = "synthetic/AppHttp.scala",
        lines             = List("import leaderboard.search.elasticsearch.ElasticsearchStartupReadinessStatusResponse"),
        forbiddenPatterns = appHttpForbiddenImports,
        allowedPatterns   = appHttpAllowedImports,
      )
      assert(allowedDirectAppHttpImport.isEmpty)

      val spoofedAppHttpImport = importLineViolations(
        displayPath       = "synthetic/AppHttp.scala",
        lines             = List("import leaderboard.search.elasticsearch.ElasticsearchStartupReadinessStatusResponseExtra"),
        forbiddenPatterns = appHttpForbiddenImports,
        allowedPatterns   = appHttpAllowedImports,
      )
      assert(spoofedAppHttpImport.nonEmpty)

      val allowedAppHttpImports = importLineViolations(
        displayPath = "synthetic/AppHttp.scala",
        lines = List(
          "import leaderboard.search.elasticsearch.ElasticsearchStartupReadinessStatusResponse",
          "import leaderboard.search.elasticsearch.ElasticsearchStartupReadinessTransition",
        ),
        forbiddenPatterns = appHttpForbiddenImports,
        allowedPatterns   = appHttpAllowedImports,
      )
      assert(allowedAppHttpImports.isEmpty)

      val allowedGroupedAppHttpImport = importLineViolations(
        displayPath = "synthetic/AppHttp.scala",
        lines = List(
          "import leaderboard.search.elasticsearch.{ElasticsearchStartupReadinessStatusResponse, ElasticsearchStartupReadinessTransition}",
        ),
        forbiddenPatterns = appHttpForbiddenImports,
        allowedPatterns   = appHttpAllowedImports,
      )
      assert(allowedGroupedAppHttpImport.isEmpty)

      val mixedGroupedAppHttpImport = importLineViolations(
        displayPath = "synthetic/AppHttp.scala",
        lines = List(
          "import leaderboard.search.elasticsearch.{ElasticsearchStartupReadinessStatusResponse, ElasticsearchClient}",
        ),
        forbiddenPatterns = appHttpForbiddenImports,
        allowedPatterns   = appHttpAllowedImports,
      )
      assert(mixedGroupedAppHttpImport.nonEmpty)

      val forbiddenAppHttpPluginModulesImport = importLineViolations(
        displayPath       = "synthetic/AppHttp.scala",
        lines             = List("import leaderboard.plugins.BeautySearchPluginModules"),
        forbiddenPatterns = appHttpForbiddenImports,
        allowedPatterns   = appHttpAllowedImports,
      )
      assert(forbiddenAppHttpPluginModulesImport.nonEmpty)

      val forbiddenAppHttpConfigImport = importLineViolations(
        displayPath       = "synthetic/AppHttp.scala",
        lines             = List("import leaderboard.config.QdrantPortCfg"),
        forbiddenPatterns = appHttpForbiddenImports,
        allowedPatterns   = appHttpAllowedImports,
      )
      assert(forbiddenAppHttpConfigImport.nonEmpty)

      val forbiddenAppHttpSqlImport = importLineViolations(
        displayPath       = "synthetic/AppHttp.scala",
        lines             = List("import leaderboard.sql.SQL"),
        forbiddenPatterns = appHttpForbiddenImports,
        allowedPatterns   = appHttpAllowedImports,
      )
      assert(forbiddenAppHttpSqlImport.nonEmpty)

      val forbiddenAppHttpDoobieImport = importLineViolations(
        displayPath       = "synthetic/AppHttp.scala",
        lines             = List("import doobie.ConnectionIO"),
        forbiddenPatterns = appHttpForbiddenImports,
        allowedPatterns   = appHttpAllowedImports,
      )
      assert(forbiddenAppHttpDoobieImport.nonEmpty)

      val forbiddenAppHttpModuleDefImport = importLineViolations(
        displayPath       = "synthetic/AppHttp.scala",
        lines             = List("import izumi.distage.model.definition.ModuleDef"),
        forbiddenPatterns = appHttpForbiddenImports,
        allowedPatterns   = appHttpAllowedImports,
      )
      assert(forbiddenAppHttpModuleDefImport.nonEmpty)

      val forbiddenAppHttpResourceImport = importLineViolations(
        displayPath       = "synthetic/AppHttp.scala",
        lines             = List("import cats.effect.Resource"),
        forbiddenPatterns = appHttpForbiddenImports,
        allowedPatterns   = appHttpAllowedImports,
      )
      assert(forbiddenAppHttpResourceImport.nonEmpty)

      val forbiddenAppHttpCatalogGraphImport = importLineViolations(
        displayPath       = "synthetic/AppHttp.scala",
        lines             = List("import leaderboard.repo.BeautyQCatalogGraph"),
        forbiddenPatterns = appHttpForbiddenImports,
        allowedPatterns   = appHttpAllowedImports,
      )
      assert(forbiddenAppHttpCatalogGraphImport.nonEmpty)

      val forbiddenAppHttpMaterializationImport = importLineViolations(
        displayPath       = "synthetic/AppHttp.scala",
        lines             = List("import leaderboard.search.document.BeautyQVariantSearchDocumentMaterialization"),
        forbiddenPatterns = appHttpForbiddenImports,
        allowedPatterns   = appHttpAllowedImports,
      )
      assert(forbiddenAppHttpMaterializationImport.nonEmpty)

      val forbiddenAppHttpResponseAssemblerImport = importLineViolations(
        displayPath       = "synthetic/AppHttp.scala",
        lines             = List("import leaderboard.search.interpreter.SearchResponseAssembler"),
        forbiddenPatterns = appHttpForbiddenImports,
        allowedPatterns   = appHttpAllowedImports,
      )
      assert(forbiddenAppHttpResponseAssemblerImport.nonEmpty)

      val forbiddenAppHttpBackendRouterImport = importLineViolations(
        displayPath       = "synthetic/AppHttp.scala",
        lines             = List("import leaderboard.search.routing.SearchBackendRouter"),
        forbiddenPatterns = appHttpForbiddenImports,
        allowedPatterns   = appHttpAllowedImports,
      )
      assert(forbiddenAppHttpBackendRouterImport.nonEmpty)

      val forbiddenAppHttpLlamaCppConfigImport = importLineViolations(
        displayPath       = "synthetic/AppHttp.scala",
        lines             = List("import leaderboard.search.embedding.LlamaCppEmbeddingClientConfig"),
        forbiddenPatterns = appHttpForbiddenImports,
        allowedPatterns   = appHttpAllowedImports,
      )
      assert(forbiddenAppHttpLlamaCppConfigImport.nonEmpty)

      val forbiddenAppHttpHybridRealClientImport = importLineViolations(
        displayPath       = "synthetic/AppHttp.scala",
        lines             = List("import leaderboard.search.hybrid.BeautyQNonProductionHybridRunnerRealClientInputs"),
        forbiddenPatterns = appHttpForbiddenImports,
        allowedPatterns   = appHttpAllowedImports,
      )
      assert(forbiddenAppHttpHybridRealClientImport.nonEmpty)

      val allowedAppHttpTapirEndpointsImport = importLineViolations(
        displayPath       = "synthetic/AppHttp.scala",
        lines             = List("import leaderboard.http.tapir.BeautySearchTapirEndpoints"),
        forbiddenPatterns = appHttpForbiddenImports,
        allowedPatterns   = appHttpAllowedImports,
      )
      assert(allowedAppHttpTapirEndpointsImport.isEmpty)

      val allowedAppHttpRoutesImport = importLineViolations(
        displayPath       = "synthetic/AppHttp.scala",
        lines             = List("import org.http4s.HttpRoutes"),
        forbiddenPatterns = appHttpForbiddenImports,
        allowedPatterns   = appHttpAllowedImports,
      )
      assert(allowedAppHttpRoutesImport.isEmpty)

      val allowedAppHttpTapirServerImport = importLineViolations(
        displayPath       = "synthetic/AppHttp.scala",
        lines             = List("import sttp.tapir.server.http4s.Http4sServerInterpreter"),
        forbiddenPatterns = appHttpForbiddenImports,
        allowedPatterns   = appHttpAllowedImports,
      )
      assert(allowedAppHttpTapirServerImport.isEmpty)

      val allowedAppHttpTapirEndpointImport = importLineViolations(
        displayPath       = "synthetic/AppHttp.scala",
        lines             = List("import sttp.tapir.Endpoint"),
        forbiddenPatterns = appHttpForbiddenImports,
        allowedPatterns   = appHttpAllowedImports,
      )
      assert(allowedAppHttpTapirEndpointImport.isEmpty)

      val allowedAppHttpAsyncImport = importLineViolations(
        displayPath       = "synthetic/AppHttp.scala",
        lines             = List("import cats.effect.Async"),
        forbiddenPatterns = appHttpForbiddenImports,
        allowedPatterns   = appHttpAllowedImports,
      )
      assert(allowedAppHttpAsyncImport.isEmpty)

      val allowedAppHttpBioImport = importLineViolations(
        displayPath       = "synthetic/AppHttp.scala",
        lines             = List("import izumi.functional.bio.Error2"),
        forbiddenPatterns = appHttpForbiddenImports,
        allowedPatterns   = appHttpAllowedImports,
      )
      assert(allowedAppHttpBioImport.isEmpty)

      val allowedAppHttpCirceImport = importLineViolations(
        displayPath       = "synthetic/AppHttp.scala",
        lines             = List("import io.circe.Json"),
        forbiddenPatterns = appHttpForbiddenImports,
        allowedPatterns   = appHttpAllowedImports,
      )
      assert(allowedAppHttpCirceImport.isEmpty)

      val allowedAppHttpLogstageImport = importLineViolations(
        displayPath       = "synthetic/AppHttp.scala",
        lines             = List("import logstage.LogIO2"),
        forbiddenPatterns = appHttpForbiddenImports,
        allowedPatterns   = appHttpAllowedImports,
      )
      assert(allowedAppHttpLogstageImport.isEmpty)

      val allowedAppHttpModelImport = importLineViolations(
        displayPath       = "synthetic/AppHttp.scala",
        lines             = List("import leaderboard.model.Category"),
        forbiddenPatterns = appHttpForbiddenImports,
        allowedPatterns   = appHttpAllowedImports,
      )
      assert(allowedAppHttpModelImport.isEmpty)

      val allowedAppHttpRepoImport = importLineViolations(
        displayPath       = "synthetic/AppHttp.scala",
        lines             = List("import leaderboard.repo.Categories"),
        forbiddenPatterns = appHttpForbiddenImports,
        allowedPatterns   = appHttpAllowedImports,
      )
      assert(allowedAppHttpRepoImport.isEmpty)

      val allowedAppHttpRanksImport = importLineViolations(
        displayPath       = "synthetic/AppHttp.scala",
        lines             = List("import leaderboard.services.Ranks"),
        forbiddenPatterns = appHttpForbiddenImports,
        allowedPatterns   = appHttpAllowedImports,
      )
      assert(allowedAppHttpRanksImport.isEmpty)

      val allowedAppHttpSearchServiceImport = importLineViolations(
        displayPath       = "synthetic/AppHttp.scala",
        lines             = List("import leaderboard.search.BeautySearchService"),
        forbiddenPatterns = appHttpForbiddenImports,
        allowedPatterns   = appHttpAllowedImports,
      )
      assert(allowedAppHttpSearchServiceImport.isEmpty)

      val allowedAppHttpSearchResponseImport = importLineViolations(
        displayPath       = "synthetic/AppHttp.scala",
        lines             = List("import leaderboard.search.{BeautySearchResponse, UserSearchInput}"),
        forbiddenPatterns = appHttpForbiddenImports,
        allowedPatterns   = appHttpAllowedImports,
      )
      assert(allowedAppHttpSearchResponseImport.isEmpty)

      val forbiddenSearchContractCoreBeautyQImport = importLineViolations(
        displayPath       = "synthetic/SearchContractCore.scala",
        lines             = List("import leaderboard.search.beautyq.contract.BeautyQSearchDomainContract"),
        forbiddenPatterns = searchContractCoreForbiddenImports,
        allowedPatterns   = Nil,
      )
      assert(forbiddenSearchContractCoreBeautyQImport.nonEmpty)

      val forbiddenSearchContractCoreRepoImport = importLineViolations(
        displayPath       = "synthetic/SearchContractCore.scala",
        lines             = List("import leaderboard.repo.Categories"),
        forbiddenPatterns = searchContractCoreForbiddenImports,
        allowedPatterns   = Nil,
      )
      assert(forbiddenSearchContractCoreRepoImport.nonEmpty)

      val forbiddenRepoCoreBeautyQContractImport = importLineViolations(
        displayPath       = "synthetic/RepoCore.scala",
        lines             = List("import leaderboard.search.beautyq.contract.BeautyQSearchDomainContract"),
        forbiddenPatterns = repoCoreForbiddenImports,
        allowedPatterns   = Nil,
      )
      assert(forbiddenRepoCoreBeautyQContractImport.nonEmpty)

      val forbiddenRepoCoreCategoriesImport = importLineViolations(
        displayPath       = "synthetic/RepoCore.scala",
        lines             = List("import leaderboard.repo.Categories"),
        forbiddenPatterns = repoCoreForbiddenImports,
        allowedPatterns   = Nil,
      )
      assert(forbiddenRepoCoreCategoriesImport.nonEmpty)

      val forbiddenRepoCoreSqlImport = importLineViolations(
        displayPath       = "synthetic/RepoCore.scala",
        lines             = List("import leaderboard.sql.SQL"),
        forbiddenPatterns = repoCoreForbiddenImports,
        allowedPatterns   = Nil,
      )
      assert(forbiddenRepoCoreSqlImport.nonEmpty)

      val forbiddenRepoCoreDoobieImport = importLineViolations(
        displayPath       = "synthetic/RepoCore.scala",
        lines             = List("import doobie.ConnectionIO"),
        forbiddenPatterns = repoCoreForbiddenImports,
        allowedPatterns   = Nil,
      )
      assert(forbiddenRepoCoreDoobieImport.nonEmpty)

      val allowedRepoCoreQueryFailureImport = importLineViolations(
        displayPath       = "synthetic/RepoCore.scala",
        lines             = List("import leaderboard.model.QueryFailure"),
        forbiddenPatterns = repoCoreForbiddenImports,
        allowedPatterns   = Nil,
      )
      assert(allowedRepoCoreQueryFailureImport.isEmpty)

      val allowedRepoCoreBioImport = importLineViolations(
        displayPath       = "synthetic/RepoCore.scala",
        lines             = List("import izumi.functional.bio.{Error2, F}"),
        forbiddenPatterns = repoCoreForbiddenImports,
        allowedPatterns   = Nil,
      )
      assert(allowedRepoCoreBioImport.isEmpty)

      val forbiddenBeautyqModelRepoImport = importLineViolations(
        displayPath       = "synthetic/BeautyqModel.scala",
        lines             = List("import leaderboard.repo.Categories"),
        forbiddenPatterns = beautyqModelForbiddenImports,
        allowedPatterns   = Nil,
      )
      assert(forbiddenBeautyqModelRepoImport.nonEmpty)

      val forbiddenBeautyqModelSearchContractImport = importLineViolations(
        displayPath       = "synthetic/BeautyqModel.scala",
        lines             = List("import leaderboard.search.beautyq.contract.BeautyQSearchDomainContract"),
        forbiddenPatterns = beautyqModelForbiddenImports,
        allowedPatterns   = Nil,
      )
      assert(forbiddenBeautyqModelSearchContractImport.nonEmpty)

      val forbiddenBeautyqModelSqlImport = importLineViolations(
        displayPath       = "synthetic/BeautyqModel.scala",
        lines             = List("import leaderboard.sql.SQL"),
        forbiddenPatterns = beautyqModelForbiddenImports,
        allowedPatterns   = Nil,
      )
      assert(forbiddenBeautyqModelSqlImport.nonEmpty)

      val forbiddenBeautyqModelDoobieImport = importLineViolations(
        displayPath       = "synthetic/BeautyqModel.scala",
        lines             = List("import doobie.ConnectionIO"),
        forbiddenPatterns = beautyqModelForbiddenImports,
        allowedPatterns   = Nil,
      )
      assert(forbiddenBeautyqModelDoobieImport.nonEmpty)

      val forbiddenBeautyqModelZioImport = importLineViolations(
        displayPath       = "synthetic/BeautyqModel.scala",
        lines             = List("import zio.Task"),
        forbiddenPatterns = beautyqModelForbiddenImports,
        allowedPatterns   = Nil,
      )
      assert(forbiddenBeautyqModelZioImport.nonEmpty)

      val allowedBeautyqModelCirceImport = importLineViolations(
        displayPath       = "synthetic/BeautyqModel.scala",
        lines             = List("import io.circe.Codec"),
        forbiddenPatterns = beautyqModelForbiddenImports,
        allowedPatterns   = Nil,
      )
      assert(allowedBeautyqModelCirceImport.isEmpty)

      val allowedBeautyqModelInternalImport = importLineViolations(
        displayPath       = "synthetic/BeautyqModel.scala",
        lines             = List("import leaderboard.model.AttributeMap.Impl"),
        forbiddenPatterns = beautyqModelForbiddenImports,
        allowedPatterns   = Nil,
      )
      assert(allowedBeautyqModelInternalImport.isEmpty)

      val allowedBeautyqModelUuidImport = importLineViolations(
        displayPath       = "synthetic/BeautyqModel.scala",
        lines             = List("import java.util.UUID"),
        forbiddenPatterns = beautyqModelForbiddenImports,
        allowedPatterns   = Nil,
      )
      assert(allowedBeautyqModelUuidImport.isEmpty)

      val forbiddenRepositoriesSearchContractImport = importLineViolations(
        displayPath       = "synthetic/BeautyqRepositories.scala",
        lines             = List("import leaderboard.search.beautyq.contract.BeautyQSearchDomainContract"),
        forbiddenPatterns = beautyqRepositoriesForbiddenImports,
        allowedPatterns   = Nil,
      )
      assert(forbiddenRepositoriesSearchContractImport.nonEmpty)

      val forbiddenRepositoriesCatalogGraphImport = importLineViolations(
        displayPath       = "synthetic/BeautyqRepositories.scala",
        lines             = List("import leaderboard.repo.BeautyQCatalogGraph"),
        forbiddenPatterns = beautyqRepositoriesForbiddenImports,
        allowedPatterns   = Nil,
      )
      assert(forbiddenRepositoriesCatalogGraphImport.nonEmpty)

      val forbiddenRepositoriesMaterializationImport = importLineViolations(
        displayPath       = "synthetic/BeautyqRepositories.scala",
        lines             = List("import leaderboard.search.document.BeautyQVariantSearchDocumentMaterialization"),
        forbiddenPatterns = beautyqRepositoriesForbiddenImports,
        allowedPatterns   = Nil,
      )
      assert(forbiddenRepositoriesMaterializationImport.nonEmpty)

      val forbiddenRepositoriesHttpImport = importLineViolations(
        displayPath       = "synthetic/BeautyqRepositories.scala",
        lines             = List("import leaderboard.http.tapir.BeautySearchApi"),
        forbiddenPatterns = beautyqRepositoriesForbiddenImports,
        allowedPatterns   = Nil,
      )
      assert(forbiddenRepositoriesHttpImport.nonEmpty)

      val forbiddenRepositoriesConfigImport = importLineViolations(
        displayPath       = "synthetic/BeautyqRepositories.scala",
        lines             = List("import leaderboard.config.PostgresCfg"),
        forbiddenPatterns = beautyqRepositoriesForbiddenImports,
        allowedPatterns   = Nil,
      )
      assert(forbiddenRepositoriesConfigImport.nonEmpty)

      val forbiddenRepositoriesQdrantClientImport = importLineViolations(
        displayPath       = "synthetic/BeautyqRepositories.scala",
        lines             = List("import leaderboard.search.qdrant.QdrantClient"),
        forbiddenPatterns = beautyqRepositoriesForbiddenImports,
        allowedPatterns   = Nil,
      )
      assert(forbiddenRepositoriesQdrantClientImport.nonEmpty)

      val allowedRepositoriesModelImport = importLineViolations(
        displayPath       = "synthetic/BeautyqRepositories.scala",
        lines             = List("import leaderboard.model.Category"),
        forbiddenPatterns = beautyqRepositoriesForbiddenImports,
        allowedPatterns   = Nil,
      )
      assert(allowedRepositoriesModelImport.isEmpty)

      val allowedRepositoriesRepoOpImport = importLineViolations(
        displayPath       = "synthetic/BeautyqRepositories.scala",
        lines             = List("import leaderboard.repo.RepoOp.ManyByKey"),
        forbiddenPatterns = beautyqRepositoriesForbiddenImports,
        allowedPatterns   = Nil,
      )
      assert(allowedRepositoriesRepoOpImport.isEmpty)

      val allowedRepositoriesSqlImport = importLineViolations(
        displayPath       = "synthetic/BeautyqRepositories.scala",
        lines             = List("import leaderboard.sql.SQL"),
        forbiddenPatterns = beautyqRepositoriesForbiddenImports,
        allowedPatterns   = Nil,
      )
      assert(allowedRepositoriesSqlImport.isEmpty)

      val allowedRepositoriesDoobieImport = importLineViolations(
        displayPath       = "synthetic/BeautyqRepositories.scala",
        lines             = List("import doobie.ConnectionIO"),
        forbiddenPatterns = beautyqRepositoriesForbiddenImports,
        allowedPatterns   = Nil,
      )
      assert(allowedRepositoriesDoobieImport.isEmpty)

      val allowedRepositoriesLifecycleImport = importLineViolations(
        displayPath       = "synthetic/BeautyqRepositories.scala",
        lines             = List("import distage.Lifecycle"),
        forbiddenPatterns = beautyqRepositoriesForbiddenImports,
        allowedPatterns   = Nil,
      )
      assert(allowedRepositoriesLifecycleImport.isEmpty)

      val allowedRepositoriesBioImport = importLineViolations(
        displayPath       = "synthetic/BeautyqRepositories.scala",
        lines             = List("import izumi.functional.bio.{Error2, F}"),
        forbiddenPatterns = beautyqRepositoriesForbiddenImports,
        allowedPatterns   = Nil,
      )
      assert(allowedRepositoriesBioImport.isEmpty)

val forbiddenAppServicesApiImport = importLineViolations(
        displayPath       = "synthetic/AppServices.scala",
        lines             = List("import leaderboard.api.ProfileApi"),
        forbiddenPatterns = appServicesForbiddenImports,
        allowedPatterns   = Nil,
      )
      assert(forbiddenAppServicesApiImport.nonEmpty)

      val forbiddenAppServicesTapirImport = importLineViolations(
        displayPath       = "synthetic/AppServices.scala",
        lines             = List("import leaderboard.http.tapir.ProfileTapirEndpoints"),
        forbiddenPatterns = appServicesForbiddenImports,
        allowedPatterns   = Nil,
      )
      assert(forbiddenAppServicesTapirImport.nonEmpty)

      val forbiddenAppServicesConfigImport = importLineViolations(
        displayPath       = "synthetic/AppServices.scala",
        lines             = List("import leaderboard.config.PostgresCfg"),
        forbiddenPatterns = appServicesForbiddenImports,
        allowedPatterns   = Nil,
      )
      assert(forbiddenAppServicesConfigImport.nonEmpty)

      val forbiddenAppServicesSqlImport = importLineViolations(
        displayPath       = "synthetic/AppServices.scala",
        lines             = List("import leaderboard.sql.SQL"),
        forbiddenPatterns = appServicesForbiddenImports,
        allowedPatterns   = Nil,
      )
      assert(forbiddenAppServicesSqlImport.nonEmpty)

      val forbiddenAppServicesDoobieImport = importLineViolations(
        displayPath       = "synthetic/AppServices.scala",
        lines             = List("import doobie.ConnectionIO"),
        forbiddenPatterns = appServicesForbiddenImports,
        allowedPatterns   = Nil,
      )
      assert(forbiddenAppServicesDoobieImport.nonEmpty)

      val forbiddenAppServicesModuleDefImport = importLineViolations(
        displayPath       = "synthetic/AppServices.scala",
        lines             = List("import izumi.distage.model.definition.ModuleDef"),
        forbiddenPatterns = appServicesForbiddenImports,
        allowedPatterns   = Nil,
      )
      assert(forbiddenAppServicesModuleDefImport.nonEmpty)

      val forbiddenAppServicesSearchContractImport = importLineViolations(
        displayPath       = "synthetic/AppServices.scala",
        lines             = List("import leaderboard.search.beautyq.contract.BeautyQSearchDomainContract"),
        forbiddenPatterns = appServicesForbiddenImports,
        allowedPatterns   = Nil,
      )
      assert(forbiddenAppServicesSearchContractImport.nonEmpty)

      val forbiddenAppServicesQdrantClientImport = importLineViolations(
        displayPath       = "synthetic/AppServices.scala",
        lines             = List("import leaderboard.search.qdrant.QdrantClient"),
        forbiddenPatterns = appServicesForbiddenImports,
        allowedPatterns   = Nil,
      )
      assert(forbiddenAppServicesQdrantClientImport.nonEmpty)

      val forbiddenAppServicesPluginModulesImport = importLineViolations(
        displayPath       = "synthetic/AppServices.scala",
        lines             = List("import leaderboard.plugins.BeautySearchPluginModules"),
        forbiddenPatterns = appServicesForbiddenImports,
        allowedPatterns   = Nil,
      )
      assert(forbiddenAppServicesPluginModulesImport.nonEmpty)

      val forbiddenAppServicesLlamaCppConfigImport = importLineViolations(
        displayPath       = "synthetic/AppServices.scala",
        lines             = List("import leaderboard.search.embedding.LlamaCppEmbeddingClientConfig"),
        forbiddenPatterns = appServicesForbiddenImports,
        allowedPatterns   = Nil,
      )
      assert(forbiddenAppServicesLlamaCppConfigImport.nonEmpty)

      val forbiddenAppServicesCatalogGraphImport = importLineViolations(
        displayPath       = "synthetic/AppServices.scala",
        lines             = List("import leaderboard.repo.BeautyQCatalogGraph"),
        forbiddenPatterns = appServicesForbiddenImports,
        allowedPatterns   = Nil,
      )
      assert(forbiddenAppServicesCatalogGraphImport.nonEmpty)

      val allowedAppServicesModelImport = importLineViolations(
        displayPath       = "synthetic/AppServices.scala",
        lines             = List("import leaderboard.model.{QueryFailure, RankedProfile, UserId}"),
        forbiddenPatterns = appServicesForbiddenImports,
        allowedPatterns   = Nil,
      )
      assert(allowedAppServicesModelImport.isEmpty)

      val allowedAppServicesRepoImport = importLineViolations(
        displayPath       = "synthetic/AppServices.scala",
        lines             = List("import leaderboard.repo.{Ladder, Profiles}"),
        forbiddenPatterns = appServicesForbiddenImports,
        allowedPatterns   = Nil,
      )
      assert(allowedAppServicesRepoImport.isEmpty)

      val allowedAppServicesBioImport = importLineViolations(
        displayPath       = "synthetic/AppServices.scala",
        lines             = List("import izumi.functional.bio.Monad2"),
        forbiddenPatterns = appServicesForbiddenImports,
        allowedPatterns   = Nil,
      )
      assert(allowedAppServicesBioImport.isEmpty)


    }
  }

  private def repoRoot: Path = {
    def loop(path: Path): Path = {
      val hasBuild = Files.isRegularFile(path.resolve("build.sbt"))

      if (hasBuild) {
        path
      } else {
        Option(path.getParent) match {
          case Some(parent) => loop(parent)
          case None =>
            fail(s"Could not find repo root from ${Paths.get("").toAbsolutePath}; expected build.sbt")
        }
      }
    }

    loop(Paths.get("").toAbsolutePath)
  }

  private def scalaMainFiles(relativeDir: String): List[Path] = {
    val root = repoRoot.resolve(relativeDir)
    if (!Files.isDirectory(root)) {
      fail(s"Expected Scala source directory to exist: $relativeDir")
    }

    Using.resource(Files.walk(root)) { stream =>
      stream.iterator.asScala.toList.filter { path =>
        val normalized = path.toString.replace('\\', '/')
        Files.isRegularFile(path) && normalized.endsWith(".scala") && !normalized.contains("/target/")
      }
    }
  }

  private def read(path: Path): String =
    Files.readString(path, StandardCharsets.UTF_8)

  private def importLines(path: Path): List[String] =
    read(path).linesIterator.map(_.trim).filter(_.startsWith("import ")).toList

  private def containsForbiddenImport(path: Path, forbiddenPatterns: List[String], allowedPatterns: List[String]): List[String] =
    importLineViolations(relative(path), importLines(path), forbiddenPatterns, allowedPatterns)

  private def importLineViolations(
    displayPath: String,
    lines: List[String],
    forbiddenPatterns: List[String],
    allowedPatterns: List[String],
  ): List[String] =
    lines.filter { line =>
      forbiddenPatterns.exists(line.contains) && !isAllowedImportLine(line, allowedPatterns)
    }.map(line => s"$displayPath: forbidden import: $line")

  private def isAllowedImportLine(line: String, allowedPatterns: List[String]): Boolean =
    expandedImportTargets(line).exists { targets =>
      targets.nonEmpty && targets.forall(allowedPatterns.contains)
    }

  private def expandedImportTargets(line: String): Option[List[String]] = {
    val importTarget = line.stripPrefix("import ").trim
    val braceStart   = importTarget.indexOf(".{")
    val braceEnd     = importTarget.lastIndexOf("}")

    if (braceStart < 0 || braceEnd < braceStart) {
      Some(List(importTarget))
    } else {
      val prefix = importTarget.substring(0, braceStart)
      val names  = importTarget.substring(braceStart + 2, braceEnd).split(",").toList.map(_.trim).filter(_.nonEmpty)
      Some(names.map(name => s"$prefix.$name"))
    }
  }

  private def relative(path: Path): String =
    repoRoot.relativize(path).toString.replace('\\', '/')

  private def buildBlock(projectId: String): String = {
    val lines      = read(repoRoot.resolve("build.sbt")).linesIterator.toList
    val startIndex = lines.indexWhere(_.trim.startsWith(s"lazy val $projectId "))

    if (startIndex < 0) {
      fail(s"Could not find build.sbt block for lazy val $projectId")
    }

    val remaining = lines.drop(startIndex + 1)
    val nextIndex = remaining.indexWhere(_.trim.startsWith("lazy val "))
    val blockLines =
      if (nextIndex < 0) {
        lines.drop(startIndex)
      } else {
        lines.slice(startIndex, startIndex + 1 + nextIndex)
      }

    blockLines.mkString("\n")
  }

  private def assertBlockContains(projectId: String, required: String): Unit = {
    val block = buildBlock(projectId)
    if (!block.contains(required)) {
      fail(s"Expected build.sbt block for $projectId to contain: $required\nActual block:\n$block")
    }
  }

  private def assertBlockNotContains(projectId: String, forbidden: String): Unit = {
    val block = buildBlock(projectId)
    if (block.contains(forbidden)) {
      fail(s"Expected build.sbt block for $projectId NOT to contain: $forbidden\nActual block:\n$block")
    }
  }

  private def assertNoViolations(context: String, violations: List[String]): Unit =
    if (violations.nonEmpty) {
      fail((context :: violations).mkString("\n"))
    }
}
