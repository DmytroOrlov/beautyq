package leaderboard.search.gen2.elasticsearch

import leaderboard.search.gen2.core.materialization.*
import org.scalatest.wordspec.AnyWordSpec

import java.time.Instant

/** Neutral calibration for the generic Elasticsearch generation compiler, using the shared book/library
  * document fixture ([[ElasticsearchTestFixtures]]) unrelated to BeautyQ.
  */
final class ElasticsearchGenerationCompilerSpec extends AnyWordSpec {
  import ElasticsearchTestFixtures.*

  private val baseSnapshot =
    VersionedSnapshot(value = "raw-snapshot-value", contentFingerprint = ContentFingerprint("content-fp-1"), sourceRevision = Some(SourceRevision("rev-1")), capturedAt = Instant.parse("2024-01-01T00:00:00Z"))

  private def materializedOf(
    sourceFingerprint: ContentFingerprint = ContentFingerprint("content-fp-1"),
    projectedFingerprint: ProjectedDocumentsFingerprint = ProjectedDocumentsFingerprint("projected-fp-1"),
    projectionVersion: ProjectionFormatVersion = ProjectionFormatVersion("book-projection-v1"),
  ): MaterializedSearchDocuments[String, BookDocument] =
    MaterializedSearchDocuments(
      sourceSnapshot = baseSnapshot.copy(contentFingerprint = sourceFingerprint),
      documents = Vector(bookA),
      projectedDocumentsFingerprint = projectedFingerprint,
      projectionFormatVersion = projectionVersion,
    )

  private def compileOrFail(
    materialized: MaterializedSearchDocuments[String, BookDocument] = materializedOf(),
    usingPolicy: ElasticsearchPolicy[BookDocument, String] = fullPolicy,
  ): CompiledElasticsearchGeneration[BookDocument, String] =
    ElasticsearchGenerationCompiler.compile(usingPolicy, materialized) match {
      case Right(compiled) => compiled
      case Left(error)     => fail(s"expected successful generation compilation, got $error")
    }

  "ElasticsearchGenerationCompiler.compile" should {
    "preserve the source snapshot's exact content fingerprint" in {
      val compiled = compileOrFail(materializedOf(sourceFingerprint = ContentFingerprint("content-fp-xyz")))
      assert(compiled.identity.sourceContentFingerprint == ContentFingerprint("content-fp-xyz"))
    }

    "preserve the exact projected-documents fingerprint" in {
      val compiled = compileOrFail(materializedOf(projectedFingerprint = ProjectedDocumentsFingerprint("projected-fp-xyz")))
      assert(compiled.identity.projectedDocumentsFingerprint == ProjectedDocumentsFingerprint("projected-fp-xyz"))
    }

    "preserve the exact projection format version" in {
      val compiled = compileOrFail(materializedOf(projectionVersion = ProjectionFormatVersion("book-projection-v9")))
      assert(compiled.identity.projectionFormatVersion == ProjectionFormatVersion("book-projection-v9"))
    }

    "use the complete policy's own exact derived contract fingerprint" in {
      assert(compileOrFail().identity.contractFingerprint == fullPolicy.contractFingerprint)
    }

    "carry the index policy's own compiler and index-format versions" in {
      val compiled = compileOrFail()
      assert(compiled.identity.compilerVersion == policy.compilerVersion)
      assert(compiled.identity.indexFormatVersion == policy.indexFormatVersion)
    }

    "produce an equal identity and byte-identical mapping/documents for repeated compilation of identical input" in {
      val first  = compileOrFail()
      val second = compileOrFail()
      assert(first.identity == second.identity)
      assert(first.mapping.json == second.mapping.json)
      assert(first.documents == second.documents)
    }

    "alter identity when the source content fingerprint changes" in {
      val base    = compileOrFail()
      val changed = compileOrFail(materializedOf(sourceFingerprint = ContentFingerprint("different")))
      assert(changed.identity != base.identity)
    }

    "alter identity when the projected-documents fingerprint changes" in {
      val base    = compileOrFail()
      val changed = compileOrFail(materializedOf(projectedFingerprint = ProjectedDocumentsFingerprint("different")))
      assert(changed.identity != base.identity)
    }

    "alter identity when the policy's own version changes" in {
      val base = compileOrFail()

      val changedIndex  = ElasticsearchIndexPolicy.unsafeFrom(document, ElasticsearchPolicyVersion("book-elasticsearch-v9"), bothTextFields)
      val changedPolicy =
        ElasticsearchPolicy.unsafeFrom(
          planContractVersion,
          changedIndex,
          queryTextFields,
          ElasticsearchTextOperator.Or,
          Some(geoScoringPolicy),
          ElasticsearchTotalHitsPolicy.ExactRequired,
          defaultSortPolicy,
        )
      val changed = compileOrFail(usingPolicy = changedPolicy)
      assert(changed.identity.contractFingerprint != base.identity.contractFingerprint)
      assert(changed.identity != base.identity)
    }

    "bind mapping, documents and identity from one compiler-owned call" in {
      val compiled          = compileOrFail()
      val expectedMapping   = ElasticsearchMappingCompiler.compile(policy).getOrElse(fail("expected a valid mapping"))
      val expectedDocuments = ElasticsearchDocumentCompiler.compile(policy, Vector(bookA)).getOrElse(fail("expected valid documents"))
      assert(compiled.mapping == expectedMapping)
      assert(compiled.documents == expectedDocuments)
    }

    // assertDoesNotCompile typechecks each snippet below as an isolated unit, with no access to this
    // file's local vals (only globally-reachable, fully-qualified types/values resolve) - so every
    // snippet is self-contained: real constructor arguments of the exact arity, reached through full
    // paths or a method parameter, never a local like a previously-compiled `compiled` result. That is
    // what makes each snippet fail for the real construction-boundary reason (inaccessible constructor,
    // `final`) rather than an incidental undefined-identifier or missing-argument error.
    "reject construction outside ElasticsearchGenerationCompiler" in {
      assertDoesNotCompile(
        """new leaderboard.search.gen2.elasticsearch.ElasticsearchGenerationCompiler.CompiledElasticsearchGeneration[leaderboard.search.gen2.elasticsearch.ElasticsearchTestFixtures.BookDocument, String](
          |  leaderboard.search.gen2.elasticsearch.ElasticsearchMapping(io.circe.Json.obj()),
          |  Vector.empty,
          |  leaderboard.search.gen2.elasticsearch.ElasticsearchGenerationIdentity(
          |    leaderboard.search.gen2.core.materialization.ContentFingerprint(""),
          |    leaderboard.search.gen2.core.materialization.ProjectedDocumentsFingerprint(""),
          |    leaderboard.search.gen2.elasticsearch.ElasticsearchTestFixtures.fullPolicy.contractFingerprint,
          |    leaderboard.search.gen2.core.materialization.ProjectionFormatVersion(""),
          |    leaderboard.search.gen2.elasticsearch.ElasticsearchTestFixtures.policy.compilerVersion,
          |    leaderboard.search.gen2.elasticsearch.ElasticsearchTestFixtures.policy.indexFormatVersion,
          |  ),
          |)""".stripMargin
      )
    }

    "reject calling .copy on a produced generation result - no copy method exists" in {
      assertDoesNotCompile(
        """def forgeCopy(value: leaderboard.search.gen2.elasticsearch.ElasticsearchGenerationCompiler.CompiledElasticsearchGeneration[leaderboard.search.gen2.elasticsearch.ElasticsearchTestFixtures.BookDocument, String]): Any =
          |  value.copy(mapping = value.mapping)""".stripMargin
      )
    }

    "reject subclassing outside ElasticsearchGenerationCompiler" in {
      assertDoesNotCompile(
        """final class ForgedGeneration extends leaderboard.search.gen2.elasticsearch.ElasticsearchGenerationCompiler.CompiledElasticsearchGeneration[leaderboard.search.gen2.elasticsearch.ElasticsearchTestFixtures.BookDocument, String](
          |  leaderboard.search.gen2.elasticsearch.ElasticsearchMapping(io.circe.Json.obj()),
          |  Vector.empty,
          |  leaderboard.search.gen2.elasticsearch.ElasticsearchGenerationIdentity(
          |    leaderboard.search.gen2.core.materialization.ContentFingerprint(""),
          |    leaderboard.search.gen2.core.materialization.ProjectedDocumentsFingerprint(""),
          |    leaderboard.search.gen2.elasticsearch.ElasticsearchTestFixtures.fullPolicy.contractFingerprint,
          |    leaderboard.search.gen2.core.materialization.ProjectionFormatVersion(""),
          |    leaderboard.search.gen2.elasticsearch.ElasticsearchTestFixtures.policy.compilerVersion,
          |    leaderboard.search.gen2.elasticsearch.ElasticsearchTestFixtures.policy.indexFormatVersion,
          |  ),
          |)""".stripMargin
      )
    }
  }
}
