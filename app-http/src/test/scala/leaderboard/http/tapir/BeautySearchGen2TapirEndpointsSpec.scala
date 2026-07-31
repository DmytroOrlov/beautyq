package leaderboard.http.tapir

import org.scalatest.wordspec.AnyWordSpec
import sttp.model.Method

final class BeautySearchGen2TapirEndpointsSpec extends AnyWordSpec {
  "BeautySearchGen2TapirEndpoints" should {
    "expose exactly two endpoints: POST search and GET status" in {
      BeautySearchGen2TapirEndpoints.all match {
        case List(post, get) =>
          assert(post eq BeautySearchGen2TapirEndpoints.searchBeautyGen2)
          assert(get eq BeautySearchGen2TapirEndpoints.statusBeautyGen2)
        case other => fail(s"expected exactly two Gen2 endpoints, got $other")
      }
      assert(BeautySearchGen2TapirEndpoints.searchBeautyGen2.method.contains(Method.POST))
      assert(BeautySearchGen2TapirEndpoints.statusBeautyGen2.method.contains(Method.GET))
    }

    "mount the native Gen2 search route at the agreed prefix only" in {
      val path = BeautySearchGen2TapirEndpoints.searchBeautyGen2.input
      val rendered = path.show
      assert(rendered.contains("/beauty-search"))
      assert(!rendered.contains("/beauty-search-gen2"))
    }

    "mount the operator status route at /beauty-search/status" in {
      val path = BeautySearchGen2TapirEndpoints.statusBeautyGen2.input
      val rendered = path.show
      assert(rendered.contains("beauty-search"))
      assert(rendered.contains("status"))
    }
  }
}
