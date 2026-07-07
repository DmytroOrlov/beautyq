package leaderboard.search

import java.util.UUID

import leaderboard.model.MasterServiceOfferVariantId
import leaderboard.search.document.VariantSearchDocument
import leaderboard.search.dsl.SearchGeoPoint
import leaderboard.search.hybrid.BeautyQHybridCandidateSource.{Lexical, Semantic}
import leaderboard.search.hybrid.{
  BeautyQHybridProjectionDiagnostics,
  BeautyQHybridProjectionPolicyResult,
  BeautyQHybridVariantCandidate,
  BeautyQHybridVariantProjection,
}
import org.scalatest.wordspec.AnyWordSpec

final class BeautyQHybridVariantProjectionSpec extends AnyWordSpec {
  "BeautyQ hybrid variant projection" should {
    "project policy candidates into hydrated variant candidates" in {
      val first = variantDocument(1)
      val second = variantDocument(2)

      val result = project(
        candidates = List(
          BeautyQHybridVariantCandidate(first.variantId, Some(12.0), None, Set(Lexical)),
          BeautyQHybridVariantCandidate(second.variantId, None, Some(0.84), Set(Semantic)),
        ),
        documents = List(first, second),
      )

      assert(result.candidates.map(_.document) == List(first, second))
    }

    "preserve policy order" in {
      val first = variantDocument(3)
      val second = variantDocument(4)

      val result = project(
        candidates = List(
          BeautyQHybridVariantCandidate(second.variantId, None, Some(0.78), Set(Semantic)),
          BeautyQHybridVariantCandidate(first.variantId, Some(7.0), None, Set(Lexical)),
        ),
        documents = List(first, second),
      )

      assert(result.candidates.map(_.document.variantId) == List(second.variantId, first.variantId))
    }

    "preserve lexical scores" in {
      val document = variantDocument(5)

      val result = project(
        candidates = List(BeautyQHybridVariantCandidate(document.variantId, Some(18.5), None, Set(Lexical))),
        documents = List(document),
      )

      assert(result.candidates.head.lexicalScore == Some(18.5))
    }

    "preserve semantic scores" in {
      val document = variantDocument(6)

      val result = project(
        candidates = List(BeautyQHybridVariantCandidate(document.variantId, None, Some(0.91), Set(Semantic))),
        documents = List(document),
      )

      assert(result.candidates.head.semanticScore == Some(0.91))
    }

    "preserve sources" in {
      val document = variantDocument(7)

      val result = project(
        candidates = List(BeautyQHybridVariantCandidate(document.variantId, Some(9.0), None, Set(Lexical))),
        documents = List(document),
      )

      assert(result.candidates.head.sources == Set(Lexical))
    }

    "keep overlap candidate as a both-source candidate" in {
      val document = variantDocument(8)

      val result = project(
        candidates = List(BeautyQHybridVariantCandidate(document.variantId, Some(11.0), Some(0.88), Set(Lexical, Semantic))),
        documents = List(document),
      )

      assert(result.candidates.head.sources == Set(Lexical, Semantic))
      assert(result.candidates.head.lexicalScore == Some(11.0))
      assert(result.candidates.head.semanticScore == Some(0.88))
    }

    "fail clearly when a policy candidate has no document" in {
      val missingId = variantId(9)

      val result = BeautyQHybridVariantProjection.project(
        policyResult = policyResult(List(BeautyQHybridVariantCandidate(missingId, Some(4.0), None, Set(Lexical)))),
        documentsByVariantId = Map.empty,
      )

      assert(result.isLeft)
      assert(result.left.exists(_.message.contains("Missing VariantSearchDocument")))
    }

    "include missing variant id in missing document error" in {
      val missingId = variantId(10)

      val result = BeautyQHybridVariantProjection.project(
        policyResult = policyResult(List(BeautyQHybridVariantCandidate(missingId, None, Some(0.77), Set(Semantic)))),
        documentsByVariantId = Map.empty,
      )

      assert(result.left.exists(_.message.contains(missingId.toString)))
    }

    "not produce a fused score field" in {
      val document = variantDocument(11)

      val candidate = project(
        candidates = List(BeautyQHybridVariantCandidate(document.variantId, Some(12.0), Some(0.9), Set(Lexical, Semantic))),
        documents = List(document),
      ).candidates.head

      assert(!candidate.productElementNames.toSet.contains("score"))
    }

    "report input and projected candidate count diagnostics" in {
      val first = variantDocument(12)
      val second = variantDocument(13)

      val result = project(
        candidates = List(
          BeautyQHybridVariantCandidate(first.variantId, Some(6.0), None, Set(Lexical)),
          BeautyQHybridVariantCandidate(second.variantId, None, Some(0.74), Set(Semantic)),
        ),
        documents = List(first, second),
      )

      assert(result.diagnostics.inputCandidateCount == 2)
      assert(result.diagnostics.projectedCandidateCount == 2)
      assert(result.diagnostics.missingDocumentIds == Nil)
    }

    "not create provider, service, facet, or response projection objects" in {
      val document = variantDocument(14)

      val projected = project(
        candidates = List(BeautyQHybridVariantCandidate(document.variantId, Some(3.0), Some(0.66), Set(Lexical, Semantic))),
        documents = List(document),
      )

      assert(projected.productElementNames.toSet == Set("candidates", "diagnostics"))
      assert(projected.candidates.head.productElementNames.toSet == Set("document", "lexicalScore", "semanticScore", "sources"))
    }
  }

  private def project(
    candidates: List[BeautyQHybridVariantCandidate],
    documents: List[VariantSearchDocument],
  ) =
    BeautyQHybridVariantProjection.project(
      policyResult = policyResult(candidates),
      documentsByVariantId = documents.map(document => document.variantId -> document).toMap,
    ) match {
      case Right(result) => result
      case Left(error) => fail(error.message)
    }

  private def policyResult(candidates: List[BeautyQHybridVariantCandidate]): BeautyQHybridProjectionPolicyResult =
    BeautyQHybridProjectionPolicyResult(
      candidates = candidates,
      diagnostics = BeautyQHybridProjectionDiagnostics(
        lexicalInputCount = candidates.count(_.lexicalScore.nonEmpty),
        semanticInputCount = candidates.count(_.semanticScore.nonEmpty),
        overlapCount = candidates.count(candidate => candidate.lexicalScore.nonEmpty && candidate.semanticScore.nonEmpty),
        lexicalOnlyCount = candidates.count(candidate => candidate.lexicalScore.nonEmpty && candidate.semanticScore.isEmpty),
        semanticOnlyCount = candidates.count(candidate => candidate.lexicalScore.isEmpty && candidate.semanticScore.nonEmpty),
      ),
    )

  private def variantDocument(index: Int): VariantSearchDocument =
    VariantSearchDocument(
      variantId = variantId(index),
      masterServiceOfferId = variantId(index + 100),
      masterLocationId = variantId(index + 200),
      masterId = variantId(index + 300),
      serviceId = variantId(index + 400),
      categoryId = variantId(index + 500),
      serviceName = s"Service $index",
      categoryName = s"Category $index",
      masterName = s"Master $index",
      locationName = s"Location $index",
      address = s"Main street $index",
      location = SearchGeoPoint(lat = BigDecimal("52.5200"), lon = BigDecimal("13.4050")),
      lat = BigDecimal("52.5200"),
      lon = BigDecimal("13.4050"),
      priceFrom = BigDecimal("25.00"),
      priceTo = BigDecimal("40.00"),
      durationMin = 45,
      enumAttributes = Map("coverage" -> "gel"),
      booleanAttributes = Map("with_removal" -> true),
      intAttributes = Map("nails_count" -> 10),
      bigDecimalAttributes = Map("rating" -> BigDecimal("4.8")),
      allText = s"service $index category master location",
      serviceText = s"service $index",
      attributeText = "coverage gel with removal",
      providerText = s"master $index location $index",
      locationText = s"location $index main street $index",
    )

  private def variantId(value: Int): MasterServiceOfferVariantId =
    UUID.fromString(f"00000000-0000-0000-0000-$value%012d")
}
