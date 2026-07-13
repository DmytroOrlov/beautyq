package leaderboard.search.gen2.contract

sealed trait ConstraintPrecedenceError

object ConstraintPrecedenceError {
  case object DuplicateSourceIdentity extends ConstraintPrecedenceError
}

/** One typed source order for hard-constraint precedence. A complete typed source-to-constraints
  * function is required, so a missing source cannot silently become an empty tier.
  */
final class ConstraintPrecedence[Source] private (
  val higherSource: Source,
  val lowerSource: Source,
) {
  val sourceOrder: Vector[Source] = Vector(higherSource, lowerSource)

  def tiers[Document](constraintsFor: Source => Vector[SourcedConstraint[Document]]): ConstraintPriorityTiers[Document] =
    new ConstraintPriorityTiers(
      constraintsFor(higherSource),
      constraintsFor(lowerSource),
    )
}

object ConstraintPrecedence {
  def above[Source](higher: Source, lower: Source): Either[ConstraintPrecedenceError, ConstraintPrecedence[Source]] =
    if (higher == lower) Left(ConstraintPrecedenceError.DuplicateSourceIdentity)
    else Right(new ConstraintPrecedence(higher, lower))

  def unsafeAbove[Source](higher: Source, lower: Source): ConstraintPrecedence[Source] =
    above(higher, lower) match {
      case Right(value) => value
      case Left(error)   => throw new IllegalStateException(renderError(error))
    }

  private def renderError(error: ConstraintPrecedenceError): String =
    error match {
      case ConstraintPrecedenceError.DuplicateSourceIdentity =>
        "invalid constraint precedence: higher and lower source must differ"
    }
}
