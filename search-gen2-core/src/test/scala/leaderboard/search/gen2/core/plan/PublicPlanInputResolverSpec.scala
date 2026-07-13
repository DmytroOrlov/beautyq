package leaderboard.search.gen2.core.plan

import leaderboard.search.gen2.contract.*
import org.scalatest.wordspec.AnyWordSpec

import java.util.UUID

/** Neutral trail/venue fixture (trailType/difficulty/location) proving public plan-input resolution -
  * including geo-clause resolution against an optional origin - is independent of any one domain's
  * document shape, wrapper types, or field vocabulary.
  */
final class PublicPlanInputResolverSpec extends AnyWordSpec {

  private final case class TrailDocument(id: UUID, difficulty: Int, location: GeoPoint)

  private val difficultyField = field[TrailDocument, Int]("difficulty", _.difficulty).integer.filterable(FilterOperator.Range).sortable(SortMode.Value)
  private val locationField = field[TrailDocument, GeoPoint]("location", _.location).geoPoint.filterable(FilterOperator.GeoDistance).sortable(SortMode.Distance)

  // Domain wrapper types unrelated to any registry/decode mechanics - the resolver only ever sees these
  // through the adapter views below.
  private final case class TrailFilterWrapper(publicName: PublicFieldName, clause: PublicFilterClause[TrailDocument], provenance: ConstraintProvenance)
  private final case class TrailSortWrapper(publicName: PublicSortName, clause: PublicSortClause[TrailDocument])

  private object TrailFilterView extends PublicFilterPlanView[TrailFilterWrapper, TrailDocument, PublicFieldName] {
    def name(value: TrailFilterWrapper): PublicFieldName = value.publicName
    def clause(value: TrailFilterWrapper): PublicFilterClause[TrailDocument] = value.clause
    def provenance(value: TrailFilterWrapper): ConstraintProvenance = value.provenance
  }

  private object TrailSortView extends PublicSortPlanView[TrailSortWrapper, TrailDocument, PublicSortName] {
    def name(value: TrailSortWrapper): PublicSortName = value.publicName
    def clause(value: TrailSortWrapper): PublicSortClause[TrailDocument] = value.clause
  }

  private val origin = GeoPoint(BigDecimal("47.0"), BigDecimal("11.0"))
  private val radius = Distance(5000)

  private def plannedFilter(value: Int, provenance: ConstraintProvenance): TrailFilterWrapper =
    TrailFilterWrapper(PublicFieldName("difficulty"), PublicFilterClause.Constraint(PlannedConstraint.Terms(difficultyField, Set(value))), provenance)

  private def geoFilter(provenance: ConstraintProvenance): TrailFilterWrapper =
    TrailFilterWrapper(PublicFieldName("distanceMeters"), PublicFilterClause.GeoRadius(locationField, radius), provenance)

  private def plannedSort(direction: SortDirection): TrailSortWrapper =
    TrailSortWrapper(PublicSortName("difficulty"), PublicSortClause.Planned(PlannedSort.FieldValue(difficultyField, direction)))

  private def geoSort(direction: SortDirection): TrailSortWrapper =
    TrailSortWrapper(PublicSortName("distanceMeters"), PublicSortClause.GeoDistance(locationField, direction))

  "PublicPlanInputResolver.resolve" should {
    "preserve an already-planned filter unchanged, with its provenance" in {
      val filter = plannedFilter(3, ConstraintProvenance.ExplicitUi)
      PublicPlanInputResolver.resolve(Vector(filter), Vector.empty, None, TrailFilterView, TrailSortView) match {
        case Right(resolved) =>
          assert(resolved.constraints == Vector(SourcedConstraint(PlannedConstraint.Terms(difficultyField, Set(3)), ConstraintProvenance.ExplicitUi)))
          assert(resolved.sort.isEmpty)
        case Left(errors) => fail(s"expected success, got ${errors.toVector}")
      }
    }

    "resolve a geo-radius filter into a GeoDistanceFilter using the supplied origin" in {
      val filter = geoFilter(ConstraintProvenance.ExplicitUi)
      PublicPlanInputResolver.resolve(Vector(filter), Vector.empty, Some(origin), TrailFilterView, TrailSortView) match {
        case Right(resolved) =>
          assert(resolved.constraints == Vector(SourcedConstraint(PlannedConstraint.GeoDistanceFilter(locationField, origin, radius), ConstraintProvenance.ExplicitUi)))
        case Left(errors) => fail(s"expected success, got ${errors.toVector}")
      }
    }

    "preserve an already-planned sort unchanged" in {
      val sort = plannedSort(SortDirection.Asc)
      PublicPlanInputResolver.resolve(Vector.empty, Vector(sort), None, TrailFilterView, TrailSortView) match {
        case Right(resolved) => assert(resolved.sort == Vector(PlannedSort.FieldValue(difficultyField, SortDirection.Asc)))
        case Left(errors)    => fail(s"expected success, got ${errors.toVector}")
      }
    }

    "resolve a geo-distance sort using the supplied origin" in {
      val sort = geoSort(SortDirection.Desc)
      PublicPlanInputResolver.resolve(Vector.empty, Vector(sort), Some(origin), TrailFilterView, TrailSortView) match {
        case Right(resolved) => assert(resolved.sort == Vector(PlannedSort.GeoDistance(locationField, origin, SortDirection.Desc)))
        case Left(errors)    => fail(s"expected success, got ${errors.toVector}")
      }
    }

    "accumulate missing filter-origin errors by filter index, before any missing sort-origin errors" in {
      val filters = Vector(plannedFilter(1, ConstraintProvenance.ExplicitUi), geoFilter(ConstraintProvenance.ExplicitUi), geoFilter(ConstraintProvenance.ExplicitUi))
      val sorts = Vector(geoSort(SortDirection.Asc))
      PublicPlanInputResolver.resolve(filters, sorts, None, TrailFilterView, TrailSortView) match {
        case Left(errors) =>
          assert(
            errors.toVector ==
              Vector(
                PublicPlanInputResolutionError.MissingLocationForFilter(1, PublicFieldName("distanceMeters")),
                PublicPlanInputResolutionError.MissingLocationForFilter(2, PublicFieldName("distanceMeters")),
                PublicPlanInputResolutionError.MissingLocationForSort(0, PublicSortName("distanceMeters")),
              )
          )
        case Right(value) => fail(s"expected missing-location errors, got $value")
      }
    }

    "add nothing - no filter, sort, or signal - when coordinates alone are present with no geo clause requested" in {
      val filter = plannedFilter(2, ConstraintProvenance.ExplicitUi)
      PublicPlanInputResolver.resolve(Vector(filter), Vector.empty, Some(origin), TrailFilterView, TrailSortView) match {
        case Right(resolved) =>
          assert(resolved.constraints == Vector(SourcedConstraint(PlannedConstraint.Terms(difficultyField, Set(2)), ConstraintProvenance.ExplicitUi)))
          assert(resolved.sort.isEmpty)
        case Left(errors) => fail(s"expected success, got ${errors.toVector}")
      }
    }

    "preserve filter provenance across a mix of planned and geo-resolved filters" in {
      val planned = plannedFilter(4, ConstraintProvenance.FacetSelection(FacetSelectionId("sel-9")))
      val geo = geoFilter(ConstraintProvenance.ParsedHard)
      PublicPlanInputResolver.resolve(Vector(planned, geo), Vector.empty, Some(origin), TrailFilterView, TrailSortView) match {
        case Right(resolved) =>
          assert(resolved.constraints.map(_.provenance) == Vector(ConstraintProvenance.FacetSelection(FacetSelectionId("sel-9")), ConstraintProvenance.ParsedHard))
        case Left(errors) => fail(s"expected success, got ${errors.toVector}")
      }
    }

    "preserve input order across filters and, independently, across sorts" in {
      val filters = Vector(plannedFilter(1, ConstraintProvenance.ExplicitUi), geoFilter(ConstraintProvenance.ExplicitUi))
      val sorts = Vector(geoSort(SortDirection.Asc), plannedSort(SortDirection.Desc))
      PublicPlanInputResolver.resolve(filters, sorts, Some(origin), TrailFilterView, TrailSortView) match {
        case Right(resolved) =>
          assert(resolved.constraints.map(_.constraint) == Vector(PlannedConstraint.Terms(difficultyField, Set(1)), PlannedConstraint.GeoDistanceFilter(locationField, origin, radius)))
          assert(resolved.sort == Vector(PlannedSort.GeoDistance(locationField, origin, SortDirection.Asc), PlannedSort.FieldValue(difficultyField, SortDirection.Desc)))
        case Left(errors) => fail(s"expected success, got ${errors.toVector}")
      }
    }

    "work through adapter views over a plain, domain-neutral wrapper type unrelated to any registry mechanics" in {
      // TrailFilterWrapper/TrailSortWrapper above are ordinary case classes with no PublicInputRegistry/
      // PublicSortRegistry ancestry at all - the resolver needs only the adapter view, never a registry.
      val filter = plannedFilter(5, ConstraintProvenance.ExplicitUi)
      val sort = plannedSort(SortDirection.Asc)
      assert(PublicPlanInputResolver.resolve(Vector(filter), Vector(sort), None, TrailFilterView, TrailSortView).isRight)
    }
  }
}
