package leaderboard.search.contract

import org.scalatest.wordspec.AnyWordSpec

// Deterministic generic fixtures. No BeautyQ types anywhere in this file.
final case class FixtureCatalog(rootCount: Int)
final case class FixtureDocument(fieldCount: Int)
final case class FixtureResultUnit(label: String)

final class SearchDomainSpecSpec extends AnyWordSpec {

  private def minimalSpec: SearchDomainSpec[FixtureCatalog, FixtureDocument, FixtureResultUnit] =
    SearchDomainSpec(
      id      = SearchDomainId("fixture-domain"),
      catalog = CatalogSection(FixtureCatalog(rootCount = 2)),
      document = DocumentSection(
        document   = FixtureDocument(fieldCount = 1),
        resultUnit = FixtureResultUnit("unit"),
        fields     = List(SearchField(SearchFieldName("title"), SearchFieldKind.Text)),
      ),
      intent = IntentSection(
        languages        = List(SearchLanguage("en")),
        vocabularies     = List(SearchVocabulary(SearchVocabularyId("colors"), List("red", "blue"), Map.empty)),
        vocabularyGroups = List(SearchVocabularyGroup("attributes", Nil)),
        noiseControls    = List(NoiseControl("filler words", List("the", "a"))),
      ),
      runtime = RuntimeSection(
        declarations = List(
          SearchRuntimeDeclaration(
            SearchBackendId("es-1"),
            SearchBackendKind.Elasticsearch,
            SearchBackendCapabilities(supportsFullText = true, supportsFacets = true, supportsGeo = false, supportsSemanticVector = false),
          ),
          SearchRuntimeDeclaration(
            SearchBackendId("qdrant-1"),
            SearchBackendKind.Qdrant,
            SearchBackendCapabilities(supportsFullText = false, supportsFacets = false, supportsGeo = false, supportsSemanticVector = true),
          ),
        ),
      ),
      response = ResponseSection(
        grouping        = None,
        carousel        = None,
        facets          = None,
        inferredFilters = None,
        presentation    = PresentationMetadata(Map.empty),
        debug           = DebugPolicy(includeExplanation = false, includeScoreBreakdown = false),
      ),
      evaluation = EvalSection(
        acceptedQueryRoles  = List(EvalQueryRole.Golden, EvalQueryRole.Negative),
        negativeControls    = List("must-not-match-x"),
        backendExpectations = Nil,
        scorecard           = EvalScorecardConfig(metrics = List("recall")),
      ),
    )

  "SearchDomainSpec" should {
    "be constructible from a minimal generic assembly with no BeautyQ values" in {
      val spec = minimalSpec
      assert(spec.id == SearchDomainId("fixture-domain"))
    }

    "preserve section ids/names through construction" in {
      val spec = minimalSpec
      assert(spec.catalog.descriptor == FixtureCatalog(2))
      assert(spec.document.fields.head.name == SearchFieldName("title"))
      assert(spec.intent.languages == List(SearchLanguage("en")))
      assert(spec.intent.vocabularies.head.id == SearchVocabularyId("colors"))
    }
  }

  "RuntimeSection" should {
    "declare Elasticsearch and Qdrant backends purely declaratively, with no client import" in {
      val declarations = minimalSpec.runtime.declarations
      assert(declarations.map(_.kind) == List(SearchBackendKind.Elasticsearch, SearchBackendKind.Qdrant))
      assert(declarations.map(_.backendId) == List(SearchBackendId("es-1"), SearchBackendId("qdrant-1")))
    }
  }

  "EvalSection" should {
    "carry an explicit, type-enforced non-production-routing effect" in {
      val evaluation = minimalSpec.evaluation
      assert(evaluation.productionRoutingEffect == EvalProductionRoutingEffect.None)
    }
  }
}
