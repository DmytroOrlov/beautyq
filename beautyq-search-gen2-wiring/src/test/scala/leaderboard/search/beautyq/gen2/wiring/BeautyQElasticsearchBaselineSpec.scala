package leaderboard.search.beautyq.gen2.wiring

import leaderboard.search.beautyq.gen2.contract.*
import leaderboard.search.gen2.contract.*
import org.scalatest.wordspec.AnyWordSpec

/** Concrete BeautyQ proof that the baseline facade compiles a real `CompiledBeautyQSearchPlan` through the
  * generic request compiler - never a hand-maintained parallel request. Response and lifecycle-boundary
  * behavior is covered by neutral decoder and lifecycle specs because executable targets are not domain-test
  * construction values.
  */
final class BeautyQElasticsearchBaselineSpec extends AnyWordSpec {

  private val allFacetIds = Vector(FacetId("service"), FacetId("category"), FacetId("price"), FacetId("durationMinutes"))

  private def compiledPlanOf(requestedFacets: Vector[FacetId], cursor: Option[SearchCursor] = None): CompiledBeautyQSearchPlan = {
    val vocabulary = BeautyQIntentVocabulary.value
    val page       = PageRequest(cursor, PageSize.from(20).getOrElse(fail("expected a valid PageSize")))
    val raw        = BeautySearchRequestGen2(None, Vector.empty, requestedFacets, Vector.empty, page, None)

    val validated = BeautySearchRequestGen2.validate(raw) match {
      case Right(value) => value
      case Left(errors) => fail(s"expected a valid fixture request, got ${errors.toVector}")
    }
    val intent = BeautyQIntentParserGen2.parse(validated, vocabulary) match {
      case Right(value) => value
      case Left(errors) => fail(s"expected a valid fixture intent, got ${errors.toVector}")
    }
    BeautyQSearchPlanCompiler.compile(validated, intent) match {
      case Right(value) => value
      case Left(errors) => fail(s"expected a valid fixture plan, got ${errors.toVector}")
    }
  }

  private def compileRequestOrFail(compiledPlan: CompiledBeautyQSearchPlan): CompiledBeautyQElasticsearchSearchRequest =
    BeautyQElasticsearchBaseline.compileRequest(compiledPlan) match {
      case Right(prepared) => prepared
      case Left(error)     => fail(s"expected successful compilation, got $error")
    }

  "BeautyQElasticsearchBaseline.compileRequest" should {
    "compile a real CompiledBeautyQSearchPlan.boundPlan through the generic request compiler" in {
      val request = compileRequestOrFail(compiledPlanOf(Vector.empty))
      assert(request.body.asObject.exists(_.contains("query")))
    }

    "compile the price facet as a named filters (interval overlap) aggregation" in {
      val request = compileRequestOrFail(compiledPlanOf(Vector(FacetId("price"))))
      assert(request.body.hcursor.downField("aggs").downField("facet:price").downField("filters").succeeded)
    }

    "compile the service and category facets over their stable code fields" in {
      val request = compileRequestOrFail(compiledPlanOf(Vector(FacetId("service"), FacetId("category"))))
      assert(request.body.hcursor.downField("aggs").downField("facet:service").downField("terms").downField("field").as[String].contains("serviceCode"))
      assert(request.body.hcursor.downField("aggs").downField("facet:category").downField("terms").downField("field").as[String].contains("categoryCode"))
    }

    "compile all four BeautyQ facets with their stable IDs" in {
      val request  = compileRequestOrFail(compiledPlanOf(allFacetIds))
      val aggNames = request.body.hcursor.downField("aggs").focus.flatMap(_.asObject).map(_.keys.toSet).getOrElse(Set.empty)
      assert(aggNames == Set("facet:service", "facet:category", "facet:price", "facet:durationMinutes"))
    }
  }

}
