package leaderboard.search.beautyq.gen2.contract

import leaderboard.search.gen2.contract.*

object BeautySearchRequestTrace {
  def render(request: ValidatedBeautySearchRequestGen2): String = {
    val query = request.query match {
      case Some(value) => s"present=\"${escape(value)}\""
      case None        => "absent"
    }
    val filterLines = request.filters.zipWithIndex.map { case (filter, index) =>
      val clause = filter.clause match {
        case BeautyPublicFilterClause.Constraint(value) => PlannedAlgebraTrace.constraint(value)
        case BeautyPublicFilterClause.GeoRadius(field, radius) => s"geo-radius field=${field.id.value}:${field.codec.typeId.value} radius=${SearchValueCodec.bigDecimal.encodeCanonical(radius.meters)}"
      }
      s"request.filter[$index] name=${BeautyQPublicFilterRegistry.publicNameOf(filter).value} provenance=${SearchPlanTrace.provenance(filter.provenance)} $clause"
    }
    val sortLines = request.sort.zipWithIndex.map { case (sortInput, index) =>
      val rendered = sortInput.clause match {
        case DecodedBeautySort.Planned(value) => PlannedAlgebraTrace.sort(value)
        case DecodedBeautySort.GeoDistance(field, direction) => s"geo-distance field=${field.id.value}:${field.codec.typeId.value} direction=$direction origin=unresolved"
      }
      s"request.sort[$index] $rendered"
    }
    val location = request.userLocation.map(point => SearchValueCodec.geoPoint.encodeCanonical(point)).getOrElse("absent")
    (Vector(s"request.query=$query") ++ filterLines ++ Vector(s"request.facets=${request.requestedFacets.map(_.value).mkString("[", ",", "]")}") ++ sortLines ++ Vector(s"request.cursor=${if (request.page.cursor.isDefined) "present" else "absent"}", s"request.page-size=${request.page.size.value}", s"request.user-location=$location")).mkString("\n")
  }

  private def escape(value: String): String = value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t")
}

object BeautyIntentTrace {
  def render(intent: ParsedBeautyIntentGen2): String = {
    val query = intent.normalizedQuery.map(value => s"\"${escape(value)}\"").getOrElse("absent")
    val hard = intent.hardConstraints.zipWithIndex.map { case (constraint, index) => s"intent.hard[$index] provenance=${SearchPlanTrace.provenance(constraint.provenance)} ${PlannedAlgebraTrace.constraint(constraint.constraint)}" }
    val signals = intent.softSignals.zipWithIndex.map { case (signal, index) => s"intent.soft[$index] ${PlannedAlgebraTrace.signal(signal)}" }
    val labels = intent.canonicalSemanticLabels.map(label => s"${label.stableKey}:${escape(label.text)}").mkString("[", ",", "]")
    (Vector(s"intent.normalized-query=$query", s"intent.matched-rules=${intent.matchedRuleIds.map(_.value).mkString("[", ",", "]")}") ++ hard ++ signals ++ Vector(s"intent.residual=${intent.residualText.map(escape).getOrElse("absent")}", s"intent.labels=$labels")).mkString("\n")
  }

  private def escape(value: String): String = value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t")
}

/** Deterministic generated view of one [[BeautyIntentRule]] declaration. `render` supplies the
  * rendering compared against the independently authored complete current vocabulary/declaration
  * regression golden in `BeautyQIntentVocabularyEvidenceSpec`. The renderer is not itself the source
  * of truth for that golden, whose expected traces are a hand-authored literal vector rather than a
  * value derived from this renderer or any other production traversal helper. Aliases render
  * normalized (through the one shared [[BeautyQIntentTextGen2]] contract) so a normalization
  * regression is visible here too, not only in parser behavior.
  */
object BeautyIntentRuleTrace {
  def render(rule: BeautyIntentRule): String =
    s"rule id=${rule.id.value} aliases=${renderAliases(rule.aliases)} mode=${rule.mode} hard=${renderActions(rule.hardActions)} semantic=${renderActions(rule.semanticActions)} requires=${renderActions(rule.requires)} excludes=${renderActions(rule.excludes)} noise=${rule.noise} label=${renderLabel(rule.canonicalSemanticLabel)}"

  private def renderAliases(aliases: Vector[String]): String = aliases.map(BeautyQIntentTextGen2.normalize).mkString("[", ",", "]")

  private def renderActions(actions: Vector[BeautyIntentAction]): String = actions.map(renderAction).mkString("[", ",", "]")

  private def renderAction(action: BeautyIntentAction): String =
    action match {
      case BeautyIntentAction.Service(code)                  => s"service(${code.value})"
      case BeautyIntentAction.ServiceAny(codes)               => s"service-any(${codes.map(_.value).mkString(",")})"
      case BeautyIntentAction.Category(code)                  => s"category(${code.value})"
      case BeautyIntentAction.EnumAttribute(code, value)      => s"enum($code=$value)"
      case BeautyIntentAction.EnumAttributeAny(code, values)  => s"enum-any($code=${values.mkString(",")})"
      case BeautyIntentAction.BooleanAttribute(code, value)   => s"bool($code=$value)"
      case BeautyIntentAction.IntAttribute(code, value)       => s"int($code=$value)"
      case BeautyIntentAction.NearUser                         => "near-user"
    }

  private def renderLabel(label: Option[CanonicalSemanticLabel]): String =
    label match {
      case Some(value) => value.stableKey
      case None        => "absent"
    }
}
