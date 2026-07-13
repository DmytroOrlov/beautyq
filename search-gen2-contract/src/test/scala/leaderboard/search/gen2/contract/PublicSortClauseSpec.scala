package leaderboard.search.gen2.contract

import org.scalatest.wordspec.AnyWordSpec

import java.util.UUID

/** Neutral fixture proving the public-sort-clause split is generic, independent of any one domain's
  * document shape. TrailDocument (region/difficulty/location) shares no name, field, or vocabulary with
  * any owning business domain in this repository.
  */
final class PublicSortClauseSpec extends AnyWordSpec {

  private final case class TrailDocument(id: UUID, difficulty: Int, location: GeoPoint)

  private val difficultyField =
    field[TrailDocument, Int]("difficulty", _.difficulty).integer.sortable(SortMode.Value)

  private val locationField =
    field[TrailDocument, GeoPoint]("location", _.location).geoPoint.sortable(SortMode.Distance)

  private val origin = GeoPoint(BigDecimal("47.0"), BigDecimal("11.0"))

  "PublicSortClause.Planned" should {
    "carry an already-planned sort unchanged" in {
      val planned = PlannedSort.FieldValue(difficultyField, SortDirection.Asc)
      val clause: PublicSortClause[TrailDocument] = PublicSortClause.Planned(planned)
      clause match {
        case PublicSortClause.Planned(value) => assert(value == planned)
        case other                           => fail(s"expected Planned, got $other")
      }
    }
  }

  "PublicSortClause.GeoDistance" should {
    "carry the field and direction without an origin, unlike PlannedSort.GeoDistance" in {
      val clause: PublicSortClause[TrailDocument] = PublicSortClause.GeoDistance(locationField, SortDirection.Desc)
      clause match {
        case PublicSortClause.GeoDistance(field, direction) =>
          assert(field eq locationField)
          assert(direction == SortDirection.Desc)
        case other => fail(s"expected GeoDistance, got $other")
      }
    }

    "resolve into a PlannedSort.GeoDistance once an origin is supplied" in {
      val clause = PublicSortClause.GeoDistance(locationField, SortDirection.Asc)
      val resolved = PlannedSort.GeoDistance(clause.field, origin, clause.direction)
      assert(resolved == PlannedSort.GeoDistance(locationField, origin, SortDirection.Asc))
    }
  }

  "document-type safety" should {
    "reject a PublicSortClause declared for one document type where another document type is expected, at compile time" in {
      assertDoesNotCompile(
        """
          |final case class OtherDocument(id: UUID)
          |val wrongDocumentClause: PublicSortClause[OtherDocument] = PublicSortClause.Planned(PlannedSort.FieldValue(difficultyField, SortDirection.Asc))
          |""".stripMargin
      )
    }
  }
}
