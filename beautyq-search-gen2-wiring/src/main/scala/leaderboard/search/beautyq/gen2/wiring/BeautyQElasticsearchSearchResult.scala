package leaderboard.search.beautyq.gen2.wiring

import leaderboard.model.*
import leaderboard.model.Category.CategoryId
import leaderboard.search.beautyq.gen2.contract.*
import leaderboard.search.gen2.contract.GroupId
import leaderboard.search.gen2.elasticsearch.*

final class BeautyQProviderCarouselItem private[beautyq] (
  val masterId: MasterId,
  val masterName: String,
  val masterLocationId: MasterLocationId,
  val locationName: String,
  val address: String,
  val matchingVariantCount: Long,
  val representativeVariantId: MasterServiceOfferVariantId,
  val bestScore: BigDecimal,
  val distanceMeters: Option[BigDecimal],
  val precision: ElasticsearchGroupPrecision,
)

final class BeautyQServiceIntentCarouselItem private[beautyq] (
  val serviceId: ServiceId,
  val serviceName: String,
  val categoryId: CategoryId,
  val categoryName: String,
  val matchingVariantCount: Long,
  val representativeVariantId: MasterServiceOfferVariantId,
  val bestScore: BigDecimal,
  val precision: ElasticsearchGroupPrecision,
)

sealed trait BeautyQCarouselProjectionError

object BeautyQCarouselProjectionError {
  final case class MissingGroup(id: GroupId) extends BeautyQCarouselProjectionError
  final case class InvalidGroup(id: GroupId, message: String) extends BeautyQCarouselProjectionError
}

final class BeautyQElasticsearchSearchResult private[beautyq] (
  val baseline: ElasticsearchFullSearchResult[VariantSearchDocumentGen2, MasterServiceOfferVariantId],
  val providerCarousel: Vector[BeautyQProviderCarouselItem],
  val serviceIntentCarousel: Vector[BeautyQServiceIntentCarouselItem],
) {
  def hits: Vector[ElasticsearchDocumentHit[MasterServiceOfferVariantId]] = baseline.hits
  def totalHits: Long = baseline.totalHits
  def facets: Vector[ElasticsearchFacetResult[VariantSearchDocumentGen2]] = baseline.facets
  def diagnostics: ElasticsearchResponseDiagnostics = baseline.diagnostics
  def nextCursor = baseline.nextCursor
}

object BeautyQElasticsearchSearchResult {
  def project(
    baseline: ElasticsearchFullSearchResult[VariantSearchDocumentGen2, MasterServiceOfferVariantId]
  ): Either[BeautyQCarouselProjectionError, BeautyQElasticsearchSearchResult] =
    for {
      provider <- projectProvider(baseline)
      service <- projectService(baseline)
    } yield new BeautyQElasticsearchSearchResult(baseline, provider, service)

  private def projectProvider(
    baseline: ElasticsearchFullSearchResult[VariantSearchDocumentGen2, MasterServiceOfferVariantId]
  ): Either[BeautyQCarouselProjectionError, Vector[BeautyQProviderCarouselItem]] =
    for {
      group <- baseline.group(BeautyQSearchPlanPolicy.ProviderGroupId).toRight(BeautyQCarouselProjectionError.MissingGroup(BeautyQSearchPlanPolicy.ProviderGroupId))
      typed <- group.typedFor(BeautyQSearchPlanPolicy.ProviderGroupId, BeautyQSearchDeclarations.variants.Fields.masterLocationId).toRight(BeautyQCarouselProjectionError.MissingGroup(BeautyQSearchPlanPolicy.ProviderGroupId))
      items <- sequence(typed.buckets.map { bucket =>
        for {
          masterId <- bucket.representative.requiredValue(BeautyQSearchDeclarations.variants.Fields.masterId).left.map(error => BeautyQCarouselProjectionError.InvalidGroup(BeautyQSearchPlanPolicy.ProviderGroupId, error.toString))
          masterName <- bucket.representative.requiredValue(BeautyQSearchDeclarations.variants.Fields.masterName).left.map(error => BeautyQCarouselProjectionError.InvalidGroup(BeautyQSearchPlanPolicy.ProviderGroupId, error.toString))
          masterLocationId <- bucket.key(BeautyQSearchDeclarations.variants.Fields.masterLocationId).left.map(error => BeautyQCarouselProjectionError.InvalidGroup(BeautyQSearchPlanPolicy.ProviderGroupId, error.toString))
          locationName <- bucket.representative.requiredValue(BeautyQSearchDeclarations.variants.Fields.locationName).left.map(error => BeautyQCarouselProjectionError.InvalidGroup(BeautyQSearchPlanPolicy.ProviderGroupId, error.toString))
          address <- bucket.representative.requiredValue(BeautyQSearchDeclarations.variants.Fields.address).left.map(error => BeautyQCarouselProjectionError.InvalidGroup(BeautyQSearchPlanPolicy.ProviderGroupId, error.toString))
          bestScore <- bucket.bestScore(BeautyQSearchPlanPolicy.BestScoreMetricId).toRight(BeautyQCarouselProjectionError.InvalidGroup(BeautyQSearchPlanPolicy.ProviderGroupId, "missing best-score metric"))
        } yield new BeautyQProviderCarouselItem(masterId, masterName, masterLocationId, locationName, address, bucket.matchingDocumentCount, bucket.representative.id, bestScore, bucket.minGeoDistanceMeters(BeautyQSearchPlanPolicy.MinDistanceMetricId), typed.precision)
      })
    } yield items

  private def projectService(
    baseline: ElasticsearchFullSearchResult[VariantSearchDocumentGen2, MasterServiceOfferVariantId]
  ): Either[BeautyQCarouselProjectionError, Vector[BeautyQServiceIntentCarouselItem]] =
    for {
      group <- baseline.group(BeautyQSearchPlanPolicy.ServiceGroupId).toRight(BeautyQCarouselProjectionError.MissingGroup(BeautyQSearchPlanPolicy.ServiceGroupId))
      typed <- group.typedFor(BeautyQSearchPlanPolicy.ServiceGroupId, BeautyQSearchDeclarations.variants.Fields.serviceId).toRight(BeautyQCarouselProjectionError.MissingGroup(BeautyQSearchPlanPolicy.ServiceGroupId))
      items <- sequence(typed.buckets.map { bucket =>
        for {
          serviceId <- bucket.key(BeautyQSearchDeclarations.variants.Fields.serviceId).left.map(error => BeautyQCarouselProjectionError.InvalidGroup(BeautyQSearchPlanPolicy.ServiceGroupId, error.toString))
          serviceName <- bucket.representative.requiredValue(BeautyQSearchDeclarations.variants.Fields.serviceName).left.map(error => BeautyQCarouselProjectionError.InvalidGroup(BeautyQSearchPlanPolicy.ServiceGroupId, error.toString))
          categoryId <- bucket.representative.requiredValue(BeautyQSearchDeclarations.variants.Fields.categoryId).left.map(error => BeautyQCarouselProjectionError.InvalidGroup(BeautyQSearchPlanPolicy.ServiceGroupId, error.toString))
          categoryName <- bucket.representative.requiredValue(BeautyQSearchDeclarations.variants.Fields.categoryName).left.map(error => BeautyQCarouselProjectionError.InvalidGroup(BeautyQSearchPlanPolicy.ServiceGroupId, error.toString))
          bestScore <- bucket.bestScore(BeautyQSearchPlanPolicy.BestScoreMetricId).toRight(BeautyQCarouselProjectionError.InvalidGroup(BeautyQSearchPlanPolicy.ServiceGroupId, "missing best-score metric"))
        } yield new BeautyQServiceIntentCarouselItem(serviceId, serviceName, categoryId, categoryName, bucket.matchingDocumentCount, bucket.representative.id, bestScore, typed.precision)
      })
    } yield items

  private def sequence[A](values: Vector[Either[BeautyQCarouselProjectionError, A]]): Either[BeautyQCarouselProjectionError, Vector[A]] =
    values.foldLeft[Either[BeautyQCarouselProjectionError, Vector[A]]](Right(Vector.empty)) { (acc, next) =>
      acc.flatMap(done => next.map(done :+ _))
    }
}
