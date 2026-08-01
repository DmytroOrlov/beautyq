package leaderboard.search.beautyq.gen2.eval

import io.circe.Json
import leaderboard.search.gen2.eval.{AcceptedEvaluationBaseline, AggregateSection, MetricKeyScope, ProvenanceComponent}

sealed trait BeautyQAcceptedBaselineVerificationError {
  def code: String
  def observed: String
  def expected: String
}
object BeautyQAcceptedBaselineVerificationError {
  final case class Internal(code: String, observed: String, expected: String) extends BeautyQAcceptedBaselineVerificationError
}

final class BeautyQAcceptedBaselineVerificationCheck private (
  val code: String,
  val passed: Boolean,
  val observed: String,
  val expected: String,
) {
  def toJson: Json = Json.obj(
    "code" -> Json.fromString(code),
    "passed" -> Json.fromBoolean(passed),
    "observed" -> Json.fromString(observed),
    "expected" -> Json.fromString(expected),
  )
}
object BeautyQAcceptedBaselineVerificationCheck {
  private[eval] def create(code: String, passed: Boolean, observed: String, expected: String): BeautyQAcceptedBaselineVerificationCheck =
    new BeautyQAcceptedBaselineVerificationCheck(code, passed, observed, expected)
}

final class BeautyQAcceptedBaselineDelta private (
  val observationKey: String,
  val scope: MetricKeyScope,
  val canonical: BigDecimal,
  val candidate: BigDecimal,
  val delta: BigDecimal,
  val applicableCount: Int,
  val notApplicableCount: Int,
) {
  private def decimal(value: BigDecimal): String = value.setScale(12, BigDecimal.RoundingMode.HALF_UP).bigDecimal.toPlainString

  def toJson: Json = Json.obj(
    "observationKey" -> Json.fromString(observationKey),
    "surface" -> Json.fromString(scope.surfaceId.value),
    "metric" -> Json.fromString(scope.metricId.value),
    "cutoff" -> Json.fromInt(scope.cutoff.value),
    "canonical" -> Json.fromString(decimal(canonical)),
    "candidate" -> Json.fromString(decimal(candidate)),
    "delta" -> Json.fromString(decimal(delta)),
    "applicableCount" -> Json.fromInt(applicableCount),
    "notApplicableCount" -> Json.fromInt(notApplicableCount),
  )
}
object BeautyQAcceptedBaselineDelta {
  private[eval] def create(
    observationKey: String,
    scope: MetricKeyScope,
    canonical: BigDecimal,
    candidate: BigDecimal,
    delta: BigDecimal,
    applicableCount: Int,
    notApplicableCount: Int,
  ): BeautyQAcceptedBaselineDelta = new BeautyQAcceptedBaselineDelta(
    observationKey, scope, canonical, candidate, delta, applicableCount, notApplicableCount,
  )
}

final class BeautyQAcceptedBaselineVerificationResult private (
  val passed: Boolean,
  val candidateManifestDigest: String,
  val canonicalManifestDigest: String,
  val candidateApplicationRevision: String,
  val canonicalApplicationRevision: String,
  val protectedCorpusFingerprint: String,
  val evaluationPolicyVersion: String,
  val protectedPolicyFingerprint: String,
  val checks: Vector[BeautyQAcceptedBaselineVerificationCheck],
  val orderedDeltas: Vector[BeautyQAcceptedBaselineDelta],
) {
  def toJson: Json = Json.obj(
    "schemaVersion" -> Json.fromString(BeautyQAcceptedBaselineVerificationResult.CurrentSchemaVersion),
    "passed" -> Json.fromBoolean(passed),
    "candidateManifestDigest" -> Json.fromString(candidateManifestDigest),
    "canonicalManifestDigest" -> Json.fromString(canonicalManifestDigest),
    "candidateApplicationRevision" -> Json.fromString(candidateApplicationRevision),
    "canonicalApplicationRevision" -> Json.fromString(canonicalApplicationRevision),
    "protectedCorpusFingerprint" -> Json.fromString(protectedCorpusFingerprint),
    "evaluationPolicyVersion" -> Json.fromString(evaluationPolicyVersion),
    "protectedPolicyFingerprint" -> Json.fromString(protectedPolicyFingerprint),
    "checks" -> Json.fromValues(checks.map(_.toJson)),
    "orderedDeltas" -> Json.fromValues(orderedDeltas.map(_.toJson)),
  )
}
object BeautyQAcceptedBaselineVerificationResult {
  val CurrentSchemaVersion = "beautyq-accepted-baseline-verification-v1"
  private[eval] def create(
    passed: Boolean,
    candidateManifestDigest: String,
    canonicalManifestDigest: String,
    candidateApplicationRevision: String,
    canonicalApplicationRevision: String,
    protectedCorpusFingerprint: String,
    evaluationPolicyVersion: String,
    protectedPolicyFingerprint: String,
    checks: Vector[BeautyQAcceptedBaselineVerificationCheck],
    orderedDeltas: Vector[BeautyQAcceptedBaselineDelta],
  ): BeautyQAcceptedBaselineVerificationResult = new BeautyQAcceptedBaselineVerificationResult(
    passed, candidateManifestDigest, canonicalManifestDigest, candidateApplicationRevision,
    canonicalApplicationRevision, protectedCorpusFingerprint, evaluationPolicyVersion,
    protectedPolicyFingerprint, checks, orderedDeltas,
  )
}

object BeautyQAcceptedBaselineVerifier {
  private val StableProvenance = Vector(
    "visible-corpus-fingerprint",
    "protected-corpus-fingerprint",
    "protected-policy-fingerprint",
    "source-content-fingerprint",
    "projected-documents-fingerprint",
    "embedding-provider",
    "embedding-model",
    "embedding-revision",
    "embedding-dimension",
    "embedding-text-format-version",
    "elasticsearch-version",
    "qdrant-version",
  )

  private val RunSpecificProvenance = Vector(
    "application-revision",
    "application-revision-source",
    "elasticsearch-generation-reference",
    "qdrant-generation-id",
    "visible-report-digest",
    "protected-report-digest",
  )

  def verify(
    candidate: AcceptedEvaluationBaseline,
    canonical: AcceptedEvaluationBaseline,
    policy: BeautyQProtectedAcceptancePolicy,
  ): Either[BeautyQAcceptedBaselineVerificationError, BeautyQAcceptedBaselineVerificationResult] = {
    val candidateDigest = BeautyQAcceptedEvaluationBaseline.candidateDigest(candidate)
    val canonicalDigest = BeautyQAcceptedEvaluationBaseline.candidateDigest(canonical)
    val checks = Vector(
      equalityCheck("schema-version", candidate.schemaVersion, canonical.schemaVersion),
      equalityCheck("corpus-fingerprint", candidate.corpusFingerprint, canonical.corpusFingerprint),
      equalityCheck("metric-schema-version", candidate.metricSchemaVersion, canonical.metricSchemaVersion),
      equalityCheck("evaluation-policy-version", candidate.evaluationPolicyVersion, canonical.evaluationPolicyVersion),
      equalityCheck("policy-evaluation-version", candidate.evaluationPolicyVersion, policy.evaluationPolicyVersion),
      candidateRevisionCheck(candidate),
      candidateRevisionSourceCheck(candidate),
      policyFingerprintCheck(candidate, policy),
    ) ++ stableProvenanceChecks(candidate.provenanceComponents, canonical.provenanceComponents) ++
      runSpecificPresenceChecks(candidate.provenanceComponents, canonical.provenanceComponents) ++
      aggregateChecks(candidate.aggregateObservations, canonical.aggregateObservations, policy)

    val deltas = if (deltaCompatible(checks, candidate.aggregateObservations, canonical.aggregateObservations))
      computeDeltas(candidate.aggregateObservations, canonical.aggregateObservations)
    else Vector.empty

    val result = BeautyQAcceptedBaselineVerificationResult.create(
      passed = checks.forall(_.passed),
      candidateManifestDigest = candidateDigest,
      canonicalManifestDigest = canonicalDigest,
      candidateApplicationRevision = candidate.applicationRevision,
      canonicalApplicationRevision = canonical.applicationRevision,
      protectedCorpusFingerprint = candidate.corpusFingerprint,
      evaluationPolicyVersion = candidate.evaluationPolicyVersion,
      protectedPolicyFingerprint = exactOne(candidate.provenanceComponents, "protected-policy-fingerprint").getOrElse("missing"),
      checks = checks,
      orderedDeltas = deltas,
    )
    Right(result)
  }

  private def equalityCheck(code: String, observed: String, expected: String): BeautyQAcceptedBaselineVerificationCheck =
    BeautyQAcceptedBaselineVerificationCheck.create(code, observed == expected, observed, expected)

  private def candidateRevisionCheck(candidate: AcceptedEvaluationBaseline): BeautyQAcceptedBaselineVerificationCheck =
    BeautyQAcceptedBaselineVerificationCheck.create(
      "candidate-application-revision",
      candidate.applicationRevision.nonEmpty && candidate.applicationRevision != "working-tree",
      safeValue(candidate.applicationRevision),
      "non-working-tree-committed-revision",
    )

  private def candidateRevisionSourceCheck(candidate: AcceptedEvaluationBaseline): BeautyQAcceptedBaselineVerificationCheck = {
    val observed = exactOne(candidate.provenanceComponents, "application-revision-source").getOrElse("missing")
    BeautyQAcceptedBaselineVerificationCheck.create("candidate-application-revision-source", observed == "system-property", observed, "system-property")
  }

  private def policyFingerprintCheck(
    candidate: AcceptedEvaluationBaseline,
    policy: BeautyQProtectedAcceptancePolicy,
  ): BeautyQAcceptedBaselineVerificationCheck = {
    val observed = exactOne(candidate.provenanceComponents, "protected-policy-fingerprint").getOrElse("missing")
    BeautyQAcceptedBaselineVerificationCheck.create("protected-policy-fingerprint", observed == policy.fingerprint && isDigest(observed), safeValue(observed), "policy-fingerprint")
  }

  private def stableProvenanceChecks(
    candidate: Vector[ProvenanceComponent],
    canonical: Vector[ProvenanceComponent],
  ): Vector[BeautyQAcceptedBaselineVerificationCheck] = StableProvenance.map { id =>
    val candidateValues = values(candidate, id)
    val canonicalValues = values(canonical, id)
    val validCandidate = candidateValues.size == 1 && validProvenanceValue(id, candidateValues.headOption.getOrElse(""))
    val validCanonical = canonicalValues.size == 1 && validProvenanceValue(id, canonicalValues.headOption.getOrElse(""))
    val equal = validCandidate && validCanonical && candidateValues == canonicalValues
    BeautyQAcceptedBaselineVerificationCheck.create(
      s"stable-provenance-$id",
      equal,
      provenanceState(candidateValues, validCandidate),
      provenanceState(canonicalValues, validCanonical),
    )
  }

  private def runSpecificPresenceChecks(
    candidate: Vector[ProvenanceComponent],
    canonical: Vector[ProvenanceComponent],
  ): Vector[BeautyQAcceptedBaselineVerificationCheck] = RunSpecificProvenance.map { id =>
    val candidateValues = values(candidate, id)
    val canonicalValues = values(canonical, id)
    val validCandidate = candidateValues.size == 1 && validRunSpecificValue(id, candidateValues.headOption.getOrElse(""))
    val validCanonical = canonicalValues.size == 1 && validRunSpecificValue(id, canonicalValues.headOption.getOrElse(""))
    BeautyQAcceptedBaselineVerificationCheck.create(
      s"run-provenance-$id",
      validCandidate && validCanonical,
      provenanceState(candidateValues, validCandidate),
      provenanceState(canonicalValues, validCanonical),
    )
  }

  private def aggregateChecks(
    candidate: Vector[(String, AggregateSection)],
    canonical: Vector[(String, AggregateSection)],
    policy: BeautyQProtectedAcceptancePolicy,
  ): Vector[BeautyQAcceptedBaselineVerificationCheck] = {
    val candidateKeys = candidate.map(_._1)
    val canonicalKeys = canonical.map(_._1)
    val policyKeys = policy.requiredMetricMinimums.map(_.observationKey).foldLeft(Vector.empty[String]) { (seen, key) =>
      if (seen.contains(key)) seen else seen :+ key
    }
    val missing = canonicalKeys.filterNot(candidateKeys.contains)
    val unexpected = candidateKeys.filterNot(canonicalKeys.contains)
    val policyMissing = policyKeys.filterNot(candidateKeys.contains)
    val policyUnexpected = candidateKeys.filterNot(policyKeys.contains)
    val base = Vector(
      equalityCheck("aggregate-key-order", candidateKeys.mkString("\u0000"), canonicalKeys.mkString("\u0000")),
      equalityCheck("policy-aggregate-key-order", candidateKeys.mkString("\u0000"), policyKeys.mkString("\u0000")),
      equalityCheck("missing-aggregate-keys", missing.mkString("\u0000"), ""),
      equalityCheck("unexpected-aggregate-keys", unexpected.mkString("\u0000"), ""),
      equalityCheck("missing-policy-aggregate-keys", policyMissing.mkString("\u0000"), ""),
      equalityCheck("unexpected-policy-aggregate-keys", policyUnexpected.mkString("\u0000"), ""),
    )
    val paired = candidate.zip(canonical).zipWithIndex.flatMap { case (((candidateKey, candidateSection), (canonicalKey, canonicalSection)), index) =>
      val key = if (candidateKey.nonEmpty) candidateKey else canonicalKey
      val scopes = candidateSection.metricObservations.map(_.scope)
      val canonicalScopes = canonicalSection.metricObservations.map(_.scope)
      val scopeChecks = Vector(
        equalityCheck(s"metric-scope-order-$index", renderScopes(scopes), renderScopes(canonicalScopes)),
        equalityCheck(s"missing-metric-scopes-$index", renderScopes(canonicalScopes.filterNot(scopes.contains)), ""),
        equalityCheck(s"unexpected-metric-scopes-$index", renderScopes(scopes.filterNot(canonicalScopes.contains)), ""),
        equalityCheck(s"quality-counts-$key", renderQualityCounts(candidateSection), renderQualityCounts(canonicalSection)),
        equalityCheck(s"metric-section-counts-$key", renderMetricCounts(candidateSection), renderMetricCounts(canonicalSection)),
      )
      val metricChecks = candidateSection.metricObservations.zip(canonicalSection.metricObservations).zipWithIndex.flatMap { case ((candidateMetric, canonicalMetric), metricIndex) =>
        Vector(
          equalityCheck(s"applicable-count-$index-$metricIndex", candidateMetric.applicableCount.toString, canonicalMetric.applicableCount.toString),
          equalityCheck(s"not-applicable-count-$index-$metricIndex", candidateMetric.notApplicableCount.toString, canonicalMetric.notApplicableCount.toString),
        )
      }
      scopeChecks ++ metricChecks
    }
    base ++ paired
  }

  private def deltaCompatible(
    checks: Vector[BeautyQAcceptedBaselineVerificationCheck],
    candidate: Vector[(String, AggregateSection)],
    canonical: Vector[(String, AggregateSection)],
  ): Boolean = checks.forall(_.passed) && candidate.size == canonical.size && candidate.zip(canonical).forall {
    case ((candidateKey, candidateSection), (canonicalKey, canonicalSection)) =>
      candidateKey == canonicalKey && candidateSection.metricObservations.map(_.scope) == canonicalSection.metricObservations.map(_.scope)
  }

  private def computeDeltas(
    candidate: Vector[(String, AggregateSection)],
    canonical: Vector[(String, AggregateSection)],
  ): Vector[BeautyQAcceptedBaselineDelta] = candidate.zip(canonical).flatMap {
    case ((key, candidateSection), (_, canonicalSection)) => candidateSection.metricObservations.zip(canonicalSection.metricObservations).map {
      case (candidateMetric, canonicalMetric) =>
        val candidateValue = normalize(candidateMetric.average)
        val canonicalValue = normalize(canonicalMetric.average)
        BeautyQAcceptedBaselineDelta.create(
          key,
          candidateMetric.scope,
          canonicalValue,
          candidateValue,
          (candidateValue - canonicalValue).setScale(12, BigDecimal.RoundingMode.HALF_UP),
          candidateMetric.applicableCount,
          candidateMetric.notApplicableCount,
        )
    }
  }

  private def normalize(value: BigDecimal): BigDecimal = value.setScale(12, BigDecimal.RoundingMode.HALF_UP)

  private def values(components: Vector[ProvenanceComponent], id: String): Vector[String] =
    components.collect { case component if component.id.value == id => component.value }

  private def exactOne(components: Vector[ProvenanceComponent], id: String): Option[String] = values(components, id) match {
    case Vector(value) => Some(value)
    case _ => None
  }

  private def validProvenanceValue(id: String, value: String): Boolean =
    value.nonEmpty && value.trim == value && (!id.endsWith("fingerprint") && !id.endsWith("digest") || isDigest(value))

  private def validRunSpecificValue(id: String, value: String): Boolean =
    validProvenanceValue(id, value) && value != "missing" && (!id.endsWith("digest") || isDigest(value))

  private def isDigest(value: String): Boolean = value.matches("[0-9a-f]{64}")

  private def provenanceState(values: Vector[String], valid: Boolean): String = values match {
    case Vector(value) if valid => value
    case Vector(_) => "invalid"
    case Vector() => "missing"
    case _ => "duplicate"
  }

  private def safeValue(value: String): String = if (value.nonEmpty) value else "missing"

  private def renderScopes(scopes: Vector[MetricKeyScope]): String = scopes.map(scope => s"${scope.surfaceId.value}/${scope.metricId.value}/${scope.cutoff.value}").mkString("|")

  private def renderQualityCounts(section: AggregateSection): String =
    s"${section.structuralInvalidCount}/${section.duplicateIdentityCount}/${section.zeroResultCount}/${section.forbiddenHitCount}"

  private def renderMetricCounts(section: AggregateSection): String =
    s"${section.applicableMetricCount}/${section.notApplicableMetricCount}"
}
