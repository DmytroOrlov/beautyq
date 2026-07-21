package leaderboard.search.gen2.elasticsearch

import leaderboard.search.gen2.contract.*
import leaderboard.search.gen2.core.plan.SearchCursorEnvelope
import leaderboard.search.gen2.elasticsearch.lifecycle.AuthorizedElasticsearchSearchRequest

import io.circe.{Json, JsonObject}

final case class ElasticsearchDocumentHit[Id](
  id: Id,
  source: JsonObject,
  score: BigDecimal,
  sortValues: Vector[ElasticsearchSearchAfterValue],
)

enum ElasticsearchTermsFacetPrecision {
  case Exact
  case Bounded(upperBound: Long)
  case Unknown
}

final case class ElasticsearchTermsFacetBucket[A](
  key: A,
  canonicalKey: String,
  count: Long,
  docCountErrorUpperBound: Option[Long],
)

final case class ElasticsearchTermsFacetResult[Document, A](
  id: FacetId,
  field: SearchField[Document, A],
  buckets: Vector[ElasticsearchTermsFacetBucket[A]],
  sumOtherDocCount: Long,
  precision: ElasticsearchTermsFacetPrecision,
  countingPolicy: FacetCountingPolicy,
)

private sealed trait ElasticsearchTermsFacetResultLike[Document] {
  def id: FacetId
  private[elasticsearch] def select[A](id: FacetId, field: SearchField[Document, A]): Option[ElasticsearchTermsFacetResult[Document, A]]
}

private final class TypedTermsFacetResult[Document, A](result: ElasticsearchTermsFacetResult[Document, A]) extends ElasticsearchTermsFacetResultLike[Document] {
  def id: FacetId = result.id

  private[elasticsearch] def select[B](requestedId: FacetId, requestedField: SearchField[Document, B]): Option[ElasticsearchTermsFacetResult[Document, B]] =
    // The identity and exact field-handle checks establish the existential member before this
    // package-private projection; callers can only reach it through BaselineSearchPage.termsFacet.
    if (requestedId == result.id && (requestedField eq result.field)) Some(result.asInstanceOf[ElasticsearchTermsFacetResult[Document, B]])
    else None
}

final case class ElasticsearchRangeFacetBucket(id: FacetBucketId, count: Long)

final case class ElasticsearchRangeFacetResult(
  id: FacetId,
  buckets: Vector[ElasticsearchRangeFacetBucket],
  countingPolicy: FacetCountingPolicy,
)

final case class ElasticsearchIntervalFacetBucket(id: FacetBucketId, count: Long)

final case class ElasticsearchIntervalFacetResult(
  id: FacetId,
  buckets: Vector[ElasticsearchIntervalFacetBucket],
  countingPolicy: FacetCountingPolicy,
)

sealed trait ElasticsearchFacetResult[Document] {
  def id: FacetId
}

object ElasticsearchFacetResult {
  final case class Terms[Document](result: ElasticsearchTermsFacetResultLike[Document]) extends ElasticsearchFacetResult[Document] {
    def id: FacetId = result.id
  }
  final case class NumberRange[Document](result: ElasticsearchRangeFacetResult) extends ElasticsearchFacetResult[Document] {
    def id: FacetId = result.id
  }
  final case class IntervalOverlap[Document](result: ElasticsearchIntervalFacetResult) extends ElasticsearchFacetResult[Document] {
    def id: FacetId = result.id
  }
}

final case class ElasticsearchResponseDiagnostics(
  timedOut: Boolean,
  shardsTotal: Int,
  shardsSuccessful: Int,
  shardsFailed: Int,
)

sealed trait ElasticsearchSearchResponseError

object ElasticsearchSearchResponseError {
  final case class MalformedResponse(message: String) extends ElasticsearchSearchResponseError
  final case class PartialResponse(diagnostics: ElasticsearchResponseDiagnostics) extends ElasticsearchSearchResponseError
  final case class NonExactTotalRelation(actual: String) extends ElasticsearchSearchResponseError
  final case class MalformedHit(index: Int, message: String) extends ElasticsearchSearchResponseError
  final case class IdentityDecodeFailed(index: Int, error: SearchValueDecodeError) extends ElasticsearchSearchResponseError
  final case class SortArityMismatch(index: Int, expected: Int, actual: Int) extends ElasticsearchSearchResponseError
  final case class UnexpectedHitCount(maximum: Int, actual: Int) extends ElasticsearchSearchResponseError
  final case class UnsupportedSortValueShape(hitIndex: Int, sortIndex: Int) extends ElasticsearchSearchResponseError
  final case class MissingRequestedAggregation(facetId: FacetId) extends ElasticsearchSearchResponseError
  final case class UnknownAggregation(name: String) extends ElasticsearchSearchResponseError
  final case class MalformedAggregation(facetId: FacetId, message: String) extends ElasticsearchSearchResponseError
  final case class MissingDeclaredBucket(facetId: FacetId, bucketId: FacetBucketId) extends ElasticsearchSearchResponseError
  final case class UnknownBucket(facetId: FacetId, bucketKey: String) extends ElasticsearchSearchResponseError
}

type ElasticsearchSearchResponseErrors = NonEmptyErrors[ElasticsearchSearchResponseError]

type BaselineSearchPage[Document, Id] = ElasticsearchSearchResponseDecoder.BaselineSearchPage[Document, Id]

/** The one production entry point decoding a raw Elasticsearch response `Json` against the exact
  * lifecycle-authorized [[AuthorizedElasticsearchSearchRequest]] that produced it - never separately supplied plan, facet, sort
  * or policy values, so a response can never be paired with an unrelated request. A timed-out or
  * shard-failed response is never represented as a trusted exact baseline page: it is a typed
  * [[ElasticsearchSearchResponseError.PartialResponse]] instead. Missing/unknown requested aggregations
  * and missing/unknown declared buckets accumulate every issue found, in request/stable-name order;
  * structural response problems (partial response, non-exact total, a malformed hit) stop at the first
  * one found, since nothing later in the response can be trusted once one of those occurs.
  */
object ElasticsearchSearchResponseDecoder {

  /** Final read-only result owned by this decoder. A private constructor and no companion factory make
    * this decoder the only production construction path. Validated backend diagnostics remain on the page
    * alongside hits, totals, facets and the next cursor. */
  final class BaselineSearchPage[Document, Id] private[ElasticsearchSearchResponseDecoder] (
    val hits: Vector[ElasticsearchDocumentHit[Id]],
    val totalHits: Long,
    val totalRelation: String,
    val facets: Vector[ElasticsearchFacetResult[Document]],
    val diagnostics: ElasticsearchResponseDiagnostics,
    val nextCursor: Option[SearchCursor],
  ) {
    def termsFacet[A](id: FacetId, field: SearchField[Document, A]): Option[ElasticsearchTermsFacetResult[Document, A]] =
      facets.flatMap {
        case ElasticsearchFacetResult.Terms(result) => result.select(id, field).toVector
        case _                                      => Vector.empty
      }.headOption
  }

  def decode[Document, Id](
    compiled: AuthorizedElasticsearchSearchRequest[Document, Id],
    response: Json,
  ): Either[ElasticsearchSearchResponseErrors, BaselineSearchPage[Document, Id]] =
    for {
      obj         <- liftSingle(topLevelObject(response))
      diagnostics <- liftSingle(decodeDiagnostics(obj))
      _           <- liftSingle(requireNotPartial(diagnostics))
      total       <- liftSingle(decodeTotal(obj, compiled.prepared.totalHitsPolicy))
      rawHits     <- liftSingle(decodeRawHits(obj))
      _           <- liftSingle(requireHitWindow(rawHits, compiled.prepared.pageSize.value + 1))
      hits        <- liftSingle(decodeHits(rawHits, compiled))
      facets      <- decodeFacets(obj, compiled.prepared.requestedFacets)
    } yield assemblePage(compiled, hits, total.value, total.relation, facets, diagnostics)

  private def single[A](error: A): NonEmptyErrors[A] = NonEmptyErrors.fromHead(error, Vector.empty)

  private def liftSingle[A](result: Either[ElasticsearchSearchResponseError, A]): Either[ElasticsearchSearchResponseErrors, A] =
    result.left.map(single)

  // ---------------------------------------------------------------------------------------------------
  // Structural response decoding - single-error, short-circuiting: once one of these fails, nothing later
  // in the response can be trusted.
  // ---------------------------------------------------------------------------------------------------

  private def topLevelObject(response: Json): Either[ElasticsearchSearchResponseError, JsonObject] =
    response.asObject.toRight(ElasticsearchSearchResponseError.MalformedResponse("expected a JSON object"))

  private def decodeDiagnostics(obj: JsonObject): Either[ElasticsearchSearchResponseError, ElasticsearchResponseDiagnostics] =
    for {
      timedOut   <- obj("timed_out").flatMap(_.asBoolean).toRight(ElasticsearchSearchResponseError.MalformedResponse("missing or invalid 'timed_out'"))
      shardsObj  <- obj("_shards").flatMap(_.asObject).toRight(ElasticsearchSearchResponseError.MalformedResponse("missing or invalid '_shards'"))
      total      <- intField(shardsObj, "total")
      successful <- intField(shardsObj, "successful")
      failed     <- intField(shardsObj, "failed")
    } yield ElasticsearchResponseDiagnostics(timedOut, total, successful, failed)

  private def intField(obj: JsonObject, name: String): Either[ElasticsearchSearchResponseError, Int] =
    obj(name).flatMap(_.asNumber).flatMap(_.toInt).toRight(ElasticsearchSearchResponseError.MalformedResponse(s"missing or invalid '_shards.$name'"))

  private def requireNotPartial(diagnostics: ElasticsearchResponseDiagnostics): Either[ElasticsearchSearchResponseError, Unit] =
    if (diagnostics.shardsTotal < 0 || diagnostics.shardsSuccessful < 0 || diagnostics.shardsFailed < 0 ||
        diagnostics.shardsSuccessful > diagnostics.shardsTotal || diagnostics.shardsFailed > diagnostics.shardsTotal ||
        diagnostics.shardsSuccessful > diagnostics.shardsTotal - diagnostics.shardsFailed)
      Left(ElasticsearchSearchResponseError.MalformedResponse("invalid shard diagnostics"))
    else if (diagnostics.timedOut || diagnostics.shardsFailed > 0) Left(ElasticsearchSearchResponseError.PartialResponse(diagnostics))
    else Right(())

  private final case class DecodedTotal(value: Long, relation: String)

  private def decodeTotal(obj: JsonObject, totalHitsPolicy: ElasticsearchTotalHitsPolicy): Either[ElasticsearchSearchResponseError, DecodedTotal] =
    for {
      hitsObj  <- obj("hits").flatMap(_.asObject).toRight(ElasticsearchSearchResponseError.MalformedResponse("missing or invalid 'hits'"))
      totalObj <- hitsObj("total").flatMap(_.asObject).toRight(ElasticsearchSearchResponseError.MalformedResponse("missing or invalid 'hits.total'"))
      value    <- totalObj("value").flatMap(_.asNumber).flatMap(_.toLong).toRight(ElasticsearchSearchResponseError.MalformedResponse("missing or invalid 'hits.total.value'"))
      _        <- Either.cond(value >= 0L, (), ElasticsearchSearchResponseError.MalformedResponse("negative 'hits.total.value'"))
      relation <- totalObj("relation").flatMap(_.asString).toRight(ElasticsearchSearchResponseError.MalformedResponse("missing or invalid 'hits.total.relation'"))
      _        <- requireExactRelationIfDemanded(relation, totalHitsPolicy)
    } yield DecodedTotal(value, relation)

  private def requireExactRelationIfDemanded(relation: String, policy: ElasticsearchTotalHitsPolicy): Either[ElasticsearchSearchResponseError, Unit] =
    policy match {
      case ElasticsearchTotalHitsPolicy.ExactRequired =>
        if (relation == "eq") Right(()) else Left(ElasticsearchSearchResponseError.NonExactTotalRelation(relation))
    }

  private def decodeRawHits(obj: JsonObject): Either[ElasticsearchSearchResponseError, Vector[Json]] =
    for {
      hitsObj <- obj("hits").flatMap(_.asObject).toRight(ElasticsearchSearchResponseError.MalformedResponse("missing or invalid 'hits'"))
      hitsArr <- hitsObj("hits").flatMap(_.asArray).map(_.toVector).toRight(ElasticsearchSearchResponseError.MalformedResponse("missing or invalid 'hits.hits'"))
    } yield hitsArr

  private def requireHitWindow(rawHits: Vector[Json], maximum: Int): Either[ElasticsearchSearchResponseError, Unit] =
    if (rawHits.length <= maximum) Right(()) else Left(ElasticsearchSearchResponseError.UnexpectedHitCount(maximum, rawHits.length))

  private def decodeHits[Document, Id](
    rawHits: Vector[Json],
    compiled: AuthorizedElasticsearchSearchRequest[Document, Id],
  ): Either[ElasticsearchSearchResponseError, Vector[ElasticsearchDocumentHit[Id]]] =
    rawHits.zipWithIndex.foldLeft[Either[ElasticsearchSearchResponseError, Vector[ElasticsearchDocumentHit[Id]]]](Right(Vector.empty)) {
      case (acc, (json, index)) => acc.flatMap(done => decodeOneHit(json, index, compiled).map(done :+ _))
    }

  private def decodeOneHit[Document, Id](
    json: Json,
    index: Int,
    compiled: AuthorizedElasticsearchSearchRequest[Document, Id],
  ): Either[ElasticsearchSearchResponseError, ElasticsearchDocumentHit[Id]] =
    for {
      hitObj     <- json.asObject.toRight(ElasticsearchSearchResponseError.MalformedHit(index, "expected a JSON object"))
      idString   <- hitObj("_id").flatMap(_.asString).toRight(ElasticsearchSearchResponseError.MalformedHit(index, "missing or invalid '_id'"))
      id         <- compiled.prepared.identityField.codec.decodeCanonical(idString).left.map(error => ElasticsearchSearchResponseError.IdentityDecodeFailed(index, error))
      source     <- hitObj("_source").flatMap(_.asObject).toRight(ElasticsearchSearchResponseError.MalformedHit(index, "missing or non-object '_source'"))
      score      <- hitObj("_score").flatMap(_.asNumber).flatMap(_.toBigDecimal).toRight(ElasticsearchSearchResponseError.MalformedHit(index, "missing or invalid '_score'"))
      sortJson   <- hitObj("sort").flatMap(_.asArray).map(_.toVector).toRight(ElasticsearchSearchResponseError.MalformedHit(index, "missing or invalid 'sort'"))
      _          <- requireSortArity(index, compiled.prepared.sortShape.length, sortJson.length)
      sortValues <- decodeSortValues(index, sortJson)
    } yield ElasticsearchDocumentHit(id, source, score, sortValues)

  private def requireSortArity(index: Int, expected: Int, actual: Int): Either[ElasticsearchSearchResponseError, Unit] =
    if (expected == actual) Right(()) else Left(ElasticsearchSearchResponseError.SortArityMismatch(index, expected, actual))

  private def decodeSortValues(hitIndex: Int, sortJson: Vector[Json]): Either[ElasticsearchSearchResponseError, Vector[ElasticsearchSearchAfterValue]] =
    sortJson.zipWithIndex.foldLeft[Either[ElasticsearchSearchResponseError, Vector[ElasticsearchSearchAfterValue]]](Right(Vector.empty)) {
      case (acc, (json, sortIndex)) =>
        acc.flatMap { done =>
          ElasticsearchSearchAfterValue.fromJson(json) match {
            case Some(value) => Right(done :+ value)
            case None        => Left(ElasticsearchSearchResponseError.UnsupportedSortValueShape(hitIndex, sortIndex))
          }
        }
    }

  // ---------------------------------------------------------------------------------------------------
  // Facet decoding - accumulates every missing/unknown/malformed issue found, rather than stopping at the
  // first one, since each requested facet is an independent concern.
  // ---------------------------------------------------------------------------------------------------

  private def decodeFacets[Document](
    obj: JsonObject,
    requestedFacets: Vector[FacetRequest[Document]],
  ): Either[ElasticsearchSearchResponseErrors, Vector[ElasticsearchFacetResult[Document]]] = {
    val aggsObjEither: Either[ElasticsearchSearchResponseError, JsonObject] =
      obj("aggregations").orElse(obj("aggs")) match {
        case None       => Right(JsonObject.empty)
        case Some(value) => value.asObject.toRight(ElasticsearchSearchResponseError.MalformedResponse("'aggregations' must be a JSON object"))
      }

    aggsObjEither match {
      case Left(error) => Left(single(error))
      case Right(aggsObj) =>
        val requestedNames = requestedFacets.map(facet => ElasticsearchSearchRequestCompiler.aggregationName(facet.id)).toSet

        val missingErrors: Vector[ElasticsearchSearchResponseError] =
          requestedFacets.flatMap { facet =>
            if (aggsObj.contains(ElasticsearchSearchRequestCompiler.aggregationName(facet.id))) Vector.empty
            else Vector(ElasticsearchSearchResponseError.MissingRequestedAggregation(facet.id))
          }

        val unknownErrors: Vector[ElasticsearchSearchResponseError] =
          aggsObj.keys.toVector.sorted.flatMap { name =>
            if (requestedNames.contains(name)) Vector.empty else Vector(ElasticsearchSearchResponseError.UnknownAggregation(name))
          }

        val (decoded, malformedErrors) =
          requestedFacets.foldLeft((Vector.empty[ElasticsearchFacetResult[Document]], Vector.empty[ElasticsearchSearchResponseError])) { case ((results, errors), facet) =>
            aggsObj(ElasticsearchSearchRequestCompiler.aggregationName(facet.id)) match {
              case None => (results, errors)
              case Some(aggJson) =>
                decodeOneFacet(facet, aggJson) match {
                  case Right(result)  => (results :+ result, errors)
                  case Left(newErrors) => (results, errors ++ newErrors)
                }
            }
          }

        NonEmptyErrors.fromVector(missingErrors ++ unknownErrors ++ malformedErrors) match {
          case Some(errors) => Left(errors)
          case None         => Right(decoded)
        }
    }
  }

  private def decodeOneFacet[Document](facet: FacetRequest[Document], aggJson: Json): Either[Vector[ElasticsearchSearchResponseError], ElasticsearchFacetResult[Document]] =
    facet match {
      case terms: FacetRequest.Terms[Document, ?] =>
        decodeTermsFacet(terms, aggJson)

      case numberRange: FacetRequest.NumberRange[Document, ?] =>
        decodeKeyedFacet(numberRange.id, numberRange.buckets.map(_.id), numberRange.countingPolicy, aggJson) { (buckets, policy) =>
          ElasticsearchFacetResult.NumberRange[Document](
            ElasticsearchRangeFacetResult(numberRange.id, buckets.map { case (id, count) => ElasticsearchRangeFacetBucket(id, count) }, policy)
          )
        }

      case interval: FacetRequest.IntervalOverlap[Document, ?] =>
        decodeKeyedFacet(interval.id, interval.buckets.map(_.id), interval.countingPolicy, aggJson) { (buckets, policy) =>
          ElasticsearchFacetResult.IntervalOverlap[Document](
            ElasticsearchIntervalFacetResult(interval.id, buckets.map { case (id, count) => ElasticsearchIntervalFacetBucket(id, count) }, policy)
          )
        }
    }

  private def decodeTermsFacet[Document, A](facet: FacetRequest.Terms[Document, A], aggJson: Json): Either[Vector[ElasticsearchSearchResponseError], ElasticsearchFacetResult[Document]] =
    aggJson.asObject match {
      case None => Left(Vector(ElasticsearchSearchResponseError.MalformedAggregation(facet.id, "expected a JSON object")))
      case Some(aggObj) =>
        (for {
          bucketsJson <- aggObj("buckets").flatMap(_.asArray).map(_.toVector).toRight("missing or invalid 'buckets'")
          buckets     <- sequenceStrings(bucketsJson.map(bucketJson => decodeTermsBucket(facet.field, bucketJson)))
          sumOther    <- aggObj("sum_other_doc_count").flatMap(_.asNumber).flatMap(_.toLong).toRight("missing or invalid 'sum_other_doc_count'")
          _           <- Either.cond(sumOther >= 0L, (), "negative 'sum_other_doc_count'")
          errorBound  <- optionalLongField(aggObj, "doc_count_error_upper_bound")
          _           <- errorBound match {
                           case Some(value) if value < -1L => Left("invalid 'doc_count_error_upper_bound'")
                           case _                          => Right(())
                         }
        } yield ElasticsearchFacetResult.Terms[Document](
          new TypedTermsFacetResult(
            ElasticsearchTermsFacetResult(facet.id, facet.field, buckets, sumOther, precisionOf(errorBound), facet.countingPolicy)
          )
        )).left.map(message => Vector(ElasticsearchSearchResponseError.MalformedAggregation(facet.id, message)))
    }

  private def precisionOf(errorUpperBound: Option[Long]): ElasticsearchTermsFacetPrecision =
    errorUpperBound match {
      case None                       => ElasticsearchTermsFacetPrecision.Unknown
      case Some(-1L)                  => ElasticsearchTermsFacetPrecision.Unknown
      case Some(value) if value == 0L => ElasticsearchTermsFacetPrecision.Exact
      case Some(value)                => ElasticsearchTermsFacetPrecision.Bounded(value)
    }

  private def decodeTermsBucket[Document, A](field: SearchField[Document, A], bucketJson: Json): Either[String, ElasticsearchTermsFacetBucket[A]] =
    for {
      bucketObj <- bucketJson.asObject.toRight("expected a bucket object")
      count     <- bucketObj("doc_count").flatMap(_.asNumber).flatMap(_.toLong).toRight("missing or invalid bucket 'doc_count'")
      _         <- Either.cond(count >= 0L, (), "negative bucket 'doc_count'")
      canonical <- canonicalTermsKey(bucketObj, field.kind).toRight("missing or invalid bucket key")
      decoded   <- field.codec.decodeCanonical(canonical).left.map(error => s"bucket key '$canonical' failed to decode: ${error.message}")
      errorBound <- optionalLongField(bucketObj, "doc_count_error_upper_bound")
      _         <- errorBound match {
                     case Some(value) if value < -1L => Left("invalid bucket 'doc_count_error_upper_bound'")
                     case _                          => Right(())
                   }
    } yield ElasticsearchTermsFacetBucket(decoded, canonical, count, errorBound)

  private def canonicalTermsKey(bucketObj: JsonObject, kind: SearchFieldKind): Option[String] =
    bucketObj("key_as_string") match {
      case Some(value) => value.asString
      case None =>
        bucketObj("key").flatMap { json =>
          kind match {
            case SearchFieldKind.Keyword => json.asString
            case SearchFieldKind.Integer | SearchFieldKind.Long => json.asNumber.map(_.toString)
            case SearchFieldKind.Boolean => json.asBoolean.map(_.toString)
            case _ => None
          }
        }
    }

  private def optionalLongField(obj: JsonObject, name: String): Either[String, Option[Long]] =
    obj(name) match {
      case None        => Right(None)
      case Some(value) => value.asNumber.flatMap(_.toLong).toRight(s"invalid '$name'").map(Some(_))
    }

  private def sequenceStrings[A](values: Vector[Either[String, A]]): Either[String, Vector[A]] =
    values.foldLeft[Either[String, Vector[A]]](Right(Vector.empty)) { (acc, next) => acc.flatMap(done => next.map(done :+ _)) }

  // Shared keyed-bucket-object decoding for NumberRange (range agg) and IntervalOverlap (named filters
  // agg) facets: both return ES responses shaped as one keyed object of {"doc_count": N} entries, reordered
  // into the original declared bucket order, with the same missing/unknown-bucket accounting.
  private def decodeKeyedFacet[Document](
    facetId: FacetId,
    declaredIds: Vector[FacetBucketId],
    countingPolicy: FacetCountingPolicy,
    aggJson: Json,
  )(
    assemble: (Vector[(FacetBucketId, Long)], FacetCountingPolicy) => ElasticsearchFacetResult[Document]
  ): Either[Vector[ElasticsearchSearchResponseError], ElasticsearchFacetResult[Document]] =
    aggJson.asObject.flatMap(_.apply("buckets")).flatMap(_.asObject) match {
      case None => Left(Vector(ElasticsearchSearchResponseError.MalformedAggregation(facetId, "missing or invalid keyed 'buckets' object")))
      case Some(bucketsObj) =>
        val missingErrors = declaredIds.filterNot(id => bucketsObj.contains(id.value)).map(id => ElasticsearchSearchResponseError.MissingDeclaredBucket(facetId, id))
        val unknownErrors = bucketsObj.keys.toVector.sorted.filterNot(key => declaredIds.exists(_.value == key)).map(key => ElasticsearchSearchResponseError.UnknownBucket(facetId, key))

        val presentDeclared = declaredIds.filter(id => bucketsObj.contains(id.value))
        val decodeResults    = presentDeclared.map(id => decodeBucketCount(bucketsObj, id.value).map(count => id -> count))
        val malformedErrors  = decodeResults.collect { case Left(_) => ElasticsearchSearchResponseError.MalformedAggregation(facetId, "malformed or negative bucket doc_count") }
        val decodedBuckets   = decodeResults.collect { case Right(bucket) => bucket }

        val allErrors = missingErrors ++ unknownErrors ++ malformedErrors
        if (allErrors.nonEmpty) Left(allErrors) else Right(assemble(decodedBuckets, countingPolicy))
    }

  private def decodeBucketCount(bucketsObj: JsonObject, key: String): Either[Unit, Long] =
    bucketsObj(key).flatMap(_.asObject).flatMap(_.apply("doc_count")).flatMap(_.asNumber).flatMap(_.toLong).filter(_ >= 0L).toRight(())

  // ---------------------------------------------------------------------------------------------------
  // Pagination/result binding.
  // ---------------------------------------------------------------------------------------------------

  private def assemblePage[Document, Id](
    compiled: AuthorizedElasticsearchSearchRequest[Document, Id],
    decodedHits: Vector[ElasticsearchDocumentHit[Id]],
    totalHits: Long,
    totalRelation: String,
    facets: Vector[ElasticsearchFacetResult[Document]],
    diagnostics: ElasticsearchResponseDiagnostics,
  ): BaselineSearchPage[Document, Id] = {
    val pageSize = compiled.prepared.pageSize.value
    val hasNext  = decodedHits.length > pageSize
    val pageHits = decodedHits.take(pageSize)

    val nextCursor =
      if (!hasNext) None
      else
        pageHits.lastOption.map { lastHit =>
          val state = ElasticsearchCursorState(ElasticsearchSearchCompilerVersion.Current, compiled.generationReference, lastHit.sortValues)
          SearchCursorEnvelope.issue(compiled.prepared.boundPlan, ElasticsearchCursorStateCodec.encode(state))
        }

    new BaselineSearchPage[Document, Id](pageHits, totalHits, totalRelation, facets, diagnostics, nextCursor)
  }
}
