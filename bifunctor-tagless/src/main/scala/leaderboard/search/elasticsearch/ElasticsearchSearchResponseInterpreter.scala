package leaderboard.search.elasticsearch

import io.circe.{Decoder, HCursor, Json}
import leaderboard.model.{MasterServiceOfferVariantId, QueryFailure}
import leaderboard.search.{BeautySearchResponse, ParsedSearchIntent, UserSearchInput}
import leaderboard.search.document.VariantSearchDocument
import leaderboard.search.dsl.BeautySearchSpec
import leaderboard.search.interpreter.SearchResponseAssembler
import leaderboard.search.interpreter.SearchSpecSupport
import leaderboard.search.interpreter.SearchSpecSupport.ScoredDocument
import leaderboard.search.lexical.LexicalDocumentHit

object ElasticsearchSearchResponseInterpreter {
  private final case class SearchHit(
    score: Double,
    source: VariantSearchDocument,
    matchedFields: List[String],
  )

  private object SearchHit {
    implicit val decoder: Decoder[SearchHit] = Decoder.instance {
      c =>
        for {
          score <- c.get[Option[Double]]("_score")
          source <- c.get[VariantSearchDocument]("_source")
          matchedFields <- c.get[Option[List[String]]]("_matched_queries")
        } yield SearchHit(score.getOrElse(0.0d), source, matchedFields.getOrElse(Nil))
    }
  }

  def interpret(
    spec: BeautySearchSpec,
    input: UserSearchInput,
    intent: ParsedSearchIntent,
    response: Json,
  ): Either[QueryFailure, BeautySearchResponse] = {
    for {
      hits <- decodeHits(response.hcursor)
      scoredDocuments = hits.map { hit =>
        ScoredDocument(
          document = hit.source,
          textScore = hit.score,
          boostScore = 0.0d,
          distanceKm = SearchSpecSupport.computeDistanceKm(input, hit.source),
        )
      }
      assembled <- SearchResponseAssembler.assemble(spec, input, intent, scoredDocuments)
    } yield assembled
  }

  def documentHits(response: Json): Either[QueryFailure, List[LexicalDocumentHit[MasterServiceOfferVariantId]]] =
    decodeHits(response.hcursor).map {
      hits =>
        hits.map {
          hit =>
            LexicalDocumentHit(
              documentId = hit.source.variantId,
              score = hit.score,
              matchedFields = hit.matchedFields,
            )
        }
    }

  private def decodeHits(cursor: HCursor): Either[QueryFailure, List[SearchHit]] =
    cursor.downField("hits").downField("hits").as[List[SearchHit]].left.map {
      error =>
        QueryFailure.operation("decode-elasticsearch-search-response", error.getMessage)
    }
}
