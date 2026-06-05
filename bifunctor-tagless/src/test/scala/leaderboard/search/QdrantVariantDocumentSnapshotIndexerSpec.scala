package leaderboard.search

import io.circe.Json
import leaderboard.model.QueryFailure
import leaderboard.search.document.{VariantSearchDocument, VariantSearchDocumentSnapshotProvider}
import leaderboard.search.dsl.SearchGeoPoint
import leaderboard.search.qdrant.{QdrantSnapshotIndexingResult, QdrantVariantDocumentSnapshotIndexer, QdrantVariantDocumentUpsert}
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

    "propagate snapshot provider failure without indexing" in {
      val failure = QueryFailure.operation("load-snapshot", "snapshot failed")
      val callsRef = runUio(Ref.make(List.empty[(String, UUID)]))
      val indexer = new QdrantVariantDocumentSnapshotIndexer(
        new FakeSnapshotProvider(Left(failure)),
        new FakeDocumentIndexer(callsRef, Right(Json.obj())),
      )

      val error = runFail(indexer.indexSnapshot("beauty-semantic"))

      assert(error == failure)
      assert(runUio(callsRef.get).isEmpty)
    }

    "propagate first indexing failure" in {
      val documents = List(variantDocument(1), variantDocument(2), variantDocument(3))
      val failure = QueryFailure.operation("index-document", "indexing failed")
      val callsRef = runUio(Ref.make(List.empty[(String, UUID)]))
      val indexer = new QdrantVariantDocumentSnapshotIndexer(
        new FakeSnapshotProvider(Right(documents)),
        new FailingOnVariantDocumentIndexer(callsRef, documents(1).variantId, failure),
      )

      val error = runFail(indexer.indexSnapshot("beauty-semantic"))

      assert(error == failure)
      assert(runUio(callsRef.get) == documents.take(2).map(document => "beauty-semantic" -> document.variantId))
    }
  }

  private final class FakeSnapshotProvider(
    result: Either[QueryFailure, List[VariantSearchDocument]]
  ) extends VariantSearchDocumentSnapshotProvider[IO] {
    override def loadSnapshot(): IO[QueryFailure, List[VariantSearchDocument]] =
      ZIO.fromEither(result)
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
