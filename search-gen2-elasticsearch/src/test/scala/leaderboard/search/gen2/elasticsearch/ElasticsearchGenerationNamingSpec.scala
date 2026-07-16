package leaderboard.search.gen2.elasticsearch

import org.scalatest.wordspec.AnyWordSpec

final class ElasticsearchGenerationNamingSpec extends AnyWordSpec {
  private val identity = ElasticsearchPersistedGenerationIdentity("source", "projected", "contract", "projection-v1", "compiler-v1", "index-v1")

  "ElasticsearchGenerationNaming" should {
    "derive one deterministic lowercase SHA-256 physical name" in {
      val first = ElasticsearchGenerationNaming.generationId(identity)
      val second = ElasticsearchGenerationNaming.generationId(identity)
      assert(first == second)
      assert(first.value.matches("[0-9a-f]{64}"))
      assert(ElasticsearchGenerationNaming.physicalIndexName("books_", identity).map(_.value) == Right(s"books_${first.value}"))
    }

    "reject a cursor reference outside the configured prefix and hash shape" in {
      assert(ElasticsearchGenerationNaming.validateCandidateReference("books_", ElasticsearchGenerationReference("other_index")).isLeft)
    }

    "change for every persisted identity component and metadata schema version" in {
      val original = ElasticsearchGenerationNaming.generationId(identity)
      val mutations = Vector(
        identity.copy(sourceContentFingerprint = "source-2"),
        identity.copy(projectedDocumentsFingerprint = "projected-2"),
        identity.copy(contractFingerprint = "contract-2"),
        identity.copy(projectionFormatVersion = "projection-v2"),
        identity.copy(compilerVersion = "compiler-v2"),
        identity.copy(indexFormatVersion = "index-v2"),
      )
      mutations.foreach(changed => assert(ElasticsearchGenerationNaming.generationId(changed) != original))
      assert(ElasticsearchGenerationNaming.generationId(identity, ElasticsearchGenerationMetadataSchemaVersion("schema-v2")) != original)
    }

    "reject empty prefixes before a 64-hex reference can enter the namespace" in {
      val reference = ElasticsearchGenerationReference("0" * 64)
      assert(ElasticsearchGenerationNaming.validateCandidateReference("", reference).isLeft)
    }
  }
}
