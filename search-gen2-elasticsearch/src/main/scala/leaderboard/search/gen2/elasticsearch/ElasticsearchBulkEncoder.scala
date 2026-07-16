package leaderboard.search.gen2.elasticsearch

import io.circe.Json

import java.nio.charset.StandardCharsets

final class ElasticsearchBulkBatchingPolicy private (val maxDocuments: Int, val maxUtf8Bytes: Long)

object ElasticsearchBulkBatchingPolicy {
  def create(maxDocuments: Int, maxUtf8Bytes: Long): Either[ElasticsearchBulkEncodeError.InvalidBatchingPolicy, ElasticsearchBulkBatchingPolicy] =
    if (maxDocuments <= 0 || maxUtf8Bytes <= 0L) Left(ElasticsearchBulkEncodeError.InvalidBatchingPolicy(maxDocuments, maxUtf8Bytes))
    else Right(new ElasticsearchBulkBatchingPolicy(maxDocuments, maxUtf8Bytes))
}

final case class ElasticsearchBulkBatch(
  startDocumentIndex: Int,
  documents: Vector[ElasticsearchIndexedDocument],
  body: String,
  utf8Bytes: Long,
)

sealed trait ElasticsearchBulkEncodeError
object ElasticsearchBulkEncodeError {
  final case class InvalidBatchingPolicy(maxDocuments: Int, maxUtf8Bytes: Long) extends ElasticsearchBulkEncodeError
  final case class OversizedDocument(documentIndex: Int, documentId: String, utf8Bytes: Long, maximum: Long) extends ElasticsearchBulkEncodeError
}

object ElasticsearchBulkEncoder {
  def encode(
    documents: Vector[ElasticsearchIndexedDocument],
    policy: ElasticsearchBulkBatchingPolicy,
  ): Either[ElasticsearchBulkEncodeError, Vector[ElasticsearchBulkBatch]] = {
    final case class State(completed: Vector[ElasticsearchBulkBatch], start: Int, current: Vector[ElasticsearchIndexedDocument], lines: Vector[String], bytes: Long)

    def finish(state: State): State =
      if (state.current.isEmpty) state
      else state.copy(
        completed = state.completed :+ ElasticsearchBulkBatch(state.start, state.current, state.lines.mkString, state.bytes),
        start = state.start + state.current.length,
        current = Vector.empty,
        lines = Vector.empty,
        bytes = 0L,
      )

    val initial = State(Vector.empty, 0, Vector.empty, Vector.empty, 0L)
    documents.zipWithIndex.foldLeft[Either[ElasticsearchBulkEncodeError, State]](Right(initial)) {
      case (acc, (document, index)) => acc.flatMap { state0 =>
        val action = Json.obj("index" -> Json.obj("_id" -> Json.fromString(document.id))).noSpaces + "\n"
        val source = document.source.noSpaces + "\n"
        val part = action + source
        val size = part.getBytes(StandardCharsets.UTF_8).length.toLong
        if (size > policy.maxUtf8Bytes) Left(ElasticsearchBulkEncodeError.OversizedDocument(index, document.id, size, policy.maxUtf8Bytes))
        else {
          val full = state0.current.length >= policy.maxDocuments || state0.bytes + size > policy.maxUtf8Bytes
          val state = if (full) finish(state0) else state0
          Right(state.copy(current = state.current :+ document, lines = state.lines :+ part, bytes = state.bytes + size))
        }
      }
    }.map(state => finish(state).completed)
  }
}
