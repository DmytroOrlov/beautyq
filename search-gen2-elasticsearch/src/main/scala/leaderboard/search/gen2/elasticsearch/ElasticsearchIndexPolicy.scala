package leaderboard.search.gen2.elasticsearch

import leaderboard.search.gen2.contract.*
import leaderboard.search.gen2.core.materialization.CanonicalFingerprint
import leaderboard.search.gen2.core.plan.{ContractFingerprint, PlanContractFingerprint}

final case class ElasticsearchAnalyzerName(value: String)

object ElasticsearchAnalyzerName {
  val Standard: ElasticsearchAnalyzerName = ElasticsearchAnalyzerName("standard")
}

/** One declared searchable text field's analyzer choice. `field` must be the exact handle owned by the
  * bound declaration - never a recreated or foreign-document field - which [[ElasticsearchIndexPolicy]]'s
  * validation checks by reference. */
final case class ElasticsearchTextFieldMapping[Document](
  field: SearchField[Document, String],
  analyzer: ElasticsearchAnalyzerName,
)

/** Explicit domain compatibility choice, mixed into the derived plan-contract contribution and
  * contract fingerprint alongside the framework-owned compiler/index-format versions below. */
final case class ElasticsearchPolicyVersion(value: String)

/** Framework-owned compiler/index-format identity: bumped only when this module's own mapping/source
  * compilation behavior or physical index format changes, independent of any one domain's policy
  * version. */
final case class ElasticsearchCompilerVersion(value: String)
final case class ElasticsearchIndexFormatVersion(value: String)

object ElasticsearchCompilerVersion {
  val Current: ElasticsearchCompilerVersion = ElasticsearchCompilerVersion("es-compiler-v1")
}

object ElasticsearchIndexFormatVersion {
  val Current: ElasticsearchIndexFormatVersion = ElasticsearchIndexFormatVersion("es-index-format-v1")
}

sealed trait ElasticsearchIndexPolicyError

object ElasticsearchIndexPolicyError {
  final case class MissingAnalyzerAssignment(fieldId: FieldId, index: Int) extends ElasticsearchIndexPolicyError
  final case class DuplicateAnalyzerAssignment(fieldId: FieldId, firstIndex: Int, duplicateIndex: Int) extends ElasticsearchIndexPolicyError
  final case class UndeclaredTextFieldHandle(fieldId: FieldId, index: Int) extends ElasticsearchIndexPolicyError
  final case class NonSearchableTextFieldAssignment(fieldId: FieldId, index: Int) extends ElasticsearchIndexPolicyError
}

type ElasticsearchIndexPolicyErrors = NonEmptyErrors[ElasticsearchIndexPolicyError]

/** A validated, immutable Elasticsearch index policy binding one [[SearchDocumentDeclaration]] to the
  * domain's explicit analyzer choices and compatibility version. The only construction paths are
  * [[ElasticsearchIndexPolicy.apply]] and [[ElasticsearchIndexPolicy.unsafeFrom]]; `contributions` and
  * `contractFingerprint` are derived once from the bound declaration/policy, never independently
  * supplied. `textFields` always exposes the validated assignments normalized into document declaration
  * order - the caller's own vector order is input syntax, not business policy, and is never preserved as
  * a second, independent order. */
final class ElasticsearchIndexPolicy[Document, Id] private (
  val declaration: SearchDocumentDeclaration[Document, Id],
  val planContractVersion: PlanContractVersion,
  val policyVersion: ElasticsearchPolicyVersion,
  val compilerVersion: ElasticsearchCompilerVersion,
  val indexFormatVersion: ElasticsearchIndexFormatVersion,
  val textFields: Vector[ElasticsearchTextFieldMapping[Document]],
) {
  private val analyzerByFieldId: Map[FieldId, ElasticsearchAnalyzerName] =
    textFields.map(mapping => mapping.field.id -> mapping.analyzer).toMap

  def analyzerOf(field: SearchField[Document, ?]): Option[ElasticsearchAnalyzerName] = analyzerByFieldId.get(field.id)

  /** The one Elasticsearch contribution to [[PlanContractContributions]], derived canonically from the
    * explicit policy/compiler/index-format versions plus every searchable text field's declaration
    * identity and analyzer, in document declaration order - never a second, independently maintained
    * hash. */
  val contributions: PlanContractContributions =
    Map(ElasticsearchIndexPolicy.ContributionId -> PlanContractContributionVersion(ElasticsearchIndexPolicy.contributionVersion(this)))

  val contractFingerprint: ContractFingerprint =
    PlanContractFingerprint.compute(planContractVersion, declaration, contributions)
}

object ElasticsearchIndexPolicy {

  val ContributionId: PlanContractContributionId = PlanContractContributionId("elasticsearch")

  def apply[Document, Id](
    declaration: SearchDocumentDeclaration[Document, Id],
    planContractVersion: PlanContractVersion,
    policyVersion: ElasticsearchPolicyVersion,
    textFields: Vector[ElasticsearchTextFieldMapping[Document]],
  ): Either[ElasticsearchIndexPolicyErrors, ElasticsearchIndexPolicy[Document, Id]] =
    NonEmptyErrors.fromVector(validate(declaration, textFields)) match {
      case Some(errors) => Left(errors)
      case None =>
        Right(
          new ElasticsearchIndexPolicy(
            declaration,
            planContractVersion,
            policyVersion,
            ElasticsearchCompilerVersion.Current,
            ElasticsearchIndexFormatVersion.Current,
            normalize(declaration, textFields),
          )
        )
    }

  /** For static domain-policy declarations only, where an invalid source policy is a broken source
    * invariant to fail loudly on, never a runtime input case. Domain files must call this rather than
    * hand-roll their own `apply(...).getOrElse(throw ...)`. */
  def unsafeFrom[Document, Id](
    declaration: SearchDocumentDeclaration[Document, Id],
    planContractVersion: PlanContractVersion,
    policyVersion: ElasticsearchPolicyVersion,
    textFields: Vector[ElasticsearchTextFieldMapping[Document]],
  ): ElasticsearchIndexPolicy[Document, Id] =
    apply(declaration, planContractVersion, policyVersion, textFields) match {
      case Right(policy) => policy
      case Left(errors)  => throw new IllegalStateException(s"invalid source ElasticsearchIndexPolicy declaration: ${errors.toVector.mkString("; ")}")
    }

  private def declaredSearchableTextFields[Document, Id](declaration: SearchDocumentDeclaration[Document, Id]): Vector[SearchField[Document, ?]] =
    declaration.allFields.filter(field => field.capabilities.searchable && field.kind == SearchFieldKind.Text)

  // Caller-supplied vector order is input syntax, not business policy. `validate` already proves, before
  // this ever runs, that every declared searchable Text field has exactly one assignment with no
  // duplicate, undeclared, or non-searchable entry - so re-ordering by filtering the validated assignments
  // into declaration order is total: no assignment is ever absent or repeated here. The stored policy
  // therefore exposes exactly one order (declaration order), the same order mapping and fingerprint
  // derivation already use, never a second caller-controlled order.
  private def normalize[Document, Id](
    declaration: SearchDocumentDeclaration[Document, Id],
    textFields: Vector[ElasticsearchTextFieldMapping[Document]],
  ): Vector[ElasticsearchTextFieldMapping[Document]] =
    declaredSearchableTextFields(declaration).flatMap(field => textFields.filter(_.field eq field))

  // Deterministic error order: assignment-shape violations (undeclared handle, non-searchable/non-text
  // target) and duplicate assignments in `textFields` order first - both describe what is wrong with the
  // supplied policy itself - then missing assignments in document declaration order, describing what the
  // policy forgot to cover.
  private def validate[Document, Id](
    declaration: SearchDocumentDeclaration[Document, Id],
    textFields: Vector[ElasticsearchTextFieldMapping[Document]],
  ): Vector[ElasticsearchIndexPolicyError] = {
    val searchableTextFields = declaredSearchableTextFields(declaration)

    val assignmentViolations: Vector[ElasticsearchIndexPolicyError] =
      textFields.zipWithIndex.flatMap { case (assignment, index) =>
        declaration.allFields.find(_ eq assignment.field) match {
          case None =>
            Vector(ElasticsearchIndexPolicyError.UndeclaredTextFieldHandle(assignment.field.id, index))
          case Some(declared) if !(declared.capabilities.searchable && declared.kind == SearchFieldKind.Text) =>
            Vector(ElasticsearchIndexPolicyError.NonSearchableTextFieldAssignment(declared.id, index))
          case Some(_) =>
            Vector.empty
        }
      }

    val duplicateViolations: Vector[ElasticsearchIndexPolicyError] =
      textFields.zipWithIndex.flatMap { case (assignment, index) =>
        textFields.take(index).indexWhere(_.field eq assignment.field) match {
          case -1         => Vector.empty
          case firstIndex => Vector(ElasticsearchIndexPolicyError.DuplicateAnalyzerAssignment(assignment.field.id, firstIndex, index))
        }
      }

    val missingViolations: Vector[ElasticsearchIndexPolicyError] =
      searchableTextFields.zipWithIndex.flatMap { case (field, index) =>
        if (textFields.exists(_.field eq field)) Vector.empty else Vector(ElasticsearchIndexPolicyError.MissingAnalyzerAssignment(field.id, index))
      }

    assignmentViolations ++ duplicateViolations ++ missingViolations
  }

  // Reuses the same neutral token/hash mechanics PlanContractFingerprint itself uses, rather than a
  // second, separately maintained hash protocol. Iterates the policy's own normalized textFields directly
  // - never a second, independent declaration traversal plus an optional analyzer lookup - so there is no
  // impossible "missing assignment" state to fall back on here: normalization already guarantees exactly
  // one entry per declared searchable Text field, in declaration order.
  private def contributionVersion[Document, Id](policy: ElasticsearchIndexPolicy[Document, Id]): String = {
    val textFieldTokens: Vector[String] =
      policy.textFields.zipWithIndex.flatMap { case (mapping, index) =>
        Vector(
          CanonicalFingerprint.token(s"text-field[$index].id", mapping.field.id.value),
          CanonicalFingerprint.token(s"text-field[$index].analyzer", mapping.analyzer.value),
        )
      }

    CanonicalFingerprint.sha256HexTokens(
      Vector(
        CanonicalFingerprint.token("policy-version", policy.policyVersion.value),
        CanonicalFingerprint.token("compiler-version", policy.compilerVersion.value),
        CanonicalFingerprint.token("index-format-version", policy.indexFormatVersion.value),
      ) ++ textFieldTokens
    )
  }
}
