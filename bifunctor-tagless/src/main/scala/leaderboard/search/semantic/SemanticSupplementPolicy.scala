package leaderboard.search.semantic

/** Generic cap/window for supplementing a lexical prefix with semantic candidates. `capRoom` is the
  * number of semantic candidates that may be appended after the lexical prefix.
  */
final case class SemanticSupplementWindow(capRoom: Int)

/** Generic, document-type-agnostic policy for supplementing a lexical prefix with semantic candidates.
  *
  * Invariants common to every implementation:
  *   - the lexical prefix is preserved exactly and never reordered;
  *   - semantic candidates already present in the lexical prefix (per `sameDocument`) are removed;
  *   - at most `window.capRoom` semantic candidates are appended.
  *
  * This is the generic search core; it carries no BeautyQ/app/Qdrant types. App-specific eligibility and
  * adapting (ES/Qdrant id types, business constraints) live in the caller's facade.
  */
trait SemanticSupplementPolicy[A] {
  def select(
    lexicalPrefix: List[A],
    semanticCandidates: List[A],
    window: SemanticSupplementWindow,
  ): List[A]
}

object SemanticSupplementPolicy {

  /** Append every semantic candidate not already in the lexical prefix, subject to cap room. */
  final case class AppendAll[A](
    sameDocument: (A, A) => Boolean = (left: A, right: A) => left == right
  ) extends SemanticSupplementPolicy[A] {
    override def select(
      lexicalPrefix: List[A],
      semanticCandidates: List[A],
      window: SemanticSupplementWindow,
    ): List[A] =
      appendWithinCap(lexicalPrefix, semanticOnly(lexicalPrefix, semanticCandidates, sameDocument), window)
  }

  /** Append at most one eligible semantic candidate not already in the lexical prefix, subject to cap room. */
  final case class PrefixPreservingTop1[A](
    eligible: A => Boolean,
    sameDocument: (A, A) => Boolean = (left: A, right: A) => left == right,
  ) extends SemanticSupplementPolicy[A] {
    override def select(
      lexicalPrefix: List[A],
      semanticCandidates: List[A],
      window: SemanticSupplementWindow,
    ): List[A] = {
      val eligibleCandidates = semanticOnly(lexicalPrefix, semanticCandidates, sameDocument).filter(eligible)
      appendWithinCap(lexicalPrefix, eligibleCandidates.take(1), window)
    }
  }

  private def semanticOnly[A](
    lexicalPrefix: List[A],
    semanticCandidates: List[A],
    sameDocument: (A, A) => Boolean,
  ): List[A] =
    semanticCandidates.filterNot(candidate => lexicalPrefix.exists(prefix => sameDocument(prefix, candidate)))

  private def appendWithinCap[A](
    lexicalPrefix: List[A],
    semanticCandidates: List[A],
    window: SemanticSupplementWindow,
  ): List[A] = {
    val capRoom = math.max(0, window.capRoom)
    lexicalPrefix ++ semanticCandidates.take(capRoom)
  }
}
