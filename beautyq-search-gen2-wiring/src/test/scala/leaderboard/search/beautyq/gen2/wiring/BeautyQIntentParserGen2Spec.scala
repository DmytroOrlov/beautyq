package leaderboard.search.beautyq.gen2.wiring

import leaderboard.search.beautyq.gen2.contract.*
import leaderboard.search.gen2.contract.*
import org.scalatest.wordspec.AnyWordSpec

final class BeautyQIntentParserGen2Spec extends AnyWordSpec {
  private val page = PageRequest(None, PageSize.from(20) match {
    case Right(value) => value
    case Left(error)  => fail(s"fixture page size failed: $error")
  })

  // Fixtures go through the real trust boundary (BeautySearchRequestGen2.validate), never a direct
  // ValidatedBeautySearchRequestGen2(...) construction: that constructor is private[contract] precisely
  // so this wiring module cannot forge a validated request, in production or in tests.
  private def validate(request: BeautySearchRequestGen2): ValidatedBeautySearchRequestGen2 =
    BeautySearchRequestGen2.validate(request) match {
      case Right(validated) => validated
      case Left(errors)     => fail(s"fixture request failed to validate: ${errors.toVector}")
    }

  private def request(query: Option[String], location: Option[GeoPoint] = None): ValidatedBeautySearchRequestGen2 =
    validate(BeautySearchRequestGen2(query, Vector.empty, Vector.empty, Vector.empty, page, location))

  "BeautyQIntentParserGen2" should {
    "translate a service and inclusive budget overlap while leaving no budget tokens" in {
      BeautyQIntentParserGen2.parse(request(Some("маникюр under 50")), BeautyQIntentVocabulary.value) match {
        case Right(intent) =>
          assert(intent.hardConstraints.exists(_.constraint match {
            case PlannedConstraint.Terms(field, values) => (field eq BeautyQSearchDeclarations.variants.Fields.serviceCode) && values.toVector.map(field.codec.encodeCanonical) == Vector("manicure")
            case _ => false
          }))
          assert(intent.hardConstraints.exists(_.constraint match {
            case PlannedConstraint.IntervalOverlap(from, to, RangeBounds(Bound.Unbounded, Bound.Inclusive(value))) =>
              (from eq BeautyQSearchDeclarations.variants.Fields.priceFrom) && (to eq BeautyQSearchDeclarations.variants.Fields.priceTo) && value == BigDecimal(50)
            case _ => false
          }))
          assert(intent.residualText.isEmpty)
          assert(intent.hardConstraints.forall(_.provenance == ConstraintProvenance.ParsedHard))
          assert(intent.canonicalSemanticLabels.map(_.stableKey) == Vector("service:manicure", "attribute.enum:nail_service_type=manicure"))
        case Left(errors) => fail(s"parse failed: ${errors.toVector}")
      }
    }

    "keep multilingual longest aliases and contextual attributes deterministic" in {
      BeautyQIntentParserGen2.parse(request(Some("volume2_d Wandsbek lashes")), BeautyQIntentVocabulary.value) match {
        case Right(intent) =>
          assert(intent.hardConstraints.exists(_.constraint match {
            case PlannedConstraint.Terms(field, values) => (field eq BeautyQSearchDeclarations.variants.Fields.enumAttributesByCode("lash_volume")) && values == Set("volume2_d")
            case _ => false
          }))
          assert(intent.residualText.contains("wandsbek"))
        case Left(errors) => fail(s"parse failed: ${errors.toVector}")
      }
    }

    "preserve source multi-value intent as one terms constraint" in {
      BeautyQIntentParserGen2.parse(request(Some("lashes and brows")), BeautyQIntentVocabulary.value) match {
        case Right(intent) =>
          assert(intent.hardConstraints.exists(_.constraint match {
            case PlannedConstraint.Terms(field, values) =>
              (field eq BeautyQSearchDeclarations.variants.Fields.serviceCode) && values.map(field.codec.encodeCanonical) == Set("lashes", "brows")
            case _ => false
          }))
        case Left(errors) => fail(s"parse failed: ${errors.toVector}")
      }
    }

    "produce only geo proximity for NearUser and require coordinates" in {
      BeautyQIntentParserGen2.parse(request(Some("manicure nearby"), Some(GeoPoint(BigDecimal("52.5"), BigDecimal("13.4")))), BeautyQIntentVocabulary.value) match {
        case Right(intent) =>
          assert(intent.softSignals.size == 1)
          assert(intent.softSignals.headOption.exists {
            case _: PlannedSignal.GeoProximitySignal[?] => true
            case _ => false
          })
          assert(intent.hardConstraints.forall {
            case SourcedConstraint(PlannedConstraint.GeoDistanceFilter(_, _, _), _) => false
            case _ => true
          })
        case Left(errors) => fail(s"parse failed: ${errors.toVector}")
      }
      BeautyQIntentParserGen2.parse(request(Some("manicure nearby")), BeautyQIntentVocabulary.value) match {
        case Left(errors) => assert(errors.toVector == Vector(BeautyIntentParseError.MissingUserLocationForNearUser))
        case Right(intent) => fail(s"missing location unexpectedly succeeded: $intent")
      }
    }

    "deduplicate semantic labels and keep noise action-free" in {
      BeautyQIntentParserGen2.parse(request(Some("nails under 50 салон красоты")), BeautyQIntentVocabulary.value) match {
        case Right(intent) =>
          assert(intent.canonicalSemanticLabels.map(_.stableKey).distinct == intent.canonicalSemanticLabels.map(_.stableKey))
          assert(intent.matchedRuleIds.contains(IntentRuleId("r017")))
          assert(intent.residualText.isEmpty)
        case Left(errors) => fail(s"parse failed: ${errors.toVector}")
      }
    }

    // The nail-extension service (r005, "наращивание ногтей") is the only rule that produces
    // enum(nail_service_type=extension), which is exactly the restored Gen1 gate for the contextual gel
    // coating rule r032. So extension context enables r032; refill/removal context does not.
    "activate the contextual gel coating rule only under an extension gate (\"наращивание ногтей gel\")" in {
      BeautyQIntentParserGen2.parse(request(Some("наращивание ногтей gel")), BeautyQIntentVocabulary.value) match {
        case Right(intent) =>
          assert(intent.matchedRuleIds == Vector(IntentRuleId("r005"), IntentRuleId("r032")))
          assert(intent.hardConstraints.size == 3)
          assert(intent.hardConstraints.exists(_.constraint match {
            case PlannedConstraint.Terms(field, values) => (field eq BeautyQSearchDeclarations.variants.Fields.serviceCode) && values.map(field.codec.encodeCanonical) == Set("nail_modeling")
            case _ => false
          }))
          assert(intent.hardConstraints.exists(_.constraint match {
            case PlannedConstraint.Terms(field, values) => (field eq BeautyQSearchDeclarations.variants.Fields.enumAttributesByCode("nail_service_type")) && values == Set("extension")
            case _ => false
          }))
          assert(intent.hardConstraints.exists(_.constraint match {
            case PlannedConstraint.Terms(field, values) => (field eq BeautyQSearchDeclarations.variants.Fields.enumAttributesByCode("nail_coating_type")) && values == Set("gel")
            case _ => false
          }))
          assert(intent.residualText.isEmpty)
        case Left(errors) => fail(s"parse failed: ${errors.toVector}")
      }
    }

    "not activate the contextual acrylic coating rule under a refill gate (\"коррекция гелевых ногтей acrylic\")" in {
      BeautyQIntentParserGen2.parse(request(Some("коррекция гелевых ногтей acrylic")), BeautyQIntentVocabulary.value) match {
        case Right(intent) =>
          assert(intent.matchedRuleIds == Vector(IntentRuleId("r029")))
          // The refill/correction rule produces nail_service_type=refill (not extension), so the acrylic
          // gate (extension) is not satisfied: r033 never fires, "acrylic" stays residual.
          assert(intent.hardConstraints.exists(_.constraint match {
            case PlannedConstraint.Terms(field, values) => (field eq BeautyQSearchDeclarations.variants.Fields.enumAttributesByCode("nail_service_type")) && values == Set("refill")
            case _ => false
          }))
          assert(!intent.hardConstraints.exists(_.constraint match {
            case PlannedConstraint.Terms(field, values) => (field eq BeautyQSearchDeclarations.variants.Fields.enumAttributesByCode("nail_coating_type")) && values == Set("acrylic")
            case _ => false
          }))
          // the existing gel coating constraint from r029 is preserved
          assert(intent.hardConstraints.exists(_.constraint match {
            case PlannedConstraint.Terms(field, values) => (field eq BeautyQSearchDeclarations.variants.Fields.enumAttributesByCode("nail_coating_type")) && values == Set("gel")
            case _ => false
          }))
          assert(intent.residualText.contains("acrylic"))
        case Left(errors) => fail(s"parse failed: ${errors.toVector}")
      }
    }

    "not activate a contextual coating rule under a removal gate (\"снять гель acrylic\")" in {
      BeautyQIntentParserGen2.parse(request(Some("снять гель acrylic")), BeautyQIntentVocabulary.value) match {
        case Right(intent) =>
          assert(intent.matchedRuleIds == Vector(IntentRuleId("r026")))
          // removal context (nail_service_type=removal) does not satisfy the extension-only gate
          assert(intent.hardConstraints.exists(_.constraint match {
            case PlannedConstraint.Terms(field, values) => (field eq BeautyQSearchDeclarations.variants.Fields.enumAttributesByCode("nail_service_type")) && values == Set("removal")
            case _ => false
          }))
          assert(!intent.hardConstraints.exists(_.constraint match {
            case PlannedConstraint.Terms(field, values) => (field eq BeautyQSearchDeclarations.variants.Fields.enumAttributesByCode("nail_coating_type")) && values == Set("acrylic")
            case _ => false
          }))
          assert(intent.residualText.contains("acrylic"))
        case Left(errors) => fail(s"parse failed: ${errors.toVector}")
      }
    }

    "let a produced ServiceAny satisfy a singular Service requirement (\"lashes and brows volume2_d\")" in {
      BeautyQIntentParserGen2.parse(request(Some("lashes and brows volume2_d")), BeautyQIntentVocabulary.value) match {
        case Right(intent) =>
          assert(intent.matchedRuleIds == Vector(IntentRuleId("r018"), IntentRuleId("r035")))
          assert(intent.hardConstraints.exists(_.constraint match {
            case PlannedConstraint.Terms(field, values) => (field eq BeautyQSearchDeclarations.variants.Fields.serviceCode) && values.map(field.codec.encodeCanonical) == Set("lashes", "brows")
            case _ => false
          }))
          assert(intent.hardConstraints.exists(_.constraint match {
            case PlannedConstraint.Terms(field, values) => (field eq BeautyQSearchDeclarations.variants.Fields.enumAttributesByCode("lash_volume")) && values == Set("volume2_d")
            case _ => false
          }))
          assert(intent.residualText.isEmpty)
        case Left(errors) => fail(s"parse failed: ${errors.toVector}")
      }
    }

    "activate two independent contextual rules around one earlier match (\"volume2_d lashes extension\")" in {
      BeautyQIntentParserGen2.parse(request(Some("volume2_d lashes extension")), BeautyQIntentVocabulary.value) match {
        case Right(intent) =>
          assert(intent.matchedRuleIds == Vector(IntentRuleId("r035"), IntentRuleId("r006"), IntentRuleId("r038")))
          assert(intent.hardConstraints.size == 3)
          assert(intent.hardConstraints.exists(_.constraint match {
            case PlannedConstraint.Terms(field, values) => (field eq BeautyQSearchDeclarations.variants.Fields.serviceCode) && values.map(field.codec.encodeCanonical) == Set("lashes")
            case _ => false
          }))
          assert(intent.hardConstraints.exists(_.constraint match {
            case PlannedConstraint.Terms(field, values) => (field eq BeautyQSearchDeclarations.variants.Fields.enumAttributesByCode("lash_volume")) && values == Set("volume2_d")
            case _ => false
          }))
          assert(intent.hardConstraints.exists(_.constraint match {
            case PlannedConstraint.Terms(field, values) => (field eq BeautyQSearchDeclarations.variants.Fields.enumAttributesByCode("lash_service_type")) && values == Set("extension")
            case _ => false
          }))
          assert(intent.residualText.isEmpty)
        case Left(errors) => fail(s"parse failed: ${errors.toVector}")
      }
    }

    "suppress a contextual rule via excludes when the excluded action is already produced (face/gesicht exclusion scenario)" in {
      BeautyQIntentParserGen2.parse(request(Some("увлажнение лица face")), BeautyQIntentVocabulary.value) match {
        case Right(intent) =>
          assert(intent.matchedRuleIds == Vector(IntentRuleId("r065")))
          assert(intent.hardConstraints.size == 3)
          assert(intent.hardConstraints.exists(_.constraint match {
            case PlannedConstraint.Terms(field, values) => (field eq BeautyQSearchDeclarations.variants.Fields.enumAttributesByCode("body_area")) && values == Set("face_neck_decollete")
            case _ => false
          }))
          assert(!intent.hardConstraints.exists(_.constraint match {
            case PlannedConstraint.Terms(field, values) => (field eq BeautyQSearchDeclarations.variants.Fields.enumAttributesByCode("body_area")) && values == Set("face")
            case _ => false
          }))
          assert(intent.residualText.contains("face"))
        case Left(errors) => fail(s"parse failed: ${errors.toVector}")
      }
    }

    "translate a single multi-action independent rule (\"реснички 2д корр\")" in {
      BeautyQIntentParserGen2.parse(request(Some("реснички 2д корр")), BeautyQIntentVocabulary.value) match {
        case Right(intent) =>
          assert(intent.matchedRuleIds == Vector(IntentRuleId("r023")))
          assert(intent.hardConstraints.size == 4)
          assert(intent.hardConstraints.exists(_.constraint match {
            case PlannedConstraint.Terms(field, values) => (field eq BeautyQSearchDeclarations.variants.Fields.serviceCode) && values.map(field.codec.encodeCanonical) == Set("lashes")
            case _ => false
          }))
          assert(intent.hardConstraints.exists(_.constraint match {
            case PlannedConstraint.Terms(field, values) => (field eq BeautyQSearchDeclarations.variants.Fields.enumAttributesByCode("lash_volume")) && values == Set("volume2_d")
            case _ => false
          }))
          assert(intent.hardConstraints.exists(_.constraint match {
            case PlannedConstraint.Terms(field, values) => (field eq BeautyQSearchDeclarations.variants.Fields.enumAttributesByCode("lash_service_type")) && values == Set("refill")
            case _ => false
          }))
          assert(intent.hardConstraints.exists(_.constraint match {
            case PlannedConstraint.Terms(field, values) => (field eq BeautyQSearchDeclarations.variants.Fields.booleanAttributesByCode("with_correction")) && values == Set(true)
            case _ => false
          }))
          assert(intent.residualText.isEmpty)
        case Left(errors) => fail(s"parse failed: ${errors.toVector}")
      }
    }

    "resolve token overlap between an independent alias and a noise phrase deterministically (\"не татуаж\")" in {
      BeautyQIntentParserGen2.parse(request(Some("не татуаж")), BeautyQIntentVocabulary.value) match {
        case Right(intent) =>
          // A noise phrase without requirements stays in the independent phase, so the longer
          // negation wins over the nested PMU alias and contributes neither a hard action nor residual text.
          assert(intent.matchedRuleIds == Vector(IntentRuleId("r030")))
          assert(intent.hardConstraints.isEmpty)
          assert(intent.residualText.isEmpty)
        case Left(errors) => fail(s"parse failed: ${errors.toVector}")
      }
    }

    "keep the inherited hair-removal noise rule r076 reachable, never shadowed by the Gen2-only r087, without coordinates (\"hair removal рядом\")" in {
      BeautyQIntentParserGen2.parse(request(Some("hair removal рядом")), BeautyQIntentVocabulary.value) match {
        case Right(intent) =>
          // The hair-removal service (r009) satisfies r076's requirement; r076 and r087 both match the
          // same "рядом" token, but r076 is the earlier source rule and wins the deterministic contextual
          // tie-break, consuming "рядом" as noise. r087 never fires: no NearUser, no signal, no missing-
          // location error even though no coordinates were supplied.
          assert(intent.matchedRuleIds == Vector(IntentRuleId("r009"), IntentRuleId("r076")))
          assert(!intent.matchedRuleIds.contains(IntentRuleId("r087")))
          assert(intent.softSignals.isEmpty)
          assert(intent.hardConstraints.exists(_.constraint match {
            case PlannedConstraint.Terms(field, values) => (field eq BeautyQSearchDeclarations.variants.Fields.serviceCode) && values.map(field.codec.encodeCanonical) == Set("hair_removal")
            case _ => false
          }))
          assert(intent.residualText.isEmpty)
        case Left(errors) => fail(s"parse failed: ${errors.toVector}")
      }
    }

    "keep r076 winning \"рядом\" and produce no NearUser signal even when coordinates are present (\"hair removal рядом\")" in {
      BeautyQIntentParserGen2.parse(request(Some("hair removal рядом"), Some(GeoPoint(BigDecimal("52.5"), BigDecimal("13.4")))), BeautyQIntentVocabulary.value) match {
        case Right(intent) =>
          assert(intent.matchedRuleIds == Vector(IntentRuleId("r009"), IntentRuleId("r076")))
          assert(!intent.matchedRuleIds.contains(IntentRuleId("r087")))
          assert(intent.softSignals.isEmpty)
          assert(intent.residualText.isEmpty)
        case Left(errors) => fail(s"parse failed: ${errors.toVector}")
      }
    }

    "produce exactly one GeoProximitySignal for a general NearUser query with coordinates (\"маникюр рядом\")" in {
      BeautyQIntentParserGen2.parse(request(Some("маникюр рядом"), Some(GeoPoint(BigDecimal("52.5"), BigDecimal("13.4")))), BeautyQIntentVocabulary.value) match {
        case Right(intent) =>
          assert(intent.matchedRuleIds == Vector(IntentRuleId("r001"), IntentRuleId("r087")))
          assert(intent.softSignals.size == 1)
          assert(intent.softSignals.headOption.exists {
            case _: PlannedSignal.GeoProximitySignal[?] => true
            case _ => false
          })
          assert(intent.hardConstraints.exists(_.constraint match {
            case PlannedConstraint.Terms(field, values) => (field eq BeautyQSearchDeclarations.variants.Fields.serviceCode) && values.map(field.codec.encodeCanonical) == Set("manicure")
            case _ => false
          }))
          // no geo hard filter or sort is ever produced by the parser
          assert(intent.hardConstraints.forall {
            case SourcedConstraint(PlannedConstraint.GeoDistanceFilter(_, _, _), _) => false
            case _ => true
          })
          assert(intent.residualText.isEmpty)
        case Left(errors) => fail(s"parse failed: ${errors.toVector}")
      }
    }

    "overlay NearUser on long non-hair-removal aliases that contain the location token" in {
      Vector("дешевый маникюр рядом", "что-то для лица рядом", "недорогие ногти рядом").foreach { query =>
        BeautyQIntentParserGen2.parse(request(Some(query), Some(GeoPoint(BigDecimal("52.5"), BigDecimal("13.4")))), BeautyQIntentVocabulary.value) match {
          case Right(intent) =>
            assert(intent.matchedRuleIds.contains(IntentRuleId("r087")), s"NearUser did not overlay '$query': $intent")
            assert(intent.softSignals.size == 1)
          case Left(errors) => fail(s"parse failed for '$query': ${errors.toVector}")
        }
      }
    }

    "return the typed missing-location error for a general NearUser query without coordinates (\"маникюр рядом\")" in {
      BeautyQIntentParserGen2.parse(request(Some("маникюр рядом")), BeautyQIntentVocabulary.value) match {
        case Left(errors)  => assert(errors.toVector == Vector(BeautyIntentParseError.MissingUserLocationForNearUser))
        case Right(intent) => fail(s"missing location unexpectedly succeeded: $intent")
      }
    }

    "match the semantic-overlay r087 as a pure fallback for a standalone \"рядом\" with no other matched action" in {
      BeautyQIntentParserGen2.parse(request(Some("рядом"), Some(GeoPoint(BigDecimal("52.5"), BigDecimal("13.4")))), BeautyQIntentVocabulary.value) match {
        case Right(intent) =>
          // r087 is a semantic overlay with no requirements, so it still matches when nothing else does.
          assert(intent.matchedRuleIds == Vector(IntentRuleId("r087")))
          assert(intent.softSignals.size == 1)
          assert(intent.residualText.isEmpty)
        case Left(errors) => fail(s"parse failed: ${errors.toVector}")
      }
    }

    "render readable traces without cursor contents" in {
      val validated = validate(BeautySearchRequestGen2(Some("manicure"), Vector.empty, Vector.empty, Vector.empty, page.copy(cursor = None), None))
      val requestTrace = BeautySearchRequestTrace.render(validated)
      assert(requestTrace.contains("request.cursor=absent"))
      BeautyQIntentParserGen2.parse(validated, BeautyQIntentVocabulary.value) match {
        case Right(intent) =>
          val trace = BeautyIntentTrace.render(intent)
          assert(trace.contains("intent.normalized-query=\"manicure\""))
          assert(!trace.contains("SearchField("))
        case Left(errors) => fail(s"parse failed: ${errors.toVector}")
      }
    }

    "map broad self-care query to r088 ServiceAny allowlist and r087 NearUser with geo signal" in {
      BeautyQIntentParserGen2.parse(request(Some("хочу привести себя в порядок рядом"), Some(GeoPoint(BigDecimal("52.5"), BigDecimal("13.4")))), BeautyQIntentVocabulary.value) match {
        case Right(intent) =>
          assert(intent.matchedRuleIds == Vector(IntentRuleId("r088"), IntentRuleId("r087")))
          intent.hardConstraints match {
            case Vector(SourcedConstraint(PlannedConstraint.Terms(field, values), _)) =>
              assert(field eq BeautyQSearchDeclarations.variants.Fields.serviceCode)
              assert(values.map(field.codec.encodeCanonical) == Set("manicure", "lashes", "brows", "facial"))
            case other => fail(s"expected one ServiceAny constraint, got $other")
          }
          intent.softSignals match {
            case Vector(_: PlannedSignal.GeoProximitySignal[?]) => ()
            case other => fail(s"expected one NearUser signal, got $other")
          }
          assert(intent.residualText.isEmpty)
          assert(intent.canonicalSemanticLabels.map(_.stableKey) == Vector("service-any:manicure,lashes,brows,facial", "location:near-user"))
        case Left(errors) => fail(s"parse failed: ${errors.toVector}")
      }
    }

    "map broad self-care query without location to r088 ServiceAny allowlist, no geo signal" in {
      BeautyQIntentParserGen2.parse(request(Some("хочу привести себя в порядок")), BeautyQIntentVocabulary.value) match {
        case Right(intent) =>
          assert(intent.matchedRuleIds == Vector(IntentRuleId("r088")))
          intent.hardConstraints match {
            case Vector(SourcedConstraint(PlannedConstraint.Terms(field, values), _)) =>
              assert(field eq BeautyQSearchDeclarations.variants.Fields.serviceCode)
              assert(values.map(field.codec.encodeCanonical) == Set("manicure", "lashes", "brows", "facial"))
            case other => fail(s"expected one ServiceAny constraint, got $other")
          }
          assert(intent.softSignals.isEmpty)
          assert(intent.residualText.isEmpty)
        case Left(errors) => fail(s"parse failed: ${errors.toVector}")
      }
    }

    "map Russian BB Glow paraphrase to r062 with exact three hard constraints and no PMU" in {
      BeautyQIntentParserGen2.parse(request(Some("хочу чтобы тон лица выглядел ровнее без ежедневного макияжа")), BeautyQIntentVocabulary.value) match {
        case Right(intent) =>
          assert(intent.matchedRuleIds == Vector(IntentRuleId("r062")))
          assert(intent.residualText.isEmpty)
          intent.hardConstraints match {
            case Vector(
                  SourcedConstraint(PlannedConstraint.Terms(serviceField, serviceValues), _),
                  SourcedConstraint(PlannedConstraint.Terms(treatmentField, treatmentValues), _),
                  SourcedConstraint(PlannedConstraint.Terms(areaField, areaValues), _),
                ) =>
              assert(serviceField eq BeautyQSearchDeclarations.variants.Fields.serviceCode)
              assert(serviceValues.map(serviceField.codec.encodeCanonical) == Set("facial"))
              assert(treatmentField eq BeautyQSearchDeclarations.variants.Fields.enumAttributesByCode("facial_treatment_type"))
              assert(treatmentValues == Set("bb_glow"))
              assert(areaField eq BeautyQSearchDeclarations.variants.Fields.enumAttributesByCode("body_area"))
              assert(areaValues == Set("face"))
            case other => fail(s"expected the exact BB Glow constraints, got $other")
          }
          assert(!intent.hardConstraints.exists(_.constraint match {
            case PlannedConstraint.Terms(field, values) => (field eq BeautyQSearchDeclarations.variants.Fields.enumAttributesByCode("pmu_area"))
            case _ => false
          }))
        case Left(errors) => fail(s"parse failed: ${errors.toVector}")
      }
    }

    "map Russian powder-brow paraphrase to r058, longer phrase defeats generic r007" in {
      BeautyQIntentParserGen2.parse(request(Some("брови с мягким пудровым эффектом надолго")), BeautyQIntentVocabulary.value) match {
        case Right(intent) =>
          assert(intent.matchedRuleIds == Vector(IntentRuleId("r058")))
          assert(intent.residualText.isEmpty)
          intent.hardConstraints match {
            case Vector(
                  SourcedConstraint(PlannedConstraint.Terms(serviceField, serviceValues), _),
                  SourcedConstraint(PlannedConstraint.Terms(areaField, areaValues), _),
                ) =>
              assert(serviceField eq BeautyQSearchDeclarations.variants.Fields.serviceCode)
              assert(serviceValues.map(serviceField.codec.encodeCanonical) == Set("pmu"))
              assert(areaField eq BeautyQSearchDeclarations.variants.Fields.enumAttributesByCode("pmu_area"))
              assert(areaValues == Set("brows"))
            case other => fail(s"expected the exact powder-brow constraints, got $other")
          }
          assert(!intent.hardConstraints.exists(_.constraint match {
            case PlannedConstraint.Terms(field, values) => (field eq BeautyQSearchDeclarations.variants.Fields.enumAttributesByCode("brow_service_type"))
            case _ => false
          }))
        case Left(errors) => fail(s"parse failed: ${errors.toVector}")
      }
    }

    "keep plain брови mapping to r007/service brows" in {
      BeautyQIntentParserGen2.parse(request(Some("брови")), BeautyQIntentVocabulary.value) match {
        case Right(intent) =>
          assert(intent.matchedRuleIds == Vector(IntentRuleId("r007")))
          assert(intent.hardConstraints.exists(_.constraint match {
            case PlannedConstraint.Terms(field, values) => (field eq BeautyQSearchDeclarations.variants.Fields.serviceCode) && values.map(field.codec.encodeCanonical) == Set("brows")
            case _ => false
          }))
        case Left(errors) => fail(s"parse failed: ${errors.toVector}")
      }
    }

    "keep plain bb glow mapping to r062" in {
      BeautyQIntentParserGen2.parse(request(Some("bb glow")), BeautyQIntentVocabulary.value) match {
        case Right(intent) =>
          assert(intent.matchedRuleIds == Vector(IntentRuleId("r062")))
        case Left(errors) => fail(s"parse failed: ${errors.toVector}")
      }
    }

    "keep plain powder brows mapping to r058" in {
      BeautyQIntentParserGen2.parse(request(Some("powder brows")), BeautyQIntentVocabulary.value) match {
        case Right(intent) =>
          assert(intent.matchedRuleIds == Vector(IntentRuleId("r058")))
        case Left(errors) => fail(s"parse failed: ${errors.toVector}")
      }
    }

    "merge same-slot parsed Terms for manicure and pedicure without changing rule order" in {
      BeautyQIntentParserGen2.parse(request(Some("маникюр педикюр")), BeautyQIntentVocabulary.value) match {
        case Right(intent) =>
          assert(intent.matchedRuleIds == Vector(IntentRuleId("r001"), IntentRuleId("r004")))
          intent.hardConstraints match {
            case Vector(
                  SourcedConstraint(PlannedConstraint.Terms(serviceField, serviceValues), serviceProvenance),
                  SourcedConstraint(PlannedConstraint.Terms(nailField, nailValues), nailProvenance),
                ) =>
              assert(serviceField.id == BeautyQSearchDeclarations.variants.Fields.serviceCode.id)
              assert(serviceValues.map(serviceField.codec.encodeCanonical) == Set("manicure", "pedicure"))
              assert(serviceProvenance == ConstraintProvenance.ParsedHard)
              assert(nailField.id == BeautyQSearchDeclarations.variants.Fields.enumAttributesByCode("nail_service_type").id)
              assert(nailValues.map(nailField.codec.encodeCanonical) == Set("manicure", "pedicure"))
              assert(nailProvenance == ConstraintProvenance.ParsedHard)
              assert(Vector(serviceField.id, nailField.id).distinct.size == 2)
            case other => fail(s"expected two merged parsed Terms constraints, got $other")
          }
          assert(intent.residualText.isEmpty)
        case Left(errors) => fail(s"parse failed: ${errors.toVector}")
      }
    }

    "preserve first parsed slot order and keep a budget IntervalOverlap unchanged" in {
      BeautyQIntentParserGen2.parse(request(Some("маникюр педикюр under 50")), BeautyQIntentVocabulary.value) match {
        case Right(intent) =>
          assert(intent.matchedRuleIds == Vector(IntentRuleId("r001"), IntentRuleId("r004")))
          intent.hardConstraints match {
            case Vector(
                  SourcedConstraint(PlannedConstraint.Terms(serviceField, serviceValues), serviceProvenance),
                  SourcedConstraint(PlannedConstraint.Terms(nailField, nailValues), nailProvenance),
                  SourcedConstraint(PlannedConstraint.IntervalOverlap(from, to, bounds), budgetProvenance),
                ) =>
              assert(serviceField.id == BeautyQSearchDeclarations.variants.Fields.serviceCode.id)
              assert(serviceValues.map(serviceField.codec.encodeCanonical) == Set("manicure", "pedicure"))
              assert(serviceProvenance == ConstraintProvenance.ParsedHard)
              assert(nailField.id == BeautyQSearchDeclarations.variants.Fields.enumAttributesByCode("nail_service_type").id)
              assert(nailValues.map(nailField.codec.encodeCanonical) == Set("manicure", "pedicure"))
              assert(nailProvenance == ConstraintProvenance.ParsedHard)
              assert(from.id == BeautyQSearchDeclarations.variants.Fields.priceFrom.id)
              assert(to.id == BeautyQSearchDeclarations.variants.Fields.priceTo.id)
              assert(bounds == RangeBounds(Bound.Unbounded, Bound.Inclusive(BigDecimal(50))))
              assert(budgetProvenance == ConstraintProvenance.ParsedHard)
            case other => fail(s"expected merged Terms followed by the original budget constraint, got $other")
          }
          assert(intent.residualText.isEmpty)
        case Left(errors) => fail(s"parse failed: ${errors.toVector}")
      }
    }
  }

  // Independent evidence, not derived from BeautyIntentTrace or any other production traversal helper:
  // each golden string below was authored by hand from the mechanics verified in the scenario tests
  // above, then confirmed to match production output byte-for-byte. Must fail the moment normalization,
  // matching, hard constraints, signals, residual text or labels change for any of these eight queries.
  "BeautyIntentTrace golden traces" should {
    "render the exact golden trace for every required scenario" in {
      val scenarios: Vector[(String, Option[GeoPoint])] =
        Vector(
          "маникюр under 50" -> None,
          "наращивание ногтей gel" -> None,
          "lashes and brows volume2_d" -> None,
          "реснички 2д корр" -> None,
          "volume2_d Wandsbek lashes" -> None,
          "volume2_d lashes extension" -> None,
          "увлажнение лица face" -> None,
          "manicure nearby" -> Some(GeoPoint(BigDecimal("52.5"), BigDecimal("13.4"))),
        )

      val expected =
        Vector(
          Vector(
            "intent.normalized-query=\"маникюр\"",
            "intent.matched-rules=[r001]",
            "intent.hard[0] provenance=ParsedHard constraint.terms field=serviceCode:ServiceCode values=[manicure]",
            "intent.hard[1] provenance=ParsedHard constraint.terms field=enumAttributes.nail_service_type:string values=[manicure]",
            "intent.hard[2] provenance=ParsedHard constraint.interval-overlap from=priceFrom:decimal to=priceTo:decimal bounds=[unbounded, inclusive(50)]",
            "intent.residual=absent",
            "intent.labels=[service:manicure:manicure,attribute.enum:nail_service_type=manicure:nail service type: manicure]",
          ).mkString("\n"),
          Vector(
            "intent.normalized-query=\"наращивание ногтей gel\"",
            "intent.matched-rules=[r005,r032]",
            "intent.hard[0] provenance=ParsedHard constraint.terms field=serviceCode:ServiceCode values=[nail_modeling]",
            "intent.hard[1] provenance=ParsedHard constraint.terms field=enumAttributes.nail_service_type:string values=[extension]",
            "intent.hard[2] provenance=ParsedHard constraint.terms field=enumAttributes.nail_coating_type:string values=[gel]",
            "intent.residual=absent",
            "intent.labels=[service:nail_modeling:nail modeling,attribute.enum:nail_service_type=extension:nail service type: extension,attribute.enum:nail_coating_type=gel:nail coating type: gel]",
          ).mkString("\n"),
          Vector(
            "intent.normalized-query=\"lashes and brows volume2_d\"",
            "intent.matched-rules=[r018,r035]",
            "intent.hard[0] provenance=ParsedHard constraint.terms field=serviceCode:ServiceCode values=[brows, lashes]",
            "intent.hard[1] provenance=ParsedHard constraint.terms field=enumAttributes.lash_volume:string values=[volume2_d]",
            "intent.residual=absent",
            "intent.labels=[service-any:lashes,brows:lashes, brows,attribute.enum:lash_volume=volume2_d:lash volume: volume2 d]",
          ).mkString("\n"),
          Vector(
            "intent.normalized-query=\"реснички 2д корр\"",
            "intent.matched-rules=[r023]",
            "intent.hard[0] provenance=ParsedHard constraint.terms field=serviceCode:ServiceCode values=[lashes]",
            "intent.hard[1] provenance=ParsedHard constraint.terms field=enumAttributes.lash_volume:string values=[volume2_d]",
            "intent.hard[2] provenance=ParsedHard constraint.terms field=enumAttributes.lash_service_type:string values=[refill]",
            "intent.hard[3] provenance=ParsedHard constraint.terms field=booleanAttributes.with_correction:boolean values=[true]",
            "intent.residual=absent",
            "intent.labels=[service:lashes:lashes,attribute.enum:lash_volume=volume2_d:lash volume: volume2 d,attribute.enum:lash_service_type=refill:lash service type: refill,attribute.boolean:with_correction=true:with correction: true]",
          ).mkString("\n"),
          Vector(
            "intent.normalized-query=\"volume2_d wandsbek lashes\"",
            "intent.matched-rules=[r035,r006]",
            "intent.hard[0] provenance=ParsedHard constraint.terms field=enumAttributes.lash_volume:string values=[volume2_d]",
            "intent.hard[1] provenance=ParsedHard constraint.terms field=serviceCode:ServiceCode values=[lashes]",
            "intent.residual=wandsbek",
            "intent.labels=[attribute.enum:lash_volume=volume2_d:lash volume: volume2 d,service:lashes:lashes]",
          ).mkString("\n"),
          Vector(
            "intent.normalized-query=\"volume2_d lashes extension\"",
            "intent.matched-rules=[r035,r006,r038]",
            "intent.hard[0] provenance=ParsedHard constraint.terms field=enumAttributes.lash_volume:string values=[volume2_d]",
            "intent.hard[1] provenance=ParsedHard constraint.terms field=serviceCode:ServiceCode values=[lashes]",
            "intent.hard[2] provenance=ParsedHard constraint.terms field=enumAttributes.lash_service_type:string values=[extension]",
            "intent.residual=absent",
            "intent.labels=[attribute.enum:lash_volume=volume2_d:lash volume: volume2 d,service:lashes:lashes,attribute.enum:lash_service_type=extension:lash service type: extension]",
          ).mkString("\n"),
          Vector(
            "intent.normalized-query=\"увлажнение лица face\"",
            "intent.matched-rules=[r065]",
            "intent.hard[0] provenance=ParsedHard constraint.terms field=serviceCode:ServiceCode values=[facial]",
            "intent.hard[1] provenance=ParsedHard constraint.terms field=enumAttributes.facial_treatment_type:string values=[hydration]",
            "intent.hard[2] provenance=ParsedHard constraint.terms field=enumAttributes.body_area:string values=[face_neck_decollete]",
            "intent.residual=face",
            "intent.labels=[service:facial:facial,attribute.enum:facial_treatment_type=hydration:facial treatment type: hydration,attribute.enum:body_area=face_neck_decollete:body area: face neck decollete]",
          ).mkString("\n"),
          Vector(
            "intent.normalized-query=\"manicure nearby\"",
            "intent.matched-rules=[r001,r087]",
            "intent.hard[0] provenance=ParsedHard constraint.terms field=serviceCode:ServiceCode values=[manicure]",
            "intent.hard[1] provenance=ParsedHard constraint.terms field=enumAttributes.nail_service_type:string values=[manicure]",
            "intent.soft[0] signal.geo-proximity field=location:geo-point origin=52.5,13.4",
            "intent.residual=absent",
            "intent.labels=[service:manicure:manicure,attribute.enum:nail_service_type=manicure:nail service type: manicure,location:near-user:near user]",
          ).mkString("\n"),
        )

      val actual =
        scenarios.map { case (query, location) =>
          BeautyQIntentParserGen2.parse(request(Some(query), location), BeautyQIntentVocabulary.value) match {
            case Right(intent) => BeautyIntentTrace.render(intent)
            case Left(errors)  => fail(s"parse failed for '$query': ${errors.toVector}")
          }
        }

      assert(actual == expected)
    }

    "derive typed hard constraints for every disclosed exact-intent language form" in {
      val fields = BeautyQSearchDeclarations.variants.Fields
      val cases = Vector(
        ("Gel-Maniküre mit Entfernung und schlichtem Finish", Set(fields.serviceCode.id -> Set("manicure"), fields.enumAttributesByCode("nail_service_type").id -> Set("manicure"), fields.enumAttributesByCode("nail_coating_type").id -> Set("gel_polish"))),
        ("маникюр с гель-лаком", Set(fields.serviceCode.id -> Set("manicure"), fields.enumAttributesByCode("nail_service_type").id -> Set("manicure"), fields.enumAttributesByCode("nail_coating_type").id -> Set("gel_polish"))),
        ("Pediküre für gepflegte Zehennägel", Set(fields.serviceCode.id -> Set("pedicure"), fields.enumAttributesByCode("nail_service_type").id -> Set("pedicure"))),
        ("lash lift und wimpern styling", Set(fields.serviceCode.id -> Set("lashes"), fields.enumAttributesByCode("lash_service_type").id -> Set("lifting"))),
        ("facial cleansing and skin care", Set(fields.serviceCode.id -> Set("facial"), fields.enumAttributesByCode("facial_treatment_type").id -> Set("cleansing"), fields.enumAttributesByCode("body_area").id -> Set("face"))),
        ("пудровый перманент бровей", Set(fields.serviceCode.id -> Set("pmu"), fields.enumAttributesByCode("pmu_area").id -> Set("brows"))),
        ("hair removal for underarms", Set(fields.serviceCode.id -> Set("hair_removal"), fields.enumAttributesByCode("body_area").id -> Set("armpits"))),
      )

      cases.foreach { case (query, expected) =>
        BeautyQIntentParserGen2.parse(request(Some(query)), BeautyQIntentVocabulary.value) match {
          case Right(intent) =>
            val actual = intent.hardConstraints.collect {
              case SourcedConstraint(PlannedConstraint.Terms(field, values), ConstraintProvenance.ParsedHard) =>
                field.id -> values.map(field.codec.encodeCanonical)
            }.toSet
            assert(actual == expected, s"unexpected parsed constraints for '$query': $actual")
          case Left(errors) => fail(s"parse failed for '$query': ${errors.toVector}")
        }
      }
    }

    "derive exact typed constraints for every second-cycle migrated exact-intent form" in {
      val fields = BeautyQSearchDeclarations.variants.Fields
      val cases = Vector(
        (
          "Ищу педикюр без какого-либо покрытия",
          Vector("r004", "r031"),
          Set(
            fields.serviceCode.id -> Set("pedicure"),
            fields.enumAttributesByCode("nail_service_type").id -> Set("pedicure"),
            fields.enumAttributesByCode("nail_coating_type").id -> Set("no_coating"),
          ),
        ),
        (
          "Хочу аккуратно снять наращённые ресницы",
          Vector("r090", "r006"),
          Set(
            fields.serviceCode.id -> Set("lashes"),
            fields.enumAttributesByCode("lash_service_type").id -> Set("removal"),
            fields.booleanAttributesByCode("with_removal").id -> Set("true"),
          ),
        ),
        (
          "Нужно ламинирование бровей вместе с окрашиванием",
          Vector("r046", "r007", "r053"),
          Set(
            fields.serviceCode.id -> Set("brows"),
            fields.enumAttributesByCode("brow_service_type").id -> Set("lamination"),
            fields.booleanAttributesByCode("with_tinting").id -> Set("true"),
          ),
        ),
        (
          "Нужна процедура аквафейшл для лица",
          Vector("r060"),
          Set(
            fields.serviceCode.id -> Set("facial"),
            fields.enumAttributesByCode("facial_treatment_type").id -> Set("aquafacial"),
            fields.enumAttributesByCode("body_area").id -> Set("face"),
          ),
        ),
      )

      cases.foreach { case (query, expectedRules, expectedConstraints) =>
        BeautyQIntentParserGen2.parse(request(Some(query)), BeautyQIntentVocabulary.value) match {
          case Right(intent) =>
            assert(intent.matchedRuleIds.map(_.value) == expectedRules, s"unexpected rules for '$query'")
            val actual = intent.hardConstraints.collect {
              case SourcedConstraint(PlannedConstraint.Terms(field, values), ConstraintProvenance.ParsedHard) =>
                field.id -> values.map(field.codec.encodeCanonical)
            }.toSet
            assert(actual == expectedConstraints, s"unexpected parsed constraints for '$query': $actual")
            assert(intent.hardConstraints.size == expectedConstraints.size)
          case Left(errors) => fail(s"parse failed for '$query': ${errors.toVector}")
        }
      }
    }

    "generalize each repaired semantic class to independently authored visible paraphrases" in {
      val fields = BeautyQSearchDeclarations.variants.Fields
      val cases = Vector(
        "педикюр без любого покрытия" -> (fields.enumAttributesByCode("nail_coating_type").id -> Set("no_coating")),
        "хочу педикюр совсем без покрытия" -> (fields.enumAttributesByCode("nail_coating_type").id -> Set("no_coating")),
        "снять старые ресницы" -> (fields.enumAttributesByCode("lash_service_type").id -> Set("removal")),
        "пожалуйста снять накладные ресницы" -> (fields.enumAttributesByCode("lash_service_type").id -> Set("removal")),
        "ламинирование бровей с окраской" -> (fields.booleanAttributesByCode("with_tinting").id -> Set("true")),
        "хочу окрашивание и ламинирование бровей" -> (fields.enumAttributesByCode("brow_service_type").id -> Set("lamination")),
        "аквафэйшл для кожи" -> (fields.enumAttributesByCode("facial_treatment_type").id -> Set("aquafacial")),
        "процедуру аква-фейшл для лица" -> (fields.enumAttributesByCode("facial_treatment_type").id -> Set("aquafacial")),
      )

      cases.foreach { case (query, (expectedField, expectedValues)) =>
        BeautyQIntentParserGen2.parse(request(Some(query)), BeautyQIntentVocabulary.value) match {
          case Right(intent) =>
            assert(
              intent.hardConstraints.exists {
                case SourcedConstraint(PlannedConstraint.Terms(field, values), ConstraintProvenance.ParsedHard) =>
                  field.id == expectedField && values.map(field.codec.encodeCanonical) == expectedValues
                case _ => false
              },
              s"missing generalized constraint for '$query': ${intent.hardConstraints}",
            )
          case Left(errors) => fail(s"parse failed for '$query': ${errors.toVector}")
        }
      }
    }

    "derive exact typed constraints for the complete migrated exact-intent closure slice" in {
      val fields = BeautyQSearchDeclarations.variants.Fields
      val cases = Vector(
        "Нужен уход для рук с гель-лаком без дизайна" -> Set(
          fields.serviceCode.id -> Set("manicure"),
          fields.enumAttributesByCode("nail_service_type").id -> Set("manicure"),
          fields.enumAttributesByCode("nail_coating_type").id -> Set("gel_polish"),
          fields.booleanAttributesByCode("with_design").id -> Set("false"),
        ),
        "оформление и коррекция бровей" -> Set(
          fields.serviceCode.id -> Set("brows"),
          fields.enumAttributesByCode("brow_service_type").id -> Set("shaping"),
        ),
        "Ищу перманент губ с последующей коррекцией" -> Set(
          fields.serviceCode.id -> Set("pmu"),
          fields.enumAttributesByCode("pmu_area").id -> Set("lips"),
          fields.booleanAttributesByCode("with_correction").id -> Set("true"),
        ),
        "Ищу лазерное удаление волос в зоне подмышек" -> Set(
          fields.serviceCode.id -> Set("hair_removal"),
          fields.enumAttributesByCode("hair_removal_method").id -> Set("laser"),
          fields.enumAttributesByCode("body_area").id -> Set("armpits"),
        ),
        "Fußnägel mit Gel-Farbe behandeln" -> Set(
          fields.serviceCode.id -> Set("pedicure"),
          fields.enumAttributesByCode("nail_service_type").id -> Set("pedicure"),
          fields.enumAttributesByCode("nail_coating_type").id -> Set("gel_polish"),
        ),
        "нужен уход за руками без цветного покрытия" -> Set(
          fields.serviceCode.id -> Set("manicure"),
          fields.enumAttributesByCode("nail_service_type").id -> Set("manicure"),
          fields.enumAttributesByCode("nail_coating_type").id -> Set("no_coating"),
        ),
        "hand nail care with ordinary polish" -> Set(
          fields.serviceCode.id -> Set("manicure"),
          fields.enumAttributesByCode("nail_service_type").id -> Set("manicure"),
          fields.enumAttributesByCode("nail_coating_type").id -> Set("regular_polish"),
        ),
        "künstliche Nägel aus Acryl verlängern" -> Set(
          fields.serviceCode.id -> Set("nail_modeling"),
          fields.enumAttributesByCode("nail_service_type").id -> Set("extension"),
          fields.enumAttributesByCode("nail_coating_type").id -> Set("acrylic"),
        ),
      )

      cases.foreach { case (query, expected) =>
        BeautyQIntentParserGen2.parse(request(Some(query)), BeautyQIntentVocabulary.value) match {
          case Right(intent) =>
            val actual = intent.hardConstraints.collect {
              case SourcedConstraint(PlannedConstraint.Terms(field, values), ConstraintProvenance.ParsedHard) =>
                field.id -> values.map(field.codec.encodeCanonical)
            }.toSet
            assert(actual == expected, s"unexpected closure constraints for '$query': $actual")
          case Left(errors) => fail(s"parse failed for '$query': ${errors.toVector}")
        }
      }
    }

    "generalize every closure semantic class to exact independently authored visible constraints" in {
      val fields = BeautyQSearchDeclarations.variants.Fields
      val cases = Vector(
        "care for hands with regular polish" -> Set(
          fields.serviceCode.id -> Set("manicure"),
          fields.enumAttributesByCode("nail_service_type").id -> Set("manicure"),
          fields.enumAttributesByCode("nail_coating_type").id -> Set("regular_polish"),
        ),
        "уход за руками с обычным лаком" -> Set(
          fields.serviceCode.id -> Set("manicure"),
          fields.enumAttributesByCode("nail_service_type").id -> Set("manicure"),
          fields.enumAttributesByCode("nail_coating_type").id -> Set("regular_polish"),
        ),
        "foot nail care with gel polish" -> Set(
          fields.serviceCode.id -> Set("pedicure"),
          fields.enumAttributesByCode("nail_service_type").id -> Set("pedicure"),
          fields.enumAttributesByCode("nail_coating_type").id -> Set("gel_polish"),
        ),
        "Pflege der Fußnägel mit Gel-Lack" -> Set(
          fields.serviceCode.id -> Set("pedicure"),
          fields.enumAttributesByCode("nail_service_type").id -> Set("pedicure"),
          fields.enumAttributesByCode("nail_coating_type").id -> Set("gel_polish"),
        ),
        "artificial nails acrylic" -> Set(
          fields.serviceCode.id -> Set("nail_modeling"),
          fields.enumAttributesByCode("nail_service_type").id -> Set("extension"),
          fields.enumAttributesByCode("nail_coating_type").id -> Set("acrylic"),
        ),
        "künstliche Nägel Acryl" -> Set(
          fields.serviceCode.id -> Set("nail_modeling"),
          fields.enumAttributesByCode("nail_service_type").id -> Set("extension"),
          fields.enumAttributesByCode("nail_coating_type").id -> Set("acrylic"),
        ),
        "маникюр без дизайна" -> Set(
          fields.serviceCode.id -> Set("manicure"),
          fields.enumAttributesByCode("nail_service_type").id -> Set("manicure"),
          fields.booleanAttributesByCode("with_design").id -> Set("false"),
        ),
        "маникюр without design" -> Set(
          fields.serviceCode.id -> Set("manicure"),
          fields.enumAttributesByCode("nail_service_type").id -> Set("manicure"),
          fields.booleanAttributesByCode("with_design").id -> Set("false"),
        ),
      )

      cases.foreach { case (query, expected) =>
        BeautyQIntentParserGen2.parse(request(Some(query)), BeautyQIntentVocabulary.value) match {
          case Right(intent) =>
            val actual = intent.hardConstraints.collect {
              case SourcedConstraint(PlannedConstraint.Terms(field, values), ConstraintProvenance.ParsedHard) =>
                field.id -> values.map(field.codec.encodeCanonical)
            }.toSet
            assert(actual == expected, s"unexpected closure semantic class for '$query': $actual")
            assert(intent.hardConstraints.size == expected.size)
          case Left(errors) => fail(s"parse failed for '$query': ${errors.toVector}")
        }
      }
    }

    "keep no-design contextual to the catalog-owned nail service family" in {
      val fields = BeautyQSearchDeclarations.variants.Fields
      val cases = Vector(
        ("facial without design", "without design", Set(fields.serviceCode.id -> Set("facial"))),
        ("брови без дизайна", "без дизайна", Set(fields.serviceCode.id -> Set("brows"))),
        ("lashes ohne design", "ohne design", Set(fields.serviceCode.id -> Set("lashes"))),
      )

      cases.foreach { case (query, expectedResidual, expected) =>
        BeautyQIntentParserGen2.parse(request(Some(query)), BeautyQIntentVocabulary.value) match {
          case Right(intent) =>
            val actual = intent.hardConstraints.collect {
              case SourcedConstraint(PlannedConstraint.Terms(field, values), ConstraintProvenance.ParsedHard) =>
                field.id -> values.map(field.codec.encodeCanonical)
            }.toSet
            assert(actual == expected, s"no-design leaked outside nail context for '$query': $actual")
            assert(!intent.matchedRuleIds.contains(IntentRuleId("r092")))
            assert(intent.residualText.contains(expectedResidual))
          case Left(errors) => fail(s"parse failed for '$query': ${errors.toVector}")
        }
      }
    }

    "keep explicit regular-polish semantics valid as a standalone nail attribute" in {
      val fields = BeautyQSearchDeclarations.variants.Fields
      BeautyQIntentParserGen2.parse(request(Some("regular polish")), BeautyQIntentVocabulary.value) match {
        case Right(intent) =>
          assert(intent.matchedRuleIds == Vector(IntentRuleId("r091")))
          assert(intent.hardConstraints match {
            case Vector(SourcedConstraint(PlannedConstraint.Terms(field, values), ConstraintProvenance.ParsedHard)) =>
              field.id == fields.enumAttributesByCode("nail_coating_type").id &&
                values.map(field.codec.encodeCanonical) == Set("regular_polish")
            case _ => false
          })
          assert(intent.residualText.isEmpty)
        case Left(errors) => fail(s"parse failed: ${errors.toVector}")
      }
    }

    "preserve action and relation words outside declaration-owned phrase matches" in {
      val cases = Vector(
        "Fußnägel mit Gel-Farbe behandeln" -> "mit behandeln",
        "künstliche Nägel aus Acryl verlängern" -> "aus verlängern",
      )
      cases.foreach { case (query, expectedResidual) =>
        BeautyQIntentParserGen2.parse(request(Some(query)), BeautyQIntentVocabulary.value) match {
          case Right(intent) => assert(intent.residualText.contains(expectedResidual))
          case Left(errors)  => fail(s"parse failed for '$query': ${errors.toVector}")
        }
      }
    }
  }

  "the provenance trust boundary" should {
    "reject direct construction of ValidatedBeautySearchRequestGen2 from outside the contract package, at compile time" in {
      assertDoesNotCompile("""ValidatedBeautySearchRequestGen2(None, Vector.empty, Vector.empty, Vector.empty, page, None)""")
    }

    "reject direct construction of DecodedPublicFilter from outside the contract package, at compile time" in {
      assertDoesNotCompile(
        """DecodedPublicFilter(BeautyPublicFilterClause.GeoRadius(BeautyQSearchDeclarations.variants.Fields.location, Distance(500)), ConstraintProvenance.ParsedHard)"""
      )
    }
  }
}
