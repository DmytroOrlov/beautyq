package leaderboard.search.gen2.contract

import org.scalatest.wordspec.AnyWordSpec

/** Shape-firewall / vocabulary-drift detector, not a domain declaration spec. Two complementary
  * mechanisms:
  *
  *   1. exact current-vocabulary pins for every backend-neutral enum, so an accidental narrowing or
  *      silent widening is a required, reviewable test-diff instead of an unnoticed change;
  *   2. an executable proof that the one identified gap with an existing code surface (a multi-valued
  *      field) still fails to compile today, so the moment it is closed, this file itself forces an
  *      update rather than staying silently stale.
  *
  * Gaps with no code surface yet (interval-overlap constraints, the geo proximity/filter/sort triad -
  * both planned for Brick 4's request/intent/plan algebra) have nothing to pin here; they are tracked
  * as prose in docs/gen2/SEARCH_GEN2_FRAMEWORK_SCOPE.md instead, which is the authoritative gap ledger.
  * This file only pins what is mechanically checkable today.
  */
final class SearchGen2VocabularyLedgerSpec extends AnyWordSpec {

  "the current backend-neutral vocabulary" should {
    "contain exactly the accepted SearchFieldKind cases" in {
      assert(
        SearchFieldKind.values.toSet == Set(
          SearchFieldKind.Keyword,
          SearchFieldKind.Text,
          SearchFieldKind.Integer,
          SearchFieldKind.Long,
          SearchFieldKind.Decimal,
          SearchFieldKind.Boolean,
          SearchFieldKind.DateTime,
          SearchFieldKind.GeoPoint,
        )
      )
    }

    "contain exactly the accepted FilterOperator cases" in {
      assert(FilterOperator.values.toSet == Set(FilterOperator.Equal, FilterOperator.In, FilterOperator.Range, FilterOperator.GeoDistance))
    }

    "contain exactly the accepted FacetMode cases" in {
      assert(FacetMode.values.toSet == Set(FacetMode.Terms, FacetMode.Range))
    }

    "contain exactly the accepted SortMode cases" in {
      assert(SortMode.values.toSet == Set(SortMode.Value, SortMode.Distance))
    }

    "contain exactly the accepted GroupMode cases" in {
      assert(GroupMode.values.toSet == Set(GroupMode.Terms))
    }

    "contain exactly the accepted FacetCountingPolicy cases" in {
      assert(FacetCountingPolicy.values.toSet == Set(FacetCountingPolicy.AllAppliedHardFilters))
    }

    "contain exactly the accepted TermsFacetOrder cases" in {
      assert(TermsFacetOrder.values.toSet == Set(TermsFacetOrder.CountDescThenKeyAsc, TermsFacetOrder.KeyAsc))
    }

    "contain exactly the accepted GroupPrecisionPolicy cases" in {
      assert(GroupPrecisionPolicy.values.toSet == Set(GroupPrecisionPolicy.RequireExact, GroupPrecisionPolicy.AllowApproximate))
    }

    "contain exactly the accepted SuppressionReason cases" in {
      assert(SuppressionReason.values.toSet == Set(SuppressionReason.OverriddenByHigherPrecedence, SuppressionReason.EquivalentDuplicate))
    }
  }

  // ConstraintProvenance is a sealed trait, not an enum (FacetSelection carries a payload), so it has no
  // `.values` to pin exhaustively. Pinning each of the five forms through direct construction still
  // catches an accidental rename/removal as a required test-diff, without adding a public provenance
  // enum or string decoder that nothing in this contract needs yet.
  "the current trusted constraint-provenance vocabulary" should {
    "contain exactly the five accepted forms, constructed directly" in {
      val forms: Vector[ConstraintProvenance] =
        Vector(
          ConstraintProvenance.ExplicitUi,
          ConstraintProvenance.FacetSelection(FacetSelectionId("pin")),
          ConstraintProvenance.ParsedHard,
          ConstraintProvenance.ParsedSoft,
          ConstraintProvenance.SystemDefault,
        )
      assert(forms.distinct.size == 5)
    }
  }

  "the candidate-evaluation payload shapes" should {
    "pin the current generic success, rejection and malformed-policy forms" in {
      val emptyOrBlank = SemanticQueryTextError.EmptyOrBlank
      val passed: CandidateGateOutcome[String] = CandidateGateOutcome.Passed
      val rejected: CandidateGateOutcome[String] = CandidateGateOutcome.Rejected("pin")
      val malformed = CandidateEvaluationError.MissingSemanticTextAfterAllGatesPassed(emptyOrBlank)

      assert(emptyOrBlank == SemanticQueryTextError.EmptyOrBlank)
      assert(passed == CandidateGateOutcome.Passed)
      assert(rejected == CandidateGateOutcome.Rejected("pin"))
      assert(malformed == CandidateEvaluationError.MissingSemanticTextAfterAllGatesPassed(SemanticQueryTextError.EmptyOrBlank))
    }
  }

  // See docs/gen2/SEARCH_GEN2_FRAMEWORK_SCOPE.md, gap G-3: a Vector[A]-valued searchable/filterable
  // field has no SearchFieldKind, SearchValueCodec, or FieldExtraction shape today.
  // LibraryTracerDomainSpec proves the practical consequence (completeDocument forces an explicit
  // .ignore); these two assertions are the minimal generic, BeautyQ-free compile-time proof that no
  // inferred authoring or the representative explicit String-kind choice can express it, independent
  // of any one domain's document shape. The remaining explicit kind methods require their own exact
  // scalar value type and therefore cannot accept Vector[String] either.
  "the tracked multi-value field gap (G-3)" should {
    "still reject a Vector[String] field through .inferred, with no default kind evidence" in {
      assertDoesNotCompile(
        """{
          |  final case class TaggedDoc(id: java.util.UUID, tags: Vector[String])
          |  searchFields[TaggedDoc]("taggedDocs").inferred(_.tags)
          |}""".stripMargin
      )
    }

    "still reject a Vector[String] field through the explicit .keyword choice, with no codec evidence" in {
      assertDoesNotCompile(
        """{
          |  final case class TaggedDoc(id: java.util.UUID, tags: Vector[String])
          |  searchFields[TaggedDoc]("taggedDocs").keyword(_.tags)
          |}""".stripMargin
      )
    }
  }
}
