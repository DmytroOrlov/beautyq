package leaderboard.search.qdrant

import io.circe.Json
import leaderboard.model.QueryFailure

object QdrantCollectionCompatibilityValidator {
  type ValidationResult =
    Either[QueryFailure, Either[List[QdrantCollectionCompatibilityMismatch], Unit]]

  private val OperationName = "validate-qdrant-collection-compatibility"

  def validate(
    expected: QdrantCollectionCompatibilityExpectation,
    collectionInfoJson: Json,
  ): ValidationResult =
    for {
      observedCollectionName <- Right(decodeObservedCollectionName(collectionInfoJson).getOrElse(expected.collectionName))
      observedVectorName <- decodeObservedVectorName(collectionInfoJson)
      observed <- QdrantCollectionInfoDecoder.decode(
        collectionName = observedCollectionName,
        vectorName = observedVectorName,
        json = collectionInfoJson,
      )
    } yield QdrantCollectionIdentity.checkCompatibility(
      expected,
      observed.copy(embeddingModelName = decodeObservedEmbeddingModelName(collectionInfoJson)),
    )

  private def decodeObservedCollectionName(json: Json): Option[String] =
    List(
      json.hcursor.downField("result").get[String]("name").toOption,
      json.hcursor.get[String]("name").toOption,
    ).flatten.headOption

  private def decodeObservedVectorName(json: Json): Either[QueryFailure, String] = {
    val vectorNames = List(
      json.hcursor.downField("result").downField("config").downField("params").downField("vectors").focus,
      json.hcursor.downField("config").downField("params").downField("vectors").focus,
      json.hcursor.downField("params").downField("vectors").focus,
    ).flatten.collectFirst { case vectorJson if vectorJson.isObject => vectorJson.asObject.toList.flatMap(_.keys.toList) }.getOrElse(Nil)

    vectorNames match {
      case observedVectorName :: Nil => Right(observedVectorName)
      case Nil => Left(failure("Missing params.vectors in Qdrant collection info"))
      case _ => Left(failure(s"Expected exactly one named vector in Qdrant collection info, found ${vectorNames.mkString(", ")}"))
    }
  }

  private def decodeObservedEmbeddingModelName(json: Json): Option[String] =
    QdrantCollectionInfoDecoder.metadataValue(json, "embeddingModelName").flatMap(_.asString)

  private def failure(message: String): QueryFailure =
    QueryFailure.operation(OperationName, message)
}
