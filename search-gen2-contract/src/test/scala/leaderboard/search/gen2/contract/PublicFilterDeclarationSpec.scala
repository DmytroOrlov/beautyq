package leaderboard.search.gen2.contract

import org.scalatest.wordspec.AnyWordSpec

final class PublicFilterDeclarationSpec extends AnyWordSpec {
  private final case class Document(code: String, start: Int, finish: Int, location: GeoPoint, attributes: Map[String, Int])
  private final case class Definition(code: String)

  private val codeField =
    field[Document, String]("storedCode", _.code).keyword.filterable(FilterOperator.Equal, FilterOperator.In)
  private val numberField =
    field[Document, Int]("number", _.start).integer.filterable(FilterOperator.Equal, FilterOperator.In)
  private val startField =
    field[Document, Int]("start", _.start).integer.filterable(FilterOperator.Range)
  private val finishField =
    field[Document, Int]("finish", _.finish).integer.filterable(FilterOperator.Range)
  private val locationField =
    field[Document, GeoPoint]("location", _.location).geoPoint.filterable(FilterOperator.GeoDistance)
  private val dynamicFamily =
    searchFields[Document]("neutral-dynamic")
      .dynamicMap(_.attributes, Vector(Definition("first"), Definition("second")))(_.code)
      .integer
      .filterable(FilterOperator.Range)
      .declare

  "PublicFilterDeclaration" should {
    "preserve explicit names, declaration order and typed field handles" in {
      val declarations = Vector(
        PublicFilterDeclaration.value(PublicFieldName("publicCode"), codeField),
        PublicFilterDeclaration.intervalOverlap(PublicFieldName("window"), startField, finishField),
        PublicFilterDeclaration.geoDistance(PublicFieldName("near"), locationField),
        PublicFilterDeclaration.ordered(PublicFieldName("attribute.first"), dynamicFamily.byCode.get("first").getOrElse(fail("expected dynamic field"))),
      )
      val registry = PublicFilterRegistry.unsafeFrom(declarations)

      assert(registry.fields.map(_.name.value) == Vector("publicCode", "window", "near", "attribute.first"))
      assert(registry.fields.map(_.fieldHandles) == Vector(Vector(codeField), Vector(startField, finishField), Vector(locationField), Vector(dynamicFamily.byCode.get("first").getOrElse(fail("expected dynamic field")))))
      assert(registry.fields.map(_.acceptedOperators) == Vector(
        Vector(PublicOperator.Equal, PublicOperator.In),
        Vector(PublicOperator.GreaterThan, PublicOperator.GreaterThanOrEqual, PublicOperator.LessThan, PublicOperator.LessThanOrEqual, PublicOperator.Between),
        Vector(PublicOperator.WithinDistance),
        Vector(PublicOperator.GreaterThan, PublicOperator.GreaterThanOrEqual, PublicOperator.LessThan, PublicOperator.LessThanOrEqual, PublicOperator.Between),
      ))
    }

    "decode value, interval and geo shapes without deriving the public name from field path" in {
      val registry = PublicFilterRegistry.unsafeFrom(Vector(
        PublicFilterDeclaration.value(PublicFieldName("publicCode"), codeField),
        PublicFilterDeclaration.intervalOverlap(PublicFieldName("window"), startField, finishField),
        PublicFilterDeclaration.geoDistance(PublicFieldName("near"), locationField),
      ))

      registry.decode(PublicFilterInput(PublicFieldName("publicCode"), PublicOperator.Equal, PublicFilterValue.Scalar("svc"), None)) match {
        case Right(PublicFilterClause.Constraint(PlannedConstraint.Terms(field, values))) =>
          assert(field eq codeField)
          assert(values == Set("svc"))
        case other => fail(s"expected typed terms constraint, got $other")
      }
      registry.decode(PublicFilterInput(PublicFieldName("window"), PublicOperator.Between, PublicFilterValue.BetweenBounds("10", "20", true, false), None)) match {
        case Right(PublicFilterClause.Constraint(PlannedConstraint.IntervalOverlap(from, to, _))) =>
          assert(from eq startField)
          assert(to eq finishField)
        case other => fail(s"expected interval constraint, got $other")
      }
      registry.decode(PublicFilterInput(PublicFieldName("near"), PublicOperator.WithinDistance, PublicFilterValue.Scalar("12.5"), None)) match {
        case Right(PublicFilterClause.GeoRadius(field, radius)) =>
          assert(field eq locationField)
          assert(radius == Distance(BigDecimal("12.5")))
        case other => fail(s"expected geo radius, got $other")
      }
    }

    "accumulate indexed decode failures and detect canonical duplicates after decoding" in {
      val registry = PublicFilterRegistry.unsafeFrom(Vector(
        PublicFilterDeclaration.value(PublicFieldName("numbers"), numberField),
      ))
      registry.decode(PublicFilterInput(PublicFieldName("numbers"), PublicOperator.In, PublicFilterValue.Many(Vector("bad", "also")), None)) match {
        case Left(errors) =>
          val indexes = errors.toVector.map {
            case PublicFilterError.InvalidCanonicalValueAt(_, index, _, _, _) => index
            case other => fail(s"expected indexed canonical error, got $other")
          }
          assert(indexes == Vector(0, 1))
        case other => fail(s"expected indexed decoding errors, got $other")
      }
      registry.decode(PublicFilterInput(PublicFieldName("numbers"), PublicOperator.In, PublicFilterValue.Many(Vector("1", "1")), None)) match {
        case Left(errors) => assert(errors.toVector == Vector(PublicFilterError.DuplicateCanonicalTerm(PublicFieldName("numbers"), "1")))
        case other => fail(s"expected canonical duplicate, got $other")
      }
    }

    "accumulate duplicate and unsupported declaration errors" in {
      val invalid = PublicFilterRegistry(Vector(
        PublicFilterDeclaration.value(PublicFieldName("code"), codeField),
        PublicFilterDeclaration.value(PublicFieldName("code"), codeField),
        PublicFilterDeclaration.value(PublicFieldName("range"), codeField).withOperators(Vector(PublicOperator.Between)),
        PublicFilterDeclaration.geoDistance(PublicFieldName("missing-geo-capability"), locationField).withOperators(Vector(PublicOperator.Equal)),
        PublicFilterDeclaration.value(PublicFieldName("code"), codeField),
      ))

      invalid match {
        case Left(errors) =>
          assert(errors.toVector.exists {
            case PublicFilterDeclarationError.DuplicateName(_, 0, 1) => true
            case _ => false
          })
          assert(errors.toVector.exists {
            case PublicFilterDeclarationError.DuplicateName(_, 0, 4) => true
            case _ => false
          })
          assert(errors.toVector.exists {
            case PublicFilterDeclarationError.UnsupportedOperator(_, _, PublicOperator.Between, _) => true
            case _ => false
          })
        case Right(registry) => fail(s"expected declaration rejection, got ${registry.fields}")
      }
    }

    "reject a declaration for one document type when another document type is expected" in {
      assertDoesNotCompile(
        """
          |val other: PublicFilterDeclaration[String] =
          |  PublicFilterDeclaration.value(PublicFieldName("code"), codeField)
          |""".stripMargin
      )
    }
  }
}
