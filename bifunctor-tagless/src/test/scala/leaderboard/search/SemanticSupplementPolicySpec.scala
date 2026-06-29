package leaderboard.search

import leaderboard.search.dsl.*
import leaderboard.search.hybrid.SemanticSupplementPolicy
import org.scalatest.wordspec.AnyWordSpec

final class SemanticSupplementPolicySpec extends AnyWordSpec {
  "SemanticSupplementPolicy.PrefixPreservingTop1" should {
    "preserve lexical prefix order and append only the first eligible semantic candidate after the prefix" in {
      val firstLexical = ToyDocument("lex-1", eligible = true)
      val secondLexical = ToyDocument("lex-2", eligible = true)
      val skippedSemantic = ToyDocument("sem-1", eligible = false)
      val selectedSemantic = ToyDocument("sem-2", eligible = true)
      val laterSemantic = ToyDocument("sem-3", eligible = true)
      val policy = SemanticSupplementPolicy.PrefixPreservingTop1[ToyDocument](_.eligible)

      val selected = policy.select(
        lexicalPrefix = List(firstLexical, secondLexical),
        semanticCandidates = List(skippedSemantic, selectedSemantic, laterSemantic),
        request = UserSearchInput("toy", None, None, limit = 4),
        runtimeSpec = runtimeSpec,
      )

      assert(selected == List(firstLexical, secondLexical, selectedSemantic))
    }

    "never produce Qdrant-only fallback when request limit leaves no room after the lexical prefix" in {
      val lexicalPrefix = List(ToyDocument("lex-1", eligible = true), ToyDocument("lex-2", eligible = true))
      val policy = SemanticSupplementPolicy.PrefixPreservingTop1[ToyDocument](_.eligible)

      val selected = policy.select(
        lexicalPrefix = lexicalPrefix,
        semanticCandidates = List(ToyDocument("sem-1", eligible = true)),
        request = UserSearchInput("toy", None, None, limit = lexicalPrefix.size),
        runtimeSpec = runtimeSpec,
      )

      assert(selected == lexicalPrefix)
    }
  }

  "SemanticSupplementPolicy.AppendAll" should {
    "append semantic candidates after the lexical prefix without reordering the prefix" in {
      val lexicalPrefix = List(ToyDocument("lex-1", eligible = true), ToyDocument("lex-2", eligible = true))
      val semanticCandidates = List(ToyDocument("sem-1", eligible = true), ToyDocument("sem-2", eligible = true))
      val policy = SemanticSupplementPolicy.AppendAll[ToyDocument]()

      val selected = policy.select(
        lexicalPrefix = lexicalPrefix,
        semanticCandidates = semanticCandidates,
        request = UserSearchInput("toy", None, None, limit = 4),
        runtimeSpec = runtimeSpec,
      )

      assert(selected == lexicalPrefix ++ semanticCandidates)
    }
  }

  private final case class ToyDocument(id: String, eligible: Boolean)

  private val idField: SearchField[ToyDocument] =
    SearchField(
      path = "id",
      kind = SearchFieldKind.Keyword,
      extract = document => Some(SearchValue.Keyword(document.id)),
    )

  private val runtimeSpec: SearchRuntimeSpec[ToyDocument] =
    SearchRuntimeSpec(
      documentSpec = SearchDocumentSpec(
        indexName = "toy-semantic-supplement",
        id = _.id,
        fields = List(idField),
      ),
      querySchema = SearchQuerySchema(
        serviceName = idField,
        categoryName = idField,
        priceFrom = idField,
        durationMin = idField,
        location = idField,
        enumAttribute = code => Left(leaderboard.model.QueryFailure.domain(s"enum $code")),
        booleanAttribute = code => Left(leaderboard.model.QueryFailure.domain(s"boolean $code")),
        intAttribute = code => Left(leaderboard.model.QueryFailure.domain(s"int $code")),
        decimalAttribute = code => Left(leaderboard.model.QueryFailure.domain(s"decimal $code")),
      ),
      requestSpec = SearchRequestSpec(),
      facetSpec = FacetSpec(enabled = false, fields = Nil),
      carouselSpec = CarouselSpec(
        variantSize = 10,
        providerGroupField = idField,
        serviceIntentGroupField = idField,
      ),
      payloadSpecs = Map.empty,
      embeddingSpec = None,
      vectorSearchSpec = None,
    )
}
