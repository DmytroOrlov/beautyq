package leaderboard.search.gen2.contract

import org.scalatest.wordspec.AnyWordSpec

final class SearchIntentMatcherSpec extends AnyWordSpec {
  private final case class Rule(
    id: String,
    aliases: Vector[String],
    mode: SearchIntentRuleMode,
    hard: Vector[String],
    semantic: Vector[String],
    requires: Vector[String],
    excludes: Vector[String],
    labels: Vector[String],
  )

  private val view = new SearchIntentRuleView[Rule, String, String] {
    def id(rule: Rule): String = rule.id
    def aliases(rule: Rule): Vector[String] = rule.aliases
    def mode(rule: Rule): SearchIntentRuleMode = rule.mode
    def hardActions(rule: Rule): Vector[String] = rule.hard
    def semanticActions(rule: Rule): Vector[String] = rule.semantic
    def requires(rule: Rule): Vector[String] = rule.requires
    def excludes(rule: Rule): Vector[String] = rule.excludes
    def labels(rule: Rule): Vector[String] = rule.labels
  }

  private def tokens(value: String): Vector[String] = value.split(" ").toVector

  "SearchIntentMatcher" should {
    "keep independent, contextual and overlay semantics domain-neutral" in {
      val rules = Vector(
        Rule("independent-book", Vector("book"), SearchIntentRuleMode.Independent, Vector("book"), Vector.empty, Vector.empty, Vector.empty, Vector("book")),
        Rule("contextual-fiction", Vector("fiction"), SearchIntentRuleMode.Contextual, Vector("fiction"), Vector.empty, Vector("book"), Vector.empty, Vector.empty),
        Rule("overlay-near", Vector("book"), SearchIntentRuleMode.SemanticOverlay, Vector.empty, Vector("near"), Vector.empty, Vector.empty, Vector("near")),
      )

      val matches = SearchIntentMatcher.select(rules, tokens("book fiction"), tokens, view, _ == _)

      assert(matches.map(_.id) == Vector("independent-book", "overlay-near", "contextual-fiction"))
      assert(matches.flatMap(_.hardActions) == Vector("book", "fiction"))
      assert(matches.flatMap(_.semanticActions) == Vector("near"))
      assert(SearchIntentMatcher.residualTokens(tokens("book fiction"), matches).isEmpty)
      assert(matches.flatMap(_.labels) == Vector("book", "near"))
    }

    "select the longest independent alias before a shorter overlapping alias" in {
      val rules = Vector(
        Rule("short", Vector("red"), SearchIntentRuleMode.Independent, Vector("short"), Vector.empty, Vector.empty, Vector.empty, Vector.empty),
        Rule("long", Vector("red book"), SearchIntentRuleMode.Independent, Vector("long"), Vector.empty, Vector.empty, Vector.empty, Vector.empty),
      )

      val matches = SearchIntentMatcher.select(rules, tokens("red book"), tokens, view, _ == _)

      assert(matches.map(_.id) == Vector("long"))
      assert(SearchIntentMatcher.residualTokens(tokens("red book now"), matches) == Vector("now"))
    }
  }
}
