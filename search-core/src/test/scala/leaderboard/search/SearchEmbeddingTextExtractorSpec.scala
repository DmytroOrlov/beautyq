package leaderboard.search

import leaderboard.search.dsl.{EmbeddingSpec, SearchDocumentSpec, SearchField, SearchFieldKind, SearchValue, VectorDistance}
import leaderboard.search.interpreter.SearchEmbeddingTextExtractor
import org.scalatest.wordspec.AnyWordSpec

final class SearchEmbeddingTextExtractorSpec extends AnyWordSpec {
  "SearchEmbeddingTextExtractor" should {
    "extract embedding text from configured source fields in source order" in {
      val document = ToyDocument("toy-1", "  first title  ", "second body", "ignored")

      val text = SearchEmbeddingTextExtractor.extract(documentSpec, embeddingSpec(List(bodyField, titleField)), document)

      assert(text == "second body first title")
    }

    "respect SearchDocumentSpec.fieldsByPath" in {
      val document = ToyDocument("toy-1", "title", "body", "outside")

      val text = SearchEmbeddingTextExtractor.extract(documentSpec, embeddingSpec(List(titleField, outsideField, bodyField)), document)

      assert(text == "title body")
    }

    "trim empty values" in {
      val document = ToyDocument("toy-1", "   ", " useful body ", "ignored")

      val text = SearchEmbeddingTextExtractor.extract(documentSpec, embeddingSpec(List(titleField, bodyField)), document)

      assert(text == "useful body")
    }
  }

  private final case class ToyDocument(
    id: String,
    title: String,
    body: String,
    outside: String,
  )

  private val titleField: SearchField[ToyDocument] =
    SearchField(
      path = "title",
      kind = SearchFieldKind.Text,
      extract = document => Some(SearchValue.Text(document.title)),
    )

  private val bodyField: SearchField[ToyDocument] =
    SearchField(
      path = "body",
      kind = SearchFieldKind.Text,
      extract = document => Some(SearchValue.Text(document.body)),
    )

  private val outsideField: SearchField[ToyDocument] =
    SearchField(
      path = "outside",
      kind = SearchFieldKind.Text,
      extract = document => Some(SearchValue.Text(document.outside)),
    )

  private val documentSpec: SearchDocumentSpec[ToyDocument] =
    SearchDocumentSpec(
      indexName = "toy-documents",
      id = _.id,
      fields = List(titleField, bodyField),
    )

  private def embeddingSpec(sourceTextFields: List[SearchField[ToyDocument]]): EmbeddingSpec[ToyDocument] =
    EmbeddingSpec(
      vectorName = "toy-vector",
      modelName = "toy-model",
      dimension = 3,
      distance = VectorDistance.Cosine,
      sourceTextFields = sourceTextFields,
    )
}
