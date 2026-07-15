package leaderboard.search.gen2.core

import org.scalatest.wordspec.AnyWordSpec

/** Code outside the owning core.plan package must consume the framework fingerprint, never construct one. */
final class ContractFingerprintBoundarySpec extends AnyWordSpec {
  "ContractFingerprint" should {
    "reject handwritten construction outside the fingerprint owner package" in {
      assertDoesNotCompile("""val forged: leaderboard.search.gen2.core.plan.ContractFingerprint = "handwritten-final-hash"""")
    }
  }
}
