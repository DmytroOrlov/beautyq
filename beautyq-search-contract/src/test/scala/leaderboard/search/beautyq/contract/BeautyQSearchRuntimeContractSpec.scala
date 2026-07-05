package leaderboard.search.beautyq.contract

import leaderboard.search.contract.{SearchBackendId, SearchBackendKind}
import leaderboard.search.dsl.VectorDistance
import org.scalatest.wordspec.AnyWordSpec

final class BeautyQSearchRuntimeContractSpec extends AnyWordSpec {

  "BeautyQSearchRuntimeContract" should {
    "declare the exact Elasticsearch and Qdrant backend id constants" in {
      assert(BeautyQSearchRuntimeContract.ElasticsearchBackendId == SearchBackendId("elasticsearch"))
      assert(BeautyQSearchRuntimeContract.QdrantBackendId == SearchBackendId("qdrant"))
    }

    "use the backend-id constants in the elasticsearch and qdrant declarations" in {
      assert(BeautyQSearchRuntimeContract.elasticsearch.backendId == BeautyQSearchRuntimeContract.ElasticsearchBackendId)
      assert(BeautyQSearchRuntimeContract.qdrant.backendId == BeautyQSearchRuntimeContract.QdrantBackendId)
    }

    "declare Elasticsearch then Qdrant, in that order, in the runtime section" in {
      assert(BeautyQSearchRuntimeContract.section.declarations == List(BeautyQSearchRuntimeContract.elasticsearch, BeautyQSearchRuntimeContract.qdrant))
      assert(BeautyQSearchRuntimeContract.section.declarations.map(_.backendId) == List(SearchBackendId("elasticsearch"), SearchBackendId("qdrant")))
      assert(BeautyQSearchRuntimeContract.section.declarations.map(_.kind) == List(SearchBackendKind.Elasticsearch, SearchBackendKind.Qdrant))
    }

    "declare the exact canonical managed-local Qdrant runtime defaults" in {
      assert(BeautyQSearchRuntimeContract.ManagedLocalQdrantEmbeddingModelName == "local-llama-cpp-embedding")
      assert(BeautyQSearchRuntimeContract.ManagedLocalQdrantExpectedVectorDimension == 1024)
      assert(BeautyQSearchRuntimeContract.ManagedLocalQdrantVectorDistance == VectorDistance.Cosine)
    }
  }
}
