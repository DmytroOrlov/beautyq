package leaderboard.search.beautyq.gen2.wiring

import org.scalatest.wordspec.AnyWordSpec

final class BeautyQSupplementStartupPolicySpec extends AnyWordSpec {

  "SupplementStartupPolicy.fromStableCode" should {
    "parse required" in {
      SupplementStartupPolicy.fromStableCode("required") match {
        case Right(SupplementStartupPolicy.Required) => succeed
        case other => fail(s"expected Required, got $other")
      }
    }

    "parse preferred" in {
      SupplementStartupPolicy.fromStableCode("preferred") match {
        case Right(SupplementStartupPolicy.Preferred) => succeed
        case other => fail(s"expected Preferred, got $other")
      }
    }

    "parse disabled" in {
      SupplementStartupPolicy.fromStableCode("disabled") match {
        case Right(SupplementStartupPolicy.Disabled) => succeed
        case other => fail(s"expected Disabled, got $other")
      }
    }

    "reject empty string" in {
      SupplementStartupPolicy.fromStableCode("") match {
        case Left(error) => assert(error.getMessage.contains("empty"))
        case other => fail(s"expected Left, got $other")
      }
    }

    "reject whitespace in code" in {
      SupplementStartupPolicy.fromStableCode(" required") match {
        case Left(error) => assert(error.getMessage.contains("whitespace"))
        case other => fail(s"expected Left, got $other")
      }
    }

    "reject unknown code" in {
      SupplementStartupPolicy.fromStableCode("unknown") match {
        case Left(error) => assert(error.getMessage.contains("unknown"))
        case other => fail(s"expected Left, got $other")
      }
    }
  }

  "SupplementStartupPolicy.stableCode" should {
    "return exact stable codes" in {
      assert(SupplementStartupPolicy.Required.stableCode == "required")
      assert(SupplementStartupPolicy.Preferred.stableCode == "preferred")
      assert(SupplementStartupPolicy.Disabled.stableCode == "disabled")
    }
  }

  "SupplementStartupPolicy.ordered" should {
    "be Required, Preferred, Disabled" in {
      assert(SupplementStartupPolicy.ordered == Vector(
        SupplementStartupPolicy.Required,
        SupplementStartupPolicy.Preferred,
        SupplementStartupPolicy.Disabled,
      ))
    }
  }

  "SupplementStartupPolicy.Default" should {
    "be Required" in {
      assert(SupplementStartupPolicy.Default == SupplementStartupPolicy.Required)
    }
  }

  "BeautyQServingMode.modeCode" should {
    "return exact mode codes" in {
      assert(BeautyQServingMode.FullSearch.modeCode == "full_search")
      assert(BeautyQServingMode.BaselineOnly.modeCode == "baseline_only")
    }
  }

  "StartupServingStatus" should {
    "report supplementReady true for FullSearch" in {
      val status = BeautyQStartupServingStatusTestFixtures.fullSearch
      assert(status.supplementReady)
      assert(status.servingMode == BeautyQServingMode.FullSearch)
      assert(!status.restartRequired)
    }

    "report Preferred degraded BaselineOnly with a typed reason" in {
      val status = BeautyQStartupServingStatusTestFixtures.degradedBaseline
      assert(status.policy == SupplementStartupPolicy.Preferred)
      assert(!status.supplementReady)
      assert(status.servingMode == BeautyQServingMode.BaselineOnly)
      assert(status.condition == "degraded")
      assert(status.restartRequired)
      status.reason match {
        case Some(reason) =>
          assert(reason.code == "qdrant_supplement_unavailable")
          assert(reason.typedCause.nonEmpty)
        case None => fail("expected a typed degraded reason")
      }
    }

    "report Disabled limited BaselineOnly" in {
      val status = BeautyQStartupServingStatusTestFixtures.disabledBaseline
      assert(status.policy == SupplementStartupPolicy.Disabled)
      assert(!status.supplementReady)
      assert(status.servingMode == BeautyQServingMode.BaselineOnly)
      assert(status.condition == "limited")
      assert(status.restartRequired)
    }

    "carry elasticsearch reference and physical target" in {
      val status = BeautyQStartupServingStatusTestFixtures.fullSearch
      assert(status.elasticsearchReference == "beautyq-status-test-generation")
      assert(status.elasticsearchPhysicalTarget == "beautyq-status-test-target")
      assert(status.sourceContentFingerprint == BeautyQStartupServingStatusTestFixtures.materialized.sourceSnapshot.contentFingerprint.value)
      assert(status.projectedDocumentsFingerprint == BeautyQStartupServingStatusTestFixtures.materialized.projectedDocumentsFingerprint.value)
      assert(status.qdrantGenerationId.exists(_.nonEmpty))
      assert(status.qdrantPhysicalCollection.exists(_.nonEmpty))
    }
  }
}
