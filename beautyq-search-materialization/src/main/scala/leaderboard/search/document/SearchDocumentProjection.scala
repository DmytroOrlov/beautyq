package leaderboard.search.document

import leaderboard.model.QueryFailure
import leaderboard.repo.{EntityNode, RepoField, RepoValueSource}
import leaderboard.search.dsl.SearchDocumentSpec

final case class SearchDocumentProjection[S, A](
  documentSpec: SearchDocumentSpec[A],
  project: S => Either[QueryFailure, List[A]],
)

object SearchDocumentProjection {
  final case class SnapshotRows[A, K](
    entityName: String,
    keyField: RepoField[A, K],
    rows: List[A],
  )

  final case class IndexedRows[A, K](
    entityName: String,
    keyField: RepoField[A, K],
    rowsByKey: Map[K, A],
  )

  def source[A, K](node: EntityNode[A, K], rows: List[A]): SnapshotRows[A, K] =
    SnapshotRows(node.entity.modelName, node.key, rows)

  def valueSource[A, K, Row](source: RepoValueSource[A, K, Row], rows: List[A]): SnapshotRows[A, K] =
    SnapshotRows(source.valueModelName, source.keyField, rows)

  def indexByKeyPreservingFirst[A, K](rows: SnapshotRows[A, K]): IndexedRows[A, K] = {
    val indexed = rows.rows.foldLeft(Map.empty[K, A]) {
      (acc, row) =>
        val key = rows.keyField.select(row)
        if (acc.contains(key)) {
          acc
        } else {
          acc.updated(key, row)
        }
    }

    IndexedRows(rows.entityName, rows.keyField, indexed)
  }

  def requiredJoin[Root, Joined, K](
    operationName: String,
    joined: IndexedRows[Joined, K],
    key: Root => K,
    rootId: Root => Any,
  ): Root => Either[QueryFailure, Joined] =
    root =>
      requiredByKey(
        operationName = operationName,
        joined = joined,
        key = key(root),
        rootId = rootId(root),
      )

  def requiredByKey[Joined, K](
    operationName: String,
    joined: IndexedRows[Joined, K],
    key: K,
    rootId: Any,
  ): Either[QueryFailure, Joined] =
    joined.rowsByKey.get(key).toRight {
      QueryFailure.domain(s"$operationName: missing ${joined.entityName} $key while building document for variant $rootId")
    }

  def optionalLookup[Joined, K](
    joined: IndexedRows[Joined, K],
    key: K,
  ): Option[Joined] =
    joined.rowsByKey.get(key)

  def checkInvariant(
    condition: Boolean,
    failure: => QueryFailure,
  ): Either[QueryFailure, Unit] =
    if (condition) {
      Right(())
    } else {
      Left(failure)
    }

  def projectRoots[Root, A](
    roots: List[Root]
  )(projectRoot: Root => Either[QueryFailure, A]): Either[QueryFailure, List[A]] =
    roots.foldRight[Either[QueryFailure, List[A]]](Right(Nil)) {
      (root, acc) =>
        for {
          tail <- acc
          head <- projectRoot(root)
        } yield head :: tail
    }
}
