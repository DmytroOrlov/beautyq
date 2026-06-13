package leaderboard.search

import distage.{DIKey, Mode}
import io.circe.Json
import izumi.distage.model.definition.Activation
import leaderboard.{LeaderboardTest, ProdTest}
import leaderboard.config.QdrantPortCfg
import leaderboard.model.{MasterServiceOfferVariantId, QueryFailure}
import leaderboard.search.document.{VariantSearchDocument, VariantSearchDocumentSnapshotProvider}
import leaderboard.search.dsl.{EmbeddingSpec, SearchGeoPoint, VectorDistance, VectorSearchSpec}
import leaderboard.search.qdrant.{
  QdrantClient,
  QdrantClientCollectionInfoAdapter,
  QdrantCollectionCompatibilityChecker,
  QdrantCollectionCompatibilityGuard,
  QdrantCollectionIdentity,
  QdrantJsonInterpreter,
  QdrantSnapshotIndexingCompatibilityGuard,
  QdrantSnapshotIndexingResult,
  QdrantVariantDocumentSnapshotIndexer,
  QdrantVariantDocumentUpsert,
}
import zio.{IO, Ref, ZIO}

import java.util.UUID

final class QdrantSnapshotIndexingCompatibilityIntegrationSpec extends LeaderboardTest with ProdTest {
  override def config = super.config.copy(
    activation = super.config.activation ++ Activation(Mode -> Mode.Test),
    memoizationRoots = super.config.memoizationRoots + DIKey[QdrantPortCfg],
  )

  "Qdrant snapshot indexing compatibility integration" should {
    "index through a real collection compatibility guard without real document upserts" in {
      (
        portCfg: QdrantPortCfg,
      ) =>
        val qdrantClient = new QdrantClient(portCfg.host, portCfg.port)
        val collectionName = s"snapshot_indexing_compat_${UUID.randomUUID().toString.replace('-', '_')}"
        val collectionPath = s"/collections/$collectionName"
        val vectorSearchSpec = VectorSearchSpec(
          collectionName = collectionName,
          vectorName = "compat-vector",
          topK = 10,
          scoreThreshold = None,
        )
        val embeddingSpec = EmbeddingSpec[Any](
          vectorName = vectorSearchSpec.vectorName,
          modelName = "compat-model",
          dimension = 3,
          distance = VectorDistance.Cosine,
          sourceTextFieldPaths = Nil,
        )
        val expectation = QdrantCollectionIdentity.compatibilityExpectation(embeddingSpec, vectorSearchSpec)
        val guard = new QdrantCollectionCompatibilityGuard(
          new QdrantCollectionCompatibilityChecker(new QdrantClientCollectionInfoAdapter(qdrantClient))
        )
        val documents = List(variantDocument(1), variantDocument(2))

        (for {
          snapshotLoadsRef <- Ref.make(0)
          upsertCallsRef <- Ref.make(List.empty[(String, MasterServiceOfferVariantId)])
          indexer = new QdrantVariantDocumentSnapshotIndexer(
            new FakeSnapshotProvider(documents, snapshotLoadsRef),
            new RecordingDocumentUpsert(upsertCallsRef),
          )
          _ <- qdrantClient.createCollection(
            collectionPath,
            QdrantJsonInterpreter.createCollectionJson(vectorSearchSpec, embeddingSpec),
          )
          result <- indexer.indexCompatibleSnapshot(QdrantSnapshotIndexingCompatibilityGuard(expectation, guard))
          upsertCalls <- upsertCallsRef.get
          snapshotLoads <- snapshotLoadsRef.get
          _ <- ZIO.succeed {
            assert(result == QdrantSnapshotIndexingResult(
              totalDocumentsLoaded = 2,
              totalDocumentsIndexed = 2,
              indexedVariantIds = documents.map(_.variantId),
            ))
            assert(snapshotLoads == 1)
            assert(upsertCalls == documents.map(document => expectation.collectionName -> document.variantId))
            assert(upsertCalls.map(_._1).distinct == List(expectation.collectionName))
          }
        } yield ()).ensuring(qdrantClient.deleteCollection(collectionPath).either.unit)
    }

    "fail before snapshot loading and upserts when the real guard sees a dimension mismatch" in {
      (
        portCfg: QdrantPortCfg,
      ) =>
        val qdrantClient = new QdrantClient(portCfg.host, portCfg.port)
        val collectionName = s"snapshot_indexing_compat_${UUID.randomUUID().toString.replace('-', '_')}"
        val collectionPath = s"/collections/$collectionName"
        val vectorSearchSpec = VectorSearchSpec(
          collectionName = collectionName,
          vectorName = "compat-vector",
          topK = 10,
          scoreThreshold = None,
        )
        val embeddingSpec = EmbeddingSpec[Any](
          vectorName = vectorSearchSpec.vectorName,
          modelName = "compat-model",
          dimension = 3,
          distance = VectorDistance.Cosine,
          sourceTextFieldPaths = Nil,
        )
        val expectation = QdrantCollectionIdentity
          .compatibilityExpectation(embeddingSpec, vectorSearchSpec)
          .copy(expectedDimension = embeddingSpec.dimension + 1)
        val guard = new QdrantCollectionCompatibilityGuard(
          new QdrantCollectionCompatibilityChecker(new QdrantClientCollectionInfoAdapter(qdrantClient))
        )

        (for {
          snapshotLoadsRef <- Ref.make(0)
          upsertCallsRef <- Ref.make(List.empty[(String, MasterServiceOfferVariantId)])
          indexer = new QdrantVariantDocumentSnapshotIndexer(
            new FakeSnapshotProvider(List(variantDocument(1)), snapshotLoadsRef),
            new RecordingDocumentUpsert(upsertCallsRef),
          )
          _ <- qdrantClient.createCollection(
            collectionPath,
            QdrantJsonInterpreter.createCollectionJson(vectorSearchSpec, embeddingSpec),
          )
          error <- indexer.indexCompatibleSnapshot(QdrantSnapshotIndexingCompatibilityGuard(expectation, guard)).either
          snapshotLoads <- snapshotLoadsRef.get
          upsertCalls <- upsertCallsRef.get
          _ <- ZIO.succeed {
            error match {
              case Left(QueryFailure.OperationFailure("qdrant-collection-compatibility", message)) =>
                assert(message.contains("DimensionMismatch(expected=4, observed=3)"))
              case other =>
                fail(s"Expected qdrant-collection-compatibility failure, got $other")
            }
            assert(snapshotLoads == 0)
            assert(upsertCalls.isEmpty)
          }
        } yield ()).ensuring(qdrantClient.deleteCollection(collectionPath).either.unit)
    }
  }

  private final class FakeSnapshotProvider(
    documents: List[VariantSearchDocument],
    loadsRef: Ref[Int],
  ) extends VariantSearchDocumentSnapshotProvider[IO] {
    override def loadSnapshot(): IO[QueryFailure, List[VariantSearchDocument]] =
      loadsRef.update(_ + 1).as(documents)
  }

  private final class RecordingDocumentUpsert(
    callsRef: Ref[List[(String, MasterServiceOfferVariantId)]]
  ) extends QdrantVariantDocumentUpsert {
    override def upsertDocument(collectionName: String, document: VariantSearchDocument): IO[QueryFailure, Json] =
      callsRef.update(_ :+ (collectionName -> document.variantId)).as(Json.obj())
  }

  private def variantDocument(index: Int): VariantSearchDocument =
    VariantSearchDocument(
      variantId = uuid(index, 1),
      masterServiceOfferId = uuid(index, 2),
      masterLocationId = uuid(index, 3),
      masterId = uuid(index, 4),
      serviceId = uuid(index, 5),
      categoryId = uuid(index, 6),
      serviceName = s"Service $index",
      categoryName = "Category",
      masterName = s"Master $index",
      locationName = s"Location $index",
      address = s"Address $index",
      location = SearchGeoPoint(lat = BigDecimal("52.5200"), lon = BigDecimal("13.4050")),
      lat = BigDecimal("52.5200"),
      lon = BigDecimal("13.4050"),
      priceFrom = BigDecimal("25.00"),
      priceTo = BigDecimal("40.00"),
      durationMin = 45,
      enumAttributes = Map("coverage" -> "gel"),
      booleanAttributes = Map("with_removal" -> true),
      intAttributes = Map("nails_count" -> 10),
      bigDecimalAttributes = Map("rating" -> BigDecimal("4.8")),
      allText = s"service $index category master location",
      serviceText = s"service $index category",
      attributeText = "coverage gel with removal",
      providerText = s"master $index location $index",
      locationText = s"location $index address $index category",
    )

  private def uuid(index: Int, suffix: Int): UUID =
    UUID.fromString(f"00000000-0000-0000-0000-${index * 100 + suffix}%012d")
}
