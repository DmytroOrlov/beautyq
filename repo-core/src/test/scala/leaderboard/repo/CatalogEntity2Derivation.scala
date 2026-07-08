package leaderboard.repo

import scala.quoted.*

/** Phase C.2b design spike - NOT a production change.
  *
  * Test-only, BeautyQ-free validating macro for a directly-parameterized
  * entity-evidence typeclass ("Candidate B" from the Phase C.2 design spike).
  * Named `ParamCatalogEntity` rather than `CatalogEntity2`: `CatalogEntity2`
  * already exists in this same package, in
  * `CatalogEntityKeyPropagationDesignSpec.scala`, as a deliberately
  * non-validating stand-in (accepts any `K`, proving nothing). This is a
  * genuinely different, validating implementation, so it needs its own name
  * to coexist rather than collide.
  *
  * Mirrors `CatalogEntity.derivedFromId`/`CatalogEntityDerivation` (repo-core
  * main sources) exactly: discover `A`'s conventional `id` field, abort at
  * compile time if none exists, abort if its type does not match the
  * requested `K`. Never falls back to runtime reflection; no BeautyQ
  * dependency.
  *
  * The macro implementation and the `given` that calls it both live in this
  * file; the call sites (the actual `summon[...]` tests) live in
  * `CatalogEntityParameterizedDesignSpec.scala`. Dotty requires a macro
  * implementation and its expansion call site to compile as separate units -
  * a single file mixing macro definition and macro use (summoning it inside
  * a test body in the same file) fails with "Cyclic macro dependencies",
  * confirmed in the Phase C.2 spike.
  */
trait ParamCatalogEntity[A, K]

private[repo] object ParamCatalogEntityDerivation {

  def derivedImpl[A: Type, K: Type](using Quotes): Expr[ParamCatalogEntity[A, K]] = {
    import quotes.reflect.*

    val aTpe = TypeRepr.of[A]
    val kTpe = TypeRepr.of[K]

    val idField = aTpe.typeSymbol.fieldMember("id")
    if (idField.isNoSymbol) {
      report.errorAndAbort(
        s"ParamCatalogEntity[${aTpe.show}, ${kTpe.show}] derivation requires a conventional `id` " +
          s"field on ${aTpe.show}, but none was found."
      )
    }

    val discoveredKeyTpe = aTpe.memberType(idField)
    if (!(discoveredKeyTpe =:= kTpe)) {
      report.errorAndAbort(
        s"ParamCatalogEntity[${aTpe.show}, ${kTpe.show}] derivation failed: ${aTpe.show}'s `id` " +
          s"field has type ${discoveredKeyTpe.show}, which does not match the expected key type ${kTpe.show}."
      )
    }

    '{ new ParamCatalogEntity[A, K] {} }
  }
}

object ParamCatalogEntity {

  /** Automatic, validating entity evidence for any conventional id-keyed
    * product type - the parameterized ("Candidate B") counterpart to
    * `CatalogEntity.derivedFromId`.
    */
  transparent inline given derivedFromId[A, K](using scala.deriving.Mirror.ProductOf[A]): ParamCatalogEntity[A, K] =
    ${ ParamCatalogEntityDerivation.derivedImpl[A, K] }
}
