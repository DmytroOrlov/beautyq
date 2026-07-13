package leaderboard.search.gen2.contract

/** Generic non-empty error accumulation shared by declaration, projection and future planning
  * boundaries. Domain companions remain responsible for business-specific ordering before calling
  * [[fromVector]].
  */
final case class NonEmptyErrors[+Error] private[search] (
  head: Error,
  tail: Vector[Error],
) {
  def toVector: Vector[Error] = head +: tail

  def map[Other](f: Error => Other): NonEmptyErrors[Other] =
    NonEmptyErrors.fromHead(f(head), tail.map(f))
}

object NonEmptyErrors {
  def fromVector[Error](errors: Vector[Error]): Option[NonEmptyErrors[Error]] =
    errors match {
      case head +: tail => Some(new NonEmptyErrors(head, tail))
      case _            => None
    }

  private[search] def fromHead[Error](head: Error, tail: Vector[Error]): NonEmptyErrors[Error] =
    new NonEmptyErrors(head, tail)
}
