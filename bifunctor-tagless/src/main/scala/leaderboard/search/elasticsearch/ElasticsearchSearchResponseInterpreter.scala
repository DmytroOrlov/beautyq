package leaderboard.search.elasticsearch

import io.circe.{Decoder, Json}
import leaderboard.model.QueryFailure
import leaderboard.search.dsl.SearchDocumentSpec
import leaderboard.search.lexical.LexicalDocumentHit

final case class ElasticsearchDocumentHit[A](
  score: Double,
  source: A,
  matchedFields: List[String],
)

object ElasticsearchDocumentHit {
  implicit def decoder[A: Decoder]: Decoder[ElasticsearchDocumentHit[A]] = Decoder.instance {
      c =>
        for {
          score <- c.get[Option[Double]]("_score")
          source <- c.get[A]("_source")
          matchedQueries <- c.get[Option[List[String]]]("matched_queries")
          legacyMatchedQueries <- c.get[Option[List[String]]]("_matched_queries")
        } yield ElasticsearchDocumentHit(score.getOrElse(0.0d), source, matchedQueries.orElse(legacyMatchedQueries).getOrElse(Nil))
  }
}

object ElasticsearchSearchResponseInterpreter {
  def decodeDocumentHits[A: Decoder](
    response: Json,
  ): Either[QueryFailure, List[ElasticsearchDocumentHit[A]]] =
    response.hcursor.downField("hits").downField("hits").as[List[ElasticsearchDocumentHit[A]]].left.map {
      error =>
        QueryFailure.operation("decode-elasticsearch-search-response", error.getMessage)
    }

  def lexicalHits[A, Id](
    documentSpec: SearchDocumentSpec[A],
    response: Json,
    documentId: A => Id,
  )(implicit decoder: Decoder[A]): Either[QueryFailure, List[LexicalDocumentHit[Id]]] =
    decodeDocumentHits[A](response).flatMap {
      hits =>
        hits.foldRight[Either[QueryFailure, List[LexicalDocumentHit[Id]]]](Right(Nil)) {
          (hit, acc) =>
            for {
              tail <- acc
              _ <- Either.cond(
                documentSpec.id(hit.source).trim.nonEmpty,
                (),
                QueryFailure.operation("decode-elasticsearch-search-response", s"Decoded document for index '${documentSpec.indexName}' has an empty id"),
              )
            } yield LexicalDocumentHit(
              documentId = documentId(hit.source),
              score = hit.score,
              matchedFields = hit.matchedFields,
            ) :: tail
        }
    }
}
