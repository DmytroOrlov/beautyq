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
    "have exactly 96 cases after the permanent break-glass migration" in {
      assert(corpus.cases.length == 96)
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
}
