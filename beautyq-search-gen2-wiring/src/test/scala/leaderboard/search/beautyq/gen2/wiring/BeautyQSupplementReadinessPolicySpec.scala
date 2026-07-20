package leaderboard.search.beautyq.gen2.wiring

import BeautyQSupplementReadinessPolicy.*
import org.scalatest.wordspec.AnyWordSpec

final class BeautyQSupplementReadinessPolicySpec extends AnyWordSpec {

  "BeautyQSupplementReadinessPolicy.evaluate" should {
    "return FullSearch when all dependencies available" in {
      BeautyQSupplementReadinessPolicy.evaluate(Set.empty) match {
        case s @ Serving(BeautyQServingMode.FullSearch) =>
          assert(s.mode == BeautyQServingMode.FullSearch)
          assert(s.supplementReady)
        case other => fail(s"expected Serving(FullSearch), got $other")
      }
    }

    "return BaselineOnly when only Qdrant unavailable" in {
      BeautyQSupplementReadinessPolicy.evaluate(Set(BeautyQSearchDependency.QdrantSupplement)) match {
        case s @ Serving(BeautyQServingMode.BaselineOnly) =>
          assert(s.mode == BeautyQServingMode.BaselineOnly)
          assert(!s.supplementReady)
        case other => fail(s"expected Serving(BaselineOnly), got $other")
      }
    }

    "return NotServing when ES baseline unavailable" in {
      BeautyQSupplementReadinessPolicy.evaluate(Set(BeautyQSearchDependency.ElasticsearchBaseline)) match {
        case s @ NotServing(Vector(BeautyQSearchDependency.ElasticsearchBaseline)) =>
          assert(s.unavailableRequired == Vector(BeautyQSearchDependency.ElasticsearchBaseline))
        case other => fail(s"expected NotServing([ElasticsearchBaseline]), got $other")
      }
    }

    "return NotServing when document lookup unavailable" in {
      BeautyQSupplementReadinessPolicy.evaluate(Set(BeautyQSearchDependency.DocumentLookup)) match {
        case s @ NotServing(Vector(BeautyQSearchDependency.DocumentLookup)) =>
          assert(s.unavailableRequired == Vector(BeautyQSearchDependency.DocumentLookup))
        case other => fail(s"expected NotServing([DocumentLookup]), got $other")
      }
    }

    "return NotServing with both required unavailable in declared order" in {
      BeautyQSupplementReadinessPolicy.evaluate(
        Set(BeautyQSearchDependency.ElasticsearchBaseline, BeautyQSearchDependency.DocumentLookup),
      ) match {
        case s @ NotServing(Vector(BeautyQSearchDependency.ElasticsearchBaseline, BeautyQSearchDependency.DocumentLookup)) =>
          assert(s.unavailableRequired == Vector(BeautyQSearchDependency.ElasticsearchBaseline, BeautyQSearchDependency.DocumentLookup))
        case other => fail(s"expected NotServing([ES, DocLookup]), got $other")
      }
    }

    "return NotServing regardless of Qdrant when required dependency unavailable" in {
      BeautyQSupplementReadinessPolicy.evaluate(
        Set(BeautyQSearchDependency.ElasticsearchBaseline, BeautyQSearchDependency.QdrantSupplement),
      ) match {
        case s @ NotServing(Vector(BeautyQSearchDependency.ElasticsearchBaseline)) =>
          assert(s.unavailableRequired == Vector(BeautyQSearchDependency.ElasticsearchBaseline))
        case other => fail(s"expected NotServing([ElasticsearchBaseline]), got $other")
      }
    }

    "not derive order from enum inventory" in {
      // The declared order is explicit in requiredDependencies
      assert(BeautyQSupplementPolicy.requiredDependencies == Vector(
        BeautyQSearchDependency.ElasticsearchBaseline,
        BeautyQSearchDependency.DocumentLookup,
      ))
    }
  }
}
