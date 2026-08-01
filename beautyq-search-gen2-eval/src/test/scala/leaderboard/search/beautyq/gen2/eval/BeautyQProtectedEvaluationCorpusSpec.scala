package leaderboard.search.beautyq.gen2.eval

import io.circe.Json
import leaderboard.search.gen2.eval.*
import org.scalatest.wordspec.AnyWordSpec

final class BeautyQProtectedEvaluationCorpusSpec extends AnyWordSpec {
  "BeautyQ protected corpus" should {
    "accept a valid all-protected corpus with exact case count and slice counts" in {
      val visible = visibleCorpus()
      val (corpus, policy) = validProtectedCorpusAndPolicy()
      val json = BeautyQEvaluationCorpus.canonicalJson(corpus)
      val result = BeautyQProtectedEvaluationCorpus.fromJson(json, visible, policy)
      result match {
        case Right(protectedCorpus) =>
          assert(protectedCorpus.caseCount == policy.expectedCaseCount)
          assert(protectedCorpus.orderedRequiredSliceCounts.map(_._1.value) == policy.requiredSliceMinimums.map(_.sliceId.value))
          assert(protectedCorpus.orderedRequiredSliceCounts.map(_._2) == Vector(2, 1))
        case Left(error) => fail(s"expected valid protected corpus, got $error")
      }
    }

    "reject the visible regression corpus as a protected input" in {
      val visible = BeautyQEvaluationCorpus.loadCanonical() match {
        case Right(value) => value
        case Left(error) => fail(s"expected canonical corpus, got $error")
      }
      val result = BeautyQProtectedEvaluationCorpus.fromJson(BeautyQEvaluationCorpus.canonicalJson(visible), visible, dummyPolicy())
      result match {
        case Left(BeautyQProtectedEvaluationCorpusError.ContainsNonProtectedCase) => ()
        case other => fail(s"expected non-protected partition rejection, got $other")
      }
    }

    "reject development partition" in {
      val visible = visibleCorpus()
      val corpus = partitionCorpus(EvaluationPartition.Development)
      val json = BeautyQEvaluationCorpus.canonicalJson(corpus)
      val result = BeautyQProtectedEvaluationCorpus.fromJson(json, visible, policyForCorpus(corpus))
      assert(result == Left(BeautyQProtectedEvaluationCorpusError.ContainsNonProtectedCase))
    }

    "reject empty corpus" in {
      val visible = visibleCorpus()
      val malformed = Json.obj(
        "schemaVersion" -> Json.fromString("beautyq-evaluation-corpus-v2"),
        "corpusId" -> Json.fromString("protected-test"),
        "dataset" -> Json.fromString("test"),
        "version" -> Json.fromInt(1),
        "defaultUserLocation" -> Json.obj(
          "label" -> Json.fromString("test"),
          "lat" -> Json.fromDoubleOrNull(53.55),
          "lon" -> Json.fromDoubleOrNull(10.0),
        ),
        "cases" -> Json.arr(),
      )
      BeautyQProtectedEvaluationCorpus.fromJson(malformed, visible, dummyPolicy()) match {
        case Left(BeautyQProtectedEvaluationCorpusError.InvalidInput(_)) => ()
        case other => fail(s"expected strict schema rejection, got $other")
      }
    }

    "reject mixed partitions" in {
      val visible = visibleCorpus()
      val corpus = buildCorpus(Vector(
        mkCase("case-1", EvaluationPartition.ProtectedHoldout, Vector(sid("smoke"))),
        mkCase("case-2", EvaluationPartition.Regression, Vector(sid("smoke"))),
      ))
      val json = BeautyQEvaluationCorpus.canonicalJson(corpus)
      val result = BeautyQProtectedEvaluationCorpus.fromJson(json, visible, policyForCorpus(corpus))
      assert(result == Left(BeautyQProtectedEvaluationCorpusError.ContainsNonProtectedCase))
    }

    "reject fingerprint mismatch" in {
      val visible = visibleCorpus()
      val (corpus, _) = validProtectedCorpusAndPolicy()
      val wrongPolicy = policyWithFingerprint("b" * 64, corpus.cases.size)
      val json = BeautyQEvaluationCorpus.canonicalJson(corpus)
      val result = BeautyQProtectedEvaluationCorpus.fromJson(json, visible, wrongPolicy)
      assert(result == Left(BeautyQProtectedEvaluationCorpusError.FingerprintMismatch))
    }

    "reject case-count mismatch" in {
      val visible = visibleCorpus()
      val (corpus, _) = validProtectedCorpusAndPolicy()
      val wrongPolicy = policyWithFingerprint(corpus.corpusFingerprint, corpus.cases.size + 10)
      val json = BeautyQEvaluationCorpus.canonicalJson(corpus)
      val result = BeautyQProtectedEvaluationCorpus.fromJson(json, visible, wrongPolicy)
      assert(result == Left(BeautyQProtectedEvaluationCorpusError.CaseCountMismatch))
    }

    "reject duplicate protected case ID" in {
      val visible = visibleCorpus()
      val case1 = corpusCaseToJson(mkCase("case-1", EvaluationPartition.ProtectedHoldout, Vector(sid("smoke"))))
      val case2 = corpusCaseToJson(mkCase("case-1", EvaluationPartition.ProtectedHoldout, Vector(sid("smoke"))))
      val json = Json.obj(
        "schemaVersion" -> Json.fromString("beautyq-evaluation-corpus-v2"),
        "corpusId" -> Json.fromString("test"),
        "dataset" -> Json.fromString("test"),
        "version" -> Json.fromInt(1),
        "defaultUserLocation" -> Json.obj(
          "label" -> Json.fromString("test"),
          "lat" -> Json.fromDoubleOrNull(53.55),
          "lon" -> Json.fromDoubleOrNull(10.0),
        ),
        "cases" -> Json.arr(case1, case2),
      )
      val policy = makePolicy("a" * 64, 2, slices = Vector(sid("smoke") -> 1))
      val result = BeautyQProtectedEvaluationCorpus.fromJson(json, visible, policy)
      result match {
        case Left(BeautyQProtectedEvaluationCorpusError.InvalidInput(msg)) => assert(msg.contains("not unique") || msg.contains("schema"))
        case other => fail(s"expected duplicate ID rejection, got $other")
      }
    }

    "reject visible case-ID overlap" in {
      val visible = visibleCorpusWithCase(cid("overlap-case"))
      val corpus = buildCorpus(Vector(
        mkCase("overlap-case", EvaluationPartition.ProtectedHoldout, Vector(sid("smoke"))),
      ))
      val json = BeautyQEvaluationCorpus.canonicalJson(corpus)
      val result = BeautyQProtectedEvaluationCorpus.fromJson(json, visible, policyForCorpus(corpus))
      assert(result == Left(BeautyQProtectedEvaluationCorpusError.OverlapsVisibleCorpus))
    }

    "reject missing required slice" in {
      val visible = visibleCorpus()
      val corpus = buildCorpus(Vector(
        mkCase("case-1", EvaluationPartition.ProtectedHoldout, Vector(sid("smoke"))),
      ))
      val json = BeautyQEvaluationCorpus.canonicalJson(corpus)
      val policy = makePolicy(
        corpusCorpusFingerprint(corpus), corpus.cases.size,
        slices = Vector(sid("smoke") -> 1, sid("development") -> 1),
      )
      val result = BeautyQProtectedEvaluationCorpus.fromJson(json, visible, policy)
      assert(result == Left(BeautyQProtectedEvaluationCorpusError.MissingRequiredSlice("development")))
    }

    "reject insufficient required-slice count" in {
      val visible = visibleCorpus()
      val corpus = buildCorpus(Vector(
        mkCase("case-1", EvaluationPartition.ProtectedHoldout, Vector(sid("smoke"))),
        mkCase("case-2", EvaluationPartition.ProtectedHoldout, Vector(sid("smoke"), sid("development"))),
      ))
      val json = BeautyQEvaluationCorpus.canonicalJson(corpus)
      val policy = makePolicy(
        corpusCorpusFingerprint(corpus), corpus.cases.size,
        slices = Vector(sid("smoke") -> 1, sid("development") -> 2),
      )
      val result = BeautyQProtectedEvaluationCorpus.fromJson(json, visible, policy)
      assert(result == Left(BeautyQProtectedEvaluationCorpusError.SliceCountTooSmall("development")))
    }

    "contain no synthetic sentinels in error output" in {
      val visible = visibleCorpus()
      val malformed = Json.obj(
        "schemaVersion" -> Json.fromString("beautyq-evaluation-corpus-v2"),
        "corpusId" -> Json.fromString("empty"), "dataset" -> Json.fromString("test"),
        "version" -> Json.fromInt(1),
        "defaultUserLocation" -> Json.obj("label" -> Json.fromString("test"), "lat" -> Json.fromDoubleOrNull(53.55), "lon" -> Json.fromDoubleOrNull(10.0)),
        "cases" -> Json.arr(),
      )
      val result = BeautyQProtectedEvaluationCorpus.fromJson(malformed, visible, dummyPolicy())
      val text = result.toString
      assert(!text.contains("synthetic-case"))
      assert(!text.contains("synthetic-query"))
    }
  }

  private def visibleCorpus(): BeautyQEvaluationCorpus = BeautyQEvaluationCorpus.loadCanonical() match {
    case Right(value) => value
    case Left(error) => fail(s"expected canonical corpus, got $error")
  }

  private def visibleCorpusWithCase(id: EvaluationCaseId): BeautyQEvaluationCorpus = {
    val visible = visibleCorpus()
    val extraCase = mkCase(id.value, EvaluationPartition.ProtectedHoldout, Vector(sid("smoke")))
    val json = BeautyQEvaluationCorpus.canonicalJson(visible)
    val extraJson = Vector(corpusCaseToJson(extraCase))
    val cases = json.hcursor.downField("cases").values.getOrElse(Vector.empty).toVector
    val modified = json.mapObject(_.add("cases", Json.fromValues(cases ++ extraJson)))
    BeautyQEvaluationCorpus.decodeFromJson(modified) match {
      case Right(value) => value
      case Left(error) => fail(s"expected decoded corpus, got $error")
    }
  }

  private def partitionCorpus(partition: EvaluationPartition): BeautyQEvaluationCorpus =
    buildCorpus(Vector(mkCase("case-1", partition, Vector(sid("smoke")))))

  private def buildCorpus(cases: Vector[CorpusCase]): BeautyQEvaluationCorpus = {
    val json = Json.obj(
      "schemaVersion" -> Json.fromString("beautyq-evaluation-corpus-v2"),
      "corpusId" -> Json.fromString("test"),
      "dataset" -> Json.fromString("test"),
      "version" -> Json.fromInt(1),
      "defaultUserLocation" -> Json.obj(
        "label" -> Json.fromString("test"),
        "lat" -> Json.fromDoubleOrNull(53.55),
        "lon" -> Json.fromDoubleOrNull(10.0),
      ),
      "cases" -> Json.fromValues(cases.map(corpusCaseToJson)),
    )
    BeautyQEvaluationCorpus.decodeFromJson(json) match {
      case Right(value) => value
      case Left(error) => fail(s"expected decoded corpus, got $error")
    }
  }

  private def corpusCaseToJson(current: CorpusCase): Json = Json.obj(
    "id" -> Json.fromString(current.caseId.value),
    "partition" -> Json.fromString(current.partition.stableCode),
    "judgmentMode" -> Json.fromString(current.judgmentMode.stableCode),
    "query" -> Json.fromString(current.query),
    "language" -> Json.fromString(current.language),
    "slices" -> Json.fromValues(current.slices.map(s => Json.fromString(s.value))),
    "userIntent" -> Json.fromString(current.userIntent),
    "notes" -> Json.fromValues(current.notes.map(Json.fromString)),
    "judgments" -> Json.obj(
      "variants" -> judgmentsJson(current.variantJudgments),
      "providers" -> judgmentsJson(current.providerJudgments),
      "serviceIntents" -> judgmentsJson(current.serviceIntentJudgments),
    ),
  )

  private def judgmentsJson(j: RankingJudgments): Json = {
    val base = Vector(
      "acceptableIds" -> Json.fromValues(j.acceptableIds.map(id => Json.fromString(id.value))),
      "forbiddenIds" -> Json.fromValues(j.forbiddenIds.map(id => Json.fromString(id.value))),
      "neutralIds" -> Json.fromValues(j.neutralIds.map(id => Json.fromString(id.value))),
      "gradedGains" -> Json.fromValues(j.gradedGains.map(g => Json.obj("id" -> Json.fromString(g.id.value), "gain" -> Json.fromInt(g.gain.value)))),
    )
    Json.obj(base: _*)
  }

  private def corpusCorpusFingerprint(corpus: BeautyQEvaluationCorpus): String = corpus.corpusFingerprint

  private def validProtectedCorpusAndPolicy(): (BeautyQEvaluationCorpus, BeautyQProtectedAcceptancePolicy) = {
    val corpus = buildCorpus(Vector(
      mkCase("case-1", EvaluationPartition.ProtectedHoldout, Vector(sid("smoke"), sid("development"))),
      mkCase("case-2", EvaluationPartition.ProtectedHoldout, Vector(sid("smoke"))),
    ))
    val policy = makePolicy(corpus.corpusFingerprint, corpus.cases.size,
      slices = Vector(sid("smoke") -> 2, sid("development") -> 1),
    )
    (corpus, policy)
  }

  private def policyForCorpus(corpus: BeautyQEvaluationCorpus): BeautyQProtectedAcceptancePolicy =
    makePolicy(corpus.corpusFingerprint, corpus.cases.size, slices = Vector(sid("smoke") -> 1))

  private def policyWithFingerprint(fingerprint: String, caseCount: Int): BeautyQProtectedAcceptancePolicy =
    makePolicy(fingerprint, caseCount, slices = Vector(sid("smoke") -> 1, sid("development") -> 1))

  private def dummyPolicy(): BeautyQProtectedAcceptancePolicy =
    makePolicy("a" * 64, 1, slices = Vector(sid("smoke") -> 1))

  private def makePolicy(
    fingerprint: String,
    caseCount: Int,
    slices: Vector[(EvaluationSliceId, Int)],
  ): BeautyQProtectedAcceptancePolicy =
    BeautyQProtectedAcceptancePolicy.fromJson(Json.obj(
      "schemaVersion" -> Json.fromString(BeautyQProtectedAcceptancePolicy.CurrentSchemaVersion),
      "evaluationPolicyVersion" -> Json.fromString(BeautyQEvaluationPolicy.CurrentVersion),
      "protectedAcceptancePolicyVersion" -> Json.fromString("protected-policy-v1"),
      "expectedCorpusFingerprint" -> Json.fromString(fingerprint),
      "expectedCaseCount" -> Json.fromInt(caseCount),
      "requiredSliceMinimums" -> Json.fromValues(slices.map { case (id, count) =>
        Json.obj("sliceId" -> Json.fromString(id.value), "minimumCaseCount" -> Json.fromInt(count))
      }),
      "requiredMetricMinimums" -> Json.arr(Json.obj(
        "observationKey" -> Json.fromString("protected-global"),
        "surface" -> Json.fromString(BeautyQEvaluationPolicy.Variants.value),
        "metric" -> Json.fromString("success"),
        "cutoff" -> Json.fromInt(1),
        "minimum" -> Json.fromString("0.000000000000"),
      )),
    )) match {
      case Right(value) => value
      case Left(error) => fail(s"expected policy fixture, got $error")
    }

  private def mkCase(id: String, partition: EvaluationPartition, slices: Vector[EvaluationSliceId]): CorpusCase = {
    val caseId = cid(id)
    val emptyJudgments = RankingJudgments.from(
      JudgmentMode.Partial,
      Vector(rid("00000000-0000-0000-0000-000000000001")), Vector.empty, Vector.empty, Vector.empty,
    ) match {
      case Right(value) => value
      case Left(error) => fail(error.toString)
    }
    new CorpusCase(caseId, partition, JudgmentMode.Partial, "query", "en", slices, "intent", Vector.empty, emptyJudgments, emptyJudgments, emptyJudgments)
  }

  private def cid(id: String): EvaluationCaseId = EvaluationCaseId.from(id) match {
    case Right(value) => value
    case Left(error) => fail(error.toString)
  }
  private def rid(id: String): EvaluationResultId = EvaluationResultId.from(id) match {
    case Right(value) => value
    case Left(error) => fail(error.toString)
  }
  private def sid(id: String): EvaluationSliceId = EvaluationSliceId.from(id) match {
    case Right(value) => value
    case Left(error) => fail(error.toString)
  }
}
