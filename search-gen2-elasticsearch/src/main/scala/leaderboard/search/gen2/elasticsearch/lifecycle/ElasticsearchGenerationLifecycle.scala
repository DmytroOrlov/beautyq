package leaderboard.search.gen2.elasticsearch.lifecycle

import leaderboard.search.gen2.elasticsearch.*

import io.circe.{Json, JsonObject}

import java.time.Clock

enum ElasticsearchGenerationRetentionPolicy {
  case KeepAll
}

final class ElasticsearchGenerationLifecycleConfig private (
  val alias: String,
  val physicalIndexPrefix: String,
  val retention: ElasticsearchGenerationRetentionPolicy,
  val batching: ElasticsearchBulkBatchingPolicy,
)

sealed trait ElasticsearchGenerationLifecycleConfigError
object ElasticsearchGenerationLifecycleConfigError {
  final case class InvalidAlias(value: String) extends ElasticsearchGenerationLifecycleConfigError
  final case class InvalidPhysicalIndexPrefix(value: String) extends ElasticsearchGenerationLifecycleConfigError
}

object ElasticsearchGenerationLifecycleConfig {
  def create(
    alias: String,
    physicalIndexPrefix: String,
    retention: ElasticsearchGenerationRetentionPolicy,
    batching: ElasticsearchBulkBatchingPolicy,
  ): Either[ElasticsearchGenerationLifecycleConfigError, ElasticsearchGenerationLifecycleConfig] =
    if (!ElasticsearchGenerationNaming.validIndexName(alias)) Left(ElasticsearchGenerationLifecycleConfigError.InvalidAlias(alias))
    else if (physicalIndexPrefix.isEmpty) Left(ElasticsearchGenerationLifecycleConfigError.InvalidPhysicalIndexPrefix(physicalIndexPrefix))
    else if (!ElasticsearchGenerationNaming.validIndexName(physicalIndexPrefix + "0".repeat(64))) Left(ElasticsearchGenerationLifecycleConfigError.InvalidPhysicalIndexPrefix(physicalIndexPrefix))
    else Right(new ElasticsearchGenerationLifecycleConfig(alias, physicalIndexPrefix, retention, batching))
}

sealed trait ElasticsearchGenerationLifecycleError
object ElasticsearchGenerationLifecycleError {
  final case class Transport(operation: String, error: ElasticsearchGen2TransportError) extends ElasticsearchGenerationLifecycleError
  final case class Naming(error: ElasticsearchGenerationNamingError) extends ElasticsearchGenerationLifecycleError
  final case class Metadata(error: ElasticsearchGenerationMetadataError) extends ElasticsearchGenerationLifecycleError
  final case class BulkEncoding(error: ElasticsearchBulkEncodeError) extends ElasticsearchGenerationLifecycleError
  final case class InvalidCreateResponse(message: String) extends ElasticsearchGenerationLifecycleError
  final case class InvalidRefreshResponse(message: String) extends ElasticsearchGenerationLifecycleError
  final case class InvalidMappingResponse(message: String) extends ElasticsearchGenerationLifecycleError
  final case class PersistedIdentityMismatch(expected: ElasticsearchPersistedGenerationIdentity, actual: ElasticsearchPersistedGenerationIdentity) extends ElasticsearchGenerationLifecycleError
  final case class MappingMismatch(expected: Json, actual: Json) extends ElasticsearchGenerationLifecycleError
  final case class DocumentCountMismatch(expected: Long, metadata: Long, live: Long) extends ElasticsearchGenerationLifecycleError
  final case class InvalidBulkResponse(batchStart: Int, message: String) extends ElasticsearchGenerationLifecycleError
  final case class InvalidBulkItem(
    globalDocumentIndex: Int,
    localDocumentIndex: Int,
    expectedId: String,
    actualId: Option[String],
    expectedTarget: String,
    actualTarget: Option[String],
    status: Option[Int],
    error: Option[Json],
  ) extends ElasticsearchGenerationLifecycleError
  final case class InvalidCountResponse(target: String, message: String) extends ElasticsearchGenerationLifecycleError
  final case class PartialCountResponse(target: String, count: Long, shards: ElasticsearchLifecycleShardDiagnostics) extends ElasticsearchGenerationLifecycleError
  final case class InvalidAliasResponse(message: String) extends ElasticsearchGenerationLifecycleError
  final case class MissingActiveGeneration(alias: String) extends ElasticsearchGenerationLifecycleError
  final case class AmbiguousActiveGeneration(alias: String, targets: Vector[String]) extends ElasticsearchGenerationLifecycleError
  final case class Authorization(error: ElasticsearchSearchRequestAuthorizationError) extends ElasticsearchGenerationLifecycleError
  final case class CleanupFailed(primary: ElasticsearchGenerationLifecycleError, cleanup: ElasticsearchGen2TransportError) extends ElasticsearchGenerationLifecycleError
}

final case class ElasticsearchLifecycleShardDiagnostics(total: Int, successful: Int, failed: Int)

/** Owns physical generation creation, validation, alias activation and request authorization. */
final class ElasticsearchGenerationLifecycle(
  private[elasticsearch] val client: ElasticsearchGen2JsonClient,
  val config: ElasticsearchGenerationLifecycleConfig,
  clock: Clock,
) {
  import ElasticsearchGen2TransportError.HttpFailure
  import ElasticsearchGenerationLifecycleError.*

  def activate[Document, Id](
    generation: CompiledElasticsearchGeneration[Document, Id],
  ): Either[ElasticsearchGenerationLifecycleError, LifecycleResolvedElasticsearchGeneration] = {
    val persisted = ElasticsearchPersistedGenerationIdentity.fromTrusted(generation.identity)
    for {
      target <- ElasticsearchGenerationNaming.physicalIndexName(config.physicalIndexPrefix, persisted).left.map(Naming.apply)
      name = target.value
      id = ElasticsearchGenerationNaming.generationId(persisted)
      metadata = ElasticsearchGenerationMetadata(
        ElasticsearchGenerationMetadataSchemaVersion.Current,
        id,
        clock.instant(),
        generation.documents.length.toLong,
        persisted,
      )
      resolved <- inspectMapping(name) match {
        case Right(mappingResponse) => validateAndActivateExisting(name, generation.mapping.json, persisted, generation.documents.length.toLong, mappingResponse)
        case Left(Transport(_, HttpFailure("GET", _, 404, _))) => createAndActivate(name, generation, metadata)
        case Left(error) => Left(error)
      }
    } yield resolved
  }

  def authorize[Document, Id](
    prepared: PreparedElasticsearchSearchRequest[Document, Id],
  ): Either[ElasticsearchGenerationLifecycleError, AuthorizedElasticsearchSearchRequest[Document, Id]] =
    for {
      resolved <- prepared.generationRequirement match {
        case ElasticsearchGenerationRequirement.Active => resolveActive()
        case ElasticsearchGenerationRequirement.Pinned(reference) => resolvePinned(reference)
      }
      authorized <- ElasticsearchSearchRequestAuthorization.authorize(prepared, resolved).left.map(Authorization.apply)
    } yield authorized

  private def createAndActivate[Document, Id](
    name: String,
    generation: CompiledElasticsearchGeneration[Document, Id],
    metadata: ElasticsearchGenerationMetadata,
  ): Either[ElasticsearchGenerationLifecycleError, LifecycleResolvedElasticsearchGeneration] = {
    val createBody = Json.obj("mappings" -> withMetadata(generation.mapping.json, metadata))
    client.putJson(s"/$name", createBody) match {
      case Left(HttpFailure("PUT", _, 400, body)) if isResourceAlreadyExists(body) =>
        inspectMapping(name).flatMap(response => validateAndActivateExisting(name, generation.mapping.json, metadata.identity, generation.documents.length.toLong, response))
      case Left(error) => Left(Transport("create-index", error))
      case Right(response) =>
        validateCreateResponse(name, response) match {
          case Left(primary) => cleanup(name, primary)
          case Right(()) =>
            val provision = for {
              batches <- ElasticsearchBulkEncoder.encode(generation.documents, config.batching).left.map(BulkEncoding.apply)
              _ <- runBatches(name, batches)
              _ <- if (batches.isEmpty) Right(()) else refresh(name)
              mappingResponse <- inspectMapping(name)
              resolved <- validateExisting(name, generation.mapping.json, metadata.identity, generation.documents.length.toLong, mappingResponse)
            } yield resolved
            provision match {
              case Right(resolved) => activateAlias(name).map(_ => resolved)
              case Left(primary) => cleanup(name, primary)
            }
        }
    }
  }

  private def validateAndActivateExisting(
    name: String,
    expectedMapping: Json,
    expectedIdentity: ElasticsearchPersistedGenerationIdentity,
    expectedDocumentCount: Long,
    mappingResponse: Json,
  ): Either[ElasticsearchGenerationLifecycleError, LifecycleResolvedElasticsearchGeneration] =
    for {
      resolved <- validateExisting(name, expectedMapping, expectedIdentity, expectedDocumentCount, mappingResponse)
      _ <- activateAlias(name)
    } yield resolved

  private def validateExisting(
    name: String,
    expectedMapping: Json,
    expectedIdentity: ElasticsearchPersistedGenerationIdentity,
    expectedDocumentCount: Long,
    mappingResponse: Json,
  ): Either[ElasticsearchGenerationLifecycleError, LifecycleResolvedElasticsearchGeneration] =
    for {
      decoded <- decodeMappingResponse(name, mappingResponse)
      (metadata, properties) = decoded
      _ <- Either.cond(metadata.identity == expectedIdentity, (), PersistedIdentityMismatch(expectedIdentity, metadata.identity))
      _ <- Either.cond(metadata.documentCount == expectedDocumentCount, (), DocumentCountMismatch(expectedDocumentCount, metadata.documentCount, metadata.documentCount))
      expectedProperties <- propertiesOf(expectedMapping).toRight(InvalidMappingResponse("compiled mapping has no properties object"))
      _ <- Either.cond(properties == expectedProperties, (), MappingMismatch(Json.fromJsonObject(expectedProperties), Json.fromJsonObject(properties)))
      liveCount <- count(name)
      _ <- Either.cond(liveCount == metadata.documentCount, (), DocumentCountMismatch(metadata.documentCount, metadata.documentCount, liveCount))
      target <- ElasticsearchGenerationNaming.validateReference(config.physicalIndexPrefix, ElasticsearchGenerationReference(name), metadata).left.map(Naming.apply)
    } yield new LifecycleResolvedElasticsearchGeneration(ElasticsearchGenerationReference(name), target, metadata)

  private def resolvePinned(reference: ElasticsearchGenerationReference): Either[ElasticsearchGenerationLifecycleError, LifecycleResolvedElasticsearchGeneration] =
    for {
      _ <- ElasticsearchGenerationNaming.validateCandidateReference(config.physicalIndexPrefix, reference).left.map(Naming.apply)
      mapping <- inspectMapping(reference.value)
      decoded <- decodeMappingResponse(reference.value, mapping)
      (metadata, _) = decoded
      target <- ElasticsearchGenerationNaming.validateReference(config.physicalIndexPrefix, reference, metadata).left.map(Naming.apply)
      liveCount <- count(target.value)
      _ <- Either.cond(liveCount == metadata.documentCount, (), DocumentCountMismatch(metadata.documentCount, metadata.documentCount, liveCount))
    } yield new LifecycleResolvedElasticsearchGeneration(reference, target, metadata)

  private def resolveActive(): Either[ElasticsearchGenerationLifecycleError, LifecycleResolvedElasticsearchGeneration] =
    aliasTargets().flatMap {
      case Vector() => Left(MissingActiveGeneration(config.alias))
      case Vector(name) => resolvePinned(ElasticsearchGenerationReference(name))
      case many => Left(AmbiguousActiveGeneration(config.alias, many))
    }

  private def runBatches(name: String, batches: Vector[ElasticsearchBulkBatch]): Either[ElasticsearchGenerationLifecycleError, Unit] =
    batches.foldLeft[Either[ElasticsearchGenerationLifecycleError, Unit]](Right(())) { (acc, batch) =>
      acc.flatMap { _ =>
        client.postNdjson(s"/$name/_bulk", batch.body).left.map(Transport("bulk", _)).flatMap(response => validateBulkResponse(name, batch, response))
      }
    }

  private def validateBulkResponse(name: String, batch: ElasticsearchBulkBatch, response: Json): Either[ElasticsearchGenerationLifecycleError, Unit] =
    response.asObject match {
      case None => Left(InvalidBulkResponse(batch.startDocumentIndex, "expected object"))
      case Some(obj) =>
        val topErrors = obj("errors").flatMap(_.asBoolean)
        val items = obj("items").flatMap(_.asArray).map(_.toVector)
        (topErrors, items) match {
          case (Some(flag), Some(values)) if values.length == batch.documents.length =>
            val decoded = values.zip(batch.documents).zipWithIndex.map { case ((item, document), localIndex) =>
              val itemObject = item.asObject
              val indexed = itemObject.filter(_.keys.toVector == Vector("index")).flatMap(_("index")).flatMap(_.asObject)
              val actualTarget = indexed.flatMap(_("_index")).flatMap(_.asString)
              val actualId = indexed.flatMap(_("_id")).flatMap(_.asString)
              val status = indexed.flatMap(_("status")).flatMap(_.asNumber).flatMap(_.toInt)
              val error = indexed.flatMap(_("error"))
              val failed = indexed.isEmpty || !actualTarget.contains(name) || !actualId.contains(document.id) || !status.exists(value => value >= 200 && value < 300) || error.nonEmpty
              (failed, InvalidBulkItem(batch.startDocumentIndex + localIndex, localIndex, document.id, actualId, name, actualTarget, status, error))
            }
            val firstFailure = decoded.collectFirst { case (true, failure) => failure }
            (flag, firstFailure) match {
              case (true, Some(failure)) => Left(failure)
              case (false, None) => Right(())
              case (false, Some(_)) => Left(InvalidBulkResponse(batch.startDocumentIndex, "errors=false contradicts a failed item"))
              case (true, None) => Left(InvalidBulkResponse(batch.startDocumentIndex, "errors=true but every item succeeded"))
            }
          case _ => Left(InvalidBulkResponse(batch.startDocumentIndex, "top-level errors or item-count mismatch"))
        }
    }

  private def refresh(name: String): Either[ElasticsearchGenerationLifecycleError, Unit] =
    client.post(s"/$name/_refresh").left.map(Transport("refresh", _)).flatMap { json =>
      val shards = json.hcursor.downField("_shards")
      val total = shards.get[Int]("total").toOption
      val successful = shards.get[Int]("successful").toOption
      val failed = shards.get[Int]("failed").toOption
      val valid = (total, successful, failed) match {
        case (Some(t), Some(s), Some(f)) => t >= 0 && s >= 0 && f == 0 && s <= t
        case _ => false
      }
      Either.cond(valid, (), InvalidRefreshResponse("missing, negative, failed or inconsistent shard counts"))
    }

  private def inspectMapping(name: String): Either[ElasticsearchGenerationLifecycleError, Json] =
    client.getJson(s"/$name/_mapping").left.map(Transport("get-mapping", _))

  private def count(name: String): Either[ElasticsearchGenerationLifecycleError, Long] =
    client.postJson(s"/$name/_count", Json.obj("query" -> Json.obj("match_all" -> Json.obj()))).left.map(Transport("count", _)).flatMap { json =>
      val cursor = json.hcursor
      val count = cursor.get[Long]("count").toOption
      val shardsCursor = cursor.downField("_shards")
      val shards = for {
        total <- shardsCursor.get[Int]("total").toOption
        successful <- shardsCursor.get[Int]("successful").toOption
        failed <- shardsCursor.get[Int]("failed").toOption
      } yield ElasticsearchLifecycleShardDiagnostics(total, successful, failed)
      (count, shards) match {
        case (Some(value), Some(diag)) if value >= 0L && validShards(diag) && diag.failed == 0 => Right(value)
        case (Some(value), Some(diag)) if value >= 0L && validShards(diag) => Left(PartialCountResponse(name, value, diag))
        case _ => Left(InvalidCountResponse(name, "missing, negative or inconsistent count/shard diagnostics"))
      }
    }

  private def activateAlias(name: String): Either[ElasticsearchGenerationLifecycleError, Unit] =
    aliasTargets().flatMap { targets =>
      if (targets == Vector(name)) Right(())
      else {
        val removals = targets.filterNot(_ == name).sorted.map(target => Json.obj("remove" -> Json.obj("index" -> Json.fromString(target), "alias" -> Json.fromString(config.alias))))
        val addition = if (targets.contains(name)) Vector.empty else Vector(Json.obj("add" -> Json.obj("index" -> Json.fromString(name), "alias" -> Json.fromString(config.alias))))
        client.postJson("/_aliases", Json.obj("actions" -> Json.fromValues(removals ++ addition))).left.map(Transport("update-alias", _)).flatMap { response =>
          Either.cond(response.hcursor.get[Boolean]("acknowledged").toOption.contains(true), (), InvalidAliasResponse("alias update was not acknowledged"))
        }
      }
    }

  private def aliasTargets(): Either[ElasticsearchGenerationLifecycleError, Vector[String]] =
    client.getJson(s"/_alias/${config.alias}") match {
      case Left(HttpFailure("GET", _, 404, _)) => Right(Vector.empty)
      case Left(error) => Left(Transport("get-alias", error))
      case Right(json) =>
        json.asObject.toRight(InvalidAliasResponse("expected object")).flatMap { obj =>
          obj.toVector.foldLeft[Either[ElasticsearchGenerationLifecycleError, Vector[String]]](Right(Vector.empty)) {
            case (acc, (target, value)) => acc.flatMap { done =>
              val containsAlias = value.hcursor.downField("aliases").focus.flatMap(_.asObject).exists(_.contains(config.alias))
              Either.cond(containsAlias, done :+ target, InvalidAliasResponse(s"target '$target' does not contain alias '${config.alias}'"))
            }
          }.map(_.sorted)
        }
    }

  private def validateCreateResponse(name: String, response: Json): Either[ElasticsearchGenerationLifecycleError, Unit] = {
    val cursor = response.hcursor
    val valid = cursor.get[Boolean]("acknowledged").toOption.contains(true) &&
      cursor.get[Boolean]("shards_acknowledged").toOption.contains(true) &&
      cursor.get[String]("index").toOption.contains(name)
    Either.cond(valid, (), InvalidCreateResponse("expected acknowledged=true, shards_acknowledged=true and exact index"))
  }

  private def decodeMappingResponse(name: String, response: Json): Either[ElasticsearchGenerationLifecycleError, (ElasticsearchGenerationMetadata, JsonObject)] =
    for {
      top <- response.asObject.toRight(InvalidMappingResponse("expected object"))
      _ <- Either.cond(top.keys.toVector == Vector(name), (), InvalidMappingResponse("expected exactly the requested index key"))
      index <- top(name).flatMap(_.asObject).toRight(InvalidMappingResponse("expected index object"))
      mappings <- index("mappings").flatMap(_.asObject).toRight(InvalidMappingResponse("expected mappings object"))
      metaJson <- mappings("_meta").toRight(InvalidMappingResponse("missing mappings._meta"))
      metadata <- ElasticsearchGenerationMetadataCodec.decode(metaJson).left.map(Metadata.apply)
      properties <- mappings("properties").flatMap(_.asObject).toRight(InvalidMappingResponse("missing mappings.properties"))
    } yield (metadata, properties)

  private def withMetadata(mapping: Json, metadata: ElasticsearchGenerationMetadata): Json =
    mapping.asObject match {
      case Some(obj) => Json.fromJsonObject(obj.add("_meta", ElasticsearchGenerationMetadataCodec.encode(metadata)))
      case None      => mapping
    }

  private def propertiesOf(mapping: Json): Option[JsonObject] = mapping.asObject.flatMap(_("properties")).flatMap(_.asObject)

  private def cleanup[A](name: String, primary: ElasticsearchGenerationLifecycleError): Either[ElasticsearchGenerationLifecycleError, A] =
    client.delete(s"/$name") match {
      case Right(()) => Left(primary)
      case Left(error) => Left(CleanupFailed(primary, error))
    }

  private def validShards(value: ElasticsearchLifecycleShardDiagnostics): Boolean =
    value.total >= 0 && value.successful >= 0 && value.failed >= 0 &&
      value.successful <= value.total && value.failed <= value.total &&
      value.successful + value.failed <= value.total

  private def isResourceAlreadyExists(body: String): Boolean =
    io.circe.parser.parse(body).toOption
      .flatMap(_.hcursor.downField("error").get[String]("type").toOption)
      .contains("resource_already_exists_exception")
}
