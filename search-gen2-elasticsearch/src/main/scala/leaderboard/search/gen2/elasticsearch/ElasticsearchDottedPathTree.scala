package leaderboard.search.gen2.elasticsearch

import leaderboard.search.gen2.contract.{FieldId, FieldPath}

import io.circe.{Json, JsonObject}

/** One declaration path that cannot coexist with another Elasticsearch object/leaf path already built at
  * that position - for example a plain field declared at `"intAttributes"` alongside a dynamic family
  * whose members live under the same `"intAttributes.<code>"` prefix. `fieldId`/`path` identify the field
  * whose insertion first detected the conflict. */
final case class ElasticsearchPathConflict(fieldId: FieldId, path: FieldPath)

/** Shared dotted-path nested-JSON-object assembly reused by both the mapping compiler (each nesting level
  * wrapped in an ES `"properties"` object) and the document/source compiler (each nesting level is the
  * plain nested value itself) - the two compilers differ only in `wrapChild`/`unwrapChild`, never in
  * traversal or conflict-detection order. Entries are inserted in the given order; the first field whose
  * path cannot be placed - because an earlier field already claimed that exact key as a leaf, or already
  * claimed a leaf where this field needs an object - reports the deterministic conflict.
  */
private[elasticsearch] object ElasticsearchDottedPathTree {

  def build(
    entries: Vector[(FieldId, FieldPath, Json)],
    wrapChild: JsonObject => Json,
    unwrapChild: Json => Option[JsonObject],
  ): Either[ElasticsearchPathConflict, JsonObject] =
    entries.foldLeft[Either[ElasticsearchPathConflict, JsonObject]](Right(JsonObject.empty)) { case (acc, (fieldId, path, leaf)) =>
      acc.flatMap(obj => insert(obj, path.value.split('.').toVector, leaf, fieldId, path, wrapChild, unwrapChild))
    }

  private def insert(
    obj: JsonObject,
    segments: Vector[String],
    leaf: Json,
    fieldId: FieldId,
    path: FieldPath,
    wrapChild: JsonObject => Json,
    unwrapChild: Json => Option[JsonObject],
  ): Either[ElasticsearchPathConflict, JsonObject] =
    segments match {
      case Vector(onlySegment) =>
        if (obj.contains(onlySegment)) Left(ElasticsearchPathConflict(fieldId, path))
        else Right(obj.add(onlySegment, leaf))

      case headSegment +: tailSegments =>
        val existingChild: Option[JsonObject] =
          obj(headSegment) match {
            case None           => Some(JsonObject.empty)
            case Some(existing) => unwrapChild(existing)
          }

        existingChild match {
          case None => Left(ElasticsearchPathConflict(fieldId, path))
          case Some(childObj) =>
            insert(childObj, tailSegments, leaf, fieldId, path, wrapChild, unwrapChild)
              .map(updatedChild => obj.add(headSegment, wrapChild(updatedChild)))
        }

      case _ =>
        // FieldPath is validated non-blank by StableName at declaration time, so `split('.')` always
        // yields at least one segment; this branch is unreachable.
        Left(ElasticsearchPathConflict(fieldId, path))
    }
}
