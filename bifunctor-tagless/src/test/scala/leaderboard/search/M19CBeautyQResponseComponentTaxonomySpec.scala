package leaderboard.search

import io.circe.parser.parse
import leaderboard.search.eval.*
import leaderboard.search.eval.M19CBeautyQResponseComponentTaxonomy.{
  BeautyResponseComponentId,
  ComponentEvidence,
}
import org.scalatest.wordspec.AnyWordSpec

import java.nio.charset.StandardCharsets
import scala.io.Source
import scala.util.Using

final class M19CBeautyQResponseComponentTaxonomySpec extends AnyWordSpec {

  private val allComponents = BeautyResponseComponentId.stableOrder

  "M19CBeautyQResponseComponentTaxonomy.ResponseComponentFacts" should {

    "list every source-confirmed response component from BeautySearchResponse" in {
      val components = M19CBeautyQResponseComponentTaxonomy.ResponseComponentFacts.map(_.componentId)

      assert(components == allComponents)
      assert(components.contains(BeautyResponseComponentId.VariantCarousel))
      assert(components.contains(BeautyResponseComponentId.ProviderCarousel))
      assert(components.contains(BeautyResponseComponentId.ServiceIntentCarousel))
      assert(components.contains(BeautyResponseComponentId.Facets))
      assert(components.contains(BeautyResponseComponentId.InferredFilters))
    }

    "mark component identity as source-confirmed for every component" in {
      M19CBeautyQResponseComponentTaxonomy.ResponseComponentFacts.foreach { fact =>
        assert(fact.sourceConfirmed, s"${fact.componentId.render} should be source-confirmed")
        assert(fact.sourceEvidenceCitations.nonEmpty, s"${fact.componentId.render} should cite evidence")
      }
    }

    "mark the production owner as source-confirmed for every component" in {
      M19CBeautyQResponseComponentTaxonomy.ResponseComponentFacts.foreach { fact =>
        assert(fact.productionOwnerSourceConfirmed, s"${fact.componentId.render} should have a source-confirmed owner")
        assert(fact.productionOwnerLabel.nonEmpty)
        assert(fact.sourceDataForBuild.nonEmpty)
      }
    }

    "treat ES as the ES-candidate evidence source for the three carousels" in {
      val byId = M19CBeautyQResponseComponentTaxonomy.ResponseComponentFacts.map(f => f.componentId -> f).toMap

      assert(byId(BeautyResponseComponentId.VariantCarousel).esEvidence == ComponentEvidence.BackendCandidateEvidence)
      assert(byId(BeautyResponseComponentId.ProviderCarousel).esEvidence == ComponentEvidence.BackendCandidateEvidence)
      assert(byId(BeautyResponseComponentId.ServiceIntentCarousel).esEvidence == ComponentEvidence.BackendCandidateEvidence)
    }

    "treat Qdrant as candidate-level evidence only for the two grouped carousels" in {
      val byId = M19CBeautyQResponseComponentTaxonomy.ResponseComponentFacts.map(f => f.componentId -> f).toMap

      assert(byId(BeautyResponseComponentId.VariantCarousel).qdrantEvidence == ComponentEvidence.BackendCandidateEvidence)
      assert(byId(BeautyResponseComponentId.ProviderCarousel).qdrantEvidence == ComponentEvidence.CandidateLevelOnly)
      assert(byId(BeautyResponseComponentId.ServiceIntentCarousel).qdrantEvidence == ComponentEvidence.CandidateLevelOnly)
    }

    "record that Qdrant does not produce facets or inferred filters in the listed source files" in {
      val byId = M19CBeautyQResponseComponentTaxonomy.ResponseComponentFacts.map(f => f.componentId -> f).toMap

      assert(byId(BeautyResponseComponentId.Facets).qdrantEvidence == ComponentEvidence.NotApplicable)
      assert(byId(BeautyResponseComponentId.InferredFilters).qdrantEvidence == ComponentEvidence.NotApplicable)
      assert(byId(BeautyResponseComponentId.Facets).candidateLevelOnly)
      assert(byId(BeautyResponseComponentId.InferredFilters).candidateLevelOnly)
    }

    "raise at least one open policy question per component and never claim a policy choice" in {
      M19CBeautyQResponseComponentTaxonomy.ResponseComponentFacts.foreach { fact =>
        assert(fact.policyQuestions.nonEmpty, s"${fact.componentId.render} should record open questions")
        fact.policyQuestions.foreach { question =>
          assert(question.nonEmpty)
          assert(
            question.contains("?") || question.contains("?") || question.trim.endsWith("?"),
            s"policy questions should contain a question, got: $question",
          )
          assert(!question.contains("approved"), s"policy questions must not pre-approve policy, got: $question")
          assert(!question.contains("decided"), s"policy questions must not pre-decide policy, got: $question")
        }
      }
    }
  }

  "BeautyQResponseComponentTaxonomy.fromEvalQueries" should {

    "report the offline/eval-only boundary and never claim serving-policy status" in {
      val taxonomy = M19CBeautyQResponseComponentTaxonomy.BeautyQResponseComponentTaxonomy.fromEvalQueries(Nil)

      assert(taxonomy.offlineEvalOnly)
      assert(taxonomy.notServingPolicy)
      assert(taxonomy.components == M19CBeautyQResponseComponentTaxonomy.ResponseComponentFacts)
    }
  }

  "EvalQueryCoverage.fromQuery" should {

    "preserve available fields and keep missing fields explicit" in {
      val taxonomy = M19CBeautyQResponseComponentTaxonomy.BeautyQResponseComponentTaxonomy.fromEvalQueries(
        List(sampleQuery("q_demo_001", hasVariant = true, hasProvider = true, hasService = true))
      )

      val row = onlyCoverage(taxonomy)

      assert(row.queryId == "q_demo_001")
      assert(row.queryText.contains("manicure"))
      assert(row.language.contains("ru"))
      assert(row.hasVariantCarouselExpectations)
      assert(row.hasProviderCarouselExpectations)
      assert(row.hasServiceIntentCarouselExpectations)
      assert(!row.hasFacetsExpectations)
      assert(!row.hasInferredFiltersExpectations)
      assert(row.hasTopKConstraints)
      assert(row.hasScoring)
      assert(row.variantAcceptableIdsCount.contains(2))
      assert(row.providerAcceptableIdsCount.contains(1))
      assert(row.serviceIntentAcceptableIdsCount.contains(1))
      assert(row.informVariantRecall)
      assert(row.informProviderCarousel)
      assert(row.informServiceIntentCarousel)
      assert(!row.informFacets)
      assert(!row.informInferredFilters)
    }

    "mark query coverage as candidate-level only when facets/inferredFilters are absent" in {
      val taxonomy = M19CBeautyQResponseComponentTaxonomy.BeautyQResponseComponentTaxonomy.fromEvalQueries(
        List(sampleQuery("q_demo_002", hasVariant = true, hasProvider = false, hasService = false))
      )

      val row = onlyCoverage(taxonomy)

      assert(row.candidateLevelOnly)
      assert(row.informVariantRecall)
      assert(!row.informProviderCarousel)
      assert(!row.informServiceIntentCarousel)
    }

    "treat query types as unknown and leave query classes empty for unrecognised tags" in {
      val taxonomy = M19CBeautyQResponseComponentTaxonomy.BeautyQResponseComponentTaxonomy.fromEvalQueries(
        List(sampleQuery(queryId = "q_demo_003", queryTypes = List("completely_unmapped_tag")))
      )

      val row = onlyCoverage(taxonomy)

      assert(row.rawQueryTypes == List("completely_unmapped_tag"))
      assert(row.queryClasses.isEmpty)
    }

    "map known query type tags to EngineEvalQueryClass" in {
      val taxonomy = M19CBeautyQResponseComponentTaxonomy.BeautyQResponseComponentTaxonomy.fromEvalQueries(
        List(sampleQuery(queryId = "q_demo_004", queryTypes = List("direct", "attribute")))
      )

      val row = onlyCoverage(taxonomy)

      assert(row.queryClasses.contains(EngineEvalQueryClass.ExactService))
      assert(row.queryClasses.contains(EngineEvalQueryClass.StructuredFilter))
    }

    "treat queries with no notes as not having notes rather than inventing a notes preview" in {
      val taxonomy = M19CBeautyQResponseComponentTaxonomy.BeautyQResponseComponentTaxonomy.fromEvalQueries(
        List(sampleQuery(queryId = "q_demo_005", hasNotes = false))
      )

      val row = onlyCoverage(taxonomy)

      assert(!row.hasNotes)
      assert(row.notesPreview.isEmpty)
    }
  }

  "Taxonomy over the listed eval-query JSON" should {

    "load the listed dataset and report a known query count without inventing fields" in {
      val queries = loadEvalQueries()

      assert(queries.nonEmpty)
      assert(queries.size == 64)
    }

    "report a known carousels expectation for every query and no facets/inferredFilters expectations" in {
      val queries = loadEvalQueries()
      val taxonomy = M19CBeautyQResponseComponentTaxonomy.BeautyQResponseComponentTaxonomy.fromEvalQueries(queries)

      taxonomy.evalQueryCoverage.foreach { row =>
        assert(row.hasVariantCarouselExpectations, s"${row.queryId} should carry variantCarousel expectations")
        assert(row.hasProviderCarouselExpectations, s"${row.queryId} should carry providerCarousel expectations")
        assert(row.hasServiceIntentCarouselExpectations, s"${row.queryId} should carry serviceIntentCarousel expectations")
        assert(!row.hasFacetsExpectations, s"${row.queryId} must not invent facets expectations")
        assert(!row.hasInferredFiltersExpectations, s"${row.queryId} must not invent inferredFilters expectations")
        assert(row.informVariantRecall)
        assert(row.informProviderCarousel)
        assert(row.informServiceIntentCarousel)
        assert(!row.informFacets)
        assert(!row.informInferredFilters)
      }
    }

    "preserve query-class diversity from the listed query type tags" in {
      val queries = loadEvalQueries()
      val taxonomy = M19CBeautyQResponseComponentTaxonomy.BeautyQResponseComponentTaxonomy.fromEvalQueries(queries)

      val distinctClasses = taxonomy.evalQueryCoverage.flatMap(_.queryClasses).toSet
      assert(distinctClasses.nonEmpty)
      assert(distinctClasses.size >= 3)
    }

    "keep notes preview truncated rather than echoing whole notes back as content" in {
      val queries = loadEvalQueries()
      val taxonomy = M19CBeautyQResponseComponentTaxonomy.BeautyQResponseComponentTaxonomy.fromEvalQueries(queries)

      val withNotes = taxonomy.evalQueryCoverage.filter(_.hasNotes)
      assert(withNotes.nonEmpty)
    }
  }

  private def onlyCoverage(
    taxonomy: M19CBeautyQResponseComponentTaxonomy.BeautyQResponseComponentTaxonomy
  ): M19CBeautyQResponseComponentTaxonomy.EvalQueryCoverage =
    taxonomy.evalQueryCoverage match {
      case row :: Nil => row
      case other      => fail(s"expected exactly one eval query coverage row, got: $other")
    }

  private def sampleQuery(
    queryId: String,
    hasVariant: Boolean = true,
    hasProvider: Boolean = true,
    hasService: Boolean = true,
    hasNotes: Boolean = true,
    queryTypes: List[String] = List("direct"),
  ): BeautyQEvalQueryJson =
    BeautyQEvalQueryJson(
      queryId = queryId,
      queryText = Some("manicure"),
      language = Some("ru"),
      queryTypes = queryTypes,
      notes = if (hasNotes) Some("sample note") else None,
      expected = Some(
        BeautyQEvalQueryExpectedJson(
          variantCarousel = if (hasVariant) Some(BeautyQEvalCarouselJson(Some(2), None, None, Some(io.circe.Json.obj()))) else None,
          providerCarousel = if (hasProvider) Some(BeautyQEvalCarouselJson(None, Some(1), None, Some(io.circe.Json.obj()))) else None,
          serviceIntentCarousel = if (hasService) Some(BeautyQEvalCarouselJson(None, None, Some(1), Some(io.circe.Json.obj()))) else None,
          facets = None,
          inferredFilters = None,
        )
      ),
      scoring = Some(BeautyQEvalQueryScoringJson(hasVariant, hasProvider, hasService)),
    )

  private def loadEvalQueries(): List[BeautyQEvalQueryJson] = {
    val stream = Option(getClass.getResourceAsStream(M9BeautyQSearchEvalQueryDataset.ResourcePath))

    stream match {
      case None =>
        fail(s"missing checked-in dataset resource ${M9BeautyQSearchEvalQueryDataset.ResourcePath}")
      case Some(value) =>
        val text = Using.resource(Source.fromInputStream(value, StandardCharsets.UTF_8.name()))(_.mkString)
        val json = parse(text).getOrElse(fail("invalid eval-query JSON"))
        val queries = json.hcursor.downField("queries")
        if (queries.succeeded) {
          queries.values
            .getOrElse(fail("queries array is not a JSON array"))
            .map(BeautyQEvalQueryJson.decoder.decodeJson)
            .toList
            .map {
              case Right(value)  => value
              case Left(failure) => fail(s"failed to decode query: ${failure.message}")
            }
        } else {
          fail("queries field missing from the listed dataset")
        }
    }
  }
}
