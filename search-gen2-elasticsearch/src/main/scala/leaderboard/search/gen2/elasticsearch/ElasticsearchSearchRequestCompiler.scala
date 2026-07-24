package leaderboard.search.gen2.elasticsearch

import leaderboard.search.gen2.contract.*
import leaderboard.search.gen2.core.plan.{BoundSearchPlan, ContractFingerprint}

import io.circe.Json

/** Where one compiled backend value came from, so a [[ElasticsearchSearchRequestCompileError.ValueEncoding]]
  * carries actionable context - mirrors `ElasticsearchDocumentCompileError.ValueEncoding`'s documentIndex
  * context on the indexing side. */
sealed trait ElasticsearchQueryValueContext

object ElasticsearchQueryValueContext {
  final case class Constraint(index: Int, fieldId: FieldId) extends ElasticsearchQueryValueContext
  final case class Facet(facetId: FacetId, fieldId: FieldId) extends ElasticsearchQueryValueContext
}

sealed trait ElasticsearchSearchRequestCompileError

object ElasticsearchSearchRequestCompileError {
  final case class ContractFingerprintMismatch(expected: ContractFingerprint, actual: ContractFingerprint) extends ElasticsearchSearchRequestCompileError
  final case class ValueEncoding(context: ElasticsearchQueryValueContext, error: SearchValueDecodeError) extends ElasticsearchSearchRequestCompileError
  final case class UnrepresentablePageSize(pageSize: Int) extends ElasticsearchSearchRequestCompileError
  final case class CursorStateDecodeFailed(error: ElasticsearchCursorStateError) extends ElasticsearchSearchRequestCompileError
  final case class MissingGeoScoringPolicy(signalIndex: Int, fieldId: FieldId) extends ElasticsearchSearchRequestCompileError
}

type CompiledElasticsearchSearchRequest[Document, Id] = ElasticsearchSearchRequestCompiler.CompiledElasticsearchSearchRequest[Document, Id]
type PreparedElasticsearchSearchRequest[Document, Id] = CompiledElasticsearchSearchRequest[Document, Id]

sealed trait ElasticsearchGenerationRequirement

object ElasticsearchGenerationRequirement {
  case object Active extends ElasticsearchGenerationRequirement
  final case class Pinned(reference: ElasticsearchGenerationReference) extends ElasticsearchGenerationRequirement
}

/** The one production entry point compiling a validated [[ElasticsearchPolicy]] and an already-bound
  * [[BoundSearchPlan]] into one immutable prepared [[CompiledElasticsearchSearchRequest]]. Accepts only a bound
  * plan - never a raw [[SearchPlan]] - and rejects a bound plan whose contract fingerprint differs from
  * the policy's own, so a caller cannot compile a request against a plan/generation the policy did not
  * produce. A non-empty `plan.groups` is carried into the prepared request and executed by the generic
  * secondary group-query owner; it is never silently ignored or recomputed from the hit window.
  */
object ElasticsearchSearchRequestCompiler {

  private final case class CompiledScoring(query: Json, hasScoring: Boolean)

  def compile[Document, Id](
    policy: ElasticsearchPolicy[Document, Id],
    boundPlan: BoundSearchPlan[Document],
  ): Either[ElasticsearchSearchRequestCompileError, PreparedElasticsearchSearchRequest[Document, Id]] = {
    val plan = boundPlan.plan
    for {
      _              <- requireMatchingFingerprint(policy, boundPlan)
      lookaheadSize  <- lookaheadSizeOf(plan.page.size)
      baseQuery      <- compileBaseQuery(policy, plan)
      scoredQuery    <- applyGeoScoring(policy, plan.softSignals, baseQuery)
      sortClauses     = compileSort(policy, policy.index.declaration.identity, plan, scoredQuery.hasScoring)
      cursorState    <- decodeCursorStateIfPresent(boundPlan, sortClauses.length)
      facetEntries   <- compileFacets(plan.facets)
    } yield {
      val generationRequirement = cursorState match {
        case None        => ElasticsearchGenerationRequirement.Active
        case Some(state) => ElasticsearchGenerationRequirement.Pinned(state.generationReference)
      }
      val searchAfterJson   = cursorState.map(_.searchAfterValues.map(ElasticsearchSearchAfterValue.toJson))
      val body              = assembleRequestBody(scoredQuery.query, sortClauses, searchAfterJson, facetEntries, lookaheadSize)

      new CompiledElasticsearchSearchRequest[Document, Id](
        generationRequirement = generationRequirement,
        body = body,
        executionQuery = scoredQuery.query,
        boundPlan = boundPlan,
        identityField = policy.index.declaration.identity,
        sortShape = sortClauses,
        requestedFacets = plan.facets,
        requestedGroups = plan.groups,
        totalHitsPolicy = policy.totalHitsPolicy,
        pageSize = plan.page.size,
      )
    }
  }

  /** Final read-only prepared result owned by this compiler. It deliberately contains no executable target;
    * lifecycle authorization binds this value to a retained physical generation before transport. */
  final class CompiledElasticsearchSearchRequest[Document, Id] private[ElasticsearchSearchRequestCompiler] (
    val generationRequirement: ElasticsearchGenerationRequirement,
    val body: Json,
    val executionQuery: Json,
    val boundPlan: BoundSearchPlan[Document],
    val identityField: SearchField[Document, Id],
    val sortShape: Vector[Json],
    val requestedFacets: Vector[FacetRequest[Document]],
    val requestedGroups: Vector[GroupRequest[Document, ?]],
    val totalHitsPolicy: ElasticsearchTotalHitsPolicy,
    val pageSize: PageSize,
  )

  private def requireMatchingFingerprint[Document, Id](
    policy: ElasticsearchPolicy[Document, Id],
    boundPlan: BoundSearchPlan[Document],
  ): Either[ElasticsearchSearchRequestCompileError, Unit] =
    if (boundPlan.identity.contractFingerprint == policy.contractFingerprint) Right(())
    else Left(ElasticsearchSearchRequestCompileError.ContractFingerprintMismatch(policy.contractFingerprint, boundPlan.identity.contractFingerprint))

  private def lookaheadSizeOf(pageSize: PageSize): Either[ElasticsearchSearchRequestCompileError, Int] =
    if (pageSize.value <= Int.MaxValue - 1) Right(pageSize.value + 1)
    else Left(ElasticsearchSearchRequestCompileError.UnrepresentablePageSize(pageSize.value))

  private def decodeCursorStateIfPresent[Document](
    boundPlan: BoundSearchPlan[Document],
    expectedSearchAfterArity: Int,
  ): Either[ElasticsearchSearchRequestCompileError, Option[ElasticsearchCursorState]] =
    boundPlan.backendState match {
      case None => Right(None)
      case Some(state) =>
        ElasticsearchCursorStateCodec.decode(state.opaqueValue, expectedSearchAfterArity) match {
          case Left(error)    => Left(ElasticsearchSearchRequestCompileError.CursorStateDecodeFailed(error))
          case Right(decoded) => Right(Some(decoded))
        }
    }

  private def encodeValue[Document, A](
    field: SearchField[Document, A],
    value: A,
    context: ElasticsearchQueryValueContext,
  ): Either[ElasticsearchSearchRequestCompileError, Json] =
    ElasticsearchScalarCompiler.toBackendJson(field.kind, field.codec.encodeCanonical(value)) match {
      case Left(error) => Left(ElasticsearchSearchRequestCompileError.ValueEncoding(context, error))
      case Right(json) => Right(json)
    }

  private def sequence[A](values: Vector[Either[ElasticsearchSearchRequestCompileError, A]]): Either[ElasticsearchSearchRequestCompileError, Vector[A]] =
    values.foldLeft[Either[ElasticsearchSearchRequestCompileError, Vector[A]]](Right(Vector.empty)) { (acc, next) =>
      acc.flatMap(done => next.map(done :+ _))
    }

  // ---------------------------------------------------------------------------------------------------
  // Base query: text + hard filters, in plan order. An entirely empty plan (no text, no filters) compiles
  // to explicit match_all, never an empty bool.
  // ---------------------------------------------------------------------------------------------------

  private def compileBaseQuery[Document](policy: ElasticsearchPolicy[Document, ?], plan: SearchPlan[Document]): Either[ElasticsearchSearchRequestCompileError, Json] =
    for {
      filterClauses <- compileHardFilters(plan.appliedFilters)
    } yield {
      val textClause = plan.residualText.map(text => multiMatchClause(policy, text))
      assembleBoolOrMatchAll(filterClauses, textClause)
    }

  private def multiMatchClause[Document](policy: ElasticsearchPolicy[Document, ?], text: String): Json =
    Json.obj(
      "multi_match" -> Json.obj(
        "query"    -> Json.fromString(text),
        "fields"   -> Json.fromValues(policy.queryTextFields.map(mapping => Json.fromString(s"${mapping.field.path.value}^${mapping.weight.value}"))),
        "operator" -> Json.fromString(policy.textOperator.wireValue),
      )
    )

  // Filter context has no standalone top-level query type in Elasticsearch - it only exists nested under
  // `bool.filter` - so hard filters alone still require a `bool` wrapper. `multi_match` is a standalone
  // top-level query type, so text alone needs no such wrapper; only the combination of text and filters
  // requires `bool.must` alongside `bool.filter`.
  private def assembleBoolOrMatchAll(filterClauses: Vector[Json], textClause: Option[Json]): Json =
    (filterClauses.isEmpty, textClause) match {
      case (true, None)          => Json.obj("match_all" -> Json.obj())
      case (true, Some(clause))  => clause
      case (false, textOpt) =>
        val mustField   = textOpt.map(clause => "must" -> Json.fromValues(Vector(clause))).toVector
        val filterField = Vector("filter" -> Json.fromValues(filterClauses))
        Json.obj("bool" -> Json.obj((mustField ++ filterField)*))
    }

  private def compileHardFilters[Document](appliedFilters: Vector[AppliedFilter[Document]]): Either[ElasticsearchSearchRequestCompileError, Vector[Json]] =
    sequence(appliedFilters.zipWithIndex.map { case (filter, index) => compileConstraint(filter.source.constraint, index) })

  private def compileConstraint[Document](constraint: PlannedConstraint[Document], index: Int): Either[ElasticsearchSearchRequestCompileError, Json] =
    constraint match {
      case terms: PlannedConstraint.Terms[Document, ?] =>
        compileTerms(terms.field, terms.values, index)
      case range: PlannedConstraint.NumberRange[Document, ?] =>
        compileNumberRange(range.field, range.bounds, index)
      case overlap: PlannedConstraint.IntervalOverlap[Document, ?] =>
        compileIntervalOverlapPredicate(overlap.from, overlap.to, overlap.bounds, field => ElasticsearchQueryValueContext.Constraint(index, field.id))
      case geo: PlannedConstraint.GeoDistanceFilter[Document] =>
        Right(compileGeoDistanceFilter(geo.field, geo.origin, geo.radius))
    }

  // Canonicalizes through the field codec/backend scalar compiler, then sorts by canonical value so the
  // compiled clause never depends on Set iteration order. An empty set compiles to match_none, never
  // disappearing from the filter list.
  private def compileTerms[Document, A](field: SearchField[Document, A], values: Set[A], constraintIndex: Int): Either[ElasticsearchSearchRequestCompileError, Json] =
    if (values.isEmpty) {
      Right(Json.obj("match_none" -> Json.obj()))
    } else {
      val sortedValues = values.toVector.map(field.codec.encodeCanonical).sorted
      sequence(sortedValues.map(canonical => encodeCanonicalValue(field, canonical, ElasticsearchQueryValueContext.Constraint(constraintIndex, field.id)))).map {
        case Vector(single) => Json.obj("term" -> Json.obj(field.path.value -> single))
        case many            => Json.obj("terms" -> Json.obj(field.path.value -> Json.fromValues(many)))
      }
    }

  private def encodeCanonicalValue[Document](
    field: SearchField[Document, ?],
    canonical: String,
    context: ElasticsearchQueryValueContext,
  ): Either[ElasticsearchSearchRequestCompileError, Json] =
    ElasticsearchScalarCompiler.toBackendJson(field.kind, canonical) match {
      case Left(error) => Left(ElasticsearchSearchRequestCompileError.ValueEncoding(context, error))
      case Right(json) => Right(json)
    }

  private def compileNumberRange[Document, A](field: SearchField[Document, A], bounds: RangeBounds[A], constraintIndex: Int): Either[ElasticsearchSearchRequestCompileError, Json] =
    for {
      lowerEntry <- boundToRangeEntry(field, bounds.lower, isLower = true, ElasticsearchQueryValueContext.Constraint(constraintIndex, field.id))
      upperEntry <- boundToRangeEntry(field, bounds.upper, isLower = false, ElasticsearchQueryValueContext.Constraint(constraintIndex, field.id))
    } yield {
      val entries = Vector(lowerEntry, upperEntry).flatten
      entries match {
        case Vector() => Json.obj("match_all" -> Json.obj())
        case _        => Json.obj("range" -> Json.obj(field.path.value -> Json.obj(entries*)))
      }
    }

  private def boundToRangeEntry[Document, A](
    field: SearchField[Document, A],
    bound: Bound[A],
    isLower: Boolean,
    context: ElasticsearchQueryValueContext,
  ): Either[ElasticsearchSearchRequestCompileError, Option[(String, Json)]] =
    bound match {
      case Bound.Unbounded => Right(None)
      case Bound.Inclusive(value) => encodeValue(field, value, context).map(json => Some((if (isLower) "gte" else "lte") -> json))
      case Bound.Exclusive(value) => encodeValue(field, value, context).map(json => Some((if (isLower) "gt" else "lt") -> json))
    }

  // Matches the exact interval-overlap direction mapping: the request's lower bound constrains
  // `to`, and its upper bound constrains `from` - never a range over `from` alone. Reused by both hard
  // IntervalOverlap filters and IntervalOverlap facet buckets via the `contextFor` parameter, so both
  // compile the exact same predicate shape.
  private def compileIntervalOverlapPredicate[Document, A](
    from: SearchField[Document, A],
    to: SearchField[Document, A],
    bounds: RangeBounds[A],
    contextFor: SearchField[Document, ?] => ElasticsearchQueryValueContext,
  ): Either[ElasticsearchSearchRequestCompileError, Json] =
    for {
      toEntry   <- bounds.lower match {
                     case Bound.Unbounded        => Right(None)
                     case Bound.Inclusive(value) => encodeValue(to, value, contextFor(to)).map(json => Some("gte" -> json))
                     case Bound.Exclusive(value) => encodeValue(to, value, contextFor(to)).map(json => Some("gt" -> json))
                   }
      fromEntry <- bounds.upper match {
                     case Bound.Unbounded        => Right(None)
                     case Bound.Inclusive(value) => encodeValue(from, value, contextFor(from)).map(json => Some("lte" -> json))
                     case Bound.Exclusive(value) => encodeValue(from, value, contextFor(from)).map(json => Some("lt" -> json))
                   }
    } yield {
      val clauses = Vector(
        toEntry.map(entry => Json.obj("range" -> Json.obj(to.path.value -> Json.obj(entry)))),
        fromEntry.map(entry => Json.obj("range" -> Json.obj(from.path.value -> Json.obj(entry)))),
      ).flatten
      clauses match {
        case Vector()      => Json.obj("match_all" -> Json.obj())
        case Vector(single) => single
        case many           => Json.obj("bool" -> Json.obj("filter" -> Json.fromValues(many)))
      }
    }

  private def compileGeoDistanceFilter[Document](field: SearchField[Document, GeoPoint], origin: GeoPoint, radius: Distance): Json =
    Json.obj(
      "geo_distance" -> Json.obj(
        "distance"      -> Json.fromString(s"${radius.meters}m"),
        "distance_type" -> Json.fromString("arc"),
        field.path.value -> Json.obj("lat" -> Json.fromBigDecimal(origin.lat), "lon" -> Json.fromBigDecimal(origin.lon)),
      )
    )

  // ---------------------------------------------------------------------------------------------------
  // Geo scoring: one `gauss` function per GeoProximitySignal, combined into one function_score wrapping
  // the same base query - hard filters remain untouched. Every signal must have a policy; none may vanish.
  // ---------------------------------------------------------------------------------------------------

  private def applyGeoScoring[Document](policy: ElasticsearchPolicy[Document, ?], softSignals: Vector[PlannedSignal[Document]], baseQuery: Json): Either[ElasticsearchSearchRequestCompileError, CompiledScoring] = {
    val geoSignals = softSignals.zipWithIndex.collect { case (signal: PlannedSignal.GeoProximitySignal[Document], index) => signal -> index }
    policy.geoScoringPolicy match {
      case None if geoSignals.nonEmpty =>
        geoSignals match {
          case Vector((signal, index), _*) => Left(ElasticsearchSearchRequestCompileError.MissingGeoScoringPolicy(index, signal.field.id))
          case _                           => Right(CompiledScoring(baseQuery, hasScoring = false))
        }
      case None => Right(CompiledScoring(baseQuery, hasScoring = false))
      case Some(geo) =>
        val functions = geoSignals.map { case (signal, _) => geoFunction(geo, signal) }
        if (functions.isEmpty) Right(CompiledScoring(baseQuery, hasScoring = false))
        else
          Right(
            CompiledScoring(
              Json.obj(
                "function_score" -> Json.obj(
                  "query"      -> baseQuery,
                  "functions"  -> Json.fromValues(functions),
                  "score_mode" -> Json.fromString("sum"),
                  "boost_mode" -> Json.fromString("sum"),
                )
              ),
              hasScoring = true,
            )
          )
    }
  }

  private def geoFunction[Document](geo: ElasticsearchGeoScoringPolicy, signal: PlannedSignal.GeoProximitySignal[Document]): Json =
    Json.obj(
        "gauss" -> Json.obj(
          signal.field.path.value -> Json.obj(
            "origin" -> Json.obj("lat" -> Json.fromBigDecimal(signal.origin.lat), "lon" -> Json.fromBigDecimal(signal.origin.lon)),
            "scale"  -> Json.fromString(s"${geo.scale.meters}m"),
            "offset" -> Json.fromString(s"${geo.offset.meters}m"),
            "decay"  -> Json.fromBigDecimal(BigDecimal(geo.decay.value)),
          )
        ),
        "weight" -> Json.fromBigDecimal(BigDecimal(geo.weight.value)),
      )

  // ---------------------------------------------------------------------------------------------------
  // Sort: explicit clauses in plan order when present; otherwise relevance (when scoring is active) or
  // nothing; the declared identity field is always appended as the final deterministic tie-breaker.
  // ---------------------------------------------------------------------------------------------------

  private def compileSort[Document, Id](policy: ElasticsearchPolicy[Document, Id], identityField: SearchField[Document, Id], plan: SearchPlan[Document], scoringActive: Boolean): Vector[Json] = {
    val explicitClauses = plan.sort.map(sortClauseJson)
    val hasScoring = plan.residualText.isDefined || scoringActive

    val baseClauses =
      if (explicitClauses.nonEmpty) explicitClauses
      else if (hasScoring) Vector(Json.obj("_score" -> Json.obj("order" -> Json.fromString(directionLabel(policy.defaultSortPolicy.relevanceDirection)))))
      else Vector.empty

    baseClauses :+ Json.obj(identityField.path.value -> Json.obj("order" -> Json.fromString(directionLabel(policy.defaultSortPolicy.identityTieBreakerDirection))))
  }

  private def sortClauseJson[Document](sort: PlannedSort[Document]): Json =
    sort match {
      case fieldValue: PlannedSort.FieldValue[Document, ?] =>
        Json.obj(fieldValue.field.path.value -> Json.obj("order" -> Json.fromString(directionLabel(fieldValue.direction))))
      case geoDistance: PlannedSort.GeoDistance[Document] =>
        Json.obj(
          "_geo_distance" -> Json.obj(
            geoDistance.field.path.value -> Json.obj("lat" -> Json.fromBigDecimal(geoDistance.origin.lat), "lon" -> Json.fromBigDecimal(geoDistance.origin.lon)),
            "order"         -> Json.fromString(directionLabel(geoDistance.direction)),
            "unit"          -> Json.fromString("m"),
            "distance_type" -> Json.fromString("arc"),
          )
        )
    }

  private def directionLabel(direction: SortDirection): String =
    direction match {
      case SortDirection.Asc  => "asc"
      case SortDirection.Desc => "desc"
    }

  // ---------------------------------------------------------------------------------------------------
  // Facets: aggregation names derive directly from the typed FacetId, never a normalized path or a
  // second public identity.
  // ---------------------------------------------------------------------------------------------------

  private[elasticsearch] def aggregationName(facetId: FacetId): String = s"facet:${facetId.value}"

  private def compileFacets[Document](facets: Vector[FacetRequest[Document]]): Either[ElasticsearchSearchRequestCompileError, Vector[(String, Json)]] =
    sequence(facets.map(facet => compileOneFacet(facet).map(json => aggregationName(facet.id) -> json)))

  private def compileOneFacet[Document](facet: FacetRequest[Document]): Either[ElasticsearchSearchRequestCompileError, Json] =
    facet match {
      case terms: FacetRequest.Terms[Document, ?]                 => Right(compileTermsFacet(terms))
      case numberRange: FacetRequest.NumberRange[Document, ?]     => compileNumberRangeFacet(numberRange)
      case interval: FacetRequest.IntervalOverlap[Document, ?]     => compileIntervalOverlapFacet(interval)
    }

  private def compileTermsFacet[Document, A](facet: FacetRequest.Terms[Document, A]): Json =
    Json.obj(
      "terms" -> Json.obj(
        "field"                     -> Json.fromString(facet.field.path.value),
        "size"                      -> Json.fromInt(facet.size.value),
        "order"                     -> termsOrderJson(facet.order),
        "show_term_doc_count_error" -> Json.fromBoolean(true),
      )
    )

  private def termsOrderJson(order: TermsFacetOrder): Json =
    order match {
      case TermsFacetOrder.CountDescThenKeyAsc =>
        Json.arr(Json.obj("_count" -> Json.fromString("desc")), Json.obj("_key" -> Json.fromString("asc")))
      case TermsFacetOrder.KeyAsc =>
        Json.obj("_key" -> Json.fromString("asc"))
    }

  private def compileNumberRangeFacet[Document, A](facet: FacetRequest.NumberRange[Document, A]): Either[ElasticsearchSearchRequestCompileError, Json] =
    sequence(facet.buckets.map(bucket => rangeBucketJson(facet.field, bucket, facet.id))).map { buckets =>
      Json.obj("range" -> Json.obj("field" -> Json.fromString(facet.field.path.value), "keyed" -> Json.fromBoolean(true), "ranges" -> Json.fromValues(buckets)))
    }

  private def rangeBucketJson[Document, A](field: SearchField[Document, A], bucket: FacetBucket[A], facetId: FacetId): Either[ElasticsearchSearchRequestCompileError, Json] =
    bucket match {
      case FacetBucket.HalfOpen(id, min, max) =>
        for {
          minJson <- encodeValue(field, min, ElasticsearchQueryValueContext.Facet(facetId, field.id))
          maxJson <- encodeValue(field, max, ElasticsearchQueryValueContext.Facet(facetId, field.id))
        } yield Json.obj("key" -> Json.fromString(id.value), "from" -> minJson, "to" -> maxJson)
      case FacetBucket.UpperUnbounded(id, min) =>
        encodeValue(field, min, ElasticsearchQueryValueContext.Facet(facetId, field.id)).map(minJson => Json.obj("key" -> Json.fromString(id.value), "from" -> minJson))
    }

  private def compileIntervalOverlapFacet[Document, A](facet: FacetRequest.IntervalOverlap[Document, A]): Either[ElasticsearchSearchRequestCompileError, Json] =
    sequence(facet.buckets.map(bucket => intervalBucketFilterEntry(facet.from, facet.to, bucket, facet.id))).map { entries =>
      Json.obj("filters" -> Json.obj("filters" -> Json.obj(entries*)))
    }

  private def intervalBucketFilterEntry[Document, A](
    from: SearchField[Document, A],
    to: SearchField[Document, A],
    bucket: FacetBucket[A],
    facetId: FacetId,
  ): Either[ElasticsearchSearchRequestCompileError, (String, Json)] =
    compileIntervalOverlapPredicate(from, to, bucket.bounds, field => ElasticsearchQueryValueContext.Facet(facetId, field.id)).map(json => bucket.id.value -> json)

  // ---------------------------------------------------------------------------------------------------
  // Request body assembly.
  // ---------------------------------------------------------------------------------------------------

  private def assembleRequestBody(
    query: Json,
    sortClauses: Vector[Json],
    searchAfter: Option[Vector[Json]],
    facetEntries: Vector[(String, Json)],
    size: Int,
  ): Json = {
    val baseFields = Vector(
      "_source"          -> Json.fromBoolean(true),
      "track_total_hits" -> Json.fromBoolean(true),
      "track_scores"     -> Json.fromBoolean(true),
      "size"             -> Json.fromInt(size),
      "query"            -> query,
      "sort"             -> Json.fromValues(sortClauses),
    )
    val searchAfterField = searchAfter.map(values => "search_after" -> Json.fromValues(values)).toVector
    val aggsField         = if (facetEntries.nonEmpty) Vector("aggs" -> Json.obj(facetEntries*)) else Vector.empty

    Json.obj((baseFields ++ searchAfterField ++ aggsField)*)
  }
}
