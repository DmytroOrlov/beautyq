package leaderboard.search

import io.circe.Json
import io.circe.syntax.*
import leaderboard.model.QueryFailure
import leaderboard.search.document.{VariantSearchDocument, VariantSearchDocumentSnapshotProvider}
import leaderboard.search.dsl.{SearchGeoPoint, VectorDistance}
import leaderboard.search.qdrant.{
  QdrantCollectionCompatibilityChecker,
  QdrantCollectionCompatibilityExpectation,
  QdrantCollectionCompatibilityGuard,
  QdrantCollectionInfoClient,
  QdrantSnapshotIndexingResult,
  QdrantVariantDocumentSnapshotIndexer,
  QdrantVariantDocumentUpsert,
}
import org.scalatest.wordspec.AnyWordSpec
import zio.{IO, Ref, Runtime, Unsafe, ZIO}

import java.util.UUID

final class QdrantVariantDocumentSnapshotIndexerSpec extends AnyWordSpec {
  "QdrantVariantDocumentSnapshotIndexer" should {
    "load snapshot from provider and index every document in input order" in {
      val documents = List(variantDocument(1), variantDocument(2), variantDocument(3))
      val callsRef = runUio(Ref.make(List.empty[(String, UUID)]))
      val provider = new FakeSnapshotProvider(Right(documents))
      val documentIndexer = new FakeDocumentIndexer(callsRef, Right(Json.obj()))
      val indexer = new QdrantVariantDocumentSnapshotIndexer(provider, documentIndexer)

      val result = run(indexer.indexSnapshot("beauty-semantic"))

      assert(runUio(callsRef.get) == documents.map(document => "beauty-semantic" -> document.variantId))
      assert(result.indexedVariantIds == documents.map(_.variantId))
    }

    "report loaded and indexed counts" in {
      val documents = List(variantDocument(1), variantDocument(2))
      val callsRef = runUio(Ref.make(List.empty[(String, UUID)]))
      val indexer = new QdrantVariantDocumentSnapshotIndexer(
        new FakeSnapshotProvider(Right(documents)),
        new FakeDocumentIndexer(callsRef, Right(Json.obj())),
      )

      val result = run(indexer.indexSnapshot("beauty-semantic"))

      assert(result == QdrantSnapshotIndexingResult(
        totalDocumentsLoaded = 2,
        totalDocumentsIndexed = 2,
        indexedVariantIds = documents.map(_.variantId),
      ))
    }

    "report zero for empty snapshot" in {
      val callsRef = runUio(Ref.make(List.empty[(String, UUID)]))
      val indexer = new QdrantVariantDocumentSnapshotIndexer(
        new FakeSnapshotProvider(Right(Nil)),
        new FakeDocumentIndexer(callsRef, Right(Json.obj())),
      )

      val result = run(indexer.indexSnapshot("beauty-semantic"))

      assert(result == QdrantSnapshotIndexingResult(
        totalDocumentsLoaded = 0,
        totalDocumentsIndexed = 0,
        indexedVariantIds = Nil,
      ))
      assert(runUio(callsRef.get).isEmpty)
    }

    "index snapshot normally when compatible guard succeeds" in {
      val documents = List(variantDocument(1), variantDocument(2))
      val snapshotCallsRef = runUio(Ref.make(0))
      val indexCallsRef = runUio(Ref.make(List.empty[(String, UUID)]))
      val indexer = new QdrantVariantDocumentSnapshotIndexer(
        new FakeSnapshotProvider(Right(documents), Some(snapshotCallsRef)),
        new FakeDocumentIndexer(indexCallsRef, Right(Json.obj())),
        Some(expectation -> compatibleGuard),
      )

      val result = run(indexer.indexSnapshot(expectation.collectionName))

      assert(runUio(snapshotCallsRef.get) == 1)
      assert(runUio(indexCallsRef.get) == documents.map(document => expectation.collectionName -> document.variantId))
      assert(result == QdrantSnapshotIndexingResult(
        totalDocumentsLoaded = 2,
        totalDocumentsIndexed = 2,
        indexedVariantIds = documents.map(_.variantId),
      ))
    }

    "propagate guard QueryFailure without loading snapshot or indexing" in {
      val failure = QueryFailure.operation("get-qdrant-collection-info", "qdrant failed")
      val snapshotCallsRef = runUio(Ref.make(0))
      val indexCallsRef = runUio(Ref.make(List.empty[(String, UUID)]))
      val indexer = new QdrantVariantDocumentSnapshotIndexer(
        new FakeSnapshotProvider(Right(List(variantDocument(1))), Some(snapshotCallsRef)),
        new FakeDocumentIndexer(indexCallsRef, Right(Json.obj())),
        Some(expectation -> guard(new ConstQdrantCollectionInfoClient(Left(failure)))),
      )

      val error = runFail(indexer.indexSnapshot(expectation.collectionName))

      assert(error == failure)
      assert(runUio(snapshotCallsRef.get) == 0)
      assert(runUio(indexCallsRef.get).isEmpty)
    }

    "propagate guard mismatch QueryFailure without loading snapshot or indexing" in {
      val snapshotCallsRef = runUio(Ref.make(0))
      val indexCallsRef = runUio(Ref.make(List.empty[(String, UUID)]))
      val indexer = new QdrantVariantDocumentSnapshotIndexer(
        new FakeSnapshotProvider(Right(List(variantDocument(1))), Some(snapshotCallsRef)),
        new FakeDocumentIndexer(indexCallsRef, Right(Json.obj())),
        Some(expectation -> guard(new ConstQdrantCollectionInfoClient(Right(collectionInfoJson(dimension = 768))))),
      )

      val error = runFail(indexer.indexSnapshot(expectation.collectionName))

      error match {
        case QueryFailure.OperationFailure("qdrant-collection-compatibility", message) =>
          assert(message.contains("DimensionMismatch(expected=1024, observed=768)"))
        case other =>
          fail(s"Expected qdrant-collection-compatibility failure, got $other")
      }
      assert(runUio(snapshotCallsRef.get) == 0)
      assert(runUio(indexCallsRef.get).isEmpty)
    }

    "propagate snapshot provider failure without indexing when compatible guard succeeds" in {
      val failure = QueryFailure.operation("load-snapshot", "snapshot failed")
      val callsRef = runUio(Ref.make(List.empty[(String, UUID)]))
      val indexer = new QdrantVariantDocumentSnapshotIndexer(
        new FakeSnapshotProvider(Left(failure)),
        new FakeDocumentIndexer(callsRef, Right(Json.obj())),
        Some(expectation -> compatibleGuard),
      )

      val error = runFail(indexer.indexSnapshot("beauty-semantic"))

      assert(error == failure)
      assert(runUio(callsRef.get).isEmpty)
    }

    "propagate first indexing failure when compatible guard succeeds" in {
      val documents = List(variantDocument(1), variantDocument(2), variantDocument(3))
      val failure = QueryFailure.operation("index-document", "indexing failed")
      val callsRef = runUio(Ref.make(List.empty[(String, UUID)]))
      val indexer = new QdrantVariantDocumentSnapshotIndexer(
        new FakeSnapshotProvider(Right(documents)),
        new FailingOnVariantDocumentIndexer(callsRef, documents(1).variantId, failure),
        Some(expectation -> compatibleGuard),
      )

      val error = runFail(indexer.indexSnapshot("beauty-semantic"))

      assert(error == failure)
      assert(runUio(callsRef.get) == documents.take(2).map(document => "beauty-semantic" -> document.variantId))
    }
  }

  private final class FakeSnapshotProvider(
    result: Either[QueryFailure, List[VariantSearchDocument]],
    callsRef: Option[Ref[Int]] = None,
  ) extends VariantSearchDocumentSnapshotProvider[IO] {
    override def loadSnapshot(): IO[QueryFailure, List[VariantSearchDocument]] =
      ZIO.foreachDiscard(callsRef)(_.update(_ + 1)) *> ZIO.fromEither(result)
  }

  private final class FakeDocumentIndexer(
    callsRef: Ref[List[(String, UUID)]],
    result: Either[QueryFailure, Json],
  ) extends QdrantVariantDocumentUpsert {
    override def upsertDocument(collectionName: String, document: VariantSearchDocument): IO[QueryFailure, Json] =
      callsRef.update(_ :+ (collectionName -> document.variantId)) *> ZIO.fromEither(result)
  }

  private final class FailingOnVariantDocumentIndexer(
    callsRef: Ref[List[(String, UUID)]],
    failingVariantId: UUID,
    failure: QueryFailure,
  ) extends QdrantVariantDocumentUpsert {
    override def upsertDocument(collectionName: String, document: VariantSearchDocument): IO[QueryFailure, Json] =
      callsRef.update(_ :+ (collectionName -> document.variantId)) *>
        ZIO.cond(document.variantId != failingVariantId, Json.obj(), failure)
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

  private val expectation =
    QdrantCollectionCompatibilityExpectation(
      collectionName = "beauty_variant_v1_local_llama_cpp_embedding_variant_embedding_1024_cosine",
      vectorName = "variant-embedding",
      expectedDimension = 1024,
      expectedDistance = VectorDistance.Cosine,
      embeddingModelName = "llama-cpp-embedding",
    )

  private def compatibleGuard: QdrantCollectionCompatibilityGuard =
    guard(new ConstQdrantCollectionInfoClient(Right(collectionInfoJson())))

  private def guard(client: QdrantCollectionInfoClient): QdrantCollectionCompatibilityGuard =
    new QdrantCollectionCompatibilityGuard(new QdrantCollectionCompatibilityChecker(client))

  private final class ConstQdrantCollectionInfoClient(result: Either[QueryFailure, Json]) extends QdrantCollectionInfoClient {
    override def collectionInfo(path: String): IO[QueryFailure, Json] =
      ZIO.fromEither(result)
  }

  private def collectionInfoJson(
    observedCollectionName: String = expectation.collectionName,
    observedVectorName: String = expectation.vectorName,
    dimension: Int = expectation.expectedDimension,
    distance: String = "Cosine",
    observedEmbeddingModelName: Option[String] = None,
  ): Json =
    Json.obj(
      "result" -> Json.obj(
        "name" -> observedCollectionName.asJson,
        "config" -> Json.obj(
          "params" -> Json.obj(
            "vectors" -> Json.obj(
              observedVectorName -> Json.obj(
                "size" -> dimension.asJson,
                "distance" -> distance.asJson,
              )
            )
          )
        ),
      ).deepMerge(
        observedEmbeddingModelName.fold(Json.obj()) { embeddingModelName =>
          Json.obj(
            "metadata" -> Json.obj(
              "embeddingModelName" -> embeddingModelName.asJson
            )
          )
        }
      )
    )

  private def run[A](effect: IO[QueryFailure, A]): A =
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(effect).getOrThrowFiberFailure()
    }

  private def runUio[A](effect: UIO[A]): A =
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(effect).getOrThrowFiberFailure()
    }

  private def runFail[A](effect: IO[QueryFailure, A]): QueryFailure =
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(effect.either).getOrThrowFiberFailure().swap.getOrElse(sys.error("expected failure"))
    }

  private type UIO[A] = zio.UIO[A]
}
