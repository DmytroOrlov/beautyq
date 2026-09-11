package leaderboard.search.beautyq.gen2.eval

import leaderboard.search.gen2.eval.*
import leaderboard.model.{MasterServiceOfferVariantId, MasterLocationId, ServiceId}
import io.circe.{Json, JsonObject}
import io.circe.parser.*
import java.util.UUID
import scala.util.Try

sealed trait CorpusLoadError
object CorpusLoadError {
  final case class ResourceNotFound(path: String) extends CorpusLoadError
  final case class MalformedJson(message: String) extends CorpusLoadError
  final case class ExpectedObject(context: String) extends CorpusLoadError
  final case class UnexpectedFields(context: String, actual: Set[String]) extends CorpusLoadError
  final case class MissingOrInvalidField(context: String, name: String) extends CorpusLoadError
  final case class UnsupportedSchemaVersion(actual: String) extends CorpusLoadError
  final case class InvalidCorpusId(actual: String) extends CorpusLoadError
  final case class InvalidVersion(actual: Int) extends CorpusLoadError
  case object EmptyCorpus extends CorpusLoadError
  final case class InvalidLatitude(value: Double) extends CorpusLoadError
  final case class InvalidLongitude(value: Double) extends CorpusLoadError
  final case class InvalidUuid(context: String, value: String) extends CorpusLoadError
  final case class DuplicateCaseId(id: String) extends CorpusLoadError
  final case class DuplicateSliceId(caseId: String, sliceId: String) extends CorpusLoadError
  final case class MissingSurface(caseId: String, surface: String) extends CorpusLoadError
  final case class ParseError(message: String) extends CorpusLoadError
}

final class UserLocation private (
  val label: String,
  val lat: Double,
  val lon: Double,
) {
  override def equals(obj: Any): Boolean = obj match {
    case other: UserLocation => label == other.label && lat == other.lat && lon == other.lon
    case _                   => false
  }
  override def hashCode(): Int = {
    var h = label.hashCode
    h = 31 * h + lat.hashCode
    h = 31 * h + lon.hashCode
    h
  }
  override def toString: String = s"UserLocation($label, $lat, $lon)"
}

object UserLocation {
  def from(label: String, lat: Double, lon: Double): UserLocation =
    new UserLocation(label, lat, lon)
}

final class CorpusCase private[beautyq] (
  val caseId: EvaluationCaseId,
  val partition: EvaluationPartition,
  val judgmentMode: JudgmentMode,
  val query: String,
  val language: String,
  val slices: Vector[EvaluationSliceId],
  val userIntent: String,
  val notes: Vector[String],
  val variantJudgments: RankingJudgments,
  val providerJudgments: RankingJudgments,
  val serviceIntentJudgments: RankingJudgments,
) {
  override def equals(obj: Any): Boolean = obj match {
    case other: CorpusCase =>
      caseId == other.caseId && partition == other.partition && judgmentMode == other.judgmentMode &&
        query == other.query && language == other.language && slices == other.slices &&
        userIntent == other.userIntent && notes == other.notes &&
        variantJudgments == other.variantJudgments && providerJudgments == other.providerJudgments &&
        serviceIntentJudgments == other.serviceIntentJudgments
    case _ => false
  }
  override def hashCode(): Int = {
    var h = caseId.hashCode
    h = 31 * h + partition.hashCode
    h = 31 * h + judgmentMode.hashCode
    h = 31 * h + query.hashCode
    h = 31 * h + language.hashCode
    h = 31 * h + slices.hashCode
    h = 31 * h + userIntent.hashCode
    h = 31 * h + notes.hashCode
    h = 31 * h + variantJudgments.hashCode
    h = 31 * h + providerJudgments.hashCode
    h = 31 * h + serviceIntentJudgments.hashCode
    h
  }
  override def toString: String =
    s"CorpusCase(${caseId.value}, $partition, $query, ${slices.size} slices)"
}

final class BeautyQEvaluationCorpus private[beautyq] (
  val schemaVersion: String,
  val corpusId: String,
  val dataset: String,
  val version: Int,
  val defaultUserLocation: UserLocation,
  val cases: Vector[CorpusCase],
) {
  override def equals(obj: Any): Boolean = obj match {
    case other: BeautyQEvaluationCorpus =>
      schemaVersion == other.schemaVersion && corpusId == other.corpusId &&
        dataset == other.dataset && version == other.version &&
        defaultUserLocation == other.defaultUserLocation && cases == other.cases
    case _ => false
  }
  override def hashCode(): Int = {
    var h = schemaVersion.hashCode
    h = 31 * h + corpusId.hashCode
    h = 31 * h + dataset.hashCode
    h = 31 * h + version.hashCode
    h = 31 * h + defaultUserLocation.hashCode
    h = 31 * h + cases.hashCode
    h
  }
  override def toString: String =
    s"BeautyQEvaluationCorpus($corpusId, v$version, ${cases.size} cases)"
}

object BeautyQEvaluationCorpus {
  private val ResourcePath = "leaderboard/search/beautyq/gen2/eval/beautyq_evaluation_corpus_v2.json"
  private val ExpectedSchemaVersion = "beautyq-evaluation-corpus-v2"
  private val CorpusIdPattern = "^[a-z0-9][a-z0-9._:-]*$".r

  private val TopLevelFields = Set(
    "schemaVersion", "corpusId", "dataset", "version", "defaultUserLocation", "cases",
  )
  private val LocationFields = Set("label", "lat", "lon")
  private val CaseFields = Set(
    "id", "partition", "judgmentMode", "query", "language", "slices", "userIntent", "notes", "judgments",
  )
  private val JudgmentFields = Set("variants", "providers", "serviceIntents")
  private val JudgmentSurfaceOrder = Vector("variants", "providers", "serviceIntents")
  private val SurfaceFields = Set("acceptableIds", "forbiddenIds", "neutralIds", "gradedGains")
  private val GradedGainFields = Set("id", "gain")

  def loadCanonical(): Either[CorpusLoadError, BeautyQEvaluationCorpus] =
    for {
      raw <- readResource(ResourcePath)
      json <- parse(raw).left.map(e => CorpusLoadError.MalformedJson(e.message))
      corpus <- decodeFromJson(json)
    } yield corpus

  def decodeFromJson(json: Json): Either[CorpusLoadError, BeautyQEvaluationCorpus] =
    for {
      obj <- json.asObject.toRight(CorpusLoadError.ExpectedObject("root"))
      _ <- checkExactFields(obj, "root", TopLevelFields)
      schema <- string(obj, "root", "schemaVersion")
      _ <- Either.cond(schema == ExpectedSchemaVersion, (),
        CorpusLoadError.UnsupportedSchemaVersion(schema): CorpusLoadError)
      corpusId <- string(obj, "root", "corpusId")
      _ <- validateCorpusId(corpusId)
      dataset <- string(obj, "root", "dataset")
      version <- int(obj, "root", "version")
      _ <- Either.cond(version > 0, (), CorpusLoadError.InvalidVersion(version): CorpusLoadError)
      location <- parseUserLocation(obj)
      casesJson <- array(obj, "root", "cases")
      _ <- Either.cond(casesJson.nonEmpty, (), CorpusLoadError.EmptyCorpus: CorpusLoadError)
      cases <- parseCases(casesJson)
      _ <- checkUniqueCaseIds(cases)
    } yield new BeautyQEvaluationCorpus(schema, corpusId, dataset, version, location, cases)

  private def validateCorpusId(
    corpusId: String,
  ): Either[CorpusLoadError, Unit] =
    if (corpusId.isEmpty) Left(CorpusLoadError.InvalidCorpusId("empty"))
    else if (!CorpusIdPattern.matches(corpusId)) Left(CorpusLoadError.InvalidCorpusId(corpusId))
    else Right(())

  private def parseUserLocation(
    obj: JsonObject,
  ): Either[CorpusLoadError, UserLocation] =
    for {
      locObj <- obj("defaultUserLocation").flatMap(_.asObject)
        .toRight(CorpusLoadError.MissingOrInvalidField("root", "defaultUserLocation"))
      _ <- checkExactFields(locObj, "root.defaultUserLocation", LocationFields)
      label <- string(locObj, "root.defaultUserLocation", "label")
      lat <- double(locObj, "root.defaultUserLocation", "lat")
      _ <- Either.cond(lat >= -90.0 && lat <= 90.0, (),
        CorpusLoadError.InvalidLatitude(lat): CorpusLoadError)
      lon <- double(locObj, "root.defaultUserLocation", "lon")
      _ <- Either.cond(lon >= -180.0 && lon <= 180.0, (),
        CorpusLoadError.InvalidLongitude(lon): CorpusLoadError)
    } yield UserLocation.from(label, lat, lon)

  private def parseCases(
    casesJson: Vector[Json],
  ): Either[CorpusLoadError, Vector[CorpusCase]] =
    casesJson.foldLeft[Either[CorpusLoadError, Vector[CorpusCase]]](Right(Vector.empty)) {
      (acc, json) => acc.flatMap(list => parseCase(json).map(list :+ _))
    }

  private def parseCase(
    json: Json,
  ): Either[CorpusLoadError, CorpusCase] =
    for {
      obj <- json.asObject.toRight(CorpusLoadError.ExpectedObject("case"))
      _ <- checkExactFields(obj, "case", CaseFields)
      id <- string(obj, "case", "id")
      caseId <- EvaluationCaseId.from(id).left.map(e => CorpusLoadError.ParseError(e))
      partitionRaw <- string(obj, "case", "partition")
      partition <- parsePartition(partitionRaw)
      modeRaw <- string(obj, "case", "judgmentMode")
      mode <- parseJudgmentMode(modeRaw)
      _ <- Either.cond(mode == JudgmentMode.Partial, (),
        CorpusLoadError.ParseError(s"judgmentMode must be partial, got ${mode.stableCode}"))
      query <- string(obj, "case", "query")
      language <- string(obj, "case", "language")
      slicesJson <- array(obj, "case", "slices")
      slices <- parseSlices(slicesJson, id)
      _ <- checkUniqueSlices(slices, id)
      userIntent <- string(obj, "case", "userIntent")
      notes <- obj("notes").flatMap(_.asArray) match {
        case Some(arr) => arr.foldLeft[Either[CorpusLoadError, Vector[String]]](Right(Vector.empty)) {
          case (acc, note) => acc.flatMap(xs => note.asString
            .toRight(CorpusLoadError.MissingOrInvalidField("case", "notes string element"))
            .map(xs :+ _))
        }
        case None => Left(CorpusLoadError.MissingOrInvalidField("case", "notes"))
      }
      judgmentsObj <- obj("judgments").flatMap(_.asObject)
        .toRight(CorpusLoadError.MissingOrInvalidField("case", "judgments"))
      _ <- checkExactFields(judgmentsObj, s"case.$id.judgments", JudgmentFields)
      _ <- Either.cond(judgmentsObj.keys.toVector == JudgmentSurfaceOrder, (),
        CorpusLoadError.UnexpectedFields(s"case.$id.judgments", judgmentsObj.keys.toSet): CorpusLoadError)
      variantJudgments <- parseJudgments(judgmentsObj, "variants", mode, id, decodeVariantId)
      providerJudgments <- parseJudgments(judgmentsObj, "providers", mode, id, decodeProviderId)
      serviceIntentJudgments <- parseJudgments(judgmentsObj, "serviceIntents", mode, id, decodeServiceIntentId)
    } yield new CorpusCase(
      caseId, partition, mode, query, language, slices, userIntent, notes,
      variantJudgments, providerJudgments, serviceIntentJudgments,
    )

  private def parseJudgments(
    obj: JsonObject,
    surfaceName: String,
    mode: JudgmentMode,
    caseId: String,
    decodeId: String => Either[CorpusLoadError, EvaluationResultId],
  ): Either[CorpusLoadError, RankingJudgments] = {
    val context = s"case.$caseId.judgments"
    for {
      surfaceObj <- obj(surfaceName).flatMap(_.asObject)
        .toRight(CorpusLoadError.MissingSurface(caseId, surfaceName))
      _ <- checkExactFields(surfaceObj, s"$context.$surfaceName", SurfaceFields)
      acceptableIds <- parseResultIds(surfaceObj, "acceptableIds", caseId, surfaceName, decodeId)
      forbiddenIds <- parseResultIds(surfaceObj, "forbiddenIds", caseId, surfaceName, decodeId)
      neutralIds <- parseResultIds(surfaceObj, "neutralIds", caseId, surfaceName, decodeId)
      gradedGains <- parseGradedGains(surfaceObj, caseId, surfaceName, decodeId)
      judgments <- RankingJudgments.from(mode, acceptableIds, forbiddenIds, neutralIds, gradedGains)
        .left.map(e => CorpusLoadError.ParseError(e))
    } yield judgments
  }

  private def parseResultIds(
    obj: JsonObject,
    fieldName: String,
    caseId: String,
    surfaceName: String,
    decodeId: String => Either[CorpusLoadError, EvaluationResultId],
  ): Either[CorpusLoadError, Vector[EvaluationResultId]] = {
    val context = s"case.$caseId.judgments.$surfaceName"
    for {
      rawIds <- obj(fieldName) match {
        case Some(json) => json.asArray.toRight(CorpusLoadError.MissingOrInvalidField(context, fieldName))
        case None => Left(CorpusLoadError.MissingOrInvalidField(context, fieldName))
      }
      ids <- rawIds.foldLeft[Either[CorpusLoadError, Vector[EvaluationResultId]]](Right(Vector.empty)) {
        (acc, json) => acc.flatMap { list =>
          for {
            raw <- json.asString
              .toRight(CorpusLoadError.MissingOrInvalidField(s"$context.$fieldName", "string element"))
            resultId <- decodeId(raw)
          } yield list :+ resultId
        }
      }
    } yield ids
  }

  private def parseGradedGains(
    obj: JsonObject,
    caseId: String,
    surfaceName: String,
    decodeId: String => Either[CorpusLoadError, EvaluationResultId],
  ): Either[CorpusLoadError, Vector[GradedGain]] = {
    val context = s"case.$caseId.judgments.$surfaceName"
    for {
      gainsJson <- obj("gradedGains") match {
        case Some(json) => json.asArray.toRight(CorpusLoadError.MissingOrInvalidField(context, "gradedGains"))
        case None => Left(CorpusLoadError.MissingOrInvalidField(context, "gradedGains"))
      }
      gains <- gainsJson.foldLeft[Either[CorpusLoadError, Vector[GradedGain]]](Right(Vector.empty)) {
        (acc, json) => acc.flatMap { list =>
          for {
            gainObj <- json.asObject
              .toRight(CorpusLoadError.ExpectedObject(s"$context.gradedGain"))
            _ <- checkExactFields(gainObj, s"$context.gradedGain", GradedGainFields)
            rawId <- string(gainObj, s"$context.gradedGain", "id")
            resultId <- decodeId(rawId)
            gainValue <- int(gainObj, s"$context.gradedGain", "gain")
            gain <- RelevanceGain.from(gainValue).left.map(e => CorpusLoadError.ParseError(e))
          } yield list :+ GradedGain.from(resultId, gain)
        }
      }
    } yield gains
  }

  private def decodeVariantId(raw: String): Either[CorpusLoadError, EvaluationResultId] =
    for {
      uuid <- parseCanonicalUuid(raw, s"variant:$raw")
      variantId = MasterServiceOfferVariantId(uuid)
      canonical = variantId.value.toString
      resultId <- EvaluationResultId.from(canonical).left.map(e => CorpusLoadError.ParseError(e))
    } yield resultId

  private def decodeProviderId(raw: String): Either[CorpusLoadError, EvaluationResultId] =
    for {
      uuid <- parseCanonicalUuid(raw, s"provider:$raw")
      locationId = MasterLocationId(uuid)
      canonical = locationId.value.toString
      resultId <- EvaluationResultId.from(canonical).left.map(e => CorpusLoadError.ParseError(e))
    } yield resultId

  private def decodeServiceIntentId(raw: String): Either[CorpusLoadError, EvaluationResultId] =
    for {
      uuid <- parseCanonicalUuid(raw, s"serviceIntent:$raw")
      serviceId = ServiceId(uuid)
      canonical = serviceId.value.toString
      resultId <- EvaluationResultId.from(canonical).left.map(e => CorpusLoadError.ParseError(e))
    } yield resultId

  private def parseCanonicalUuid(raw: String, context: String): Either[CorpusLoadError, UUID] =
    Try(UUID.fromString(raw)).toEither match {
      case Left(_) => Left(CorpusLoadError.InvalidUuid(context, raw))
      case Right(uuid) =>
        if (uuid.toString != raw) Left(CorpusLoadError.InvalidUuid(context, raw))
        else Right(uuid)
    }

  private def parsePartition(raw: String): Either[CorpusLoadError, EvaluationPartition] =
    EvaluationPartition.values.find(_.stableCode == raw)
      .toRight(CorpusLoadError.ParseError(s"unknown partition: $raw"))

  private def parseJudgmentMode(raw: String): Either[CorpusLoadError, JudgmentMode] =
    JudgmentMode.values.find(_.stableCode == raw)
      .toRight(CorpusLoadError.ParseError(s"unknown judgmentMode: $raw"))

  private def parseSlices(
    raw: Vector[Json],
    caseId: String,
  ): Either[CorpusLoadError, Vector[EvaluationSliceId]] =
    raw.foldLeft[Either[CorpusLoadError, Vector[EvaluationSliceId]]](Right(Vector.empty)) {
      (acc, json) => acc.flatMap { list =>
        for {
          rawStr <- json.asString
            .toRight(CorpusLoadError.MissingOrInvalidField(s"case.$caseId.slices", "string element"))
          sliceId <- EvaluationSliceId.from(rawStr).left.map(e => CorpusLoadError.ParseError(e))
        } yield list :+ sliceId
      }
    }

  private def checkUniqueSlices(
    slices: Vector[EvaluationSliceId],
    caseId: String,
  ): Either[CorpusLoadError, Unit] =
    if (slices.distinct.size != slices.size) {
      val duplicates = slices.groupBy(identity).collect { case (k, v) if v.size > 1 => k.value }.mkString(",")
      Left(CorpusLoadError.DuplicateSliceId(caseId, duplicates))
    } else Right(())

  private def checkUniqueCaseIds(
    cases: Vector[CorpusCase],
  ): Either[CorpusLoadError, Unit] =
    if (cases.map(_.caseId).distinct.size != cases.size) {
      val duplicates = cases.groupBy(_.caseId).collect { case (k, v) if v.size > 1 => k.value }.mkString(",")
      Left(CorpusLoadError.DuplicateCaseId(duplicates))
    } else Right(())

  def canonicalJson(corpus: BeautyQEvaluationCorpus): Json = Json.obj(
    "schemaVersion" -> Json.fromString(corpus.schemaVersion),
    "corpusId" -> Json.fromString(corpus.corpusId),
    "dataset" -> Json.fromString(corpus.dataset),
    "version" -> Json.fromInt(corpus.version),
    "defaultUserLocation" -> Json.obj(
      "label" -> Json.fromString(corpus.defaultUserLocation.label),
      "lat" -> Json.fromDoubleOrNull(corpus.defaultUserLocation.lat),
      "lon" -> Json.fromDoubleOrNull(corpus.defaultUserLocation.lon),
    ),
    "cases" -> Json.fromValues(corpus.cases.map { current =>
      Json.obj(
        "id" -> Json.fromString(current.caseId.value),
        "partition" -> Json.fromString(current.partition.stableCode),
        "judgmentMode" -> Json.fromString(current.judgmentMode.stableCode),
        "query" -> Json.fromString(current.query),
        "language" -> Json.fromString(current.language),
        "slices" -> Json.fromValues(current.slices.map(slice => Json.fromString(slice.value))),
        "userIntent" -> Json.fromString(current.userIntent),
        "notes" -> Json.fromValues(current.notes.map(Json.fromString)),
        "judgments" -> Json.obj(
          "variants" -> encodeJudgments(current.variantJudgments),
          "providers" -> encodeJudgments(current.providerJudgments),
          "serviceIntents" -> encodeJudgments(current.serviceIntentJudgments),
        ),
      )
    }),
  )

  private def encodeJudgments(judgments: RankingJudgments): Json = Json.obj(
    "acceptableIds" -> Json.fromValues(judgments.acceptableIds.map(id => Json.fromString(id.value))),
    "forbiddenIds" -> Json.fromValues(judgments.forbiddenIds.map(id => Json.fromString(id.value))),
    "neutralIds" -> Json.fromValues(judgments.neutralIds.map(id => Json.fromString(id.value))),
    "gradedGains" -> Json.fromValues(judgments.gradedGains.map(gain => Json.obj(
      "id" -> Json.fromString(gain.id.value), "gain" -> Json.fromInt(gain.gain.value),
    ))),
  )

  private def readResource(path: String): Either[CorpusLoadError, String] = {
    val stream = getClass.getClassLoader.getResourceAsStream(path)
    if (stream == null) Left(CorpusLoadError.ResourceNotFound(path))
    else {
      val content = scala.io.Source.fromInputStream(stream, "UTF-8").mkString
      try { stream.close() } catch { case _: Exception => () }
      Right(content)
    }
  }

  private def checkExactFields(
    obj: JsonObject,
    context: String,
    expected: Set[String],
  ): Either[CorpusLoadError, Unit] =
    Either.cond(obj.keys.toSet == expected, (),
      CorpusLoadError.UnexpectedFields(context, obj.keys.toSet))

  private def string(
    obj: JsonObject,
    context: String,
    name: String,
  ): Either[CorpusLoadError, String] =
    obj(name).flatMap(_.asString)
      .toRight(CorpusLoadError.MissingOrInvalidField(context, name))

  private def int(
    obj: JsonObject,
    context: String,
    name: String,
  ): Either[CorpusLoadError, Int] =
    obj(name).flatMap(_.asNumber).flatMap(_.toInt)
      .toRight(CorpusLoadError.MissingOrInvalidField(context, name))

  private def double(
    obj: JsonObject,
    context: String,
    name: String,
  ): Either[CorpusLoadError, Double] =
    obj(name).flatMap(_.asNumber).map(_.toDouble)
      .toRight(CorpusLoadError.MissingOrInvalidField(context, name))

  private def array(
    obj: JsonObject,
    context: String,
    name: String,
  ): Either[CorpusLoadError, Vector[Json]] =
    obj(name).flatMap(_.asArray)
      .toRight(CorpusLoadError.MissingOrInvalidField(context, name))
}
