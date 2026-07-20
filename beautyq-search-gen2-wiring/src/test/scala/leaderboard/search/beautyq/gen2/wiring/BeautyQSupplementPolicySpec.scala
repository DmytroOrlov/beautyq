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

  "BeautyQSupplementPolicy.requiredDependencies" should {
    "declare ElasticsearchBaseline before DocumentLookup" in {
      assert(BeautyQSupplementPolicy.requiredDependencies == Vector(
        BeautyQSearchDependency.ElasticsearchBaseline,
        BeautyQSearchDependency.DocumentLookup,
      ))
    }

    "declare exactly two required dependencies" in {
      assert(BeautyQSupplementPolicy.requiredDependencies.length == 2)
    }
  }

  "BeautyQSupplementPolicy.supplementDependency" should {
    "be QdrantSupplement" in {
      assert(BeautyQSupplementPolicy.supplementDependency == BeautyQSearchDependency.QdrantSupplement)
    }
  }

  "BeautyQSearchDependency stable IDs" should {
    "be exact" in {
      assert(BeautyQSearchDependency.ElasticsearchBaseline.stableId == "elasticsearch-baseline")
      assert(BeautyQSearchDependency.DocumentLookup.stableId == "document-lookup")
      assert(BeautyQSearchDependency.QdrantSupplement.stableId == "qdrant-supplement")
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
}
