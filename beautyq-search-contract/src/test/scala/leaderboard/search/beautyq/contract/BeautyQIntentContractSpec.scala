package leaderboard.search.beautyq.contract

import io.circe.syntax.*
import leaderboard.search.dsl.{BeautyQSearchFieldSemantics, BeautyQSearchIntentVocabulary, SearchConstraint}
import org.scalatest.wordspec.AnyWordSpec

final class BeautyQIntentContractSpec extends AnyWordSpec {

  "BeautyQSearchIntentVocabulary" should {
    "be usable from beautyq-search-contract alone, with no repositories/materialization" in {
      assert(BeautyQSearchIntentVocabulary.vocabulary.rules.nonEmpty)
    }

    "contain known BeautyQ intent tokens" in {
      val allTokens = BeautyQSearchIntentVocabulary.vocabulary.rules.flatMap(_.tokens).toSet
      assert(allTokens.contains("маникюр"))
      assert(allTokens.contains("lashes"))
    }
  }

  "SearchConstraint" should {
    "round-trip ServiceAny through its circe codec" in {
      val constraint: SearchConstraint = SearchConstraint.ServiceAny(Set("Маникюр"))
      assert(constraint.asJson.as[SearchConstraint] == Right(constraint))
    }

    "round-trip EnumAttr through its circe codec" in {
      val constraint: SearchConstraint = SearchConstraint.EnumAttr("nail_coating_type", Set("gel_polish"))
      assert(constraint.asJson.as[SearchConstraint] == Right(constraint))
    }

    "round-trip PriceRange through its circe codec" in {
      val constraint: SearchConstraint = SearchConstraint.PriceRange(Some(BigDecimal(10)), Some(BigDecimal(50)))
      assert(constraint.asJson.as[SearchConstraint] == Right(constraint))
    }

    "round-trip NearUser through its circe codec" in {
      val constraint: SearchConstraint = SearchConstraint.NearUser
      assert(constraint.asJson.as[SearchConstraint] == Right(constraint))
    }
  }

  "BeautyQSearchFieldSemantics" should {
    "derive the enumAttributes field path" in {
      assert(BeautyQSearchFieldSemantics.EnumAttribute("x").value == "enumAttributes.x")
    }

    "derive the booleanAttributes field path" in {
      assert(BeautyQSearchFieldSemantics.BooleanAttribute("x").value == "booleanAttributes.x")
    }
  }
}
