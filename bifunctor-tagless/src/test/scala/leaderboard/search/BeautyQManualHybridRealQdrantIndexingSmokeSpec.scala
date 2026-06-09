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

final class BeautyQManualHybridRealQdrantIndexingSmokeSpec extends LeaderboardTest with ProdTest {
  override def config = super.config.copy(
    activation = super.config.activation ++ Activation(Mode -> Mode.Test),
    memoizationRoots = super.config.memoizationRoots + DIKey[QdrantPortCfg],
  )

  "BeautyQ manual hybrid real Qdrant indexing smoke" should {
    "index a variant document through the QdrantClientInputs path against real Qdrant" in {
      (
        portCfg: QdrantPortCfg,
      ) =>
        sys.env.get(BeautyQManualHybridRealQdrantIndexingSmokeSpec.EnvGate) match {
          case Some("true") =>
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
                purpose = s"manual-hybrid-runner-indexing-smoke-$uniquenessSuffix",
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

            val failFastLexicalBackend = new LexicalDocumentBackend[IO, MasterServiceOfferVariantId] {
              override def documentHits(input: UserSearchInput, intent: ParsedSearchIntent): IO[QueryFailure, List[LexicalDocumentHit[MasterServiceOfferVariantId]]] =
                ZIO.fail(QueryFailure.operation("fail-fast-lexical", s"Lexical backend must not be called for $input: $intent"))
            }

            val failFastDocumentLookup = new SemanticDocumentLookup[IO, MasterServiceOfferVariantId, VariantSearchDocument] {
              override def lookup(ids: List[MasterServiceOfferVariantId]): IO[QueryFailure, Map[MasterServiceOfferVariantId, VariantSearchDocument]] =
                ZIO.fail(QueryFailure.operation("fail-fast-document-lookup", s"Document lookup must not be called for $ids"))
            }

            val snapshotProvider = new VariantSearchDocumentSnapshotProvider[IO] {
              override def loadSnapshot(): IO[QueryFailure, List[VariantSearchDocument]] =
                ZIO.succeed(List(testDocument))
            }

            val qdrantClientInputs = BeautyQNonProductionHybridRunnerQdrantClientInputs(
              lexicalBackend = failFastLexicalBackend,
              readinessConfig = readinessConfig,
              qdrantClient = qdrantClient,
              snapshotProvider = snapshotProvider,
              embeddingClient = scriptedEmbeddingClient,
              documentLookup = failFastDocumentLookup,
              documentSpec = BeautySearchSpecV1.spec.variantDocument,
              embeddingSpec = embeddingSpec,
            )

            val handle = qdrantClientInputs.buildHandle()

            (for {
              _ <- qdrantClient.createCollection(
                collectionPath,
                QdrantJsonInterpreter.createCollectionJson(readinessConfig.vectorSearchSpec, embeddingSpec),
              )
              result <- handle.indexSnapshot()
              _ <- ZIO.succeed {
                assert(result.totalDocumentsLoaded == 1, s"Expected 1 document loaded, got ${result.totalDocumentsLoaded}")
                assert(result.totalDocumentsIndexed == 1, s"Expected 1 document indexed, got ${result.totalDocumentsIndexed}")
                assert(result.indexedVariantIds.contains(testVariantId), s"Expected indexed variant id $testVariantId in ${result.indexedVariantIds}")
              }
            } yield ()).ensuring(qdrantClient.deleteCollection(collectionPath).either.unit)
          case _ =>
            cancel(s"Set ${BeautyQManualHybridRealQdrantIndexingSmokeSpec.EnvGate}=true to run the manual hybrid real Qdrant indexing smoke")
        }
    }
  }
}

private object BeautyQManualHybridRealQdrantIndexingSmokeSpec {
  val EnvGate = "BEAUTYQ_MANUAL_HYBRID_REAL_QDRANT_INDEXING_SMOKE"
}
