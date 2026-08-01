package leaderboard.search.beautyq.gen2.eval

import io.circe.Json
import java.nio.file.{Files, Paths}
import org.scalatest.wordspec.AnyWordSpec

final class BeautyQProtectedAcceptancePolicySpec extends AnyWordSpec {
  "BeautyQ protected acceptance policy" should {
    "decode strict ordered requirements and retain a canonical fingerprint" in {
      val policy = decode(policyJson())
      assert(policy.evaluationPolicyVersion == BeautyQEvaluationPolicy.CurrentVersion)
      assert(policy.protectedAcceptancePolicyVersion == "beautyq-protected-acceptance-policy-v1")
      assert(policy.requiredSliceMinimums.map(_.sliceId.value) == Vector("smoke"))
      assert(policy.requiredMetricMinimums.map(_.observationKey) == Vector("protected-global", "protected-global"))
      assert(policy.requiredMetricMinimums.map(_.scope.metricId.value) == Vector("success", "mrr"))
      assert(policy.requiredMetricMinimums.map(_.scope.cutoff.value) == Vector(1, 3))
      val roundTripJson = BeautyQProtectedAcceptancePolicy.fromJson(policy.canonicalJson) match {
        case Right(roundTrip) => roundTrip.canonicalJson
        case Left(error) => fail(s"expected canonical policy round trip, got $error")
      }
      assert(policy.canonicalJson == roundTripJson)
      assert(policy.fingerprint.nonEmpty)
    }

    "preserve array declaration order" in {
      val json = policyJson(
        slices = Vector(
          sliceObj("bravo", 1),
          sliceObj("alpha", 2),
        ),
        metrics = Vector(
          metricObj("key-2", "variants", "mrr", 3, "0.250000000000"),
          metricObj("key-1", "variants", "success", 1, "0.500000000000"),
        ),
      )
      val policy = decode(json)
      assert(policy.requiredSliceMinimums.map(_.sliceId.value) == Vector("bravo", "alpha"))
      assert(policy.requiredMetricMinimums.map(_.observationKey) == Vector("key-2", "key-1"))
    }

    "reject empty slice inventory" in {
      val json = policyJson(slices = Vector.empty)
      assert(BeautyQProtectedAcceptancePolicy.fromJson(json).isLeft)
    }

    "reject empty metric inventory" in {
      val json = policyJson(metrics = Vector.empty)
      assert(BeautyQProtectedAcceptancePolicy.fromJson(json).isLeft)
    }

    "reject duplicate slice ID" in {
      val json = policyJson(slices = Vector(
        sliceObj("smoke", 1),
        sliceObj("smoke", 2),
      ))
      assert(BeautyQProtectedAcceptancePolicy.fromJson(json).isLeft)
    }

    "reject duplicate observation key and scope" in {
      val json = policyJson(metrics = Vector(
        metricObj("protected-global", "variants", "success", 1, "0.500000000000"),
        metricObj("protected-global", "variants", "success", 1, "0.750000000000"),
      ))
      assert(BeautyQProtectedAcceptancePolicy.fromJson(json).isLeft)
    }

    "reject blank observation key" in {
      val json = policyJson(metrics = Vector(
        metricObj(" ", "variants", "success", 1, "0.500000000000"),
      ))
      assert(BeautyQProtectedAcceptancePolicy.fromJson(json).isLeft)
    }

    "reject unknown root and nested fields" in {
      val unknown = policyJson().mapObject(_.add("unexpected", Json.fromString("x")))
      assert(BeautyQProtectedAcceptancePolicy.fromJson(unknown).isLeft)

      val metrics = policyJson().hcursor.downField("requiredMetricMinimums").values.getOrElse(Vector.empty).toVector
      val unknownNested = metrics.head.mapObject(_.add("extra", Json.fromString("x")))
      val nested = policyJson().mapObject(_.add("requiredMetricMinimums", Json.fromValues(metrics.tail :+ unknownNested)))
      assert(BeautyQProtectedAcceptancePolicy.fromJson(nested).isLeft)
    }

    "reject missing nested field" in {
      val metrics = policyJson().hcursor.downField("requiredMetricMinimums").values.getOrElse(Vector.empty).toVector
      val missing = metrics.head.mapObject(_.remove("minimum"))
      val json = policyJson().mapObject(_.add("requiredMetricMinimums", Json.fromValues(metrics.tail :+ missing)))
      assert(BeautyQProtectedAcceptancePolicy.fromJson(json).isLeft)
    }

    "reject wrong nested type" in {
      val json = policyJson().mapObject(_.add("expectedCaseCount", Json.fromString("2")))
      assert(BeautyQProtectedAcceptancePolicy.fromJson(json).isLeft)
    }

    "reject unsupported schema version" in {
      val json = policyJson().mapObject(_.add("schemaVersion", Json.fromString("wrong-schema")))
      assert(BeautyQProtectedAcceptancePolicy.fromJson(json).isLeft)
    }

    "reject invalid fingerprint" in {
      val json = policyJson().mapObject(_.add("expectedCorpusFingerprint", Json.fromString("not-a-fingerprint")))
      assert(BeautyQProtectedAcceptancePolicy.fromJson(json).isLeft)
    }

    "reject non-positive case count" in {
      val json = policyJson().mapObject(_.add("expectedCaseCount", Json.fromInt(0)))
      assert(BeautyQProtectedAcceptancePolicy.fromJson(json).isLeft)
    }

    "reject non-positive slice minimum" in {
      val json = policyJson(slices = Vector(sliceObj("smoke", 0)))
      assert(BeautyQProtectedAcceptancePolicy.fromJson(json).isLeft)
    }

    "reject unsupported cutoff" in {
      val json = policyJson(metrics = Vector(
        metricObj("protected-global", "variants", "success", 999, "0.500000000000"),
      ))
      assert(BeautyQProtectedAcceptancePolicy.fromJson(json).isLeft)
    }

    "reject malformed string decimal" in {
      val json = policyJson(metrics = Vector(
        metricObj("protected-global", "variants", "success", 1, "not-a-number"),
      ))
      assert(BeautyQProtectedAcceptancePolicy.fromJson(json).isLeft)
    }

    "reject decimal scale above 12" in {
      val json = policyJson(metrics = Vector(
        metricObj("protected-global", "variants", "success", 1, "0.5000000000000"),
      ))
      assert(BeautyQProtectedAcceptancePolicy.fromJson(json).isLeft)
    }

    "return a sanitized error for malformed JSON without raw parser prose" in {
      val dir = Paths.get("target/codex-sbt")
      Files.createDirectories(dir)
      val path = dir.resolve("malformed-policy-test.json")
      Files.writeString(path, "{ not valid json", java.nio.charset.StandardCharsets.UTF_8)
      val result = BeautyQProtectedAcceptancePolicy.load(path)
      result match {
        case Left(BeautyQProtectedAcceptancePolicyError.InvalidDocument(message)) =>
          assert(message == "protected policy is not valid JSON")
        case other => fail(s"expected sanitized InvalidDocument, got $other")
      }
      Files.deleteIfExists(path): Unit
    }
  }

  private def decode(json: Json): BeautyQProtectedAcceptancePolicy =
    BeautyQProtectedAcceptancePolicy.fromJson(json) match {
      case Right(value) => value
      case Left(error) => fail(s"expected valid policy, got $error")
    }

  private def policyJson(
    slices: Vector[Json] = Vector(sliceObj("smoke", 1)),
    metrics: Vector[Json] = Vector(
      metricObj("protected-global", "variants", "success", 1, "0.500000000000"),
      metricObj("protected-global", "variants", "mrr", 3, "0.250000000000"),
    ),
    corpusFingerprint: String = "a" * 64,
    caseCount: Int = 2,
  ): Json = Json.obj(
    "schemaVersion" -> Json.fromString(BeautyQProtectedAcceptancePolicy.CurrentSchemaVersion),
    "evaluationPolicyVersion" -> Json.fromString(BeautyQEvaluationPolicy.CurrentVersion),
    "protectedAcceptancePolicyVersion" -> Json.fromString("beautyq-protected-acceptance-policy-v1"),
    "expectedCorpusFingerprint" -> Json.fromString(corpusFingerprint),
    "expectedCaseCount" -> Json.fromInt(caseCount),
    "requiredSliceMinimums" -> Json.fromValues(slices),
    "requiredMetricMinimums" -> Json.fromValues(metrics),
  )

  private def sliceObj(id: String, count: Int): Json = Json.obj(
    "sliceId" -> Json.fromString(id),
    "minimumCaseCount" -> Json.fromInt(count),
  )

  private def metricObj(observationKey: String, surface: String, metric: String, cutoff: Int, minimum: String): Json = Json.obj(
    "observationKey" -> Json.fromString(observationKey),
    "surface" -> Json.fromString(surface),
    "metric" -> Json.fromString(metric),
    "cutoff" -> Json.fromInt(cutoff),
    "minimum" -> Json.fromString(minimum),
  )
}
