package leaderboard.search.beautyq.gen2.eval

import io.circe.Json
import leaderboard.search.gen2.eval.{AcceptedEvaluationBaseline, AcceptedBaselineCodec, EvaluationReportDigest, ProvenanceComponent}

sealed trait BeautyQAcceptedEvaluationBaselineError
object BeautyQAcceptedEvaluationBaselineError {
  case object GateNotGreen extends BeautyQAcceptedEvaluationBaselineError
  case object WorkingTreeRevision extends BeautyQAcceptedEvaluationBaselineError
  case object NonPropertyRevision extends BeautyQAcceptedEvaluationBaselineError
  final case class MissingProvenance(id: String) extends BeautyQAcceptedEvaluationBaselineError
  final case class InvalidBaseline(message: String) extends BeautyQAcceptedEvaluationBaselineError
}

object BeautyQAcceptedEvaluationBaseline {
  def fromAcceptedProtectedRun(
    visible: BeautyQMeasuredEvaluationResult,
    protectedRun: BeautyQMeasuredEvaluationResult,
    protectedCorpus: BeautyQProtectedEvaluationCorpus,
    policy: BeautyQProtectedAcceptancePolicy,
    acceptance: BeautyQProtectedAcceptanceResult,
  ): Either[BeautyQAcceptedEvaluationBaselineError, AcceptedEvaluationBaseline] = {
    val provenance = protectedRun.report.provenanceComponents
    val value = (id: String) => provenance.find(_.id.value == id).map(_.value)
    for {
      _ <- Either.cond(acceptance.passed, (), BeautyQAcceptedEvaluationBaselineError.GateNotGreen)
      _ <- Either.cond(visible.correctionGate.passed, (), BeautyQAcceptedEvaluationBaselineError.GateNotGreen)
      _ <- Either.cond(protectedRun.correctionGate.passed, (), BeautyQAcceptedEvaluationBaselineError.GateNotGreen)
      _ <- Either.cond(acceptance.evaluationPolicyVersion == policy.evaluationPolicyVersion, (), BeautyQAcceptedEvaluationBaselineError.InvalidBaseline("evaluation policy identity differs"))
      _ <- Either.cond(acceptance.protectedAcceptancePolicyVersion == policy.protectedAcceptancePolicyVersion, (), BeautyQAcceptedEvaluationBaselineError.InvalidBaseline("protected policy identity differs"))
      _ <- Either.cond(acceptance.policyFingerprint == policy.fingerprint, (), BeautyQAcceptedEvaluationBaselineError.InvalidBaseline("policy fingerprint differs"))
      _ <- Either.cond(acceptance.protectedCorpusFingerprint == protectedCorpus.corpusFingerprint, (), BeautyQAcceptedEvaluationBaselineError.InvalidBaseline("protected corpus fingerprint differs"))
      _ <- Either.cond(acceptance.protectedCaseCount == protectedCorpus.caseCount, (), BeautyQAcceptedEvaluationBaselineError.InvalidBaseline("protected case count differs"))
      _ <- Either.cond(acceptance.protectedReportDigest == protectedRun.protectedReportDigest, (), BeautyQAcceptedEvaluationBaselineError.InvalidBaseline("protected report digest differs"))
      _ <- Either.cond(protectedRun.evaluationPolicyVersion == policy.evaluationPolicyVersion, (), BeautyQAcceptedEvaluationBaselineError.InvalidBaseline("protected run policy differs"))
      actualRevision <- value("application-revision").toRight(BeautyQAcceptedEvaluationBaselineError.MissingProvenance("application-revision"))
      revisionSource <- value("application-revision-source").toRight(BeautyQAcceptedEvaluationBaselineError.MissingProvenance("application-revision-source"))
      _ <- Either.cond(actualRevision != "working-tree", (), BeautyQAcceptedEvaluationBaselineError.WorkingTreeRevision)
      _ <- Either.cond(revisionSource == "system-property", (), BeautyQAcceptedEvaluationBaselineError.NonPropertyRevision)
      visibleCorpusFingerprint <- visible.report.provenanceComponents.find(_.id.value == "corpus-fingerprint").map(_.value)
        .toRight(BeautyQAcceptedEvaluationBaselineError.MissingProvenance("visible-corpus-fingerprint"))
      visibleReportDigest = visible.reportDigest
      actualCorpus <- value("corpus-fingerprint").toRight(BeautyQAcceptedEvaluationBaselineError.MissingProvenance("corpus-fingerprint"))
      _ <- Either.cond(actualCorpus == protectedCorpus.corpusFingerprint, (), BeautyQAcceptedEvaluationBaselineError.InvalidBaseline("run corpus fingerprint differs"))
      metricSchema <- value("metric-schema-version").toRight(BeautyQAcceptedEvaluationBaselineError.MissingProvenance("metric-schema-version"))
      evaluationPolicy <- value("evaluation-policy-version").toRight(BeautyQAcceptedEvaluationBaselineError.MissingProvenance("evaluation-policy-version"))
      orderedAggregates <- BeautyQProtectedAcceptanceGate.orderedPolicyAggregates(protectedRun.report, policy)
        .left.map(BeautyQAcceptedEvaluationBaselineError.InvalidBaseline.apply)
      baseline <- AcceptedEvaluationBaseline.create(
        protectedCorpus.corpusFingerprint,
        metricSchema,
        evaluationPolicy,
        actualRevision,
        provenance ++ additionalProvenance(
          visibleCorpusFingerprint,
          visibleReportDigest,
          protectedCorpus.corpusFingerprint,
          protectedRun.protectedReportDigest,
          policy.fingerprint,
        ),
        protectedRun.protectedReportDigest,
        orderedAggregates,
      ).left.map(BeautyQAcceptedEvaluationBaselineError.InvalidBaseline.apply)
    } yield baseline
  }

  def encodeCandidate(baseline: AcceptedEvaluationBaseline): Json = AcceptedBaselineCodec.encode(baseline)

  def candidateDigest(baseline: AcceptedEvaluationBaseline): String =
    EvaluationReportDigest.compute(encodeCandidate(baseline))

  private def additionalProvenance(
    visibleCorpusFingerprint: String,
    visibleReportDigest: String,
    protectedCorpusFingerprint: String,
    protectedReportDigest: String,
    policyFingerprint: String,
  ): Vector[ProvenanceComponent] = {
    val values = Vector(
      "visible-corpus-fingerprint" -> visibleCorpusFingerprint,
      "visible-report-digest" -> visibleReportDigest,
      "protected-corpus-fingerprint" -> protectedCorpusFingerprint,
      "protected-report-digest" -> protectedReportDigest,
      "protected-policy-fingerprint" -> policyFingerprint,
    )
    values.flatMap { case (idText, value) =>
      leaderboard.search.gen2.eval.EvaluationProvenanceId.from(idText).toOption.flatMap { id =>
        ProvenanceComponent.from(id, value).toOption
      }
    }
  }
}
