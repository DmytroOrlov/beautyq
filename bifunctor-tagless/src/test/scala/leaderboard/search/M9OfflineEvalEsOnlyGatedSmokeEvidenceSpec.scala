package leaderboard.search

import leaderboard.search.eval.{
  CandidateSource,
  M9OfflineEvalBackendExecutionMode,
  M9OfflineEvalEsOnlyGatedSmokeEvidence,
  M9OfflineEvalEsOnlyGatedSmokeInput,
  M9OfflineEvalRealBackendKind,
  M9OfflineEvalRealBackendResourceConfig,
  M9OfflineEvalRealBackendResourceGate,
  M9OfflineEvalRealBackendResourceGateStatus,
  M9OfflineEvalReportRenderer,
  OfflineEvalMetricName,
  ServingMode,
}
import org.scalatest.wordspec.AnyWordSpec

import java.nio.charset.StandardCharsets
import scala.io.Source
import scala.util.Using

final class M9OfflineEvalEsOnlyGatedSmokeEvidenceSpec extends AnyWordSpec {

  "M9OfflineEvalEsOnlyGatedSmokeEvidence" should {

    "render default-disabled ES smoke evidence without real ES calls" in {
      val input = smokeInput(resourceConfig = None)
      val result = M9OfflineEvalEsOnlyGatedSmokeEvidence.captureDefaultDisabled(input)

      result match {
        case Right(value) =>
          assert(value.gateDecision.status == M9OfflineEvalRealBackendResourceGateStatus.Disabled)
          assert(!value.realBackendCallImplemented)
          assert(value.offlineEvalEvidenceOnly)
          assert(value.notProductionTelemetry)
          assert(value.notActivationApproval)
          assert(value.artifact.savedReport.rows.forall(_.regressionStatus == "failed"))
          assert(value.artifact.savedReport.rows.flatMap(_.warnings).exists(_.contains("disabled")))
          assert(value.artifact.markdownArtifact.filename == M9OfflineEvalEsOnlyGatedSmokeEvidence.DefaultDisabledFilename)
        case Left(error) =>
          fail(s"expected default-disabled ES smoke evidence, got $error")
      }
    }

    "render not-configured ES smoke evidence when gate enabled without resource config" in {
      val input = smokeInput(resourceConfig = None)
      val result = M9OfflineEvalEsOnlyGatedSmokeEvidence.captureNotConfigured(input)

      result match {
        case Right(value) =>
          assert(value.gateDecision.status == M9OfflineEvalRealBackendResourceGateStatus.AllowedWithoutResourceConfig)
          assert(!value.gateDecision.permitsRealBackendCall)
          assert(!value.resourceConfigPresent)
          assert(!value.realBackendCallImplemented)
          assert(value.artifact.savedReport.rows.forall(_.regressionStatus == "failed"))
          assert(value.artifact.savedReport.rows.flatMap(_.warnings).exists(_.contains("not configured")))
          assert(value.artifact.markdownArtifact.filename == M9OfflineEvalEsOnlyGatedSmokeEvidence.NotConfiguredFilename)
        case Left(error) =>
          fail(s"expected not-configured ES smoke evidence, got $error")
      }
    }

    "preserve ES source and mode attribution" in {
      val input = smokeInput(resourceConfig = None)
      val result = M9OfflineEvalEsOnlyGatedSmokeEvidence.captureDefaultDisabled(input)

      result match {
        case Right(value) =>
          assert(value.esSource == CandidateSource.Es)
          assert(value.esExecutionMode == M9OfflineEvalBackendExecutionMode.EsOnlyOffline)
          assert(value.esServingMode == ServingMode.EsOnly)
          assert(value.artifact.summary.backendKind == Some(M9OfflineEvalRealBackendKind.Es))
          assert(value.artifact.summary.executionMode == M9OfflineEvalBackendExecutionMode.EsOnlyOffline)
          assert(value.artifact.summary.candidateSource == CandidateSource.Es)
          assert(value.artifact.savedReport.metadata.servingMode == ServingMode.EsOnly)
          assert(value.artifact.savedReport.metadata.candidateSource == CandidateSource.Es)
          assert(value.artifact.savedReport.rows.forall(_.servingMode == ServingMode.EsOnly))
          assert(value.artifact.savedReport.rows.forall(_.candidateSource == CandidateSource.Es))
        case Left(error) =>
          fail(s"expected ES attribution preserved, got $error")
      }
    }

    "record production non-approval" in {
      val input = smokeInput(resourceConfig = None)
      val result = M9OfflineEvalEsOnlyGatedSmokeEvidence.captureDefaultDisabled(input)

      result match {
        case Right(value) =>
          assert(value.productionActivationNotApprovedConfirmed)
          assert(value.notActivationApproval)
          assert(value.notProductionTelemetry)
          assert(value.offlineEvalEvidenceOnly)
          assert(value.artifact.savedReport.notes.exists(_.contains("not production telemetry")))
          assert(value.artifact.savedReport.notes.exists(_.contains("not production activation approval")))
          assert(value.artifact.savedReport.warnings.exists(_.contains("production_activation_not_approved_confirmed=true")))
          assert(value.artifact.markdownArtifact.contents.contains("Offline eval evidence only; not production telemetry; not production activation approval."))
        case Left(error) =>
          fail(s"expected production non-approval recorded, got $error")
      }
    }

    "not fake successful ES backend execution" in {
      val input = smokeInput(resourceConfig = None)
      val result = M9OfflineEvalEsOnlyGatedSmokeEvidence.captureDefaultDisabled(input)

      result match {
        case Right(value) =>
          assert(!value.realBackendCallImplemented)
          assert(value.artifact.savedReport.rows.forall(_.regressionStatus == "failed"))
          assert(value.artifact.savedReport.rows.forall(_.topKResultIds.isEmpty))
          assert(value.artifact.savedReport.aggregateMetrics.exists(
            m => m.name == OfflineEvalMetricName.QualityGateDecision && m.value.render == "not_evaluated_not_for_activation"
          ))
          assert(!value.artifact.markdownArtifact.contents.toLowerCase.contains("elasticsearch query executed"))
          assert(!value.artifact.markdownArtifact.contents.toLowerCase.contains("es backend success"))
        case Left(error) =>
          fail(s"expected no fake success, got $error")
      }
    }

    "represent warnings and failures as data" in {
      val input = smokeInput(resourceConfig = None)
      val result = M9OfflineEvalEsOnlyGatedSmokeEvidence.captureNotConfigured(input)

      result match {
        case Right(value) =>
          assert(value.artifact.savedReport.rows.forall(_.regressionStatus == "failed"))
          assert(value.artifact.savedReport.rows.flatMap(_.metrics).exists(_.name == OfflineEvalMetricName.FailureCount))
          assert(value.artifact.savedReport.rows.flatMap(_.warnings).nonEmpty)
          assert(value.artifact.savedReport.warnings.nonEmpty)
          assert(value.artifact.savedReport.aggregateMetrics.exists(_.name == OfflineEvalMetricName.QualityGateDecision))
        case Left(error) =>
          fail(s"expected failure data, got $error")
      }
    }

    "produce a byte-for-byte stable artifact for not-configured ES smoke" in {
      val input = smokeInput(resourceConfig = None)
      val result = M9OfflineEvalEsOnlyGatedSmokeEvidence.captureNotConfigured(input)

      result match {
        case Right(value) =>
          val rendered = value.artifact.markdownArtifact.contents
          val renderedAgain = M9OfflineEvalReportRenderer.renderMarkdown(value.artifact.savedReport)

          assert(rendered == renderedAgain)
          assert(rendered == checkedInNotConfiguredArtifact)
        case Left(error) =>
          fail(s"expected stable artifact, got $error")
      }
    }

    "not require real ES calls and avoid route/plugin/DI/http surfaces" in {
      val input = smokeInput(resourceConfig = None)
      val result = M9OfflineEvalEsOnlyGatedSmokeEvidence.captureDefaultDisabled(input)

      result match {
        case Right(value) =>
          assert(!value.realBackendCallImplemented)
          assert(value.offlineEvalEvidenceOnly)
          assert(!value.artifact.markdownArtifact.contents.toLowerCase.contains("elasticsearch query executed"))
          assert(!value.artifact.markdownArtifact.contents.toLowerCase.contains("qdrant query executed"))
          assert(!value.artifact.markdownArtifact.contents.toLowerCase.contains("hybrid execution"))
          assert(!value.artifact.markdownArtifact.contents.toLowerCase.contains("route"))
          assert(!value.artifact.markdownArtifact.contents.toLowerCase.contains("plugin"))
          assert(!value.artifact.markdownArtifact.contents.toLowerCase.contains("distage"))
        case Left(error) =>
          fail(s"expected no-backend-call artifact, got $error")
      }
    }

    "capture a denied artifact when resource config is invalid" in {
      val invalidConfig = M9OfflineEvalRealBackendResourceConfig(
        resourceName = "",
        connectionTarget = "",
        resourceIdentity = None,
        connectionTimeoutMillis = 0,
      )
      val invalidInput = M9OfflineEvalEsOnlyGatedSmokeInput(
        resourceConfig = Some(invalidConfig),
        gate = M9OfflineEvalRealBackendResourceGate(runRealBackendOfflineEval = true),
      )
      val result = M9OfflineEvalEsOnlyGatedSmokeEvidence.capture(
        input = invalidInput,
        markdownFilename = "m9-es-only-gated-smoke-denied.md",
      )

      result match {
        case Right(value) =>
          assert(value.gateDecision.status == M9OfflineEvalRealBackendResourceGateStatus.Denied)
          assert(value.artifact.savedReport.rows.forall(_.regressionStatus == "failed"))
          assert(value.artifact.savedReport.rows.flatMap(_.warnings).exists(_.contains("denied")))
        case Left(error) =>
          fail(s"expected denied artifact, got $error")
      }
    }
  }

  private def smokeInput(
    resourceConfig: Option[M9OfflineEvalRealBackendResourceConfig]
  ): M9OfflineEvalEsOnlyGatedSmokeInput =
    M9OfflineEvalEsOnlyGatedSmokeInput(
      resourceConfig = resourceConfig,
      gate = M9OfflineEvalRealBackendResourceGate(runRealBackendOfflineEval = true),
    )

  private def checkedInNotConfiguredArtifact: String =
    readCheckedInResource("/leaderboard/search/eval/m9-es-only-gated-smoke-not-configured-report.md")

  private def readCheckedInResource(path: String): String = {
    val stream = Option(getClass.getResourceAsStream(path))

    stream match {
      case Some(value) =>
        Using.resource(Source.fromInputStream(value, StandardCharsets.UTF_8.name()))(_.mkString)
      case None =>
        fail(s"missing checked-in resource artifact $path")
    }
  }
}
