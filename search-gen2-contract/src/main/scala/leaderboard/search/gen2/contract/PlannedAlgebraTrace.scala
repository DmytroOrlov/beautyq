package leaderboard.search.gen2.contract

/** Deterministic, human-readable diagnostic view of one [[PlannedConstraint]], [[PlannedSignal]],
  * [[PlannedSort]], or [[PlanConstraintError]] value, in the same spirit as
  * [[SearchStructureRenderer]] for document declarations: a reviewer-readable rendering of an
  * executable value, never a second hand-maintained representation of it.
  *
  * This trace is not PlanIdentity, cursor input, backend JSON, or a canonical wire format. It exists
  * to make a plan-time constraint/signal/sort or its rejection reviewable in a test diff or a log line.
  * Brick 4C's `PlanIdentity`/cursor encoding must derive its own canonical representation directly from
  * typed plan values (field IDs, canonical codec values, stable case identity) and must not depend on,
  * parse, or embed these trace strings.
  *
  * Every rendered field uses only its own stable declaration metadata - [[FieldId]] and
  * [[SearchValueTypeId]] - never `SearchField.toString`, extractor function identity, object identity/
  * hash code, or `Set.toString`. Every rendered value goes through the field's own
  * [[SearchValueCodec.encodeCanonical]], the same canonical form the field's codec already guarantees
  * round-trips, rather than a separate ad hoc formatting rule.
  */
object PlannedAlgebraTrace {

  def constraint[Document](value: PlannedConstraint[Document]): String =
    value match {
      case PlannedConstraint.Terms(field, values) =>
        val sortedEncodedValues = values.iterator.map(field.codec.encodeCanonical).toVector.sorted
        s"constraint.terms field=${renderField(field)} values=[${sortedEncodedValues.mkString(", ")}]"

      case PlannedConstraint.NumberRange(field, bounds) =>
        s"constraint.number-range field=${renderField(field)} bounds=${renderRangeBounds(field, bounds)}"

      case PlannedConstraint.IntervalOverlap(from, to, bounds) =>
        // Each field's own type ID is rendered independently (renderField), while bounds are always
        // encoded through the `from` codec - total even for an unvalidated mismatched interval, per
        // PlannedConstraint.IntervalOverlap's own doc comment.
        s"constraint.interval-overlap from=${renderField(from)} to=${renderField(to)} bounds=${renderRangeBounds(from, bounds)}"

      case PlannedConstraint.GeoDistanceFilter(field, origin, radius) =>
        s"constraint.geo-distance-filter field=${renderField(field)} origin=${renderGeoPoint(origin)} radius=${renderDistance(radius)}"
    }

  def signal[Document](value: PlannedSignal[Document]): String =
    value match {
      case PlannedSignal.GeoProximitySignal(field, origin) =>
        s"signal.geo-proximity field=${renderField(field)} origin=${renderGeoPoint(origin)}"
    }

  def sort[Document](value: PlannedSort[Document]): String =
    value match {
      case PlannedSort.FieldValue(field, direction) =>
        s"sort.field-value field=${renderField(field)} direction=${renderDirection(direction)}"

      case PlannedSort.GeoDistance(field, origin, direction) =>
        s"sort.geo-distance field=${renderField(field)} origin=${renderGeoPoint(origin)} direction=${renderDirection(direction)}"
    }

  def error(value: PlanConstraintError): String =
    value match {
      case PlanConstraintError.UnsupportedFilterOperator(fieldId, kind, accepted) =>
        s"error.unsupported-filter-operator field=${fieldId.value} kind=${renderKind(kind)} accepted=[${accepted.map(renderFilterOperator).mkString(", ")}]"

      case PlanConstraintError.MismatchedIntervalFields(fromFieldId, toFieldId, fromTypeId, toTypeId) =>
        s"error.mismatched-interval-fields from=${renderFieldRef(fromFieldId, fromTypeId)} to=${renderFieldRef(toFieldId, toTypeId)}"

      case PlanConstraintError.UnsupportedSortMode(fieldId, kind, required) =>
        s"error.unsupported-sort-mode field=${fieldId.value} kind=${renderKind(kind)} required=${renderSortMode(required)}"
    }

  // Compact "id:typeId" field reference, built only from stable declaration metadata - never
  // SearchField.toString, extractor identity, or object hash code.
  private def renderField[Document, A](field: SearchField[Document, A]): String =
    renderFieldRef(field.id, field.codec.typeId)

  private def renderFieldRef(id: FieldId, typeId: SearchValueTypeId): String =
    s"${id.value}:${typeId.value}"

  private def renderRangeBounds[Document, A](field: SearchField[Document, A], bounds: RangeBounds[A]): String =
    s"[${renderBound(field, bounds.lower)}, ${renderBound(field, bounds.upper)}]"

  private def renderBound[Document, A](field: SearchField[Document, A], bound: Bound[A]): String =
    bound match {
      case Bound.Unbounded        => "unbounded"
      case Bound.Inclusive(value) => s"inclusive(${field.codec.encodeCanonical(value)})"
      case Bound.Exclusive(value) => s"exclusive(${field.codec.encodeCanonical(value)})"
    }

  // GeoPoint's own codec already normalizes lat/lon through the canonical BigDecimal form
  // (SearchValueCodec.geoPoint delegates decimal formatting the same way SearchValueCodec.bigDecimal
  // does), so geo origins never go through BigDecimal.toString.
  private def renderGeoPoint(point: GeoPoint): String = SearchValueCodec.geoPoint.encodeCanonical(point)

  private def renderDistance(distance: Distance): String = SearchValueCodec.bigDecimal.encodeCanonical(distance.meters)

  private def renderDirection(direction: SortDirection): String = direction.toString

  private def renderKind(kind: SearchFieldKind): String = kind.toString

  private def renderFilterOperator(operator: FilterOperator): String = operator.toString

  private def renderSortMode(mode: SortMode): String = mode.toString
}
