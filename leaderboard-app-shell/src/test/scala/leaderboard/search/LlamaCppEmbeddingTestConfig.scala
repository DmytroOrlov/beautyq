package leaderboard.search

import com.typesafe.config.ConfigFactory
import leaderboard.search.embedding.{LlamaCppEmbeddingClient, LlamaCppEmbeddingClientConfig}

object LlamaCppEmbeddingTestConfig {
  val default: LlamaCppEmbeddingClientConfig = {
    val config = ConfigFactory.load("common-reference.conf").resolve().getConfig("llama-cpp-embedding")
    LlamaCppEmbeddingClientConfig(
      baseUrl = config.getString("baseUrl"),
      endpointPath = config.getString("endpointPath"),
    )
  }

  def withBaseUrl(baseUrl: String): LlamaCppEmbeddingClientConfig =
    default.copy(baseUrl = baseUrl)

  def client(config: LlamaCppEmbeddingClientConfig = default): LlamaCppEmbeddingClient =
    new LlamaCppEmbeddingClient(config)
}
