package leaderboard.search.gen2.contract

import org.scalatest.wordspec.AnyWordSpec

import java.util.UUID

/** Neutral fixtures proving the plan-time constraint/signal/sort algebra, independent of any one
  * domain's document shape. WindowDocument exercises IntervalOverlap over a non-price interval
  * (deliberately not BeautyQ's price pair); VenueDocument exercises the geo filter/signal/sort trio.
  */
final class PlannedConstraintSpec extends AnyWordSpec {

  private final case class WindowDocument(
    id: UUID,
    label: String,
    openFrom: Int,
    openTo: Int,
  )

  private final case class VenueDocument(
    id: UUID,
    name: String,
    location: GeoPoint,
  )

  // A second, independent SearchValueCodec[Int] instance - same Scala type as SearchValueCodec.int,
  // different logical identity - proving that two SearchField[Document, Int] handles are not
  // guaranteed to share one codec merely by sharing one Scala type parameter.
  private val alternateIntCodec: SearchValueCodec[Int] =
    SearchValueCodec.int.imap(SearchValueTypeId("alternate-int"))(Right(_), identity)

  private val rangeCapableFrom =
    field[WindowDocument, Int]("openFrom", _.openFrom).integer.filterable(FilterOperator.Range)

  private val rangeCapableTo =
    field[WindowDocument, Int]("openTo", _.openTo).integer.filterable(FilterOperator.Range)

  private val rangeCapableToAlternateCodec =
    field[WindowDocument, Int]("openTo", _.openTo)(using alternateIntCodec).integer.filterable(FilterOperator.Range)

  private val notRangeCapableTo =
    field[WindowDocument, Int]("openTo", _.openTo).integer

  private val equalOnlyLabel =
    field[WindowDocument, String]("label", _.label).keyword.filterable(FilterOperator.Equal)

  private val inOnlyLabel =
    field[WindowDocument, String]("label", _.label).keyword.filterable(FilterOperator.In)

  private val equalAndInLabel =
    field[WindowDocument, String]("label", _.label).keyword.filterable(FilterOperator.Equal, FilterOperator.In)

  private val noFilterCapableLabel =
    field[WindowDocument, String]("label", _.label).keyword

  private val sortableOpenFrom =
    field[WindowDocument, Int]("openFrom", _.openFrom).integer.sortable(SortMode.Value)

  private val notSortableOpenFrom =
    field[WindowDocument, Int]("openFrom", _.openFrom).integer

  private val geoFilterableLocation =
    field[VenueDocument, GeoPoint]("location", _.location).geoPoint.filterable(FilterOperator.GeoDistance)

  private val geoNotFilterableLocation =
    field[VenueDocument, GeoPoint]("location", _.location).geoPoint

  private val geoSortableLocation =
    field[VenueDocument, GeoPoint]("location", _.location).geoPoint.sortable(SortMode.Distance)

  private val geoNotSortableLocation =
    field[VenueDocument, GeoPoint]("location", _.location).geoPoint

  private val origin = GeoPoint(BigDecimal("1.5"), BigDecimal("2.5"))
  private val bounds  = RangeBounds(Bound.Inclusive(0), Bound.Exclusive(100))

  "PlannedConstraint.validate" should {
    "accept Terms on a field filterable only by Equal" in {
      assert(PlannedConstraint.validate(PlannedConstraint.Terms(equalOnlyLabel, Set("a", "b"))).isRight)
    }

    "accept Terms on a field filterable only by In" in {
      assert(PlannedConstraint.validate(PlannedConstraint.Terms(inOnlyLabel, Set("a", "b"))).isRight)
    }

    "accept Terms on a field filterable by both Equal and In" in {
      assert(PlannedConstraint.validate(PlannedConstraint.Terms(equalAndInLabel, Set("a", "b"))).isRight)
    }

    "reject Terms on a field with neither Equal nor In" in {
      PlannedConstraint.validate(PlannedConstraint.Terms(noFilterCapableLabel, Set("a"))) match {
        case Left(errors) =>
          assert(
            errors.toVector ==
              Vector(PlanConstraintError.UnsupportedFilterOperator(noFilterCapableLabel.id, SearchFieldKind.Keyword, Vector(FilterOperator.Equal, FilterOperator.In)))
          )
        case Right(value) => fail(s"expected rejection, got: $value")
      }
    }

    "accept NumberRange on a Range-filterable field" in {
      assert(PlannedConstraint.validate(PlannedConstraint.NumberRange(rangeCapableFrom, bounds)).isRight)
    }

    "reject NumberRange on a field without Range" in {
      PlannedConstraint.validate(PlannedConstraint.NumberRange(notSortableOpenFrom, bounds)) match {
        case Left(errors) =>
          assert(
            errors.toVector ==
              Vector(PlanConstraintError.UnsupportedFilterOperator(notSortableOpenFrom.id, SearchFieldKind.Integer, Vector(FilterOperator.Range)))
          )
        case Right(value) => fail(s"expected rejection, got: $value")
      }
    }

    "accept IntervalOverlap when both fields are Range-filterable and share one codec identity" in {
      assert(PlannedConstraint.validate(PlannedConstraint.IntervalOverlap(rangeCapableFrom, rangeCapableTo, bounds)).isRight)
    }

    "reject IntervalOverlap with MismatchedIntervalFields when both fields are Range-filterable but use different codecs" in {
      PlannedConstraint.validate(PlannedConstraint.IntervalOverlap(rangeCapableFrom, rangeCapableToAlternateCodec, bounds)) match {
        case Left(errors) =>
          assert(
            errors.toVector ==
              Vector(
                PlanConstraintError.MismatchedIntervalFields(
                  rangeCapableFrom.id,
                  rangeCapableToAlternateCodec.id,
                  SearchValueTypeId("int"),
                  SearchValueTypeId("alternate-int"),
                )
              )
          )
        case Right(value) => fail(s"expected rejection, got: $value")
      }
    }

    "reject IntervalOverlap with UnsupportedFilterOperator when only one field is Range-filterable, without a spurious MismatchedIntervalFields" in {
      PlannedConstraint.validate(PlannedConstraint.IntervalOverlap(rangeCapableFrom, notRangeCapableTo, bounds)) match {
        case Left(errors) =>
          assert(
            errors.toVector ==
              Vector(PlanConstraintError.UnsupportedFilterOperator(notRangeCapableTo.id, SearchFieldKind.Integer, Vector(FilterOperator.Range)))
          )
        case Right(value) => fail(s"expected rejection, got: $value")
      }
    }

    "accumulate both the from-field and to-field violations, in that deterministic order, when neither is Range-filterable" in {
      val neitherRangeFrom = field[WindowDocument, Int]("openFrom", _.openFrom).integer
      val neitherRangeTo   = field[WindowDocument, Int]("openTo", _.openTo).integer

      PlannedConstraint.validate(PlannedConstraint.IntervalOverlap(neitherRangeFrom, neitherRangeTo, bounds)) match {
        case Left(errors) =>
          assert(
            errors.toVector == Vector(
              PlanConstraintError.UnsupportedFilterOperator(neitherRangeFrom.id, SearchFieldKind.Integer, Vector(FilterOperator.Range)),
              PlanConstraintError.UnsupportedFilterOperator(neitherRangeTo.id, SearchFieldKind.Integer, Vector(FilterOperator.Range)),
            )
          )
        case Right(value) => fail(s"expected rejection, got: $value")
      }
    }

    "accept GeoDistanceFilter on a GeoDistance-filterable field" in {
      assert(PlannedConstraint.validate(PlannedConstraint.GeoDistanceFilter(geoFilterableLocation, origin, Distance(500))).isRight)
    }

    "reject GeoDistanceFilter on a field without GeoDistance" in {
      PlannedConstraint.validate(PlannedConstraint.GeoDistanceFilter(geoNotFilterableLocation, origin, Distance(500))) match {
        case Left(errors) =>
          assert(
            errors.toVector ==
              Vector(PlanConstraintError.UnsupportedFilterOperator(geoNotFilterableLocation.id, SearchFieldKind.GeoPoint, Vector(FilterOperator.GeoDistance)))
          )
        case Right(value) => fail(s"expected rejection, got: $value")
      }
    }
  }

  "PlannedSignal.GeoProximitySignal" should {
    "construct directly over a GeoPoint field with no capability requirement" in {
      val signal = PlannedSignal.GeoProximitySignal(geoNotFilterableLocation, origin)
      assert(signal.field eq geoNotFilterableLocation)
      assert(signal.origin == origin)
    }
  }

  "PlannedSort.validate" should {
    "accept FieldValue sort on a Value-sortable field" in {
      assert(PlannedSort.validate(PlannedSort.FieldValue(sortableOpenFrom, SortDirection.Asc)).isRight)
    }

    "reject FieldValue sort on a field without SortMode.Value" in {
      PlannedSort.validate(PlannedSort.FieldValue(notSortableOpenFrom, SortDirection.Asc)) match {
        case Left(errors) =>
          assert(errors.toVector == Vector(PlanConstraintError.UnsupportedSortMode(notSortableOpenFrom.id, SearchFieldKind.Integer, SortMode.Value)))
        case Right(value) => fail(s"expected rejection, got: $value")
      }
    }

    "accept GeoDistance sort on a Distance-sortable field" in {
      assert(PlannedSort.validate(PlannedSort.GeoDistance(geoSortableLocation, origin, SortDirection.Asc)).isRight)
    }

    "reject GeoDistance sort on a field without SortMode.Distance" in {
      PlannedSort.validate(PlannedSort.GeoDistance(geoNotSortableLocation, origin, SortDirection.Asc)) match {
        case Left(errors) =>
          assert(errors.toVector == Vector(PlanConstraintError.UnsupportedSortMode(geoNotSortableLocation.id, SearchFieldKind.GeoPoint, SortMode.Distance)))
        case Right(value) => fail(s"expected rejection, got: $value")
      }
    }
  }

  "document-type safety" should {
    "reject a PlannedConstraint declared for one document type where another document type is expected, at compile time" in {
      assertDoesNotCompile(
        """
          |val wrongDocumentConstraint: PlannedConstraint[VenueDocument] =
          |  PlannedConstraint.Terms(equalAndInLabel, Set("a"))
          |""".stripMargin
      )
    }
  }

  // One golden trace per Brick 4A variant, each asserted as a complete string, not a substring/contains
  // check - the exact text is the specification. Fixture values are deliberately chosen so unrelated
  // variants never collide (e.g. openFrom/openTo appear with different type IDs only in the
  // MismatchedIntervalFields case) and so all three Bound forms (unbounded, inclusive, exclusive)
  // appear at least once across the block.
  "PlannedAlgebraTrace" should {
    "render Terms with values sorted lexicographically, independent of the input Set's own order" in {
      val trace = PlannedAlgebraTrace.constraint(PlannedConstraint.Terms(equalAndInLabel, Set("banana", "apple")))
      assert(trace == "constraint.terms field=label:string values=[apple, banana]")
    }

    "render NumberRange with an unbounded lower bound and an exclusive upper bound" in {
      val trace =
        PlannedAlgebraTrace.constraint(PlannedConstraint.NumberRange(rangeCapableFrom, RangeBounds(Bound.Unbounded, Bound.Exclusive(100))))
      assert(trace == "constraint.number-range field=openFrom:int bounds=[unbounded, exclusive(100)]")
    }

    "render IntervalOverlap with an inclusive lower bound and an exclusive upper bound, using both field IDs" in {
      val trace = PlannedAlgebraTrace.constraint(PlannedConstraint.IntervalOverlap(rangeCapableFrom, rangeCapableTo, bounds))
      assert(trace == "constraint.interval-overlap from=openFrom:int to=openTo:int bounds=[inclusive(0), exclusive(100)]")
    }

    "render GeoDistanceFilter with a canonically encoded origin and radius" in {
      val trace = PlannedAlgebraTrace.constraint(PlannedConstraint.GeoDistanceFilter(geoFilterableLocation, origin, Distance(500)))
      assert(trace == "constraint.geo-distance-filter field=location:geo-point origin=1.5,2.5 radius=500")
    }

    "render GeoProximitySignal with a canonically encoded origin" in {
      val trace = PlannedAlgebraTrace.signal(PlannedSignal.GeoProximitySignal(geoNotFilterableLocation, origin))
      assert(trace == "signal.geo-proximity field=location:geo-point origin=1.5,2.5")
    }

    "render a FieldValue sort with an explicit direction label" in {
      val trace = PlannedAlgebraTrace.sort(PlannedSort.FieldValue(sortableOpenFrom, SortDirection.Asc))
      assert(trace == "sort.field-value field=openFrom:int direction=Asc")
    }

    "render a GeoDistance sort with a canonically encoded origin and an explicit direction label" in {
      val trace = PlannedAlgebraTrace.sort(PlannedSort.GeoDistance(geoSortableLocation, origin, SortDirection.Asc))
      assert(trace == "sort.geo-distance field=location:geo-point origin=1.5,2.5 direction=Asc")
    }

    "render UnsupportedFilterOperator with accepted operators in declared Equal-then-In order" in {
      val trace =
        PlannedAlgebraTrace.error(
          PlanConstraintError.UnsupportedFilterOperator(noFilterCapableLabel.id, SearchFieldKind.Keyword, Vector(FilterOperator.Equal, FilterOperator.In))
        )
      assert(trace == "error.unsupported-filter-operator field=label kind=Keyword accepted=[Equal, In]")
    }

    "render MismatchedIntervalFields with both field IDs and both, genuinely different, type IDs" in {
      val trace =
        PlannedAlgebraTrace.error(
          PlanConstraintError.MismatchedIntervalFields(
            rangeCapableFrom.id,
            rangeCapableToAlternateCodec.id,
            SearchValueTypeId("int"),
            SearchValueTypeId("alternate-int"),
          )
        )
      assert(trace == "error.mismatched-interval-fields from=openFrom:int to=openTo:alternate-int")
      assert(trace.contains("openFrom") && trace.contains("openTo"))
      assert(trace.contains(":int") && trace.contains(":alternate-int"))
    }

    "render UnsupportedSortMode with an explicit required-mode label" in {
      val trace = PlannedAlgebraTrace.error(PlanConstraintError.UnsupportedSortMode(notSortableOpenFrom.id, SearchFieldKind.Integer, SortMode.Value))
      assert(trace == "error.unsupported-sort-mode field=openFrom kind=Integer required=Value")
    }

    "keep the geo constraint/signal/sort traces prefixed distinctly, never collapsed into one generic geo operation" in {
      val geoDistanceFilterTrace = PlannedAlgebraTrace.constraint(PlannedConstraint.GeoDistanceFilter(geoFilterableLocation, origin, Distance(500)))
      val geoProximitySignalTrace = PlannedAlgebraTrace.signal(PlannedSignal.GeoProximitySignal(geoNotFilterableLocation, origin))
      val geoDistanceSortTrace = PlannedAlgebraTrace.sort(PlannedSort.GeoDistance(geoSortableLocation, origin, SortDirection.Asc))

      val prefixes = Vector(geoDistanceFilterTrace, geoProximitySignalTrace, geoDistanceSortTrace).map(_.takeWhile(_ != ' '))
      assert(prefixes == Vector("constraint.geo-distance-filter", "signal.geo-proximity", "sort.geo-distance"))
      assert(prefixes.distinct.size == prefixes.size)
    }

    "never leak function/object identity (no '@'-style default toString) into any rendered trace" in {
      val allTraces = Vector(
        PlannedAlgebraTrace.constraint(PlannedConstraint.Terms(equalAndInLabel, Set("banana", "apple"))),
        PlannedAlgebraTrace.constraint(PlannedConstraint.NumberRange(rangeCapableFrom, RangeBounds(Bound.Unbounded, Bound.Exclusive(100)))),
        PlannedAlgebraTrace.constraint(PlannedConstraint.IntervalOverlap(rangeCapableFrom, rangeCapableTo, bounds)),
        PlannedAlgebraTrace.constraint(PlannedConstraint.GeoDistanceFilter(geoFilterableLocation, origin, Distance(500))),
        PlannedAlgebraTrace.signal(PlannedSignal.GeoProximitySignal(geoNotFilterableLocation, origin)),
        PlannedAlgebraTrace.sort(PlannedSort.FieldValue(sortableOpenFrom, SortDirection.Asc)),
        PlannedAlgebraTrace.sort(PlannedSort.GeoDistance(geoSortableLocation, origin, SortDirection.Asc)),
        PlannedAlgebraTrace.error(
          PlanConstraintError.UnsupportedFilterOperator(noFilterCapableLabel.id, SearchFieldKind.Keyword, Vector(FilterOperator.Equal, FilterOperator.In))
        ),
        PlannedAlgebraTrace.error(
          PlanConstraintError.MismatchedIntervalFields(
            rangeCapableFrom.id,
            rangeCapableToAlternateCodec.id,
            SearchValueTypeId("int"),
            SearchValueTypeId("alternate-int"),
          )
        ),
        PlannedAlgebraTrace.error(PlanConstraintError.UnsupportedSortMode(notSortableOpenFrom.id, SearchFieldKind.Integer, SortMode.Value)),
      )

      assert(!allTraces.exists(_.contains("@")))
    }
  }
}
