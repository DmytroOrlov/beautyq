package leaderboard.search.gen2.elasticsearch

import org.scalatest.wordspec.AnyWordSpec

/** Neutral calibration for the generic Elasticsearch index/mapping-shape policy algebra, using the shared
  * book/library document fixture ([[ElasticsearchTestFixtures]]) unrelated to BeautyQ - per
  * docs/search/DOMAIN_AUTHORING_PRINCIPLES.md's reuse proof requirement. Contribution/fingerprint
  * derivation is [[ElasticsearchPolicy]]'s own concern (see `ElasticsearchPolicySpec`); this type carries
  * no independent fingerprint authority.
  */
final class ElasticsearchIndexPolicySpec extends AnyWordSpec {
  import ElasticsearchTestFixtures.*

  private def policyOrFail(textFields: Vector[ElasticsearchTextFieldMapping[BookDocument]] = bothTextFields): ElasticsearchIndexPolicy[BookDocument, String] =
    ElasticsearchIndexPolicy(document, policyVersion, textFields) match {
      case Right(policy) => policy
      case Left(errors)  => fail(s"expected a valid policy, got ${errors.toVector}")
    }

  "ElasticsearchIndexPolicy" should {
    "accept the exact owned searchable text handles" in {
      val policy = policyOrFail()
      assert(policy.textFields.map(_.field) == Vector(title, subtitle))
    }

    "normalize textFields into document declaration order for reversed input, preserving each assignment's exact analyzer and field handle" in {
      // Distinct analyzers per field (not both Standard) so a broken normalization that silently swapped
      // analyzers between fields, rather than genuinely reordering, would be caught.
      val distinctAnalyzers = Vector(ElasticsearchTextFieldMapping(title, ElasticsearchAnalyzerName.Standard), ElasticsearchTextFieldMapping(subtitle, ElasticsearchAnalyzerName("whitespace")))
      val reversed          = distinctAnalyzers.reverse

      val policyInOrder  = policyOrFail(distinctAnalyzers)
      val policyReversed = policyOrFail(reversed)

      // Normalized textFields always expose declaration order, regardless of caller input order.
      assert(policyInOrder.textFields.map(_.field) == Vector(title, subtitle))
      assert(policyReversed.textFields.map(_.field) == Vector(title, subtitle))

      // Each assignment's exact analyzer stays attached to its own field - normalization reorders
      // assignments, it never reassigns analyzers between fields.
      assert(policyReversed.textFields.map(_.analyzer) == Vector(ElasticsearchAnalyzerName.Standard, ElasticsearchAnalyzerName("whitespace")))
      assert(policyReversed.analyzerOf(title) == Some(ElasticsearchAnalyzerName.Standard))
      assert(policyReversed.analyzerOf(subtitle) == Some(ElasticsearchAnalyzerName("whitespace")))

      // Exact handle identity survives normalization - fields are reordered, never recreated.
      assert(policyReversed.textFields.map(_.field).zip(Vector(title, subtitle)).forall { case (normalized, canonical) => normalized eq canonical })
    }

    "reject a missing searchable-field analyzer assignment, reported in document declaration order" in {
      ElasticsearchIndexPolicy(document, policyVersion, Vector.empty) match {
        case Left(errors) =>
          assert(
            errors.toVector ==
              Vector(ElasticsearchIndexPolicyError.MissingAnalyzerAssignment(title.id, 0), ElasticsearchIndexPolicyError.MissingAnalyzerAssignment(subtitle.id, 1))
          )
        case Right(_) => fail("expected missing-assignment errors")
      }
    }

    "reject a duplicate analyzer assignment, reporting the first and duplicate index" in {
      ElasticsearchIndexPolicy(
        document,
        policyVersion,
        Vector(
          ElasticsearchTextFieldMapping(title, ElasticsearchAnalyzerName.Standard),
          ElasticsearchTextFieldMapping(subtitle, ElasticsearchAnalyzerName.Standard),
          ElasticsearchTextFieldMapping(title, ElasticsearchAnalyzerName.Standard),
        ),
      ) match {
        case Left(errors) => assert(errors.toVector == Vector(ElasticsearchIndexPolicyError.DuplicateAnalyzerAssignment(title.id, 0, 2)))
        case Right(_)      => fail("expected a duplicate-assignment error")
      }
    }

    "reject a recreated field handle that is not the exact declared instance" in {
      val recreatedTitle = leaderboard.search.gen2.contract.field[BookDocument, String]("title", _.title).text.searchable

      ElasticsearchIndexPolicy(
        document,
        policyVersion,
        Vector(ElasticsearchTextFieldMapping(recreatedTitle, ElasticsearchAnalyzerName.Standard), ElasticsearchTextFieldMapping(subtitle, ElasticsearchAnalyzerName.Standard)),
      ) match {
        case Left(errors) => assert(errors.toVector.contains(ElasticsearchIndexPolicyError.UndeclaredTextFieldHandle(recreatedTitle.id, 0)))
        case Right(_)      => fail("expected an undeclared-handle error")
      }
    }

    "reject a non-searchable-or-non-Text field assigned as a searchable text mapping" in {
      ElasticsearchIndexPolicy(
        document,
        policyVersion,
        Vector(ElasticsearchTextFieldMapping(title, ElasticsearchAnalyzerName.Standard), ElasticsearchTextFieldMapping(subtitle, ElasticsearchAnalyzerName.Standard), ElasticsearchTextFieldMapping(genre, ElasticsearchAnalyzerName.Standard)),
      ) match {
        case Left(errors) => assert(errors.toVector.contains(ElasticsearchIndexPolicyError.NonSearchableTextFieldAssignment(genre.id, 2)))
        case Right(_)      => fail("expected genre (non-Text) to be rejected")
      }

      ElasticsearchIndexPolicy(
        document,
        policyVersion,
        Vector(
          ElasticsearchTextFieldMapping(title, ElasticsearchAnalyzerName.Standard),
          ElasticsearchTextFieldMapping(subtitle, ElasticsearchAnalyzerName.Standard),
          ElasticsearchTextFieldMapping(internalNote, ElasticsearchAnalyzerName.Standard),
        ),
      ) match {
        case Left(errors) => assert(errors.toVector.contains(ElasticsearchIndexPolicyError.NonSearchableTextFieldAssignment(internalNote.id, 2)))
        case Right(_)      => fail("expected internalNote (non-searchable Text) to be rejected")
      }
    }

    "reject a SearchField belonging to another document type, at compile time" in {
      assertDoesNotCompile("""ElasticsearchTextFieldMapping[BookDocument](otherHeadline, ElasticsearchAnalyzerName.Standard)""")
    }

    "produce identical textFields for an identical policy" in {
      val first  = policyOrFail()
      val second = policyOrFail()
      assert(first.textFields == second.textFields)
    }

    "always carry the framework-owned current compiler and index-format versions - never a caller-supplied one" in {
      val policy = policyOrFail()
      assert(policy.compilerVersion == ElasticsearchCompilerVersion.Current)
      assert(policy.indexFormatVersion == ElasticsearchIndexFormatVersion.Current)
    }

    "reject a domain caller passing compilerVersion or indexFormatVersion to unsafeFrom, at compile time" in {
      assertDoesNotCompile(
        """leaderboard.search.gen2.elasticsearch.ElasticsearchIndexPolicy.unsafeFrom(
          |  leaderboard.search.gen2.elasticsearch.ElasticsearchTestFixtures.document,
          |  leaderboard.search.gen2.elasticsearch.ElasticsearchTestFixtures.policyVersion,
          |  leaderboard.search.gen2.elasticsearch.ElasticsearchTestFixtures.bothTextFields,
          |  compilerVersion = leaderboard.search.gen2.elasticsearch.ElasticsearchCompilerVersion("es-compiler-v9"),
          |)""".stripMargin
      )
      assertDoesNotCompile(
        """leaderboard.search.gen2.elasticsearch.ElasticsearchIndexPolicy.unsafeFrom(
          |  leaderboard.search.gen2.elasticsearch.ElasticsearchTestFixtures.document,
          |  leaderboard.search.gen2.elasticsearch.ElasticsearchTestFixtures.policyVersion,
          |  leaderboard.search.gen2.elasticsearch.ElasticsearchTestFixtures.bothTextFields,
          |  indexFormatVersion = leaderboard.search.gen2.elasticsearch.ElasticsearchIndexFormatVersion("es-index-format-v9"),
          |)""".stripMargin
      )
    }
  }
}
