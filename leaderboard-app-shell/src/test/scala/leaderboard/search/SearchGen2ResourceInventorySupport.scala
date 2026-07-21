package leaderboard.search

import io.circe.Json

/** Neutral test-owned decoders for the managed Gen2 resource inventories.
  * These validate the wire shape instead of silently dropping malformed entries.
  */
object SearchGen2ResourceInventorySupport {
  final case class QdrantAliasEntry(alias: String, collection: String)

  def decodeQdrantAliases(raw: Json): Either[String, Vector[QdrantAliasEntry]] =
    for {
      obj <- raw.asObject.toRight("response must be an object")
      _ <- Either.cond(obj("status").flatMap(_.asString).contains("ok"), (), "status must be ok")
      result <- obj("result").flatMap(_.asObject).toRight("result must be an object")
      aliases <- result("aliases").flatMap(_.asArray).toRight("result.aliases must be an array")
      entries <- aliases.toVector.zipWithIndex.foldLeft[Either[String, Vector[QdrantAliasEntry]]](Right(Vector.empty)) {
        case (acc, (entry, index)) =>
          acc.flatMap { done =>
            for {
              entryObject <- entry.asObject.toRight(s"result.aliases[$index] must be an object")
              alias <- entryObject("alias_name").flatMap(_.asString).toRight(s"result.aliases[$index].alias_name must be a string")
              collection <- entryObject("collection_name").flatMap(_.asString).toRight(s"result.aliases[$index].collection_name must be a string")
            } yield done :+ QdrantAliasEntry(alias, collection)
          }
      }
    } yield entries

  def decodeQdrantCollectionNames(raw: Json): Either[String, Vector[String]] =
    for {
      obj <- raw.asObject.toRight("response must be an object")
      _ <- Either.cond(obj("status").flatMap(_.asString).contains("ok"), (), "status must be ok")
      result <- obj("result").flatMap(_.asObject).toRight("result must be an object")
      collections <- result("collections").flatMap(_.asArray).toRight("result.collections must be an array")
      names <- collections.toVector.zipWithIndex.foldLeft[Either[String, Vector[String]]](Right(Vector.empty)) {
        case (acc, (entry, index)) =>
          acc.flatMap { done =>
            for {
              entryObject <- entry.asObject.toRight(s"result.collections[$index] must be an object")
              name <- entryObject("name").flatMap(_.asString).toRight(s"result.collections[$index].name must be a string")
            } yield done :+ name
          }
      }
    } yield names
}
