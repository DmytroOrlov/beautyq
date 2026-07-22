package leaderboard.http.tapir

import org.scalatest.wordspec.AnyWordSpec
import sttp.model.Method

final class BeautySearchGen2TapirEndpointsSpec extends AnyWordSpec {
  "BeautySearchGen2TapirEndpoints" should {
    "expose one independent POST endpoint" in {
      BeautySearchGen2TapirEndpoints.all match {
        case List(endpoint) => assert(endpoint eq BeautySearchGen2TapirEndpoints.searchBeautyGen2)
        case other => fail(s"expected exactly one Gen2 endpoint, got $other")
      }
      assert(BeautySearchGen2TapirEndpoints.searchBeautyGen2.method.contains(Method.POST))
    }

    "mount the native Gen2 route at the agreed prefix only" in {
      val path = BeautySearchGen2TapirEndpoints.searchBeautyGen2.input
      val rendered = path.show
      assert(rendered.contains("/beauty-search"))
      assert(!rendered.contains("/beauty-search-gen2"))
    }
  }
}
