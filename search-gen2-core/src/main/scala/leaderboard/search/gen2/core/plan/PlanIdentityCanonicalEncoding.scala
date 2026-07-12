package leaderboard.search.gen2.core.plan

import leaderboard.search.gen2.contract.*
import leaderboard.search.gen2.core.materialization.CanonicalFingerprint

/** The versioned, value-only wire projection for PlanIdentity. It is intentionally separate from the
  * readable trace: changing trace wording must never change identity bytes, and the encoder never
  * stores SearchField, codec, extractor, provenance or diagnostic values.
  */
object PlanIdentityCanonicalEncoding {
  val EncodingVersion: String = "search-plan-identity-v1"

  def tokens(identity: PlanIdentity): Vector[String] = {
    val sections =
      Vector(
        token("identity.version", EncodingVersion),
        token("contract.fingerprint", identity.contractFingerprint.value),
        queryTokens(identity.normalizedQuery),
        constraintsTokens(identity.hardConstraints),
        signalsTokens(identity.softSignals),
        sortsTokens(identity.sort),
        facetsTokens(identity.facets),
        groupsTokens(identity.groups),
        token("page.size", identity.pageSize.toString),
      )

    sections.flatten
  }

  def block(identity: PlanIdentity): String = CanonicalFingerprint.block(tokens(identity))

  private def token(tag: String, value: String): Vector[String] = Vector(CanonicalFingerprint.token(tag, value))

  private def queryTokens(query: Option[NormalizedQueryText]): Vector[String] =
    query match {
      case None => token("query.present", "false")
      case Some(value) => token("query.present", "true") ++ token("query.value", value.value)
    }

  private def constraintsTokens(values: Vector[CanonicalConstraint]): Vector[String] =
    token("constraint.count", values.size.toString) ++ values.zipWithIndex.flatMap { case (value, index) => constraintTokens(index, value) }

  private def constraintTokens(index: Int, value: CanonicalConstraint): Vector[String] = {
    val prefix = s"constraint[$index]"
    value match {
      case CanonicalConstraint.Terms(fieldId, values) =>
        token(s"$prefix.kind", "terms") ++
          token(s"$prefix.fieldId", fieldId.value) ++
          token(s"$prefix.value.count", values.size.toString) ++
          values.zipWithIndex.flatMap { case (item, valueIndex) => token(s"$prefix.value[$valueIndex]", item) }
      case CanonicalConstraint.NumberRange(fieldId, bounds) =>
        token(s"$prefix.kind", "number-range") ++ token(s"$prefix.fieldId", fieldId.value) ++ boundTokens(s"$prefix.lower", bounds.lower) ++ boundTokens(s"$prefix.upper", bounds.upper)
      case CanonicalConstraint.IntervalOverlap(fromFieldId, toFieldId, bounds) =>
        token(s"$prefix.kind", "interval-overlap") ++ token(s"$prefix.fromFieldId", fromFieldId.value) ++ token(s"$prefix.toFieldId", toFieldId.value) ++ boundTokens(s"$prefix.lower", bounds.lower) ++ boundTokens(s"$prefix.upper", bounds.upper)
      case CanonicalConstraint.GeoDistanceFilter(fieldId, origin, radius) =>
        token(s"$prefix.kind", "geo-distance-filter") ++ token(s"$prefix.fieldId", fieldId.value) ++ token(s"$prefix.origin", origin.value) ++ token(s"$prefix.radius", radius.meters)
    }
  }

  private def boundTokens(prefix: String, value: CanonicalBound): Vector[String] =
    value match {
      case CanonicalBound.Unbounded => token(s"$prefix.kind", "unbounded")
      case CanonicalBound.Inclusive(item) => token(s"$prefix.kind", "inclusive") ++ token(s"$prefix.value", item)
      case CanonicalBound.Exclusive(item) => token(s"$prefix.kind", "exclusive") ++ token(s"$prefix.value", item)
    }

  private def signalsTokens(values: Vector[CanonicalSignal]): Vector[String] =
    token("signal.count", values.size.toString) ++ values.zipWithIndex.flatMap { case (value, index) =>
      value match {
        case CanonicalSignal.GeoProximity(fieldId, origin) =>
          val prefix = s"signal[$index]"
          token(s"$prefix.kind", "geo-proximity") ++ token(s"$prefix.fieldId", fieldId.value) ++ token(s"$prefix.origin", origin.value)
      }
    }

  private def sortsTokens(values: Vector[CanonicalSort]): Vector[String] =
    token("sort.count", values.size.toString) ++ values.zipWithIndex.flatMap { case (value, index) =>
      val prefix = s"sort[$index]"
      value match {
        case CanonicalSort.FieldValue(fieldId, direction) =>
          token(s"$prefix.kind", "field-value") ++ token(s"$prefix.fieldId", fieldId.value) ++ token(s"$prefix.direction", directionLabel(direction))
        case CanonicalSort.GeoDistance(fieldId, origin, direction) =>
          token(s"$prefix.kind", "geo-distance") ++ token(s"$prefix.fieldId", fieldId.value) ++ token(s"$prefix.origin", origin.value) ++ token(s"$prefix.direction", directionLabel(direction))
      }
    }

  private def facetsTokens(values: Vector[CanonicalFacetRequest]): Vector[String] =
    token("facet.count", values.size.toString) ++ values.zipWithIndex.flatMap { case (value, index) => facetTokens(index, value) }

  private def facetTokens(index: Int, value: CanonicalFacetRequest): Vector[String] = {
    val prefix = s"facet[$index]"
    value match {
      case CanonicalFacetRequest.Terms(id, fieldId, size, order, countingPolicy) =>
        token(s"$prefix.kind", "terms") ++ token(s"$prefix.id", id.value) ++ token(s"$prefix.fieldId", fieldId.value) ++ token(s"$prefix.size", size.toString) ++ token(s"$prefix.order", termsOrderLabel(order)) ++ token(s"$prefix.countingPolicy", countingPolicyLabel(countingPolicy))
      case CanonicalFacetRequest.NumberRange(id, fieldId, buckets, countingPolicy) =>
        token(s"$prefix.kind", "number-range") ++ token(s"$prefix.id", id.value) ++ token(s"$prefix.fieldId", fieldId.value) ++ token(s"$prefix.bucket.count", buckets.size.toString) ++ buckets.zipWithIndex.flatMap { case (bucket, bucketIndex) => bucketTokens(s"$prefix.bucket[$bucketIndex]", bucket) } ++ token(s"$prefix.countingPolicy", countingPolicyLabel(countingPolicy))
      case CanonicalFacetRequest.IntervalOverlap(id, fromFieldId, toFieldId, buckets, countingPolicy) =>
        token(s"$prefix.kind", "interval-overlap") ++ token(s"$prefix.id", id.value) ++ token(s"$prefix.fromFieldId", fromFieldId.value) ++ token(s"$prefix.toFieldId", toFieldId.value) ++ token(s"$prefix.bucket.count", buckets.size.toString) ++ buckets.zipWithIndex.flatMap { case (bucket, bucketIndex) => bucketTokens(s"$prefix.bucket[$bucketIndex]", bucket) } ++ token(s"$prefix.countingPolicy", countingPolicyLabel(countingPolicy))
    }
  }

  private def bucketTokens(prefix: String, value: CanonicalFacetBucket): Vector[String] =
    value match {
      case CanonicalFacetBucket.HalfOpen(id, min, max) =>
        token(s"$prefix.kind", "half-open") ++ token(s"$prefix.id", id.value) ++ token(s"$prefix.min", min) ++ token(s"$prefix.max", max)
      case CanonicalFacetBucket.UpperUnbounded(id, min) =>
        token(s"$prefix.kind", "upper-unbounded") ++ token(s"$prefix.id", id.value) ++ token(s"$prefix.min", min)
    }

  private def groupsTokens(values: Vector[CanonicalGroupRequest]): Vector[String] =
    token("group.count", values.size.toString) ++ values.zipWithIndex.flatMap { case (value, index) => groupTokens(index, value) }

  private def groupTokens(index: Int, value: CanonicalGroupRequest): Vector[String] = {
    val prefix = s"group[$index]"
    token(s"$prefix.id", value.id.value) ++ token(s"$prefix.keyFieldId", value.keyFieldId.value) ++ token(s"$prefix.size", value.size.toString) ++
      representativeTokens(s"$prefix.representative", value.representative) ++
      token(s"$prefix.metric.count", value.metrics.size.toString) ++ value.metrics.zipWithIndex.flatMap { case (metric, metricIndex) => metricTokens(s"$prefix.metric[$metricIndex]", metric) } ++
      token(s"$prefix.order.count", value.order.size.toString) ++ value.order.zipWithIndex.flatMap { case (order, orderIndex) => orderTokens(s"$prefix.order[$orderIndex]", order) } ++
      token(s"$prefix.precision", precisionLabel(value.precision))
  }

  private def representativeTokens(prefix: String, value: CanonicalRepresentativeRequest): Vector[String] =
    value match {
      case CanonicalRepresentativeRequest.IdentityOnly => token(s"$prefix.kind", "identity-only")
      case CanonicalRepresentativeRequest.Fields(fieldIds) =>
        token(s"$prefix.kind", "fields") ++ token(s"$prefix.count", fieldIds.size.toString) ++ fieldIds.zipWithIndex.flatMap { case (fieldId, index) => token(s"$prefix.field[$index]", fieldId.value) }
    }

  private def metricTokens(prefix: String, value: CanonicalGroupMetric): Vector[String] =
    value match {
      case CanonicalGroupMetric.BestScore(id) => token(s"$prefix.kind", "best-score") ++ token(s"$prefix.id", id.value)
      case CanonicalGroupMetric.MinGeoDistance(id, fieldId, origin) => token(s"$prefix.kind", "min-geo-distance") ++ token(s"$prefix.id", id.value) ++ token(s"$prefix.fieldId", fieldId.value) ++ token(s"$prefix.origin", origin.value)
    }

  private def orderTokens(prefix: String, value: CanonicalGroupOrder): Vector[String] =
    value match {
      case CanonicalGroupOrder.Metric(metricId, direction) => token(s"$prefix.kind", "metric") ++ token(s"$prefix.metricId", metricId.value) ++ token(s"$prefix.direction", directionLabel(direction))
      case CanonicalGroupOrder.MatchingDocumentCount(direction) => token(s"$prefix.kind", "matching-document-count") ++ token(s"$prefix.direction", directionLabel(direction))
      case CanonicalGroupOrder.Key(direction) => token(s"$prefix.kind", "key") ++ token(s"$prefix.direction", directionLabel(direction))
    }

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
