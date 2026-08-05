package leaderboard.search

import leaderboard.search.beautyq.gen2.eval.BeautyQEvaluationCorpus
import org.scalatest.wordspec.AnyWordSpec

final class BeautyQProtectedRecoveryReserveCompositionSpec extends AnyWordSpec {
  private lazy val composition = BeautyQProtectedRecoveryReserveComposition.load().fold(error => fail(error), identity)
  private lazy val composition2 = BeautyQProtectedRecoveryReserveComposition.load().fold(error => fail(error), identity)

  "BeautyQProtectedRecoveryReserveComposition" should {
    "load the canonical catalog internally" in {
      val catalog = BeautyQCanonicalSeedEvaluationCatalog.load().fold(error => fail(error), identity)
      assert(catalog.variantResultIds.nonEmpty)
      assert(catalog.providerResultIds.nonEmpty)
      assert(catalog.serviceIntentResultIds.nonEmpty)
    }

    "load recovery through the eval owner from app-shell classpath" in {
      assert(composition.recovery.authorReserve.cases.size == 32)
      assert(composition.recovery.judgedReserve.corpus.cases.size == 32)
      assert(composition.recovery.selectionAudit.selectedCaseIds.size == 8)
    }

    "reject an invented variant identity" in {
      val catalog = BeautyQCanonicalSeedEvaluationCatalog.load().fold(error => fail(error), identity)
      val inventedJson = io.circe.Json.obj(
        "schemaVersion" -> io.circe.Json.fromString("beautyq-evaluation-corpus-v2"),
        "corpusId" -> io.circe.Json.fromString("fixture"),
        "dataset" -> io.circe.Json.fromString("test"),
        "version" -> io.circe.Json.fromInt(1),
        "defaultUserLocation" -> io.circe.Json.obj(
          "label" -> io.circe.Json.fromString("test"),
          "lat" -> io.circe.Json.fromDoubleOrNull(53.57),
          "lon" -> io.circe.Json.fromDoubleOrNull(10.06),
        ),
        "cases" -> io.circe.Json.arr(io.circe.Json.obj(
          "id" -> io.circe.Json.fromString("test-invented-id"),
          "partition" -> io.circe.Json.fromString("protected-holdout"),
          "judgmentMode" -> io.circe.Json.fromString("partial"),
          "query" -> io.circe.Json.fromString("test query"),
          "language" -> io.circe.Json.fromString("en"),
          "slices" -> io.circe.Json.arr(io.circe.Json.fromString("exact-intent")),
          "userIntent" -> io.circe.Json.fromString("test"),
          "notes" -> io.circe.Json.arr(),
          "judgments" -> io.circe.Json.obj(
            "variants" -> io.circe.Json.obj(
              "acceptableIds" -> io.circe.Json.arr(io.circe.Json.fromString("00000000-0000-0000-0000-000000000000")),
              "forbiddenIds" -> io.circe.Json.arr(),
              "neutralIds" -> io.circe.Json.arr(),
              "gradedGains" -> io.circe.Json.arr(),
            ),
            "providers" -> io.circe.Json.obj(
              "acceptableIds" -> io.circe.Json.arr(),
              "forbiddenIds" -> io.circe.Json.arr(),
              "neutralIds" -> io.circe.Json.arr(),
              "gradedGains" -> io.circe.Json.arr(),
            ),
            "serviceIntents" -> io.circe.Json.obj(
              "acceptableIds" -> io.circe.Json.arr(),
              "forbiddenIds" -> io.circe.Json.arr(),
              "neutralIds" -> io.circe.Json.arr(),
              "gradedGains" -> io.circe.Json.arr(),
            ),
          ),
        )),
      )
      val inventedCorpus = BeautyQEvaluationCorpus.decodeFromJson(inventedJson).getOrElse(fail("fixture decode failed"))
      val result = BeautyQProtectedRecoveryReserveComposition.validateCatalogSurfaces(inventedCorpus, catalog)
      assert(result.left.exists(_.contains("invalid variant")))
    }

    "reject an invented provider identity" in {
      val catalog = BeautyQCanonicalSeedEvaluationCatalog.load().fold(error => fail(error), identity)
      val inventedJson = io.circe.Json.obj(
        "schemaVersion" -> io.circe.Json.fromString("beautyq-evaluation-corpus-v2"),
        "corpusId" -> io.circe.Json.fromString("fixture"),
        "dataset" -> io.circe.Json.fromString("test"),
        "version" -> io.circe.Json.fromInt(1),
        "defaultUserLocation" -> io.circe.Json.obj(
          "label" -> io.circe.Json.fromString("test"),
          "lat" -> io.circe.Json.fromDoubleOrNull(53.57),
          "lon" -> io.circe.Json.fromDoubleOrNull(10.06),
        ),
        "cases" -> io.circe.Json.arr(io.circe.Json.obj(
          "id" -> io.circe.Json.fromString("test-invented-provider"),
          "partition" -> io.circe.Json.fromString("protected-holdout"),
          "judgmentMode" -> io.circe.Json.fromString("partial"),
          "query" -> io.circe.Json.fromString("test query"),
          "language" -> io.circe.Json.fromString("en"),
          "slices" -> io.circe.Json.arr(io.circe.Json.fromString("exact-intent")),
          "userIntent" -> io.circe.Json.fromString("test"),
          "notes" -> io.circe.Json.arr(),
          "judgments" -> io.circe.Json.obj(
            "variants" -> io.circe.Json.obj(
              "acceptableIds" -> io.circe.Json.arr(),
              "forbiddenIds" -> io.circe.Json.arr(),
              "neutralIds" -> io.circe.Json.arr(),
              "gradedGains" -> io.circe.Json.arr(),
            ),
            "providers" -> io.circe.Json.obj(
              "acceptableIds" -> io.circe.Json.arr(io.circe.Json.fromString("00000000-0000-0000-0000-000000000000")),
              "forbiddenIds" -> io.circe.Json.arr(),
              "neutralIds" -> io.circe.Json.arr(),
              "gradedGains" -> io.circe.Json.arr(),
            ),
            "serviceIntents" -> io.circe.Json.obj(
              "acceptableIds" -> io.circe.Json.arr(),
              "forbiddenIds" -> io.circe.Json.arr(),
              "neutralIds" -> io.circe.Json.arr(),
              "gradedGains" -> io.circe.Json.arr(),
            ),
          ),
        )),
      )
      val inventedCorpus = BeautyQEvaluationCorpus.decodeFromJson(inventedJson).getOrElse(fail("fixture decode failed"))
      val result = BeautyQProtectedRecoveryReserveComposition.validateCatalogSurfaces(inventedCorpus, catalog)
      assert(result.left.exists(_.contains("invalid provider")))
    }

    "reject an invented service intent identity" in {
      val catalog = BeautyQCanonicalSeedEvaluationCatalog.load().fold(error => fail(error), identity)
      val inventedJson = io.circe.Json.obj(
        "schemaVersion" -> io.circe.Json.fromString("beautyq-evaluation-corpus-v2"),
        "corpusId" -> io.circe.Json.fromString("fixture"),
        "dataset" -> io.circe.Json.fromString("test"),
        "version" -> io.circe.Json.fromInt(1),
        "defaultUserLocation" -> io.circe.Json.obj(
          "label" -> io.circe.Json.fromString("test"),
          "lat" -> io.circe.Json.fromDoubleOrNull(53.57),
          "lon" -> io.circe.Json.fromDoubleOrNull(10.06),
        ),
        "cases" -> io.circe.Json.arr(io.circe.Json.obj(
          "id" -> io.circe.Json.fromString("test-invented-si"),
          "partition" -> io.circe.Json.fromString("protected-holdout"),
          "judgmentMode" -> io.circe.Json.fromString("partial"),
          "query" -> io.circe.Json.fromString("test query"),
          "language" -> io.circe.Json.fromString("en"),
          "slices" -> io.circe.Json.arr(io.circe.Json.fromString("exact-intent")),
          "userIntent" -> io.circe.Json.fromString("test"),
          "notes" -> io.circe.Json.arr(),
          "judgments" -> io.circe.Json.obj(
            "variants" -> io.circe.Json.obj(
              "acceptableIds" -> io.circe.Json.arr(),
              "forbiddenIds" -> io.circe.Json.arr(),
              "neutralIds" -> io.circe.Json.arr(),
              "gradedGains" -> io.circe.Json.arr(),
            ),
            "providers" -> io.circe.Json.obj(
              "acceptableIds" -> io.circe.Json.arr(),
              "forbiddenIds" -> io.circe.Json.arr(),
              "neutralIds" -> io.circe.Json.arr(),
              "gradedGains" -> io.circe.Json.arr(),
            ),
            "serviceIntents" -> io.circe.Json.obj(
              "acceptableIds" -> io.circe.Json.arr(io.circe.Json.fromString("00000000-0000-0000-0000-000000000000")),
              "forbiddenIds" -> io.circe.Json.arr(),
              "neutralIds" -> io.circe.Json.arr(),
              "gradedGains" -> io.circe.Json.arr(),
            ),
          ),
        )),
      )
      val inventedCorpus = BeautyQEvaluationCorpus.decodeFromJson(inventedJson).getOrElse(fail("fixture decode failed"))
      val result = BeautyQProtectedRecoveryReserveComposition.validateCatalogSurfaces(inventedCorpus, catalog)
      assert(result.left.exists(_.contains("invalid service intent")))
    }

    "reject a graded-gain identity on the wrong surface" in {
      val catalog = BeautyQCanonicalSeedEvaluationCatalog.load().fold(error => fail(error), identity)
      val variantId = catalog.variantResultIds.head
      val inventedJson = io.circe.Json.obj(
        "schemaVersion" -> io.circe.Json.fromString("beautyq-evaluation-corpus-v2"),
        "corpusId" -> io.circe.Json.fromString("fixture"),
        "dataset" -> io.circe.Json.fromString("test"),
        "version" -> io.circe.Json.fromInt(1),
        "defaultUserLocation" -> io.circe.Json.obj(
          "label" -> io.circe.Json.fromString("test"),
          "lat" -> io.circe.Json.fromDoubleOrNull(53.57),
          "lon" -> io.circe.Json.fromDoubleOrNull(10.06),
        ),
        "cases" -> io.circe.Json.arr(io.circe.Json.obj(
          "id" -> io.circe.Json.fromString("test-graded-wrong-surface"),
          "partition" -> io.circe.Json.fromString("protected-holdout"),
          "judgmentMode" -> io.circe.Json.fromString("partial"),
          "query" -> io.circe.Json.fromString("test query"),
          "language" -> io.circe.Json.fromString("en"),
          "slices" -> io.circe.Json.arr(io.circe.Json.fromString("exact-intent")),
          "userIntent" -> io.circe.Json.fromString("test"),
          "notes" -> io.circe.Json.arr(),
          "judgments" -> io.circe.Json.obj(
            "variants" -> io.circe.Json.obj(
              "acceptableIds" -> io.circe.Json.arr(),
              "forbiddenIds" -> io.circe.Json.arr(),
              "neutralIds" -> io.circe.Json.arr(),
              "gradedGains" -> io.circe.Json.arr(),
            ),
            "providers" -> io.circe.Json.obj(
              "acceptableIds" -> io.circe.Json.arr(io.circe.Json.fromString(variantId.value)),
              "forbiddenIds" -> io.circe.Json.arr(),
              "neutralIds" -> io.circe.Json.arr(),
              "gradedGains" -> io.circe.Json.arr(io.circe.Json.obj(
                "id" -> io.circe.Json.fromString(variantId.value),
                "gain" -> io.circe.Json.fromInt(3),
              )),
            ),
            "serviceIntents" -> io.circe.Json.obj(
              "acceptableIds" -> io.circe.Json.arr(),
              "forbiddenIds" -> io.circe.Json.arr(),
              "neutralIds" -> io.circe.Json.arr(),
              "gradedGains" -> io.circe.Json.arr(),
            ),
          ),
        )),
      )
      val inventedCorpus = BeautyQEvaluationCorpus.decodeFromJson(inventedJson).getOrElse(fail("fixture decode failed"))
      val result = BeautyQProtectedRecoveryReserveComposition.validateCatalogSurfaces(inventedCorpus, catalog)
      assert(result.left.exists(_.contains("invalid provider")))
    }

    "reject canonical catalog fingerprint mismatch" in {
      val catalog = BeautyQCanonicalSeedEvaluationCatalog.load().fold(error => fail(error), identity)
      val result = BeautyQProtectedRecoveryReserveComposition.validateCatalogFingerprint(
        "0000000000000000000000000000000000000000000000000000000000000000",
        catalog,
      )
      assert(result.isLeft)
      assert(result.left.exists(_.contains("fingerprint_mismatch")))
    }

    "produce identical result from two independent composition loads" in {
      assert(composition.recovery.authorReserve.cases.map(_.id) == composition2.recovery.authorReserve.cases.map(_.id))
      assert(composition.recovery.judgedReserve.corpus.cases.map(_.caseId.value) == composition2.recovery.judgedReserve.corpus.cases.map(_.caseId.value))
      assert(composition.recovery.selectionAudit.selectedCaseIds == composition2.recovery.selectionAudit.selectedCaseIds)
    }

    "bind canon catalog fingerprint" in {
      val catalog = BeautyQCanonicalSeedEvaluationCatalog.load().fold(error => fail(error), identity)
      assert(composition.recovery.selectionAudit.canonicalCatalogFingerprint == catalog.sourceFingerprint.value)
    }
  }
}
