package leaderboard.repo

import scala.quoted.*

/** Compile-time derivation of [[CatalogEntity]] evidence from a model's
  * conventional `id` field, so infrastructure never has to spell out
  * `_.id` by hand. Fails to compile if `A` has no `id` field; never falls
  * back to runtime reflection, structural typing, or unsafe casts.
  */
private[repo] object CatalogEntityDerivation {

  def derivedImpl[A: Type](entity: Expr[RepoEntity[A]])(using Quotes): Expr[CatalogEntity[A]] = {
    import quotes.reflect.*

    val aTpe    = TypeRepr.of[A]
    val idField = aTpe.typeSymbol.fieldMember("id")

    if (idField.isNoSymbol) {
      report.errorAndAbort(
        s"CatalogEntity.derived requires a conventional `id` field on ${aTpe.show}, but none was found."
      )
    }

    val keyTpe = aTpe.memberType(idField)

    keyTpe.asType match {
      case '[k] =>
        val methodTpe = MethodType(List("value"))(_ => List(aTpe), _ => keyTpe)
        val selector =
          Lambda(
            Symbol.spliceOwner,
            methodTpe,
            (_, params) => Select.unique(params.head.asInstanceOf[Term], "id"),
          ).asExprOf[A => k]

        '{ CatalogEntity.from[A, k]($entity.node($selector)) }
    }
  }
}
