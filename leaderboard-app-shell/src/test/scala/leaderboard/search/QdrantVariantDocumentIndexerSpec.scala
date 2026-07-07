package leaderboard.search

import io.circe.Json
import leaderboard.model.QueryFailure
import leaderboard.search.document.VariantSearchDocument
import leaderboard.search.dsl.{EmbeddingSpec, SearchDocumentSpec, SearchField, SearchFieldKind, SearchGeoPoint, SearchValue, VectorDistance}
import leaderboard.search.embedding.EmbeddingClient
import leaderboard.search.interpreter.SearchEmbeddingTextExtractor
import leaderboard.search.qdrant.{QdrantPointUpsertClient, QdrantVariantDocumentIndexer, QdrantVariantDocumentPointBuilder}
import org.scalatest.wordspec.AnyWordSpec
import zio.{IO, Ref, Runtime, Unsafe, ZIO}

import java.util.UUID

final class QdrantVariantDocumentIndexerSpec extends AnyWordSpec {
  "QdrantVariantDocumentIndexer" should {
    "embed extracted document text, upsert the expected point json, and return the Qdrant response" in {
      val document = variantDocument()
      val vector = Vector(0.125, -0.25, 0.5)
      val response = Json.obj("status" -> Json.fromString("ok"))
      val textRef = runUio(Ref.make(Option.empty[String]))
      val pathRef = runUio(Ref.make(Option.empty[String]))
      val jsonRef = runUio(Ref.make(Option.empty[Json]))
      val embeddingClient = new FakeEmbeddingClient(textRef, Right(vector))
      val upsertClient = new FakeQdrantPointUpsertClient(pathRef, jsonRef, Right(response))
      val indexer = new QdrantVariantDocumentIndexer(embeddingClient, upsertClient, documentSpec, embeddingSpec)
      val expectedText = SearchEmbeddingTextExtractor.extract(documentSpec, embeddingSpec, document)
      val expectedJson = QdrantVariantDocumentPointBuilder.upsertPointJson(document, embeddingSpec.vectorName, vector.toList)

      val result = run(indexer.upsertDocument("beauty-semantic", document))

      assert(runUio(textRef.get).contains(expectedText))
      assert(runUio(pathRef.get).contains("/collections/beauty-semantic/points?wait=true"))
      assert(runUio(jsonRef.get).contains(expectedJson))
      assert(result == response)
    }

    "propagate embedding failure without upserting" in {
      val failure = QueryFailure.operation("embed-document", "embedding failed")
      val textRef = runUio(Ref.make(Option.empty[String]))
      val pathRef = runUio(Ref.make(Option.empty[String]))
      val jsonRef = runUio(Ref.make(Option.empty[Json]))
      val embeddingClient = new FakeEmbeddingClient(textRef, Left(failure))
      val upsertClient = new FakeQdrantPointUpsertClient(pathRef, jsonRef, Right(Json.obj()))
      val indexer = new QdrantVariantDocumentIndexer(embeddingClient, upsertClient, documentSpec, embeddingSpec)

      val error = runFail(indexer.upsertDocument("beauty-semantic", variantDocument()))

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
      val indexer = new QdrantVariantDocumentIndexer(embeddingClient, upsertClient, documentSpec, embeddingSpec)

      val error = runFail(indexer.upsertDocument("beauty-semantic", variantDocument()))

      assert(error == failure)
      assert(runUio(pathRef.get).contains("/collections/beauty-semantic/points?wait=true"))
      assert(runUio(jsonRef.get).nonEmpty)
    }
  }

  private val documentSpec: SearchDocumentSpec[VariantSearchDocument] =
    SearchDocumentSpec(
      indexName = "test-variant-index",
      id = _.variantId.toString,
      fields = List(
        SearchField(
          path = "serviceText",
          kind = SearchFieldKind.Text,
          extract = document => Some(SearchValue.Text(document.serviceText)),
        ),
        SearchField(
          path = "attributeText",
          kind = SearchFieldKind.Text,
          extract = document => Some(SearchValue.Text(document.attributeText)),
        ),
        SearchField(
          path = "providerText",
          kind = SearchFieldKind.Text,
          extract = document => Some(SearchValue.Text(document.providerText)),
        ),
      ),
    )

  private val embeddingSpec: EmbeddingSpec[VariantSearchDocument] =
    EmbeddingSpec(
      vectorName = "variant-semantic-vector",
      modelName = "fake-embedding",
      dimension = 3,
      distance = VectorDistance.Cosine,
      sourceTextFields = documentSpec.fields,
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

  private def variantDocument(): VariantSearchDocument =
    VariantSearchDocument(
      variantId = UUID.fromString("00000000-0000-0000-0000-000000000101"),
      masterServiceOfferId = UUID.fromString("00000000-0000-0000-0000-000000000202"),
      masterLocationId = UUID.fromString("00000000-0000-0000-0000-000000000303"),
      masterId = UUID.fromString("00000000-0000-0000-0000-000000000404"),
      serviceId = UUID.fromString("00000000-0000-0000-0000-000000000505"),
      categoryId = UUID.fromString("00000000-0000-0000-0000-000000000606"),
      serviceName = "Manicure",
      categoryName = "Nails",
      masterName = "Beauty Master",
      locationName = "Central Studio",
      address = "Main street 1",
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
      allText = "manicure nails beauty master central studio",
      serviceText = "manicure nails",
      attributeText = "coverage gel with removal",
      providerText = "beauty master central studio",
      locationText = "central studio main street 1 nails",
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
