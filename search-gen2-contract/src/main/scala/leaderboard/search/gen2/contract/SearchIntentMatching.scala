package leaderboard.search.gen2.contract

/** Matching phases shared by domain intent vocabularies. A domain owns the actions and their
  * meaning; the matcher owns deterministic phrase selection and phase ordering. */
enum SearchIntentRuleMode {
  case Independent, Contextual, SemanticOverlay
}

/** The reusable view the matcher needs from a domain rule. It deliberately does not know how an
  * action becomes a constraint, signal or label; those are domain policies at the edge. */
trait SearchIntentRuleView[Rule, Action, Label] {
  def id(rule: Rule): String
  def aliases(rule: Rule): Vector[String]
  def mode(rule: Rule): SearchIntentRuleMode
  def hardActions(rule: Rule): Vector[Action]
  def semanticActions(rule: Rule): Vector[Action]
  def requires(rule: Rule): Vector[Action]
  def excludes(rule: Rule): Vector[Action]
  def labels(rule: Rule): Vector[Label]
}

final case class SearchIntentMatch[Action, Label](
  id: String,
  hardActions: Vector[Action],
  semanticActions: Vector[Action],
  labels: Vector[Label],
  start: Int,
  end: Int,
  declarationIndex: Int,
) {
  def length: Int = end - start
}

/** Domain-neutral deterministic phrase matcher. The query is already tokenized by the caller; the
  * supplied `phraseTokens` function is the one shared normalization/tokenization policy for aliases.
  * Matching is longest-first, then leftmost, then declaration order. Contextual rules are evaluated
  * to a fixed point, while semantic overlays can share occupied tokens with an earlier hard match. */
object SearchIntentMatcher {
  def select[Rule, Action, Label](
    rules: Vector[Rule],
    tokens: Vector[String],
    phraseTokens: String => Vector[String],
    view: SearchIntentRuleView[Rule, Action, Label],
    covers: (Action, Action) => Boolean,
  ): Vector[SearchIntentMatch[Action, Label]] = {
    val independent = selectMatches(
      rules.filter(rule => view.mode(rule) == SearchIntentRuleMode.Independent),
      tokens,
      Vector.empty,
      Set.empty,
      phraseTokens,
      view,
      covers,
    )
    val contextual = selectContextual(rules, tokens, independent, phraseTokens, view, covers)
    val overlayContext = (independent ++ contextual).flatMap(matchValue => actions(matchValue, view))
    val overlays = selectMatches(
      rules.filter(rule => view.mode(rule) == SearchIntentRuleMode.SemanticOverlay),
      tokens,
      overlayContext,
      Set.empty,
      phraseTokens,
      view,
      covers,
    )
    (independent ++ contextual ++ overlays)
      .sortBy(matchValue => (matchValue.start, matchValue.end, view.id(matchValue.rule)))
      .map(matchValue => materialize(matchValue, view))
  }

  def residualTokens[Action, Label](tokens: Vector[String], matches: Vector[SearchIntentMatch[Action, Label]]): Vector[String] = {
    val occupied = matches.foldLeft(Set.empty[Int])((acc, matchValue) => acc ++ (matchValue.start until matchValue.end))
    tokens.zipWithIndex.collect { case (token, index) if !occupied.contains(index) => token }
  }

  private final case class Candidate[Rule](rule: Rule, start: Int, end: Int, declarationIndex: Int)

  private def materialize[Rule, Action, Label](candidate: Candidate[Rule], view: SearchIntentRuleView[Rule, Action, Label]): SearchIntentMatch[Action, Label] =
    SearchIntentMatch(
      view.id(candidate.rule),
      view.hardActions(candidate.rule),
      view.semanticActions(candidate.rule),
      view.labels(candidate.rule),
      candidate.start,
      candidate.end,
      candidate.declarationIndex,
    )

  private def actions[Rule, Action, Label](candidate: Candidate[Rule], view: SearchIntentRuleView[Rule, Action, Label]): Vector[Action] =
    view.hardActions(candidate.rule) ++ view.semanticActions(candidate.rule)

  private def selectContextual[Rule, Action, Label](
    rules: Vector[Rule],
    tokens: Vector[String],
    selected: Vector[Candidate[Rule]],
    phraseTokens: String => Vector[String],
    view: SearchIntentRuleView[Rule, Action, Label],
    covers: (Action, Action) => Boolean,
  ): Vector[Candidate[Rule]] = {
    val context = selected.flatMap(candidate => actions(candidate, view))
    val occupied = selected.foldLeft(Set.empty[Int])((acc, candidate) => acc ++ (candidate.start until candidate.end))
    val next = selectMatches(
      rules.filter(rule => view.mode(rule) == SearchIntentRuleMode.Contextual),
      tokens,
      context,
      occupied,
      phraseTokens,
      view,
      covers,
    )
    if (next.isEmpty) Vector.empty else next ++ selectContextual(rules, tokens, selected ++ next, phraseTokens, view, covers)
  }

  private def selectMatches[Rule, Action, Label](
    rules: Vector[Rule],
    tokens: Vector[String],
    currentActions: Vector[Action],
    occupied: Set[Int],
    phraseTokens: String => Vector[String],
    view: SearchIntentRuleView[Rule, Action, Label],
    covers: (Action, Action) => Boolean,
  ): Vector[Candidate[Rule]] = {
    val candidates = rules.zipWithIndex.flatMap { case (rule, declarationIndex) =>
      if (!requiresSatisfied(view.requires(rule), currentActions, covers) || excludesSatisfied(view.excludes(rule), currentActions, covers)) Vector.empty
      else view.aliases(rule).flatMap(alias => phraseMatches(rule, alias, tokens, declarationIndex, phraseTokens))
    }.sortBy(candidate => (-(candidate.end - candidate.start), candidate.start, candidate.declarationIndex))

    candidates.foldLeft(Vector.empty[Candidate[Rule]]) { (selected, candidate) =>
      val overlapsSelected = selected.exists(existing => overlaps(existing, candidate))
      val overlapsPrevious = (candidate.start until candidate.end).exists(occupied.contains)
      if (overlapsSelected || overlapsPrevious) selected else selected :+ candidate
    }
  }

  private def phraseMatches[Rule](
    rule: Rule,
    alias: String,
    tokens: Vector[String],
    declarationIndex: Int,
    phraseTokens: String => Vector[String],
  ): Vector[Candidate[Rule]] = {
    val phrase = phraseTokens(alias)
    if (phrase.isEmpty || phrase.length > tokens.length) Vector.empty
    else tokens.sliding(phrase.length).zipWithIndex.collect {
      case (candidate, index) if candidate == phrase => Candidate(rule, index, index + phrase.length, declarationIndex)
    }.toVector
  }

  private def overlaps[Rule](left: Candidate[Rule], right: Candidate[Rule]): Boolean =
    left.start < right.end && right.start < left.end

  private def requiresSatisfied[Action](required: Vector[Action], current: Vector[Action], covers: (Action, Action) => Boolean): Boolean =
    required.forall(item => current.exists(produced => covers(produced, item)))

  private def excludesSatisfied[Action](excluded: Vector[Action], current: Vector[Action], covers: (Action, Action) => Boolean): Boolean =
    excluded.exists(item => current.exists(produced => covers(produced, item)))
}
