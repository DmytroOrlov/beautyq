package leaderboard.search.gen2.contract

/** One public input declaration. The declaration owns the public name and policy-specific decoder;
  * [[PublicInputRegistry]] owns lookup, operator gating and deterministic inventory construction. */
trait PublicInputSpec[Input, Name, Operator, Clause, Error] {
  def name: Name
  def acceptedOperators: Vector[Operator]
  def decode(input: Input): Either[NonEmptyErrors[Error], Clause]
}

final case class PublicInputField[Name, Operator](name: Name, acceptedOperators: Vector[Operator])

/** Generic registry mechanics for any typed public request. The declaration vector is ordered and
  * is expected to contain one entry per public name; lookup follows that declaration order. The
  * domain supplies only the input accessors and typed unknown/unsupported errors. */
final class PublicInputRegistry[Input, Name, Operator, Clause, Error] private (
  specs: Vector[PublicInputSpec[Input, Name, Operator, Clause, Error]],
  nameOf: Input => Name,
  operatorOf: Input => Operator,
  unknownField: Name => Error,
  unsupportedOperator: (Name, Operator, Vector[Operator]) => Error,
) {
  val fields: Vector[PublicInputField[Name, Operator]] = specs.map(spec => PublicInputField(spec.name, spec.acceptedOperators))

  private val specsByName: Map[Name, PublicInputSpec[Input, Name, Operator, Clause, Error]] =
    specs.map(spec => spec.name -> spec).toMap

  def decode(input: Input): Either[NonEmptyErrors[Error], Clause] = {
    decodeNamed(input).map(_._2)
  }

  def decodeNamed(input: Input): Either[NonEmptyErrors[Error], (Name, Clause)] = {
    val name = nameOf(input)
    val operator = operatorOf(input)
    specsByName.get(name) match {
      case None => Left(NonEmptyErrors.fromHead(unknownField(name), Vector.empty))
      case Some(spec) if !spec.acceptedOperators.contains(operator) =>
        Left(NonEmptyErrors.fromHead(unsupportedOperator(name, operator, spec.acceptedOperators), Vector.empty))
      case Some(spec) => spec.decode(input).map(value => name -> value)
    }
  }
}

object PublicInputRegistry {
  def apply[Input, Name, Operator, Clause, Error](
    specs: Vector[PublicInputSpec[Input, Name, Operator, Clause, Error]],
    nameOf: Input => Name,
    operatorOf: Input => Operator,
    unknownField: Name => Error,
    unsupportedOperator: (Name, Operator, Vector[Operator]) => Error,
  ): PublicInputRegistry[Input, Name, Operator, Clause, Error] =
    new PublicInputRegistry(specs, nameOf, operatorOf, unknownField, unsupportedOperator)
}
