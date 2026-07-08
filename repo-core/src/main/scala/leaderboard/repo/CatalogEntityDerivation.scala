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

  /** Compile-time derivation of [[CatalogEntity.Aux]] evidence purely from
    * `A`'s own conventional `id` field - no `RepoEntity` value, repo
    * companion, or hand-written selector required. Derives a fresh
    * [[RepoEntity]] via [[RepoEntity.derived]] and checks the discovered id
    * field type against the requested `K` before building the node; aborts
    * at compile time (naming the entity type, the expected `K`, and the
    * discovered id type when one was found) if there is no conventional `id`
    * field, or if its type does not match `K`. Never falls back to runtime
    * reflection. See [[CatalogEntity.derivedFromId]] for a caveat: this
    * message only reliably surfaces when that given is invoked directly,
    * not when it fails during ordinary given search.
    */
  def derivedGivenImpl[A: Type, K: Type](mirror: Expr[scala.deriving.Mirror.ProductOf[A]])(using Quotes): Expr[CatalogEntity.Aux[A, K]] = {
    import quotes.reflect.*

    val aTpe = TypeRepr.of[A]
    val kTpe = TypeRepr.of[K]

    val idField = aTpe.typeSymbol.fieldMember("id")

    if (idField.isNoSymbol) {
      report.errorAndAbort(
        s"CatalogEntity.Aux[${aTpe.show}, ${kTpe.show}] derivation requires a conventional `id` " +
          s"field on ${aTpe.show}, but none was found."
      )
    }

    val discoveredKeyTpe = aTpe.memberType(idField)

    if (!(discoveredKeyTpe =:= kTpe)) {
      report.errorAndAbort(
        s"CatalogEntity.Aux[${aTpe.show}, ${kTpe.show}] derivation failed: ${aTpe.show}'s `id` " +
          s"field has type ${discoveredKeyTpe.show}, which does not match the expected key type ${kTpe.show}."
      )
    }

    val methodTpe = MethodType(List("value"))(_ => List(aTpe), _ => kTpe)
    val selector =
      Lambda(
        Symbol.spliceOwner,
        methodTpe,
        (_, params) => Select.unique(params.head.asInstanceOf[Term], "id"),
      ).asExprOf[A => K]

    '{ CatalogEntity.from[A, K](RepoEntity.derived[A](using $mirror).node($selector)) }
  }
}
