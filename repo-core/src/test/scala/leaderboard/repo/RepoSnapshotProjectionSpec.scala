package leaderboard.repo

import leaderboard.model.QueryFailure
import org.scalatest.wordspec.AnyWordSpec

final case class OwnerId(value: String)
final case class Row(id: OwnerId, name: String)
final case class Aggregate(ownerId: OwnerId, items: List[AggregateRow])
final case class AggregateRow(ownerId: OwnerId, code: String)

final class RepoSnapshotProjectionSpec extends AnyWordSpec {

  private val rowNode = RepoEntity.derived[Row].node(_.id)

  private val aggregateValueSource =
    RepoValueSource.derived[Aggregate, OwnerId, AggregateRow](_.ownerId)

  "RepoSnapshotProjection.source" should {
    "use EntityNode model name and key field metadata" in {
      val rows = List(Row(OwnerId("a"), "Alice"))

      val snapshotRows = RepoSnapshotProjection.source(rowNode, rows)

      assert(snapshotRows.entityName == "Row")
      assert(snapshotRows.keyField.label == "id")
      assert(snapshotRows.rows == rows)
    }
  }

  "RepoSnapshotProjection.valueSource" should {
    "use RepoValueSource value model name and key field metadata" in {
      val rows = List(Aggregate(OwnerId("a"), Nil))

      val snapshotRows = RepoSnapshotProjection.valueSource(aggregateValueSource, rows)

      assert(snapshotRows.entityName == "Aggregate")
      assert(snapshotRows.keyField.label == "ownerId")
      assert(snapshotRows.rows == rows)
    }
  }

  "RepoSnapshotProjection.indexByKeyPreservingFirst" should {
    "keep the first row for duplicate keys" in {
      val first  = Row(OwnerId("a"), "First")
      val second = Row(OwnerId("a"), "Second")
      val other  = Row(OwnerId("b"), "Other")

      val indexed = RepoSnapshotProjection.indexByKeyPreservingFirst(
        RepoSnapshotProjection.source(rowNode, List(first, second, other))
      )

      assert(indexed.rowsByKey(OwnerId("a")) == first)
      assert(indexed.rowsByKey(OwnerId("b")) == other)
      assert(indexed.rowsByKey.size == 2)
    }
  }

  "RepoSnapshotProjection.requiredByKey" should {
    "succeed for a present key" in {
      val row = Row(OwnerId("a"), "Alice")
      val indexed = RepoSnapshotProjection.indexByKeyPreservingFirst(
        RepoSnapshotProjection.source(rowNode, List(row))
      )

      val result = RepoSnapshotProjection.requiredByKey(
        operationName = "operation",
        joined         = indexed,
        key            = OwnerId("a"),
        rootId         = "rootId",
      )

      assert(result == Right(row))
    }

    "fail with the exact canonical missing-entity message" in {
      val indexed = RepoSnapshotProjection.indexByKeyPreservingFirst(
        RepoSnapshotProjection.source(rowNode, Nil)
      )

      val result = RepoSnapshotProjection.requiredByKey(
        operationName = "operation",
        joined         = indexed,
        key            = OwnerId("missing"),
        rootId         = "rootId",
      )

      assert(result == Left(QueryFailure.domain("operation: missing Row OwnerId(missing) while building document for variant rootId")))
    }
  }

  "RepoSnapshotProjection.requiredJoin" should {
    "delegate through the root key selector and keep the same failure shape" in {
      val row = Row(OwnerId("a"), "Alice")
      val indexed = RepoSnapshotProjection.indexByKeyPreservingFirst(
        RepoSnapshotProjection.source(rowNode, List(row))
      )

      val join = RepoSnapshotProjection.requiredJoin[AggregateRow, Row, OwnerId](
        operationName = "operation",
        joined        = indexed,
        key           = (aggregateRow: AggregateRow) => aggregateRow.ownerId,
        rootId        = (aggregateRow: AggregateRow) => aggregateRow.code,
      )

      assert(join(AggregateRow(OwnerId("a"), "code-a")) == Right(row))
      assert(
        join(AggregateRow(OwnerId("missing"), "code-b")) ==
          Left(QueryFailure.domain("operation: missing Row OwnerId(missing) while building document for variant code-b"))
      )
    }
  }

  "RepoSnapshotProjection.optionalLookup" should {
    "return Some for a present key and None for a missing key, without failure" in {
      val row = Row(OwnerId("a"), "Alice")
      val indexed = RepoSnapshotProjection.indexByKeyPreservingFirst(
        RepoSnapshotProjection.source(rowNode, List(row))
      )

      assert(RepoSnapshotProjection.optionalLookup(indexed, OwnerId("a")) == Some(row))
      assert(RepoSnapshotProjection.optionalLookup(indexed, OwnerId("missing")) == None)
    }
  }

  "RepoSnapshotProjection.checkInvariant" should {
    "return Right(()) when the condition holds" in {
      assert(RepoSnapshotProjection.checkInvariant(true, QueryFailure.domain("unused")) == Right(()))
    }

    "return the provided failure when the condition fails" in {
      val failure = QueryFailure.domain("invariant violated")

      assert(RepoSnapshotProjection.checkInvariant(false, failure) == Left(failure))
    }
  }

  "RepoSnapshotProjection.projectRoots" should {
    "preserve root order on success" in {
      val roots = List(1, 2, 3)

      val result = RepoSnapshotProjection.projectRoots(roots) {
        root => Right(root * 10): Either[QueryFailure, Int]
      }

      assert(result == Right(List(10, 20, 30)))
    }

    "fail when a single root fails" in {
      val roots = List(1, 2, 3)

      val result = RepoSnapshotProjection.projectRoots(roots) {
        case 2    => Left(QueryFailure.domain("failed on 2"))
        case root => Right(root * 10)
      }

      assert(result == Left(QueryFailure.domain("failed on 2")))
    }

    "preserve current failure semantics: the rightmost failing root wins, and roots to its left are never evaluated" in {
      val roots = List(1, 2, 3)

      val result = RepoSnapshotProjection.projectRoots(roots) {
        case 1 => Left(QueryFailure.domain("failed on 1, should not surface"))
        case 3 => Left(QueryFailure.domain("failed on 3, rightmost"))
        case n => Right(n * 10)
      }

      assert(result == Left(QueryFailure.domain("failed on 3, rightmost")))
    }
  }
}
