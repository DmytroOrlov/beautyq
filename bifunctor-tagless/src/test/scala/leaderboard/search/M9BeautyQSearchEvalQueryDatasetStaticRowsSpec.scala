package leaderboard.search

import leaderboard.search.eval.{
  CandidateSource,
  M9BeautyQSearchEvalQueryDataset,
  M9BeautyQSearchEvalQueryDatasetStaticRows,
  M9OfflineEvalReportRenderer,
  M9OfflineEvalStaticRunner,
  M9OfflineEvalStaticRunnerConfig,
  QueryClass,
  ServingMode,
}
import org.scalatest.wordspec.AnyWordSpec

import java.nio.charset.StandardCharsets
import scala.io.Source
import scala.util.Using

final class M9BeautyQSearchEvalQueryDatasetStaticRowsSpec extends AnyWordSpec {

  "M9BeautyQSearchEvalQueryDatasetStaticRows" should {

    "map representative BeautyQ dataset queries into rows accepted by the static runner" in {
      val result = M9OfflineEvalStaticRunner.run(
        input = M9BeautyQSearchEvalQueryDatasetStaticRows.DefaultResult.staticRunInput,
        config = M9OfflineEvalStaticRunnerConfig(M9BeautyQSearchEvalQueryDatasetStaticRows.MarkdownFilename),
      )

      result match {
        case Right(staticRun) =>
          assert(staticRun.report.rows.map(_.queryId) == M9BeautyQSearchEvalQueryDatasetStaticRows.RepresentativeQueryIds)
          assert(staticRun.qualityGateSummary.totalRows == 3)
          assert(staticRun.qualityGateSummary.warningRows == 3)
          assert(staticRun.qualityGateSummary.failedRows == 0)
          assert(staticRun.qualityGateSummary.qualityGateDecision == "static_dataset_mapping_only")
          assert(staticRun.markdownArtifact.contents == M9OfflineEvalReportRenderer.renderMarkdown(staticRun.report))
          (): Unit
        case Left(failure) =>
          fail(s"expected static rows to feed static runner, got validation failure $failure")
      }
    }

    "include RU, DE, and hard-negative/noise representative query ids" in {
      val rows = M9BeautyQSearchEvalQueryDatasetStaticRows.DefaultResult.staticRunInput.rows

      assert(rows.map(_.queryId) == List("q_nails_001", "q_nails_003", "q_noise_005"))
      assert(rows.map(_.queryClass) == List(QueryClass.IngredientAttribute, QueryClass.FilterHeavy, QueryClass.Ambiguous))
    }

    "carry dataset metadata into notes and summary" in {
      val result = M9BeautyQSearchEvalQueryDatasetStaticRows.DefaultResult
      val notes = result.staticRunInput.notes.mkString("\n")

      assert(result.summary.datasetId == "wandsbek_hamburg_beauty_services_seed_ready")
      assert(result.summary.fullDatasetQueryCount == 63)
      assert(result.summary.mappedRowCount == 3)
      assert(result.summary.representativeQueryIds == List("q_nails_001", "q_nails_003", "q_noise_005"))
      assert(notes.contains("version=1"))
      assert(notes.contains("full_query_count=63"))
      assert(notes.contains("ru=31, en=21, de=6, mixed=5"))
      assert(notes.contains("variantCarousel, providerCarousel, serviceIntentCarousel"))
    }

    "keep deterministic row count, manual source attribution, and unknown serving mode" in {
      val input = M9BeautyQSearchEvalQueryDatasetStaticRows.DefaultResult.staticRunInput

      assert(input.rows.size == 3)
      assert(input.rows.forall(_.servingMode == ServingMode.Unknown))
      assert(input.rows.forall(_.candidateSource == CandidateSource.Manual))
      assert(input.rows.forall(_.regressionStatus == "unknown"))
      assert(input.rows.forall(_.warnings.exists(_.contains("not backend retrieval results"))))
    }

    "render byte-for-byte stable checked-in artifact" in {
      val expected = readResource("/leaderboard/search/eval/m9-beautyq-eval-dataset-static-rows-report.md")
      val actual = M9BeautyQSearchEvalQueryDatasetStaticRows.MarkdownArtifact.contents

      assert(M9BeautyQSearchEvalQueryDatasetStaticRows.MarkdownArtifact.filename == "m9-beautyq-eval-dataset-static-rows-report.md")
      assert(actual == expected)
    }

    "require no real backend call and no route plugin DI or HTTP source" in {
      val result = M9BeautyQSearchEvalQueryDatasetStaticRows.DefaultResult
      val rendered = M9BeautyQSearchEvalQueryDatasetStaticRows.MarkdownArtifact.contents

      assert(!result.summary.realBackendCallRequired)
      assert(!result.summary.routePluginDiHttpSourceInvolved)
      assert(rendered.contains("Backend execution is not represented by this artifact."))
      assert(!rendered.contains("Elasticsearch query executed"))
      assert(!rendered.contains("Qdrant query executed"))
      assert(!rendered.contains("production activation approved"))
      assert(!rendered.contains("hybrid execution"))
    }

    "validate full dataset presence through bounded resource anchors without full JSON parsing" in {
      val resourceText = readResource(M9BeautyQSearchEvalQueryDataset.ResourcePath)
      val missingAnchors = M9BeautyQSearchEvalQueryDatasetStaticRows.RequiredResourceAnchors.filterNot(resourceText.contains)
      val summary = M9BeautyQSearchEvalQueryDatasetStaticRows.DefaultResult.summary

      assert(missingAnchors == Nil)
      assert(!summary.fullJsonParsingImplemented)
      assert(!summary.full63QueryExpansionImplemented)
      assert(M9BeautyQSearchEvalQueryDatasetStaticRows.RequiredResourceAnchors.contains("\"queryCount\": 63"))
    }

    "mark full 63-query row expansion as future work" in {
      val input = M9BeautyQSearchEvalQueryDatasetStaticRows.DefaultResult.staticRunInput
      val warningsAndNotes = (input.warnings ++ input.notes).mkString("\n")

      assert(warningsAndNotes.contains("Full 63-query row expansion remains future work"))
      assert(warningsAndNotes.contains("Representative subset only: mapped_row_count=3; full_dataset_query_count=63"))
      assert(warningsAndNotes.contains("No JSON parser/dependency is used"))
    }
  }

  private def readResource(path: String): String = {
    val stream = Option(getClass.getResourceAsStream(path))

    stream match {
      case Some(value) =>
        Using.resource(Source.fromInputStream(value, StandardCharsets.UTF_8.name()))(_.mkString)
      case None =>
        fail(s"missing checked-in resource $path")
    }
  }
}
