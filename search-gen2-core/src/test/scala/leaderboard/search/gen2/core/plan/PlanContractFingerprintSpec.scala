package leaderboard.search.gen2.core.plan

import leaderboard.search.gen2.contract.*
import org.scalatest.wordspec.AnyWordSpec

import scala.collection.immutable.ListMap

final class PlanContractFingerprintSpec extends AnyWordSpec {
  import PlanIdentityFixtures.*

  private val noContributions: PlanContractContributions = Map.empty

  private def declaration(
    identity: SearchField[InventoryDocument, String],
    fields: Vector[SearchField[InventoryDocument, ?]],
    documentId: String = "inventory",
  ): SearchDocumentDeclaration[InventoryDocument, String] =
    fields.foldLeft(searchDocument[InventoryDocument](documentId).id(identity)) { (builder, field) =>
      builder.field(field)
    }.build match {
      case Right(value) => value
      case Left(error)  => fail(s"expected valid declaration, got $error")
    }

  private val requiredDepartment =
    leaderboard.search.gen2.contract.field[InventoryDocument, String]("department", _.department).keyword

  private val identityText = leaderboard.search.gen2.contract.field[InventoryDocument, String]("department", _.department).text
  private val identityAlternateCodec = leaderboard.search.gen2.contract.field[InventoryDocument, String]("department", _.department)(using alternateStringCodec).keyword
  private val identityDifferentCapabilities = requiredDepartment.filterable(FilterOperator.Equal, FilterOperator.In).facetable(FacetMode.Terms)

  private val availabilityStartWithChangedPath = leaderboard.search.gen2.contract.field[InventoryDocument, Int]("availabilityStart", _.availabilityEnd).integer.filterable(FilterOperator.Range).facetable(FacetMode.Range)
  private val availabilityStartWithChangedSemantic = availabilityStart.withSemantic("availability-start")
  private val availabilityStartWithChangedFilter = availabilityStart.filterable(FilterOperator.Equal)
  private val availabilityStartWithChangedId = leaderboard.search.gen2.contract.field[InventoryDocument, Int]("availabilityStart-v2", _.availabilityStart).integer.filterable(FilterOperator.Range).facetable(FacetMode.Range)
  private val supplierBase = leaderboard.search.gen2.contract.field[InventoryDocument, String]("supplier", _.supplier).keyword
  private val optionalSupplier = computedField[InventoryDocument, String]("supplier", "supplier")(document => Some(document.supplier)).keyword
  private val textSupplier = leaderboard.search.gen2.contract.field[InventoryDocument, String]("supplier", _.supplier).text
  private val alternateSupplierCodec = leaderboard.search.gen2.contract.field[InventoryDocument, String]("supplier", _.supplier)(using alternateStringCodec).keyword
  private val supplierWithFacet = supplierBase.facetable(FacetMode.Terms)
  private val supplierWithGroup = supplierBase.groupable(GroupMode.Terms)
  private val supplierWithPayload = supplierBase.payloadEligible
  private val headlineWithoutSearch = leaderboard.search.gen2.contract.field[InventoryDocument, String]("headline", _.headline).text
  private val qualityWithoutSort = leaderboard.search.gen2.contract.field[InventoryDocument, Int]("quality", _.quality).integer.filterable(FilterOperator.Range).facetable(FacetMode.Range)

  private val baseFields: Vector[SearchField[InventoryDocument, ?]] = Vector(availabilityStart, quality, supplierBase, headline)
  private val base = declaration(requiredDepartment, baseFields)
  private val version = PlanContractVersion("inventory-plan-v1")

  "PlanContractFingerprint" should {
    "derive deterministically from the same executable declaration" in {
      assert(PlanContractFingerprint.compute(version, base, noContributions) == PlanContractFingerprint.compute(version, base, noContributions))
      assert(PlanContractFingerprint.compute(version, base) == PlanContractFingerprint.compute(version, base, noContributions))
    }

    "change when the explicit contract version changes" in {
      assert(PlanContractFingerprint.compute(version, base, noContributions) != PlanContractFingerprint.compute(PlanContractVersion("inventory-plan-v2"), base, noContributions))
    }

    "change for every reachable document-structure component" in {
      val baseFingerprint = PlanContractFingerprint.compute(version, base, noContributions)
      Vector(
        "document id" -> PlanContractFingerprint.compute(version, declaration(requiredDepartment, baseFields, "inventory-v2"), noContributions),
        "identity id" -> PlanContractFingerprint.compute(version, declaration(leaderboard.search.gen2.contract.field[InventoryDocument, String]("department-v2", _.department).keyword, baseFields), noContributions),
        "identity path" -> PlanContractFingerprint.compute(version, declaration(leaderboard.search.gen2.contract.field[InventoryDocument, String]("department", _.alternateLabel).keyword, baseFields), noContributions),
        "identity kind" -> PlanContractFingerprint.compute(version, declaration(identityText, baseFields), noContributions),
        "identity value type" -> PlanContractFingerprint.compute(version, declaration(identityAlternateCodec, baseFields), noContributions),
        "identity semantic" -> PlanContractFingerprint.compute(version, declaration(requiredDepartment.withSemantic("alternate-department"), baseFields), noContributions),
        "identity capabilities" -> PlanContractFingerprint.compute(version, declaration(identityDifferentCapabilities, baseFields), noContributions),
        "ordinary field id" -> PlanContractFingerprint.compute(version, declaration(requiredDepartment, Vector(availabilityStartWithChangedId, quality, supplierBase, headline)), noContributions),
        "ordinary field path" -> PlanContractFingerprint.compute(version, declaration(requiredDepartment, Vector(availabilityStartWithChangedPath, quality, supplierBase, headline)), noContributions),
        "ordinary field kind" -> PlanContractFingerprint.compute(version, declaration(requiredDepartment, Vector(availabilityStart, quality, textSupplier, headline)), noContributions),
        "ordinary field value type" -> PlanContractFingerprint.compute(version, declaration(requiredDepartment, Vector(availabilityStart, quality, alternateSupplierCodec, headline)), noContributions),
        "ordinary field semantic" -> PlanContractFingerprint.compute(version, declaration(requiredDepartment, Vector(availabilityStartWithChangedSemantic, quality, supplierBase, headline)), noContributions),
        "ordinary field presence" -> PlanContractFingerprint.compute(version, declaration(requiredDepartment, Vector(availabilityStart, quality, optionalSupplier, headline)), noContributions),
        "searchable" -> PlanContractFingerprint.compute(version, declaration(requiredDepartment, Vector(availabilityStart, quality, supplierBase, headlineWithoutSearch)), noContributions),
        "filter operators" -> PlanContractFingerprint.compute(version, declaration(requiredDepartment, Vector(availabilityStartWithChangedFilter, quality, supplierBase, headline)), noContributions),
        "facet modes" -> PlanContractFingerprint.compute(version, declaration(requiredDepartment, Vector(availabilityStart, quality, supplierWithFacet, headline)), noContributions),
        "sort modes" -> PlanContractFingerprint.compute(version, declaration(requiredDepartment, Vector(availabilityStart, qualityWithoutSort, supplierBase, headline)), noContributions),
        "group modes" -> PlanContractFingerprint.compute(version, declaration(requiredDepartment, Vector(availabilityStart, quality, supplierWithGroup, headline)), noContributions),
        "payload eligibility" -> PlanContractFingerprint.compute(version, declaration(requiredDepartment, Vector(availabilityStart, quality, supplierWithPayload, headline)), noContributions),
        "field addition" -> PlanContractFingerprint.compute(version, declaration(requiredDepartment, baseFields :+ availabilityEnd), noContributions),
        "field removal" -> PlanContractFingerprint.compute(version, declaration(requiredDepartment, baseFields.dropRight(1)), noContributions),
        "field order" -> PlanContractFingerprint.compute(version, declaration(requiredDepartment, Vector(quality, availabilityStart, supplierBase, headline)), noContributions),
      ).foreach { case (label, fingerprint) =>
        assert(fingerprint != baseFingerprint, s"$label should change the contract fingerprint")
      }
    }

    "compose typed backend contributions canonically" in {
      val elasticId = PlanContractContributionId("elasticsearch-execution")
      val qdrantId = PlanContractContributionId("qdrant-candidate")
      val versionOne = PlanContractContributionVersion("v1")
      val withBoth = PlanContractFingerprint.compute(version, base, ListMap(elasticId -> versionOne, qdrantId -> versionOne))
      assert(withBoth != PlanContractFingerprint.compute(version, base, noContributions))
      assert(withBoth == PlanContractFingerprint.compute(version, base, ListMap(qdrantId -> versionOne, elasticId -> versionOne)))
      assert(withBoth != PlanContractFingerprint.compute(version, base, ListMap(elasticId -> PlanContractContributionVersion("v2"), qdrantId -> versionOne)))
      assert(withBoth != PlanContractFingerprint.compute(version, base, Map(elasticId -> versionOne)))
    }

    "change when an explicit contract contribution changes" in {
      val contributionId = PlanContractContributionId("elasticsearch-execution")
      assert(
        PlanContractFingerprint.compute(version, base, Map(contributionId -> PlanContractContributionVersion("v1"))) !=
          PlanContractFingerprint.compute(version, base, Map(contributionId -> PlanContractContributionVersion("v2")))
      )
    }

    "not depend on the human-readable structure renderer" in {
      val fingerprint = PlanContractFingerprint.compute(version, base, noContributions)
      assert(fingerprint.value != base.renderStructure)
    }
  }
}
