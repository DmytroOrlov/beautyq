package leaderboard.search

import leaderboard.search.embedding.{LlamaCppEmbeddingClient, LlamaCppEmbeddingClientConfig}
import org.scalatest.wordspec.AnyWordSpec

final class LlamaCppEmbeddingSmokeSpec extends AnyWordSpec {
  private lazy val embeddingUrl = sys.env.get("LLAMA_CPP_EMBEDDING_URL")

  "LlamaCppEmbeddingClient smoke" should {
    "call the manually running server when configured" in {
      embeddingUrl match {
        case None =>
          cancel("Set LLAMA_CPP_EMBEDDING_URL to run the llama.cpp smoke test")
        case Some(url) =>
          val client = new LlamaCppEmbeddingClient(LlamaCppEmbeddingClientConfig(baseUrl = url))
          val result = unsafeRun(client.embed("test"))
          assert(result.nonEmpty)
      }
    }
  }

  private def unsafeRun[A](effect: zio.IO[leaderboard.model.QueryFailure, A]): A =
    zio.Unsafe.unsafe { implicit unsafe =>
      zio.Runtime.default.unsafe.run(effect).getOrThrowFiberFailure()
    }
}
