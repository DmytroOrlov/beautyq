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
          assert(errors.toVector == Vector(
            PublicFilterDeclarationError.UnsupportedOperator(PublicFieldName("range"), 2, PublicOperator.Between, Vector(PublicOperator.Equal, PublicOperator.In)),
            PublicFilterDeclarationError.UnsupportedOperator(PublicFieldName("missing-geo-capability"), 3, PublicOperator.Equal, Vector(PublicOperator.WithinDistance)),
            PublicFilterDeclarationError.DuplicateName(PublicFieldName("code"), 0, 1),
            PublicFilterDeclarationError.DuplicateName(PublicFieldName("code"), 0, 4),
          ))
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

    "derive value operators from Equal/In field capabilities" in {
      val decl = PublicFilterDeclaration.value(PublicFieldName("code"), codeField)
      assert(decl.supportedOperators == Vector(PublicOperator.Equal, PublicOperator.In))
    }

    "derive ordered operators from a single Range field" in {
      val decl = PublicFilterDeclaration.ordered(PublicFieldName("num"), startField)
      assert(decl.supportedOperators == Vector(
        PublicOperator.GreaterThan,
        PublicOperator.GreaterThanOrEqual,
        PublicOperator.LessThan,
        PublicOperator.LessThanOrEqual,
        PublicOperator.Between,
      ))
    }

    "derive interval operators from two Range fields" in {
      val decl = PublicFilterDeclaration.intervalOverlap(PublicFieldName("window"), startField, finishField)
      assert(decl.supportedOperators == Vector(
        PublicOperator.GreaterThan,
        PublicOperator.GreaterThanOrEqual,
        PublicOperator.LessThan,
        PublicOperator.LessThanOrEqual,
        PublicOperator.Between,
      ))
    }

    "derive geo operators from a GeoDistance field" in {
      val decl = PublicFilterDeclaration.geoDistance(PublicFieldName("near"), locationField)
      assert(decl.supportedOperators == Vector(PublicOperator.WithinDistance))
    }

    "preserve declaration order and field-handle order in registry.fields" in {
      val decls = Vector(
        PublicFilterDeclaration.value(PublicFieldName("zValue"), codeField),
        PublicFilterDeclaration.ordered(PublicFieldName("aOrdered"), startField),
        PublicFilterDeclaration.intervalOverlap(PublicFieldName("bInterval"), startField, finishField),
        PublicFilterDeclaration.geoDistance(PublicFieldName("cGeo"), locationField),
      )
      val registry = PublicFilterRegistry.unsafeFrom(decls)
      assert(registry.fields.map(_.name.value) == Vector("zValue", "aOrdered", "bInterval", "cGeo"))
      assert(registry.fields.map(_.fieldHandles) == Vector(Vector(codeField), Vector(startField), Vector(startField, finishField), Vector(locationField)))
      assert(registry.fields.map(_.acceptedOperators) == Vector(
        Vector(PublicOperator.Equal, PublicOperator.In),
        Vector(PublicOperator.GreaterThan, PublicOperator.GreaterThanOrEqual, PublicOperator.LessThan, PublicOperator.LessThanOrEqual, PublicOperator.Between),
        Vector(PublicOperator.GreaterThan, PublicOperator.GreaterThanOrEqual, PublicOperator.LessThan, PublicOperator.LessThanOrEqual, PublicOperator.Between),
        Vector(PublicOperator.WithinDistance),
      ))
    }

    "keep public names deliberately different from SearchField paths" in {
      assert(codeField.path.value == "code")
      assert(numberField.path.value == "start")
      assert(startField.path.value == "start")
      assert(finishField.path.value == "finish")
      assert(locationField.path.value == "location")
      val decls = Vector(
        PublicFilterDeclaration.value(PublicFieldName("publicCode"), codeField),
        PublicFilterDeclaration.ordered(PublicFieldName("attributeOrder"), numberField),
        PublicFilterDeclaration.intervalOverlap(PublicFieldName("priceWindow"), startField, finishField),
        PublicFilterDeclaration.geoDistance(PublicFieldName("nearby"), locationField),
      )
      val registry = PublicFilterRegistry.unsafeFrom(decls)
      assert(registry.fields.map(_.name.value) == Vector("publicCode", "attributeOrder", "priceWindow", "nearby"))
    }

    "compile single-bound operators to exact RangeBounds" in {
      val decl = PublicFilterDeclaration.ordered(PublicFieldName("num"), startField)
      val registry = PublicFilterRegistry.unsafeFrom(Vector(decl))
      def input(op: PublicOperator): PublicFilterInput =
        PublicFilterInput(PublicFieldName("num"), op, PublicFilterValue.Scalar("5"), None)

      registry.decode(input(PublicOperator.GreaterThan)) match {
        case Right(PublicFilterClause.Constraint(PlannedConstraint.NumberRange(_, bounds))) =>
          assert(bounds == RangeBounds(Bound.Exclusive(5), Bound.Unbounded))
        case other => fail(s"expected GreaterThan bounds, got $other")
      }
      registry.decode(input(PublicOperator.GreaterThanOrEqual)) match {
        case Right(PublicFilterClause.Constraint(PlannedConstraint.NumberRange(_, bounds))) =>
          assert(bounds == RangeBounds(Bound.Inclusive(5), Bound.Unbounded))
        case other => fail(s"expected GreaterThanOrEqual bounds, got $other")
      }
      registry.decode(input(PublicOperator.LessThan)) match {
        case Right(PublicFilterClause.Constraint(PlannedConstraint.NumberRange(_, bounds))) =>
          assert(bounds == RangeBounds(Bound.Unbounded, Bound.Exclusive(5)))
        case other => fail(s"expected LessThan bounds, got $other")
      }
      registry.decode(input(PublicOperator.LessThanOrEqual)) match {
        case Right(PublicFilterClause.Constraint(PlannedConstraint.NumberRange(_, bounds))) =>
          assert(bounds == RangeBounds(Bound.Unbounded, Bound.Inclusive(5)))
        case other => fail(s"expected LessThanOrEqual bounds, got $other")
      }
    }

    "compile Between with all four inclusivity combinations" in {
      val decl = PublicFilterDeclaration.ordered(PublicFieldName("num"), startField)
      val registry = PublicFilterRegistry.unsafeFrom(Vector(decl))
      def input(li: Boolean, ui: Boolean): PublicFilterInput =
        PublicFilterInput(PublicFieldName("num"), PublicOperator.Between, PublicFilterValue.BetweenBounds("10", "20", li, ui), None)

      registry.decode(input(true, true)) match {
        case Right(PublicFilterClause.Constraint(PlannedConstraint.NumberRange(_, bounds))) =>
          assert(bounds == RangeBounds(Bound.Inclusive(10), Bound.Inclusive(20)))
        case other => fail(s"expected [10,20] bounds, got $other")
      }
      registry.decode(input(true, false)) match {
        case Right(PublicFilterClause.Constraint(PlannedConstraint.NumberRange(_, bounds))) =>
          assert(bounds == RangeBounds(Bound.Inclusive(10), Bound.Exclusive(20)))
        case other => fail(s"expected [10,20) bounds, got $other")
      }
      registry.decode(input(false, true)) match {
        case Right(PublicFilterClause.Constraint(PlannedConstraint.NumberRange(_, bounds))) =>
          assert(bounds == RangeBounds(Bound.Exclusive(10), Bound.Inclusive(20)))
        case other => fail(s"expected (10,20] bounds, got $other")
      }
      registry.decode(input(false, false)) match {
        case Right(PublicFilterClause.Constraint(PlannedConstraint.NumberRange(_, bounds))) =>
          assert(bounds == RangeBounds(Bound.Exclusive(10), Bound.Exclusive(20)))
        case other => fail(s"expected (10,20) bounds, got $other")
      }
    }

    "reject inverted Between endpoints and accept equal endpoints only when both inclusive" in {
      val decl = PublicFilterDeclaration.ordered(PublicFieldName("num"), startField)
      val registry = PublicFilterRegistry.unsafeFrom(Vector(decl))

      registry.decode(PublicFilterInput(PublicFieldName("num"), PublicOperator.Between, PublicFilterValue.BetweenBounds("20", "10", true, true), None)) match {
        case Left(errors) =>
          assert(errors.toVector == Vector(PublicFilterError.InvalidBetweenBounds(PublicFieldName("num"), "20", "10", "lower bound is greater than upper bound")))
        case other => fail(s"expected inverted bounds rejection, got $other")
      }
      registry.decode(PublicFilterInput(PublicFieldName("num"), PublicOperator.Between, PublicFilterValue.BetweenBounds("10", "10", true, false), None)) match {
        case Left(errors) =>
          assert(errors.toVector == Vector(PublicFilterError.InvalidBetweenBounds(PublicFieldName("num"), "10", "10", "equal endpoints require both bounds inclusive")))
        case other => fail(s"expected equal-exclusive rejection, got $other")
      }
      registry.decode(PublicFilterInput(PublicFieldName("num"), PublicOperator.Between, PublicFilterValue.BetweenBounds("10", "10", false, true), None)) match {
        case Left(errors) =>
          assert(errors.toVector == Vector(PublicFilterError.InvalidBetweenBounds(PublicFieldName("num"), "10", "10", "equal endpoints require both bounds inclusive")))
        case other => fail(s"expected equal-exclusive rejection, got $other")
      }
      registry.decode(PublicFilterInput(PublicFieldName("num"), PublicOperator.Between, PublicFilterValue.BetweenBounds("10", "10", true, true), None)) match {
        case Right(PublicFilterClause.Constraint(PlannedConstraint.NumberRange(_, bounds))) =>
          assert(bounds == RangeBounds(Bound.Inclusive(10), Bound.Inclusive(10)))
        case other => fail(s"expected equal-inclusive acceptance, got $other")
      }
    }

    "preserve both authoritative field handles in intervalOverlap" in {
      val decl = PublicFilterDeclaration.intervalOverlap(PublicFieldName("window"), startField, finishField)
      assert(decl.fieldHandles == Vector(startField, finishField))
      val registry = PublicFilterRegistry.unsafeFrom(Vector(decl))
      registry.decode(PublicFilterInput(PublicFieldName("window"), PublicOperator.Between, PublicFilterValue.BetweenBounds("10", "20", true, true), None)) match {
        case Right(PublicFilterClause.Constraint(PlannedConstraint.IntervalOverlap(from, to, _))) =>
          assert(from eq startField)
          assert(to eq finishField)
        case other => fail(s"expected interval clause, got $other")
      }
    }

    "reject wrong public value shapes with exact errors" in {
      val registry = PublicFilterRegistry.unsafeFrom(Vector(
        PublicFilterDeclaration.value(PublicFieldName("code"), codeField),
        PublicFilterDeclaration.ordered(PublicFieldName("num"), startField),
        PublicFilterDeclaration.geoDistance(PublicFieldName("near"), locationField),
      ))

      registry.decode(PublicFilterInput(PublicFieldName("code"), PublicOperator.Equal, PublicFilterValue.Many(Vector("a")), None)) match {
        case Left(errors) =>
          assert(errors.toVector == Vector(PublicFilterError.WrongPublicValueShape(PublicFieldName("code"), PublicOperator.Equal, "Scalar")))
        case other => fail(s"expected Scalar rejection for Equal+Many, got $other")
      }
      registry.decode(PublicFilterInput(PublicFieldName("code"), PublicOperator.In, PublicFilterValue.Scalar("a"), None)) match {
        case Left(errors) =>
          assert(errors.toVector == Vector(PublicFilterError.WrongPublicValueShape(PublicFieldName("code"), PublicOperator.In, "Many")))
        case other => fail(s"expected Many rejection for In+Scalar, got $other")
      }
      registry.decode(PublicFilterInput(PublicFieldName("num"), PublicOperator.GreaterThan, PublicFilterValue.Many(Vector("1")), None)) match {
        case Left(errors) =>
          assert(errors.toVector == Vector(PublicFilterError.WrongPublicValueShape(PublicFieldName("num"), PublicOperator.GreaterThan, "Scalar")))
        case other => fail(s"expected Scalar rejection for single-bound+Many, got $other")
      }
      registry.decode(PublicFilterInput(PublicFieldName("num"), PublicOperator.Between, PublicFilterValue.Scalar("10"), None)) match {
        case Left(errors) =>
          assert(errors.toVector == Vector(PublicFilterError.WrongPublicValueShape(PublicFieldName("num"), PublicOperator.Between, "BetweenBounds")))
        case other => fail(s"expected BetweenBounds rejection for Between+Scalar, got $other")
      }
      registry.decode(PublicFilterInput(PublicFieldName("num"), PublicOperator.Between, PublicFilterValue.Many(Vector("10")), None)) match {
        case Left(errors) =>
          assert(errors.toVector == Vector(PublicFilterError.WrongPublicValueShape(PublicFieldName("num"), PublicOperator.Between, "BetweenBounds")))
        case other => fail(s"expected BetweenBounds rejection for Between+Many, got $other")
      }
      registry.decode(PublicFilterInput(PublicFieldName("near"), PublicOperator.WithinDistance, PublicFilterValue.Many(Vector("10")), None)) match {
        case Left(errors) =>
          assert(errors.toVector == Vector(PublicFilterError.WrongPublicValueShape(PublicFieldName("near"), PublicOperator.WithinDistance, "Scalar")))
        case other => fail(s"expected Scalar rejection for WithinDistance+Many, got $other")
      }
      registry.decode(PublicFilterInput(PublicFieldName("near"), PublicOperator.WithinDistance, PublicFilterValue.BetweenBounds("1", "2", true, true), None)) match {
        case Left(errors) =>
          assert(errors.toVector == Vector(PublicFilterError.WrongPublicValueShape(PublicFieldName("near"), PublicOperator.WithinDistance, "Scalar")))
        case other => fail(s"expected Scalar rejection for WithinDistance+BetweenBounds, got $other")
      }
    }

    "explicit narrowing: registry.fields exposes exactly the narrowed subset" in {
      val decl = PublicFilterDeclaration.ordered(PublicFieldName("num"), startField).withOperators(Vector(PublicOperator.GreaterThan, PublicOperator.LessThan))
      val registry = PublicFilterRegistry.unsafeFrom(Vector(decl))
      assert(registry.fields.map(_.acceptedOperators) == Vector(Vector(PublicOperator.GreaterThan, PublicOperator.LessThan)))
    }

    "explicit narrowing: retained operator decodes, rejected operator produces UnsupportedOperator" in {
      val decl = PublicFilterDeclaration.ordered(PublicFieldName("num"), startField).withOperators(Vector(PublicOperator.GreaterThan, PublicOperator.LessThan))
      val registry = PublicFilterRegistry.unsafeFrom(Vector(decl))

      registry.decode(PublicFilterInput(PublicFieldName("num"), PublicOperator.GreaterThan, PublicFilterValue.Scalar("5"), None)) match {
        case Right(PublicFilterClause.Constraint(PlannedConstraint.NumberRange(_, _))) => ()
        case other => fail(s"expected GreaterThan decode, got $other")
      }
      registry.decode(PublicFilterInput(PublicFieldName("num"), PublicOperator.LessThan, PublicFilterValue.Scalar("5"), None)) match {
        case Right(PublicFilterClause.Constraint(PlannedConstraint.NumberRange(_, _))) => ()
        case other => fail(s"expected LessThan decode, got $other")
      }
      registry.decode(PublicFilterInput(PublicFieldName("num"), PublicOperator.GreaterThanOrEqual, PublicFilterValue.Scalar("5"), None)) match {
        case Left(errors) =>
          assert(errors.toVector == Vector(PublicFilterError.UnsupportedPublicOperator(
            PublicFieldName("num"),
            PublicOperator.GreaterThanOrEqual,
            Vector(PublicOperator.GreaterThan, PublicOperator.LessThan),
          )))
        case other => fail(s"expected UnsupportedOperator, got $other")
      }
    }

    "reject unsupported narrowing as UnsupportedOperator in declaration errors" in {
      val invalid = PublicFilterRegistry(Vector(
        PublicFilterDeclaration.ordered(PublicFieldName("num"), startField).withOperators(Vector(PublicOperator.Equal)),
      ))
      invalid match {
        case Left(errors) =>
          assert(errors.toVector == Vector(
            PublicFilterDeclarationError.UnsupportedOperator(PublicFieldName("num"), 0, PublicOperator.Equal, Vector(
              PublicOperator.GreaterThan,
              PublicOperator.GreaterThanOrEqual,
              PublicOperator.LessThan,
              PublicOperator.LessThanOrEqual,
              PublicOperator.Between,
            )),
          ))
        case Right(registry) => fail(s"expected UnsupportedOperator rejection, got ${registry.fields}")
      }
    }

    "reject a declaration with no supported operators" in {
      val invalid = PublicFilterRegistry(Vector(
        PublicFilterDeclaration.value(PublicFieldName("code"), codeField).withOperators(Vector.empty),
      ))
      invalid match {
        case Left(errors) =>
          assert(errors.toVector == Vector(
            PublicFilterDeclarationError.NoSupportedOperators(PublicFieldName("code"), 0),
          ))
        case Right(registry) => fail(s"expected NoSupportedOperators rejection, got ${registry.fields}")
      }
    }

    "produce exact duplicate-name errors with firstIndex and duplicateIndex" in {
      val invalid = PublicFilterRegistry(Vector(
        PublicFilterDeclaration.value(PublicFieldName("code"), codeField),
        PublicFilterDeclaration.value(PublicFieldName("code"), codeField),
        PublicFilterDeclaration.value(PublicFieldName("other"), codeField),
        PublicFilterDeclaration.value(PublicFieldName("code"), codeField),
      ))
      invalid match {
        case Left(errors) =>
          assert(errors.toVector == Vector(
            PublicFilterDeclarationError.DuplicateName(PublicFieldName("code"), 0, 1),
            PublicFilterDeclarationError.DuplicateName(PublicFieldName("code"), 0, 3),
          ))
        case Right(registry) => fail(s"expected duplicate rejection, got ${registry.fields}")
      }
    }

    "accumulate policy errors before duplicate-name errors" in {
      val invalid = PublicFilterRegistry(Vector(
        PublicFilterDeclaration.value(PublicFieldName("code"), codeField).withOperators(Vector(PublicOperator.Between)),
        PublicFilterDeclaration.value(PublicFieldName("code"), codeField),
        PublicFilterDeclaration.value(PublicFieldName("other"), codeField).withOperators(Vector(PublicOperator.GreaterThan)),
        PublicFilterDeclaration.value(PublicFieldName("code"), codeField),
      ))
      invalid match {
        case Left(errors) =>
          assert(errors.toVector == Vector(
            PublicFilterDeclarationError.UnsupportedOperator(PublicFieldName("code"), 0, PublicOperator.Between, Vector(PublicOperator.Equal, PublicOperator.In)),
            PublicFilterDeclarationError.UnsupportedOperator(PublicFieldName("other"), 2, PublicOperator.GreaterThan, Vector(PublicOperator.Equal, PublicOperator.In)),
            PublicFilterDeclarationError.DuplicateName(PublicFieldName("code"), 0, 1),
            PublicFilterDeclarationError.DuplicateName(PublicFieldName("code"), 0, 3),
          ))
        case Right(registry) => fail(s"expected combined errors, got ${registry.fields}")
      }
    }

    "use the neutral dynamic-family handle with Range operators" in {
      val firstField = dynamicFamily.byCode.get("first").getOrElse(fail("expected dynamic field"))
      val decl = PublicFilterDeclaration.ordered(PublicFieldName("attribute.first"), firstField)
      assert(decl.supportedOperators == Vector(
        PublicOperator.GreaterThan,
        PublicOperator.GreaterThanOrEqual,
        PublicOperator.LessThan,
        PublicOperator.LessThanOrEqual,
        PublicOperator.Between,
      ))
      val registry = PublicFilterRegistry.unsafeFrom(Vector(decl))
      registry.decode(PublicFilterInput(PublicFieldName("attribute.first"), PublicOperator.GreaterThan, PublicFilterValue.Scalar("3"), None)) match {
        case Right(PublicFilterClause.Constraint(PlannedConstraint.NumberRange(f, _))) =>
          assert(f eq firstField)
        case other => fail(s"expected NumberRange, got $other")
      }
    }
  }
}
