package leaderboard.search.dsl

import scala.quoted.*

/** Compile-time extraction of a selected field label from a direct field selector, owned by the search
  * DSL so it stays independently movable to a future search-core module.
  *
  * This deliberately does not reuse `leaderboard.repo.RepoFieldMacro`: the search DSL must not depend on
  * repo internals. It mirrors the same narrow selector shape (`_.fieldName`) and rejects anything else at
  * compile time, with no runtime reflection.
  */
object SearchFieldMacro {

  inline def label[A, B](inline selector: A => B): String =
    ${ labelImpl[A, B]('selector) }

  def labelImpl[A: Type, B: Type](selector: Expr[A => B])(using Quotes): Expr[String] = {
    import quotes.reflect.*

    def unwrap(term: Term): Term =
      term match {
        case Inlined(_, _, inner) => unwrap(inner)
        case Block(Nil, inner)    => unwrap(inner)
        case Typed(inner, _)      => unwrap(inner)
        case _                    => term
      }

    val fieldName: Option[String] =
      unwrap(selector.asTerm) match {
        case Lambda(List(param), body) =>
          unwrap(body) match {
            case Select(Ident(qualifier), name) if qualifier == param.name => Some(name)
            case _                                                          => None
          }
        case _ => None
      }

    fieldName match {
      case Some(name) =>
        Expr(name)
      case None =>
        report.errorAndAbort("SearchField selector must be a direct field selection like _.fieldName")
    }
  }
}
