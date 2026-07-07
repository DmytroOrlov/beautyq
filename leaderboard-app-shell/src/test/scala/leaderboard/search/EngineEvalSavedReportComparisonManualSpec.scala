package leaderboard.search

import io.circe.parser.decode
import leaderboard.model.{MasterServiceOfferVariantId, QueryFailure}
import leaderboard.search.eval._
import org.scalatest.wordspec.AnyWordSpec

import java.util.UUID

final class EngineEvalSavedReportComparisonManualSpec extends AnyWordSpec {
  "EngineEvalSavedReportComparison manual comparison" should {
    "compare two saved JSON reports from environment variables" in {
      if (envFlag(EngineEvalSavedReportComparisonManualSpec.EnvCompareSavedReports)) {
        println("ENGINE_EVAL_SAVED_REPORT_COMPARISON_MODE=REAL_ARTIFACTS")
        runRealArtifacts()
      } else {
        println("ENGINE_EVAL_SAVED_REPORT_COMPARISON_MODE=DEFAULT_FIXTURE")
        runDefaultFixture()
      }
    }

    "decode query-class sidecar JSON object shape" in {
      val result = EngineEvalSavedReportComparisonManualSpec.decodeQueryClassesJson(
        """{
          |  "q_broad_001": ["ExactService"],
          |  "q_broad_005": ["PriceDuration", "BroadIntent"]
          |}""".stripMargin
      )

      result match {
        case Right(classesByQueryId) =>
          classesByQueryId.toList.find { case (queryId, _) => queryId == "q_broad_001" } match {
            case Some((_, classes)) =>
              assert(classes == List(EngineEvalQueryClass.ExactService))
            case None =>
              fail(s"expected q_broad_001 sidecar entry, got $classesByQueryId")
          }
          classesByQueryId.toList.find { case (queryId, _) => queryId == "q_broad_005" } match {
            case Some((_, classes)) =>
              assert(classes == List(EngineEvalQueryClass.PriceDuration, EngineEvalQueryClass.BroadIntent))
            case None =>
              fail(s"expected q_broad_005 sidecar entry, got $classesByQueryId")
          }
        case Left(failure) =>
          fail(s"unexpected sidecar decode failure: $failure")
      }
    }

    "fail clearly when query-class sidecar JSON contains an invalid class name" in {
      val result = EngineEvalSavedReportComparisonManualSpec.decodeQueryClassesJson(
        """{"q_broad_001": ["ExactService", "NotAClass"]}"""
      )

      result match {
        case Left(QueryFailure.OperationFailure(operationName, message)) =>
          assert(operationName == "engine-eval-query-class-sidecar-json")
          assert(message.contains("NotAClass"))
        case other =>
          fail(s"expected invalid class name failure, got $other")
      }
    }

    "fail clearly when only one paired query-class sidecar env value is supplied" in {
      val result = EngineEvalSavedReportComparisonManualSpec.compareJsonWithOptionalSidecars(
        leftJson = "{}",
        rightJson = "{}",
        leftClassesJson = Some("""{"q_broad_001": ["ExactService"]}"""),
        rightClassesJson = None,
      )

      result match {
        case Left(QueryFailure.OperationFailure(operationName, message)) =>
          assert(operationName == "engine-eval-saved-report-comparison")
          assert(message.contains(EngineEvalSavedReportComparisonManualSpec.EnvRightQueryClassesJson))
        case other =>
          fail(s"expected missing paired sidecar env failure, got $other")
      }
    }
  }

  private def runDefaultFixture(): Unit = {
    val leftReport = EngineEvalSavedReportComparisonManualSpec.buildReport(
      queryId = "q_fixture_left",
      esIds = List(EngineEvalSavedReportComparisonManualSpec.v1, EngineEvalSavedReportComparisonManualSpec.v2),
      qdrantIds = List(EngineEvalSavedReportComparisonManualSpec.v2, EngineEvalSavedReportComparisonManualSpec.v3),
      expectedRole = EngineExpectedRole.QdrantMayComplement,
      expectedIds = Set(EngineEvalSavedReportComparisonManualSpec.v1, EngineEvalSavedReportComparisonManualSpec.v2, EngineEvalSavedReportComparisonManualSpec.v3),
    )

    val rightReport = EngineEvalSavedReportComparisonManualSpec.buildReport(
      queryId = "q_fixture_right",
      esIds = List(EngineEvalSavedReportComparisonManualSpec.v1),
      qdrantIds = List(EngineEvalSavedReportComparisonManualSpec.v1, EngineEvalSavedReportComparisonManualSpec.v3, EngineEvalSavedReportComparisonManualSpec.v4),
      expectedRole = EngineExpectedRole.HybridMayImprove,
      expectedIds = Set(EngineEvalSavedReportComparisonManualSpec.v1, EngineEvalSavedReportComparisonManualSpec.v3, EngineEvalSavedReportComparisonManualSpec.v4),
    )

    val leftJson = EngineEvalReportJson.encodeReportString(leftReport)
    val rightJson = EngineEvalReportJson.encodeReportString(rightReport)

    EngineEvalSavedReportComparison.compareReportJsonStrings(leftJson, rightJson) match {
      case Left(failure) =>
        fail(s"unexpected saved-report comparison failure: $failure")
      case Right(comparison) =>
        val formatted = EngineEvalSavedReportComparison.formatComparison(comparison)
        println("BEGIN_ENGINE_EVAL_SAVED_REPORT_COMPARISON")
        println(formatted)
        println("END_ENGINE_EVAL_SAVED_REPORT_COMPARISON")

        assert(comparison.queryCountDelta == 0, s"expected queryCountDelta 0, got ${comparison.queryCountDelta}")
        assert(
          comparison.esRecallCountDelta != 0 ||
            comparison.qdrantRecallCountDelta != 0 ||
            comparison.qdrantComplementCountDelta != 0 ||
            comparison.simulatedHybridGainCountDelta != 0,
          s"expected at least one non-zero delta, got left=${comparison.left} right=${comparison.right}"
        )
        assert(comparison.classComparisons == Nil)
        (): Unit
    }
  }

  private def runRealArtifacts(): Unit = {
    val leftJson = sys.env.get(EngineEvalSavedReportComparisonManualSpec.EnvLeftJson)
    val rightJson = sys.env.get(EngineEvalSavedReportComparisonManualSpec.EnvRightJson)

    val missing = List(
      (EngineEvalSavedReportComparisonManualSpec.EnvLeftJson, leftJson),
      (EngineEvalSavedReportComparisonManualSpec.EnvRightJson, rightJson),
    ).collect { case (name, None) => name }

    if (missing.nonEmpty) {
      fail(s"ENGINE_EVAL_COMPARE_SAVED_REPORTS is enabled but missing required env var(s): ${missing.mkString(", ")}")
    } else {
      (leftJson, rightJson) match {
        case (Some(left), Some(right)) =>
          EngineEvalSavedReportComparisonManualSpec.compareJsonWithOptionalSidecars(
            leftJson = left,
            rightJson = right,
            leftClassesJson = sys.env.get(EngineEvalSavedReportComparisonManualSpec.EnvLeftQueryClassesJson),
            rightClassesJson = sys.env.get(EngineEvalSavedReportComparisonManualSpec.EnvRightQueryClassesJson),
          ) match {
            case Left(failure) =>
              fail(s"unexpected saved-report comparison failure: $failure")
            case Right(comparison) =>
              val formatted = EngineEvalSavedReportComparison.formatComparison(comparison)
              println("BEGIN_ENGINE_EVAL_SAVED_REPORT_COMPARISON")
              println(formatted)
              println("END_ENGINE_EVAL_SAVED_REPORT_COMPARISON")
          }
        case _ =>
          fail("unexpected env var extraction failure after missing-check")
      }
    }
  }

  private def envFlag(name: String): Boolean =
    sys.env.get(name).exists { value =>
      val normalized = value.trim.toLowerCase
      normalized == "1" || normalized == "true" || normalized == "yes"
    }
}

object EngineEvalSavedReportComparisonManualSpec {
  val EnvCompareSavedReports = "ENGINE_EVAL_COMPARE_SAVED_REPORTS"
  val EnvLeftJson = "ENGINE_EVAL_LEFT_JSON"
  val EnvRightJson = "ENGINE_EVAL_RIGHT_JSON"
  val EnvLeftQueryClassesJson = "ENGINE_EVAL_LEFT_QUERY_CLASSES_JSON"
  val EnvRightQueryClassesJson = "ENGINE_EVAL_RIGHT_QUERY_CLASSES_JSON"

  private val QueryClassSidecarJsonOperation = "engine-eval-query-class-sidecar-json"
  private val SavedReportComparisonOperation = "engine-eval-saved-report-comparison"

  private def variantId(slot: Int): MasterServiceOfferVariantId =
    UUID.fromString(f"00000000-0000-0000-0000-00000000${slot}%04x")

  val v1: MasterServiceOfferVariantId = variantId(1)
  val v2: MasterServiceOfferVariantId = variantId(2)
  val v3: MasterServiceOfferVariantId = variantId(3)
  val v4: MasterServiceOfferVariantId = variantId(4)

  def compareJsonWithOptionalSidecars(
    leftJson: String,
    rightJson: String,
    leftClassesJson: Option[String],
    rightClassesJson: Option[String],
  ): Either[QueryFailure, EngineEvalAggregateComparison] =
    (leftClassesJson, rightClassesJson) match {
      case (None, None) =>
        EngineEvalSavedReportComparison.compareReportJsonStrings(leftJson, rightJson)
      case (Some(leftSidecar), Some(rightSidecar)) =>
        for {
          leftClasses <- decodeQueryClassesJson(leftSidecar)
          rightClasses <- decodeQueryClassesJson(rightSidecar)
          comparison <- EngineEvalSavedReportComparison.compareReportJsonStringsWithQueryClasses(
            leftJson = leftJson,
            rightJson = rightJson,
            leftClassesByQueryId = leftClasses,
            rightClassesByQueryId = rightClasses,
          )
        } yield comparison
      case (Some(_), None) =>
        Left(QueryFailure.operation(
          SavedReportComparisonOperation,
          s"Missing required paired env var ${EnvRightQueryClassesJson} when ${EnvLeftQueryClassesJson} is supplied",
        ))
      case (None, Some(_)) =>
        Left(QueryFailure.operation(
          SavedReportComparisonOperation,
          s"Missing required paired env var ${EnvLeftQueryClassesJson} when ${EnvRightQueryClassesJson} is supplied",
        ))
    }

  def decodeQueryClassesJson(value: String): Either[QueryFailure, Map[String, List[EngineEvalQueryClass]]] =
    decode[Map[String, List[String]]](value)
      .left
      .map(error => QueryFailure.operation(
        QueryClassSidecarJsonOperation,
        s"Invalid EngineEval query-class sidecar JSON: ${error.getMessage}",
      ))
      .flatMap { classesByQueryId =>
        classesByQueryId.foldLeft(Right(Map.empty[String, List[EngineEvalQueryClass]]): Either[QueryFailure, Map[String, List[EngineEvalQueryClass]]]) {
          case (Left(failure), _) =>
            Left(failure)
          case (Right(acc), (queryId, classNames)) =>
            decodeQueryClasses(queryId, classNames).map(classes => acc + (queryId -> classes))
        }
      }

  private def decodeQueryClasses(
    queryId: String,
    classNames: List[String],
  ): Either[QueryFailure, List[EngineEvalQueryClass]] = {
    val classByName = EngineEvalQueryClass.stableOrder.map(queryClass => queryClass.toString -> queryClass).toMap
    val invalid = classNames.distinct.filterNot(classByName.contains)

    if (invalid.nonEmpty) {
      Left(QueryFailure.operation(
        QueryClassSidecarJsonOperation,
        s"Unknown EngineEval query class name(s) for query id $queryId: ${invalid.mkString(", ")}",
      ))
    } else {
      classNames.foldLeft(Right(List.empty[EngineEvalQueryClass]): Either[QueryFailure, List[EngineEvalQueryClass]]) {
        case (Left(failure), _) =>
          Left(failure)
        case (Right(acc), className) =>
          classByName.toList.find { case (knownName, _) => knownName == className } match {
            case Some((_, queryClass)) =>
              Right(acc :+ queryClass)
            case None =>
              Left(QueryFailure.operation(
                QueryClassSidecarJsonOperation,
                s"Unknown EngineEval query class name for query id $queryId: $className",
              ))
          }
      }
    }
  }

  private def buildReport(
    queryId: String,
    esIds: List[MasterServiceOfferVariantId],
    qdrantIds: List[MasterServiceOfferVariantId],
    expectedRole: EngineExpectedRole,
    expectedIds: Set[MasterServiceOfferVariantId],
  ): EngineEvalAggregateReport = {
    val es = EngineEvalResult(EngineEvalEngine.Elasticsearch, queryId, esIds)
    val qdrant = EngineEvalResult(EngineEvalEngine.Qdrant, queryId, qdrantIds)
    val queryReport = EngineEvalQueryReport.from(expectedRole, expectedIds, es, qdrant)
    EngineEvalAggregateReport.from(List(queryReport))
  }
}
