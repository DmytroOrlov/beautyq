package leaderboard.search.gen2.core.plan

import leaderboard.search.gen2.contract.*
import org.scalatest.wordspec.AnyWordSpec

import java.util.UUID

/** Neutral fixtures proving canonical constraint projection and slot derivation are independent of any
  * one domain's document shape. InventoryDocument (department/stock/coordinates) and TrailDocument
  * (region/location) share no name, field, or vocabulary with each other or with any owning business
  * domain in this repository.
  */
final class CanonicalConstraintViewSpec extends AnyWordSpec {

  private final case class InventoryDocument(id: UUID, department: String, stockMin: Int, stockMax: Int, coordinates: GeoPoint)
  private final case class TrailDocument(id: UUID, region: String, location: GeoPoint)

  private val department = field[InventoryDocument, String]("department", _.department).keyword.filterable(FilterOperator.Equal, FilterOperator.In)
  private val stockMin = field[InventoryDocument, Int]("stockMin", _.stockMin).integer.filterable(FilterOperator.Range)
  private val stockMax = field[InventoryDocument, Int]("stockMax", _.stockMax).integer.filterable(FilterOperator.Range)
  private val coordinates = field[InventoryDocument, GeoPoint]("coordinates", _.coordinates).geoPoint.filterable(FilterOperator.GeoDistance)

  // Same FieldId as `department`, but a different path, extractor, semantic and capability set - proves
  // canonical projection depends only on FieldId and codec-encoded value, never on these.
  private val departmentAlternateHandle =
    computedField[InventoryDocument, String]("department", "alt.path.department")(doc => Some(doc.department))
      .keyword
      .withSemantic("alt-semantic")
      .facetable(FacetMode.Terms)

  private val location = field[TrailDocument, GeoPoint]("location", _.location).geoPoint.filterable(FilterOperator.GeoDistance)

  private val origin = GeoPoint(BigDecimal("10.0"), BigDecimal("20.0"))
  private val radius = Distance(500)

  "CanonicalConstraintView.apply" should {
    "project Terms with codec-encoded, deduplicated, lexicographically sorted values" in {
      val constraint = PlannedConstraint.Terms(department, Set("shoes", "apparel", "apparel"))
      assert(CanonicalConstraintView(constraint) == CanonicalConstraint.Terms(FieldId("department"), Vector("apparel", "shoes")))
    }

    "project NumberRange with codec-encoded bounds" in {
      val constraint = PlannedConstraint.NumberRange(stockMin, RangeBounds(Bound.Inclusive(1), Bound.Exclusive(10)))
      assert(
        CanonicalConstraintView(constraint) ==
          CanonicalConstraint.NumberRange(FieldId("stockMin"), CanonicalRangeBounds(CanonicalBound.Inclusive("1"), CanonicalBound.Exclusive("10")))
      )
    }

    "project IntervalOverlap using the from field's codec for both bounds" in {
      val constraint = PlannedConstraint.IntervalOverlap(stockMin, stockMax, RangeBounds(Bound.Inclusive(2), Bound.Inclusive(8)))
      assert(
        CanonicalConstraintView(constraint) ==
          CanonicalConstraint.IntervalOverlap(FieldId("stockMin"), FieldId("stockMax"), CanonicalRangeBounds(CanonicalBound.Inclusive("2"), CanonicalBound.Inclusive("8")))
      )
    }

    "project GeoDistanceFilter with canonical origin and radius, on a structurally unrelated document" in {
      val constraint = PlannedConstraint.GeoDistanceFilter(location, origin, radius)
      assert(CanonicalConstraintView(constraint) == CanonicalConstraint.GeoDistanceFilter(FieldId("location"), CanonicalGeoPoint("10,20"), CanonicalDistance("500")))
    }

    "produce the identical canonical constraint for the same FieldId and equal value regardless of path, semantic, capabilities or extractor identity" in {
      val viaDirect = CanonicalConstraintView(PlannedConstraint.Terms(department, Set("shoes")))
      val viaAlternate = CanonicalConstraintView(PlannedConstraint.Terms(departmentAlternateHandle, Set("shoes")))
      assert(viaDirect == viaAlternate)
    }

    "change the canonical value - and therefore equivalence - when the underlying value changes" in {
      val a = CanonicalConstraintView(PlannedConstraint.Terms(department, Set("shoes")))
      val b = CanonicalConstraintView(PlannedConstraint.Terms(department, Set("apparel")))
      assert(a != b)
    }

    "project a second, structurally unrelated document's GeoDistanceFilter identically to the first, independent of document shape" in {
      val inventoryGeo = CanonicalConstraintView(PlannedConstraint.GeoDistanceFilter(coordinates, origin, radius))
      val trailGeo = CanonicalConstraintView(PlannedConstraint.GeoDistanceFilter(location, origin, radius))
      assert(inventoryGeo == CanonicalConstraint.GeoDistanceFilter(FieldId("coordinates"), CanonicalGeoPoint("10,20"), CanonicalDistance("500")))
      assert(trailGeo == CanonicalConstraint.GeoDistanceFilter(FieldId("location"), CanonicalGeoPoint("10,20"), CanonicalDistance("500")))
    }
  }

  "CanonicalConstraintView.slot" should {
    "include the constraint kind, so Terms and NumberRange over the same FieldId are different slots" in {
      val termsSlot = CanonicalConstraintView.slot(CanonicalConstraint.Terms(FieldId("stockMin"), Vector("1")))
      val rangeSlot = CanonicalConstraintView.slot(CanonicalConstraint.NumberRange(FieldId("stockMin"), CanonicalRangeBounds(CanonicalBound.Unbounded, CanonicalBound.Unbounded)))
      assert(termsSlot != rangeSlot)
    }

    "use FieldId only, unaffected by path, semantic, capabilities or extractor identity" in {
      val direct = CanonicalConstraintView.slot(CanonicalConstraintView(PlannedConstraint.Terms(department, Set("shoes"))))
      val alternate = CanonicalConstraintView.slot(CanonicalConstraintView(PlannedConstraint.Terms(departmentAlternateHandle, Set("shoes"))))
      assert(direct == alternate)
      assert(direct == ConstraintSlot.Terms(FieldId("department")))
    }

    "change when the FieldId changes, even for an otherwise identical Terms constraint" in {
      val a = CanonicalConstraintView.slot(CanonicalConstraintView(PlannedConstraint.Terms(department, Set("shoes"))))
      val otherField = field[InventoryDocument, String]("otherDepartment", _.department).keyword
      val b = CanonicalConstraintView.slot(CanonicalConstraintView(PlannedConstraint.Terms(otherField, Set("shoes"))))
      assert(a != b)
    }

    "derive an IntervalOverlap slot from the from/to FieldId pair and a GeoDistanceFilter slot from its FieldId" in {
      val interval = CanonicalConstraintView.slot(CanonicalConstraintView(PlannedConstraint.IntervalOverlap(stockMin, stockMax, RangeBounds(Bound.Unbounded, Bound.Unbounded))))
      val geo = CanonicalConstraintView.slot(CanonicalConstraintView(PlannedConstraint.GeoDistanceFilter(coordinates, origin, radius)))
      assert(interval == ConstraintSlot.IntervalOverlap(FieldId("stockMin"), FieldId("stockMax")))
      assert(geo == ConstraintSlot.GeoDistanceFilter(FieldId("coordinates")))
    }

    "derive equal slots for the same Terms field across two structurally unrelated documents' otherwise unrelated fields sharing one FieldId value" in {
      val inventorySlot = CanonicalConstraintView.slot(CanonicalConstraintView(PlannedConstraint.Terms(department, Set("shoes"))))
      val sameIdOnTrail = field[TrailDocument, String]("department", _.region).keyword
      val trailSlot = CanonicalConstraintView.slot(CanonicalConstraintView(PlannedConstraint.Terms(sameIdOnTrail, Set("shoes"))))
      assert(inventorySlot == trailSlot)
    }
  }
}
