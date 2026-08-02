package leaderboard.search.gen2.elasticsearch.lifecycle {

  import java.time.Instant

  import _root_.leaderboard.search.gen2.elasticsearch.*

  /** Test-only access to the lifecycle-owned value returned by the existing
    * Elasticsearch activation owner.  The status fixture never constructs a
    * serving status or its reason directly. */
  private[leaderboard] object BeautyQStartupServingStatusElasticsearchFixture {
    def resolved(
      compiled: CompiledElasticsearchGeneration[?, ?],
    ): LifecycleResolvedElasticsearchGeneration = {
      val persisted = ElasticsearchPersistedGenerationIdentity.fromTrusted(compiled.identity)
      val metadata = ElasticsearchGenerationMetadata(
        ElasticsearchGenerationMetadataSchemaVersion.Current,
        ElasticsearchGenerationNaming.generationId(persisted),
        Instant.parse("2024-01-01T00:00:00Z"),
        compiled.documents.size.toLong,
        persisted,
      )
      new LifecycleResolvedElasticsearchGeneration(
        ElasticsearchGenerationReference("beautyq-status-test-generation"),
        ElasticsearchSearchTarget("beautyq-status-test-target"),
        metadata,
      )
    }
  }
}

package leaderboard.search.gen2.qdrant {

  /** Test-only access to the qdrant lifecycle result shape returned by the
    * existing typed generation compiler. */
  private[leaderboard] object BeautyQStartupServingStatusQdrantFixture {
    def active(
      alias: QdrantResourceName,
      compiled: QdrantCompiledGeneration,
    ): ActiveQdrantGeneration =
      new ActiveQdrantGeneration(
        alias,
        QdrantResourceName.from(compiled.physicalCollectionName).fold(
          error => throw new AssertionError(s"expected a valid physical collection name: $error"),
          identity,
        ),
        compiled.metadata,
      )
  }
}

package leaderboard.search.beautyq.gen2.wiring {

  import _root_.leaderboard.search.beautyq.gen2.materialization.MaterializedBeautyQVariantDocuments
  import _root_.leaderboard.search.beautyq.gen2.wiring.BeautyQSearchGenerationActivationError.Embedding
  import _root_.leaderboard.search.beautyq.gen2.wiring.BeautyQEmbeddingRequestError.Timeout
  import _root_.leaderboard.search.beautyq.gen2.wiring.testkit.BeautyQOrchestrationTestKit
  import _root_.leaderboard.search.gen2.elasticsearch.lifecycle.*
  import _root_.leaderboard.search.gen2.qdrant.*

  /**
    * The one Test-scope owner for StartupServingStatus fixtures.  Values are
    * derived from the existing materialized and generation compiler fixtures,
    * then routed through the retained production healthy/degraded/limited
    * owners.  No status or reason constructor is reachable here.
    */
  object BeautyQStartupServingStatusTestFixtures {
    val materialized: MaterializedBeautyQVariantDocuments =
      BeautyQOrchestrationTestKit.materialized

    private val compiledElasticsearch: CompiledBeautyQElasticsearchGeneration =
      BeautyQElasticsearchGeneration.compile(materialized).fold(
        error => throw new AssertionError(s"expected a typed Elasticsearch generation: $error"),
        identity,
      )

    private val elasticsearchGeneration: LifecycleResolvedElasticsearchGeneration =
      _root_.leaderboard.search.gen2.elasticsearch.lifecycle.BeautyQStartupServingStatusElasticsearchFixture.resolved(compiledElasticsearch)

    private val compiledQdrant: QdrantCompiledGeneration =
      QdrantGenerationCompiler.prepare(BeautyQQdrantPolicy.policy, materialized).flatMap { prepared =>
        val embeddings = prepared.points.map { point =>
          QdrantEmbeddingResult.from(
            point.embeddingInput,
            Vector.fill(point.embeddingInput.modelValue.dimension)(0.1),
          ).fold(
            error => throw new AssertionError(s"expected a typed embedding fixture: $error"),
            identity,
          )
        }
        QdrantGenerationCompiler.complete(
          prepared,
          embeddings,
          BeautyQSearchGen2ResourceNames.QdrantPhysicalCollectionPrefix,
        )
      }.fold(
        error => throw new AssertionError(s"expected a typed Qdrant generation: $error"),
        identity,
      )

    private val qdrantAlias: QdrantResourceName =
      QdrantResourceName.from(BeautyQSearchGen2ResourceNames.QdrantCollectionAlias).fold(
        error => throw new AssertionError(s"expected a typed Qdrant alias fixture: $error"),
        identity,
      )

    private val qdrantGeneration: ActiveQdrantGeneration =
      _root_.leaderboard.search.gen2.qdrant.BeautyQStartupServingStatusQdrantFixture.active(qdrantAlias, compiledQdrant)

    private val degradableCause: BeautyQSearchGenerationActivationError =
      Embedding(Timeout("test embedding timeout"))

    val fullSearch: StartupServingStatus =
      StartupServingStatus.healthy(
        SupplementStartupPolicy.Required,
        materialized,
        elasticsearchGeneration,
        qdrantGeneration,
      )

    val degradedBaseline: StartupServingStatus =
      StartupServingStatus.degraded(
        SupplementStartupPolicy.Preferred,
        materialized,
        elasticsearchGeneration,
        "qdrant_supplement_unavailable",
        "Qdrant supplement was unavailable at startup; the complete Elasticsearch baseline was returned; restart is required",
        "Embedding error: Timeout",
        degradableCause,
      )

    val disabledBaseline: StartupServingStatus =
      StartupServingStatus.limited(
        SupplementStartupPolicy.Disabled,
        materialized,
        elasticsearchGeneration,
        "qdrant_supplement_operator_disabled",
        "Qdrant supplement was disabled by operator policy; the complete Elasticsearch baseline was returned; restart is required",
        "supplement disabled by operator startup policy",
      )
  }
}
