package leaderboard.search.qdrant

import io.circe.Json
import leaderboard.model.QueryFailure
import leaderboard.search.beautyq.contract.{BeautyQSearchRuntimeContract, BeautyQSearchSourceTextFieldsContract}
import leaderboard.search.document.{BeautyQVariantSearchDocumentContract, InMemoryVariantSearchDocumentSnapshotProvider, VariantSearchDocument, VariantSearchDocumentSnapshotProvider}
import leaderboard.search.dsl.{BeautySearchSpecV1, EmbeddingSpec, SearchField, VectorDistance, VectorSearchSpec}
import leaderboard.search.embedding.{EmbeddingClient, LlamaCppEmbeddingClient, LlamaCppEmbeddingClientConfig}
import leaderboard.search.eval.BeautySearchEvalQuery
import leaderboard.search.semantic.SemanticCandidateHit
import leaderboard.search.{ParsedSearchIntent, UserSearchInput}
import zio.{IO, ZIO}

trait QdrantEmbeddingBenchmarkEmbeddingClientFactory {
  def clientFor(candidate: QdrantEmbeddingBenchmarkCandidate): IO[QueryFailure, EmbeddingClient]
}

object QdrantEmbeddingBenchmarkEmbeddingClientFactory {
  final class LlamaCppEndpoint(configTemplate: LlamaCppEmbeddingClientConfig) extends QdrantEmbeddingBenchmarkEmbeddingClientFactory {
    override def clientFor(candidate: QdrantEmbeddingBenchmarkCandidate): IO[QueryFailure, EmbeddingClient] =
      if (candidate.endpointLabel.trim.isEmpty) {
        ZIO.fail(QueryFailure.operation("qdrant-embedding-benchmark-executor", s"Candidate ${candidate.candidateId} has an empty endpoint label"))
      } else {
        ZIO.succeed(new LlamaCppEmbeddingClient(configTemplate.copy(baseUrl = candidate.endpointLabel)))
      }
  }
}

trait QdrantEmbeddingBenchmarkCollectionClient {
  def createCollection(path: String, json: Json): IO[QueryFailure, Json]

  def deleteCollection(path: String): IO[QueryFailure, Unit]
}

final class QdrantEmbeddingBenchmarkQdrantCollectionClient(qdrantClient: QdrantClient) extends QdrantEmbeddingBenchmarkCollectionClient {
  override def createCollection(path: String, json: Json): IO[QueryFailure, Json] =
    qdrantClient.createCollection(path, json)

  override def deleteCollection(path: String): IO[QueryFailure, Unit] =
    qdrantClient.deleteCollection(path)
}

trait QdrantEmbeddingBenchmarkCompositionFactory {
  def build(
    readinessConfig: QdrantCollectionReadinessConfig,
    embeddingClient: EmbeddingClient,
    snapshotProvider: VariantSearchDocumentSnapshotProvider[IO],
    embeddingSpec: EmbeddingSpec[VariantSearchDocument],
  ): IO[QueryFailure, QdrantNonProductionExperimentComposition]
}

final class QdrantEmbeddingBenchmarkDefaultCompositionFactory(qdrantClient: QdrantClient) extends QdrantEmbeddingBenchmarkCompositionFactory {
  override def build(
    readinessConfig: QdrantCollectionReadinessConfig,
    embeddingClient: EmbeddingClient,
    snapshotProvider: VariantSearchDocumentSnapshotProvider[IO],
    embeddingSpec: EmbeddingSpec[VariantSearchDocument],
  ): IO[QueryFailure, QdrantNonProductionExperimentComposition] = {
    val compatibilityGuard = new QdrantCollectionCompatibilityGuard(
      new QdrantCollectionCompatibilityChecker(new QdrantClientCollectionInfoAdapter(qdrantClient))
    )
    val documentUpsert = new QdrantVariantDocumentIndexer(
      embeddingClient,
      new QdrantClientPointUpsertAdapter(qdrantClient),
      BeautySearchSpecV1.spec.variantDocument,
      embeddingSpec,
    )
    val semanticCandidateSearch = new QdrantSemanticCandidateSearch(
      embeddingClient,
      new QdrantClientSearchAdapter(qdrantClient),
      BeautyQVariantSearchDocumentContract.Fields.variantId,
    )

    ZIO.succeed(QdrantNonProductionExperimentComposition.build(
      readinessConfig = readinessConfig,
      compatibilityGuard = compatibilityGuard,
      snapshotProvider = snapshotProvider,
      documentUpsert = documentUpsert,
      semanticCandidateSearch = semanticCandidateSearch,
    ))
  }
}

final case class QdrantEmbeddingBenchmarkExecutorConfig(
  domainName: String = "beautyq",
  searchSpecVersion: String = "v1",
  collectionPurposePrefix: String = "embedding-benchmark",
  collectionRunId: String = "manual",
  vectorName: String = "llama-cpp-embedding",
  topK: Int = 20,
  scoreThreshold: Option[Double] = None,
  distance: VectorDistance = BeautyQSearchRuntimeContract.ManagedLocalQdrantVectorDistance,
  sourceTextFields: List[SearchField[VariantSearchDocument]] = BeautyQSearchSourceTextFieldsContract.qdrantSourceTextFields,
  cleanupCollections: Boolean = true,
) {
  def sourceTextFieldPaths: List[String] = sourceTextFields.map(_.path)
}

final class QdrantEmbeddingBenchmarkQdrantCandidateExecutor(
  snapshotProvider: VariantSearchDocumentSnapshotProvider[IO],
  collectionClient: QdrantEmbeddingBenchmarkCollectionClient,
  embeddingClientFactory: QdrantEmbeddingBenchmarkEmbeddingClientFactory,
  compositionFactory: QdrantEmbeddingBenchmarkCompositionFactory,
  config: QdrantEmbeddingBenchmarkExecutorConfig,
) extends QdrantEmbeddingBenchmarkCandidateExecutor {
  override def runCandidate(
    candidate: QdrantEmbeddingBenchmarkCandidate,
    queries: List[BeautySearchEvalQuery],
  ): IO[QueryFailure, List[QdrantEmbeddingBenchmarkQueryResult]] =
    for {
      documents <- snapshotProvider.loadSnapshot()
      embeddingSpec = QdrantEmbeddingBenchmarkQdrantCandidateExecutor.embeddingSpec(candidate, config)
      readinessConfig = QdrantEmbeddingBenchmarkQdrantCandidateExecutor.readinessConfig(candidate, config, embeddingSpec)
      collectionPath = s"/collections/${readinessConfig.collectionName}"
      embeddingClient <- embeddingClientFactory.clientFor(candidate)
      fixedSnapshotProvider = new InMemoryVariantSearchDocumentSnapshotProvider[IO](documents)
      composition <- compositionFactory.build(readinessConfig, embeddingClient, fixedSnapshotProvider, embeddingSpec)
      createJson = QdrantJsonInterpreter.createCollectionJson(readinessConfig.vectorSearchSpec, embeddingSpec)
      results <- (
        for {
          _ <- collectionClient.createCollection(collectionPath, createJson)
          _ <- composition.indexSnapshot()
          queryResults <- ZIO.foreach(queries)(query => runQuery(candidate, query, documents, composition))
        } yield queryResults
      ).ensuring(cleanup(collectionPath))
    } yield results

  private def runQuery(
    candidate: QdrantEmbeddingBenchmarkCandidate,
    query: BeautySearchEvalQuery,
    documents: List[VariantSearchDocument],
    composition: QdrantNonProductionExperimentComposition,
  ): IO[QueryFailure, QdrantEmbeddingBenchmarkQueryResult] =
    for {
      startedAt <- ZIO.succeed(System.nanoTime())
      hits <- composition.semanticBackend.candidates(
        UserSearchInput(query = query.query, userLat = None, userLon = None, limit = config.topK),
        ParsedSearchIntent(query.query, Nil, Nil, Nil, query.query),
      )
      finishedAt <- ZIO.succeed(System.nanoTime())
      assembly = QdrantCandidateAssembler.assemble(hits, documents)
    } yield QdrantEmbeddingBenchmarkQueryResult(
      candidateId = candidate.candidateId,
      queryId = query.id,
      queryText = query.query,
      topVariantIds = assembly.variantCandidates.map(_.document.variantId),
      topProviderIds = assembly.providerCandidates.map(_.masterLocationId),
      topServiceIds = assembly.serviceCandidates.map(_.serviceId),
      scores = variantScores(hits, assembly),
      queryLatencyMs = Some((finishedAt - startedAt) / 1000000L),
    )

  private def variantScores(
    hits: List[SemanticCandidateHit],
    assembly: QdrantCandidateAssembly,
  ): List[Double] = {
    val assembledVariantIds = assembly.variantCandidates.map(_.document.variantId).toSet
    hits.filter(hit => assembledVariantIds(hit.variantId)).map(_.score)
  }

  private def cleanup(collectionPath: String): IO[Nothing, Unit] =
    if (config.cleanupCollections) collectionClient.deleteCollection(collectionPath).either.unit
    else ZIO.unit
}

object QdrantEmbeddingBenchmarkQdrantCandidateExecutor {
  def fromQdrantClient(
    snapshotProvider: VariantSearchDocumentSnapshotProvider[IO],
    qdrantClient: QdrantClient,
    embeddingConfig: LlamaCppEmbeddingClientConfig,
    embeddingClientFactory: Option[QdrantEmbeddingBenchmarkEmbeddingClientFactory] = None,
    config: QdrantEmbeddingBenchmarkExecutorConfig = QdrantEmbeddingBenchmarkExecutorConfig(),
  ): QdrantEmbeddingBenchmarkQdrantCandidateExecutor =
    new QdrantEmbeddingBenchmarkQdrantCandidateExecutor(
      snapshotProvider = snapshotProvider,
      collectionClient = new QdrantEmbeddingBenchmarkQdrantCollectionClient(qdrantClient),
      embeddingClientFactory = embeddingClientFactory.getOrElse(new QdrantEmbeddingBenchmarkEmbeddingClientFactory.LlamaCppEndpoint(embeddingConfig)),
      compositionFactory = new QdrantEmbeddingBenchmarkDefaultCompositionFactory(qdrantClient),
      config = config,
    )

  def embeddingSpec(
    candidate: QdrantEmbeddingBenchmarkCandidate,
    config: QdrantEmbeddingBenchmarkExecutorConfig,
  ): EmbeddingSpec[VariantSearchDocument] =
    EmbeddingSpec[VariantSearchDocument](
      vectorName = config.vectorName,
      modelName = candidate.modelName,
      dimension = candidate.vectorDimension,
      distance = config.distance,
      sourceTextFields = config.sourceTextFields,
    )

  def readinessConfig(
    candidate: QdrantEmbeddingBenchmarkCandidate,
    config: QdrantEmbeddingBenchmarkExecutorConfig,
    embeddingSpec: EmbeddingSpec[VariantSearchDocument],
  ): QdrantCollectionReadinessConfig =
    QdrantCollectionReadinessConfig.derive(QdrantCollectionReadinessInput(
      domainName = config.domainName,
      searchSpecVersion = config.searchSpecVersion,
      purpose = collectionPurpose(candidate, config),
      embeddingSpec = embeddingSpec,
      vectorSearchSpec = VectorSearchSpec(
        collectionName = "placeholder",
        vectorName = config.vectorName,
        topK = config.topK,
        scoreThreshold = config.scoreThreshold,
      ),
    ))

  private def collectionPurpose(
    candidate: QdrantEmbeddingBenchmarkCandidate,
    config: QdrantEmbeddingBenchmarkExecutorConfig,
  ): String =
    s"${config.collectionPurposePrefix}-${config.collectionRunId}-${candidate.candidateId}"
}
