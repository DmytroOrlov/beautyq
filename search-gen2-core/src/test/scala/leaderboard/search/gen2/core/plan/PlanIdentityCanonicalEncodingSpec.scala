package leaderboard.search.gen2.core.plan

import leaderboard.search.gen2.contract.*
import leaderboard.search.gen2.core.materialization.CanonicalFingerprint
import org.scalatest.wordspec.AnyWordSpec

final class PlanIdentityCanonicalEncodingSpec extends AnyWordSpec {
  import PlanIdentityFixtures.*
  import PlanIdentityAssertions.*

  private val identityA = viewA.identityOf(planA)
  private val identityB = viewB.identityOf(planB)

  "PlanIdentityCanonicalEncoding" should {
    "emit Shape A in the exact declared section and indexed token order" in {
      val expected = Vector(
        CanonicalFingerprint.token("identity.version", "search-plan-identity-v1"),
        CanonicalFingerprint.token("contract.fingerprint", "inventory-contract-v1"),
        CanonicalFingerprint.token("query.present", "true"),
        CanonicalFingerprint.token("query.value", "  In Stock\n"),
        CanonicalFingerprint.token("constraint.count", "3"),
        CanonicalFingerprint.token("constraint[0].kind", "terms"),
        CanonicalFingerprint.token("constraint[0].fieldId", "department"),
        CanonicalFingerprint.token("constraint[0].value.count", "2"),
        CanonicalFingerprint.token("constraint[0].value[0]", "retail"),
        CanonicalFingerprint.token("constraint[0].value[1]", "wholesale"),
        CanonicalFingerprint.token("constraint[1].kind", "number-range"),
        CanonicalFingerprint.token("constraint[1].fieldId", "quality"),
        CanonicalFingerprint.token("constraint[1].lower.kind", "inclusive"),
        CanonicalFingerprint.token("constraint[1].lower.value", "2"),
        CanonicalFingerprint.token("constraint[1].upper.kind", "exclusive"),
        CanonicalFingerprint.token("constraint[1].upper.value", "5"),
        CanonicalFingerprint.token("constraint[2].kind", "interval-overlap"),
        CanonicalFingerprint.token("constraint[2].fromFieldId", "availabilityStart"),
        CanonicalFingerprint.token("constraint[2].toFieldId", "availabilityEnd"),
        CanonicalFingerprint.token("constraint[2].lower.kind", "inclusive"),
        CanonicalFingerprint.token("constraint[2].lower.value", "10"),
        CanonicalFingerprint.token("constraint[2].upper.kind", "exclusive"),
        CanonicalFingerprint.token("constraint[2].upper.value", "20"),
        CanonicalFingerprint.token("signal.count", "0"),
        CanonicalFingerprint.token("sort.count", "1"),
        CanonicalFingerprint.token("sort[0].kind", "field-value"),
        CanonicalFingerprint.token("sort[0].fieldId", "quality"),
        CanonicalFingerprint.token("sort[0].direction", "desc"),
        CanonicalFingerprint.token("facet.count", "3"),
        CanonicalFingerprint.token("facet[0].kind", "terms"),
        CanonicalFingerprint.token("facet[0].id", "supplier-facet"),
        CanonicalFingerprint.token("facet[0].fieldId", "supplier"),
        CanonicalFingerprint.token("facet[0].size", "12"),
        CanonicalFingerprint.token("facet[0].order", "key-asc"),
        CanonicalFingerprint.token("facet[0].countingPolicy", "all-applied-hard-filters"),
        CanonicalFingerprint.token("facet[1].kind", "number-range"),
        CanonicalFingerprint.token("facet[1].id", "quality-facet"),
        CanonicalFingerprint.token("facet[1].fieldId", "quality"),
        CanonicalFingerprint.token("facet[1].bucket.count", "2"),
        CanonicalFingerprint.token("facet[1].bucket[0].kind", "half-open"),
        CanonicalFingerprint.token("facet[1].bucket[0].id", "q-low"),
        CanonicalFingerprint.token("facet[1].bucket[0].min", "0"),
        CanonicalFingerprint.token("facet[1].bucket[0].max", "3"),
        CanonicalFingerprint.token("facet[1].bucket[1].kind", "upper-unbounded"),
        CanonicalFingerprint.token("facet[1].bucket[1].id", "q-high"),
        CanonicalFingerprint.token("facet[1].bucket[1].min", "3"),
        CanonicalFingerprint.token("facet[1].countingPolicy", "all-applied-hard-filters"),
        CanonicalFingerprint.token("facet[2].kind", "interval-overlap"),
        CanonicalFingerprint.token("facet[2].id", "availability-facet"),
        CanonicalFingerprint.token("facet[2].fromFieldId", "availabilityStart"),
        CanonicalFingerprint.token("facet[2].toFieldId", "availabilityEnd"),
        CanonicalFingerprint.token("facet[2].bucket.count", "2"),
        CanonicalFingerprint.token("facet[2].bucket[0].kind", "half-open"),
        CanonicalFingerprint.token("facet[2].bucket[0].id", "short"),
        CanonicalFingerprint.token("facet[2].bucket[0].min", "0"),
        CanonicalFingerprint.token("facet[2].bucket[0].max", "10"),
        CanonicalFingerprint.token("facet[2].bucket[1].kind", "upper-unbounded"),
        CanonicalFingerprint.token("facet[2].bucket[1].id", "long"),
        CanonicalFingerprint.token("facet[2].bucket[1].min", "10"),
        CanonicalFingerprint.token("facet[2].countingPolicy", "all-applied-hard-filters"),
        CanonicalFingerprint.token("group.count", "1"),
        CanonicalFingerprint.token("group[0].id", "supplier-group"),
        CanonicalFingerprint.token("group[0].keyFieldId", "supplier"),
        CanonicalFingerprint.token("group[0].size", "5"),
        CanonicalFingerprint.token("group[0].representative.kind", "fields"),
        CanonicalFingerprint.token("group[0].representative.count", "2"),
        CanonicalFingerprint.token("group[0].representative.field[0]", "headline"),
        CanonicalFingerprint.token("group[0].representative.field[1]", "quality"),
        CanonicalFingerprint.token("group[0].metric.count", "1"),
        CanonicalFingerprint.token("group[0].metric[0].kind", "best-score"),
        CanonicalFingerprint.token("group[0].metric[0].id", "best"),
        CanonicalFingerprint.token("group[0].order.count", "3"),
        CanonicalFingerprint.token("group[0].order[0].kind", "metric"),
        CanonicalFingerprint.token("group[0].order[0].metricId", "best"),
        CanonicalFingerprint.token("group[0].order[0].direction", "desc"),
        CanonicalFingerprint.token("group[0].order[1].kind", "matching-document-count"),
        CanonicalFingerprint.token("group[0].order[1].direction", "desc"),
        CanonicalFingerprint.token("group[0].order[2].kind", "key"),
        CanonicalFingerprint.token("group[0].order[2].direction", "asc"),
        CanonicalFingerprint.token("group[0].precision", "require-exact"),
        CanonicalFingerprint.token("page.size", "25"),
      )
      assert(PlanIdentityCanonicalEncoding.tokens(identityA) == expected)
    }

    "emit Shape B in the exact declared section and indexed token order" in {
      val expected = Vector(
        CanonicalFingerprint.token("identity.version", "search-plan-identity-v1"),
        CanonicalFingerprint.token("contract.fingerprint", "trail-contract-v1"),
        CanonicalFingerprint.token("query.present", "false"),
        CanonicalFingerprint.token("constraint.count", "1"),
        CanonicalFingerprint.token("constraint[0].kind", "geo-distance-filter"),
        CanonicalFingerprint.token("constraint[0].fieldId", "coordinates"),
        CanonicalFingerprint.token("constraint[0].origin", "50.1,30.2"),
        CanonicalFingerprint.token("constraint[0].radius", "2500"),
        CanonicalFingerprint.token("signal.count", "1"),
        CanonicalFingerprint.token("signal[0].kind", "geo-proximity"),
        CanonicalFingerprint.token("signal[0].fieldId", "coordinates"),
        CanonicalFingerprint.token("signal[0].origin", "50,30"),
        CanonicalFingerprint.token("sort.count", "1"),
        CanonicalFingerprint.token("sort[0].kind", "geo-distance"),
        CanonicalFingerprint.token("sort[0].fieldId", "coordinates"),
        CanonicalFingerprint.token("sort[0].origin", "50,30"),
        CanonicalFingerprint.token("sort[0].direction", "asc"),
        CanonicalFingerprint.token("facet.count", "1"),
        CanonicalFingerprint.token("facet[0].kind", "terms"),
        CanonicalFingerprint.token("facet[0].id", "trail-type-facet"),
        CanonicalFingerprint.token("facet[0].fieldId", "trailType"),
        CanonicalFingerprint.token("facet[0].size", "8"),
        CanonicalFingerprint.token("facet[0].order", "count-desc-then-key-asc"),
        CanonicalFingerprint.token("facet[0].countingPolicy", "all-applied-hard-filters"),
        CanonicalFingerprint.token("group.count", "1"),
        CanonicalFingerprint.token("group[0].id", "region-group"),
        CanonicalFingerprint.token("group[0].keyFieldId", "region"),
        CanonicalFingerprint.token("group[0].size", "3"),
        CanonicalFingerprint.token("group[0].representative.kind", "identity-only"),
        CanonicalFingerprint.token("group[0].metric.count", "1"),
        CanonicalFingerprint.token("group[0].metric[0].kind", "min-geo-distance"),
        CanonicalFingerprint.token("group[0].metric[0].id", "nearest"),
        CanonicalFingerprint.token("group[0].metric[0].fieldId", "coordinates"),
        CanonicalFingerprint.token("group[0].metric[0].origin", "50,30"),
        CanonicalFingerprint.token("group[0].order.count", "2"),
        CanonicalFingerprint.token("group[0].order[0].kind", "metric"),
        CanonicalFingerprint.token("group[0].order[0].metricId", "nearest"),
        CanonicalFingerprint.token("group[0].order[0].direction", "asc"),
        CanonicalFingerprint.token("group[0].order[1].kind", "key"),
        CanonicalFingerprint.token("group[0].order[1].direction", "asc"),
        CanonicalFingerprint.token("group[0].precision", "allow-approximate"),
        CanonicalFingerprint.token("page.size", "20"),
      )
      assert(PlanIdentityCanonicalEncoding.tokens(identityB) == expected)
    }

    "use explicit stable labels and shared length-prefixed framing" in {
      val tokens = PlanIdentityCanonicalEncoding.tokens(identityA) ++ PlanIdentityCanonicalEncoding.tokens(identityB)
      val unboundedTokens = PlanIdentityCanonicalEncoding.tokens(identityA.copy(
        hardConstraints = Vector(CanonicalConstraint.NumberRange(FieldId("quality"), CanonicalRangeBounds(CanonicalBound.Unbounded, CanonicalBound.Inclusive("3"))))
      ))
      val expectedLabels = Vector(
        CanonicalFingerprint.token("constraint[0].kind", "terms"),
        CanonicalFingerprint.token("constraint[1].kind", "number-range"),
        CanonicalFingerprint.token("constraint[2].kind", "interval-overlap"),
        CanonicalFingerprint.token("constraint[0].kind", "geo-distance-filter"),
        CanonicalFingerprint.token("signal[0].kind", "geo-proximity"),
        CanonicalFingerprint.token("sort[0].kind", "field-value"),
        CanonicalFingerprint.token("sort[0].kind", "geo-distance"),
        CanonicalFingerprint.token("constraint[0].lower.kind", "unbounded"),
        CanonicalFingerprint.token("constraint[1].lower.kind", "inclusive"),
        CanonicalFingerprint.token("constraint[1].upper.kind", "exclusive"),
        CanonicalFingerprint.token("facet[1].bucket[0].kind", "half-open"),
        CanonicalFingerprint.token("facet[1].bucket[1].kind", "upper-unbounded"),
        CanonicalFingerprint.token("group[0].representative.kind", "identity-only"),
        CanonicalFingerprint.token("group[0].representative.kind", "fields"),
        CanonicalFingerprint.token("group[0].metric[0].kind", "best-score"),
        CanonicalFingerprint.token("group[0].metric[0].kind", "min-geo-distance"),
        CanonicalFingerprint.token("group[0].order[0].kind", "metric"),
        CanonicalFingerprint.token("group[0].order[1].kind", "matching-document-count"),
        CanonicalFingerprint.token("group[0].order[1].kind", "key"),
        CanonicalFingerprint.token("sort[0].direction", "asc"),
        CanonicalFingerprint.token("sort[0].direction", "desc"),
        CanonicalFingerprint.token("facet[0].order", "count-desc-then-key-asc"),
        CanonicalFingerprint.token("facet[0].order", "key-asc"),
        CanonicalFingerprint.token("facet[0].countingPolicy", "all-applied-hard-filters"),
        CanonicalFingerprint.token("group[0].precision", "require-exact"),
        CanonicalFingerprint.token("group[0].precision", "allow-approximate"),
      )
      expectedLabels.foreach { expected =>
        assert((tokens ++ unboundedTokens).contains(expected), s"missing stable token $expected")
      }
      assert(tokens.forall(!_.contains("CanonicalConstraint")))
      assert(PlanIdentityCanonicalEncoding.block(identityA) == CanonicalFingerprint.block(PlanIdentityCanonicalEncoding.tokens(identityA)))
    }

    "encode the unbounded bound case explicitly" in {
      val identity = identityA.copy(
        hardConstraints = Vector(
          CanonicalConstraint.NumberRange(
            FieldId("quality"),
            CanonicalRangeBounds(CanonicalBound.Unbounded, CanonicalBound.Inclusive("3")),
          )
        )
      )
      val tokens = PlanIdentityCanonicalEncoding.tokens(identity)
      assert(tokens.contains(CanonicalFingerprint.token("constraint[0].lower.kind", "unbounded")))
      assert(tokens.contains(CanonicalFingerprint.token("constraint[0].upper.kind", "inclusive")))
    }

    "distinguish absent query from present empty query" in {
      val absent = viewB.identityOf(planB.copy(residualText = None))
      val empty = viewB.identityOf(planB.copy(residualText = Some("")))
      assert(PlanIdentityCanonicalEncoding.tokens(absent) != PlanIdentityCanonicalEncoding.tokens(empty))
      assert(PlanIdentityHash.compute(absent) != PlanIdentityHash.compute(empty))
    }

    "produce stable hash shapes for both neutral plans" in {
      assert(PlanIdentityHash.compute(identityA).value == "cc53c4c361afc858fe55bcd2adcf9583b6e46cef37025ece45a2dfd88a8bc3fa")
      assert(PlanIdentityHash.compute(identityB).value == "101438482ab80ad86c79e18115e662b338021dc244a24485cd1771470b3ecd8c")
      assert(PlanIdentityHash.compute(identityA) == PlanIdentityHash.compute(identityA))
    }

    "change the hash for every major canonical identity section" in {
      val variants = Vector(
        identityA.copy(contractFingerprint = testContractFingerprint("changed-contract")),
        identityA.copy(normalizedQuery = Some(NormalizedQueryText("different"))),
        identityA.copy(hardConstraints = Vector(CanonicalConstraint.NumberRange(FieldId("quality"), CanonicalRangeBounds(CanonicalBound.Unbounded, CanonicalBound.Inclusive("3"))))),
        identityA.copy(softSignals = Vector(CanonicalSignal.GeoProximity(FieldId("coordinates"), CanonicalGeoPoint("50,30")))),
        identityA.copy(sort = Vector(CanonicalSort.FieldValue(FieldId("quality"), SortDirection.Asc))),
        identityA.copy(facets = Vector(CanonicalFacetRequest.Terms(FacetId("different"), FieldId("supplier"), 1, TermsFacetOrder.KeyAsc, FacetCountingPolicy.AllAppliedHardFilters))),
        identityA.copy(groups = Vector(CanonicalGroupRequest(GroupId("different"), FieldId("supplier"), 1, CanonicalRepresentativeRequest.IdentityOnly, Vector.empty, Vector(CanonicalGroupOrder.Key(SortDirection.Asc)), GroupPrecisionPolicy.RequireExact))),
        identityA.copy(pageSize = 26),
      )

      variants.foreach { variant =>
        assert(PlanIdentityHash.compute(variant) != PlanIdentityHash.compute(identityA))
      }
    }

    "include every root and constraint component independently" in {
      val terms = CanonicalConstraint.Terms(FieldId("quality"), Vector("2"))
      val numberRange = CanonicalConstraint.NumberRange(FieldId("quality"), CanonicalRangeBounds(CanonicalBound.Inclusive("2"), CanonicalBound.Exclusive("5")))
      val interval = CanonicalConstraint.IntervalOverlap(FieldId("availabilityStart"), FieldId("availabilityEnd"), CanonicalRangeBounds(CanonicalBound.Inclusive("10"), CanonicalBound.Exclusive("20")))
      val geo = CanonicalConstraint.GeoDistanceFilter(FieldId("coordinates"), CanonicalGeoPoint("50.1,30.2"), CanonicalDistance("2500"))
      val termsIdentity = identityA.copy(hardConstraints = Vector(terms))
      val rangeIdentity = identityA.copy(hardConstraints = Vector(numberRange))
      val intervalIdentity = identityA.copy(hardConstraints = Vector(interval))
      val geoIdentity = identityB.copy(hardConstraints = Vector(geo))

      assertIdentityChanges("contract fingerprint", identityA, identityA.copy(contractFingerprint = testContractFingerprint("contract-v2")))
      assertIdentityChanges("query absent versus present", identityB.copy(normalizedQuery = None), identityB.copy(normalizedQuery = Some(NormalizedQueryText("query"))))
      assertIdentityChanges("query value", identityA, identityA.copy(normalizedQuery = Some(NormalizedQueryText("different query"))))
      assertIdentityChanges("page size", identityA, identityA.copy(pageSize = 26))
      assertIdentityChanges("hard-constraint vector order", identityA, identityA.copy(hardConstraints = identityA.hardConstraints.reverse))
      assertIdentityChanges("constraint kind", termsIdentity, rangeIdentity)
      assertIdentityChanges("Terms FieldId", termsIdentity, termsIdentity.copy(hardConstraints = Vector(terms.copy(fieldId = FieldId("department-v2")))))
      assertIdentityChanges("Terms canonical values", termsIdentity, termsIdentity.copy(hardConstraints = Vector(terms.copy(values = Vector("retail", "wholesale", "vip")))))
      assertIdentityChanges("NumberRange FieldId", rangeIdentity, rangeIdentity.copy(hardConstraints = Vector(numberRange.copy(fieldId = FieldId("quality-v2")))))
      assertIdentityChanges("NumberRange lower bound kind", rangeIdentity, rangeIdentity.copy(hardConstraints = Vector(numberRange.copy(bounds = numberRange.bounds.copy(lower = CanonicalBound.Exclusive("2"))))))
      assertIdentityChanges("NumberRange lower bound value", rangeIdentity, rangeIdentity.copy(hardConstraints = Vector(numberRange.copy(bounds = numberRange.bounds.copy(lower = CanonicalBound.Inclusive("3"))))))
      assertIdentityChanges("NumberRange upper bound kind", rangeIdentity, rangeIdentity.copy(hardConstraints = Vector(numberRange.copy(bounds = numberRange.bounds.copy(upper = CanonicalBound.Inclusive("5"))))))
      assertIdentityChanges("NumberRange upper bound value", rangeIdentity, rangeIdentity.copy(hardConstraints = Vector(numberRange.copy(bounds = numberRange.bounds.copy(upper = CanonicalBound.Exclusive("6"))))))
      assertIdentityChanges("IntervalOverlap from FieldId", intervalIdentity, intervalIdentity.copy(hardConstraints = Vector(interval.copy(fromFieldId = FieldId("availabilityStart-v2")))))
      assertIdentityChanges("IntervalOverlap to FieldId", intervalIdentity, intervalIdentity.copy(hardConstraints = Vector(interval.copy(toFieldId = FieldId("availabilityEnd-v2")))))
      assertIdentityChanges("IntervalOverlap lower bound kind", intervalIdentity, intervalIdentity.copy(hardConstraints = Vector(interval.copy(bounds = interval.bounds.copy(lower = CanonicalBound.Exclusive("10"))))))
      assertIdentityChanges("IntervalOverlap lower bound value", intervalIdentity, intervalIdentity.copy(hardConstraints = Vector(interval.copy(bounds = interval.bounds.copy(lower = CanonicalBound.Inclusive("11"))))))
      assertIdentityChanges("GeoDistanceFilter FieldId", geoIdentity, geoIdentity.copy(hardConstraints = Vector(geo.copy(fieldId = FieldId("coordinates-v2")))))
      assertIdentityChanges("GeoDistanceFilter origin", geoIdentity, geoIdentity.copy(hardConstraints = Vector(geo.copy(origin = CanonicalGeoPoint("51,31")))))
      assertIdentityChanges("GeoDistanceFilter radius", geoIdentity, geoIdentity.copy(hardConstraints = Vector(geo.copy(radius = CanonicalDistance("3000")))))
    }

    "include every signal and sort component independently" in {
      val signalOne = CanonicalSignal.GeoProximity(FieldId("coordinates"), CanonicalGeoPoint("50,30"))
      val signalTwo = CanonicalSignal.GeoProximity(FieldId("region-centre"), CanonicalGeoPoint("51,31"))
      val signals = identityB.copy(softSignals = Vector(signalOne, signalTwo))
      assertIdentityChanges("soft-signal vector order", signals, signals.copy(softSignals = signals.softSignals.reverse))
      assertIdentityChanges("GeoProximity FieldId", signals, signals.copy(softSignals = Vector(signalOne.copy(fieldId = FieldId("coordinates-v2")), signalTwo)))
      assertIdentityChanges("GeoProximity origin", signals, signals.copy(softSignals = Vector(signalOne.copy(origin = CanonicalGeoPoint("52,32")), signalTwo)))

      val fieldSort = CanonicalSort.FieldValue(FieldId("quality"), SortDirection.Desc)
      val fieldSortTwo = CanonicalSort.FieldValue(FieldId("department"), SortDirection.Asc)
      val fieldSorts = identityA.copy(sort = Vector(fieldSort, fieldSortTwo))
      assertIdentityChanges("sort vector order", fieldSorts, fieldSorts.copy(sort = fieldSorts.sort.reverse))
      assertIdentityChanges("sort variant", identityA, identityA.copy(sort = Vector(CanonicalSort.GeoDistance(FieldId("coordinates"), CanonicalGeoPoint("50,30"), SortDirection.Asc))))
      assertIdentityChanges("FieldValue FieldId", identityA, identityA.copy(sort = Vector(fieldSort.copy(fieldId = FieldId("quality-v2")))))
      assertIdentityChanges("FieldValue direction", identityA, identityA.copy(sort = Vector(fieldSort.copy(direction = SortDirection.Asc))))
      val geoSort = CanonicalSort.GeoDistance(FieldId("coordinates"), CanonicalGeoPoint("50,30"), SortDirection.Asc)
      val geoSortIdentity = identityB.copy(sort = Vector(geoSort))
      assertIdentityChanges("GeoDistance FieldId", geoSortIdentity, geoSortIdentity.copy(sort = Vector(geoSort.copy(fieldId = FieldId("coordinates-v2")))))
      assertIdentityChanges("GeoDistance origin", geoSortIdentity, geoSortIdentity.copy(sort = Vector(geoSort.copy(origin = CanonicalGeoPoint("51,31")))))
      assertIdentityChanges("GeoDistance direction", geoSortIdentity, geoSortIdentity.copy(sort = Vector(geoSort.copy(direction = SortDirection.Desc))))
    }

    "include every facet and bucket component independently" in {
      val termsFacet = CanonicalFacetRequest.Terms(FacetId("supplier-facet"), FieldId("supplier"), 12, TermsFacetOrder.KeyAsc, FacetCountingPolicy.AllAppliedHardFilters)
      val termsFacets = identityA.copy(facets = Vector(termsFacet, termsFacet.copy(id = FacetId("second-facet"))))
      assertIdentityChanges("facet vector order", termsFacets, termsFacets.copy(facets = termsFacets.facets.reverse))
      val singleTermsFacetIdentity = identityA.copy(facets = Vector(termsFacet))
      assertIdentityChanges("facet variant", singleTermsFacetIdentity, singleTermsFacetIdentity.copy(facets = Vector(CanonicalFacetRequest.NumberRange(FacetId("supplier-facet"), FieldId("supplier"), Vector(CanonicalFacetBucket.UpperUnbounded(FacetBucketId("all"), "0")), FacetCountingPolicy.AllAppliedHardFilters))))
      assertIdentityChanges("facet ID", identityA, identityA.copy(facets = identityA.facets.updated(0, termsFacet.copy(id = FacetId("supplier-facet-v2")))))
      assertIdentityChanges("terms facet FieldId", identityA, identityA.copy(facets = identityA.facets.updated(0, termsFacet.copy(fieldId = FieldId("supplier-v2")))))
      assertIdentityChanges("terms facet size", identityA, identityA.copy(facets = identityA.facets.updated(0, termsFacet.copy(size = 13))))
      assertIdentityChanges("terms facet order", identityA, identityA.copy(facets = identityA.facets.updated(0, termsFacet.copy(order = TermsFacetOrder.CountDescThenKeyAsc))))

      val numberBuckets = Vector[CanonicalFacetBucket](CanonicalFacetBucket.HalfOpen(FacetBucketId("q-low"), "0", "3"), CanonicalFacetBucket.UpperUnbounded(FacetBucketId("q-high"), "3"))
      val numberFacet = CanonicalFacetRequest.NumberRange(FacetId("quality-facet"), FieldId("quality"), numberBuckets, FacetCountingPolicy.AllAppliedHardFilters)
      val numberFacetIdentity = identityA.copy(facets = Vector(numberFacet))
      assertIdentityChanges("number-range facet FieldId", numberFacetIdentity, numberFacetIdentity.copy(facets = Vector(numberFacet.copy(fieldId = FieldId("quality-v2")))))

      val intervalBuckets = Vector[CanonicalFacetBucket](CanonicalFacetBucket.HalfOpen(FacetBucketId("short"), "0", "10"), CanonicalFacetBucket.UpperUnbounded(FacetBucketId("long"), "10"))
      val intervalFacet = CanonicalFacetRequest.IntervalOverlap(FacetId("availability-facet"), FieldId("availabilityStart"), FieldId("availabilityEnd"), intervalBuckets, FacetCountingPolicy.AllAppliedHardFilters)
      val intervalFacetIdentity = identityA.copy(facets = Vector(intervalFacet))
      assertIdentityChanges("interval facet from FieldId", intervalFacetIdentity, intervalFacetIdentity.copy(facets = Vector(intervalFacet.copy(fromFieldId = FieldId("availabilityStart-v2")))))
      assertIdentityChanges("interval facet to FieldId", intervalFacetIdentity, intervalFacetIdentity.copy(facets = Vector(intervalFacet.copy(toFieldId = FieldId("availabilityEnd-v2")))))
      assertIdentityChanges("bucket ID", numberFacetIdentity, numberFacetIdentity.copy(facets = Vector(numberFacet.copy(buckets = Vector(CanonicalFacetBucket.HalfOpen(FacetBucketId("q-low-v2"), "0", "3"), CanonicalFacetBucket.UpperUnbounded(FacetBucketId("q-high"), "3"))))))
      assertIdentityChanges("bucket kind", numberFacetIdentity, numberFacetIdentity.copy(facets = Vector(numberFacet.copy(buckets = Vector(CanonicalFacetBucket.UpperUnbounded(FacetBucketId("q-low"), "0"), CanonicalFacetBucket.UpperUnbounded(FacetBucketId("q-high"), "3"))))))
      assertIdentityChanges("bucket minimum", numberFacetIdentity, numberFacetIdentity.copy(facets = Vector(numberFacet.copy(buckets = Vector(CanonicalFacetBucket.HalfOpen(FacetBucketId("q-low"), "1", "3"), CanonicalFacetBucket.UpperUnbounded(FacetBucketId("q-high"), "3"))))))
      assertIdentityChanges("half-open bucket maximum", numberFacetIdentity, numberFacetIdentity.copy(facets = Vector(numberFacet.copy(buckets = Vector(CanonicalFacetBucket.HalfOpen(FacetBucketId("q-low"), "0", "4"), CanonicalFacetBucket.UpperUnbounded(FacetBucketId("q-high"), "3"))))))
      assertIdentityChanges("bucket declaration order", numberFacetIdentity, numberFacetIdentity.copy(facets = Vector(numberFacet.copy(buckets = numberFacet.buckets.reverse))))
      assert(PlanIdentityCanonicalEncoding.tokens(identityA).contains(CanonicalFingerprint.token("facet[0].countingPolicy", "all-applied-hard-filters")))
    }

    "include every group, representative, metric and order component independently" in {
      val groupBase = CanonicalGroupRequest(
        GroupId("supplier-group"),
        FieldId("supplier"),
        5,
        CanonicalRepresentativeRequest.Fields(Vector(FieldId("headline"), FieldId("quality"))),
        Vector(
          CanonicalGroupMetric.BestScore(GroupMetricId("best")),
          CanonicalGroupMetric.MinGeoDistance(GroupMetricId("nearest"), FieldId("coordinates"), CanonicalGeoPoint("50,30")),
        ),
        Vector(
          CanonicalGroupOrder.Metric(GroupMetricId("best"), SortDirection.Desc),
          CanonicalGroupOrder.MatchingDocumentCount(SortDirection.Desc),
          CanonicalGroupOrder.Key(SortDirection.Asc),
        ),
        GroupPrecisionPolicy.RequireExact,
      )
      val groupIdentity = identityA.copy(groups = Vector(groupBase))
      val secondGroup = groupBase.copy(id = GroupId("second-group"))
      assertIdentityChanges("group vector order", identityA.copy(groups = Vector(groupBase, secondGroup)), identityA.copy(groups = Vector(secondGroup, groupBase)))
      assertIdentityChanges("group ID", groupIdentity, groupIdentity.copy(groups = Vector(groupBase.copy(id = GroupId("supplier-group-v2")))))
      assertIdentityChanges("group key FieldId", groupIdentity, groupIdentity.copy(groups = Vector(groupBase.copy(keyFieldId = FieldId("supplier-v2")))))
      assertIdentityChanges("group size", groupIdentity, groupIdentity.copy(groups = Vector(groupBase.copy(size = 6))))
      assertIdentityChanges("representative kind", groupIdentity, groupIdentity.copy(groups = Vector(groupBase.copy(representative = CanonicalRepresentativeRequest.IdentityOnly))))
      assertIdentityChanges("representative FieldId", groupIdentity, groupIdentity.copy(groups = Vector(groupBase.copy(representative = CanonicalRepresentativeRequest.Fields(Vector(FieldId("headline-v2"), FieldId("quality")))))))
      assertIdentityChanges("representative field order", groupIdentity, groupIdentity.copy(groups = Vector(groupBase.copy(representative = CanonicalRepresentativeRequest.Fields(Vector(FieldId("quality"), FieldId("headline")))))))
      assertIdentityChanges("metric ID", groupIdentity, groupIdentity.copy(groups = Vector(groupBase.copy(metrics = groupBase.metrics.updated(0, CanonicalGroupMetric.BestScore(GroupMetricId("best-v2")))))))
      assertIdentityChanges("metric kind", groupIdentity, groupIdentity.copy(groups = Vector(groupBase.copy(metrics = groupBase.metrics.updated(0, CanonicalGroupMetric.MinGeoDistance(GroupMetricId("best"), FieldId("coordinates"), CanonicalGeoPoint("50,30")))))))
      assertIdentityChanges("MinGeoDistance FieldId", groupIdentity, groupIdentity.copy(groups = Vector(groupBase.copy(metrics = groupBase.metrics.updated(1, CanonicalGroupMetric.MinGeoDistance(GroupMetricId("nearest"), FieldId("coordinates-v2"), CanonicalGeoPoint("50,30")))))))
      assertIdentityChanges("MinGeoDistance origin", groupIdentity, groupIdentity.copy(groups = Vector(groupBase.copy(metrics = groupBase.metrics.updated(1, CanonicalGroupMetric.MinGeoDistance(GroupMetricId("nearest"), FieldId("coordinates"), CanonicalGeoPoint("51,31")))))))
      assertIdentityChanges("metric declaration order", groupIdentity, groupIdentity.copy(groups = Vector(groupBase.copy(metrics = groupBase.metrics.reverse))))
      val groupOrderKind = groupBase.copy(order = Vector(CanonicalGroupOrder.Metric(GroupMetricId("best"), SortDirection.Desc), CanonicalGroupOrder.Key(SortDirection.Asc)))
      val groupOrderKindIdentity = identityA.copy(groups = Vector(groupOrderKind))
      assertIdentityChanges("group-order criterion kind", groupOrderKindIdentity, groupOrderKindIdentity.copy(groups = Vector(groupOrderKind.copy(order = Vector(CanonicalGroupOrder.MatchingDocumentCount(SortDirection.Desc), CanonicalGroupOrder.Key(SortDirection.Asc))))))
      assertIdentityChanges("group-order metric ID", groupIdentity, groupIdentity.copy(groups = Vector(groupBase.copy(order = groupBase.order.updated(0, CanonicalGroupOrder.Metric(GroupMetricId("best-v2"), SortDirection.Desc))))))
      assertIdentityChanges("group-order direction", groupIdentity, groupIdentity.copy(groups = Vector(groupBase.copy(order = groupBase.order.updated(0, CanonicalGroupOrder.Metric(GroupMetricId("best"), SortDirection.Asc))))))
      assertIdentityChanges("group-order sequence", groupIdentity, groupIdentity.copy(groups = Vector(groupBase.copy(order = groupBase.order.reverse))))
      assertIdentityChanges("group precision", groupIdentity, groupIdentity.copy(groups = Vector(groupBase.copy(precision = GroupPrecisionPolicy.AllowApproximate))))
    }
  }
}
