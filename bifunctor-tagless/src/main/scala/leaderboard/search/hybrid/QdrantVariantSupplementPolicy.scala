package leaderboard.search.hybrid

import leaderboard.model.MasterServiceOfferVariantId
import leaderboard.search.ParsedSearchIntent
import leaderboard.search.dsl.SearchConstraint
import leaderboard.search.document.VariantSearchDocument
import leaderboard.search.semantic.SemanticCandidateHit

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
      if (capRoom <= 0) Nil
      else qdrantHits.filterNot(hit => esVariantIds.contains(hit.variantId)).take(capRoom)
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
    ): List[SemanticCandidateHit] =
      if (capRoom <= 0) Nil
      else {
        val qdrantOnly = qdrantHits.filterNot(hit => esVariantIds.contains(hit.variantId))
        val constraints = intent.explicitConstraints
        val filtered =
          if (constraints.isEmpty) qdrantOnly
          else
            qdrantOnly.filter { hit =>
              documentsById.get(hit.variantId).exists { document =>
                constraints.forall(constraint => constraintSatisfied(document, constraint))
              }
            }
        // `qdrantHits` is already in descending-score order, so the first survivor IS the
        // highest-scored one; no re-sort needed (top-1 cap is additionally bounded by capRoom).
        filtered.take(math.min(1, capRoom))
      }
  }

  // Total-match constraint satisfaction against a `VariantSearchDocument`. Mirrors
  // `RuntimeEsQdrantScorecardProofSpec`'s `y0gConstraintSatisfied` exactly: `NearUser` must not
  // approve or reject candidate quality by itself, so it is treated as vacuously satisfied.
  private def constraintSatisfied(document: VariantSearchDocument, constraint: SearchConstraint): Boolean =
    constraint match {
      case SearchConstraint.ServiceAny(names) =>
        names.contains(document.serviceName)
      case SearchConstraint.CategoryAny(names) =>
        names.contains(document.categoryName)
      case SearchConstraint.EnumAttr(code, values) =>
        document.enumAttributes.get(code).exists(values.contains)
      case SearchConstraint.BoolAttr(code, value) =>
        document.booleanAttributes.get(code).contains(value)
      case SearchConstraint.IntRange(code, min, max) =>
        document.intAttributes.get(code).exists(v => min.forall(v >= _) && max.forall(v <= _))
      case SearchConstraint.DecimalRange(code, min, max) =>
        document.bigDecimalAttributes.get(code).exists(v => min.forall(v >= _) && max.forall(v <= _))
      case SearchConstraint.PriceRange(min, max) =>
        max.forall(document.priceFrom <= _) && min.forall(document.priceTo >= _)
      case SearchConstraint.DurationRange(min, max) =>
        max.forall(document.durationMin <= _) && min.forall(document.durationMin >= _)
      case SearchConstraint.NearUser =>
        true
    }
}
