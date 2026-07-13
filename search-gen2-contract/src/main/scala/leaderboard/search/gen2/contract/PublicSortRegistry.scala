package leaderboard.search.gen2.contract

trait PublicSortSpec[Input, Name, Decoded, Error] {
  def name: Name
  def decode(input: Input): Either[NonEmptyErrors[Error], Decoded]
}

/** Generic public-sort lookup. Sort names are expected to be unique in the ordered declaration
  * vector and are declared once; unknown-name handling and the decoded value remain domain policy. */
final class PublicSortRegistry[Input, Name, Decoded, Error] private (
  specs: Vector[PublicSortSpec[Input, Name, Decoded, Error]],
  nameOf: Input => Name,
  unknownSort: Name => Error,
) {
  val names: Vector[Name] = specs.map(_.name)
  private val specsByName: Map[Name, PublicSortSpec[Input, Name, Decoded, Error]] = specs.map(spec => spec.name -> spec).toMap

  def decode(input: Input): Either[NonEmptyErrors[Error], Decoded] = {
    val name = nameOf(input)
    specsByName.get(name) match {
      case Some(spec) => spec.decode(input)
      case None       => Left(NonEmptyErrors.fromHead(unknownSort(name), Vector.empty))
    }
  }
}

object PublicSortRegistry {
  def apply[Input, Name, Decoded, Error](
    specs: Vector[PublicSortSpec[Input, Name, Decoded, Error]],
    nameOf: Input => Name,
    unknownSort: Name => Error,
  ): PublicSortRegistry[Input, Name, Decoded, Error] =
    new PublicSortRegistry(specs, nameOf, unknownSort)
}
