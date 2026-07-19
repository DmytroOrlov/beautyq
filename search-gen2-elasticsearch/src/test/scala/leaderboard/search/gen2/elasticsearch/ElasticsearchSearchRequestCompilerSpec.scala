package leaderboard.search.gen2.elasticsearch

import leaderboard.search.gen2.contract.*
import leaderboard.search.gen2.core.plan.*
import org.scalatest.wordspec.AnyWordSpec

import io.circe.Json

/** Neutral calibration for the generic Elasticsearch search-request compiler, using the shared
  * book/library document fixture ([[ElasticsearchTestFixtures]]) unrelated to BeautyQ.
  */
final class ElasticsearchSearchRequestCompilerSpec extends AnyWordSpec {
  import ElasticsearchTestFixtures.*

  private def boundPlanOf(
    residualText: Option[String] = None,
    appliedFilters: Vector[AppliedFilter[BookDocument]] = Vector.empty,
    softSignals: Vector[PlannedSignal[BookDocument]] = Vector.empty,
    sort: Vector[PlannedSort[BookDocument]] = Vector.empty,
    facets: Vector[FacetRequest[BookDocument]] = Vector.empty,
    groups: Vector[GroupRequest[BookDocument, ?]] = Vector.empty,
    pageSize: Int = 20,
    usingPolicy: ElasticsearchPolicy[BookDocument, String] = fullPolicy,
  ): BoundSearchPlan[BookDocument] = {
    val plan =
      SearchPlan(
        residualText = residualText,
        appliedFilters = appliedFilters,
        softSignals = softSignals,
        sort = sort,
        page = PageRequest(None, PageSize.from(pageSize).getOrElse(fail("expected a valid PageSize"))),
        facets = facets,
        groups = groups,
        diagnostics = PlanDiagnostics.empty,
      )
    SearchCursorEnvelope.bind(plan, CanonicalPlanView[BookDocument](usingPolicy.contractFingerprint)) match {
      case Right(bound) => bound
      case Left(error)  => fail(s"expected a valid bound plan, got $error")
    }
  }

  private def explicitUi[Document](constraint: PlannedConstraint[Document]): AppliedFilter[Document] =
    AppliedFilter(SourcedConstraint(constraint, ConstraintProvenance.ExplicitUi))

  private def compileOrFail(
    boundPlan: BoundSearchPlan[BookDocument],
    usingPolicy: ElasticsearchPolicy[BookDocument, String] = fullPolicy,
  ): CompiledElasticsearchSearchRequest[BookDocument, String] =
    ElasticsearchSearchRequestCompiler.compile(usingPolicy, boundPlan) match {
      case Right(compiled) => compiled
      case Left(error)     => fail(s"expected successful compilation, got $error")
    }

  private def at(body: Json, path: String*): Json =
    path.foldLeft(body.hcursor: io.circe.ACursor)((cursor, field) => cursor.downField(field)).focus.getOrElse(fail(s"expected JSON at path ${path.mkString(".")}"))

  "ElasticsearchSearchRequestCompiler.compile" should {
    "reject a bound plan whose contract fingerprint differs from the policy's own" in {
      val otherFingerprint = PlanContractFingerprint.compute(PlanContractVersion("unrelated-contract-v1"), document, Map.empty)
      val otherView        = CanonicalPlanView[BookDocument](otherFingerprint)
      val plan              = SearchPlan[BookDocument](None, Vector.empty, Vector.empty, Vector.empty, PageRequest(None, PageSize.from(10).getOrElse(fail("bad size"))), Vector.empty, Vector.empty, PlanDiagnostics.empty)
      val otherBound        = SearchCursorEnvelope.bind(plan, otherView).getOrElse(fail("expected a valid bound plan"))

      ElasticsearchSearchRequestCompiler.compile(fullPolicy, otherBound) match {
        case Left(_: ElasticsearchSearchRequestCompileError.ContractFingerprintMismatch) => succeed
        case other                                                                       => fail(s"expected ContractFingerprintMismatch, got $other")
      }
    }

    "carry non-empty plan.groups and the exact execution query into the prepared request" in {
      val group =
        GroupRequest[BookDocument, String](
          GroupId("genre-group"),
          genre,
          GroupSize.from(5).getOrElse(fail("bad size")),
          RepresentativeRequest.IdentityOnly(),
          Vector.empty,
          Vector(GroupOrder.Key(SortDirection.Asc)),
          GroupPrecisionPolicy.RequireExact,
        )
      val compiled = compileOrFail(boundPlanOf(groups = Vector(group)))
      assert(compiled.requestedGroups.map(_.id) == Vector(GroupId("genre-group")))
      assert(compiled.executionQuery == at(compiled.body, "query"))
    }

    "never emit an offset/from field" in {
      val body = compileOrFail(boundPlanOf()).body
      assert(body.asObject.exists(!_.contains("from")))
    }

    "compile size as pageSize + 1 and request exact/tracked totals" in {
      val body = compileOrFail(boundPlanOf(pageSize = 20)).body
      assert(at(body, "size") == Json.fromInt(21))
      assert(at(body, "track_total_hits") == Json.fromBoolean(true))
      assert(at(body, "track_scores") == Json.fromBoolean(true))
      assert(at(body, "_source") == Json.fromBoolean(true))
    }

    "omit search_after and aggs when absent" in {
      val body = compileOrFail(boundPlanOf()).body
      assert(body.asObject.exists(!_.contains("search_after")))
      assert(body.asObject.exists(!_.contains("aggs")))
    }
  }

  "text/base query compilation" should {
    "compile residual text as a deterministic multi_match over the policy's weighted fields and operator" in {
      val body = compileOrFail(boundPlanOf(residualText = Some("scala programming"))).body
      assert(
        at(body, "query", "multi_match") ==
          Json.obj(
            "query"    -> Json.fromString("scala programming"),
            "fields"   -> Json.arr(Json.fromString("title^3.0"), Json.fromString("subtitle^1.5")),
            "operator" -> Json.fromString("or"),
          )
      )
    }

    "compile an otherwise empty/default browse plan to explicit match_all, never an empty bool" in {
      val body = compileOrFail(boundPlanOf()).body
      assert(at(body, "query") == Json.obj("match_all" -> Json.obj()))
    }

    "compile hard filters alone (no text) under bool.filter, without a must clause" in {
      val body = compileOrFail(boundPlanOf(appliedFilters = Vector(explicitUi(PlannedConstraint.Terms(genre, Set("Technology")))))).body
      val boolObj = at(body, "query", "bool").asObject.getOrElse(fail("expected a bool object"))
      assert(!boolObj.contains("must"))
      assert(boolObj.contains("filter"))
    }
  }

  "hard constraint compilation" should {
    "compile a Terms filter with canonical values sorted independent of Set iteration order" in {
      val bodyA = compileOrFail(boundPlanOf(appliedFilters = Vector(explicitUi(PlannedConstraint.Terms(genre, Set("Technology", "Fiction")))))).body
      val bodyB = compileOrFail(boundPlanOf(appliedFilters = Vector(explicitUi(PlannedConstraint.Terms(genre, Set("Fiction", "Technology")))))).body
      val expected = Json.obj("terms" -> Json.obj("genre" -> Json.arr(Json.fromString("Fiction"), Json.fromString("Technology"))))
      assert(at(bodyA, "query", "bool", "filter").asArray.exists(_.contains(expected)))
      assert(at(bodyB, "query", "bool", "filter").asArray.exists(_.contains(expected)))
    }

    "compile a single-value Terms filter as term, not terms" in {
      val body = compileOrFail(boundPlanOf(appliedFilters = Vector(explicitUi(PlannedConstraint.Terms(genre, Set("Technology")))))).body
      assert(at(body, "query", "bool", "filter").asArray.exists(_.contains(Json.obj("term" -> Json.obj("genre" -> Json.fromString("Technology"))))))
    }

    "compile an empty Terms filter to match_none, never disappearing from the filter list" in {
      val body = compileOrFail(boundPlanOf(appliedFilters = Vector(explicitUi(PlannedConstraint.Terms(genre, Set.empty))))).body
      assert(at(body, "query", "bool", "filter").asArray.exists(_.contains(Json.obj("match_none" -> Json.obj()))))
    }

    "compile a boolean Terms filter" in {
      val body = compileOrFail(boundPlanOf(appliedFilters = Vector(explicitUi(PlannedConstraint.Terms(inPrint, Set(true)))))).body
      assert(at(body, "query", "bool", "filter").asArray.exists(_.contains(Json.obj("term" -> Json.obj("inPrint" -> Json.fromBoolean(true))))))
    }

    // Every inclusive/exclusive/unbounded combination, using the numeric pageCount field.
    "preserve every NumberRange inclusive/exclusive/unbounded combination" in {
      val cases: Vector[(String, RangeBounds[Int], Json)] =
        Vector(
          "both inclusive"  -> RangeBounds(Bound.Inclusive(100), Bound.Inclusive(500)) -> Json.obj("gte" -> Json.fromInt(100), "lte" -> Json.fromInt(500)),
          "both exclusive"  -> RangeBounds(Bound.Exclusive(100), Bound.Exclusive(500)) -> Json.obj("gt"  -> Json.fromInt(100), "lt"  -> Json.fromInt(500)),
          "lower unbounded" -> RangeBounds(Bound.Unbounded, Bound.Inclusive(500))       -> Json.obj("lte" -> Json.fromInt(500)),
          "upper unbounded" -> RangeBounds(Bound.Inclusive(100), Bound.Unbounded)       -> Json.obj("gte" -> Json.fromInt(100)),
          "both unbounded"  -> RangeBounds(Bound.Unbounded, Bound.Unbounded)             -> Json.obj("match_all" -> Json.obj()),
        ).map { case ((label, bounds), expected) => (label, bounds, expected) }

      cases.foreach { case (label, bounds, expectedRange) =>
        val body = compileOrFail(boundPlanOf(appliedFilters = Vector(explicitUi(PlannedConstraint.NumberRange(pageCount, bounds))))).body
        val expected = if (label == "both unbounded") Json.obj("match_all" -> Json.obj()) else Json.obj("range" -> Json.obj("pageCount" -> expectedRange))
        assert(at(body, "query", "bool", "filter").asArray.exists(_.contains(expected)), s"$label: unexpected range JSON")
      }
    }

    "preserve a DateTime NumberRange filter using the canonical ISO instant string" in {
      val from = java.time.Instant.parse("2020-01-01T00:00:00Z")
      val body = compileOrFail(boundPlanOf(appliedFilters = Vector(explicitUi(PlannedConstraint.NumberRange(publishedAt, RangeBounds(Bound.Inclusive(from), Bound.Unbounded)))))).body
      assert(at(body, "query", "bool", "filter").asArray.exists(_.contains(Json.obj("range" -> Json.obj("publishedAt" -> Json.obj("gte" -> Json.fromString(from.toString)))))))
    }

    "compile the exact IntervalOverlap predicate: request lower binds `to`, request upper binds `from`" in {
      val body =
        compileOrFail(
          boundPlanOf(appliedFilters =
            Vector(explicitUi(PlannedConstraint.IntervalOverlap(stockFrom, stockTo, RangeBounds(Bound.Inclusive(BigDecimal(10)), Bound.Exclusive(BigDecimal(50))))))
          )
        ).body
      val expected =
        Json.obj(
          "bool" -> Json.obj(
            "filter" -> Json.arr(
              Json.obj("range" -> Json.obj("stockTo" -> Json.obj("gte" -> Json.fromBigDecimal(BigDecimal(10))))),
              Json.obj("range" -> Json.obj("stockFrom" -> Json.obj("lt" -> Json.fromBigDecimal(BigDecimal(50))))),
            )
          )
        )
      assert(at(body, "query", "bool", "filter").asArray.exists(_.contains(expected)))
    }

    "compile a fully unbounded IntervalOverlap to match_all" in {
      val body =
        compileOrFail(boundPlanOf(appliedFilters = Vector(explicitUi(PlannedConstraint.IntervalOverlap(stockFrom, stockTo, RangeBounds(Bound.Unbounded, Bound.Unbounded)))))).body
      assert(at(body, "query", "bool", "filter").asArray.exists(_.contains(Json.obj("match_all" -> Json.obj()))))
    }

    "compile a GeoDistanceFilter using meters and explicit distance_type arc" in {
      val origin = GeoPoint(BigDecimal("10.0"), BigDecimal("20.0"))
      val body   = compileOrFail(boundPlanOf(appliedFilters = Vector(explicitUi(PlannedConstraint.GeoDistanceFilter(storeLocation, origin, Distance(BigDecimal(3000))))))).body
      assert(
        at(body, "query", "bool", "filter").asArray.exists(
          _.contains(
            Json.obj(
              "geo_distance" -> Json.obj(
                "distance"      -> Json.fromString("3000m"),
                "distance_type" -> Json.fromString("arc"),
                "storeLocation" -> Json.obj("lat" -> Json.fromBigDecimal(BigDecimal("10.0")), "lon" -> Json.fromBigDecimal(BigDecimal("20.0"))),
              )
            )
          )
        )
      )
    }
  }

  "geo scoring independence" should {
    "compile a GeoProximitySignal alone as function_score scoring, with no hard filter" in {
      val origin = GeoPoint(BigDecimal("10.0"), BigDecimal("20.0"))
      val body   = compileOrFail(boundPlanOf(softSignals = Vector(PlannedSignal.GeoProximitySignal(storeLocation, origin)))).body
      assert(at(body, "query", "function_score", "query") == Json.obj("match_all" -> Json.obj()))
      assert(at(body, "query", "function_score", "score_mode") == Json.fromString("sum"))
      assert(at(body, "query", "function_score", "boost_mode") == Json.fromString("sum"))
      val gauss = at(body, "query", "function_score", "functions").asArray.flatMap(_.headOption).flatMap(_.asObject).flatMap(_.apply("gauss")).getOrElse(fail("expected a gauss function"))
      assert(gauss.asObject.exists(_.contains("storeLocation")))
      assert(gauss.hcursor.downField("storeLocation").downField("decay").focus.contains(Json.fromBigDecimal(BigDecimal("0.3"))))
      val function = at(body, "query", "function_score", "functions").asArray.flatMap(_.headOption).getOrElse(fail("expected one geo function"))
      assert(function.hcursor.downField("weight").focus.contains(Json.fromBigDecimal(BigDecimal("2.0"))))
    }

    "reject a geo signal when the complete policy has no geo-scoring policy" in {
      val noGeoPolicy = ElasticsearchPolicy.unsafeFrom(planContractVersion, policy, queryTextFields, ElasticsearchTextOperator.Or, None, ElasticsearchTotalHitsPolicy.ExactRequired, defaultSortPolicy)
      val origin      = GeoPoint(BigDecimal("10.0"), BigDecimal("20.0"))
      val bound       = boundPlanOf(softSignals = Vector(PlannedSignal.GeoProximitySignal(storeLocation, origin)), usingPolicy = noGeoPolicy)
      ElasticsearchSearchRequestCompiler.compile(noGeoPolicy, bound) match {
        case Left(ElasticsearchSearchRequestCompileError.MissingGeoScoringPolicy(index, fieldId)) =>
          assert(index == 0)
          assert(fieldId == storeLocation.id)
        case other => fail(s"expected MissingGeoScoringPolicy, got $other")
      }
    }

    "preserve the order of multiple geo scoring functions" in {
      val firstOrigin  = GeoPoint(BigDecimal("10.0"), BigDecimal("20.0"))
      val secondOrigin = GeoPoint(BigDecimal("11.0"), BigDecimal("21.0"))
      val body = compileOrFail(boundPlanOf(softSignals = Vector(
        PlannedSignal.GeoProximitySignal(storeLocation, firstOrigin),
        PlannedSignal.GeoProximitySignal(storeLocation, secondOrigin),
      ))).body
      val functions = at(body, "query", "function_score", "functions").asArray.getOrElse(fail("expected geo functions"))
      assert(functions.length == 2)
      assert(functions.headOption.flatMap(_.hcursor.downField("gauss").downField("storeLocation").downField("origin").downField("lat").as[BigDecimal].toOption).contains(BigDecimal("10.0")))
      assert(functions.drop(1).headOption.flatMap(_.hcursor.downField("gauss").downField("storeLocation").downField("origin").downField("lat").as[BigDecimal].toOption).contains(BigDecimal("11.0")))
    }

    "compile a GeoDistanceFilter alone as a hard filter, with no scoring function" in {
      val origin = GeoPoint(BigDecimal("10.0"), BigDecimal("20.0"))
      val body   = compileOrFail(boundPlanOf(appliedFilters = Vector(explicitUi(PlannedConstraint.GeoDistanceFilter(storeLocation, origin, Distance(BigDecimal(1000))))))).body
      assert(body.asObject.exists(!_.apply("query").exists(_.asObject.exists(_.contains("function_score")))))
    }

    "compile a GeoDistanceSort alone as an explicit sort clause, with no scoring function and no hard filter" in {
      val origin = GeoPoint(BigDecimal("10.0"), BigDecimal("20.0"))
      val body   = compileOrFail(boundPlanOf(sort = Vector(PlannedSort.GeoDistance(storeLocation, origin, SortDirection.Asc)))).body
      assert(at(body, "query") == Json.obj("match_all" -> Json.obj()))
      assert(at(body, "sort").asArray.exists(_.headOption.exists(_.asObject.exists(_.contains("_geo_distance")))))
    }

    "compose a GeoProximitySignal together with independent hard filters, preserving both" in {
      val origin = GeoPoint(BigDecimal("10.0"), BigDecimal("20.0"))
      val body =
        compileOrFail(
          boundPlanOf(appliedFilters = Vector(explicitUi(PlannedConstraint.Terms(genre, Set("Technology")))), softSignals = Vector(PlannedSignal.GeoProximitySignal(storeLocation, origin)))
        ).body
      assert(at(body, "query", "function_score", "query", "bool", "filter").asArray.exists(_.contains(Json.obj("term" -> Json.obj("genre" -> Json.fromString("Technology"))))))
    }
  }

  "sort compilation" should {
    "preserve an explicit field sort in plan order, appending the identity tie-breaker" in {
      val body = compileOrFail(boundPlanOf(sort = Vector(PlannedSort.FieldValue(pageCount, SortDirection.Desc)))).body
      assert(
        at(body, "sort") ==
          Json.arr(
            Json.obj("pageCount" -> Json.obj("order" -> Json.fromString("desc"))),
            Json.obj("isbn" -> Json.obj("order" -> Json.fromString("desc"))),
          )
      )
    }

    "use the policy's relevance direction as the default sort when scoring is active and no explicit sort exists" in {
      val body = compileOrFail(boundPlanOf(residualText = Some("scala"))).body
      assert(
        at(body, "sort") ==
          Json.arr(
            Json.obj("_score" -> Json.obj("order" -> Json.fromString("asc"))),
            Json.obj("isbn" -> Json.obj("order" -> Json.fromString("desc"))),
          )
      )
    }

    "use only the identity tie-breaker when no explicit sort exists and no scoring is active" in {
      val body = compileOrFail(boundPlanOf()).body
      assert(at(body, "sort") == Json.arr(Json.obj("isbn" -> Json.obj("order" -> Json.fromString("desc")))))
    }

    "the compiled sort vector arity matches the exposed sortShape length" in {
      val compiled = compileOrFail(boundPlanOf(sort = Vector(PlannedSort.FieldValue(pageCount, SortDirection.Asc))))
      assert(compiled.sortShape.length == 2)
    }
  }

  "facet compilation" should {
    "derive the aggregation name directly from the typed FacetId" in {
      val facet = FacetRequest.Terms(FacetId("genreFacet"), genre, FacetSize.from(10).getOrElse(fail("bad size")), TermsFacetOrder.KeyAsc, FacetCountingPolicy.AllAppliedHardFilters)
      val body  = compileOrFail(boundPlanOf(facets = Vector(facet))).body
      assert(at(body, "aggs").asObject.exists(_.contains("facet:genreFacet")))
    }

    "compile a Terms facet with show_term_doc_count_error and explicit compound order" in {
      val facet = FacetRequest.Terms(FacetId("genreFacet"), genre, FacetSize.from(10).getOrElse(fail("bad size")), TermsFacetOrder.CountDescThenKeyAsc, FacetCountingPolicy.AllAppliedHardFilters)
      val body  = compileOrFail(boundPlanOf(facets = Vector(facet))).body
      assert(
        at(body, "aggs", "facet:genreFacet", "terms") ==
          Json.obj(
            "field"                     -> Json.fromString("genre"),
            "size"                      -> Json.fromInt(10),
            "order"                     -> Json.arr(Json.obj("_count" -> Json.fromString("desc")), Json.obj("_key" -> Json.fromString("asc"))),
            "show_term_doc_count_error" -> Json.fromBoolean(true),
          )
      )
    }

    "compile a NumberRange facet as a keyed range aggregation with an upper-unbounded final bucket" in {
      val buckets: Vector[FacetBucket[Int]] = Vector(FacetBucket.HalfOpen(FacetBucketId("short"), 0, 300), FacetBucket.UpperUnbounded(FacetBucketId("long"), 300))
      val facet   = FacetRequest.NumberRange(FacetId("pages"), pageCount, buckets, FacetCountingPolicy.AllAppliedHardFilters)
      val body    = compileOrFail(boundPlanOf(facets = Vector(facet))).body
      assert(
        at(body, "aggs", "facet:pages", "range") ==
          Json.obj(
            "field" -> Json.fromString("pageCount"),
            "keyed" -> Json.fromBoolean(true),
            "ranges" -> Json.arr(
              Json.obj("key" -> Json.fromString("short"), "from" -> Json.fromInt(0), "to" -> Json.fromInt(300)),
              Json.obj("key" -> Json.fromString("long"), "from" -> Json.fromInt(300)),
            ),
          )
      )
    }

    "compile an IntervalOverlap facet as a named filters aggregation, one canonical overlap predicate per bucket" in {
      val buckets: Vector[FacetBucket[BigDecimal]] =
        Vector(FacetBucket.HalfOpen(FacetBucketId("low"), BigDecimal(0), BigDecimal(20)), FacetBucket.UpperUnbounded(FacetBucketId("high"), BigDecimal(20)))
      val facet      = FacetRequest.IntervalOverlap(FacetId("stock"), stockFrom, stockTo, buckets, FacetCountingPolicy.AllAppliedHardFilters)
      val body       = compileOrFail(boundPlanOf(facets = Vector(facet))).body
      val filtersObj = at(body, "aggs", "facet:stock", "filters", "filters").asObject.getOrElse(fail("expected keyed filters"))
      assert(filtersObj.keys.toVector == Vector("low", "high"))

      val expectedLow: Json =
        Json.obj(
          "bool" -> Json.obj(
            "filter" -> Json.arr(
              Json.obj("range" -> Json.obj("stockTo" -> Json.obj("gte" -> Json.fromBigDecimal(BigDecimal(0))))),
              Json.obj("range" -> Json.obj("stockFrom" -> Json.obj("lt" -> Json.fromBigDecimal(BigDecimal(20))))),
            )
          )
        )
      assert(filtersObj("low").contains(expectedLow))
    }

    "compile every facet kind under one aggs object" in {
      val termsFacet = FacetRequest.Terms(FacetId("genreFacet"), genre, FacetSize.from(5).getOrElse(fail("bad size")), TermsFacetOrder.KeyAsc, FacetCountingPolicy.AllAppliedHardFilters)
      val rangeFacet = FacetRequest.NumberRange(FacetId("pages"), pageCount, Vector(FacetBucket.UpperUnbounded(FacetBucketId("any"), 0)), FacetCountingPolicy.AllAppliedHardFilters)
      val intervalFacet = FacetRequest.IntervalOverlap(FacetId("stock"), stockFrom, stockTo, Vector(FacetBucket.UpperUnbounded(FacetBucketId("any"), BigDecimal(0))), FacetCountingPolicy.AllAppliedHardFilters)
      val body = compileOrFail(boundPlanOf(facets = Vector(termsFacet, rangeFacet, intervalFacet))).body
      assert(at(body, "aggs").asObject.exists(obj => obj.keys.toVector == Vector("facet:genreFacet", "facet:pages", "facet:stock")))
    }
  }

  "construction boundary" should {
    "reject a raw SearchPlan passed where a BoundSearchPlan is required, at compile time" in {
      assertDoesNotCompile(
        """leaderboard.search.gen2.elasticsearch.ElasticsearchSearchRequestCompiler.compile(
          |  leaderboard.search.gen2.elasticsearch.ElasticsearchTestFixtures.fullPolicy,
          |  leaderboard.search.gen2.contract.SearchPlan[leaderboard.search.gen2.elasticsearch.ElasticsearchTestFixtures.BookDocument](
          |    None,
          |    Vector.empty,
          |    Vector.empty,
          |    Vector.empty,
          |    leaderboard.search.gen2.contract.PageRequest(None, leaderboard.search.gen2.contract.PageSize.from(10).getOrElse(throw new IllegalStateException())),
          |    Vector.empty,
          |    Vector.empty,
          |    leaderboard.search.gen2.contract.PlanDiagnostics.empty,
          |  ),
          |  leaderboard.search.gen2.elasticsearch.ElasticsearchGenerationRequirement.Active,
          |)""".stripMargin
      )
    }

    // `assertDoesNotCompile` requires a statically-known string literal, so the real, well-typed
    // BoundSearchPlan construction (never `null`) is duplicated verbatim into both snippets below
    // rather than shared via interpolation.

    "reject construction outside ElasticsearchSearchRequestCompiler" in {
      assertDoesNotCompile(
        """new leaderboard.search.gen2.elasticsearch.ElasticsearchSearchRequestCompiler.CompiledElasticsearchSearchRequest[leaderboard.search.gen2.elasticsearch.ElasticsearchTestFixtures.BookDocument, String](
          |  leaderboard.search.gen2.elasticsearch.ElasticsearchGenerationRequirement.Active,
          |  io.circe.Json.obj(),
          |  leaderboard.search.gen2.core.plan.SearchCursorEnvelope.bind(leaderboard.search.gen2.contract.SearchPlan[leaderboard.search.gen2.elasticsearch.ElasticsearchTestFixtures.BookDocument](None, Vector.empty, Vector.empty, Vector.empty, leaderboard.search.gen2.contract.PageRequest(None, leaderboard.search.gen2.contract.PageSize.from(10).getOrElse(throw new IllegalStateException())), Vector.empty, Vector.empty, leaderboard.search.gen2.contract.PlanDiagnostics.empty), leaderboard.search.gen2.core.plan.CanonicalPlanView[leaderboard.search.gen2.elasticsearch.ElasticsearchTestFixtures.BookDocument](leaderboard.search.gen2.elasticsearch.ElasticsearchTestFixtures.fullPolicy.contractFingerprint)).getOrElse(throw new IllegalStateException()),
          |  leaderboard.search.gen2.elasticsearch.ElasticsearchTestFixtures.isbn,
          |  Vector.empty,
          |  Vector.empty,
          |  leaderboard.search.gen2.elasticsearch.ElasticsearchTotalHitsPolicy.ExactRequired,
          |  leaderboard.search.gen2.contract.PageSize.from(10).getOrElse(throw new IllegalStateException()),
          |)""".stripMargin
      )
    }

    "reject calling .copy on a compiled request - no copy method exists" in {
      assertDoesNotCompile(
        """def forgeCopy(value: leaderboard.search.gen2.elasticsearch.ElasticsearchSearchRequestCompiler.CompiledElasticsearchSearchRequest[leaderboard.search.gen2.elasticsearch.ElasticsearchTestFixtures.BookDocument, String]): Any =
          |  value.copy(body = value.body)""".stripMargin
      )
    }

    "reject subclassing outside ElasticsearchSearchRequestCompiler" in {
      assertDoesNotCompile(
        """final class ForgedRequest extends leaderboard.search.gen2.elasticsearch.ElasticsearchSearchRequestCompiler.CompiledElasticsearchSearchRequest[leaderboard.search.gen2.elasticsearch.ElasticsearchTestFixtures.BookDocument, String](
          |  leaderboard.search.gen2.elasticsearch.ElasticsearchGenerationRequirement.Active,
          |  io.circe.Json.obj(),
          |  leaderboard.search.gen2.core.plan.SearchCursorEnvelope.bind(leaderboard.search.gen2.contract.SearchPlan[leaderboard.search.gen2.elasticsearch.ElasticsearchTestFixtures.BookDocument](None, Vector.empty, Vector.empty, Vector.empty, leaderboard.search.gen2.contract.PageRequest(None, leaderboard.search.gen2.contract.PageSize.from(10).getOrElse(throw new IllegalStateException())), Vector.empty, Vector.empty, leaderboard.search.gen2.contract.PlanDiagnostics.empty), leaderboard.search.gen2.core.plan.CanonicalPlanView[leaderboard.search.gen2.elasticsearch.ElasticsearchTestFixtures.BookDocument](leaderboard.search.gen2.elasticsearch.ElasticsearchTestFixtures.fullPolicy.contractFingerprint)).getOrElse(throw new IllegalStateException()),
          |  leaderboard.search.gen2.elasticsearch.ElasticsearchTestFixtures.isbn,
          |  Vector.empty,
          |  Vector.empty,
          |  leaderboard.search.gen2.elasticsearch.ElasticsearchTotalHitsPolicy.ExactRequired,
          |  leaderboard.search.gen2.contract.PageSize.from(10).getOrElse(throw new IllegalStateException()),
          |)""".stripMargin
      )
    }
  }
}
