package leaderboard.search.beautyq.gen2.wiring

/** BeautyQ-owned embedding-port failure algebra for request-time adapter failures only.
  * Model mismatch, dimension mismatch, non-finite vectors, blank text and input mismatch
  * belong to QdrantEmbeddingError and remain integrity/compiler failures. */
sealed trait BeautyQEmbeddingRequestError {
  def details: String
}

object BeautyQEmbeddingRequestError {
  final case class Timeout(details: String) extends BeautyQEmbeddingRequestError
  final case class Unavailable(details: String) extends BeautyQEmbeddingRequestError
  final case class Transport(details: String) extends BeautyQEmbeddingRequestError
  final case class MalformedResponse(details: String) extends BeautyQEmbeddingRequestError
  final case class InvalidResult(error: leaderboard.search.gen2.qdrant.QdrantEmbeddingError) extends BeautyQEmbeddingRequestError {
    def details: String = error.toString
  }
}
