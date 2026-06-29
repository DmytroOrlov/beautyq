package leaderboard.search

import leaderboard.search.dsl.{BeautyQSearchIntentVocabulary, BeautySearchSpecV1, SearchConstraint, SearchIntentRule}
import leaderboard.search.parser.BeautySearchIntentParser
import org.scalatest.wordspec.AnyWordSpec

/** Contractual + Blackbox + Atomic coverage for the BeautyQ intent vocabulary.
  *
  * Locks that the structured intent aliases and residual query-noise phrases stay distinct ownership and
  * that the parser still derives the same constraints from them after the synonym-dictionary model was
  * replaced. These rules are structured service/category/attribute intent, not lexical ES analyzer synonyms.
  */
final class BeautyQSearchIntentVocabularySpec extends AnyWordSpec {

  private val rules = BeautyQSearchIntentVocabulary.vocabulary.rules
  private val parser = new BeautySearchIntentParser(BeautySearchSpecV1.spec)

  private def ruleWithToken(token: String): SearchIntentRule =
    rules.find(_.tokens.contains(token)).getOrElse(fail(s"No intent rule owns token '$token'"))

  private def parseConstraints(query: String): List[SearchConstraint] =
    parser.parse(UserSearchInput(query, None, None)).explicitConstraints

  private def isStructuredAlias(rule: SearchIntentRule): Boolean =
    rule match {
      case _: SearchIntentRule.StructuredAlias => true
      case _ => false
    }

  private def isQueryNoisePhrase(rule: SearchIntentRule): Boolean =
    rule match {
      case _: SearchIntentRule.QueryNoisePhrase => true
      case _ => false
    }

  "BeautyQSearchIntentVocabulary" should {

    "expose a non-empty vocabulary" in {
      assert(rules.nonEmpty)
    }

    "contain both structured aliases and query-noise phrases" in {
      assert(rules.exists(isStructuredAlias))
      assert(rules.exists(isQueryNoisePhrase))
    }

    "give every rule non-empty tokens" in {
      assert(rules.forall(_.tokens.nonEmpty))
    }

    "give every structured alias at least one constraint, soft boost, require or exclude" in {
      rules.collect { case alias: SearchIntentRule.StructuredAlias => alias }.foreach { alias =>
        assert(
          alias.constraints.nonEmpty || alias.softBoosts.nonEmpty || alias.requires.nonEmpty || alias.excludes.nonEmpty,
          s"StructuredAlias ${alias.tokens} carries no intent",
        )
      }
    }

    "keep every query-noise phrase free of constraints and soft boosts" in {
      rules.collect { case noise: SearchIntentRule.QueryNoisePhrase => noise }.foreach { noise =>
        assert(noise.constraints.isEmpty, s"QueryNoisePhrase ${noise.tokens} should have no constraints")
        assert(noise.softBoosts.isEmpty, s"QueryNoisePhrase ${noise.tokens} should have no soft boosts")
      }
    }

    "map the manicure misspellings to the manicure service constraint" in {
      List("маникюр", "манекюр").foreach { token =>
        val rule = ruleWithToken(token)
        assert(rule.constraints.contains(SearchConstraint.ServiceAny(Set("Маникюр"))), s"Token '$token' lost its manicure service")
      }
    }

    "map nails to the nails category with a soft nail service boost" in {
      val rule = ruleWithToken("nails")
      assert(rule.constraints.contains(SearchConstraint.CategoryAny(Set("Ногти, маникюр и педикюр"))))
      assert(rule.softBoosts.exists {
        case SearchConstraint.ServiceAny(names) => names.contains("Маникюр")
        case _ => false
      })
    }

    "classify 'салон красоты' as a query-noise phrase" in {
      assert(isQueryNoisePhrase(ruleWithToken("салон красоты")))
    }

    "classify 'не татуаж' as a query-noise phrase" in {
      assert(isQueryNoisePhrase(ruleWithToken("не татуаж")))
    }

    "keep the contextual face/gesicht alias excludes" in {
      val rule = ruleWithToken("face")
      assert(rule.tokens.contains("gesicht"))
      assert(rule.excludes.contains(SearchConstraint.EnumAttr("body_area", Set("face_neck_decollete"))))
    }
  }

  "BeautySearchIntentParser over the intent vocabulary" should {

    "produce manicure service plus a budget upper bound for 'маникюр under 50'" in {
      val constraints = parseConstraints("маникюр under 50")
      assert(constraints.contains(SearchConstraint.ServiceAny(Set("Маникюр"))))
      assert(constraints.contains(SearchConstraint.PriceRange(None, Some(BigDecimal(50)))))
    }

    "produce the nails category, soft service boost and budget for 'nails under 50'" in {
      val intent = parser.parse(UserSearchInput("nails under 50", None, None))
      assert(intent.explicitConstraints.contains(SearchConstraint.CategoryAny(Set("Ногти, маникюр и педикюр"))))
      assert(intent.explicitConstraints.contains(SearchConstraint.PriceRange(None, Some(BigDecimal(50)))))
      assert(intent.softBoosts.exists {
        case SearchConstraint.ServiceAny(names) => names.contains("Маникюр")
        case _ => false
      })
    }

    "leave no dominating residual tokens for 'салон красоты'" in {
      val intent = parser.parse(UserSearchInput("салон красоты", None, None))
      assert(intent.explicitConstraints.isEmpty)
      assert(intent.remainingText.isEmpty)
    }

    "leave no tattoo/pmu constraint for 'не татуаж'" in {
      val constraints = parseConstraints("не татуаж")
      assert(!constraints.contains(SearchConstraint.ServiceAny(Set("Permanent Make-Up"))))
    }

    "produce lashes refill 2d with correction for 'реснички 2д корр'" in {
      val constraints = parseConstraints("реснички 2д корр")
      assert(constraints.contains(SearchConstraint.ServiceAny(Set("Ресницы"))))
      assert(constraints.contains(SearchConstraint.EnumAttr("lash_volume", Set("volume2_d"))))
      assert(constraints.contains(SearchConstraint.EnumAttr("lash_service_type", Set("refill"))))
      assert(constraints.contains(SearchConstraint.BoolAttr("with_correction", true)))
    }

    "respect the face exclude behavior under a face_neck_decollete context" in {
      val constraints = parseConstraints("увлажнение лица face")
      assert(constraints.contains(SearchConstraint.EnumAttr("body_area", Set("face_neck_decollete"))))
      assert(!constraints.contains(SearchConstraint.EnumAttr("body_area", Set("face"))))
    }
  }
}
