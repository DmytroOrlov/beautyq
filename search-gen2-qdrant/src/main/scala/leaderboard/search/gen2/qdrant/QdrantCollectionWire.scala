package leaderboard.search.gen2.qdrant

import io.circe.{Json, JsonObject}

private[qdrant] object QdrantCollectionWire {
  final case class AliasEntry(alias: String, collection: String)
  final case class VectorConfig(size: Int, distance: String)
  final case class Details(
    target: String,
    vectors: Map[String, VectorConfig],
    metadata: QdrantGenerationMetadata,
    payloadSchema: Map[String, String],
    pointsCount: Option[Long],
  )

  sealed trait Error
  object Error {
    final case class Malformed(path: String, message: String) extends Error
    final case class Metadata(error: QdrantGenerationMetadataCodec.Error) extends Error
    final case class Incompatible(reason: String) extends Error
  }

  def aliases(raw: Json): Either[Error, Vector[AliasEntry]] =
    for {
      obj <- raw.asObject.toRight(Error.Malformed("response", "must be an object"))
      _ <- Either.cond(obj("status").flatMap(_.asString).contains("ok"), (), Error.Malformed("status", "must be ok"))
      result <- obj("result").flatMap(_.asObject).toRight(Error.Malformed("result", "must be an object"))
      values <- result("aliases").flatMap(_.asArray).toRight(Error.Malformed("result.aliases", "must be an array"))
      entries <- values.toVector.zipWithIndex.foldLeft[Either[Error, Vector[AliasEntry]]](Right(Vector.empty)) {
        case (acc, (value, index)) =>
          acc.flatMap { done =>
            value.asObject.toRight(Error.Malformed(s"result.aliases[$index]", "must be an object")).flatMap { item =>
              for {
                alias <- string(item, "alias_name", s"result.aliases[$index]")
                collection <- string(item, "collection_name", s"result.aliases[$index]")
              } yield done :+ AliasEntry(alias, collection)
            }
          }
      }
    } yield entries

  def details(raw: Json, target: String): Either[Error, Details] =
    for {
      obj <- raw.asObject.toRight(Error.Malformed("response", "must be an object"))
      _ <- Either.cond(obj("status").flatMap(_.asString).contains("ok"), (), Error.Malformed("status", "must be ok"))
      result <- obj("result").flatMap(_.asObject).toRight(Error.Malformed("result", "must be an object"))
      config <- result("config").flatMap(_.asObject).toRight(Error.Malformed("result.config", "must be an object"))
      params <- config("params").flatMap(_.asObject).toRight(Error.Malformed("result.config.params", "must be an object"))
      vectors <- parseVectors(params)
      metadataObject <- config("metadata").flatMap(_.asObject).toRight(Error.Malformed("result.config.metadata", "must be an object"))
      metadataJson <- metadataObject("search_gen2").toRight(Error.Malformed("result.config.metadata.search_gen2", "is required"))
      metadata <- QdrantGenerationMetadataCodec.decode(metadataJson).left.map(Error.Metadata.apply)
      payload <- parsePayloadSchema(result)
      points <- result("points_count").flatMap(_.asNumber).flatMap(_.toLong).filter(_ >= 0).toRight(Error.Malformed("result.points_count", "must be a non-negative integer"))
    } yield Details(target, vectors, metadata, payload, Some(points))

  def validateGeneration(details: Details, target: String, generation: QdrantCompiledGeneration): Either[Error, Unit] = {
    val expectedGenerationId = QdrantGenerationNaming.generationId(details.metadata.identity)
    for {
      _ <- Either.cond(details.metadata == generation.metadata, (), Error.Incompatible("persisted generation metadata differs from the compiled generation"))
      _ <- Either.cond(details.metadata.generationId == QdrantGenerationNaming.generationId(generation.identity), (), Error.Incompatible("generation id does not match the compiled identity"))
      _ <- Either.cond(expectedGenerationId == details.metadata.generationId, (), Error.Incompatible("generation id is not reproducible from persisted identity"))
      _ <- Either.cond(target == generation.physicalCollectionName, (), Error.Incompatible("physical collection name differs from compiled generation"))
      _ <- Either.cond(details.vectors == expectedVectors(generation), (), Error.Incompatible("named-vector configuration differs from compiled generation"))
    } yield ()
  }

  def validatePolicy(details: Details, policy: QdrantPolicy[?, ?]): Either[Error, Unit] = {
    val expectedVectors = Map(policy.vectorName.value -> VectorConfig(policy.embeddingModel.dimension, policy.distance.wireValue))
    val expectedPayload = policy.payloadIndexes.map(index => index.path.value -> index.schema.wireValue).toMap
    for {
      _ <- Either.cond(details.metadata.schemaVersion == QdrantGenerationMetadataCodec.CurrentSchemaVersion, (), Error.Incompatible("unsupported metadata schema"))
      _ <- Either.cond(details.metadata.identity.collectionContractFingerprint == policy.collectionContractFingerprint.value, (), Error.Incompatible("collection contract fingerprint differs from policy"))
      _ <- Either.cond(details.metadata.identity.compilerVersion == policy.compilerVersion.value, (), Error.Incompatible("compiler version differs from policy"))
      _ <- Either.cond(details.metadata.identity.collectionFormatVersion == policy.collectionFormatVersion.value, (), Error.Incompatible("collection format differs from policy"))
      _ <- Either.cond(details.metadata.identity.embeddingModel == policy.embeddingModel, (), Error.Incompatible("embedding model differs from policy"))
      _ <- Either.cond(details.vectors == expectedVectors, (), Error.Incompatible("named-vector configuration differs from policy"))
      _ <- Either.cond(details.payloadSchema == expectedPayload, (), Error.Incompatible("payload schema differs from policy"))
    } yield ()
  }

  def expectedVectors(generation: QdrantCompiledGeneration): Map[String, VectorConfig] =
    generation.collectionJson.hcursor.downField("vectors").focus.flatMap(_.asObject).toVector.flatMap(_.toVector).flatMap { case (name, json) =>
      for {
        obj <- json.asObject
        size <- obj("size").flatMap(_.asNumber).flatMap(_.toInt)
        distance <- obj("distance").flatMap(_.asString)
      } yield name -> VectorConfig(size, distance)
    }.toMap

  def expectedPayloadSchema(requests: Vector[Json]): Map[String, String] = requests.flatMap { request =>
    for {
      obj <- request.asObject.toVector
      field <- obj("field_name").flatMap(_.asString).toVector
      schema <- obj("field_schema").flatMap(_.asString).toVector
    } yield field -> schema
  }.toMap

  private def parseVectors(params: JsonObject): Either[Error, Map[String, VectorConfig]] =
    params("vectors").flatMap(_.asObject).toRight(Error.Malformed("result.config.params.vectors", "must be an object")).flatMap { values =>
      values.toVector.foldLeft[Either[Error, Map[String, VectorConfig]]](Right(Map.empty)) {
        case (acc, (name, json)) =>
          acc.flatMap { done =>
            json.asObject.toRight(Error.Malformed(s"vectors.$name", "must be an object")).flatMap { obj =>
              for {
                size <- obj("size").flatMap(_.asNumber).flatMap(_.toInt).filter(_ > 0).toRight(Error.Malformed(s"vectors.$name.size", "must be positive"))
                distance <- string(obj, "distance", s"vectors.$name")
              } yield done.updated(name, VectorConfig(size, distance))
            }
          }
      }
    }

  private def parsePayloadSchema(result: JsonObject): Either[Error, Map[String, String]] =
    result("payload_schema").flatMap(_.asObject).toRight(Error.Malformed("result.payload_schema", "must be an object")).flatMap { values =>
      values.toVector.foldLeft[Either[Error, Map[String, String]]](Right(Map.empty)) {
        case (acc, (name, json)) =>
          acc.flatMap { done =>
            json.asObject.flatMap(_("data_type").flatMap(_.asString)) match {
              case Some(schema) => Right(done.updated(name, schema))
              case None => Left(Error.Malformed(s"result.payload_schema.$name.data_type", "must be a string"))
            }
          }
      }
    }

  private def string(obj: JsonObject, name: String, path: String): Either[Error, String] =
    obj(name).flatMap(_.asString).toRight(Error.Malformed(s"$path.$name", "must be a string"))
}
