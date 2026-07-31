package leaderboard.search.beautyq.gen2.wiring

import io.circe.{Json, JsonObject}
import leaderboard.search.beautyq.gen2.contract.*
import leaderboard.search.gen2.contract.{ConstraintProvenance, PlannedAlgebraTrace, SuppressionReason}
import leaderboard.search.gen2.contract.SearchCursor.opaqueValue
import leaderboard.search.gen2.elasticsearch.*

enum BeautyQSearchHitOrigin(val stableCode: String) {
  case ElasticsearchBaseline extends BeautyQSearchHitOrigin("elasticsearch_baseline")
  case QdrantSupplement extends BeautyQSearchHitOrigin("qdrant_supplement")
}

type BeautyQSearchHitGen2 = BeautyQSearchResponseGen2Projector.Hit
type BeautyQAppliedFilterGen2 = BeautyQSearchResponseGen2Projector.AppliedFilter
type BeautyQSuppressedFilterGen2 = BeautyQSearchResponseGen2Projector.SuppressedFilter
type BeautyQFacetBucketGen2 = BeautyQSearchResponseGen2Projector.FacetBucket
type BeautyQFacetSummaryGen2 = BeautyQSearchResponseGen2Projector.FacetSummary
type BeautyQGroupSummaryGen2 = BeautyQSearchResponseGen2Projector.GroupSummary
type BeautyQGroupBucketGen2 = BeautyQSearchResponseGen2Projector.GroupBucket
type BeautyQProviderCarouselItemGen2 = BeautyQSearchResponseGen2Projector.ProviderCarouselItem
type BeautyQServiceIntentCarouselItemGen2 = BeautyQSearchResponseGen2Projector.ServiceIntentCarouselItem
type BeautyQSearchWarningGen2 = BeautyQSearchResponseGen2Projector.Warning
type BeautyQSearchResponseGen2 = BeautyQSearchResponseGen2Projector.Response

sealed trait BeautyQSearchResponseProjectionError
object BeautyQSearchResponseProjectionError {
  final case class Baseline(error: BeautyQCarouselProjectionError) extends BeautyQSearchResponseProjectionError
  final case class MissingFacet(id: String) extends BeautyQSearchResponseProjectionError
}

object BeautyQSearchResponseGen2Projector {
  final class Hit private[BeautyQSearchResponseGen2Projector] (
    val id: String,
    val score: BigDecimal,
    val origin: BeautyQSearchHitOrigin,
    val source: JsonObject,
  )

  final class AppliedFilter private[BeautyQSearchResponseGen2Projector] (
    val fieldId: String,
    val constraint: String,
    val provenance: String,
  )

  final class SuppressedFilter private[BeautyQSearchResponseGen2Projector] (
    val fieldId: String,
    val constraint: String,
    val provenance: String,
    val reason: String,
  )

  final class FacetBucket private[BeautyQSearchResponseGen2Projector] (
    val key: Option[String],
    val id: Option[String],
    val count: Long,
    val docCountErrorUpperBound: Option[Long],
  )

  final class FacetSummary private[BeautyQSearchResponseGen2Projector] (
    val id: String,
    val kind: String,
    val buckets: Vector[FacetBucket],
    val sumOtherDocCount: Option[Long],
    val precision: String,
  )

  final class GroupSummary private[BeautyQSearchResponseGen2Projector] (
    val id: String,
    val buckets: Vector[GroupBucket],
    val precision: String,
  )

  final class GroupBucket private[BeautyQSearchResponseGen2Projector] (
    val key: String,
    val matchingDocumentCount: Long,
    val representativeId: String,
    val representativeScore: BigDecimal,
  )

  final class ProviderCarouselItem private[BeautyQSearchResponseGen2Projector] (
    val masterId: String,
    val masterName: String,
    val masterLocationId: String,
    val locationName: String,
    val address: String,
    val matchingVariantCount: Long,
    val representativeVariantId: String,
    val bestScore: BigDecimal,
    val distanceMeters: Option[BigDecimal],
  )

  final class ServiceIntentCarouselItem private[BeautyQSearchResponseGen2Projector] (
    val serviceId: String,
    val serviceName: String,
    val categoryId: String,
    val categoryName: String,
    val matchingVariantCount: Long,
    val representativeVariantId: String,
    val bestScore: BigDecimal,
  )

  final class Warning private[BeautyQSearchResponseGen2Projector] (
    val code: String,
    val message: String,
  )

  final class Response private[BeautyQSearchResponseGen2Projector] (
    val hits: Vector[Hit],
    val totalHits: Long,
    val totalRelation: String,
    val facets: Vector[FacetSummary],
    val groups: Vector[GroupSummary],
    val providerCarousel: Vector[ProviderCarouselItem],
    val serviceIntentCarousel: Vector[ServiceIntentCarouselItem],
    val appliedFilters: Vector[AppliedFilter],
    val suppressedFilters: Vector[SuppressedFilter],
    val nextCursor: Option[String],
    val supplementCount: Int,
    val supplementStatus: BeautyQSupplementStatus,
    val supplementStatusCode: String,
    val ineligibilityReason: Option[String],
    val degradationReason: Option[String],
    val servingMode: String,
    val restartRequired: Boolean,
    val warnings: Vector[Warning],
    val diagnostics: ElasticsearchResponseDiagnostics,
  )

  def project(
    result: BeautyQSearchOrchestrator.Result,
    startupStatus: StartupServingStatus,
  ): Either[BeautyQSearchResponseProjectionError, BeautyQSearchResponseGen2] = {
    projectInternal(result, startupStatus.servingMode.modeCode, startupStatus.restartRequired, deriveWarnings(result, startupStatus))
  }

  def projectWithoutStatus(
    result: BeautyQSearchOrchestrator.Result,
  ): Either[BeautyQSearchResponseProjectionError, BeautyQSearchResponseGen2] = {
    projectInternal(result, BeautyQServingMode.FullSearch.modeCode, restartRequired = false, Vector.empty)
  }

  private def projectInternal(
    result: BeautyQSearchOrchestrator.Result,
    servingModeCode: String,
    restartRequired: Boolean,
    warnings: Vector[Warning],
  ): Either[BeautyQSearchResponseProjectionError, BeautyQSearchResponseGen2] = {
    BeautyQElasticsearchSearchResult.project(result.baselineResult)
      .left.map(BeautyQSearchResponseProjectionError.Baseline.apply)
      .flatMap { projected =>
        val baselineHits = projected.hits.map { hit =>
          new Hit(
            id = hit.id.toString,
            score = hit.score,
            origin = BeautyQSearchHitOrigin.ElasticsearchBaseline,
            source = hit.source,
          )
        }
        val supplementHits = result.appendedCandidates.map { candidate =>
          new Hit(
            id = candidate.id.toString,
            score = BigDecimal(candidate.score),
            origin = BeautyQSearchHitOrigin.QdrantSupplement,
            source = sourceOf(candidate.document),
          )
        }
        val groups = result.baselineResult.groupResults.map(group => new GroupSummary(
          group.id.value,
          group.buckets.map(bucket => new GroupBucket(bucket.canonicalKey, bucket.matchingDocumentCount, bucket.representative.id.toString, bucket.representative.score)),
          groupPrecision(group.precision),
        ))
        val providerCarousel = projected.providerCarousel.map(item => new ProviderCarouselItem(
          item.masterId.toString,
          item.masterName,
          item.masterLocationId.toString,
          item.locationName,
          item.address,
          item.matchingVariantCount,
          item.representativeVariantId.toString,
          item.bestScore,
          item.distanceMeters,
        ))
        val serviceIntentCarousel = projected.serviceIntentCarousel.map(item => new ServiceIntentCarouselItem(
          item.serviceId.toString,
          item.serviceName,
          item.categoryId.toString,
          item.categoryName,
          item.matchingVariantCount,
          item.representativeVariantId.toString,
          item.bestScore,
        ))
        val resolvedWarnings = warnings
        facets(projected, result.evaluation.compiled.plan.facets).map { facetSummaries =>
          new Response(
            hits = baselineHits ++ supplementHits,
            totalHits = projected.totalHits,
            totalRelation = projected.totalRelation,
            facets = facetSummaries,
            groups = groups,
            providerCarousel = providerCarousel,
            serviceIntentCarousel = serviceIntentCarousel,
            appliedFilters = result.evaluation.compiled.plan.appliedFilters.map { applied =>
              new AppliedFilter(
                fieldId(applied.source.constraint),
                PlannedAlgebraTrace.constraint(applied.source.constraint),
                provenance(applied.source.provenance),
              )
            },
            suppressedFilters = result.evaluation.compiled.plan.diagnostics.suppressedFilters.map { suppressed =>
              new SuppressedFilter(
                fieldId(suppressed.source.constraint),
                PlannedAlgebraTrace.constraint(suppressed.source.constraint),
                provenance(suppressed.source.provenance),
                suppressionReason(suppressed.reason),
              )
            },
            nextCursor = projected.nextCursor.map(_.opaqueValue),
            supplementCount = result.supplementCount,
            supplementStatus = result.status,
            supplementStatusCode = result.statusCode,
            ineligibilityReason = result.ineligibilityReason.map(_.stableCode),
            degradationReason = result.degradationReason.map(_.reasonCode),
            servingMode = servingModeCode,
            restartRequired = restartRequired,
            warnings = resolvedWarnings,
            diagnostics = projected.diagnostics,
          )
        }
      }
  }

  private def deriveWarnings(
    result: BeautyQSearchOrchestrator.Result,
    startupStatus: StartupServingStatus,
  ): Vector[Warning] =
    if (startupStatus.supplementReady) {
      result.degradationReason match {
        case Some(reason) =>
          Vector(new Warning(
            reason.reasonCode,
            "Qdrant supplement failed for this request; the complete Elasticsearch baseline was returned",
          ))
        case None =>
          Vector.empty
      }
    } else {
      startupStatus.reason match {
        case Some(reason) =>
          Vector(new Warning(reason.code, reason.message))
        case None =>
          Vector.empty
      }
    }

  private def provenance(value: ConstraintProvenance): String = value match {
    case ConstraintProvenance.ExplicitUi       => "explicit-ui"
    case ConstraintProvenance.FacetSelection(_) => "facet-selection"
    case ConstraintProvenance.ParsedHard       => "parsed-hard"
    case ConstraintProvenance.ParsedSoft       => "parsed-soft"
    case ConstraintProvenance.SystemDefault    => "system-default"
  }

  private def suppressionReason(value: SuppressionReason): String = value match {
    case SuppressionReason.OverriddenByHigherPrecedence => "overridden-by-higher-precedence"
    case SuppressionReason.EquivalentDuplicate          => "equivalent-duplicate"
  }

  private def facets(
    projected: BeautyQElasticsearchSearchResult,
    requests: Vector[leaderboard.search.gen2.contract.FacetRequest[VariantSearchDocumentGen2]],
  ): Either[BeautyQSearchResponseProjectionError, Vector[BeautyQFacetSummaryGen2]] =
    requests.foldLeft[Either[BeautyQSearchResponseProjectionError, Vector[BeautyQFacetSummaryGen2]]](Right(Vector.empty)) {
      case (Left(error), _) => Left(error)
      case (Right(done), request) => facetSummary(projected, request).map(value => done :+ value)
    }

  private def facetSummary(
    projected: BeautyQElasticsearchSearchResult,
    request: leaderboard.search.gen2.contract.FacetRequest[VariantSearchDocumentGen2],
  ): Either[BeautyQSearchResponseProjectionError, BeautyQFacetSummaryGen2] = request match {
    case terms: leaderboard.search.gen2.contract.FacetRequest.Terms[VariantSearchDocumentGen2, ?] =>
      projected.baseline.termsFacet(terms.id, terms.field) match {
        case Some(result) => Right(new FacetSummary(
          terms.id.value,
          "terms",
          result.buckets.map(bucket => new FacetBucket(Some(bucket.canonicalKey), None, bucket.count, bucket.docCountErrorUpperBound)),
          Some(result.sumOtherDocCount),
          termsPrecision(result.precision),
        ))
        case None => Left(BeautyQSearchResponseProjectionError.MissingFacet(terms.id.value))
      }
    case number: leaderboard.search.gen2.contract.FacetRequest.NumberRange[VariantSearchDocumentGen2, ?] =>
      projected.baseline.facets.collectFirst { case value: ElasticsearchFacetResult.NumberRange[VariantSearchDocumentGen2] if value.id == number.id => value.result } match {
        case Some(result) => Right(new FacetSummary(number.id.value, "number-range", result.buckets.map(bucket => new FacetBucket(None, Some(bucket.id.value), bucket.count, None)), None, "exact"))
        case None => Left(BeautyQSearchResponseProjectionError.MissingFacet(number.id.value))
      }
    case interval: leaderboard.search.gen2.contract.FacetRequest.IntervalOverlap[VariantSearchDocumentGen2, ?] =>
      projected.baseline.facets.collectFirst { case value: ElasticsearchFacetResult.IntervalOverlap[VariantSearchDocumentGen2] if value.id == interval.id => value.result } match {
        case Some(result) => Right(new FacetSummary(interval.id.value, "interval-overlap", result.buckets.map(bucket => new FacetBucket(None, Some(bucket.id.value), bucket.count, None)), None, "exact"))
        case None => Left(BeautyQSearchResponseProjectionError.MissingFacet(interval.id.value))
      }
  }

  private def termsPrecision(value: ElasticsearchTermsFacetPrecision): String = value match {
    case ElasticsearchTermsFacetPrecision.Exact => "exact"
    case ElasticsearchTermsFacetPrecision.Bounded(upperBound) => s"bounded:$upperBound"
    case ElasticsearchTermsFacetPrecision.Unknown => "unknown"
  }

  private def groupPrecision(value: leaderboard.search.gen2.elasticsearch.ElasticsearchGroupPrecision): String = value match {
    case leaderboard.search.gen2.elasticsearch.ElasticsearchGroupPrecision.Exact => "exact"
  }

  private def fieldId(constraint: leaderboard.search.gen2.contract.PlannedConstraint[VariantSearchDocumentGen2]): String = constraint match {
    case value: leaderboard.search.gen2.contract.PlannedConstraint.Terms[?, ?] => value.field.id.value
    case value: leaderboard.search.gen2.contract.PlannedConstraint.NumberRange[?, ?] => value.field.id.value
    case value: leaderboard.search.gen2.contract.PlannedConstraint.IntervalOverlap[?, ?] => value.from.id.value
    case value: leaderboard.search.gen2.contract.PlannedConstraint.GeoDistanceFilter[?] => value.field.id.value
  }

  private def sourceOf(document: VariantSearchDocumentGen2): JsonObject =
    JsonObject.fromIterable(
      Vector(
        "variantId" -> Json.fromString(document.variantId.toString),
        "masterServiceOfferId" -> Json.fromString(document.masterServiceOfferId.toString),
        "masterLocationId" -> Json.fromString(document.masterLocationId.toString),
        "masterId" -> Json.fromString(document.masterId.toString),
        "serviceId" -> Json.fromString(document.serviceId.toString),
        "serviceCode" -> Json.fromString(document.serviceCode.toString),
        "categoryId" -> Json.fromString(document.categoryId.toString),
        "categoryCode" -> Json.fromString(document.categoryCode.toString),
        "serviceName" -> Json.fromString(document.serviceName),
        "categoryName" -> Json.fromString(document.categoryName),
        "masterName" -> Json.fromString(document.masterName),
        "locationName" -> Json.fromString(document.locationName),
        "address" -> Json.fromString(document.address),
        "lat" -> Json.fromBigDecimal(document.lat),
        "lon" -> Json.fromBigDecimal(document.lon),
        "priceFrom" -> Json.fromBigDecimal(document.priceFrom),
        "priceTo" -> Json.fromBigDecimal(document.priceTo),
        "durationMin" -> Json.fromInt(document.durationMin),
        "enumAttributes" -> Json.obj(document.enumAttributes.toVector.map { case (key, value) => key -> Json.fromString(value) }*),
        "booleanAttributes" -> Json.obj(document.booleanAttributes.toVector.map { case (key, value) => key -> Json.fromBoolean(value) }*),
        "intAttributes" -> Json.obj(document.intAttributes.toVector.map { case (key, value) => key -> Json.fromInt(value) }*),
        "bigDecimalAttributes" -> Json.obj(document.bigDecimalAttributes.toVector.map { case (key, value) => key -> Json.fromBigDecimal(value) }*),
        "allText" -> Json.fromString(document.allText),
        "serviceText" -> Json.fromString(document.serviceText),
        "attributeText" -> Json.fromString(document.attributeText),
        "providerText" -> Json.fromString(document.providerText),
        "locationText" -> Json.fromString(document.locationText),
      )
    )
}
