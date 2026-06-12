package leaderboard.search

import io.circe.{Decoder, KeyDecoder}
import io.circe.parser.decode
import leaderboard.model.{MasterLocationId, MasterServiceOfferVariantId, ServiceId}
import leaderboard.search.eval._
import leaderboard.search.qdrant.QdrantEmbeddingBenchmarkReportJson
import org.scalatest.wordspec.AnyWordSpec

final class EngineEvalSavedReportAssemblyManualSpec extends AnyWordSpec {

  "EngineEval saved-report assembly manual spec" should {
    "assemble an EngineEval aggregate report from saved ES and Qdrant JSON" in {
      if (!envFlag(EngineEvalSavedReportAssemblyManualSpec.EnvGate)) {
        cancel(cancelMessage)
      } else {
        (
          sys.env.get(EngineEvalSavedReportAssemblyManualSpec.EnvEsReportsJson),
          sys.env.get(EngineEvalSavedReportAssemblyManualSpec.EnvQdrantRunOutputJson),
          sys.env.get(EngineEvalSavedReportAssemblyManualSpec.EnvQdrantCandidateId),
          sys.env.get(EngineEvalSavedReportAssemblyManualSpec.EnvExpectedRolesJson),
        ) match {
          case (Some(esReportsJson), Some(qdrantRunOutputJson), Some(qdrantCandidateId), Some(expectedRolesJson)) =>
            val esReports = decode[List[BeautySearchEvalReport]](esReportsJson) match {
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
            }

          case _ =>
            cancel(cancelMessage)
        }
      }
    }
  }

  private def envFlag(name: String): Boolean =
    sys.env.get(name).exists { value =>
      val normalized = value.trim.toLowerCase
      normalized == "1" || normalized == "true" || normalized == "yes"
    }

  private def cancelMessage: String =
    s"Set ${EngineEvalSavedReportAssemblyManualSpec.EnvGate}=true, " +
      s"${EngineEvalSavedReportAssemblyManualSpec.EnvEsReportsJson}=<json>, " +
      s"${EngineEvalSavedReportAssemblyManualSpec.EnvQdrantRunOutputJson}=<json>, " +
      s"${EngineEvalSavedReportAssemblyManualSpec.EnvQdrantCandidateId}=<id>, and " +
      s"${EngineEvalSavedReportAssemblyManualSpec.EnvExpectedRolesJson}=<json> to run the saved-report assembly manual spec"

  private implicit val beautySearchEvalReportDecoder: Decoder[BeautySearchEvalReport] = Decoder.instance { c =>
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

object EngineEvalSavedReportAssemblyManualSpec {
  val EnvGate = "ENGINE_EVAL_ASSEMBLE_SAVED_REPORT"
  val EnvEsReportsJson = "ENGINE_EVAL_ES_REPORTS_JSON"
  val EnvQdrantRunOutputJson = "ENGINE_EVAL_QDRANT_RUN_OUTPUT_JSON"
  val EnvQdrantCandidateId = "ENGINE_EVAL_QDRANT_CANDIDATE_ID"
  val EnvExpectedRolesJson = "ENGINE_EVAL_EXPECTED_ROLES_JSON"
}
