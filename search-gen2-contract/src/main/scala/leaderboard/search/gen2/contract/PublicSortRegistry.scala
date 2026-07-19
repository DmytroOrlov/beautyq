package leaderboard.search.gen2.contract

trait PublicSortSpec[Input, Name, Decoded, Error] {
  def name: Name
  def decode(input: Input): Either[NonEmptyErrors[Error], Decoded]
}

/** Generic public-sort lookup. Sort names are validated as unique in the ordered declaration vector;
  * unknown-name handling and the decoded value remain domain policy. */
final class PublicSortRegistry[Input, Name, Decoded, Error] private (
  index: PublicDeclarationIndex[Name, PublicSortSpec[Input, Name, Decoded, Error]],
  nameOf: Input => Name,
  unknownSort: Name => Error,
) {
  val names: Vector[Name] = index.declarations.map(_.name)

  def decode(input: Input): Either[NonEmptyErrors[Error], Decoded] = {
    val name = nameOf(input)
    index.byName.get(name) match {
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
  ): Either[NonEmptyErrors[PublicDeclarationError[Name]], PublicSortRegistry[Input, Name, Decoded, Error]] =
    PublicDeclarationIndex(specs, _.name).map(index => new PublicSortRegistry(index, nameOf, unknownSort))

  def unsafeFrom[Input, Name, Decoded, Error](
    specs: Vector[PublicSortSpec[Input, Name, Decoded, Error]],
    nameOf: Input => Name,
    unknownSort: Name => Error,
  ): PublicSortRegistry[Input, Name, Decoded, Error] =
    new PublicSortRegistry(PublicDeclarationIndex.unsafeFrom(specs, _.name), nameOf, unknownSort)
}
