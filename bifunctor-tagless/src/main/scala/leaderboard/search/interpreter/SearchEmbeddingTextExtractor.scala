package leaderboard.search.interpreter

import leaderboard.search.dsl.{EmbeddingSpec, SearchDocumentSpec}

object SearchEmbeddingTextExtractor {
  def extract[A](documentSpec: SearchDocumentSpec[A], embeddingSpec: EmbeddingSpec[A], document: A): String = {
    embeddingSpec.sourceTextFieldPaths.iterator
      .flatMap { path =>
        documentSpec.fieldsByPath.get(path).flatMap { field =>
          field.extract(document).map(_.render)
        }
      }
      .map(_.trim)
      .filter(_.nonEmpty)
      .mkString(" ")
  }
}
