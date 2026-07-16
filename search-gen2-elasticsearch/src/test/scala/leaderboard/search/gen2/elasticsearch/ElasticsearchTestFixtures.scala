package leaderboard.search.gen2.elasticsearch

import leaderboard.search.gen2.contract.*

import java.time.Instant

/** Shared neutral book/library document fixture, unrelated to BeautyQ, covering all eight
  * [[SearchFieldKind]]s plus one optional dotted dynamic family and one same-typed interval-overlap pair
  * - reused by every spec in this module per docs/search/DOMAIN_AUTHORING_PRINCIPLES.md's reuse-proof
  * requirement, mirroring `search-gen2-core`'s own shared `PlanIdentityFixtures` convention.
  */
object ElasticsearchTestFixtures {

  final case class BookDocument(
    isbn: String,
    title: String,
    subtitle: String,
    internalNote: String,
    genre: String,
    pageCount: Int,
    wordCount: Long,
    price: BigDecimal,
    inPrint: Boolean,
    publishedAt: Instant,
    storeLocation: GeoPoint,
    editionRatings: Map[String, Int],
    stockFrom: BigDecimal,
    stockTo: BigDecimal,
  )

  final case class EditionDefinition(code: String)
  val editionDefinitions: Vector[EditionDefinition] = Vector(EditionDefinition("hardcover"), EditionDefinition("paperback"))

  private val declarations = searchFields[BookDocument]("books")

  val isbn: SearchField[BookDocument, String] = declarations.keyword(_.isbn).declare
  val title: SearchField[BookDocument, String] = declarations.text(_.title).searchable.declare
  val subtitle: SearchField[BookDocument, String] = declarations.text(_.subtitle).searchable.declare
  val internalNote: SearchField[BookDocument, String] = declarations.text(_.internalNote).declare

  val genre: SearchField[BookDocument, String] =
    declarations.keyword(_.genre).filterable(FilterOperator.Equal, FilterOperator.In).facetable(FacetMode.Terms).groupable(GroupMode.Terms).declare

  val pageCount: SearchField[BookDocument, Int] =
    declarations.integer(_.pageCount).filterable(FilterOperator.Range).facetable(FacetMode.Range, FacetMode.Terms).sortable(SortMode.Value).declare

  val wordCount: SearchField[BookDocument, Long] = declarations.long(_.wordCount).facetable(FacetMode.Terms).declare

  val price: SearchField[BookDocument, BigDecimal] =
    declarations.decimal(_.price).filterable(FilterOperator.Range).sortable(SortMode.Value).declare

  val inPrint: SearchField[BookDocument, Boolean] =
    declarations.boolean(_.inPrint).filterable(FilterOperator.Equal, FilterOperator.In).facetable(FacetMode.Terms).declare

  val publishedAt: SearchField[BookDocument, Instant] =
    declarations.dateTime(_.publishedAt).filterable(FilterOperator.Range).declare

  val storeLocation: SearchField[BookDocument, GeoPoint] =
    declarations.geoPoint(_.storeLocation).filterable(FilterOperator.GeoDistance).sortable(SortMode.Distance).declare

  val editionRatings: DynamicFieldFamily[BookDocument, Int] = declarations.dynamicMap(_.editionRatings, editionDefinitions)(_.code).inferred.declare

  val stockFrom: SearchField[BookDocument, BigDecimal] =
    declarations.decimal(_.stockFrom).filterable(FilterOperator.Range).facetable(FacetMode.Range).declare

  val stockTo: SearchField[BookDocument, BigDecimal] =
    declarations.decimal(_.stockTo).filterable(FilterOperator.Range).facetable(FacetMode.Range).declare

  val document: SearchDocumentDeclaration[BookDocument, String] = declarations.completeDocument(isbn)

  val planContractVersion: PlanContractVersion   = PlanContractVersion("book-plan-v1")
  val policyVersion: ElasticsearchPolicyVersion = ElasticsearchPolicyVersion("book-elasticsearch-v1")

  val bothTextFields: Vector[ElasticsearchTextFieldMapping[BookDocument]] =
    Vector(ElasticsearchTextFieldMapping(title, ElasticsearchAnalyzerName.Standard), ElasticsearchTextFieldMapping(subtitle, ElasticsearchAnalyzerName.Standard))

  val policy: ElasticsearchIndexPolicy[BookDocument, String] =
    ElasticsearchIndexPolicy.unsafeFrom(document, policyVersion, bothTextFields)

  val queryTextFields: Vector[ElasticsearchWeightedTextField[BookDocument]] =
    Vector(ElasticsearchWeightedTextField(title, ElasticsearchQueryWeight(3.0)), ElasticsearchWeightedTextField(subtitle, ElasticsearchQueryWeight(1.5)))

  val geoScoringPolicy: ElasticsearchGeoScoringPolicy =
    ElasticsearchGeoScoringPolicy(
      scale = Distance(BigDecimal(1000)),
      offset = Distance(BigDecimal(0)),
      decay = ElasticsearchGeoDecay(0.3),
      weight = ElasticsearchQueryWeight(2.0),
    )

  val defaultSortPolicy: ElasticsearchDefaultSortPolicy =
    ElasticsearchDefaultSortPolicy(relevanceDirection = SortDirection.Asc, identityTieBreakerDirection = SortDirection.Desc)

  // The complete Elasticsearch policy (index + query), deliberately using different numeric choices and
  // a different text operator/sort direction than BeautyQ's own policy, so a test that passes only because
  // it happens to reuse BeautyQ's exact values would be caught.
  val fullPolicy: ElasticsearchPolicy[BookDocument, String] =
    ElasticsearchPolicy.unsafeFrom(
      planContractVersion = planContractVersion,
      index = policy,
      queryTextFields = queryTextFields,
      textOperator = ElasticsearchTextOperator.Or,
      geoScoringPolicy = Some(geoScoringPolicy),
      totalHitsPolicy = ElasticsearchTotalHitsPolicy.ExactRequired,
      defaultSortPolicy = defaultSortPolicy,
    )

  // A representative, fully-populated document, free of trailing-zero decimal noise, shared by the
  // document- and generation-compiler specs.
  val bookA: BookDocument =
    BookDocument(
      isbn = "978-0-13-468599-1",
      title = "Programming in Scala",
      subtitle = "A Comprehensive Step-by-Step Guide",
      internalNote = "warehouse note",
      genre = "Technology",
      pageCount = 852,
      wordCount = 320000L,
      price = BigDecimal("49.99"),
      inPrint = true,
      publishedAt = Instant.parse("2021-03-15T00:00:00Z"),
      storeLocation = GeoPoint(BigDecimal("52.520008"), BigDecimal("13.404954")),
      editionRatings = Map("hardcover" -> 5, "paperback" -> 4),
      stockFrom = BigDecimal("10.5"),
      stockTo = BigDecimal("40.25"),
    )

  final case class OtherDocument(id: String, headline: String)
  private val otherDeclarations = searchFields[OtherDocument]("other")
  val otherHeadline: SearchField[OtherDocument, String] = otherDeclarations.text(_.headline).searchable.declare
}
