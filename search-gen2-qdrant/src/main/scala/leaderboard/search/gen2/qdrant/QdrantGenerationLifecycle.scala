package leaderboard.search.gen2.qdrant

import io.circe.Json

final class QdrantGenerationLifecycleConfig private (
  val alias: QdrantResourceName,
  val physicalCollectionPrefix: String,
)

object QdrantGenerationLifecycleConfig {
  sealed trait Error
  object Error {
    final case class InvalidAlias(error: QdrantResourceNameError) extends Error
    final case class InvalidPrefix(value: String) extends Error
  }

  def create(alias: QdrantResourceName, physicalCollectionPrefix: String): Either[Error, QdrantGenerationLifecycleConfig] =
    if (physicalCollectionPrefix.trim.isEmpty || physicalCollectionPrefix.contains("/") || physicalCollectionPrefix.contains("*") || !physicalCollectionPrefix.startsWith(alias.value))
      Left(Error.InvalidPrefix(physicalCollectionPrefix))
    else Right(new QdrantGenerationLifecycleConfig(alias, physicalCollectionPrefix))
}

final class ActiveQdrantGeneration private[qdrant] (
  val alias: QdrantResourceName,
  val physicalCollection: QdrantResourceName,
  val metadata: QdrantGenerationMetadata,
)

sealed trait QdrantGenerationLifecycleError
object QdrantGenerationLifecycleError {
  final case class InvalidPhysicalName(value: String) extends QdrantGenerationLifecycleError
  final case class Transport(operation: String, error: leaderboard.search.gen2.transport.Gen2HttpTransportError) extends QdrantGenerationLifecycleError
  final case class Malformed(operation: String, message: String) extends QdrantGenerationLifecycleError
  final case class CollectionIncompatible(target: String, reason: String) extends QdrantGenerationLifecycleError
  final case class AliasAmbiguous(alias: String, targets: Vector[String]) extends QdrantGenerationLifecycleError
  final case class MutationRejected(operation: String, raw: Json) extends QdrantGenerationLifecycleError
  final case class PointCountMismatch(expected: Int, actual: Long) extends QdrantGenerationLifecycleError
}

final class QdrantGenerationLifecycle(
  client: QdrantGen2Client,
  config: QdrantGenerationLifecycleConfig,
) {
  import QdrantGenerationLifecycleError.*

  def activate(generation: QdrantCompiledGeneration): Either[QdrantGenerationLifecycleError, ActiveQdrantGeneration] =
    for {
      target <- targetName(generation)
      details <- convergeCollection(target, generation)
      aliases <- readAliases()
      targets <- aliasTargets(aliases, config.alias.value)
      _ <- switchAlias(target.value, targets)
    } yield new ActiveQdrantGeneration(config.alias, target, details.metadata)

  private def targetName(generation: QdrantCompiledGeneration): Either[QdrantGenerationLifecycleError, QdrantResourceName] =
    if (!generation.physicalCollectionName.startsWith(config.physicalCollectionPrefix)) Left(InvalidPhysicalName(generation.physicalCollectionName))
    else QdrantResourceName.from(generation.physicalCollectionName).left.map(_ => InvalidPhysicalName(generation.physicalCollectionName))

  private def convergeCollection(target: QdrantResourceName, generation: QdrantCompiledGeneration): Either[QdrantGenerationLifecycleError, QdrantCollectionWire.Details] =
    client.getCollection(target) match {
      case Left(error) if isNotFound(error) =>
        client.createCollection(target, generation.collectionJson).left.map(Transport("create-collection", _)).flatMap { raw =>
          decodeMutation("create-collection", raw, requireBoolean = true).flatMap(_ => readAndValidate(target, generation))
        }
      case Left(error) => Left(Transport("get-collection", error))
      case Right(raw)  => readDetailsAndValidate(target, generation, raw)
    }

  private def readAndValidate(target: QdrantResourceName, generation: QdrantCompiledGeneration): Either[QdrantGenerationLifecycleError, QdrantCollectionWire.Details] =
    client.getCollection(target).left.map(Transport("get-collection", _)).flatMap(raw => readDetailsAndValidate(target, generation, raw))

  private def readDetailsAndValidate(target: QdrantResourceName, generation: QdrantCompiledGeneration, raw: Json): Either[QdrantGenerationLifecycleError, QdrantCollectionWire.Details] =
    for {
      initial <- parseDetails(raw, target.value)
      _ <- QdrantCollectionWire.validateGeneration(initial, target.value, generation).left.map(error => collectionError(target.value, error))
      _ <- ensurePayloadIndexes(target, generation, initial.payloadSchema)
      _ <- upsertPoints(target, generation)
      actual <- countPoints(target)
      _ <- Either.cond(actual == generation.metadata.pointCount.toLong, (), PointCountMismatch(generation.metadata.pointCount, actual))
    } yield initial

  private def ensurePayloadIndexes(target: QdrantResourceName, generation: QdrantCompiledGeneration, existing: Map[String, String]): Either[QdrantGenerationLifecycleError, Unit] = {
    val expected = QdrantCollectionWire.expectedPayloadSchema(generation.payloadIndexRequests)
    val unexpected = existing.keySet.diff(expected.keySet).toVector.sorted
    val mismatch = existing.collectFirst { case (field, schema) if expected.get(field).exists(_ != schema) => s"payload index $field has schema $schema instead of ${expected(field)}" }
    if (unexpected.nonEmpty) Left(CollectionIncompatible(target.value, s"unexpected payload indexes: ${unexpected.mkString(",")}"))
    else mismatch match {
      case Some(reason) => Left(CollectionIncompatible(target.value, reason))
      case None =>
        expected.toVector.filterNot { case (field, _) => existing.contains(field) }.foldLeft[Either[QdrantGenerationLifecycleError, Unit]](Right(())) {
          case (acc, (field, schema)) => acc.flatMap { _ =>
            val body = Json.obj("field_name" -> Json.fromString(field), "field_schema" -> Json.fromString(schema))
            client.createPayloadIndex(target, body).left.map(Transport("create-payload-index", _)).flatMap(raw => decodeMutation("create-payload-index", raw, requireBoolean = false).map(_ => ()))
          }
        }
    }
  }

  private def upsertPoints(target: QdrantResourceName, generation: QdrantCompiledGeneration): Either[QdrantGenerationLifecycleError, Unit] = {
    val points = generation.points.map { point =>
      Json.obj(
        "id" -> QdrantPointId.json(point.id),
        "vector" -> Json.obj(point.vectorName.value -> Json.fromValues(point.vector.map(value => Json.fromBigDecimal(BigDecimal(value))))),
        "payload" -> point.payload,
      )
    }
    client.upsertPoints(target, Json.obj("points" -> Json.fromValues(points))).left.map(Transport("upsert-points", _)).flatMap(raw => decodeMutation("upsert-points", raw, requireBoolean = false).map(_ => ()))
  }

  private def countPoints(target: QdrantResourceName): Either[QdrantGenerationLifecycleError, Long] =
    client.countPoints(target).left.map(Transport("count-points", _)).flatMap { raw =>
      for {
        obj <- raw.asObject.toRight(Malformed("count-points", "response must be an object"))
        _ <- Either.cond(obj("status").flatMap(_.asString).contains("ok"), (), Malformed("count-points", "status must be ok"))
        result <- obj("result").flatMap(_.asObject).toRight(Malformed("count-points", "result must be an object"))
        count <- result("count").flatMap(_.asNumber).flatMap(_.toLong).filter(_ >= 0).toRight(Malformed("count-points", "result.count must be non-negative"))
      } yield count
    }

  private def readAliases(): Either[QdrantGenerationLifecycleError, Vector[QdrantCollectionWire.AliasEntry]] =
    client.listAliases().left.map(Transport("list-aliases", _)).flatMap(raw => QdrantCollectionWire.aliases(raw).left.map(error => Malformed("list-aliases", error.toString)))

  private def aliasTargets(entries: Vector[QdrantCollectionWire.AliasEntry], alias: String): Either[QdrantGenerationLifecycleError, Vector[String]] = {
    val targets = entries.filter(_.alias == alias).map(_.collection).distinct.sorted
    if (targets.size > 1) Left(AliasAmbiguous(alias, targets)) else Right(targets)
  }

  private def switchAlias(target: String, current: Vector[String]): Either[QdrantGenerationLifecycleError, Unit] = {
    val actions = current match {
      case Vector() => Vector(Json.obj("create_alias" -> Json.obj("collection_name" -> Json.fromString(target), "alias_name" -> Json.fromString(config.alias.value))))
      case Vector(existing) if existing == target => Vector.empty
      case Vector(_) => Vector(
        Json.obj("delete_alias" -> Json.obj("alias_name" -> Json.fromString(config.alias.value))),
        Json.obj("create_alias" -> Json.obj("collection_name" -> Json.fromString(target), "alias_name" -> Json.fromString(config.alias.value))),
      )
      case _ => Vector.empty
    }
    if (actions.isEmpty) Right(())
    else client.updateAliases(Json.obj("actions" -> Json.fromValues(actions))).left.map(Transport("update-aliases", _)).flatMap(raw => decodeMutation("update-aliases", raw, requireBoolean = true).map(_ => ()))
  }

  private def parseDetails(raw: Json, target: String): Either[QdrantGenerationLifecycleError, QdrantCollectionWire.Details] =
    for {
      obj <- raw.asObject.toRight(Malformed("get-collection", "response must be an object"))
      _ <- Either.cond(obj("status").flatMap(_.asString).contains("ok"), (), Malformed("get-collection", "status must be ok"))
      details <- QdrantCollectionWire.details(raw, target).left.map(error => collectionError(target, error))
    } yield details

  private def decodeMutation(operation: String, raw: Json, requireBoolean: Boolean): Either[QdrantGenerationLifecycleError, Unit] =
    for {
      obj <- raw.asObject.toRight(Malformed(operation, "response must be an object"))
      _ <- Either.cond(obj("status").flatMap(_.asString).contains("ok"), (), Malformed(operation, "status must be ok"))
      result <- obj("result").toRight(Malformed(operation, "result is required"))
      _ <- if (requireBoolean) Either.cond(result.asBoolean.contains(true), (), MutationRejected(operation, raw))
           else Either.cond(result.asObject.flatMap(_("status").flatMap(_.asString)).contains("completed"), (), MutationRejected(operation, raw))
    } yield ()

  private def collectionError(target: String, error: QdrantCollectionWire.Error): QdrantGenerationLifecycleError = error match {
    case QdrantCollectionWire.Error.Malformed(path, message) => Malformed("get-collection", s"$path: $message")
    case QdrantCollectionWire.Error.Metadata(value)           => Malformed("get-collection", value.toString)
    case QdrantCollectionWire.Error.Incompatible(reason)      => CollectionIncompatible(target, reason)
  }

  private def isNotFound(error: leaderboard.search.gen2.transport.Gen2HttpTransportError): Boolean = error match {
    case leaderboard.search.gen2.transport.Gen2HttpTransportError.HttpFailure(_, _, 404, _) => true
    case _ => false
  }
}
