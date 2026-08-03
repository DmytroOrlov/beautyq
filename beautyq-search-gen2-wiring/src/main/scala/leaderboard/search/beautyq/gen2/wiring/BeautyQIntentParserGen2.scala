package leaderboard.search.beautyq.gen2.wiring

import leaderboard.search.beautyq.gen2.contract.*
import leaderboard.search.gen2.contract.*

import scala.util.Try

/** Pure BeautyQ inbound interpretation. Budget extraction, location requirements and semantic-label
  * policy are domain choices here; phrase selection is delegated to the generic matcher. It
  * deliberately stops at typed parsed intent; Brick 4F is the only layer that combines this source
  * with public filters and constructs a SearchPlan. */
object BeautyQIntentParserGen2 {
  private val BudgetPattern = java.util.regex.Pattern.compile("(?i)\\b(?:under|below|up\\s+to)\\s+(\\d+(?:[.,]\\d+)?)(k)?\\b")

  def parse(
    request: ValidatedBeautySearchRequestGen2,
    vocabulary: BeautyQIntentVocabulary,
  ): Either[NonEmptyErrors[BeautyIntentParseError], ParsedBeautyIntentGen2] = {
    val (budget, queryForMatching) = extractBudget(request.query.getOrElse(""))
    val normalized = BeautyQIntentTextGen2.normalize(queryForMatching)
    val tokens = BeautyQIntentTextGen2.tokenizeForIntentMatching(queryForMatching)
    val matches = SearchIntentMatcher.select(
      vocabulary.rules,
      tokens,
      BeautyQIntentTextGen2.tokenizeForIntentMatching,
      BeautyQIntentRuleView,
      BeautyIntentAction.covers,
    )
    val budgetConstraint = budget.toVector.map(bounds => PlannedConstraint.IntervalOverlap(BeautyQSearchDeclarations.variants.Fields.priceFrom, BeautyQSearchDeclarations.variants.Fields.priceTo, bounds))
    val translated = matches.flatMap(value => value.hardActions ++ value.semanticActions)
    val nearUserRequested = translated.contains(BeautyIntentAction.NearUser)
    val nearUserWithoutLocation = nearUserRequested && request.userLocation.isEmpty
    if (nearUserWithoutLocation) Left(singleError(BeautyIntentParseError.MissingUserLocationForNearUser))
    else {
      // Exactly zero or one signal, driven by a plain boolean membership check over BeautyIntentAction
      // values (which compare structurally on plain codes/strings) - never signals.distinct, which would
      // depend on SearchField/extractor equality inside PlannedSignal.GeoProximitySignal.
      val signals: Vector[PlannedSignal[VariantSearchDocumentGen2]] =
        if (nearUserRequested) request.userLocation.map(origin => PlannedSignal.GeoProximitySignal(BeautyQSearchDeclarations.variants.Fields.location, origin)).toVector
        else Vector.empty
      val hardActions = matches.flatMap(value => value.hardActions.flatMap(BeautyQIntentActionCompiler.hardConstraints))
      val parsedHardConstraints = (hardActions ++ budgetConstraint.map(value => SourcedConstraint(value, ConstraintProvenance.ParsedHard))).toVector
      val hardConstraints = normalizeParsedHardConstraints(parsedHardConstraints)
      val residual = SearchIntentMatcher.residualTokens(tokens, matches).mkString(" ")
      val labels = matches.flatMap(value => value.labels ++ (value.hardActions ++ value.semanticActions).flatMap(BeautyQSemanticLabelPolicy.forAction)).foldLeft(Vector.empty[CanonicalSemanticLabel]) { (acc, label) =>
        if (acc.exists(_.stableKey == label.stableKey)) acc else acc :+ label
      }
      val matchedIds = matches.foldLeft(Vector.empty[IntentRuleId]) { (acc, value) =>
        val id = IntentRuleId(value.id)
        if (acc.contains(id)) acc else acc :+ id
      }
      Right(ParsedBeautyIntentGen2(nonEmpty(normalized), hardConstraints, signals, nonEmpty(residual), labels, matchedIds))
    }
  }

  private def extractBudget(query: String): (Option[RangeBounds[BigDecimal]], String) = {
    val matcher = BudgetPattern.matcher(query)
    if (!matcher.find()) (None, query)
    else {
      val amount = Try(BigDecimal(matcher.group(1).replace(',', '.'))).toOption.map(value => if (matcher.group(2) == null) value else value * 1000)
      amount match {
        case None => (None, query)
        case Some(value) =>
          val cleaned = (query.substring(0, matcher.start) + " " + query.substring(matcher.end)).replaceAll("\\s+", " ").trim
          (Some(RangeBounds(Bound.Unbounded, Bound.Inclusive(value))), cleaned)
      }
    }
  }

  private def nonEmpty(value: String): Option[String] = if (value.isEmpty) None else Some(value)

  private def normalizeParsedHardConstraints(
    constraints: Vector[SourcedConstraint[VariantSearchDocumentGen2]],
  ): Vector[SourcedConstraint[VariantSearchDocumentGen2]] =
    constraints.foldLeft(Vector.empty[SourcedConstraint[VariantSearchDocumentGen2]]) { (acc, current) =>
      if (current.provenance != ConstraintProvenance.ParsedHard) acc :+ current
      else {
        acc.indexWhere(existing => existing.provenance == ConstraintProvenance.ParsedHard && sameTermsSlot(existing, current)) match {
          case -1 => acc :+ current
          case index => acc.updated(index, mergeTerms(acc(index), current))
        }
      }
    }

  private def sameTermsSlot(
    left: SourcedConstraint[VariantSearchDocumentGen2],
    right: SourcedConstraint[VariantSearchDocumentGen2],
  ): Boolean =
    (left.constraint, right.constraint) match {
      case (PlannedConstraint.Terms(leftField, _), PlannedConstraint.Terms(rightField, _)) =>
        leftField.id == rightField.id
      case _ => false
    }

  private def mergeTerms(
    first: SourcedConstraint[VariantSearchDocumentGen2],
    next: SourcedConstraint[VariantSearchDocumentGen2],
  ): SourcedConstraint[VariantSearchDocumentGen2] =
    (first.constraint, next.constraint) match {
      case (PlannedConstraint.Terms(firstField, firstValues), PlannedConstraint.Terms(nextField, nextValues)) =>
        val nextCanonicalValues = nextValues.iterator.map(nextField.codec.encodeCanonical).toVector
        val decodedNextValues = nextCanonicalValues.map { value =>
          firstField.codec.decodeCanonical(value) match {
            case Right(decoded) => decoded
            case Left(_) =>
              throw new IllegalStateException(
                s"BeautyQ parsed Terms fields with id '${firstField.id.value}' have incompatible canonical values"
              )
          }
        }
        SourcedConstraint(
          PlannedConstraint.Terms(firstField, firstValues ++ decodedNextValues),
          ConstraintProvenance.ParsedHard,
        )
      case _ => first
    }

  private def singleError(error: BeautyIntentParseError): NonEmptyErrors[BeautyIntentParseError] =
    NonEmptyErrors.fromHead(error, Vector.empty)
}
