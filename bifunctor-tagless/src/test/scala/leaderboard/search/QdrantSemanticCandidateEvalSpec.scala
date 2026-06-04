package leaderboard.search

import distage.{DIKey, Mode}
import io.circe.Json
import izumi.distage.model.definition.Activation
import leaderboard.{LeaderboardTest, ProdTest}
import leaderboard.config.QdrantPortCfg
import leaderboard.model.QueryFailure
import leaderboard.repo.{Categories, MasterLocations, MasterServiceOfferVariants, MasterServiceOffers, Masters, ServiceVariantSchemas, Services}
import leaderboard.search.embedding.{LlamaCppEmbeddingClient, LlamaCppEmbeddingClientConfig}
import leaderboard.search.document.{BeautySearchCatalogSnapshotLoader, VariantSearchDocument, VariantSearchDocumentBuilder}
import leaderboard.search.dsl.{BeautySearchSpecV1, EmbeddingSpec, VectorDistance, VectorSearchSpec}
import leaderboard.search.eval.BeautySearchEvalQuery
import leaderboard.search.interpreter.SearchEmbeddingTextExtractor
import leaderboard.search.qdrant.{QdrantClient, QdrantSearchHit}
import leaderboard.search.qdrant.QdrantJsonInterpreter
import leaderboard.seed.BeautyQSeedLoader
import zio.{IO, ZIO}

import java.util.UUID

final class QdrantSemanticCandidateEvalSpec extends LeaderboardTest with ProdTest {
  override def config = super.config.copy(
    activation = super.config.activation ++ Activation(Mode -> Mode.Test),
    memoizationRoots = super.config.memoizationRoots + DIKey[QdrantPortCfg],
  )

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
      ) =>
        val testEffect: IO[QueryFailure, Unit] = sys.env.get("LLAMA_CPP_EMBEDDING_URL") match {
          case None =>
            ZIO.succeed(cancel("Set LLAMA_CPP_EMBEDDING_URL=http://localhost:8081 to run the Qdrant semantic candidate eval")).unit
          case Some(url) if url != "http://localhost:8081" =>
            ZIO.succeed(cancel("Set LLAMA_CPP_EMBEDDING_URL=http://localhost:8081 to run the Qdrant semantic candidate eval")).unit
          case Some(url) =>
            val qdrantClient = new QdrantClient(portCfg.host, portCfg.port)
            val embeddingClient = new LlamaCppEmbeddingClient(LlamaCppEmbeddingClientConfig(baseUrl = url))
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
                )
                _ <- assertIO(documents.size == seed.masterServiceOfferVariants.size)
                firstDocument <- ZIO
                  .fromOption(documents.headOption)
                  .orElseFail(QueryFailure.domain("No seeded variant documents were available for Qdrant semantic evaluation"))
                firstText = embeddingText(firstDocument)
                _ <- assertIO(firstText.nonEmpty)
                firstVector <- embeddingClient.embed(firstText)
                _ <- assertIO(firstVector.nonEmpty)
                embeddingSpec: EmbeddingSpec[VariantSearchDocument] = embeddingSpecTemplate.copy(dimension = firstVector.length)
                collectionJson = QdrantJsonInterpreter.createCollectionJson(vectorSearchSpec, embeddingSpec)
                _ <- qdrantClient.createCollection(collectionPath, collectionJson)
                _ <- upsertDocument(qdrantClient, collectionPath, embeddingSpec, firstDocument, firstVector)
                _ <- ZIO.foreachDiscard(documents.tail) { document =>
                  for {
                    vector <- embeddingClient.embed(embeddingText(document))
                    _ <- qdrantClient.upsertPoint(
                      s"$collectionPath/points?wait=true",
                      QdrantJsonInterpreter.upsertPointJson(document.variantId.toString, embeddingSpec.vectorName, vector.toList, payload(document)),
                    )
                  } yield ()
                }
                semanticQueries <- ZIO.succeed(evalSuite.queries.filter(query => semanticCandidateQueryIds.contains(query.id)))
                _ <- assertIO(semanticQueries.map(_.id).toSet == semanticCandidateQueryIds)
                measurements <- ZIO.foreach(semanticQueries) { query =>
                  for {
                    queryVector <- embeddingClient.embed(query.query)
                    hits <- qdrantClient.search(
                      s"$collectionPath/points/search",
                      QdrantJsonInterpreter.searchRequestJson(vectorSearchSpec, queryVector.toList),
                    )
                    measurement = SemanticEvalMeasurement.from(query, hits)
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
  ): IO[QueryFailure, List[VariantSearchDocument]] = {
    val loader = new BeautySearchCatalogSnapshotLoader.SeedScopedFromRepositories[IO](
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

  private def embeddingText(document: VariantSearchDocument): String =
    SearchEmbeddingTextExtractor.extract(BeautySearchSpecV1.spec.variantDocument, embeddingSpecTemplate, document)

  private def upsertDocument(
    qdrantClient: QdrantClient,
    collectionPath: String,
    embeddingSpec: EmbeddingSpec[VariantSearchDocument],
    document: VariantSearchDocument,
    vector: Vector[Double],
  ): IO[QueryFailure, Json] =
    qdrantClient.upsertPoint(
      s"$collectionPath/points?wait=true",
      QdrantJsonInterpreter.upsertPointJson(document.variantId.toString, embeddingSpec.vectorName, vector.toList, payload(document)),
    )

  private def payload(document: VariantSearchDocument): Map[String, Json] =
    Map(
      "variantId" -> Json.fromString(document.variantId.toString),
      "masterLocationId" -> Json.fromString(document.masterLocationId.toString),
      "serviceId" -> Json.fromString(document.serviceId.toString),
      "serviceName" -> Json.fromString(document.serviceName),
    )

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

    // ES eval uses a tighter variant top-3 expectation, but that is too strict for
    // Qdrant-only broad semantic recall. This optional Qdrant-only gate validates
    // semantic candidate recall quality: an acceptable variant must appear somewhere
    // in the returned Qdrant topK, while provider/service stay constrained by their
    // eval topK windows. Exact variant ranking, fusion, and reranking remain future
    // hybrid/rerank work and are intentionally out of scope for this spec.
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
  def from(query: BeautySearchEvalQuery, hits: List[QdrantSearchHit]): SemanticEvalMeasurement =
    SemanticEvalMeasurement(
      queryId = query.id,
      queryText = query.query,
      topVariantIds = payloadValues(hits, "variantId"),
      topProviderIds = payloadValues(hits, "masterLocationId"),
      topServiceIds = payloadValues(hits, "serviceId"),
      scores = hits.map(_.score),
      acceptableVariantIds = query.expectedVariantCarousel.acceptableVariantIds.map(_.toString).toSet,
      acceptableProviderIds = query.expectedProviderCarousel.acceptableProviderLocationIds.map(_.toString).toSet,
      acceptableServiceIds = query.expectedServiceIntentCarousel.acceptableServiceIds.map(_.toString).toSet,
    )

  private def payloadValues(hits: List[QdrantSearchHit], key: String): List[String] =
    hits.iterator.flatMap(_.payload.apply(key).flatMap(_.asString)).toList.distinct
}
