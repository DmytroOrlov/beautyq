package leaderboard.search

import io.circe.{Json, JsonObject}
import leaderboard.model.QueryFailure
import leaderboard.search.document.SearchDocumentPayloadSpec
import leaderboard.search.dsl.*
import org.scalatest.wordspec.AnyWordSpec

/** Contractual + Blackbox + Atomic: the generic runtime fingerprint over a toy document domain (not
  * `VariantSearchDocument`), proving that the fingerprint inputs are derived from the runtime/schema spec
  * and the document schema rather than any concrete domain.
  */
final class SearchRuntimeFingerprintSpec extends AnyWordSpec {
  import SearchRuntimeFingerprintSpec.*

  private val docA = ToyDoc("a", "alpha", BigDecimal(10))
  private val docB = ToyDoc("b", "bravo", BigDecimal(20))
  private val docC = ToyDoc("c", "charlie", BigDecimal(30))
  private val documents = List(docA, docB, docC)

  private def fingerprint(
    docs: List[ToyDoc] = documents,
    spec: SearchRuntimeSpec[ToyDoc] = baseRuntimeSpec,
    catalogSource: String = "toy-catalog",
    extra: JsonObject = JsonObject.empty,
  ): String =
    SearchRuntimeFingerprint.value(
      SearchRuntimeFingerprint.inputs("toy-v1", catalogSource, docs, spec, extra)
    )

  private def runtimeSection(spec: SearchRuntimeSpec[ToyDoc]): Json =
    SearchRuntimeFingerprint
      .inputs("toy-v1", "toy-catalog", documents, spec, JsonObject.empty)
      .hcursor
      .get[Json]("runtime")
      .getOrElse(fail("expected a runtime section in the fingerprint inputs"))

  "SearchRuntimeFingerprint" should {
    "produce deterministic inputs for the same runtime, documents, and extra sections" in {
      val first = SearchRuntimeFingerprint.inputs("toy-v1", "toy-catalog", documents, baseRuntimeSpec, JsonObject.empty)
      val second = SearchRuntimeFingerprint.inputs("toy-v1", "toy-catalog", documents, baseRuntimeSpec, JsonObject.empty)
      assert(first == second)
      assert(SearchRuntimeFingerprint.value(first) == SearchRuntimeFingerprint.value(second))
    }

    "sort documents by documentSpec.id so input order does not change the fingerprint" in {
      assert(fingerprint(docs = documents) == fingerprint(docs = documents.reverse))
    }

    "derive catalog document JSON from the document schema" in {
      val cursor = SearchRuntimeFingerprint
        .inputs("toy-v1", "toy-catalog", documents, baseRuntimeSpec, JsonObject.empty)
        .hcursor
      val firstDocument = cursor.downField("catalog").downField("documents").downN(0)
      assert(firstDocument.get[String]("name") == Right(docA.name))
      assert(firstDocument.get[String]("id") == Right(docA.id))
    }

    "change the fingerprint when a document content field changes" in {
      val changed = documents.map {
        case doc if doc.id == docA.id => doc.copy(name = s"${doc.name}-changed")
        case doc                      => doc
      }
      assert(fingerprint() != fingerprint(docs = changed))
    }

    "change runtime schema inputs when a document field path changes" in {
      val changedFields = baseFields.map {
        case field if field.path == nameField.path => field.copy(path = "name_v2")
        case field                                 => field
      }
      val changedSpec = baseRuntimeSpec.copy(documentSpec = documentSpec(changedFields))
      assert(runtimeSection(baseRuntimeSpec) != runtimeSection(changedSpec))
    }

    "change runtime schema inputs when a document field kind changes" in {
      val changedFields = baseFields.map {
        case field if field.path == nameField.path => field.copy(kind = SearchFieldKind.Keyword)
        case field                                 => field
      }
      val changedSpec = baseRuntimeSpec.copy(documentSpec = documentSpec(changedFields))
      assert(runtimeSection(baseRuntimeSpec) != runtimeSection(changedSpec))
    }

    "change runtime schema inputs when a document field semantic changes" in {
      val changedFields = baseFields.map {
        case field if field.path == nameField.path => field.copy(semantic = Some(SearchFieldSemantic.ServiceText))
        case field                                 => field
      }
      val changedSpec = baseRuntimeSpec.copy(documentSpec = documentSpec(changedFields))
      assert(runtimeSection(baseRuntimeSpec) != runtimeSection(changedSpec))
    }

    "change runtime schema inputs when the qdrant payload fields change" in {
      val changedSpec = baseRuntimeSpec.copy(
        payloadSpecs = Map(
          SearchRuntimeSpec.QdrantPayloadSpecName ->
            SearchDocumentPayloadSpec(documentSpec(baseFields), List(idField))
        )
      )
      assert(runtimeSection(baseRuntimeSpec) != runtimeSection(changedSpec))
    }

    "change runtime schema inputs when the embedding source fields change" in {
      val changedSpec = baseRuntimeSpec.copy(
        embeddingSpec = Some(
          EmbeddingSpec[ToyDoc]("toy-vector", "toy-model", 8, VectorDistance.Cosine, List(nameField, priceField))
        )
      )
      assert(runtimeSection(baseRuntimeSpec) != runtimeSection(changedSpec))
    }

    "change runtime schema inputs when the vector config changes" in {
      val changedSpec = baseRuntimeSpec.copy(
        vectorSearchSpec = Some(VectorSearchSpec("toy-collection", "toy-vector-changed", 10, None))
      )
      assert(runtimeSection(baseRuntimeSpec) != runtimeSection(changedSpec))
      assert(fingerprint() != fingerprint(spec = changedSpec))
    }

    "layer extra/backend sections at the top level alongside the runtime sections" in {
      val extra = JsonObject("backend" -> Json.obj("label" -> Json.fromString("toy-backend")))
      val cursor = SearchRuntimeFingerprint
        .inputs("toy-v1", "toy-catalog", documents, baseRuntimeSpec, extra)
        .hcursor
      assert(cursor.get[String]("fingerprintVersion") == Right("toy-v1"))
      assert(cursor.downField("catalog").succeeded)
      assert(cursor.downField("runtime").succeeded)
      assert(cursor.downField("backend").get[String]("label") == Right("toy-backend"))
      assert(fingerprint() != fingerprint(extra = extra))
    }
  }
}

object SearchRuntimeFingerprintSpec {
  final case class ToyDoc(id: String, name: String, price: BigDecimal)

  val idField: SearchField[ToyDoc] =
    SearchField[ToyDoc](
      path = "id",
      kind = SearchFieldKind.Keyword,
      extract = document => Some(SearchValue.Keyword(document.id)),
      semantic = Some(SearchFieldSemantic.VariantId),
      filterable = true,
    )
  val nameField: SearchField[ToyDoc] =
    SearchField[ToyDoc](
      path = "name",
      kind = SearchFieldKind.Text,
      extract = document => Some(SearchValue.Text(document.name)),
      semantic = Some(SearchFieldSemantic.ServiceName),
      searchable = true,
    )
  val priceField: SearchField[ToyDoc] =
    SearchField[ToyDoc](
      path = "price",
      kind = SearchFieldKind.Decimal,
      extract = document => Some(SearchValue.Decimal(document.price)),
      semantic = Some(SearchFieldSemantic.PriceFrom),
      filterable = true,
      facetable = true,
    )
  val categoryField: SearchField[ToyDoc] =
    SearchField[ToyDoc](
      path = "category",
      kind = SearchFieldKind.Keyword,
      extract = _ => None,
      semantic = Some(SearchFieldSemantic.CategoryName),
    )
  val durationField: SearchField[ToyDoc] =
    SearchField[ToyDoc](
      path = "duration",
      kind = SearchFieldKind.Integer,
      extract = _ => None,
      semantic = Some(SearchFieldSemantic.DurationMin),
    )
  val locationField: SearchField[ToyDoc] =
    SearchField[ToyDoc](
      path = "location",
      kind = SearchFieldKind.GeoPoint,
      extract = _ => None,
      semantic = Some(SearchFieldSemantic.Location),
    )

  val baseFields: List[SearchField[ToyDoc]] =
    List(idField, nameField, priceField, categoryField, durationField, locationField)

  def documentSpec(fields: List[SearchField[ToyDoc]]): SearchDocumentSpec[ToyDoc] =
    SearchDocumentSpec[ToyDoc](
      indexName = "toy_index",
      id = _.id,
      fields = fields,
    )

  private def unsupportedAttribute(code: String): Either[QueryFailure, SearchField[ToyDoc]] =
    Left(QueryFailure.domain(s"toy schema has no attribute '$code'"))

  val querySchema: SearchQuerySchema[ToyDoc] =
    SearchQuerySchema[ToyDoc](
      serviceName = nameField,
      categoryName = categoryField,
      priceFrom = priceField,
      durationMin = durationField,
      location = locationField,
      enumAttribute = unsupportedAttribute,
      booleanAttribute = unsupportedAttribute,
      intAttribute = unsupportedAttribute,
      decimalAttribute = unsupportedAttribute,
    )

  val baseRuntimeSpec: SearchRuntimeSpec[ToyDoc] =
    SearchRuntimeSpec[ToyDoc](
      documentSpec = documentSpec(baseFields),
      querySchema = querySchema,
      requestSpec = SearchRequestSpec(),
      facetSpec = FacetSpec[ToyDoc](
        enabled = true,
        fields = List(
          FacetField(nameField, FacetFieldMode.Terms),
          FacetField(
            priceField,
            FacetFieldMode.Ranges(
              List(
                FacetRangeBucket("low", max = Some(BigDecimal(15))),
                FacetRangeBucket("high", min = Some(BigDecimal(15))),
              )
            ),
          ),
        ),
      ),
      carouselSpec = CarouselSpec[ToyDoc](
        providerGroupField = idField,
        serviceIntentGroupField = nameField,
      ),
      payloadSpecs = Map(
        SearchRuntimeSpec.QdrantPayloadSpecName ->
          SearchDocumentPayloadSpec(documentSpec(baseFields), List(idField, nameField))
      ),
      embeddingSpec = Some(EmbeddingSpec[ToyDoc]("toy-vector", "toy-model", 8, VectorDistance.Cosine, List(nameField))),
      vectorSearchSpec = Some(VectorSearchSpec("toy-collection", "toy-vector", 10, None)),
    )
}
