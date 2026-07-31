package leaderboard.search.gen2.eval

import scala.math.BigDecimal.RoundingMode

final class RankingEvaluationInput private (
  val caseId: EvaluationCaseId,
  val partition: EvaluationPartition,
  val surfaceId: EvaluationSurfaceId,
  val slices: Vector[EvaluationSliceId],
  val judgments: RankingJudgments,
  val rawRankedIds: Vector[EvaluationResultId],
  val cutoffs: EvaluationCutoffs,
) {
  override def equals(obj: Any): Boolean = obj match {
    case other: RankingEvaluationInput =>
      caseId == other.caseId && partition == other.partition && surfaceId == other.surfaceId &&
        slices == other.slices && judgments == other.judgments && rawRankedIds == other.rawRankedIds &&
        cutoffs == other.cutoffs
    case _ => false
  }
  override def hashCode(): Int = {
    var h = caseId.hashCode
    h = 31 * h + partition.hashCode
    h = 31 * h + surfaceId.hashCode
    h = 31 * h + slices.hashCode
    h = 31 * h + judgments.hashCode
    h = 31 * h + rawRankedIds.hashCode
    h = 31 * h + cutoffs.hashCode
    h
  }
  override def toString: String =
    s"RankingEvaluationInput(case=${caseId.value}, surface=${surfaceId.value}, partition=$partition, rankedIds=${rawRankedIds.size})"
}
object RankingEvaluationInput {
  def from(
    caseId: EvaluationCaseId,
    partition: EvaluationPartition,
    surfaceId: EvaluationSurfaceId,
    slices: Vector[EvaluationSliceId],
    judgments: RankingJudgments,
    rawRankedIds: Vector[EvaluationResultId],
    cutoffs: EvaluationCutoffs,
  ): Either[String, RankingEvaluationInput] =
    if (slices.distinct.size != slices.size)
      Left(s"slices must be unique")
    else
      Right(new RankingEvaluationInput(caseId, partition, surfaceId, slices, judgments, rawRankedIds, cutoffs))
}

sealed trait NotApplicableReason {
  def stableCode: String
}
object NotApplicableReason {
  case object EmptyAcceptableSet extends NotApplicableReason { val stableCode = "empty_acceptable_set" }
  case object RequiresExhaustive extends NotApplicableReason { val stableCode = "requires_exhaustive" }
  case object RequiresPartial extends NotApplicableReason { val stableCode = "requires_partial" }
  case object RequiresPositiveGain extends NotApplicableReason { val stableCode = "requires_positive_gain" }
  case object NoJudgedInTopK extends NotApplicableReason { val stableCode = "no_judged_in_top_k" }
  case object UnjudgedTopK extends NotApplicableReason { val stableCode = "unjudged_top_k" }
}

sealed trait MetricValue
object MetricValue {
  final class Applicable private (val value: BigDecimal) extends MetricValue {
    override def equals(obj: Any): Boolean = obj match {
      case other: Applicable => value == other.value
      case _ => false
    }
    override def hashCode(): Int = value.hashCode
  }
  object Applicable {
    private[eval] def apply(value: BigDecimal): Applicable = new Applicable(value)
    def unapply(value: MetricValue): Option[BigDecimal] = value match {
      case actual: Applicable => Some(actual.value)
      case _ => None
    }
  }
  final class NotApplicable private (val reason: NotApplicableReason) extends MetricValue {
    override def equals(obj: Any): Boolean = obj match {
      case other: NotApplicable => reason == other.reason
      case _ => false
    }
    override def hashCode(): Int = reason.hashCode
  }
  object NotApplicable {
    private[eval] def apply(reason: NotApplicableReason): NotApplicable = new NotApplicable(reason)
    def unapply(value: MetricValue): Option[NotApplicableReason] = value match {
      case actual: NotApplicable => Some(actual.reason)
      case _ => None
    }
  }
}

final class DuplicateInfo private (
  val id: EvaluationResultId,
  val encounterOrder: Int,
) {
  override def equals(obj: Any): Boolean = obj match {
    case other: DuplicateInfo => id == other.id && encounterOrder == other.encounterOrder
    case _                    => false
  }
  override def hashCode(): Int = 31 * id.hashCode + encounterOrder.hashCode
}
object DuplicateInfo {
  def from(id: EvaluationResultId, encounterOrder: Int): DuplicateInfo =
    new DuplicateInfo(id, encounterOrder)
}

final class ForbiddenHitInfo private (
  val ids: Vector[EvaluationResultId],
  val count: Int,
) {
  override def equals(obj: Any): Boolean = obj match {
    case other: ForbiddenHitInfo => ids == other.ids && count == other.count
    case _                       => false
  }
  override def hashCode(): Int = 31 * ids.hashCode + count.hashCode
}
object ForbiddenHitInfo {
  private[eval] def from(ids: Vector[EvaluationResultId]): ForbiddenHitInfo =
    new ForbiddenHitInfo(ids, ids.size)
}

final class MetricRow private (
  val cutoff: EvaluationCutoff,
  val success: MetricValue,
  val mrr: MetricValue,
  val precision: MetricValue,
  val recall: MetricValue,
  val judgedPrecision: MetricValue,
  val unjudgedRate: BigDecimal,
  val forbiddenHits: ForbiddenHitInfo,
  val ndcg: MetricValue,
  val pooledNdcg: MetricValue,
  val pooledFingerprint: Option[JudgedPoolFingerprint],
  val pooledCoverage: Option[Int],
) {
  override def equals(obj: Any): Boolean = obj match {
    case other: MetricRow =>
      cutoff == other.cutoff && success == other.success && mrr == other.mrr &&
        precision == other.precision && recall == other.recall && judgedPrecision == other.judgedPrecision &&
        unjudgedRate == other.unjudgedRate && forbiddenHits == other.forbiddenHits && ndcg == other.ndcg &&
        pooledNdcg == other.pooledNdcg && pooledFingerprint == other.pooledFingerprint &&
        pooledCoverage == other.pooledCoverage
    case _ => false
  }
  override def hashCode(): Int = {
    var h = cutoff.hashCode
    h = 31 * h + success.hashCode
    h = 31 * h + mrr.hashCode
    h = 31 * h + precision.hashCode
    h = 31 * h + recall.hashCode
    h = 31 * h + judgedPrecision.hashCode
    h = 31 * h + unjudgedRate.hashCode
    h = 31 * h + forbiddenHits.hashCode
    h = 31 * h + ndcg.hashCode
    h = 31 * h + pooledNdcg.hashCode
    h = 31 * h + pooledFingerprint.hashCode
    h = 31 * h + pooledCoverage.hashCode
    h
  }
}
object MetricRow {
  private[eval] def from(
    cutoff: EvaluationCutoff,
    success: MetricValue,
    mrr: MetricValue,
    precision: MetricValue,
    recall: MetricValue,
    judgedPrecision: MetricValue,
    unjudgedRate: BigDecimal,
    forbiddenHits: ForbiddenHitInfo,
    ndcg: MetricValue,
    pooledNdcg: MetricValue,
    pooledFingerprint: Option[JudgedPoolFingerprint],
    pooledCoverage: Option[Int],
  ): MetricRow =
    new MetricRow(cutoff, success, mrr, precision, recall, judgedPrecision, unjudgedRate, forbiddenHits,
      ndcg, pooledNdcg, pooledFingerprint, pooledCoverage)
}

final class RankingEvaluationResult private (
  val rawRanking: Vector[EvaluationResultId],
  val uniqueRanking: Vector[EvaluationResultId],
  val duplicateIds: Vector[DuplicateInfo],
  val structurallyValid: Boolean,
  val metricRows: Vector[MetricRow],
) {
  override def equals(obj: Any): Boolean = obj match {
    case other: RankingEvaluationResult =>
      rawRanking == other.rawRanking && uniqueRanking == other.uniqueRanking &&
        duplicateIds == other.duplicateIds && structurallyValid == other.structurallyValid &&
        metricRows == other.metricRows
    case _ => false
  }
  override def hashCode(): Int = {
    var h = rawRanking.hashCode
    h = 31 * h + uniqueRanking.hashCode
    h = 31 * h + duplicateIds.hashCode
    h = 31 * h + structurallyValid.hashCode
    h = 31 * h + metricRows.hashCode
    h
  }
}
object RankingEvaluationResult {
  private[eval] def from(
    rawRanking: Vector[EvaluationResultId],
    uniqueRanking: Vector[EvaluationResultId],
    duplicateIds: Vector[DuplicateInfo],
    structurallyValid: Boolean,
    metricRows: Vector[MetricRow],
  ): RankingEvaluationResult =
    new RankingEvaluationResult(rawRanking, uniqueRanking, duplicateIds, structurallyValid, metricRows)
}

object RankingEvaluator {
  val MetricSchemaVersion: String = "search-gen2-ranking-metrics-v1"

  private val scale = 12

  private def normalize(value: BigDecimal): BigDecimal =
    value.setScale(scale, RoundingMode.HALF_UP)

  private def normalize(value: Double): BigDecimal =
    normalize(BigDecimal(value))

  private def log2(x: Double): Double =
    java.lang.StrictMath.log(x) / java.lang.StrictMath.log(2.0)

  def evaluate(input: RankingEvaluationInput): RankingEvaluationResult = {
    val rawRanking = input.rawRankedIds
    val (uniqueRanking, duplicateIds) = computeUniqueRanking(rawRanking)
    val structurallyValid = duplicateIds.isEmpty
    val judgments = input.judgments
    val acceptableSet = judgments.acceptableIds.toSet
    val forbiddenSet = judgments.forbiddenIds.toSet
    val judgedPool = acceptableSet ++ forbiddenSet ++ judgments.neutralIds.toSet

    val metricRows = input.cutoffs.values.map { cutoff =>
      computeMetricRow(
        cutoff, uniqueRanking, judgments, acceptableSet, forbiddenSet,
        judgedPool, input.judgments.mode,
      )
    }

    RankingEvaluationResult.from(rawRanking, uniqueRanking, duplicateIds, structurallyValid, metricRows)
  }

  private def computeUniqueRanking(raw: Vector[EvaluationResultId]): (Vector[EvaluationResultId], Vector[DuplicateInfo]) = {
    val seen = scala.collection.mutable.Set.empty[EvaluationResultId]
    val reported = scala.collection.mutable.Set.empty[EvaluationResultId]
    val duplicates = scala.collection.mutable.ListBuffer.empty[DuplicateInfo]
    val unique = scala.collection.mutable.ListBuffer.empty[EvaluationResultId]
    var pos = 0
    raw.foreach { id =>
      pos += 1
      if (seen.contains(id)) {
        if (!reported.contains(id)) {
          reported += id
          duplicates += DuplicateInfo.from(id, pos)
        }
      } else {
        seen += id
        unique += id
      }
    }
    (Vector.from(unique), Vector.from(duplicates))
  }

  private def computeMetricRow(
    cutoff: EvaluationCutoff,
    uniqueRanking: Vector[EvaluationResultId],
    judgments: RankingJudgments,
    acceptableSet: Set[EvaluationResultId],
    forbiddenSet: Set[EvaluationResultId],
    judgedPool: Set[EvaluationResultId],
    mode: JudgmentMode,
  ): MetricRow = {
    val k = cutoff.value
    val topK = uniqueRanking.take(k)
    val actualK = topK.size

    val success = computeSuccess(topK, acceptableSet)
    val mrr = computeMRR(topK, acceptableSet)

    val precision = computePrecision(cutoff, topK, acceptableSet, mode)
    val recall = computeRecall(topK, acceptableSet, mode)

    val (judgedPrecision, unjudgedRate) = computePartialMetrics(topK, acceptableSet, judgedPool, mode, actualK)

    val forbiddenHits = computeForbiddenHits(topK, forbiddenSet)

    val (ndcg, pooledNdcg, pooledFingerprint, pooledCoverage) =
      computeNDCG(cutoff, topK, judgments, judgedPool, mode)

    MetricRow.from(
      cutoff, success, mrr, precision, recall, judgedPrecision, unjudgedRate,
      forbiddenHits, ndcg, pooledNdcg, pooledFingerprint, pooledCoverage,
    )
  }

  private def computeSuccess(topK: Vector[EvaluationResultId], acceptableSet: Set[EvaluationResultId]): MetricValue =
    if (acceptableSet.isEmpty) MetricValue.NotApplicable(NotApplicableReason.EmptyAcceptableSet)
    else {
      val has = topK.exists(acceptableSet.contains)
      MetricValue.Applicable(normalize(if (has) 1.0 else 0.0))
    }

  private def computeMRR(topK: Vector[EvaluationResultId], acceptableSet: Set[EvaluationResultId]): MetricValue =
    if (acceptableSet.isEmpty) MetricValue.NotApplicable(NotApplicableReason.EmptyAcceptableSet)
    else {
      val idx = topK.zipWithIndex.find { case (id, _) => acceptableSet.contains(id) }
      val value = idx match {
        case Some((_, i)) =>
          val rank = i + 1.0
          1.0 / rank
        case None => 0.0
      }
      MetricValue.Applicable(normalize(value))
    }

  private def computePrecision(
    cutoff: EvaluationCutoff,
    topK: Vector[EvaluationResultId],
    acceptableSet: Set[EvaluationResultId],
    mode: JudgmentMode,
  ): MetricValue =
    mode match {
      case JudgmentMode.Exhaustive =>
        if (cutoff.value == 0) MetricValue.Applicable(normalize(0.0))
        else {
          val acceptable = topK.count(acceptableSet.contains)
          MetricValue.Applicable(normalize(BigDecimal(acceptable) / BigDecimal(cutoff.value)))
        }
      case JudgmentMode.Partial =>
        MetricValue.NotApplicable(NotApplicableReason.RequiresExhaustive)
    }

  private def computeRecall(
    topK: Vector[EvaluationResultId],
    acceptableSet: Set[EvaluationResultId],
    mode: JudgmentMode,
  ): MetricValue =
    mode match {
      case JudgmentMode.Exhaustive =>
        if (acceptableSet.isEmpty) MetricValue.NotApplicable(NotApplicableReason.EmptyAcceptableSet)
        else {
          val hits = topK.count(acceptableSet.contains)
          MetricValue.Applicable(normalize(BigDecimal(hits) / BigDecimal(acceptableSet.size)))
        }
      case JudgmentMode.Partial =>
        MetricValue.NotApplicable(NotApplicableReason.RequiresExhaustive)
    }

  private def computePartialMetrics(
    topK: Vector[EvaluationResultId],
    acceptableSet: Set[EvaluationResultId],
    judgedPool: Set[EvaluationResultId],
    mode: JudgmentMode,
    actualK: Int,
  ): (MetricValue, BigDecimal) = {
    val unjudgedRate = if (actualK == 0) normalize(0.0)
    else {
      val unjudged = topK.count(id => !judgedPool.contains(id))
      normalize(BigDecimal(unjudged) / BigDecimal(actualK))
    }

    val judgedPrecision = mode match {
      case JudgmentMode.Partial =>
        val judgedInTopK = topK.filter(judgedPool.contains)
        if (judgedInTopK.isEmpty) MetricValue.NotApplicable(NotApplicableReason.NoJudgedInTopK)
        else {
          val acceptableInTopK = topK.count(acceptableSet.contains)
          MetricValue.Applicable(normalize(BigDecimal(acceptableInTopK) / BigDecimal(judgedInTopK.size)))
        }
      case JudgmentMode.Exhaustive =>
        MetricValue.NotApplicable(NotApplicableReason.RequiresPartial)
    }

    (judgedPrecision, unjudgedRate)
  }

  private def computeForbiddenHits(
    topK: Vector[EvaluationResultId],
    forbiddenSet: Set[EvaluationResultId],
  ): ForbiddenHitInfo = {
    val hits = topK.filter(forbiddenSet.contains)
    ForbiddenHitInfo.from(hits)
  }

  private def computeNDCG(
    cutoff: EvaluationCutoff,
    topK: Vector[EvaluationResultId],
    judgments: RankingJudgments,
    judgedPool: Set[EvaluationResultId],
    mode: JudgmentMode,
  ): (MetricValue, MetricValue, Option[JudgedPoolFingerprint], Option[Int]) = {

    def dcg(ids: Vector[EvaluationResultId]): BigDecimal = {
      var total: BigDecimal = BigDecimal(0)
      var rank = 0
      ids.foreach { id =>
        rank += 1
        val gain = judgments.gainFor(id)
        if (gain > 0) {
          val discounted = BigDecimal(gain) / BigDecimal(log2((rank + 1).toDouble))
          total = total + discounted
        }
      }
      total
    }

    val hasPositive = judgments.acceptableIds.exists(id => judgments.gainFor(id) > 0)

    val ndcgResult: MetricValue = mode match {
      case JudgmentMode.Exhaustive =>
        if (!hasPositive) MetricValue.NotApplicable(NotApplicableReason.RequiresPositiveGain)
        else {
          val actualDcg = dcg(topK)
          val allPositiveSorted = judgments.acceptableIds.zipWithIndex
            .map { case (id, declarationIndex) => (id, judgments.gainFor(id), declarationIndex) }
            .filter { case (_, gain, _) => gain > 0 }
            .sortBy { case (_, gain, declarationIndex) => (-gain, declarationIndex) }
            .map { case (id, _, _) => id }
          val idealDcg = dcg(allPositiveSorted.take(cutoff.value))
          if (idealDcg.compare(BigDecimal(0)) == 0) MetricValue.NotApplicable(NotApplicableReason.RequiresPositiveGain)
          else MetricValue.Applicable(normalize(actualDcg / idealDcg))
        }

      case JudgmentMode.Partial =>
        MetricValue.NotApplicable(NotApplicableReason.RequiresExhaustive)
    }

    val (pooledNdcgResult, fp, cover) = mode match {
      case JudgmentMode.Partial =>
        if (topK.nonEmpty && topK.exists(id => !judgedPool.contains(id))) {
          (MetricValue.NotApplicable(NotApplicableReason.UnjudgedTopK): MetricValue, None, None)
        } else if (!hasPositive) {
          (MetricValue.NotApplicable(NotApplicableReason.RequiresPositiveGain): MetricValue, None, None)
        } else {
          val actualDcg = dcg(topK)
          val allPositiveFromPool = judgments.acceptableIds.zipWithIndex
            .map { case (id, declarationIndex) => (id, judgments.gainFor(id), declarationIndex) }
            .filter { case (_, gain, _) => gain > 0 }
            .sortBy { case (_, gain, declarationIndex) => (-gain, declarationIndex) }
            .map { case (id, _, _) => id }
          val idealDcg = dcg(allPositiveFromPool.take(cutoff.value))
          val fingerprint = JudgedPoolFingerprint.compute(
            judgments.acceptableIds, judgments.forbiddenIds,
            judgments.neutralIds, judgments.gradedGains,
          )
          val coverage = judgedPool.size
          if (idealDcg.compare(BigDecimal(0)) == 0)
            (MetricValue.NotApplicable(NotApplicableReason.RequiresPositiveGain): MetricValue, Some(fingerprint), Some(coverage))
          else
            (MetricValue.Applicable(normalize(actualDcg / idealDcg)): MetricValue, Some(fingerprint), Some(coverage))
        }

      case JudgmentMode.Exhaustive =>
        (MetricValue.NotApplicable(NotApplicableReason.RequiresPartial): MetricValue, None, None)
    }

    (ndcgResult, pooledNdcgResult, fp, cover)
  }
}
