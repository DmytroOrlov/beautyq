package leaderboard.search.lexical

final case class LexicalDocumentHit[Id](
  documentId: Id,
  score: Double,
  matchedFields: List[String] = Nil,
)
