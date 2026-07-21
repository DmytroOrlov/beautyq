package leaderboard.search

import io.circe.Json
import com.typesafe.config.ConfigFactory
import distage.{Injector, ModuleDef, Scene}
import izumi.distage.model.definition.{Activation, LocatorPrivacy}
import izumi.distage.model.plan.Roots
import izumi.distage.config.model.AppConfig
import izumi.logstage.api.IzLogger
import izumi.logstage.distage.LogIO2Module
import leaderboard.config.{ElasticsearchPortCfg, QdrantGen2PortCfg}
import leaderboard.plugins.{ElasticsearchDockerPlugin, QdrantGen2DockerPlugin}
import leaderboard.search.gen2.contract.*
import leaderboard.search.gen2.core.materialization.*
import leaderboard.search.gen2.core.plan.*
import leaderboard.search.gen2.elasticsearch.*
import leaderboard.search.gen2.elasticsearch.ElasticsearchTestFixtures.*
import leaderboard.search.gen2.elasticsearch.lifecycle.*
import leaderboard.search.gen2.qdrant.*
import leaderboard.search.gen2.qdrant.QdrantTestFixtures
import leaderboard.search.gen2.qdrant.QdrantTestFixtures.*
import leaderboard.search.gen2.transport.*
import zio.{IO, Runtime, Task, Unsafe, ZIO}

import java.time.{Clock, Duration, Instant}
import java.util.UUID

/** Communication proofs using only Distage-managed ES and the separate Gen2 Qdrant 1.18.3 resource. */
final class SearchGen2ManagedResourceSpec extends org.scalatest.wordspec.AnyWordSpec {
  "managed Elasticsearch Gen2" should {
    "build generations, preserve in-progress resources and reject an old cursor" in {
      withManagedElasticsearch { port =>
          val endpoint = ElasticsearchGen2Endpoint.fromString(s"http://${port.host}:${port.port}").getOrElse(fail("expected managed Elasticsearch endpoint"))
          val transport = ElasticsearchGen2TransportConfig.create(endpoint, Duration.ofSeconds(5), Duration.ofSeconds(60)).getOrElse(fail("expected Elasticsearch transport"))
          val client = ElasticsearchGen2JsonClient.jdk(transport)
          val suffix = UUID.randomUUID().toString.replace("-", "")
          val alias = s"gen2_books_$suffix"
          val prefix = s"${alias}_"
          val docsA = Vector.tabulate(257) { index =>
            bookA.copy(
              isbn = f"managed-$suffix-$index%04d",
              title = s"Scala Managed $index",
              genre = if (index % 2 == 0) "Technology" else "Reference",
            )
          }
          val docsB = docsA.updated(0, bookA.copy(isbn = s"managed-$suffix-0", title = "Changed managed title"))
          val materializedA = managedDocuments(docsA, s"a-$suffix")
          val materializedB = managedDocuments(docsB, s"b-$suffix")
          val materializedC = managedDocuments(Vector.empty, s"c-$suffix")
          val generationA = ElasticsearchGenerationCompiler.compile(fullPolicy, materializedA).getOrElse(fail("expected generation A"))
          val generationB = ElasticsearchGenerationCompiler.compile(fullPolicy, materializedB).getOrElse(fail("expected generation B"))
          val generationC = ElasticsearchGenerationCompiler.compile(fullPolicy, materializedC).getOrElse(fail("expected generation C"))
          val targetA = ElasticsearchGenerationNaming.physicalIndexName(prefix, generationA.identity).getOrElse(fail("expected target A")).value
          val targetB = ElasticsearchGenerationNaming.physicalIndexName(prefix, generationB.identity).getOrElse(fail("expected target B")).value
          val targetC = ElasticsearchGenerationNaming.physicalIndexName(prefix, generationC.identity).getOrElse(fail("expected target C")).value
          val unrelated = s"${prefix}${"f" * 64}"
          assert(targetA != targetB)
          assert(targetA != targetC && targetB != targetC && targetC != unrelated)
          val batching = ElasticsearchBulkBatchingPolicy.create(100, 1024L * 1024L).getOrElse(fail("expected batching"))
          val encodedBatches = ElasticsearchBulkEncoder.encode(generationA.documents, batching).getOrElse(fail("expected bulk batches"))
          assert(encodedBatches.length >= 3, s"expected multi-batch ingestion, got ${encodedBatches.length}")
          val lifecycleConfig = ElasticsearchGenerationLifecycleConfig.create(alias, prefix, batching).getOrElse(fail("expected lifecycle config"))
          val lifecycle = new ElasticsearchGenerationLifecycle(client, lifecycleConfig, Clock.systemUTC())
          val service = new ElasticsearchBaselineService(lifecycle)
          val firstPlan = searchPlan(None)
          val preparedFirst = prepare(firstPlan)
          try {
            lifecycle.activate(generationA) match {
              case Right(_) => ()
              case Left(error) => fail(s"expected activation A, got $error")
            }
            val firstPage = service.search(preparedFirst) match {
              case Right(value) => value
              case Left(error) => fail(s"expected first page, got $error")
            }
            val pinnedCursor = firstPage.nextCursor.getOrElse(fail("expected a lookahead cursor"))
            val secondPage = service.search(prepare(firstPlan.copy(page = PageRequest(Some(SearchCursor.fromTransport(pinnedCursor.opaqueValue)), firstPlan.page.size)))) match {
              case Right(value) => value
              case Left(error) => fail(s"expected second page, got $error")
            }
            assert(firstPage.hits.nonEmpty)
            assert(firstPage.totalHits == 257L)
            assert(firstPage.termsFacet(FacetId("genreFacet"), genre).exists(_.buckets.map(_.count).sum == 257L))
            assert(firstPage.group(GroupId("genre-group")).exists(_.buckets.map(_.matchingDocumentCount).sum == 257L))
            assert(secondPage.hits.nonEmpty)
            assert(secondPage.totalHits == 257L)
            assert(secondPage.hits.map(_.id).toSet.intersect(firstPage.hits.map(_.id).toSet).isEmpty)
            val persistedC = ElasticsearchPersistedGenerationIdentity.fromTrusted(generationC.identity)
            val metadataC = ElasticsearchGenerationMetadata(
              ElasticsearchGenerationMetadataSchemaVersion.Current,
              ElasticsearchGenerationNaming.generationId(generationC.identity),
              Instant.parse("2026-01-01T00:00:00Z"),
              0L,
              persistedC,
            )
            val mappingC = generationC.mapping.json.asObject.getOrElse(fail("expected C mapping")).add("_meta", ElasticsearchGenerationMetadataCodec.encode(metadataC))
            client.putJson(s"/$targetC", Json.obj("mappings" -> Json.fromJsonObject(mappingC))) match {
              case Right(_) => ()
              case Left(error) => fail(s"failed to create in-progress generation C: $error")
            }
            client.putJson(s"/$unrelated", Json.obj("mappings" -> Json.obj("properties" -> Json.obj()))) match {
              case Right(_) => ()
              case Left(error) => fail(s"failed to create unrelated same-prefix index: $error")
            }
            lifecycle.activate(generationB) match {
              case Right(_) => ()
              case Left(error) => fail(s"expected activation B, got $error")
            }
            client.getJson(s"/_alias/$alias") match {
              case Right(json) => assert(json.asObject.exists(_.keys.toVector == Vector(targetB)))
              case Left(error) => fail(s"expected active alias, got $error")
            }
            client.getJson(s"/$targetA/_mapping") match {
              case Left(ElasticsearchGen2TransportError.HttpFailure("GET", _, 404, _)) => ()
              case other => fail(s"expected old generation to be deleted, got $other")
            }
            service.search(prepare(firstPlan.copy(page = PageRequest(Some(SearchCursor.fromTransport(pinnedCursor.opaqueValue)), firstPlan.page.size)))) match {
              case Left(ElasticsearchBaselineServiceError.Lifecycle(ElasticsearchGenerationLifecycleError.StaleGeneration(reference))) =>
                assert(reference == ElasticsearchGenerationReference(targetA))
              case other => fail(s"expected stale pinned generation after cleanup, got $other")
            }
            client.getJson(s"/$targetB/_mapping") match {
              case Right(_) => ()
              case Left(error) => fail(s"expected active generation to remain, got $error")
            }
            client.getJson(s"/$targetC/_mapping") match {
              case Right(_) => ()
              case other => fail(s"expected in-progress generation C to survive, got $other")
            }
            lifecycle.activate(generationB) match {
              case Right(_) => ()
              case Left(error) => fail(s"expected convergent repeat activation B, got $error")
            }
            client.getJson(s"/$unrelated/_mapping") match {
              case Right(_) => ()
              case other => fail(s"expected unrelated same-prefix index to survive, got $other")
            }
          } finally {
            Vector(targetA, targetB, targetC, unrelated).foreach(target => client.delete(s"/$target") match {
              case Right(()) => ()
              case Left(ElasticsearchGen2TransportError.HttpFailure("DELETE", _, 404, _)) => ()
              case Left(error) => fail(s"failed exact managed cleanup for $target: $error")
            })
          }
      }
    }
  }

  "managed Gen2 Qdrant" should {
    "use the injected 1.18.3 endpoint for generation and candidate execution" in {
      withManagedQdrant { port =>
          val endpoint = Gen2HttpEndpoint.fromString(s"http://${port.host}:${port.port}").getOrElse(fail("expected managed Gen2 Qdrant endpoint"))
          val transport = Gen2HttpTransportConfig.create(endpoint, Duration.ofSeconds(5), Duration.ofSeconds(60)).getOrElse(fail("expected Qdrant transport"))
          val http = Gen2JsonHttpClient.jdk(transport)
          val root = http.getJson("/").getOrElse(fail("expected Qdrant root response"))
          assert(root.hcursor.get[String]("version") == Right("1.18.3"))
          val suffix = UUID.randomUUID().toString.replace("-", "")
          val alias = QdrantResourceName.from(s"gen2_neutral_$suffix").getOrElse(fail("expected alias"))
          val prefix = s"${alias.value}_"
          val lifecycleConfig = QdrantGenerationLifecycleConfig.create(alias, prefix).getOrElse(fail("expected lifecycle config"))
          val candidateConfig = QdrantCandidateServiceConfig.create(alias, prefix).getOrElse(fail("expected candidate config"))
          val client = QdrantGen2Client.fromTransport(http)
          val lifecycle = new QdrantGenerationLifecycle(client, lifecycleConfig)
          val generationA = compiledQdrantGeneration(prefix, s"a-$suffix")
          val generationB = compiledQdrantGeneration(prefix, s"b-$suffix")
          assert(generationA.physicalCollectionName != generationB.physicalCollectionName)
          try {
            lifecycle.activate(generationA) match {
              case Right(_) => ()
              case Left(error) => fail(s"expected managed Qdrant activation, got $error")
            }
            val candidateService = new QdrantCandidateService(client, candidateConfig)
            val candidatePlan = CandidatePlan[QdrantTestFixtures.NeutralDocument](SemanticQueryText.from("alpha").getOrElse(fail("expected semantic text")), Vector.empty)
            val prepared = QdrantCandidateRequestCompiler.prepare(QdrantTestFixtures.policy, candidatePlan).getOrElse(fail("expected candidate preparation"))
            val embedding = QdrantEmbeddingResult.from(prepared.embeddingInput, Vector(0.1, 0.2, 0.3)).getOrElse(fail("expected candidate embedding"))
            val request = QdrantCandidateRequestCompiler.complete(prepared, embedding).getOrElse(fail("expected candidate request"))
            val result = candidateService.execute(QdrantTestFixtures.policy, request).getOrElse(fail("expected authorized candidate execution"))
            assert(result.target.value == generationA.physicalCollectionName)
            assert(result.hits.nonEmpty, "expected live Qdrant candidate hits for generation A")
            val generationBTarget = QdrantResourceName.from(generationB.physicalCollectionName).getOrElse(fail("expected generation B name"))
            client.createCollection(generationBTarget, generationB.collectionJson) match {
              case Right(_) => ()
              case Left(error) => fail(s"expected partially prepared generation B collection, got $error")
            }
            lifecycle.activate(generationB) match {
              case Right(_) => ()
              case Left(error) => fail(s"expected managed Qdrant generation B activation, got $error")
            }
            val secondResult = candidateService.execute(QdrantTestFixtures.policy, request).getOrElse(fail("expected authorized candidate execution on B"))
            assert(secondResult.target.value == generationB.physicalCollectionName)
            assert(secondResult.hits.nonEmpty, "expected live Qdrant candidate hits for converged generation B")
            assert(client.getCollection(QdrantResourceName.from(generationA.physicalCollectionName).getOrElse(fail("expected generation A name"))).isRight)
            val activeTargets = decodeQdrantAliasTargets(client.listAliases().getOrElse(fail("expected aliases after B activation")), alias.value)
            assert(activeTargets == Vector(generationB.physicalCollectionName))
          } finally {
            client.updateAliases(io.circe.Json.obj("actions" -> io.circe.Json.arr(
              io.circe.Json.obj("delete_alias" -> io.circe.Json.obj("alias_name" -> io.circe.Json.fromString(alias.value))),
            ))) match {
              case Right(_) => ()
              case Left(Gen2HttpTransportError.HttpFailure(_, _, 404, _)) => ()
              case Left(error) => fail(s"failed exact Qdrant alias cleanup: $error")
            }
            Vector(generationA.physicalCollectionName, generationB.physicalCollectionName).foreach { target =>
              http.delete(s"/collections/$target") match {
                case Right(_) => ()
                case Left(Gen2HttpTransportError.HttpFailure(_, _, 404, _)) => ()
                case Left(error) => fail(s"failed exact Qdrant cleanup for '$target': $error")
              }
            }
            (): Unit
          }
      }
    }
  }

  private def managedDocuments(documents: Vector[BookDocument], marker: String): MaterializedSearchDocuments[Unit, BookDocument] =
    MaterializedSearchDocuments(
      VersionedSnapshot((), ContentFingerprint(s"source-$marker"), None, Instant.parse("2026-01-01T00:00:00Z")),
      documents,
      ProjectedDocumentsFingerprint(s"projected-$marker"),
      ProjectionFormatVersion("projection-v1"),
    )

  private def prepare(plan: SearchPlan[BookDocument]): PreparedElasticsearchSearchRequest[BookDocument, String] = {
    val bound = SearchCursorEnvelope.bind(plan, CanonicalPlanView(fullPolicy.contractFingerprint)).getOrElse(fail("expected bound plan"))
    ElasticsearchSearchRequestCompiler.compile(fullPolicy, bound).getOrElse(fail("expected prepared request"))
  }

  private def withManagedElasticsearch[A](f: ElasticsearchPortCfg => A): A = {
    val module = new ModuleDef {
      make[AppConfig].fromValue(AppConfig.provided(ConfigFactory.load("common-reference.conf").resolve()))
      include(LogIO2Module[IO]())
      make[IzLogger].fromValue(IzLogger())
      include(ElasticsearchDockerPlugin.dockerModule[IO])
    }
    val effect = Injector[Task]().produce(
      bindings = module,
      roots = Roots.target[ElasticsearchPortCfg],
      activation = Activation(Scene -> Scene.Managed),
      locatorPrivacy = LocatorPrivacy.PublicByDefault,
    ).use { locator =>
      ZIO.attemptBlocking(f(locator.get[ElasticsearchPortCfg]))
    }
    Unsafe.unsafe { implicit unsafe => Runtime.default.unsafe.run(effect).getOrThrowFiberFailure() }
  }

  private def withManagedQdrant[A](f: QdrantGen2PortCfg => A): A = {
    val module = new ModuleDef {
      make[AppConfig].fromValue(AppConfig.provided(ConfigFactory.load("common-reference.conf").resolve()))
      include(LogIO2Module[IO]())
      make[IzLogger].fromValue(IzLogger())
      include(QdrantGen2DockerPlugin.dockerModule[IO])
    }
    val effect = Injector[Task]().produce(
      bindings = module,
      roots = Roots.target[QdrantGen2PortCfg],
      activation = Activation(Scene -> Scene.Managed),
      locatorPrivacy = LocatorPrivacy.PublicByDefault,
    ).use { locator =>
      ZIO.attemptBlocking(f(locator.get[QdrantGen2PortCfg]))
    }
    Unsafe.unsafe { implicit unsafe => Runtime.default.unsafe.run(effect).getOrThrowFiberFailure() }
  }

  private def searchPlan(cursor: Option[SearchCursor]): SearchPlan[BookDocument] = {
    val facet = FacetRequest.Terms(
      FacetId("genreFacet"),
      genre,
      FacetSize.from(10).getOrElse(fail("expected facet size")),
      TermsFacetOrder.KeyAsc,
      FacetCountingPolicy.AllAppliedHardFilters,
    )
    val group = GroupRequest[BookDocument, String](
      GroupId("genre-group"),
      genre,
      GroupSize.from(10).getOrElse(fail("expected group size")),
      RepresentativeRequest.IdentityOnly(),
      Vector(GroupMetricRequest.BestScore(GroupMetricId("best-score"))),
      Vector(GroupOrder.Metric(GroupMetricId("best-score"), SortDirection.Desc), GroupOrder.Key(SortDirection.Asc)),
      GroupPrecisionPolicy.RequireExact,
    )
    // The communication proof intentionally uses the framework's explicit match_all baseline.
    // Group representatives are required to expose a score by the backend group contract; a
    // match_all query gives every representative the deterministic score 1.0 while this test
    // remains focused on pagination, facets and groups over the managed wire path.
    SearchPlan(None, Vector.empty, Vector.empty, Vector.empty, PageRequest(cursor, PageSize.from(10).getOrElse(fail("expected page size"))), Vector(facet), Vector(group), PlanDiagnostics.empty)
  }

  private def compiledQdrantGeneration(prefix: String, marker: String): QdrantCompiledGeneration = {
    val materialized = QdrantTestFixtures.materialized.copy(projectedDocumentsFingerprint = ProjectedDocumentsFingerprint(s"managed-$marker"))
    val prepared = QdrantGenerationCompiler.prepare(QdrantTestFixtures.policy, materialized).getOrElse(fail("expected Qdrant preparation"))
    val embeddings = prepared.points.map(point => QdrantEmbeddingResult.from(point.embeddingInput, Vector(0.1, 0.2, 0.3)).getOrElse(fail("expected point embedding")))
    QdrantGenerationCompiler.complete(prepared, embeddings, prefix).getOrElse(fail("expected Qdrant generation"))
  }

  private def decodeQdrantAliasTargets(raw: io.circe.Json, alias: String): Vector[String] = {
    SearchGen2ResourceInventorySupport.decodeQdrantAliases(raw)
      .getOrElse(fail("expected strict Qdrant alias response"))
      .filter(_.alias == alias)
      .map(_.collection)
      .distinct
      .sorted
  }
}
