package leaderboard.repo

import leaderboard.model.QueryFailure

import scala.quoted.*

/** Compile-time derivation of [[RepoOp]] adapter bodies from a repo's own
  * method signatures. Matches a target operation shape
  * (`K => F[QueryFailure, Option[A]]`, `K => F[QueryFailure, List[A]]`,
  * `K => F[QueryFailure, A]`, or `() => F[QueryFailure, List[A]]`) against
  * every method declared directly on `Repo`, by full parameter-key-type and
  * result-type equality - never by method name, and never by child-entity
  * type alone. A repo with two many-by-key methods for distinct nominal key
  * types disambiguates correctly because the key type is part of the match, not
  * just the result element type. Repos whose keys are transparent aliases of
  * the same underlying type remain ambiguous and must keep explicit wrappers
  * or move to nominal/opaque keys. Aborts at compile time on zero or more than
  * one match. Never falls back to runtime reflection.
  */
private[repo] object RepoOpDerivation {

  def optionalByKeyImpl[F[_, _]: Type, Repo: Type, K: Type, A: Type](repo: Expr[Repo])(using Quotes): Expr[RepoOp.OptionalByKey[F, K, A]] = {
    import quotes.reflect.*

    val repoTpe   = TypeRepr.of[Repo]
    val keyTpe    = TypeRepr.of[K]
    val resultTpe = TypeRepr.of[F[QueryFailure, Option[A]]]

    val method = uniqueSingleArgMethod("RepoOp.OptionalByKey.derived", "OptionalByKey", repoTpe, keyTpe, resultTpe)

    val run = Select.unique(repo.asTerm, method.name).etaExpand(Symbol.spliceOwner).asExprOf[K => F[QueryFailure, Option[A]]]
    '{ RepoOp.OptionalByKey[F, K, A]($run) }
  }

  def manyByKeyImpl[F[_, _]: Type, Repo: Type, K: Type, A: Type](repo: Expr[Repo])(using Quotes): Expr[RepoOp.ManyByKey[F, K, A]] = {
    import quotes.reflect.*

    val repoTpe   = TypeRepr.of[Repo]
    val keyTpe    = TypeRepr.of[K]
    val resultTpe = TypeRepr.of[F[QueryFailure, List[A]]]

    val method = uniqueSingleArgMethod("RepoOp.ManyByKey.derived", "ManyByKey", repoTpe, keyTpe, resultTpe)

    val run = Select.unique(repo.asTerm, method.name).etaExpand(Symbol.spliceOwner).asExprOf[K => F[QueryFailure, List[A]]]
    '{ RepoOp.ManyByKey[F, K, A]($run) }
  }

  def valueByKeyImpl[F[_, _]: Type, Repo: Type, K: Type, A: Type](repo: Expr[Repo])(using Quotes): Expr[RepoOp.ValueByKey[F, K, A]] = {
    import quotes.reflect.*

    val repoTpe   = TypeRepr.of[Repo]
    val keyTpe    = TypeRepr.of[K]
    val resultTpe = TypeRepr.of[F[QueryFailure, A]]

    val method = uniqueSingleArgMethod("RepoOp.ValueByKey.derived", "ValueByKey", repoTpe, keyTpe, resultTpe)

    val run = Select.unique(repo.asTerm, method.name).etaExpand(Symbol.spliceOwner).asExprOf[K => F[QueryFailure, A]]
    '{ RepoOp.ValueByKey[F, K, A]($run) }
  }

  def allValuesImpl[F[_, _]: Type, Repo: Type, A: Type](repo: Expr[Repo])(using Quotes): Expr[RepoOp.AllValues[F, A]] = {
    import quotes.reflect.*

    val repoTpe   = TypeRepr.of[Repo]
    val resultTpe = TypeRepr.of[F[QueryFailure, List[A]]]

    val candidates = repoTpe.typeSymbol.declaredMethods.filter {
      sym =>
        repoTpe.memberType(sym) match {
          case mt: MethodType => mt.paramTypes.isEmpty && mt.resType =:= resultTpe
          case _              => false
        }
    }

    val method = uniqueMethod("RepoOp.AllValues.derived", "AllValues", repoTpe, s"() => ${resultTpe.show}", candidates)

    val run = Select.unique(repo.asTerm, method.name).appliedToNone.asExprOf[F[QueryFailure, List[A]]]
    '{ RepoOp.AllValues[F, A](() => $run) }
  }

  private def uniqueSingleArgMethod(using quotes: Quotes)(
    opName: String,
    wrapperName: String,
    repoTpe: quotes.reflect.TypeRepr,
    keyTpe: quotes.reflect.TypeRepr,
    resultTpe: quotes.reflect.TypeRepr,
  ): quotes.reflect.Symbol = {
    import quotes.reflect.*

    val candidates = repoTpe.typeSymbol.declaredMethods.filter {
      sym =>
        repoTpe.memberType(sym) match {
          case mt: MethodType =>
            mt.paramTypes match {
              case List(paramTpe) => paramTpe =:= keyTpe && mt.resType =:= resultTpe
              case _               => false
            }
          case _ => false
        }
    }

    uniqueMethod(opName, wrapperName, repoTpe, s"${keyTpe.show} => ${resultTpe.show}", candidates)
  }

  private def uniqueMethod(using quotes: Quotes)(
    opName: String,
    wrapperName: String,
    repoTpe: quotes.reflect.TypeRepr,
    expectedShape: String,
    candidates: List[quotes.reflect.Symbol],
  ): quotes.reflect.Symbol = {
    import quotes.reflect.*

    candidates match {
      case List(single) =>
        single
      case Nil =>
        report.errorAndAbort(
          s"$opName expected exactly one method on ${repoTpe.show} " +
            s"with shape $expectedShape, but found none. " +
            s"Use an explicit $wrapperName wrapper if the repo is intentionally ambiguous or has no matching method."
        )
      case many =>
        report.errorAndAbort(
          s"$opName expected exactly one method on ${repoTpe.show} " +
            s"with shape $expectedShape, but found: ${many.map(_.name).mkString(", ")}. " +
            s"Use an explicit $wrapperName wrapper if the repo is intentionally ambiguous."
        )
    }
  }
}
