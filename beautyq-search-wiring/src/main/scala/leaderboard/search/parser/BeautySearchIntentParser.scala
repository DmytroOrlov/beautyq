package leaderboard.search.parser

import leaderboard.search.{ParsedSearchIntent, UserSearchInput}
import leaderboard.search.dsl.{BeautySearchSpec, IntentMatchMode, SearchConstraint, SearchIntentRule}

import java.util.Locale

final class BeautySearchIntentParser(spec: BeautySearchSpec) {
  import BeautySearchIntentParser.*

  def parse(input: UserSearchInput): ParsedSearchIntent = {
    val (budgetConstraint, queryForMatching) = extractBudget(input.query)
    val normalizedQuery = normalize(queryForMatching)
    val normalizedTokens = tokenize(normalizedQuery)
    val baseMatches = selectMatches(
      rules = spec.intentVocabulary.rules.filter(_.requires.isEmpty),
      tokens = normalizedTokens,
      currentConstraints = Nil,
      occupied = Set.empty,
    )

    val contextualMatches = selectContextualMatches(
      rules = spec.intentVocabulary.rules.filter(_.requires.nonEmpty),
      tokens = normalizedTokens,
      selected = baseMatches,
    )

    val allMatches = (baseMatches ++ contextualMatches).sortBy(matchResult => (matchResult.start, matchResult.end))
    val explicitConstraints = distinctConstraints(allMatches.flatMap(_.rule.constraints) ++ budgetConstraint.toList)
    val softBoosts = distinctConstraints(allMatches.flatMap(_.rule.softBoosts))
    val occupiedPositions = allMatches.foldLeft(Set.empty[Int]) { (acc, next) =>
      acc ++ (next.start until next.end)
    }
    val remainingText = normalizedTokens.zipWithIndex.collect {
      case (token, index) if !occupiedPositions.contains(index) => token
    }.mkString(" ")

    ParsedSearchIntent(
      originalQuery = input.query,
      normalizedTokens = normalizedTokens,
      explicitConstraints = explicitConstraints,
      softBoosts = softBoosts,
      remainingText = remainingText,
    )
  }
}

object BeautySearchIntentParser {
  /** Deterministic natural-language budget phrase: an upper-bound keyword followed by a number with an
    * optional `k` thousands shorthand. Matches `under 3k`, `under 3000`, `below 3000`, `up to 3000`,
    * `under 50`, `under 50.00`. No fuzzy ("cheap") interpretation, no currency parsing, no rewrite.
    */
  private val BudgetPattern: java.util.regex.Pattern =
    java.util.regex.Pattern.compile("(?i)\\b(?:under|below|up\\s+to)\\s+(\\d+(?:[.,]\\d+)?)(k)?\\b")

  /** Extracts at most one hard upper-bound [[SearchConstraint.PriceRange]] from a raw query and returns
    * the query with the matched budget phrase removed so the residual text stays free of `under`/`below`/
    * `3k` tokens. Noisy input without a numeric bound (e.g. `under abc`, `nails 3k followers`) yields no
    * constraint and an unchanged query.
    */
  private[parser] def extractBudget(query: String): (Option[SearchConstraint], String) = {
    val matcher = BudgetPattern.matcher(query)
    if (matcher.find()) {
      val hasThousandsSuffix = Option(matcher.group(2)).isDefined
      parseBudgetAmount(matcher.group(1), hasThousandsSuffix) match {
        case Some(amount) =>
          val cleaned = (query.substring(0, matcher.start) + " " + query.substring(matcher.end))
            .replaceAll("\\s+", " ")
            .trim
          (Some(SearchConstraint.PriceRange(None, Some(amount))), cleaned)
        case None =>
          (None, query)
      }
    } else {
      (None, query)
    }
  }

  private def parseBudgetAmount(numberText: String, hasThousandsSuffix: Boolean): Option[BigDecimal] =
    scala.util.Try(BigDecimal(numberText.replace(',', '.'))).toOption.map { value =>
      if (hasThousandsSuffix) value * BigDecimal(1000) else value
    }

  private final case class MatchResult(
    rule: SearchIntentRule[SearchConstraint],
    phrase: String,
    start: Int,
    end: Int,
  ) {
    val length: Int = end - start
  }

  private def normalize(value: String): String =
    value
      .toLowerCase(Locale.ROOT)
      .replace('ё', 'е')
      .replace('–', ' ')
      .replace('—', ' ')
      .replace('-', ' ')
      .replace('/', ' ')
      .replace('\\', ' ')
      .replace('(', ' ')
      .replace(')', ' ')
      .replace('[', ' ')
      .replace(']', ' ')
      .replace('{', ' ')
      .replace('}', ' ')
      .replace(',', ' ')
      .replace('.', ' ')
      .replace(':', ' ')
      .replace(';', ' ')
      .replace('!', ' ')
      .replace('?', ' ')
      .replace('"', ' ')
      .replace('\'', ' ')
      .trim
      .replaceAll("\\s+", " ")

  private def tokenize(value: String): List[String] =
    value.split(' ').toList.map(_.trim).filter(_.nonEmpty)

  private def selectContextualMatches(
    rules: List[SearchIntentRule[SearchConstraint]],
    tokens: List[String],
    selected: List[MatchResult],
  ): List[MatchResult] = {
    val currentConstraints = distinctConstraints(selected.flatMap(_.rule.constraints))
    val occupied = selected.foldLeft(Set.empty[Int]) { (acc, next) =>
      acc ++ (next.start until next.end)
    }
    val nextMatches = selectMatches(rules, tokens, currentConstraints, occupied)
    if (nextMatches.isEmpty) {
      Nil
    } else {
      nextMatches ++ selectContextualMatches(rules, tokens, selected ++ nextMatches)
    }
  }

  private def selectMatches(
    rules: List[SearchIntentRule[SearchConstraint]],
    tokens: List[String],
    currentConstraints: List[SearchConstraint],
    occupied: Set[Int],
  ): List[MatchResult] = {
    val candidates = rules.iterator
      .filter { rule =>
        val requiresSatisfied = constraintsSatisfied(rule.requires, currentConstraints)
        val excludesSatisfied = rule.excludes.nonEmpty && constraintsSatisfied(rule.excludes, currentConstraints)
        requiresSatisfied && !excludesSatisfied
      }
      .flatMap(rule => rule.tokens.iterator.flatMap(token => matchToken(rule, token, tokens)))
      .toList
      .sortBy(candidate => (-candidate.length, candidate.start, -candidate.rule.constraints.size))

    candidates.foldLeft(List.empty[MatchResult]) {
      case (acc, candidate) =>
        val candidateIndexes = candidate.start until candidate.end
        val occupiedBySelection = acc.exists(existing => overlaps(existing, candidate))
        val occupiedByPrevious = candidateIndexes.exists(occupied.contains)
        if (occupiedBySelection || occupiedByPrevious) {
          acc
        } else {
          candidate :: acc
        }
    }.reverse
  }

  private def matchToken(
    rule: SearchIntentRule[SearchConstraint],
    rawToken: String,
    tokens: List[String],
  ): List[MatchResult] = {
    val token = normalize(rawToken)
    val phraseTokens = tokenize(token)
    if (phraseTokens.isEmpty) {
      Nil
    } else {
      rule.matchMode match {
        case IntentMatchMode.Phrase =>
          slidingMatches(rule, rawToken, phraseTokens, tokens)
        case IntentMatchMode.Token =>
          phraseTokens.flatMap { tokenValue =>
            tokens.zipWithIndex.collect {
              case (candidate, index) if candidate == tokenValue => MatchResult(rule, rawToken, index, index + 1)
            }
          }
      }
    }
  }

  private def slidingMatches(
    rule: SearchIntentRule[SearchConstraint],
    rawToken: String,
    phraseTokens: List[String],
    tokens: List[String],
  ): List[MatchResult] = {
    if (phraseTokens.length > tokens.length) {
      Nil
    } else {
      tokens.sliding(phraseTokens.length).zipWithIndex.collect {
        case (candidateTokens, index) if candidateTokens == phraseTokens =>
          MatchResult(rule, rawToken, index, index + phraseTokens.length)
      }.toList
    }
  }

  private def overlaps(left: MatchResult, right: MatchResult): Boolean =
    left.start < right.end && right.start < left.end

  private def constraintsSatisfied(required: List[SearchConstraint], current: List[SearchConstraint]): Boolean =
    required.forall(requiredConstraint => current.exists(currentConstraint => covers(currentConstraint, requiredConstraint)))

  private def covers(current: SearchConstraint, required: SearchConstraint): Boolean =
    (current, required) match {
      case (SearchConstraint.ServiceAny(currentNames), SearchConstraint.ServiceAny(requiredNames)) =>
        requiredNames.subsetOf(currentNames)
      case (SearchConstraint.CategoryAny(currentNames), SearchConstraint.CategoryAny(requiredNames)) =>
        requiredNames.subsetOf(currentNames)
      case (
            SearchConstraint.EnumAttr(currentCode, currentValues),
            SearchConstraint.EnumAttr(requiredCode, requiredValues),
          ) =>
        currentCode == requiredCode && requiredValues.subsetOf(currentValues)
      case (SearchConstraint.BoolAttr(currentCode, currentValue), SearchConstraint.BoolAttr(requiredCode, requiredValue)) =>
        currentCode == requiredCode && currentValue == requiredValue
      case (
            SearchConstraint.IntRange(currentCode, currentMin, currentMax),
            SearchConstraint.IntRange(requiredCode, requiredMin, requiredMax),
          ) =>
        currentCode == requiredCode && boundCovers(currentMin, currentMax, requiredMin, requiredMax)
      case (
            SearchConstraint.DecimalRange(currentCode, currentMin, currentMax),
            SearchConstraint.DecimalRange(requiredCode, requiredMin, requiredMax),
          ) =>
        currentCode == requiredCode && boundCovers(currentMin, currentMax, requiredMin, requiredMax)
      case (SearchConstraint.PriceRange(currentMin, currentMax), SearchConstraint.PriceRange(requiredMin, requiredMax)) =>
        boundCovers(currentMin, currentMax, requiredMin, requiredMax)
      case (SearchConstraint.DurationRange(currentMin, currentMax), SearchConstraint.DurationRange(requiredMin, requiredMax)) =>
        boundCovers(currentMin, currentMax, requiredMin, requiredMax)
      case (SearchConstraint.NearUser, SearchConstraint.NearUser) =>
        true
      case _ =>
        false
    }

  private def boundCovers[A: Ordering](
    currentMin: Option[A],
    currentMax: Option[A],
    requiredMin: Option[A],
    requiredMax: Option[A],
  ): Boolean = {
    val ordering = summon[Ordering[A]]
    val minSatisfied = (currentMin, requiredMin) match {
      case (_, None) => true
      case (Some(current), Some(required)) => ordering.lteq(current, required)
      case (None, Some(_)) => false
    }
    val maxSatisfied = (currentMax, requiredMax) match {
      case (_, None) => true
      case (Some(current), Some(required)) => ordering.gteq(current, required)
      case (None, Some(_)) => false
    }
    minSatisfied && maxSatisfied
  }

  private def distinctConstraints(constraints: List[SearchConstraint]): List[SearchConstraint] =
    constraints.foldLeft((Set.empty[SearchConstraint], List.empty[SearchConstraint])) {
      case ((seen, acc), constraint) =>
        if (seen.contains(constraint)) {
          (seen, acc)
        } else {
          (seen + constraint, acc :+ constraint)
        }
    }._2
}
