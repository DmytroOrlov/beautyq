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
  fullAcceptedQueryExpansionImplemented: Boolean,
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

  val StaticQueryIds: List[String] =
    List(
      "q_nails_001",
      "q_nails_002",
      "q_nails_003",
      "q_nails_004",
      "q_nails_005",
      "q_price_003",
      "q_nails_006",
      "q_nails_007",
      "q_nails_008",
      "q_nails_009",
      "q_nails_010",
      "q_nails_011",
      "q_nails_012",
      "q_lashes_001",
      "q_lashes_002",
      "q_lashes_003",
      "q_lashes_004",
      "q_lashes_005",
      "q_lashes_006",
      "q_lashes_007",
      "q_lashes_008",
      "q_brows_001",
      "q_brows_002",
      "q_brows_003",
      "q_brows_004",
      "q_brows_005",
      "q_brows_006",
      "q_hair_001",
      "q_hair_002",
      "q_hair_003",
      "q_hair_004",
      "q_hair_005",
      "q_hair_006",
      "q_hair_007",
      "q_hair_008",
      "q_hair_009",
      "q_hair_010",
      "q_pmu_001",
      "q_pmu_002",
      "q_pmu_003",
      "q_pmu_004",
      "q_pmu_005",
      "q_pmu_006",
      "q_face_001",
      "q_face_002",
      "q_face_003",
      "q_face_004",
      "q_face_005",
      "q_face_006",
      "q_face_007",
      "q_face_008",
      "q_home_001",
      "q_home_002",
      "q_broad_001",
      "q_broad_002",
      "q_broad_003",
      "q_broad_004",
      "q_broad_005",
      "q_broad_006",
      "q_noise_001",
      "q_noise_002",
      "q_noise_003",
      "q_noise_004",
      "q_noise_005",
      "q_semantic_001",
      "q_semantic_002",
      "q_semantic_003",
      "q_semantic_004",
      "q_semantic_005",
      "q_semantic_006",
      "q_semantic_007",
      "q_semantic_008",
      "q_semantic_009",
      "q_semantic_010",
    )

  val RequiredResourceAnchors: List[String] =
    List(
      "\"queryCount\": 74",
      "\"acceptableVariantIds\"",
      "\"acceptableProviderLocationIds\"",
      "\"acceptableServiceIds\"",
      "\"serviceIntentCarousel\"",
    ) ++ StaticQueryIds.map(queryId => s""""id": "$queryId"""")

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
    val queries = datasetQueries
    val rows = staticRows
    val summary = M9BeautyQSearchEvalQueryDatasetStaticRowsSummary(
      datasetId = input.metadata.datasetId,
      fullDatasetQueryCount = input.metadata.queryCount,
      mappedRowCount = rows.size,
      representativeQueryIds = RepresentativeQueryIds,
      fullJsonParsingImplemented = false,
      fullAcceptedQueryExpansionImplemented = true,
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
        warnings = warnings,
        generatedAt = input.generatedAt,
      ),
      summary = summary,
    )
  }

  private def datasetQueries: List[M9OfflineEvalDatasetQuery] = {
    val representativeById = representativeDatasetQueries.map(query => query.queryId -> query).toMap

    StaticQueryIds.map { queryId =>
      representativeById.getOrElse(queryId, placeholderDatasetQuery(queryId))
    }
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

  private def staticRows: List[M9OfflineEvalReportRow] = {
    val representativeRowsById = representativeDatasetQueries.map(representativeRow).map(row => row.queryId -> row).toMap

    StaticQueryIds.map { queryId =>
      representativeRowsById.getOrElse(queryId, placeholderRow(queryId))
    }
  }

  private def representativeRow(query: M9OfflineEvalDatasetQuery): M9OfflineEvalReportRow =
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

  private def placeholderDatasetQuery(queryId: String): M9OfflineEvalDatasetQuery =
    M9OfflineEvalDatasetQuery(
      queryId = queryId,
      rawQueryText = queryId,
      normalizedQueryText = Some(queryId),
      queryClass = QueryClass.Category,
      filters = List("static_fixture_anchor=true"),
      categories = Nil,
      expectedResults = Nil,
      expectedNotes = List(
        "Static 74-query fixture anchor only; full JSON parsing is intentionally deferred.",
        "No backend retrieval quality is measured by this placeholder query.",
      ),
      negativeOutOfCatalog = false,
    )

  private def placeholderRow(queryId: String): M9OfflineEvalReportRow =
    M9OfflineEvalReportRow(
      queryId = queryId,
      queryClass = QueryClass.Category,
      servingMode = ServingMode.Unknown,
      candidateSource = CandidateSource.Manual,
      topKResultIds = Nil,
      metrics = List(
        metric(OfflineEvalMetricName.RegressionPassFail, OfflineEvalMetricValue.Text("unknown")),
        metric(OfflineEvalMetricName.QualityGateDecision, OfflineEvalMetricValue.Text("static_dataset_placeholder_only")),
      ),
      regressionStatus = "unknown",
      warnings = List(
        "Static dataset fixture placeholder only; top_k_result_ids are intentionally empty and are not backend retrieval results.",
        "No ES, Qdrant, route, plugin, DI, HTTP, hybrid, fusion, or reranking execution is represented.",
      ),
    )

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
      s"Full 74-query static-row expansion implemented: mapped_row_count=${summary.mappedRowCount}; full_dataset_query_count=${summary.fullDatasetQueryCount}.",
      "This slice uses a checked-in static query-id list validated against bounded resource anchors instead of parsing the complete JSON.",
      "Default /beauty-search remains ES-backed; Qdrant production activation remains not approved.",
    )

  private def warnings: List[String] =
    List(
      "Offline dataset fixture mapping only; not production telemetry and not activation approval.",
      "Real backend calls remain disabled by default and are not required to build this report.",
      "No JSON parser/dependency is used; full JSON parsing is intentionally deferred.",
      "Rows are static placeholders/fixture anchors only, not backend retrieval results or production quality evidence.",
    )

  private def metric(
    name: OfflineEvalMetricName,
    value: OfflineEvalMetricValue,
  ): OfflineEvalMetric =
    OfflineEvalMetric(name, value)
}
