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
          assert(staticRun.report.rows.map(_.queryId) == M9BeautyQSearchEvalQueryDatasetStaticRows.StaticQueryIds)
          assert(staticRun.qualityGateSummary.totalRows == 63)
          assert(staticRun.qualityGateSummary.warningRows == 63)
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

      assert(rows.map(_.queryId).contains("q_nails_001"))
      assert(rows.map(_.queryId).contains("q_nails_003"))
      assert(rows.map(_.queryId).contains("q_noise_005"))
      assert(rowById(rows, "q_nails_001").queryClass == QueryClass.IngredientAttribute)
      assert(rowById(rows, "q_nails_003").queryClass == QueryClass.FilterHeavy)
      assert(rowById(rows, "q_noise_005").queryClass == QueryClass.Ambiguous)
    }

    "carry dataset metadata into notes and summary" in {
      val result = M9BeautyQSearchEvalQueryDatasetStaticRows.DefaultResult
      val notes = result.staticRunInput.notes.mkString("\n")

      assert(result.summary.datasetId == "wandsbek_hamburg_beauty_services_seed_ready")
      assert(result.summary.fullDatasetQueryCount == 63)
      assert(result.summary.mappedRowCount == 63)
      assert(result.summary.representativeQueryIds == List("q_nails_001", "q_nails_003", "q_noise_005"))
      assert(result.summary.full63QueryExpansionImplemented)
      assert(notes.contains("version=1"))
      assert(notes.contains("full_query_count=63"))
      assert(notes.contains("ru=31, en=21, de=6, mixed=5"))
      assert(notes.contains("variantCarousel, providerCarousel, serviceIntentCarousel"))
      assert(notes.contains("Full 63-query static-row expansion implemented"))
    }

    "keep deterministic row count, manual source attribution, and unknown serving mode" in {
      val input = M9BeautyQSearchEvalQueryDatasetStaticRows.DefaultResult.staticRunInput

      assert(input.rows.size == 63)
      assert(input.rows.forall(_.servingMode == ServingMode.Unknown))
      assert(input.rows.forall(_.candidateSource == CandidateSource.Manual))
      assert(input.rows.forall(_.regressionStatus == "unknown"))
      assert(input.rows.forall(_.warnings.exists(_.contains("not backend retrieval results"))))
      assert(input.rows.forall(_.warnings.exists(_.contains("No ES, Qdrant, route, plugin, DI, HTTP, hybrid, fusion, or reranking execution"))))
      assert(input.rows.forall(row => row.candidateSource != CandidateSource.Es && row.candidateSource != CandidateSource.Qdrant))
      assert(input.rows.forall(row => row.servingMode != ServingMode.EsOnly && row.servingMode != ServingMode.QdrantOnly))
    }

    "preserve the existing representative top-k fixture anchors" in {
      val rows = M9BeautyQSearchEvalQueryDatasetStaticRows.DefaultResult.staticRunInput.rows

      assert(rowById(rows, "q_nails_001").topKResultIds == List(
        "c82d90c3-d9e4-5f0b-8689-6476c5e7fe35",
        "989e0858-bc32-5b71-a355-6ce1e20b0cb1",
        "a1085253-a9bf-517c-80c4-262b0bf9a5a4",
      ))
      assert(rowById(rows, "q_nails_003").topKResultIds == List(
        "798c4326-e081-59a9-b659-98671f1fd656",
        "78fdf5d2-0f92-5c2c-b20d-e5d2549d1c52",
        "a1085253-a9bf-517c-80c4-262b0bf9a5a4",
      ))
      assert(rowById(rows, "q_noise_005").topKResultIds == List(
        "4f5d8aa6-d826-50a5-bd06-f19eee2bd9c7",
        "d658c194-38f7-5396-b8cb-cf155739c235",
        "504424ba-7d46-5cc9-a6b7-1ee064e610fd",
      ))
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
      val queryIdsInResource = extractQueryIds(resourceText)
      val missingAnchors = M9BeautyQSearchEvalQueryDatasetStaticRows.RequiredResourceAnchors.filterNot(resourceText.contains)
      val summary = M9BeautyQSearchEvalQueryDatasetStaticRows.DefaultResult.summary

      assert(M9BeautyQSearchEvalQueryDatasetStaticRows.StaticQueryIds.size == 63)
      assert(M9BeautyQSearchEvalQueryDatasetStaticRows.StaticQueryIds.distinct == M9BeautyQSearchEvalQueryDatasetStaticRows.StaticQueryIds)
      assert(queryIdsInResource == M9BeautyQSearchEvalQueryDatasetStaticRows.StaticQueryIds)
      assert(missingAnchors == Nil)
      assert(!summary.fullJsonParsingImplemented)
      assert(summary.full63QueryExpansionImplemented)
      assert(M9BeautyQSearchEvalQueryDatasetStaticRows.RequiredResourceAnchors.contains("\"queryCount\": 63"))
    }

    "mark full JSON parsing as deferred while full static-row expansion is implemented" in {
      val input = M9BeautyQSearchEvalQueryDatasetStaticRows.DefaultResult.staticRunInput
      val warningsAndNotes = (input.warnings ++ input.notes).mkString("\n")

      assert(warningsAndNotes.contains("Full 63-query static-row expansion implemented: mapped_row_count=63; full_dataset_query_count=63"))
      assert(warningsAndNotes.contains("Rows are static placeholders/fixture anchors only"))
      assert(warningsAndNotes.contains("No JSON parser/dependency is used"))
    }
  }

  private def rowById(
    rows: List[leaderboard.search.eval.M9OfflineEvalReportRow],
    queryId: String,
  ): leaderboard.search.eval.M9OfflineEvalReportRow =
    rows.find(_.queryId == queryId) match {
      case Some(row) => row
      case None      => fail(s"missing static row for $queryId")
    }

  private def extractQueryIds(resourceText: String): List[String] =
    """"id": "(q_[^"]+)"""".r.findAllMatchIn(resourceText).map(_.group(1)).toList

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
