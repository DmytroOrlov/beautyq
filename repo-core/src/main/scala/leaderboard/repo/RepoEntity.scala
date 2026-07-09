package leaderboard.repo

import scala.compiletime.constValue
import scala.compiletime.constValueTuple
import scala.deriving.Mirror
import scala.quoted.*

/** A model-derived field of `A` producing `B`.
  *
  * Built from a Scala 3 field selector (`_.parentId`); the `label` and physical
  * `column` are derived at compile time from the selected field name through the
  * existing [[RepoNamingStrategy]], never written as raw strings.
  */
final case class RepoField[A, B](
  label: String,
  column: String,
  select: A => B,
)

object RepoField {

  /** Derive a [[RepoField]] from a direct field selector, deriving the physical
    * column from the field label via [[RepoNamingStrategy.SnakeCase]].
    */
  inline def derived[A, B](inline selector: A => B): RepoField[A, B] = {
    val label = RepoFieldMacro.label[A, B](selector)
    RepoField(label, RepoNamingStrategy.SnakeCase.column(label), selector)
  }
}

/** Compile-time extraction of the selected field label from a selector. */
object RepoFieldMacro {

  inline def label[A, B](inline selector: A => B): String =
    ${ labelImpl[A, B]('selector) }

  def labelImpl[A: Type, B: Type](selector: Expr[A => B])(using Quotes): Expr[String] = {
    import quotes.reflect.*

    def unwrap(term: Term): Term =
      term match {
        case Inlined(_, _, inner) => unwrap(inner)
        case Block(Nil, inner)    => unwrap(inner)
        case Typed(inner, _)      => unwrap(inner)
        case _                    => term
      }

    val fieldName: Option[String] =
      unwrap(selector.asTerm) match {
        case Lambda(_, body) =>
          unwrap(body) match {
            case Select(_, name) => Some(name)
            case _               => None
          }
        case _ => None
      }

    fieldName match {
      case Some(name) =>
        Expr(name)
      case None =>
        report.errorAndAbort(
          s"RepoField selector must be a direct field selection like `_.fieldName`, but got: ${selector.asTerm.show}"
        )
    }
  }
}

/** A typed value/aggregate source.
  *
  * The aggregate model `A` is keyed by `K` and physically sourced from `Row`
  * rows. The aggregate is deliberately not described as a [[RepoEntity]] of its
  * own physical columns: its physical truth is the [[rowSource]], while
  * [[keyField]] is the key it is grouped/looked up by.
  */
final case class RepoValueSource[A, K, Row](
  valueModelName: String,
  rowSource: RepoEntity[Row],
  keyField: RepoField[A, K],
)

object RepoValueSource {

  /** Derive a [[RepoValueSource]] for aggregate `A` keyed by `K` via a direct
    * field selector, physically sourced from `Row` rows - the value-source
    * counterpart to [[RepoEntity.derived]]. `valueModelName` comes from `A`'s
    * own model label, `rowSource` from `Row`'s own [[RepoEntity.derived]], and
    * `keyField` from the selector: no raw table/column string is ever needed
    * by the caller. `A` is deliberately never claimed to be a [[RepoEntity]]
    * of its own physical columns - only `Row` is (see [[RepoValueSource]]'s
    * own doc).
    */
  inline def derived[A, K, Row](inline key: A => K)(using
    valueMirror: Mirror.ProductOf[A],
    rowMirror: Mirror.ProductOf[Row],
  ): RepoValueSource[A, K, Row] =
    RepoValueSource(
      valueModelName = constValue[valueMirror.MirroredLabel],
      rowSource      = RepoEntity.derived[Row],
      keyField       = RepoField.derived[A, K](key),
    )
}

/** Model-derived entity/source metadata.
  *
  * All physical identifiers are derived from the Scala model through a
  * [[RepoNamingStrategy]]; defining an entity never requires a raw table or
  * column string. For case classes with a conventional `id` field the
  * id/key column is derived by default; value sources without an `id` field
  * expose [[idColumn]] as `None` and rely on a typed key selector at the
  * relation layer.
  */
final case class RepoEntity[A](
  modelName: String,
  sourceName: String,
  fieldLabels: List[String],
  columns: List[String],
  idColumn: Option[String],
) {

  /** Columns other than the derived id/key column. */
  def dataColumns: List[String] =
    idColumn match {
      case Some(id) =>
        columns.filterNot(_ == id)
      case None =>
        columns
    }

  /** Physical column for a known field label, falling back to the snake_case
    * strategy (the same strategy used to derive [[columns]]).
    */
  def columnFor(label: String): String =
    fieldLabels
      .zip(columns)
      .collectFirst { case (knownLabel, knownColumn) if knownLabel == label => knownColumn }
      .getOrElse(RepoNamingStrategy.SnakeCase.column(label))

  /** Derive a typed [[RepoField]] from a direct field selector. The physical
    * column is taken from this entity's derived columns when the field is
    * known, and otherwise from [[RepoNamingStrategy.SnakeCase]].
    */
  inline def field[B](inline selector: A => B): RepoField[A, B] = {
    val label = RepoFieldMacro.label[A, B](selector)
    RepoField(label, columnFor(label), selector)
  }

  /** Build a graph node for this entity keyed by a selector-derived field. */
  inline def node[K](inline key: A => K): EntityNode[A, K] =
    EntityNode(this, field(key))
}

object RepoEntity {
  private val IdFieldLabel = "id"

  /** Derive entity metadata for a product type using the [[RepoNamingStrategy.SnakeCase]] strategy. */
  inline def derived[A](using mirror: Mirror.ProductOf[A]): RepoEntity[A] =
    derived[A](RepoNamingStrategy.SnakeCase)

  inline def derived[A](naming: RepoNamingStrategy)(using mirror: Mirror.ProductOf[A]): RepoEntity[A] = {
    val modelName   = constValue[mirror.MirroredLabel]
    val fieldLabels = labelsOf[mirror.MirroredElemLabels]
    RepoEntity(
      modelName   = modelName,
      sourceName  = naming.table(modelName),
      fieldLabels = fieldLabels,
      columns     = fieldLabels.map(naming.column),
      idColumn    = Option.when(fieldLabels.contains(IdFieldLabel))(naming.column(IdFieldLabel)),
    )
  }

  private inline def labelsOf[Labels <: Tuple]: List[String] =
    constValueTuple[Labels].productIterator.map(_.toString).toList
}
