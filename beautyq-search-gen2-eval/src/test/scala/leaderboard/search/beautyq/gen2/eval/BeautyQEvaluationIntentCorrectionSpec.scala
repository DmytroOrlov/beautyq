package leaderboard.search.beautyq.gen2.eval

import leaderboard.search.beautyq.gen2.contract.*
import leaderboard.search.beautyq.gen2.wiring.*
import leaderboard.search.gen2.contract.*
import leaderboard.search.gen2.eval.*
import org.scalatest.wordspec.AnyWordSpec

final class BeautyQEvaluationIntentCorrectionSpec extends AnyWordSpec {

  private val Fields = BeautyQSearchDeclarations.variants.Fields

  private lazy val corpus: BeautyQEvaluationCorpus =
    BeautyQEvaluationCorpus.loadCanonical().getOrElse(fail("failed to load canonical corpus"))

  private def caseById(id: String): CorpusCase =
    corpus.cases.find(_.caseId.value == id).getOrElse(fail(s"case $id not found"))

  private def exactlyOneCase(id: String): CorpusCase = {
    corpus.cases.filter(_.caseId.value == id) match {
      case Vector(value) => value
      case other         => fail(s"expected exactly one case $id, found ${other.size}")
    }
  }

  private def parseFromCorpus(c: CorpusCase): ParsedBeautyIntentGen2 = {
    val request = BeautyQEvaluationRequestFactory.fromCase(corpus, c) match {
      case Right(req) => req
      case Left(error) => fail(s"request construction failed for ${c.caseId.value}: $error")
    }
    val validated = BeautySearchRequestGen2.validate(request) match {
      case Right(v) => v
      case Left(errors) => fail(s"validation failed for ${c.caseId.value}: ${errors.toVector}")
    }
    BeautyQIntentParserGen2.parse(validated, BeautyQIntentVocabulary.value) match {
      case Right(intent) => intent
      case Left(errors) => fail(s"parse failed for ${c.caseId.value}: ${errors.toVector}")
    }
  }

  "canonical corpus" should {
    "have exactly 132 cases after the recovery-rotation-2 exact-intent migration" in {
      assert(corpus.cases.length == 132)
    }

    "have every case as Regression" in {
      corpus.cases.foreach(c => assert(c.partition == EvaluationPartition.Regression))
    }

    "have no protected holdout" in {
      assert(!corpus.cases.exists(_.partition == EvaluationPartition.ProtectedHoldout))
    }

    "have a stable fingerprint across two loads" in {
      val corpus2 = BeautyQEvaluationCorpus.loadCanonical().getOrElse(fail("failed to reload corpus"))
      assert(corpus.corpusFingerprint == corpus2.corpusFingerprint)
    }

    "contain all three correction regression cases" in {
      val qBroad004 = exactlyOneCase("q_broad_004")
      val qHoldoutFace = exactlyOneCase("q_holdout_face_bb_glow_001")
      val qHoldoutPowder = exactlyOneCase("q_holdout_pmu_powder_brows_001")
      assert(Vector(qBroad004, qHoldoutFace, qHoldoutPowder).forall(_.partition == EvaluationPartition.Regression))
      assert(qBroad004.query == "хочу привести себя в порядок рядом")
      assert(qHoldoutFace.query == "хочу чтобы тон лица выглядел ровнее без ежедневного макияжа")
      assert(qHoldoutPowder.query == "брови с мягким пудровым эффектом надолго")
    }
  }

  "request factory" should {
    "use the corpus default location for request construction" in {
      val c = caseById("q_broad_004")
      BeautyQEvaluationRequestFactory.fromCase(corpus, c) match {
        case Right(request) =>
          request.userLocation match {
            case Some(location) =>
              assert(location.lat == BigDecimal(corpus.defaultUserLocation.lat.toString))
              assert(location.lon == BigDecimal(corpus.defaultUserLocation.lon.toString))
            case None => fail("expected the corpus default location")
          }
        case Left(error) => fail(s"expected request, got $error")
      }
    }
  }

  "q_broad_004 broad self-care correction" should {
    "parse to r088 then r087 with ServiceAny hard constraint and geo signal" in {
      val intent = parseFromCorpus(caseById("q_broad_004"))
      assert(intent.matchedRuleIds == Vector(IntentRuleId("r088"), IntentRuleId("r087")))
      intent.hardConstraints match {
        case Vector(SourcedConstraint(PlannedConstraint.Terms(field, values), _)) =>
          assert(field eq Fields.serviceCode)
          assert(values.map(field.codec.encodeCanonical) == Set("manicure", "lashes", "brows", "facial"))
        case other => fail(s"expected one ServiceAny-derived constraint, got $other")
      }
      intent.softSignals match {
        case Vector(_: PlannedSignal.GeoProximitySignal[?]) => ()
        case other => fail(s"expected one NearUser geo signal, got $other")
      }
      assert(intent.residualText.isEmpty)
    }
  }

  "q_holdout_face_bb_glow_001 BB Glow correction" should {
    "parse to r062 with three exact hard constraints" in {
      val intent = parseFromCorpus(caseById("q_holdout_face_bb_glow_001"))
      assert(intent.matchedRuleIds == Vector(IntentRuleId("r062")))
      intent.hardConstraints match {
        case Vector(
              SourcedConstraint(PlannedConstraint.Terms(serviceField, serviceValues), _),
              SourcedConstraint(PlannedConstraint.Terms(treatmentField, treatmentValues), _),
              SourcedConstraint(PlannedConstraint.Terms(areaField, areaValues), _),
            ) =>
          assert(serviceField eq Fields.serviceCode)
          assert(serviceValues.map(serviceField.codec.encodeCanonical) == Set("facial"))
          assert(treatmentField eq Fields.enumAttributesByCode("facial_treatment_type"))
          assert(treatmentValues == Set("bb_glow"))
          assert(areaField eq Fields.enumAttributesByCode("body_area"))
          assert(areaValues == Set("face"))
        case other => fail(s"expected the exact BB Glow constraints, got $other")
      }
      assert(intent.residualText.isEmpty)
    }
  }

  "q_holdout_pmu_powder_brows_001 powder-brow correction" should {
    "parse to r058 with two exact hard constraints, defeating generic r007" in {
      val intent = parseFromCorpus(caseById("q_holdout_pmu_powder_brows_001"))
      assert(intent.matchedRuleIds == Vector(IntentRuleId("r058")))
      intent.hardConstraints match {
        case Vector(
              SourcedConstraint(PlannedConstraint.Terms(serviceField, serviceValues), _),
              SourcedConstraint(PlannedConstraint.Terms(areaField, areaValues), _),
            ) =>
          assert(serviceField eq Fields.serviceCode)
          assert(serviceValues.map(serviceField.codec.encodeCanonical) == Set("pmu"))
          assert(areaField eq Fields.enumAttributesByCode("pmu_area"))
          assert(areaValues == Set("brows"))
        case other => fail(s"expected the exact powder-brow constraints, got $other")
      }
      assert(intent.residualText.isEmpty)
    }
  }

  "q2 convergence migrated cases" should {
    "retain exact typed parser constraints for all eight visible migrations" in {
      val cases = Vector(
        "q2i5_reserve_001" -> Set(
          Fields.serviceCode.id -> Set("manicure"),
          Fields.enumAttributesByCode("nail_service_type").id -> Set("manicure"),
        ),
        "q2i5_reserve_004" -> Set(
          Fields.serviceCode.id -> Set("manicure"),
          Fields.enumAttributesByCode("nail_service_type").id -> Set("manicure"),
          Fields.enumAttributesByCode("nail_coating_type").id -> Set("gel_polish"),
        ),
        "q2i5_reserve_007" -> Set(
          Fields.serviceCode.id -> Set("manicure", "pedicure"),
          Fields.enumAttributesByCode("nail_service_type").id -> Set("manicure", "pedicure"),
        ),
        "q2i5_reserve_010" -> Set(
          Fields.serviceCode.id -> Set("facial"),
          Fields.enumAttributesByCode("facial_treatment_type").id -> Set("bb_glow"),
          Fields.enumAttributesByCode("body_area").id -> Set("face"),
        ),
        "q2i5_reserve_013" -> Set(
          Fields.serviceCode.id -> Set("manicure"),
          Fields.enumAttributesByCode("nail_service_type").id -> Set("manicure"),
        ),
        "q2i5_reserve_016" -> Set(
          Fields.serviceCode.id -> Set("manicure"),
          Fields.enumAttributesByCode("nail_service_type").id -> Set("manicure"),
          Fields.enumAttributesByCode("nail_coating_type").id -> Set("gel_polish"),
        ),
        "q2i5_reserve_019" -> Set(
          Fields.serviceCode.id -> Set("facial"),
          Fields.enumAttributesByCode("facial_treatment_type").id -> Set("cleansing"),
          Fields.enumAttributesByCode("body_area").id -> Set("face"),
        ),
        "q2i5_reserve_022" -> Set(
          Fields.serviceCode.id -> Set("lashes"),
          Fields.enumAttributesByCode("lash_service_type").id -> Set("removal"),
          Fields.booleanAttributesByCode("with_removal").id -> Set("true"),
        ),
      )

      cases.foreach { case (id, expected) =>
        val intent = parseFromCorpus(exactlyOneCase(id))
        val actual = intent.hardConstraints.collect {
          case SourcedConstraint(PlannedConstraint.Terms(field, values), ConstraintProvenance.ParsedHard) =>
            field.id -> values.map(field.codec.encodeCanonical)
        }.toSet
        assert(actual == expected, s"unexpected convergence constraints for '$id': $actual")
        assert(intent.hardConstraints.size == expected.size)
      }
    }
  }

  "q2 post-recovery migrated cases" should {
    "retain exact typed parser constraints for all sixteen bindable post-recovery migrations" in {
      val cases = Vector(
        "q2i7_recovery_001" -> Set(
          Fields.serviceCode.id -> Set("manicure"),
          Fields.enumAttributesByCode("nail_service_type").id -> Set("manicure"),
          Fields.enumAttributesByCode("nail_coating_type").id -> Set("gel_polish"),
        ),
        "q2i7_recovery_005" -> Set(
          Fields.serviceCode.id -> Set("pedicure"),
          Fields.enumAttributesByCode("nail_service_type").id -> Set("pedicure"),
          Fields.enumAttributesByCode("nail_coating_type").id -> Set("regular_polish"),
        ),
        "q2i7_recovery_009" -> Set(
          Fields.serviceCode.id -> Set("lashes"),
          Fields.enumAttributesByCode("lash_volume").id -> Set("classic1_d"),
          Fields.enumAttributesByCode("lash_service_type").id -> Set("extension"),
        ),
        "q2i7_recovery_013" -> Set(
          Fields.serviceCode.id -> Set("brows"),
          Fields.enumAttributesByCode("brow_service_type").id -> Set("lamination"),
          Fields.booleanAttributesByCode("with_tinting").id -> Set("true"),
        ),
        "q2i7_recovery_017" -> Set(
          Fields.serviceCode.id -> Set("pmu"),
          Fields.enumAttributesByCode("pmu_area").id -> Set("brows"),
        ),
        "q2i7_recovery_021" -> Set(
          Fields.serviceCode.id -> Set("facial"),
          Fields.enumAttributesByCode("facial_treatment_type").id -> Set("aquafacial"),
          Fields.enumAttributesByCode("body_area").id -> Set("face"),
        ),
        "q2i7_recovery_025" -> Set(
          Fields.serviceCode.id -> Set("hair_removal"),
          Fields.enumAttributesByCode("hair_removal_method").id -> Set("laser"),
          Fields.enumAttributesByCode("body_area").id -> Set("upper_lip"),
        ),
        "q2i7_recovery_026" -> Set(
          Fields.serviceCode.id -> Set("hair_removal"),
          Fields.enumAttributesByCode("hair_removal_method").id -> Set("wax"),
          Fields.enumAttributesByCode("body_area").id -> Set("upper_lip"),
        ),
        "q2i7_recovery_029" -> Set(
          Fields.serviceCode.id -> Set("nail_modeling"),
          Fields.enumAttributesByCode("nail_service_type").id -> Set("extension"),
          Fields.enumAttributesByCode("nail_coating_type").id -> Set("gel"),
        ),
        "q2i7_recovery_002" -> Set(
          Fields.serviceCode.id -> Set("manicure"),
          Fields.enumAttributesByCode("nail_service_type").id -> Set("manicure"),
          Fields.enumAttributesByCode("nail_coating_type").id -> Set("shellac"),
        ),
        "q2i7_recovery_006" -> Set(
          Fields.serviceCode.id -> Set("pedicure"),
          Fields.enumAttributesByCode("nail_service_type").id -> Set("pedicure"),
          Fields.enumAttributesByCode("nail_coating_type").id -> Set("gel_polish"),
        ),
        "q2i7_recovery_010" -> Set(
          Fields.serviceCode.id -> Set("lashes"),
          Fields.enumAttributesByCode("lash_volume").id -> Set("volume3_d"),
        ),
        "q2i7_recovery_014" -> Set(
          Fields.serviceCode.id -> Set("brows"),
          Fields.booleanAttributesByCode("with_tinting").id -> Set("true"),
        ),
        "q2i7_recovery_018" -> Set(
          Fields.serviceCode.id -> Set("pmu"),
          Fields.enumAttributesByCode("pmu_area").id -> Set("eyeliner"),
        ),
        "q2i7_recovery_022" -> Set(
          Fields.serviceCode.id -> Set("facial"),
          Fields.enumAttributesByCode("facial_treatment_type").id -> Set("cleansing"),
          Fields.enumAttributesByCode("body_area").id -> Set("face"),
        ),
        "q2i7_recovery_031" -> Set(
          Fields.serviceCode.id -> Set("nail_modeling"),
          Fields.enumAttributesByCode("nail_service_type").id -> Set("extension"),
          Fields.enumAttributesByCode("nail_coating_type").id -> Set("acrylic"),
        ),
      )

      cases.foreach { case (id, expected) =>
        val intent = parseFromCorpus(exactlyOneCase(id))
        val actual = intent.hardConstraints.collect {
          case SourcedConstraint(PlannedConstraint.Terms(field, values), ConstraintProvenance.ParsedHard) =>
            field.id -> values.map(field.codec.encodeCanonical)
        }.toSet
        assert(actual == expected, s"unexpected post-recovery constraints for '$id': $actual")
        assert(intent.hardConstraints.size == expected.size)
      }
    }
  }
}
