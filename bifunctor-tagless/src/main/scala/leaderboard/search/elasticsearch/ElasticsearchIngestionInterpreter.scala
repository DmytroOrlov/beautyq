package leaderboard.search.elasticsearch

import io.circe.Json
import leaderboard.search.document.{SearchDocumentJson, VariantSearchDocument}
import leaderboard.search.dsl.BeautySearchSpec

object ElasticsearchIngestionInterpreter {
  def bulkPayload(
    spec: BeautySearchSpec,
    documents: List[VariantSearchDocument],
  ): String =
    documents.map { document =>
      val action = Json.obj(
        "index" -> Json.obj(
          "_index" -> Json.fromString(spec.variantDocument.indexName),
          "_id" -> Json.fromString(spec.variantDocument.id(document)),
        )
      ).noSpaces
      val source = sourceJson(spec, document).noSpaces
      s"$action\n$source"
    }.mkString("\n", "\n", "\n")

  def sourceJson(
    spec: BeautySearchSpec,
    document: VariantSearchDocument,
  ): Json =
    SearchDocumentJson.sourceJson(spec.variantDocument, document)
}
