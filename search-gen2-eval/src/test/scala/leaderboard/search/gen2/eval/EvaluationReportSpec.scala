package leaderboard.search.gen2.eval

import org.scalatest.wordspec.AnyWordSpec
import io.circe.Json

final class EvaluationReportSpec extends AnyWordSpec {
  private def id(raw: String): EvaluationResultId = EvaluationResultId.from(raw).getOrElse(fail(s"invalid result id: $raw"))
  private def caseId(raw: String): EvaluationCaseId = EvaluationCaseId.from(raw).getOrElse(fail(s"invalid case id: $raw"))
  private def surface(raw: String): EvaluationSurfaceId = EvaluationSurfaceId.from(raw).getOrElse(fail(s"invalid surface id: $raw"))
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

}
