package leaderboard.search.gen2.eval

import io.circe.Json
import scala.collection.mutable

final class ProvenanceComponent private (
  val id: EvaluationProvenanceId,
  val value: String,
) {
  override def equals(obj: Any): Boolean = obj match {
    case other: ProvenanceComponent => id == other.id && value == other.value
    case _ => false
  }
  override def hashCode(): Int = 31 * id.hashCode + value.hashCode
}
object ProvenanceComponent {
  def from(id: EvaluationProvenanceId, value: String): Either[String, ProvenanceComponent] =
    if (value.isEmpty) Left(s"provenance value for ${id.value} must be non-empty")
    else if (value.trim != value) Left(s"provenance value for ${id.value} must not have surrounding whitespace")
    else Right(new ProvenanceComponent(id, value))
}

final class AggregateMetricObservation private (
  val scope: MetricKeyScope,
  val average: BigDecimal,
  val applicableCount: Int,
  val notApplicableCount: Int,
) {
  override def equals(obj: Any): Boolean = obj match {
    case other: AggregateMetricObservation =>
      scope == other.scope && average == other.average &&
        applicableCount == other.applicableCount && notApplicableCount == other.notApplicableCount
    case _ => false
  }
  override def hashCode(): Int = {
    var h = scope.hashCode
    h = 31 * h + average.hashCode
    h = 31 * h + applicableCount
    h = 31 * h + notApplicableCount
    h
  }
}
object AggregateMetricObservation {
  private[eval] def from(
    scope: MetricKeyScope,
    average: BigDecimal,
    applicableCount: Int,
    notApplicableCount: Int,
  ): AggregateMetricObservation = new AggregateMetricObservation(scope, average, applicableCount, notApplicableCount)
}

final class AggregateSection private (
  val structuralInvalidCount: Int,
  val duplicateIdentityCount: Int,
  val zeroResultCount: Int,
  val forbiddenHitCount: Int,
  val applicableMetricCount: Int,
  val notApplicableMetricCount: Int,
  val metricObservations: Vector[AggregateMetricObservation],
) {
  override def equals(obj: Any): Boolean = obj match {
    case other: AggregateSection =>
      structuralInvalidCount == other.structuralInvalidCount &&
        duplicateIdentityCount == other.duplicateIdentityCount && zeroResultCount == other.zeroResultCount &&
        forbiddenHitCount == other.forbiddenHitCount && applicableMetricCount == other.applicableMetricCount &&
        notApplicableMetricCount == other.notApplicableMetricCount && metricObservations == other.metricObservations
    case _ => false
  }
  override def hashCode(): Int = {
    var h = structuralInvalidCount
    h = 31 * h + duplicateIdentityCount
    h = 31 * h + zeroResultCount
    h = 31 * h + forbiddenHitCount
    h = 31 * h + applicableMetricCount
    h = 31 * h + notApplicableMetricCount
    h = 31 * h + metricObservations.hashCode
    h
  }
}
object AggregateSection {
  private[eval] def from(
    structuralInvalidCount: Int,
    duplicateIdentityCount: Int,
    zeroResultCount: Int,
    forbiddenHitCount: Int,
    applicableMetricCount: Int,
    notApplicableMetricCount: Int,
    metricObservations: Vector[AggregateMetricObservation],
  ): AggregateSection = new AggregateSection(
    structuralInvalidCount, duplicateIdentityCount, zeroResultCount, forbiddenHitCount,
    applicableMetricCount, notApplicableMetricCount, metricObservations,
  )
}

final class CaseEvaluationResult private (
  val caseId: EvaluationCaseId,
  val partition: EvaluationPartition,
  val judgmentMode: JudgmentMode,
  val slices: Option[Vector[EvaluationSliceId]],
  val surfaceResults: Vector[(EvaluationSurfaceId, SurfaceCaseResult)],
) {
  override def equals(obj: Any): Boolean = obj match {
    case other: CaseEvaluationResult =>
      caseId == other.caseId && partition == other.partition && judgmentMode == other.judgmentMode &&
        slices == other.slices && surfaceResults == other.surfaceResults
    case _ => false
  }
  override def hashCode(): Int = {
    var h = caseId.hashCode
    h = 31 * h + partition.hashCode
    h = 31 * h + judgmentMode.hashCode
    h = 31 * h + slices.hashCode
    h = 31 * h + surfaceResults.hashCode
    h
  }
}
object CaseEvaluationResult {
  private[eval] def create(
    caseId: EvaluationCaseId,
    partition: EvaluationPartition,
    judgmentMode: JudgmentMode,
    slices: Option[Vector[EvaluationSliceId]],
    surfaceResults: Vector[(EvaluationSurfaceId, SurfaceCaseResult)],
  ): CaseEvaluationResult = new CaseEvaluationResult(caseId, partition, judgmentMode, slices, surfaceResults)
}

final class SurfaceCaseResult private (
  val rawRanking: Vector[EvaluationResultId],
  val uniqueRanking: Vector[EvaluationResultId],
  val structurallyValid: Boolean,
  val duplicateIds: Vector[DuplicateInfo],
  val metricRows: Vector[MetricRow],
) {
  override def equals(obj: Any): Boolean = obj match {
    case other: SurfaceCaseResult =>
      rawRanking == other.rawRanking && uniqueRanking == other.uniqueRanking &&
        structurallyValid == other.structurallyValid && duplicateIds == other.duplicateIds &&
        metricRows == other.metricRows
    case _ => false
  }
  override def hashCode(): Int = {
    var h = rawRanking.hashCode
    h = 31 * h + uniqueRanking.hashCode
    h = 31 * h + structurallyValid.hashCode
    h = 31 * h + duplicateIds.hashCode
    h = 31 * h + metricRows.hashCode
    h
  }
}
object SurfaceCaseResult {
  private[eval] def from(result: RankingEvaluationResult): SurfaceCaseResult =
    new SurfaceCaseResult(result.rawRanking, result.uniqueRanking, result.structurallyValid, result.duplicateIds, result.metricRows)
}

final class EvaluationReportCaseInput private (
  val caseId: EvaluationCaseId,
  val partition: EvaluationPartition,
  val judgmentMode: JudgmentMode,
  val slices: Option[Vector[EvaluationSliceId]],
  val orderedSurfaceResults: Vector[(EvaluationSurfaceId, RankingEvaluationResult)],
)
object EvaluationReportCaseInput {
  def from(
    caseId: EvaluationCaseId,
    partition: EvaluationPartition,
    judgmentMode: JudgmentMode,
    slices: Option[Vector[EvaluationSliceId]],
    orderedSurfaceResults: Vector[(EvaluationSurfaceId, RankingEvaluationResult)],
  ): Either[String, EvaluationReportCaseInput] = {
    val surfaceIds = orderedSurfaceResults.map(_._1)
    if (surfaceIds.isEmpty) Left("orderedSurfaceResults must be non-empty")
    else if (surfaceIds.distinct.size != surfaceIds.size) Left("surface IDs must be unique")
    else if (slices.exists(s => s.distinct.size != s.size)) Left("slice IDs must be unique")
    else Right(new EvaluationReportCaseInput(caseId, partition, judgmentMode, slices, orderedSurfaceResults))
  }
}

final class EvaluationReport private (
  val schemaVersion: String,
  val provenanceComponents: Vector[ProvenanceComponent],
  val caseResults: Vector[CaseEvaluationResult],
  val globalAggregates: AggregateSection,
  val partitionAggregates: Vector[(EvaluationPartition, AggregateSection)],
  val surfaceAggregates: Vector[(EvaluationSurfaceId, AggregateSection)],
  val sliceAggregates: Vector[(EvaluationSliceId, AggregateSection)],
) {
  override def equals(obj: Any): Boolean = obj match {
    case other: EvaluationReport =>
      schemaVersion == other.schemaVersion && provenanceComponents == other.provenanceComponents &&
        caseResults == other.caseResults && globalAggregates == other.globalAggregates &&
        partitionAggregates == other.partitionAggregates && surfaceAggregates == other.surfaceAggregates &&
        sliceAggregates == other.sliceAggregates
    case _ => false
  }
  override def hashCode(): Int = {
    var h = schemaVersion.hashCode
    h = 31 * h + provenanceComponents.hashCode
    h = 31 * h + caseResults.hashCode
    h = 31 * h + globalAggregates.hashCode
    h = 31 * h + partitionAggregates.hashCode
    h = 31 * h + surfaceAggregates.hashCode
    h = 31 * h + sliceAggregates.hashCode
    h
  }
}

object EvaluationReport {
  val CurrentSchemaVersion = "search-gen2-evaluation-report-v1"

  private[eval] def create(
    provenanceComponents: Vector[ProvenanceComponent],
    caseResults: Vector[CaseEvaluationResult],
    globalAggregates: AggregateSection,
    partitionAggregates: Vector[(EvaluationPartition, AggregateSection)],
    surfaceAggregates: Vector[(EvaluationSurfaceId, AggregateSection)],
    sliceAggregates: Vector[(EvaluationSliceId, AggregateSection)],
  ): EvaluationReport = new EvaluationReport(CurrentSchemaVersion, provenanceComponents, caseResults, globalAggregates, partitionAggregates, surfaceAggregates, sliceAggregates)

  def encodeDetailed(report: EvaluationReport): Json = {
    val visible = report.caseResults.filter(_.partition != EvaluationPartition.ProtectedHoldout).map(toInput)
      .foldLeft[Either[String, Vector[EvaluationReportCaseInput]]](Right(Vector.empty)) {
        case (acc, input) => acc.flatMap(xs => input.map(xs :+ _))
      }
    val visibleReport = visible match {
      case Right(inputs) => EvaluationReportBuilder.build(report.provenanceComponents, inputs)
      case Left(error) => throw new IllegalArgumentException(error)
    }
    encode(visibleReport, includeCases = true)
  }

  def encodeProtected(report: EvaluationReport): Json = {
    val protectedInputs = report.caseResults.filter(_.partition == EvaluationPartition.ProtectedHoldout).map(toInput)
      .foldLeft[Either[String, Vector[EvaluationReportCaseInput]]](Right(Vector.empty)) {
        case (acc, input) => acc.flatMap(xs => input.map(xs :+ _))
      }
    val protectedReport = protectedInputs match {
      case Right(inputs) => EvaluationReportBuilder.build(report.provenanceComponents, inputs)
      case Left(error) => throw new IllegalArgumentException(error)
    }
    encode(protectedReport, includeCases = false)
  }

  private def toInput(cr: CaseEvaluationResult): Either[String, EvaluationReportCaseInput] =
    EvaluationReportCaseInput.from(
      cr.caseId, cr.partition, cr.judgmentMode, cr.slices,
      cr.surfaceResults.map { case (surface, result) =>
        surface -> RankingEvaluationResult.from(
          result.rawRanking, result.uniqueRanking, result.duplicateIds,
          result.structurallyValid, result.metricRows,
        )
      },
    )

  private def encode(report: EvaluationReport, includeCases: Boolean): Json = {
    val fields = Vector(
      "schemaVersion" -> Json.fromString(report.schemaVersion),
      "provenance" -> Json.fromValues(report.provenanceComponents.map { pc =>
        Json.obj("id" -> Json.fromString(pc.id.value), "value" -> Json.fromString(pc.value))
      }),
    )
    val withCases = if (includeCases) fields :+ ("caseResults" -> Json.fromValues(report.caseResults.map(encodeCaseResult))) else fields
    Json.obj((withCases ++ Vector(
      "globalAggregates" -> encodeAggregateSection(report.globalAggregates),
      "partitionAggregates" -> Json.fromValues(report.partitionAggregates.map { case (p, a) =>
        Json.obj("partition" -> Json.fromString(p.stableCode), "section" -> encodeAggregateSection(a))
      }),
      "surfaceAggregates" -> Json.fromValues(report.surfaceAggregates.map { case (s, a) =>
        Json.obj("surface" -> Json.fromString(s.value), "section" -> encodeAggregateSection(a))
      }),
      "sliceAggregates" -> Json.fromValues(report.sliceAggregates.map { case (s, a) =>
        Json.obj("slice" -> Json.fromString(s.value), "section" -> encodeAggregateSection(a))
      }),
    )): _*)
  }

  private def encodeCaseResult(cr: CaseEvaluationResult): Json = {
    val fields = Vector(
      "caseId" -> Json.fromString(cr.caseId.value),
      "partition" -> Json.fromString(cr.partition.stableCode),
      "judgmentMode" -> Json.fromString(cr.judgmentMode.stableCode),
      "surfaceResults" -> Json.fromValues(cr.surfaceResults.map { case (surface, result) =>
        Json.obj("surface" -> Json.fromString(surface.value), "result" -> encodeSurfaceCaseResult(result))
      }),
    )
    cr.slices match {
      case Some(slices) => Json.obj((fields :+ ("slices" -> Json.fromValues(slices.map(s => Json.fromString(s.value))))): _*)
      case None => Json.obj(fields: _*)
    }
  }

  private def encodeSurfaceCaseResult(res: SurfaceCaseResult): Json = Json.obj(
    "rawRanking" -> Json.fromValues(res.rawRanking.map(id => Json.fromString(id.value))),
    "uniqueRanking" -> Json.fromValues(res.uniqueRanking.map(id => Json.fromString(id.value))),
    "structurallyValid" -> Json.fromBoolean(res.structurallyValid),
    "duplicateIds" -> Json.fromValues(res.duplicateIds.map(d => Json.obj(
      "id" -> Json.fromString(d.id.value), "encounterOrder" -> Json.fromInt(d.encounterOrder),
    ))),
    "metricRows" -> Json.fromValues(res.metricRows.map(encodeMetricRow)),
  )

  private def encodeMetricRow(row: MetricRow): Json = {
    val fields = Vector(
      "cutoff" -> Json.fromInt(row.cutoff.value),
      "success" -> encodeMetricValue(row.success),
      "mrr" -> encodeMetricValue(row.mrr),
      "precision" -> encodeMetricValue(row.precision),
      "recall" -> encodeMetricValue(row.recall),
      "judged_precision" -> encodeMetricValue(row.judgedPrecision),
      "unjudged_rate" -> Json.fromBigDecimal(row.unjudgedRate),
      "forbidden_hits" -> Json.obj(
        "count" -> Json.fromInt(row.forbiddenHits.count),
        "ids" -> Json.fromValues(row.forbiddenHits.ids.map(id => Json.fromString(id.value))),
      ),
      "ndcg" -> encodeMetricValue(row.ndcg),
      "pooled_ndcg" -> encodeMetricValue(row.pooledNdcg),
    )
    val withFingerprint = row.pooledFingerprint.fold(fields)(fp => fields :+ ("pooled_fingerprint" -> Json.fromString(fp.value)))
    row.pooledCoverage.fold(Json.obj(withFingerprint: _*))(coverage => Json.obj((withFingerprint :+ ("pooled_coverage" -> Json.fromInt(coverage))): _*))
  }

  private def encodeMetricValue(value: MetricValue): Json = value match {
    case actual: MetricValue.Applicable => Json.fromBigDecimal(actual.value)
    case actual: MetricValue.NotApplicable => Json.obj(
      "not_applicable" -> Json.fromBoolean(true),
      "reason" -> Json.fromString(actual.reason.stableCode),
    )
  }

  private def encodeAggregateSection(section: AggregateSection): Json = Json.obj(
    "structuralInvalidCount" -> Json.fromInt(section.structuralInvalidCount),
    "duplicateIdentityCount" -> Json.fromInt(section.duplicateIdentityCount),
    "zeroResultCount" -> Json.fromInt(section.zeroResultCount),
    "forbiddenHitCount" -> Json.fromInt(section.forbiddenHitCount),
    "applicableMetricCount" -> Json.fromInt(section.applicableMetricCount),
    "notApplicableMetricCount" -> Json.fromInt(section.notApplicableMetricCount),
    "metricObservations" -> Json.fromValues(section.metricObservations.map { observation =>
      Json.obj(
        "surface" -> Json.fromString(observation.scope.surfaceId.value),
        "metric" -> Json.fromString(observation.scope.metricId.value),
        "cutoff" -> Json.fromInt(observation.scope.cutoff.value),
        "average" -> Json.fromBigDecimal(observation.average),
        "applicableCount" -> Json.fromInt(observation.applicableCount),
        "notApplicableCount" -> Json.fromInt(observation.notApplicableCount),
      )
    }),
  )
}

object EvaluationReportBuilder {
  private val scale = 12
  private def normalize(value: BigDecimal): BigDecimal = value.setScale(scale, BigDecimal.RoundingMode.HALF_UP)

  def build(
    provenanceComponents: Vector[ProvenanceComponent],
    inputs: Vector[EvaluationReportCaseInput],
  ): EvaluationReport = {
    val provenanceIds = provenanceComponents.map(_.id)
    require(provenanceIds.distinct.size == provenanceIds.size, "provenance IDs must be unique")
    val caseIds = inputs.map(_.caseId)
    require(caseIds.distinct.size == caseIds.size, "case IDs must be unique")
    val caseResults = inputs.map { input =>
      CaseEvaluationResult.create(
        input.caseId, input.partition, input.judgmentMode, input.slices,
        input.orderedSurfaceResults.map { case (surface, result) => surface -> SurfaceCaseResult.from(result) },
      )
    }
    val partition = caseResults.map(_.partition).distinct.flatMap(p => {
      val selected = caseResults.filter(_.partition == p)
      if (selected.nonEmpty) Some(p -> aggregate(selected.flatMap(cr => cr.surfaceResults.map((cr, _))))) else None
    })
    val surfaces = caseResults.flatMap(_.surfaceResults.map(_._1)).distinct.flatMap { surface =>
      val selected = caseResults.flatMap(cr => cr.surfaceResults.collect { case (id, result) if id == surface => (cr, (id, result)) })
      if (selected.nonEmpty) Some(surface -> aggregate(selected)) else None
    }
    val slices = caseResults.flatMap(_.slices.toVector.flatten).distinct.flatMap { slice =>
      val selected = caseResults.filter(_.slices.exists(_.contains(slice))).flatMap(cr => cr.surfaceResults.map((cr, _)))
      if (selected.nonEmpty) Some(slice -> aggregate(selected)) else None
    }
    EvaluationReport.create(
      provenanceComponents, caseResults,
      aggregate(caseResults.flatMap(cr => cr.surfaceResults.map((cr, _)))), partition, surfaces, slices,
    )
  }

  private final case class Acc(sum: BigDecimal, applicable: Int, notApplicable: Int)

  private def aggregate(entries: Vector[(CaseEvaluationResult, (EvaluationSurfaceId, SurfaceCaseResult))]): AggregateSection = {
    var structuralInvalid = 0
    var duplicateIdentity = 0
    var zeroResult = 0
    var forbiddenHit = 0
    var applicableCount = 0
    var notApplicableCount = 0
    val accumulators = mutable.LinkedHashMap.empty[MetricKeyScope, Acc]
    entries.foreach { case (_, (surface, result)) =>
      if (!result.structurallyValid) structuralInvalid += 1
      duplicateIdentity += result.duplicateIds.size
      if (result.uniqueRanking.isEmpty) zeroResult += 1
      val largestCutoff = result.metricRows.map(_.cutoff.value).maxOption
      result.metricRows.foreach { row =>
        if (largestCutoff.contains(row.cutoff.value)) forbiddenHit += row.forbiddenHits.count
        val values = Vector(
          "success" -> row.success, "mrr" -> row.mrr, "precision" -> row.precision,
          "recall" -> row.recall, "judged_precision" -> row.judgedPrecision,
          "unjudged_rate" -> MetricValue.Applicable(row.unjudgedRate),
          "ndcg" -> row.ndcg, "pooled_ndcg" -> row.pooledNdcg,
        )
        values.foreach { case (metric, value) =>
          val metricId = EvaluationMetricId.from(metric).fold(error => throw new IllegalArgumentException(error), identity)
          val scope = MetricKeyScope.from(surface, metricId, row.cutoff)
          value match {
            case actual: MetricValue.Applicable =>
              applicableCount += 1
              accumulators.update(scope, accumulators.get(scope) match {
                case Some(previous) => Acc(previous.sum + actual.value, previous.applicable + 1, previous.notApplicable)
                case None => Acc(actual.value, 1, 0)
              })
            case _: MetricValue.NotApplicable =>
              notApplicableCount += 1
              accumulators.update(scope, accumulators.get(scope) match {
                case Some(previous) => Acc(previous.sum, previous.applicable, previous.notApplicable + 1)
                case None => Acc(BigDecimal(0), 0, 1)
              })
          }
        }
      }
    }
    val observations = accumulators.toVector.map { case (scope, acc) =>
      AggregateMetricObservation.from(scope, if (acc.applicable == 0) BigDecimal(0).setScale(scale) else normalize(acc.sum / BigDecimal(acc.applicable)), acc.applicable, acc.notApplicable)
    }
    AggregateSection.from(structuralInvalid, duplicateIdentity, zeroResult, forbiddenHit, applicableCount, notApplicableCount, observations)
  }
}

final class MetricKeyScope private (
  val surfaceId: EvaluationSurfaceId,
  val metricId: EvaluationMetricId,
  val cutoff: EvaluationCutoff,
) {
  override def equals(obj: Any): Boolean = obj match {
    case other: MetricKeyScope => surfaceId == other.surfaceId && metricId == other.metricId && cutoff == other.cutoff
    case _ => false
  }
  override def hashCode(): Int = (surfaceId, metricId, cutoff).hashCode
}
object MetricKeyScope {
  def from(surfaceId: EvaluationSurfaceId, metricId: EvaluationMetricId, cutoff: EvaluationCutoff): MetricKeyScope = new MetricKeyScope(surfaceId, metricId, cutoff)
}
