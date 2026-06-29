package leaderboard.search

import leaderboard.search.document.{BeautyQVariantSearchDocumentSchema, SearchDocumentPayloadSpec}
import leaderboard.search.dsl.*
import org.scalatest.wordspec.AnyWordSpec

final class SearchRuntimeSpecSpec extends AnyWordSpec {
  "BeautySearchSpecV1.runtimeSpec" should {
    "derive runtime owners from the BeautyQ schema and BeautySearchSpecV1" in {
      val runtimeSpec = BeautySearchSpecV1.runtimeSpec
      val searchSpec = BeautySearchSpecV1.spec
      val qdrantPayloadSpec = runtimeSpec.payloadSpecs
        .get(SearchRuntimeSpec.QdrantPayloadSpecName)
        .getOrElse(fail("expected qdrant payload spec"))

      assert(runtimeSpec.documentSpec == BeautyQVariantSearchDocumentSchema.documentSpec)
      assert(runtimeSpec.querySchema == BeautyQVariantSearchDocumentSchema.querySchema)
      assert(qdrantPayloadSpec == BeautyQVariantSearchDocumentSchema.qdrantPayloadSpec)
      assert(runtimeSpec.embeddingSpec == searchSpec.embeddingSpec)
      assert(runtimeSpec.facetSpec == searchSpec.facetSpec)
      assert(runtimeSpec.carouselSpec == searchSpec.carouselSpec)
      assert(runtimeSpec.requestSpec == searchSpec.requestSpec)
      assert(runtimeSpec.vectorSearchSpec == searchSpec.vectorSearchSpec)
    }

    "remain generic enough for a non-BeautyQ document type" in {
      final case class ToyDocument(id: String, name: String)

      val idField = SearchField[ToyDocument](
        path = "id",
        kind = SearchFieldKind.Keyword,
        extract = document => Some(SearchValue.Keyword(document.id)),
      )
      val nameField = SearchField[ToyDocument](
        path = "name",
        kind = SearchFieldKind.Text,
        extract = document => Some(SearchValue.Text(document.name)),
      )
      val documentSpec = SearchDocumentSpec[ToyDocument](
        indexName = "toy",
        id = _.id,
        fields = List(idField, nameField),
      )
      val querySchema = SearchQuerySchema[ToyDocument](
        serviceName = nameField,
        categoryName = nameField,
        priceFrom = idField,
        durationMin = idField,
        location = idField,
        enumAttribute = code => Left(leaderboard.model.QueryFailure.domain(s"enum $code")),
        booleanAttribute = code => Left(leaderboard.model.QueryFailure.domain(s"boolean $code")),
        intAttribute = code => Left(leaderboard.model.QueryFailure.domain(s"int $code")),
        decimalAttribute = code => Left(leaderboard.model.QueryFailure.domain(s"decimal $code")),
      )
      val runtimeSpec = SearchRuntimeSpec[ToyDocument](
        documentSpec = documentSpec,
        querySchema = querySchema,
        requestSpec = SearchRequestSpec(),
        facetSpec = FacetSpec(enabled = false, fields = Nil),
        carouselSpec = CarouselSpec(
          variantSize = 3,
          providerGroupField = idField,
          serviceIntentGroupField = nameField,
        ),
        payloadSpecs = Map(SearchRuntimeSpec.QdrantPayloadSpecName -> SearchDocumentPayloadSpec(documentSpec, List(idField))),
        embeddingSpec = None,
        vectorSearchSpec = None,
      )

      assert(runtimeSpec.documentSpec.indexName == "toy")
      assert(runtimeSpec.documentSpec.id(ToyDocument("toy-1", "Synthetic")) == "toy-1")
      assert(runtimeSpec.payloadSpecs.keySet == Set(SearchRuntimeSpec.QdrantPayloadSpecName))
    }
  }
}
