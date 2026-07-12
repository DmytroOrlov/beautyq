package leaderboard.search.gen2.contract

sealed trait SearchDeclarationError

final case class InvalidSearchDocumentId(value: String) extends SearchDeclarationError
final case class InvalidFieldId(fieldId: FieldId) extends SearchDeclarationError
final case class InvalidFieldPath(fieldId: FieldId, path: FieldPath) extends SearchDeclarationError
final case class InvalidFieldSemantic(fieldId: FieldId, semantic: FieldSemantic) extends SearchDeclarationError
final case class InvalidSearchValueTypeId(fieldId: FieldId, typeId: SearchValueTypeId) extends SearchDeclarationError
final case class EmptySearchDocument(documentId: SearchDocumentId) extends SearchDeclarationError
final case class IdentityFieldMustBeRequired(fieldId: FieldId) extends SearchDeclarationError
final case class IdentityFieldRepeated(fieldId: FieldId) extends SearchDeclarationError
final case class RepeatedFieldHandle(fieldId: FieldId, firstIndex: Int, repeatedIndex: Int) extends SearchDeclarationError
final case class DuplicateFieldId(fieldId: FieldId) extends SearchDeclarationError
final case class DuplicateFieldPath(path: FieldPath) extends SearchDeclarationError
final case class IncompatibleFieldCapabilities(
  fieldId: FieldId,
  kind: SearchFieldKind,
  violations: Vector[CapabilityViolation],
) extends SearchDeclarationError

sealed trait CapabilityViolation

object CapabilityViolation {
  case object SearchableRequiresText extends CapabilityViolation

  final case class UnsupportedFilterOperator(operator: FilterOperator) extends CapabilityViolation
  final case class UnsupportedFacetMode(mode: FacetMode) extends CapabilityViolation
  final case class UnsupportedSortMode(mode: SortMode) extends CapabilityViolation
  final case class UnsupportedGroupMode(mode: GroupMode) extends CapabilityViolation
}

type SearchDeclarationErrors = NonEmptyErrors[SearchDeclarationError]

object SearchDeclarationErrors {
  private[contract] def apply(
    head: SearchDeclarationError,
    tail: Vector[SearchDeclarationError],
  ): SearchDeclarationErrors = NonEmptyErrors.fromHead(head, tail)
}

enum FieldPresence {
  case Required
  case Optional
}

final case class SearchFieldStructure(
  id: FieldId,
  path: FieldPath,
  kind: SearchFieldKind,
  valueType: SearchValueTypeId,
  semantic: Option[FieldSemantic],
  presence: FieldPresence,
  capabilities: FieldCapabilities,
)

final case class SearchDocumentStructure(
  id: SearchDocumentId,
  identity: SearchFieldStructure,
  fields: Vector[SearchFieldStructure],
)

/** A validated, immutable document declaration. `identity` is the distinguished document identity
  * field (always required extraction, never repeated in `fields`); `fields` is the ordinary field
  * list, in declared order. The only construction path is [[SearchDocumentDeclaration.validate]],
  * reached directly or through the [[searchDocument]] builder.
  */
final case class SearchDocumentDeclaration[Document, Id] private (
  id: SearchDocumentId,
  identity: SearchField[Document, Id],
  fields: Vector[SearchField[Document, ?]],
) {
  def allFields: Vector[SearchField[Document, ?]] = identity +: fields

  def structure: SearchDocumentStructure =
    SearchDocumentStructure(
      id = id,
      identity = SearchDocumentDeclaration.fieldStructure(identity),
      fields = fields.map(SearchDocumentDeclaration.fieldStructure),
    )

  def renderStructure: String = SearchStructureRenderer.renderDocument(structure)
}

object SearchDocumentDeclaration {

  def validate[Document, Id](
    id: SearchDocumentId,
    identity: SearchField[Document, Id],
    fields: Vector[SearchField[Document, ?]],
  ): Either[SearchDeclarationErrors, SearchDocumentDeclaration[Document, Id]] =
    collectErrors(id, identity, fields) match {
      case head +: tail => Left(SearchDeclarationErrors(head, tail))
      case _            => Right(new SearchDocumentDeclaration[Document, Id](id, identity, fields))
    }

  // Deterministic error order: document ID, empty document, identity-required, identity repeated in
  // fields, per-field validation in allFields order, repeated ordinary handles, duplicate IDs,
  // duplicate paths.
  private def collectErrors[Document, Id](
    documentId: SearchDocumentId,
    identity: SearchField[Document, Id],
    fields: Vector[SearchField[Document, ?]],
  ): Vector[SearchDeclarationError] = {
    val allFields = identity +: fields

    val documentIdErrors: Vector[SearchDeclarationError] =
      if (StableName.isValid(documentId.value)) Vector.empty else Vector(InvalidSearchDocumentId(documentId.value))

    val emptyDocumentErrors: Vector[SearchDeclarationError] =
      if (fields.isEmpty) Vector(EmptySearchDocument(documentId)) else Vector.empty

    val identityRequiredErrors: Vector[SearchDeclarationError] =
      if (identity.required) Vector.empty else Vector(IdentityFieldMustBeRequired(identity.id))

    val identityRepeatedErrors: Vector[SearchDeclarationError] =
      if (fields.exists(_ eq identity)) Vector(IdentityFieldRepeated(identity.id)) else Vector.empty

    val perFieldErrors: Vector[SearchDeclarationError] =
      allFields.flatMap(fieldValidationErrors)

    val repeatedHandleErrors: Vector[SearchDeclarationError] =
      fields.zipWithIndex.flatMap { case (candidate, index) =>
        fields.take(index).indexWhere(_ eq candidate) match {
          case -1        => Vector.empty
          case firstIndex => Vector(RepeatedFieldHandle(candidate.id, firstIndex, index))
        }
      }

    val duplicateIdErrors: Vector[SearchDeclarationError] =
      firstDuplicateKeysInOrder(allFields.map(f => f -> f.id)).map(DuplicateFieldId.apply)

    val duplicatePathErrors: Vector[SearchDeclarationError] =
      firstDuplicateKeysInOrder(allFields.map(f => f -> f.path)).map(DuplicateFieldPath.apply)

    documentIdErrors ++
      emptyDocumentErrors ++
      identityRequiredErrors ++
      identityRepeatedErrors ++
      perFieldErrors ++
      repeatedHandleErrors ++
      duplicateIdErrors ++
      duplicatePathErrors
  }

  private def fieldValidationErrors[Document](field: SearchField[Document, ?]): Vector[SearchDeclarationError] = {
    val idErrors: Vector[SearchDeclarationError] =
      if (StableName.isValid(field.id.value)) Vector.empty else Vector(InvalidFieldId(field.id))

    val pathErrors: Vector[SearchDeclarationError] =
      if (StableName.isValid(field.path.value)) Vector.empty else Vector(InvalidFieldPath(field.id, field.path))

    val semanticErrors: Vector[SearchDeclarationError] =
      field.semantic match {
        case Some(semantic) if !StableName.isValid(semantic.value) => Vector(InvalidFieldSemantic(field.id, semantic))
        case _                                                     => Vector.empty
      }

    val typeIdErrors: Vector[SearchDeclarationError] =
      if (StableName.isValid(field.codec.typeId.value)) Vector.empty else Vector(InvalidSearchValueTypeId(field.id, field.codec.typeId))

    val capabilityErrors: Vector[SearchDeclarationError] =
      capabilityViolations(field.kind, field.capabilities) match {
        case violations if violations.isEmpty => Vector.empty
        case violations                       => Vector(IncompatibleFieldCapabilities(field.id, field.kind, violations))
      }

    idErrors ++ pathErrors ++ semanticErrors ++ typeIdErrors ++ capabilityErrors
  }

  // Finds, for each distinct key K, the position (scanning `items` left to right) at which a second
  // field object *not* `eq` to an already-seen field for that key first appears, and reports the key
  // once at that position. Two or more `eq`-identical handles sharing a key never count as a
  // duplicate here (that is RepeatedFieldHandle/IdentityFieldRepeated's concern instead).
  private def firstDuplicateKeysInOrder[Document, K](items: Vector[(SearchField[Document, ?], K)]): Vector[K] = {
    final case class State(seenByKey: Map[K, Vector[SearchField[Document, ?]]], reported: Set[K], order: Vector[K])

    val finalState = items.foldLeft(State(Map.empty, Set.empty, Vector.empty)) { case (state, (field, key)) =>
      val priorForKey        = state.seenByKey.getOrElse(key, Vector.empty)
      val distinctPriorExists = priorForKey.exists(other => !(other eq field))
      val nextSeenByKey       = state.seenByKey.updated(key, priorForKey :+ field)

      if (distinctPriorExists && !state.reported.contains(key)) {
        State(nextSeenByKey, state.reported + key, state.order :+ key)
      } else {
        State(nextSeenByKey, state.reported, state.order)
      }
    }

    finalState.order
  }

  private val allowedFilterOperators: Map[FilterOperator, Set[SearchFieldKind]] = Map(
    FilterOperator.Equal -> Set(
      SearchFieldKind.Keyword,
      SearchFieldKind.Integer,
      SearchFieldKind.Long,
      SearchFieldKind.Decimal,
      SearchFieldKind.Boolean,
    ),
    FilterOperator.In -> Set(
      SearchFieldKind.Keyword,
      SearchFieldKind.Integer,
      SearchFieldKind.Long,
      SearchFieldKind.Decimal,
      SearchFieldKind.Boolean,
    ),
    FilterOperator.Range -> Set(SearchFieldKind.Integer, SearchFieldKind.Long, SearchFieldKind.Decimal, SearchFieldKind.DateTime),
    FilterOperator.GeoDistance -> Set(SearchFieldKind.GeoPoint),
  )

  private val allowedFacetModes: Map[FacetMode, Set[SearchFieldKind]] = Map(
    FacetMode.Terms -> Set(SearchFieldKind.Keyword, SearchFieldKind.Integer, SearchFieldKind.Long, SearchFieldKind.Boolean),
    FacetMode.Range -> Set(SearchFieldKind.Integer, SearchFieldKind.Long, SearchFieldKind.Decimal, SearchFieldKind.DateTime),
  )

  private val allowedSortModes: Map[SortMode, Set[SearchFieldKind]] = Map(
    SortMode.Value -> Set(SearchFieldKind.Keyword, SearchFieldKind.Integer, SearchFieldKind.Long, SearchFieldKind.Decimal, SearchFieldKind.DateTime),
    SortMode.Distance -> Set(SearchFieldKind.GeoPoint),
  )

  private val allowedGroupModes: Map[GroupMode, Set[SearchFieldKind]] = Map(
    GroupMode.Terms -> Set(SearchFieldKind.Keyword),
  )

  private def capabilityViolations(kind: SearchFieldKind, capabilities: FieldCapabilities): Vector[CapabilityViolation] = {
    val searchableViolation: Vector[CapabilityViolation] =
      if (capabilities.searchable && kind != SearchFieldKind.Text) Vector(CapabilityViolation.SearchableRequiresText) else Vector.empty

    val filterViolations = capabilities.filterOperators.toVector.sortBy(_.ordinal).flatMap { operator =>
      if (allowedFilterOperators.getOrElse(operator, Set.empty).contains(kind)) Vector.empty
      else Vector(CapabilityViolation.UnsupportedFilterOperator(operator))
    }

    val facetViolations = capabilities.facetModes.toVector.sortBy(_.ordinal).flatMap { mode =>
      if (allowedFacetModes.getOrElse(mode, Set.empty).contains(kind)) Vector.empty
      else Vector(CapabilityViolation.UnsupportedFacetMode(mode))
    }

    val sortViolations = capabilities.sortModes.toVector.sortBy(_.ordinal).flatMap { mode =>
      if (allowedSortModes.getOrElse(mode, Set.empty).contains(kind)) Vector.empty
      else Vector(CapabilityViolation.UnsupportedSortMode(mode))
    }

    val groupViolations = capabilities.groupModes.toVector.sortBy(_.ordinal).flatMap { mode =>
      if (allowedGroupModes.getOrElse(mode, Set.empty).contains(kind)) Vector.empty
      else Vector(CapabilityViolation.UnsupportedGroupMode(mode))
    }

    searchableViolation ++ filterViolations ++ facetViolations ++ sortViolations ++ groupViolations
  }

  private def fieldStructure[Document](field: SearchField[Document, ?]): SearchFieldStructure =
    SearchFieldStructure(
      id = field.id,
      path = field.path,
      kind = field.kind,
      valueType = field.codec.typeId,
      semantic = field.semantic,
      presence = if (field.required) FieldPresence.Required else FieldPresence.Optional,
      capabilities = field.capabilities,
    )

}

def searchDocument[Document](id: String): SearchDocumentIdentityBuilder[Document] =
  new SearchDocumentIdentityBuilder[Document](SearchDocumentId(id))

final class SearchDocumentIdentityBuilder[Document] private[contract] (documentId: SearchDocumentId) {
  def id[Id](field: SearchField[Document, Id]): SearchDocumentFieldsBuilder[Document, Id] =
    new SearchDocumentFieldsBuilder[Document, Id](documentId, field, Vector.empty)
}

final class SearchDocumentFieldsBuilder[Document, Id] private[contract] (
  documentId: SearchDocumentId,
  identity: SearchField[Document, Id],
  fields: Vector[SearchField[Document, ?]],
) {
  def field[Value](field: SearchField[Document, Value]): SearchDocumentFieldsBuilder[Document, Id] =
    new SearchDocumentFieldsBuilder[Document, Id](documentId, identity, fields :+ field)

  def build: Either[SearchDeclarationErrors, SearchDocumentDeclaration[Document, Id]] =
    SearchDocumentDeclaration.validate(documentId, identity, fields)
}
