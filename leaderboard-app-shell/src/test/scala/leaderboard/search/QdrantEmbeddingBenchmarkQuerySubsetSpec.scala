package leaderboard.search

import leaderboard.model.{MasterLocationId, MasterServiceOfferVariantId, QueryFailure, ServiceId}
import leaderboard.search.eval.{
  BeautySearchEvalQuery,
  EvalCarouselWeights,
  EvalProviderExpectation,
  EvalScoring,
  EvalServiceExpectation,
  EvalTopK,
  EvalVariantExpectation,
}
import leaderboard.search.qdrant.QdrantEmbeddingBenchmarkQuerySubset
import org.scalatest.wordspec.AnyWordSpec

import java.util.UUID

final class QdrantEmbeddingBenchmarkQuerySubsetSpec extends AnyWordSpec {
  "QdrantEmbeddingBenchmarkQuerySubset" should {
    "define the named semantic broad smoke subset with the original broad semantic query ids" in {
      assert(QdrantEmbeddingBenchmarkQuerySubset.SemanticBroadSmoke.queryIds.contains("q_broad_004"))
      assert(QdrantEmbeddingBenchmarkQuerySubset.SemanticBroadSmoke.queryIds.contains("q_broad_006"))
    }

    "define the named semantic broad smoke subset with additional explicit eval query ids" in {
      val originalIds = Set("q_broad_004", "q_broad_006")
      val additionalIds = QdrantEmbeddingBenchmarkQuerySubset.SemanticBroadSmoke.queryIds.filterNot(originalIds)
      val inventoryIds = BeautySearchEvalInventory.evalSuite.queries.map(_.id).toSet

      assert(additionalIds.nonEmpty)
      assert(additionalIds.toSet.subsetOf(inventoryIds))
    }

    "select the named semantic broad smoke subset in deterministic eval inventory order" in {
      val selected = QdrantEmbeddingBenchmarkQuerySubset
        .select(
          QdrantEmbeddingBenchmarkQuerySubset.SemanticBroadSmoke,
          BeautySearchEvalInventory.evalSuite.queries,
        )
        .toOption
        .getOrElse(fail("expected semantic broad smoke subset selection success"))

      assert(selected.map(_.id) == QdrantEmbeddingBenchmarkQuerySubset.SemanticBroadSmoke.queryIds)
    }

    "define the named semantic broad smoke subset without duplicate query ids" in {
      val queryIds = QdrantEmbeddingBenchmarkQuerySubset.SemanticBroadSmoke.queryIds

      assert(queryIds.distinct == queryIds)
    }

    "select explicit ids in inventory order instead of request order" in {
      val q1 = query(id = "q1", queryTypes = List("lexical"))
      val q2 = query(id = "q2", queryTypes = List("broad"))
      val q3 = query(id = "q3", queryTypes = List("semantic"))

      val result = QdrantEmbeddingBenchmarkQuerySubset.select(
        subset = QdrantEmbeddingBenchmarkQuerySubset(
          id = "explicit",
          description = "explicit ids",
          queryIds = List("q3", "q1"),
        ),
        queries = List(q1, q2, q3),
      )

      assert(result == Right(List(q1, q3)))
    }

    "select by query types" in {
      val q1 = query(id = "q1", queryTypes = List("broad", "conversational"))
      val q2 = query(id = "q2", queryTypes = List("hard-negative"))
      val q3 = query(id = "q3", queryTypes = List("broad"))

      val result = QdrantEmbeddingBenchmarkQuerySubset.select(
        subset = QdrantEmbeddingBenchmarkQuerySubset(
          id = "types",
          description = "type match",
          queryTypes = List("broad"),
        ),
        queries = List(q1, q2, q3),
      )

      assert(result == Right(List(q1, q3)))
    }

    "combine ids and query types as a union" in {
      val q1 = query(id = "q1", queryTypes = List("lexical"))
      val q2 = query(id = "q2", queryTypes = List("broad"))
      val q3 = query(id = "q3", queryTypes = List("semantic"))

      val result = QdrantEmbeddingBenchmarkQuerySubset.select(
        subset = QdrantEmbeddingBenchmarkQuerySubset(
          id = "union",
          description = "ids plus types",
          queryIds = List("q3"),
          queryTypes = List("broad"),
        ),
        queries = List(q1, q2, q3),
      )

      assert(result == Right(List(q2, q3)))
    }

    "deduplicate overlapping id and type matches" in {
      val q1 = query(id = "q1", queryTypes = List("broad"))
      val q2 = query(id = "q2", queryTypes = List("semantic"))

      val result = QdrantEmbeddingBenchmarkQuerySubset.select(
        subset = QdrantEmbeddingBenchmarkQuerySubset(
          id = "dedupe",
          description = "overlap",
          queryIds = List("q1"),
          queryTypes = List("broad"),
        ),
        queries = List(q1, q2),
      )

      assert(result == Right(List(q1)))
    }

    "apply maxQueries after filtering and deduplication" in {
      val q1 = query(id = "q1", queryTypes = List("broad"))
      val q2 = query(id = "q2", queryTypes = List("broad"))
      val q3 = query(id = "q3", queryTypes = List("broad"))

      val result = QdrantEmbeddingBenchmarkQuerySubset.select(
        subset = QdrantEmbeddingBenchmarkQuerySubset(
          id = "max",
          description = "limit",
          queryTypes = List("broad"),
          maxQueries = Some(2),
        ),
        queries = List(q1, q2, q3),
      )

      assert(result == Right(List(q1, q2)))
    }

    "fail when an explicit query id is missing" in {
      val q1 = query(id = "q1", queryTypes = List("broad"))

      val result = QdrantEmbeddingBenchmarkQuerySubset.select(
        subset = QdrantEmbeddingBenchmarkQuerySubset(
          id = "missing-id",
          description = "missing explicit id",
          queryIds = List("q1", "q2"),
        ),
        queries = List(q1),
      )

      assert(
        result == Left(
          QueryFailure.operation(
            "qdrant-embedding-benchmark-query-subset",
            "Subset missing-id references missing query ids: q2",
          )
        )
      )
    }

    "fail when the resulting subset is empty" in {
      val q1 = query(id = "q1", queryTypes = List("lexical"))

      val result = QdrantEmbeddingBenchmarkQuerySubset.select(
        subset = QdrantEmbeddingBenchmarkQuerySubset(
          id = "empty",
          description = "no matches",
          queryTypes = List("broad"),
        ),
        queries = List(q1),
      )

      assert(
        result == Left(
          QueryFailure.operation(
            "qdrant-embedding-benchmark-query-subset",
            "Subset empty selected no queries",
          )
        )
      )
    }

    "preserve selected query objects exactly" in {
      val q1 = query(id = "q1", queryTypes = List("lexical"))
      val q2 = query(id = "q2", queryTypes = List("broad"))

      val result = QdrantEmbeddingBenchmarkQuerySubset.select(
        subset = QdrantEmbeddingBenchmarkQuerySubset(
          id = "preserve",
          description = "same instances",
          queryIds = List("q2"),
        ),
        queries = List(q1, q2),
      )

      val selected = result.toOption.getOrElse(fail("expected selection success"))
      assert(selected == List(q2))
      assert(selected.head eq q2)
    }

    "select purely from provided fixtures without any external IO" in {
      val q1 = query(id = "q1", queryTypes = List("broad"))
      val q2 = query(id = "q2", queryTypes = List("semantic"))

      val result = QdrantEmbeddingBenchmarkQuerySubset.select(
        subset = QdrantEmbeddingBenchmarkQuerySubset(
          id = "fixtures-only",
          description = "fixtures only",
          queryIds = List("q1"),
          queryTypes = List("semantic"),
        ),
        queries = List(q1, q2),
      )

      assert(result == Right(List(q1, q2)))
    }
  }

  private def query(
    id: String,
    queryTypes: List[String],
  ): BeautySearchEvalQuery =
    BeautySearchEvalQuery(
      id = id,
      query = s"query-$id",
      language = "en",
      queryTypes = queryTypes,
      expectedVariantCarousel = EvalVariantExpectation(
        acceptableVariantIds = List(variantId(1)),
        topK = EvalTopK(),
      ),
      expectedProviderCarousel = EvalProviderExpectation(
        acceptableProviderLocationIds = List(providerId(1)),
        topK = EvalTopK(),
      ),
      expectedServiceIntentCarousel = EvalServiceExpectation(
        acceptableServiceIds = List(serviceId(1)),
        topK = EvalTopK(),
      ),
      scoring = EvalScoring(
        variantCarousel = EvalCarouselWeights(top3RequiredWeight = 1),
        providerCarousel = EvalCarouselWeights(top3RequiredWeight = 1),
        serviceIntentCarousel = EvalCarouselWeights(top3RequiredWeight = 1),
      ),
    )

  private def variantId(index: Int): MasterServiceOfferVariantId =
    UUID.nameUUIDFromBytes(s"variant-$index".getBytes)

  private def providerId(index: Int): MasterLocationId =
    UUID.nameUUIDFromBytes(s"provider-$index".getBytes)

  private def serviceId(index: Int): ServiceId =
    UUID.nameUUIDFromBytes(s"service-$index".getBytes)
}
