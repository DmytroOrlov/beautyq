package leaderboard.search.gen2.contract

/** A validated, ordered index for a public declaration inventory.
  *
  * The declaration vector is the only source of order. Construction rejects every later duplicate
  * with both positions before a lookup map is created, so a registry can never silently use a
  * last-wins entry.
  */
final class PublicDeclarationIndex[Name, Declaration] private (
  val declarations: Vector[Declaration],
  val byName: Map[Name, Declaration],
)

sealed trait PublicDeclarationError[+Name]

object PublicDeclarationError {
  final case class DuplicateName[Name](name: Name, firstIndex: Int, duplicateIndex: Int) extends PublicDeclarationError[Name]
}

object PublicDeclarationIndex {
  def apply[Name, Declaration](
    declarations: Vector[Declaration],
    nameOf: Declaration => Name,
  ): Either[NonEmptyErrors[PublicDeclarationError[Name]], PublicDeclarationIndex[Name, Declaration]] = {
    val duplicates = duplicateErrors(declarations, nameOf)
    NonEmptyErrors.fromVector(duplicates) match {
      case Some(errors) => Left(errors)
      case None         => Right(new PublicDeclarationIndex(declarations, declarations.map(value => nameOf(value) -> value).toMap))
    }
  }

  def unsafeFrom[Name, Declaration](
    declarations: Vector[Declaration],
    nameOf: Declaration => Name,
  ): PublicDeclarationIndex[Name, Declaration] =
    apply(declarations, nameOf) match {
      case Right(index) => index
      case Left(errors) =>
        throw new IllegalStateException(
          errors.toVector.map(renderError).mkString("invalid public declarations: ", "; ", "")
        )
    }

  private def duplicateErrors[Name, Declaration](
    declarations: Vector[Declaration],
    nameOf: Declaration => Name,
  ): Vector[PublicDeclarationError[Name]] = {
    val (_, errors) =
      declarations.zipWithIndex.foldLeft((Map.empty[Name, Int], Vector.empty[PublicDeclarationError[Name]])) {
        case ((firstIndexes, errors), (declaration, index)) =>
          val name = nameOf(declaration)
          firstIndexes.get(name) match {
            case Some(firstIndex) => (firstIndexes, errors :+ PublicDeclarationError.DuplicateName(name, firstIndex, index))
            case None             => (firstIndexes.updated(name, index), errors)
          }
      }
    errors
  }

  private def renderError[Name](error: PublicDeclarationError[Name]): String =
    error match {
      case PublicDeclarationError.DuplicateName(name, firstIndex, duplicateIndex) =>
        s"DuplicatePublicName(name=$name, firstIndex=$firstIndex, duplicateIndex=$duplicateIndex)"
    }
}
