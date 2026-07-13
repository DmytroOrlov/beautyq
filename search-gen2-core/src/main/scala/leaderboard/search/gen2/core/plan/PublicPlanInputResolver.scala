package leaderboard.search.gen2.core.plan

import leaderboard.search.gen2.contract.*

/** Narrow adapter view exposing only what [[PublicPlanInputResolver]] needs from one domain's decoded
  * public filter wrapper: its public name (for error reporting), its already-typed clause, and its
  * trusted provenance. The resolver never inspects the wrapper type itself.
  */
trait PublicFilterPlanView[Filter, Document, Name] {
  def name(value: Filter): Name
  def clause(value: Filter): PublicFilterClause[Document]
  def provenance(value: Filter): ConstraintProvenance
}

/** Narrow adapter view exposing only what [[PublicPlanInputResolver]] needs from one domain's decoded
  * public sort wrapper: its public name (for error reporting) and its already-typed clause.
  */
trait PublicSortPlanView[Sort, Document, Name] {
  def name(value: Sort): Name
  def clause(value: Sort): PublicSortClause[Document]
}

final case class ResolvedPublicPlanInput[Document](
  constraints: Vector[SourcedConstraint[Document]],
  sort: Vector[PlannedSort[Document]],
)

sealed trait PublicPlanInputResolutionError[FilterName, SortName]

object PublicPlanInputResolutionError {
  final case class MissingLocationForFilter[FilterName, SortName](index: Int, name: FilterName) extends PublicPlanInputResolutionError[FilterName, SortName]
  final case class MissingLocationForSort[FilterName, SortName](index: Int, name: SortName) extends PublicPlanInputResolutionError[FilterName, SortName]
}

/** Resolves a domain's already-decoded public filters/sorts into [[SourcedConstraint]]/[[PlannedSort]]
  * values, given one optional request-supplied geo origin. This is the one reusable place a
  * [[PublicFilterClause.GeoRadius]]/[[PublicSortClause.GeoDistance]] clause turns into a concrete
  * [[PlannedConstraint.GeoDistanceFilter]]/[[PlannedSort.GeoDistance]] - a missing origin is a typed
  * error here, never an inferred radius, filter, sort, or signal.
  */
object PublicPlanInputResolver {

  def resolve[Filter, Sort, Document, FilterName, SortName](
    filters: Vector[Filter],
    sorts: Vector[Sort],
    origin: Option[GeoPoint],
    filterView: PublicFilterPlanView[Filter, Document, FilterName],
    sortView: PublicSortPlanView[Sort, Document, SortName],
  ): Either[NonEmptyErrors[PublicPlanInputResolutionError[FilterName, SortName]], ResolvedPublicPlanInput[Document]] = {

    val filterResults: Vector[Either[PublicPlanInputResolutionError[FilterName, SortName], SourcedConstraint[Document]]] =
      filters.zipWithIndex.map { case (filter, index) =>
        filterView.clause(filter) match {
          case PublicFilterClause.Constraint(value) =>
            Right(SourcedConstraint(value, filterView.provenance(filter)))
          case PublicFilterClause.GeoRadius(field, radius) =>
            origin match {
              case Some(point) => Right(SourcedConstraint(PlannedConstraint.GeoDistanceFilter(field, point, radius), filterView.provenance(filter)))
              case None        => Left(PublicPlanInputResolutionError.MissingLocationForFilter(index, filterView.name(filter)))
            }
        }
      }

    val sortResults: Vector[Either[PublicPlanInputResolutionError[FilterName, SortName], PlannedSort[Document]]] =
      sorts.zipWithIndex.map { case (sort, index) =>
        sortView.clause(sort) match {
          case PublicSortClause.Planned(value) => Right(value)
          case PublicSortClause.GeoDistance(field, direction) =>
            origin match {
              case Some(point) => Right(PlannedSort.GeoDistance(field, point, direction))
              case None        => Left(PublicPlanInputResolutionError.MissingLocationForSort(index, sortView.name(sort)))
            }
        }
      }

    // Fixed order: missing filter locations in request filter order, then missing sort locations in
    // request sort order - never merged/interleaved by index across the two vectors.
    val allErrors = filterResults.collect { case Left(error) => error } ++ sortResults.collect { case Left(error) => error }

    NonEmptyErrors.fromVector(allErrors) match {
      case Some(errors) => Left(errors)
      case None =>
        Right(
          ResolvedPublicPlanInput(
            constraints = filterResults.collect { case Right(value) => value },
            sort = sortResults.collect { case Right(value) => value },
          )
        )
    }
  }
}
