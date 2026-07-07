package leaderboard.search.beautyq.contract

import org.scalatest.wordspec.AnyWordSpec

final class BeautyQSearchResponseProvenanceContractSpec extends AnyWordSpec {

  import BeautyQSearchResponseProvenanceContract.{ExecutionModes, JsonFields, ResultOrigins, RouteDiagnosticReasonCategories}

  "BeautyQSearchResponseProvenanceContract.JsonFields" should {
    "declare the exact response provenance JSON field names" in {
      assert(JsonFields.ExecutionMode == "executionMode")
      assert(JsonFields.ResultOrigin == "resultOrigin")
    }

    "declare distinct field names" in {
      assert(JsonFields.ExecutionMode != JsonFields.ResultOrigin)
    }
  }

  "BeautyQSearchResponseProvenanceContract.ExecutionModes" should {
    "declare the exact execution mode values" in {
      assert(ExecutionModes.EsOnly == "es_only")
      assert(ExecutionModes.EsPlusQdrantSupplement == "es_plus_qdrant_supplement")
    }

    "declare All in the exact source order" in {
      assert(ExecutionModes.All == List(ExecutionModes.EsOnly, ExecutionModes.EsPlusQdrantSupplement))
    }

    "declare All with no duplicate values" in {
      assert(ExecutionModes.All.distinct == ExecutionModes.All)
    }
  }

  "BeautyQSearchResponseProvenanceContract.ResultOrigins" should {
    "declare the exact result origin values" in {
      assert(ResultOrigins.EsBaseline == "es_baseline")
      assert(ResultOrigins.QdrantSupplement == "qdrant_supplement")
    }

    "declare All in the exact source order" in {
      assert(ResultOrigins.All == List(ResultOrigins.EsBaseline, ResultOrigins.QdrantSupplement))
    }

    "declare All with no duplicate values" in {
      assert(ResultOrigins.All.distinct == ResultOrigins.All)
    }
  }

  "BeautyQSearchResponseProvenanceContract.RouteDiagnosticReasonCategories" should {
    "declare the exact route diagnostic reason category values" in {
      assert(RouteDiagnosticReasonCategories.LexicalOnly == "lexical-only")
      assert(RouteDiagnosticReasonCategories.SemanticCandidates == "semantic-candidates")
      assert(RouteDiagnosticReasonCategories.FallbackNotImplemented == "fallback-not-implemented")
      assert(RouteDiagnosticReasonCategories.LexicalWithQdrantVariantSupplement == "lexical-with-qdrant-variant-supplement")
    }

    "declare All in the exact source order" in {
      assert(
        RouteDiagnosticReasonCategories.All ==
          List(
            RouteDiagnosticReasonCategories.LexicalOnly,
            RouteDiagnosticReasonCategories.SemanticCandidates,
            RouteDiagnosticReasonCategories.FallbackNotImplemented,
            RouteDiagnosticReasonCategories.LexicalWithQdrantVariantSupplement,
          )
      )
    }

    "declare All with no duplicate values" in {
      assert(RouteDiagnosticReasonCategories.All.distinct == RouteDiagnosticReasonCategories.All)
    }
  }
}
