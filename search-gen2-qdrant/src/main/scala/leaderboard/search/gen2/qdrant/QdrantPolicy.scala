package leaderboard.search.gen2.qdrant

import leaderboard.search.gen2.contract.*
import leaderboard.search.gen2.core.plan.{ContractFingerprint, PlanContractFingerprint}

/** The closed dense-vector distance vocabulary emitted to Qdrant wire JSON. */
enum QdrantDistance {
  case Cosine
  case Dot
  case Euclid

  def wireValue: String = this match {
    case QdrantDistance.Cosine => "Cosine"
    case QdrantDistance.Dot    => "Dot"
    case QdrantDistance.Euclid => "Euclid"
  }
}

final case class QdrantVectorName private (value: String)

object QdrantVectorName {
  def from(value: String): Either[QdrantValueError, QdrantVectorName] =
    if (value.trim.nonEmpty) Right(new QdrantVectorName(value))
    else Left(QdrantValueError.Blank("vector-name"))

  def unsafeFrom(value: String): QdrantVectorName =
    from(value) match {
      case Right(result) => result
      case Left(error)   => throw new IllegalStateException(s"invalid Qdrant vector name: $error")
    }
}

final case class QdrantEmbeddingModelIdentity(
  provider: String,
  model: String,
  revision: String,
  dimension: Int,
  textFormatVersion: String,
)

final case class QdrantRetrievalPolicy(
  topK: Int,
  oversamplingFactor: Int,
  scoreThreshold: Option[Double],
) {
  def limit: Either[QdrantValueError, Int] =
    if (topK <= 0) Left(QdrantValueError.NonPositive("top-k", topK.toString))
    else if (oversamplingFactor <= 0) Left(QdrantValueError.NonPositive("oversampling-factor", oversamplingFactor.toString))
    else if (topK > Int.MaxValue / oversamplingFactor) Left(QdrantValueError.Overflow("retrieval-limit"))
    else Right(topK * oversamplingFactor)
}

final case class QdrantCompilerVersion(value: String)
object QdrantCompilerVersion {
  val Current: QdrantCompilerVersion = QdrantCompilerVersion("qdrant-compiler-v1")
}

final case class QdrantCollectionFormatVersion(value: String)
object QdrantCollectionFormatVersion {
  val Current: QdrantCollectionFormatVersion = QdrantCollectionFormatVersion("qdrant-collection-format-v1")
}

final case class QdrantProtocolVersion(value: String)
object QdrantProtocolVersion {
  val Current: QdrantProtocolVersion = QdrantProtocolVersion("qdrant-rest-v1.18")
}

enum QdrantPayloadFieldSchema {
  case Keyword
  case Integer
  case Float
  case Bool
  case DateTime
  case Geo

  def wireValue: String = this match {
    case QdrantPayloadFieldSchema.Keyword  => "keyword"
    case QdrantPayloadFieldSchema.Integer  => "integer"
    case QdrantPayloadFieldSchema.Float    => "float"
    case QdrantPayloadFieldSchema.Bool     => "bool"
    case QdrantPayloadFieldSchema.DateTime => "datetime"
    case QdrantPayloadFieldSchema.Geo      => "geo"
  }
}

final case class QdrantPayloadIndex(
  field: SearchField[?, ?],
  schema: QdrantPayloadFieldSchema,
) {
  def path: FieldPath = field.path
}

sealed trait QdrantValueError
object QdrantValueError {
  final case class Blank(name: String) extends QdrantValueError
  final case class NonPositive(name: String, value: String) extends QdrantValueError
  final case class Overflow(name: String) extends QdrantValueError
  final case class NonFinite(name: String, value: Double) extends QdrantValueError
}

sealed trait QdrantPolicyError
object QdrantPolicyError {
  final case class InvalidValue(error: QdrantValueError) extends QdrantPolicyError
  final case class ForeignIdentityField(fieldId: FieldId) extends QdrantPolicyError
  final case class ForeignEmbeddingField(fieldId: FieldId) extends QdrantPolicyError
  final case class InvalidIdentityKind(fieldId: FieldId, kind: SearchFieldKind) extends QdrantPolicyError
  final case class InvalidEmbeddingField(fieldId: FieldId, kind: SearchFieldKind, searchable: Boolean) extends QdrantPolicyError
  final case class NonPayloadFilterField(fieldId: FieldId, operator: FilterOperator) extends QdrantPolicyError
  final case class UnsupportedFilterKind(fieldId: FieldId, kind: SearchFieldKind, operator: FilterOperator) extends QdrantPolicyError
}

type QdrantPolicyErrors = NonEmptyErrors[QdrantPolicyError]

/** One complete, validated Qdrant policy. Payload fields and indexes are derived from the executable
  * document declaration; they are never separately supplied by a domain. The two fingerprints are
  * derived views of this one policy: collection compatibility is independent from query-only knobs. */
final class QdrantPolicy[Document, Id] private (
  val planContractVersion: PlanContractVersion,
  val declaration: SearchDocumentDeclaration[Document, Id],
  val identity: SearchField[Document, Id],
  val embeddingField: SearchField[Document, String],
  val vectorName: QdrantVectorName,
  val embeddingModel: QdrantEmbeddingModelIdentity,
  val distance: QdrantDistance,
  val retrieval: QdrantRetrievalPolicy,
  val compilerVersion: QdrantCompilerVersion,
  val collectionFormatVersion: QdrantCollectionFormatVersion,
  val protocolVersion: QdrantProtocolVersion,
  val payloadFields: Vector[SearchField[Document, ?]],
  val payloadIndexes: Vector[QdrantPayloadIndex],
  val collectionContractFingerprint: ContractFingerprint,
  val candidateContractFingerprint: ContractFingerprint,
)

object QdrantPolicy {
  val ContributionId: PlanContractContributionId = PlanContractContributionId("qdrant")
  val CandidateContributionId: PlanContractContributionId = PlanContractContributionId("qdrant-candidate")

  def apply[Document, Id](
    planContractVersion: PlanContractVersion,
    declaration: SearchDocumentDeclaration[Document, Id],
    identity: SearchField[Document, Id],
    embeddingField: SearchField[Document, String],
    vectorName: QdrantVectorName,
    embeddingModel: QdrantEmbeddingModelIdentity,
    distance: QdrantDistance,
    retrieval: QdrantRetrievalPolicy,
  ): Either[QdrantPolicyErrors, QdrantPolicy[Document, Id]] = {
    val errors = validate(declaration, identity, embeddingField, embeddingModel, retrieval)
    NonEmptyErrors.fromVector(errors) match {
      case Some(nonEmpty) => Left(nonEmpty)
      case None =>
        val payloadFields  = declaration.allFields.filter(_.capabilities.payloadEligible)
        val payloadIndexes = payloadFields.filter(_.capabilities.filterOperators.nonEmpty).map(field => QdrantPayloadIndex(field, schemaFor(field.kind)))
        val collectionContribution = PlanContractContributionVersion(
          contributionVersion(declaration, embeddingField, vectorName, embeddingModel, distance, payloadIndexes)
        )
        val candidateContribution = PlanContractContributionVersion(
          candidateVersion(retrieval, distance, vectorName, embeddingModel, payloadIndexes)
        )
        Right(
          new QdrantPolicy(
            planContractVersion,
            declaration,
            identity,
            embeddingField,
            vectorName,
            embeddingModel,
            distance,
            retrieval,
            QdrantCompilerVersion.Current,
            QdrantCollectionFormatVersion.Current,
            QdrantProtocolVersion.Current,
            payloadFields,
            payloadIndexes,
            PlanContractFingerprint.compute(planContractVersion, declaration, Map(ContributionId -> collectionContribution)),
            PlanContractFingerprint.compute(planContractVersion, declaration, Map(CandidateContributionId -> candidateContribution)),
          )
        )
    }
  }

  def unsafeFrom[Document, Id](
    planContractVersion: PlanContractVersion,
    declaration: SearchDocumentDeclaration[Document, Id],
    identity: SearchField[Document, Id],
    embeddingField: SearchField[Document, String],
    vectorName: QdrantVectorName,
    embeddingModel: QdrantEmbeddingModelIdentity,
    distance: QdrantDistance,
    retrieval: QdrantRetrievalPolicy,
  ): QdrantPolicy[Document, Id] =
    apply(planContractVersion, declaration, identity, embeddingField, vectorName, embeddingModel, distance, retrieval) match {
      case Right(policy) => policy
      case Left(errors)  => throw new IllegalStateException(s"invalid source QdrantPolicy declaration: ${errors.toVector.mkString("; ")}")
    }

  private def validate[Document, Id](
    declaration: SearchDocumentDeclaration[Document, Id],
    identity: SearchField[Document, Id],
    embeddingField: SearchField[Document, String],
    model: QdrantEmbeddingModelIdentity,
    retrieval: QdrantRetrievalPolicy,
  ): Vector[QdrantPolicyError] = {
    val identityErrors = Vector(
      Either.cond(declaration.identity eq identity, (), QdrantPolicyError.ForeignIdentityField(identity.id)),
      Either.cond(
        Set(SearchFieldKind.Keyword, SearchFieldKind.Integer, SearchFieldKind.Long).contains(identity.kind),
        (),
        QdrantPolicyError.InvalidIdentityKind(identity.id, identity.kind),
      ),
    ).collect { case Left(error) => error }

    val embeddingErrors = Vector(
      Either.cond(declaration.allFields.exists(_ eq embeddingField), (), QdrantPolicyError.ForeignEmbeddingField(embeddingField.id)),
      Either.cond(embeddingField.kind == SearchFieldKind.Text && embeddingField.capabilities.searchable, (), QdrantPolicyError.InvalidEmbeddingField(embeddingField.id, embeddingField.kind, embeddingField.capabilities.searchable)),
    ).collect { case Left(error) => error }

    val modelErrors =
      Vector(model.provider, model.model, model.revision, model.textFormatVersion).zip(Vector("provider", "model", "revision", "text-format-version")).flatMap {
        case (value, name) if value.trim.isEmpty => Vector(QdrantPolicyError.InvalidValue(QdrantValueError.Blank(name)))
        case _                                   => Vector.empty
      } ++
        (if (model.dimension > 0) Vector.empty else Vector(QdrantPolicyError.InvalidValue(QdrantValueError.NonPositive("dimension", model.dimension.toString))))

    val retrievalErrors =
      (retrieval.limit match {
        case Left(error)  => Vector(QdrantPolicyError.InvalidValue(error))
        case Right(_)     => Vector.empty
      }) ++
        retrieval.scoreThreshold.toVector.flatMap { value =>
          if (value.isFinite) Vector.empty else Vector(QdrantPolicyError.InvalidValue(QdrantValueError.NonFinite("score-threshold", value)))
        }

    val fieldErrors = declaration.allFields.flatMap { field =>
      field.capabilities.filterOperators.toVector.flatMap { operator =>
        if (!field.capabilities.payloadEligible) Vector(QdrantPolicyError.NonPayloadFilterField(field.id, operator))
        else if (supports(operator, field.kind)) Vector.empty
        else Vector(QdrantPolicyError.UnsupportedFilterKind(field.id, field.kind, operator))
      }
    }

    identityErrors ++ embeddingErrors ++ modelErrors ++ retrievalErrors ++ fieldErrors
  }

  private def supports(operator: FilterOperator, kind: SearchFieldKind): Boolean = operator match {
    case FilterOperator.Equal | FilterOperator.In =>
      Set(SearchFieldKind.Keyword, SearchFieldKind.Integer, SearchFieldKind.Long, SearchFieldKind.Decimal, SearchFieldKind.Boolean).contains(kind)
    case FilterOperator.Range =>
      Set(SearchFieldKind.Integer, SearchFieldKind.Long, SearchFieldKind.Decimal, SearchFieldKind.DateTime).contains(kind)
    case FilterOperator.GeoDistance => kind == SearchFieldKind.GeoPoint
  }

  def schemaFor(kind: SearchFieldKind): QdrantPayloadFieldSchema = kind match {
    case SearchFieldKind.Keyword  => QdrantPayloadFieldSchema.Keyword
    case SearchFieldKind.Text     => QdrantPayloadFieldSchema.Keyword
    case SearchFieldKind.Integer  => QdrantPayloadFieldSchema.Integer
    case SearchFieldKind.Long     => QdrantPayloadFieldSchema.Integer
    case SearchFieldKind.Decimal  => QdrantPayloadFieldSchema.Float
    case SearchFieldKind.Boolean  => QdrantPayloadFieldSchema.Bool
    case SearchFieldKind.DateTime => QdrantPayloadFieldSchema.DateTime
    case SearchFieldKind.GeoPoint => QdrantPayloadFieldSchema.Geo
  }

  private def contributionVersion[Document, Id](
    declaration: SearchDocumentDeclaration[Document, Id],
    embeddingField: SearchField[Document, String],
    vectorName: QdrantVectorName,
    model: QdrantEmbeddingModelIdentity,
    distance: QdrantDistance,
    indexes: Vector[QdrantPayloadIndex],
  ): String =
    QdrantCanonical.sha256(
      Vector(
        s"embedding-field=${embeddingField.id.value}:${embeddingField.path.value}",
        s"vector=${vectorName.value}",
        s"model=${model.provider}:${model.model}:${model.revision}:${model.dimension}:${model.textFormatVersion}",
        s"distance=${distance.wireValue}",
        s"compiler=${QdrantCompilerVersion.Current.value}",
        s"format=${QdrantCollectionFormatVersion.Current.value}",
        s"payload=${indexes.map(index => s"${index.path.value}:${index.schema.wireValue}").mkString(",")}",
        s"document=${declaration.id.value}",
      )
    )

  private def candidateVersion[Document](
    retrieval: QdrantRetrievalPolicy,
    distance: QdrantDistance,
    vectorName: QdrantVectorName,
    model: QdrantEmbeddingModelIdentity,
    indexes: Vector[QdrantPayloadIndex],
  ): String =
    QdrantCanonical.sha256(
      Vector(
        s"vector=${vectorName.value}",
        s"model=${model.provider}:${model.model}:${model.revision}:${model.dimension}:${model.textFormatVersion}",
        s"distance=${distance.wireValue}",
        s"top-k=${retrieval.topK}",
        s"oversampling=${retrieval.oversamplingFactor}",
        s"threshold=${retrieval.scoreThreshold.map(QdrantCanonical.doubleToken).getOrElse("")}",
        s"payload=${indexes.map(index => s"${index.path.value}:${index.schema.wireValue}").mkString(",")}",
      )
    )
}

/** Minimal canonical hash helper kept in the Qdrant module so backend policy identity does not depend
  * on a renderer or on case-class `toString`. */
private[qdrant] object QdrantCanonical {
  def doubleToken(value: Double): String = java.lang.Double.toHexString(value)

  def sha256(parts: Vector[String]): String = {
    val digest = java.security.MessageDigest.getInstance("SHA-256")
    digest.digest(parts.mkString("\n").getBytes(java.nio.charset.StandardCharsets.UTF_8)).map(byte => f"$byte%02x").mkString
  }
}
