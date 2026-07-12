package leaderboard.search.gen2.contract

import org.scalatest.wordspec.AnyWordSpec

import java.util.UUID

/** Neutral fixture proving the group-request algebra, independent of any one domain's document shape.
  * ProviderDocument exercises Terms grouping, representative-field projection and geo metric ordering
  * over one document shape, deliberately not BeautyQ's provider/service carousels; OtherDocument is a
  * second, structurally unrelated shape used both to calibrate Terms grouping beyond ProviderDocument
  * and to prove document-type safety at compile time.
  */
final class GroupRequestSpec extends AnyWordSpec {

  private final case class ProviderDocument(
    id: UUID,
    department: String,
    seller: String,
    title: String,
    location: GeoPoint,
  )

  private final case class OtherDocument(id: UUID, region: String)

  private val termsGroupableRegion =
    field[OtherDocument, String]("region", _.region).keyword.groupable(GroupMode.Terms)

  private val termsGroupableDepartment =
    field[ProviderDocument, String]("department", _.department).keyword.groupable(GroupMode.Terms)

  private val notTermsGroupableDepartment =
    field[ProviderDocument, String]("department", _.department).keyword

  private val sellerField = field[ProviderDocument, String]("seller", _.seller).keyword
  private val titleField  = field[ProviderDocument, String]("title", _.title).text
  private val locationField = field[ProviderDocument, GeoPoint]("location", _.location).geoPoint

  private val bestScoreMetricId = GroupMetricId("bestScore")
  private val geoMetricId       = GroupMetricId("minDistance")
  private val origin            = GeoPoint(BigDecimal("1.5"), BigDecimal("2.5"))

  private val validOrder =
    Vector(GroupOrder.Metric(bestScoreMetricId, SortDirection.Desc), GroupOrder.MatchingDocumentCount(SortDirection.Desc), GroupOrder.Key(SortDirection.Asc))

  private val validGroupSize = GroupSize.from(5).getOrElse(fail("expected a valid GroupSize"))

  private def baseGroup(
    keyField: SearchField[ProviderDocument, String] = termsGroupableDepartment,
    representative: RepresentativeRequest[ProviderDocument] = RepresentativeRequest.Fields(Vector(sellerField, titleField)),
    metrics: Vector[GroupMetricRequest[ProviderDocument]] = Vector(GroupMetricRequest.BestScore(bestScoreMetricId)),
    order: Vector[GroupOrder] = validOrder,
  ): GroupRequest[ProviderDocument, String] =
    GroupRequest(GroupId("providers"), keyField, validGroupSize, representative, metrics, order, GroupPrecisionPolicy.RequireExact)

  "GroupRequest.validate" should {
    "accept a keyField with Terms group capability" in {
      assert(GroupRequest.validate(baseGroup()).isRight)
    }

    "reject a keyField without Terms group capability" in {
      val request = baseGroup(keyField = notTermsGroupableDepartment)
      GroupRequest.validate(request) match {
        case Left(errors) =>
          assert(errors.toVector == Vector(GroupRequestError.UnsupportedGroupMode(request.id, notTermsGroupableDepartment.id, SearchFieldKind.Keyword)))
        case Right(value) => fail(s"expected rejection, got: $value")
      }
    }

    "accept an IdentityOnly representative" in {
      val request = baseGroup(representative = RepresentativeRequest.IdentityOnly())
      assert(GroupRequest.validate(request).isRight)
    }

    "reject an empty Fields representative" in {
      val request = baseGroup(representative = RepresentativeRequest.Fields(Vector.empty))
      GroupRequest.validate(request) match {
        case Left(errors) => assert(errors.toVector == Vector(GroupRequestError.EmptyRepresentativeFields(request.id)))
        case Right(value) => fail(s"expected rejection, got: $value")
      }
    }

    "report a duplicated representative field exactly once" in {
      val request = baseGroup(representative = RepresentativeRequest.Fields(Vector(sellerField, sellerField, titleField)))
      GroupRequest.validate(request) match {
        case Left(errors) => assert(errors.toVector == Vector(GroupRequestError.DuplicateRepresentativeField(request.id, sellerField.id)))
        case Right(value) => fail(s"expected rejection, got: $value")
      }
    }

    "report a duplicated metric ID exactly once" in {
      val request = baseGroup(metrics = Vector(GroupMetricRequest.BestScore(bestScoreMetricId), GroupMetricRequest.BestScore(bestScoreMetricId)))
      GroupRequest.validate(request) match {
        case Left(errors) => assert(errors.toVector == Vector(GroupRequestError.DuplicateGroupMetricId(request.id, bestScoreMetricId)))
        case Right(value) => fail(s"expected rejection, got: $value")
      }
    }

    "reject a Metric order clause referencing an undeclared metric ID" in {
      val unknownMetricId = GroupMetricId("unknown")
      val request         = baseGroup(order = Vector(GroupOrder.Metric(unknownMetricId, SortDirection.Desc), GroupOrder.Key(SortDirection.Asc)))
      GroupRequest.validate(request) match {
        case Left(errors) => assert(errors.toVector == Vector(GroupRequestError.UnknownGroupOrderMetric(request.id, unknownMetricId)))
        case Right(value) => fail(s"expected rejection, got: $value")
      }
    }

    "reject a duplicated order criterion, reporting the second occurrence" in {
      val request =
        baseGroup(order =
          Vector(GroupOrder.MatchingDocumentCount(SortDirection.Desc), GroupOrder.MatchingDocumentCount(SortDirection.Asc), GroupOrder.Key(SortDirection.Asc))
        )
      GroupRequest.validate(request) match {
        case Left(errors) =>
          assert(errors.toVector == Vector(GroupRequestError.DuplicateGroupOrder(request.id, GroupOrder.MatchingDocumentCount(SortDirection.Asc))))
        case Right(value) => fail(s"expected rejection, got: $value")
      }
    }

    "reject an order tuple that does not end with Key(Asc)" in {
      val request = baseGroup(order = Vector(GroupOrder.Metric(bestScoreMetricId, SortDirection.Desc)))
      GroupRequest.validate(request) match {
        case Left(errors) => assert(errors.toVector == Vector(GroupRequestError.MissingStableKeyTieBreaker(request.id)))
        case Right(value) => fail(s"expected rejection, got: $value")
      }
    }

    "reject an order tuple ending with Key(Desc) instead of Key(Asc)" in {
      val request = baseGroup(order = Vector(GroupOrder.Key(SortDirection.Desc)))
      GroupRequest.validate(request) match {
        case Left(errors) => assert(errors.toVector == Vector(GroupRequestError.MissingStableKeyTieBreaker(request.id)))
        case Right(value) => fail(s"expected rejection, got: $value")
      }
    }

    "accept the exact valid order tuple: metric(bestScore) desc, matchingDocumentCount desc, key asc" in {
      val request =
        baseGroup(
          metrics = Vector(GroupMetricRequest.BestScore(bestScoreMetricId)),
          order = Vector(GroupOrder.Metric(bestScoreMetricId, SortDirection.Desc), GroupOrder.MatchingDocumentCount(SortDirection.Desc), GroupOrder.Key(SortDirection.Asc)),
        )
      assert(GroupRequest.validate(request).isRight)
    }

    "accept a geo order tuple when the geo metric is declared" in {
      val request =
        baseGroup(
          metrics = Vector(GroupMetricRequest.MinGeoDistance(geoMetricId, locationField, origin)),
          order = Vector(GroupOrder.Metric(geoMetricId, SortDirection.Asc), GroupOrder.Key(SortDirection.Asc)),
        )
      assert(GroupRequest.validate(request).isRight)
    }

    "reject a geo order tuple when the geo metric is not declared" in {
      val request =
        baseGroup(
          metrics = Vector.empty,
          order = Vector(GroupOrder.Metric(geoMetricId, SortDirection.Asc), GroupOrder.Key(SortDirection.Asc)),
        )
      GroupRequest.validate(request) match {
        case Left(errors) => assert(errors.toVector == Vector(GroupRequestError.UnknownGroupOrderMetric(request.id, geoMetricId)))
        case Right(value) => fail(s"expected rejection, got: $value")
      }
    }
  }

  "GroupMetricRequest.MinGeoDistance" should {
    "construct explicitly over a GeoPoint field, retaining its field and origin" in {
      val metric = GroupMetricRequest.MinGeoDistance(geoMetricId, locationField, origin)
      assert(metric.field eq locationField)
      assert(metric.origin == origin)
    }
  }

  "GroupRequest.validate across a second, structurally unrelated document shape" should {
    "accept a Terms group on OtherDocument, independent of ProviderDocument's own field shape" in {
      val request =
        GroupRequest(
          GroupId("regions"),
          termsGroupableRegion,
          validGroupSize,
          RepresentativeRequest.IdentityOnly(),
          Vector.empty,
          Vector(GroupOrder.Key(SortDirection.Asc)),
          GroupPrecisionPolicy.AllowApproximate,
        )
      assert(GroupRequest.validate(request).isRight)
    }
  }

  "document-type safety" should {
    "reject a GroupRequest declared for one document type where another document type is expected, at compile time" in {
      assertDoesNotCompile(
        """
          |val wrongDocumentGroup: GroupRequest[OtherDocument, String] = baseGroup()
          |""".stripMargin
      )
    }
  }
}
