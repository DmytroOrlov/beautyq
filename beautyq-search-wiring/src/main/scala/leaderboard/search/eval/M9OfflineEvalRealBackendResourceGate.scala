package leaderboard.search.eval

enum M9OfflineEvalRealBackendKind {
  case Es
  case Qdrant

  def executionMode: M9OfflineEvalBackendExecutionMode =
    this match {
      case M9OfflineEvalRealBackendKind.Es     => M9OfflineEvalBackendExecutionMode.EsOnlyOffline
      case M9OfflineEvalRealBackendKind.Qdrant => M9OfflineEvalBackendExecutionMode.QdrantOnlyOffline
    }

  def candidateSource: CandidateSource =
    this match {
      case M9OfflineEvalRealBackendKind.Es     => CandidateSource.Es
      case M9OfflineEvalRealBackendKind.Qdrant => CandidateSource.Qdrant
    }

  def render: String =
    this match {
      case M9OfflineEvalRealBackendKind.Es     => "es"
      case M9OfflineEvalRealBackendKind.Qdrant => "qdrant"
    }
}

enum M9OfflineEvalRealBackendRequestIdPolicy {
  case UsePlanRequestId
  case DeriveFromQueryId

  def render: String =
    this match {
      case M9OfflineEvalRealBackendRequestIdPolicy.UsePlanRequestId  => "use_plan_request_id"
      case M9OfflineEvalRealBackendRequestIdPolicy.DeriveFromQueryId => "derive_from_query_id"
    }
}

final case class M9OfflineEvalRealBackendResourceConfig(
  resourceName: String,
  connectionTarget: String,
  resourceIdentity: Option[String],
  connectionTimeoutMillis: Long,
)

object M9OfflineEvalRealBackendResourceConfig {
  def validate(config: M9OfflineEvalRealBackendResourceConfig): List[String] =
    List(
      Option.when(config.resourceName.trim.isEmpty)("backend resource name is required"),
      Option.when(config.connectionTarget.trim.isEmpty)("backend resource connection target is required"),
      Option.when(config.connectionTimeoutMillis <= 0)("backend resource connection timeout budget must be positive"),
    ).flatten
}

enum M9OfflineEvalRealBackendResourceGateStatus {
  case Disabled
  case Denied
  case AllowedWithoutResourceConfig
  case AllowedForConfiguredResource

  def render: String =
    this match {
      case M9OfflineEvalRealBackendResourceGateStatus.Disabled                     => "disabled"
      case M9OfflineEvalRealBackendResourceGateStatus.Denied                       => "denied"
      case M9OfflineEvalRealBackendResourceGateStatus.AllowedWithoutResourceConfig => "allowed_without_resource_config"
      case M9OfflineEvalRealBackendResourceGateStatus.AllowedForConfiguredResource => "allowed_for_configured_resource"
    }

  def allowsAdapterRun: Boolean =
    this match {
      case M9OfflineEvalRealBackendResourceGateStatus.AllowedWithoutResourceConfig |
          M9OfflineEvalRealBackendResourceGateStatus.AllowedForConfiguredResource =>
        true
      case M9OfflineEvalRealBackendResourceGateStatus.Disabled |
          M9OfflineEvalRealBackendResourceGateStatus.Denied =>
        false
    }

  def permitsRealBackendCall: Boolean =
    this == M9OfflineEvalRealBackendResourceGateStatus.AllowedForConfiguredResource
}

final case class M9OfflineEvalRealBackendResourceGateDecision(
  status: M9OfflineEvalRealBackendResourceGateStatus,
  reasons: List[String],
  warnings: List[String],
) {
  def allowsAdapterRun: Boolean = status.allowsAdapterRun

  def permitsRealBackendCall: Boolean = status.permitsRealBackendCall
}

final case class M9OfflineEvalRealBackendResourceGate(
  runRealBackendOfflineEval: Boolean,
) {
  def decide(input: M9OfflineEvalRealBackendSpikeInput): M9OfflineEvalRealBackendResourceGateDecision =
    if (!runRealBackendOfflineEval) {
      M9OfflineEvalRealBackendResourceGateDecision(
        status = M9OfflineEvalRealBackendResourceGateStatus.Disabled,
        reasons = List("RUN_REAL_BACKEND_OFFLINE_EVAL=1 is required for real backend offline eval"),
        warnings = List("real backend offline eval disabled by default"),
      )
    } else {
      val requiredReasons = M9OfflineEvalRealBackendResourceGate.requiredInputReasons(input)
      requiredReasons match {
        case _ :: _ =>
          M9OfflineEvalRealBackendResourceGateDecision(
            status = M9OfflineEvalRealBackendResourceGateStatus.Denied,
            reasons = requiredReasons,
            warnings = List("real backend offline eval denied before any backend resource call"),
          )
        case Nil =>
          input.resourceConfig match {
            case Some(config) =>
              M9OfflineEvalRealBackendResourceConfig.validate(config) match {
                case Nil =>
                  M9OfflineEvalRealBackendResourceGateDecision(
                    status = M9OfflineEvalRealBackendResourceGateStatus.AllowedForConfiguredResource,
                    reasons = Nil,
                    warnings = List(
                      "resource gate passed, but this spike still does not implement a real backend client call"
                    ),
                  )
                case reasons =>
                  M9OfflineEvalRealBackendResourceGateDecision(
                    status = M9OfflineEvalRealBackendResourceGateStatus.Denied,
                    reasons = reasons,
                    warnings = List("real backend offline eval denied before any backend resource call"),
                  )
              }
            case None =>
              M9OfflineEvalRealBackendResourceGateDecision(
                status = M9OfflineEvalRealBackendResourceGateStatus.AllowedWithoutResourceConfig,
                reasons = Nil,
                warnings = List("backend resource config is absent; adapter must emit not-configured data"),
              )
          }
      }
    }
}

object M9OfflineEvalRealBackendResourceGate {
  val EnablementEnvVar: String = "RUN_REAL_BACKEND_OFFLINE_EVAL"

  val DefaultDisabled: M9OfflineEvalRealBackendResourceGate =
    M9OfflineEvalRealBackendResourceGate(runRealBackendOfflineEval = false)

  def fromEnv(env: Map[String, String]): M9OfflineEvalRealBackendResourceGate =
    M9OfflineEvalRealBackendResourceGate(
      runRealBackendOfflineEval = env.get(EnablementEnvVar).exists(_.trim == "1")
    )

  private def requiredInputReasons(input: M9OfflineEvalRealBackendSpikeInput): List[String] = {
    val plan = input.request.plan
    val dataset = input.request.dataset
    val backendReasons = input.backendKind match {
      case Some(kind) =>
        List(
          Option.when(input.candidateSource != Some(kind.candidateSource))(
            s"candidate source must be ${kind.candidateSource.render} for backend kind ${kind.render}"
          ),
          Option.when(plan.executionMode != kind.executionMode)(
            s"execution plan mode must be ${kind.executionMode.render} for backend kind ${kind.render}"
          ),
        ).flatten
      case None =>
        List("backend kind is required")
    }

    List(
      Option.when(plan.evalDatasetId.value.trim.isEmpty)("eval dataset id is required"),
      Option.when(dataset.evalDatasetId.value.trim.isEmpty)("dataset eval dataset id is required"),
      Option.when(plan.evalDatasetId != dataset.evalDatasetId)(
        s"execution plan eval dataset id ${plan.evalDatasetId.render} does not match dataset eval dataset id ${dataset.evalDatasetId.render}"
      ),
      Option.when(plan.catalogSnapshotId.value.trim.isEmpty)("catalog snapshot id is required"),
      Option.when(dataset.catalogSnapshotId.value.trim.isEmpty)("dataset catalog snapshot id is required"),
      Option.when(plan.catalogSnapshotId != dataset.catalogSnapshotId)(
        s"execution plan catalog snapshot id ${plan.catalogSnapshotId.render} does not match dataset catalog snapshot id ${dataset.catalogSnapshotId.render}"
      ),
      Option.when(input.candidateSource.isEmpty)("candidate source is required"),
      Option.when(input.candidateSource.contains(CandidateSource.Unknown))("candidate source must be explicit"),
      Option.when(plan.experimentId.value.trim.isEmpty)("experiment id is required"),
      Option.when(plan.requestId.isEmpty && input.requestIdPolicy.isEmpty)("request id policy or explicit request id is required"),
      Option.when(input.queryClass.isEmpty)("query class is required"),
      Option.when(input.topK.forall(_ <= 0))("top-k must be positive"),
      Option.when(input.timeoutBudgetMillis.forall(_ <= 0))("timeout budget must be positive"),
      Option.when(!input.productionActivationNotApprovedConfirmed)(
        "explicit production-activation non-approval confirmation is required"
      ),
    ).flatten ++ backendReasons
  }
}

final case class M9OfflineEvalRealBackendSpikeInput(
  request: M9OfflineEvalBackendRunRequest,
  backendKind: Option[M9OfflineEvalRealBackendKind],
  candidateSource: Option[CandidateSource],
  requestIdPolicy: Option[M9OfflineEvalRealBackendRequestIdPolicy],
  queryClass: Option[QueryClass],
  topK: Option[Int],
  timeoutBudgetMillis: Option[Long],
  resourceConfig: Option[M9OfflineEvalRealBackendResourceConfig],
  productionActivationNotApprovedConfirmed: Boolean,
)

final case class M9OfflineEvalRealBackendSpikeEvidence(
  gateDecision: M9OfflineEvalRealBackendResourceGateDecision,
  backendKind: Option[M9OfflineEvalRealBackendKind],
  executionMode: M9OfflineEvalBackendExecutionMode,
  candidateSource: CandidateSource,
  resourceConfigPresent: Boolean,
  productionActivationNotApprovedConfirmed: Boolean,
  realBackendCallImplemented: Boolean,
) {
  def warnings: List[String] =
    gateDecision.warnings ++ List(
      s"gate_status=${gateDecision.status.render}",
      s"resource_config_present=$resourceConfigPresent",
      s"production_activation_not_approved_confirmed=$productionActivationNotApprovedConfirmed",
      s"real_backend_call_implemented=$realBackendCallImplemented",
    )
}

final case class M9OfflineEvalRealBackendSpikeResult(
  evidence: M9OfflineEvalRealBackendSpikeEvidence,
  response: M9OfflineEvalBackendRunResponse,
)

final case class M9OfflineEvalRealBackendSpikeAdapter(
  input: M9OfflineEvalRealBackendSpikeInput,
  gate: M9OfflineEvalRealBackendResourceGate,
) extends M9OfflineEvalBackendAdapter {
  override val executionMode: M9OfflineEvalBackendExecutionMode =
    input.backendKind.fold(M9OfflineEvalBackendExecutionMode.Unknown)(_.executionMode)

  val candidateSource: CandidateSource =
    input.candidateSource.getOrElse(CandidateSource.Unknown)

  override def run(request: M9OfflineEvalBackendRunRequest): M9OfflineEvalBackendRunResponse =
    runWithEvidence(request).response

  def runWithEvidence(request: M9OfflineEvalBackendRunRequest): M9OfflineEvalRealBackendSpikeResult = {
    val decision = gate.decide(input.copy(request = request))
    val evidence = M9OfflineEvalRealBackendSpikeEvidence(
      gateDecision = decision,
      backendKind = input.backendKind,
      executionMode = executionMode,
      candidateSource = candidateSource,
      resourceConfigPresent = input.resourceConfig.nonEmpty,
      productionActivationNotApprovedConfirmed = input.productionActivationNotApprovedConfirmed,
      realBackendCallImplemented = false,
    )
    val queryResults = request.dataset.queries.map { query =>
      failureResult(
        query = query,
        message = failureMessage(decision),
        retryable = false,
        gateStatus = decision.status,
      )
    }

    M9OfflineEvalRealBackendSpikeResult(
      evidence = evidence,
      response = M9OfflineEvalBackendRunResponse(
        plan = request.plan,
        dataset = request.dataset,
        queryResults = queryResults,
        aggregateMetrics = List(OfflineEvalMetric(
          name = OfflineEvalMetricName.QualityGateDecision,
          value = OfflineEvalMetricValue.Text("not_evaluated_not_for_activation"),
        )),
        warnings = evidence.warnings,
      ),
    )
  }

  private def failureMessage(decision: M9OfflineEvalRealBackendResourceGateDecision): String =
    decision.status match {
      case M9OfflineEvalRealBackendResourceGateStatus.Disabled |
          M9OfflineEvalRealBackendResourceGateStatus.Denied =>
        s"real backend offline eval denied: ${decision.reasons.mkString("; ")}"
      case M9OfflineEvalRealBackendResourceGateStatus.AllowedWithoutResourceConfig =>
        s"${candidateSource.render} offline adapter resource config is not configured; no real backend call attempted"
      case M9OfflineEvalRealBackendResourceGateStatus.AllowedForConfiguredResource =>
        s"${candidateSource.render} offline adapter resource is configured, but real backend client calls are not implemented in this spike"
    }

  private def failureResult(
    query: M9OfflineEvalDatasetQuery,
    message: String,
    retryable: Boolean,
    gateStatus: M9OfflineEvalRealBackendResourceGateStatus,
  ): M9OfflineEvalBackendQueryResult =
    M9OfflineEvalBackendQueryResult(
      queryId = query.queryId,
      queryClass = query.queryClass,
      servingMode = executionMode.servingMode,
      candidateSource = candidateSource,
      candidates = Nil,
      metrics = Nil,
      regressionStatus = "failed",
      warnings = List(s"gate_status=${gateStatus.render}"),
      failure = Some(M9OfflineEvalBackendFailure(
        queryId = query.queryId,
        candidateSource = candidateSource,
        message = message,
        retryable = retryable,
      )),
    )
}
