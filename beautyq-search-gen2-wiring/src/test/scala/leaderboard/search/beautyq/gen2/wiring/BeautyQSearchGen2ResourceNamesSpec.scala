package leaderboard.search.beautyq.gen2.wiring

import org.scalatest.wordspec.AnyWordSpec

final class BeautyQSearchGen2ResourceNamesSpec extends AnyWordSpec {

  private val gen1ElasticsearchIndex = "beautyq_variant_v1"
  private val gen1QdrantCollection   = "beauty_variant_v1_local_llama_cpp_embedding_variant_embedding_1024_cosine"

  "BeautyQSearchGen2ResourceNames" should {
    "expose the exact accepted Gen2 resource names" in {
      assert(BeautyQSearchGen2ResourceNames.ElasticsearchAlias == "beautyq_variant_gen2")
      assert(BeautyQSearchGen2ResourceNames.ElasticsearchPhysicalIndexPrefix == "beautyq_variant_gen2_")
      assert(BeautyQSearchGen2ResourceNames.QdrantCollectionAlias == "beautyq_variant_gen2")
      assert(BeautyQSearchGen2ResourceNames.QdrantPhysicalCollectionPrefix == "beautyq_variant_gen2_")
    }

    "keep both Elasticsearch names distinct from the Gen1 index" in {
      assert(BeautyQSearchGen2ResourceNames.ElasticsearchAlias != gen1ElasticsearchIndex)
      assert(BeautyQSearchGen2ResourceNames.ElasticsearchPhysicalIndexPrefix != gen1ElasticsearchIndex)
    }

    "keep both Qdrant names distinct from the current V1 local managed collection" in {
      assert(BeautyQSearchGen2ResourceNames.QdrantCollectionAlias != gen1QdrantCollection)
      assert(BeautyQSearchGen2ResourceNames.QdrantPhysicalCollectionPrefix != gen1QdrantCollection)
    }

    "visibly contain gen2 in every reserved value" in {
      val values = List(
        BeautyQSearchGen2ResourceNames.ElasticsearchAlias,
        BeautyQSearchGen2ResourceNames.ElasticsearchPhysicalIndexPrefix,
        BeautyQSearchGen2ResourceNames.QdrantCollectionAlias,
        BeautyQSearchGen2ResourceNames.QdrantPhysicalCollectionPrefix,
      )

      val missingGen2 = values.filterNot(_.contains("gen2"))
      assert(missingGen2.isEmpty, s"expected every value to contain 'gen2', missing in: ${missingGen2.mkString(", ")}")
    }

    "start each physical prefix with its own stable alias" in {
      assert(BeautyQSearchGen2ResourceNames.ElasticsearchPhysicalIndexPrefix.startsWith(BeautyQSearchGen2ResourceNames.ElasticsearchAlias))
      assert(BeautyQSearchGen2ResourceNames.QdrantPhysicalCollectionPrefix.startsWith(BeautyQSearchGen2ResourceNames.QdrantCollectionAlias))
    }
  }
}
