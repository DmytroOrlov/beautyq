package leaderboard.search

import com.typesafe.config.ConfigFactory
import distage.{Injector, ModuleDef, Scene}
import izumi.distage.config.model.AppConfig
import izumi.distage.model.definition.{Activation, LocatorPrivacy}
import izumi.distage.model.plan.Roots
import leaderboard.plugins.{BeautySearchLocalQdrantSupplementLauncherModule, LeaderboardPlugin}
import leaderboard.search.embedding.{EmbeddingClient, LlamaCppEmbeddingClientConfig}
import org.scalatest.wordspec.AnyWordSpec

import scala.io.Source
import scala.util.Using

final class LlamaCppEmbeddingClientConfigSpec extends AnyWordSpec {
  "llama-cpp-embedding config" should {
    "resolve the common-reference defaults" in {
      val config = loadConfig()

      assert(config.baseUrl == "http://localhost:8081")
      assert(config.endpointPath == "/v1/embeddings")
    }

    "require explicit LlamaCppEmbeddingClient config at source level" in {
      val noArgClientSource =
        "new leaderboard.search.embedding.LlamaCppEmbedding" + "Client()"
      val noArgConfigSource =
        "leaderboard.search.embedding.LlamaCppEmbeddingClient" + "Config()"

      assertDoesNotCompile(noArgClientSource)
      assertDoesNotCompile(noArgConfigSource)
      assertCompiles(
        """new leaderboard.search.embedding.LlamaCppEmbeddingClient(
          |  leaderboard.search.embedding.LlamaCppEmbeddingClientConfig(
          |    "http://localhost:8081",
          |    "/v1/embeddings",
          |  )
          |)""".stripMargin
      )
    }

    "resolve managed local EmbeddingClient and config through Distage" in {
      val module = new ModuleDef {
        make[AppConfig].fromValue(AppConfig.provided(ConfigFactory.load("common-reference.conf").resolve()))
        include(LeaderboardPlugin.modules.configs)
        include(BeautySearchLocalQdrantSupplementLauncherModule.managedLocalDefault)
        make[Probe].from {
          (config: LlamaCppEmbeddingClientConfig, client: EmbeddingClient) =>
            Probe(config, client.getClass.getName)
        }
      }

      val locator = Injector().produce(
        bindings = module,
        roots = Roots.target[Probe],
        activation = Activation(Scene -> Scene.Managed),
        locatorPrivacy = LocatorPrivacy.PublicByDefault,
      ).unsafeGet()

      val probe = locator.get[Probe]
      assert(probe.config == loadConfig())
      assert(probe.embeddingClientClassName.endsWith("LlamaCppEmbeddingClient"))
    }

    "represent the optional M18_QDRANT_EMBEDDING_ENDPOINT override in HOCON" in {
      val source = Using.resource(Source.fromResource("common-reference.conf"))(_.mkString)

      assert(source.contains("baseUrl = ${?M18_QDRANT_EMBEDDING_ENDPOINT}"))
    }
  }

  private def loadConfig(): LlamaCppEmbeddingClientConfig = {
    val config = ConfigFactory.load("common-reference.conf").resolve().getConfig("llama-cpp-embedding")
    LlamaCppEmbeddingClientConfig(
      baseUrl = config.getString("baseUrl"),
      endpointPath = config.getString("endpointPath"),
    )
  }

  private final case class Probe(
    config: LlamaCppEmbeddingClientConfig,
    embeddingClientClassName: String,
  )
}
