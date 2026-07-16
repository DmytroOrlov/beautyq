package leaderboard.search.gen2.elasticsearch

import leaderboard.search.gen2.contract.*
import leaderboard.search.gen2.core.materialization.CanonicalFingerprint
import leaderboard.search.gen2.core.plan.{ContractFingerprint, PlanContractFingerprint}

/** A positive finite multiplicative boost applied to one weighted query field or to geo scoring. */
final case class ElasticsearchQueryWeight(value: Double)

/** A finite decay factor strictly between zero and one for `gauss` geo scoring. */
final case class ElasticsearchGeoDecay(value: Double)

enum ElasticsearchTextOperator {
  case And
  case Or

  def wireValue: String = this match {
    case ElasticsearchTextOperator.And => "and"
    case ElasticsearchTextOperator.Or  => "or"
  }
}

/** One declared searchable text field's query-time boost. `field` must be the exact handle owned by the
  * bound declaration - never a recreated or foreign-document field - which [[ElasticsearchPolicy]]'s
  * validation checks by reference, mirroring [[ElasticsearchTextFieldMapping]]. */
final case class ElasticsearchWeightedTextField[Document](
  field: SearchField[Document, String],
  weight: ElasticsearchQueryWeight,
)

/** The one supported geo-proximity scoring shape: one `function_score`/`gauss` function applied to
  * whichever field a [[PlannedSignal.GeoProximitySignal]] names. `scale`/`offset` are meter distances,
  * reusing the generic contract's own [[Distance]] rather than a second geo-unit type. */
final case class ElasticsearchGeoScoringPolicy(
  scale: Distance,
  offset: Distance,
  decay: ElasticsearchGeoDecay,
  weight: ElasticsearchQueryWeight,
)

enum ElasticsearchTotalHitsPolicy {
  case ExactRequired
}

/** The framework's default ordering choice when a plan carries no explicit sort: `relevanceDirection` is
  * used when scoring is active (residual text, geo proximity, or both), and `identityTieBreakerDirection`
  * is always appended as the final deterministic tie-breaker - both to an explicit sort vector and, when
  * no scoring is active either, as the sole default order. */
final case class ElasticsearchDefaultSortPolicy(
  relevanceDirection: SortDirection,
  identityTieBreakerDirection: SortDirection,
)

/** Framework-owned search-compiler/cursor-state protocol identity: bumped only when this module's own
  * query-compilation or cursor-state wire format changes, independent of any one domain's query policy. */
final case class ElasticsearchSearchCompilerVersion(value: String)

object ElasticsearchSearchCompilerVersion {
  val Current: ElasticsearchSearchCompilerVersion = ElasticsearchSearchCompilerVersion("es-search-compiler-v2")
}

sealed trait ElasticsearchQueryPolicyError

object ElasticsearchQueryPolicyError {
  final case class MissingQueryWeightAssignment(fieldId: FieldId, index: Int) extends ElasticsearchQueryPolicyError
  final case class DuplicateQueryWeightAssignment(fieldId: FieldId, firstIndex: Int, duplicateIndex: Int) extends ElasticsearchQueryPolicyError
  final case class UndeclaredQueryTextFieldHandle(fieldId: FieldId, index: Int) extends ElasticsearchQueryPolicyError
  final case class NonSearchableQueryTextFieldAssignment(fieldId: FieldId, index: Int) extends ElasticsearchQueryPolicyError
  final case class NonPositiveQueryWeight(fieldId: FieldId, index: Int, value: Double) extends ElasticsearchQueryPolicyError
  final case class NonPositiveGeoScale(value: BigDecimal) extends ElasticsearchQueryPolicyError
  final case class NegativeGeoOffset(value: BigDecimal) extends ElasticsearchQueryPolicyError
  final case class InvalidGeoDecay(value: Double) extends ElasticsearchQueryPolicyError
  final case class NonPositiveGeoWeight(value: Double) extends ElasticsearchQueryPolicyError
  final case class UnsupportedIdentityTieBreaker(fieldId: FieldId, kind: SearchFieldKind) extends ElasticsearchQueryPolicyError
}

type ElasticsearchQueryPolicyErrors = NonEmptyErrors[ElasticsearchQueryPolicyError]

/** The one complete, validated, immutable Elasticsearch policy for one document declaration: binds Brick
  * 5A's [[ElasticsearchIndexPolicy]] (mapping/source shape) to Brick 5B's query-time choices (weighted
  * text fields, text operator, optional geo scoring, total-hits policy, default sort/tie-break policy) and
  * the framework-owned [[ElasticsearchSearchCompilerVersion]]. This is the one production owner of the
  * complete Elasticsearch `contributions`/`contractFingerprint`; [[ElasticsearchIndexPolicy]] carries
  * neither. The only construction paths are [[ElasticsearchPolicy.apply]] and
  * [[ElasticsearchPolicy.unsafeFrom]]. `queryTextFields` always exposes the validated assignments
  * normalized into document declaration order, exactly like `index.textFields`. */
final class ElasticsearchPolicy[Document, Id] private (
  val planContractVersion: PlanContractVersion,
  val index: ElasticsearchIndexPolicy[Document, Id],
  val queryTextFields: Vector[ElasticsearchWeightedTextField[Document]],
  val textOperator: ElasticsearchTextOperator,
  val geoScoringPolicy: Option[ElasticsearchGeoScoringPolicy],
  val totalHitsPolicy: ElasticsearchTotalHitsPolicy,
  val defaultSortPolicy: ElasticsearchDefaultSortPolicy,
  val searchCompilerVersion: ElasticsearchSearchCompilerVersion,
) {
  private val weightByFieldId: Map[FieldId, ElasticsearchQueryWeight] =
    queryTextFields.map(mapping => mapping.field.id -> mapping.weight).toMap

  def weightOf(field: SearchField[Document, ?]): Option[ElasticsearchQueryWeight] = weightByFieldId.get(field.id)

  /** The one Elasticsearch contribution to [[PlanContractContributions]], derived canonically from every
    * executable choice that affects mapping, indexing or query execution - never a second, independently
    * maintained hash. Changing any one of them changes this contribution and [[contractFingerprint]]. */
  val contributions: PlanContractContributions =
    Map(ElasticsearchPolicy.ContributionId -> PlanContractContributionVersion(ElasticsearchPolicy.contributionVersion(this)))

  val contractFingerprint: ContractFingerprint =
    PlanContractFingerprint.compute(planContractVersion, index.declaration, contributions)
}

object ElasticsearchPolicy {

  val ContributionId: PlanContractContributionId = PlanContractContributionId("elasticsearch")

  def apply[Document, Id](
    planContractVersion: PlanContractVersion,
    index: ElasticsearchIndexPolicy[Document, Id],
    queryTextFields: Vector[ElasticsearchWeightedTextField[Document]],
    textOperator: ElasticsearchTextOperator,
    geoScoringPolicy: Option[ElasticsearchGeoScoringPolicy],
    totalHitsPolicy: ElasticsearchTotalHitsPolicy,
    defaultSortPolicy: ElasticsearchDefaultSortPolicy,
  ): Either[ElasticsearchQueryPolicyErrors, ElasticsearchPolicy[Document, Id]] =
    NonEmptyErrors.fromVector(validate(index.declaration, queryTextFields, geoScoringPolicy)) match {
      case Some(errors) => Left(errors)
      case None =>
        Right(
          new ElasticsearchPolicy(
            planContractVersion,
            index,
            normalize(index.declaration, queryTextFields),
            textOperator,
            geoScoringPolicy,
            totalHitsPolicy,
            defaultSortPolicy,
            ElasticsearchSearchCompilerVersion.Current,
          )
        )
    }

  /** For static domain-policy declarations only, where an invalid source policy is a broken source
    * invariant to fail loudly on, never a runtime input case. Domain files must call this rather than
    * hand-roll their own `apply(...).getOrElse(throw ...)`. */
  def unsafeFrom[Document, Id](
    planContractVersion: PlanContractVersion,
    index: ElasticsearchIndexPolicy[Document, Id],
    queryTextFields: Vector[ElasticsearchWeightedTextField[Document]],
    textOperator: ElasticsearchTextOperator,
    geoScoringPolicy: Option[ElasticsearchGeoScoringPolicy],
    totalHitsPolicy: ElasticsearchTotalHitsPolicy,
    defaultSortPolicy: ElasticsearchDefaultSortPolicy,
  ): ElasticsearchPolicy[Document, Id] =
    apply(planContractVersion, index, queryTextFields, textOperator, geoScoringPolicy, totalHitsPolicy, defaultSortPolicy) match {
      case Right(policy) => policy
      case Left(errors)  => throw new IllegalStateException(s"invalid source ElasticsearchPolicy declaration: ${errors.toVector.mkString("; ")}")
    }

  // Mirrors ElasticsearchIndexPolicy's own assignment-shape validation exactly (undeclared handle,
  // non-searchable/non-text target, duplicates in textFields order, then missing assignments in
  // declaration order), plus this policy's own numeric-parameter checks. Deterministic order: query-field
  // assignment-shape violations, then per-assignment weight positivity (declaration order), then geo
  // parameter violations (scale, offset, decay, weight) when a geo policy is supplied.
  private def validate[Document](
    declaration: SearchDocumentDeclaration[Document, ?],
    queryTextFields: Vector[ElasticsearchWeightedTextField[Document]],
    geoScoringPolicy: Option[ElasticsearchGeoScoringPolicy],
  ): Vector[ElasticsearchQueryPolicyError] = {
    val searchableTextFields = ElasticsearchIndexPolicy.declaredSearchableTextFields(declaration)

    val assignmentViolations: Vector[ElasticsearchQueryPolicyError] =
      queryTextFields.zipWithIndex.flatMap { case (assignment, index) =>
        declaration.allFields.find(_ eq assignment.field) match {
          case None =>
            Vector(ElasticsearchQueryPolicyError.UndeclaredQueryTextFieldHandle(assignment.field.id, index))
          case Some(declared) if !(declared.capabilities.searchable && declared.kind == SearchFieldKind.Text) =>
            Vector(ElasticsearchQueryPolicyError.NonSearchableQueryTextFieldAssignment(declared.id, index))
          case Some(_) =>
            Vector.empty
        }
      }

    val duplicateViolations: Vector[ElasticsearchQueryPolicyError] =
      queryTextFields.zipWithIndex.flatMap { case (assignment, index) =>
        queryTextFields.take(index).indexWhere(_.field eq assignment.field) match {
          case -1         => Vector.empty
          case firstIndex => Vector(ElasticsearchQueryPolicyError.DuplicateQueryWeightAssignment(assignment.field.id, firstIndex, index))
        }
      }

    val missingViolations: Vector[ElasticsearchQueryPolicyError] =
      searchableTextFields.zipWithIndex.flatMap { case (field, index) =>
        if (queryTextFields.exists(_.field eq field)) Vector.empty else Vector(ElasticsearchQueryPolicyError.MissingQueryWeightAssignment(field.id, index))
      }

    val identityTieBreakerViolations: Vector[ElasticsearchQueryPolicyError] =
      identityTieBreakerError(declaration.identity).toVector

    val weightViolations: Vector[ElasticsearchQueryPolicyError] =
      queryTextFields.zipWithIndex.flatMap { case (assignment, index) =>
        if (java.lang.Double.isFinite(assignment.weight.value) && assignment.weight.value > 0.0) Vector.empty
        else Vector(ElasticsearchQueryPolicyError.NonPositiveQueryWeight(assignment.field.id, index, assignment.weight.value))
      }

    val geoViolations: Vector[ElasticsearchQueryPolicyError] =
      geoScoringPolicy match {
        case None => Vector.empty
        case Some(geo) =>
          val scaleViolation   = if (geo.scale.meters > 0) Vector.empty else Vector(ElasticsearchQueryPolicyError.NonPositiveGeoScale(geo.scale.meters))
          val offsetViolation  = if (geo.offset.meters >= 0) Vector.empty else Vector(ElasticsearchQueryPolicyError.NegativeGeoOffset(geo.offset.meters))
          val decayViolation   = if (java.lang.Double.isFinite(geo.decay.value) && geo.decay.value > 0.0 && geo.decay.value < 1.0) Vector.empty else Vector(ElasticsearchQueryPolicyError.InvalidGeoDecay(geo.decay.value))
          val weightViolation  = if (java.lang.Double.isFinite(geo.weight.value) && geo.weight.value > 0.0) Vector.empty else Vector(ElasticsearchQueryPolicyError.NonPositiveGeoWeight(geo.weight.value))
          scaleViolation ++ offsetViolation ++ decayViolation ++ weightViolation
      }

    assignmentViolations ++ duplicateViolations ++ missingViolations ++ identityTieBreakerViolations ++ weightViolations ++ geoViolations
  }

  private def identityTieBreakerError[Document](identity: SearchField[Document, ?]): Option[ElasticsearchQueryPolicyError] =
    identity.kind match {
      case SearchFieldKind.Keyword | SearchFieldKind.Integer | SearchFieldKind.Long | SearchFieldKind.Decimal |
          SearchFieldKind.Boolean | SearchFieldKind.DateTime => None
      case SearchFieldKind.Text | SearchFieldKind.GeoPoint =>
        Some(ElasticsearchQueryPolicyError.UnsupportedIdentityTieBreaker(identity.id, identity.kind))
    }

  // Caller-supplied vector order is input syntax, not business policy - mirrors ElasticsearchIndexPolicy's
  // own normalization exactly, for weight assignments instead of analyzer assignments.
  private def normalize[Document](
    declaration: SearchDocumentDeclaration[Document, ?],
    queryTextFields: Vector[ElasticsearchWeightedTextField[Document]],
  ): Vector[ElasticsearchWeightedTextField[Document]] =
    ElasticsearchIndexPolicy.declaredSearchableTextFields(declaration).flatMap(field => queryTextFields.filter(_.field eq field))

  private def directionLabel(value: SortDirection): String =
    value match {
      case SortDirection.Asc  => "asc"
      case SortDirection.Desc => "desc"
    }

  private def totalHitsPolicyLabel(value: ElasticsearchTotalHitsPolicy): String =
    value match {
      case ElasticsearchTotalHitsPolicy.ExactRequired => "exact-required"
    }

  // Reuses the same neutral token/hash mechanics PlanContractFingerprint itself uses, rather than a
  // second, separately maintained hash protocol. Iterates the policy's own normalized index.textFields/
  // queryTextFields directly - never an independent declaration traversal plus an optional lookup.
  private def contributionVersion[Document, Id](policy: ElasticsearchPolicy[Document, Id]): String = {
    val analyzerTokens: Vector[String] =
      policy.index.textFields.zipWithIndex.flatMap { case (mapping, index) =>
        Vector(
          CanonicalFingerprint.token(s"analyzer-field[$index].id", mapping.field.id.value),
          CanonicalFingerprint.token(s"analyzer-field[$index].analyzer", mapping.analyzer.value),
        )
      }

    val queryFieldTokens: Vector[String] =
      policy.queryTextFields.zipWithIndex.flatMap { case (mapping, index) =>
        Vector(
          CanonicalFingerprint.token(s"query-field[$index].id", mapping.field.id.value),
          CanonicalFingerprint.token(s"query-field[$index].weight", mapping.weight.value.toString),
        )
      }

    val geoTokens: Vector[String] =
      policy.geoScoringPolicy match {
        case None =>
          Vector(CanonicalFingerprint.token("geo.present", "false"))
        case Some(geo) =>
          Vector(
            CanonicalFingerprint.token("geo.present", "true"),
            CanonicalFingerprint.token("geo.scale", geo.scale.meters.toString),
            CanonicalFingerprint.token("geo.offset", geo.offset.meters.toString),
            CanonicalFingerprint.token("geo.decay", geo.decay.value.toString),
            CanonicalFingerprint.token("geo.weight", geo.weight.value.toString),
          )
      }

    CanonicalFingerprint.sha256HexTokens(
      Vector(
        CanonicalFingerprint.token("index-policy-version", policy.index.policyVersion.value),
        CanonicalFingerprint.token("compiler-version", policy.index.compilerVersion.value),
        CanonicalFingerprint.token("index-format-version", policy.index.indexFormatVersion.value),
      ) ++ analyzerTokens ++
        queryFieldTokens ++
        Vector(CanonicalFingerprint.token("text-operator", policy.textOperator.wireValue)) ++
        geoTokens ++
        Vector(
          CanonicalFingerprint.token("total-hits-policy", totalHitsPolicyLabel(policy.totalHitsPolicy)),
          CanonicalFingerprint.token("default-sort.relevance-direction", directionLabel(policy.defaultSortPolicy.relevanceDirection)),
          CanonicalFingerprint.token("default-sort.identity-tie-breaker-direction", directionLabel(policy.defaultSortPolicy.identityTieBreakerDirection)),
          CanonicalFingerprint.token("search-compiler-version", policy.searchCompilerVersion.value),
        )
    )
  }
}
