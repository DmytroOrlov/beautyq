package leaderboard.api

import org.scalatest.wordspec.AnyWordSpec

final class BeautySearchServingGateSpec extends AnyWordSpec {

  "BeautySearchServingGate.disabled" should {
    "not reject serving" in {
      assert(BeautySearchServingGate.disabled.rejectsServing == false)
    }
  }

  "BeautySearchServingGate.enabledNotReady" should {
    "reject serving" in {
      assert(BeautySearchServingGate.enabledNotReady.rejectsServing == true)
    }
  }

  "BeautySearchServingGate.enabledReady" should {
    "not reject serving" in {
      assert(BeautySearchServingGate.enabledReady.rejectsServing == false)
    }
  }

  "BeautySearchServingGate" should {
    "not reject when disabled and not ready" in {
      assert(BeautySearchServingGate(enabled = false, servingReady = false).rejectsServing == false)
    }

    "not reject when disabled and ready" in {
      assert(BeautySearchServingGate(enabled = false, servingReady = true).rejectsServing == false)
    }

    "reject when enabled and not ready" in {
      assert(BeautySearchServingGate(enabled = true, servingReady = false).rejectsServing == true)
    }

    "not reject when enabled and ready" in {
      assert(BeautySearchServingGate(enabled = true, servingReady = true).rejectsServing == false)
    }
  }
}
