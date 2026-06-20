package leaderboard.search.eval

final case class M8TelemetrySchemaFormatVersion(value: String) extends AnyVal {
  def render: String = value
}

object M8TelemetrySchemaFormatVersion {
  val Current: M8TelemetrySchemaFormatVersion = M8TelemetrySchemaFormatVersion("m8-telemetry-schema-v1")
}

enum M8TelemetrySchemaAvailability {
  case Planned
  case ConditionalFuture

  def render: String =
    this match {
      case M8TelemetrySchemaAvailability.Planned           => "planned"
      case M8TelemetrySchemaAvailability.ConditionalFuture => "conditional_future"
    }
}

final case class M8TelemetryField(
  name: TelemetryFieldName,
  description: String,
  availability: M8TelemetrySchemaAvailability,
)

final case class M8TelemetryEventSchema(
  family: TelemetryEventFamily,
  fields: List[TelemetryFieldName],
  notes: List[String],
  warnings: List[String],
)

final case class M8TelemetryMetricSchema(
  name: TelemetryMetricName,
  description: String,
  availability: M8TelemetrySchemaAvailability,
)

final case class M8TelemetrySchemaDocument(
  formatVersion: M8TelemetrySchemaFormatVersion,
  events: List[M8TelemetryEventSchema],
  fields: List[M8TelemetryField],
  metrics: List[M8TelemetryMetricSchema],
  notes: List[String],
  warnings: List[String],
) {
  def schemaPlan: TelemetrySchemaPlan =
    TelemetrySchemaPlan(
      eventFamilies = events.map(_.family),
      fields = fields.map(_.name),
      metrics = metrics.map(_.name),
    )
}

object M8TelemetrySchemaRenderer {

  def renderMarkdown(document: M8TelemetrySchemaDocument): String = {
    val builder = new StringBuilder

    line(builder, "# M8 Telemetry Schema")
    line(builder, "")
    line(builder, "This artifact is a pure planned telemetry schema. It does not represent telemetry emission, route hooks, metrics clients, backend clients, or production traffic.")
    line(builder, "")
    line(builder, "## Format")
    line(builder, "")
    line(builder, s"- format_version: ${document.formatVersion.render}")
    renderListSection(builder, "Notes", document.notes)
    renderListSection(builder, "Warnings", document.warnings)
    renderEvents(builder, document.events)
    renderFields(builder, document.fields)
    renderMetrics(builder, document.metrics)

    builder.result()
  }

  def renderCompactText(document: M8TelemetrySchemaDocument): String =
    List(
      s"format_version=${document.formatVersion.render}",
      s"event_families=${document.events.map(_.family.render).mkString(",")}",
      s"fields=${document.fields.map(_.name.render).mkString(",")}",
      s"metrics=${document.metrics.map(metric => s"${metric.name.render}:${metric.availability.render}").mkString(",")}",
      s"notes=${document.notes.map(renderText).mkString(";")}",
      s"warnings=${document.warnings.map(renderText).mkString(";")}",
    ).mkString("\n") + "\n"

  private def renderEvents(
    builder: StringBuilder,
    events: List[M8TelemetryEventSchema],
  ): Unit = {
    line(builder, "")
    line(builder, "## Event Families")
    line(builder, "")
    line(builder, "| family | fields | notes | warnings |")
    line(builder, "|---|---|---|---|")
    events match {
      case Nil =>
        line(builder, "| - | - | - | - |")
      case _ =>
        events.foreach { event =>
          line(
            builder,
            s"| ${event.family.render} | ${renderValues(event.fields.map(_.render))} | ${renderValues(event.notes)} | ${renderValues(event.warnings)} |",
          )
        }
    }
  }

  private def renderFields(
    builder: StringBuilder,
    fields: List[M8TelemetryField],
  ): Unit = {
    line(builder, "")
    line(builder, "## Fields")
    line(builder, "")
    line(builder, "| field | availability | description |")
    line(builder, "|---|---|---|")
    fields match {
      case Nil =>
        line(builder, "| - | - | - |")
      case _ =>
        fields.foreach { field =>
          line(builder, s"| ${field.name.render} | ${field.availability.render} | ${renderText(field.description)} |")
        }
    }
  }

  private def renderMetrics(
    builder: StringBuilder,
    metrics: List[M8TelemetryMetricSchema],
  ): Unit = {
    line(builder, "")
    line(builder, "## Metrics")
    line(builder, "")
    line(builder, "| metric | availability | description |")
    line(builder, "|---|---|---|")
    metrics match {
      case Nil =>
        line(builder, "| - | - | - |")
      case _ =>
        metrics.foreach { metric =>
          line(builder, s"| ${metric.name.render} | ${metric.availability.render} | ${renderText(metric.description)} |")
        }
    }
  }

  private def renderListSection(
    builder: StringBuilder,
    title: String,
    values: List[String],
  ): Unit = {
    line(builder, "")
    line(builder, s"## $title")
    line(builder, "")
    values match {
      case Nil => line(builder, "- -")
      case _   => values.foreach(value => line(builder, s"- ${renderText(value)}"))
    }
  }

  private def renderValues(values: List[String]): String =
    values match {
      case Nil => "-"
      case _   => values.map(renderText).mkString(", ")
    }

  private def renderText(value: String): String =
    value
      .replace("\r\n", " ")
      .replace('\n', ' ')
      .replace('\r', ' ')
      .replace("|", "\\|")

  private def line(builder: StringBuilder, value: String): Unit = {
    builder.append(value)
    builder.append('\n')
  }
}

object M8TelemetrySchemaAdapter {
  private val Planned: M8TelemetrySchemaAvailability = M8TelemetrySchemaAvailability.Planned
  private val ConditionalFuture: M8TelemetrySchemaAvailability = M8TelemetrySchemaAvailability.ConditionalFuture

  def defaultPlannedSchema: M8TelemetrySchemaDocument =
    M8TelemetrySchemaDocument(
      formatVersion = M8TelemetrySchemaFormatVersion.Current,
      events = defaultEvents,
      fields = defaultFields,
      metrics = defaultMetrics,
      notes = List(
        "Static schema descriptor only; no request, clock, HTTP, route, metrics-client, ES, Qdrant, or production traffic reads are performed.",
        "Use these names to align future M8 telemetry and M9 offline evidence where semantics match.",
      ),
      warnings = List(
        "Telemetry emission is future work.",
        "Fusion, rerank, fallback, interaction, and production-traffic metrics are conditional future metrics only.",
      ),
    )

  private def defaultEvents: List[M8TelemetryEventSchema] =
    List(
      M8TelemetryEventSchema(
        family = TelemetryEventFamily.SearchRequest,
        fields = List(
          field("timestamp"),
          field("request_id"),
          field("query_text_or_privacy_safe_placeholder"),
          field("normalized_query"),
          field("filters"),
          field("categories"),
          field("serving_mode"),
          field("query_class"),
          field("routing_policy_id"),
          field("fusion_policy"),
          field("reranker_policy"),
          field("experiment_id"),
          field("catalog_snapshot_id"),
          field("eval_dataset_id"),
          field("latency_ms"),
          field("status"),
          field("notes"),
          field("warnings"),
        ),
        notes = List("Request-level planned fields only; no runtime request observation exists in this schema."),
        warnings = Nil,
      ),
      M8TelemetryEventSchema(
        family = TelemetryEventFamily.BackendCandidate,
        fields = List(
          field("timestamp"),
          field("request_id"),
          field("serving_mode"),
          field("candidate_source"),
          field("backend_label"),
          field("query_class"),
          field("candidate_count"),
          field("top_k_ids"),
          field("backend_latency_ms"),
          field("embedding_latency_ms"),
          field("routing_policy_id"),
          field("fusion_policy"),
          field("reranker_policy"),
          field("experiment_id"),
          field("catalog_snapshot_id"),
          field("timeout"),
          field("error_status"),
          field("notes"),
          field("warnings"),
        ),
        notes = List("Backend candidate attribution is planned for ES, Qdrant, manual, or unknown sources."),
        warnings = Nil,
      ),
      M8TelemetryEventSchema(
        family = TelemetryEventFamily.ResultExposure,
        fields = List(
          field("timestamp"),
          field("request_id"),
          field("serving_mode"),
          field("query_class"),
          field("top_k_ids"),
          field("result_count"),
          field("candidate_source"),
          field("routing_policy_id"),
          field("fusion_policy"),
          field("reranker_policy"),
          field("experiment_id"),
          field("catalog_snapshot_id"),
          field("notes"),
          field("warnings"),
        ),
        notes = List("Exposure schema is planned for future result presentation telemetry."),
        warnings = Nil,
      ),
      M8TelemetryEventSchema(
        family = TelemetryEventFamily.OptionalInteraction,
        fields = List(
          field("timestamp"),
          field("request_id"),
          field("result_id"),
          field("interaction_type"),
          field("position"),
          field("serving_mode"),
          field("candidate_source"),
          field("routing_policy_id"),
          field("fusion_policy"),
          field("reranker_policy"),
          field("experiment_id"),
          field("notes"),
          field("warnings"),
        ),
        notes = List("Interaction event is optional and relevant only if click, save, order, or comparable downstream data exists later."),
        warnings = List("No interaction data source exists in this schema slice."),
      ),
      M8TelemetryEventSchema(
        family = TelemetryEventFamily.FailureTimeout,
        fields = List(
          field("timestamp"),
          field("request_id"),
          field("serving_mode"),
          field("candidate_source"),
          field("failure_class"),
          field("timeout"),
          field("error_status"),
          field("latency_ms"),
          field("routing_policy_id"),
          field("fusion_policy"),
          field("reranker_policy"),
          field("experiment_id"),
          field("catalog_snapshot_id"),
          field("notes"),
          field("warnings"),
        ),
        notes = List("Failure and timeout fields are planned schema names only."),
        warnings = Nil,
      ),
    )

  private def defaultFields: List[M8TelemetryField] =
    List(
      plannedField("timestamp", "Event timestamp supplied by a future emitter."),
      plannedField("request_id", "Request-level correlation id."),
      plannedField("query_text_or_privacy_safe_placeholder", "Raw query text or a later privacy-safe placeholder/hash."),
      plannedField("normalized_query", "Normalized query text when available."),
      plannedField("filters", "Structured filters supplied or inferred for the request."),
      plannedField("categories", "Category inputs or inferred category labels."),
      plannedField("serving_mode", "Shared serving mode vocabulary."),
      plannedField("candidate_source", "Shared candidate source attribution vocabulary."),
      plannedField("query_class", "Shared query-class taxonomy when known."),
      plannedField("routing_policy_id", "Versioned routing policy identifier."),
      conditionalField("fusion_policy", "Fusion policy identifier; meaningful only if fusion is implemented later."),
      conditionalField("reranker_policy", "Reranker policy identifier; meaningful only if reranking is implemented later."),
      plannedField("experiment_id", "Experiment or run identifier."),
      plannedField("catalog_snapshot_id", "Catalog snapshot identifier."),
      plannedField("eval_dataset_id", "Offline dataset identifier when the context is offline/eval."),
      plannedField("candidate_count", "Number of candidates returned by a backend or final projection."),
      plannedField("top_k_ids", "Ordered top-k result or candidate ids."),
      plannedField("result_count", "Number of exposed results."),
      plannedField("latency_ms", "End-to-end latency in milliseconds."),
      plannedField("backend_latency_ms", "Backend-specific latency in milliseconds."),
      plannedField("embedding_latency_ms", "Embedding latency for Qdrant-like semantic paths when measured."),
      plannedField("timeout", "Timeout status flag."),
      plannedField("error_status", "Failure or timeout status/category."),
      plannedField("backend_label", "Backend implementation label or version when available."),
      plannedField("result_id", "Single exposed result id for optional interaction events."),
      conditionalField("interaction_type", "Click, save, order, or comparable interaction type if such data exists later."),
      conditionalField("position", "Displayed result rank or position if interaction data exists later."),
      plannedField("failure_class", "Failure class or family for planned failure telemetry."),
      plannedField("notes", "Human-readable notes for schema/evidence contexts."),
      plannedField("warnings", "Human-readable warnings for schema/evidence contexts."),
    )

  private def defaultMetrics: List[M8TelemetryMetricSchema] =
    List(
      plannedMetric(TelemetryMetricName.RequestCount, "Request count over a metric window."),
      plannedMetric(TelemetryMetricName.ZeroResultRate, "Share of requests or eval rows with zero results."),
      plannedMetric(TelemetryMetricName.LowResultRate, "Share of requests or eval rows below a configured result threshold."),
      plannedMetric(TelemetryMetricName.TopKCoverage, "Top-k result or candidate coverage for planned telemetry/evidence contexts."),
      plannedMetric(TelemetryMetricName.LatencyP50, "End-to-end latency p50."),
      plannedMetric(TelemetryMetricName.LatencyP95, "End-to-end latency p95."),
      plannedMetric(TelemetryMetricName.LatencyP99, "End-to-end latency p99."),
      plannedMetric(TelemetryMetricName.EsLatency, "Elasticsearch latency where an ES path is measured."),
      plannedMetric(TelemetryMetricName.QdrantLatency, "Qdrant latency where a Qdrant path is measured."),
      plannedMetric(TelemetryMetricName.EmbeddingLatency, "Embedding latency where semantic embedding is measured."),
      conditionalMetric(TelemetryMetricName.FusionLatency, "Fusion latency only if score/candidate fusion is implemented later."),
      conditionalMetric(TelemetryMetricName.RerankLatency, "Rerank latency only if reranking is implemented later."),
      plannedMetric(TelemetryMetricName.BackendFailureRate, "Backend failure rate over a metric window."),
      plannedMetric(TelemetryMetricName.TimeoutRate, "Timeout rate over a metric window."),
      conditionalMetric(TelemetryMetricName.FallbackUsedRate, "Fallback-used rate only if fallback is explicitly implemented later."),
      conditionalMetric(TelemetryMetricName.InteractionProxy, "Click/order/save proxy only if such downstream data exists later."),
      plannedMetric(TelemetryMetricName.ManualRelevanceJudgment, "Manual relevance judgment metric for offline/no-production-traffic context."),
    )

  private def field(name: String): TelemetryFieldName =
    TelemetryFieldName(name)

  private def plannedField(
    name: String,
    description: String,
  ): M8TelemetryField =
    M8TelemetryField(field(name), description, Planned)

  private def conditionalField(
    name: String,
    description: String,
  ): M8TelemetryField =
    M8TelemetryField(field(name), description, ConditionalFuture)

  private def plannedMetric(
    name: TelemetryMetricName,
    description: String,
  ): M8TelemetryMetricSchema =
    M8TelemetryMetricSchema(name, description, Planned)

  private def conditionalMetric(
    name: TelemetryMetricName,
    description: String,
  ): M8TelemetryMetricSchema =
    M8TelemetryMetricSchema(name, description, ConditionalFuture)
}
