package leaderboard.search

import leaderboard.search.eval.{
  M8TelemetrySchemaAdapter,
  M8TelemetrySchemaAvailability,
  M8TelemetrySchemaFormatVersion,
  M8TelemetrySchemaRenderer,
  TelemetryEventFamily,
  TelemetryMetricName,
  TelemetrySchemaSummary,
}
import org.scalatest.wordspec.AnyWordSpec

final class M8TelemetrySchemaRendererSpec extends AnyWordSpec {

  "M8TelemetrySchemaAdapter.defaultPlannedSchema" should {

    "keep the stable schema format version" in {
      assert(M8TelemetrySchemaFormatVersion.Current.render == "m8-telemetry-schema-v1")
      assert(schema.formatVersion == M8TelemetrySchemaFormatVersion.Current)
    }

    "preserve planned event-family order deterministically" in {
      assert(schema.events.map(_.family) == TelemetryEventFamily.plannedM8Families)
      assert(schema.events.map(_.family.render) == List(
        "search_request",
        "backend_candidate",
        "result_exposure",
        "optional_interaction",
        "failure_timeout",
      ))
    }

    "include planned field names for request, attribution, offline context, latency, errors, notes, and warnings" in {
      val renderedFields = schema.fields.map(_.name.render)

      assert(renderedFields.contains("timestamp"))
      assert(renderedFields.contains("request_id"))
      assert(renderedFields.contains("query_text_or_privacy_safe_placeholder"))
      assert(renderedFields.contains("normalized_query"))
      assert(renderedFields.contains("filters"))
      assert(renderedFields.contains("categories"))
      assert(renderedFields.contains("serving_mode"))
      assert(renderedFields.contains("candidate_source"))
      assert(renderedFields.contains("query_class"))
      assert(renderedFields.contains("routing_policy_id"))
      assert(renderedFields.contains("fusion_policy"))
      assert(renderedFields.contains("reranker_policy"))
      assert(renderedFields.contains("experiment_id"))
      assert(renderedFields.contains("catalog_snapshot_id"))
      assert(renderedFields.contains("eval_dataset_id"))
      assert(renderedFields.contains("candidate_count"))
      assert(renderedFields.contains("top_k_ids"))
      assert(renderedFields.contains("latency_ms"))
      assert(renderedFields.contains("backend_latency_ms"))
      assert(renderedFields.contains("embedding_latency_ms"))
      assert(renderedFields.contains("timeout"))
      assert(renderedFields.contains("error_status"))
      assert(renderedFields.contains("notes"))
      assert(renderedFields.contains("warnings"))
    }

    "include planned metric names and mark future-conditional metrics explicitly" in {
      assert(schema.metrics.map(_.name) == TelemetryMetricName.plannedM8Metrics)

      val conditionalMetrics = schema.metrics.collect {
        case metric if metric.availability == M8TelemetrySchemaAvailability.ConditionalFuture => metric.name.render
      }

      assert(conditionalMetrics == List(
        "fusion_latency",
        "rerank_latency",
        "fallback_used_rate",
        "interaction_proxy",
      ))
      assert(schema.metrics.filter(_.availability == M8TelemetrySchemaAvailability.Planned).map(_.name.render).contains("manual_relevance_judgment"))
    }

    "summarize through the existing Option109 schema-plan contracts" in {
      val summary = TelemetrySchemaSummary.from(schema.schemaPlan)

      assert(schema.schemaPlan.eventFamilies == TelemetryEventFamily.plannedM8Families)
      assert(schema.schemaPlan.metrics == TelemetryMetricName.plannedM8Metrics)
      assert(summary == TelemetrySchemaSummary(
        eventFamilyCount = 5,
        fieldCount = schema.fields.size,
        metricCount = 17,
      ))
    }

    "avoid route, plugin, DI, HTTP, backend client, and telemetry emission source surfaces" in {
      val fields =
        schema.productElementNames.toList ++
          schema.events.flatMap(_.productElementNames.toList) ++
          schema.fields.flatMap(_.productElementNames.toList) ++
          schema.metrics.flatMap(_.productElementNames.toList)
      val forbiddenTerms = List("route", "plugin", "distage", "module", "http", "client", "emitter")

      assert(!fields.exists(field => forbiddenTerms.exists(term => field.toLowerCase.contains(term))))
    }
  }

  "M8TelemetrySchemaRenderer.renderMarkdown" should {

    "render every M8 event family and planned metric without representing production telemetry emission" in {
      val rendered = M8TelemetrySchemaRenderer.renderMarkdown(schema)

      assert(TelemetryEventFamily.plannedM8Families.forall(family => rendered.contains(s"| ${family.render} |")))
      assert(TelemetryMetricName.plannedM8Metrics.forall(metric => rendered.contains(s"| ${metric.render} |")))
      assert(rendered.contains("This artifact is a pure planned telemetry schema."))
      assert(rendered.contains("| fusion_latency | conditional_future | Fusion latency only if score/candidate fusion is implemented later. |"))
      assert(rendered.contains("| rerank_latency | conditional_future | Rerank latency only if reranking is implemented later. |"))
      assert(rendered.contains("| fallback_used_rate | conditional_future | Fallback-used rate only if fallback is explicitly implemented later. |"))
      assert(!rendered.contains("telemetry emitted"))
      assert(!rendered.contains("production traffic observed"))
      assert(!rendered.contains("Elasticsearch query executed"))
      assert(!rendered.contains("Qdrant query executed"))
    }

    "preserve input order for events, fields, metrics, notes, and warnings" in {
      val rendered = M8TelemetrySchemaRenderer.renderMarkdown(schema)

      assert(rendered.indexOf("| search_request |") < rendered.indexOf("| backend_candidate |"))
      assert(rendered.indexOf("| backend_candidate |") < rendered.indexOf("| result_exposure |"))
      assert(rendered.indexOf("| timestamp |") < rendered.indexOf("| request_id |"))
      assert(rendered.indexOf("| request_count |") < rendered.indexOf("| zero_result_rate |"))
      assert(rendered.indexOf("- Static schema descriptor only") < rendered.indexOf("- Use these names"))
      assert(rendered.indexOf("- Telemetry emission is future work.") < rendered.indexOf("- Fusion, rerank, fallback"))
    }

    "render byte-for-byte stable markdown and compact text" in {
      val markdown = M8TelemetrySchemaRenderer.renderMarkdown(schema)
      val markdownAgain = M8TelemetrySchemaRenderer.renderMarkdown(schema)
      val compact = M8TelemetrySchemaRenderer.renderCompactText(schema)
      val compactAgain = M8TelemetrySchemaRenderer.renderCompactText(schema)

      assert(markdown == markdownAgain)
      assert(compact == compactAgain)
      assert(compact.startsWith("format_version=m8-telemetry-schema-v1\n"))
      assert(compact.contains("event_families=search_request,backend_candidate,result_exposure,optional_interaction,failure_timeout"))
      assert(compact.contains("fusion_latency:conditional_future"))
    }
  }

  private def schema =
    M8TelemetrySchemaAdapter.defaultPlannedSchema
}
