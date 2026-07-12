package leaderboard.search.gen2.core.plan

import leaderboard.search.gen2.contract.*

/** Readable generated review view of a PlanIdentity. This trace is not the canonical encoding, cursor
  * transport format, backend JSON or a substitute for PlanIdentityCanonicalEncoding.
  */
object PlanIdentityTrace {
  def render(identity: PlanIdentity): String = {
    val lines =
      Vector(
        s"contract-fingerprint=${quoted(identity.contractFingerprint.value)}",
        queryLine(identity.normalizedQuery),
        s"page-size=${identity.pageSize}",
      ) ++
        identity.hardConstraints.zipWithIndex.map { case (value, index) => s"constraint[$index]=${constraint(value)}" } ++
        identity.softSignals.zipWithIndex.map { case (value, index) => s"signal[$index]=${signal(value)}" } ++
        identity.sort.zipWithIndex.map { case (value, index) => s"sort[$index]=${sort(value)}" } ++
        identity.facets.zipWithIndex.map { case (value, index) => s"facet[$index]=${facet(value)}" } ++
        identity.groups.zipWithIndex.map { case (value, index) => s"group[$index]=${group(value)}" }

    lines.mkString("\n")
  }

  private def queryLine(value: Option[NormalizedQueryText]): String =
    value match {
      case None        => "query=absent"
      case Some(query) => s"query=${quoted(query.value)}"
    }

  private def constraint(value: CanonicalConstraint): String =
    value match {
      case CanonicalConstraint.Terms(fieldId, values) => s"terms field=${fieldId.value} values=${values.map(quoted).mkString("[", ",", "]")}"
      case CanonicalConstraint.NumberRange(fieldId, bounds) => s"number-range field=${fieldId.value} bounds=${renderBounds(bounds)}"
      case CanonicalConstraint.IntervalOverlap(fromFieldId, toFieldId, bounds) => s"interval-overlap from=${fromFieldId.value} to=${toFieldId.value} bounds=${renderBounds(bounds)}"
      case CanonicalConstraint.GeoDistanceFilter(fieldId, origin, radius) => s"geo-distance-filter field=${fieldId.value} origin=${quoted(origin.value)} radius=${quoted(radius.meters)}"
    }

  private def signal(value: CanonicalSignal): String =
    value match {
      case CanonicalSignal.GeoProximity(fieldId, origin) => s"geo-proximity field=${fieldId.value} origin=${quoted(origin.value)}"
    }

  private def sort(value: CanonicalSort): String =
    value match {
      case CanonicalSort.FieldValue(fieldId, direction) => s"field-value field=${fieldId.value} direction=${directionLabel(direction)}"
      case CanonicalSort.GeoDistance(fieldId, origin, direction) => s"geo-distance field=${fieldId.value} origin=${quoted(origin.value)} direction=${directionLabel(direction)}"
    }

  private def facet(value: CanonicalFacetRequest): String =
    value match {
      case CanonicalFacetRequest.Terms(id, fieldId, size, order, countingPolicy) =>
        s"terms id=${id.value} field=${fieldId.value} size=$size order=${termsOrderLabel(order)} counting=${countingPolicyLabel(countingPolicy)}"
      case CanonicalFacetRequest.NumberRange(id, fieldId, buckets, countingPolicy) =>
        s"number-range id=${id.value} field=${fieldId.value} buckets=${buckets.map(renderBucket).mkString("[", ",", "]")} counting=${countingPolicyLabel(countingPolicy)}"
      case CanonicalFacetRequest.IntervalOverlap(id, fromFieldId, toFieldId, buckets, countingPolicy) =>
        s"interval-overlap id=${id.value} from=${fromFieldId.value} to=${toFieldId.value} buckets=${buckets.map(renderBucket).mkString("[", ",", "]")} counting=${countingPolicyLabel(countingPolicy)}"
    }

  private def renderBucket(value: CanonicalFacetBucket): String =
    value match {
      case CanonicalFacetBucket.HalfOpen(id, min, max) => s"half-open(${id.value},${quoted(min)},${quoted(max)})"
      case CanonicalFacetBucket.UpperUnbounded(id, min) => s"upper-unbounded(${id.value},${quoted(min)})"
    }

  private def group(value: CanonicalGroupRequest): String =
    s"id=${value.id.value} key=${value.keyFieldId.value} size=${value.size} representative=${representative(value.representative)} metrics=${value.metrics.map(metric).mkString("[", ",", "]")} order=${value.order.map(order).mkString("[", ",", "]")} precision=${precisionLabel(value.precision)}"

  private def representative(value: CanonicalRepresentativeRequest): String =
    value match {
      case CanonicalRepresentativeRequest.IdentityOnly => "identity-only"
      case CanonicalRepresentativeRequest.Fields(fieldIds) => s"fields=${fieldIds.map(_.value).mkString("[", ",", "]")}"
    }

  private def metric(value: CanonicalGroupMetric): String =
    value match {
      case CanonicalGroupMetric.BestScore(id) => s"best-score(${id.value})"
      case CanonicalGroupMetric.MinGeoDistance(id, fieldId, origin) => s"min-geo-distance(${id.value},${fieldId.value},${quoted(origin.value)})"
    }

  private def order(value: CanonicalGroupOrder): String =
    value match {
      case CanonicalGroupOrder.Metric(metricId, direction) => s"metric(${metricId.value},${directionLabel(direction)})"
      case CanonicalGroupOrder.MatchingDocumentCount(direction) => s"matching-document-count(${directionLabel(direction)})"
      case CanonicalGroupOrder.Key(direction) => s"key(${directionLabel(direction)})"
    }

  private def renderBounds(value: CanonicalRangeBounds): String =
    s"[${renderBound(value.lower)},${renderBound(value.upper)}]"

  private def renderBound(value: CanonicalBound): String =
    value match {
      case CanonicalBound.Unbounded        => "unbounded"
      case CanonicalBound.Inclusive(item) => s"inclusive(${quoted(item)})"
      case CanonicalBound.Exclusive(item) => s"exclusive(${quoted(item)})"
    }

  private def quoted(value: String): String =
    "\"" + value
      .replace("\\", "\\\\")
      .replace("\n", "\\n")
      .replace("\r", "\\r")
      .replace("\t", "\\t")
      .replace("\"", "\\\"") + "\""

  private def directionLabel(value: SortDirection): String =
    value match {
      case SortDirection.Asc  => "asc"
      case SortDirection.Desc => "desc"
    }

  private def termsOrderLabel(value: TermsFacetOrder): String =
    value match {
      case TermsFacetOrder.CountDescThenKeyAsc => "count-desc-then-key-asc"
      case TermsFacetOrder.KeyAsc              => "key-asc"
    }

  private def countingPolicyLabel(value: FacetCountingPolicy): String =
    value match {
      case FacetCountingPolicy.AllAppliedHardFilters => "all-applied-hard-filters"
    }

  private def precisionLabel(value: GroupPrecisionPolicy): String =
    value match {
      case GroupPrecisionPolicy.RequireExact     => "require-exact"
      case GroupPrecisionPolicy.AllowApproximate => "allow-approximate"
    }
}
