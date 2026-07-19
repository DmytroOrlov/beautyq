package leaderboard.search.gen2.elasticsearch

import leaderboard.search.gen2.contract.*
import leaderboard.search.gen2.elasticsearch.lifecycle.AuthorizedElasticsearchSearchRequest

import io.circe.Json

sealed trait ElasticsearchGroupExecutionError

object ElasticsearchGroupExecutionError {
  final case class Compile(error: ElasticsearchGroupQueryCompileError) extends ElasticsearchGroupExecutionError
  final case class Transport(groupId: GroupId, page: Int, error: ElasticsearchGen2TransportError) extends ElasticsearchGroupExecutionError
  final case class Response(errors: ElasticsearchGroupResponseErrors) extends ElasticsearchGroupExecutionError
  final case class MissingMetric(groupId: GroupId, metricId: GroupMetricId, canonicalKey: String) extends ElasticsearchGroupExecutionError
}

final class ElasticsearchFullSearchResult[Document, Id] private[elasticsearch] (
  val page: BaselineSearchPage[Document, Id],
  val groups: Vector[ElasticsearchGroupResult[Document, Id]],
) {
  def hits: Vector[ElasticsearchDocumentHit[Id]] = page.hits
  def totalHits: Long = page.totalHits
  def facets: Vector[ElasticsearchFacetResult[Document]] = page.facets
  def diagnostics: ElasticsearchResponseDiagnostics = page.diagnostics
  def nextCursor: Option[SearchCursor] = page.nextCursor

  def termsFacet[A](id: FacetId, field: SearchField[Document, A]): Option[ElasticsearchTermsFacetResult[Document, A]] =
    page.termsFacet(id, field)

  def group(groupId: GroupId): Option[ElasticsearchGroupResult[Document, Id]] =
    groups.find(_.id == groupId)
}

object ElasticsearchGroupExecutor {
  def execute[Document, Id](
    client: ElasticsearchGen2JsonClient,
    authorized: AuthorizedElasticsearchSearchRequest[Document, Id],
  ): Either[ElasticsearchGroupExecutionError, Vector[ElasticsearchGroupResult[Document, Id]]] =
    sequence(authorized.prepared.requestedGroups.map(group => executeOne(client, authorized, group))).map(_.toVector)

  private def executeOne[Document, Id](
    client: ElasticsearchGen2JsonClient,
    authorized: AuthorizedElasticsearchSearchRequest[Document, Id],
    request: GroupRequest[Document, ?],
  ): Either[ElasticsearchGroupExecutionError, ElasticsearchGroupResult[Document, Id]] =
    for {
      compiled <- ElasticsearchGroupQueryCompiler.compile(authorized.prepared, request).left.map(ElasticsearchGroupExecutionError.Compile.apply)
      pages <- readAll(client, authorized, compiled)
      allBuckets = pages.flatMap(_.buckets)
      _ <- ensureUniqueKeys(request.id, allBuckets)
      sorted <- sortBuckets(request, allBuckets)
    } yield new ElasticsearchGroupResult(request.id, request.keyField, sorted.take(request.size.value), ElasticsearchGroupPrecision.Exact, pages.map(_.diagnostics))

  private def readAll[Document, Id](
    client: ElasticsearchGen2JsonClient,
    authorized: AuthorizedElasticsearchSearchRequest[Document, Id],
    compiled: CompiledElasticsearchGroupQuery[Document, Id],
  ): Either[ElasticsearchGroupExecutionError, Vector[DecodedElasticsearchGroupPage[Document, Id]]] = {
    def loop(
      page: Int,
      afterKey: Option[Json],
      offset: Long,
      pages: Vector[DecodedElasticsearchGroupPage[Document, Id]],
    ): Either[ElasticsearchGroupExecutionError, Vector[DecodedElasticsearchGroupPage[Document, Id]]] =
      client.postJson("/" + authorized.target.value + "/_search", compiled.body(afterKey)) match {
        case Left(error) => Left(ElasticsearchGroupExecutionError.Transport(compiled.request.id, page, error))
        case Right(response) =>
          ElasticsearchGroupResponseDecoder.decode(compiled, page, response, afterKey, offset) match {
            case Left(errors) => Left(ElasticsearchGroupExecutionError.Response(errors))
            case Right(decoded) =>
              val next = pages :+ decoded
              decoded.afterKey match {
                case None => Right(next)
                case Some(value) => loop(page + 1, Some(value), offset + decoded.buckets.length, next)
              }
          }
      }

    loop(0, None, 0L, Vector.empty)
  }

  private def ensureUniqueKeys[Document, Id](
    groupId: GroupId,
    buckets: Vector[ElasticsearchGroupBucket[Document, Id]],
  ): Either[ElasticsearchGroupExecutionError, Unit] = {
    val (_, duplicate) = buckets.foldLeft((Set.empty[String], Option.empty[String])) { case ((seen, firstDuplicate), bucket) =>
      if (firstDuplicate.nonEmpty || !seen.contains(bucket.canonicalKey)) (seen + bucket.canonicalKey, firstDuplicate)
      else (seen, Some(bucket.canonicalKey))
    }
    duplicate match {
      case Some(key) => Left(ElasticsearchGroupExecutionError.Response(NonEmptyErrors.fromHead(ElasticsearchGroupResponseError.DuplicateKey(groupId, 0, key), Vector.empty)))
      case None => Right(())
    }
  }

  private sealed trait SortValue {
    def compare(other: SortValue): Int
  }
  private final case class NumericValue(value: BigDecimal) extends SortValue {
    def compare(other: SortValue): Int = other match {
      case NumericValue(that) => value.compare(that)
      case _                  => 0
    }
  }
  private final case class CountValue(value: Long) extends SortValue {
    def compare(other: SortValue): Int = other match {
      case CountValue(that) => value.compare(that)
      case _                => 0
    }
  }
  private final case class KeyValue(value: String) extends SortValue {
    def compare(other: SortValue): Int = other match {
      case KeyValue(that) => value.compare(that)
      case _              => 0
    }
  }
  private final case class SortableBucket[Document, Id](
    bucket: ElasticsearchGroupBucket[Document, Id],
    values: Vector[(SortValue, SortDirection)],
  )

  private def sortBuckets[Document, Id](
    request: GroupRequest[Document, ?],
    buckets: Vector[ElasticsearchGroupBucket[Document, Id]],
  ): Either[ElasticsearchGroupExecutionError, Vector[ElasticsearchGroupBucket[Document, Id]]] =
    sequence(buckets.map(bucket => sortable(request, bucket))).map { sortableBuckets =>
      sortableBuckets.sortWith { (left, right) => compareByOrder(left.values, right.values) < 0 }.map(_.bucket)
    }

  private def sortable[Document, Id](
    request: GroupRequest[Document, ?],
    bucket: ElasticsearchGroupBucket[Document, Id],
  ): Either[ElasticsearchGroupExecutionError, SortableBucket[Document, Id]] =
    sequence(request.order.map {
      case GroupOrder.Metric(metricId, direction) =>
        bucket.bestScore(metricId).orElse(bucket.minGeoDistanceMeters(metricId)) match {
          case Some(value) => Right((NumericValue(value): SortValue, direction))
          case None        => Left(ElasticsearchGroupExecutionError.MissingMetric(request.id, metricId, bucket.canonicalKey))
        }
      case GroupOrder.MatchingDocumentCount(direction) => Right((CountValue(bucket.matchingDocumentCount): SortValue, direction))
      case GroupOrder.Key(direction)                   => Right((KeyValue(bucket.canonicalKey): SortValue, direction))
    }).map(values => SortableBucket(bucket, values))

  private def compareByOrder(left: Vector[(SortValue, SortDirection)], right: Vector[(SortValue, SortDirection)]): Int =
    left.iterator.zip(right.iterator).map { case ((leftValue, direction), (rightValue, _)) =>
      directed(leftValue.compare(rightValue), direction)
    }.find(_ != 0).getOrElse(0)

  private def directed(value: Int, direction: SortDirection): Int =
    direction match {
      case SortDirection.Asc => value
      case SortDirection.Desc => -value
    }

  private def sequence[A](values: Vector[Either[ElasticsearchGroupExecutionError, A]]): Either[ElasticsearchGroupExecutionError, Vector[A]] =
    values.foldLeft[Either[ElasticsearchGroupExecutionError, Vector[A]]](Right(Vector.empty)) { (acc, next) =>
      acc.flatMap(done => next.map(done :+ _))
    }
}
