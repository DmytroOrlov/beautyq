package leaderboard.search.gen2.elasticsearch

import leaderboard.search.gen2.contract.*
import leaderboard.search.gen2.core.materialization.*
import leaderboard.search.gen2.core.plan.*
import leaderboard.search.gen2.elasticsearch.lifecycle.*
import org.scalatest.wordspec.AnyWordSpec
import io.circe.Json

import java.time.{Clock, Duration, Instant}
import java.util.UUID

/** Default-local end-to-end resource proof. It has no environment gate and cancels only when the
  * localhost Elasticsearch endpoint cannot be reached. */
final class ElasticsearchLocalResourceSpec extends AnyWordSpec {
  import ElasticsearchTestFixtures.*

  "the local Elasticsearch resource" should {
    "build two generations, clean the first, preserve the second and reject its stale cursor" in {
      val endpoint = ElasticsearchGen2Endpoint.fromString("http://localhost:9200").getOrElse(fail("expected local endpoint"))
      val transport = ElasticsearchGen2TransportConfig.create(endpoint, Duration.ofSeconds(1), Duration.ofSeconds(10)).getOrElse(fail("expected config"))
      val client = ElasticsearchGen2JsonClient.jdk(transport)
      client.getJson("/") match {
        case Left(_: ElasticsearchGen2TransportError.ConnectionFailed) => cancel("local Elasticsearch is unavailable")
        case Left(error) => fail(s"local Elasticsearch returned a non-connectivity failure: $error")
        case Right(_) =>
          val suffix = UUID.randomUUID().toString.replace("-", "")
          val alias = s"gen2_books_$suffix"
          val prefix = s"${alias}_"
          val documents = Vector.tabulate(257) { index =>
            bookA.copy(isbn = f"book-$index%04d", title = s"Scala Book $index", genre = if (index % 2 == 0) "Technology" else "Reference")
          }
          val materializedA = MaterializedSearchDocuments(
            VersionedSnapshot((), ContentFingerprint(s"source-$suffix"), None, Instant.parse("2026-01-01T00:00:00Z")),
            documents,
            ProjectedDocumentsFingerprint(s"projected-$suffix"),
            ProjectionFormatVersion("projection-v1"),
          )
          val materializedB = MaterializedSearchDocuments(
            VersionedSnapshot((), ContentFingerprint(s"source-$suffix-b"), None, Instant.parse("2026-01-01T00:00:00Z")),
            documents,
            ProjectedDocumentsFingerprint(s"projected-$suffix-b"),
            ProjectionFormatVersion("projection-v1"),
          )
          val materializedC = MaterializedSearchDocuments[Unit, BookDocument](
            VersionedSnapshot((), ContentFingerprint(s"source-$suffix-c"), None, Instant.parse("2026-01-01T00:00:00Z")),
            Vector.empty[BookDocument],
            ProjectedDocumentsFingerprint(s"projected-$suffix-c"),
            ProjectionFormatVersion("projection-v1"),
          )
          val generationA = ElasticsearchGenerationCompiler.compile(fullPolicy, materializedA).getOrElse(fail("expected generation A"))
          val generationB = ElasticsearchGenerationCompiler.compile(fullPolicy, materializedB).getOrElse(fail("expected generation B"))
          val generationC = ElasticsearchGenerationCompiler.compile(fullPolicy, materializedC).getOrElse(fail("expected generation C"))
          val physicalA = ElasticsearchGenerationNaming.physicalIndexName(prefix, generationA.identity).map(_.value).getOrElse(fail("expected physical A name"))
          val physicalB = ElasticsearchGenerationNaming.physicalIndexName(prefix, generationB.identity).map(_.value).getOrElse(fail("expected physical B name"))
          val physicalC = ElasticsearchGenerationNaming.physicalIndexName(prefix, generationC.identity).map(_.value).getOrElse(fail("expected physical C name"))
          assert(physicalA != physicalB && physicalA != physicalC && physicalB != physicalC)
          val unrelated = s"${prefix}${"f".repeat(64)}"
          assert(physicalA != unrelated && physicalB != unrelated)
          val batching = ElasticsearchBulkBatchingPolicy.create(100, 1024L * 1024L).getOrElse(fail("expected batching"))
          val batches = ElasticsearchBulkEncoder.encode(generationA.documents, batching).getOrElse(fail("expected bulk batches"))
          assert(batches.length >= 3)
          val lifecycleConfig = ElasticsearchGenerationLifecycleConfig.create(alias, prefix, batching).getOrElse(fail("expected lifecycle config"))
          val lifecycle = new ElasticsearchGenerationLifecycle(client, lifecycleConfig, Clock.systemUTC())
          val service = new ElasticsearchBaselineService(lifecycle)

          try {
            val resolved = lifecycle.activate(generationA).getOrElse(fail("expected activation A"))
            assert(resolved.physicalTarget == ElasticsearchSearchTarget(physicalA))

            val facet = FacetRequest.Terms(FacetId("genreFacet"), genre, FacetSize.from(10).getOrElse(fail("expected facet size")), TermsFacetOrder.KeyAsc, FacetCountingPolicy.AllAppliedHardFilters)
            val firstPlan = searchPlan(None, Vector(facet))
            val first = service.search(prepare(firstPlan)).getOrElse(fail("expected first page"))
            assert(first.totalHits == 257L)
            val genreResult = first.termsFacet(FacetId("genreFacet"), genre).getOrElse(fail("expected genre facet"))
            assert(genreResult.buckets.map(_.count).sum == 257L)
            val cursor = first.nextCursor.getOrElse(fail("expected next cursor"))

            val second = service.search(prepare(searchPlan(Some(SearchCursor.fromTransport(cursor.opaqueValue)), Vector(facet)))).getOrElse(fail("expected second page"))
            assert(second.totalHits == 257L)
            assert(second.hits.map(_.id).toSet.intersect(first.hits.map(_.id).toSet).isEmpty)

            val persistedC = ElasticsearchPersistedGenerationIdentity.fromTrusted(generationC.identity)
            val metadataC = ElasticsearchGenerationMetadata(
              ElasticsearchGenerationMetadataSchemaVersion.Current,
              ElasticsearchGenerationNaming.generationId(persistedC),
              Instant.parse("2026-01-01T00:00:00Z"),
              0L,
              persistedC,
            )
            val mappingC = generationC.mapping.json.asObject.getOrElse(fail("expected C mapping")).add("_meta", ElasticsearchGenerationMetadataCodec.encode(metadataC))
            client.putJson(s"/$physicalC", Json.obj("mappings" -> Json.fromJsonObject(mappingC))) match {
              case Right(_) => succeed
              case Left(error) => fail(s"failed to create valid in-progress generation C: $error")
            }

            lifecycle.activate(generationB).getOrElse(fail("expected activation B"))
            client.getJson(s"/_alias/$alias") match {
              case Right(json) =>
                assert(json.asObject.exists(_.keys.toVector == Vector(physicalB)))
              case Left(error) => fail(s"expected alias after activation B, got $error")
            }
            client.getJson(s"/$physicalA/_mapping") match {
              case Left(ElasticsearchGen2TransportError.HttpFailure("GET", _, 404, _)) => succeed
              case other => fail(s"expected generation A to be deleted, got $other")
            }
            client.getJson(s"/$physicalB/_mapping") match {
              case Right(_) => succeed
              case Left(error) => fail(s"expected generation B to remain, got $error")
            }
            client.postJson("/_aliases", Json.obj("actions" -> Json.arr(
              Json.obj("add" -> Json.obj("index" -> Json.fromString(physicalB), "alias" -> Json.fromString(s"$alias--superseded")))
            ))) match {
              case Right(_) => succeed
              case Left(error) => fail(s"failed to install a stale superseded marker for reactivation proof: $error")
            }
            lifecycle.activate(generationB).getOrElse(fail("expected reactivation of generation B"))
            client.getJson(s"/_alias/$alias--superseded") match {
              case Left(ElasticsearchGen2TransportError.HttpFailure("GET", _, 404, _)) => succeed
              case other => fail(s"expected reactivation to remove B's stale superseded marker, got $other")
            }
            client.getJson(s"/$physicalC/_mapping") match {
              case Right(_) => succeed
              case Left(error) => fail(s"expected in-progress generation C to survive, got $error")
            }
            val secondGenerationPage = service.search(prepare(searchPlan(None, Vector(facet)))).getOrElse(fail("expected generation B search"))
            assert(secondGenerationPage.totalHits == 257L)
            service.search(prepare(searchPlan(Some(SearchCursor.fromTransport(cursor.opaqueValue)), Vector(facet)))) match {
              case Left(ElasticsearchBaselineServiceError.Lifecycle(ElasticsearchGenerationLifecycleError.StaleGeneration(reference))) =>
                assert(reference == ElasticsearchGenerationReference(physicalA))
              case other => fail(s"expected stale generation cursor, got $other")
            }

            client.putJson(s"/$unrelated", Json.obj("mappings" -> Json.obj("properties" -> Json.obj()))) match {
              case Right(_) => succeed
              case Left(error) => fail(s"failed to create unrelated same-prefix index: $error")
            }
            lifecycle.activate(generationB).getOrElse(fail("expected repeat activation B"))
            client.getJson(s"/$unrelated/_mapping") match {
              case Right(_) => succeed
              case Left(error) => fail(s"expected unrelated same-prefix index to survive, got $error")
            }
          } finally {
            Vector(physicalA, physicalB, physicalC, unrelated).foreach { physical =>
            client.delete(s"/$physical") match {
                case Right(()) => ()
                case Left(ElasticsearchGen2TransportError.HttpFailure("DELETE", _, 404, _)) => ()
                case Left(error) => fail(s"failed exact cleanup of '$physical': $error")
            }
            }
          }
      }
    }
  }

  private def searchPlan(cursor: Option[SearchCursor], facets: Vector[FacetRequest[BookDocument]]): SearchPlan[BookDocument] =
    SearchPlan(None, Vector.empty, Vector.empty, Vector.empty, PageRequest(cursor, PageSize.from(10).getOrElse(fail("expected page size"))), facets, Vector.empty, PlanDiagnostics.empty)

  private def prepare(plan: SearchPlan[BookDocument]): PreparedElasticsearchSearchRequest[BookDocument, String] = {
    val bound = SearchCursorEnvelope.bind(plan, CanonicalPlanView(fullPolicy.contractFingerprint)).getOrElse(fail("expected bound plan"))
    ElasticsearchSearchRequestCompiler.compile(fullPolicy, bound).getOrElse(fail("expected prepared request"))
  }
}
