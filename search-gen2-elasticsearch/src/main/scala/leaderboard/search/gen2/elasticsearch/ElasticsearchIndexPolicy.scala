package leaderboard.search.gen2.elasticsearch

import leaderboard.search.gen2.contract.*

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

/** Explicit domain compatibility choice for mapping/source compilation, mixed into the derived
  * Elasticsearch contract fingerprint by [[ElasticsearchPolicy]] alongside the framework-owned
  * compiler/index-format versions below. Query-affecting choices live in [[ElasticsearchPolicy]], not
  * here - this type owns only mapping/source shape. */
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

/** A validated, immutable Elasticsearch mapping/source policy binding one [[SearchDocumentDeclaration]]
  * to the domain's explicit analyzer choices and mapping/source compatibility version. The only
  * construction paths are [[ElasticsearchIndexPolicy.apply]] and [[ElasticsearchIndexPolicy.unsafeFrom]].
  * `textFields` always exposes the validated assignments normalized into document declaration order - the
  * caller's own vector order is input syntax, not business policy, and is never preserved as a second,
  * independent order.
  *
  * This type owns only mapping/`_id`/`_source` compilation inputs. It carries no independently usable
  * `contributions`/`contractFingerprint` - [[ElasticsearchPolicy]] is the one production owner of the
  * complete Elasticsearch contract fingerprint, binding this index policy to Brick 5B's query policy
  * before deriving it. */
final class ElasticsearchIndexPolicy[Document, Id] private (
  val declaration: SearchDocumentDeclaration[Document, Id],
  val policyVersion: ElasticsearchPolicyVersion,
  val compilerVersion: ElasticsearchCompilerVersion,
  val indexFormatVersion: ElasticsearchIndexFormatVersion,
  val textFields: Vector[ElasticsearchTextFieldMapping[Document]],
) {
  private val analyzerByFieldId: Map[FieldId, ElasticsearchAnalyzerName] =
    textFields.map(mapping => mapping.field.id -> mapping.analyzer).toMap

  def analyzerOf(field: SearchField[Document, ?]): Option[ElasticsearchAnalyzerName] = analyzerByFieldId.get(field.id)
}

object ElasticsearchIndexPolicy {

  def apply[Document, Id](
    declaration: SearchDocumentDeclaration[Document, Id],
    policyVersion: ElasticsearchPolicyVersion,
    textFields: Vector[ElasticsearchTextFieldMapping[Document]],
  ): Either[ElasticsearchIndexPolicyErrors, ElasticsearchIndexPolicy[Document, Id]] =
    NonEmptyErrors.fromVector(validate(declaration, textFields)) match {
      case Some(errors) => Left(errors)
      case None =>
        Right(
          new ElasticsearchIndexPolicy(
            declaration,
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
    policyVersion: ElasticsearchPolicyVersion,
    textFields: Vector[ElasticsearchTextFieldMapping[Document]],
  ): ElasticsearchIndexPolicy[Document, Id] =
    apply(declaration, policyVersion, textFields) match {
      case Right(policy) => policy
      case Left(errors)  => throw new IllegalStateException(s"invalid source ElasticsearchIndexPolicy declaration: ${errors.toVector.mkString("; ")}")
    }

  private[elasticsearch] def declaredSearchableTextFields[Document, Id](declaration: SearchDocumentDeclaration[Document, Id]): Vector[SearchField[Document, ?]] =
    declaration.allFields.filter(field => field.capabilities.searchable && field.kind == SearchFieldKind.Text)

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

  // Caller-supplied vector order is input syntax, not business policy. `validate` already proves, before
  // this ever runs, that every declared searchable Text field has exactly one assignment with no
  // duplicate, undeclared, or non-searchable entry - so re-ordering by filtering the validated assignments
  // into declaration order is total: no assignment is ever absent or repeated here.
  private def normalize[Document, Id](
    declaration: SearchDocumentDeclaration[Document, Id],
    textFields: Vector[ElasticsearchTextFieldMapping[Document]],
  ): Vector[ElasticsearchTextFieldMapping[Document]] =
    declaredSearchableTextFields(declaration).flatMap(field => textFields.filter(_.field eq field))
}
