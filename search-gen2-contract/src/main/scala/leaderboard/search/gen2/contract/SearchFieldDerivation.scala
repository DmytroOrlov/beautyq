package leaderboard.search.gen2.contract

import scala.quoted.*

/** Compile-time extraction of a selected field label from a direct field selector (`_.fieldName`),
  * owned independently by this Gen2 contract module so it stays free of any Gen1 dependency. Rejects
  * anything else - nested selectors, method calls, or expressions - at compile time, with no runtime
  * reflection. `computedField` is the only Commit 2 API for a path that is not a direct selector.
  */
private[contract] object SearchFieldDerivation {

  inline def path[Document, Value](inline selector: Document => Value): String =
    ${ pathImpl[Document, Value]('selector) }

  private def pathImpl[Document: Type, Value: Type](selector: Expr[Document => Value])(using Quotes): Expr[String] = {
    import quotes.reflect.*

    def unwrap(term: Term): Term =
      term match {
        case Inlined(_, _, inner) => unwrap(inner)
        case Block(Nil, inner)    => unwrap(inner)
        case Typed(inner, _)      => unwrap(inner)
        case _                    => term
      }

    // A case-class constructor field and a parameterless computed `def` both parse to the exact
    // same `Select(Ident(param), name)` shape, so the shape alone cannot tell them apart. The
    // selected symbol's flags can: a constructor-parameter-derived accessor (case class or plain
    // `class` `val`/`var` parameter) carries CaseAccessor and/or ParamAccessor; an ordinary method
    // with a body carries neither, however it dresses up syntactically.
    def isStoredFieldSymbol(symbol: Symbol): Boolean =
      symbol.flags.is(Flags.CaseAccessor) || symbol.flags.is(Flags.ParamAccessor)

    val fieldName: Option[String] =
      unwrap(selector.asTerm) match {
        case Lambda(List(param), body) =>
          unwrap(body) match {
            case select @ Select(Ident(qualifier), name) if qualifier == param.name && isStoredFieldSymbol(select.symbol) =>
              Some(name)
            case _ =>
              None
          }
        case _ => None
      }

    fieldName match {
      case Some(name) =>
        Expr(name)
      case None =>
        report.errorAndAbort(
          "Search field selector must be a direct field selection like _.fieldName; use computedField for nested, computed, or dynamic paths"
        )
    }
  }
}
