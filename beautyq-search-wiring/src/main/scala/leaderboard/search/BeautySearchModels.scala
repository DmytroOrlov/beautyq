package leaderboard.search

import io.circe.{Codec, Decoder, Encoder}
import io.circe.generic.semiauto
import izumi.functional.bio.Error2
import leaderboard.model.*
import leaderboard.search.parser.BeautySearchIntentParser
import leaderboard.search.dsl.SearchConstraint

import scala.annotation.nowarn

final case class UserSearchInput(
  query: String,
  userLat: Option[BigDecimal],
  userLon: Option[BigDecimal],
  limit: Int = 10,
)

object UserSearchInput {
  implicit val codec: Codec.AsObject[UserSearchInput] = semiauto.deriveCodec
}

final case class ParsedSearchIntent(
  originalQuery: String,
  normalizedTokens: List[String],
  explicitConstraints: List[SearchConstraint],
  softBoosts: List[SearchConstraint],
  remainingText: String,
)

object ParsedSearchIntent {
  implicit val codec: Codec.AsObject[ParsedSearchIntent] = semiauto.deriveCodec
}

final case class BeautySearchAppliedFilter(
  constraint: SearchConstraint,
  explicit: Boolean,
)

object BeautySearchAppliedFilter {
  implicit val codec: Codec.AsObject[BeautySearchAppliedFilter] = semiauto.deriveCodec
}

final case class BeautySearchFacetValue(
  value: String,
  count: Int,
)

object BeautySearchFacetValue {
  implicit val codec: Codec.AsObject[BeautySearchFacetValue] = semiauto.deriveCodec
}

final case class BeautySearchFacet(
  fieldPath: String,
  values: List[BeautySearchFacetValue],
)

object BeautySearchFacet {
  implicit val codec: Codec.AsObject[BeautySearchFacet] = semiauto.deriveCodec
}

sealed trait BeautySearchExecutionMode extends Product with Serializable {
  def label: String
}

object BeautySearchExecutionMode {
  case object EsOnly extends BeautySearchExecutionMode {
    override val label: String = "es_only"
  }
  case object EsPlusQdrantSupplement extends BeautySearchExecutionMode {
    override val label: String = "es_plus_qdrant_supplement"
  }

  implicit val encoder: Encoder[BeautySearchExecutionMode] =
    Encoder.encodeString.contramap(_.label)
  implicit val decoder: Decoder[BeautySearchExecutionMode] =
    Decoder.decodeString.emap {
      case EsOnly.label                 => Right(EsOnly)
      case EsPlusQdrantSupplement.label => Right(EsPlusQdrantSupplement)
      case other                        => Left(s"Unknown BeautySearchExecutionMode: $other")
    }
}

sealed trait VariantResultOrigin extends Product with Serializable {
  def label: String
}

object VariantResultOrigin {
  case object EsBaseline extends VariantResultOrigin {
    override val label: String = "es_baseline"
  }
  case object QdrantSupplement extends VariantResultOrigin {
    override val label: String = "qdrant_supplement"
  }

  implicit val encoder: Encoder[VariantResultOrigin] =
    Encoder.encodeString.contramap(_.label)
  implicit val decoder: Decoder[VariantResultOrigin] =
    Decoder.decodeString.emap {
      case EsBaseline.label        => Right(EsBaseline)
      case QdrantSupplement.label  => Right(QdrantSupplement)
      case other                   => Left(s"Unknown VariantResultOrigin: $other")
    }
}

sealed trait QdrantSupplementStatus extends Product with Serializable {
  def label: String
}

object QdrantSupplementStatus {
  case object NotUsed extends QdrantSupplementStatus {
    override val label: String = "not_used"
  }
  case object UsedNoAppend extends QdrantSupplementStatus {
    override val label: String = "used_no_append"
  }
  case object UsedWithAppend extends QdrantSupplementStatus {
    override val label: String = "used_with_append"
  }

  implicit val encoder: Encoder[QdrantSupplementStatus] =
    Encoder.encodeString.contramap(_.label)
  implicit val decoder: Decoder[QdrantSupplementStatus] =
    Decoder.decodeString.emap {
      case NotUsed.label        => Right(NotUsed)
      case UsedNoAppend.label   => Right(UsedNoAppend)
      case UsedWithAppend.label => Right(UsedWithAppend)
      case other                => Left(s"Unknown QdrantSupplementStatus: $other")
    }
}

sealed trait QdrantSupplementPolicyName extends Product with Serializable {
  def label: String
}

object QdrantSupplementPolicyName {
  case object None extends QdrantSupplementPolicyName {
    override val label: String = "none"
  }
  case object ExplicitConstraintsFilterPlusTop1 extends QdrantSupplementPolicyName {
    override val label: String = "explicit_constraints_filter_plus_top1"
  }

  implicit val encoder: Encoder[QdrantSupplementPolicyName] =
    Encoder.encodeString.contramap(_.label)
  implicit val decoder: Decoder[QdrantSupplementPolicyName] =
    Decoder.decodeString.emap {
      case None.label                              => Right(None)
      case ExplicitConstraintsFilterPlusTop1.label => Right(ExplicitConstraintsFilterPlusTop1)
      case other                                   => Left(s"Unknown QdrantSupplementPolicyName: $other")
    }
}

sealed trait QdrantSupplementContribution extends Product with Serializable {
  def label: String
}

object QdrantSupplementContribution {
  case object None extends QdrantSupplementContribution {
    override val label: String = "none"
  }
  case object QdrantOnlyVariantAppend extends QdrantSupplementContribution {
    override val label: String = "qdrant_only_variant_append"
  }

  implicit val encoder: Encoder[QdrantSupplementContribution] =
    Encoder.encodeString.contramap(_.label)
  implicit val decoder: Decoder[QdrantSupplementContribution] =
    Decoder.decodeString.emap {
      case None.label                    => Right(None)
      case QdrantOnlyVariantAppend.label => Right(QdrantOnlyVariantAppend)
      case other                         => Left(s"Unknown QdrantSupplementContribution: $other")
    }
}

final case class QdrantSupplementSummary(
  status: QdrantSupplementStatus,
  policy: QdrantSupplementPolicyName,
  appendedVariantIds: List[MasterServiceOfferVariantId],
  contribution: QdrantSupplementContribution,
)

object QdrantSupplementSummary {
  val notUsed: QdrantSupplementSummary =
    QdrantSupplementSummary(
      status = QdrantSupplementStatus.NotUsed,
      policy = QdrantSupplementPolicyName.None,
      appendedVariantIds = Nil,
      contribution = QdrantSupplementContribution.None,
    )

  def usedNoAppend(policy: QdrantSupplementPolicyName): QdrantSupplementSummary =
    QdrantSupplementSummary(
      status = QdrantSupplementStatus.UsedNoAppend,
      policy = policy,
      appendedVariantIds = Nil,
      contribution = QdrantSupplementContribution.None,
    )

  def usedWithAppend(policy: QdrantSupplementPolicyName, appendedVariantIds: List[MasterServiceOfferVariantId]): QdrantSupplementSummary =
    QdrantSupplementSummary(
      status = QdrantSupplementStatus.UsedWithAppend,
      policy = policy,
      appendedVariantIds = appendedVariantIds,
      contribution = QdrantSupplementContribution.QdrantOnlyVariantAppend,
    )

  implicit val codec: Codec.AsObject[QdrantSupplementSummary] = semiauto.deriveCodec
}

final case class VariantSearchResult(
  variantId: MasterServiceOfferVariantId,
  masterServiceOfferId: MasterServiceOfferId,
  masterLocationId: MasterLocationId,
  masterId: MasterId,
  serviceId: ServiceId,
  categoryId: Category.CategoryId,
  serviceName: String,
  categoryName: String,
  masterName: String,
  locationName: String,
  address: String,
  lat: BigDecimal,
  lon: BigDecimal,
  priceFrom: BigDecimal,
  priceTo: BigDecimal,
  durationMin: Int,
  enumAttributes: Map[String, String],
  booleanAttributes: Map[String, Boolean],
  intAttributes: Map[String, Int],
  bigDecimalAttributes: Map[String, BigDecimal],
  score: Double,
  distanceKm: Option[BigDecimal],
  resultOrigin: VariantResultOrigin = VariantResultOrigin.EsBaseline,
)

object VariantSearchResult {
  implicit val codec: Codec.AsObject[VariantSearchResult] = semiauto.deriveCodec
}

final case class ProviderSearchResult(
  masterId: MasterId,
  masterName: String,
  masterLocationId: MasterLocationId,
  locationName: String,
  address: String,
  matchingVariantCount: Int,
  sampleMatchingVariantIds: List[MasterServiceOfferVariantId],
  bestScore: Double,
  distanceKm: Option[BigDecimal],
)

object ProviderSearchResult {
  implicit val codec: Codec.AsObject[ProviderSearchResult] = semiauto.deriveCodec
}

final case class ServiceIntentSearchResult(
  serviceId: ServiceId,
  serviceName: String,
  categoryId: Category.CategoryId,
  categoryName: String,
  matchingVariantCount: Int,
  bestScore: Double,
)

object ServiceIntentSearchResult {
  implicit val codec: Codec.AsObject[ServiceIntentSearchResult] = semiauto.deriveCodec
}

final case class BeautySearchResponse(
  variantCarousel: List[VariantSearchResult],
  providerCarousel: List[ProviderSearchResult],
  serviceIntentCarousel: List[ServiceIntentSearchResult],
  facets: List[BeautySearchFacet],
  inferredFilters: List[BeautySearchAppliedFilter],
  executionMode: BeautySearchExecutionMode = BeautySearchExecutionMode.EsOnly,
  qdrantSupplement: QdrantSupplementSummary = QdrantSupplementSummary.notUsed,
)

object BeautySearchResponse {
  implicit val codec: Codec.AsObject[BeautySearchResponse] = semiauto.deriveCodec
}

trait BeautySearchBackend[F[_, _]] {
  def search(input: UserSearchInput, intent: ParsedSearchIntent): F[QueryFailure, BeautySearchResponse]
}

trait BeautySearchService[F[_, _]] {
  def search(input: UserSearchInput): F[QueryFailure, BeautySearchResponse]
}

object BeautySearchService {
  @nowarn("msg=unused")
  final class Impl[F[+_, +_]: Error2](
    parser: BeautySearchIntentParser,
    backend: BeautySearchBackend[F],
  ) extends BeautySearchService[F] {

    override def search(input: UserSearchInput): F[QueryFailure, BeautySearchResponse] =
      backend.search(input, parser.parse(input))
  }
}
