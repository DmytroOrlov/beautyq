package leaderboard.search

import io.circe.generic.semiauto.deriveEncoder
import io.circe.{Decoder, Encoder, KeyDecoder, KeyEncoder}
import io.circe.parser.decode
import io.circe.syntax.*
import leaderboard.model.{MasterLocationId, MasterServiceOfferVariantId, ServiceId}
import leaderboard.search.eval._
import leaderboard.search.qdrant._
import org.scalatest.wordspec.AnyWordSpec

import java.util.UUID

final class EngineEvalSavedReportAssemblyManualSpec extends AnyWordSpec {

  "EngineEval saved-report assembly manual spec" should {
    "assemble an EngineEval aggregate report from saved ES and Qdrant JSON" in {
      if (envFlag(EngineEvalSavedReportAssemblyManualSpec.EnvGate)) {
        println("ENGINE_EVAL_SAVED_REPORT_ASSEMBLY_MODE=REAL_ARTIFACTS")
        runRealArtifacts()
      } else {
        println("ENGINE_EVAL_SAVED_REPORT_ASSEMBLY_MODE=DEFAULT_FIXTURE")
        runDefaultFixture()
      }
    }
  }

  private def runDefaultFixture(): Unit = {
    import EngineEvalSavedReportAssemblyManualSpec.given

    val candidateId = "fixture-candidate-01"

    val esReports = List(
      EngineEvalSavedReportAssemblyManualSpec.fixtureEsReport("q_nails_001", EngineEvalSavedReportAssemblyManualSpec.nailsA1),
      EngineEvalSavedReportAssemblyManualSpec.fixtureEsReport("q_lashes_001", EngineEvalSavedReportAssemblyManualSpec.lashesB1, EngineEvalSavedReportAssemblyManualSpec.lashesB2),
    )

    val esReportsJson = esReports.asJson.noSpaces

    val qdrantRunOutput = EngineEvalSavedReportAssemblyManualSpec.fixtureQdrantRunOutput(candidateId)
    val qdrantRunOutputJson = QdrantEmbeddingBenchmarkReportJson.encodeRunOutputString(qdrantRunOutput)

    val expectedRoles = Map(
      "q_nails_001" -> EngineExpectedRole.QdrantMayComplement,
      "q_lashes_001" -> EngineExpectedRole.EsShouldHandle,
    )
    val expectedRolesJson = expectedRoles.asJson(
      io.circe.Encoder.encodeMap[String, EngineExpectedRole](KeyEncoder.encodeKeyString, EngineEvalReportJson.expectedRoleEncoder)
    ).noSpaces

    runAssembly(esReportsJson, qdrantRunOutputJson, candidateId, expectedRolesJson)
  }

  private def runRealArtifacts(): Unit = {
    val esReportsJson = sys.env.get(EngineEvalSavedReportAssemblyManualSpec.EnvEsReportsJson)
    val qdrantRunOutputJson = sys.env.get(EngineEvalSavedReportAssemblyManualSpec.EnvQdrantRunOutputJson)
    val qdrantCandidateId = sys.env.get(EngineEvalSavedReportAssemblyManualSpec.EnvQdrantCandidateId)
    val expectedRolesJson = sys.env.get(EngineEvalSavedReportAssemblyManualSpec.EnvExpectedRolesJson)

    val missing = List(
      (EngineEvalSavedReportAssemblyManualSpec.EnvEsReportsJson, esReportsJson),
      (EngineEvalSavedReportAssemblyManualSpec.EnvQdrantRunOutputJson, qdrantRunOutputJson),
      (EngineEvalSavedReportAssemblyManualSpec.EnvQdrantCandidateId, qdrantCandidateId),
      (EngineEvalSavedReportAssemblyManualSpec.EnvExpectedRolesJson, expectedRolesJson),
    ).collect { case (name, None) => name }

    if (missing.nonEmpty) {
      fail(s"ENGINE_EVAL_ASSEMBLE_SAVED_REPORT is enabled but missing required env var(s): ${missing.mkString(", ")}")
    } else {
      (esReportsJson, qdrantRunOutputJson, qdrantCandidateId, expectedRolesJson) match {
        case (Some(esJson), Some(qdrantJson), Some(candidateId), Some(rolesJson)) =>
          runAssembly(esJson, qdrantJson, candidateId, rolesJson)
        case _ =>
          fail("unexpected env var extraction failure after missing-check")
      }
    }
  }

  private def runAssembly(
    esReportsJson: String,
    qdrantRunOutputJson: String,
    qdrantCandidateId: String,
    expectedRolesJson: String,
  ): Unit = {
    val esReports = decode[List[BeautySearchEvalReport]](esReportsJson)(
      Decoder.decodeList(EngineEvalSavedReportAssemblyManualSpec.beautySearchEvalReportDecoder)
    ) match {
      case Right(value) => value
      case Left(error)  => fail(s"failed to decode ES reports JSON: $error")
    }

    val qdrantRunOutput = QdrantEmbeddingBenchmarkReportJson.decodeRunOutputString(qdrantRunOutputJson) match {
      case Right(value) => value
      case Left(error)  => fail(s"failed to decode Qdrant run-output JSON: $error")
    }

    val expectedRoles = decode[Map[String, EngineExpectedRole]](expectedRolesJson)(
      Decoder.decodeMap[String, EngineExpectedRole](KeyDecoder.decodeKeyString, EngineEvalReportJson.expectedRoleDecoder)
    ) match {
      case Right(value) => value
      case Left(error)  => fail(s"failed to decode expected roles JSON: $error")
    }

    val esReportQueryIds = esReports.map(_.queryId).toSet
    val inventoryQueryById = BeautySearchEvalInventory.evalSuite.queries.map(q => q.id -> q).toMap
    val missingIds = esReportQueryIds.diff(inventoryQueryById.keySet).toList.sorted
    if (missingIds.nonEmpty) {
      fail(s"ES report query id(s) not found in eval inventory: ${missingIds.mkString(", ")}")
    }

    val queries = BeautySearchEvalInventory.evalSuite.queries.filter(q => esReportQueryIds.contains(q.id))
    if (queries.isEmpty) {
      fail("no matching queries found after filtering inventory by ES report query ids")
    }

    EngineEvalReportAssembly.fromOutputsForQdrantCandidate(
      queries,
      expectedRoles,
      esReports,
      qdrantRunOutput,
      qdrantCandidateId,
    ) match {
      case Left(failure) =>
        fail(s"assembly failed: $failure")
      case Right(report) =>
        println("BEGIN_ENGINE_EVAL_AGGREGATE_REPORT")
        println(EngineEvalReportFormatter.format(report))
        println("END_ENGINE_EVAL_AGGREGATE_REPORT")
        println("BEGIN_ENGINE_EVAL_AGGREGATE_REPORT_JSON")
        println(EngineEvalReportJson.encodeReportString(report))
        println("END_ENGINE_EVAL_AGGREGATE_REPORT_JSON")

        val encodedJson = EngineEvalReportJson.encodeReportString(report)
        val decoded = EngineEvalReportJson.decodeReportString(encodedJson) match {
          case Right(value) => value
          case Left(error)  => fail(s"round-trip decode failed: $error")
        }

        assert(decoded.aggregate.queryCount == 2, s"expected queryCount 2, got ${decoded.aggregate.queryCount}")
        decoded.queryReports.find(_.queryId == "q_nails_001") match {
          case Some(qr) =>
            assert(qr.queryId == "q_nails_001")
            assert(
              qr.metrics.qdrantComplementCount > 0 || qr.metrics.simulatedHybridGainCount > 0 || qr.metrics.qdrantRecallCount > 0,
              s"expected at least one non-zero complement/gain/recall metric for q_nails_001, got complement=${qr.metrics.qdrantComplementCount} gain=${qr.metrics.simulatedHybridGainCount} recall=${qr.metrics.qdrantRecallCount}"
            )
            (): Unit
          case None =>
            fail("expected query report for q_nails_001 not found in decoded report")
        }
    }
  }

  private def envFlag(name: String): Boolean =
    sys.env.get(name).exists { value =>
      val normalized = value.trim.toLowerCase
      normalized == "1" || normalized == "true" || normalized == "yes"
    }
}

object EngineEvalSavedReportAssemblyManualSpec {
  val EnvGate = "ENGINE_EVAL_ASSEMBLE_SAVED_REPORT"
  val EnvEsReportsJson = "ENGINE_EVAL_ES_REPORTS_JSON"
  val EnvQdrantRunOutputJson = "ENGINE_EVAL_QDRANT_RUN_OUTPUT_JSON"
  val EnvQdrantCandidateId = "ENGINE_EVAL_QDRANT_CANDIDATE_ID"
  val EnvExpectedRolesJson = "ENGINE_EVAL_EXPECTED_ROLES_JSON"

  private def variantId(slot: Int): MasterServiceOfferVariantId =
    UUID.fromString(f"00000000-0000-0000-0000-00000000${slot}%04x")

  val v1: MasterServiceOfferVariantId = variantId(1)
  val v2: MasterServiceOfferVariantId = variantId(2)
  val v3: MasterServiceOfferVariantId = variantId(3)

  private def locationId(slot: Int): MasterLocationId =
    UUID.fromString(f"10000000-0000-0000-0000-00000000${slot}%04x")

  private def serviceId(slot: Int): ServiceId =
    UUID.fromString(f"20000000-0000-0000-0000-00000000${slot}%04x")

  private val l1: MasterLocationId = locationId(1)
  private val s1: ServiceId = serviceId(1)

  val nailsA1: MasterServiceOfferVariantId = UUID.fromString("c82d90c3-d9e4-5f0b-8689-6476c5e7fe35")
  val nailsA2: MasterServiceOfferVariantId = UUID.fromString("1fcd6e17-c6bb-5901-9f63-205668897659")
  val lashesB1: MasterServiceOfferVariantId = UUID.fromString("01051aa3-95cb-5c3f-9691-582f2b145f38")
  val lashesB2: MasterServiceOfferVariantId = UUID.fromString("5848ff40-41eb-5981-b56e-84c5ae8c9e07")

  def fixtureEsReport(
    queryId: String,
    topVariantIds: MasterServiceOfferVariantId*,
  ): BeautySearchEvalReport =
    BeautySearchEvalReport(
      queryId = queryId,
      query = s"fixture query $queryId",
      score = 1,
      failedAssertions = Nil,
      topVariantIds = topVariantIds.toList,
      topProviderLocationIds = List(l1),
      topServiceIds = List(s1),
    )

  def fixtureQdrantRunOutput(candidateId: String): QdrantEmbeddingBenchmarkRunOutput = {
    val candidate = QdrantEmbeddingBenchmarkCandidate(
      candidateId = candidateId,
      modelName = "fixture-model",
      endpointLabel = "fixture-endpoint",
      vectorDimension = 4,
    )

    val queryResults = List(
      QdrantEmbeddingBenchmarkQueryResult(
        candidateId = candidateId,
        queryId = "q_nails_001",
        queryText = "fixture query q_nails_001",
        topVariantIds = List(EngineEvalSavedReportAssemblyManualSpec.nailsA1, EngineEvalSavedReportAssemblyManualSpec.nailsA2),
        topProviderIds = List(l1),
        topServiceIds = List(s1),
        scores = List(0.9, 0.8),
      ),
      QdrantEmbeddingBenchmarkQueryResult(
        candidateId = candidateId,
        queryId = "q_lashes_001",
        queryText = "fixture query q_lashes_001",
        topVariantIds = List(EngineEvalSavedReportAssemblyManualSpec.lashesB1),
        topProviderIds = List(l1),
        topServiceIds = List(s1),
        scores = List(0.85),
      ),
    )

    val expectedByQueryId = Map(
      "q_nails_001" -> QdrantEmbeddingBenchmarkExpected(
        acceptableVariantIds = List(EngineEvalSavedReportAssemblyManualSpec.nailsA1, EngineEvalSavedReportAssemblyManualSpec.nailsA2),
        acceptableProviderIds = List(l1),
        acceptableServiceIds = List(s1),
      ),
      "q_lashes_001" -> QdrantEmbeddingBenchmarkExpected(
        acceptableVariantIds = List(EngineEvalSavedReportAssemblyManualSpec.lashesB1, EngineEvalSavedReportAssemblyManualSpec.lashesB2),
        acceptableProviderIds = List(l1),
        acceptableServiceIds = List(s1),
      ),
    )

    val plan = QdrantEmbeddingBenchmarkPlan(
      runMode = QdrantEmbeddingBenchmarkRunMode.SingleEndpointManualRestart,
      candidates = List(candidate),
      k = 3,
    )

    val report = QdrantEmbeddingBenchmark.report(plan, Map(candidateId -> queryResults), expectedByQueryId)

    QdrantEmbeddingBenchmarkRunOutput(
      report = report,
      queryResultsByCandidateId = Map(candidateId -> queryResults),
    )
  }

  implicit val beautySearchEvalReportEncoder: Encoder.AsObject[BeautySearchEvalReport] = deriveEncoder

  implicit val beautySearchEvalReportDecoder: Decoder[BeautySearchEvalReport] = Decoder.instance { c =>
    for {
      queryId <- c.get[String]("queryId")
      query <- c.get[String]("query")
      score <- c.get[Int]("score")
      failedAssertions <- c.getOrElse[List[String]]("failedAssertions")(Nil)
      topVariantIds <- c.getOrElse[List[MasterServiceOfferVariantId]]("topVariantIds")(Nil)
      topProviderLocationIds <- c.getOrElse[List[MasterLocationId]]("topProviderLocationIds")(Nil)
      topServiceIds <- c.getOrElse[List[ServiceId]]("topServiceIds")(Nil)
    } yield BeautySearchEvalReport(queryId, query, score, failedAssertions, topVariantIds, topProviderLocationIds, topServiceIds)
  }
}
