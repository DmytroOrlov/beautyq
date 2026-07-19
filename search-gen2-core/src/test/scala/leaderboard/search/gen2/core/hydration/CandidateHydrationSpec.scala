package leaderboard.search.gen2.core.hydration

import java.time.Instant
import java.util.UUID
import leaderboard.search.gen2.contract.*
import leaderboard.search.gen2.core.candidate.*
import leaderboard.search.gen2.core.materialization.*
import org.scalatest.wordspec.AnyWordSpec

final class CandidateHydrationSpec extends AnyWordSpec {
  private final case class Document(
    id: UUID,
    count: Int,
    from: BigDecimal,
    to: BigDecimal,
    location: GeoPoint,
  )

  private val declarations = searchFields[Document]("neutral")
  private val idField = declarations.keyword(_.id).payloadEligible.declare
  private val countField = declarations.integer(_.count).filterable(FilterOperator.Range).payloadEligible.declare
  private val fromField = declarations.decimal(_.from).filterable(FilterOperator.Range).payloadEligible.declare
  private val toField = declarations.decimal(_.to).filterable(FilterOperator.Range).payloadEligible.declare
  private val locationField = declarations.geoPoint(_.location).filterable(FilterOperator.GeoDistance).payloadEligible.declare
  private val declaration = searchDocument[Document]("neutral").id(idField).field(countField).field(fromField).field(toField).field(locationField).build.getOrElse(fail("expected declaration"))
  private val first = Document(UUID.fromString("00000000-0000-0000-0000-000000000001"), 3, BigDecimal("10"), BigDecimal("20"), GeoPoint(BigDecimal("52.5"), BigDecimal("13.4")))
  private val second = first.copy(
    id = UUID.fromString("00000000-0000-0000-0000-000000000002"),
    count = 7,
    from = BigDecimal("30"),
    to = BigDecimal("40"),
    location = GeoPoint(BigDecimal("52.6"), BigDecimal("13.5")),
  )
  private val text = SemanticQueryText.from("neutral query").getOrElse(fail("expected semantic text"))

  private final case class Metadata(
    sourceContentFingerprint: String,
    projectedDocumentsFingerprint: String,
    projectionFormatVersion: String,
    pointCount: Int,
  ) extends CandidateGenerationMetadata

  private val metadata = Metadata("source", "projected", "projection-v1", 2)
  private val materialized = MaterializedSearchDocuments(
    VersionedSnapshot(Vector(first, second), ContentFingerprint("source"), None, Instant.parse("2026-01-01T00:00:00Z")),
    Vector(second, first),
    ProjectedDocumentsFingerprint("projected"),
    ProjectionFormatVersion("projection-v1"),
  )

  private final class Execution(
    val candidatePlan: CandidatePlan[Document],
    val hits: Vector[CandidateHit[UUID, Double]],
    metadataValue: Metadata = metadata,
  ) extends BoundCandidateExecution[Document, UUID, Double, String, Metadata, String] {
    def declaration: SearchDocumentDeclaration[Document, UUID] = CandidateHydrationSpec.this.declaration
    def target: String = "neutral-generation"
    def metadata: Metadata = metadataValue
    def diagnostics: String = "diagnostics"
  }

  private val noPolicy = CandidateHydrationPolicy(CandidateMissingDocumentPolicy.Fail, CandidateHardConstraintPolicy.RequireAll, "neutral")

  "SearchDocumentDeclaration.identityOf" should {
    "derive the typed identity from the declared identity field" in {
      assert(declaration.identityOf(first) == first.id)
    }
  }

  "OrderedCandidateDocumentLookup" should {
    "preserve candidate order and reject duplicate or missing document identities" in {
      OrderedCandidateDocumentLookup.lookup(Vector(second.id, first.id), Vector(first, second))(declaration.identityOf) match {
        case Right(value) => assert(value == Vector(second, first))
        case Left(error)  => fail(s"expected ordered lookup, got $error")
      }
      OrderedCandidateDocumentLookup.lookup(Vector(first.id), Vector(first, first))(declaration.identityOf) match {
        case Left(OrderedCandidateDocumentLookupError.DuplicateDocumentIdentity(id, 0, 1)) => assert(id == first.id)
        case other => fail(s"expected duplicate identity, got $other")
      }
      val missing = UUID.fromString("00000000-0000-0000-0000-000000000099")
      OrderedCandidateDocumentLookup.lookup(Vector(missing), Vector(first))(declaration.identityOf) match {
        case Left(OrderedCandidateDocumentLookupError.MissingDocument(0, id)) => assert(id == missing)
        case other => fail(s"expected missing document, got $other")
      }
    }
  }

  "PlannedConstraintAssertion" should {
    "assert terms, numeric ranges, interval overlap and geo distance" in {
      val constraints = Vector[PlannedConstraint[Document]](
        PlannedConstraint.Terms(countField, Set(3)),
        PlannedConstraint.NumberRange(countField, RangeBounds(Bound.Inclusive(2), Bound.Exclusive(4))),
        PlannedConstraint.IntervalOverlap(fromField, toField, RangeBounds(Bound.Inclusive(BigDecimal("15")), Bound.Inclusive(BigDecimal("25")))),
        PlannedConstraint.GeoDistanceFilter(locationField, first.location, Distance(BigDecimal("1"))),
      )
      assert(PlannedConstraintAssertion.assertAll(first, constraints) == Right(()))
      PlannedConstraintAssertion.assertAll(second, constraints) match {
        case Left(values) => assert(values.map(_.constraintIndex) == Vector(0, 1, 2, 3))
        case Right(value)  => fail(s"expected violations, got $value")
      }
    }
  }

  "CandidateHydrator" should {
    "bind generation evidence, preserve scores/order and attach provenance" in {
      val plan = CandidatePlan(text, Vector(PlannedConstraint.NumberRange(countField, RangeBounds(Bound.Inclusive(2), Bound.Unbounded))))
      val executed = new Execution(plan, Vector(CandidateHit(second.id, 0.9), CandidateHit(first.id, 0.8)))
      CandidateHydrator.hydrate(executed, materialized, noPolicy) match {
        case Right(result) =>
          assert(result.candidates.map(_.id) == Vector(second.id, first.id))
          assert(result.candidates.map(_.score) == Vector(0.9, 0.8))
          assert(result.candidates.map(_.provenance) == Vector("neutral", "neutral"))
          assert(result.target == "neutral-generation")
          assert(result.metadata == metadata)
        case Left(error) => fail(s"expected hydrated candidates, got $error")
      }
    }

    "reject a generation fingerprint mismatch before document lookup" in {
      val plan = CandidatePlan[Document](text, Vector.empty)
      val executed = new Execution(plan, Vector(CandidateHit(first.id, 0.9)), metadata.copy(sourceContentFingerprint = "other"))
      CandidateHydrator.hydrate(executed, materialized, noPolicy) match {
        case Left(CandidateHydrationError.SourceSnapshotMismatch("other", "source")) => succeed
        case other => fail(s"expected source mismatch, got $other")
      }
    }

    "reject projected-document and projection-format mismatches before lookup" in {
      val plan = CandidatePlan[Document](text, Vector.empty)
      CandidateHydrator.hydrate(Execution(plan, Vector(CandidateHit(first.id, 0.9)), metadata.copy(projectedDocumentsFingerprint = "other")), materialized, noPolicy) match {
        case Left(CandidateHydrationError.ProjectedDocumentsMismatch("other", "projected")) => succeed
        case other => fail(s"expected projected-document mismatch, got $other")
      }
      CandidateHydrator.hydrate(Execution(plan, Vector(CandidateHit(first.id, 0.9)), metadata.copy(projectionFormatVersion = "other")), materialized, noPolicy) match {
        case Left(CandidateHydrationError.ProjectionFormatMismatch("other", "projection-v1")) => succeed
        case other => fail(s"expected projection-format mismatch, got $other")
      }
    }

    "reject a point-count mismatch before candidate lookup" in {
      val plan = CandidatePlan[Document](text, Vector.empty)
      CandidateHydrator.hydrate(Execution(plan, Vector(CandidateHit(first.id, 0.9)), metadata.copy(pointCount = 3)), materialized, noPolicy) match {
        case Left(CandidateHydrationError.PointCountMismatch(3, 2)) => succeed
        case other => fail(s"expected point-count mismatch, got $other")
      }
    }

    "reject hydrated documents that violate the candidate plan constraints" in {
      val plan = CandidatePlan(text, Vector(PlannedConstraint.Terms(countField, Set(999))))
      CandidateHydrator.hydrate(Execution(plan, Vector(CandidateHit(first.id, 0.9))), materialized, noPolicy) match {
        case Left(CandidateHydrationError.ConstraintViolations(values)) =>
          assert(values.map(_._1) == Vector(0))
          assert(values.headOption.exists(_._2 == first.id))
        case other => fail(s"expected constraint violations, got $other")
      }
    }

    "fail instead of silently dropping a missing candidate document" in {
      val plan = CandidatePlan[Document](text, Vector.empty)
      val missing = UUID.fromString("00000000-0000-0000-0000-000000000099")
      val executed = new Execution(plan, Vector(CandidateHit(missing, 0.9)))
      CandidateHydrator.hydrate(executed, materialized, noPolicy) match {
        case Left(CandidateHydrationError.Lookup(OrderedCandidateDocumentLookupError.MissingDocument(0, id))) => assert(id == missing)
        case other => fail(s"expected missing candidate error, got $other")
      }
    }
  }
}
