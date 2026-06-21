package leaderboard.search.eval

final case class M9BeautyQSearchEvalQueryDatasetStaticRowsInput(
  metadata: M9BeautyQSearchEvalQueryDatasetMetadata,
  generatedAt: String,
)

final case class M9BeautyQSearchEvalQueryDatasetStaticRowsSummary(
  datasetId: String,
  fullDatasetQueryCount: Int,
  mappedRowCount: Int,
  representativeQueryIds: List[String],
  fullJsonParsingImplemented: Boolean,
  full63QueryExpansionImplemented: Boolean,
  realBackendCallRequired: Boolean,
  routePluginDiHttpSourceInvolved: Boolean,
)

final case class M9BeautyQSearchEvalQueryDatasetStaticRowsResult(
  staticRunInput: M9OfflineEvalStaticRunInput,
  summary: M9BeautyQSearchEvalQueryDatasetStaticRowsSummary,
)

object M9BeautyQSearchEvalQueryDatasetStaticRows {
  val EvalDataset: EvalDatasetId =
    EvalDatasetId(M9BeautyQSearchEvalQueryDataset.Metadata.datasetId)
  val CatalogSnapshot: CatalogSnapshotId =
    CatalogSnapshotId("seed-resource-catalog")

  val MarkdownFilename: String =
    "m9-beautyq-eval-dataset-static-rows-report.md"

  val RepresentativeQueryIds: List[String] =
    List("q_nails_001", "q_nails_003", "q_noise_005")

  val RequiredResourceAnchors: List[String] =
    List(
      "\"queryCount\": 63",
      "\"id\": \"q_nails_001\"",
      "\"id\": \"q_nails_003\"",
      "\"id\": \"q_noise_005\"",
      "\"acceptableVariantIds\"",
      "\"acceptableProviderLocationIds\"",
      "\"acceptableServiceIds\"",
      "\"serviceIntentCarousel\"",
    )

  val DefaultInput: M9BeautyQSearchEvalQueryDatasetStaticRowsInput =
    M9BeautyQSearchEvalQueryDatasetStaticRowsInput(
      metadata = M9BeautyQSearchEvalQueryDataset.Metadata,
      generatedAt = "2026-06-21T00:00:00Z",
    )

  val DefaultResult: M9BeautyQSearchEvalQueryDatasetStaticRowsResult =
    build(DefaultInput)

  val StaticRun: M9OfflineEvalStaticRunResult =
    M9OfflineEvalStaticRunner
      .run(
        input = DefaultResult.staticRunInput,
        config = M9OfflineEvalStaticRunnerConfig(markdownFilename = MarkdownFilename),
      )
      .fold(
        failure => throw new IllegalStateException(s"Invalid BeautyQ dataset static rows: ${failure.reasons.mkString("; ")}"),
        identity,
      )

  val MarkdownArtifact: M9OfflineEvalReportArtifact =
    StaticRun.markdownArtifact

  def build(input: M9BeautyQSearchEvalQueryDatasetStaticRowsInput): M9BeautyQSearchEvalQueryDatasetStaticRowsResult = {
    val queries = representativeDatasetQueries
    val rows = representativeRows
    val summary = M9BeautyQSearchEvalQueryDatasetStaticRowsSummary(
      datasetId = input.metadata.datasetId,
      fullDatasetQueryCount = input.metadata.queryCount,
      mappedRowCount = rows.size,
      representativeQueryIds = rows.map(_.queryId),
      fullJsonParsingImplemented = false,
      full63QueryExpansionImplemented = false,
      realBackendCallRequired = false,
      routePluginDiHttpSourceInvolved = false,
    )

    M9BeautyQSearchEvalQueryDatasetStaticRowsResult(
      staticRunInput = M9OfflineEvalStaticRunInput(
        dataset = M9OfflineEvalDataset(
          evalDatasetId = EvalDataset,
          catalogSnapshotId = CatalogSnapshot,
          queries = queries,
        ),
        metadata = OfflineEvalRunMetadata(
          servingMode = ServingMode.Unknown,
          candidateSource = CandidateSource.Manual,
          routingPolicyId = RoutingPolicyId("m9-beautyq-eval-dataset-static-rows-v1"),
          fusionPolicy = FusionPolicy.None,
          rerankerPolicy = RerankerPolicy.None,
          experimentId = ExperimentId("m9-beautyq-eval-dataset-static-rows"),
          catalogSnapshotId = CatalogSnapshot,
          evalDatasetId = EvalDataset,
          metricWindow = MetricWindow("static-dataset-fixture"),
        ),
        reportEvalDatasetId = EvalDataset,
        reportCatalogSnapshotId = CatalogSnapshot,
        rows = rows,
        aggregateMetrics = aggregateMetrics(summary),
        qualityGateDecision = "static_dataset_mapping_only",
        notes = notes(input.metadata, summary),
        warnings = warnings(summary),
        generatedAt = input.generatedAt,
      ),
      summary = summary,
    )
  }

  private def representativeDatasetQueries: List[M9OfflineEvalDatasetQuery] =
    List(
      M9OfflineEvalDatasetQuery(
        queryId = "q_nails_001",
        rawQueryText = "маникюр гель лак",
        normalizedQueryText = Some("маникюр гель лак"),
        queryClass = QueryClass.IngredientAttribute,
        filters = List("language=ru", "service=Маникюр", "nail_service_type=manicure", "nail_coating_type=gel_polish"),
        categories = List("Ногти, маникюр и педикюр"),
        expectedResults = List(
          M9OfflineEvalExpectedResult(
            resultId = "c82d90c3-d9e4-5f0b-8689-6476c5e7fe35",
            note = Some("VariantCarousel acceptable variant anchor from fixture; not a backend result."),
          ),
          M9OfflineEvalExpectedResult(
            resultId = "989e0858-bc32-5b71-a355-6ce1e20b0cb1",
            note = Some("ProviderCarousel acceptable provider-location anchor from fixture."),
          ),
          M9OfflineEvalExpectedResult(
            resultId = "a1085253-a9bf-517c-80c4-262b0bf9a5a4",
            note = Some("ServiceIntentCarousel acceptable service anchor from fixture."),
          ),
        ),
        expectedNotes = List(
          "RU nail query representative for variant/provider/service-intent evidence boundaries.",
          "Fixture mapping only; no ES or Qdrant result quality is measured.",
        ),
        negativeOutOfCatalog = false,
      ),
      M9OfflineEvalDatasetQuery(
        queryId = "q_nails_003",
        rawQueryText = "shellac entfernen und neu",
        normalizedQueryText = Some("shellac entfernen und neu"),
        queryClass = QueryClass.FilterHeavy,
        filters = List("language=de", "service=Маникюр", "nail_coating_type=shellac", "with_removal=true"),
        categories = List("Ногти, маникюр и педикюр"),
        expectedResults = List(
          M9OfflineEvalExpectedResult(
            resultId = "798c4326-e081-59a9-b659-98671f1fd656",
            note = Some("VariantCarousel acceptable variant anchor from fixture; not a backend result."),
          ),
          M9OfflineEvalExpectedResult(
            resultId = "78fdf5d2-0f92-5c2c-b20d-e5d2549d1c52",
            note = Some("ProviderCarousel acceptable provider-location anchor from fixture."),
          ),
          M9OfflineEvalExpectedResult(
            resultId = "a1085253-a9bf-517c-80c4-262b0bf9a5a4",
            note = Some("ServiceIntentCarousel acceptable service anchor from fixture."),
          ),
        ),
        expectedNotes = List(
          "DE nail query representative for shellac/removal attribute coverage.",
          "Fixture mapping only; no ES or Qdrant result quality is measured.",
        ),
        negativeOutOfCatalog = false,
      ),
      M9OfflineEvalDatasetQuery(
        queryId = "q_noise_005",
        rawQueryText = "lifting",
        normalizedQueryText = Some("lifting"),
        queryClass = QueryClass.Ambiguous,
        filters = List("language=mixed", "query_type=ambiguous", "query_type=hard_negative"),
        categories = List("Ресницы, брови и permanent make-up"),
        expectedResults = List(
          M9OfflineEvalExpectedResult(
            resultId = "4f5d8aa6-d826-50a5-bd06-f19eee2bd9c7",
            note = Some("Brow lamination acceptable variant anchor from fixture; not a backend result."),
          ),
          M9OfflineEvalExpectedResult(
            resultId = "d658c194-38f7-5396-b8cb-cf155739c235",
            note = Some("Lash lifting acceptable variant anchor from fixture; not a backend result."),
          ),
          M9OfflineEvalExpectedResult(
            resultId = "504424ba-7d46-5cc9-a6b7-1ee064e610fd",
            note = Some("ServiceIntentCarousel brow service anchor from fixture."),
          ),
        ),
        expectedNotes = List(
          "Hard-negative/noise representative: short ambiguous lifting query with forbidden facial anti-aging evidence in the fixture.",
          "Fixture mapping only; no ES or Qdrant result quality is measured.",
        ),
        negativeOutOfCatalog = false,
      ),
    )

  private def representativeRows: List[M9OfflineEvalReportRow] =
    representativeDatasetQueries.map { query =>
      M9OfflineEvalReportRow(
        queryId = query.queryId,
        queryClass = query.queryClass,
        servingMode = ServingMode.Unknown,
        candidateSource = CandidateSource.Manual,
        topKResultIds = query.expectedResults.map(_.resultId),
        metrics = List(
          metric(OfflineEvalMetricName.RegressionPassFail, OfflineEvalMetricValue.Text("unknown")),
          metric(OfflineEvalMetricName.QualityGateDecision, OfflineEvalMetricValue.Text("static_dataset_mapping_only")),
        ),
        regressionStatus = "unknown",
        warnings = List(
          "Static dataset fixture mapping only; top_k_result_ids are fixture anchors, not backend retrieval results.",
          "No ES, Qdrant, route, plugin, DI, HTTP, hybrid, fusion, or reranking execution is represented.",
        ),
      )
    }

  private def aggregateMetrics(summary: M9BeautyQSearchEvalQueryDatasetStaticRowsSummary): List[OfflineEvalMetric] =
    List(
      metric(OfflineEvalMetricName.FailureCount, OfflineEvalMetricValue.Numeric(BigDecimal("0"))),
      metric(OfflineEvalMetricName.QualityGateDecision, OfflineEvalMetricValue.Text("static_dataset_mapping_only")),
      metric(OfflineEvalMetricName.RecallAtK, OfflineEvalMetricValue.Text("not_measured")),
      metric(OfflineEvalMetricName.TopKOverlap, OfflineEvalMetricValue.Text(s"mapped_rows=${summary.mappedRowCount};full_dataset_queries=${summary.fullDatasetQueryCount}")),
    )

  private def notes(
    metadata: M9BeautyQSearchEvalQueryDatasetMetadata,
    summary: M9BeautyQSearchEvalQueryDatasetStaticRowsSummary,
  ): List[String] =
    List(
      s"Dataset fixture: ${metadata.datasetId}; version=${metadata.version}; full_query_count=${metadata.queryCount}.",
      s"Language counts: ru=${metadata.languageCounts.ru}, en=${metadata.languageCounts.en}, de=${metadata.languageCounts.de}, mixed=${metadata.languageCounts.mixed}.",
      s"Target carousels carried from dataset metadata: ${metadata.targetCarousels.mkString(", ")}.",
      s"Representative static rows mapped: ${summary.representativeQueryIds.mkString(", ")}.",
      "Full 63-query row expansion remains future work; this slice validates resource anchors instead of parsing the complete JSON.",
      "Default /beauty-search remains ES-backed; Qdrant production activation remains not approved.",
    )

  private def warnings(summary: M9BeautyQSearchEvalQueryDatasetStaticRowsSummary): List[String] =
    List(
      "Offline dataset fixture mapping only; not production telemetry and not activation approval.",
      "Real backend calls remain disabled by default and are not required to build this report.",
      "No JSON parser/dependency is used; full JSON parsing is intentionally deferred.",
      s"Representative subset only: mapped_row_count=${summary.mappedRowCount}; full_dataset_query_count=${summary.fullDatasetQueryCount}.",
    )

  private def metric(
    name: OfflineEvalMetricName,
    value: OfflineEvalMetricValue,
  ): OfflineEvalMetric =
    OfflineEvalMetric(name, value)
}
