package leaderboard.search.beautyq.gen2.wiring

/** Reserved BeautyQ Gen2 Elasticsearch/Qdrant resource namespaces. Names only - no lifecycle,
  * client, configuration, or runtime binding.
  */
object BeautyQSearchGen2ResourceNames {
  private val BaseName = "beautyq_variant_gen2"

  val ElasticsearchAlias: String = BaseName
  val ElasticsearchPhysicalIndexPrefix: String = s"${ElasticsearchAlias}_"
  val QdrantCollectionAlias: String = BaseName
  val QdrantPhysicalCollectionPrefix: String = s"${QdrantCollectionAlias}_"
}
