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

  "BeautyQSearchLanguageContract" should {
    "declare only de, en, ru" in {
      assert(BeautyQSearchLanguageContract.supported.map(_.code) == List("de", "en", "ru"))
    }

    "not declare mixed as a language" in {
      assert(!BeautyQSearchLanguageContract.supported.map(_.code).contains("mixed"))
    }
  }

  "BeautyQSearchIntentSectionContract" should {
    "reference the same supported languages as BeautyQSearchLanguageContract" in {
      assert(BeautyQSearchIntentSectionContract.section.languages eq BeautyQSearchLanguageContract.supported)
    }

    "declare non-empty structured-alias vocabularies" in {
      assert(BeautyQSearchIntentSectionContract.structuredAliasVocabularies.nonEmpty)
    }

    "include known structured alias terms" in {
      val allTerms = BeautyQSearchIntentSectionContract.structuredAliasVocabularies.flatMap(_.terms).toSet
      assert(allTerms.contains("маникюр"))
      assert(allTerms.contains("lashes"))
    }

    "declare empty synonyms for every vocabulary" in {
      assert(BeautyQSearchIntentSectionContract.structuredAliasVocabularies.forall(_.synonyms == Map.empty))
    }

    "group all structured-alias vocabularies under structuredAliasVocabularyGroup" in {
      assert(BeautyQSearchIntentSectionContract.structuredAliasVocabularyGroup.vocabularies == BeautyQSearchIntentSectionContract.structuredAliasVocabularies)
    }

    "declare non-empty noise controls" in {
      assert(BeautyQSearchIntentSectionContract.noiseControls.nonEmpty)
    }

    "include a known noise-control excluded term" in {
      val excludedTerms = BeautyQSearchIntentSectionContract.noiseControls.flatMap(_.excludedTerms).toSet
      assert(excludedTerms.contains("не татуаж"))
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
