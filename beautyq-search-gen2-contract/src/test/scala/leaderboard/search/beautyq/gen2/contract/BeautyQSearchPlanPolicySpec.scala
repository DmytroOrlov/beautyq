package leaderboard.search.beautyq.gen2.contract

import leaderboard.search.gen2.contract.*
import org.scalatest.wordspec.AnyWordSpec

/** Pins BeautyQ's plan policy against an independent expected-value table. Expected values below are
  * hand-written test literals, never read from `BeautyQSearchPlanPolicy` itself, so this spec actually
  * proves the policy's declared values rather than restating them.
  */
final class BeautyQSearchPlanPolicySpec extends AnyWordSpec {

  private val Fields = BeautyQSearchDeclarations.variants.Fields

  private val page = PageRequest(None, PageSize.from(20).getOrElse(fail("expected a valid PageSize")))

  private def requestWithLocation(userLocation: Option[GeoPoint]): ValidatedBeautySearchRequestGen2 =
    ValidatedBeautySearchRequestGen2(None, Vector.empty, Vector.empty, Vector.empty, page, userLocation)

  "BeautyQSearchPlanPolicy.constraintPrecedence" should {
    "declare the typed source order PublicRequest strictly above ParsedIntent" in {
      assert(BeautyQSearchPlanPolicy.constraintPrecedence.sourceOrder == Vector(BeautyQConstraintSource.PublicRequest, BeautyQConstraintSource.ParsedIntent))
    }

    "place public constraints in the higher tier and parsed constraints in the lower tier" in {
      val serviceCode = leaderboard.model.ServiceCode.fromString("manicure").getOrElse(fail("expected a valid ServiceCode fixture"))
      val categoryCode = leaderboard.model.CategoryCode.fromString("nails").getOrElse(fail("expected a valid CategoryCode fixture"))
      val public = Vector(SourcedConstraint(PlannedConstraint.Terms(Fields.serviceCode, Set(serviceCode)), ConstraintProvenance.ExplicitUi))
      val parsed = Vector(SourcedConstraint(PlannedConstraint.Terms(Fields.categoryCode, Set(categoryCode)), ConstraintProvenance.ParsedHard))
      val tiers = BeautyQSearchPlanPolicy.constraintPrecedence.tiers(
        {
          case BeautyQConstraintSource.PublicRequest => public
          case BeautyQConstraintSource.ParsedIntent  => parsed
        }
      )
      assert(tiers.higher == public)
      assert(tiers.lower == parsed)
    }
  }

  "BeautyQGeoOriginPolicy.RequestUserLocation" should {
    "declare request.userLocation as the source path" in {
      assert(BeautyQSearchPlanPolicy.geoOriginPolicy.sourcePath == "request.userLocation")
    }

    "actually resolve from the request's own userLocation - not merely label it so" in {
      val here = GeoPoint(BigDecimal("52.5"), BigDecimal("13.4"))
      assert(BeautyQSearchPlanPolicy.geoOriginPolicy.resolve(requestWithLocation(Some(here))) == Some(here))
      assert(BeautyQSearchPlanPolicy.geoOriginPolicy.resolve(requestWithLocation(None)) == None)
    }
  }

  "BeautyQSearchPlanPolicy.groups" should {
    "declare provider and service groups in business order" in {
      assert(BeautyQSearchPlanPolicy.groups.map(_.id) == Vector(BeautyQSearchPlanPolicy.ProviderGroupId, BeautyQSearchPlanPolicy.ServiceGroupId))
      assert(BeautyQSearchPlanPolicy.groups.forall(group => GroupRequest.validate(group).isRight))
      BeautyQSearchPlanPolicy.groups match {
        case Vector(provider, service) =>
          assert(provider.keyField eq Fields.masterLocationId)
          assert(service.keyField eq Fields.serviceId)
          assert(provider.size.value == 10)
          assert(service.size.value == 10)
        case other => fail(s"expected provider and service groups, got $other")
      }
    }

    "add provider distance ordering only for the compiled location signal" in {
      val origin = GeoPoint(BigDecimal("52.5"), BigDecimal("13.4"))
      val withoutSignal = BeautyQSearchPlanPolicy.groupsFor(Vector.empty)
      val withSignal = BeautyQSearchPlanPolicy.groupsFor(Vector(PlannedSignal.GeoProximitySignal(Fields.location, origin)))
      assert(withoutSignal.headOption.exists(_.metrics.forall(!_.isInstanceOf[GroupMetricRequest.MinGeoDistance[?]])))
      withSignal match {
        case Vector(provider, _) =>
          assert(provider.metrics.exists {
            case GroupMetricRequest.MinGeoDistance(id, field, value) => id == BeautyQSearchPlanPolicy.MinDistanceMetricId && (field eq Fields.location) && value == origin
            case _ => false
          })
          assert(provider.order.contains(GroupOrder.Metric(BeautyQSearchPlanPolicy.MinDistanceMetricId, SortDirection.Asc)))
        case other => fail(s"expected provider and service groups, got $other")
      }
    }
  }

  "BeautyQSearchPlanPolicy.defaultBrowseNotice" should {
    "declare the default-browse code with no detail" in {
      assert(BeautyQSearchPlanPolicy.defaultBrowseNotice == PlanDiagnostic(PlanDiagnosticCode("default-browse"), None))
    }
  }

  "BeautyQSearchPlanMode.values" should {
    "declare exactly SemanticSearch, StructuredBrowse and DefaultBrowse" in {
      assert(BeautyQSearchPlanMode.values.toVector == Vector(BeautyQSearchPlanMode.SemanticSearch, BeautyQSearchPlanMode.StructuredBrowse, BeautyQSearchPlanMode.DefaultBrowse))
    }
  }

  "BeautyQSearchPlanPolicy.facetRegistry" should {
    "declare exactly the service, category, price and durationMinutes facets in that order" in {
      assert(BeautyQSearchPlanPolicy.facetRegistry.ids == Vector(FacetId("service"), FacetId("category"), FacetId("price"), FacetId("durationMinutes")))
    }

    "use the authoritative serviceCode handle for the service facet, and categoryCode for category" in {
      BeautyQSearchPlanPolicy.facetRegistry.resolve(Vector(FacetId("service"))) match {
        case Right(Vector(FacetRequest.Terms(FacetId("service"), field, size, order, counting))) =>
          assert(field eq Fields.serviceCode)
          assert(size.value == 10)
          assert(order == TermsFacetOrder.CountDescThenKeyAsc)
          assert(counting == FacetCountingPolicy.AllAppliedHardFilters)
        case other => fail(s"expected a Terms facet over serviceCode, got $other")
      }
      BeautyQSearchPlanPolicy.facetRegistry.resolve(Vector(FacetId("category"))) match {
        case Right(Vector(FacetRequest.Terms(FacetId("category"), field, size, order, counting))) =>
          assert(field eq Fields.categoryCode)
          assert(size.value == 10)
          assert(order == TermsFacetOrder.CountDescThenKeyAsc)
          assert(counting == FacetCountingPolicy.AllAppliedHardFilters)
        case other => fail(s"expected a Terms facet over categoryCode, got $other")
      }
    }

    "use both price fields for the price facet as an IntervalOverlap, with the exact Gen1-evidence bucket table" in {
      val expectedBuckets: Vector[FacetBucket[BigDecimal]] =
        Vector(
          FacetBucket.HalfOpen(FacetBucketId("0-30"), BigDecimal(0), BigDecimal(30)),
          FacetBucket.HalfOpen(FacetBucketId("30-50"), BigDecimal(30), BigDecimal(50)),
          FacetBucket.HalfOpen(FacetBucketId("50-80"), BigDecimal(50), BigDecimal(80)),
          FacetBucket.HalfOpen(FacetBucketId("80-120"), BigDecimal(80), BigDecimal(120)),
          FacetBucket.UpperUnbounded(FacetBucketId("120+"), BigDecimal(120)),
        )
      BeautyQSearchPlanPolicy.facetRegistry.resolve(Vector(FacetId("price"))) match {
        case Right(Vector(FacetRequest.IntervalOverlap(FacetId("price"), from, to, buckets, counting))) =>
          assert(from eq Fields.priceFrom)
          assert(to eq Fields.priceTo)
          assert(buckets == expectedBuckets)
          assert(counting == FacetCountingPolicy.AllAppliedHardFilters)
        case other => fail(s"expected an IntervalOverlap facet over priceFrom/priceTo, got $other")
      }
    }

    "use the exact durationMin field for the durationMinutes facet, with the exact Gen1-evidence bucket table" in {
      val expectedBuckets: Vector[FacetBucket[Int]] =
        Vector(
          FacetBucket.HalfOpen(FacetBucketId("0-30"), 0, 30),
          FacetBucket.HalfOpen(FacetBucketId("30-60"), 30, 60),
          FacetBucket.HalfOpen(FacetBucketId("60-90"), 60, 90),
          FacetBucket.HalfOpen(FacetBucketId("90-120"), 90, 120),
          FacetBucket.UpperUnbounded(FacetBucketId("120+"), 120),
        )
      BeautyQSearchPlanPolicy.facetRegistry.resolve(Vector(FacetId("durationMinutes"))) match {
        case Right(Vector(FacetRequest.NumberRange(FacetId("durationMinutes"), field, buckets, counting))) =>
          assert(field eq Fields.durationMin)
          assert(buckets == expectedBuckets)
          assert(counting == FacetCountingPolicy.AllAppliedHardFilters)
        case other => fail(s"expected a NumberRange facet over durationMin, got $other")
      }
    }

    "declare every finite bucket half-open (inclusive lower, exclusive upper) and every final bucket upper-unbounded, for both numeric facets" in {
      def bucketsOf(id: String): Vector[FacetBucket[?]] =
        BeautyQSearchPlanPolicy.facetRegistry.resolve(Vector(FacetId(id))) match {
          case Right(Vector(request: FacetRequest.IntervalOverlap[VariantSearchDocumentGen2, ?])) => request.buckets
          case Right(Vector(request: FacetRequest.NumberRange[VariantSearchDocumentGen2, ?]))      => request.buckets
          case other                                                                                => fail(s"expected a bucketed facet, got $other")
        }

      Vector("price", "durationMinutes").foreach { id =>
        val buckets = bucketsOf(id)
        val finite = buckets.dropRight(1)
        val last = buckets.lastOption.getOrElse(fail(s"expected a final bucket for $id"))
        assert(finite.forall {
          case _: FacetBucket.HalfOpen[?] => true
          case _ => false
        }, s"expected every finite $id bucket to be HalfOpen")
        last match {
          case _: FacetBucket.UpperUnbounded[?] => ()
          case other => fail(s"expected the final $id bucket to be UpperUnbounded, got $other")
        }
      }
    }

    "validate every declared facet" in {
      val allFacets = BeautyQSearchPlanPolicy.facetRegistry.resolve(BeautyQSearchPlanPolicy.facetRegistry.ids) match {
        case Right(facets) => facets
        case Left(errors)  => fail(s"expected all facet IDs to resolve, got ${errors.toVector}")
      }
      allFacets.foreach(facet => assert(FacetRequest.validate(facet).isRight, s"expected $facet to validate"))
    }
  }

  "BeautyQSearchPlanPolicy.facetRegistry" should {
    "be the direct owner of public facet IDs and lookup" in {
      assert(BeautyQSearchPlanPolicy.facetRegistry.ids == Vector("service", "category", "price", "durationMinutes").map(FacetId.apply))
      assert(BeautyQSearchPlanPolicy.facetRegistry.contains(FacetId("service")))
      assert(!BeautyQSearchPlanPolicy.facetRegistry.contains(FacetId("nonexistent")))
    }
  }

  "BeautyQSearchDeclarations.variants.plan" should {
    "derive its inventory from BeautyQSearchPlanPolicy, exposing the same facet IDs and default-browse code" in {
      assert(BeautyQSearchDeclarations.variants.plan.facets == BeautyQSearchPlanPolicy.facetRegistry.ids)
      assert(BeautyQSearchDeclarations.variants.plan.groups == BeautyQSearchPlanPolicy.groups.map(_.id.value))
      assert(BeautyQSearchDeclarations.variants.plan.defaultBrowseCode == BeautyQSearchPlanPolicy.defaultBrowseNotice.code.value)
      assert(BeautyQSearchDeclarations.variants.plan.modes == Vector("SemanticSearch", "StructuredBrowse", "DefaultBrowse"))
    }

    "expose the exact same typed policy values a reviewer would navigate to in BeautyQSearchPlanPolicy, not copies" in {
      assert(BeautyQSearchDeclarations.variants.plan.constraintPrecedence eq BeautyQSearchPlanPolicy.constraintPrecedence)
      assert(BeautyQSearchDeclarations.variants.plan.geoOriginPolicy eq BeautyQSearchPlanPolicy.geoOriginPolicy)
      assert(BeautyQSearchDeclarations.variants.plan.facetRegistry eq BeautyQSearchPlanPolicy.facetRegistry)
      assert(BeautyQSearchDeclarations.variants.plan.groupPolicy eq BeautyQSearchPlanPolicy.groups)
      assert(BeautyQSearchDeclarations.variants.plan.defaultBrowsePolicy == BeautyQSearchPlanPolicy.defaultBrowseNotice)
      assert(BeautyQSearchDeclarations.variants.plan.modeClassifier(None, Vector.empty, Vector.empty, Vector.empty, Vector.empty) == BeautyQSearchPlanPolicy.classify(None, Vector.empty, Vector.empty, Vector.empty, Vector.empty))
    }
  }
}
