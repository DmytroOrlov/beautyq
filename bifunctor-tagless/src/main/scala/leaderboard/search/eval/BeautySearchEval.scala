package leaderboard.search.eval

import io.circe.{Decoder, DecodingFailure, HCursor}
import io.circe.parser.decode
import leaderboard.model.Category.CategoryId
import leaderboard.model.*
import leaderboard.search.*

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

final case class BeautySearchEvalLocation(
  label: String,
  lat: BigDecimal,
  lon: BigDecimal,
)

final case class EvalNumericRange(
  min: Option[BigDecimal] = None,
  max: Option[BigDecimal] = None,
)

final case class EvalConstraintBlock(
  servicesAny: List[String] = Nil,
  categoriesAny: List[String] = Nil,
  enumAttributes: Map[String, List[String]] = Map.empty,
  booleanAttributes: Map[String, Boolean] = Map.empty,
  intAttributes: Map[String, EvalNumericRange] = Map.empty,
  bigDecimalAttributes: Map[String, EvalNumericRange] = Map.empty,
  price: Option[EvalNumericRange] = None,
  duration: Option[EvalNumericRange] = None,
  nearUser: Option[Boolean] = None,
)

final case class EvalTopK(
  requiredInTopK: Option[Int] = None,
  preferredInTopK: Option[Int] = None,
  forbiddenNotInTopK: Option[Int] = None,
)

final case class EvalCarouselWeights(
  top1RequiredWeight: Option[Int] = None,
  top3RequiredWeight: Int = 0,
  top5PreferredWeight: Option[Int] = None,
  distancePreferenceWeight: Option[Int] = None,
  top10ForbiddenPenalty: Int = 0,
)

final case class EvalVariantExpectation(
  required: EvalConstraintBlock = EvalConstraintBlock(),
  preferred: EvalConstraintBlock = EvalConstraintBlock(),
  forbidden: EvalConstraintBlock = EvalConstraintBlock(),
  acceptableVariantIds: List[MasterServiceOfferVariantId] = Nil,
  forbiddenVariantIds: List[MasterServiceOfferVariantId] = Nil,
  topK: EvalTopK = EvalTopK(),
)

final case class EvalProviderExpectation(
  required: EvalConstraintBlock = EvalConstraintBlock(),
  preferred: EvalConstraintBlock = EvalConstraintBlock(),
  forbidden: EvalConstraintBlock = EvalConstraintBlock(),
  acceptableProviderLocationIds: List[MasterLocationId] = Nil,
  topK: EvalTopK = EvalTopK(),
)

final case class EvalServiceExpectation(
  required: EvalConstraintBlock = EvalConstraintBlock(),
  preferred: EvalConstraintBlock = EvalConstraintBlock(),
  forbidden: EvalConstraintBlock = EvalConstraintBlock(),
  acceptableServiceIds: List[ServiceId] = Nil,
  topK: EvalTopK = EvalTopK(),
)

final case class EvalScoring(
  variantCarousel: EvalCarouselWeights,
  providerCarousel: EvalCarouselWeights,
  serviceIntentCarousel: EvalCarouselWeights,
)

final case class BeautySearchEvalQuery(
  id: String,
  query: String,
  language: String,
  queryTypes: List[String],
  expectedVariantCarousel: EvalVariantExpectation,
  expectedProviderCarousel: EvalProviderExpectation,
  expectedServiceIntentCarousel: EvalServiceExpectation,
  scoring: EvalScoring,
)

final case class BeautySearchEvalSuite(
  testUserLocation: BeautySearchEvalLocation,
  queries: List[BeautySearchEvalQuery],
)

final case class BeautySearchEvalReport(
  queryId: String,
  query: String,
  score: Int,
  failedAssertions: List[String],
  topVariantIds: List[MasterServiceOfferVariantId],
  topProviderLocationIds: List[MasterLocationId],
  topServiceIds: List[ServiceId],
)

object BeautySearchEvalLoader {
  def load(path: Path): Either[QueryFailure, BeautySearchEvalSuite] =
    for {
      content <- read(path)
      suite <- decode[BeautySearchEvalSuite](content).left.map(error => QueryFailure.operation("decode-beautyq-search-eval", error.getMessage))
    } yield suite

  private def read(path: Path): Either[QueryFailure, String] =
    try {
      Right(Files.readString(path, StandardCharsets.UTF_8))
    } catch {
      case error: Throwable =>
        Left(QueryFailure.fromThrowable("read-beautyq-search-eval", error))
    }

  implicit val evalNumericRangeDecoder: Decoder[EvalNumericRange] = Decoder.instance { c =>
    for {
      min <- c.get[Option[BigDecimal]]("min")
      max <- c.get[Option[BigDecimal]]("max")
    } yield EvalNumericRange(min, max)
  }

  implicit val evalConstraintBlockDecoder: Decoder[EvalConstraintBlock] = Decoder.instance { c =>
    for {
      servicesAny <- c.getOrElse[List[String]]("servicesAny")(Nil)
      categoriesAny <- c.getOrElse[List[String]]("categoriesAny")(Nil)
      enumAttributes <- c.getOrElse[Map[String, List[String]]]("enumAttributes")(Map.empty)
      booleanAttributes <- c.getOrElse[Map[String, Boolean]]("booleanAttributes")(Map.empty)
      intAttributes <- c.getOrElse[Map[String, EvalNumericRange]]("intAttributes")(Map.empty)
      bigDecimalAttributes <- c.getOrElse[Map[String, EvalNumericRange]]("bigDecimalAttributes")(Map.empty)
      price <- c.get[Option[EvalNumericRange]]("price")
      duration <- c.get[Option[EvalNumericRange]]("duration")
      nearUser <- c.get[Option[Boolean]]("nearUser")
    } yield EvalConstraintBlock(servicesAny, categoriesAny, enumAttributes, booleanAttributes, intAttributes, bigDecimalAttributes, price, duration, nearUser)
  }

  implicit val evalTopKDecoder: Decoder[EvalTopK] = Decoder.instance { c =>
    for {
      requiredInTopK <- c.get[Option[Int]]("requiredInTopK")
      preferredInTopK <- c.get[Option[Int]]("preferredInTopK")
      forbiddenNotInTopK <- c.get[Option[Int]]("forbiddenNotInTopK")
    } yield EvalTopK(requiredInTopK, preferredInTopK, forbiddenNotInTopK)
  }

  implicit val evalCarouselWeightsDecoder: Decoder[EvalCarouselWeights] = Decoder.instance { c =>
    for {
      top1RequiredWeight <- c.get[Option[Int]]("top1RequiredWeight")
      top3RequiredWeight <- c.getOrElse[Int]("top3RequiredWeight")(0)
      top5PreferredWeight <- c.get[Option[Int]]("top5PreferredWeight")
      distancePreferenceWeight <- c.get[Option[Int]]("distancePreferenceWeight")
      top10ForbiddenPenalty <- c.getOrElse[Int]("top10ForbiddenPenalty")(0)
    } yield EvalCarouselWeights(top1RequiredWeight, top3RequiredWeight, top5PreferredWeight, distancePreferenceWeight, top10ForbiddenPenalty)
  }

  implicit val evalVariantExpectationDecoder: Decoder[EvalVariantExpectation] = Decoder.instance { c =>
    for {
      required <- c.getOrElse[EvalConstraintBlock]("required")(EvalConstraintBlock())
      preferred <- c.getOrElse[EvalConstraintBlock]("preferred")(EvalConstraintBlock())
      forbidden <- c.getOrElse[EvalConstraintBlock]("forbidden")(EvalConstraintBlock())
      acceptableVariantIds <- c.getOrElse[List[MasterServiceOfferVariantId]]("acceptableVariantIds")(Nil)
      forbiddenVariantIds <- c.getOrElse[List[MasterServiceOfferVariantId]]("forbiddenVariantIds")(Nil)
      topK <- c.getOrElse[EvalTopK]("topK")(EvalTopK())
    } yield EvalVariantExpectation(required, preferred, forbidden, acceptableVariantIds, forbiddenVariantIds, topK)
  }

  implicit val evalProviderExpectationDecoder: Decoder[EvalProviderExpectation] = Decoder.instance { c =>
    for {
      required <- c.getOrElse[EvalConstraintBlock]("required")(EvalConstraintBlock())
      preferred <- c.getOrElse[EvalConstraintBlock]("preferred")(EvalConstraintBlock())
      forbidden <- c.getOrElse[EvalConstraintBlock]("forbidden")(EvalConstraintBlock())
      acceptableProviderLocationIds <- c.getOrElse[List[MasterLocationId]]("acceptableProviderLocationIds")(Nil)
      topK <- c.getOrElse[EvalTopK]("topK")(EvalTopK())
    } yield EvalProviderExpectation(required, preferred, forbidden, acceptableProviderLocationIds, topK)
  }

  implicit val evalServiceExpectationDecoder: Decoder[EvalServiceExpectation] = Decoder.instance { c =>
    for {
      required <- c.getOrElse[EvalConstraintBlock]("required")(EvalConstraintBlock())
      preferred <- c.getOrElse[EvalConstraintBlock]("preferred")(EvalConstraintBlock())
      forbidden <- c.getOrElse[EvalConstraintBlock]("forbidden")(EvalConstraintBlock())
      acceptableServiceIds <- c.getOrElse[List[ServiceId]]("acceptableServiceIds")(Nil)
      topK <- c.getOrElse[EvalTopK]("topK")(EvalTopK())
    } yield EvalServiceExpectation(required, preferred, forbidden, acceptableServiceIds, topK)
  }

  implicit val evalScoringDecoder: Decoder[EvalScoring] = Decoder.instance { c =>
    for {
      variantCarousel <- c.get[EvalCarouselWeights]("variantCarousel")
      providerCarousel <- c.get[EvalCarouselWeights]("providerCarousel")
      serviceIntentCarousel <- c.get[EvalCarouselWeights]("serviceIntentCarousel")
    } yield EvalScoring(variantCarousel, providerCarousel, serviceIntentCarousel)
  }

  implicit val evalLocationDecoder: Decoder[BeautySearchEvalLocation] = Decoder.instance { c =>
    for {
      label <- c.get[String]("label")
      lat <- c.get[BigDecimal]("lat")
      lon <- c.get[BigDecimal]("lon")
    } yield BeautySearchEvalLocation(label, lat, lon)
  }

  implicit val evalQueryDecoder: Decoder[BeautySearchEvalQuery] = Decoder.instance { c =>
    for {
      id <- c.get[String]("id")
      query <- c.get[String]("query")
      language <- c.get[String]("language")
      queryTypes <- c.getOrElse[List[String]]("queryTypes")(Nil)
      expected <- c.downField("expected").success.toRight(DecodingFailure("missing expected", c.history))
      variantExpectation <- expected.get[EvalVariantExpectation]("variantCarousel")
      providerExpectation <- expected.get[EvalProviderExpectation]("providerCarousel")
      serviceExpectation <- expected.get[EvalServiceExpectation]("serviceIntentCarousel")
      scoring <- c.get[EvalScoring]("scoring")
    } yield BeautySearchEvalQuery(id, query, language, queryTypes, variantExpectation, providerExpectation, serviceExpectation, scoring)
  }

  implicit val evalSuiteDecoder: Decoder[BeautySearchEvalSuite] = Decoder.instance { c =>
    for {
      testUserLocation <- c.get[BeautySearchEvalLocation]("testUserLocation")
      queries <- c.get[List[BeautySearchEvalQuery]]("queries")
    } yield BeautySearchEvalSuite(testUserLocation, queries)
  }
}

object BeautySearchEvalScorer {
  def score(
    query: BeautySearchEvalQuery,
    response: BeautySearchResponse,
  ): BeautySearchEvalReport = {
    val topVariantIds = response.variantCarousel.map(_.variantId)
    val topProviderIds = response.providerCarousel.map(_.masterLocationId)
    val topServiceIds = response.serviceIntentCarousel.map(_.serviceId)

    val variantScore = scoreVariants(query.expectedVariantCarousel, query.scoring.variantCarousel, response.variantCarousel)
    val providerScore = scoreProviders(query.expectedProviderCarousel, query.scoring.providerCarousel, response.providerCarousel)
    val serviceScore = scoreServices(query.expectedServiceIntentCarousel, query.scoring.serviceIntentCarousel, response.serviceIntentCarousel)
    val failedAssertions = variantScore._2 ++ providerScore._2 ++ serviceScore._2

    BeautySearchEvalReport(
      queryId = query.id,
      query = query.query,
      score = variantScore._1 + providerScore._1 + serviceScore._1,
      failedAssertions = failedAssertions,
      topVariantIds = topVariantIds,
      topProviderLocationIds = topProviderIds,
      topServiceIds = topServiceIds,
    )
  }

  private def scoreVariants(
    expectation: EvalVariantExpectation,
    weights: EvalCarouselWeights,
    results: List[VariantSearchResult],
  ): (Int, List[String]) = {
    val top1 = results.take(1)
    val top3 = results.take(3)
    val top5 = results.take(5)
    val top10 = results.take(10)
    var score = 0
    var failures = List.empty[String]

    if (expectation.acceptableVariantIds.nonEmpty && top1.exists(result => expectation.acceptableVariantIds.contains(result.variantId))) {
      score += weights.top1RequiredWeight.getOrElse(0)
    } else if (expectation.acceptableVariantIds.nonEmpty && top3.exists(result => expectation.acceptableVariantIds.contains(result.variantId))) {
      score += weights.top3RequiredWeight
    } else if (expectation.acceptableVariantIds.nonEmpty) {
      failures ::= s"variant required ids not present in top-${expectation.topK.requiredInTopK.getOrElse(3)}"
    }

    if (matchesConstraintBlock(expectation.preferred, top5)) {
      score += weights.top5PreferredWeight.getOrElse(0)
    }

    if (top10.exists(result => expectation.forbiddenVariantIds.contains(result.variantId)) || matchesConstraintBlock(expectation.forbidden, top10)) {
      score += weights.top10ForbiddenPenalty
      failures ::= "variant forbidden match present in top-10"
    }

    (score, failures.reverse)
  }

  private def scoreProviders(
    expectation: EvalProviderExpectation,
    weights: EvalCarouselWeights,
    results: List[ProviderSearchResult],
  ): (Int, List[String]) = {
    val top3 = results.take(3)
    val top10 = results.take(10)
    var score = 0
    var failures = List.empty[String]

    if (expectation.acceptableProviderLocationIds.nonEmpty && top3.exists(result => expectation.acceptableProviderLocationIds.contains(result.masterLocationId))) {
      score += weights.top3RequiredWeight
    } else if (expectation.acceptableProviderLocationIds.nonEmpty) {
      failures ::= "provider required ids not present in top-3"
    }

    val topDistance = top3.flatMap(_.distanceKm).headOption
    val minDistance = top3.flatMap(_.distanceKm).sorted.headOption
    if (weights.distancePreferenceWeight.nonEmpty && topDistance.nonEmpty && minDistance.nonEmpty && topDistance == minDistance) {
      score += weights.distancePreferenceWeight.get
    }

    if (top10.exists(result => !expectation.acceptableProviderLocationIds.isEmpty && !expectation.acceptableProviderLocationIds.contains(result.masterLocationId) && expectation.forbidden.nearUser.contains(false))) {
      score += weights.top10ForbiddenPenalty
      failures ::= "provider forbidden condition present in top-10"
    }

    (score, failures.reverse)
  }

  private def scoreServices(
    expectation: EvalServiceExpectation,
    weights: EvalCarouselWeights,
    results: List[ServiceIntentSearchResult],
  ): (Int, List[String]) = {
    val top3 = results.take(3)
    val top10 = results.take(10)
    var score = 0
    var failures = List.empty[String]

    if (expectation.acceptableServiceIds.nonEmpty && top3.exists(result => expectation.acceptableServiceIds.contains(result.serviceId))) {
      score += weights.top3RequiredWeight
    } else if (expectation.acceptableServiceIds.nonEmpty) {
      failures ::= "service required ids not present in top-3"
    }

    if (top10.exists(result => expectation.forbidden.servicesAny.contains(result.serviceName))) {
      score += weights.top10ForbiddenPenalty
      failures ::= "service forbidden match present in top-10"
    }

    (score, failures.reverse)
  }

  private def matchesConstraintBlock(block: EvalConstraintBlock, results: List[VariantSearchResult]): Boolean = {
    if (
      block.servicesAny.isEmpty &&
      block.categoriesAny.isEmpty &&
      block.enumAttributes.isEmpty &&
      block.booleanAttributes.isEmpty &&
      block.intAttributes.isEmpty &&
      block.bigDecimalAttributes.isEmpty &&
      block.price.isEmpty &&
      block.duration.isEmpty
    ) {
      false
    } else {
      results.exists(matchesConstraintBlock(block, _))
    }
  }

  private def matchesConstraintBlock(block: EvalConstraintBlock, result: VariantSearchResult): Boolean = {
    val serviceOk = block.servicesAny.isEmpty || block.servicesAny.contains(result.serviceName)
    val categoryOk = block.categoriesAny.isEmpty || block.categoriesAny.contains(result.categoryName)
    val enumOk = block.enumAttributes.forall {
      case (code, values) =>
        result.enumAttributes.get(code).exists(values.contains)
    }
    val booleanOk = block.booleanAttributes.forall {
      case (code, value) =>
        result.booleanAttributes.get(code).contains(value)
    }
    val intOk = block.intAttributes.forall {
      case (code, range) =>
        result.intAttributes.get(code).exists(value => matchesRange(BigDecimal(value), range))
    }
    val bigDecimalOk = block.bigDecimalAttributes.forall {
      case (code, range) =>
        result.bigDecimalAttributes.get(code).exists(value => matchesRange(value, range))
    }
    val priceOk = block.price.forall(range => matchesRange(result.priceFrom, range))
    val durationOk = block.duration.forall(range => matchesRange(BigDecimal(result.durationMin), range))
    serviceOk && categoryOk && enumOk && booleanOk && intOk && bigDecimalOk && priceOk && durationOk
  }

  private def matchesRange(value: BigDecimal, range: EvalNumericRange): Boolean =
    range.min.forall(value >= _) && range.max.forall(value <= _)
}
