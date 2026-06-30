package leaderboard.search

import leaderboard.search.dsl.*
import leaderboard.search.elasticsearch.ElasticsearchMappingInterpreter
import org.scalatest.wordspec.AnyWordSpec

final class ElasticsearchMappingInterpreterSpec extends AnyWordSpec {
  "ElasticsearchMappingInterpreter.mapping" should {
    "render all field kinds from a generic document spec without BeautyQ fixtures" in {
      val mapping = ElasticsearchMappingInterpreter.mapping(ToyElasticsearchSearchSpec.documentSpec)
      val properties = mapping.hcursor.downField("mappings").downField("properties")

      assert(properties.downField("title").get[String]("type") == Right("text"))
      assert(properties.downField("title").get[String]("analyzer") == Right("standard"))
      assert(properties.downField("serviceName").get[String]("type") == Right("keyword"))
      assert(properties.downField("durationMin").get[String]("type") == Right("integer"))
      assert(properties.downField("priceFrom").get[String]("type") == Right("double"))
      assert(properties.downField("available").get[String]("type") == Right("boolean"))
      assert(properties.downField("location").get[String]("type") == Right("geo_point"))
      assert(properties.downField("attrs").downField("properties").downField("color").get[String]("type") == Right("keyword"))
    }
  }
}

final case class ToyElasticsearchDocument(
  id: String,
  title: String,
  body: String,
  serviceName: String,
  categoryName: String,
  priceFrom: BigDecimal,
  durationMin: Int,
  available: Boolean,
  location: SearchGeoPoint,
  providerId: String,
  serviceId: String,
  color: String,
  level: Int,
  rating: BigDecimal,
)

object ToyElasticsearchSearchSpec {
  val title: SearchField[ToyElasticsearchDocument] =
    SearchField(
      path = "title",
      kind = SearchFieldKind.Text,
      extract = document => Some(SearchValue.Text(document.title)),
      searchable = true,
      boost = 3.0,
      analyzer = Some("standard"),
    )
  val body: SearchField[ToyElasticsearchDocument] =
    SearchField("body", SearchFieldKind.Text, document => Some(SearchValue.Text(document.body)), searchable = true, boost = 1.5)
  val serviceName: SearchField[ToyElasticsearchDocument] =
    SearchField("serviceName", SearchFieldKind.Keyword, document => Some(SearchValue.Keyword(document.serviceName)), semantic = Some(SearchFieldSemantic.ServiceName), filterable = true, facetable = true)
  val categoryName: SearchField[ToyElasticsearchDocument] =
    SearchField("categoryName", SearchFieldKind.Keyword, document => Some(SearchValue.Keyword(document.categoryName)), semantic = Some(SearchFieldSemantic.CategoryName), filterable = true, facetable = true)
  val priceFrom: SearchField[ToyElasticsearchDocument] =
    SearchField("priceFrom", SearchFieldKind.Decimal, document => Some(SearchValue.Decimal(document.priceFrom)), semantic = Some(SearchFieldSemantic.PriceFrom), filterable = true, facetable = true)
  val durationMin: SearchField[ToyElasticsearchDocument] =
    SearchField("durationMin", SearchFieldKind.Integer, document => Some(SearchValue.Integer(document.durationMin)), semantic = Some(SearchFieldSemantic.DurationMin), filterable = true)
  val available: SearchField[ToyElasticsearchDocument] =
    SearchField("available", SearchFieldKind.Boolean, document => Some(SearchValue.Boolean(document.available)), filterable = true)
  val location: SearchField[ToyElasticsearchDocument] =
    SearchField("location", SearchFieldKind.GeoPoint, document => Some(SearchValue.GeoPoint(document.location)), semantic = Some(SearchFieldSemantic.Location))
  val providerId: SearchField[ToyElasticsearchDocument] =
    SearchField("providerId", SearchFieldKind.Keyword, document => Some(SearchValue.Keyword(document.providerId)), filterable = true)
  val serviceId: SearchField[ToyElasticsearchDocument] =
    SearchField("serviceId", SearchFieldKind.Keyword, document => Some(SearchValue.Keyword(document.serviceId)), filterable = true)
  val color: SearchField[ToyElasticsearchDocument] =
    SearchField("attrs.color", SearchFieldKind.Keyword, document => Some(SearchValue.Keyword(document.color)), semantic = Some(SearchFieldSemantic.EnumAttribute("color")), filterable = true, facetable = true)
  val level: SearchField[ToyElasticsearchDocument] =
    SearchField("counts.level", SearchFieldKind.Integer, document => Some(SearchValue.Integer(document.level)), semantic = Some(SearchFieldSemantic.IntAttribute("level")), filterable = true)
  val rating: SearchField[ToyElasticsearchDocument] =
    SearchField("metrics.rating", SearchFieldKind.Decimal, document => Some(SearchValue.Decimal(document.rating)), semantic = Some(SearchFieldSemantic.DecimalAttribute("rating")), filterable = true)

  val documentSpec: SearchDocumentSpec[ToyElasticsearchDocument] =
    SearchDocumentSpec(
      indexName = "toy_documents",
      id = _.id,
      fields = List(title, body, serviceName, categoryName, priceFrom, durationMin, available, location, providerId, serviceId, color, level, rating),
    )

  val querySchema: SearchQuerySchema[ToyElasticsearchDocument] =
    SearchQuerySchema(
      serviceName = serviceName,
      categoryName = categoryName,
      priceFrom = priceFrom,
      durationMin = durationMin,
      location = location,
      enumAttribute = code => if (code == "color") Right(color) else Left(leaderboard.model.QueryFailure.domain(s"unknown enum $code")),
      booleanAttribute = code => if (code == "available") Right(available) else Left(leaderboard.model.QueryFailure.domain(s"unknown boolean $code")),
      intAttribute = code => if (code == "level") Right(level) else Left(leaderboard.model.QueryFailure.domain(s"unknown int $code")),
      decimalAttribute = code => if (code == "rating") Right(rating) else Left(leaderboard.model.QueryFailure.domain(s"unknown decimal $code")),
    )

  val facetSpec: FacetSpec[ToyElasticsearchDocument] =
    FacetSpec(
      enabled = true,
      fields = List(
        FacetField(serviceName, FacetFieldMode.Terms),
        FacetField(
          priceFrom,
          FacetFieldMode.Ranges(
            List(
              FacetRangeBucket("cheap", max = Some(BigDecimal(20))),
              FacetRangeBucket("premium", min = Some(BigDecimal(20))),
            )
          ),
        ),
      ),
    )

  val runtimeSpec: SearchRuntimeSpec[ToyElasticsearchDocument] =
    SearchRuntimeSpec(
      documentSpec = documentSpec,
      querySchema = querySchema,
      requestSpec = SearchRequestSpec(hitWindowSize = 5, textOperator = TextOperator.Or, aggregationSize = 7, geoDistanceScale = "3km", geoDistanceOffset = "1km", geoDistanceDecay = 0.25d),
      facetSpec = facetSpec,
      carouselSpec = CarouselSpec(providerGroupField = providerId, serviceIntentGroupField = serviceId, ranking = RankingSpec(providerDistanceWeight = 1.75)),
      payloadSpecs = Map.empty,
      embeddingSpec = None,
      vectorSearchSpec = None,
    )

  val document: ToyElasticsearchDocument =
    ToyElasticsearchDocument(
      id = "toy-1",
      title = "Fresh haircut",
      body = "Short style",
      serviceName = "Haircut",
      categoryName = "Hair",
      priceFrom = BigDecimal("18.50"),
      durationMin = 30,
      available = true,
      location = SearchGeoPoint(BigDecimal("52.5"), BigDecimal("13.4")),
      providerId = "provider-1",
      serviceId = "service-1",
      color = "red",
      level = 2,
      rating = BigDecimal("4.7"),
    )
}
