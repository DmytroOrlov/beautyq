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
      val status = new StartupServingStatus(
        policy = SupplementStartupPolicy.Required,
        servingMode = BeautyQServingMode.FullSearch,
        condition = "healthy",
        reason = None,
        restartRequired = false,
        sourceContentFingerprint = "src-fp",
        projectedDocumentsFingerprint = "proj-fp",
        elasticsearchReference = "es-ref",
        elasticsearchPhysicalTarget = "es-target",
        qdrantGenerationId = Some("qdrant-gen"),
        qdrantPhysicalCollection = Some("qdrant-coll"),
      )
      assert(status.supplementReady)
      assert(status.servingMode == BeautyQServingMode.FullSearch)
      assert(!status.restartRequired)
    }

    "report supplementReady false for BaselineOnly" in {
      val status = new StartupServingStatus(
        policy = SupplementStartupPolicy.Preferred,
        servingMode = BeautyQServingMode.BaselineOnly,
        condition = "degraded",
        reason = None,
        restartRequired = true,
        sourceContentFingerprint = "src-fp",
        projectedDocumentsFingerprint = "proj-fp",
        elasticsearchReference = "es-ref",
        elasticsearchPhysicalTarget = "es-target",
        qdrantGenerationId = None,
        qdrantPhysicalCollection = None,
      )
      assert(!status.supplementReady)
      assert(status.servingMode == BeautyQServingMode.BaselineOnly)
      assert(status.restartRequired)
      assert(status.condition == "degraded")
    }

    "preserve immutable condition" in {
      val status = new StartupServingStatus(
        policy = SupplementStartupPolicy.Disabled,
        servingMode = BeautyQServingMode.BaselineOnly,
        condition = "limited",
        reason = None,
        restartRequired = true,
        sourceContentFingerprint = "src-fp",
        projectedDocumentsFingerprint = "proj-fp",
        elasticsearchReference = "es-ref",
        elasticsearchPhysicalTarget = "es-target",
        qdrantGenerationId = None,
        qdrantPhysicalCollection = None,
      )
      assert(status.condition == "limited")
      assert(status.restartRequired)
    }

    "carry elasticsearch reference and physical target" in {
      val status = new StartupServingStatus(
        policy = SupplementStartupPolicy.Required,
        servingMode = BeautyQServingMode.FullSearch,
        condition = "healthy",
        reason = None,
        restartRequired = false,
        sourceContentFingerprint = "src-v1",
        projectedDocumentsFingerprint = "proj-v1",
        elasticsearchReference = "generation-2024-01-01",
        elasticsearchPhysicalTarget = "beautyq-gen-2024-01-01",
        qdrantGenerationId = Some("qdrant-1"),
        qdrantPhysicalCollection = Some("collection-1"),
      )
      assert(status.elasticsearchReference == "generation-2024-01-01")
      assert(status.elasticsearchPhysicalTarget == "beautyq-gen-2024-01-01")
      assert(status.sourceContentFingerprint == "src-v1")
      assert(status.projectedDocumentsFingerprint == "proj-v1")
      assert(status.qdrantGenerationId.contains("qdrant-1"))
      assert(status.qdrantPhysicalCollection.contains("collection-1"))
    }
  }
}
