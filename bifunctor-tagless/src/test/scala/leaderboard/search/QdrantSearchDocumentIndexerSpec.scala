package leaderboard.search

import io.circe.Json
import leaderboard.model.QueryFailure
import leaderboard.search.dsl.{EmbeddingSpec, SearchDocumentSpec, SearchField, SearchFieldKind, SearchValue, VectorDistance}
import leaderboard.search.embedding.EmbeddingClient
import leaderboard.search.qdrant.{QdrantDocumentPointBuilder, QdrantPointId, QdrantPointUpsertClient, QdrantSearchDocumentIndexer}
import org.scalatest.wordspec.AnyWordSpec
import zio.{IO, Ref, Runtime, Unsafe, ZIO}

final class QdrantSearchDocumentIndexerSpec extends AnyWordSpec {
  "QdrantSearchDocumentIndexer" should {
    "extract generic document text, embed it, build generic point json, and upsert through the Qdrant points endpoint" in {
      val document = TestSearchDocument(
        id = "00000000-0000-0000-0000-000000000123",
        title = "  Generic search title  ",
        body = "Reusable semantic body",
      )
      val vector = Vector(0.125, -0.25, 0.5)
      val response = Json.obj("status" -> Json.fromString("ok"))
      val textRef = runUio(Ref.make(Option.empty[String]))
      val pathRef = runUio(Ref.make(Option.empty[String]))
      val jsonRef = runUio(Ref.make(Option.empty[Json]))
      val embeddingClient = new FakeEmbeddingClient(textRef, Right(vector))
      val upsertClient = new FakeQdrantPointUpsertClient(pathRef, jsonRef, Right(response))
      val indexer = new QdrantSearchDocumentIndexer[TestSearchDocument](
        embeddingClient,
        upsertClient,
        documentSpec,
        embeddingSpec,
        TestSearchDocumentPointBuilder,
      )

      val result = run(indexer.upsertDocument("generic-semantic", document))
      val point = runUio(jsonRef.get).getOrElse(sys.error("expected point json")).hcursor.downField("points").downArray
      val payload = point.downField("payload")

      assert(runUio(textRef.get).contains("Generic search title Reusable semantic body"))
      assert(runUio(pathRef.get).contains("/collections/generic-semantic/points?wait=true"))
      assert(point.downField("id").as[String] == Right("00000000-0000-0000-0000-000000000123"))
      assert(point.downField("vector").downField("generic-semantic-vector").as[List[Double]] == Right(vector.toList))
      assert(payload.downField("title").as[String] == Right("Generic search title"))
      assert(payload.downField("bodyLength").as[Int] == Right(document.body.length))
      assert(result == response)
    }

    "render unsigned integer point ids as Qdrant JSON numbers" in {
      val document = testDocument.copy(id = "42")
      val vector = Vector(0.125, -0.25, 0.5)
      val pathRef = runUio(Ref.make(Option.empty[String]))
      val jsonRef = runUio(Ref.make(Option.empty[Json]))
      val embeddingClient = new FakeEmbeddingClient(runUio(Ref.make(Option.empty[String])), Right(vector))
      val upsertClient = new FakeQdrantPointUpsertClient(pathRef, jsonRef, Right(Json.obj()))
      val indexer = new QdrantSearchDocumentIndexer[TestSearchDocument](
        embeddingClient,
        upsertClient,
        documentSpec,
        embeddingSpec,
        TestUnsignedLongPointBuilder,
      )

      run(indexer.upsertDocument("generic-semantic", document))

      val point = runUio(jsonRef.get).getOrElse(sys.error("expected point json")).hcursor.downField("points").downArray
      assert(point.downField("id").as[Long] == Right(42L))
    }

    "reject invalid generic point ids before embedding or upserting" in {
      val textRef = runUio(Ref.make(Option.empty[String]))
      val pathRef = runUio(Ref.make(Option.empty[String]))
      val jsonRef = runUio(Ref.make(Option.empty[Json]))
      val embeddingClient = new FakeEmbeddingClient(textRef, Right(Vector(1.0, 0.0)))
      val upsertClient = new FakeQdrantPointUpsertClient(pathRef, jsonRef, Right(Json.obj()))
      val indexer = new QdrantSearchDocumentIndexer[TestSearchDocument](
        embeddingClient,
        upsertClient,
        documentSpec,
        embeddingSpec,
        TestSearchDocumentPointBuilder,
      )

      val error = runFail(indexer.upsertDocument("generic-semantic", testDocument.copy(id = "face-care")))

      assert(error.message.contains("Qdrant point id must be a UUID string or unsigned integer"))
      assert(runUio(textRef.get).isEmpty)
      assert(runUio(pathRef.get).isEmpty)
      assert(runUio(jsonRef.get).isEmpty)
    }

    "propagate embedding failure without upserting" in {
      val failure = QueryFailure.operation("embed-document", "embedding failed")
      val textRef = runUio(Ref.make(Option.empty[String]))
      val pathRef = runUio(Ref.make(Option.empty[String]))
      val jsonRef = runUio(Ref.make(Option.empty[Json]))
      val embeddingClient = new FakeEmbeddingClient(textRef, Left(failure))
      val upsertClient = new FakeQdrantPointUpsertClient(pathRef, jsonRef, Right(Json.obj()))
      val indexer = new QdrantSearchDocumentIndexer[TestSearchDocument](
        embeddingClient,
        upsertClient,
        documentSpec,
        embeddingSpec,
        TestSearchDocumentPointBuilder,
      )

      val error = runFail(indexer.upsertDocument("generic-semantic", testDocument))

      assert(error == failure)
      assert(runUio(textRef.get).nonEmpty)
      assert(runUio(pathRef.get).isEmpty)
      assert(runUio(jsonRef.get).isEmpty)
    }

    "propagate upsert failure" in {
      val failure = QueryFailure.operation("upsert-qdrant-point", "qdrant failed")
      val textRef = runUio(Ref.make(Option.empty[String]))
      val pathRef = runUio(Ref.make(Option.empty[String]))
      val jsonRef = runUio(Ref.make(Option.empty[Json]))
      val embeddingClient = new FakeEmbeddingClient(textRef, Right(Vector(1.0, 0.0)))
      val upsertClient = new FakeQdrantPointUpsertClient(pathRef, jsonRef, Left(failure))
      val indexer = new QdrantSearchDocumentIndexer[TestSearchDocument](
        embeddingClient,
        upsertClient,
        documentSpec,
        embeddingSpec,
        TestSearchDocumentPointBuilder,
      )

      val error = runFail(indexer.upsertDocument("generic-semantic", testDocument))

      assert(error == failure)
      assert(runUio(pathRef.get).contains("/collections/generic-semantic/points?wait=true"))
      assert(runUio(jsonRef.get).nonEmpty)
    }
  }

  private final case class TestSearchDocument(
    id: String,
    title: String,
    body: String,
  )

  private object TestSearchDocumentPointBuilder extends QdrantDocumentPointBuilder[TestSearchDocument] {
    override def qdrantPointId(document: TestSearchDocument): Either[QueryFailure, QdrantPointId] =
      QdrantPointId.fromUuidString(document.id)

    override def payload(document: TestSearchDocument): Map[String, Json] =
      Map(
        "title" -> Json.fromString(document.title.trim),
        "bodyLength" -> Json.fromInt(document.body.length),
      )
  }

  private object TestUnsignedLongPointBuilder extends QdrantDocumentPointBuilder[TestSearchDocument] {
    override def qdrantPointId(document: TestSearchDocument): Either[QueryFailure, QdrantPointId] =
      document.id.toLongOption
        .toRight(QueryFailure.operation("qdrant-point-id", s"Expected unsigned integer point id, got '${document.id}'"))
        .flatMap(QdrantPointId.fromUnsignedLong)

    override def payload(document: TestSearchDocument): Map[String, Json] =
      Map(
        "title" -> Json.fromString(document.title.trim),
        "bodyLength" -> Json.fromInt(document.body.length),
      )
  }

  private val documentSpec: SearchDocumentSpec[TestSearchDocument] =
    SearchDocumentSpec(
      indexName = "test-generic-index",
      id = _.id,
      fields = List(
        SearchField(
          path = "title",
          kind = SearchFieldKind.Text,
          extract = document => Some(SearchValue.Text(document.title)),
        ),
        SearchField(
          path = "body",
          kind = SearchFieldKind.Text,
          extract = document => Some(SearchValue.Text(document.body)),
        ),
      ),
    )

  private val embeddingSpec: EmbeddingSpec[TestSearchDocument] =
    EmbeddingSpec(
      vectorName = "generic-semantic-vector",
      modelName = "fake-embedding",
      dimension = 3,
      distance = VectorDistance.Cosine,
      sourceTextFields = documentSpec.fields,
    )

  private val testDocument: TestSearchDocument =
    TestSearchDocument(
      id = "00000000-0000-0000-0000-000000000123",
      title = "Generic search title",
      body = "Reusable semantic body",
    )

  private final class FakeEmbeddingClient(
    textRef: Ref[Option[String]],
    result: Either[QueryFailure, Vector[Double]],
  ) extends EmbeddingClient {
    override def embed(text: String): IO[QueryFailure, Vector[Double]] =
      textRef.set(Some(text)) *> ZIO.fromEither(result)
  }

  private final class FakeQdrantPointUpsertClient(
    pathRef: Ref[Option[String]],
    jsonRef: Ref[Option[Json]],
    result: Either[QueryFailure, Json],
  ) extends QdrantPointUpsertClient {
    override def upsertPoint(path: String, json: Json): IO[QueryFailure, Json] =
      pathRef.set(Some(path)) *> jsonRef.set(Some(json)) *> ZIO.fromEither(result)
  }

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
