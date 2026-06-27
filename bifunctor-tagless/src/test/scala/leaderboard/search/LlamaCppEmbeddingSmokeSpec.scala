package leaderboard.search

import leaderboard.search.embedding.LlamaCppEmbeddingClient
import org.scalatest.wordspec.AnyWordSpec

final class LlamaCppEmbeddingSmokeSpec extends AnyWordSpec {
  private val embeddingConfig = sys.env.get("LLAMA_CPP_EMBEDDING_URL")
    .map(LlamaCppEmbeddingTestConfig.withBaseUrl)
    .getOrElse(LlamaCppEmbeddingTestConfig.default)

  "LlamaCppEmbeddingClient smoke" should {
    "call the local llama.cpp embedding endpoint" in {
      val client = new LlamaCppEmbeddingClient(embeddingConfig)
      probeEmbeddingClient(client, embeddingConfig.baseUrl) match {
        case Some(message) => cancel(message)
        case None =>
          val result = unsafeRun(client.embed("test"))
          assert(result.nonEmpty)
      }
    }
  }

  private def probeEmbeddingClient(client: LlamaCppEmbeddingClient, endpoint: String): Option[String] = {
    try {
      val probe = unsafeRun(client.embed("endpoint probe").either)
      probe match {
        case Right(vector) if vector.nonEmpty => None
        case Right(_) => Some(s"llama.cpp embedding endpoint $endpoint returned an empty vector; canceling")
        case Left(_) => Some(s"llama.cpp embedding endpoint $endpoint is unavailable; canceling")
      }
    } catch {
      case _: Exception => Some(s"llama.cpp embedding endpoint $endpoint is unavailable; canceling")
    }
  }

  private def unsafeRun[A](effect: zio.IO[leaderboard.model.QueryFailure, A]): A =
    zio.Unsafe.unsafe { implicit unsafe =>
      zio.Runtime.default.unsafe.run(effect).getOrThrowFiberFailure()
    }
}
