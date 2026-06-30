package leaderboard.search.interpreter

import leaderboard.model.QueryFailure
import leaderboard.search.*
import leaderboard.search.document.VariantSearchDocument
import leaderboard.search.dsl.*
import leaderboard.search.interpreter.SearchSpecSupport.ScoredDocument

object SearchResponseAssembler {
  private val Presentation = BeautyQSearchPresentation

  def assemble(
    spec: BeautySearchSpec,
    input: UserSearchInput,
    intent: ParsedSearchIntent,
    scoredDocuments: List[ScoredDocument],
  ): Either[QueryFailure, BeautySearchResponse] = {
    val sortedDocuments = sortDocuments(scoredDocuments)
    for {
      variantLimit <- Presentation.variantLimit(spec.carouselSpec)
      providerLimit <- Presentation.providerLimit(spec.carouselSpec)
      serviceIntentLimit <- Presentation.serviceIntentLimit(spec.carouselSpec)
      providerCarousel <- buildProviderCarousel(spec, sortedDocuments)
      serviceIntentCarousel <- buildServiceIntentCarousel(spec, sortedDocuments)
      facets <- buildFacets(spec, sortedDocuments)
      inferredFilters <- buildInferredFilters(spec, intent, facets, sortedDocuments.size)
    } yield BeautySearchResponse(
      variantCarousel = sortedDocuments.take(math.min(input.limit, variantLimit)).map(toVariantResult),
      providerCarousel = providerCarousel.take(providerLimit),
      serviceIntentCarousel = serviceIntentCarousel.take(serviceIntentLimit),
      facets = facets,
      inferredFilters = inferredFilters,
    )
  }

  private def buildProviderCarousel(
    spec: BeautySearchSpec,
    scoredDocuments: List[ScoredDocument],
  ): Either[QueryFailure, List[ProviderSearchResult]] = {
    for {
      providerGroupField <- Presentation.providerGroupField(spec.carouselSpec)
      textScoreWeight <- Presentation.textScoreWeight(spec.carouselSpec.ranking)
      providerMatchingVariantCountWeight <- Presentation.providerMatchingVariantCountWeight(spec.carouselSpec.ranking)
      providerDistanceWeight <- Presentation.providerDistanceWeight(spec.carouselSpec.ranking)
      grouped <- groupByField(spec, scoredDocuments, providerGroupField)
    } yield {
        grouped.values.toList
          .map { group =>
            val sorted = sortDocuments(group)
            val head = sorted.head
            val minDistance = group.flatMap(_.distanceKm).sorted.headOption
            val groupScore = head.totalScore * textScoreWeight +
              group.size.toDouble * providerMatchingVariantCountWeight +
              minDistance.map(distance => proximityScore(distance) * providerDistanceWeight).getOrElse(0.0)
            ProviderSearchResult(
              masterId = head.document.masterId,
              masterName = head.document.masterName,
              masterLocationId = head.document.masterLocationId,
              locationName = head.document.locationName,
              address = head.document.address,
              matchingVariantCount = group.size,
              sampleMatchingVariantIds = sorted.take(3).map(_.document.variantId),
              bestScore = groupScore,
              distanceKm = minDistance,
            )
          }
          .sortBy(result => (-result.bestScore, result.distanceKm.getOrElse(BigDecimal("999999")), result.masterLocationId.toString))
    }
  }

  private def buildServiceIntentCarousel(
    spec: BeautySearchSpec,
    scoredDocuments: List[ScoredDocument],
  ): Either[QueryFailure, List[ServiceIntentSearchResult]] = {
    for {
      serviceIntentGroupField <- Presentation.serviceIntentGroupField(spec.carouselSpec)
      textScoreWeight <- Presentation.textScoreWeight(spec.carouselSpec.ranking)
      serviceBoostWeight <- Presentation.serviceBoostWeight(spec.carouselSpec.ranking)
      grouped <- groupByField(spec, scoredDocuments, serviceIntentGroupField)
    } yield {
        grouped.values.toList
          .map { group =>
            val sorted = sortDocuments(group)
            val head = sorted.head
            val groupScore = head.totalScore * textScoreWeight +
              group.size.toDouble * serviceBoostWeight
            ServiceIntentSearchResult(
              serviceId = head.document.serviceId,
              serviceName = head.document.serviceName,
              categoryId = head.document.categoryId,
              categoryName = head.document.categoryName,
              matchingVariantCount = group.size,
              bestScore = groupScore,
            )
          }
          .sortBy(result => (-result.bestScore, -result.matchingVariantCount, result.serviceId.toString))
    }
  }

  private def buildFacets(
    spec: BeautySearchSpec,
    scoredDocuments: List[ScoredDocument],
  ): Either[QueryFailure, List[BeautySearchFacet]] = {
    if (!spec.facetSpec.enabled) {
      Right(Nil)
    } else {
      spec.facetSpec.fields.foldRight[Either[QueryFailure, List[BeautySearchFacet]]](Right(Nil)) {
        (facetField, acc) =>
          for {
            tail <- acc
            values <- facetValues(spec, facetField, scoredDocuments)
          } yield BeautySearchFacet(facetField.path, values) :: tail
      }
    }
  }

  private def buildInferredFilters(
    spec: BeautySearchSpec,
    intent: ParsedSearchIntent,
    facets: List[BeautySearchFacet],
    totalDocumentCount: Int,
  ): Either[QueryFailure, List[BeautySearchAppliedFilter]] = {
    val explicitFilters = intent.explicitConstraints.map(constraint => BeautySearchAppliedFilter(constraint, explicit = true))
    if (totalDocumentCount == 0 || !spec.facetSpec.enabled) {
      Right(explicitFilters)
    } else {
      val inferredEither = facets.foldRight[Either[QueryFailure, List[BeautySearchAppliedFilter]]](Right(Nil)) {
        (facet, acc) =>
          for {
            tail <- acc
            next <- inferFacet(spec, facet, intent.explicitConstraints, totalDocumentCount)
          } yield next.toList ++ tail
      }
      inferredEither.map(explicitFilters ++ _)
    }
  }

  private def inferFacet(
    spec: BeautySearchSpec,
    facet: BeautySearchFacet,
    explicitConstraints: List[SearchConstraint],
    totalDocumentCount: Int,
  ): Either[QueryFailure, Option[BeautySearchAppliedFilter]] = {
    spec.facetSpec.fields.find(_.path == facet.fieldPath) match {
      case Some(facetField) if facetField.inferable && facet.values.nonEmpty =>
        val sorted = facet.values.sortBy(value => (-value.count, value.value))
        val dominant = sorted.head
        val dominance = BigDecimal(dominant.count) / BigDecimal(totalDocumentCount)
        if (dominant.count < spec.facetSpec.inferredFilterMinCount || dominance < spec.facetSpec.inferredFilterDominanceThreshold) {
          Right(None)
        } else {
          SearchSpecSupport.facetConstraint(spec, facetField, dominant.value).map {
            constraint =>
              if (explicitConstraints.contains(constraint)) {
                None
              } else {
                Some(BeautySearchAppliedFilter(constraint, explicit = false))
              }
          }
        }
      case _ =>
        Right(None)
    }
  }

  private def facetValues(
    spec: BeautySearchSpec,
    facetField: FacetField[VariantSearchDocument],
    scoredDocuments: List[ScoredDocument],
  ): Either[QueryFailure, List[BeautySearchFacetValue]] = {
    facetField.mode match {
      case FacetFieldMode.Terms =>
        scoredDocuments.foldLeft[Either[QueryFailure, Map[String, Int]]](Right(Map.empty)) {
          case (acc, scoredDocument) =>
            for {
              current <- acc
              value <- SearchSpecSupport.valueByField(spec, scoredDocument.document, facetField.field)
            } yield {
              value match {
                case Some(found) =>
                  current.updatedWith(found.render) {
                    case Some(count) => Some(count + 1)
                    case None => Some(1)
                  }
                case None => current
              }
            }
        }.map(_.toList.sortBy { case (value, count) => (-count, value) }.take(facetField.limit).map(BeautySearchFacetValue.apply))
      case FacetFieldMode.Ranges(buckets) =>
        buckets.foldRight[Either[QueryFailure, List[BeautySearchFacetValue]]](Right(Nil)) {
          (bucket, acc) =>
            for {
              tail <- acc
              count <- countBucket(spec, facetField.field, bucket, scoredDocuments)
            } yield {
              if (count == 0) tail else BeautySearchFacetValue(bucket.key, count) :: tail
            }
        }
    }
  }

  private def countBucket(
    spec: BeautySearchSpec,
    field: SearchField[VariantSearchDocument],
    bucket: FacetRangeBucket,
    scoredDocuments: List[ScoredDocument],
  ): Either[QueryFailure, Int] =
    scoredDocuments.foldLeft[Either[QueryFailure, Int]](Right(0)) {
      case (acc, scoredDocument) =>
        for {
          current <- acc
          value <- SearchSpecSupport.valueByField(spec, scoredDocument.document, field)
        } yield {
          val matches = value.exists {
            case SearchValue.Integer(found) => rangeMatches(BigDecimal(found), bucket)
            case SearchValue.Decimal(found) => rangeMatches(found, bucket)
            case _ => false
          }
          if (matches) current + 1 else current
        }
    }

  private def rangeMatches(value: BigDecimal, bucket: FacetRangeBucket): Boolean = {
    val minOk = bucket.min.forall(value >= _)
    val maxOk = bucket.max.forall(value <= _)
    minOk && maxOk
  }

  private def groupByField(
    spec: BeautySearchSpec,
    scoredDocuments: List[ScoredDocument],
    field: SearchField[VariantSearchDocument],
  ): Either[QueryFailure, Map[String, List[ScoredDocument]]] =
    scoredDocuments.foldLeft[Either[QueryFailure, Map[String, List[ScoredDocument]]]](Right(Map.empty)) {
      case (acc, scoredDocument) =>
        for {
          current <- acc
          key <- SearchSpecSupport.groupValue(spec, scoredDocument.document, field)
        } yield current.updatedWith(key) {
          case Some(existing) => Some(scoredDocument :: existing)
          case None => Some(List(scoredDocument))
        }
    }

  private def sortDocuments(scoredDocuments: List[ScoredDocument]): List[ScoredDocument] =
    scoredDocuments.sortBy(scored => (-scored.totalScore, scored.distanceKm.getOrElse(BigDecimal("999999")), scored.document.priceFrom, scored.document.variantId.toString))

  private def proximityScore(distanceKm: BigDecimal): Double =
    1.0d / (1.0d + distanceKm.toDouble)

  private def toVariantResult(scoredDocument: ScoredDocument): VariantSearchResult = {
    val document = scoredDocument.document
    VariantSearchResult(
      variantId = document.variantId,
      masterServiceOfferId = document.masterServiceOfferId,
      masterLocationId = document.masterLocationId,
      masterId = document.masterId,
      serviceId = document.serviceId,
      categoryId = document.categoryId,
      serviceName = document.serviceName,
      categoryName = document.categoryName,
      masterName = document.masterName,
      locationName = document.locationName,
      address = document.address,
      lat = document.lat,
      lon = document.lon,
      priceFrom = document.priceFrom,
      priceTo = document.priceTo,
      durationMin = document.durationMin,
      enumAttributes = document.enumAttributes,
      booleanAttributes = document.booleanAttributes,
      intAttributes = document.intAttributes,
      bigDecimalAttributes = document.bigDecimalAttributes,
      score = scoredDocument.totalScore,
      distanceKm = scoredDocument.distanceKm,
    )
  }
}
