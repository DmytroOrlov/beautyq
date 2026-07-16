package leaderboard.search.gen2.elasticsearch

import io.circe.Json
import org.scalatest.wordspec.AnyWordSpec
import leaderboard.search.gen2.core.materialization.*
import leaderboard.search.gen2.core.plan.ContractFingerprint.value

import java.time.Instant

final class ElasticsearchGenerationMetadataSpec extends AnyWordSpec {
  private val identity = ElasticsearchPersistedGenerationIdentity("source", "projected", "contract", "projection-v1", "compiler-v1", "index-v1")
  private val metadata = ElasticsearchGenerationMetadata(
    ElasticsearchGenerationMetadataSchemaVersion.Current,
    ElasticsearchGenerationNaming.generationId(identity),
    Instant.parse("2026-01-01T00:00:00Z"),
    3L,
    identity,
  )

  "ElasticsearchGenerationMetadataCodec" should {
    "round-trip the exact flat persisted contract" in {
      assert(ElasticsearchGenerationMetadataCodec.decode(ElasticsearchGenerationMetadataCodec.encode(metadata)) == Right(metadata))
    }

    "reject extra fields, non-canonical timestamps and negative counts" in {
      val encoded = ElasticsearchGenerationMetadataCodec.encode(metadata).asObject.getOrElse(fail("expected object"))
      assert(ElasticsearchGenerationMetadataCodec.decode(Json.fromJsonObject(encoded.add("extra", Json.True))).isLeft)
      assert(ElasticsearchGenerationMetadataCodec.decode(Json.fromJsonObject(encoded.add("builtAt", Json.fromString("2026-01-01T00:00:00.000Z")))).isLeft)
      assert(ElasticsearchGenerationMetadataCodec.decode(Json.fromJsonObject(encoded.add("documentCount", Json.fromLong(-1L)))).isLeft)
    }

    "reject non-objects, missing/wrong fields, unsupported schema, invalid IDs and timestamps" in {
      val encoded = ElasticsearchGenerationMetadataCodec.encode(metadata).asObject.getOrElse(fail("expected object"))
      assert(ElasticsearchGenerationMetadataCodec.decode(Json.arr()).isLeft)
      assert(ElasticsearchGenerationMetadataCodec.decode(Json.fromJsonObject(encoded.remove("contractFingerprint"))).isLeft)
      assert(ElasticsearchGenerationMetadataCodec.decode(Json.fromJsonObject(encoded.add("documentCount", Json.fromString("3")))).isLeft)
      assert(ElasticsearchGenerationMetadataCodec.decode(Json.fromJsonObject(encoded.add("schemaVersion", Json.fromString("future")))).isLeft)
      assert(ElasticsearchGenerationMetadataCodec.decode(Json.fromJsonObject(encoded.add("generationId", Json.fromString("not-a-hash")))).isLeft)
      assert(ElasticsearchGenerationMetadataCodec.decode(Json.fromJsonObject(encoded.add("builtAt", Json.fromString("not-an-instant")))).isLeft)
    }

    "convert trusted identity only into raw persisted strings" in {
      import ElasticsearchTestFixtures.fullPolicy
      val trusted = ElasticsearchGenerationIdentity(
        ContentFingerprint("source"),
        ProjectedDocumentsFingerprint("projected"),
        fullPolicy.contractFingerprint,
        ProjectionFormatVersion("projection-v1"),
        fullPolicy.index.compilerVersion,
        fullPolicy.index.indexFormatVersion,
      )
      val persisted = ElasticsearchPersistedGenerationIdentity.fromTrusted(trusted)
      assert(persisted.contractFingerprint == trusted.contractFingerprint.value)
      assertDoesNotCompile("leaderboard.search.gen2.core.plan.ContractFingerprint(\"forged\")")
    }
  }
}
