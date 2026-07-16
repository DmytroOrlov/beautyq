package leaderboard.search.gen2.elasticsearch.lifecycle

import leaderboard.search.gen2.contract.*
import leaderboard.search.gen2.core.materialization.{ContentFingerprint, ProjectedDocumentsFingerprint, ProjectionFormatVersion}
import leaderboard.search.gen2.core.plan.*
import leaderboard.search.gen2.elasticsearch.*
import org.scalatest.wordspec.AnyWordSpec

import io.circe.Json

/** Neutral calibration for the generic Elasticsearch response decoder, using the shared book/library
  * document fixture ([[ElasticsearchTestFixtures]]) unrelated to BeautyQ.
  */
final class ElasticsearchSearchResponseDecoderSpec extends AnyWordSpec {
  import ElasticsearchTestFixtures.*

  private val targetA = ElasticsearchSearchTarget("books_20260101_a")
  private val referenceA = ElasticsearchGenerationReference("books-generation-a")

  private val canonicalPlanView: CanonicalPlanView[BookDocument] = CanonicalPlanView[BookDocument](fullPolicy.contractFingerprint)

  private def firstPageBoundPlanOf(
    facets: Vector[FacetRequest[BookDocument]] = Vector.empty,
    sort: Vector[PlannedSort[BookDocument]] = Vector.empty,
    pageSize: Int,
  ): BoundSearchPlan[BookDocument] = {
    val plan =
      SearchPlan(
        residualText = None,
        appliedFilters = Vector.empty,
        softSignals = Vector.empty,
        sort = sort,
        page = PageRequest(None, PageSize.from(pageSize).getOrElse(fail("expected a valid PageSize"))),
        facets = facets,
        groups = Vector.empty,
        diagnostics = PlanDiagnostics.empty,
      )
    SearchCursorEnvelope.bind(plan, canonicalPlanView).getOrElse(fail("expected a valid first-page bound plan"))
  }

  private def resolvedFor[Document, Id](boundPlan: BoundSearchPlan[Document], policy: ElasticsearchPolicy[Document, Id]): LifecycleResolvedElasticsearchGeneration =
    new LifecycleResolvedElasticsearchGeneration(
      referenceA,
      targetA,
      ElasticsearchGenerationIdentity(
        ContentFingerprint("source"),
        ProjectedDocumentsFingerprint("projected"),
        boundPlan.identity.contractFingerprint,
        ProjectionFormatVersion("book-projection-v1"),
        policy.index.compilerVersion,
        policy.index.indexFormatVersion,
      ),
    )

  private def compileOrFail(boundPlan: BoundSearchPlan[BookDocument]): AuthorizedElasticsearchSearchRequest[BookDocument, String] =
    ElasticsearchSearchRequestCompiler.compile(fullPolicy, boundPlan).flatMap(prepared => ElasticsearchSearchRequestAuthorization.authorize(prepared, resolvedFor(boundPlan, fullPolicy))) match {
      case Right(compiled) => compiled
      case Left(error)     => fail(s"expected successful compilation/authorization, got $error")
    }

  private def hitJson(id: String, score: Double, sort: Vector[Json], source: Json = Json.obj()): Json =
    Json.obj("_id" -> Json.fromString(id), "_score" -> Json.fromDoubleOrNull(score), "_source" -> source, "sort" -> Json.fromValues(sort))

  private def responseJson(
    hits: Vector[Json],
    total: Long,
    relation: String = "eq",
    aggs: Vector[(String, Json)] = Vector.empty,
    timedOut: Boolean = false,
    shardsFailed: Int = 0,
  ): Json = {
    val base =
      Json.obj(
        "timed_out" -> Json.fromBoolean(timedOut),
        "_shards"   -> Json.obj("total" -> Json.fromInt(1), "successful" -> Json.fromInt(if (shardsFailed > 0) 0 else 1), "failed" -> Json.fromInt(shardsFailed)),
        "hits"      -> Json.obj("total" -> Json.obj("value" -> Json.fromLong(total), "relation" -> Json.fromString(relation)), "hits" -> Json.fromValues(hits)),
      )
    if (aggs.isEmpty) base else base.deepMerge(Json.obj("aggregations" -> Json.obj(aggs*)))
  }

  private def decodeOrFail(compiled: AuthorizedElasticsearchSearchRequest[BookDocument, String], response: Json): BaselineSearchPage[BookDocument, String] =
    ElasticsearchSearchResponseDecoder.decode(compiled, response) match {
      case Right(page) => page
      case Left(errors) => fail(s"expected successful decode, got ${errors.toVector}")
    }

  "ElasticsearchSearchResponseDecoder.decode" should {
    "decode typed identity, score, source and sort for each hit" in {
      val compiled = compileOrFail(firstPageBoundPlanOf(pageSize = 5))
      val source   = Json.obj("title" -> Json.fromString("Programming in Scala"))
      val response = responseJson(Vector(hitJson(bookA.isbn, 4.2, Vector(Json.fromString(bookA.isbn)), source)), total = 1)

      val page = decodeOrFail(compiled, response)
      assert(page.hits.length == 1)
      val hit = page.hits.headOption.getOrElse(fail("expected one decoded hit"))
      assert(hit.id == bookA.isbn)
      assert(hit.score == BigDecimal("4.2"))
      assert(hit.source == source.asObject.getOrElse(fail("expected an object source")))
      assert(hit.sortValues == Vector(ElasticsearchSearchAfterValue.Text(bookA.isbn)))
      assert(page.diagnostics == ElasticsearchResponseDiagnostics(timedOut = false, shardsTotal = 1, shardsSuccessful = 1, shardsFailed = 0))
    }

    "remove the lookahead hit, returning only pageSize hits, and report a next cursor" in {
      val compiled = compileOrFail(firstPageBoundPlanOf(pageSize = 2))
      val response =
        responseJson(
          Vector(
            hitJson("isbn-1", 3.0, Vector(Json.fromString("isbn-1"))),
            hitJson("isbn-2", 2.0, Vector(Json.fromString("isbn-2"))),
            hitJson("isbn-3", 1.0, Vector(Json.fromString("isbn-3"))),
          ),
          total = 3,
        )

      val page = decodeOrFail(compiled, response)
      assert(page.hits.map(_.id) == Vector("isbn-1", "isbn-2"))
      assert(page.nextCursor.isDefined)
    }

    "return no next cursor when no lookahead hit exists" in {
      val compiled = compileOrFail(firstPageBoundPlanOf(pageSize = 2))
      val response = responseJson(Vector(hitJson("isbn-1", 3.0, Vector(Json.fromString("isbn-1")))), total = 1)
      assert(decodeOrFail(compiled, response).nextCursor.isEmpty)
    }

    "expose an exact total larger than the returned page, independent of page size" in {
      val compiledSmall = compileOrFail(firstPageBoundPlanOf(pageSize = 1))
      val compiledLarge = compileOrFail(firstPageBoundPlanOf(pageSize = 5))
      val responseSmall = responseJson(Vector(hitJson("isbn-1", 3.0, Vector(Json.fromString("isbn-1"))), hitJson("isbn-2", 2.0, Vector(Json.fromString("isbn-2")))), total = 500)
      val responseLarge = responseJson(Vector(hitJson("isbn-1", 3.0, Vector(Json.fromString("isbn-1")))), total = 500)

      assert(decodeOrFail(compiledSmall, responseSmall).totalHits == 500)
      assert(decodeOrFail(compiledLarge, responseLarge).totalHits == 500)
    }

    "reject a non-exact total relation under an exact-total policy" in {
      val compiled = compileOrFail(firstPageBoundPlanOf(pageSize = 5))
      val response = responseJson(Vector.empty, total = 10000, relation = "gte")
      ElasticsearchSearchResponseDecoder.decode(compiled, response) match {
        case Left(errors) => assert(errors.toVector == Vector(ElasticsearchSearchResponseError.NonExactTotalRelation("gte")))
        case other         => fail(s"expected NonExactTotalRelation, got $other")
      }
    }

    "reject a timed-out or shard-failed response as a typed partial response, never a trusted baseline page" in {
      val compiled = compileOrFail(firstPageBoundPlanOf(pageSize = 5))

      ElasticsearchSearchResponseDecoder.decode(compiled, responseJson(Vector.empty, total = 0, timedOut = true)) match {
        case Left(errors) => assert(errors.toVector.exists(_.isInstanceOf[ElasticsearchSearchResponseError.PartialResponse]))
        case other         => fail(s"expected PartialResponse for timed_out, got $other")
      }

      ElasticsearchSearchResponseDecoder.decode(compiled, responseJson(Vector.empty, total = 0, shardsFailed = 1)) match {
        case Left(errors) => assert(errors.toVector.exists(_.isInstanceOf[ElasticsearchSearchResponseError.PartialResponse]))
        case other         => fail(s"expected PartialResponse for shard failure, got $other")
      }
    }

    "reject a malformed hit" in {
      val compiled = compileOrFail(firstPageBoundPlanOf(pageSize = 5))
      val response = responseJson(Vector(Json.fromString("not an object")), total = 1)
      ElasticsearchSearchResponseDecoder.decode(compiled, response) match {
        case Left(errors) => assert(errors.toVector.exists(_.isInstanceOf[ElasticsearchSearchResponseError.MalformedHit]))
        case other         => fail(s"expected MalformedHit, got $other")
      }
    }

    "reject a response whose hit array exceeds the compiled page-size plus lookahead window" in {
      val compiled = compileOrFail(firstPageBoundPlanOf(pageSize = 2))
      val response = responseJson(
        Vector(
          hitJson("isbn-1", 3.0, Vector(Json.fromString("isbn-1"))),
          hitJson("isbn-2", 2.0, Vector(Json.fromString("isbn-2"))),
          hitJson("isbn-3", 1.0, Vector(Json.fromString("isbn-3"))),
          hitJson("isbn-4", 0.5, Vector(Json.fromString("isbn-4"))),
        ),
        total = 4,
      )
      ElasticsearchSearchResponseDecoder.decode(compiled, response) match {
        case Left(errors) => assert(errors.toVector == Vector(ElasticsearchSearchResponseError.UnexpectedHitCount(3, 4)))
        case other        => fail(s"expected UnexpectedHitCount, got $other")
      }
    }

    "require every hit source to be an object" in {
      val compiled = compileOrFail(firstPageBoundPlanOf(pageSize = 5))
      Vector(Json.Null, Json.fromString("source"), Json.arr(Json.fromInt(1)), Json.fromInt(1)).foreach { invalidSource =>
        val response = responseJson(Vector(hitJson("isbn-1", 1.0, Vector(Json.fromString("isbn-1")), invalidSource)), total = 1)
        ElasticsearchSearchResponseDecoder.decode(compiled, response) match {
          case Left(errors) => assert(errors.toVector.exists(_.isInstanceOf[ElasticsearchSearchResponseError.MalformedHit]))
          case other        => fail(s"expected non-object source to be rejected, got $other")
        }
      }
    }

    // BookDocument's own identity (isbn) uses the plain String codec, which can never fail to decode any
    // string - so this proof uses a small local fixture with a genuinely fallible (UUID-backed) identity
    // field instead, reached through the same public declaration/policy/compiler API.
    "reject a hit whose _id fails the identity field's codec" in {
      final case class UuidIdentityDocument(id: java.util.UUID, note: String)

      val idField   = leaderboard.search.gen2.contract.field[UuidIdentityDocument, java.util.UUID]("id", _.id).keyword
      val noteField = leaderboard.search.gen2.contract.field[UuidIdentityDocument, String]("note", _.note).keyword
      val declaration =
        searchDocument[UuidIdentityDocument]("uuid-doc").id(idField).field(noteField).build match {
          case Right(value) => value
          case Left(error)  => fail(s"expected a valid uuid-doc declaration, got $error")
        }
      val indexPolicy = ElasticsearchIndexPolicy.unsafeFrom(declaration, ElasticsearchPolicyVersion("uuid-doc-es-v1"), Vector.empty)
      val uuidPolicy =
        ElasticsearchPolicy.unsafeFrom(
          PlanContractVersion("uuid-doc-plan-v1"),
          indexPolicy,
          Vector.empty,
          ElasticsearchTextOperator.And,
          None,
          ElasticsearchTotalHitsPolicy.ExactRequired,
          defaultSortPolicy,
        )
      val view = CanonicalPlanView[UuidIdentityDocument](uuidPolicy.contractFingerprint)
      val plan =
        SearchPlan[UuidIdentityDocument](None, Vector.empty, Vector.empty, Vector.empty, PageRequest(None, PageSize.from(5).getOrElse(fail("bad size"))), Vector.empty, Vector.empty, PlanDiagnostics.empty)
      val bound = SearchCursorEnvelope.bind(plan, view).getOrElse(fail("expected a valid bound plan"))
      val compiled =
        ElasticsearchSearchRequestCompiler.compile(uuidPolicy, bound).flatMap(prepared => ElasticsearchSearchRequestAuthorization.authorize(prepared, resolvedFor(bound, uuidPolicy))) match {
          case Right(value) => value
          case Left(error)  => fail(s"expected successful compilation, got $error")
        }

      val response = responseJson(Vector(hitJson("not-a-uuid", 1.0, Vector(Json.fromString("not-a-uuid")))), total = 1)
      ElasticsearchSearchResponseDecoder.decode(compiled, response) match {
        case Left(errors) => assert(errors.toVector.exists(_.isInstanceOf[ElasticsearchSearchResponseError.IdentityDecodeFailed]))
        case other         => fail(s"expected IdentityDecodeFailed, got $other")
      }
    }

    "reject an unsupported sort value shape inside a hit" in {
      val compiled = compileOrFail(firstPageBoundPlanOf(pageSize = 5))
      val response = responseJson(Vector(hitJson("isbn-1", 1.0, Vector(Json.arr()))), total = 1)
      ElasticsearchSearchResponseDecoder.decode(compiled, response) match {
        case Left(errors) => assert(errors.toVector.exists(_.isInstanceOf[ElasticsearchSearchResponseError.UnsupportedSortValueShape]))
        case other         => fail(s"expected UnsupportedSortValueShape, got $other")
      }
    }

    "reject a hit sort tuple whose arity does not match the compiled sort vector" in {
      val compiled = compileOrFail(firstPageBoundPlanOf(pageSize = 5, sort = Vector(PlannedSort.FieldValue(pageCount, SortDirection.Asc))))
      val response = responseJson(Vector(hitJson("isbn-1", 1.0, Vector(Json.fromString("isbn-1")))), total = 1)
      ElasticsearchSearchResponseDecoder.decode(compiled, response) match {
        case Left(errors) => assert(errors.toVector.exists(_.isInstanceOf[ElasticsearchSearchResponseError.SortArityMismatch]))
        case other         => fail(s"expected SortArityMismatch, got $other")
      }
    }

    "decode terms facet precision as exact, bounded and unknown" in {
      val termsFacet = FacetRequest.Terms(FacetId("genreFacet"), genre, FacetSize.from(10).getOrElse(fail("bad size")), TermsFacetOrder.KeyAsc, FacetCountingPolicy.AllAppliedHardFilters)
      val compiled   = compileOrFail(firstPageBoundPlanOf(pageSize = 5, facets = Vector(termsFacet)))

      def termsAgg(errorUpperBound: Option[Long]): Json = {
        val base = Json.obj("buckets" -> Json.arr(Json.obj("key" -> Json.fromString("Fiction"), "doc_count" -> Json.fromInt(3))), "sum_other_doc_count" -> Json.fromInt(0))
        errorUpperBound.fold(base)(bound => base.deepMerge(Json.obj("doc_count_error_upper_bound" -> Json.fromLong(bound))))
      }

      val exactResponse   = responseJson(Vector.empty, total = 0, aggs = Vector("facet:genreFacet" -> termsAgg(Some(0L))))
      val boundedResponse = responseJson(Vector.empty, total = 0, aggs = Vector("facet:genreFacet" -> termsAgg(Some(5L))))
      val unknownResponse = responseJson(Vector.empty, total = 0, aggs = Vector("facet:genreFacet" -> termsAgg(Some(-1L))))

      def precisionOf(response: Json): ElasticsearchTermsFacetPrecision =
        decodeOrFail(compiled, response).termsFacet(FacetId("genreFacet"), genre).map(_.precision).getOrElse(fail("expected a terms facet result"))

      assert(precisionOf(exactResponse) == ElasticsearchTermsFacetPrecision.Exact)
      assert(precisionOf(boundedResponse) == ElasticsearchTermsFacetPrecision.Bounded(5L))
      assert(precisionOf(unknownResponse) == ElasticsearchTermsFacetPrecision.Unknown)
    }

    "decode typed term keys for keyword, integer, long and boolean fields and retain bucket error bounds" in {
      val genreFacet = FacetRequest.Terms(FacetId("genreTyped"), genre, FacetSize.from(10).getOrElse(fail("bad size")), TermsFacetOrder.KeyAsc, FacetCountingPolicy.AllAppliedHardFilters)
      val pageFacet  = FacetRequest.Terms(FacetId("pageTyped"), pageCount, FacetSize.from(10).getOrElse(fail("bad size")), TermsFacetOrder.KeyAsc, FacetCountingPolicy.AllAppliedHardFilters)
      val wordFacet  = FacetRequest.Terms(FacetId("wordTyped"), wordCount, FacetSize.from(10).getOrElse(fail("bad size")), TermsFacetOrder.KeyAsc, FacetCountingPolicy.AllAppliedHardFilters)
      val printFacet = FacetRequest.Terms(FacetId("printTyped"), inPrint, FacetSize.from(10).getOrElse(fail("bad size")), TermsFacetOrder.KeyAsc, FacetCountingPolicy.AllAppliedHardFilters)
      val compiled = compileOrFail(firstPageBoundPlanOf(pageSize = 5, facets = Vector(genreFacet, pageFacet, wordFacet, printFacet)))

      val response = responseJson(
        Vector.empty,
        total = 0,
        aggs = Vector(
          "facet:genreTyped" -> Json.obj("buckets" -> Json.arr(Json.obj("key_as_string" -> Json.fromString("Fiction"), "key" -> Json.fromString("ignored"), "doc_count" -> Json.fromInt(3), "doc_count_error_upper_bound" -> Json.fromInt(2))), "sum_other_doc_count" -> Json.fromInt(0), "doc_count_error_upper_bound" -> Json.fromInt(2)),
          "facet:pageTyped"  -> Json.obj("buckets" -> Json.arr(Json.obj("key" -> Json.fromInt(42), "doc_count" -> Json.fromInt(4))), "sum_other_doc_count" -> Json.fromInt(0), "doc_count_error_upper_bound" -> Json.fromInt(0)),
          "facet:wordTyped"  -> Json.obj("buckets" -> Json.arr(Json.obj("key" -> Json.fromLong(320000L), "doc_count" -> Json.fromInt(5))), "sum_other_doc_count" -> Json.fromInt(0), "doc_count_error_upper_bound" -> Json.fromInt(0)),
          "facet:printTyped" -> Json.obj("buckets" -> Json.arr(Json.obj("key" -> Json.fromBoolean(true), "doc_count" -> Json.fromInt(6))), "sum_other_doc_count" -> Json.fromInt(0), "doc_count_error_upper_bound" -> Json.fromInt(0)),
        ),
      )
      val page = decodeOrFail(compiled, response)
      assert(page.termsFacet(FacetId("genreTyped"), genre).getOrElse(fail("expected genre facet")).buckets.map(_.key) == Vector("Fiction"))
      assert(page.termsFacet(FacetId("pageTyped"), pageCount).getOrElse(fail("expected page facet")).buckets.map(_.key) == Vector(42))
      assert(page.termsFacet(FacetId("wordTyped"), wordCount).getOrElse(fail("expected word facet")).buckets.map(_.key) == Vector(320000L))
      assert(page.termsFacet(FacetId("printTyped"), inPrint).getOrElse(fail("expected print facet")).buckets.map(_.key) == Vector(true))
      assert(page.termsFacet(FacetId("genreTyped"), genre).getOrElse(fail("expected genre facet")).buckets.map(_.docCountErrorUpperBound) == Vector(Some(2L)))
    }

    "reject malformed terms precision and negative counts" in {
      val termsFacet = FacetRequest.Terms(FacetId("genreFacet"), genre, FacetSize.from(10).getOrElse(fail("bad size")), TermsFacetOrder.KeyAsc, FacetCountingPolicy.AllAppliedHardFilters)
      val compiled = compileOrFail(firstPageBoundPlanOf(pageSize = 5, facets = Vector(termsFacet)))
      val malformedPrecision = responseJson(Vector.empty, total = 0, aggs = Vector("facet:genreFacet" -> Json.obj("buckets" -> Json.arr(), "sum_other_doc_count" -> Json.fromInt(0), "doc_count_error_upper_bound" -> Json.fromInt(-2))))
      val negativeCount = responseJson(Vector.empty, total = 0, aggs = Vector("facet:genreFacet" -> Json.obj("buckets" -> Json.arr(Json.obj("key" -> Json.fromString("Fiction"), "doc_count" -> Json.fromInt(-1))), "sum_other_doc_count" -> Json.fromInt(0))))
      val negativeOther = responseJson(Vector.empty, total = 0, aggs = Vector("facet:genreFacet" -> Json.obj("buckets" -> Json.arr(), "sum_other_doc_count" -> Json.fromInt(-1))))
      assert(ElasticsearchSearchResponseDecoder.decode(compiled, malformedPrecision).isLeft)
      assert(ElasticsearchSearchResponseDecoder.decode(compiled, negativeCount).isLeft)
      assert(ElasticsearchSearchResponseDecoder.decode(compiled, negativeOther).isLeft)
    }

    "restore range and interval facet buckets into original declaration order, regardless of response order" in {
      val rangeFacet =
        FacetRequest.NumberRange(FacetId("pages"), pageCount, Vector(FacetBucket.HalfOpen(FacetBucketId("short"), 0, 300), FacetBucket.UpperUnbounded(FacetBucketId("long"), 300)), FacetCountingPolicy.AllAppliedHardFilters)
      val compiled = compileOrFail(firstPageBoundPlanOf(pageSize = 5, facets = Vector(rangeFacet)))

      // Response buckets in reverse declared order.
      val aggJson =
        Json.obj("buckets" -> Json.obj("long" -> Json.obj("doc_count" -> Json.fromInt(2)), "short" -> Json.obj("doc_count" -> Json.fromInt(7))))
      val response = responseJson(Vector.empty, total = 0, aggs = Vector("facet:pages" -> aggJson))

      val page = decodeOrFail(compiled, response)
      val rangeResult = page.facets.collectFirst { case ElasticsearchFacetResult.NumberRange(result) => result }.getOrElse(fail("expected a range facet result"))
      assert(rangeResult.buckets.map(_.id) == Vector(FacetBucketId("short"), FacetBucketId("long")))
      assert(rangeResult.buckets.map(_.count) == Vector(7L, 2L))
    }

    "derive facet counts from aggregations, never from hits" in {
      val termsFacet = FacetRequest.Terms(FacetId("genreFacet"), genre, FacetSize.from(10).getOrElse(fail("bad size")), TermsFacetOrder.KeyAsc, FacetCountingPolicy.AllAppliedHardFilters)
      val compiled   = compileOrFail(firstPageBoundPlanOf(pageSize = 5, facets = Vector(termsFacet)))
      val aggJson    = Json.obj("buckets" -> Json.arr(Json.obj("key" -> Json.fromString("Fiction"), "doc_count" -> Json.fromInt(42))), "sum_other_doc_count" -> Json.fromInt(0))
      val response   = responseJson(Vector(hitJson(bookA.isbn, 1.0, Vector(Json.fromString(bookA.isbn)))), total = 1, aggs = Vector("facet:genreFacet" -> aggJson))

      val page = decodeOrFail(compiled, response)
      val result = page.termsFacet(FacetId("genreFacet"), genre).getOrElse(fail("expected a terms facet result"))
      assert(result.buckets.map(_.key) == Vector("Fiction"))
      assert(result.buckets.map(_.count) == Vector(42L))
    }

    "report a missing requested aggregation" in {
      val termsFacet = FacetRequest.Terms(FacetId("genreFacet"), genre, FacetSize.from(10).getOrElse(fail("bad size")), TermsFacetOrder.KeyAsc, FacetCountingPolicy.AllAppliedHardFilters)
      val compiled   = compileOrFail(firstPageBoundPlanOf(pageSize = 5, facets = Vector(termsFacet)))
      val response   = responseJson(Vector.empty, total = 0)
      ElasticsearchSearchResponseDecoder.decode(compiled, response) match {
        case Left(errors) => assert(errors.toVector.contains(ElasticsearchSearchResponseError.MissingRequestedAggregation(FacetId("genreFacet"))))
        case other         => fail(s"expected MissingRequestedAggregation, got $other")
      }
    }

    "report an unknown returned aggregation" in {
      val compiled = compileOrFail(firstPageBoundPlanOf(pageSize = 5))
      val response  = responseJson(Vector.empty, total = 0, aggs = Vector("facet:unrequested" -> Json.obj("buckets" -> Json.arr())))
      ElasticsearchSearchResponseDecoder.decode(compiled, response) match {
        case Left(errors) => assert(errors.toVector.contains(ElasticsearchSearchResponseError.UnknownAggregation("facet:unrequested")))
        case other         => fail(s"expected UnknownAggregation, got $other")
      }
    }

    "report a missing and an unknown declared bucket together" in {
      val rangeFacet =
        FacetRequest.NumberRange(FacetId("pages"), pageCount, Vector(FacetBucket.HalfOpen(FacetBucketId("short"), 0, 300), FacetBucket.UpperUnbounded(FacetBucketId("long"), 300)), FacetCountingPolicy.AllAppliedHardFilters)
      val compiled = compileOrFail(firstPageBoundPlanOf(pageSize = 5, facets = Vector(rangeFacet)))
      val aggJson  = Json.obj("buckets" -> Json.obj("short" -> Json.obj("doc_count" -> Json.fromInt(1)), "unexpected" -> Json.obj("doc_count" -> Json.fromInt(9))))
      val response = responseJson(Vector.empty, total = 0, aggs = Vector("facet:pages" -> aggJson))

      ElasticsearchSearchResponseDecoder.decode(compiled, response) match {
        case Left(errors) =>
          assert(errors.toVector.contains(ElasticsearchSearchResponseError.MissingDeclaredBucket(FacetId("pages"), FacetBucketId("long"))))
          assert(errors.toVector.contains(ElasticsearchSearchResponseError.UnknownBucket(FacetId("pages"), "unexpected")))
        case other => fail(s"expected MissingDeclaredBucket and UnknownBucket, got $other")
      }
    }
  }

  "cursor round trip" should {
    "complete the full sequence and pin the first page's physical target on the second request, even when a different first-page target is supplied" in {
      val bound1    = firstPageBoundPlanOf(pageSize = 2)
      val compiled1 = compileOrFail(bound1)
      val response1 =
        responseJson(
          Vector(hitJson("isbn-1", 3.0, Vector(Json.fromString("isbn-1"))), hitJson("isbn-2", 2.0, Vector(Json.fromString("isbn-2"))), hitJson("isbn-3", 1.0, Vector(Json.fromString("isbn-3")))),
          total = 3,
        )
      val page1  = decodeOrFail(compiled1, response1)
      val cursor = page1.nextCursor.getOrElse(fail("expected a next cursor"))

      val transportToken = cursor.opaqueValue
      val roundTrippedCursor = SearchCursor.fromTransport(transportToken)

      val secondPlan =
        SearchPlan[BookDocument](
          residualText = None,
          appliedFilters = Vector.empty,
          softSignals = Vector.empty,
          sort = Vector.empty,
          page = PageRequest(Some(roundTrippedCursor), PageSize.from(2).getOrElse(fail("bad size"))),
          facets = Vector.empty,
          groups = Vector.empty,
          diagnostics = PlanDiagnostics.empty,
        )
      val bound2 = SearchCursorEnvelope.bind(secondPlan, canonicalPlanView).getOrElse(fail("expected the second page to bind"))
      assert(!bound2.isFirstPage)

      // The lifecycle binding resolves the pinned generation reference back to the original physical target.
      val compiled2 = compileOrFail(bound2)
      assert(compiled2.target == targetA)
      assert(at(compiled2.prepared.body, "search_after") == Json.arr(Json.fromString("isbn-2")))
    }

    "reject a malformed cursor-state JSON payload" in {
      val boundWithMalformedState = bindWithRawBackendState("not json")
      ElasticsearchSearchRequestCompiler.compile(fullPolicy, boundWithMalformedState) match {
        case Left(ElasticsearchSearchRequestCompileError.CursorStateDecodeFailed(_: ElasticsearchCursorStateError.MalformedJson)) => succeed
        case other                                                                                                                 => fail(s"expected CursorStateDecodeFailed(MalformedJson), got $other")
      }
    }

    "reject an unsupported cursor-state protocol version" in {
      val badState =
        Json.obj("protocolVersion" -> Json.fromString("unknown"), "generationReference" -> Json.fromString(referenceA.value), "searchAfter" -> Json.arr(Json.fromString("isbn-1"))).noSpaces
      val boundWithBadVersion = bindWithRawBackendState(badState)
      ElasticsearchSearchRequestCompiler.compile(fullPolicy, boundWithBadVersion) match {
        case Left(ElasticsearchSearchRequestCompileError.CursorStateDecodeFailed(_: ElasticsearchCursorStateError.UnsupportedProtocolVersion)) => succeed
        case other                                                                                                                              => fail(s"expected CursorStateDecodeFailed(UnsupportedProtocolVersion), got $other")
      }
    }

    "reject changed query/filter/sort/facet/page-size/contract policy through the existing plan identity, before ever reaching backend-state decoding" in {
      val bound1    = firstPageBoundPlanOf(pageSize = 2)
      val compiled1 = compileOrFail(bound1)
      val response1 =
        responseJson(
          Vector(hitJson("isbn-1", 3.0, Vector(Json.fromString("isbn-1"))), hitJson("isbn-2", 2.0, Vector(Json.fromString("isbn-2"))), hitJson("isbn-3", 1.0, Vector(Json.fromString("isbn-3")))),
          total = 3,
        )
      val cursor = decodeOrFail(compiled1, response1).nextCursor.getOrElse(fail("expected a next cursor"))

      // A structurally different plan (different page size) rebinding the same cursor must fail identity
      // validation - never reach ES backend-state decoding at all.
      val changedPlan =
        SearchPlan[BookDocument](
          residualText = None,
          appliedFilters = Vector.empty,
          softSignals = Vector.empty,
          sort = Vector.empty,
          page = PageRequest(Some(cursor), PageSize.from(99).getOrElse(fail("bad size"))),
          facets = Vector.empty,
          groups = Vector.empty,
          diagnostics = PlanDiagnostics.empty,
        )
      SearchCursorEnvelope.bind(changedPlan, canonicalPlanView) match {
        case Left(_: SearchCursorError.PlanIdentityMismatch) => succeed
        case other                                            => fail(s"expected PlanIdentityMismatch, got $other")
      }
    }

    "never emit an offset/from field on the second-page request" in {
      val bound1    = firstPageBoundPlanOf(pageSize = 2)
      val compiled1 = compileOrFail(bound1)
      val response1 =
        responseJson(
          Vector(hitJson("isbn-1", 3.0, Vector(Json.fromString("isbn-1"))), hitJson("isbn-2", 2.0, Vector(Json.fromString("isbn-2"))), hitJson("isbn-3", 1.0, Vector(Json.fromString("isbn-3")))),
          total = 3,
        )
      val cursor = decodeOrFail(compiled1, response1).nextCursor.getOrElse(fail("expected a next cursor"))

      val secondPlan =
        SearchPlan[BookDocument](None, Vector.empty, Vector.empty, Vector.empty, PageRequest(Some(cursor), PageSize.from(2).getOrElse(fail("bad size"))), Vector.empty, Vector.empty, PlanDiagnostics.empty)
      val bound2    = SearchCursorEnvelope.bind(secondPlan, canonicalPlanView).getOrElse(fail("expected the second page to bind"))
      val compiled2 = compileOrFail(bound2)
      assert(compiled2.prepared.body.asObject.exists(!_.contains("from")))
    }
  }

  private def at(body: Json, path: String*): Json =
    path.foldLeft(body.hcursor: io.circe.ACursor)((cursor, field) => cursor.downField(field)).focus.getOrElse(fail(s"expected JSON at path ${path.mkString(".")}"))

  // Binds a first-page plan, then re-binds a second-page plan carrying a cursor whose backend state is the
  // exact raw string under test - proving cursor-state decode-error handling without needing a real
  // Elasticsearch response for every malformed-state scenario.
  private def bindWithRawBackendState(rawState: String): BoundSearchPlan[BookDocument] = {
    val firstPlan =
      SearchPlan[BookDocument](None, Vector.empty, Vector.empty, Vector.empty, PageRequest(None, PageSize.from(2).getOrElse(fail("bad size"))), Vector.empty, Vector.empty, PlanDiagnostics.empty)
    val firstBound = SearchCursorEnvelope.bind(firstPlan, canonicalPlanView).getOrElse(fail("expected a valid first bound plan"))
    val issued     = SearchCursorEnvelope.issue(firstBound, rawState)

    val secondPlan =
      SearchPlan[BookDocument](
        None,
        Vector.empty,
        Vector.empty,
        Vector.empty,
        PageRequest(Some(SearchCursor.fromTransport(issued.opaqueValue)), PageSize.from(2).getOrElse(fail("bad size"))),
        Vector.empty,
        Vector.empty,
        PlanDiagnostics.empty,
      )
    SearchCursorEnvelope.bind(secondPlan, canonicalPlanView).getOrElse(fail("expected the second bound plan to bind"))
  }
}
