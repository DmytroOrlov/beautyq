package leaderboard.search.gen2.contract

/** A domain's one executable owner of a facet's identity and actual [[FacetRequest]]. A domain declares
  * this once per facet; there is no second, manually maintained facet-ID list or lookup map anywhere
  * else. Field handles are never repeated here - [[FacetRequest]] already carries them, and
  * [[fieldHandles]] derives them from it for any reviewer/presentation view that needs them.
  */
final case class FacetPlanDeclaration[Document](
  request: FacetRequest[Document]
) extends PublicFacetSpec[FacetId] {
  def id: FacetId = request.id
  def fieldHandles: Vector[SearchField[Document, ?]] = FacetRequest.fieldHandles(request)
}

/** One error hierarchy for declaration-time facet-policy validity and request-time requested-facet
  * resolution. The request resolver narrows its error type to [[UnknownRequestedFacet]] because a
  * registry has already rejected declaration errors before it becomes usable. */
sealed trait FacetPlanRegistryError

object FacetPlanRegistryError {
  final case class DuplicateFacetId(id: FacetId, firstIndex: Int, duplicateIndex: Int) extends FacetPlanRegistryError
  final case class InvalidFacetDeclaration(id: FacetId, index: Int, error: FacetRequestError) extends FacetPlanRegistryError
  final case class UnknownRequestedFacet(id: FacetId) extends FacetPlanRegistryError
}

/** A validated, ordered registry of one domain's [[FacetPlanDeclaration]]s: the public facet inventory
  * (built on the generic [[PublicFacetRegistry]]) and requested-facet resolution both derive from the
  * same declarations, so a domain never writes its own facet-ID lookup map. Construction validates every
  * declaration's [[FacetRequest]] and rejects duplicate facet IDs; an invalid or duplicated declaration
  * never reaches a caller as a usable registry.
  */
final class FacetPlanRegistry[Document] private (declarations: Vector[FacetPlanDeclaration[Document]]) {
  val ids: Vector[FacetId] = declarations.map(_.id)

  val publicFacets: PublicFacetRegistry[FacetId, FacetPlanDeclaration[Document]] = PublicFacetRegistry(declarations)

  private val requestById: Map[FacetId, FacetRequest[Document]] = declarations.map(declaration => declaration.id -> declaration.request).toMap

  def contains(id: FacetId): Boolean = publicFacets.contains(id)

  /** Resolves requested facet IDs into their actual [[FacetRequest]] values, preserving request order.
    * An unknown ID is a typed error rather than a silently dropped facet.
    */
  def resolve(requested: Vector[FacetId]): Either[NonEmptyErrors[FacetPlanRegistryError.UnknownRequestedFacet], Vector[FacetRequest[Document]]] = {
    val errors = requested.flatMap(id => if (requestById.contains(id)) Vector.empty else Vector(FacetPlanRegistryError.UnknownRequestedFacet(id)))
    NonEmptyErrors.fromVector(errors) match {
      case Some(nonEmptyErrors) => Left(nonEmptyErrors)
      case None                 => Right(requested.flatMap(requestById.get))
    }
  }
}

object FacetPlanRegistry {
  def apply[Document](declarations: Vector[FacetPlanDeclaration[Document]]): Either[NonEmptyErrors[FacetPlanRegistryError], FacetPlanRegistry[Document]] = {
    val declarationErrors =
      declarations.zipWithIndex.flatMap { case (declaration, index) =>
        FacetRequest.validate(declaration.request) match {
          case Left(errors) => errors.toVector.map(error => FacetPlanRegistryError.InvalidFacetDeclaration(declaration.id, index, error))
          case Right(_)     => Vector.empty
        }
      }

    val duplicateErrors = duplicateDeclarationErrors(declarations)

    NonEmptyErrors.fromVector(declarationErrors ++ duplicateErrors) match {
      case Some(errors) => Left(errors)
      case None         => Right(new FacetPlanRegistry(declarations))
    }
  }

  /** For static domain-policy declarations only, where an invalid/duplicated declaration is a broken
    * source invariant to fail loudly on, never a runtime input case. Domain files must call this rather
    * than hand-roll their own `apply(...)` match/throw; the thrown message names every offending
    * declaration's facet ID, index and validation error.
    */
  def unsafeFrom[Document](declarations: Vector[FacetPlanDeclaration[Document]]): FacetPlanRegistry[Document] =
    apply(declarations) match {
      case Right(registry) => registry
      case Left(errors)    => throw new IllegalStateException(errors.toVector.map(renderError).mkString("invalid facet-plan declarations: ", "; ", ""))
    }

  private def renderError(error: FacetPlanRegistryError): String =
    error match {
      case FacetPlanRegistryError.DuplicateFacetId(id, firstIndex, duplicateIndex) =>
        s"DuplicateFacetId(id=${id.value}, firstIndex=$firstIndex, duplicateIndex=$duplicateIndex)"
      case FacetPlanRegistryError.InvalidFacetDeclaration(id, index, validationError) =>
        s"InvalidFacetDeclaration(id=${id.value}, index=$index, error=$validationError)"
      case FacetPlanRegistryError.UnknownRequestedFacet(id) =>
        s"UnknownRequestedFacet(id=${id.value})"
    }

  private def duplicateDeclarationErrors[Document](declarations: Vector[FacetPlanDeclaration[Document]]): Vector[FacetPlanRegistryError] = {
    val (_, errors) =
      declarations.zipWithIndex.foldLeft((Map.empty[FacetId, Int], Vector.empty[FacetPlanRegistryError])) {
        case ((firstIndexes, errors), (declaration, index)) =>
          firstIndexes.get(declaration.id) match {
            case Some(firstIndex) => (firstIndexes, errors :+ FacetPlanRegistryError.DuplicateFacetId(declaration.id, firstIndex, index))
            case None             => (firstIndexes.updated(declaration.id, index), errors)
          }
      }
    errors
  }
}
