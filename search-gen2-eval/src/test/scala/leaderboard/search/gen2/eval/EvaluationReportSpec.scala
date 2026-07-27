package leaderboard.search.gen2.eval

import org.scalatest.wordspec.AnyWordSpec
import io.circe.Json

final class EvaluationReportSpec extends AnyWordSpec {
  private def id(raw: String): EvaluationResultId = EvaluationResultId.from(raw).getOrElse(fail(s"invalid result id: $raw"))
  private def caseId(raw: String): EvaluationCaseId = EvaluationCaseId.from(raw).getOrElse(fail(s"invalid case id: $raw"))
  private def surface(raw: String): EvaluationSurfaceId = EvaluationSurfaceId.from(raw).getOrElse(fail(s"invalid surface id: $raw"))
  private def metric(raw: String): EvaluationMetricId = EvaluationMetricId.from(raw).getOrElse(fail(s"invalid metric id: $raw"))
  private def prov(raw: String): EvaluationProvenanceId = EvaluationProvenanceId.from(raw).getOrElse(fail(s"invalid provenance id: $raw"))

  private def evaluated(partition: EvaluationPartition): RankingEvaluationResult = {
    val judgments = RankingJudgments.from(JudgmentMode.Exhaustive, Vector(id("doc-a")), Vector.empty, Vector.empty, Vector.empty).getOrElse(fail("valid judgments expected"))
    val input = RankingEvaluationInput.from(
      caseId("case-1"), partition, surface("variants"), Vector.empty, judgments,
      Vector(id("doc-a"), id("doc-b")), EvaluationCutoffs.from(Vector(1, 3)).getOrElse(fail("valid cutoffs expected")),
    ).getOrElse(fail("valid input expected"))
    RankingEvaluator.evaluate(input)
  }

  private def reportOf(partition: EvaluationPartition, withSlice: Boolean = false): EvaluationReport = {
    val provenance = ProvenanceComponent.from(prov("test-prov"), "test-value").getOrElse(fail("valid provenance expected"))
    val input = EvaluationReportCaseInput.from(
      caseId("case-1"), partition, JudgmentMode.Exhaustive,
      if (withSlice) Some(Vector(EvaluationSliceId.from("direct").getOrElse(fail("valid slice")))) else None,
      Vector(surface("variants") -> evaluated(partition)),
    ).getOrElse(fail("valid case input expected"))
    EvaluationReportBuilder.build(Vector(provenance), Vector(input))
  }

  private def cutoff(value: Int): EvaluationCutoff = EvaluationCutoff.from(value).getOrElse(fail("valid cutoff"))
  private def scope(metricName: String, value: Int = 1): MetricKeyScope =
    MetricKeyScope.from(surface("variants"), metric(metricName), cutoff(value))
  private def section(observations: Vector[AggregateMetricObservation]): AggregateSection =
    AggregateSection.from(0, 0, 0, 0, observations.map(_.applicableCount).sum, observations.map(_.notApplicableCount).sum, observations)
  private def schema(fingerprint: String = "a" * 64, metricVersion: String = "metrics-v1", policyVersion: String = "policy-v1", scopes: Vector[MetricKeyScope]): ComparisonSchema =
    ComparisonSchema.from(fingerprint, metricVersion, policyVersion, scopes).getOrElse(fail("valid schema"))

  "EvaluationReport" should {
    "encode detailed output deterministically" in {
      val report = reportOf(EvaluationPartition.Regression)
      assert(EvaluationReport.encodeDetailed(report).noSpaces == EvaluationReport.encodeDetailed(report).noSpaces)
    }

    "retain protected aggregates while omitting protected case details" in {
      val report = reportOf(EvaluationPartition.ProtectedHoldout, withSlice = true)
      val json = EvaluationReport.encodeProtected(report)
      def strings(value: Json): Vector[String] =
        value.asString.toVector ++ value.asArray.toVector.flatMap(_.flatMap(strings)) ++ value.asObject.toVector.flatMap(_.values.toVector.flatMap(strings))
      val allStrings = strings(json)
      assert(!allStrings.contains("case-1"))
      assert(!allStrings.contains("doc-a"))
      assert(!allStrings.contains("doc-b"))
      assert(json.hcursor.downField("caseResults").focus.isEmpty)
      assert(json.hcursor.downField("globalAggregates").downField("structuralInvalidCount").as[Int] == Right(0))
      val globalObservations = json.hcursor.downField("globalAggregates").downField("metricObservations").focus.flatMap(_.asArray).getOrElse(fail("expected global observations"))
      val firstAverage = globalObservations.headOption.flatMap(_.hcursor.downField("average").focus.flatMap(_.asNumber).flatMap(_.toBigDecimal)).getOrElse(fail("expected aggregate average"))
      assert(firstAverage == BigDecimal("1.000000000000"))
      assert(json.hcursor.downField("partitionAggregates").focus.flatMap(_.asArray).exists(_.nonEmpty))
      assert(json.hcursor.downField("surfaceAggregates").focus.flatMap(_.asArray).exists(_.nonEmpty))
      assert(json.hcursor.downField("sliceAggregates").focus.flatMap(_.asArray).exists(_.nonEmpty))
    }
  }

  "EvaluationReportDigest" should {
    "produce 64-char lowercase hex" in {
      val digest = EvaluationReportDigest.compute(Json.obj("key" -> Json.fromString("value")))
      assert(digest.matches("^[0-9a-f]{64}$"))
    }
  }

  "EvaluationComparator" should {
    "compare exact ordered scope observations" in {
      val currentScope = scope("success")
      val baselineObservation = AggregateMetricObservation.from(currentScope, BigDecimal("0.500000000000"), 1, 0)
      val candidateObservation = AggregateMetricObservation.from(currentScope, BigDecimal("0.750000000000"), 1, 0)
      val currentSchema = schema(scopes = Vector(currentScope))
      val compared = EvaluationComparator.compare(currentSchema, currentSchema, "rev-1", Vector(surface("variants") -> section(Vector(candidateObservation))), Vector(surface("variants") -> section(Vector(baselineObservation))))
      compared match {
        case Right(result) =>
          result.deltas match {
            case Vector(delta) => assert(delta.delta == BigDecimal("0.250000000000"))
            case other => fail(s"expected one delta, got $other")
          }
        case Left(error) => fail(s"comparison failed: $error")
      }
    }

    "reject a corpus mismatch through the comparator" in {
      val currentScope = scope("success")
      val schemaA = schema(scopes = Vector(currentScope))
      val schemaB = schema("b" * 64, scopes = Vector(currentScope))
      EvaluationComparator.compare(schemaA, schemaB, "rev-1", Vector.empty, Vector.empty) match {
        case Left(ComparisonError.CorpusFingerprintMismatch(expected, actual)) =>
          assert(expected == "b" * 64)
          assert(actual == "a" * 64)
        case other => fail(s"expected corpus mismatch, got $other")
      }
    }

    "reject metric schema and policy mismatches" in {
      val currentScope = scope("success")
      val base = schema(scopes = Vector(currentScope))
      val observation = AggregateMetricObservation.from(currentScope, BigDecimal("1.000000000000"), 1, 0)
      val rows = Vector(surface("variants") -> section(Vector(observation)))
      EvaluationComparator.compare(schema(metricVersion = "metrics-v2", scopes = Vector(currentScope)), base, "rev-1", rows, rows) match {
        case Left(ComparisonError.SchemaMismatch("metrics-v1", "metrics-v2")) => ()
        case other => fail(s"expected metric schema mismatch, got $other")
      }
      EvaluationComparator.compare(schema(policyVersion = "policy-v2", scopes = Vector(currentScope)), base, "rev-1", rows, rows) match {
        case Left(ComparisonError.PolicyMismatch("policy-v1", "policy-v2")) => ()
        case other => fail(s"expected policy mismatch, got $other")
      }
    }

    "reject reordered schemas and actual observations" in {
      val success = scope("success")
      val mrr = scope("mrr")
      val baselineSchema = schema(scopes = Vector(success, mrr))
      val candidateSchema = schema(scopes = Vector(mrr, success))
      val successObservation = AggregateMetricObservation.from(success, BigDecimal("1.000000000000"), 1, 0)
      val mrrObservation = AggregateMetricObservation.from(mrr, BigDecimal("1.000000000000"), 1, 0)
      val normal = Vector(surface("variants") -> section(Vector(successObservation, mrrObservation)))
      val reversed = Vector(surface("variants") -> section(Vector(mrrObservation, successObservation)))
      EvaluationComparator.compare(candidateSchema, baselineSchema, "rev-1", normal, normal) match {
        case Left(ComparisonError.MetricKeyOrderMismatch(_, _)) => ()
        case other => fail(s"expected schema order mismatch, got $other")
      }
      EvaluationComparator.compare(baselineSchema, baselineSchema, "rev-1", reversed, normal) match {
        case Left(ComparisonError.MetricKeyOrderMismatch(_, _)) => ()
        case other => fail(s"expected candidate observation order mismatch, got $other")
      }
      EvaluationComparator.compare(baselineSchema, baselineSchema, "rev-1", normal, reversed) match {
        case Left(ComparisonError.MetricKeyOrderMismatch(_, _)) => ()
        case other => fail(s"expected baseline observation order mismatch, got $other")
      }
    }

    "reject missing, unexpected, duplicate and outer-surface observations" in {
      val success = scope("success")
      val mrr = scope("mrr")
      val expected = schema(scopes = Vector(success, mrr))
      val successObservation = AggregateMetricObservation.from(success, BigDecimal("1.000000000000"), 1, 0)
      val mrrObservation = AggregateMetricObservation.from(mrr, BigDecimal("1.000000000000"), 1, 0)
      val baseline = Vector(surface("variants") -> section(Vector(successObservation, mrrObservation)))
      val missing = Vector(surface("variants") -> section(Vector(successObservation)))
      val extraScope = scope("recall")
      val extra = AggregateMetricObservation.from(extraScope, BigDecimal("1.000000000000"), 1, 0)
      val unexpected = Vector(surface("variants") -> section(Vector(successObservation, mrrObservation, extra)))
      val duplicate = Vector(surface("variants") -> section(Vector(successObservation, successObservation)))
      val mismatchedOuter = Vector(surface("providers") -> section(Vector(successObservation, mrrObservation)))
      EvaluationComparator.compare(expected, expected, "rev-1", missing, baseline) match {
        case Left(ComparisonError.MetricKeyOrderMismatch(_, _)) => ()
        case other => fail(s"expected missing candidate observation, got $other")
      }
      EvaluationComparator.compare(expected, expected, "rev-1", baseline, missing) match {
        case Left(ComparisonError.MetricKeyOrderMismatch(_, _)) => ()
        case other => fail(s"expected missing baseline observation, got $other")
      }
      EvaluationComparator.compare(expected, expected, "rev-1", unexpected, baseline) match {
        case Left(ComparisonError.MetricKeyOrderMismatch(_, _)) => ()
        case other => fail(s"expected unexpected observation, got $other")
      }
      EvaluationComparator.compare(expected, expected, "rev-1", duplicate, baseline) match {
        case Left(ComparisonError.DuplicateObservation(_, "candidate")) => ()
        case other => fail(s"expected duplicate observation, got $other")
      }
      EvaluationComparator.compare(expected, expected, "rev-1", mismatchedOuter, baseline) match {
        case Left(ComparisonError.OuterSurfaceMismatch(_, _)) => ()
        case other => fail(s"expected outer surface mismatch, got $other")
      }
    }
  }
}
