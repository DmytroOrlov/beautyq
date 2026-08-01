package leaderboard.search.beautyq.gen2.eval

import io.circe.Json
import leaderboard.search.beautyq.gen2.contract.BeautySearchRequestGen2
import leaderboard.search.beautyq.gen2.wiring.*
import leaderboard.search.gen2.contract.{GeoPoint, PageRequest}
import leaderboard.search.gen2.eval.*

import java.time.Instant

sealed trait BeautyQEvaluationExecutionError
object BeautyQEvaluationExecutionError {
  final case class Corpus(error: CorpusLoadError) extends BeautyQEvaluationExecutionError
  final case class Startup(actualPolicy: String, actualMode: String) extends BeautyQEvaluationExecutionError
  final case class Environment(message: String) extends BeautyQEvaluationExecutionError
  final case class Request(caseId: String, query: String, message: String) extends BeautyQEvaluationExecutionError
  final case class Application(caseId: String, query: String, pass: String, error: BeautyQSearchApplicationError)
      extends BeautyQEvaluationExecutionError
  final case class Projection(caseId: String, query: String, pass: String, error: BeautyQSearchResponseProjectionError)
      extends BeautyQEvaluationExecutionError
  final case class Evidence(caseId: String, query: String, pass: String, error: BeautyQNoHarmSupplementEvidence.EvidenceError)
      extends BeautyQEvaluationExecutionError
  final case class Identity(caseId: String, query: String, pass: String, surface: String, value: String, message: String)
      extends BeautyQEvaluationExecutionError
  final case class RankingInput(caseId: String, query: String, pass: String, surface: String, message: String)
      extends BeautyQEvaluationExecutionError
  final case class ReportInput(caseId: String, query: String, pass: String, message: String)
      extends BeautyQEvaluationExecutionError
  final case class InvalidDuration(caseId: String, query: String, pass: String, nanos: Long)
      extends BeautyQEvaluationExecutionError
  final case class NonDeterministicObservation(
    caseId: String,
    query: String,
    component: String,
    expectedPass: Int,
    actualPass: Int,
    expected: Vector[String],
    actual: Vector[String],
  ) extends BeautyQEvaluationExecutionError
  final case class Provenance(message: String) extends BeautyQEvaluationExecutionError
}

final class BeautyQEvaluationEnvironment private (
  val applicationRevision: String,
  val applicationRevisionSource: String,
  val snapshotCapturedAt: Instant,
  val sourceRevision: String,
  val elasticsearchVersion: String,
  val qdrantVersion: String,
  val osName: String,
  val osVersion: String,
  val osArch: String,
  val availableProcessors: Int,
  val javaVersion: String,
  val javaVendor: String,
)

object BeautyQEvaluationEnvironment {
  private val RevisionProperty = "search.gen2.eval.application-revision"

  def fromSystem(
    snapshotCapturedAt: Instant,
    sourceRevision: String,
    elasticsearchVersion: String,
    qdrantVersion: String,
  ): Either[String, BeautyQEvaluationEnvironment] = {
    val configuredRevision = Option(System.getProperty(RevisionProperty))
    val (revision, revisionSource) = configuredRevision match {
      case Some(value) if value.nonEmpty && value.trim == value => value -> "system-property"
      case Some(_) => return Left(s"$RevisionProperty must be non-empty and have no surrounding whitespace")
      case None => "working-tree" -> "working-tree-default"
    }
    val required = Vector(
      "sourceRevision" -> sourceRevision,
      "elasticsearchVersion" -> elasticsearchVersion,
      "qdrantVersion" -> qdrantVersion,
    )
    required.find { case (_, value) => value.isEmpty || value.trim != value } match {
      case Some((name, _)) => Left(s"$name must be non-empty and have no surrounding whitespace")
      case None =>
        Right(new BeautyQEvaluationEnvironment(
          revision,
          revisionSource,
          snapshotCapturedAt,
          sourceRevision,
          elasticsearchVersion,
          qdrantVersion,
          systemProperty("os.name"),
          systemProperty("os.version"),
          systemProperty("os.arch"),
          Runtime.getRuntime.availableProcessors(),
          systemProperty("java.version"),
          systemProperty("java.vendor"),
        ))
    }
  }

  private def systemProperty(name: String): String =
    Option(System.getProperty(name)).filter(_.nonEmpty).getOrElse("unknown")
}

object BeautyQEvaluationRequestFactory {
  def fromCase(
    corpus: BeautyQEvaluationCorpus,
    current: CorpusCase,
  ): Either[String, BeautySearchRequestGen2] =
    if (current.query.trim.isEmpty) Left(s"case ${current.caseId.value} query must be non-blank")
    else Right(BeautySearchRequestGen2(
      query = Some(current.query),
      filters = Vector.empty,
      requestedFacets = Vector.empty,
      sort = Vector.empty,
      page = PageRequest(cursor = None, size = BeautyQEvaluationPolicy.pageSize),
      userLocation = Some(GeoPoint(
        BigDecimal(corpus.defaultUserLocation.lat.toString),
        BigDecimal(corpus.defaultUserLocation.lon.toString),
      )),
    ))
}

final class BeautyQLatencySummary private (
  val sampleCount: Int,
  val minimum: Long,
  val p50: Long,
  val p95: Long,
  val maximum: Long,
)

object BeautyQLatencySummary {
  def from(samples: Vector[Long]): Either[String, BeautyQLatencySummary] =
    if (samples.isEmpty) Left("latency samples must be non-empty")
    else samples.find(_ < 0L) match {
      case Some(value) => Left(s"latency sample must be non-negative: $value")
      case None =>
        val sorted = samples.sorted
        Right(new BeautyQLatencySummary(
          sorted.size,
          sorted(0),
          percentile(sorted, 0.50),
          percentile(sorted, 0.95),
          sorted(sorted.size - 1),
        ))
    }

  private def percentile(sorted: Vector[Long], quantile: Double): Long = {
    val oneBased = java.lang.StrictMath.ceil(quantile * sorted.size.toDouble).toInt
    sorted(java.lang.Math.max(0, oneBased - 1))
  }
}

private[eval] final class BeautyQMeasuredCase private (
  val corpusCase: CorpusCase,
  val reportInput: EvaluationReportCaseInput,
  val signature: BeautyQMeasuredCase.Signature,
  val durationNanos: Long,
  val duplicateIdentityCount: Int,
  val forbiddenHitCount: Int,
  val requestDegraded: Boolean,
  val baselinePrefixPreserved: Boolean,
  val baselineOwnedComponentsPreserved: Boolean,
  val appendBudgetPreserved: Boolean,
  val scoreObservations: Vector[BeautyQSupplementScoreObservation],
)

final class BeautyQSupplementScoreObservation private[eval] (
  val caseId: String,
  val query: String,
  val resultId: String,
  val origin: String,
  val score: BigDecimal,
  val judgment: String,
  val supplementStatus: String,
  val degradationReason: Option[String],
  val baselineIds: Vector[String],
  val appendedIds: Vector[String],
)

private[eval] object BeautyQMeasuredCase {
  final case class Signature(
    variants: Vector[String],
    providers: Vector[String],
    serviceIntents: Vector[String],
    status: String,
    degradationReason: Vector[String],
    baselineIds: Vector[String],
    resultIds: Vector[String],
    appendedIds: Vector[String],
    baselineOwnedComponentsPreserved: Boolean,
  )

  def syntheticForGate(
    base: BeautyQMeasuredCase,
    signature: Option[Signature] = None,
    duplicateIdentityCount: Option[Int] = None,
    forbiddenHitCount: Option[Int] = None,
    requestDegraded: Option[Boolean] = None,
    baselinePrefixPreserved: Option[Boolean] = None,
    baselineOwnedComponentsPreserved: Option[Boolean] = None,
    appendBudgetPreserved: Option[Boolean] = None,
  ): BeautyQMeasuredCase =
    new BeautyQMeasuredCase(
      base.corpusCase,
      base.reportInput,
      signature.getOrElse(base.signature),
      base.durationNanos,
      duplicateIdentityCount.getOrElse(base.duplicateIdentityCount),
      forbiddenHitCount.getOrElse(base.forbiddenHitCount),
      requestDegraded.getOrElse(base.requestDegraded),
      baselinePrefixPreserved.getOrElse(base.baselinePrefixPreserved),
      baselineOwnedComponentsPreserved.getOrElse(base.baselineOwnedComponentsPreserved),
      appendBudgetPreserved.getOrElse(base.appendBudgetPreserved),
      base.scoreObservations,
    )

  def fromExecution(
    current: CorpusCase,
    result: BeautyQSearchOrchestrator.Result,
    response: BeautyQSearchResponseGen2,
    durationNanos: Long,
    pass: String,
  ): Either[BeautyQEvaluationExecutionError, BeautyQMeasuredCase] = {
    if (durationNanos < 0L)
      Left(BeautyQEvaluationExecutionError.InvalidDuration(current.caseId.value, current.query, pass, durationNanos))
    else for {
      evidence <- BeautyQNoHarmSupplementEvidence.fromExecution(result, response)
        .left.map(BeautyQEvaluationExecutionError.Evidence(current.caseId.value, current.query, pass, _))
      variants <- decodeIds(current, pass, BeautyQEvaluationPolicy.Variants, response.hits.map(_.id))
      providers <- decodeIds(current, pass, BeautyQEvaluationPolicy.Providers, response.providerCarousel.map(_.masterLocationId))
      serviceIntents <- decodeIds(current, pass, BeautyQEvaluationPolicy.ServiceIntents, response.serviceIntentCarousel.map(_.serviceId))
      variantResult <- ranking(current, pass, BeautyQEvaluationPolicy.Variants, current.variantJudgments, variants)
      providerResult <- ranking(current, pass, BeautyQEvaluationPolicy.Providers, current.providerJudgments, providers)
      serviceResult <- ranking(current, pass, BeautyQEvaluationPolicy.ServiceIntents, current.serviceIntentJudgments, serviceIntents)
      ordered = Vector(
        BeautyQEvaluationPolicy.Variants -> variantResult,
        BeautyQEvaluationPolicy.Providers -> providerResult,
        BeautyQEvaluationPolicy.ServiceIntents -> serviceResult,
      )
      _ <- Either.cond(
        ordered.map(_._1) == BeautyQEvaluationPolicy.activeSurfaces,
        (),
        BeautyQEvaluationExecutionError.ReportInput(current.caseId.value, current.query, pass, "surface order differs from BeautyQEvaluationPolicy.activeSurfaces"),
      )
      reportInput <- EvaluationReportCaseInput.from(
        current.caseId,
        current.partition,
        current.judgmentMode,
        Some(current.slices),
        ordered,
      ).left.map(BeautyQEvaluationExecutionError.ReportInput(current.caseId.value, current.query, pass, _))
    } yield {
      val results = ordered.map(_._2)
      val largestRows = results.flatMap(_.metricRows.maxByOption(_.cutoff.value))
      val baselineIds = evidence.baselineIds.map(_.value.toString)
      val returnedBaselinePrefix = evidence.resultIds.filter(baselineIds.contains)
      val prefixPreserved = baselineIds.distinct.forall(evidence.resultIds.contains) && returnedBaselinePrefix == baselineIds.distinct
      val forbidden = current.variantJudgments.forbiddenIds.map(_.value).toSet
      val acceptable = current.variantJudgments.acceptableIds.map(_.value).toSet
      val neutral = current.variantJudgments.neutralIds.map(_.value).toSet
      val appended = evidence.appendedIds.map(_.value.toString)
      val scoreObservations = response.hits.map { hit =>
        val judgment =
          if (forbidden.contains(hit.id)) "forbidden"
          else if (acceptable.contains(hit.id)) "acceptable"
          else if (neutral.contains(hit.id)) "neutral"
          else "unjudged"
        new BeautyQSupplementScoreObservation(
          current.caseId.value,
          current.query,
          hit.id,
          hit.origin.stableCode,
          hit.score,
          judgment,
          evidence.statusCode,
          evidence.degradationReason.map(_.reasonCode),
          baselineIds,
          appended,
        )
      }
      new BeautyQMeasuredCase(
        current,
        reportInput,
        Signature(
          variants.map(_.value),
          providers.map(_.value),
          serviceIntents.map(_.value),
          evidence.statusCode,
          evidence.degradationReason.map(_.reasonCode).toVector,
          baselineIds,
          evidence.resultIds,
          evidence.appendedIds.map(_.value.toString),
          evidence.baselineOwnedComponentsPreserved,
        ),
        durationNanos,
        results.map(_.duplicateIds.size).sum,
        largestRows.map(_.forbiddenHits.count).sum,
        evidence.degradationReason.nonEmpty || evidence.status == BeautyQSupplementStatus.SupplementFailed,
        prefixPreserved,
        evidence.baselineOwnedComponentsPreserved,
        evidence.appendedIds.size <= BeautyQSupplementPolicy.appendOnly.maxAppended,
        scoreObservations,
      )
    }
  }

  private def decodeIds(
    current: CorpusCase,
    pass: String,
    surface: EvaluationSurfaceId,
    values: Vector[String],
  ): Either[BeautyQEvaluationExecutionError, Vector[EvaluationResultId]] =
    values.foldLeft[Either[BeautyQEvaluationExecutionError, Vector[EvaluationResultId]]](Right(Vector.empty)) {
      case (acc, value) => acc.flatMap { done =>
        EvaluationResultId.from(value)
          .left.map(BeautyQEvaluationExecutionError.Identity(current.caseId.value, current.query, pass, surface.value, value, _))
          .map(done :+ _)
      }
    }

  private def ranking(
    current: CorpusCase,
    pass: String,
    surface: EvaluationSurfaceId,
    judgments: RankingJudgments,
    ids: Vector[EvaluationResultId],
  ): Either[BeautyQEvaluationExecutionError, RankingEvaluationResult] =
    RankingEvaluationInput.from(
      current.caseId,
      current.partition,
      surface,
      current.slices,
      judgments,
      ids,
      BeautyQEvaluationPolicy.cutoffs,
    ).left.map(BeautyQEvaluationExecutionError.RankingInput(current.caseId.value, current.query, pass, surface.value, _))
      .map(RankingEvaluator.evaluate)
}

final class BeautyQMeasuredEvaluationResult private[BeautyQMeasuredEvaluationResult] (
  val report: EvaluationReport,
  val detailedJson: Json,
  val protectedReportJson: Json,
  val protectedReportDigest: String,
  val reportDigest: String,
  val measurementJson: Json,
  val scoreSeparationJson: Json,
  val correctionGate: BeautyQEvaluationCorrectionGateResult,
  val warmupExecutions: Int,
  val measuredExecutions: Int,
  val evaluationPolicyVersion: String,
)
object BeautyQMeasuredEvaluationResult {
  private[eval] def create(
    report: EvaluationReport,
    detailedJson: Json,
    protectedReportJson: Json,
    protectedReportDigest: String,
    reportDigest: String,
    measurementJson: Json,
    scoreSeparationJson: Json,
    correctionGate: BeautyQEvaluationCorrectionGateResult,
    warmupExecutions: Int,
    measuredExecutions: Int,
    evaluationPolicyVersion: String,
  ): BeautyQMeasuredEvaluationResult =
    new BeautyQMeasuredEvaluationResult(
      report,
      detailedJson,
      protectedReportJson,
      protectedReportDigest,
      reportDigest,
      measurementJson,
      scoreSeparationJson,
      correctionGate,
      warmupExecutions,
      measuredExecutions,
      evaluationPolicyVersion,
    )
}

object BeautyQMeasuredEvaluation {
  def executeCanonical(
    application: BeautyQSearchApplication,
    startupStatus: StartupServingStatus,
    environment: BeautyQEvaluationEnvironment,
  ): Either[BeautyQEvaluationExecutionError, BeautyQMeasuredEvaluationResult] =
    for {
      corpus <- BeautyQEvaluationCorpus.loadCanonical().left.map(BeautyQEvaluationExecutionError.Corpus.apply)
      result <- executeCorpus(application, startupStatus, environment, corpus, BeautyQEvaluationPolicy.CurrentVersion)
    } yield result

  def executeProtected(
    application: BeautyQSearchApplication,
    startupStatus: StartupServingStatus,
    environment: BeautyQEvaluationEnvironment,
    corpus: BeautyQEvaluationCorpus,
    evaluationPolicyVersion: String,
  ): Either[BeautyQEvaluationExecutionError, BeautyQMeasuredEvaluationResult] =
    executeCorpus(application, startupStatus, environment, corpus, evaluationPolicyVersion)

  private def executeCorpus(
    application: BeautyQSearchApplication,
    startupStatus: StartupServingStatus,
    environment: BeautyQEvaluationEnvironment,
    corpus: BeautyQEvaluationCorpus,
    evaluationPolicyVersion: String,
  ): Either[BeautyQEvaluationExecutionError, BeautyQMeasuredEvaluationResult] =
    for {
      _ <- validateStartup(startupStatus)
      warmup <- executePass(application, startupStatus, corpus, "warmup[1]")
      measured <- (1 to BeautyQEvaluationPolicy.measuredPasses).toVector.foldLeft[
        Either[BeautyQEvaluationExecutionError, Vector[Vector[BeautyQMeasuredCase]]]
      ](Right(Vector.empty)) { (acc, passIndex) =>
        acc.flatMap(done => executePass(application, startupStatus, corpus, s"measured[$passIndex]").map(done :+ _))
      }
      first <- measured.headOption.toRight(BeautyQEvaluationExecutionError.Environment("measured passes must be non-empty"))
      _ <- validateMeasuredDeterminism(first, measured)
      provenance <- provenanceComponents(corpus, startupStatus, environment, evaluationPolicyVersion)
      report = EvaluationReportBuilder.build(provenance, first.map(_.reportInput))
      detailed = EvaluationReport.encodeDetailed(report)
      protectedReport = EvaluationReport.encodeProtected(report)
      protectedDigest = EvaluationReportDigest.compute(protectedReport)
      digest = EvaluationReportDigest.compute(detailed)
      gate = BeautyQEvaluationCorrectionGate.evaluate(
        startupStatus.policy,
        startupStatus.servingMode,
        corpus.cases.size,
        warmup,
        measured,
        deterministic = true,
      )
      measurement <- measurementJson(corpus, startupStatus, environment, first, measured, digest)
      scoreSeparation = BeautyQScoreSeparationArtifact.encode(corpus, first)
    } yield BeautyQMeasuredEvaluationResult.create(
      report,
      detailed,
      protectedReport,
      protectedDigest,
      digest,
      measurement,
      scoreSeparation,
      gate,
      warmup.size,
      measured.map(_.size).sum,
      evaluationPolicyVersion,
    )

  private def validateStartup(status: StartupServingStatus): Either[BeautyQEvaluationExecutionError, Unit] =
    if (
      status.policy == SupplementStartupPolicy.Required &&
      status.servingMode == BeautyQServingMode.FullSearch &&
      status.supplementReady &&
      !status.restartRequired
    ) Right(())
    else Left(BeautyQEvaluationExecutionError.Startup(status.policy.stableCode, status.servingMode.modeCode))

  private def executePass(
    application: BeautyQSearchApplication,
    startupStatus: StartupServingStatus,
    corpus: BeautyQEvaluationCorpus,
    pass: String,
  ): Either[BeautyQEvaluationExecutionError, Vector[BeautyQMeasuredCase]] =
    corpus.cases.foldLeft[Either[BeautyQEvaluationExecutionError, Vector[BeautyQMeasuredCase]]](Right(Vector.empty)) {
      case (acc, current) => acc.flatMap { done =>
        for {
          request <- BeautyQEvaluationRequestFactory.fromCase(corpus, current)
            .left.map(BeautyQEvaluationExecutionError.Request(current.caseId.value, current.query, _))
          started = System.nanoTime()
          resultEither = application.execute(request)
          finished = System.nanoTime()
          result <- resultEither.left.map(BeautyQEvaluationExecutionError.Application(current.caseId.value, current.query, pass, _))
          response <- BeautyQSearchResponseGen2Projector.project(result, startupStatus)
            .left.map(BeautyQEvaluationExecutionError.Projection(current.caseId.value, current.query, pass, _))
          observed <- BeautyQMeasuredCase.fromExecution(current, result, response, finished - started, pass)
        } yield done :+ observed
      }
    }

  private[eval] def validateMeasuredDeterminism(
    expected: Vector[BeautyQMeasuredCase],
    measured: Vector[Vector[BeautyQMeasuredCase]],
  ): Either[BeautyQEvaluationExecutionError, Unit] =
    measured.zipWithIndex.drop(1).foldLeft[Either[BeautyQEvaluationExecutionError, Unit]](Right(())) {
      case (acc, (actualPass, zeroBasedIndex)) => acc.flatMap { _ =>
        expected.zip(actualPass).find { case (left, right) => left.signature != right.signature } match {
          case None if actualPass.size == expected.size => Right(())
          case None => Left(BeautyQEvaluationExecutionError.Environment(
            s"measured pass ${zeroBasedIndex + 1} size ${actualPass.size} differs from expected ${expected.size}"
          ))
          case Some((left, right)) =>
            val (component, expectedValue, actualValue) = firstDifference(left.signature, right.signature)
            Left(BeautyQEvaluationExecutionError.NonDeterministicObservation(
              left.corpusCase.caseId.value,
              left.corpusCase.query,
              component,
              1,
              zeroBasedIndex + 1,
              expectedValue,
              actualValue,
            ))
        }
      }
    }

  private def firstDifference(
    expected: BeautyQMeasuredCase.Signature,
    actual: BeautyQMeasuredCase.Signature,
  ): (String, Vector[String], Vector[String]) = {
    val candidates = Vector(
      ("variants", expected.variants, actual.variants),
      ("providers", expected.providers, actual.providers),
      ("service-intents", expected.serviceIntents, actual.serviceIntents),
      ("supplement-status", Vector(expected.status), Vector(actual.status)),
      ("degradation-reason", expected.degradationReason, actual.degradationReason),
      ("baseline-ids", expected.baselineIds, actual.baselineIds),
      ("result-ids", expected.resultIds, actual.resultIds),
      ("appended-ids", expected.appendedIds, actual.appendedIds),
      ("baseline-owned-components", Vector(expected.baselineOwnedComponentsPreserved.toString), Vector(actual.baselineOwnedComponentsPreserved.toString)),
    )
    candidates.find { case (_, left, right) => left != right }
      .getOrElse(("unknown", Vector.empty, Vector.empty))
  }

  private def provenanceComponents(
    corpus: BeautyQEvaluationCorpus,
    status: StartupServingStatus,
    environment: BeautyQEvaluationEnvironment,
    evaluationPolicyVersion: String,
  ): Either[BeautyQEvaluationExecutionError, Vector[ProvenanceComponent]] = {
    val model = BeautyQQdrantPolicy.policy.embeddingModel
    val raw = Vector(
      "corpus-fingerprint" -> corpus.corpusFingerprint,
      "source-content-fingerprint" -> status.sourceContentFingerprint,
      "projected-documents-fingerprint" -> status.projectedDocumentsFingerprint,
      "elasticsearch-generation-reference" -> status.elasticsearchReference,
      "qdrant-generation-id" -> status.qdrantGenerationId.getOrElse("missing"),
      "metric-schema-version" -> RankingEvaluator.MetricSchemaVersion,
      "evaluation-policy-version" -> evaluationPolicyVersion,
      "application-revision" -> environment.applicationRevision,
      "application-revision-source" -> environment.applicationRevisionSource,
      "embedding-provider" -> model.provider,
      "embedding-model" -> model.model,
      "embedding-revision" -> model.revision,
      "embedding-dimension" -> model.dimension.toString,
      "embedding-text-format-version" -> model.textFormatVersion,
      "elasticsearch-version" -> environment.elasticsearchVersion,
      "qdrant-version" -> environment.qdrantVersion,
    )
    raw.foldLeft[Either[BeautyQEvaluationExecutionError, Vector[ProvenanceComponent]]](Right(Vector.empty)) {
      case (acc, (idText, value)) => acc.flatMap { done =>
        for {
          id <- EvaluationProvenanceId.from(idText).left.map(BeautyQEvaluationExecutionError.Provenance.apply)
          component <- ProvenanceComponent.from(id, value).left.map(BeautyQEvaluationExecutionError.Provenance.apply)
        } yield done :+ component
      }
    }
  }

  private def measurementJson(
    corpus: BeautyQEvaluationCorpus,
    status: StartupServingStatus,
    environment: BeautyQEvaluationEnvironment,
    firstMeasured: Vector[BeautyQMeasuredCase],
    measured: Vector[Vector[BeautyQMeasuredCase]],
    digest: String,
  ): Either[BeautyQEvaluationExecutionError, Json] = {
    val allSamples = measured.flatten.map(_.durationNanos)
    val global = BeautyQLatencySummary.from(allSamples).left.map(BeautyQEvaluationExecutionError.Environment.apply)
    global.flatMap { globalSummary =>
      val visible = firstMeasured.filter(_.corpusCase.partition != EvaluationPartition.ProtectedHoldout)
      val perCase = visible.foldLeft[Either[BeautyQEvaluationExecutionError, Vector[Json]]](Right(Vector.empty)) {
        case (acc, current) => acc.flatMap { done =>
          val samples = measured.flatMap(_.find(_.corpusCase.caseId == current.corpusCase.caseId).map(_.durationNanos))
          BeautyQLatencySummary.from(samples).left.map(BeautyQEvaluationExecutionError.Environment.apply)
            .map(summary => done :+ Json.obj(
              "caseId" -> Json.fromString(current.corpusCase.caseId.value),
              "sampleCount" -> Json.fromInt(summary.sampleCount),
              "minimum" -> Json.fromLong(summary.minimum),
              "p50" -> Json.fromLong(summary.p50),
              "p95" -> Json.fromLong(summary.p95),
              "maximum" -> Json.fromLong(summary.maximum),
            ))
        }
      }
      perCase.flatMap { visibleJson =>
        val visibleScoreObservations = visible.flatMap(_.scoreObservations)
        val protectedSamples = measured.flatten
          .filter(_.corpusCase.partition == EvaluationPartition.ProtectedHoldout)
          .map(_.durationNanos)
        val protectedJson = if (protectedSamples.isEmpty) Right(Json.Null)
        else BeautyQLatencySummary.from(protectedSamples).left.map(BeautyQEvaluationExecutionError.Environment.apply)
          .map(latencyJson)
        protectedJson.map { protectedValue =>
          val partitionCounts = corpus.cases.map(_.partition).distinct.map { partition =>
            Json.obj(
              "partition" -> Json.fromString(partition.stableCode),
              "count" -> Json.fromInt(corpus.cases.count(_.partition == partition)),
            )
          }
          val model = BeautyQQdrantPolicy.policy.embeddingModel
          Json.obj(
            "schemaVersion" -> Json.fromString("beautyq-evaluation-measurement-v1"),
            "corpus" -> Json.obj(
              "id" -> Json.fromString(corpus.corpusId),
              "version" -> Json.fromInt(corpus.version),
              "fingerprint" -> Json.fromString(corpus.corpusFingerprint),
              "caseCount" -> Json.fromInt(corpus.cases.size),
              "partitionCounts" -> Json.fromValues(partitionCounts),
            ),
            "executionProfile" -> Json.obj(
              "warmupPasses" -> Json.fromInt(BeautyQEvaluationPolicy.warmupPasses),
              "measuredPasses" -> Json.fromInt(BeautyQEvaluationPolicy.measuredPasses),
              "concurrency" -> Json.fromInt(BeautyQEvaluationPolicy.concurrency),
              "warmupExecutions" -> Json.fromInt(corpus.cases.size * BeautyQEvaluationPolicy.warmupPasses),
              "measuredExecutions" -> Json.fromInt(allSamples.size),
              "timingScope" -> Json.fromString("beautyq_search_application_execute"),
              "startupIncluded" -> Json.fromBoolean(false),
              "materializationIncluded" -> Json.fromBoolean(false),
              "projectionIncluded" -> Json.fromBoolean(false),
            ),
            "latencyNanos" -> latencyJson(globalSummary),
            "visibleCaseLatency" -> Json.fromValues(visibleJson),
            "protectedLatency" -> protectedValue,
            "scoreEvidence" -> Json.obj(
              "observations" -> Json.fromValues(visibleScoreObservations.map(scoreObservationJson)),
              "supplementSeparation" -> BeautyQSupplementScoreSeparation.from(visibleScoreObservations).toJson,
            ),
            "snapshot" -> Json.obj(
              "capturedAt" -> Json.fromString(environment.snapshotCapturedAt.toString),
              "sourceRevision" -> Json.fromString(environment.sourceRevision),
              "sourceContentFingerprint" -> Json.fromString(status.sourceContentFingerprint),
              "projectedDocumentsFingerprint" -> Json.fromString(status.projectedDocumentsFingerprint),
              "elasticsearchGenerationReference" -> Json.fromString(status.elasticsearchReference),
              "qdrantGenerationId" -> status.qdrantGenerationId.fold(Json.Null)(Json.fromString),
            ),
            "backends" -> Json.obj(
              "elasticsearchVersion" -> Json.fromString(environment.elasticsearchVersion),
              "qdrantVersion" -> Json.fromString(environment.qdrantVersion),
              "embeddingProvider" -> Json.fromString(model.provider),
              "embeddingModel" -> Json.fromString(model.model),
              "embeddingRevision" -> Json.fromString(model.revision),
              "embeddingDimension" -> Json.fromInt(model.dimension),
              "embeddingTextFormatVersion" -> Json.fromString(model.textFormatVersion),
            ),
            "environment" -> Json.obj(
              "applicationRevision" -> Json.fromString(environment.applicationRevision),
              "applicationRevisionSource" -> Json.fromString(environment.applicationRevisionSource),
              "osName" -> Json.fromString(environment.osName),
              "osVersion" -> Json.fromString(environment.osVersion),
              "osArch" -> Json.fromString(environment.osArch),
              "availableProcessors" -> Json.fromInt(environment.availableProcessors),
              "javaVersion" -> Json.fromString(environment.javaVersion),
              "javaVendor" -> Json.fromString(environment.javaVendor),
            ),
            "qualityReportDigest" -> Json.fromString(digest),
          )
        }
      }
    }
  }

  private def latencyJson(summary: BeautyQLatencySummary): Json = Json.obj(
    "sampleCount" -> Json.fromInt(summary.sampleCount),
    "minimum" -> Json.fromLong(summary.minimum),
    "p50" -> Json.fromLong(summary.p50),
    "p95" -> Json.fromLong(summary.p95),
    "maximum" -> Json.fromLong(summary.maximum),
  )

  private def scoreObservationJson(value: BeautyQSupplementScoreObservation): Json = Json.obj(
    "caseId" -> Json.fromString(value.caseId),
    "query" -> Json.fromString(value.query),
    "resultId" -> Json.fromString(value.resultId),
    "origin" -> Json.fromString(value.origin),
    "score" -> Json.fromBigDecimal(value.score),
    "judgment" -> Json.fromString(value.judgment),
    "supplementStatus" -> Json.fromString(value.supplementStatus),
    "degradationReason" -> value.degradationReason.fold(Json.Null)(Json.fromString),
    "baselineIds" -> Json.fromValues(value.baselineIds.map(Json.fromString)),
    "appendedIds" -> Json.fromValues(value.appendedIds.map(Json.fromString)),
  )

}

/** Strict, redacted score-separation artifact owned by the BeautyQ evaluation runner.
  * Visible cases retain ordered hit evidence; protected cases contribute only aggregate ranges.
  */
object BeautyQScoreSeparationArtifact {
  private val SchemaVersion = "beautyq-evaluation-score-separation-v1"

  private def observationJson(value: BeautyQSupplementScoreObservation): Json = Json.obj(
    "resultId" -> Json.fromString(value.resultId),
    "origin" -> Json.fromString(value.origin),
    "score" -> Json.fromBigDecimal(value.score),
    "judgment" -> Json.fromString(value.judgment),
    "supplementStatus" -> Json.fromString(value.supplementStatus),
    "degradationReason" -> value.degradationReason.fold(Json.Null)(Json.fromString),
    "baselineIds" -> Json.fromValues(value.baselineIds.map(Json.fromString)),
    "appendedIds" -> Json.fromValues(value.appendedIds.map(Json.fromString)),
  )

  private def aggregateJson(values: Vector[BeautyQSupplementScoreObservation]): Json = {
    val supplement = values.filter(_.origin == "qdrant_supplement")
    val forbidden = supplement.filter(_.judgment == "forbidden").map(_.score)
    val nonForbidden = supplement.filter(value => Set("acceptable", "neutral", "unjudged").contains(value.judgment)).map(_.score)
    Json.obj(
      "forbiddenCount" -> Json.fromInt(forbidden.size),
      "nonForbiddenCount" -> Json.fromInt(nonForbidden.size),
      "minimumForbidden" -> forbidden.minOption.fold(Json.Null)(Json.fromBigDecimal),
      "maximumForbidden" -> forbidden.maxOption.fold(Json.Null)(Json.fromBigDecimal),
      "minimumNonForbidden" -> nonForbidden.minOption.fold(Json.Null)(Json.fromBigDecimal),
      "maximumNonForbidden" -> nonForbidden.maxOption.fold(Json.Null)(Json.fromBigDecimal),
    )
  }

  private[eval] def encode(corpus: BeautyQEvaluationCorpus, measured: Vector[BeautyQMeasuredCase]): Json = {
    val byCase = measured.map(value => value.corpusCase.caseId -> value).toMap
    val visibleCases = corpus.cases.filter(_.partition != EvaluationPartition.ProtectedHoldout).flatMap { current =>
      byCase.get(current.caseId).map { observed =>
        Json.obj(
          "caseId" -> Json.fromString(current.caseId.value),
          "query" -> Json.fromString(current.query),
          "observations" -> Json.fromValues(observed.scoreObservations.map(observationJson)),
        )
      }
    }
    val protectedValues = measured
      .filter(_.corpusCase.partition == EvaluationPartition.ProtectedHoldout)
      .flatMap(_.scoreObservations)
    val separation = BeautyQSupplementScoreSeparation.from(measured.flatMap(_.scoreObservations))
    Json.obj(
      "schemaVersion" -> Json.fromString(SchemaVersion),
      "visibleCases" -> Json.fromValues(visibleCases),
      "protectedAggregate" -> (if (protectedValues.isEmpty) Json.Null else aggregateJson(protectedValues)),
      "supplementSeparation" -> Json.obj(
        "maximumForbiddenSupplementScore" -> separation.maximumForbidden.fold(Json.Null)(Json.fromBigDecimal),
        "minimumNonForbiddenSupplementScore" -> separation.minimumNonForbidden.fold(Json.Null)(Json.fromBigDecimal),
        "separable" -> Json.fromBoolean(separation.strictlySeparable),
        "proposedThreshold" -> separation.candidateThreshold.fold(Json.Null)(Json.fromBigDecimal),
      ),
    )
  }
}
