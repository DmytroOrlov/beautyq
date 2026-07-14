package leaderboard.search.beautyq.gen2.wiring

import leaderboard.search.beautyq.gen2.contract.*
import leaderboard.search.gen2.contract.*
import org.scalatest.wordspec.AnyWordSpec

/** Exact golden traces built from real validated requests, parsed intents, compiled plans and the bound
  * candidate evaluation. The expected strings are literal and describe only facts owned by that one
  * evaluation; policy declarations are rendered by the canonical domain structure, not re-read here.
  */
final class BeautyQCandidatePlanTraceSpec extends AnyWordSpec {

  private val vocabulary = BeautyQIntentVocabulary.value
  private val page = PageRequest(None, PageSize.from(20).getOrElse(fail("expected a valid PageSize")))
  private val berlin = GeoPoint(BigDecimal("52.5"), BigDecimal("13.4"))

  private def rawRequest(
    query: Option[String] = None,
    sort: Vector[BeautySortInput] = Vector.empty,
    userLocation: Option[GeoPoint] = None,
  ): BeautySearchRequestGen2 =
    BeautySearchRequestGen2(query, Vector.empty, Vector.empty, sort, page, userLocation)

  private def traceOf(request: BeautySearchRequestGen2): String = {
    val validated = BeautySearchRequestGen2.validate(request) match {
      case Right(value) => value
      case Left(errors) => fail(s"fixture request failed to validate: ${errors.toVector}")
    }
    val intent = BeautyQIntentParserGen2.parse(validated, vocabulary) match {
      case Right(value) => value
      case Left(errors) => fail(s"fixture intent failed to parse: ${errors.toVector}")
    }
    val compiled = BeautyQSearchPlanCompiler.compile(validated, intent) match {
      case Right(value) => value
      case Left(errors) => fail(s"fixture failed to compile: ${errors.toVector}")
    }
    val evaluation = BeautyQCandidatePlanCompiler.compile(compiled) match {
      case Right(value) => value
      case Left(error)  => fail(s"fixture failed to evaluate: $error")
    }
    BeautyQCandidatePlanTrace.render(evaluation)
  }

  private val eligibleGates =
    """=== semantic ===
      |semantic.part[0] id=residual-text
      |semantic.part[1] id=canonical-semantic-labels
      |
      |=== eligibility ===
      |eligibility.gate[0] id=semantic-query-text outcome=passed
      |eligibility.gate[1] id=first-page outcome=passed
      |eligibility.gate[2] id=default-sort outcome=passed
      |
      |=== decision ===""".stripMargin

  "BeautyQCandidatePlanTrace.render" should {
    "render residual text alone as an eligible plan with no hard constraints" in {
      assert(
        traceOf(rawRequest(query = Some("something entirely unmatched"))) ==
          s"""$eligibleGates
             |decision.eligible semantic-text=\"something entirely unmatched\"""".stripMargin
      )
    }

    "render one canonical label alone as an eligible plan with its one hard constraint" in {
      assert(
        traceOf(rawRequest(query = Some("ресницы"))) ==
          s"""$eligibleGates
             |decision.eligible semantic-text=\"lashes\"
             |decision.hard-constraint[0] constraint.terms field=serviceCode:ServiceCode values=[lashes]""".stripMargin
      )
    }

    "render residual text plus multiple canonical labels together, residual first" in {
      assert(
        traceOf(rawRequest(query = Some("ногти zzz"))) ==
          s"""$eligibleGates
             |decision.eligible semantic-text=\"zzz nails manicure pedicure nail modeling\"
             |decision.hard-constraint[0] constraint.terms field=categoryCode:CategoryCode values=[nails]""".stripMargin
      )
    }

    "render an empty default-browse request as ineligible with no semantic query text" in {
      assert(
        traceOf(rawRequest()) ==
          """=== semantic ===
            |semantic.part[0] id=residual-text
            |semantic.part[1] id=canonical-semantic-labels
            |
            |=== eligibility ===
            |eligibility.gate[0] id=semantic-query-text outcome=rejected reason=no-semantic-query-text
            |eligibility.gate[1] id=first-page outcome=passed
            |eligibility.gate[2] id=default-sort outcome=passed
            |
            |=== decision ===
            |decision.ineligible reason=no-semantic-query-text""".stripMargin
      )
    }

    "render semantic text with an explicit price sort as ineligible with a non-default sort" in {
      assert(
        traceOf(rawRequest(query = Some("ресницы"), sort = Vector(BeautySortInput(PublicSortName("price"), SortDirection.Asc)))) ==
          """=== semantic ===
            |semantic.part[0] id=residual-text
            |semantic.part[1] id=canonical-semantic-labels
            |
            |=== eligibility ===
            |eligibility.gate[0] id=semantic-query-text outcome=passed
            |eligibility.gate[1] id=first-page outcome=passed
            |eligibility.gate[2] id=default-sort outcome=rejected reason=non-default-sort
            |
            |=== decision ===
            |decision.ineligible reason=non-default-sort""".stripMargin
      )
    }

    "render semantic text with an explicit geo-distance sort as ineligible with a non-default sort" in {
      assert(
        traceOf(rawRequest(query = Some("ресницы"), sort = Vector(BeautySortInput(PublicSortName("distanceMeters"), SortDirection.Asc)), userLocation = Some(berlin))) ==
          """=== semantic ===
            |semantic.part[0] id=residual-text
            |semantic.part[1] id=canonical-semantic-labels
            |
            |=== eligibility ===
            |eligibility.gate[0] id=semantic-query-text outcome=passed
            |eligibility.gate[1] id=first-page outcome=passed
            |eligibility.gate[2] id=default-sort outcome=rejected reason=non-default-sort
            |
            |=== decision ===
            |decision.ineligible reason=non-default-sort""".stripMargin
      )
    }

    "render an eligible plan with multiple hard constraints in order" in {
      assert(
        traceOf(rawRequest(query = Some("маникюр under 50"))) ==
          s"""$eligibleGates
             |decision.eligible semantic-text=\"manicure nail service type manicure\"
             |decision.hard-constraint[0] constraint.terms field=serviceCode:ServiceCode values=[manicure]
             |decision.hard-constraint[1] constraint.terms field=enumAttributes.nail_service_type:string values=[manicure]
             |decision.hard-constraint[2] constraint.interval-overlap from=priceFrom:decimal to=priceTo:decimal bounds=[unbounded, inclusive(50)]""".stripMargin
      )
    }

    "render no semantic query text plus an explicit sort with no-semantic-query-text, proving gate order" in {
      assert(
        traceOf(rawRequest(sort = Vector(BeautySortInput(PublicSortName("price"), SortDirection.Asc)))) ==
          """=== semantic ===
            |semantic.part[0] id=residual-text
            |semantic.part[1] id=canonical-semantic-labels
            |
            |=== eligibility ===
            |eligibility.gate[0] id=semantic-query-text outcome=rejected reason=no-semantic-query-text
            |eligibility.gate[1] id=first-page outcome=passed
            |eligibility.gate[2] id=default-sort outcome=rejected reason=non-default-sort
            |
            |=== decision ===
            |decision.ineligible reason=no-semantic-query-text""".stripMargin
      )
    }
  }
}
