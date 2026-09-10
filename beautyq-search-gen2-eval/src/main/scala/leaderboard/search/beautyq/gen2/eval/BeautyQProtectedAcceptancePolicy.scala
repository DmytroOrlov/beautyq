package leaderboard.search.beautyq.gen2.eval

import io.circe.{Json, JsonObject}
import io.circe.parser.parse
import leaderboard.search.gen2.eval.{EvaluationCutoff, EvaluationMetricId, EvaluationSliceId, EvaluationSurfaceId, MetricKeyScope}

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

sealed trait BeautyQProtectedAcceptancePolicyError
object BeautyQProtectedAcceptancePolicyError {
  final case class InvalidDocument(message: String) extends BeautyQProtectedAcceptancePolicyError
  final case class UnexpectedFields(context: String) extends BeautyQProtectedAcceptancePolicyError
  final case class MissingField(context: String) extends BeautyQProtectedAcceptancePolicyError
  final case class InvalidValue(context: String) extends BeautyQProtectedAcceptancePolicyError
  case object EmptySliceMinimums extends BeautyQProtectedAcceptancePolicyError
  case object EmptyMetricMinimums extends BeautyQProtectedAcceptancePolicyError
  final case class DuplicateRequirement(context: String) extends BeautyQProtectedAcceptancePolicyError
}

final class BeautyQProtectedSliceMinimum private (
  val sliceId: EvaluationSliceId,
  val minimumCaseCount: Int,
) {
  override def equals(obj: Any): Boolean = obj match {
    case other: BeautyQProtectedSliceMinimum => sliceId == other.sliceId && minimumCaseCount == other.minimumCaseCount
    case _ => false
  }
  override def hashCode(): Int = (sliceId, minimumCaseCount).hashCode
}
object BeautyQProtectedSliceMinimum {
  private[eval] def create(sliceId: EvaluationSliceId, minimumCaseCount: Int): BeautyQProtectedSliceMinimum =
    new BeautyQProtectedSliceMinimum(sliceId, minimumCaseCount)
}

final class BeautyQProtectedMetricMinimum private (
  val observationKey: String,
  val scope: MetricKeyScope,
  val minimum: BigDecimal,
) {
  override def equals(obj: Any): Boolean = obj match {
    case other: BeautyQProtectedMetricMinimum =>
      observationKey == other.observationKey && scope == other.scope && minimum == other.minimum
    case _ => false
  }
  override def hashCode(): Int = (observationKey, scope, minimum).hashCode
}
object BeautyQProtectedMetricMinimum {
  private[eval] def create(observationKey: String, scope: MetricKeyScope, minimum: BigDecimal): BeautyQProtectedMetricMinimum =
    new BeautyQProtectedMetricMinimum(observationKey, scope, minimum)
}

final class BeautyQProtectedAcceptancePolicy private (
  val schemaVersion: String,
  val evaluationPolicyVersion: String,
  val protectedAcceptancePolicyVersion: String,
  val expectedCaseCount: Int,
  val requiredSliceMinimums: Vector[BeautyQProtectedSliceMinimum],
  val requiredMetricMinimums: Vector[BeautyQProtectedMetricMinimum],
) {
  def canonicalJson: Json = BeautyQProtectedAcceptancePolicy.canonicalJson(this)
}

object BeautyQProtectedAcceptancePolicy {
  val CurrentSchemaVersion = "beautyq-protected-acceptance-policy-v1"
  private val RootFields = Set(
    "schemaVersion", "evaluationPolicyVersion", "protectedAcceptancePolicyVersion",
    "expectedCaseCount", "requiredSliceMinimums", "requiredMetricMinimums",
  )
  private val SliceFields = Set("sliceId", "minimumCaseCount")
  private val MetricFields = Set("observationKey", "surface", "metric", "cutoff", "minimum")
  private val Scale = 12

  def fromJson(json: Json): Either[BeautyQProtectedAcceptancePolicyError, BeautyQProtectedAcceptancePolicy] = for {
    root <- json.asObject.toRight(BeautyQProtectedAcceptancePolicyError.InvalidDocument("root must be an object"))
    _ <- exact(root, RootFields, "root")
    schema <- string(root, "schemaVersion", "root")
    _ <- Either.cond(schema == CurrentSchemaVersion, (), BeautyQProtectedAcceptancePolicyError.InvalidValue("schemaVersion"))
    evaluationVersion <- version(root, "evaluationPolicyVersion")
    protectedVersion <- version(root, "protectedAcceptancePolicyVersion")
    caseCount <- positiveInt(root, "expectedCaseCount", "root")
    sliceValues <- array(root, "requiredSliceMinimums")
    metricValues <- array(root, "requiredMetricMinimums")
    sliceMinimums <- decodeSliceMinimums(sliceValues)
    metricMinimums <- decodeMetricMinimums(metricValues)
    _ <- Either.cond(sliceMinimums.nonEmpty, (), BeautyQProtectedAcceptancePolicyError.EmptySliceMinimums)
    _ <- Either.cond(metricMinimums.nonEmpty, (), BeautyQProtectedAcceptancePolicyError.EmptyMetricMinimums)
    _ <- unique(sliceMinimums.map(_.sliceId.value), "slice ID")
    _ <- unique(metricMinimums.map(value => s"${value.observationKey}/${scopeKey(value.scope)}"), "metric observation")
  } yield new BeautyQProtectedAcceptancePolicy(
    schema, evaluationVersion, protectedVersion, caseCount, sliceMinimums, metricMinimums,
  )

  def load(path: Path): Either[BeautyQProtectedAcceptancePolicyError, BeautyQProtectedAcceptancePolicy] =
    if (!Files.isRegularFile(path)) Left(BeautyQProtectedAcceptancePolicyError.InvalidDocument("policy file is not present"))
    else {
      try parse(Files.readString(path, StandardCharsets.UTF_8))
        .left.map(_ => BeautyQProtectedAcceptancePolicyError.InvalidDocument("protected policy is not valid JSON"))
        .flatMap(fromJson)
      catch { case _: java.io.IOException => Left(BeautyQProtectedAcceptancePolicyError.InvalidDocument("policy file cannot be read")) }
    }

  private def decodeSliceMinimums(values: Vector[Json]): Either[BeautyQProtectedAcceptancePolicyError, Vector[BeautyQProtectedSliceMinimum]] =
    values.zipWithIndex.foldLeft[Either[BeautyQProtectedAcceptancePolicyError, Vector[BeautyQProtectedSliceMinimum]]](Right(Vector.empty)) {
      case (acc, (json, index)) => acc.flatMap { current =>
        for {
          obj <- json.asObject.toRight(BeautyQProtectedAcceptancePolicyError.InvalidDocument(s"requiredSliceMinimums[$index] must be an object"))
          _ <- exact(obj, SliceFields, s"requiredSliceMinimums[$index]")
          sliceText <- string(obj, "sliceId", s"requiredSliceMinimums[$index]")
          sliceId <- EvaluationSliceId.from(sliceText).left.map(_ => BeautyQProtectedAcceptancePolicyError.InvalidValue(s"requiredSliceMinimums[$index].sliceId"))
          count <- positiveInt(obj, "minimumCaseCount", s"requiredSliceMinimums[$index]")
        } yield current :+ BeautyQProtectedSliceMinimum.create(sliceId, count)
      }
    }

  private def decodeMetricMinimums(values: Vector[Json]): Either[BeautyQProtectedAcceptancePolicyError, Vector[BeautyQProtectedMetricMinimum]] =
    values.zipWithIndex.foldLeft[Either[BeautyQProtectedAcceptancePolicyError, Vector[BeautyQProtectedMetricMinimum]]](Right(Vector.empty)) {
      case (acc, (json, index)) => acc.flatMap { current =>
        for {
          obj <- json.asObject.toRight(BeautyQProtectedAcceptancePolicyError.InvalidDocument(s"requiredMetricMinimums[$index] must be an object"))
          _ <- exact(obj, MetricFields, s"requiredMetricMinimums[$index]")
          observationKey <- string(obj, "observationKey", s"requiredMetricMinimums[$index]")
          _ <- Either.cond(observationKey.nonEmpty && observationKey.trim == observationKey, (), BeautyQProtectedAcceptancePolicyError.InvalidValue(s"requiredMetricMinimums[$index].observationKey"))
          surface <- id(obj, "surface", s"requiredMetricMinimums[$index]", EvaluationSurfaceId.from)
          metric <- id(obj, "metric", s"requiredMetricMinimums[$index]", EvaluationMetricId.from)
          cutoffValue <- int(obj, "cutoff", s"requiredMetricMinimums[$index]")
          cutoff <- EvaluationCutoff.from(cutoffValue).left.map(_ => BeautyQProtectedAcceptancePolicyError.InvalidValue(s"requiredMetricMinimums[$index].cutoff"))
          _ <- Either.cond(BeautyQEvaluationPolicy.cutoffs.values.exists(_.value == cutoff.value), (), BeautyQProtectedAcceptancePolicyError.InvalidValue(s"requiredMetricMinimums[$index].cutoff"))
          minimumText <- string(obj, "minimum", s"requiredMetricMinimums[$index]")
          minimum <- decimal(minimumText, s"requiredMetricMinimums[$index].minimum")
          _ <- Either.cond(minimum.scale <= Scale, (), BeautyQProtectedAcceptancePolicyError.InvalidValue(s"requiredMetricMinimums[$index].minimum"))
        } yield current :+ BeautyQProtectedMetricMinimum.create(
          observationKey,
          MetricKeyScope.from(surface, metric, cutoff),
          minimum.setScale(Scale),
        )
      }
    }

  private def canonicalJson(policy: BeautyQProtectedAcceptancePolicy): Json = Json.obj(
    "schemaVersion" -> Json.fromString(policy.schemaVersion),
    "evaluationPolicyVersion" -> Json.fromString(policy.evaluationPolicyVersion),
    "protectedAcceptancePolicyVersion" -> Json.fromString(policy.protectedAcceptancePolicyVersion),
    "expectedCaseCount" -> Json.fromInt(policy.expectedCaseCount),
    "requiredSliceMinimums" -> Json.fromValues(policy.requiredSliceMinimums.map(value => Json.obj(
      "sliceId" -> Json.fromString(value.sliceId.value),
      "minimumCaseCount" -> Json.fromInt(value.minimumCaseCount),
    ))),
    "requiredMetricMinimums" -> Json.fromValues(policy.requiredMetricMinimums.map(value => Json.obj(
      "observationKey" -> Json.fromString(value.observationKey),
      "surface" -> Json.fromString(value.scope.surfaceId.value),
      "metric" -> Json.fromString(value.scope.metricId.value),
      "cutoff" -> Json.fromInt(value.scope.cutoff.value),
      "minimum" -> Json.fromString(value.minimum.setScale(Scale).toString),
    ))),
  )

  private def scopeKey(scope: MetricKeyScope): String = s"${scope.surfaceId.value}/${scope.metricId.value}/${scope.cutoff.value}"
  private def unique(values: Vector[String], label: String): Either[BeautyQProtectedAcceptancePolicyError, Unit] =
    Either.cond(values.distinct.size == values.size, (), BeautyQProtectedAcceptancePolicyError.DuplicateRequirement(label))
  private def exact(obj: JsonObject, expected: Set[String], context: String): Either[BeautyQProtectedAcceptancePolicyError, Unit] =
    Either.cond(obj.keys.toSet == expected, (), BeautyQProtectedAcceptancePolicyError.UnexpectedFields(context))
  private def string(obj: JsonObject, name: String, context: String): Either[BeautyQProtectedAcceptancePolicyError, String] =
    obj(name).flatMap(_.asString).toRight(BeautyQProtectedAcceptancePolicyError.MissingField(s"$context.$name"))
  private def version(obj: JsonObject, name: String): Either[BeautyQProtectedAcceptancePolicyError, String] =
    string(obj, name, "root").flatMap(value => if (value.nonEmpty && value.trim == value) Right(value) else Left(BeautyQProtectedAcceptancePolicyError.InvalidValue(name)))
  private def int(obj: JsonObject, name: String, context: String): Either[BeautyQProtectedAcceptancePolicyError, Int] =
    obj(name).flatMap(_.asNumber).flatMap(_.toInt).toRight(BeautyQProtectedAcceptancePolicyError.InvalidValue(s"$context.$name"))
  private def positiveInt(obj: JsonObject, name: String, context: String): Either[BeautyQProtectedAcceptancePolicyError, Int] =
    int(obj, name, context).flatMap(value => if (value > 0) Right(value) else Left(BeautyQProtectedAcceptancePolicyError.InvalidValue(s"$context.$name")))
  private def array(obj: JsonObject, name: String): Either[BeautyQProtectedAcceptancePolicyError, Vector[Json]] =
    obj(name).flatMap(_.asArray).toRight(BeautyQProtectedAcceptancePolicyError.InvalidValue(name))
  private def decimal(raw: String, context: String): Either[BeautyQProtectedAcceptancePolicyError, BigDecimal] =
    if (raw.isEmpty || raw.trim != raw) Left(BeautyQProtectedAcceptancePolicyError.InvalidValue(context))
    else scala.util.Try(BigDecimal(raw)).toEither.left.map(_ => BeautyQProtectedAcceptancePolicyError.InvalidValue(context))
  private def id[A](obj: JsonObject, name: String, context: String, parse: String => Either[String, A]): Either[BeautyQProtectedAcceptancePolicyError, A] =
    string(obj, name, context).flatMap(value => parse(value).left.map(_ => BeautyQProtectedAcceptancePolicyError.InvalidValue(s"$context.$name")))
}
