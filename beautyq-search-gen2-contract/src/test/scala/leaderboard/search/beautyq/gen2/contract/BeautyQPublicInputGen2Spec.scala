package leaderboard.search.beautyq.gen2.contract

import leaderboard.search.gen2.contract.*
import org.scalatest.wordspec.AnyWordSpec

final class BeautyQPublicInputGen2Spec extends AnyWordSpec {
  private val page = PageRequest(None, PageSize.from(20) match {
    case Right(value) => value
    case Left(error)  => fail(s"fixture page size failed: $error")
  })

  "BeautyQPublicFilterRegistry" should {
    "keep the explicit static public names and authoritative handles" in {
      val staticFields = BeautyQPublicFilterRegistry.fields.take(5)
      assert(staticFields.map(_.name.value) == Vector("service", "category", "price", "durationMinutes", "distanceMeters"))
      val fields = BeautyQSearchDeclarations.variants.Fields
      staticFields match {
        case Vector(service, category, price, _, distance) =>
          assert(service.fieldHandles match {
            case Vector(handle) => handle eq fields.serviceCode
            case _ => false
          })
          assert(category.fieldHandles match {
            case Vector(handle) => handle eq fields.categoryCode
            case _ => false
          })
          assert(price.fieldHandles == Vector(fields.priceFrom, fields.priceTo))
          assert(distance.name.value != fields.location.path.value)
        case other => fail(s"unexpected static public field shape: $other")
      }
    }

    "generate dynamic names from stable attribute codes" in {
      assert(BeautyQPublicFilterRegistry.fields.exists(_.name.value == "attribute.enum.nail_coating_type"))
      assert(BeautyQPublicFilterRegistry.fields.exists(_.name.value == "attribute.int.session_count"))
      assert(BeautyQPublicFilterRegistry.fields.map(_.name.value).distinct.size == BeautyQPublicFilterRegistry.fields.size)
    }

    "decode typed terms and assign server provenance" in {
      val input = PublicFilterInput(PublicFieldName("service"), PublicOperator.Equal, PublicFilterValue.Scalar("manicure"), None)
      BeautyQPublicFilterRegistry.decode(input) match {
        case Right(decoded @ DecodedPublicFilter(BeautyPublicFilterClause.Constraint(PlannedConstraint.Terms(field, values)), ConstraintProvenance.ExplicitUi)) =>
          assert(field eq BeautyQSearchDeclarations.variants.Fields.serviceCode)
          assert(values.toVector.map(field.codec.encodeCanonical) == Vector("manicure"))
          assert(BeautyQPublicFilterRegistry.publicNameOf(decoded) == PublicFieldName("service"))
        case other => fail(s"unexpected decode result: $other")
      }
    }

    "decode price as interval overlap and distance as unresolved geo radius" in {
      val price = PublicFilterInput(PublicFieldName("price"), PublicOperator.LessThanOrEqual, PublicFilterValue.Scalar("50"), None)
      assert(BeautyQPublicFilterRegistry.decode(price).exists(_.clause match {
        case BeautyPublicFilterClause.Constraint(PlannedConstraint.IntervalOverlap(from, to, RangeBounds(Bound.Unbounded, Bound.Inclusive(value)))) =>
          (from eq BeautyQSearchDeclarations.variants.Fields.priceFrom) && (to eq BeautyQSearchDeclarations.variants.Fields.priceTo) && value == BigDecimal(50)
        case _ => false
      }))
      val distance = PublicFilterInput(PublicFieldName("distanceMeters"), PublicOperator.WithinDistance, PublicFilterValue.Scalar("1000"), None)
      assert(BeautyQPublicFilterRegistry.decode(distance).exists(_.clause match {
        case BeautyPublicFilterClause.GeoRadius(field, radius) => (field eq BeautyQSearchDeclarations.variants.Fields.location) && radius.meters == BigDecimal(1000)
        case _ => false
      }))
    }
  }

  "BeautySearchRequestGen2" should {
    "preserve order, page and coordinates while keeping coordinates inert" in {
      val request = BeautySearchRequestGen2(None, Vector.empty, Vector(FacetId("service")), Vector.empty, page, Some(GeoPoint(BigDecimal("52.5"), BigDecimal("13.4"))))
      BeautySearchRequestGen2.validate(request) match {
        case Right(validated) =>
          assert(validated.page == page)
          assert(validated.userLocation.nonEmpty)
          assert(validated.filters.isEmpty)
          assert(validated.sort.isEmpty)
          assert(validated.facets.map(_.id) == Vector(FacetId("service")))
        case Left(errors) => fail(s"valid request rejected: ${errors.toVector}")
      }
    }
  }

  "BeautyQIntentVocabulary" should {
    "validate stable-code rules and preserve the source scenarios" in {
      assert(BeautyQIntentVocabulary.validation.isRight)
      assert(BeautyQIntentVocabulary.rules.exists(_.aliases.contains("маникюр")))
      assert(BeautyQIntentVocabulary.rules.exists(_.aliases.contains("салон красоты")))
      assert(BeautyQIntentVocabulary.rules.exists(_.aliases.contains("volume2_d")))
      assert(BeautyQIntentVocabulary.rules.map(_.id.value).distinct.size == BeautyQIntentVocabulary.rules.size)
    }
  }

  "BeautyQSearchDeclarations inbound branches" should {
    "point at the executable registries and validated vocabulary" in {
      assert(BeautyQSearchDeclarations.variants.request.publicFilters eq BeautyQPublicFilterRegistry.fields)
      assert(BeautyQSearchDeclarations.variants.request.publicSorts eq BeautyQPublicSortRegistry.names)
      assert(BeautyQSearchDeclarations.variants.request.publicFacets eq BeautyQSearchPlanPolicy.facetRegistry.ids)
      assert(BeautyQSearchDeclarations.variants.intent.vocabulary eq BeautyQIntentVocabulary.value)
    }
  }

  "BeautyQPublicFilterRegistry value-shape matrix" should {
    val many    = PublicFilterValue.Many(Vector("1", "2"))
    val scalar  = PublicFilterValue.Scalar("10")
    val between = PublicFilterValue.BetweenBounds("1", "10", lowerInclusive = true, upperInclusive = true)

    "reject every wrong operator/value-shape combination with the exact expected shape name" in {
      val cases: Vector[(String, PublicOperator, PublicFilterValue, String)] = Vector(
        ("service", PublicOperator.Equal, many, "Scalar"),
        ("service", PublicOperator.Equal, between, "Scalar"),
        ("service", PublicOperator.In, scalar, "Many"),
        ("service", PublicOperator.In, between, "Many"),
        ("durationMinutes", PublicOperator.GreaterThan, many, "Scalar"),
        ("durationMinutes", PublicOperator.GreaterThan, between, "Scalar"),
        ("durationMinutes", PublicOperator.GreaterThanOrEqual, many, "Scalar"),
        ("durationMinutes", PublicOperator.LessThan, many, "Scalar"),
        ("durationMinutes", PublicOperator.LessThanOrEqual, many, "Scalar"),
        ("durationMinutes", PublicOperator.Between, scalar, "BetweenBounds"),
        ("durationMinutes", PublicOperator.Between, many, "BetweenBounds"),
        ("attribute.int.session_count", PublicOperator.Between, scalar, "BetweenBounds"),
        ("attribute.int.session_count", PublicOperator.GreaterThan, many, "Scalar"),
        ("attribute.decimal.deposit_amount", PublicOperator.Between, scalar, "BetweenBounds"),
        ("attribute.decimal.deposit_amount", PublicOperator.LessThanOrEqual, many, "Scalar"),
        ("price", PublicOperator.Between, scalar, "BetweenBounds"),
        ("price", PublicOperator.Between, many, "BetweenBounds"),
        ("price", PublicOperator.GreaterThan, many, "Scalar"),
        ("distanceMeters", PublicOperator.WithinDistance, many, "Scalar"),
        ("distanceMeters", PublicOperator.WithinDistance, between, "Scalar"),
      )

      cases.foreach { case (name, operator, value, expectedShape) =>
        val input = PublicFilterInput(PublicFieldName(name), operator, value, None)
        BeautyQPublicFilterRegistry.decode(input) match {
          case Left(errors) =>
            assert(errors.toVector == Vector(PublicFilterError.WrongPublicValueShape(PublicFieldName(name), operator, expectedShape)), s"unexpected errors for $name/$operator/$value")
          case Right(other) => fail(s"expected WrongPublicValueShape for $name/$operator/$value, got: $other")
        }
      }
    }

    "never turn Between + Scalar into an equality range, for every ordered field family" in {
      val orderedFamilies = Vector("price", "durationMinutes", "attribute.int.session_count", "attribute.decimal.deposit_amount")
      orderedFamilies.foreach { name =>
        val input = PublicFilterInput(PublicFieldName(name), PublicOperator.Between, scalar, None)
        BeautyQPublicFilterRegistry.decode(input) match {
          case Left(errors) => assert(errors.toVector == Vector(PublicFilterError.WrongPublicValueShape(PublicFieldName(name), PublicOperator.Between, "BetweenBounds")), s"unexpected errors for $name")
          case Right(other) => fail(s"Between + Scalar must never decode for $name, got: $other")
        }
      }
    }
  }

  "BeautyQPublicFilterRegistry indexed In decoding" should {
    "accumulate every decoding error with its exact index, not stopping at the first invalid value" in {
      val serviceCode = BeautyQSearchDeclarations.variants.Fields.serviceCode
      val raw0 = "Not_Valid"
      val raw2 = "123"
      val error0 = serviceCode.codec.decodeCanonical(raw0) match {
        case Left(error) => error
        case Right(value) => fail(s"expected $raw0 to fail decoding, got: $value")
      }
      val error2 = serviceCode.codec.decodeCanonical(raw2) match {
        case Left(error) => error
        case Right(value) => fail(s"expected $raw2 to fail decoding, got: $value")
      }

      val input = PublicFilterInput(PublicFieldName("service"), PublicOperator.In, PublicFilterValue.Many(Vector(raw0, "manicure", raw2)), None)
      BeautyQPublicFilterRegistry.decode(input) match {
        case Left(errors) =>
          assert(
            errors.toVector ==
              Vector(
                PublicFilterError.InvalidCanonicalValueAt(PublicFieldName("service"), 0, error0.typeId, raw0, error0.message),
                PublicFilterError.InvalidCanonicalValueAt(PublicFieldName("service"), 2, error2.typeId, raw2, error2.message),
              )
          )
        case Right(other) => fail(s"expected decoding errors, got: $other")
      }
    }

    "detect a canonical duplicate only after every value decodes successfully" in {
      val input = PublicFilterInput(PublicFieldName("service"), PublicOperator.In, PublicFilterValue.Many(Vector("manicure", "pedicure", "manicure")), None)
      BeautyQPublicFilterRegistry.decode(input) match {
        case Left(errors) => assert(errors.toVector == Vector(PublicFilterError.DuplicateCanonicalTerm(PublicFieldName("service"), "manicure")))
        case Right(other) => fail(s"expected a duplicate-term rejection, got: $other")
      }
    }

    "not report a spurious duplicate when a decode error is also present" in {
      val serviceCode = BeautyQSearchDeclarations.variants.Fields.serviceCode
      val raw = "Not_Valid"
      val error = serviceCode.codec.decodeCanonical(raw) match {
        case Left(error) => error
        case Right(value) => fail(s"expected $raw to fail decoding, got: $value")
      }
      val input = PublicFilterInput(PublicFieldName("service"), PublicOperator.In, PublicFilterValue.Many(Vector("manicure", raw, "manicure")), None)
      BeautyQPublicFilterRegistry.decode(input) match {
        case Left(errors) => assert(errors.toVector == Vector(PublicFilterError.InvalidCanonicalValueAt(PublicFieldName("service"), 1, error.typeId, raw, error.message)))
        case Right(other) => fail(s"expected only the decode error, got: $other")
      }
    }
  }

  "BeautyQPublicFilterRegistry dynamic field matrix" should {
    "pin the exact complete dynamic name list, authoritative handles and accepted operators per family" in {
      val fields = BeautyQSearchDeclarations.variants.Fields
      val expectedIntNames      = Vector("session_count", "included_corrections_count", "max_clients").map(code => s"attribute.int.$code")
      val expectedDecimalNames  = Vector("deposit_amount", "home_visit_surcharge", "materials_surcharge", "fixed_discount_amount").map(code => s"attribute.decimal.$code")
      val expectedEnumNames     =
        Vector("hair_removal_method", "nail_coating_type", "nail_service_type", "lash_service_type", "lash_volume", "brow_service_type", "pmu_area", "facial_treatment_type", "body_area")
          .map(code => s"attribute.enum.$code")
      val expectedBooleanNames  = Vector("with_removal", "with_design", "with_tinting", "with_correction").map(code => s"attribute.boolean.$code")

      val dynamicFields = BeautyQPublicFilterRegistry.fields.drop(5)
      assert(dynamicFields.map(_.name.value) == expectedIntNames ++ expectedDecimalNames ++ expectedEnumNames ++ expectedBooleanNames)

      val orderedNumericOperators = Vector(PublicOperator.GreaterThan, PublicOperator.GreaterThanOrEqual, PublicOperator.LessThan, PublicOperator.LessThanOrEqual, PublicOperator.Between)
      val termsOperators          = Vector(PublicOperator.Equal, PublicOperator.In)

      (expectedIntNames ++ expectedDecimalNames).foreach { name =>
        assert(dynamicFields.find(_.name.value == name).exists(_.acceptedOperators == orderedNumericOperators), s"unexpected operators for $name")
      }
      expectedEnumNames.foreach { name =>
        assert(dynamicFields.find(_.name.value == name).exists(_.acceptedOperators == termsOperators), s"unexpected operators for $name")
      }
      expectedBooleanNames.foreach { name =>
        assert(dynamicFields.find(_.name.value == name).exists(_.acceptedOperators == Vector(PublicOperator.Equal)), s"unexpected operators for $name")
      }

      assert(dynamicFields.find(_.name.value == "attribute.int.session_count").exists(_.fieldHandles.headOption.exists(_ eq fields.intAttributesByCode("session_count"))))
      assert(dynamicFields.find(_.name.value == "attribute.decimal.deposit_amount").exists(_.fieldHandles.headOption.exists(_ eq fields.decimalAttributesByCode("deposit_amount"))))
      assert(dynamicFields.find(_.name.value == "attribute.enum.nail_coating_type").exists(_.fieldHandles.headOption.exists(_ eq fields.enumAttributesByCode("nail_coating_type"))))
      assert(dynamicFields.find(_.name.value == "attribute.boolean.with_design").exists(_.fieldHandles.headOption.exists(_ eq fields.booleanAttributesByCode("with_design"))))
    }

    "prove public names are independent of field path/id/display text" in {
      val fields = BeautyQSearchDeclarations.variants.Fields
      assert(BeautyQPublicFilterRegistry.fields.find(_.name.value == "durationMinutes").exists(_.fieldHandles == Vector(fields.durationMin)))
      assert("durationMinutes" != fields.durationMin.id.value)
      assert("durationMinutes" != fields.durationMin.path.value)
      assert(BeautyQPublicFilterRegistry.fields.find(_.name.value == "distanceMeters").exists(_.fieldHandles == Vector(fields.location)))
      assert("distanceMeters" != fields.location.id.value)
      assert("distanceMeters" != fields.location.path.value)
    }
  }

  "BeautyQPublicFilterRegistry bound mapping" should {
    "pin the exact bound shape for every single-bound operator" in {
      val cases = Vector(
        PublicOperator.GreaterThan -> RangeBounds(Bound.Exclusive(30), Bound.Unbounded),
        PublicOperator.GreaterThanOrEqual -> RangeBounds(Bound.Inclusive(30), Bound.Unbounded),
        PublicOperator.LessThan -> RangeBounds(Bound.Unbounded, Bound.Exclusive(30)),
        PublicOperator.LessThanOrEqual -> RangeBounds(Bound.Unbounded, Bound.Inclusive(30)),
      )
      cases.foreach { case (operator, expectedBounds) =>
        val input = PublicFilterInput(PublicFieldName("durationMinutes"), operator, PublicFilterValue.Scalar("30"), None)
        BeautyQPublicFilterRegistry.decode(input) match {
          case Right(DecodedPublicFilter(BeautyPublicFilterClause.Constraint(PlannedConstraint.NumberRange(field, bounds)), _)) =>
            assert(field eq BeautyQSearchDeclarations.variants.Fields.durationMin)
            assert(bounds == expectedBounds, s"unexpected bounds for $operator")
          case other => fail(s"unexpected decode result for $operator: $other")
        }
      }
    }

    "pin all four Between inclusivity combinations" in {
      val cases = Vector(
        (true, true, Bound.Inclusive(10), Bound.Inclusive(20)),
        (true, false, Bound.Inclusive(10), Bound.Exclusive(20)),
        (false, true, Bound.Exclusive(10), Bound.Inclusive(20)),
        (false, false, Bound.Exclusive(10), Bound.Exclusive(20)),
      )
      cases.foreach { case (lowerInclusive, upperInclusive, expectedLower, expectedUpper) =>
        val input = PublicFilterInput(PublicFieldName("durationMinutes"), PublicOperator.Between, PublicFilterValue.BetweenBounds("10", "20", lowerInclusive, upperInclusive), None)
        BeautyQPublicFilterRegistry.decode(input) match {
          case Right(DecodedPublicFilter(BeautyPublicFilterClause.Constraint(PlannedConstraint.NumberRange(_, bounds)), _)) =>
            assert(bounds == RangeBounds(expectedLower, expectedUpper), s"unexpected bounds for ($lowerInclusive,$upperInclusive)")
          case other => fail(s"unexpected decode result: $other")
        }
      }
    }

    "reject an inverted Between range (lower greater than upper)" in {
      val input = PublicFilterInput(PublicFieldName("durationMinutes"), PublicOperator.Between, PublicFilterValue.BetweenBounds("20", "10", lowerInclusive = true, upperInclusive = true), None)
      BeautyQPublicFilterRegistry.decode(input) match {
        case Left(errors) => assert(errors.toVector == Vector(PublicFilterError.InvalidBetweenBounds(PublicFieldName("durationMinutes"), "20", "10", "lower bound is greater than upper bound")))
        case Right(other) => fail(s"expected rejection, got: $other")
      }
    }

    "reject equal Between endpoints unless both bounds are inclusive, and accept them when both are inclusive" in {
      val input = PublicFilterInput(PublicFieldName("durationMinutes"), PublicOperator.Between, PublicFilterValue.BetweenBounds("10", "10", lowerInclusive = true, upperInclusive = false), None)
      BeautyQPublicFilterRegistry.decode(input) match {
        case Left(errors) => assert(errors.toVector == Vector(PublicFilterError.InvalidBetweenBounds(PublicFieldName("durationMinutes"), "10", "10", "equal endpoints require both bounds inclusive")))
        case Right(other) => fail(s"expected rejection, got: $other")
      }
      val accepted = PublicFilterInput(PublicFieldName("durationMinutes"), PublicOperator.Between, PublicFilterValue.BetweenBounds("10", "10", lowerInclusive = true, upperInclusive = true), None)
      assert(BeautyQPublicFilterRegistry.decode(accepted).isRight)
    }
  }

  "BeautyQPublicFilterRegistry distance sign handling" should {
    "accept a positive distance, and reject zero or negative distance" in {
      val positive = PublicFilterInput(PublicFieldName("distanceMeters"), PublicOperator.WithinDistance, PublicFilterValue.Scalar("500"), None)
      BeautyQPublicFilterRegistry.decode(positive) match {
        case Right(DecodedPublicFilter(BeautyPublicFilterClause.GeoRadius(field, radius), _)) =>
          assert(field eq BeautyQSearchDeclarations.variants.Fields.location)
          assert(radius.meters == BigDecimal(500))
        case other => fail(s"unexpected decode result: $other")
      }
      val zero = PublicFilterInput(PublicFieldName("distanceMeters"), PublicOperator.WithinDistance, PublicFilterValue.Scalar("0"), None)
      BeautyQPublicFilterRegistry.decode(zero) match {
        case Left(errors) => assert(errors.toVector == Vector(PublicFilterError.NonPositiveDistance(PublicFieldName("distanceMeters"), "0")))
        case Right(other) => fail(s"expected rejection, got: $other")
      }
      val negative = PublicFilterInput(PublicFieldName("distanceMeters"), PublicOperator.WithinDistance, PublicFilterValue.Scalar("-5"), None)
      BeautyQPublicFilterRegistry.decode(negative) match {
        case Left(errors) => assert(errors.toVector == Vector(PublicFilterError.NonPositiveDistance(PublicFieldName("distanceMeters"), "-5")))
        case Right(other) => fail(s"expected rejection, got: $other")
      }
    }
  }

  "BeautyQPublicFilterRegistry provenance assignment" should {
    "assign ExplicitUi when presentationId is absent, and FacetSelection when present" in {
      val explicit = PublicFilterInput(PublicFieldName("service"), PublicOperator.Equal, PublicFilterValue.Scalar("manicure"), None)
      BeautyQPublicFilterRegistry.decode(explicit) match {
        case Right(DecodedPublicFilter(_, ConstraintProvenance.ExplicitUi)) => ()
        case other => fail(s"unexpected result: $other")
      }
      val selected = PublicFilterInput(PublicFieldName("service"), PublicOperator.Equal, PublicFilterValue.Scalar("manicure"), Some(FacetSelectionId("sel-42")))
      BeautyQPublicFilterRegistry.decode(selected) match {
        case Right(DecodedPublicFilter(_, ConstraintProvenance.FacetSelection(id))) => assert(id == FacetSelectionId("sel-42"))
        case other => fail(s"unexpected result: $other")
      }
    }

    "reject a blank FacetSelection ID" in {
      val blank = PublicFilterInput(PublicFieldName("service"), PublicOperator.Equal, PublicFilterValue.Scalar("manicure"), Some(FacetSelectionId("   ")))
      BeautyQPublicFilterRegistry.decode(blank) match {
        case Left(errors) => assert(errors.toVector == Vector(PublicFilterError.BlankFacetSelectionId(PublicFieldName("service"))))
        case Right(other) => fail(s"expected rejection, got: $other")
      }
    }
  }

  "BeautySearchRequestGen2 error matrix" should {
    "reject an unknown public field" in {
      val request = BeautySearchRequestGen2(None, Vector(PublicFilterInput(PublicFieldName("nope"), PublicOperator.Equal, PublicFilterValue.Scalar("x"), None)), Vector.empty, Vector.empty, page, None)
      BeautySearchRequestGen2.validate(request) match {
        case Left(errors) => assert(errors.toVector == Vector(BeautySearchRequestError.InvalidFilter(0, PublicFilterError.UnknownPublicField(PublicFieldName("nope")))))
        case Right(other) => fail(s"expected rejection, got: $other")
      }
    }

    "reject an unsupported operator for a known field" in {
      val request = BeautySearchRequestGen2(None, Vector(PublicFilterInput(PublicFieldName("service"), PublicOperator.GreaterThan, PublicFilterValue.Scalar("x"), None)), Vector.empty, Vector.empty, page, None)
      BeautySearchRequestGen2.validate(request) match {
        case Left(errors) =>
          assert(
            errors.toVector ==
              Vector(BeautySearchRequestError.InvalidFilter(0, PublicFilterError.UnsupportedPublicOperator(PublicFieldName("service"), PublicOperator.GreaterThan, Vector(PublicOperator.Equal, PublicOperator.In))))
          )
        case Right(other) => fail(s"expected rejection, got: $other")
      }
    }

    "reject an unknown sort name" in {
      val request = BeautySearchRequestGen2(None, Vector.empty, Vector.empty, Vector(BeautySortInput(PublicSortName("nope"), SortDirection.Asc)), page, None)
      BeautySearchRequestGen2.validate(request) match {
        case Left(errors) => assert(errors.toVector == Vector(BeautySearchRequestError.InvalidSort(0, BeautySortError.UnknownPublicSort(PublicSortName("nope")))))
        case Right(other) => fail(s"expected rejection, got: $other")
      }
    }

    "reject a duplicate sort name" in {
      val request = BeautySearchRequestGen2(None, Vector.empty, Vector.empty, Vector(BeautySortInput(PublicSortName("price"), SortDirection.Asc), BeautySortInput(PublicSortName("price"), SortDirection.Desc)), page, None)
      BeautySearchRequestGen2.validate(request) match {
        case Left(errors) => assert(errors.toVector == Vector(BeautySearchRequestError.InvalidSort(0, BeautySortError.DuplicatePublicSort(PublicSortName("price")))))
        case Right(other) => fail(s"expected rejection, got: $other")
      }
    }

    "reject an unknown requested facet" in {
      val request = BeautySearchRequestGen2(None, Vector.empty, Vector(FacetId("nope")), Vector.empty, page, None)
      BeautySearchRequestGen2.validate(request) match {
        case Left(errors) => assert(errors.toVector == Vector(BeautySearchRequestError.UnknownRequestedFacet(FacetId("nope"))))
        case Right(other) => fail(s"expected rejection, got: $other")
      }
    }

    "reject a duplicate requested facet" in {
      val request = BeautySearchRequestGen2(None, Vector.empty, Vector(FacetId("service"), FacetId("service")), Vector.empty, page, None)
      BeautySearchRequestGen2.validate(request) match {
        case Left(errors) => assert(errors.toVector == Vector(BeautySearchRequestError.DuplicateRequestedFacet(FacetId("service"))))
        case Right(other) => fail(s"expected rejection, got: $other")
      }
    }

    "accumulate filter, sort, duplicate-sort, facet and duplicate-facet errors in exactly that global order" in {
      val request =
        BeautySearchRequestGen2(
          None,
          Vector(PublicFilterInput(PublicFieldName("nope"), PublicOperator.Equal, PublicFilterValue.Scalar("x"), None)),
          Vector(FacetId("nope"), FacetId("service"), FacetId("service")),
          Vector(BeautySortInput(PublicSortName("gone"), SortDirection.Asc), BeautySortInput(PublicSortName("price"), SortDirection.Asc), BeautySortInput(PublicSortName("price"), SortDirection.Desc)),
          page,
          None,
        )
      BeautySearchRequestGen2.validate(request) match {
        case Left(errors) =>
          assert(
            errors.toVector ==
              Vector(
                BeautySearchRequestError.InvalidFilter(0, PublicFilterError.UnknownPublicField(PublicFieldName("nope"))),
                BeautySearchRequestError.InvalidSort(0, BeautySortError.UnknownPublicSort(PublicSortName("gone"))),
                BeautySearchRequestError.InvalidSort(1, BeautySortError.DuplicatePublicSort(PublicSortName("price"))),
                BeautySearchRequestError.UnknownRequestedFacet(FacetId("nope")),
                BeautySearchRequestError.DuplicateRequestedFacet(FacetId("service")),
              )
          )
        case Right(other) => fail(s"expected rejection, got: $other")
      }
    }
  }

  "BeautySearchRequestTrace" should {
    // page.cursor is deliberately None: SearchCursor.fromOpaque is private[gen2] to search-gen2-core's
    // future Brick 4C cursor codec, so no cursor value is constructible from this BeautyQ-level test at
    // all yet. The trace format's "present" branch (request.cursor=present, never opaqueValue) is
    // exercised directly at the search-gen2-contract layer once Brick 4C exists.
    "render the exact golden trace for a complete request" in {
      val fields = BeautyQSearchDeclarations.variants.Fields
      val request =
        BeautySearchRequestGen2(
          Some("gel \"polish\" under 50"),
          Vector(
            PublicFilterInput(PublicFieldName("service"), PublicOperator.Equal, PublicFilterValue.Scalar("manicure"), None),
            PublicFilterInput(PublicFieldName("category"), PublicOperator.Equal, PublicFilterValue.Scalar("nails"), Some(FacetSelectionId("sel-1"))),
            PublicFilterInput(PublicFieldName("distanceMeters"), PublicOperator.WithinDistance, PublicFilterValue.Scalar("2000"), None),
          ),
          Vector(FacetId("service")),
          Vector(BeautySortInput(PublicSortName("price"), SortDirection.Asc), BeautySortInput(PublicSortName("distanceMeters"), SortDirection.Desc)),
          page,
          Some(GeoPoint(BigDecimal("52.5"), BigDecimal("13.4"))),
        )
      val validated = BeautySearchRequestGen2.validate(request) match {
        case Right(value) => value
        case Left(errors) => fail(s"fixture request failed to validate: ${errors.toVector}")
      }
      val trace = BeautySearchRequestTrace.render(validated)
      assert(
        trace ==
          Vector(
            "request.query=present=\"gel \\\"polish\\\" under 50\"",
            s"request.filter[0] name=service provenance=ExplicitUi constraint.terms field=serviceCode:${fields.serviceCode.codec.typeId.value} values=[manicure]",
            s"request.filter[1] name=category provenance=FacetSelection(sel-1) constraint.terms field=categoryCode:${fields.categoryCode.codec.typeId.value} values=[nails]",
            s"request.filter[2] name=distanceMeters provenance=ExplicitUi geo-radius field=location:${fields.location.codec.typeId.value} radius=2000",
            "request.facets=[service]",
            "request.sort[0] sort.field-value field=priceFrom:decimal direction=Asc",
            "request.sort[1] geo-distance field=location:geo-point direction=Desc origin=unresolved",
            "request.cursor=absent",
            "request.page-size=20",
            "request.user-location=52.5,13.4",
          ).mkString("\n")
      )
    }
  }
}
