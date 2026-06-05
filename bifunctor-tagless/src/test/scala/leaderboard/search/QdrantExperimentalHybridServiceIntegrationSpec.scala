package leaderboard.search

import distage.{DIKey, Mode}
import izumi.distage.model.definition.Activation
import leaderboard.{LeaderboardTest, ProdTest}
import leaderboard.config.QdrantPortCfg
import leaderboard.model.QueryFailure
import leaderboard.repo.{Categories, MasterLocations, MasterServiceOfferVariants, MasterServiceOffers, Masters, ServiceVariantSchemas, Services}
import leaderboard.search.document.{BeautySearchCatalogSnapshotLoader, VariantSearchDocument, VariantSearchDocumentBuilder}
import leaderboard.search.dsl.{BeautySearchSpecV1, EmbeddingSpec, VectorDistance, VectorSearchSpec}
import leaderboard.search.embedding.{LlamaCppEmbeddingClient, LlamaCppEmbeddingClientConfig}
import leaderboard.search.eval.BeautySearchEvalQuery
import leaderboard.search.hybrid.ExperimentalBeautySearchService
import leaderboard.search.parser.BeautySearchIntentParser
import leaderboard.search.qdrant.{QdrantClient, QdrantClientPointUpsertAdapter, QdrantClientSearchAdapter, QdrantJsonInterpreter, QdrantSemanticCandidateBackend, QdrantSemanticCandidateSearch, QdrantVariantDocumentIndexer}
import leaderboard.search.routing.{SearchBackendRoute, SearchBackendRouter, SearchRoutingMetadata, SearchRoutingSignal}
import leaderboard.search.semantic.InMemoryVariantSearchDocumentLookup
import leaderboard.seed.{BeautyQSeedLoader, BeautyQSeedReady}
import zio.{IO, Ref, ZIO}

import java.util.UUID
import scala.annotation.unused

final class QdrantExperimentalHybridServiceIntegrationSpec extends LeaderboardTest with ProdTest {
  override def config = super.config.copy(
    activation = super.config.activation ++ Activation(Mode -> Mode.Test),
    memoizationRoots = super.config.memoizationRoots + DIKey[QdrantPortCfg],
  )

  private val seed = new BeautyQSeedLoader.ResourceLoader().load() match {
    case Right(value) => value
    case Left(error) => throw new RuntimeException(error.message)
  }

  private val parser = new BeautySearchIntentParser(BeautySearchSpecV1.spec)
  private val evalSuite = BeautySearchEvalInventory.evalSuite
  private val queryId = "q_broad_004"
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

  "Qdrant experimental hybrid service integration" should {
    "run the explicit semantic route end-to-end with real Qdrant and llama.cpp" in {
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
        val testEffect: IO[QueryFailure, Unit] = sys.env.get("LLAMA_CPP_EMBEDDING_URL") match {
          case None =>
            ZIO.succeed(cancel("Set LLAMA_CPP_EMBEDDING_URL=http://localhost:8081 to run the Qdrant experimental hybrid service integration spec")).unit
          case Some(url) if url != "http://localhost:8081" =>
            ZIO.succeed(cancel("Set LLAMA_CPP_EMBEDDING_URL=http://localhost:8081 to run the Qdrant experimental hybrid service integration spec")).unit
          case Some(url) =>
            val qdrantClient = new QdrantClient(portCfg.host, portCfg.port)
            val embeddingClient = new LlamaCppEmbeddingClient(LlamaCppEmbeddingClientConfig(baseUrl = url))
            val collectionName = s"experimental_hybrid_${UUID.randomUUID().toString.replace('-', '_')}"
            val collectionPath = s"/collections/$collectionName"
            val vectorSearchSpec = vectorSearchSpecTemplate.copy(collectionName = collectionName)
            val metadata = SearchRoutingMetadata(signal = Some(SearchRoutingSignal.BroadSemanticCandidate))

            (
              for {
                query <- ZIO.fromEither(loadEvalQuery(queryId))
                documents <- loadDocuments(
                  seedReady,
                  categories,
                  services,
                  serviceVariantSchemas,
                  masters,
                  masterLocations,
                  masterServiceOffers,
                  masterServiceOfferVariants,
                )
                _ <- assertIO(documents.size == seed.masterServiceOfferVariants.size)
                _ <- ZIO
                  .fromOption(documents.headOption)
                  .orElseFail(QueryFailure.domain("No seeded variant documents were available for Qdrant experimental hybrid service integration"))
                  .unit
                input = UserSearchInput(query.query, userLat = None, userLon = None, limit = 10)
                intent = parser.parse(input)
                routeDecision = SearchBackendRouter.default.decide(input, intent, metadata)
                _ <- assertIO(routeDecision.route == SearchBackendRoute.QdrantCandidateRoute)
                dimensionProbeVector <- embeddingClient.embed("qdrant experimental hybrid service integration dimension probe")
                _ <- assertIO(dimensionProbeVector.nonEmpty)
                embeddingSpec: EmbeddingSpec[VariantSearchDocument] = embeddingSpecTemplate.copy(dimension = dimensionProbeVector.length)
                collectionJson = QdrantJsonInterpreter.createCollectionJson(vectorSearchSpec, embeddingSpec)
                _ <- qdrantClient.createCollection(collectionPath, collectionJson)
                indexer: QdrantVariantDocumentIndexer = new QdrantVariantDocumentIndexer(
                  embeddingClient,
                  new QdrantClientPointUpsertAdapter(qdrantClient),
                  BeautySearchSpecV1.spec.variantDocument,
                  embeddingSpec,
                )
                _ <- ZIO.foreachDiscard(documents)(document => indexer.upsertDocument(collectionName, document))
                lexicalCalls <- Ref.make(0)
                lexical: FailFastBeautySearchBackend = new FailFastBeautySearchBackend(lexicalCalls)
                semanticSearch: QdrantSemanticCandidateSearch = new QdrantSemanticCandidateSearch(embeddingClient, new QdrantClientSearchAdapter(qdrantClient))
                semanticBackend: QdrantSemanticCandidateBackend = new QdrantSemanticCandidateBackend(semanticSearch, vectorSearchSpec)
                lookup: InMemoryVariantSearchDocumentLookup[IO] = new InMemoryVariantSearchDocumentLookup[IO](documents)
                service: ExperimentalBeautySearchService[IO] = new ExperimentalBeautySearchService[IO](
                  parser,
                  BeautySearchSpecV1.spec.copy(embeddingSpec = Some(embeddingSpec), vectorSearchSpec = Some(vectorSearchSpec)),
                  lexical,
                  SearchBackendRouter.default,
                  semanticBackend,
                  lookup,
                )
                response <- service.search(input, metadata)
                returnedVariantIds = response.variantCarousel.map(_.variantId)
                returnedProviderIds = response.providerCarousel.map(_.masterLocationId)
                returnedServiceIds = response.serviceIntentCarousel.map(_.serviceId)
                acceptableVariantIds = query.expectedVariantCarousel.acceptableVariantIds.toSet
                acceptableProviderIds = query.expectedProviderCarousel.acceptableProviderLocationIds.toSet
                acceptableServiceIds = query.expectedServiceIntentCarousel.acceptableServiceIds.toSet
                calls <- lexicalCalls.get
                _ <- assertIO(calls == 0)
                _ <- assertIO(response.variantCarousel.nonEmpty)
                _ <- assertIO(response.facets == Nil)
                _ <- assertIO(response.inferredFilters == Nil)
                _ <- assertOrFail(
                  returnedVariantIds.exists(acceptableVariantIds.contains),
                  s"queryId=$queryId topVariantIds=${returnedVariantIds.mkString("[", ",", "]")} acceptableVariantIds=${acceptableVariantIds.toList.sorted.mkString("[", ",", "]")}",
                )
                _ <- assertOrFail(
                  returnedProviderIds.exists(acceptableProviderIds.contains) || returnedServiceIds.exists(acceptableServiceIds.contains),
                  s"queryId=$queryId topProviderIds=${returnedProviderIds.mkString("[", ",", "]")} acceptableProviderIds=${acceptableProviderIds.toList.sorted.mkString("[", ",", "]")} topServiceIds=${returnedServiceIds.mkString("[", ",", "]")} acceptableServiceIds=${acceptableServiceIds.toList.sorted.mkString("[", ",", "]")}",
                )
              } yield ()
            ).ensuring(qdrantClient.deleteCollection(collectionPath).either.unit)
        }

        testEffect
    }
  }

  private def loadEvalQuery(id: String): Either[QueryFailure, BeautySearchEvalQuery] =
    evalSuite.queries.find(_.id == id).toRight(QueryFailure.domain(s"Missing BeautySearch eval query: $id"))

  private def loadDocuments(
    @unused seedReady: BeautyQSeedReady,
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

  private def assertOrFail(condition: Boolean, message: String): IO[QueryFailure, Unit] =
    if (condition) ZIO.unit else ZIO.fail(QueryFailure.operation("qdrant-experimental-hybrid-service-integration", message))

  private final class FailFastBeautySearchBackend(calls: Ref[Int]) extends BeautySearchBackend[IO] {
    override def search(input: UserSearchInput, intent: ParsedSearchIntent): IO[QueryFailure, BeautySearchResponse] =
      calls.update(_ + 1) *> ZIO.fail(QueryFailure.operation("experimental-hybrid-lexical-backend", s"Lexical backend must not be called for ${input.query}: $intent"))
  }
}
