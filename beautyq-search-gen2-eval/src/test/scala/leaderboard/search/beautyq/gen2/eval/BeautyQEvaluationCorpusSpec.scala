package leaderboard.search.beautyq.gen2.eval

import leaderboard.search.gen2.eval.*
import io.circe.Json
import io.circe.parser.*
import org.scalatest.wordspec.AnyWordSpec
import scala.io.Source

final class BeautyQEvaluationCorpusSpec extends AnyWordSpec {

  private val ResourcePath = "leaderboard/search/beautyq/gen2/eval/beautyq_evaluation_corpus_v2.json"

  private lazy val corpus: BeautyQEvaluationCorpus =
    BeautyQEvaluationCorpus.loadCanonical().getOrElse(fail("failed to load canonical corpus"))

  private lazy val rawJson: Json = {
    val is = getClass.getClassLoader.getResourceAsStream(ResourcePath)
    val text = Source.fromInputStream(is, "UTF-8").mkString
    is.close()
    parse(text).getOrElse(fail("invalid corpus json"))
  }

  "BeautyQEvaluationCorpus" should {

    "load canonical resource successfully" in {
      BeautyQEvaluationCorpus.loadCanonical() match {
        case Left(err) => fail(s"expected Right, got $err")
        case _ => (): Unit
      }
    }

    "have exactly 89 cases" in {
      assert(corpus.cases.length == 89)
    }

    "have first case ID q_nails_001" in {
      assert(corpus.cases(0).caseId.value == "q_nails_001")
    }

    "have last case ID q_holdout_nails_gel_correction_design_001" in {
      assert(corpus.cases(corpus.cases.length - 1).caseId.value == "q_holdout_nails_gel_correction_design_001")
    }

    "preserve explicit source order for first 3 cases" in {
      val firstThree = corpus.cases.take(3).map(_.caseId.value)
      assert(firstThree == Vector("q_nails_001", "q_nails_002", "q_nails_003"))
    }

    "have all unique case IDs" in {
      val ids = corpus.cases.map(_.caseId.value)
      val _ = assert(ids.distinct.size == ids.size)
    }

    "have every case as Regression partition" in {
      corpus.cases.foreach { c =>
        assert(c.partition == EvaluationPartition.Regression)
      }
    }

    "have every case as Partial judgmentMode" in {
      corpus.cases.foreach { c =>
        assert(c.judgmentMode == JudgmentMode.Partial)
      }
    }

    "have no protected holdout partition" in {
      val protectedHoldouts = corpus.cases.filter(_.partition == EvaluationPartition.ProtectedHoldout)
      assert(protectedHoldouts.isEmpty)
    }

    "have exact language inventory de, en, mixed, ru" in {
      val languages = corpus.cases.map(_.language).distinct.sorted
      assert(languages == Vector("de", "en", "mixed", "ru"))
    }

    "preserve q_nails_001 query text, acceptable variant UUIDs, forbidden variant UUIDs, provider UUIDs, and service intent UUID" in {
      val qNails001 = corpus.cases.find(_.caseId.value == "q_nails_001").getOrElse(fail("q_nails_001 not found"))
      assert(qNails001.query == "маникюр гель лак")
      assert(qNails001.language == "ru")
      assert(qNails001.slices.map(_.value) == Vector("direct", "attribute"))

      assert(qNails001.variantJudgments.acceptableIds.map(_.value) == Vector(
        "c82d90c3-d9e4-5f0b-8689-6476c5e7fe35",
        "1fcd6e17-c6bb-5901-9f63-205668897659",
      ))
      assert(qNails001.variantJudgments.forbiddenIds.map(_.value) == Vector(
        "e593119b-3ec4-547f-89cb-5a1d9eaa5859",
        "d7ec2ffe-e84d-5782-b54e-b95540a58ef2",
        "010a2eb5-a541-5763-91d9-29971dfdda35",
        "cb9843b3-3f6a-5dda-bd74-d79084476f94",
        "94575ead-5606-58fc-9bff-7f2918344c74",
        "55213b7f-1ade-56b7-91be-3d4a48384ae4",
        "b036712b-991b-538e-80ef-aec745be2aba",
        "17fe4477-4866-507c-84f9-0dd9b63295cc",
        "a5a90236-d10c-5021-9dd7-cc56367a3b19",
        "99fc235a-7238-5399-b847-c7b68b85e2b4",
        "d103456e-61f3-5987-995d-03550bdf0230",
        "97bd38cc-a019-5306-972c-ca72dbd52b77",
        "64cf31e0-cef7-5f77-abbb-43055f9739f9",
      ))
      assert(qNails001.variantJudgments.forbiddenIds.length == 13)
      assert(qNails001.variantJudgments.acceptableIds.length == 2)

      assert(qNails001.providerJudgments.acceptableIds.map(_.value) == Vector(
        "989e0858-bc32-5b71-a355-6ce1e20b0cb1",
        "78fdf5d2-0f92-5c2c-b20d-e5d2549d1c52",
      ))
      assert(qNails001.providerJudgments.acceptableIds.length == 2)

      assert(qNails001.serviceIntentJudgments.acceptableIds.map(_.value) == Vector(
        "a1085253-a9bf-517c-80c4-262b0bf9a5a4",
      ))
      assert(qNails001.serviceIntentJudgments.acceptableIds.length == 1)
    }

    "preserve q_broad_003 query text and judgments" in {
      val qBroad003 = corpus.cases.find(_.caseId.value == "q_broad_003").getOrElse(fail("q_broad_003 not found"))
      assert(qBroad003.query == "что-то для лица рядом")
      assert(qBroad003.language == "ru")
      assert(qBroad003.slices.map(_.value) == Vector("conversational", "broad"))

      assert(qBroad003.variantJudgments.acceptableIds.map(_.value) == Vector(
        "54568273-7762-5881-a027-0900965ddba4",
        "a32c399d-db3a-5806-8556-e66c4ad4ea1c",
        "c276df41-547e-5ef0-ab00-9464ec45c0e1",
        "b8150d36-f350-51d7-a07c-22beeec96d1c",
        "7c0663da-bd96-585a-ab3d-d7e63e3fc766",
        "e90bad04-997a-51a1-9a15-6b9404025def",
        "f018b7f8-1997-596f-8c32-414d6cf8f538",
        "bf052e78-8be1-5267-93bb-fd92ff9b7f68",
        "29e98624-eaf8-563a-bc97-02a9ea8c17f0",
      ))
      assert(qBroad003.variantJudgments.acceptableIds.length == 9)
      assert(qBroad003.variantJudgments.forbiddenIds.length == 31)

      assert(qBroad003.providerJudgments.acceptableIds.map(_.value) == Vector(
        "1ef18ddc-5518-5775-9298-1ceb23043c60",
        "bc9f055b-d655-5bcc-ba72-1848bb295002",
        "6e071abd-9fd2-59b6-9054-3632e6dc997f",
      ))
      assert(qBroad003.providerJudgments.acceptableIds.length == 3)

      assert(qBroad003.serviceIntentJudgments.acceptableIds.map(_.value) == Vector(
        "bbc1ec9c-0493-5fe4-b637-bbfbfe7aa72d",
      ))
      assert(qBroad003.serviceIntentJudgments.acceptableIds.length == 1)
    }

    "have deterministic corpus fingerprint of 64 lowercase hex" in {
      val fp = corpus.corpusFingerprint
      assert(fp.length == 64)
      assert(fp.matches("^[0-9a-f]{64}$"))
      val corpus2 = BeautyQEvaluationCorpus.loadCanonical().getOrElse(fail("failed to reload canonical corpus"))
      assert(fp == corpus2.corpusFingerprint)
    }

    "preserve fingerprint after formatting-only encode/decode" in {
      val canonical = rawJson.noSpaces
      val reParsed = parse(canonical).getOrElse(fail("failed to re-parse canonical json"))
      val corpus2 = BeautyQEvaluationCorpus.decodeFromJson(reParsed).getOrElse(fail("failed to decode re-parsed json"))
      assert(corpus.corpusFingerprint == corpus2.corpusFingerprint)
    }

    "change fingerprint when one query text is modified" in {
      val casesJson = rawJson.hcursor.downField("cases").as[Vector[Json]].getOrElse(fail("failed to parse cases"))
      val firstCase = casesJson(0)
      val modifiedFirstCase = firstCase.mapObject { obj =>
        val oldQuery = obj("query").flatMap(_.asString).getOrElse("")
        obj.add("query", Json.fromString(oldQuery + " modified"))
      }
      val modifiedJson = rawJson.mapObject(_.add("cases", Json.fromValues(modifiedFirstCase +: casesJson.drop(1))))
      val corpus2 = BeautyQEvaluationCorpus.decodeFromJson(modifiedJson).getOrElse(fail("failed to decode modified json"))
      assert(corpus.corpusFingerprint != corpus2.corpusFingerprint)
    }

    "reject unknown top-level field" in {
      val modifiedJson = rawJson.mapObject(_.add("extraTopField", Json.fromString("x")))
      val result = BeautyQEvaluationCorpus.decodeFromJson(modifiedJson)
      result match {
        case Left(CorpusLoadError.UnexpectedFields("root", fields)) =>
          assert(fields.contains("extraTopField"))
          (): Unit
        case other => fail(s"expected UnexpectedFields for root, got $other")
      }
    }

    "reject unknown case field" in {
      val casesJson = rawJson.hcursor.downField("cases").as[Vector[Json]].getOrElse(fail("failed to parse cases"))
      val modifiedFirstCase = casesJson(0).mapObject(_.add("extraField", Json.fromString("value")))
      val modifiedJson = rawJson.mapObject(_.add("cases", Json.fromValues(modifiedFirstCase +: casesJson.drop(1))))
      val result = BeautyQEvaluationCorpus.decodeFromJson(modifiedJson)
      result match {
        case Left(CorpusLoadError.UnexpectedFields(context, _)) =>
          assert(context.startsWith("case"))
          (): Unit
        case other => fail(s"expected UnexpectedFields for case, got $other")
      }
    }

    "reject duplicate case ID" in {
      val casesJson = rawJson.hcursor.downField("cases").as[Vector[Json]].getOrElse(fail("failed to parse cases"))
      val duplicatedCases = casesJson(0) +: casesJson
      val modifiedJson = rawJson.mapObject(_.add("cases", Json.fromValues(duplicatedCases)))
      val result = BeautyQEvaluationCorpus.decodeFromJson(modifiedJson)
      result match {
        case Left(CorpusLoadError.DuplicateCaseId(_)) =>
          (): Unit
        case other => fail(s"expected DuplicateCaseId, got $other")
      }
    }

    "reject duplicate slice" in {
      val casesJson = rawJson.hcursor.downField("cases").as[Vector[Json]].getOrElse(fail("failed to parse cases"))
      val firstCase = casesJson(0)
      val slices = firstCase.hcursor.downField("slices").as[Vector[Json]].getOrElse(fail("failed to parse slices"))
      val duplicatedSlices = slices :+ slices(0)
      val modifiedFirstCase = firstCase.mapObject(_.add("slices", Json.fromValues(duplicatedSlices)))
      val modifiedJson = rawJson.mapObject(_.add("cases", Json.fromValues(modifiedFirstCase +: casesJson.drop(1))))
      val result = BeautyQEvaluationCorpus.decodeFromJson(modifiedJson)
      result match {
        case Left(CorpusLoadError.DuplicateSliceId(caseId, _)) =>
          assert(caseId == "q_nails_001")
          (): Unit
        case other => fail(s"expected DuplicateSliceId, got $other")
      }
    }

    "reject a non-array notes value" in {
      val casesJson = rawJson.hcursor.downField("cases").as[Vector[Json]].getOrElse(fail("failed to parse cases"))
      val modifiedFirstCase = casesJson(0).mapObject(_.add("notes", Json.fromString("legacy scalar")))
      val modifiedJson = rawJson.mapObject(_.add("cases", Json.fromValues(modifiedFirstCase +: casesJson.drop(1))))
      BeautyQEvaluationCorpus.decodeFromJson(modifiedJson) match {
        case Left(CorpusLoadError.MissingOrInvalidField("case", "notes")) => ()
        case other => fail(s"expected strict notes rejection, got $other")
      }
    }

    "reject non-array judgment values" in {
      val casesJson = rawJson.hcursor.downField("cases").as[Vector[Json]].getOrElse(fail("failed to parse cases"))
      val firstCase = casesJson(0)
      val judgments = firstCase.hcursor.downField("judgments").focus.flatMap(_.asObject).getOrElse(fail("expected judgments"))
      val variants = judgments("variants").flatMap(_.asObject).getOrElse(fail("expected variants"))
      val changedVariants = Json.fromJsonObject(variants.add("acceptableIds", Json.Null))
      val changedJudgments = Json.fromJsonObject(judgments.add("variants", changedVariants))
      val changedCase = firstCase.mapObject(_.add("judgments", changedJudgments))
      val modifiedJson = rawJson.mapObject(_.add("cases", Json.fromValues(changedCase +: casesJson.drop(1))))
      BeautyQEvaluationCorpus.decodeFromJson(modifiedJson) match {
        case Left(CorpusLoadError.MissingOrInvalidField(_, "acceptableIds")) => ()
        case other => fail(s"expected strict judgment array rejection, got $other")
      }
    }

    "reject reordered judgment surfaces" in {
      val casesJson = rawJson.hcursor.downField("cases").as[Vector[Json]].getOrElse(fail("failed to parse cases"))
      val firstCase = casesJson(0)
      val judgments = firstCase.hcursor.downField("judgments").focus.flatMap(_.asObject).getOrElse(fail("expected judgments"))
      val reordered = Json.fromFields(Vector("providers" -> judgments("providers").getOrElse(Json.Null), "variants" -> judgments("variants").getOrElse(Json.Null), "serviceIntents" -> judgments("serviceIntents").getOrElse(Json.Null)))
      val changedCase = firstCase.mapObject(_.add("judgments", reordered))
      val modifiedJson = rawJson.mapObject(_.add("cases", Json.fromValues(changedCase +: casesJson.drop(1))))
      BeautyQEvaluationCorpus.decodeFromJson(modifiedJson) match {
        case Left(CorpusLoadError.UnexpectedFields(context, _)) => assert(context.endsWith(".judgments"))
        case other => fail(s"expected ordered surface rejection, got $other")
      }
    }
  }
}
