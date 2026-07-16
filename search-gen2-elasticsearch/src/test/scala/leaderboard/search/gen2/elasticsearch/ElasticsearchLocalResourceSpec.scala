package leaderboard.search.gen2.elasticsearch

import leaderboard.search.gen2.contract.*
import leaderboard.search.gen2.core.materialization.*
import leaderboard.search.gen2.core.plan.*
import leaderboard.search.gen2.elasticsearch.lifecycle.*
import org.scalatest.wordspec.AnyWordSpec

import java.time.{Clock, Duration, Instant}
import java.util.UUID

/** Default-local end-to-end resource proof. It has no environment gate and cancels only when the
  * localhost Elasticsearch endpoint cannot be reached. */
final class ElasticsearchLocalResourceSpec extends AnyWordSpec {
  import ElasticsearchTestFixtures.*

  "the local Elasticsearch resource" should {
    "build, activate, search, facet and continue by cursor through the Gen2 lifecycle" in {
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
          val materialized = MaterializedSearchDocuments(
            VersionedSnapshot((), ContentFingerprint(s"source-$suffix"), None, Instant.parse("2026-01-01T00:00:00Z")),
            documents,
            ProjectedDocumentsFingerprint(s"projected-$suffix"),
            ProjectionFormatVersion("projection-v1"),
          )
          val generation = ElasticsearchGenerationCompiler.compile(fullPolicy, materialized).getOrElse(fail("expected generation"))
          val physical = ElasticsearchGenerationNaming.physicalIndexName(prefix, generation.identity).map(_.value).getOrElse(fail("expected physical name"))
          val batching = ElasticsearchBulkBatchingPolicy.create(100, 1024L * 1024L).getOrElse(fail("expected batching"))
          val batches = ElasticsearchBulkEncoder.encode(generation.documents, batching).getOrElse(fail("expected bulk batches"))
          assert(batches.length >= 3)
          val lifecycleConfig = ElasticsearchGenerationLifecycleConfig.create(alias, prefix, ElasticsearchGenerationRetentionPolicy.KeepAll, batching).getOrElse(fail("expected lifecycle config"))
          val lifecycle = new ElasticsearchGenerationLifecycle(client, lifecycleConfig, Clock.systemUTC())
          val service = new ElasticsearchBaselineService(lifecycle)

          try {
            val resolved = lifecycle.activate(generation).getOrElse(fail("expected activation"))
            assert(resolved.physicalTarget == ElasticsearchSearchTarget(physical))

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
          } finally {
            client.delete(s"/$physical") match {
              case Right(()) => ()
              case Left(ElasticsearchGen2TransportError.HttpFailure("DELETE", _, 404, _)) => ()
              case Left(error) => fail(s"failed exact cleanup of '$physical': $error")
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
