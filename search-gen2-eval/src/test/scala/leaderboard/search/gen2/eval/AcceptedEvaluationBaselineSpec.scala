package leaderboard.search.gen2.eval

import org.scalatest.wordspec.AnyWordSpec
import io.circe.Json

final class AcceptedEvaluationBaselineSpec extends AnyWordSpec {
  private def provId(raw: String): EvaluationProvenanceId = EvaluationProvenanceId.from(raw).getOrElse(fail(s"invalid provenance id: $raw"))
  private def surface(raw: String): EvaluationSurfaceId = EvaluationSurfaceId.from(raw).getOrElse(fail(s"invalid surface id: $raw"))
  private def metric(raw: String): EvaluationMetricId = EvaluationMetricId.from(raw).getOrElse(fail(s"invalid metric id: $raw"))

  private def sampleBaseline: AcceptedEvaluationBaseline = baselineWithAverage(BigDecimal("1.000000000000"))

  private def baselineWithAverage(average: BigDecimal): AcceptedEvaluationBaseline = {
    val provenance = ProvenanceComponent.from(provId("test-provenance"), "test-value").getOrElse(fail("valid provenance expected"))
    val scope = MetricKeyScope.from(surface("variants"), metric("success"), EvaluationCutoff.from(1).getOrElse(fail("valid cutoff")))
    val observation = AggregateMetricObservation.from(scope, average, 1, 0)
    val section = AggregateSection.from(0, 0, 0, 0, 1, 0, Vector(observation))
    AcceptedEvaluationBaseline.create("a" * 64, "metric-schema-v1", "policy-v1", Vector(provenance), "b" * 64, Vector("global" -> section)).getOrElse(fail("valid baseline expected"))
  }

  private def singleAverage(baseline: AcceptedEvaluationBaseline): BigDecimal = {
    val section = baseline.aggregateObservations.map(_._2) match {
      case Vector(only) => only
      case other => fail(s"expected exactly one aggregate section, got ${other.size}")
    }
    section.metricObservations match {
      case Vector(only) => only.average
      case other => fail(s"expected exactly one metric observation, got ${other.size}")
    }
  }

  "AcceptedBaselineCodec" should {
    "keep derived values outside direct construction and copy boundaries" in {
      assertDoesNotCompile("new AcceptedEvaluationBaseline(\"x\", \"x\", \"x\", \"x\", Vector.empty, \"x\", Vector.empty)")
      assertDoesNotCompile("val baseline: AcceptedEvaluationBaseline = ???; baseline.copy()")
      assertDoesNotCompile("final class Forged extends AcceptedEvaluationBaseline(???, ???, ???, ???, ???, ???, ???)")
      assertDoesNotCompile("new AggregateSection(0, 0, 0, 0, 0, 0, Vector.empty)")
      assertDoesNotCompile("new MetricDelta(???, ???, ???, BigDecimal(0), BigDecimal(0), BigDecimal(0))")
      assertDoesNotCompile("new RankingEvaluationResult(???, ???, ???, ???, ???)")
      assertDoesNotCompile("new MetricValue.Applicable(BigDecimal(0))")
      assertDoesNotCompile("new MetricValue.NotApplicable(NotApplicableReason.RequiresPartial)")
      assertDoesNotCompile("new EvaluationReport(???, ???, ???, ???, ???, ???, ???)")
      assertDoesNotCompile("new CaseEvaluationResult(???, ???, ???, ???, ???)")
      assertDoesNotCompile("new SurfaceCaseResult(???, ???, ???, ???, ???)")
      assertDoesNotCompile("new ComparisonResult(???)")
    }

    "round-trip deterministically with ordered observations" in {
      val encoded = AcceptedBaselineCodec.encode(sampleBaseline)
      AcceptedBaselineCodec.decode(encoded) match {
        case Right(decoded) => assert(AcceptedBaselineCodec.encode(decoded).noSpaces == encoded.noSpaces)
        case Left(error) => fail(s"decode failed: $error")
      }
    }

    "normalize a JSON-zero average to the owner's canonical scale-12 encoding" in {
      val canonicalZero = AcceptedBaselineCodec.encode(baselineWithAverage(BigDecimal(0).setScale(12)))
      assert(canonicalZero.noSpaces.contains("\"average\":0E-12"))
      AcceptedBaselineCodec.decode(canonicalZero) match {
        case Right(decoded) =>
          assert(singleAverage(decoded).scale == 12)
          assert(AcceptedBaselineCodec.encode(decoded).noSpaces == canonicalZero.noSpaces)
        case Left(error) => fail(s"expected owner-encoded zero average to decode, got $error")
      }
      val parsedZero = AcceptedBaselineCodec.encode(baselineWithAverage(BigDecimal(0)))
      assert(parsedZero.noSpaces.contains("\"average\":0,\"applicableCount\""))
      AcceptedBaselineCodec.decode(parsedZero) match {
        case Right(decoded) =>
          val average = singleAverage(decoded)
          assert(average.toString == "0E-12")
          assert(average.scale == 12)
          assert(AcceptedBaselineCodec.encode(decoded).noSpaces == canonicalZero.noSpaces)
        case Left(error) => fail(s"expected numerically zero average to decode, got $error")
      }
    }

    "normalize a numerically exact short-scale average to canonical scale 12" in {
      val encoded = AcceptedBaselineCodec.encode(baselineWithAverage(BigDecimal("1.0")))
      assert(encoded.noSpaces.contains("\"average\":1.0,"))
      AcceptedBaselineCodec.decode(encoded) match {
        case Right(decoded) =>
          val average = singleAverage(decoded)
          assert(average.toString == "1.000000000000")
          assert(average.scale == 12)
          assert(AcceptedBaselineCodec.encode(decoded).noSpaces.contains("\"average\":1.000000000000,"))
        case Left(error) => fail(s"expected short-scale exact average to decode, got $error")
      }
    }

    "reject unknown nested fields" in {
      val baseline = sampleBaseline
      val encoded = AcceptedBaselineCodec.encode(baseline)
      val root = encoded.asObject.getOrElse(fail("expected object"))
      val provenance = root("provenance").flatMap(_.asArray).flatMap(_.headOption).flatMap(_.asObject).getOrElse(fail("expected provenance"))
      val changed = provenance.add("extra", Json.fromString("bad"))
      val updatedProvenance = Json.fromValues(Vector(Json.fromJsonObject(changed)))
      AcceptedBaselineCodec.decode(Json.fromJsonObject(root.add("provenance", updatedProvenance))) match {
        case Left(AcceptedBaselineDecodeError.UnexpectedFields(_, _)) => ()
        case other => fail(s"expected nested UnexpectedFields, got $other")
      }
    }

    "reject duplicate observation keys in the ordered array" in {
      val encoded = AcceptedBaselineCodec.encode(sampleBaseline)
      val root = encoded.asObject.getOrElse(fail("expected object"))
      val observation = root("aggregateObservations").flatMap(_.asArray).flatMap(_.headOption).getOrElse(fail("expected observation"))
      AcceptedBaselineCodec.decode(Json.fromJsonObject(root.add("aggregateObservations", Json.fromValues(Vector(observation, observation))))) match {
        case Left(AcceptedBaselineDecodeError.DuplicateObservationKey("global")) => ()
        case other => fail(s"expected duplicate observation error, got $other")
      }
    }

    "reject a wrong nested count type instead of dropping it" in {
      val encoded = AcceptedBaselineCodec.encode(sampleBaseline)
      val root = encoded.asObject.getOrElse(fail("expected object"))
      val observations = root("aggregateObservations").flatMap(_.asArray).flatMap(_.headOption).flatMap(_.asObject).getOrElse(fail("expected observation"))
      val section = observations("section").flatMap(_.asObject).getOrElse(fail("expected section"))
      val metrics = section("metricObservations").flatMap(_.asArray).flatMap(_.headOption).flatMap(_.asObject).getOrElse(fail("expected metric"))
      val badMetric = Json.fromJsonObject(metrics.add("applicableCount", Json.fromString("one")))
      val badSection = Json.fromJsonObject(section.add("metricObservations", Json.fromValues(Vector(badMetric))))
      val badObservation = Json.fromJsonObject(observations.add("section", badSection))
      AcceptedBaselineCodec.decode(Json.fromJsonObject(root.add("aggregateObservations", Json.fromValues(Vector(badObservation))))) match {
        case Left(AcceptedBaselineDecodeError.MissingOrInvalidField("applicableCount")) => ()
        case other => fail(s"expected wrong type error, got $other")
      }
    }

    "reject missing root fields, versions and digests" in {
      val encoded = AcceptedBaselineCodec.encode(sampleBaseline)
      val root = encoded.asObject.getOrElse(fail("expected object"))
      AcceptedBaselineCodec.decode(Json.fromJsonObject(root.remove("reportDigest"))) match {
        case Left(AcceptedBaselineDecodeError.UnexpectedFields("root", _)) => ()
        case other => fail(s"expected missing root field error, got $other")
      }
      AcceptedBaselineCodec.decode(Json.fromJsonObject(root.add("schemaVersion", Json.fromString("wrong")))) match {
        case Left(AcceptedBaselineDecodeError.UnsupportedSchemaVersion("wrong")) => ()
        case other => fail(s"expected schema version error, got $other")
      }
      AcceptedBaselineCodec.decode(Json.fromJsonObject(root.add("corpusFingerprint", Json.fromString("bad")))) match {
        case Left(AcceptedBaselineDecodeError.InvalidFingerprintDigest("corpusFingerprint", "bad")) => ()
        case other => fail(s"expected fingerprint error, got $other")
      }
      AcceptedBaselineCodec.decode(Json.fromJsonObject(root.add("reportDigest", Json.fromString("bad")))) match {
        case Left(AcceptedBaselineDecodeError.InvalidFingerprintDigest("reportDigest", "bad")) => ()
        case other => fail(s"expected report digest error, got $other")
      }
    }

    "reject empty and whitespace versions" in {
      val root = AcceptedBaselineCodec.encode(sampleBaseline).asObject.getOrElse(fail("expected object"))
      AcceptedBaselineCodec.decode(Json.fromJsonObject(root.add("metricSchemaVersion", Json.fromString("")))) match {
        case Left(AcceptedBaselineDecodeError.EmptyVersionString("metricSchemaVersion")) => ()
        case other => fail(s"expected empty version error, got $other")
      }
      AcceptedBaselineCodec.decode(Json.fromJsonObject(root.add("metricSchemaVersion", Json.fromString(" v ")))) match {
        case Left(AcceptedBaselineDecodeError.WhitespaceVersionString("metricSchemaVersion")) => ()
        case other => fail(s"expected whitespace version error, got $other")
      }
    }

    "reject duplicate provenance IDs" in {
      val root = AcceptedBaselineCodec.encode(sampleBaseline).asObject.getOrElse(fail("expected object"))
      val provenance = root("provenance").flatMap(_.asArray).flatMap(_.headOption).getOrElse(fail("expected provenance"))
      AcceptedBaselineCodec.decode(Json.fromJsonObject(root.add("provenance", Json.fromValues(Vector(provenance, provenance))))) match {
        case Left(AcceptedBaselineDecodeError.DuplicateProvenanceId("test-provenance")) => ()
        case other => fail(s"expected duplicate provenance error, got $other")
      }
    }

    "reject unknown observation, section and metric fields" in {
      val root = AcceptedBaselineCodec.encode(sampleBaseline).asObject.getOrElse(fail("expected object"))
      val observation = root("aggregateObservations").flatMap(_.asArray).flatMap(_.headOption).flatMap(_.asObject).getOrElse(fail("expected observation"))
      val section = observation("section").flatMap(_.asObject).getOrElse(fail("expected section"))
      val metricObservation = section("metricObservations").flatMap(_.asArray).flatMap(_.headOption).flatMap(_.asObject).getOrElse(fail("expected metric observation"))
      val unknownObservation = Json.fromJsonObject(observation.add("extra", Json.fromString("bad")))
      AcceptedBaselineCodec.decode(Json.fromJsonObject(root.add("aggregateObservations", Json.fromValues(Vector(unknownObservation))))) match {
        case Left(AcceptedBaselineDecodeError.UnexpectedFields(_, _)) => ()
        case other => fail(s"expected unknown observation field error, got $other")
      }
      val unknownSection = Json.fromJsonObject(section.add("extra", Json.fromString("bad")))
      val changedObservation = Json.fromJsonObject(observation.add("section", unknownSection))
      AcceptedBaselineCodec.decode(Json.fromJsonObject(root.add("aggregateObservations", Json.fromValues(Vector(changedObservation))))) match {
        case Left(AcceptedBaselineDecodeError.UnexpectedFields(_, _)) => ()
        case other => fail(s"expected unknown section field error, got $other")
      }
      val unknownMetric = Json.fromJsonObject(metricObservation.add("extra", Json.fromString("bad")))
      val changedSection = Json.fromJsonObject(section.add("metricObservations", Json.fromValues(Vector(unknownMetric))))
      val changedMetricObservation = Json.fromJsonObject(observation.add("section", changedSection))
      AcceptedBaselineCodec.decode(Json.fromJsonObject(root.add("aggregateObservations", Json.fromValues(Vector(changedMetricObservation))))) match {
        case Left(AcceptedBaselineDecodeError.UnexpectedFields(_, _)) => ()
        case other => fail(s"expected unknown metric field error, got $other")
      }
    }

    "reject negative counts, over-precision averages and duplicate metric scopes" in {
      val root = AcceptedBaselineCodec.encode(sampleBaseline).asObject.getOrElse(fail("expected object"))
      val observation = root("aggregateObservations").flatMap(_.asArray).flatMap(_.headOption).flatMap(_.asObject).getOrElse(fail("expected observation"))
      val section = observation("section").flatMap(_.asObject).getOrElse(fail("expected section"))
      val metricObservation = section("metricObservations").flatMap(_.asArray).flatMap(_.headOption).flatMap(_.asObject).getOrElse(fail("expected metric observation"))
      val negativeSection = Json.fromJsonObject(section.add("forbiddenHitCount", Json.fromInt(-1)))
      AcceptedBaselineCodec.decode(Json.fromJsonObject(root.add("aggregateObservations", Json.fromValues(Vector(Json.fromJsonObject(observation.add("section", negativeSection))))))) match {
        case Left(AcceptedBaselineDecodeError.NegativeCount("forbiddenHitCount", -1)) => ()
        case other => fail(s"expected negative count error, got $other")
      }
      val overPrecisionMetric = Json.fromJsonObject(metricObservation.add("average", Json.fromBigDecimal(BigDecimal("0.5000000000001"))))
      val overPrecisionSection = Json.fromJsonObject(section.add("metricObservations", Json.fromValues(Vector(overPrecisionMetric))))
      AcceptedBaselineCodec.decode(Json.fromJsonObject(root.add("aggregateObservations", Json.fromValues(Vector(Json.fromJsonObject(observation.add("section", overPrecisionSection))))))) match {
        case Left(AcceptedBaselineDecodeError.InvalidScale("average", _)) => ()
        case other => fail(s"expected over-precision scale error, got $other")
      }
      val duplicateMetricSection = Json.fromJsonObject(section.add("metricObservations", Json.fromValues(Vector(Json.fromJsonObject(metricObservation), Json.fromJsonObject(metricObservation)))))
      AcceptedBaselineCodec.decode(Json.fromJsonObject(root.add("aggregateObservations", Json.fromValues(Vector(Json.fromJsonObject(observation.add("section", duplicateMetricSection))))))) match {
        case Left(AcceptedBaselineDecodeError.DuplicateObservationKey(_)) => ()
        case other => fail(s"expected duplicate metric scope error, got $other")
      }
    }
  }
}
