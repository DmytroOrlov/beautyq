package leaderboard.search

import leaderboard.model.QueryFailure
import leaderboard.search.dsl.{EmbeddingSpec, VectorDistance, VectorSearchSpec}
import leaderboard.search.qdrant.{
  QdrantCollectionReadinessConfig,
  QdrantCollectionReadinessInput,
  QdrantNonProductionExperimentActivation,
  QdrantNonProductionExperimentConfig,
  QdrantNonProductionExperimentIndexingTrigger,
  QdrantNonProductionExperimentMetadataSource,
}
import org.scalatest.wordspec.AnyWordSpec

final class QdrantNonProductionExperimentActivationSpec extends AnyWordSpec {
  "QdrantNonProductionExperimentActivation" should {
    "default to disabled" in {
      assert(QdrantNonProductionExperimentActivation.default == QdrantNonProductionExperimentActivation.Disabled)
    }

    "preserve experiment id for a valid enabled config" in {
      val config = createConfig("local-qdrant-experiment")
      val activation = QdrantNonProductionExperimentActivation.Enabled(config)

      assert(config.experimentId == "local-qdrant-experiment")
      assert(activation.config.experimentId == "local-qdrant-experiment")
    }

    "fail clearly for an empty experiment id" in {
      val failure = QdrantNonProductionExperimentConfig.create(
        experimentId = "",
        readinessConfig = readinessConfig,
        indexingTrigger = QdrantNonProductionExperimentIndexingTrigger.ManualTask,
        metadataSource = QdrantNonProductionExperimentMetadataSource.ExplicitMetadataOnly,
      )

      failure match {
        case Left(QueryFailure.DomainFailure(message)) =>
          assert(message.contains("experimentId"))
          assert(message.contains("non-empty"))
        case other =>
          fail(s"Expected empty experimentId domain failure, got $other")
      }
    }

    "preserve readiness config exactly" in {
      val config = createConfig("readiness-preserved")

      assert(config.readinessConfig eq readinessConfig)
      assert(config.readinessConfig == readinessConfig)
    }

    "allow manual indexing trigger" in {
      val config = createConfig(
        experimentId = "manual-indexing",
        indexingTrigger = QdrantNonProductionExperimentIndexingTrigger.ManualTask,
      )

      assert(config.indexingTrigger == QdrantNonProductionExperimentIndexingTrigger.ManualTask)
    }

    "allow test-setup indexing trigger" in {
      val config = createConfig(
        experimentId = "test-setup-indexing",
        indexingTrigger = QdrantNonProductionExperimentIndexingTrigger.TestSetup,
      )

      assert(config.indexingTrigger == QdrantNonProductionExperimentIndexingTrigger.TestSetup)
    }

    "model explicit metadata only as the sole metadata source" in {
      assert(QdrantNonProductionExperimentMetadataSource.all == List(
        QdrantNonProductionExperimentMetadataSource.ExplicitMetadataOnly,
      ))
      assert(createConfig("explicit-metadata").metadataSource == QdrantNonProductionExperimentMetadataSource.ExplicitMetadataOnly)
    }

    "not model startup, production, or automatic indexing triggers" in {
      val triggerNames = QdrantNonProductionExperimentIndexingTrigger.all.map(_.productPrefix)

      assert(QdrantNonProductionExperimentIndexingTrigger.all == List(
        QdrantNonProductionExperimentIndexingTrigger.ManualTask,
        QdrantNonProductionExperimentIndexingTrigger.TestSetup,
      ))
      assert(!triggerNames.exists(name => name.contains("Startup") || name.contains("Production") || name.contains("Auto")))
    }

    "not provide production or default enabled activation" in {
      assert(QdrantNonProductionExperimentActivation.default == QdrantNonProductionExperimentActivation.Disabled)
      assert(QdrantNonProductionExperimentActivation.default != QdrantNonProductionExperimentActivation.Enabled(createConfig("not-default")))
      assert(QdrantNonProductionExperimentActivation.default.productPrefix != "Production")
    }

    "construct activation without Qdrant, llama, or file IO" in {
      val config = createConfig("pure-construction")
      val activation = QdrantNonProductionExperimentActivation.Enabled(config)

      assert(activation.config.readinessConfig.collectionName == readinessConfig.collectionName)
      assert(QdrantNonProductionExperimentIndexingTrigger.all.map(_.productPrefix) == List("ManualTask", "TestSetup"))
      assert(QdrantNonProductionExperimentMetadataSource.all.map(_.productPrefix) == List("ExplicitMetadataOnly"))
    }

    "Disabled is the only default activation value" in {
      assert(QdrantNonProductionExperimentActivation.default == QdrantNonProductionExperimentActivation.Disabled)
      assert(QdrantNonProductionExperimentActivation.default.productPrefix == "Disabled")
    }

    "Enabled activation wraps a config but does not build Qdrant dependencies" in {
      val config = QdrantNonProductionExperimentConfig.create(
        experimentId = "no-dep-build",
        readinessConfig = readinessConfig,
        indexingTrigger = QdrantNonProductionExperimentIndexingTrigger.ManualTask,
        metadataSource = QdrantNonProductionExperimentMetadataSource.ExplicitMetadataOnly,
      ).fold(failure => fail(s"Expected valid config, got $failure"), identity)

      val activation = QdrantNonProductionExperimentActivation.Enabled(config)

      assert(activation.config == config)
      assert(activation.config.experimentId == "no-dep-build")
    }

    "Enabled activation preserves all config fields" in {
      val config = createConfig("all-fields", QdrantNonProductionExperimentIndexingTrigger.TestSetup)
      val activation = QdrantNonProductionExperimentActivation.Enabled(config)

      assert(activation.config.experimentId == "all-fields")
      assert(activation.config.indexingTrigger == QdrantNonProductionExperimentIndexingTrigger.TestSetup)
      assert(activation.config.metadataSource == QdrantNonProductionExperimentMetadataSource.ExplicitMetadataOnly)
      assert(activation.config.readinessConfig == readinessConfig)
    }
  }

  private def createConfig(
    experimentId: String,
    indexingTrigger: QdrantNonProductionExperimentIndexingTrigger = QdrantNonProductionExperimentIndexingTrigger.ManualTask,
  ): QdrantNonProductionExperimentConfig =
    QdrantNonProductionExperimentConfig.create(
      experimentId = experimentId,
      readinessConfig = readinessConfig,
      indexingTrigger = indexingTrigger,
      metadataSource = QdrantNonProductionExperimentMetadataSource.ExplicitMetadataOnly,
    ).fold(failure => fail(s"Expected valid config, got $failure"), identity)

  private val embeddingSpec: EmbeddingSpec[Any] =
    EmbeddingSpec[Any](
      vectorName = "embedding-spec-vector-name-is-not-identity-source",
      modelName = "llama-cpp-embedding",
      dimension = 1024,
      distance = VectorDistance.Cosine,
      sourceTextFields = Nil,
    )

  private val vectorSearchSpec: VectorSearchSpec =
    VectorSearchSpec(
      collectionName = "placeholder_collection",
      vectorName = "variant-embedding",
      topK = 20,
      scoreThreshold = Some(0.2),
    )

  private val readinessConfig: QdrantCollectionReadinessConfig =
    QdrantCollectionReadinessConfig.derive(QdrantCollectionReadinessInput(
      domainName = "beauty_variant",
      searchSpecVersion = "v1",
      purpose = "local_activation_test",
      embeddingSpec = embeddingSpec,
      vectorSearchSpec = vectorSearchSpec,
    ))
}
