package leaderboard.search

import leaderboard.search.eval.M9BeautyQSearchEvalQueryDataset
import org.scalatest.wordspec.AnyWordSpec

import java.nio.charset.StandardCharsets
import scala.io.Source
import scala.util.Using

final class M9BeautyQSearchEvalQueryDatasetSpec extends AnyWordSpec {

  "M9BeautyQSearchEvalQueryDataset" should {

    "register the uploaded dataset as a classpath test resource" in {
      val resourceText = readDatasetResource()

      assert(resourceText.nonEmpty)
      assert(resourceText.contains("\"version\": 1"))
      assert(resourceText.contains("\"dataset\": \"wandsbek_hamburg_beauty_services_seed_ready\""))
      assert(resourceText.contains("\"queryCount\": 75"))
    }

    "record deterministic metadata for the seed-ready eval fixture" in {
      val metadata = M9BeautyQSearchEvalQueryDataset.Metadata

      assert(metadata.version == 1)
      assert(metadata.datasetId == "wandsbek_hamburg_beauty_services_seed_ready")
      assert(metadata.queryCount == 75)
      assert(metadata.testUserLocationLabel == "Wandsbek Markt")
    }

    "record the expected language counts" in {
      val counts = M9BeautyQSearchEvalQueryDataset.Metadata.languageCounts

      assert(counts.ru == 41)
      assert(counts.en == 22)
      assert(counts.de == 6)
      assert(counts.mixed == 6)
    }

    "record all target carousel ids" in {
      val metadata = M9BeautyQSearchEvalQueryDataset.Metadata

      assert(metadata.targetCarousels == List("variantCarousel", "providerCarousel", "serviceIntentCarousel"))
      val resourceText = readDatasetResource()
      assert(resourceText.contains("\"id\": \"variantCarousel\""))
      assert(resourceText.contains("\"id\": \"providerCarousel\""))
      assert(resourceText.contains("\"id\": \"serviceIntentCarousel\""))
    }

    "record service and attribute coverage metadata" in {
      val coverage = M9BeautyQSearchEvalQueryDataset.Metadata.coverage

      assert(coverage.serviceCount == 9)
      assert(coverage.enumAttributesCovered)
      assert(coverage.booleanAttributesCovered)
      assert(coverage.numericBigDecimalAttributesCovered)

      val resourceText = readDatasetResource()
      assert(resourceText.contains("\"services\": 9"))
      assert(resourceText.contains("All enum attribute codes present in the model/seed are covered."))
      assert(resourceText.contains("All boolean attribute codes are covered."))
      assert(resourceText.contains("All current numeric attribute groups that appear in the seed are covered."))
    }

    "contain representative multilingual query ids" in {
      val resourceText = readDatasetResource()

      assert(resourceText.contains("\"id\": \"q_nails_001\""))
      assert(resourceText.contains("\"id\": \"q_nails_003\""))
      assert(resourceText.contains("\"id\": \"q_noise_005\""))
    }

    "contain acceptable and forbidden evidence anchors" in {
      val resourceText = readDatasetResource()

      assert(resourceText.contains("\"acceptableVariantIds\""))
      assert(resourceText.contains("\"forbiddenVariantIds\""))
      assert(resourceText.contains("\"acceptableProviderLocationIds\""))
      assert(resourceText.contains("\"acceptableServiceIds\""))
      assert(resourceText.contains("\"forbiddenNotInTopK\""))
    }

    "stay offline eval evidence only without route plugin DI or HTTP involvement" in {
      val boundary = M9BeautyQSearchEvalQueryDataset.Metadata.boundary

      assert(boundary.seedEvalFixtureOnly)
      assert(boundary.notProductionTelemetry)
      assert(boundary.notActivationApproval)
      assert(boundary.realBackendCallsDisabledByDefault)
      assert(boundary.defaultBeautySearchRouteEsBacked)
      assert(!boundary.qdrantProductionActivationApproved)
    }
  }

  private def readDatasetResource(): String = {
    val stream = Option(getClass.getResourceAsStream(M9BeautyQSearchEvalQueryDataset.ResourcePath))

    stream match {
      case Some(value) =>
        Using.resource(Source.fromInputStream(value, StandardCharsets.UTF_8.name()))(_.mkString)
      case None =>
        fail(s"missing checked-in dataset resource ${M9BeautyQSearchEvalQueryDataset.ResourcePath}")
    }
  }
}
