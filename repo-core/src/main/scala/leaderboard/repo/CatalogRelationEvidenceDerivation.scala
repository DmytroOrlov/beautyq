package leaderboard.repo

import leaderboard.model.QueryFailure
import leaderboard.repo.RepoOp.{AllValues, ManyByKey, ValueByKey}

import scala.quoted.*

private[repo] object CatalogRelationEvidenceDerivation {

  def rootTreeImpl[F[_, _]: Type, R: Type, A: Type, K: Type](using Quotes): Expr[CatalogRootTree.Aux[F, R, A, K]] = {
    import quotes.reflect.*

    val repositoriesTpe = TypeRepr.of[R]
    val keyTpe          = TypeRepr.of[K]
    val resultTpe       = TypeRepr.of[F[QueryFailure, List[A]]]
    val (fieldSymbol, fieldTpe) = uniqueRepositoryField(
      derivationName = "CatalogRootTree.derivedFromRepositories",
      wrapperName    = "CatalogRootTree",
      repositoriesTpe,
      expectedShape  = s"${keyTpe.show} => ${resultTpe.show}",
      matches        = fieldTpe => singleArgCandidates(fieldTpe, keyTpe, resultTpe),
    )

    fieldTpe.asType match {
      case '[repo] =>
        '{
          new CatalogRootTree[F, R, A] {
            type Key = K
            def load(repositories: R): ManyByKey[F, K, A] =
              ManyByKey.derived[F, repo, K, A](${ Select.unique('repositories.asTerm, fieldSymbol.name).asExprOf[repo] })
          }
        }
    }
  }

  def rootAllImpl[F[_, _]: Type, R: Type, A: Type](using Quotes): Expr[CatalogRootAll[F, R, A]] = {
    import quotes.reflect.*

    val repositoriesTpe = TypeRepr.of[R]
    val resultTpe       = TypeRepr.of[F[QueryFailure, List[A]]]
    val (fieldSymbol, fieldTpe) = uniqueRepositoryField(
      derivationName = "CatalogRootAll.derivedFromRepositories",
      wrapperName    = "CatalogRootAll",
      repositoriesTpe,
      expectedShape  = s"() => ${resultTpe.show}",
      matches        = fieldTpe => noArgCandidates(fieldTpe, resultTpe),
    )

    fieldTpe.asType match {
      case '[repo] =>
        '{
          new CatalogRootAll[F, R, A] {
            def load(repositories: R): AllValues[F, A] =
              AllValues.derived[F, repo, A](${ Select.unique('repositories.asTerm, fieldSymbol.name).asExprOf[repo] })
          }
        }
    }
  }

  def manyImpl[F[_, _]: Type, R: Type, P: Type, C: Type, K: Type](using Quotes): Expr[CatalogMany.Aux[F, R, P, C, K]] = {
    import quotes.reflect.*

    val repositoriesTpe = TypeRepr.of[R]
    val keyTpe          = TypeRepr.of[K]
    val resultTpe       = TypeRepr.of[F[QueryFailure, List[C]]]
    val (fieldSymbol, fieldTpe) = uniqueRepositoryField(
      derivationName = "CatalogMany.derivedFromRepositories",
      wrapperName    = "CatalogMany",
      repositoriesTpe,
      expectedShape  = s"${keyTpe.show} => ${resultTpe.show}",
      matches        = fieldTpe => singleArgCandidates(fieldTpe, keyTpe, resultTpe),
    )

    fieldTpe.asType match {
      case '[repo] =>
        '{
          new CatalogMany[F, R, P, C] {
            type Key = K
            def load(repositories: R): ManyByKey[F, K, C] =
              ManyByKey.derived[F, repo, K, C](${ Select.unique('repositories.asTerm, fieldSymbol.name).asExprOf[repo] })
          }
        }
    }
  }

  def valueEdgeImpl[F[_, _]: Type, R: Type, P: Type, V: Type, K: Type](using Quotes): Expr[CatalogValueEdge.Aux[F, R, P, V, K]] = {
    import quotes.reflect.*

    val repositoriesTpe = TypeRepr.of[R]
    val keyTpe          = TypeRepr.of[K]
    val resultTpe       = TypeRepr.of[F[QueryFailure, V]]
    val (fieldSymbol, fieldTpe) = uniqueRepositoryField(
      derivationName = "CatalogValueEdge.derivedFromRepositories",
      wrapperName    = "CatalogValueEdge",
      repositoriesTpe,
      expectedShape  = s"${keyTpe.show} => ${resultTpe.show}",
      matches        = fieldTpe => singleArgCandidates(fieldTpe, keyTpe, resultTpe),
    )

    fieldTpe.asType match {
      case '[repo] =>
        '{
          new CatalogValueEdge[F, R, P, V] {
            type Key = K
            def load(repositories: R): ValueByKey[F, K, V] =
              ValueByKey.derived[F, repo, K, V](${ Select.unique('repositories.asTerm, fieldSymbol.name).asExprOf[repo] })
          }
        }
    }
  }

  private def singleArgCandidates(using quotes: Quotes)(
    fieldTpe: quotes.reflect.TypeRepr,
    keyTpe: quotes.reflect.TypeRepr,
    resultTpe: quotes.reflect.TypeRepr,
  ): List[quotes.reflect.Symbol] = {
    import quotes.reflect.*

    fieldTpe.typeSymbol.declaredMethods.filter {
      sym =>
        fieldTpe.memberType(sym) match {
          case mt: MethodType =>
            mt.paramTypes match {
              case List(paramTpe) => paramTpe =:= keyTpe && mt.resType =:= resultTpe
              case _              => false
            }
          case _ => false
        }
    }
  }

  private def noArgCandidates(using quotes: Quotes)(
    fieldTpe: quotes.reflect.TypeRepr,
    resultTpe: quotes.reflect.TypeRepr,
  ): List[quotes.reflect.Symbol] = {
    import quotes.reflect.*

    fieldTpe.typeSymbol.declaredMethods.filter {
      sym =>
        fieldTpe.memberType(sym) match {
          case mt: MethodType => mt.paramTypes.isEmpty && mt.resType =:= resultTpe
          case _              => false
        }
    }
  }

  private def uniqueRepositoryField(using quotes: Quotes)(
    derivationName: String,
    wrapperName: String,
    repositoriesTpe: quotes.reflect.TypeRepr,
    expectedShape: String,
    matches: quotes.reflect.TypeRepr => List[quotes.reflect.Symbol],
  ): (quotes.reflect.Symbol, quotes.reflect.TypeRepr) = {
    import quotes.reflect.*

    val fields = repositoriesTpe.typeSymbol.caseFields.map {
      field =>
        field -> repositoriesTpe.memberType(field)
    }
    val allMatches = fields.flatMap {
      case (field, fieldTpe) =>
        matches(fieldTpe) match {
          case Nil     => Nil
          case methods => methods.map(method => (field, fieldTpe, method))
        }
    }

    allMatches match {
      case List((field, fieldTpe, _)) =>
        field -> fieldTpe
      case Nil =>
        report.errorAndAbort(
          s"$derivationName expected exactly one repository field on ${repositoriesTpe.show} " +
            s"with a method shaped $expectedShape, but found none. " +
            s"Use explicit $wrapperName evidence if the repositories bundle intentionally has no unambiguous matching loader."
        )
      case many =>
        val candidates = many.map {
          case (field, _, method) => s"${field.name}.${method.name}"
        }.mkString(", ")
        report.errorAndAbort(
          s"$derivationName expected exactly one repository field on ${repositoriesTpe.show} " +
            s"with a method shaped $expectedShape, but found: $candidates. " +
            s"Use explicit $wrapperName evidence when repository methods are intentionally ambiguous."
        )
    }
  }
}
