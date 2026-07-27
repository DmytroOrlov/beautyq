package leaderboard.search.gen2.eval

import java.security.MessageDigest

final class EvaluationCaseId private (val value: String) {
  override def equals(obj: Any): Boolean = obj match {
    case other: EvaluationCaseId => value == other.value
    case _                       => false
  }
  override def hashCode(): Int = value.hashCode
  override def toString: String = s"EvaluationCaseId($value)"
}
object EvaluationCaseId {
  private val Pattern = "^[a-z0-9][a-z0-9._:-]*$".r

  def from(raw: String): Either[String, EvaluationCaseId] =
    if (raw.isEmpty) Left("EvaluationCaseId must not be empty")
    else if (raw.trim != raw) Left(s"EvaluationCaseId must not have surrounding whitespace: '$raw'")
    else if (!Pattern.matches(raw)) Left(s"EvaluationCaseId contains invalid characters: '$raw'")
    else Right(new EvaluationCaseId(raw))
}

final class EvaluationSliceId private (val value: String) {
  override def equals(obj: Any): Boolean = obj match {
    case other: EvaluationSliceId => value == other.value
    case _                        => false
  }
  override def hashCode(): Int = value.hashCode
  override def toString: String = s"EvaluationSliceId($value)"
}
object EvaluationSliceId {
  private val Pattern = "^[a-z0-9][a-z0-9._:-]*$".r

  def from(raw: String): Either[String, EvaluationSliceId] =
    if (raw.isEmpty) Left("EvaluationSliceId must not be empty")
    else if (raw.trim != raw) Left(s"EvaluationSliceId must not have surrounding whitespace: '$raw'")
    else if (!Pattern.matches(raw)) Left(s"EvaluationSliceId contains invalid characters: '$raw'")
    else Right(new EvaluationSliceId(raw))
}

final class EvaluationSurfaceId private (val value: String) {
  override def equals(obj: Any): Boolean = obj match {
    case other: EvaluationSurfaceId => value == other.value
    case _                          => false
  }
  override def hashCode(): Int = value.hashCode
  override def toString: String = s"EvaluationSurfaceId($value)"
}
object EvaluationSurfaceId {
  private val Pattern = "^[a-z0-9][a-z0-9._:-]*$".r

  def from(raw: String): Either[String, EvaluationSurfaceId] =
    if (raw.isEmpty) Left("EvaluationSurfaceId must not be empty")
    else if (raw.trim != raw) Left(s"EvaluationSurfaceId must not have surrounding whitespace: '$raw'")
    else if (!Pattern.matches(raw)) Left(s"EvaluationSurfaceId contains invalid characters: '$raw'")
    else Right(new EvaluationSurfaceId(raw))
}

final class EvaluationResultId private (val value: String) {
  override def equals(obj: Any): Boolean = obj match {
    case other: EvaluationResultId => value == other.value
    case _                         => false
  }
  override def hashCode(): Int = value.hashCode
  override def toString: String = s"EvaluationResultId($value)"
}
object EvaluationResultId {
  private val Pattern = "^[a-z0-9][a-z0-9._:-]*$".r

  def from(raw: String): Either[String, EvaluationResultId] =
    if (raw.isEmpty) Left("EvaluationResultId must not be empty")
    else if (raw.trim != raw) Left(s"EvaluationResultId must not have surrounding whitespace: '$raw'")
    else if (!Pattern.matches(raw)) Left(s"EvaluationResultId contains invalid characters: '$raw'")
    else Right(new EvaluationResultId(raw))
}

final class EvaluationMetricId private (val value: String) {
  override def equals(obj: Any): Boolean = obj match {
    case other: EvaluationMetricId => value == other.value
    case _                         => false
  }
  override def hashCode(): Int = value.hashCode
  override def toString: String = s"EvaluationMetricId($value)"
}
object EvaluationMetricId {
  private val Pattern = "^[a-z0-9][a-z0-9._:-]*$".r

  def from(raw: String): Either[String, EvaluationMetricId] =
    if (raw.isEmpty) Left("EvaluationMetricId must not be empty")
    else if (raw.trim != raw) Left(s"EvaluationMetricId must not have surrounding whitespace: '$raw'")
    else if (!Pattern.matches(raw)) Left(s"EvaluationMetricId contains invalid characters: '$raw'")
    else Right(new EvaluationMetricId(raw))
}

final class EvaluationProvenanceId private (val value: String) {
  override def equals(obj: Any): Boolean = obj match {
    case other: EvaluationProvenanceId => value == other.value
    case _                             => false
  }
  override def hashCode(): Int = value.hashCode
  override def toString: String = s"EvaluationProvenanceId($value)"
}
object EvaluationProvenanceId {
  private val Pattern = "^[a-z0-9][a-z0-9._:-]*$".r

  def from(raw: String): Either[String, EvaluationProvenanceId] =
    if (raw.isEmpty) Left("EvaluationProvenanceId must not be empty")
    else if (raw.trim != raw) Left(s"EvaluationProvenanceId must not have surrounding whitespace: '$raw'")
    else if (!Pattern.matches(raw)) Left(s"EvaluationProvenanceId contains invalid characters: '$raw'")
    else Right(new EvaluationProvenanceId(raw))
}

final class EvaluationCutoff private (val value: Int) {
  override def equals(obj: Any): Boolean = obj match {
    case other: EvaluationCutoff => value == other.value
    case _                       => false
  }
  override def hashCode(): Int = value.hashCode
  override def toString: String = s"EvaluationCutoff($value)"
}
object EvaluationCutoff {
  def from(raw: Int): Either[String, EvaluationCutoff] =
    if (raw <= 0) Left(s"EvaluationCutoff must be positive: $raw")
    else Right(new EvaluationCutoff(raw))
}

final class RelevanceGain private (val value: Int) {
  override def equals(obj: Any): Boolean = obj match {
    case other: RelevanceGain => value == other.value
    case _                    => false
  }
  override def hashCode(): Int = value.hashCode
  override def toString: String = s"RelevanceGain($value)"
}
object RelevanceGain {
  def from(raw: Int): Either[String, RelevanceGain] =
    if (raw <= 0) Left(s"RelevanceGain must be positive: $raw")
    else Right(new RelevanceGain(raw))
}

final class EvaluationCutoffs private (val values: Vector[EvaluationCutoff]) {
  override def equals(obj: Any): Boolean = obj match {
    case other: EvaluationCutoffs => values == other.values
    case _                        => false
  }
  override def hashCode(): Int = values.hashCode
  override def toString: String = s"EvaluationCutoffs(${values.map(_.value).mkString(",")})"
}
object EvaluationCutoffs {
  def from(raw: Vector[Int]): Either[String, EvaluationCutoffs] =
    if (raw.isEmpty) Left("EvaluationCutoffs must be non-empty")
    else if (raw.distinct.size != raw.size) Left(s"EvaluationCutoffs must be unique: ${raw.mkString(",")}")
    else {
      val sorted = raw.sorted
      if (raw != sorted) Left(s"EvaluationCutoffs must be strictly increasing: ${raw.mkString(",")}")
      else
        raw.foldLeft[Either[String, Vector[EvaluationCutoff]]](Right(Vector.empty)) { (acc, v) =>
          acc.flatMap { list =>
            EvaluationCutoff.from(v).map(list :+ _)
          }
        }.map(list => new EvaluationCutoffs(list))
    }
}

enum EvaluationPartition(val stableCode: String) {
  case Development extends EvaluationPartition("development")
  case Regression extends EvaluationPartition("regression")
  case ProtectedHoldout extends EvaluationPartition("protected-holdout")
}

enum JudgmentMode(val stableCode: String) {
  case Exhaustive extends JudgmentMode("exhaustive")
  case Partial extends JudgmentMode("partial")
}

final class GradedGain private (
  val id: EvaluationResultId,
  val gain: RelevanceGain,
) {
  override def equals(obj: Any): Boolean = obj match {
    case other: GradedGain => id == other.id && gain == other.gain
    case _                 => false
  }
  override def hashCode(): Int = 31 * id.hashCode + gain.hashCode
}
object GradedGain {
  def from(id: EvaluationResultId, gain: RelevanceGain): GradedGain =
    new GradedGain(id, gain)
}

final class RankingJudgments private (
  val mode: JudgmentMode,
  val acceptableIds: Vector[EvaluationResultId],
  val forbiddenIds: Vector[EvaluationResultId],
  val neutralIds: Vector[EvaluationResultId],
  val gradedGains: Vector[GradedGain],
) {
  private val acceptableSet: Set[EvaluationResultId] = acceptableIds.toSet
  private val forbiddenSet: Set[EvaluationResultId] = forbiddenIds.toSet
  private val neutralSet: Set[EvaluationResultId] = neutralIds.toSet
  private val gradedGainMap: Map[EvaluationResultId, RelevanceGain] = gradedGains.map(g => g.id -> g.gain).toMap

  def judgedPool: Set[EvaluationResultId] = acceptableSet ++ forbiddenSet ++ neutralSet

  def gainFor(id: EvaluationResultId): Int =
    if (forbiddenSet.contains(id) || neutralSet.contains(id)) 0
    else gradedGainMap.get(id).map(_.value).getOrElse(if (acceptableSet.contains(id)) 1 else 0)

  override def equals(obj: Any): Boolean = obj match {
    case other: RankingJudgments =>
      acceptableIds == other.acceptableIds &&
        forbiddenIds == other.forbiddenIds &&
        neutralIds == other.neutralIds &&
        gradedGains == other.gradedGains &&
        mode == other.mode
    case _ => false
  }
  override def hashCode(): Int = {
    var h = mode.hashCode
    h = 31 * h + acceptableIds.hashCode
    h = 31 * h + forbiddenIds.hashCode
    h = 31 * h + neutralIds.hashCode
    h = 31 * h + gradedGains.hashCode
    h
  }
  override def toString: String =
    s"RankingJudgments(mode=$mode, acceptable=${acceptableIds.size}, forbidden=${forbiddenIds.size}, neutral=${neutralIds.size}, graded=${gradedGains.size})"
}
object RankingJudgments {
  def from(
    mode: JudgmentMode,
    acceptableIds: Vector[EvaluationResultId],
    forbiddenIds: Vector[EvaluationResultId],
    neutralIds: Vector[EvaluationResultId],
    gradedGains: Vector[GradedGain],
  ): Either[String, RankingJudgments] = {
    val acceptableSet = acceptableIds.toSet
    val forbiddenSet = forbiddenIds.toSet
    val neutralSet = neutralIds.toSet
    val gradedIds = gradedGains.map(_.id)

    if (acceptableIds.distinct.size != acceptableIds.size)
      Left(s"acceptableIds must be unique")
    else if (forbiddenIds.distinct.size != forbiddenIds.size)
      Left(s"forbiddenIds must be unique")
    else if (neutralIds.distinct.size != neutralIds.size)
      Left(s"neutralIds must be unique")
    else if (gradedIds.distinct.size != gradedIds.size)
      Left(s"gradedGains must have unique IDs")
    else if (acceptableSet.intersect(forbiddenSet).nonEmpty)
      Left(s"acceptable and forbidden sets must be disjoint")
    else if (acceptableSet.intersect(neutralSet).nonEmpty)
      Left(s"acceptable and neutral sets must be disjoint")
    else if (forbiddenSet.intersect(neutralSet).nonEmpty)
      Left(s"forbidden and neutral sets must be disjoint")
    else {
      val nonAcceptableGraded = gradedIds.find(id => !acceptableSet.contains(id))
      nonAcceptableGraded match {
        case Some(id) => Left(s"graded ID $id must be in acceptable set")
        case None =>
          val hasNonPositiveGain = gradedGains.find(_.gain.value <= 0)
          hasNonPositiveGain match {
            case Some(g) => Left(s"graded gain for ${g.id} must be positive: ${g.gain.value}")
            case None    => Right(new RankingJudgments(mode, acceptableIds, forbiddenIds, neutralIds, gradedGains))
          }
      }
    }
  }
}

final class JudgedPoolFingerprint private (val value: String)
object JudgedPoolFingerprint {
  def compute(
    acceptableIds: Vector[EvaluationResultId],
    forbiddenIds: Vector[EvaluationResultId],
    neutralIds: Vector[EvaluationResultId],
    gradedGains: Vector[GradedGain],
  ): JudgedPoolFingerprint = {
    val gainMap = gradedGains.map(g => g.id -> g.gain.value).toMap

    def token(id: EvaluationResultId, judgmentClass: String): String = {
      val gain = gainMap.getOrElse(id, if (acceptableIds.toSet.contains(id)) 1 else 0)
      s"${id.value} $gain $judgmentClass"
    }

    val acceptableTokens = acceptableIds.map(id => token(id, "acceptable"))
    val forbiddenTokens = forbiddenIds.map(id => token(id, "forbidden"))
    val neutralTokens = neutralIds.map(id => token(id, "neutral"))

    val allTokens = (acceptableTokens ++ forbiddenTokens ++ neutralTokens).sorted

    val digest = MessageDigest.getInstance("SHA-256")
    allTokens.foreach { t =>
      digest.update(t.getBytes("UTF-8"))
      digest.update(0x0a.toByte)
    }
    val hash = digest.digest()
    new JudgedPoolFingerprint(hash.map(b => f"$b%02x").mkString)
  }
}
