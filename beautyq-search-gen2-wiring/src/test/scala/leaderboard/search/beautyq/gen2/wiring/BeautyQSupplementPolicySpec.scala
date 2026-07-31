package leaderboard.search.beautyq.gen2.wiring

import leaderboard.search.beautyq.gen2.wiring.BeautyQSupplementPolicy.*
import leaderboard.search.gen2.qdrant.*
import leaderboard.search.gen2.transport.Gen2HttpTransportError
import org.scalatest.wordspec.AnyWordSpec

final class BeautyQSupplementPolicySpec extends AnyWordSpec {

  "BeautyQSupplementPolicy.MaxAppended" should {
    "be exactly 1" in {
      assert(BeautyQSupplementPolicy.MaxAppended == 1)
    }
  }

  "BeautyQSupplementPolicy.appendOnly" should {
    "accept maxAppended = 1" in {
      assert(BeautyQSupplementPolicy.appendOnly.maxAppended == 1)
    }
  }

  "BeautyQDegradationReason stable codes" should {
    "be exact" in {
      assert(BeautyQDegradationReason.EmbeddingTimeout.reasonCode == "embedding-timeout")
      assert(BeautyQDegradationReason.EmbeddingUnavailable.reasonCode == "embedding-unavailable")
      assert(BeautyQDegradationReason.EmbeddingTransport.reasonCode == "embedding-transport")
      assert(BeautyQDegradationReason.QdrantUnavailable.reasonCode == "qdrant-unavailable")
      assert(BeautyQDegradationReason.QdrantTransport.reasonCode == "qdrant-transport")
      assert(BeautyQDegradationReason.QdrantBackend.reasonCode == "qdrant-backend")
    }
  }

  "BeautyQSupplementPolicy.classifyPipelineError" should {
    "classify embedding timeout as degradable with embedding-timeout and exact cause" in {
      val error = BeautyQQdrantCandidatePipelineError.Qdrant(
        QdrantCandidatePipelineError.Embedding(BeautyQEmbeddingRequestError.Timeout("timeout details"))
      )
      BeautyQSupplementPolicy.classifyPipelineError(error) match {
        case degradable: BeautyQSupplementPolicy.Degradable =>
          assert(degradable.reason == BeautyQDegradationReason.EmbeddingTimeout)
          assert(degradable.cause eq error)
        case other => fail(s"expected Degradable(embedding-timeout), got $other")
      }
    }

    "classify embedding unavailable as degradable with embedding-unavailable and exact cause" in {
      val error = BeautyQQdrantCandidatePipelineError.Qdrant(
        QdrantCandidatePipelineError.Embedding(BeautyQEmbeddingRequestError.Unavailable("unavailable details"))
      )
      BeautyQSupplementPolicy.classifyPipelineError(error) match {
        case degradable: BeautyQSupplementPolicy.Degradable =>
          assert(degradable.reason == BeautyQDegradationReason.EmbeddingUnavailable)
          assert(degradable.cause eq error)
        case other => fail(s"expected Degradable(embedding-unavailable), got $other")
      }
    }

    "classify embedding transport as degradable with embedding-transport and exact cause" in {
      val error = BeautyQQdrantCandidatePipelineError.Qdrant(
        QdrantCandidatePipelineError.Embedding(BeautyQEmbeddingRequestError.Transport("transport details"))
      )
      BeautyQSupplementPolicy.classifyPipelineError(error) match {
        case degradable: BeautyQSupplementPolicy.Degradable =>
          assert(degradable.reason == BeautyQDegradationReason.EmbeddingTransport)
          assert(degradable.cause eq error)
        case other => fail(s"expected Degradable(embedding-transport), got $other")
      }
    }

    "classify Qdrant alias missing as degradable with qdrant-unavailable and exact cause" in {
      val error = BeautyQQdrantCandidatePipelineError.Qdrant(
        QdrantCandidatePipelineError.Service(
          QdrantCandidateServiceError.AliasMissing("test-alias")
        )
      )
      BeautyQSupplementPolicy.classifyPipelineError(error) match {
        case degradable: BeautyQSupplementPolicy.Degradable =>
          assert(degradable.reason == BeautyQDegradationReason.QdrantUnavailable)
          assert(degradable.cause eq error)
        case other => fail(s"expected Degradable(qdrant-unavailable), got $other")
      }
    }

    "classify Qdrant connection failed as degradable with qdrant-transport and exact cause" in {
      val error = BeautyQQdrantCandidatePipelineError.Qdrant(
        QdrantCandidatePipelineError.Service(
          QdrantCandidateServiceError.Transport("query", Gen2HttpTransportError.ConnectionFailed("GET", "/test", "connection refused"))
        )
      )
      BeautyQSupplementPolicy.classifyPipelineError(error) match {
        case degradable: BeautyQSupplementPolicy.Degradable =>
          assert(degradable.reason == BeautyQDegradationReason.QdrantTransport)
          assert(degradable.cause eq error)
        case other => fail(s"expected Degradable(qdrant-transport), got $other")
      }
    }

    "classify Qdrant request failed as degradable with qdrant-transport and exact cause" in {
      val error = BeautyQQdrantCandidatePipelineError.Qdrant(
        QdrantCandidatePipelineError.Service(
          QdrantCandidateServiceError.Transport("query", Gen2HttpTransportError.RequestFailed("GET", "/test", "request failed"))
        )
      )
      BeautyQSupplementPolicy.classifyPipelineError(error) match {
        case degradable: BeautyQSupplementPolicy.Degradable =>
          assert(degradable.reason == BeautyQDegradationReason.QdrantTransport)
          assert(degradable.cause eq error)
        case other => fail(s"expected Degradable(qdrant-transport), got $other")
      }
    }

    "classify Qdrant 404 as degradable with qdrant-unavailable and exact cause" in {
      val error = BeautyQQdrantCandidatePipelineError.Qdrant(
        QdrantCandidatePipelineError.Service(
          QdrantCandidateServiceError.Transport("query", Gen2HttpTransportError.HttpFailure("GET", "/test", 404, "not found"))
        )
      )
      BeautyQSupplementPolicy.classifyPipelineError(error) match {
        case degradable: BeautyQSupplementPolicy.Degradable =>
          assert(degradable.reason == BeautyQDegradationReason.QdrantUnavailable)
          assert(degradable.cause eq error)
        case other => fail(s"expected Degradable(qdrant-unavailable), got $other")
      }
    }

    "classify Qdrant 408 as degradable with qdrant-backend and exact cause" in {
      val error = BeautyQQdrantCandidatePipelineError.Qdrant(
        QdrantCandidatePipelineError.Service(
          QdrantCandidateServiceError.Transport("query", Gen2HttpTransportError.HttpFailure("GET", "/test", 408, "timeout"))
        )
      )
      BeautyQSupplementPolicy.classifyPipelineError(error) match {
        case degradable: BeautyQSupplementPolicy.Degradable =>
          assert(degradable.reason == BeautyQDegradationReason.QdrantBackend)
          assert(degradable.cause eq error)
        case other => fail(s"expected Degradable(qdrant-backend), got $other")
      }
    }

    "classify Qdrant 429 as degradable with qdrant-backend and exact cause" in {
      val error = BeautyQQdrantCandidatePipelineError.Qdrant(
        QdrantCandidatePipelineError.Service(
          QdrantCandidateServiceError.Transport("query", Gen2HttpTransportError.HttpFailure("GET", "/test", 429, "rate limited"))
        )
      )
      BeautyQSupplementPolicy.classifyPipelineError(error) match {
        case degradable: BeautyQSupplementPolicy.Degradable =>
          assert(degradable.reason == BeautyQDegradationReason.QdrantBackend)
          assert(degradable.cause eq error)
        case other => fail(s"expected Degradable(qdrant-backend), got $other")
      }
    }

    "classify Qdrant 500 as degradable with qdrant-backend and exact cause" in {
      val error = BeautyQQdrantCandidatePipelineError.Qdrant(
        QdrantCandidatePipelineError.Service(
          QdrantCandidateServiceError.Transport("query", Gen2HttpTransportError.HttpFailure("GET", "/test", 500, "server error"))
        )
      )
      BeautyQSupplementPolicy.classifyPipelineError(error) match {
        case degradable: BeautyQSupplementPolicy.Degradable =>
          assert(degradable.reason == BeautyQDegradationReason.QdrantBackend)
          assert(degradable.cause eq error)
        case other => fail(s"expected Degradable(qdrant-backend), got $other")
      }
    }

    "classify Qdrant 502 as degradable with qdrant-backend and exact cause" in {
      val error = BeautyQQdrantCandidatePipelineError.Qdrant(
        QdrantCandidatePipelineError.Service(
          QdrantCandidateServiceError.Transport("query", Gen2HttpTransportError.HttpFailure("GET", "/test", 502, "bad gateway"))
        )
      )
      BeautyQSupplementPolicy.classifyPipelineError(error) match {
        case degradable: BeautyQSupplementPolicy.Degradable =>
          assert(degradable.reason == BeautyQDegradationReason.QdrantBackend)
          assert(degradable.cause eq error)
        case other => fail(s"expected Degradable(qdrant-backend), got $other")
      }
    }

    "classify Qdrant 503 as degradable with qdrant-backend and exact cause" in {
      val error = BeautyQQdrantCandidatePipelineError.Qdrant(
        QdrantCandidatePipelineError.Service(
          QdrantCandidateServiceError.Transport("query", Gen2HttpTransportError.HttpFailure("GET", "/test", 503, "unavailable"))
        )
      )
      BeautyQSupplementPolicy.classifyPipelineError(error) match {
        case degradable: BeautyQSupplementPolicy.Degradable =>
          assert(degradable.reason == BeautyQDegradationReason.QdrantBackend)
          assert(degradable.cause eq error)
        case other => fail(s"expected Degradable(qdrant-backend), got $other")
      }
    }

    "classify Qdrant malformed as degradable with qdrant-backend and exact cause" in {
      val error = BeautyQQdrantCandidatePipelineError.Qdrant(
        QdrantCandidatePipelineError.Service(
          QdrantCandidateServiceError.Malformed("query", "invalid JSON")
        )
      )
      BeautyQSupplementPolicy.classifyPipelineError(error) match {
        case degradable: BeautyQSupplementPolicy.Degradable =>
          assert(degradable.reason == BeautyQDegradationReason.QdrantBackend)
          assert(degradable.cause eq error)
        case other => fail(s"expected Degradable(qdrant-backend), got $other")
      }
    }

    "classify Qdrant response decoder error as degradable with qdrant-backend and exact cause" in {
      val error = BeautyQQdrantCandidatePipelineError.Qdrant(
        QdrantCandidatePipelineError.Service(
          QdrantCandidateServiceError.Response(QdrantCandidateResponseError.Malformed("test"))
        )
      )
      BeautyQSupplementPolicy.classifyPipelineError(error) match {
        case degradable: BeautyQSupplementPolicy.Degradable =>
          assert(degradable.reason == BeautyQDegradationReason.QdrantBackend)
          assert(degradable.cause eq error)
        case other => fail(s"expected Degradable(qdrant-backend), got $other")
      }
    }

    "classify candidate preparation as hard with exact cause" in {
      val error = BeautyQQdrantCandidatePipelineError.Qdrant(
        QdrantCandidatePipelineError.Preparation(QdrantCandidateCompileError.Constraint(0, "test"))
      )
      BeautyQSupplementPolicy.classifyPipelineError(error) match {
        case hard: BeautyQSupplementPolicy.Hard =>
          assert(hard.cause eq error)
        case other => fail(s"expected Hard, got $other")
      }
    }

    "classify candidate completion as hard with exact cause" in {
      val error = BeautyQQdrantCandidatePipelineError.Qdrant(
        QdrantCandidatePipelineError.Completion(QdrantCandidateCompileError.Constraint(0, "test"))
      )
      BeautyQSupplementPolicy.classifyPipelineError(error) match {
        case hard: BeautyQSupplementPolicy.Hard =>
          assert(hard.cause eq error)
        case other => fail(s"expected Hard, got $other")
      }
    }

    "classify Qdrant fingerprint mismatch as hard with exact cause" in {
      val error = BeautyQQdrantCandidatePipelineError.Qdrant(
        QdrantCandidatePipelineError.Service(
          QdrantCandidateServiceError.ContractFingerprintMismatch("expected", "actual")
        )
      )
      BeautyQSupplementPolicy.classifyPipelineError(error) match {
        case hard: BeautyQSupplementPolicy.Hard =>
          assert(hard.cause eq error)
        case other => fail(s"expected Hard, got $other")
      }
    }

    "classify Qdrant ambiguous alias as hard with exact cause" in {
      val error = BeautyQQdrantCandidatePipelineError.Qdrant(
        QdrantCandidatePipelineError.Service(
          QdrantCandidateServiceError.AliasAmbiguous("test", Vector("a", "b"))
        )
      )
      BeautyQSupplementPolicy.classifyPipelineError(error) match {
        case hard: BeautyQSupplementPolicy.Hard =>
          assert(hard.cause eq error)
        case other => fail(s"expected Hard, got $other")
      }
    }

    "classify Qdrant unauthorized collection as hard with exact cause" in {
      val error = BeautyQQdrantCandidatePipelineError.Qdrant(
        QdrantCandidatePipelineError.Service(
          QdrantCandidateServiceError.UnauthorizedCollection("test", "reason")
        )
      )
      BeautyQSupplementPolicy.classifyPipelineError(error) match {
        case hard: BeautyQSupplementPolicy.Hard =>
          assert(hard.cause eq error)
        case other => fail(s"expected Hard, got $other")
      }
    }

    "classify 400 as hard with exact cause" in {
      val error = BeautyQQdrantCandidatePipelineError.Qdrant(
        QdrantCandidatePipelineError.Service(
          QdrantCandidateServiceError.Transport("query", Gen2HttpTransportError.HttpFailure("GET", "/test", 400, "bad request"))
        )
      )
      BeautyQSupplementPolicy.classifyPipelineError(error) match {
        case hard: BeautyQSupplementPolicy.Hard =>
          assert(hard.cause eq error)
        case other => fail(s"expected Hard, got $other")
      }
    }

    "classify 403 as hard with exact cause" in {
      val error = BeautyQQdrantCandidatePipelineError.Qdrant(
        QdrantCandidatePipelineError.Service(
          QdrantCandidateServiceError.Transport("query", Gen2HttpTransportError.HttpFailure("GET", "/test", 403, "forbidden"))
        )
      )
      BeautyQSupplementPolicy.classifyPipelineError(error) match {
        case hard: BeautyQSupplementPolicy.Hard =>
          assert(hard.cause eq error)
        case other => fail(s"expected Hard, got $other")
      }
    }

    "classify hydration error as hard with exact cause" in {
      val error = BeautyQQdrantCandidatePipelineError.Hydration(
        leaderboard.search.gen2.core.hydration.CandidateHydrationError.SourceSnapshotMismatch("expected", "actual")
      )
      BeautyQSupplementPolicy.classifyPipelineError(error) match {
        case hard: BeautyQSupplementPolicy.Hard =>
          assert(hard.cause eq error)
        case other => fail(s"expected Hard, got $other")
      }
    }
  }

  "BeautyQSupplementPolicy.classifyStartupActivationError" should {
    "classify ES as startup hard" in {
      val error = BeautyQSearchGenerationActivationError.Elasticsearch(
        BeautyQElasticsearchBaselineServiceError.Lifecycle(
          leaderboard.search.gen2.elasticsearch.lifecycle.ElasticsearchGenerationLifecycleError.StaleGeneration(
            leaderboard.search.gen2.elasticsearch.ElasticsearchGenerationReference("stale")
          )
        )
      )
      BeautyQSupplementPolicy.classifyStartupActivationError(error) match {
        case hard: BeautyQSupplementPolicy.StartupHard =>
          assert(hard.cause eq error)
        case other => fail(s"expected StartupHard, got $other")
      }
    }

    "classify Compile as startup hard" in {
      val error = BeautyQSearchGenerationActivationError.Compile(
        QdrantGenerationCompileError.DuplicatePointId("duplicate-id")
      )
      BeautyQSupplementPolicy.classifyStartupActivationError(error) match {
        case hard: BeautyQSupplementPolicy.StartupHard =>
          assert(hard.cause eq error)
        case other => fail(s"expected StartupHard, got $other")
      }
    }

    "classify Qdrant invalid physical name as startup hard" in {
      val error = BeautyQSearchGenerationActivationError.Qdrant(
        QdrantGenerationLifecycleError.InvalidPhysicalName("bad-name")
      )
      BeautyQSupplementPolicy.classifyStartupActivationError(error) match {
        case hard: BeautyQSupplementPolicy.StartupHard =>
          assert(hard.cause eq error)
        case other => fail(s"expected StartupHard, got $other")
      }
    }

    "classify Qdrant malformed as startup hard" in {
      val error = BeautyQSearchGenerationActivationError.Qdrant(
        QdrantGenerationLifecycleError.Malformed("op", "bad")
      )
      BeautyQSupplementPolicy.classifyStartupActivationError(error) match {
        case hard: BeautyQSupplementPolicy.StartupHard =>
          assert(hard.cause eq error)
        case other => fail(s"expected StartupHard, got $other")
      }
    }

    "classify Qdrant collection incompatible as startup hard" in {
      val error = BeautyQSearchGenerationActivationError.Qdrant(
        QdrantGenerationLifecycleError.CollectionIncompatible("target", "reason")
      )
      BeautyQSupplementPolicy.classifyStartupActivationError(error) match {
        case hard: BeautyQSupplementPolicy.StartupHard =>
          assert(hard.cause eq error)
        case other => fail(s"expected StartupHard, got $other")
      }
    }

    "classify Qdrant alias ambiguous as startup hard" in {
      val error = BeautyQSearchGenerationActivationError.Qdrant(
        QdrantGenerationLifecycleError.AliasAmbiguous("test", Vector("a", "b"))
      )
      BeautyQSupplementPolicy.classifyStartupActivationError(error) match {
        case hard: BeautyQSupplementPolicy.StartupHard =>
          assert(hard.cause eq error)
        case other => fail(s"expected StartupHard, got $other")
      }
    }

    "classify Qdrant mutation rejected as startup hard" in {
      val error = BeautyQSearchGenerationActivationError.Qdrant(
        QdrantGenerationLifecycleError.MutationRejected("op", io.circe.Json.obj())
      )
      BeautyQSupplementPolicy.classifyStartupActivationError(error) match {
        case hard: BeautyQSupplementPolicy.StartupHard =>
          assert(hard.cause eq error)
        case other => fail(s"expected StartupHard, got $other")
      }
    }

    "classify Qdrant point count mismatch as startup hard" in {
      val error = BeautyQSearchGenerationActivationError.Qdrant(
        QdrantGenerationLifecycleError.PointCountMismatch(10, 5L)
      )
      BeautyQSupplementPolicy.classifyStartupActivationError(error) match {
        case hard: BeautyQSupplementPolicy.StartupHard =>
          assert(hard.cause eq error)
        case other => fail(s"expected StartupHard, got $other")
      }
    }

    "classify Qdrant transport connection failed as startup degradable with qdrant-transport" in {
      val error = BeautyQSearchGenerationActivationError.Qdrant(
        QdrantGenerationLifecycleError.Transport("query", Gen2HttpTransportError.ConnectionFailed("GET", "/test", "connection refused"))
      )
      BeautyQSupplementPolicy.classifyStartupActivationError(error) match {
        case degradable: BeautyQSupplementPolicy.StartupDegradable =>
          assert(degradable.reason == BeautyQDegradationReason.QdrantTransport)
          assert(degradable.cause eq error)
        case other => fail(s"expected StartupDegradable(qdrant-transport), got $other")
      }
    }

    "classify Qdrant transport request failed as startup degradable with qdrant-transport" in {
      val error = BeautyQSearchGenerationActivationError.Qdrant(
        QdrantGenerationLifecycleError.Transport("query", Gen2HttpTransportError.RequestFailed("GET", "/test", "request failed"))
      )
      BeautyQSupplementPolicy.classifyStartupActivationError(error) match {
        case degradable: BeautyQSupplementPolicy.StartupDegradable =>
          assert(degradable.reason == BeautyQDegradationReason.QdrantTransport)
          assert(degradable.cause eq error)
        case other => fail(s"expected StartupDegradable(qdrant-transport), got $other")
      }
    }

    "classify Qdrant transport 408 as startup degradable with qdrant-backend" in {
      val error = BeautyQSearchGenerationActivationError.Qdrant(
        QdrantGenerationLifecycleError.Transport("query", Gen2HttpTransportError.HttpFailure("GET", "/test", 408, "timeout"))
      )
      BeautyQSupplementPolicy.classifyStartupActivationError(error) match {
        case degradable: BeautyQSupplementPolicy.StartupDegradable =>
          assert(degradable.reason == BeautyQDegradationReason.QdrantBackend)
          assert(degradable.cause eq error)
        case other => fail(s"expected StartupDegradable(qdrant-backend), got $other")
      }
    }

    "classify Qdrant transport 500 as startup degradable with qdrant-backend" in {
      val error = BeautyQSearchGenerationActivationError.Qdrant(
        QdrantGenerationLifecycleError.Transport("query", Gen2HttpTransportError.HttpFailure("GET", "/test", 500, "server error"))
      )
      BeautyQSupplementPolicy.classifyStartupActivationError(error) match {
        case degradable: BeautyQSupplementPolicy.StartupDegradable =>
          assert(degradable.reason == BeautyQDegradationReason.QdrantBackend)
          assert(degradable.cause eq error)
        case other => fail(s"expected StartupDegradable(qdrant-backend), got $other")
      }
    }

    "classify Qdrant transport 400 as startup hard" in {
      val error = BeautyQSearchGenerationActivationError.Qdrant(
        QdrantGenerationLifecycleError.Transport("query", Gen2HttpTransportError.HttpFailure("GET", "/test", 400, "bad request"))
      )
      BeautyQSupplementPolicy.classifyStartupActivationError(error) match {
        case hard: BeautyQSupplementPolicy.StartupHard =>
          assert(hard.cause eq error)
        case other => fail(s"expected StartupHard, got $other")
      }
    }

    "classify Qdrant invalid json response as startup hard" in {
      val error = BeautyQSearchGenerationActivationError.Qdrant(
        QdrantGenerationLifecycleError.Transport("query", Gen2HttpTransportError.InvalidJsonResponse("GET", "/test", 200, "bad json", "parse error"))
      )
      BeautyQSupplementPolicy.classifyStartupActivationError(error) match {
        case hard: BeautyQSupplementPolicy.StartupHard =>
          assert(hard.cause eq error)
        case other => fail(s"expected StartupHard, got $other")
      }
    }

    "classify embedding timeout as startup degradable with embedding-timeout" in {
      val error = BeautyQSearchGenerationActivationError.Embedding(
        BeautyQEmbeddingRequestError.Timeout("timeout details")
      )
      BeautyQSupplementPolicy.classifyStartupActivationError(error) match {
        case degradable: BeautyQSupplementPolicy.StartupDegradable =>
          assert(degradable.reason == BeautyQDegradationReason.EmbeddingTimeout)
          assert(degradable.cause eq error)
        case other => fail(s"expected StartupDegradable(embedding-timeout), got $other")
      }
    }

    "classify embedding unavailable as startup degradable with embedding-unavailable" in {
      val error = BeautyQSearchGenerationActivationError.Embedding(
        BeautyQEmbeddingRequestError.Unavailable("unavailable details")
      )
      BeautyQSupplementPolicy.classifyStartupActivationError(error) match {
        case degradable: BeautyQSupplementPolicy.StartupDegradable =>
          assert(degradable.reason == BeautyQDegradationReason.EmbeddingUnavailable)
          assert(degradable.cause eq error)
        case other => fail(s"expected StartupDegradable(embedding-unavailable), got $other")
      }
    }

    "classify embedding transport as startup degradable with embedding-transport" in {
      val error = BeautyQSearchGenerationActivationError.Embedding(
        BeautyQEmbeddingRequestError.Transport("transport details")
      )
      BeautyQSupplementPolicy.classifyStartupActivationError(error) match {
        case degradable: BeautyQSupplementPolicy.StartupDegradable =>
          assert(degradable.reason == BeautyQDegradationReason.EmbeddingTransport)
          assert(degradable.cause eq error)
        case other => fail(s"expected StartupDegradable(embedding-transport), got $other")
      }
    }

    "classify embedding malformed response as startup hard" in {
      val error = BeautyQSearchGenerationActivationError.Embedding(
        BeautyQEmbeddingRequestError.MalformedResponse("bad json")
      )
      BeautyQSupplementPolicy.classifyStartupActivationError(error) match {
        case hard: BeautyQSupplementPolicy.StartupHard =>
          assert(hard.cause eq error)
        case other => fail(s"expected StartupHard, got $other")
      }
    }

    "classify embedding invalid result as startup hard" in {
      val error = BeautyQSearchGenerationActivationError.Embedding(
        BeautyQEmbeddingRequestError.InvalidResult(QdrantEmbeddingError.DimensionMismatch(1024, 768))
      )
      BeautyQSupplementPolicy.classifyStartupActivationError(error) match {
        case hard: BeautyQSupplementPolicy.StartupHard =>
          assert(hard.cause eq error)
        case other => fail(s"expected StartupHard, got $other")
      }
    }
  }
}
