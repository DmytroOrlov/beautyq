package leaderboard.search.gen2.contract

import scala.quoted.*

/** Compile-time derivation of a [[SearchValueTypeId]] from a concrete named nominal Scala type's own
  * simple source name (e.g. `MasterServiceOfferVariantId`, `ServiceCode`), owned independently by this
  * Gen2 contract module so it stays free of any Gen1 dependency. Inspects the macro-time `TypeRepr`
  * symbol only - no runtime reflection, `ClassTag`, `TypeTag`, or stringified type representation is
  * used to produce the derived name itself.
  */
private[contract] object SearchValueTypeIdDerivation {

  def derivedImpl[A: Type](using Quotes): Expr[SearchValueTypeId] = {
    import quotes.reflect.*

    val repr = TypeRepr.of[A]

    val nameOrError: Either[String, String] =
      repr match {
        case Refinement(_, _, _) =>
          Left("an anonymous/refined type has no single stable simple source name")
        case _ =>
          val symbol = repr.typeSymbol
          if (!symbol.exists) {
            Left("the type has no resolvable symbol")
          } else if (symbol.isTypeParam) {
            Left("the type is an unresolved type parameter at the macro expansion site")
          } else if (symbol.name.isEmpty || symbol.name.exists(ch => ch == '<' || ch == '$')) {
            Left(s"the resolved symbol name '${symbol.name}' is not a stable simple source name")
          } else {
            Right(symbol.name)
          }
      }

    nameOrError match {
      case Right(name) =>
        '{ SearchValueTypeId(${ Expr(name) }) }
      case Left(reason) =>
        report.errorAndAbort(
          s"SearchValueTypeId.derived[${Type.show[A]}] requires a concrete named type with one stable simple source name: $reason"
        )
    }
  }
}
