package leaderboard.search

import distage.{DIKey, Mode}
import izumi.distage.model.definition.Activation
import leaderboard.{LeaderboardTest, ProdTest}
import leaderboard.config.QdrantPortCfg
import leaderboard.model.{MasterServiceOfferVariantId, QueryFailure}
import leaderboard.search.document.{VariantSearchDocument, VariantSearchDocumentSnapshotProvider}
import leaderboard.search.dsl.{BeautySearchSpecV1, EmbeddingSpec, VectorDistance}
import leaderboard.search.embedding.EmbeddingClient
import leaderboard.search.hybrid.{
  BeautyQNonProductionHybridRunnerQdrantClientInputs,
}
import leaderboard.search.lexical.{LexicalDocumentBackend, LexicalDocumentHit}
import leaderboard.search.qdrant.{QdrantJsonInterpreter, QdrantCollectionReadinessConfig}
import leaderboard.search.qdrant.QdrantClient
import leaderboard.search.semantic.SemanticDocumentLookup
import leaderboard.search.{ParsedSearchIntent, UserSearchInput}
import zio.{IO, ZIO}

import java.util.UUID

final class BeautyQManualHybridRealQdrantRetrievalSmokeSpec extends LeaderboardTest with ProdTest {
  override def config = {
    val base = super.config
    base.copy(
      activation = base.activation ++ Activation(Mode -> Mode.Test),
      memoizationRoots = base.memoizationRoots + DIKey[QdrantPortCfg],
    )
  }

  "BeautyQ manual hybrid real Qdrant retrieval smoke" should {
    "index a document and retrieve it through the QdrantClientInputs path against real Qdrant" in {
      (
        portCfg: QdrantPortCfg,
      ) =>
        {
            val qdrantClient = new QdrantClient(portCfg.host, portCfg.port)

            val embeddingDimension = 3
            val embeddingVector = List(0.1, 0.2, 0.3)
            val uniquenessSuffix = UUID.randomUUID().toString.replace('-', '_')

            val embeddingSpec = EmbeddingSpec[VariantSearchDocument](
              vectorName = "variant-embedding",
              modelName = "fake-embedding",
              dimension = embeddingDimension,
              distance = VectorDistance.Cosine,
              sourceTextFieldPaths = Nil,
            )

            val vectorSearchSpec = leaderboard.search.dsl.VectorSearchSpec(
              collectionName = "placeholder",
              vectorName = embeddingSpec.vectorName,
              topK = 10,
              scoreThreshold = None,
            )

            val readinessConfig = QdrantCollectionReadinessConfig.derive(
              leaderboard.search.qdrant.QdrantCollectionReadinessInput(
                domainName = "beauty_variant",
                searchSpecVersion = "v1",
                purpose = s"manual-hybrid-runner-retrieval-smoke-$uniquenessSuffix",
                embeddingSpec = embeddingSpec,
                vectorSearchSpec = vectorSearchSpec,
              )
            )

            val collectionPath = s"/collections/${readinessConfig.collectionName}"

            val testVariantId = UUID.fromString("00000000-0000-0000-0000-000000000001")
            val testDocument = VariantSearchDocument(
              variantId = testVariantId,
              masterServiceOfferId = UUID.fromString("00000000-0000-0000-0000-000000000002"),
              masterLocationId = UUID.fromString("00000000-0000-0000-0000-000000000003"),
              masterId = UUID.fromString("00000000-0000-0000-0000-000000000004"),
              serviceId = UUID.fromString("00000000-0000-0000-0000-000000000005"),
              categoryId = UUID.fromString("00000000-0000-0000-0000-000000000006"),
              serviceName = "Test Service",
              categoryName = "Test Category",
              masterName = "Test Master",
              locationName = "Test Location",
              address = "Test Address",
              location = leaderboard.search.dsl.SearchGeoPoint(BigDecimal("52.5200"), BigDecimal("13.4050")),
              lat = BigDecimal("52.5200"),
              lon = BigDecimal("13.4050"),
              priceFrom = BigDecimal("30.00"),
              priceTo = BigDecimal("45.00"),
              durationMin = 60,
              enumAttributes = Map.empty,
              booleanAttributes = Map.empty,
              intAttributes = Map.empty,
              bigDecimalAttributes = Map.empty,
              allText = "test service test category",
              serviceText = "test service test category",
              attributeText = "",
              providerText = "test master test location",
              locationText = "test location test address test category",
            )

            val scriptedEmbeddingClient = new EmbeddingClient {
              override def embed(text: String): IO[QueryFailure, Vector[Double]] =
                ZIO.succeed(embeddingVector.toVector)
            }

            val nilLexicalBackend = new LexicalDocumentBackend[IO, MasterServiceOfferVariantId] {
              override def documentHits(input: UserSearchInput, intent: ParsedSearchIntent): IO[QueryFailure, List[LexicalDocumentHit[MasterServiceOfferVariantId]]] =
                ZIO.succeed(Nil)
            }

            val testDocumentLookup = new SemanticDocumentLookup[IO, MasterServiceOfferVariantId, VariantSearchDocument] {
              override def lookup(ids: List[MasterServiceOfferVariantId]): IO[QueryFailure, Map[MasterServiceOfferVariantId, VariantSearchDocument]] =
                ZIO.succeed(Map(testVariantId -> testDocument))
            }

            val snapshotProvider = new VariantSearchDocumentSnapshotProvider[IO] {
              override def loadSnapshot(): IO[QueryFailure, List[VariantSearchDocument]] =
                ZIO.succeed(List(testDocument))
            }

            val qdrantClientInputs = BeautyQNonProductionHybridRunnerQdrantClientInputs(
              lexicalBackend = nilLexicalBackend,
              readinessConfig = readinessConfig,
              qdrantClient = qdrantClient,
              snapshotProvider = snapshotProvider,
              embeddingClient = scriptedEmbeddingClient,
              documentLookup = testDocumentLookup,
              documentSpec = BeautySearchSpecV1.spec.variantDocument,
              embeddingSpec = embeddingSpec,
            )

            val handle = qdrantClientInputs.buildHandle()

            (for {
              _ <- qdrantClient.createCollection(
                collectionPath,
                QdrantJsonInterpreter.createCollectionJson(readinessConfig.vectorSearchSpec, embeddingSpec),
              )
              indexResult <- handle.indexSnapshot()
              _ <- ZIO.succeed {
                assert(indexResult.totalDocumentsLoaded == 1, s"Expected 1 document loaded, got ${indexResult.totalDocumentsLoaded}")
                assert(indexResult.totalDocumentsIndexed == 1, s"Expected 1 document indexed, got ${indexResult.totalDocumentsIndexed}")
                assert(indexResult.indexedVariantIds.contains(testVariantId), s"Expected indexed variant id $testVariantId in ${indexResult.indexedVariantIds}")
              }
              searchInput = UserSearchInput(query = "test service", userLat = None, userLon = None, limit = 10)
              intent = ParsedSearchIntent(
                originalQuery = "test service",
                normalizedTokens = List("test", "service"),
                explicitConstraints = Nil,
                softBoosts = Nil,
                remainingText = "",
              )
              result <- handle.run(searchInput, intent)
              _ <- ZIO.succeed {
                assert(result.diagnostics.lexicalHitCount == 0, s"Expected 0 lexical hits, got ${result.diagnostics.lexicalHitCount}")
                assert(result.diagnostics.semanticHitCount >= 1, s"Expected >= 1 semantic hit, got ${result.diagnostics.semanticHitCount}")
                assert(result.diagnostics.distinctVariantIdCount >= 1, s"Expected >= 1 distinct variant id, got ${result.diagnostics.distinctVariantIdCount}")
                assert(result.response.variantCarousel.exists(_.variantId == testVariantId), s"Expected variant $testVariantId in variantCarousel: ${result.response.variantCarousel.map(_.variantId)}")
              }
            } yield ()).ensuring(qdrantClient.deleteCollection(collectionPath).either.unit)
        }
    }
  }
}
