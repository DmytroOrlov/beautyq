package leaderboard.search

import leaderboard.search.dsl.{BeautySearchSpecV1, SearchConstraint}
import leaderboard.search.parser.BeautySearchIntentParser
import org.scalatest.wordspec.AnyWordSpec

/** Focused pure coverage for natural-language budget/range parsing.
  *
  * Budget phrases (`under 3k`, `below 3000`, `up to 3000`, ...) are a baseline parser/DSL concern: they
  * become a hard [[SearchConstraint.PriceRange]] upper bound owned by Elasticsearch, never a semantic /
  * Qdrant recall hint. These assertions stay at the parser layer and touch no backend, route, or client.
  */
final class BeautySearchBudgetIntentParserSpec extends AnyWordSpec {

  private val parser = new BeautySearchIntentParser(BeautySearchSpecV1.spec)

  private def priceConstraints(query: String): List[SearchConstraint] =
    parser.parse(UserSearchInput(query, None, None)).explicitConstraints.collect {
      case price: SearchConstraint.PriceRange => price
    }

  private def parseQuery(query: String) = parser.parse(UserSearchInput(query, None, None))

  "BeautySearchIntentParser natural-language budget parsing" should {

    "read `nails under 3k` as a 3000 upper bound" in {
      assert(priceConstraints("nails under 3k") == List(SearchConstraint.PriceRange(None, Some(BigDecimal(3000)))))
    }

    "read `nails under 3000` as a 3000 upper bound" in {
      assert(priceConstraints("nails under 3000") == List(SearchConstraint.PriceRange(None, Some(BigDecimal(3000)))))
    }

    "read `nails below 3000` as a 3000 upper bound" in {
      assert(priceConstraints("nails below 3000") == List(SearchConstraint.PriceRange(None, Some(BigDecimal(3000)))))
    }

    "read `nails up to 3000` as a 3000 upper bound" in {
      assert(priceConstraints("nails up to 3000") == List(SearchConstraint.PriceRange(None, Some(BigDecimal(3000)))))
    }

    "read decimal budgets like `under 50.00`" in {
      assert(priceConstraints("nails under 50.00") == List(SearchConstraint.PriceRange(None, Some(BigDecimal("50.00")))))
      assert(priceConstraints("nails under 50") == List(SearchConstraint.PriceRange(None, Some(BigDecimal(50)))))
    }

    "keep the manicure service constraint alongside the budget for `маникюр under 50`" in {
      val intent = parseQuery("маникюр under 50")

      assert(intent.explicitConstraints.contains(SearchConstraint.PriceRange(None, Some(BigDecimal(50)))))
      assert(intent.explicitConstraints.contains(SearchConstraint.ServiceAny(Set("Маникюр"))))
    }

    "support `маникюр below 50`, `маникюр up to 50` and `nails under 50` budget forms" in {
      List("маникюр below 50", "маникюр up to 50", "nails under 50").foreach { query =>
        assert(
          priceConstraints(query) == List(SearchConstraint.PriceRange(None, Some(BigDecimal(50)))),
          s"missing budget for '$query'",
        )
      }
    }

    "strip the budget phrase from the residual text so `under`/`below`/`3k` do not dominate the query" in {
      val intent = parseQuery("nails under 3k")
      val residualTokens = intent.remainingText.split(' ').toSet

      assert(!residualTokens.contains("under"))
      assert(!residualTokens.contains("3k"))
      assert(!residualTokens.contains("3000"))
    }

    "not invent a price constraint for noisy or non-numeric budgets" in {
      assert(priceConstraints("nails under abc").isEmpty)
      assert(priceConstraints("nails 3k followers").isEmpty)
      assert(priceConstraints("маникюр").isEmpty)
    }
  }
}
