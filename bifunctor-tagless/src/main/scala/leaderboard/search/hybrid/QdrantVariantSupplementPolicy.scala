package leaderboard.search.hybrid

import leaderboard.model.MasterServiceOfferVariantId
import leaderboard.search.ParsedSearchIntent
import leaderboard.search.dsl.*
import leaderboard.search.document.VariantSearchDocument
import leaderboard.search.interpreter.SearchSpecSupport
import leaderboard.search.semantic.{SemanticCandidateHit, SemanticSupplementPolicy, SemanticSupplementWindow}

// Production-usable, testable policy for selecting which Qdrant-only candidates may supplement an
// ES-owned variant carousel in `ExperimentalHybridSearchBackend`'s `ElasticsearchWithQdrantVariantSupplement`
// route. `AppendAll` is the existing route behavior (default). `ExplicitConstraintsFilterPlusTop1` is
// the measured Y0G zero-harm gate from `RuntimeEsQdrantScorecardProofSpec`: zero semantic harm in the
// canonical measurement, but not a broad/default policy. Disabled-by-default callers keep `AppendAll`;
// only an explicit opt-in module selects `ExplicitConstraintsFilterPlusTop1`.
sealed trait QdrantVariantSupplementPolicy extends Product with Serializable {
  // `qdrantHits` must already be in descending Qdrant-score order (the production Qdrant client
  // preserves this; see `QdrantSemanticCandidateBackend`/`QdrantCandidateAssembler`). Implementations
  // must remove ids already present in `esVariantIds` themselves; callers must not pre-filter.
  def select(
    intent: ParsedSearchIntent,
    esVariantIds: Set[MasterServiceOfferVariantId],
    qdrantHits: List[SemanticCandidateHit],
    documentsById: Map[MasterServiceOfferVariantId, VariantSearchDocument],
    capRoom: Int,
  ): List[SemanticCandidateHit]
}

object QdrantVariantSupplementPolicy {
  private val sameVariantHit: (SemanticCandidateHit, SemanticCandidateHit) => Boolean =
    (left, right) => left.variantId == right.variantId

  private val genericAppendAll: SemanticSupplementPolicy[SemanticCandidateHit] =
    SemanticSupplementPolicy.AppendAll(sameVariantHit)

  // Append every qdrant-only hit, subject to cap room. Equivalent to the un-gated route's existing
  // behavior; this is the default so existing callers/tests are unaffected.
  case object AppendAll extends QdrantVariantSupplementPolicy {
    override def select(
      intent: ParsedSearchIntent,
      esVariantIds: Set[MasterServiceOfferVariantId],
      qdrantHits: List[SemanticCandidateHit],
      documentsById: Map[MasterServiceOfferVariantId, VariantSearchDocument],
      capRoom: Int,
    ): List[SemanticCandidateHit] =
      selectWithGenericPolicy(
        policy = genericAppendAll,
        esVariantIds = esVariantIds,
        qdrantHits = qdrantHits,
        capRoom = capRoom,
      )
  }

  // The Y0G zero-harm gate: drop qdrant-only hits already covered by ES, filter the remainder by
  // every `intent.explicitConstraints` (vacuous/no-op when explicitConstraints is empty), then keep
  // at most the single highest-scored survivor. Mirrors `RuntimeEsQdrantScorecardProofSpec`'s
  // `Y0GFilterMode.AppendFilteredTop1` exactly; no new gate semantics invented here.
  case object ExplicitConstraintsFilterPlusTop1 extends QdrantVariantSupplementPolicy {
    override def select(
      intent: ParsedSearchIntent,
      esVariantIds: Set[MasterServiceOfferVariantId],
      qdrantHits: List[SemanticCandidateHit],
      documentsById: Map[MasterServiceOfferVariantId, VariantSearchDocument],
      capRoom: Int,
    ): List[SemanticCandidateHit] = {
      val eligibility = BeautyQSemanticSupplementEligibility(
        constraints = intent.explicitConstraints,
        documentsById = documentsById,
        runtimeSpec = BeautySearchSpecV1.runtimeSpec,
      )
      val genericExplicitTop1: SemanticSupplementPolicy[SemanticCandidateHit] =
        SemanticSupplementPolicy.PrefixPreservingTop1(eligibility.eligible, sameVariantHit)

      selectWithGenericPolicy(
        policy = genericExplicitTop1,
        esVariantIds = esVariantIds,
        qdrantHits = qdrantHits,
        capRoom = capRoom,
      )
    }
  }

  private def selectWithGenericPolicy(
    policy: SemanticSupplementPolicy[SemanticCandidateHit],
    esVariantIds: Set[MasterServiceOfferVariantId],
    qdrantHits: List[SemanticCandidateHit],
    capRoom: Int,
  ): List[SemanticCandidateHit] = {
    val lexicalPrefix = esVariantIds.toList.map(SemanticCandidateHit(_, 0.0))
    policy
      .select(
        lexicalPrefix = lexicalPrefix,
        semanticCandidates = qdrantHits,
        window = SemanticSupplementWindow(capRoom),
      )
      .drop(lexicalPrefix.size)
  }

  private final case class BeautyQSemanticSupplementEligibility(
    constraints: List[SearchConstraint],
    documentsById: Map[MasterServiceOfferVariantId, VariantSearchDocument],
    runtimeSpec: SearchRuntimeSpec[VariantSearchDocument],
  ) {
    def eligible(hit: SemanticCandidateHit): Boolean =
      constraints.isEmpty || documentsById.get(hit.variantId).exists { document =>
        constraints.forall(constraintSatisfied(document, _))
      }

    // BeautyQ keeps its business eligibility here. In particular, price filters use interval overlap
    // against priceFrom/priceTo, matching the previous Qdrant supplement policy.
    private def constraintSatisfied(document: VariantSearchDocument, constraint: SearchConstraint): Boolean =
      constraint match {
        case SearchConstraint.PriceRange(min, max) =>
          max.forall(document.priceFrom <= _) && min.forall(document.priceTo >= _)
        case other =>
          SearchSpecSupport.matchesConstraint(runtimeSpec, document, other).getOrElse(false)
      }
  }
}
