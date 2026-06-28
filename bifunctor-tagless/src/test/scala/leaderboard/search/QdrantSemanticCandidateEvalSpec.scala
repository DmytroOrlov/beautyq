package leaderboard.search

import distage.{DIKey, Mode}
import izumi.distage.model.definition.Activation
import leaderboard.{LeaderboardTest, ProdTest}
import leaderboard.config.QdrantPortCfg
import leaderboard.model.QueryFailure
import leaderboard.repo.{Categories, MasterLocations, MasterServiceOfferVariants, MasterServiceOffers, Masters, ServiceVariantSchemas, Services}
import leaderboard.search.document.{BeautySearchCatalogSnapshotLoader, VariantSearchDocument, VariantSearchDocumentBuilder}
import leaderboard.search.dsl.{BeautySearchSpecV1, EmbeddingSpec, VectorDistance, VectorSearchSpec}
import leaderboard.search.eval.BeautySearchEvalQuery
import leaderboard.search.qdrant.{
  QdrantCandidateHit,
  QdrantClient,
  QdrantClientPointUpsertAdapter,
  QdrantClientSearchAdapter,
  QdrantJsonInterpreter,
  QdrantSemanticCandidateSearch,
  QdrantVariantDocumentIndexer,
}
import leaderboard.seed.{BeautyQSeedLoader, BeautyQSeedReady}
import zio.{IO, ZIO}

import scala.annotation.unused
import java.util.UUID

final class QdrantSemanticCandidateEvalSpec extends LeaderboardTest with ProdTest {
  override def config = {
    val base = super.config
    base.copy(
      activation = base.activation ++ Activation(Mode -> Mode.Test),
      memoizationRoots = base.memoizationRoots + DIKey[QdrantPortCfg],
    )
  }

  private val seed = new BeautyQSeedLoader.ResourceLoader().load() match {
    case Right(value) => value
    case Left(error) => throw new RuntimeException(error.message)
  }

  private val evalSuite = BeautySearchEvalInventory.evalSuite
  private val semanticCandidateQueryIds = Set("q_broad_004", "q_broad_006")
  private val qualityAssertionsEnabled = envFlag("QDRANT_SEMANTIC_QUALITY_ASSERTIONS")
  private val embeddingSpecTemplate = EmbeddingSpec[VariantSearchDocument](
    vectorName = "llama-cpp-embedding",
    modelName = "local-llama-cpp-embedding",
    dimension = 1,
    distance = VectorDistance.Cosine,
    sourceTextFieldPaths = List("serviceText", "attributeText", "allText", "categoryName"),
  )
  private val vectorSearchSpecTemplate = VectorSearchSpec(
    collectionName = "placeholder",
    vectorName = embeddingSpecTemplate.vectorName,
    topK = 20,
    scoreThreshold = None,
  )

  "Qdrant semantic candidate eval" should {
    "measure semantic retrieval for the broad candidate queries" in {
      (
        portCfg: QdrantPortCfg,
        categories: Categories[IO],
        services: Services[IO],
        serviceVariantSchemas: ServiceVariantSchemas[IO],
        masters: Masters[IO],
        masterLocations: MasterLocations[IO],
        masterServiceOffers: MasterServiceOffers[IO],
        masterServiceOfferVariants: MasterServiceOfferVariants[IO],
        seedReady: BeautyQSeedReady,
      ) =>
        val embeddingConfig = sys.env.get("LLAMA_CPP_EMBEDDING_URL")
          .map(LlamaCppEmbeddingTestConfig.withBaseUrl)
          .getOrElse(LlamaCppEmbeddingTestConfig.default)
        val embeddingClient = LlamaCppEmbeddingTestConfig.client(embeddingConfig)
        val testEffect: IO[QueryFailure, Unit] = embeddingClient.embed("semantic candidate eval endpoint probe").either.flatMap {
          case Left(_) =>
            ZIO.succeed(cancel(s"llama.cpp embedding endpoint ${embeddingConfig.baseUrl} is unavailable; canceling Qdrant semantic candidate eval"))
          case Right(vector) if vector.isEmpty =>
            ZIO.succeed(cancel(s"llama.cpp embedding endpoint ${embeddingConfig.baseUrl} returned an empty vector; canceling Qdrant semantic candidate eval"))
          case Right(_) =>
            val qdrantClient = new QdrantClient(portCfg.host, portCfg.port)
            val semanticCandidateSearch = new QdrantSemanticCandidateSearch(embeddingClient, new QdrantClientSearchAdapter(qdrantClient))
            val collectionName = s"semantic_eval_${UUID.randomUUID().toString.replace('-', '_')}"
            val collectionPath = s"/collections/$collectionName"
            val vectorSearchSpec = vectorSearchSpecTemplate.copy(collectionName = collectionName)

            (
              for {
                documents <- loadDocuments(
                  categories,
                  services,
                  serviceVariantSchemas,
                  masters,
                  masterLocations,
                  masterServiceOffers,
                  masterServiceOfferVariants,
                  seedReady,
                )
                _ <- assertIO(documents.size == seed.masterServiceOfferVariants.size)
                documentsByVariantId = documents.iterator.map(document => document.variantId -> document).toMap
                _ <- ZIO
                  .fromOption(documents.headOption)
                  .orElseFail(QueryFailure.domain("No seeded variant documents were available for Qdrant semantic evaluation"))
                  .unit
                dimensionProbeVector <- embeddingClient.embed("qdrant semantic candidate eval dimension probe")
                _ <- assertIO(dimensionProbeVector.nonEmpty)
                embeddingSpec: EmbeddingSpec[VariantSearchDocument] = embeddingSpecTemplate.copy(dimension = dimensionProbeVector.length)
                indexer = new QdrantVariantDocumentIndexer(
                  embeddingClient,
                  new QdrantClientPointUpsertAdapter(qdrantClient),
                  BeautySearchSpecV1.spec.variantDocument,
                  embeddingSpec,
                )
                collectionJson = QdrantJsonInterpreter.createCollectionJson(vectorSearchSpec, embeddingSpec)
                _ <- qdrantClient.createCollection(collectionPath, collectionJson)
                _ <- ZIO.foreachDiscard(documents)(document => indexer.upsertDocument(collectionName, document))
                semanticQueries <- ZIO.succeed(evalSuite.queries.filter(query => semanticCandidateQueryIds.contains(query.id)))
                _ <- assertIO(semanticQueries.map(_.id).toSet == semanticCandidateQueryIds)
                measurements <- ZIO.foreach(semanticQueries) { query =>
                  for {
                    hits <- semanticCandidateSearch.search(query.query, vectorSearchSpec)
                    measurement = SemanticEvalMeasurement.from(query, hits, documentsByVariantId)
                    _ <- ZIO.succeed(assert(hits.nonEmpty, measurement.render))
                    _ <- requireQualityAssertions(query, measurement)
                    _ <- ZIO.succeed(println(measurement.render))
                  } yield measurement
                }
                _ <- ZIO.succeed {
                  val summary = measurements.map(_.summary).mkString("; ")
                  println(s"Qdrant semantic candidate eval summary: $summary")
                }
              } yield ()
            ).ensuring(qdrantClient.deleteCollection(collectionPath).either.unit)
        }
        testEffect
    }
  }

  private def loadDocuments(
    categories: Categories[IO],
    services: Services[IO],
    serviceVariantSchemas: ServiceVariantSchemas[IO],
    masters: Masters[IO],
    masterLocations: MasterLocations[IO],
    masterServiceOffers: MasterServiceOffers[IO],
    masterServiceOfferVariants: MasterServiceOfferVariants[IO],
    @unused seedReady: BeautyQSeedReady,
  ): IO[QueryFailure, List[VariantSearchDocument]] = {
    val loader = new BeautySearchCatalogSnapshotLoader.SeedScopedFromRepositories[IO](
      seedReady,
      seed,
      categories,
      services,
      serviceVariantSchemas,
      masters,
      masterLocations,
      masterServiceOffers,
      masterServiceOfferVariants,
    )

    for {
      snapshot <- loader.load()
      documents <- ZIO.fromEither(VariantSearchDocumentBuilder.build(snapshot))
    } yield documents
  }

  private def requireQualityAssertions(
    query: BeautySearchEvalQuery,
    measurement: SemanticEvalMeasurement,
  ): IO[QueryFailure, Unit] =
    if (qualityAssertionsEnabled) measurement.requireTopK(query)
    else ZIO.unit

  private def envFlag(name: String): Boolean =
    sys.env.get(name).exists { value =>
      val normalized = value.trim.toLowerCase
      normalized == "1" || normalized == "true" || normalized == "yes"
    }
}

private final case class SemanticEvalMeasurement(
  queryId: String,
  queryText: String,
  topVariantIds: List[String],
  topProviderIds: List[String],
  topServiceIds: List[String],
  scores: List[Double],
  acceptableVariantIds: Set[String],
  acceptableProviderIds: Set[String],
  acceptableServiceIds: Set[String],
) {
  val hasAcceptableVariant: Boolean = topVariantIds.exists(acceptableVariantIds.contains)
  val hasAcceptableProvider: Boolean = topProviderIds.exists(acceptableProviderIds.contains)
  val hasAcceptableService: Boolean = topServiceIds.exists(acceptableServiceIds.contains)

  def status: String =
    if (hasAcceptableVariant || hasAcceptableProvider || hasAcceptableService) "pass" else "measurement-only"

  def summary: String =
    s"$queryId:$status"

  def requireTopK(query: BeautySearchEvalQuery): IO[QueryFailure, Unit] = {
    val providerTopK = query.expectedProviderCarousel.topK.requiredInTopK.getOrElse(5)
    val serviceTopK = query.expectedServiceIntentCarousel.topK.requiredInTopK.getOrElse(3)

    // ES eval uses a tighter variant top-3 expectation. This resource-backed Qdrant
    // supplement candidate quality check validates candidate recall in Qdrant topK
    // for the broad non-lexical queries: an acceptable variant must appear somewhere
    // in the returned Qdrant topK, while provider/service expectations remain
    // constrained by their eval topK windows. Exact variant ranking, score fusion,
    // reranking, and route/default behavior are intentionally out of scope and not
    // implied by this spec.
    val variantOk = topVariantIds.exists(acceptableVariantIds.contains)
    val providerOk = topProviderIds.take(providerTopK).exists(acceptableProviderIds.contains)
    val serviceOk = topServiceIds.take(serviceTopK).exists(acceptableServiceIds.contains)

    if (variantOk && providerOk && serviceOk) ZIO.unit
    else {
      val failure =
        s"queryId=$queryId query=$queryText " +
          s"variantTopK=returned_topK variantOk=$variantOk " +
          s"providerTopK=$providerTopK providerOk=$providerOk " +
          s"serviceTopK=$serviceTopK serviceOk=$serviceOk " +
          render
      ZIO.fail(QueryFailure.operation("qdrant-semantic-quality-assertion", failure))
    }
  }

  def render: String =
    s"queryId=$queryId status=$status query=$queryText " +
      s"topVariantIds=${topVariantIds.mkString("[", ",", "]")} " +
      s"topProviderIds=${topProviderIds.mkString("[", ",", "]")} " +
      s"topServiceIds=${topServiceIds.mkString("[", ",", "]")} " +
      s"scores=${scores.mkString("[", ",", "]")} " +
      s"acceptableVariantIds=${acceptableVariantIds.toList.sorted.mkString("[", ",", "]")} " +
      s"acceptableProviderIds=${acceptableProviderIds.toList.sorted.mkString("[", ",", "]")} " +
      s"acceptableServiceIds=${acceptableServiceIds.toList.sorted.mkString("[", ",", "]")}"
}

private object SemanticEvalMeasurement {
  def from(
    query: BeautySearchEvalQuery,
    hits: List[QdrantCandidateHit],
    documentsByVariantId: Map[UUID, VariantSearchDocument],
  ): SemanticEvalMeasurement =
    SemanticEvalMeasurement(
      queryId = query.id,
      queryText = query.query,
      topVariantIds = hits.map(_.variantId.toString).distinct,
      topProviderIds = documentValues(hits, documentsByVariantId)(_.masterLocationId.toString),
      topServiceIds = documentValues(hits, documentsByVariantId)(_.serviceId.toString),
      scores = hits.map(_.score),
      acceptableVariantIds = query.expectedVariantCarousel.acceptableVariantIds.map(_.toString).toSet,
      acceptableProviderIds = query.expectedProviderCarousel.acceptableProviderLocationIds.map(_.toString).toSet,
      acceptableServiceIds = query.expectedServiceIntentCarousel.acceptableServiceIds.map(_.toString).toSet,
    )

  private def documentValues(
    hits: List[QdrantCandidateHit],
    documentsByVariantId: Map[UUID, VariantSearchDocument],
  )(value: VariantSearchDocument => String): List[String] =
    hits.iterator.flatMap(hit => documentsByVariantId.get(hit.variantId).map(value)).toList.distinct
}
