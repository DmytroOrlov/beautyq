package leaderboard.search.eval

import io.circe.{Decoder, DecodingFailure}
import io.circe.parser.decode
import leaderboard.model.*

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

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
