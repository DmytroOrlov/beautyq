package leaderboard.search.gen2.core.plan

import leaderboard.search.gen2.contract.*
import org.scalatest.wordspec.AnyWordSpec

import java.nio.charset.StandardCharsets
import java.util.Base64

final class SearchCursorEnvelopeSpec extends AnyWordSpec {
  import PlanIdentityFixtures.*
  import PlanIdentityAssertions.*

  private def state(value: String): BackendCursorState = BackendCursorState.fromOpaque(value)

  private def issuedA(
    plan: SearchPlan[InventoryDocument] = planA,
    backendState: String = "backend.state:\n\tПривет",
  ): SearchCursor =
    SearchCursorEnvelope.issue(plan, viewA, state(backendState)) match {
      case Right(cursor) => cursor
      case Left(error)   => fail(s"expected cursor issue success, got $error")
    }

  private def withCursor[Document](plan: SearchPlan[Document], cursor: SearchCursor): SearchPlan[Document] =
    plan.copy(page = plan.page.copy(cursor = Some(cursor)))

  private def expectMismatch[Document](label: String, changedPlan: SearchPlan[Document], cursor: SearchCursor, view: CanonicalPlanView[Document]): Unit = {
    val segments = cursor.opaqueValue.split("\\.", -1)
    val actual = segments match {
      case Array(_, hash, _) =>
        PlanIdentityHash.parse(hash) match {
          case Some(value) => value
          case None        => fail(s"$label fixture cursor has an invalid stored hash")
        }
      case _ => fail(s"$label fixture cursor has an invalid envelope")
    }
    val expected = PlanIdentityHash.compute(view.identityOf(changedPlan.withoutCursor))
    assert(SearchCursorEnvelope.validate(withCursor(changedPlan, cursor), view) == Left(SearchCursorError.PlanIdentityMismatch(expected, actual)), label)
    (): Unit
  }

  private def first[A](values: Vector[A]): A =
    values match {
      case value +: _ => value
      case _          => fail("expected a non-empty fixture vector")
    }

  private def expectMalformed[Document](raw: String, expected: SearchCursorError): Unit = {
    val result = SearchCursorEnvelope.validate(withCursor(planA, SearchCursor.fromOpaque(raw)), viewA)
    assert(result == Left(expected))
    (): Unit
  }

  "SearchCursorEnvelope" should {
    "issue and validate a deterministic cursor while round-tripping opaque state" in {
      val cursor = issuedA()
      assert(cursor.opaqueValue == "search-cursor-envelope-v1.cc53c4c361afc858fe55bcd2adcf9583b6e46cef37025ece45a2dfd88a8bc3fa.YmFja2VuZC5zdGF0ZToKCdCf0YDQuNCy0LXRgg")
      assert(SearchCursorEnvelope.issue(planA, viewA, state("backend.state:\n\tПривет")) == Right(cursor))

      SearchCursorEnvelope.validate(withCursor(planA, cursor), viewA) match {
        case Right(Some(validated)) => assert(validated.backendState.opaqueValue == "backend.state:\n\tПривет")
        case other                  => fail(s"expected validated cursor, got $other")
      }
    }

    "round-trip empty and delimiter/unicode backend state without interpreting it" in {
      Vector("", "a.b:c\n\tПривет", "opaque/search-after value").foreach { value =>
        val cursor = issuedA(backendState = value)
        SearchCursorEnvelope.validate(withCursor(planA, cursor), viewA) match {
          case Right(Some(validated)) => assert(validated.backendState.opaqueValue == value)
          case other                  => fail(s"expected state '$value' to validate, got $other")
        }
      }
    }

    "exclude the current cursor recursively from identity" in {
      val cursor = SearchCursorEnvelope.issue(planA, viewA, state("state")) match {
        case Right(value) => value
        case Left(error)  => fail(s"expected issue success, got $error")
      }
      val cursorBearing = withCursor(planA, cursor)
      assertIdentityUnchanged("current cursor", viewA.identityOf(planA), viewA.identityOf(cursorBearing))
      assert(SearchCursorEnvelope.issue(cursorBearing, viewA, state("state")) == Right(cursor))
      assert(SearchCursorEnvelope.validate(cursorBearing, viewA).isRight)
    }

    "exclude diagnostic notices, suppressed filters and provenance while retaining the same plan identity" in {
      val cursor = issuedA()
      val firstApplied = first(planA.appliedFilters)
      val changedNotices = planA.copy(
        diagnostics = planA.diagnostics.copy(
          notices = Vector(PlanDiagnostic(PlanDiagnosticCode("different.notice"), None))
        )
      )
      val changedSuppressed = planA.copy(
        diagnostics = planA.diagnostics.copy(
          suppressedFilters = Vector(
            SuppressedFilter(
              SourcedConstraint(PlannedConstraint.Terms(department, Set("different")), ConstraintProvenance.FacetSelection(FacetSelectionId("different-selection"))),
              SuppressionReason.OverriddenByHigherPrecedence,
            )
          ),
        )
      )

      assertIdentityUnchanged("diagnostic notices", viewA.identityOf(planA), viewA.identityOf(changedNotices))
      assertIdentityUnchanged("suppressed filters", viewA.identityOf(planA), viewA.identityOf(changedSuppressed))
      assert(SearchCursorEnvelope.validate(withCursor(changedNotices, cursor), viewA).isRight)
      assert(SearchCursorEnvelope.validate(withCursor(changedSuppressed, cursor), viewA).isRight)

      Vector[(String, ConstraintProvenance)](
        "ParsedHard" -> ConstraintProvenance.ParsedHard,
        "FacetSelection ID" -> ConstraintProvenance.FacetSelection(FacetSelectionId("another-selection")),
        "ParsedSoft" -> ConstraintProvenance.ParsedSoft,
        "SystemDefault" -> ConstraintProvenance.SystemDefault,
      ).foreach { case (label, provenance) =>
        val changedProvenance = planA.copy(
          appliedFilters = planA.appliedFilters.updated(
            0,
            firstApplied.copy(source = firstApplied.source.copy(provenance = provenance)),
          )
        )
        assertIdentityUnchanged(label, viewA.identityOf(planA), viewA.identityOf(changedProvenance))
        assert(SearchCursorEnvelope.validate(withCursor(changedProvenance, cursor), viewA).isRight, label)
      }
    }

    "invalidate when any major identity section changes" in {
      val cursorA = issuedA()
      val firstApplied = first(planA.appliedFilters)
      val changedHard = firstApplied.copy(
        source = firstApplied.source.copy(constraint = PlannedConstraint.Terms(department, Set("retail")))
      )
      val changedFacet = first(planA.facets) match {
        case facet: FacetRequest.Terms[InventoryDocument, ?] => facet.copy(size = right(FacetSize.from(13)))
        case other => fail(s"expected terms facet, got $other")
      }
      val changedGroup = first(planA.groups) match {
        case group: GroupRequest[InventoryDocument, ?] => group.copy(size = right(GroupSize.from(6)))
        case other => fail(s"expected group, got $other")
      }
      expectMismatch("query", planA.copy(residualText = Some("different")), cursorA, viewA)
      expectMismatch("constraint", planA.copy(appliedFilters = planA.appliedFilters.updated(0, changedHard)), cursorA, viewA)
      expectMismatch("sort", planA.copy(sort = Vector(PlannedSort.FieldValue(quality, SortDirection.Asc))), cursorA, viewA)
      expectMismatch("facet", planA.copy(facets = planA.facets.updated(0, changedFacet)), cursorA, viewA)
      expectMismatch("group", planA.copy(groups = planA.groups.updated(0, changedGroup)), cursorA, viewA)
      expectMismatch("page size", planA.copy(page = planA.page.copy(size = right(PageSize.from(26)))), cursorA, viewA)

      val cursorB = SearchCursorEnvelope.issue(planB, viewB, state("state")) match {
        case Right(value) => value
        case Left(error)  => fail(s"expected Shape B issue success, got $error")
      }
      expectMismatch("soft signal", planB.copy(softSignals = Vector(PlannedSignal.GeoProximitySignal(coordinates, GeoPoint(BigDecimal("51"), BigDecimal("30"))))), cursorB, viewB)
      expectMismatch("geo sort", planB.copy(sort = Vector(PlannedSort.GeoDistance(coordinates, GeoPoint(BigDecimal("51"), BigDecimal("30")), SortDirection.Asc))), cursorB, viewB)
      expectMismatch("contract fingerprint", planA, cursorA, CanonicalPlanView(ContractFingerprint("changed-contract")))
    }

    "round-trip Shape B and re-issue identically from its cursor-bearing plan" in {
      val cursor = SearchCursorEnvelope.issue(planB, viewB, state("trail.backend:state")) match {
        case Right(value) => value
        case Left(error)  => fail(s"expected Shape B issue success, got $error")
      }
      SearchCursorEnvelope.validate(withCursor(planB, cursor), viewB) match {
        case Right(Some(validated)) => assert(validated.backendState.opaqueValue == "trail.backend:state")
        case other                  => fail(s"expected Shape B validation success, got $other")
      }
      assert(SearchCursorEnvelope.issue(withCursor(planB, cursor), viewB, state("trail.backend:state")) == Right(cursor))
    }

    "validate a cursor-free plan as Right(None)" in {
      assert(SearchCursorEnvelope.validate(planA, viewA) == Right(None))
    }

    "return InvalidPlan before parsing a cursor envelope" in {
      val invalidPlan = planB.copy(
        sort = Vector(PlannedSort.FieldValue(trailType, SortDirection.Asc)),
        page = PageRequest(Some(SearchCursor.fromOpaque("malformed")), pageB),
      )
      SearchCursorEnvelope.validate(invalidPlan, viewB) match {
        case Left(SearchCursorError.InvalidPlan(errors)) =>
          assert(errors.toVector == Vector(SearchPlanError.InvalidSort(0, PlanConstraintError.UnsupportedSortMode(trailType.id, trailType.kind, SortMode.Value))))
        case other => fail(s"expected InvalidPlan, got $other")
      }
      SearchCursorEnvelope.issue(invalidPlan, viewB, state("state")) match {
        case Left(SearchCursorError.InvalidPlan(errors)) =>
          assert(errors.toVector == Vector(SearchPlanError.InvalidSort(0, PlanConstraintError.UnsupportedSortMode(trailType.id, trailType.kind, SortMode.Value))))
        case other => fail(s"expected InvalidPlan from issue, got $other")
      }
    }

    "reject structural, version, hash and backend-state corruption strictly" in {
      val validHash = PlanIdentityHash.compute(viewA.identityOf(planA)).value
      val state64 = Base64.getUrlEncoder.withoutPadding().encodeToString("state".getBytes(StandardCharsets.UTF_8))

      expectMalformed("one.two", SearchCursorError.MalformedEnvelope(2))
      expectMalformed("single", SearchCursorError.MalformedEnvelope(1))
      expectMalformed("one.two.three.four", SearchCursorError.MalformedEnvelope(4))
      expectMalformed(s"wrong-version.$validHash.$state64", SearchCursorError.UnsupportedEnvelopeVersion("wrong-version"))
      expectMalformed(s".$validHash.$state64", SearchCursorError.UnsupportedEnvelopeVersion(""))
      expectMalformed(s"${SearchCursorEnvelope.EnvelopeVersion}.ABC.$state64", SearchCursorError.InvalidPlanIdentityHash("ABC"))
      expectMalformed(s"${SearchCursorEnvelope.EnvelopeVersion}.0000.$state64", SearchCursorError.InvalidPlanIdentityHash("0000"))
      expectMalformed(s"${SearchCursorEnvelope.EnvelopeVersion}..$state64", SearchCursorError.InvalidPlanIdentityHash(""))
      expectMalformed(s"${SearchCursorEnvelope.EnvelopeVersion}.${"g" * 64}.$state64", SearchCursorError.InvalidPlanIdentityHash("g" * 64))
      expectMalformed(s"${SearchCursorEnvelope.EnvelopeVersion}.${validHash.toUpperCase}.$state64", SearchCursorError.InvalidPlanIdentityHash(validHash.toUpperCase))
      expectMalformed(s"${SearchCursorEnvelope.EnvelopeVersion}.${"A" + validHash.drop(1)}.$state64", SearchCursorError.InvalidPlanIdentityHash("A" + validHash.drop(1)))
      expectMalformed(s"${SearchCursorEnvelope.EnvelopeVersion}.$validHash.not*base64", SearchCursorError.InvalidBackendStateEncoding("not*base64"))
      expectMalformed(s"${SearchCursorEnvelope.EnvelopeVersion}.$validHash.$state64=", SearchCursorError.InvalidBackendStateEncoding(s"$state64="))
      expectMalformed(s"${SearchCursorEnvelope.EnvelopeVersion}.$validHash.AB", SearchCursorError.InvalidBackendStateEncoding("AB"))

      val invalidUtf8 = Base64.getUrlEncoder.withoutPadding().encodeToString(Array(0xc3.toByte, 0x28.toByte))
      expectMalformed(s"${SearchCursorEnvelope.EnvelopeVersion}.$validHash.$invalidUtf8", SearchCursorError.InvalidBackendStateEncoding(invalidUtf8))
    }

    "leave backend state opaque and unauthenticated in Brick 4C" in {
      val cursor = issuedA(backendState = "original")
      val segments = cursor.opaqueValue.split("\\.", -1)
      val changedState = Base64.getUrlEncoder.withoutPadding().encodeToString("changed".getBytes(StandardCharsets.UTF_8))
      val forgedButHashMatching = SearchCursor.fromOpaque(s"${segments(0)}.${segments(1)}.$changedState")

      SearchCursorEnvelope.validate(withCursor(planA, forgedButHashMatching), viewA) match {
        case Right(Some(validated)) => assert(validated.backendState.opaqueValue == "changed")
        case other                  => fail(s"expected changed opaque state to remain valid, got $other")
      }
    }
  }
}
