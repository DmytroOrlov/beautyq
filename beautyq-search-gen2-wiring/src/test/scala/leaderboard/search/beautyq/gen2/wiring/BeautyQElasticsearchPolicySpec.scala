package leaderboard.search.beautyq.gen2.wiring

import leaderboard.search.beautyq.gen2.contract.*
import leaderboard.search.gen2.contract.*
import leaderboard.search.gen2.core.plan.PlanContractFingerprint
import leaderboard.search.gen2.elasticsearch.*
import org.scalatest.wordspec.AnyWordSpec

/** Concrete BeautyQ proofs for `BeautyQElasticsearchPolicy`, built from the real
  * `BeautyQSearchDeclarations.variants.Fields`/`.document`/`.plan` values - never a recreated or
  * hand-maintained parallel declaration.
  */
final class BeautyQElasticsearchPolicySpec extends AnyWordSpec {
  import BeautyQSearchDeclarations.variants.Fields

  private val canonicalTextHandles: Vector[SearchField[VariantSearchDocumentGen2, String]] =
    Vector(Fields.allText, Fields.serviceText, Fields.attributeText, Fields.providerText, Fields.locationText)

  "BeautyQElasticsearchPolicy.index" should {
    "declare exactly the five canonical text handles, in declared order" in {
      assert(BeautyQElasticsearchPolicy.index.textFields.map(_.field) == canonicalTextHandles)
    }

    "assign the standard analyzer to every declared text field" in {
      BeautyQElasticsearchPolicy.index.textFields.foreach(mapping => assert(mapping.analyzer == ElasticsearchAnalyzerName.Standard))
    }

    "reference the exact BeautyQSearchDeclarations.variants.Fields.* handles by identity, never recreated ones" in {
      val declaredHandles = BeautyQElasticsearchPolicy.index.textFields.map(_.field)
      assert(declaredHandles.zip(canonicalTextHandles).forall { case (declared, canonical) => declared eq canonical })
    }

    "declare the explicit beautyq-elasticsearch-v1 policy identity" in {
      assert(BeautyQElasticsearchPolicy.index.policyVersion == ElasticsearchPolicyVersion("beautyq-elasticsearch-v1"))
    }

    "carry the framework-owned current compiler and index-format versions - never a BeautyQ-supplied one" in {
      assert(BeautyQElasticsearchPolicy.index.compilerVersion == ElasticsearchCompilerVersion.Current)
      assert(BeautyQElasticsearchPolicy.index.indexFormatVersion == ElasticsearchIndexFormatVersion.Current)
    }

    "be complete for every searchable BeautyQ text field - the exact assignments construct without error" in {
      ElasticsearchIndexPolicy(
        BeautyQSearchDeclarations.variants.document,
        BeautyQSearchDeclarations.variants.plan.contractVersion,
        ElasticsearchPolicyVersion("beautyq-elasticsearch-v1"),
        BeautyQElasticsearchPolicy.index.textFields,
      ) match {
        case Right(_)     => succeed
        case Left(errors) => fail(s"expected a complete, valid policy, got ${errors.toVector}")
      }
    }
  }

  "BeautyQElasticsearchPolicy.contributions/contractFingerprint" should {
    "derive a non-empty contribution that changes the fingerprint the old Map.empty contribution would have produced" in {
      val oldFingerprint = PlanContractFingerprint.compute(BeautyQSearchDeclarations.variants.plan.contractVersion, BeautyQSearchDeclarations.variants.document, Map.empty)
      assert(BeautyQElasticsearchPolicy.contributions.nonEmpty)
      assert(BeautyQElasticsearchPolicy.contractFingerprint != oldFingerprint)
    }

    "share its exact contract fingerprint with BeautyQSearchPlanCompiler's bound plan identity" in {
      val vocabulary = BeautyQIntentVocabulary.value
      val page       = PageRequest(None, PageSize.from(20).getOrElse(fail("expected a valid PageSize")))
      val raw        = BeautySearchRequestGen2(Some("ресницы"), Vector.empty, Vector.empty, Vector.empty, page, None)

      val validated = BeautySearchRequestGen2.validate(raw) match {
        case Right(value) => value
        case Left(errors) => fail(s"fixture request failed to validate: ${errors.toVector}")
      }
      val intent = BeautyQIntentParserGen2.parse(validated, vocabulary) match {
        case Right(value) => value
        case Left(errors) => fail(s"fixture intent failed to parse: ${errors.toVector}")
      }
      val compiled = BeautyQSearchPlanCompiler.compile(validated, intent) match {
        case Right(value) => value
        case Left(errors) => fail(s"fixture failed to compile: ${errors.toVector}")
      }

      assert(compiled.boundPlan.identity.contractFingerprint == BeautyQElasticsearchPolicy.contractFingerprint)
    }
  }
}
