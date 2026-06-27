package leaderboard.search

import distage.{DIKey, Mode}
import izumi.distage.model.definition.Activation
import leaderboard.{LeaderboardTest, ProdTest}
import leaderboard.config.QdrantPortCfg
import leaderboard.model.QueryFailure
import leaderboard.repo.{Categories, MasterLocations, MasterServiceOfferVariants, MasterServiceOffers, Masters, ServiceVariantSchemas, Services}
import leaderboard.search.document.{BeautySearchCatalogSnapshotLoader, InMemoryVariantSearchDocumentSnapshotProvider, VariantSearchDocument, VariantSearchDocumentBuilder}
import leaderboard.search.dsl.{BeautySearchSpecV1, EmbeddingSpec, VectorDistance, VectorSearchSpec}
import leaderboard.search.hybrid.ExperimentalBeautySearchService
import leaderboard.search.interpreter.SearchEmbeddingTextExtractor
import leaderboard.search.parser.BeautySearchIntentParser
import leaderboard.search.qdrant.{
  QdrantClient,
  QdrantClientCollectionInfoAdapter,
  QdrantClientPointUpsertAdapter,
  QdrantClientSearchAdapter,
  QdrantCollectionCompatibilityChecker,
  QdrantCollectionCompatibilityGuard,
  QdrantCollectionReadinessConfig,
  QdrantCollectionReadinessInput,
  QdrantJsonInterpreter,
  QdrantNonProductionExperimentComposition,
  QdrantSemanticCandidateSearch,
  QdrantVariantDocumentIndexer,
}
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

  private val semanticSmokeSpec = BeautySearchSpecV1.spec.copy(synonyms = Nil)
  private val parser = new BeautySearchIntentParser(semanticSmokeSpec)
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
        val embeddingConfig = sys.env.get("LLAMA_CPP_EMBEDDING_URL")
          .map(LlamaCppEmbeddingTestConfig.withBaseUrl)
          .getOrElse(LlamaCppEmbeddingTestConfig.default)
        val embeddingClient = LlamaCppEmbeddingTestConfig.client(embeddingConfig)
        val testEffect: IO[QueryFailure, Unit] = embeddingClient.embed("experimental hybrid service endpoint probe").either.flatMap {
          case Left(_) =>
            ZIO.succeed(cancel(s"llama.cpp embedding endpoint ${embeddingConfig.baseUrl} is unavailable; canceling Qdrant experimental hybrid service integration"))
          case Right(vector) if vector.isEmpty =>
            ZIO.succeed(cancel(s"llama.cpp embedding endpoint ${embeddingConfig.baseUrl} returned an empty vector; canceling Qdrant experimental hybrid service integration"))
          case Right(_) =>
            val qdrantClient = new QdrantClient(portCfg.host, portCfg.port)
            val metadata = SearchRoutingMetadata(signal = Some(SearchRoutingSignal.BroadSemanticCandidate))
            val readinessPurpose = s"experimental_hybrid_${UUID.randomUUID().toString.replace('-', '_')}"

            Ref.make(Option.empty[String]).flatMap { collectionPathRef =>
              (for {
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
                targetDocument <- ZIO.fromEither(selectTargetDocument(documents, embeddingSpecTemplate))
                input = UserSearchInput(
                  SearchEmbeddingTextExtractor.extract(BeautySearchSpecV1.spec.variantDocument, embeddingSpecTemplate, targetDocument),
                  userLat = None,
                  userLon = None,
                  limit = 10,
                )
                intent = parser.parse(input)
                routeDecision = SearchBackendRouter.default.decide(input, intent, metadata)
                _ <- assertIO(routeDecision.route == SearchBackendRoute.QdrantCandidateRoute)
                dimensionProbeVector <- embeddingClient.embed("qdrant experimental hybrid service integration dimension probe")
                _ <- assertIO(dimensionProbeVector.nonEmpty)
                embeddingSpec: EmbeddingSpec[VariantSearchDocument] = embeddingSpecTemplate.copy(dimension = dimensionProbeVector.length)
                readinessConfig = QdrantCollectionReadinessConfig.derive(QdrantCollectionReadinessInput(
                  domainName = "beauty_variant",
                  searchSpecVersion = "v1",
                  purpose = readinessPurpose,
                  embeddingSpec = embeddingSpec,
                  vectorSearchSpec = vectorSearchSpecTemplate,
                ))
                collectionPath = s"/collections/${readinessConfig.collectionName}"
                vectorSearchSpec = readinessConfig.vectorSearchSpec
                collectionJson = QdrantJsonInterpreter.createCollectionJson(vectorSearchSpec, embeddingSpec)
                _ <- collectionPathRef.set(Some(collectionPath))
                _ <- qdrantClient.createCollection(collectionPath, collectionJson)
                documentUpsert: QdrantVariantDocumentIndexer = new QdrantVariantDocumentIndexer(
                  embeddingClient,
                  new QdrantClientPointUpsertAdapter(qdrantClient),
                  BeautySearchSpecV1.spec.variantDocument,
                  embeddingSpec,
                )
                compatibilityGuard = new QdrantCollectionCompatibilityGuard(
                  new QdrantCollectionCompatibilityChecker(new QdrantClientCollectionInfoAdapter(qdrantClient))
                )
                semanticSearch: QdrantSemanticCandidateSearch = new QdrantSemanticCandidateSearch(embeddingClient, new QdrantClientSearchAdapter(qdrantClient))
                composition = QdrantNonProductionExperimentComposition.build(
                  readinessConfig = readinessConfig,
                  compatibilityGuard = compatibilityGuard,
                  snapshotProvider = new InMemoryVariantSearchDocumentSnapshotProvider[IO](documents),
                  documentUpsert = documentUpsert,
                  semanticCandidateSearch = semanticSearch,
                )
                indexingResult <- composition.indexSnapshot()
                _ <- assertIO(indexingResult.totalDocumentsLoaded == documents.size)
                _ <- assertIO(indexingResult.totalDocumentsIndexed == documents.size)
                lexicalCalls <- Ref.make(0)
                lexical: FailFastBeautySearchBackend = new FailFastBeautySearchBackend(lexicalCalls)
                lookup: InMemoryVariantSearchDocumentLookup[IO] = new InMemoryVariantSearchDocumentLookup[IO](documents)
                service: ExperimentalBeautySearchService[IO] = new ExperimentalBeautySearchService[IO](
                  parser,
                  semanticSmokeSpec.copy(embeddingSpec = Some(embeddingSpec), vectorSearchSpec = Some(vectorSearchSpec)),
                  lexical,
                  SearchBackendRouter.default,
                  composition.semanticBackend,
                  lookup,
                )
                response <- service.search(input, metadata)
                returnedVariantIds = response.variantCarousel.map(_.variantId)
                indexedVariantIds = documents.iterator.map(_.variantId).toSet
                targetText = SearchEmbeddingTextExtractor.extract(BeautySearchSpecV1.spec.variantDocument, embeddingSpec, targetDocument)
                calls <- lexicalCalls.get
                _ <- assertIO(calls == 0)
                _ <- assertIO(response.variantCarousel.nonEmpty)
                _ <- assertIO(response.facets == Nil)
                _ <- assertIO(response.inferredFilters == Nil)
                _ <- assertOrFail(
                  returnedVariantIds.forall(indexedVariantIds.contains),
                  s"Returned non-indexed variant id from Qdrant candidate path: topVariantIds=${returnedVariantIds.mkString("[", ",", "]")}",
                )
                _ <- assertVariantResultsHydrated(response.variantCarousel, documents)
                _ <- assertOrFail(
                  returnedVariantIds.contains(targetDocument.variantId),
                  s"targetVariantId=${targetDocument.variantId} topVariantIds=${returnedVariantIds.mkString("[", ",", "]")}",
                )
                _ <- assertTopResultIsTargetOrSameIndexedText(response.variantCarousel, documents, targetDocument, targetText, embeddingSpec)
              } yield ()
              ).ensuring(
                collectionPathRef.get.flatMap {
                  case Some(collectionPath) => qdrantClient.deleteCollection(collectionPath).either.unit
                  case None => ZIO.unit
                }
              )
            }
        }

        testEffect
    }
  }

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

  private def selectTargetDocument(
    documents: List[VariantSearchDocument],
    embeddingSpec: EmbeddingSpec[VariantSearchDocument],
  ): Either[QueryFailure, VariantSearchDocument] =
    documents
      .sortBy(_.variantId.toString)
      .find(document => SearchEmbeddingTextExtractor.extract(BeautySearchSpecV1.spec.variantDocument, embeddingSpec, document).nonEmpty)
      .toRight(QueryFailure.domain("No seeded variant document produced embedding text for explicit Qdrant route smoke"))

  private def assertVariantResultsHydrated(
    results: List[VariantSearchResult],
    documents: List[VariantSearchDocument],
  ): IO[QueryFailure, Unit] = {
    val documentsById = documents.iterator.map(document => document.variantId -> document).toMap
    val nonHydrated = results.filterNot { result =>
      documentsById.get(result.variantId).exists { document =>
        result.masterServiceOfferId == document.masterServiceOfferId &&
        result.masterLocationId == document.masterLocationId &&
        result.masterId == document.masterId &&
        result.serviceId == document.serviceId &&
        result.categoryId == document.categoryId &&
        result.serviceName == document.serviceName &&
        result.categoryName == document.categoryName &&
        result.masterName == document.masterName &&
        result.locationName == document.locationName &&
        result.address == document.address &&
        result.lat == document.lat &&
        result.lon == document.lon &&
        result.priceFrom == document.priceFrom &&
        result.priceTo == document.priceTo &&
        result.durationMin == document.durationMin &&
        result.enumAttributes == document.enumAttributes &&
        result.booleanAttributes == document.booleanAttributes &&
        result.intAttributes == document.intAttributes &&
        result.bigDecimalAttributes == document.bigDecimalAttributes
      }
    }

    assertOrFail(
      nonHydrated.isEmpty,
      s"Returned variant results were not hydrated from indexed documents: ${nonHydrated.map(_.variantId).mkString("[", ",", "]")}",
    )
  }

  private def assertTopResultIsTargetOrSameIndexedText(
    results: List[VariantSearchResult],
    documents: List[VariantSearchDocument],
    targetDocument: VariantSearchDocument,
    targetText: String,
    embeddingSpec: EmbeddingSpec[VariantSearchDocument],
  ): IO[QueryFailure, Unit] = {
    val documentsById = documents.iterator.map(document => document.variantId -> document).toMap
    val topDocument = results.headOption.flatMap(result => documentsById.get(result.variantId))
    val topText = topDocument.map(document => SearchEmbeddingTextExtractor.extract(BeautySearchSpecV1.spec.variantDocument, embeddingSpec, document))

    assertOrFail(
      results.headOption.exists(_.variantId == targetDocument.variantId) || topText.contains(targetText),
      s"targetVariantId=${targetDocument.variantId} topVariantId=${results.headOption.map(_.variantId)} targetText=$targetText topText=${topText.getOrElse("<missing>")}",
    )
  }

  private def assertOrFail(condition: Boolean, message: String): IO[QueryFailure, Unit] =
    if (condition) ZIO.unit else ZIO.fail(QueryFailure.operation("qdrant-experimental-hybrid-service-integration", message))

  private final class FailFastBeautySearchBackend(calls: Ref[Int]) extends BeautySearchBackend[IO] {
    override def search(input: UserSearchInput, intent: ParsedSearchIntent): IO[QueryFailure, BeautySearchResponse] =
      calls.update(_ + 1) *> ZIO.fail(QueryFailure.operation("experimental-hybrid-lexical-backend", s"Lexical backend must not be called for ${input.query}: $intent"))
  }
}
