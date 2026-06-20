package leaderboard.search

import leaderboard.search.eval.{
  M9OfflineEvalBackendAdapterFailureCategory,
  M9OfflineEvalBackendAdapterFailureDisposition,
  M9OfflineEvalBackendAdapterFailureMatrix,
  M9OfflineEvalBackendAdapterFailureSeverity,
  M9OfflineEvalBackendRunError,
  M9OfflineEvalBackendRunner,
  M9OfflineEvalStaticRunner,
  M9OfflineEvalStaticRunnerConfig,
  OfflineEvalMetricName,
}
import org.scalatest.wordspec.AnyWordSpec

final class M9OfflineEvalBackendAdapterFailureMatrixSpec extends AnyWordSpec {

  "M9OfflineEvalBackendAdapterFailureMatrix" should {

    "iterate stable failure cases with expected category, severity, and code" in {
      assert(M9OfflineEvalBackendAdapterFailureMatrix.StableCases.map(_.caseId) == List(
        "es_not_connected",
        "qdrant_not_connected",
        "es_candidate_source_mismatch",
        "qdrant_candidate_source_mismatch",
        "es_execution_mode_mismatch",
        "qdrant_execution_mode_mismatch",
        "duplicate_supplied_row_for_same_query_id",
        "missing_dataset_id",
        "missing_catalog_snapshot_id",
        "missing_query_id",
        "missing_latency_metric",
        "backend_failure_row_as_data",
        "qdrant_required_metadata_warning",
        "future_hybrid_comparison_vocabulary_only",
        "hidden_fallback_not_representable",
        "output_must_not_approve_production_activation",
      ))

      M9OfflineEvalBackendAdapterFailureMatrix.StableCases.foreach { failureCase =>
        assert(failureCase.caseId.trim.nonEmpty)
        assert(failureCase.queryIdOrMutation.trim.nonEmpty)
        assert(renderedEvidence(failureCase).exists(_.contains(failureCase.expectation.codeOrPhrase)))
        assert(failureCase.expectation.productionActivationNotApproved)
        (): Unit
      }
    }

    "represent not-connected and backend execution failures as data" in {
      val caseIds = Set("es_not_connected", "qdrant_not_connected", "backend_failure_row_as_data")

      M9OfflineEvalBackendAdapterFailureMatrix.StableCases.filter(value => caseIds.contains(value.caseId)).foreach { failureCase =>
        assert(failureCase.expectation.disposition == M9OfflineEvalBackendAdapterFailureDisposition.RightFailureRowsAsData)

        M9OfflineEvalBackendRunner.run(failureCase.request, failureCase.adapter) match {
          case Right(response) =>
            assert(response.queryResults.exists(_.failure.nonEmpty))
            assert(response.rows.exists(_.metrics.exists(_.name == OfflineEvalMetricName.FailureCount)))
            assert(response.rows.flatMap(_.warnings).exists(_.contains(failureCase.expectation.codeOrPhrase)))
          case Left(error) =>
            fail(s"expected ${failureCase.caseId} to be represented as failure rows, got $error")
        }
        (): Unit
      }
    }

    "make hard source and execution-mode attribution failures deterministic" in {
      val caseIds = Set(
        "es_candidate_source_mismatch",
        "qdrant_candidate_source_mismatch",
        "es_execution_mode_mismatch",
        "qdrant_execution_mode_mismatch",
      )

      M9OfflineEvalBackendAdapterFailureMatrix.StableCases.filter(value => caseIds.contains(value.caseId)).foreach { failureCase =>
        assert(failureCase.expectation.category == M9OfflineEvalBackendAdapterFailureCategory.Attribution ||
          failureCase.expectation.category == M9OfflineEvalBackendAdapterFailureCategory.ExecutionMode)
        assert(failureCase.expectation.severity == M9OfflineEvalBackendAdapterFailureSeverity.Failure)

        val first = renderedEvidence(failureCase)
        val second = renderedEvidence(failureCase)

        assert(first == second)
        assert(first.exists(_.contains(failureCase.expectation.codeOrPhrase)))
        (): Unit
      }
    }

    "keep latency absence warning-only when supplied candidates otherwise exist" in {
      val failureCase = requiredCase("missing_latency_metric")

      assert(failureCase.expectation.category == M9OfflineEvalBackendAdapterFailureCategory.Latency)
      assert(failureCase.expectation.severity == M9OfflineEvalBackendAdapterFailureSeverity.Warning)
      assert(failureCase.expectation.disposition == M9OfflineEvalBackendAdapterFailureDisposition.RightRowsWithWarnings)

      M9OfflineEvalBackendRunner.run(failureCase.request, failureCase.adapter) match {
        case Right(response) =>
          val rows = response.queryResults.filter(_.queryId == "q_matrix_exact_001")

          rows match {
            case row :: Nil =>
              assert(row.failure.isEmpty)
              assert(row.candidates.nonEmpty)
              assert(row.warnings.exists(_.contains("latency_missing")))
            case other =>
              fail(s"expected one latency warning row, got $other")
          }
        case Left(error) =>
          fail(s"expected latency warning row, got $error")
      }
    }

    "keep required Qdrant metadata warning-only and unexecuted" in {
      val failureCase = requiredCase("qdrant_required_metadata_warning")

      assert(failureCase.expectation.category == M9OfflineEvalBackendAdapterFailureCategory.RequiredMetadata)
      assert(failureCase.expectation.severity == M9OfflineEvalBackendAdapterFailureSeverity.Warning)

      M9OfflineEvalBackendRunner.run(failureCase.request, failureCase.adapter) match {
        case Right(response) =>
          assert(response.queryResults.exists(_.candidates.nonEmpty))
          assert(response.queryResults.forall(_.failure.isEmpty))
          assert(response.warnings.exists(_.contains("required metadata only, not executed: embedding_model_identity")))
          assert(response.warnings.forall(!_.toLowerCase.contains("connected")))
        case Left(error) =>
          fail(s"expected Qdrant metadata warning response, got $error")
      }
    }

    "represent duplicate supplied rows as failure data" in {
      val failureCase = requiredCase("duplicate_supplied_row_for_same_query_id")

      assert(failureCase.expectation.category == M9OfflineEvalBackendAdapterFailureCategory.DuplicateRows)
      assert(failureCase.expectation.disposition == M9OfflineEvalBackendAdapterFailureDisposition.RightFailureRowsAsData)

      M9OfflineEvalBackendRunner.run(failureCase.request, failureCase.adapter) match {
        case Right(response) =>
          assert(response.queryResults.exists(_.failure.exists(_.message.contains("multiple supplied skeleton rows"))))
          assert(response.rows.exists(_.metrics.exists(_.name == OfflineEvalMetricName.FailureCount)))
        case Left(error) =>
          fail(s"expected duplicate supplied rows as data, got $error")
      }
    }

    "treat missing dataset, catalog, and query attribution as validation errors" in {
      val caseIds = Set("missing_dataset_id", "missing_catalog_snapshot_id", "missing_query_id")

      M9OfflineEvalBackendAdapterFailureMatrix.StableCases.filter(value => caseIds.contains(value.caseId)).foreach { failureCase =>
        assert(failureCase.expectation.disposition == M9OfflineEvalBackendAdapterFailureDisposition.LeftValidationError)
        assert(!failureCase.expectation.staticRunnerCompatible)

        M9OfflineEvalBackendRunner.run(failureCase.request, failureCase.adapter) match {
          case Left(M9OfflineEvalBackendRunError(reasons)) =>
            assert(reasons.exists(_.contains(failureCase.expectation.codeOrPhrase)))
          case other =>
            fail(s"expected ${failureCase.caseId} to fail validation, got $other")
        }
        (): Unit
      }
    }

    "keep future hybrid comparison vocabulary-only" in {
      val failureCase = requiredCase("future_hybrid_comparison_vocabulary_only")
      val validation = M9OfflineEvalBackendAdapterFailureMatrix.validationCodes(failureCase)

      assert(failureCase.expectation.category == M9OfflineEvalBackendAdapterFailureCategory.HybridVocabulary)
      assert(failureCase.expectation.severity == M9OfflineEvalBackendAdapterFailureSeverity.Guardrail)
      assert(failureCase.expectation.disposition == M9OfflineEvalBackendAdapterFailureDisposition.VocabularyOnly)
      assert(validation.map(_.code).contains("hybrid_adapter_not_implemented"))
      assert(validation.map(_.message).exists(_.contains("future hybrid comparison is vocabulary only")))
    }

    "make hidden fallback unrepresentable without explicit source or mode mismatch" in {
      val failureCase = requiredCase("hidden_fallback_not_representable")
      val fields =
        failureCase.request.productElementNames.toList ++
          failureCase.config.productElementNames.toList ++
          failureCase.config.suppliedRows.flatMap(_.productElementNames.toList)

      assert(failureCase.expectation.category == M9OfflineEvalBackendAdapterFailureCategory.HiddenFallback)
      assert(failureCase.expectation.severity == M9OfflineEvalBackendAdapterFailureSeverity.Guardrail)
      assert(!fields.exists(_.toLowerCase.contains("fallback")))
      assert(M9OfflineEvalBackendAdapterFailureMatrix.validationCodes(failureCase).exists(
        _.message.contains("candidate source qdrant does not match adapter candidate source es")
      ))
    }

    "feed M9OfflineEvalStaticRunner when expected" in {
      val compatibleCases =
        M9OfflineEvalBackendAdapterFailureMatrix.StableCases.filter(_.expectation.staticRunnerCompatible)

      assert(compatibleCases.nonEmpty)

      compatibleCases.foreach { failureCase =>
        M9OfflineEvalBackendRunner.run(failureCase.request, failureCase.adapter) match {
          case Right(response) =>
            val staticResult = M9OfflineEvalStaticRunner.run(
              input = response.toStaticRunInput,
              config = M9OfflineEvalStaticRunnerConfig(
                markdownFilename = M9OfflineEvalBackendAdapterFailureMatrix.StaticRunnerFilename
              ),
            )

            staticResult match {
              case Right(staticRun) =>
                assert(staticRun.report.rows.map(_.queryId) == response.rows.map(_.queryId))
                assert(staticRun.report.warnings.exists(_.contains("production_activation_not_approved")))
              case Left(error) =>
                fail(s"expected ${failureCase.caseId} rows to feed static runner, got $error")
            }
          case Left(error) =>
            fail(s"expected ${failureCase.caseId} to produce static-compatible rows, got $error")
        }
        (): Unit
      }
    }

    "keep production activation explicitly not approved in generated warnings" in {
      M9OfflineEvalBackendAdapterFailureMatrix.StableCases.foreach { failureCase =>
        assert(failureCase.expectation.productionActivationNotApproved)

        failureCase.expectation.disposition match {
          case M9OfflineEvalBackendAdapterFailureDisposition.LeftValidationError |
              M9OfflineEvalBackendAdapterFailureDisposition.VocabularyOnly =>
            assert(failureCase.request.plan.warnings.exists(_.contains("production activation not approved")))
          case M9OfflineEvalBackendAdapterFailureDisposition.RightFailureRowsAsData |
              M9OfflineEvalBackendAdapterFailureDisposition.RightRowsWithWarnings =>
            M9OfflineEvalBackendRunner.run(failureCase.request, failureCase.adapter) match {
              case Right(response) =>
                assert(response.warnings.exists(_.contains("production_activation_not_approved")))
                assert(response.warnings.exists(_.contains("offline adapter skeleton output does not approve production activation")))
              case Left(error) =>
                fail(s"expected warnings for ${failureCase.caseId}, got $error")
            }
        }
        (): Unit
      }
    }

    "avoid route, plugin, DI, HTTP, Docker, metrics client, and real backend-client source surfaces" in {
      val forbiddenTerms = List("route", "plugin", "distage", "module", "http", "docker", "client")

      M9OfflineEvalBackendAdapterFailureMatrix.StableCases.foreach { failureCase =>
        val fields =
          failureCase.productElementNames.toList ++
            failureCase.request.productElementNames.toList ++
            failureCase.request.plan.productElementNames.toList ++
            failureCase.config.productElementNames.toList

        assert(!fields.exists(field => forbiddenTerms.exists(term => field.toLowerCase.contains(term))))
        (): Unit
      }
    }
  }

  private def requiredCase(caseId: String) =
    M9OfflineEvalBackendAdapterFailureMatrix.StableCases.filter(_.caseId == caseId) match {
      case failureCase :: Nil => failureCase
      case other              => fail(s"expected one matrix case $caseId, got $other")
    }

  private def renderedEvidence(failureCase: leaderboard.search.eval.M9OfflineEvalBackendAdapterFailureCase): List[String] =
    failureCase.expectation.disposition match {
      case M9OfflineEvalBackendAdapterFailureDisposition.LeftValidationError =>
        M9OfflineEvalBackendRunner.run(failureCase.request, failureCase.adapter) match {
          case Left(M9OfflineEvalBackendRunError(reasons)) => reasons
          case other                                       => fail(s"expected validation error for ${failureCase.caseId}, got $other")
        }
      case M9OfflineEvalBackendAdapterFailureDisposition.VocabularyOnly =>
        M9OfflineEvalBackendAdapterFailureMatrix.validationCodes(failureCase).flatMap(value => List(value.code, value.message))
      case M9OfflineEvalBackendAdapterFailureDisposition.RightFailureRowsAsData |
          M9OfflineEvalBackendAdapterFailureDisposition.RightRowsWithWarnings =>
        M9OfflineEvalBackendRunner.run(failureCase.request, failureCase.adapter) match {
          case Right(response) =>
            response.warnings ++
              response.queryResults.flatMap(result =>
                result.warnings ++ result.failure.map(_.message).toList
              )
          case Left(error) =>
            fail(s"expected backend response for ${failureCase.caseId}, got $error")
        }
    }
}
