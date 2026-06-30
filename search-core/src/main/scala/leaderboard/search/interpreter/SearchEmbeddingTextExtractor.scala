package leaderboard.search.interpreter

import leaderboard.search.dsl.{EmbeddingSpec, SearchDocumentSpec}

object SearchEmbeddingTextExtractor {
  def extract[A](documentSpec: SearchDocumentSpec[A], embeddingSpec: EmbeddingSpec[A], document: A): String = {
    embeddingSpec.sourceTextFields.iterator
      .flatMap { field =>
        documentSpec.fieldsByPath.get(field.path).flatMap(_ => field.extract(document).map(_.render))
      }
      .map(_.trim)
      .filter(_.nonEmpty)
      .mkString(" ")
  }
}
