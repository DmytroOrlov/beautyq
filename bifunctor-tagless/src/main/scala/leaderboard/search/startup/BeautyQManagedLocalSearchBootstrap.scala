package leaderboard.search.startup

import distage.Lifecycle
import izumi.functional.bio.{Error2, F}
import leaderboard.model.QueryFailure
import leaderboard.runtime.QueryFailureToThrowable
import leaderboard.search.document.{BeautySearchReadyCatalogDocuments, InMemoryVariantSearchDocumentSnapshotProvider, VariantSearchDocument}
import leaderboard.search.dsl.{BeautySearchSpec, EmbeddingSpec, VectorDistance, VectorSearchSpec}
import io.circe.Json
import leaderboard.search.elasticsearch.{ElasticsearchJsonClient, ElasticsearchSeedIndexInitializer}
import leaderboard.search.embedding.EmbeddingClient
import leaderboard.search.qdrant.{
  QdrantClient,
  QdrantClientCollectionInfoAdapter,
  QdrantCollectionIdentity,
  QdrantCollectionReadinessConfig,
  QdrantEmbeddingBenchmarkDefaultCompositionFactory,
  QdrantJsonInterpreter,
}
import logstage.LogIO2
import zio.{Duration, IO, Schedule, ZIO}

/**
 * Local managed-launcher startup bootstrap for the BeautyQ `/beauty-search` supplement route.
 *
 * This is the single source-of-truth helper that prepares the two search backends the constrained
 * Qdrant-supplement route reads from, using the same BeautyQ catalog the SQL/ES search documents are
 * built from:
 *
 *   - Elasticsearch baseline: (re)create the seed index (`spec.variantDocument.indexName`) and bulk
 *     index the ready catalog documents through the existing `ElasticsearchSeedIndexInitializer`.
 *   - Qdrant supplement: (re)create the fixed local collection and upsert the BeautyQ variant vectors
 *     through the existing `QdrantEmbeddingBenchmarkDefaultCompositionFactory` /
 *     `QdrantNonProductionExperimentComposition.indexSnapshot()` (compatibility guard + point upsert).
 *
 * It is idempotent across repeated local starts and repeated full test runs: each backend is dropped
 * (ignoring "not found") before being recreated, so it never raises `resource_already_exists`. It does
 * NOT fake readiness: a failed embedding/ES/Qdrant call fails loudly instead of degrading to a fake
 * ES-only response. It is intended only for the `Scene.Managed` local launcher path; production startup
 * indexing remains out of scope.
 */
final case class BeautyQManagedLocalSearchBootstrapResult(
  esIndexName: String,
  esDocumentCount: Int,
  esAction: BeautyQManagedLocalSearchBootstrapAction,
  qdrantCollectionName: String,
  qdrantIndexedCount: Int,
  qdrantAction: BeautyQManagedLocalSearchBootstrapAction,
  vectorDimension: Int,
  fingerprint: String,
)

sealed trait BeautyQManagedLocalSearchBootstrapAction extends Product with Serializable {
  def label: String
}

object BeautyQManagedLocalSearchBootstrapAction {
  case object Rebuilt extends BeautyQManagedLocalSearchBootstrapAction {
    override val label: String = "rebuilt"
  }

  case object Reused extends BeautyQManagedLocalSearchBootstrapAction {
    override val label: String = "reused"
  }
}

object BeautyQManagedLocalSearchBootstrap {
  val EmbeddingPreflightOperationName: String = "beautyq-managed-local-embedding-preflight"
  val EmbeddingModelName: String = "local-llama-cpp-embedding"
  val SourceTextFieldPaths: List[String] = List("serviceText", "attributeText", "allText", "categoryName")

  /**
   * Expected local managed BeautyQ Qdrant vector dimension. It matches the fixed local launcher
   * collection (`..._1024_cosine`, dimension `1024`); the embedding preflight rejects any endpoint
   * that does not return exactly this many components so the Qdrant collection is only ever created
   * for vectors it can actually hold.
   */
  val ExpectedVectorDimension: Int = 1024

  private val DimensionProbeText: String = "beautyq managed local search bootstrap probe"
  private val EmbeddingPreflightContext: String = "BeautyQ managed local search Qdrant bootstrap embedding preflight"

  /**
   * Local managed embedding preflight: the earliest safe fail-fast point in the managed bootstrap path.
   * Before any ES/Qdrant work, it calls the configured embedding endpoint once and proves the endpoint
   * is reachable, returns a non-empty vector, and returns exactly [[ExpectedVectorDimension]] components.
   *
   * On any of those failing it fails with a typed [[QueryFailure]] that names the local managed BeautyQ
   * Qdrant bootstrap, the embedding endpoint URL, the expected dimension, and the actual failure reason
   * (connection failure, empty embedding, or wrong dimension). It never catches-and-continues and never
   * degrades to an ES-only path, so a failing preflight propagates and prevents the HTTP server bind.
   */
  def embeddingPreflight(
    embeddingClient: EmbeddingClient,
    embeddingEndpoint: String,
    expectedDimension: Int,
  ): IO[QueryFailure, Int] =
    embeddingClient
      .embed(DimensionProbeText)
      .foldZIO(
        failure =>
          ZIO.fail(
            QueryFailure.operation(
              EmbeddingPreflightOperationName,
              s"$EmbeddingPreflightContext could not reach the embedding endpoint: endpoint=$embeddingEndpoint expectedDimension=$expectedDimension reason=${failure.message}",
            )
          ),
        vector =>
          if (vector.isEmpty)
            ZIO.fail(
              QueryFailure.operation(
                EmbeddingPreflightOperationName,
                s"$EmbeddingPreflightContext received an empty embedding: endpoint=$embeddingEndpoint expectedDimension=$expectedDimension reason=empty embedding",
              )
            )
          else if (vector.length != expectedDimension)
            ZIO.fail(
              QueryFailure.operation(
                EmbeddingPreflightOperationName,
                s"$EmbeddingPreflightContext received the wrong embedding dimension: endpoint=$embeddingEndpoint expectedDimension=$expectedDimension actualDimension=${vector.length}",
              )
            )
          else
            ZIO.succeed(expectedDimension),
      )

  def embeddingSpec(vectorSearchSpec: VectorSearchSpec, dimension: Int): EmbeddingSpec[VariantSearchDocument] =
    EmbeddingSpec[VariantSearchDocument](
      vectorName = vectorSearchSpec.vectorName,
      modelName = EmbeddingModelName,
      dimension = dimension,
      distance = VectorDistance.Cosine,
      sourceTextFieldPaths = SourceTextFieldPaths,
    )

  def readinessConfig(vectorSearchSpec: VectorSearchSpec, dimension: Int): QdrantCollectionReadinessConfig = {
    val spec = embeddingSpec(vectorSearchSpec, dimension)
    QdrantCollectionReadinessConfig(
      collectionName = vectorSearchSpec.collectionName,
      vectorSearchSpec = vectorSearchSpec,
      compatibilityExpectation = QdrantCollectionIdentity.compatibilityExpectation(spec, vectorSearchSpec),
    )
  }

  def run(
    esClient: ElasticsearchJsonClient,
    qdrantClient: QdrantClient,
    embeddingClient: EmbeddingClient,
    spec: BeautySearchSpec,
    catalog: BeautySearchReadyCatalogDocuments,
    vectorSearchSpec: VectorSearchSpec,
    embeddingEndpoint: String,
  ): IO[QueryFailure, BeautyQManagedLocalSearchBootstrapResult] = {
    val documents = catalog.documents
    for {
      // Fail fast before any ES/Qdrant work if the embedding endpoint is unavailable, returns an empty
      // vector, or returns the wrong dimension. No catch-and-continue, no ES-only fallback.
      dimension <- embeddingPreflight(embeddingClient, embeddingEndpoint, ExpectedVectorDimension)
      embeddingSpec = BeautyQManagedLocalSearchBootstrap.embeddingSpec(vectorSearchSpec, dimension)
      fingerprint = BeautyQManagedLocalSearchBootstrapFingerprint.build(spec, catalog, vectorSearchSpec, embeddingSpec, embeddingEndpoint)
      reuse <- preparedResourcesReusable(esClient, qdrantClient, spec, vectorSearchSpec, dimension, documents.size, fingerprint)
      result <- if (reuse) {
        ZIO.succeed(
          BeautyQManagedLocalSearchBootstrapResult(
            esIndexName = spec.variantDocument.indexName,
            esDocumentCount = documents.size,
            esAction = BeautyQManagedLocalSearchBootstrapAction.Reused,
            qdrantCollectionName = vectorSearchSpec.collectionName,
            qdrantIndexedCount = documents.size,
            qdrantAction = BeautyQManagedLocalSearchBootstrapAction.Reused,
            vectorDimension = dimension,
            fingerprint = fingerprint.value,
          )
        )
      } else {
        for {
          esReadiness <- prepareElasticsearch(esClient, spec, catalog)
          qdrantIndexed <- prepareQdrant(qdrantClient, embeddingClient, vectorSearchSpec, dimension, documents)
          _ <- writeFingerprint(esClient, spec, fingerprint)
        } yield BeautyQManagedLocalSearchBootstrapResult(
          esIndexName = esReadiness.indexName,
          esDocumentCount = esReadiness.documentCount,
          esAction = BeautyQManagedLocalSearchBootstrapAction.Rebuilt,
          qdrantCollectionName = vectorSearchSpec.collectionName,
          qdrantIndexedCount = qdrantIndexed,
          qdrantAction = BeautyQManagedLocalSearchBootstrapAction.Rebuilt,
          vectorDimension = dimension,
          fingerprint = fingerprint.value,
        )
      }
    } yield result
  }

  private def preparedResourcesReusable(
    esClient: ElasticsearchJsonClient,
    qdrantClient: QdrantClient,
    spec: BeautySearchSpec,
    vectorSearchSpec: VectorSearchSpec,
    dimension: Int,
    expectedDocumentCount: Int,
    fingerprint: BeautyQManagedLocalSearchBootstrapFingerprint,
  ): IO[QueryFailure, Boolean] =
    for {
      esReusable     <- elasticsearchReusable(esClient, spec, fingerprint)
      qdrantReusable <- qdrantReusable(qdrantClient, vectorSearchSpec, dimension, expectedDocumentCount)
    } yield esReusable && qdrantReusable

  private def elasticsearchReusable(
    esClient: ElasticsearchJsonClient,
    spec: BeautySearchSpec,
    fingerprint: BeautyQManagedLocalSearchBootstrapFingerprint,
  ): IO[QueryFailure, Boolean] =
    for {
      indexExists <- esClient.getJson(s"/${spec.variantDocument.indexName}").either.map(_.isRight)
      marker <- if (indexExists) {
        esClient.getJson(s"/${fingerprintIndexName(spec)}/_doc/current").either.map {
          case Right(json) =>
            BeautyQManagedLocalSearchBootstrapFingerprint.decodeValue(json).contains(fingerprint.value)
          case Left(_) =>
            false
        }
      } else {
        ZIO.succeed(false)
      }
    } yield indexExists && marker

  private def qdrantReusable(
    qdrantClient: QdrantClient,
    vectorSearchSpec: VectorSearchSpec,
    dimension: Int,
    expectedDocumentCount: Int,
  ): IO[QueryFailure, Boolean] = {
    val checker = new leaderboard.search.qdrant.QdrantCollectionCompatibilityChecker(new QdrantClientCollectionInfoAdapter(qdrantClient))
    val expectation = readinessConfig(vectorSearchSpec, dimension).compatibilityExpectation
    for {
      compatibility <- checker.check(expectation).either
      reusable <- compatibility match {
        case Right(Right(())) =>
          qdrantClient.collectionInfo(s"/collections/${vectorSearchSpec.collectionName}").either.map {
            case Right(json) => observedQdrantPointCount(json).exists(_ >= expectedDocumentCount)
            case Left(_)     => false
          }
        case Right(Left(_)) =>
          ZIO.succeed(false)
        case Left(_) =>
          ZIO.succeed(false)
      }
    } yield reusable
  }

  private def prepareElasticsearch(
    esClient: ElasticsearchJsonClient,
    spec: BeautySearchSpec,
    catalog: BeautySearchReadyCatalogDocuments,
  ) =
    for {
      // Drop a stale index first so repeated starts/test runs never raise resource_already_exists.
      _ <- esClient.delete(s"/${spec.variantDocument.indexName}").either
      _ <- esClient.delete(s"/${fingerprintIndexName(spec)}").either
      readiness <- new ElasticsearchSeedIndexInitializer(spec, esClient).prepare(catalog)
    } yield readiness

  private def writeFingerprint(
    esClient: ElasticsearchJsonClient,
    spec: BeautySearchSpec,
    fingerprint: BeautyQManagedLocalSearchBootstrapFingerprint,
  ): IO[QueryFailure, Unit] =
    for {
      _ <- esClient.putJson(s"/${fingerprintIndexName(spec)}", fingerprintIndexMapping)
      _ <- esClient.putJson(s"/${fingerprintIndexName(spec)}/_doc/current", fingerprint.asMetadataJson)
      _ <- esClient.post(s"/${fingerprintIndexName(spec)}/_refresh")
    } yield ()

  private def prepareQdrant(
    qdrantClient: QdrantClient,
    embeddingClient: EmbeddingClient,
    vectorSearchSpec: VectorSearchSpec,
    dimension: Int,
    documents: List[VariantSearchDocument],
  ): IO[QueryFailure, Int] = {
    val spec = embeddingSpec(vectorSearchSpec, dimension)
    val config = readinessConfig(vectorSearchSpec, dimension)
    val collectionPath = s"/collections/${vectorSearchSpec.collectionName}"
    val snapshotProvider = new InMemoryVariantSearchDocumentSnapshotProvider[IO](documents)
    val compositionFactory = new QdrantEmbeddingBenchmarkDefaultCompositionFactory(qdrantClient)
    for {
      composition <- compositionFactory.build(config, embeddingClient, snapshotProvider, spec)
      // Drop a stale collection first so repeated starts/test runs never raise resource_already_exists.
      result <- (
        for {
          _ <- qdrantClient.deleteCollection(collectionPath).either
          _ <- qdrantClient
            .createCollection(collectionPath, QdrantJsonInterpreter.createCollectionJson(vectorSearchSpec, spec))
            .either
            .flatMap {
              case Right(_) => ZIO.unit
              case Left(failure) if isQdrantCollectionAlreadyExists(vectorSearchSpec.collectionName)(failure) =>
                ZIO.unit
              case Left(failure) =>
                ZIO.fail(failure)
            }
          result <- composition.indexSnapshot()
        } yield result
      ).retry(Schedule.recurWhile[QueryFailure](isTransientQdrantCollectionMiss(vectorSearchSpec.collectionName)) && Schedule.spaced(Duration.fromMillis(250)))
    } yield result.totalDocumentsIndexed
  }

  private def isQdrantCollectionAlreadyExists(collectionName: String)(failure: QueryFailure): Boolean =
    failure match {
      case QueryFailure.OperationFailure("create-qdrant-collection", message) =>
        message.contains(s"Collection `$collectionName` already exists")
      case _ =>
        false
    }

  private def isTransientQdrantCollectionMiss(collectionName: String)(failure: QueryFailure): Boolean =
    failure match {
      case QueryFailure.OperationFailure("upsert-qdrant-point", message) =>
        message.contains(s"Collection `$collectionName` doesn't exist")
      case _ =>
        false
    }

  private def fingerprintIndexName(spec: BeautySearchSpec): String =
    s"${spec.variantDocument.indexName}__managed_local_bootstrap_fingerprint"

  private val fingerprintIndexMapping: Json =
    Json.obj(
      "settings" -> Json.obj(
        "index" -> Json.obj(
          "number_of_shards" -> Json.fromInt(1),
          "number_of_replicas" -> Json.fromInt(0),
        )
      ),
      "mappings" -> Json.obj(
        "properties" -> Json.obj(
          "fingerprint" -> Json.obj("type" -> Json.fromString("keyword")),
          "inputs" -> Json.obj("enabled" -> Json.fromBoolean(false)),
        )
      ),
    )

  private def observedQdrantPointCount(json: Json): Option[Int] =
    List(
      json.hcursor.downField("result").get[Int]("points_count").toOption,
      json.hcursor.downField("result").get[Int]("indexed_vectors_count").toOption,
      json.hcursor.get[Int]("points_count").toOption,
      json.hcursor.get[Int]("indexed_vectors_count").toOption,
    ).flatten.headOption
}

/**
 * Startup readiness marker the local managed launcher's HTTP server depends on, mirroring
 * `BeautyQSeedReady`: when the real `Bootstrap` resource is acquired, the BeautyQ ES baseline and
 * Qdrant supplement data are prepared before `/beauty-search` is served. Non-managed (production /
 * provided) graphs get the `Noop` so no startup indexing happens there.
 */
trait BeautyQManagedLocalSearchDataReady

object BeautyQManagedLocalSearchDataReady {
  private object Ready extends BeautyQManagedLocalSearchDataReady

  final class Noop[F[+_, +_]: Error2]
    extends Lifecycle.LiftF[F[Throwable, _], BeautyQManagedLocalSearchDataReady](F.pure(Ready))

  final class Bootstrap(
    esClient: ElasticsearchJsonClient,
    qdrantClient: QdrantClient,
    embeddingClient: EmbeddingClient,
    spec: BeautySearchSpec,
    catalog: BeautySearchReadyCatalogDocuments,
    vectorSearchSpec: VectorSearchSpec,
    embeddingEndpoint: String,
    log: LogIO2[IO],
  ) extends Lifecycle.LiftF[IO[Throwable, _], BeautyQManagedLocalSearchDataReady](
      for {
        result <- QueryFailureToThrowable.lift(
          BeautyQManagedLocalSearchBootstrap.run(esClient, qdrantClient, embeddingClient, spec, catalog, vectorSearchSpec, embeddingEndpoint)
        )
        _ <- log.info(
          s"BeautyQ managed local search data ready: embeddingEndpoint=$embeddingEndpoint esIndex=${result.esIndexName} esAction=${result.esAction.label} esDocs=${result.esDocumentCount} qdrantCollection=${result.qdrantCollectionName} qdrantAction=${result.qdrantAction.label} qdrantVectors=${result.qdrantIndexedCount} dimension=${result.vectorDimension} fingerprint=${result.fingerprint}"
        )
      } yield Ready
    )
}
