package leaderboard.search.gen2.qdrant

import org.scalatest.wordspec.AnyWordSpec

final class QdrantGenerationCompilerSpec extends AnyWordSpec {
  private def embeddingResults(prepared: QdrantPreparedGeneration): Either[QdrantEmbeddingError, Vector[QdrantEmbeddingResult]] =
    prepared.points.zipWithIndex.foldLeft[Either[QdrantEmbeddingError, Vector[QdrantEmbeddingResult]]](Right(Vector.empty)) {
      case (acc, (point, index)) =>
        acc.flatMap(done => QdrantEmbeddingResult.from(point.embeddingInput, Vector(index.toDouble + 0.1, index.toDouble + 0.2, index.toDouble + 0.3)).map(done :+ _))
    }

  "QdrantGenerationCompiler" should {
    "prepare documents in canonical point-id order and complete a deterministic artifact" in {
      QdrantGenerationCompiler.prepare(QdrantTestFixtures.policy, QdrantTestFixtures.materialized) match {
        case Left(error) => fail(s"expected preparation, got $error")
        case Right(prepared) =>
          assert(prepared.points.map(point => QdrantPointId.canonical(point.id)) == Vector("uuid:00000000-0000-0000-0000-000000000001", "uuid:00000000-0000-0000-0000-000000000002"))
          embeddingResults(prepared) match {
            case Left(error) => fail(s"expected embedding results, got $error")
            case Right(results) =>
              QdrantGenerationCompiler.complete(prepared, results, "beautyq_variants_") match {
                case Left(error) => fail(s"expected completed generation, got $error")
                case Right(generation) =>
                  assert(generation.physicalCollectionName.startsWith("beautyq_variants_"))
                  assert(generation.metadata.identity == generation.identity)
                  assert(generation.collectionJson.hcursor.downField("metadata").downField("search_gen2").downField("generation_id").as[String] == Right(generation.metadata.generationId))
                  assert(generation.payloadIndexRequests.map(_.hcursor.downField("field_name").as[String]).collect { case Right(value) => value } == Vector("count", "from", "to", "active", "at", "location"))
                  QdrantGenerationCompiler.complete(prepared, results, "beautyq_variants_") match {
                    case Right(repeated) => assert(repeated.physicalCollectionName == generation.physicalCollectionName)
                    case Left(error)     => fail(s"expected deterministic repeat, got $error")
                  }
                  val firstInput = prepared.points.headOption.map(_.embeddingInput).getOrElse(fail("expected a first prepared point"))
                  val altered = results.updated(0, QdrantEmbeddingResult.from(firstInput, Vector(0.9, 0.8, 0.7)).getOrElse(fail("expected altered embedding")))
                  QdrantGenerationCompiler.complete(prepared, altered, "beautyq_variants_") match {
                    case Right(changed) => assert(changed.physicalCollectionName != generation.physicalCollectionName)
                    case Left(error)     => fail(s"expected changed generation, got $error")
                  }
              }
          }
      }
    }

    "reject an embedding paired with another prepared input" in {
      QdrantGenerationCompiler.prepare(QdrantTestFixtures.policy, QdrantTestFixtures.materialized) match {
        case Left(error) => fail(s"expected preparation, got $error")
        case Right(prepared) =>
          val reversed = prepared.points.reverse
          val embeddings = reversed.map(point => QdrantEmbeddingResult.from(point.embeddingInput, Vector(0.1, 0.2, 0.3)).getOrElse(fail("expected valid embedding")))
          QdrantGenerationCompiler.complete(prepared, embeddings, "neutral_") match {
            case Left(QdrantGenerationCompileError.EmbeddingMismatch(_, QdrantEmbeddingError.InputMismatch(_, _))) => succeed
            case Left(error) => fail(s"expected input mismatch, got $error")
            case Right(value) => fail(s"expected rejection, got $value")
          }
      }
    }
  }
}
