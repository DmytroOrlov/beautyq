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
  }
}
