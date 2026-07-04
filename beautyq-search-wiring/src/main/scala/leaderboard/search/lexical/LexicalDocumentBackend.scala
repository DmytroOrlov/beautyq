package leaderboard.search.lexical

import leaderboard.model.QueryFailure
import leaderboard.search.{ParsedSearchIntent, UserSearchInput}

trait LexicalDocumentBackend[F[_, _], Id] {
  def documentHits(input: UserSearchInput, intent: ParsedSearchIntent): F[QueryFailure, List[LexicalDocumentHit[Id]]]
}
