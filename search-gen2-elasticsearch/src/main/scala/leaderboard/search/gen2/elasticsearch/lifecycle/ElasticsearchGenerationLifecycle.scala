package leaderboard.search.gen2.elasticsearch.lifecycle

import leaderboard.search.gen2.elasticsearch.*

import io.circe.{Json, JsonObject}

import java.time.Clock

final class ElasticsearchGenerationLifecycleConfig private (
  val alias: String,
  val physicalIndexPrefix: String,
  val batching: ElasticsearchBulkBatchingPolicy,
  private[lifecycle] val supersededAlias: String,
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
    batching: ElasticsearchBulkBatchingPolicy,
  ): Either[ElasticsearchGenerationLifecycleConfigError, ElasticsearchGenerationLifecycleConfig] =
    val supersededAlias = s"$alias--superseded"
    if (!ElasticsearchGenerationNaming.validIndexName(alias)) Left(ElasticsearchGenerationLifecycleConfigError.InvalidAlias(alias))
    else if (physicalIndexPrefix.isEmpty) Left(ElasticsearchGenerationLifecycleConfigError.InvalidPhysicalIndexPrefix(physicalIndexPrefix))
    else if (!ElasticsearchGenerationNaming.validIndexName(physicalIndexPrefix + "0".repeat(64))) Left(ElasticsearchGenerationLifecycleConfigError.InvalidPhysicalIndexPrefix(physicalIndexPrefix))
    else if (!ElasticsearchGenerationNaming.validIndexName(supersededAlias)) Left(ElasticsearchGenerationLifecycleConfigError.InvalidAlias(supersededAlias))
    else Right(new ElasticsearchGenerationLifecycleConfig(alias, physicalIndexPrefix, batching, supersededAlias))
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
  final case class MetadataDocumentCountMismatch(expected: Long, metadata: Long) extends ElasticsearchGenerationLifecycleError
  final case class LiveDocumentCountMismatch(expected: Long, metadata: Long, live: Long) extends ElasticsearchGenerationLifecycleError
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
  final case class StaleGeneration(reference: ElasticsearchGenerationReference) extends ElasticsearchGenerationLifecycleError
  final case class SupersededGenerationAliasStateInvalid(target: String, message: String) extends ElasticsearchGenerationLifecycleError
  final case class SupersededGenerationMappingLookupFailed(target: String, error: ElasticsearchGen2TransportError) extends ElasticsearchGenerationLifecycleError
  final case class SupersededGenerationActiveConflict(active: String, superseded: Vector[String]) extends ElasticsearchGenerationLifecycleError
  final case class SupersededGenerationAliasGuardFailed(expectedActive: String, actualTargets: Vector[String]) extends ElasticsearchGenerationLifecycleError
  final case class SupersededGenerationActivationGuardFailed(expectedActive: String, status: Option[Int], error: Option[Json]) extends ElasticsearchGenerationLifecycleError
  final case class SupersededGenerationActivationAliasLookupFailed(alias: String, error: ElasticsearchGen2TransportError) extends ElasticsearchGenerationLifecycleError
  final case class SupersededGenerationActivationActionFailure(action: String, target: String, alias: Option[String], status: Option[Int], error: Option[Json])
  final case class SupersededGenerationActivationActionFailed(active: String, failures: Vector[SupersededGenerationActivationActionFailure]) extends ElasticsearchGenerationLifecycleError
  final case class SupersededGenerationCleanupGuardFailed(expectedActive: String, status: Int, error: Json) extends ElasticsearchGenerationLifecycleError
  final case class SupersededGenerationCleanupGuardActionFailed(expectedActive: String, failures: Vector[SupersededGenerationActivationActionFailure]) extends ElasticsearchGenerationLifecycleError
  final case class SupersededGenerationCleanupTransportFailed(active: String, error: ElasticsearchGen2TransportError) extends ElasticsearchGenerationLifecycleError
  final case class SupersededGenerationCleanupFailure(
    action: String,
    target: String,
    status: Option[Int],
    error: Option[Json],
  ) extends ElasticsearchGenerationLifecycleError
  final case class SupersededGenerationCleanupFailed(active: String, failures: Vector[SupersededGenerationCleanupFailure]) extends ElasticsearchGenerationLifecycleError
  final case class InvalidSupersededGenerationCleanupResponse(active: String, message: String) extends ElasticsearchGenerationLifecycleError
  final case class Authorization(error: ElasticsearchSearchRequestAuthorizationError) extends ElasticsearchGenerationLifecycleError
  final case class CleanupFailed(primary: ElasticsearchGenerationLifecycleError, cleanup: ElasticsearchGen2TransportError) extends ElasticsearchGenerationLifecycleError
}

final case class ElasticsearchLifecycleShardDiagnostics(total: Int, successful: Int, failed: Int)

/** A lifecycle-marked superseded target that has proved ownership through persisted Gen2 metadata and deterministic naming. */
final class OwnedElasticsearchGeneration private[lifecycle] (
  val target: ElasticsearchSearchTarget,
  val reference: ElasticsearchGenerationReference,
  val metadata: ElasticsearchGenerationMetadata,
)

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
      _ <- cleanupSupersededGenerations(resolved.reference.value)
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
      _ <- Either.cond(metadata.documentCount == expectedDocumentCount, (), MetadataDocumentCountMismatch(expectedDocumentCount, metadata.documentCount))
      expectedProperties <- propertiesOf(expectedMapping).toRight(InvalidMappingResponse("compiled mapping has no properties object"))
      _ <- Either.cond(properties == expectedProperties, (), MappingMismatch(Json.fromJsonObject(expectedProperties), Json.fromJsonObject(properties)))
      liveCount <- count(name)
      _ <- Either.cond(liveCount == metadata.documentCount, (), LiveDocumentCountMismatch(expectedDocumentCount, metadata.documentCount, liveCount))
      target <- ElasticsearchGenerationNaming.validateReference(config.physicalIndexPrefix, ElasticsearchGenerationReference(name), metadata).left.map(Naming.apply)
    } yield new LifecycleResolvedElasticsearchGeneration(ElasticsearchGenerationReference(name), target, metadata)

  private def resolvePinned(reference: ElasticsearchGenerationReference): Either[ElasticsearchGenerationLifecycleError, LifecycleResolvedElasticsearchGeneration] =
    for {
      _ <- ElasticsearchGenerationNaming.validateCandidateReference(config.physicalIndexPrefix, reference).left.map(Naming.apply)
      mapping <- inspectPinnedMapping(reference)
      decoded <- decodeMappingResponse(reference.value, mapping)
      (metadata, _) = decoded
      target <- ElasticsearchGenerationNaming.validateReference(config.physicalIndexPrefix, reference, metadata).left.map(Naming.apply)
      liveCount <- count(target.value)
      _ <- Either.cond(liveCount == metadata.documentCount, (), LiveDocumentCountMismatch(metadata.documentCount, metadata.documentCount, liveCount))
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

  private def inspectPinnedMapping(reference: ElasticsearchGenerationReference): Either[ElasticsearchGenerationLifecycleError, Json] =
    inspectMapping(reference.value) match {
      case Left(Transport(_, HttpFailure("GET", _, 404, _))) => Left(StaleGeneration(reference))
      case other => other
    }

  private final case class SupersededCleanupAction(kind: String, target: String, alias: Option[String], body: Json)
  private final case class DecodedAliasActionFailure(action: String, target: String, alias: Option[String], status: Option[Int], error: Option[Json])

  private def cleanupSupersededGenerations(active: String): Either[ElasticsearchGenerationLifecycleError, Unit] =
    supersededAliasTargets(active).flatMap { targets =>
      val sortedTargets = targets.sorted
      if (sortedTargets.contains(active)) Left(SupersededGenerationActiveConflict(active, sortedTargets))
      else validateSupersededGenerations(sortedTargets).flatMap { owned =>
        if (owned.isEmpty) Right(())
        else aliasTargets().flatMap { activeTargets =>
          if (activeTargets != Vector(active)) Left(SupersededGenerationAliasGuardFailed(active, activeTargets))
          else {
            val guardActions = Vector(
              SupersededCleanupAction("remove", active, Some(config.alias), removeAlias(active, config.alias, mustExist = true)),
              SupersededCleanupAction("add", active, Some(config.alias), addAlias(active, config.alias)),
            )
            val actions = guardActions ++ owned.sortBy(_.target.value).map { generation =>
              SupersededCleanupAction(
                "remove_index",
                generation.target.value,
                None,
                Json.obj("remove_index" -> Json.obj("index" -> Json.fromString(generation.target.value))),
              )
            }
            val body = Json.obj("actions" -> Json.fromValues(actions.map(_.body)))
            client.postJson("/_aliases", body) match {
              case Left(HttpFailure("POST", _, status, responseBody)) =>
                aliasGuardFailure(responseBody, config.alias) match {
                  case Some(error) => Left(SupersededGenerationCleanupGuardFailed(active, status, error))
                  case None => Left(SupersededGenerationCleanupTransportFailed(active, HttpFailure("POST", "/_aliases", status, responseBody)))
                }
              case Left(error) => Left(SupersededGenerationCleanupTransportFailed(active, error))
              case Right(response) => decodeSupersededCleanupResponse(active, actions, response)
            }
          }
        }
      }
    }

  private def validateSupersededGenerations(
    targets: Vector[String],
  ): Either[ElasticsearchGenerationLifecycleError, Vector[OwnedElasticsearchGeneration]] =
    targets.foldLeft[Either[ElasticsearchGenerationLifecycleError, Vector[OwnedElasticsearchGeneration]]](Right(Vector.empty)) {
      case (acc, target) =>
        acc.flatMap { owned =>
          ElasticsearchGenerationNaming
            .validateCandidateReference(config.physicalIndexPrefix, ElasticsearchGenerationReference(target))
            .left
            .map(error => SupersededGenerationAliasStateInvalid(target, s"invalid physical name: $error"))
            .flatMap { _ =>
              client.getJson(s"/$target/_mapping") match {
                case Left(HttpFailure("GET", _, 404, _)) => Right(owned)
                case Left(error) => Left(SupersededGenerationMappingLookupFailed(target, error))
                case Right(response) => decodeOwnedGeneration(target, response).map(generation => owned :+ generation)
              }
            }
        }
    }

  private def decodeOwnedGeneration(name: String, response: Json): Either[ElasticsearchGenerationLifecycleError, OwnedElasticsearchGeneration] =
    for {
      top <- response.asObject.toRight(SupersededGenerationAliasStateInvalid(name, "expected mapping response object"))
      _ <- Either.cond(top.keys.toVector == Vector(name), (), SupersededGenerationAliasStateInvalid(name, "mapping response must contain exactly the requested target"))
      entry <- top(name).toRight(SupersededGenerationAliasStateInvalid(name, "mapping response omitted the requested target"))
      index <- entry.asObject.toRight(SupersededGenerationAliasStateInvalid(name, "expected an index object"))
      mappings <- index("mappings").flatMap(_.asObject).toRight(SupersededGenerationAliasStateInvalid(name, "expected a mappings object"))
      metadataJson <- mappings("_meta").toRight(SupersededGenerationAliasStateInvalid(name, "missing mappings._meta"))
      metadata <- ElasticsearchGenerationMetadataCodec.decode(metadataJson).left.map(error => SupersededGenerationAliasStateInvalid(name, s"invalid mappings._meta: $error"))
      target <- ElasticsearchGenerationNaming
        .validateReference(config.physicalIndexPrefix, ElasticsearchGenerationReference(name), metadata)
        .left.map(error => SupersededGenerationAliasStateInvalid(name, s"metadata does not own '$name': $error"))
    } yield new OwnedElasticsearchGeneration(target, ElasticsearchGenerationReference(name), metadata)

  private def decodeSupersededCleanupResponse(
    active: String,
    actions: Vector[SupersededCleanupAction],
    response: Json,
  ): Either[ElasticsearchGenerationLifecycleError, Unit] = {
    decodeAliasesResponseEnvelope(response) match {
      case Left(message) => Left(InvalidSupersededGenerationCleanupResponse(active, message))
      case Right((false, None)) => Right(())
      case Right((true, None)) => Left(InvalidSupersededGenerationCleanupResponse(active, "errors=true requires action_results"))
      case Right((errorFlag, Some(results))) if results.length != actions.length => Left(InvalidSupersededGenerationCleanupResponse(active, "action_results length does not match requested actions"))
      case Right((errorFlag, Some(results))) =>
        decodeExpectedActionResults(actions, results) match {
          case Left(message) => Left(InvalidSupersededGenerationCleanupResponse(active, message))
          case Right((failures, _)) if !errorFlag && failures.nonEmpty => Left(InvalidSupersededGenerationCleanupResponse(active, "errors=false contradicts failed action results"))
          case Right((Vector(), false)) if errorFlag => Left(InvalidSupersededGenerationCleanupResponse(active, "errors=true but every action succeeded"))
          case Right((Vector(), _)) => Right(())
          case Right((failures, _)) if failures.exists(failure => failure.action == "remove" || failure.action == "add") =>
            Left(SupersededGenerationCleanupGuardActionFailed(active, failures.filter(failure => failure.action == "remove" || failure.action == "add").map(failure => SupersededGenerationActivationActionFailure(failure.action, failure.target, failure.alias, failure.status, failure.error))))
          case Right((failures, _)) => Left(SupersededGenerationCleanupFailed(active, failures.map(failure => SupersededGenerationCleanupFailure(failure.action, failure.target, failure.status, failure.error))))
        }
    }
  }

  private def decodeExpectedActionResults(
    actions: Vector[SupersededCleanupAction],
    results: Vector[Json],
  ): Either[String, (Vector[DecodedAliasActionFailure], Boolean)] = {
    val decoded = actions.zip(results).foldLeft[Either[String, (Vector[DecodedAliasActionFailure], Boolean)]](Right((Vector.empty, false))) {
      case (acc, (action, result)) =>
        acc.flatMap { case (failures, idempotentAbsence) =>
          result.asObject.toRight("action result must be an object").flatMap { obj =>
            val actionObject = obj("action").flatMap(_.asObject)
            val actionType = actionObject.flatMap(_("type")).flatMap(_.asString)
            val indicesMatch = actionObject.flatMap(_("indices")).flatMap(_.asArray).exists { values =>
              values.toVector match {
                case Vector(single) => single.asString.contains(action.target)
                case _ => false
              }
            }
            val aliasesMatch = action.alias match {
              case Some(expectedAlias) => actionObject.flatMap(_("aliases")).flatMap(_.asArray).exists { values =>
                values.toVector match {
                  case Vector(single) => single.asString.contains(expectedAlias)
                  case _ => false
                }
              }
              case None => actionObject.flatMap(_("aliases")) match {
                case None => true
                case Some(value) => value.asArray.exists(_.isEmpty)
              }
            }
            val actionMatches = actionType.contains(action.kind) && indicesMatch && aliasesMatch
            val status = obj("status").flatMap(_.asNumber).flatMap(_.toInt)
            val error = obj("error").filterNot(_.isNull)
            if (!actionMatches) Left(s"action result does not match requested ${action.kind} for '${action.target}'")
            else if (isAlreadyAbsentResult(action, result)) Right((failures, true))
            else if (status.exists(value => value < 200 || value >= 300) || error.nonEmpty) Right((failures :+ DecodedAliasActionFailure(action.kind, action.target, action.alias, status, error), idempotentAbsence))
            else if (status.isEmpty) Left(s"action result for '${action.target}' has no integer status")
            else Right((failures, idempotentAbsence))
          }
        }
    }
    decoded
  }

  private def decodeAliasesResponseEnvelope(response: Json): Either[String, (Boolean, Option[Vector[Json]])] =
    response.asObject.toRight("expected an object").flatMap { obj =>
      val acknowledged = obj("acknowledged").flatMap(_.asBoolean)
      val errors = obj("errors").flatMap(_.asBoolean).toRight("missing or non-boolean errors")
      val actionResults = obj("action_results") match {
        case None => Right(None)
        case Some(value) => value.asArray.toRight("action_results must be an array").map(values => Some(values.toVector))
      }
      (acknowledged, errors, actionResults) match {
        case (Some(true), Right(errorFlag), Right(results)) => Right((errorFlag, results))
        case (Some(false), _, _) => Left("acknowledged must be true")
        case (None, _, _) => Left("missing or non-boolean acknowledged")
        case (_, Left(message), _) => Left(message)
        case (_, _, Left(message)) => Left(message)
      }
    }

  private def isAlreadyAbsentResult(action: SupersededCleanupAction, result: Json): Boolean =
    action.kind == "remove_index" &&
      result.hcursor.get[Int]("status").toOption.contains(404) &&
      result.hcursor.downField("error").get[String]("type").toOption.contains("index_not_found_exception")

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
    for {
      activeTargets <- aliasTargets()
      supersededTargets <- supersededAliasTargetsForActivation()
      actions = activationActions(name, activeTargets, supersededTargets)
      _ <- if (actions.isEmpty) Right(()) else {
        val body = Json.obj("actions" -> Json.fromValues(actions.map(_.body)))
        client.postJson("/_aliases", body) match {
          case Left(HttpFailure("POST", _, status, responseBody)) =>
            aliasGuardFailure(responseBody, config.alias) match {
              case Some(error) => Left(SupersededGenerationActivationGuardFailed(name, Some(status), Some(error)))
              case None => Left(Transport("update-alias", HttpFailure("POST", "/_aliases", status, responseBody)))
            }
          case Left(error) => Left(Transport("update-alias", error))
          case Right(response) => decodeActivationResponse(name, actions, response)
        }
      }
    } yield ()

  private def activationActions(
    name: String,
    activeTargets: Vector[String],
    supersededTargets: Vector[String],
  ): Vector[SupersededCleanupAction] = {
    val removeStaleMarker =
      if (supersededTargets.contains(name)) Vector(SupersededCleanupAction("remove", name, Some(config.supersededAlias), removeAlias(name, config.supersededAlias, mustExist = true)))
      else Vector.empty
    val previousActiveActions = activeTargets.filterNot(_ == name).sorted.flatMap { target =>
      Vector(
        SupersededCleanupAction("remove", target, Some(config.alias), removeAlias(target, config.alias, mustExist = true)),
        SupersededCleanupAction("add", target, Some(config.supersededAlias), addAlias(target, config.supersededAlias)),
      )
    }
    val addActive =
      if (activeTargets.contains(name)) Vector.empty
      else Vector(SupersededCleanupAction("add", name, Some(config.alias), addAlias(name, config.alias)))
    removeStaleMarker ++ previousActiveActions ++ addActive
  }

  private def decodeActivationResponse(
    active: String,
    actions: Vector[SupersededCleanupAction],
    response: Json,
  ): Either[ElasticsearchGenerationLifecycleError, Unit] =
    decodeAliasesResponseEnvelope(response) match {
      case Left(message) => Left(InvalidAliasResponse(message))
      case Right((false, None)) => Right(())
      case Right((true, None)) => Left(InvalidAliasResponse("errors=true requires action_results"))
      case Right((errorsFlag, Some(results))) if results.length != actions.length => Left(InvalidAliasResponse("action_results length does not match requested actions"))
      case Right((errorsFlag, Some(results))) =>
        decodeExpectedActionResults(actions, results) match {
          case Left(message) => Left(InvalidAliasResponse(message))
          case Right((failures, _)) if !errorsFlag && failures.nonEmpty => Left(InvalidAliasResponse("errors=false contradicts failed action results"))
          case Right((Vector(), false)) if errorsFlag => Left(InvalidAliasResponse("errors=true but every action succeeded"))
          case Right((Vector(), _)) => Right(())
          case Right((failures, _)) => Left(SupersededGenerationActivationActionFailed(active, failures.map(failure => SupersededGenerationActivationActionFailure(failure.action, failure.target, failure.alias, failure.status, failure.error))))
        }
    }

  private def aliasTargets(): Either[ElasticsearchGenerationLifecycleError, Vector[String]] =
    aliasTargetsFor(config.alias, "get-alias", (_, message) => InvalidAliasResponse(message))

  private def supersededAliasTargets(active: String): Either[ElasticsearchGenerationLifecycleError, Vector[String]] =
    client.getJson(s"/_alias/${config.supersededAlias}") match {
      case Left(HttpFailure("GET", _, 404, _)) => Right(Vector.empty)
      case Left(error) => Left(SupersededGenerationCleanupTransportFailed(active, error))
      case Right(json) => aliasTargetsFromJson(json, config.supersededAlias, (target, message) => SupersededGenerationAliasStateInvalid(target, message))
    }

  private def supersededAliasTargetsForActivation(): Either[ElasticsearchGenerationLifecycleError, Vector[String]] =
    client.getJson(s"/_alias/${config.supersededAlias}") match {
      case Left(HttpFailure("GET", _, 404, _)) => Right(Vector.empty)
      case Left(error) => Left(SupersededGenerationActivationAliasLookupFailed(config.supersededAlias, error))
      case Right(json) => aliasTargetsFromJson(json, config.supersededAlias, (target, message) => SupersededGenerationAliasStateInvalid(target, message))
    }

  private def aliasTargetsFor(
    alias: String,
    operation: String,
    invalid: (String, String) => ElasticsearchGenerationLifecycleError,
  ): Either[ElasticsearchGenerationLifecycleError, Vector[String]] =
    client.getJson(s"/_alias/$alias") match {
      case Left(HttpFailure("GET", _, 404, _)) => Right(Vector.empty)
      case Left(error) => Left(Transport(operation, error))
      case Right(json) => aliasTargetsFromJson(json, alias, invalid)
    }

  private def aliasTargetsFromJson(
    json: Json,
    alias: String,
    invalid: (String, String) => ElasticsearchGenerationLifecycleError,
  ): Either[ElasticsearchGenerationLifecycleError, Vector[String]] =
    json.asObject.toRight(invalid("<response>", "expected object")).flatMap { obj =>
      obj.toVector.foldLeft[Either[ElasticsearchGenerationLifecycleError, Vector[String]]](Right(Vector.empty)) {
        case (acc, (target, value)) => acc.flatMap { done =>
          val containsAlias = value.asObject.exists(_.apply("aliases").flatMap(_.asObject).exists(_.contains(alias)))
          Either.cond(containsAlias, done :+ target, invalid(target, s"target '$target' does not contain alias '$alias'"))
        }
      }.map(_.sorted)
    }

  private def aliasGuardFailure(body: String, alias: String): Option[Json] =
    io.circe.parser.parse(body).toOption.flatMap { json =>
      def recognized(error: Json): Option[Json] = {
        val cursor = error.hcursor
        val structured = cursor.get[String]("type").toOption.contains("aliases_not_found_exception") &&
          cursor.get[String]("resource.type").toOption.contains("aliases") &&
          cursor.get[String]("resource.id").toOption.contains(alias)
        val exactReason = cursor.get[String]("type").toOption.contains("aliases_not_found_exception") &&
          cursor.get[String]("reason").toOption.contains(s"aliases [$alias] missing")
        if (structured || exactReason) Some(error) else None
      }
      val error = json.hcursor.downField("error")
      error.focus.flatMap(recognized).orElse {
        error.downField("root_cause").focus.flatMap(_.asArray).flatMap { causes =>
          causes.toVector.collectFirst(Function.unlift(recognized))
        }
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

  private def removeAlias(index: String, alias: String, mustExist: Boolean): Json =
    Json.obj("remove" -> Json.obj(
      "index" -> Json.fromString(index),
      "alias" -> Json.fromString(alias),
      "must_exist" -> Json.fromBoolean(mustExist),
    ))

  private def addAlias(index: String, alias: String): Json =
    Json.obj("add" -> Json.obj(
      "index" -> Json.fromString(index),
      "alias" -> Json.fromString(alias),
    ))

  private def isResourceAlreadyExists(body: String): Boolean =
    io.circe.parser.parse(body).toOption
      .flatMap(_.hcursor.downField("error").get[String]("type").toOption)
      .contains("resource_already_exists_exception")
}
