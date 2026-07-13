package leaderboard.search.gen2.contract

import org.scalatest.wordspec.AnyWordSpec

import java.util.UUID

/** Neutral proof that one typed source order derives executable tiers. */
final class ConstraintPrecedenceSpec extends AnyWordSpec {

  private final case class InventoryDocument(id: UUID, department: String, supplier: String)

  private enum Source {
    case PublicRequest
    case ParsedIntent
  }

  private val department = field[InventoryDocument, String]("department", _.department).keyword
  private val supplier    = field[InventoryDocument, String]("supplier", _.supplier).keyword

  "ConstraintPrecedence" should {
    "derive higher/lower tiers from one typed source order" in {
      val precedence = ConstraintPrecedence.above(Source.PublicRequest, Source.ParsedIntent) match {
        case Right(value) => value
        case Left(error)  => fail(s"expected valid precedence, got $error")
      }
      val public = Vector(SourcedConstraint(PlannedConstraint.Terms(department, Set("tools")), ConstraintProvenance.ExplicitUi))
      val parsed = Vector(SourcedConstraint(PlannedConstraint.Terms(supplier, Set("acme")), ConstraintProvenance.ParsedHard))

      assert(precedence.sourceOrder == Vector(Source.PublicRequest, Source.ParsedIntent))
      val tiers = precedence.tiers {
        case Source.PublicRequest => public
        case Source.ParsedIntent  => parsed
      }
      assert(tiers.higher == public)
      assert(tiers.lower == parsed)
    }

    "reject equal higher and lower source identities" in {
      assert(ConstraintPrecedence.above(Source.PublicRequest, Source.PublicRequest) == Left(ConstraintPrecedenceError.DuplicateSourceIdentity))
    }
  }
}
