package leaderboard.search

import io.circe.parser.parse
import leaderboard.search.eval.M9BeautyQSearchEvalQueryDataset
import leaderboard.search.eval.BeautyQEvalQueryJson
import leaderboard.search.eval.M19FBeautyQEvalComponentExpectationSchema.*
import org.scalatest.wordspec.AnyWordSpec

import java.nio.charset.StandardCharsets
import scala.io.Source
import scala.util.Using

final class M19FBeautyQEvalComponentExpectationSchemaSpec extends AnyWordSpec {

  "M19F component-expectation schema over the checked-in 63-query dataset" should {

    "still decode with the existing narrow decoder (tolerant, no regression)" in {
      val narrow = decodeQueries(loadCheckedInDataset())(BeautyQEvalQueryJson.decoder.decodeJson)
      assert(narrow.size == M9BeautyQSearchEvalQueryDataset.Metadata.queryCount)
    }

    "still decode with the new M19F component-expectation decoder" in {
      val rows = decodeQueries(loadCheckedInDataset())(evalQueryComponentExpectationsDecoder.decodeJson)
      assert(rows.size == M9BeautyQSearchEvalQueryDataset.Metadata.queryCount)
    }

    "mark facets and inferred filters as AbsentFromDataset, not as negative evidence" in {
      val coverage = ComponentExpectationCoverage.fromQueries(
        decodeQueries(loadCheckedInDataset())(evalQueryComponentExpectationsDecoder.decodeJson)
      )

      assert(!coverage.facetsPresentInDataset)
      assert(!coverage.inferredFiltersPresentInDataset)
      assert(coverage.rows.forall(_.facets == ExpectationFieldAvailability.AbsentFromDataset))
      assert(coverage.rows.forall(_.inferredFilters == ExpectationFieldAvailability.AbsentFromDataset))

      // The honesty flags carry through: M19F never claims Qdrant can own facets / inferred filters.
      assert(coverage.qdrantDoesNotOwnFacets)
      assert(coverage.qdrantDoesNotOwnInferredFilters)
      assert(coverage.doesNotApproveHybrid)
    }

    "see provider and service expectations that are present in the checked-in dataset" in {
      val coverage = ComponentExpectationCoverage.fromQueries(
        decodeQueries(loadCheckedInDataset())(evalQueryComponentExpectationsDecoder.decodeJson)
      )

      assert(coverage.providerPresentInDataset)
      assert(coverage.serviceIntentPresentInDataset)
      assert(coverage.rows.forall(_.provider == ExpectationFieldAvailability.PresentWithExpectations))
      assert(coverage.rows.forall(_.serviceIntent == ExpectationFieldAvailability.PresentWithExpectations))
    }

    "carry the source-confirmed provider grouping field on every provider carousel expectation" in {
      val rows = decodeQueries(loadCheckedInDataset())(evalQueryComponentExpectationsDecoder.decodeJson)
      val providers = rows.flatMap(_.expectations.provider)

      assert(providers.size == M9BeautyQSearchEvalQueryDataset.Metadata.queryCount)
      // BeautySearchSpecV1.carouselSpec.providerGroupField = "masterLocationId".
      assert(providers.forall(_.expectedGroupingField.contains("masterLocationId")))
    }

    "carry the source-confirmed service grouping field on every service intent carousel expectation" in {
      val rows = decodeQueries(loadCheckedInDataset())(evalQueryComponentExpectationsDecoder.decodeJson)
      val services = rows.flatMap(_.expectations.serviceIntent)

      assert(services.size == M9BeautyQSearchEvalQueryDataset.Metadata.queryCount)
      // BeautySearchSpecV1.carouselSpec.serviceIntentGroupField = "serviceId".
      assert(services.forall(_.expectedGroupingField.contains("serviceId")))
    }
  }

  "M19F component-expectation schema over a synthetic fixture" should {

    "decode the new provider / service / facet / inferred-filter expectation fields" in {
      val rows = decodeQueries(loadSyntheticFixture())(evalQueryComponentExpectationsDecoder.decodeJson)
      val full = rows.find(_.queryId == "q_syn_full").getOrElse(fail("missing q_syn_full fixture query"))

      val provider = full.expectations.provider.getOrElse(fail("provider expectation should decode"))
      assert(provider.acceptableProviderLocationIds == List("syn-loc-1"))
      assert(provider.expectedGroupingField.contains("masterLocationId"))
      assert(provider.samples.flatMap(_.matchingVariantCount) == List(2))
      assert(provider.samples.flatMap(_.sampleMatchingVariantIds) == List("syn-variant-1", "syn-variant-2"))

      val service = full.expectations.serviceIntent.getOrElse(fail("service expectation should decode"))
      assert(service.acceptableServiceIds == List("syn-service-1"))
      assert(service.expectedGroupingField.contains("serviceId"))
      assert(service.samples.flatMap(_.matchingVariantCount) == List(2))

      val facets = full.expectations.facets.getOrElse(fail("facets expectation should decode"))
      assert(facets.map(_.fieldPath) == List("serviceName"))
      assert(facets.flatMap(_.values).map(_.value) == List("syn-service-name"))
      assert(facets.flatMap(_.values).flatMap(_.count) == List(2))

      val inferred = full.expectations.inferredFilters.getOrElse(fail("inferred-filter expectation should decode"))
      assert(inferred.flatMap(_.explicit) == List(false))
      assert(inferred.forall(_.constraint.isDefined))
    }

    "keep absent fields absent (None), never invented, on a sparse query" in {
      val rows   = decodeQueries(loadSyntheticFixture())(evalQueryComponentExpectationsDecoder.decodeJson)
      val sparse = rows.find(_.queryId == "q_syn_sparse").getOrElse(fail("missing q_syn_sparse fixture query"))

      assert(sparse.expectations.provider.isEmpty)
      assert(sparse.expectations.serviceIntent.isEmpty)
      assert(sparse.expectations.facets.isEmpty)
      assert(sparse.expectations.inferredFilters.isEmpty)
    }

    "propagate schema-present expectations into coverage with the honest tri-state" in {
      val coverage = ComponentExpectationCoverage.fromQueries(
        decodeQueries(loadSyntheticFixture())(evalQueryComponentExpectationsDecoder.decodeJson)
      )

      assert(coverage.facetsPresentInDataset)
      assert(coverage.inferredFiltersPresentInDataset)

      val full   = coverage.rows.find(_.queryId == "q_syn_full").getOrElse(fail("missing q_syn_full row"))
      val sparse = coverage.rows.find(_.queryId == "q_syn_sparse").getOrElse(fail("missing q_syn_sparse row"))

      // Present in the query carrying expectations.
      assert(full.facets == ExpectationFieldAvailability.PresentWithExpectations)
      assert(full.inferredFilters == ExpectationFieldAvailability.PresentWithExpectations)
      assert(full.provider == ExpectationFieldAvailability.PresentWithExpectations)
      assert(full.serviceIntent == ExpectationFieldAvailability.PresentWithExpectations)

      // Supported-by-schema-but-absent-in-this-query: the third honest state, distinct from
      // AbsentFromDataset, because some other query (q_syn_full) does carry the field.
      assert(sparse.facets == ExpectationFieldAvailability.SupportedButAbsentInQuery)
      assert(sparse.inferredFilters == ExpectationFieldAvailability.SupportedButAbsentInQuery)
      assert(sparse.provider == ExpectationFieldAvailability.SupportedButAbsentInQuery)
      assert(sparse.serviceIntent == ExpectationFieldAvailability.SupportedButAbsentInQuery)
    }
  }

  private def loadCheckedInDataset(): io.circe.Json =
    loadResource(M9BeautyQSearchEvalQueryDataset.ResourcePath)

  private def loadSyntheticFixture(): io.circe.Json =
    loadResource("/leaderboard/search/eval/m19f_synthetic_component_expectations.json")

  private def loadResource(path: String): io.circe.Json =
    Option(getClass.getResourceAsStream(path)) match {
      case None =>
        fail(s"missing resource $path")
      case Some(value) =>
        val text = Using.resource(Source.fromInputStream(value, StandardCharsets.UTF_8.name()))(_.mkString)
        parse(text).fold(err => fail(s"invalid JSON in $path: ${err.message}"), identity)
    }

  private def decodeQueries[A](json: io.circe.Json)(decode: io.circe.Json => io.circe.Decoder.Result[A]): List[A] = {
    val queries = json.hcursor.downField("queries")
    if (queries.succeeded) {
      queries.values
        .getOrElse(fail("queries array is not a JSON array"))
        .map(decode)
        .toList
        .map {
          case Right(value)  => value
          case Left(failure) => fail(s"failed to decode query: ${failure.message}")
        }
    } else {
      fail("queries field missing from the dataset")
    }
  }
}
