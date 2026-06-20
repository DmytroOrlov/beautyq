package leaderboard.search.eval

object M9OfflineEvalStaticFixtures {
  val EvalDataset: EvalDatasetId = EvalDatasetId("beautyq-m9-static-fixture-v1")
  val CatalogSnapshot: CatalogSnapshotId = CatalogSnapshotId("seed-resource-catalog-static-fixture")

  val Dataset: M9OfflineEvalDataset =
    M9OfflineEvalDataset(
      evalDatasetId = EvalDataset,
      catalogSnapshotId = CatalogSnapshot,
      queries = List(
        M9OfflineEvalDatasetQuery(
          queryId = "m9_static_exact_001",
          rawQueryText = "Aveda botanical repair treatment",
          normalizedQueryText = Some("aveda botanical repair treatment"),
          queryClass = QueryClass.ExactProductNameBrand,
          filters = List("fixture:exact_product_or_brand"),
          categories = List("hair_treatment"),
          expectedResults = List(M9OfflineEvalExpectedResult(
            resultId = "fixture-variant-aveda-botanical-repair",
            note = Some("Sample exact/name/brand expected result; not a real relevance judgment."),
          )),
          expectedNotes = List("Fixture-only exact query for saved-report shape."),
          negativeOutOfCatalog = false,
        ),
        M9OfflineEvalDatasetQuery(
          queryId = "m9_static_semantic_001",
          rawQueryText = "something calming for sensitive red skin",
          normalizedQueryText = Some("calming sensitive red skin"),
          queryClass = QueryClass.SemanticDescriptive,
          filters = Nil,
          categories = List("skin_care"),
          expectedResults = List(M9OfflineEvalExpectedResult(
            resultId = "fixture-variant-calming-sensitive-skin",
            note = Some("Sample semantic expected result for artifact shape only."),
          )),
          expectedNotes = List("Fixture-only semantic/descriptive query; no vector retrieval was run."),
          negativeOutOfCatalog = false,
        ),
        M9OfflineEvalDatasetQuery(
          queryId = "m9_static_ambiguous_001",
          rawQueryText = "glow",
          normalizedQueryText = Some("glow"),
          queryClass = QueryClass.Ambiguous,
          filters = Nil,
          categories = Nil,
          expectedResults = List(
            M9OfflineEvalExpectedResult(
              resultId = "fixture-variant-glow-facial",
              note = Some("Sample ambiguous candidate; not adjudicated relevance."),
            ),
            M9OfflineEvalExpectedResult(
              resultId = "fixture-variant-glow-serum",
              note = Some("Sample ambiguous candidate; not adjudicated relevance."),
            ),
          ),
          expectedNotes = List("Fixture-only ambiguous query with multiple plausible interpretations."),
          negativeOutOfCatalog = false,
        ),
        M9OfflineEvalDatasetQuery(
          queryId = "m9_static_negative_001",
          rawQueryText = "intergalactic chrome manicure",
          normalizedQueryText = Some("intergalactic chrome manicure"),
          queryClass = QueryClass.NegativeOutOfCatalog,
          filters = Nil,
          categories = Nil,
          expectedResults = Nil,
          expectedNotes = List("Fixture-only negative/out-of-catalog query expected to stay empty."),
          negativeOutOfCatalog = true,
        ),
      ),
    )

  val Metadata: OfflineEvalRunMetadata =
    OfflineEvalRunMetadata(
      servingMode = ServingMode.Unknown,
      candidateSource = CandidateSource.Manual,
      routingPolicyId = RoutingPolicyId("m9-static-fixture-no-serving-v1"),
      fusionPolicy = FusionPolicy.None,
      rerankerPolicy = RerankerPolicy.None,
      experimentId = ExperimentId("m9-static-fixture-example"),
      catalogSnapshotId = CatalogSnapshot,
      evalDatasetId = EvalDataset,
      metricWindow = MetricWindow("static-fixture"),
    )

  val Rows: List[M9OfflineEvalReportRow] =
    List(
      row(
        queryId = "m9_static_exact_001",
        queryClass = QueryClass.ExactProductNameBrand,
        topKResultIds = List("fixture-variant-aveda-botanical-repair"),
        metrics = List(
          metric(OfflineEvalMetricName.RecallAtK, OfflineEvalMetricValue.Numeric(BigDecimal("1.0"))),
          metric(OfflineEvalMetricName.RegressionPassFail, OfflineEvalMetricValue.Text("sample_pass")),
        ),
        regressionStatus = "sample_pass",
        warnings = Nil,
      ),
      row(
        queryId = "m9_static_semantic_001",
        queryClass = QueryClass.SemanticDescriptive,
        topKResultIds = List("fixture-variant-calming-sensitive-skin"),
        metrics = List(
          metric(OfflineEvalMetricName.RecallAtK, OfflineEvalMetricValue.Numeric(BigDecimal("1.0"))),
          metric(OfflineEvalMetricName.Mrr, OfflineEvalMetricValue.Numeric(BigDecimal("1.0"))),
          metric(OfflineEvalMetricName.RegressionPassFail, OfflineEvalMetricValue.Text("sample_review")),
        ),
        regressionStatus = "sample_review",
        warnings = List("Fixture/sample relevance only."),
      ),
      row(
        queryId = "m9_static_ambiguous_001",
        queryClass = QueryClass.Ambiguous,
        topKResultIds = List("fixture-variant-glow-facial", "fixture-variant-glow-serum"),
        metrics = List(
          metric(OfflineEvalMetricName.RecallAtK, OfflineEvalMetricValue.Numeric(BigDecimal("1.0"))),
          metric(OfflineEvalMetricName.LowResultRate, OfflineEvalMetricValue.Numeric(BigDecimal("0.0"))),
          metric(OfflineEvalMetricName.RegressionPassFail, OfflineEvalMetricValue.Text("sample_review")),
        ),
        regressionStatus = "sample_review",
        warnings = List("Ambiguous fixture row; no real adjudication."),
      ),
      row(
        queryId = "m9_static_negative_001",
        queryClass = QueryClass.NegativeOutOfCatalog,
        topKResultIds = Nil,
        metrics = List(
          metric(OfflineEvalMetricName.ZeroResultRate, OfflineEvalMetricValue.Numeric(BigDecimal("1.0"))),
          metric(OfflineEvalMetricName.RegressionPassFail, OfflineEvalMetricValue.Text("sample_pass")),
        ),
        regressionStatus = "sample_pass",
        warnings = List("Negative/out-of-catalog fixture row."),
      ),
    )

  val QualityGateDecision: String = "sample_not_for_activation"

  val AggregateMetrics: List[OfflineEvalMetric] =
    List(
      metric(OfflineEvalMetricName.RecallAtK, OfflineEvalMetricValue.Numeric(BigDecimal("0.75"))),
      metric(OfflineEvalMetricName.ZeroResultRate, OfflineEvalMetricValue.Numeric(BigDecimal("0.25"))),
      metric(OfflineEvalMetricName.FailureCount, OfflineEvalMetricValue.Numeric(BigDecimal("0"))),
      metric(OfflineEvalMetricName.QualityGateDecision, OfflineEvalMetricValue.Text(QualityGateDecision)),
    )

  val Notes: List[String] =
    List(
      "Canonical static M9 fixture for saved-report artifact shape.",
      "All rows and expected results are fixture/sample values, not production metrics or real relevance judgments.",
      "Non-serving boundary: this artifact was generated from static fixture rows only; it did not query Elasticsearch, Qdrant, HTTP, routes, Distage, Docker, or production backends.",
    )

  val Warnings: List[String] =
    List(
      "Example-only artifact; do not use for activation approval.",
      "Offline ES/Qdrant backend runner remains future work.",
      "Hybrid serving, score fusion, reranking, and production telemetry emission are not represented.",
    )

  val Input: M9OfflineEvalStaticRunInput =
    M9OfflineEvalStaticRunInput(
      dataset = Dataset,
      metadata = Metadata,
      reportEvalDatasetId = EvalDataset,
      reportCatalogSnapshotId = CatalogSnapshot,
      rows = Rows,
      aggregateMetrics = AggregateMetrics,
      qualityGateDecision = QualityGateDecision,
      notes = Notes,
      warnings = Warnings,
      generatedAt = "2026-06-20T00:00:00Z",
    )

  private def row(
    queryId: String,
    queryClass: QueryClass,
    topKResultIds: List[String],
    metrics: List[OfflineEvalMetric],
    regressionStatus: String,
    warnings: List[String],
  ): M9OfflineEvalReportRow =
    M9OfflineEvalReportRow(
      queryId = queryId,
      queryClass = queryClass,
      servingMode = Metadata.servingMode,
      candidateSource = Metadata.candidateSource,
      topKResultIds = topKResultIds,
      metrics = metrics,
      regressionStatus = regressionStatus,
      warnings = warnings,
    )

  private def metric(
    name: OfflineEvalMetricName,
    value: OfflineEvalMetricValue,
  ): OfflineEvalMetric =
    OfflineEvalMetric(name, value)
}

object M9OfflineEvalExampleArtifacts {
  val MarkdownFilename: String = "m9-static-example-report.md"

  val StaticRun: M9OfflineEvalStaticRunResult =
    M9OfflineEvalStaticRunner
      .run(
        input = M9OfflineEvalStaticFixtures.Input,
        config = M9OfflineEvalStaticRunnerConfig(markdownFilename = MarkdownFilename),
      )
      .fold(
        failure => throw new IllegalStateException(s"Invalid M9 static fixture: ${failure.reasons.mkString("; ")}"),
        identity,
      )

  val MarkdownArtifact: M9OfflineEvalReportArtifact = StaticRun.markdownArtifact
}
