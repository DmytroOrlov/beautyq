package leaderboard.search.gen2.elasticsearch

import leaderboard.search.gen2.contract.{FieldId, SearchValueDecodeError}
import leaderboard.search.gen2.core.supplement.BaselineMembershipResult

import io.circe.Json

type CompiledElasticsearchBaselineMembershipRequest[Document, Id] = ElasticsearchBaselineMembershipCompiler.CompiledElasticsearchBaselineMembershipRequestImpl[Document, Id]

sealed trait ElasticsearchBaselineMembershipCompileError

object ElasticsearchBaselineMembershipCompileError {
  final case class IdentityValueEncoding(
    candidateIndex: Int,
    fieldId: FieldId,
    error: SearchValueDecodeError,
  ) extends ElasticsearchBaselineMembershipCompileError
}

object ElasticsearchBaselineMembershipCompiler {

  private[elasticsearch] final class CompiledElasticsearchBaselineMembershipRequestImpl[Document, Id](
    val baseline: BoundElasticsearchBaselineResult[Document, Id],
    val candidateIds: Vector[Id],
    val body: Json,
  ) {
    def target: ElasticsearchSearchTarget = baseline.target
  }

  type CompiledElasticsearchBaselineMembershipRequest[Document, Id] = CompiledElasticsearchBaselineMembershipRequestImpl[Document, Id]

  def compile[Document, Id](
    baseline: BoundElasticsearchBaselineResult[Document, Id],
    candidateIds: Vector[Id],
  ): Either[ElasticsearchBaselineMembershipCompileError, CompiledElasticsearchBaselineMembershipRequest[Document, Id]] = {
    val authorized = baseline.authorizedRequest
    val encoded = candidateIds.zipWithIndex.foldLeft[
      Either[ElasticsearchBaselineMembershipCompileError, Vector[Json]]
    ](Right(Vector.empty)) { (acc, pair) =>
      acc.flatMap { done =>
        val id = pair._1
        val index = pair._2
        val canonical = authorized.prepared.identityField.codec.encodeCanonical(id)
        ElasticsearchScalarCompiler.toBackendJson(authorized.prepared.identityField.kind, canonical) match {
          case Left(err) =>
            Left(ElasticsearchBaselineMembershipCompileError.IdentityValueEncoding(index, authorized.prepared.identityField.id, err))
          case Right(json) =>
            Right(done :+ json)
        }
      }
    }

    encoded.map { termsValues =>
      val membershipQuery = Json.obj(
        "bool" -> Json.obj(
          "must" -> Json.arr(authorized.prepared.executionQuery),
          "filter" -> Json.arr(
            Json.obj(
              "terms" -> Json.obj(
                authorized.prepared.identityField.path.value -> Json.fromValues(termsValues),
              ),
            ),
          ),
        ),
      )

      val body = Json.obj(
        "size" -> Json.fromInt(candidateIds.size),
        "_source" -> Json.fromBoolean(false),
        "track_total_hits" -> Json.fromBoolean(false),
        "query" -> membershipQuery,
      )

      new CompiledElasticsearchBaselineMembershipRequestImpl(baseline, candidateIds, body)
    }
  }
}

sealed trait ElasticsearchBaselineMembershipResponseError

object ElasticsearchBaselineMembershipResponseError {
  final case class Malformed(message: String) extends ElasticsearchBaselineMembershipResponseError
  final case class ExcessiveHits(maximum: Int, actual: Int) extends ElasticsearchBaselineMembershipResponseError
  final case class InvalidHit(index: Int, message: String) extends ElasticsearchBaselineMembershipResponseError
  final case class InvalidIdentity(index: Int, error: SearchValueDecodeError) extends ElasticsearchBaselineMembershipResponseError
  final case class DuplicateIdentity(id: String, firstIndex: Int, duplicateIndex: Int) extends ElasticsearchBaselineMembershipResponseError
  final case class UnexpectedIdentity(index: Int, canonical: String) extends ElasticsearchBaselineMembershipResponseError
}

object ElasticsearchBaselineMembershipDecoder {

  def decode[Document, Id](
    request: ElasticsearchBaselineMembershipCompiler.CompiledElasticsearchBaselineMembershipRequestImpl[Document, Id],
    raw: Json,
  ): Either[ElasticsearchBaselineMembershipResponseError, BaselineMembershipResult[Id]] = {
    // top-level response must be an object
    raw.asObject.toRight(ElasticsearchBaselineMembershipResponseError.Malformed("expected a JSON object"))
      .flatMap { top =>
        // hits must be an object
        top("hits").flatMap(_.asObject).toRight(ElasticsearchBaselineMembershipResponseError.Malformed("missing or invalid 'hits'"))
      }.flatMap { hitsObj =>
        // hits.hits must be an array
        hitsObj("hits").flatMap(_.asArray).map(_.toVector)
          .toRight(ElasticsearchBaselineMembershipResponseError.Malformed("missing or invalid 'hits.hits'"))
      }.flatMap { rawHits =>
        // raw hit count must not exceed candidateIds.size
        if (rawHits.length > request.candidateIds.size)
          Left(ElasticsearchBaselineMembershipResponseError.ExcessiveHits(request.candidateIds.size, rawHits.length))
        else
          Right(rawHits)
      }.flatMap { rawHits =>
        // decode each hit, collecting (canonical String, decoded Id) pairs
        rawHits.zipWithIndex.foldLeft[
          Either[ElasticsearchBaselineMembershipResponseError, Vector[(String, Id)]]
        ](Right(Vector.empty)) { (acc, pair) =>
          val hitJson = pair._1
          val index = pair._2
          acc.flatMap { decodedSoFar =>
            hitJson.asObject.toRight(ElasticsearchBaselineMembershipResponseError.InvalidHit(index, "expected a JSON object")).flatMap { hitObj =>
              hitObj("_id").flatMap(_.asString).toRight(ElasticsearchBaselineMembershipResponseError.InvalidHit(index, "missing or invalid '_id'")).flatMap { idString =>
                request.baseline.authorizedRequest.prepared.identityField.codec.decodeCanonical(idString) match {
                  case Left(err) =>
                    Left(ElasticsearchBaselineMembershipResponseError.InvalidIdentity(index, err))
                  case Right(decodedId) =>
                    Right(decodedSoFar :+ ((idString, decodedId)))
                }
              }
            }
          }
        }
      }.flatMap { decodedPairs =>
        // reject duplicate returned identities (using canonical String form)
        val indexedPairs = decodedPairs.zipWithIndex
        val firstIndexMap = indexedPairs.foldLeft(Map.empty[String, Int]) {
          case (m, ((canonical, _), idx)) if !m.contains(canonical) => m + (canonical -> idx)
          case (m, _) => m
        }
        val dupError = indexedPairs.collectFirst {
          case ((canonical, _), idx) if firstIndexMap(canonical) < idx =>
            ElasticsearchBaselineMembershipResponseError.DuplicateIdentity(canonical, firstIndexMap(canonical), idx)
        }

        dupError match {
          case Some(err) => Left(err)
          case None =>
            // reject unexpected identities
            val candidateSet = request.candidateIds.toSet
            val unexpectedError = indexedPairs.collectFirst {
              case ((canonical, decodedId), index) if !candidateSet.contains(decodedId) =>
                ElasticsearchBaselineMembershipResponseError.UnexpectedIdentity(index, canonical)
            }
            unexpectedError match {
              case Some(err) => Left(err)
              case None =>
                // create matchingIds by filtering request.candidateIds against the decoded match set, preserving request order
                val decodedSet = decodedPairs.map(_._2).toSet
                val matchingIds = request.candidateIds.filter(decodedSet.contains)
                Right(BaselineMembershipResult.fromBackend(request.candidateIds, matchingIds))
            }
        }
      }
  }
}
