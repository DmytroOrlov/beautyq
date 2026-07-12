package leaderboard.search.gen2.contract

/** Deterministic, human-readable diagnostic view of one [[SearchPlan]] or its parts, in the same spirit
  * as [[PlannedAlgebraTrace]]: a reviewer-readable rendering of an executable value, never a second
  * hand-maintained representation of it. Brick 4A values are rendered by delegating directly to
  * [[PlannedAlgebraTrace]] rather than re-deriving their format here.
  *
  * This trace is diagnostic only. It is not PlanIdentity, cursor input, backend JSON or a canonical wire
  * format. Brick 4C's `PlanIdentity`/cursor encoding must derive its own canonical representation
  * directly from typed plan values (field IDs, canonical codec values, stable case identity) and must
  * not depend on, parse, or embed these trace strings.
  */
object SearchPlanTrace {

  def page(value: PageRequest): String =
    s"page cursor=${renderCursorPresence(value.cursor)} size=${value.size.value}"

  def facet[Document](value: FacetRequest[Document]): String =
    value match {
      case terms: FacetRequest.Terms[Document, ?] =>
        s"facet.terms id=${terms.id.value} field=${renderFieldRef(terms.field.id, terms.field.codec.typeId)} size=${terms.size.value} order=${renderTermsFacetOrder(terms.order)} counting=${renderCountingPolicy(terms.countingPolicy)}"

      case numberRange: FacetRequest.NumberRange[Document, ?] =>
        s"facet.number-range id=${numberRange.id.value} field=${renderFieldRef(numberRange.field.id, numberRange.field.codec.typeId)} counting=${renderCountingPolicy(numberRange.countingPolicy)} buckets=${renderBuckets(numberRange.field, numberRange.buckets)}"

      case intervalOverlap: FacetRequest.IntervalOverlap[Document, ?] =>
        s"facet.interval-overlap id=${intervalOverlap.id.value} from=${renderFieldRef(intervalOverlap.from.id, intervalOverlap.from.codec.typeId)} to=${renderFieldRef(intervalOverlap.to.id, intervalOverlap.to.codec.typeId)} counting=${renderCountingPolicy(intervalOverlap.countingPolicy)} buckets=${renderBuckets(intervalOverlap.from, intervalOverlap.buckets)}"
    }

  def group[Document](value: GroupRequest[Document, ?]): String =
    s"group id=${value.id.value} key=${renderFieldRef(value.keyField.id, value.keyField.codec.typeId)} size=${value.size.value} representative=${renderRepresentative(value.representative)} metrics=${renderMetrics(value.metrics)} order=${renderOrder(value.order)} precision=${renderPrecision(value.precision)}"

  def provenance(value: ConstraintProvenance): String =
    value match {
      case ConstraintProvenance.ExplicitUi         => "ExplicitUi"
      case ConstraintProvenance.FacetSelection(id) => s"FacetSelection(${id.value})"
      case ConstraintProvenance.ParsedHard         => "ParsedHard"
      case ConstraintProvenance.ParsedSoft         => "ParsedSoft"
      case ConstraintProvenance.SystemDefault      => "SystemDefault"
    }

  def error(value: SearchPlanError): String =
    value match {
      case SearchPlanError.InvalidConstraint(index, error) => s"plan-error.invalid-constraint index=$index ${PlannedAlgebraTrace.error(error)}"
      case SearchPlanError.InvalidSort(index, error)        => s"plan-error.invalid-sort index=$index ${PlannedAlgebraTrace.error(error)}"
      case SearchPlanError.InvalidFacet(index, error)       => s"plan-error.invalid-facet index=$index ${renderFacetRequestError(error)}"
      case SearchPlanError.InvalidGroup(index, error)       => s"plan-error.invalid-group index=$index ${renderGroupRequestError(error)}"
      case SearchPlanError.DuplicateFacetId(id)             => s"plan-error.duplicate-facet-id id=${id.value}"
      case SearchPlanError.DuplicateGroupId(id)              => s"plan-error.duplicate-group-id id=${id.value}"
    }

  /** Renders every plan section in `SearchPlan`'s own field order, preserving each vector's declaration
    * order. Never renders [[SearchCursor.opaqueValue]] - only cursor presence/absence.
    */
  def render[Document](plan: SearchPlan[Document]): String = {
    val residualTextLine = Vector(s"plan.residual-text=${renderResidualText(plan.residualText)}")

    val appliedFilterLines =
      plan.appliedFilters.zipWithIndex.map { case (appliedFilter, index) =>
        s"plan.applied-filter[$index] provenance=${provenance(appliedFilter.source.provenance)} ${PlannedAlgebraTrace.constraint(appliedFilter.source.constraint)}"
      }

    val softSignalLines =
      plan.softSignals.zipWithIndex.map { case (signal, index) =>
        s"plan.soft-signal[$index] ${PlannedAlgebraTrace.signal(signal)}"
      }

    val sortLines =
      plan.sort.zipWithIndex.map { case (sort, index) =>
        s"plan.sort[$index] ${PlannedAlgebraTrace.sort(sort)}"
      }

    val pageLine = Vector(s"plan.${page(plan.page)}")

    val facetLines =
      plan.facets.zipWithIndex.map { case (facetRequest, index) =>
        s"plan.facet[$index] ${facet(facetRequest)}"
      }

    val groupLines =
      plan.groups.zipWithIndex.map { case (groupRequest, index) =>
        s"plan.group[$index] ${group(groupRequest)}"
      }

    val suppressedFilterLines =
      plan.diagnostics.suppressedFilters.zipWithIndex.map { case (suppressedFilter, index) =>
        s"plan.diagnostics.suppressed-filter[$index] provenance=${provenance(suppressedFilter.source.provenance)} ${PlannedAlgebraTrace.constraint(suppressedFilter.source.constraint)} reason=${renderSuppressionReason(suppressedFilter.reason)}"
      }

    val noticeLines =
      plan.diagnostics.notices.zipWithIndex.map { case (notice, index) =>
        s"plan.diagnostics.notice[$index] code=${notice.code.value} detail=${renderDetail(notice.detail)}"
      }

    (residualTextLine ++ appliedFilterLines ++ softSignalLines ++ sortLines ++ pageLine ++ facetLines ++ groupLines ++ suppressedFilterLines ++ noticeLines)
      .mkString("\n")
  }

  // ---- shared rendering helpers: only stable declaration metadata (FieldId, SearchValueTypeId) and
  // codec-encoded values, own small copies of PlannedAlgebraTrace's private idiom rather than exposing
  // its private helpers across files ----

  private def renderFieldRef(id: FieldId, typeId: SearchValueTypeId): String = s"${id.value}:${typeId.value}"

  private def renderCursorPresence(cursor: Option[SearchCursor]): String =
    cursor match {
      case Some(_) => "present"
      case None    => "absent"
    }

  private def renderResidualText(text: Option[String]): String =
    text match {
      case Some(value) => "\"" + escapeDiagnosticText(value) + "\""
      case None        => "absent"
    }

  private def renderDetail(detail: Option[String]): String =
    detail match {
      case Some(value) => "\"" + escapeDiagnosticText(value) + "\""
      case None         => "absent"
    }

  private def escapeDiagnosticText(text: String): String =
    text
      .replace("\\", "\\\\")
      .replace("\n", "\\n")
      .replace("\r", "\\r")
      .replace("\t", "\\t")

  private def renderBuckets[Document, A](field: SearchField[Document, A], buckets: Vector[FacetBucket[A]]): String =
    buckets.map(bucket => s"${bucket.id.value}=${renderRangeBounds(field, bucket.bounds)}").mkString("[", ", ", "]")

  private def renderRangeBounds[Document, A](field: SearchField[Document, A], bounds: RangeBounds[A]): String =
    s"[${renderBound(field, bounds.lower)}, ${renderBound(field, bounds.upper)}]"

  private def renderBound[Document, A](field: SearchField[Document, A], bound: Bound[A]): String =
    bound match {
      case Bound.Unbounded        => "unbounded"
      case Bound.Inclusive(value) => s"inclusive(${field.codec.encodeCanonical(value)})"
      case Bound.Exclusive(value) => s"exclusive(${field.codec.encodeCanonical(value)})"
    }

  private def renderTermsFacetOrder(order: TermsFacetOrder): String = order.toString

  private def renderCountingPolicy(policy: FacetCountingPolicy): String = policy.toString

  private def renderPrecision(policy: GroupPrecisionPolicy): String = policy.toString

  private def renderSuppressionReason(reason: SuppressionReason): String = reason.toString

  private def renderDirection(direction: SortDirection): String = direction.toString

  private def renderKind(kind: SearchFieldKind): String = kind.toString

  private def renderFacetMode(mode: FacetMode): String = mode.toString

  private def renderRepresentative[Document](representative: RepresentativeRequest[Document]): String =
    representative match {
      case RepresentativeRequest.IdentityOnly() =>
        "identity-only"
      case RepresentativeRequest.Fields(fields) =>
        fields.map(field => renderFieldRef(field.id, field.codec.typeId)).mkString("fields[", ", ", "]")
    }

  private def renderMetrics[Document](metrics: Vector[GroupMetricRequest[Document]]): String =
    metrics.map(renderMetric).mkString("[", ", ", "]")

  private def renderMetric[Document](metric: GroupMetricRequest[Document]): String =
    metric match {
      case GroupMetricRequest.BestScore(id) =>
        s"best-score(${id.value})"
      case GroupMetricRequest.MinGeoDistance(id, field, origin) =>
        s"min-geo-distance(${id.value} field=${renderFieldRef(field.id, field.codec.typeId)} origin=${SearchValueCodec.geoPoint.encodeCanonical(origin)})"
    }

  private def renderOrder(order: Vector[GroupOrder]): String =
    order.map(renderOrderCriterion).mkString("[", ", ", "]")

  private def renderOrderCriterion(criterion: GroupOrder): String =
    criterion match {
      case GroupOrder.Metric(metricId, direction)      => s"metric(${metricId.value} ${renderDirection(direction)})"
      case GroupOrder.MatchingDocumentCount(direction) => s"matching-document-count(${renderDirection(direction)})"
      case GroupOrder.Key(direction)                   => s"key(${renderDirection(direction)})"
    }

  private def renderFacetRequestError(error: FacetRequestError): String =
    error match {
      case FacetRequestError.UnsupportedFacetMode(facetId, fieldId, kind, required) =>
        s"error.unsupported-facet-mode facet=${facetId.value} field=${fieldId.value} kind=${renderKind(kind)} required=${renderFacetMode(required)}"

      case FacetRequestError.EmptyFacetBuckets(facetId) =>
        s"error.empty-facet-buckets facet=${facetId.value}"

      case FacetRequestError.DuplicateFacetBucketId(facetId, bucketId) =>
        s"error.duplicate-facet-bucket-id facet=${facetId.value} bucket=${bucketId.value}"

      case FacetRequestError.UpperUnboundedBucketMustBeLast(facetId, bucketId) =>
        s"error.upper-unbounded-bucket-must-be-last facet=${facetId.value} bucket=${bucketId.value}"

      case FacetRequestError.MismatchedIntervalFacetFields(facetId, fromFieldId, toFieldId, fromTypeId, toTypeId) =>
        s"error.mismatched-interval-facet-fields facet=${facetId.value} from=${renderFieldRef(fromFieldId, fromTypeId)} to=${renderFieldRef(toFieldId, toTypeId)}"
    }

  private def renderGroupRequestError(error: GroupRequestError): String =
    error match {
      case GroupRequestError.UnsupportedGroupMode(groupId, fieldId, kind) =>
        s"error.unsupported-group-mode group=${groupId.value} field=${fieldId.value} kind=${renderKind(kind)}"

      case GroupRequestError.EmptyRepresentativeFields(groupId) =>
        s"error.empty-representative-fields group=${groupId.value}"

      case GroupRequestError.DuplicateRepresentativeField(groupId, fieldId) =>
        s"error.duplicate-representative-field group=${groupId.value} field=${fieldId.value}"

      case GroupRequestError.DuplicateGroupMetricId(groupId, metricId) =>
        s"error.duplicate-group-metric-id group=${groupId.value} metric=${metricId.value}"

      case GroupRequestError.UnknownGroupOrderMetric(groupId, metricId) =>
        s"error.unknown-group-order-metric group=${groupId.value} metric=${metricId.value}"

      case GroupRequestError.DuplicateGroupOrder(groupId, order) =>
        s"error.duplicate-group-order group=${groupId.value} criterion=${renderOrderCriterion(order)}"

      case GroupRequestError.MissingStableKeyTieBreaker(groupId) =>
        s"error.missing-stable-key-tie-breaker group=${groupId.value}"
    }
}
