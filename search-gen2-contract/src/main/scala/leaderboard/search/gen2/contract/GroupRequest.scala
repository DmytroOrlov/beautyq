package leaderboard.search.gen2.contract

enum GroupPrecisionPolicy {
  case RequireExact
  case AllowApproximate
}

/** What a group result carries for its representative document, beyond the group key itself. Never
  * inferred from document shape - a domain states its representative projection explicitly, the same
  * way it states field capabilities.
  */
sealed trait RepresentativeRequest[Document]

object RepresentativeRequest {
  final case class IdentityOnly[Document]() extends RepresentativeRequest[Document]

  final case class Fields[Document](
    fields: Vector[SearchField[Document, ?]]
  ) extends RepresentativeRequest[Document]
}

sealed trait GroupMetricRequest[Document] {
  def id: GroupMetricId
}

object GroupMetricRequest {
  final case class BestScore[Document](
    id: GroupMetricId
  ) extends GroupMetricRequest[Document]

  /** Explicit and typed, matching [[PlannedSignal.GeoProximitySignal]]'s own separation: a group never
    * grows an implicit distance metric merely because a geo field exists.
    */
  final case class MinGeoDistance[Document](
    id: GroupMetricId,
    field: SearchField[Document, GeoPoint],
    origin: GeoPoint,
  ) extends GroupMetricRequest[Document]
}

sealed trait GroupOrder

object GroupOrder {
  final case class Metric(
    metricId: GroupMetricId,
    direction: SortDirection,
  ) extends GroupOrder

  final case class MatchingDocumentCount(
    direction: SortDirection
  ) extends GroupOrder

  final case class Key(
    direction: SortDirection
  ) extends GroupOrder
}

/** A group request over one typed key field of one document, richer than a plain terms bucket - matches
  * docs/gen2/BEAUTYQ_SEARCH_GEN2_SEMANTICS_ADR.md #15. Construction is unchecked; call
  * [[GroupRequest.validate]] to check it against `keyField`'s declared capabilities and its own internal
  * shape.
  */
final case class GroupRequest[Document, Key](
  id: GroupId,
  keyField: SearchField[Document, Key],
  size: GroupSize,
  representative: RepresentativeRequest[Document],
  metrics: Vector[GroupMetricRequest[Document]],
  order: Vector[GroupOrder],
  precision: GroupPrecisionPolicy,
)

object GroupRequest {

  def validate[Document, Key](request: GroupRequest[Document, Key]): Either[NonEmptyErrors[GroupRequestError], GroupRequest[Document, Key]] =
    NonEmptyErrors.fromVector(violations(request)) match {
      case Some(errors) => Left(errors)
      case None         => Right(request)
    }

  private def violations[Document, Key](request: GroupRequest[Document, Key]): Vector[GroupRequestError] =
    requireGroupMode(request.id, request.keyField) ++
      representativeViolations(request.id, request.representative) ++
      metricViolations(request.id, request.metrics) ++
      orderViolations(request.id, request.metrics, request.order)

  private def requireGroupMode[Document, A](groupId: GroupId, field: SearchField[Document, A]): Vector[GroupRequestError] =
    if (field.capabilities.groupModes.contains(GroupMode.Terms)) Vector.empty
    else Vector(GroupRequestError.UnsupportedGroupMode(groupId, field.id, field.kind))

  private def representativeViolations[Document](groupId: GroupId, representative: RepresentativeRequest[Document]): Vector[GroupRequestError] =
    representative match {
      case RepresentativeRequest.IdentityOnly() =>
        Vector.empty

      case RepresentativeRequest.Fields(fields) =>
        if (fields.isEmpty) Vector(GroupRequestError.EmptyRepresentativeFields(groupId))
        else duplicateFieldViolations(groupId, fields)
    }

  private def duplicateFieldViolations[Document](groupId: GroupId, fields: Vector[SearchField[Document, ?]]): Vector[GroupRequestError] = {
    val (_, _, errors) =
      fields.foldLeft((Set.empty[FieldId], Set.empty[FieldId], Vector.empty[GroupRequestError])) { case ((seen, reported, errors), field) =>
        val duplicateError =
          if (seen.contains(field.id) && !reported.contains(field.id))
            Vector(GroupRequestError.DuplicateRepresentativeField(groupId, field.id))
          else
            Vector.empty

        (seen + field.id, if (duplicateError.nonEmpty) reported + field.id else reported, errors ++ duplicateError)
      }

    errors
  }

  private def metricViolations[Document](groupId: GroupId, metrics: Vector[GroupMetricRequest[Document]]): Vector[GroupRequestError] = {
    val (_, _, errors) =
      metrics.foldLeft((Set.empty[GroupMetricId], Set.empty[GroupMetricId], Vector.empty[GroupRequestError])) { case ((seen, reported, errors), metric) =>
        val duplicateError =
          if (seen.contains(metric.id) && !reported.contains(metric.id))
            Vector(GroupRequestError.DuplicateGroupMetricId(groupId, metric.id))
          else
            Vector.empty

        (seen + metric.id, if (duplicateError.nonEmpty) reported + metric.id else reported, errors ++ duplicateError)
      }

    errors
  }

  // Order: unknown metric references (scan order), then duplicate criteria (scan order), then the
  // single whole-vector final-clause check - never reordered by which check happens to fail.
  private def orderViolations[Document](
    groupId: GroupId,
    metrics: Vector[GroupMetricRequest[Document]],
    order: Vector[GroupOrder],
  ): Vector[GroupRequestError] = {
    val declaredMetricIds = metrics.map(_.id).toSet

    val unknownMetricErrors =
      order.collect {
        case GroupOrder.Metric(metricId, _) if !declaredMetricIds.contains(metricId) =>
          GroupRequestError.UnknownGroupOrderMetric(groupId, metricId)
      }

    val duplicateErrors = duplicateOrderViolations(groupId, order)

    val finalClauseError =
      order.lastOption match {
        case Some(GroupOrder.Key(SortDirection.Asc)) => Vector.empty
        case _                                       => Vector(GroupRequestError.MissingStableKeyTieBreaker(groupId))
      }

    unknownMetricErrors ++ duplicateErrors ++ finalClauseError
  }

  // A criterion's identity ignores direction (Metric(x, Asc) and Metric(x, Desc) are the same
  // criterion), so duplicate detection keys on this signature rather than GroupOrder equality.
  private type OrderCriterionSignature = (String, Option[GroupMetricId])

  private def orderCriterionSignature(order: GroupOrder): OrderCriterionSignature =
    order match {
      case GroupOrder.Metric(metricId, _)      => ("metric", Some(metricId))
      case GroupOrder.MatchingDocumentCount(_) => ("matching-document-count", None)
      case GroupOrder.Key(_)                   => ("key", None)
    }

  private def duplicateOrderViolations(groupId: GroupId, order: Vector[GroupOrder]): Vector[GroupRequestError] = {
    val (_, _, errors) =
      order.foldLeft((Set.empty[OrderCriterionSignature], Set.empty[OrderCriterionSignature], Vector.empty[GroupRequestError])) {
        case ((seen, reported, errors), criterion) =>
          val signature = orderCriterionSignature(criterion)
          val duplicateError =
            if (seen.contains(signature) && !reported.contains(signature))
              Vector(GroupRequestError.DuplicateGroupOrder(groupId, criterion))
            else
              Vector.empty

          (seen + signature, if (duplicateError.nonEmpty) reported + signature else reported, errors ++ duplicateError)
      }

    errors
  }
}

sealed trait GroupRequestError

object GroupRequestError {
  final case class UnsupportedGroupMode(
    groupId: GroupId,
    fieldId: FieldId,
    kind: SearchFieldKind,
  ) extends GroupRequestError

  final case class EmptyRepresentativeFields(
    groupId: GroupId
  ) extends GroupRequestError

  final case class DuplicateRepresentativeField(
    groupId: GroupId,
    fieldId: FieldId,
  ) extends GroupRequestError

  final case class DuplicateGroupMetricId(
    groupId: GroupId,
    metricId: GroupMetricId,
  ) extends GroupRequestError

  final case class UnknownGroupOrderMetric(
    groupId: GroupId,
    metricId: GroupMetricId,
  ) extends GroupRequestError

  final case class DuplicateGroupOrder(
    groupId: GroupId,
    order: GroupOrder,
  ) extends GroupRequestError

  final case class MissingStableKeyTieBreaker(
    groupId: GroupId
  ) extends GroupRequestError
}
