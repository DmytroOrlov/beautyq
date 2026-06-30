package leaderboard.search.elasticsearch

import io.circe.Json
import leaderboard.search.document.SearchDocumentJson
import leaderboard.search.dsl.SearchDocumentSpec

object ElasticsearchIngestionInterpreter {
  def bulkPayload[A](
    documentSpec: SearchDocumentSpec[A],
    documents: List[A],
  ): String =
    documents.map { document =>
      val action = Json.obj(
        "index" -> Json.obj(
          "_index" -> Json.fromString(documentSpec.indexName),
          "_id" -> Json.fromString(documentSpec.id(document)),
        )
      ).noSpaces
      val source = sourceJson(documentSpec, document).noSpaces
      s"$action\n$source"
    }.mkString("\n", "\n", "\n")

  def sourceJson[A](
    documentSpec: SearchDocumentSpec[A],
    document: A,
  ): Json =
    SearchDocumentJson.sourceJson(documentSpec, document)
}
