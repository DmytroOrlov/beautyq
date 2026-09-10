package leaderboard.search.gen2.eval

import io.circe.{Json, JsonObject}

final class AcceptedEvaluationBaseline private (
  val schemaVersion: String,
  val corpusFingerprint: String,
  val metricSchemaVersion: String,
  val evaluationPolicyVersion: String,
  val provenanceComponents: Vector[ProvenanceComponent],
  val reportDigest: String,
  val aggregateObservations: Vector[(String, AggregateSection)],
) {
  override def equals(obj: Any): Boolean = obj match {
    case other: AcceptedEvaluationBaseline =>
      schemaVersion == other.schemaVersion && corpusFingerprint == other.corpusFingerprint &&
        metricSchemaVersion == other.metricSchemaVersion && evaluationPolicyVersion == other.evaluationPolicyVersion &&
        provenanceComponents == other.provenanceComponents &&
        reportDigest == other.reportDigest && aggregateObservations == other.aggregateObservations
    case _ => false
  }
  override def hashCode(): Int = (schemaVersion, corpusFingerprint, metricSchemaVersion, evaluationPolicyVersion, provenanceComponents, reportDigest, aggregateObservations).hashCode
}

object AcceptedEvaluationBaseline {
  val CurrentSchemaVersion = "search-gen2-accepted-baseline-v1"

  def create(
    corpusFingerprint: String,
    metricSchemaVersion: String,
    evaluationPolicyVersion: String,
    provenanceComponents: Vector[ProvenanceComponent],
    reportDigest: String,
    aggregateObservations: Vector[(String, AggregateSection)],
  ): Either[String, AcceptedEvaluationBaseline] = {
    val ids = provenanceComponents.map(_.id)
    val keys = aggregateObservations.map(_._1)
    if (!isDigest(corpusFingerprint)) Left("corpusFingerprint must be 64 lowercase hexadecimal characters")
    else if (!isDigest(reportDigest)) Left("reportDigest must be 64 lowercase hexadecimal characters")
    else if (metricSchemaVersion.isEmpty || metricSchemaVersion.trim != metricSchemaVersion) Left("metricSchemaVersion must be non-empty and whitespace-free")
    else if (evaluationPolicyVersion.isEmpty || evaluationPolicyVersion.trim != evaluationPolicyVersion) Left("evaluationPolicyVersion must be non-empty and whitespace-free")
    else if (ids.distinct.size != ids.size) Left("provenance IDs must be unique")
    else if (keys.exists(_.isEmpty) || keys.distinct.size != keys.size) Left("aggregate observation keys must be unique and non-empty")
    else Right(new AcceptedEvaluationBaseline(CurrentSchemaVersion, corpusFingerprint, metricSchemaVersion, evaluationPolicyVersion, provenanceComponents, reportDigest, aggregateObservations))
  }

  private def isDigest(value: String): Boolean = "^[0-9a-f]{64}$".r.matches(value)
}

sealed trait AcceptedBaselineDecodeError
object AcceptedBaselineDecodeError {
  final case class ExpectedObject(context: String) extends AcceptedBaselineDecodeError
  final case class ExpectedArray(context: String) extends AcceptedBaselineDecodeError
  final case class UnexpectedFields(context: String, actual: Set[String]) extends AcceptedBaselineDecodeError
  final case class MissingOrInvalidField(name: String) extends AcceptedBaselineDecodeError
  final case class UnsupportedSchemaVersion(actual: String) extends AcceptedBaselineDecodeError
  final case class InvalidFingerprintDigest(name: String, actual: String) extends AcceptedBaselineDecodeError
  final case class EmptyVersionString(name: String) extends AcceptedBaselineDecodeError
  final case class WhitespaceVersionString(name: String) extends AcceptedBaselineDecodeError
  final case class DuplicateProvenanceId(id: String) extends AcceptedBaselineDecodeError
  final case class DuplicateObservationKey(key: String) extends AcceptedBaselineDecodeError
  final case class MalformedMetricValue(key: String, metric: String) extends AcceptedBaselineDecodeError
  final case class NegativeCount(name: String, value: Int) extends AcceptedBaselineDecodeError
  final case class InvalidScale(name: String, value: String) extends AcceptedBaselineDecodeError
  final case class ParseError(message: String) extends AcceptedBaselineDecodeError
}

object AcceptedBaselineCodec {
  private val RootFields = Set("schemaVersion", "corpusFingerprint", "metricSchemaVersion", "evaluationPolicyVersion", "provenance", "reportDigest", "aggregateObservations")
  private val ProvenanceFields = Set("id", "value")
  private val ObservationFields = Set("key", "section")
  private val SectionFields = Set("structuralInvalidCount", "duplicateIdentityCount", "zeroResultCount", "forbiddenHitCount", "applicableMetricCount", "notApplicableMetricCount", "metricObservations")
  private val MetricFields = Set("surface", "metric", "cutoff", "average", "applicableCount", "notApplicableCount")
  private val Scale = 12

  def encode(baseline: AcceptedEvaluationBaseline): Json = Json.obj(
    "schemaVersion" -> Json.fromString(baseline.schemaVersion),
    "corpusFingerprint" -> Json.fromString(baseline.corpusFingerprint),
    "metricSchemaVersion" -> Json.fromString(baseline.metricSchemaVersion),
    "evaluationPolicyVersion" -> Json.fromString(baseline.evaluationPolicyVersion),
    "provenance" -> Json.fromValues(baseline.provenanceComponents.map(pc => Json.obj("id" -> Json.fromString(pc.id.value), "value" -> Json.fromString(pc.value)))),
    "reportDigest" -> Json.fromString(baseline.reportDigest),
    "aggregateObservations" -> Json.fromValues(baseline.aggregateObservations.map { case (key, section) => Json.obj("key" -> Json.fromString(key), "section" -> encodeSection(section)) }),
  )

  def decode(json: Json): Either[AcceptedBaselineDecodeError, AcceptedEvaluationBaseline] = for {
    root <- json.asObject.toRight(AcceptedBaselineDecodeError.ExpectedObject("root"))
    _ <- exact(root, "root", RootFields)
    schema <- string(root, "schemaVersion")
    _ <- Either.cond(schema == AcceptedEvaluationBaseline.CurrentSchemaVersion, (), AcceptedBaselineDecodeError.UnsupportedSchemaVersion(schema))
    fingerprint <- digest(root, "corpusFingerprint")
    metricSchema <- version(root, "metricSchemaVersion")
    policy <- version(root, "evaluationPolicyVersion")
    provenance <- array(root, "provenance")
    provenanceComponents <- decodeProvenance(provenance)
    reportDigest <- digest(root, "reportDigest")
    observationJson <- array(root, "aggregateObservations")
    observations <- decodeObservations(observationJson)
    result <- AcceptedEvaluationBaseline.create(fingerprint, metricSchema, policy, provenanceComponents, reportDigest, observations)
      .left.map(AcceptedBaselineDecodeError.ParseError.apply)
  } yield result

  private def decodeProvenance(values: Vector[Json]): Either[AcceptedBaselineDecodeError, Vector[ProvenanceComponent]] = {
    val decoded = values.zipWithIndex.foldLeft[Either[AcceptedBaselineDecodeError, Vector[ProvenanceComponent]]](Right(Vector.empty)) {
      case (acc, (json, index)) => acc.flatMap { current =>
        for {
          obj <- json.asObject.toRight(AcceptedBaselineDecodeError.ExpectedObject(s"provenance[$index]"))
          _ <- exact(obj, s"provenance[$index]", ProvenanceFields)
          idText <- string(obj, "id")
          id <- EvaluationProvenanceId.from(idText).left.map(AcceptedBaselineDecodeError.ParseError.apply)
          value <- string(obj, "value")
          component <- ProvenanceComponent.from(id, value).left.map(AcceptedBaselineDecodeError.ParseError.apply)
        } yield current :+ component
      }
    }
    decoded.flatMap { components =>
      components.map(_.id.value).find(id => components.count(_.id.value == id) > 1)
        .map(id => Left(AcceptedBaselineDecodeError.DuplicateProvenanceId(id)))
        .getOrElse(Right(components))
    }
  }

  private def decodeObservations(values: Vector[Json]): Either[AcceptedBaselineDecodeError, Vector[(String, AggregateSection)]] =
    values.zipWithIndex.foldLeft[Either[AcceptedBaselineDecodeError, Vector[(String, AggregateSection)]]](Right(Vector.empty)) {
      case (acc, (json, index)) => acc.flatMap { current =>
        for {
          obj <- json.asObject.toRight(AcceptedBaselineDecodeError.ExpectedObject(s"aggregateObservations[$index]"))
          _ <- exact(obj, s"aggregateObservations[$index]", ObservationFields)
          key <- string(obj, "key")
          _ <- Either.cond(key.nonEmpty, (), AcceptedBaselineDecodeError.MissingOrInvalidField("key"))
          _ <- Either.cond(!current.exists(_._1 == key), (), AcceptedBaselineDecodeError.DuplicateObservationKey(key))
          sectionJson <- obj("section").toRight(AcceptedBaselineDecodeError.MissingOrInvalidField("section"))
          section <- decodeSection(sectionJson)
        } yield current :+ (key -> section)
      }
    }

  private def decodeSection(json: Json): Either[AcceptedBaselineDecodeError, AggregateSection] = for {
    obj <- json.asObject.toRight(AcceptedBaselineDecodeError.ExpectedObject("aggregate section"))
    _ <- exact(obj, "aggregate section", SectionFields)
    structural <- nonNegativeInt(obj, "structuralInvalidCount")
    duplicate <- nonNegativeInt(obj, "duplicateIdentityCount")
    zero <- nonNegativeInt(obj, "zeroResultCount")
    forbidden <- nonNegativeInt(obj, "forbiddenHitCount")
    applicable <- nonNegativeInt(obj, "applicableMetricCount")
    notApplicable <- nonNegativeInt(obj, "notApplicableMetricCount")
    metricValues <- array(obj, "metricObservations")
    observations <- decodeMetricObservations(metricValues)
  } yield AggregateSection.from(structural, duplicate, zero, forbidden, applicable, notApplicable, observations)

  private def decodeMetricObservations(values: Vector[Json]): Either[AcceptedBaselineDecodeError, Vector[AggregateMetricObservation]] =
    values.zipWithIndex.foldLeft[Either[AcceptedBaselineDecodeError, Vector[AggregateMetricObservation]]](Right(Vector.empty)) {
      case (acc, (json, index)) => acc.flatMap { current =>
        for {
          obj <- json.asObject.toRight(AcceptedBaselineDecodeError.ExpectedObject(s"metricObservations[$index]"))
          _ <- exact(obj, s"metricObservations[$index]", MetricFields)
          surfaceText <- string(obj, "surface")
          surface <- EvaluationSurfaceId.from(surfaceText).left.map(AcceptedBaselineDecodeError.ParseError.apply)
          metricText <- string(obj, "metric")
          metric <- EvaluationMetricId.from(metricText).left.map(AcceptedBaselineDecodeError.ParseError.apply)
          cutoffValue <- int(obj, "cutoff")
          cutoff <- EvaluationCutoff.from(cutoffValue).left.map(AcceptedBaselineDecodeError.ParseError.apply)
          average <- decimal(obj, "average")
          normalizedAverage <- normalizeScale(average, "average")
          applicable <- nonNegativeInt(obj, "applicableCount")
          notApplicable <- nonNegativeInt(obj, "notApplicableCount")
          observation = AggregateMetricObservation.from(MetricKeyScope.from(surface, metric, cutoff), normalizedAverage, applicable, notApplicable)
          _ <- Either.cond(!current.exists(_.scope == observation.scope), (), AcceptedBaselineDecodeError.DuplicateObservationKey(s"$surfaceText/$metricText/$cutoffValue"))
        } yield current :+ observation
      }
    }

  private def encodeSection(section: AggregateSection): Json = Json.obj(
    "structuralInvalidCount" -> Json.fromInt(section.structuralInvalidCount),
    "duplicateIdentityCount" -> Json.fromInt(section.duplicateIdentityCount),
    "zeroResultCount" -> Json.fromInt(section.zeroResultCount),
    "forbiddenHitCount" -> Json.fromInt(section.forbiddenHitCount),
    "applicableMetricCount" -> Json.fromInt(section.applicableMetricCount),
    "notApplicableMetricCount" -> Json.fromInt(section.notApplicableMetricCount),
    "metricObservations" -> Json.fromValues(section.metricObservations.map { observation => Json.obj(
      "surface" -> Json.fromString(observation.scope.surfaceId.value),
      "metric" -> Json.fromString(observation.scope.metricId.value),
      "cutoff" -> Json.fromInt(observation.scope.cutoff.value),
      "average" -> Json.fromBigDecimal(observation.average),
      "applicableCount" -> Json.fromInt(observation.applicableCount),
      "notApplicableCount" -> Json.fromInt(observation.notApplicableCount),
    ) }),
  )

  private def exact(obj: JsonObject, context: String, expected: Set[String]): Either[AcceptedBaselineDecodeError, Unit] =
    Either.cond(obj.keys.toSet == expected, (), AcceptedBaselineDecodeError.UnexpectedFields(context, obj.keys.toSet))
  private def string(obj: JsonObject, name: String): Either[AcceptedBaselineDecodeError, String] = obj(name).flatMap(_.asString).toRight(AcceptedBaselineDecodeError.MissingOrInvalidField(name))
  private def array(obj: JsonObject, name: String): Either[AcceptedBaselineDecodeError, Vector[Json]] = obj(name).flatMap(_.asArray).toRight(AcceptedBaselineDecodeError.MissingOrInvalidField(name))
  private def int(obj: JsonObject, name: String): Either[AcceptedBaselineDecodeError, Int] = obj(name).flatMap(_.asNumber).flatMap(_.toInt).toRight(AcceptedBaselineDecodeError.MissingOrInvalidField(name))
  private def nonNegativeInt(obj: JsonObject, name: String): Either[AcceptedBaselineDecodeError, Int] = int(obj, name).flatMap(value => if (value < 0) Left(AcceptedBaselineDecodeError.NegativeCount(name, value)) else Right(value))
  private def decimal(obj: JsonObject, name: String): Either[AcceptedBaselineDecodeError, BigDecimal] = obj(name).flatMap(_.asNumber).flatMap(_.toBigDecimal).toRight(AcceptedBaselineDecodeError.MissingOrInvalidField(name))
  private def digest(obj: JsonObject, name: String): Either[AcceptedBaselineDecodeError, String] = string(obj, name).flatMap(value => if ("^[0-9a-f]{64}$".r.matches(value)) Right(value) else Left(AcceptedBaselineDecodeError.InvalidFingerprintDigest(name, value)))
  private def version(obj: JsonObject, name: String): Either[AcceptedBaselineDecodeError, String] = string(obj, name).flatMap(value => if (value.isEmpty) Left(AcceptedBaselineDecodeError.EmptyVersionString(name)) else if (value.trim != value) Left(AcceptedBaselineDecodeError.WhitespaceVersionString(name)) else Right(value))
  private def normalizeScale(value: BigDecimal, name: String): Either[AcceptedBaselineDecodeError, BigDecimal] =
    try Right(value.setScale(Scale, BigDecimal.RoundingMode.UNNECESSARY))
    catch { case _: ArithmeticException => Left(AcceptedBaselineDecodeError.InvalidScale(name, value.toString)) }
}
