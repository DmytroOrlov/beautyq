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
        .get(BeautySearchSpecV1.QdrantPayloadSpecName)
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
      sealed trait ToyConstraint
      val querySchema = SearchQuerySchema[ToyDocument, ToyConstraint](
        fields = List(SearchQueryField("name", nameField)),
        geoScoringField = None,
        resolve = _ => Left(leaderboard.model.QueryFailure.domain("toy constraint")),
        facetConstraint = (_, _) => Left(leaderboard.model.QueryFailure.domain("toy facet")),
      )
      val runtimeSpec = SearchRuntimeSpec[ToyDocument, ToyConstraint](
        documentSpec = documentSpec,
        querySchema = querySchema,
        requestSpec = SearchRequestSpec(),
        facetSpec = FacetSpec(enabled = false, fields = Nil),
        carouselSpec = CarouselSpec(
          limits = List(CarouselLimit("mainSize", 3)),
          groups = List(CarouselGroup("idGroup", idField), CarouselGroup("nameGroup", nameField)),
          ranking = RankingSpec(List(RankingWeight("textScore", 1.0))),
        ),
        payloadSpecs = Map("payload" -> SearchDocumentPayloadSpec(documentSpec, List(idField))),
        embeddingSpec = None,
        vectorSearchSpec = None,
      )

      assert(runtimeSpec.documentSpec.indexName == "toy")
      assert(runtimeSpec.documentSpec.id(ToyDocument("toy-1", "Synthetic")) == "toy-1")
      assert(runtimeSpec.payloadSpecs.keySet == Set("payload"))
    }
  }
}
