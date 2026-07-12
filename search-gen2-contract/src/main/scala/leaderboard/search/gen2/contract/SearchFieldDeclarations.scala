package leaderboard.search.gen2.contract

import java.time.Instant
import scala.annotation.tailrec
import scala.collection.mutable
import scala.compiletime.{constValue, erasedValue}
import scala.deriving.Mirror

/** Starts a low-boilerplate, per-document field authoring registry: the business/domain author
  * declares only the document case class, catalog topology, String keyword-vs-text choice, field
  * capabilities, identity selection, and dynamic definition inventories; this registry derives every
  * mechanical piece - logical codecs via [[SearchValueCodec]] givens, default [[SearchFieldKind]] via
  * [[DefaultSearchFieldKind]], [[FieldPath]]/default [[FieldId]]/default [[FieldSemantic]] via the same
  * direct-selector macro [[field]] already uses, required/optional extraction, declaration order,
  * identity exclusion, and document construction.
  */
def searchFields[Document](documentId: String): SearchFieldDeclarations[Document] =
  new SearchFieldDeclarations[Document](documentId)

/** A controlled build-time field registry for one document type. Uses private mutable storage only
  * during the declaring object's own initialization, appended to exclusively by
  * [[SearchFieldDraft.declare]]/[[DynamicFieldCapabilityDraft.declare]]; every public result
  * ([[staticFields]], [[dynamicFields]], [[allFields]]) is an immutable `Vector` snapshot, never the
  * live buffer itself. The only construction path is [[searchFields]].
  */
final class SearchFieldDeclarations[Document] private[contract] (documentId: String) {
  private val staticBuffer: mutable.ArrayBuffer[SearchField[Document, ?]]  = mutable.ArrayBuffer.empty
  private val dynamicBuffer: mutable.ArrayBuffer[SearchField[Document, ?]] = mutable.ArrayBuffer.empty
  private val ignoredBuffer: mutable.ArrayBuffer[String]                   = mutable.ArrayBuffer.empty
  private var frozen: Boolean                                             = false

  private[contract] def registerStatic(field: SearchField[Document, ?]): Unit = {
    requireNotFrozen()
    staticBuffer += field
  }

  private[contract] def registerDynamic(field: SearchField[Document, ?]): Unit = {
    requireNotFrozen()
    dynamicBuffer += field
  }

  private def requireNotFrozen(): Unit =
    if (frozen) {
      throw new IllegalStateException(
        s"SearchFieldDeclarations for document '$documentId' is already frozen by an earlier .document(...)/.validateDocument(...) call; no further field may be declared"
      )
    }

  /** Immutable snapshot of every direct field declared so far, in declaration order (identity included). */
  def staticFields: Vector[SearchField[Document, ?]] = staticBuffer.toVector

  /** Immutable snapshot of every dynamic field declared so far, in declaration order. */
  def dynamicFields: Vector[SearchField[Document, ?]] = dynamicBuffer.toVector

  /** Always `staticFields ++ dynamicFields`. */
  def allFields: Vector[SearchField[Document, ?]] = staticFields ++ dynamicFields

  /** Declares a direct field whose [[SearchFieldKind]] is derived from [[DefaultSearchFieldKind]]
    * evidence for `Value` - never for a raw `String`, which has no default instance.
    */
  inline def inferred[Value](
    inline selector: Document => Value
  )(using codec: SearchValueCodec[Value], kind: DefaultSearchFieldKind[Value]): SearchFieldDraft[Document, Value] =
    SearchFieldDraft.forDirect[Document, Value](this, SearchFieldDerivation.path[Document, Value](selector), selector, kind.kind, codec)

  /** Declares a direct field of any codec-mapped value type, explicitly as [[SearchFieldKind.Keyword]]. */
  inline def keyword[Value](
    inline selector: Document => Value
  )(using codec: SearchValueCodec[Value]): SearchFieldDraft[Document, Value] =
    SearchFieldDraft.forDirect[Document, Value](this, SearchFieldDerivation.path[Document, Value](selector), selector, SearchFieldKind.Keyword, codec)

  /** Declares a direct `String` field explicitly as [[SearchFieldKind.Text]] - keyword versus text for
    * a raw `String` field is business policy and is never inferred.
    */
  inline def text(inline selector: Document => String): SearchFieldDraft[Document, String] =
    SearchFieldDraft.forDirect[Document, String](this, SearchFieldDerivation.path[Document, String](selector), selector, SearchFieldKind.Text, SearchValueCodec.string)

  inline def integer(inline selector: Document => Int): SearchFieldDraft[Document, Int] =
    SearchFieldDraft.forDirect[Document, Int](this, SearchFieldDerivation.path[Document, Int](selector), selector, SearchFieldKind.Integer, SearchValueCodec.int)

  inline def long(inline selector: Document => Long): SearchFieldDraft[Document, Long] =
    SearchFieldDraft.forDirect[Document, Long](this, SearchFieldDerivation.path[Document, Long](selector), selector, SearchFieldKind.Long, SearchValueCodec.long)

  inline def dateTime(inline selector: Document => Instant): SearchFieldDraft[Document, Instant] =
    SearchFieldDraft.forDirect[Document, Instant](
      this,
      SearchFieldDerivation.path[Document, Instant](selector),
      selector,
      SearchFieldKind.DateTime,
      SearchValueCodec.instant,
    )

  inline def decimal(inline selector: Document => BigDecimal): SearchFieldDraft[Document, BigDecimal] =
    SearchFieldDraft.forDirect[Document, BigDecimal](
      this,
      SearchFieldDerivation.path[Document, BigDecimal](selector),
      selector,
      SearchFieldKind.Decimal,
      SearchValueCodec.bigDecimal,
    )

  inline def boolean(inline selector: Document => Boolean): SearchFieldDraft[Document, Boolean] =
    SearchFieldDraft.forDirect[Document, Boolean](this, SearchFieldDerivation.path[Document, Boolean](selector), selector, SearchFieldKind.Boolean, SearchValueCodec.boolean)

  inline def geoPoint(inline selector: Document => GeoPoint): SearchFieldDraft[Document, GeoPoint] =
    SearchFieldDraft.forDirect[Document, GeoPoint](this, SearchFieldDerivation.path[Document, GeoPoint](selector), selector, SearchFieldKind.GeoPoint, SearchValueCodec.geoPoint)

  /** Starts a dynamic map-backed field family: `selector` derives the map field's own prefix (e.g.
    * `_.intAttributes` -> `"intAttributes"`) via the same direct-selector macro the direct field
    * methods use; `definitions` is the ordered inventory of per-field definitions, and `code` reads
    * each definition's stable string code, used as both the map key and the `<prefix>.<code>` path
    * suffix.
    */
  inline def dynamicMap[Value, Definition](
    inline selector: Document => Map[String, Value],
    definitions: Iterable[Definition],
  )(
    code: Definition => String
  ): DynamicFieldKindDraft[Document, Value, Definition] =
    DynamicFieldKindDraft.forMap[Document, Value, Definition](
      this,
      SearchFieldDerivation.path[Document, Map[String, Value]](selector),
      selector,
      definitions.toVector,
      code,
    )

  /** Explicitly marks one stored product member as intentionally outside the search declaration.
    * [[completeDocument]] requires every member to be either a direct field, a dynamic-map family or
    * an explicit ignore, so adding a case-class field can never silently bypass indexing policy.
    */
  inline def ignore[Value](inline selector: Document => Value): Unit = {
    requireNotFrozen()
    ignoredBuffer += SearchFieldDerivation.path[Document, Value](selector)
    (): Unit
  }

  /** Requires `identity` to be one of this registry's own registered static handles (by reference),
    * freezes the registry, and delegates to [[SearchDocumentDeclaration.validate]] with every other
    * static field plus every dynamic field, in declaration order, as the ordinary field list.
    */
  def validateDocument[Id](identity: SearchField[Document, Id]): Either[SearchDeclarationErrors, SearchDocumentDeclaration[Document, Id]] = {
    if (!staticFields.exists(_ eq identity)) {
      throw new IllegalStateException(
        s"SearchFieldDeclarations document '$documentId': identity field '${identity.id.value}' was not declared as a static field of this same registry"
      )
    }

    frozen = true

    val ordinaryFields = staticFields.filterNot(_ eq identity) ++ dynamicFields
    SearchDocumentDeclaration.validate(SearchDocumentId(documentId), identity, ordinaryFields)
  }

  /** Ergonomic terminal: [[validateDocument]], unwrapped to the declaration or thrown as one
    * [[IllegalStateException]] listing every deterministic [[SearchDeclarationErrors]] item.
    */
  def document[Id](identity: SearchField[Document, Id]): SearchDocumentDeclaration[Document, Id] =
    validateDocument(identity) match {
      case Right(declaration) =>
        declaration
      case Left(errors) =>
        throw new IllegalStateException(
          s"SearchFieldDeclarations document '$documentId' failed validation: ${errors.toVector.mkString("; ")}"
        )
    }

  /** Business-facing exhaustive terminal. Product labels come from `Mirror`; field coverage comes
    * from the same registered selector paths that build the declaration. The lower-level [[document]]
    * terminal remains available for intentionally partial technical documents and fixtures.
    */
  inline def completeDocument[Id](identity: SearchField[Document, Id])(using
    mirror: Mirror.ProductOf[Document]
  ): SearchDocumentDeclaration[Document, Id] = {
    val productFields = ProductFieldLabels.of[Document]
    val declaredRoots = allFields.map(field => SearchFieldDeclarations.rootPath(field.path.value)).distinct
    val ignored        = ignoredBuffer.toVector.distinct
    val overlap        = declaredRoots.filter(ignored.contains)
    val covered        = (declaredRoots ++ ignored).toSet
    val missing        = productFields.filterNot(covered.contains)

    if (overlap.nonEmpty || missing.nonEmpty) {
      val overlapMessage = if (overlap.isEmpty) "" else s"; both declared and ignored: ${overlap.mkString(", ")}"
      val missingMessage = if (missing.isEmpty) "" else s"; missing: ${missing.mkString(", ")}"
      throw new IllegalStateException(
        s"SearchFieldDeclarations for document '$documentId' does not exhaustively cover its product fields$missingMessage$overlapMessage"
      )
    }

    document(identity)
  }
}

private[contract] object SearchFieldDeclarations {
  def rootPath(path: String): String = path.takeWhile(_ != '.')
}

private[contract] object ProductFieldLabels {
  inline def of[Product](using mirror: Mirror.ProductOf[Product]): Vector[String] =
    labels[mirror.MirroredElemLabels]

  private inline def labels[Labels <: Tuple]: Vector[String] =
    inline erasedValue[Labels] match {
      case _: EmptyTuple      => Vector.empty
      case _: (head *: tail) => constValue[head].toString +: labels[tail]
    }
}

/** An immutable, not-yet-registered field-in-progress for one direct field. Every capability method
  * returns another draft; the only way to register the final configured field into its owning
  * [[SearchFieldDeclarations]] is [[declare]], which registers `this` draft's current state - every
  * capability method chained before it - never an earlier unconfigured copy.
  */
final class SearchFieldDraft[Document, Value] private[contract] (
  registry: SearchFieldDeclarations[Document],
  id: FieldId,
  path: FieldPath,
  kind: SearchFieldKind,
  extraction: FieldExtraction[Document, Value],
  codec: SearchValueCodec[Value],
  semantic: Option[FieldSemantic],
  capabilities: FieldCapabilities,
) {
  private def copy(
    id: FieldId                     = this.id,
    semantic: Option[FieldSemantic] = this.semantic,
    capabilities: FieldCapabilities = this.capabilities,
  ): SearchFieldDraft[Document, Value] =
    new SearchFieldDraft(registry, id, path, kind, extraction, codec, semantic, capabilities)

  /** Overrides only the declared [[FieldId]]; the derived [[FieldPath]] never changes. */
  def named(value: String): SearchFieldDraft[Document, Value] = copy(id = FieldId(value))

  def withSemantic(value: String): SearchFieldDraft[Document, Value] = copy(semantic = Some(FieldSemantic(value)))

  def searchable: SearchFieldDraft[Document, Value] =
    copy(capabilities = capabilities.copy(searchable = true))

  def filterable(first: FilterOperator, rest: FilterOperator*): SearchFieldDraft[Document, Value] =
    copy(capabilities = capabilities.copy(filterOperators = capabilities.filterOperators ++ (first +: rest)))

  def facetable(first: FacetMode, rest: FacetMode*): SearchFieldDraft[Document, Value] =
    copy(capabilities = capabilities.copy(facetModes = capabilities.facetModes ++ (first +: rest)))

  def sortable(first: SortMode, rest: SortMode*): SearchFieldDraft[Document, Value] =
    copy(capabilities = capabilities.copy(sortModes = capabilities.sortModes ++ (first +: rest)))

  def groupable(first: GroupMode, rest: GroupMode*): SearchFieldDraft[Document, Value] =
    copy(capabilities = capabilities.copy(groupModes = capabilities.groupModes ++ (first +: rest)))

  def payloadEligible: SearchFieldDraft[Document, Value] =
    copy(capabilities = capabilities.copy(payloadEligible = true))

  /** Registers this exact final configured field into the owning registry's static declaration order
    * and returns the exact same registered handle.
    */
  def declare: SearchField[Document, Value] = {
    val builtField = SearchField[Document, Value](id, path, kind, extraction, codec, semantic, capabilities)
    registry.registerStatic(builtField)
    builtField
  }
}

private[contract] object SearchFieldDraft {
  def forDirect[Document, Value](
    registry: SearchFieldDeclarations[Document],
    path: String,
    get: Document => Value,
    kind: SearchFieldKind,
    codec: SearchValueCodec[Value],
  ): SearchFieldDraft[Document, Value] =
    new SearchFieldDraft[Document, Value](
      registry,
      FieldId(path),
      FieldPath(path),
      kind,
      FieldExtraction.Required(get),
      codec,
      Some(FieldSemantic(path)),
      FieldCapabilities(),
    )
}

/** Kind-selection stage for a dynamic map-backed field family, after
  * [[SearchFieldDeclarations.dynamicMap]] has derived the map's own prefix but before a
  * [[SearchFieldKind]] has been chosen.
  */
final class DynamicFieldKindDraft[Document, Value, Definition] private[contract] (
  registry: SearchFieldDeclarations[Document],
  prefix: String,
  selector: Document => Map[String, Value],
  definitions: Vector[Definition],
  code: Definition => String,
) {
  private def withKind[B](
    kind: SearchFieldKind,
    codec: SearchValueCodec[B],
  )(using ev: Value =:= B): DynamicFieldCapabilityDraft[Document, Value, Definition] =
    new DynamicFieldCapabilityDraft[Document, Value, Definition](
      registry,
      prefix,
      selector,
      definitions,
      code,
      kind,
      // `Value =:= B` proves Value and B are the same type; SearchValueCodec is invariant, so Scala
      // cannot apply that equality proof to the type constructor on its own. The cast is total and
      // safe given the summoned proof.
      codec.asInstanceOf[SearchValueCodec[Value]],
      FieldCapabilities(),
    )

  /** Kind derived from [[DefaultSearchFieldKind]] evidence for `Value`. */
  def inferred(using kind: DefaultSearchFieldKind[Value], codec: SearchValueCodec[Value]): DynamicFieldCapabilityDraft[Document, Value, Definition] =
    new DynamicFieldCapabilityDraft[Document, Value, Definition](registry, prefix, selector, definitions, code, kind.kind, codec, FieldCapabilities())

  def keyword(using codec: SearchValueCodec[Value]): DynamicFieldCapabilityDraft[Document, Value, Definition] =
    new DynamicFieldCapabilityDraft[Document, Value, Definition](registry, prefix, selector, definitions, code, SearchFieldKind.Keyword, codec, FieldCapabilities())

  /** Explicit `Text` kind for a dynamic `String`-valued family (e.g. per-locale searchable titles),
    * mirroring the direct-field `.text` choice. Kept separate from [[keyword]] for the same reason a
    * direct `String` field has no default kind: keyword-versus-text is business policy, never inferred.
    */
  def text(using ev: Value =:= String): DynamicFieldCapabilityDraft[Document, Value, Definition] =
    withKind(SearchFieldKind.Text, SearchValueCodec.string)

  def integer(using ev: Value =:= Int): DynamicFieldCapabilityDraft[Document, Value, Definition] =
    withKind(SearchFieldKind.Integer, SearchValueCodec.int)

  def long(using ev: Value =:= Long): DynamicFieldCapabilityDraft[Document, Value, Definition] =
    withKind(SearchFieldKind.Long, SearchValueCodec.long)

  def decimal(using ev: Value =:= BigDecimal): DynamicFieldCapabilityDraft[Document, Value, Definition] =
    withKind(SearchFieldKind.Decimal, SearchValueCodec.bigDecimal)

  def boolean(using ev: Value =:= Boolean): DynamicFieldCapabilityDraft[Document, Value, Definition] =
    withKind(SearchFieldKind.Boolean, SearchValueCodec.boolean)

  def dateTime(using ev: Value =:= Instant): DynamicFieldCapabilityDraft[Document, Value, Definition] =
    withKind(SearchFieldKind.DateTime, SearchValueCodec.instant)

  def geoPoint(using ev: Value =:= GeoPoint): DynamicFieldCapabilityDraft[Document, Value, Definition] =
    withKind(SearchFieldKind.GeoPoint, SearchValueCodec.geoPoint)
}

private[contract] object DynamicFieldKindDraft {
  def forMap[Document, Value, Definition](
    registry: SearchFieldDeclarations[Document],
    prefix: String,
    selector: Document => Map[String, Value],
    definitions: Vector[Definition],
    code: Definition => String,
  ): DynamicFieldKindDraft[Document, Value, Definition] =
    new DynamicFieldKindDraft[Document, Value, Definition](registry, prefix, selector, definitions, code)
}

/** Capability-selection stage for a dynamic map-backed field family, after a [[SearchFieldKind]] has
  * been chosen. Exposes the same capability operations as [[SearchFieldDraft]]; the terminal
  * [[declare]] validates definition-code uniqueness before registering one optional field per
  * definition, in `definitions` order, into the owning registry's dynamic declaration order.
  */
final class DynamicFieldCapabilityDraft[Document, Value, Definition] private[contract] (
  registry: SearchFieldDeclarations[Document],
  prefix: String,
  selector: Document => Map[String, Value],
  definitions: Vector[Definition],
  code: Definition => String,
  kind: SearchFieldKind,
  codec: SearchValueCodec[Value],
  capabilities: FieldCapabilities,
) {
  private def copy(capabilities: FieldCapabilities): DynamicFieldCapabilityDraft[Document, Value, Definition] =
    new DynamicFieldCapabilityDraft(registry, prefix, selector, definitions, code, kind, codec, capabilities)

  def searchable: DynamicFieldCapabilityDraft[Document, Value, Definition] =
    copy(capabilities.copy(searchable = true))

  def filterable(first: FilterOperator, rest: FilterOperator*): DynamicFieldCapabilityDraft[Document, Value, Definition] =
    copy(capabilities.copy(filterOperators = capabilities.filterOperators ++ (first +: rest)))

  def facetable(first: FacetMode, rest: FacetMode*): DynamicFieldCapabilityDraft[Document, Value, Definition] =
    copy(capabilities.copy(facetModes = capabilities.facetModes ++ (first +: rest)))

  def sortable(first: SortMode, rest: SortMode*): DynamicFieldCapabilityDraft[Document, Value, Definition] =
    copy(capabilities.copy(sortModes = capabilities.sortModes ++ (first +: rest)))

  def groupable(first: GroupMode, rest: GroupMode*): DynamicFieldCapabilityDraft[Document, Value, Definition] =
    copy(capabilities.copy(groupModes = capabilities.groupModes ++ (first +: rest)))

  def payloadEligible: DynamicFieldCapabilityDraft[Document, Value, Definition] =
    copy(capabilities.copy(payloadEligible = true))

  def declare: DynamicFieldFamily[Document, Value] = {
    val codes      = definitions.map(code)
    val duplicates = DynamicFieldCapabilityDraft.duplicateCodesInOrder(codes)
    if (duplicates.nonEmpty) {
      throw new IllegalStateException(
        s"Dynamic field family '$prefix' has duplicate definition codes: ${duplicates.mkString(", ")}"
      )
    }

    val entries: Vector[(String, SearchField[Document, Value])] =
      definitions.map {
        definition =>
          val definitionCode = code(definition)
          val fieldPath       = s"$prefix.$definitionCode"
          val extractor: Document => Option[Value] = document => selector(document).get(definitionCode)

          definitionCode -> SearchField[Document, Value](
            FieldId(fieldPath),
            FieldPath(fieldPath),
            kind,
            FieldExtraction.Optional(extractor),
            codec,
            Some(FieldSemantic(fieldPath)),
            capabilities,
          )
      }

    entries.foreach { case (_, field) => registry.registerDynamic(field) }

    new DynamicFieldFamily[Document, Value](entries)
  }
}

private[contract] object DynamicFieldCapabilityDraft {
  // Finds, in first-encounter order, each code that appears more than once, reporting it once at the
  // position of its second occurrence - deterministic regardless of how many times it repeats.
  private def duplicateCodesInOrder(codes: Vector[String]): Vector[String] = {
    @tailrec
    def loop(remaining: Vector[String], seen: Set[String], reported: Set[String], acc: Vector[String]): Vector[String] =
      remaining match {
        case head +: tail =>
          if (!seen.contains(head)) loop(tail, seen + head, reported, acc)
          else if (!reported.contains(head)) loop(tail, seen, reported + head, acc :+ head)
          else loop(tail, seen, reported, acc)
        case _ => acc
      }

    loop(codes, Set.empty, Set.empty, Vector.empty)
  }
}

/** The result of declaring one dynamic map-backed field family: [[entries]]/[[fields]] preserve
  * exactly the backing definitions' own order; [[byCode]] is the same handles keyed by their
  * definition code, safe to build via `.toMap` only because [[DynamicFieldCapabilityDraft.declare]]
  * already rejected duplicate codes before this family was constructed.
  */
final class DynamicFieldFamily[Document, Value] private[contract] (
  val entries: Vector[(String, SearchField[Document, Value])]
) {
  val fields: Vector[SearchField[Document, Value]]     = entries.map(_._2)
  val byCode: Map[String, SearchField[Document, Value]] = entries.toMap
}
