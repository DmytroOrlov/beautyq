package leaderboard.search.gen2.qdrant

import io.circe.Json
import org.scalatest.wordspec.AnyWordSpec

final class QdrantGenerationMetadataCodecSpec extends AnyWordSpec {
  "QdrantGenerationMetadataCodec" should {
    "round-trip the compiler-owned metadata shape" in {
      val generation = compiledGeneration
      val encoded = QdrantGenerationMetadataCodec.encode(generation.metadata)
      assert(QdrantGenerationMetadataCodec.decode(encoded) == Right(generation.metadata))
    }

    "reject missing, unsupported and malformed metadata" in {
      val generation = compiledGeneration
      val encoded = QdrantGenerationMetadataCodec.encode(generation.metadata)
      assert(QdrantGenerationMetadataCodec.decode(Json.obj()).isLeft)
      assert(QdrantGenerationMetadataCodec.decode(encoded.deepMerge(Json.obj("schema_version" -> Json.fromString("future")))).isLeft)
      assert(QdrantGenerationMetadataCodec.decode(encoded.deepMerge(Json.obj("point_count" -> Json.fromString("many")))).isLeft)
    }
  }

  private def compiledGeneration: QdrantCompiledGeneration = {
    val prepared = QdrantGenerationCompiler.prepare(QdrantTestFixtures.policy, QdrantTestFixtures.materialized).getOrElse(fail("expected prepared generation"))
    val embeddings = prepared.points.map(point => QdrantEmbeddingResult.from(point.embeddingInput, Vector(0.1, 0.2, 0.3)).getOrElse(fail("expected embedding")))
    QdrantGenerationCompiler.complete(prepared, embeddings, "neutral_").getOrElse(fail("expected compiled generation"))
  }
}
