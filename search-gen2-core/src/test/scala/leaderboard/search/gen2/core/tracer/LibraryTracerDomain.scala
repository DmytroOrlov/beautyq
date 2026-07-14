package leaderboard.search.gen2.core.tracer

import leaderboard.model.{CanonicalStringValue, UuidBackedId}
import leaderboard.search.gen2.contract.*
import leaderboard.search.gen2.core.materialization.*
import izumi.functional.bio.Error2
import izumi.functional.bio.impl.BioEither

import java.time.Instant
import java.util.UUID

/** Deliberately non-domain-specific-shaped executable calibration example for the reusable Gen2
  * declaration and materialization kernel, exercising its mechanics beyond the one production domain
  * the kernel was extracted from without pretending to be a second production consumer.
  * It calibrates the supported shapes recorded in
  * docs/gen2/BEAUTYQ_SEARCH_GEN2_TECHNICAL_SPEC.md rather than serving as a compile-only probe.
  *
  * Deliberately divergent shape:
  *  - `Long`/`Instant`-valued fields (the original domain has neither);
  *  - a dynamic map family with translation semantics, not "attributes", and declared `.text` rather
  *    than `.keyword`;
  *  - a snapshot with a [[CanonicalSnapshot.Single]] (non-`Vector`) source alongside `Vector` sources;
  *  - a multi-value field (`tags`) the current field vocabulary cannot express - tracked as gap G-3
  *    and explicitly `.ignore`d rather than silently dropped by `completeDocument`'s exhaustive
  *    coverage check.
  *
  * Lives in test scope only, is never wired into any serving classpath, and imports no domain-specific
  * production module.
  */
object LibraryTracerModel {
  opaque type BookId = UUID

  object BookId extends UuidBackedId[BookId] {
    def apply(value: UUID): BookId = value
    def unwrap(id: BookId): UUID   = id
    given UuidBackedId[BookId]     = this
  }

  opaque type BranchId = UUID

  object BranchId extends UuidBackedId[BranchId] {
    def apply(value: UUID): BranchId = value
    def unwrap(id: BranchId): UUID   = id
    given UuidBackedId[BranchId]     = this
  }

  // `leaderboard.model.StableCodeCompanion` lives in leaderboard-core and is public, so it is genuinely
  // reachable from here. Isbn still writes
  // its own minimal CanonicalStringValue instance instead of extending it, because
  // StableCodeCompanion's accepted grammar is fixed to lowercase snake_case (ServiceCode/CategoryCode's
  // own shape), not a real ISBN's digits-and-hyphens shape - a deliberate, different-grammar case, not
  // a visibility workaround.
  opaque type Isbn = String

  object Isbn extends CanonicalStringValue[Isbn] {
    private val pattern = "^[0-9-]+$".r

    def fromString(value: String): Either[String, Isbn] =
      if (pattern.matches(value)) Right(value) else Left(s"'$value' is not a valid Isbn: expected digits and hyphens only")

    extension (isbn: Isbn) def value: String = isbn

    def decodeCanonical(value: String): Either[String, Isbn] = fromString(value)
    def encodeCanonical(value: Isbn): String                 = value.value

    given CanonicalStringValue[Isbn] = this
  }

  final case class TranslationDefinition(localeCode: String)

  final case class Book(
    id: BookId,
    branchId: BranchId,
    isbn: Isbn,
    title: String,
    tags: Vector[String],
    publishedAt: Instant,
    checkoutCount: Long,
    translations: Map[String, String],
  )

  final case class LibraryBranch(
    id: BranchId,
    name: String,
  )

  final case class CatalogConfig(
    schemaVersion: Int,
    curatedAt: Instant,
  )

  final case class BookDocument(
    bookId: BookId,
    isbn: Isbn,
    title: String,
    tags: Vector[String],
    branchName: String,
    publishedAt: Instant,
    checkoutCount: Long,
    translations: Map[String, String],
  )
}

object LibraryTracerSnapshot {
  import LibraryTracerModel.*

  final case class LibrarySnapshot(
    books: Vector[Book],
    branches: Vector[LibraryBranch],
    config: CanonicalSnapshot.Single[CatalogConfig],
  )

  given CanonicalSourceRows[Book] = CanonicalSourceRows("book") {
    book =>
      val orderedTranslations = book.translations.toVector.sortBy { case (locale, _) => locale }
      canonicalRow("book", book)
        .field(_.id)
        .field(_.branchId)
        .field(_.isbn)
        .field(_.title)
        .field(_.publishedAt)
        .field(_.checkoutCount)
        .group("translations", orderedTranslations) { case (group, (locale, text)) =>
          group.value("locale", locale).value("text", text)
        }
        .build
  }

  given CanonicalSourceRows[LibraryBranch] = CanonicalSourceRows("branch") {
    branch => canonicalRow("branch", branch).field(_.id).field(_.name).build
  }

  given CanonicalSourceRows[CatalogConfig] = CanonicalSourceRows("catalogConfig") {
    config => canonicalRow("catalogConfig", config).field(_.schemaVersion).field(_.curatedAt).build
  }

  val sourceEncodingVersion = "library-tracer-source-v1"

  /** Mirrors the real per-domain source-fingerprint pattern: one encoding-version token plus the
    * canonical snapshot traversal, hashed via the same generic framing every domain reuses. Proves the
    * mixed `Vector`-and-[[CanonicalSnapshot.Single]] snapshot shape composes with
    * [[CanonicalFingerprint]] exactly like
    * the all-`Vector` shape.
    */
  def sourceFingerprint(snapshot: LibrarySnapshot): ContentFingerprint = {
    val encoded = CanonicalSnapshot.encode(snapshot)
    val tokens  = CanonicalFingerprint.token("encodingVersion", sourceEncodingVersion) +: encoded.tokens
    ContentFingerprint(CanonicalFingerprint.sha256HexTokens(tokens))
  }
}

object LibraryTracerProjection {
  import LibraryTracerModel.*
  import LibraryTracerSnapshot.*

  final case class LibraryProjectionError(message: String)

  /** Minimal explicit join (book -> branch), proportionate to a tracer but structurally the same
    * pattern as a real domain's projection: joins and validation stay explicit business code, never
    * inferred from snapshot shape.
    */
  def project(snapshot: LibrarySnapshot): Either[LibraryProjectionError, Vector[BookDocument]] = {
    val branchNameById            = snapshot.branches.map(branch => branch.id -> branch.name).toMap
    val booksInDeterministicOrder = snapshot.books.sortBy(_.id.value.toString)

    booksInDeterministicOrder.foldLeft[Either[LibraryProjectionError, Vector[BookDocument]]](Right(Vector.empty)) {
      (accumulated, book) =>
        for {
          documents  <- accumulated
          branchName <- branchNameById.get(book.branchId).toRight(LibraryProjectionError(s"missing branch for book ${book.id.value}"))
        } yield documents :+ BookDocument(
          bookId = book.id,
          isbn = book.isbn,
          title = book.title,
          tags = book.tags,
          branchName = branchName,
          publishedAt = book.publishedAt,
          checkoutCount = book.checkoutCount,
          translations = book.translations,
        )
    }
  }
}

object LibraryTracerSearchDomain {
  import LibraryTracerModel.*

  // Open authoring hazard G-8 (see docs/gen2/BEAUTYQ_SEARCH_GEN2_IMPLEMENTATION_PLAN.md): Fields' own inputs are kept fully local to Fields
  // (never a private val of the enclosing object it closes over). A nested Fields object that captures
  // an outer private val, combined with the enclosing object's own `val document = Fields.document`
  // alias, is a reentrant JVM class-initialization hazard: the outer alias can observe a stale/default
  // value when first read from a third object, even though direct Fields access works. The production
  // domain root avoids this by construction (its Fields object closes over nothing outside itself);
  // this tracer keeps translationDefinitions inside Fields for the same reason, not by accident.
  object Fields {
    private val translationDefinitions = Vector(TranslationDefinition("en"), TranslationDefinition("uk"))

    private val declarations = searchFields[BookDocument]("books")

    val bookId =
      declarations
        .inferred(_.bookId)
        .payloadEligible
        .declare

    val isbn =
      declarations
        .inferred(_.isbn)
        .filterable(FilterOperator.Equal)
        .payloadEligible
        .declare

    val title =
      declarations
        .text(_.title)
        .searchable
        .declare

    val branchName =
      declarations
        .keyword(_.branchName)
        .facetable(FacetMode.Terms)
        .groupable(GroupMode.Terms)
        .declare

    val publishedAt =
      declarations
        .inferred(_.publishedAt)
        .filterable(FilterOperator.Range)
        .facetable(FacetMode.Range)
        .sortable(SortMode.Value)
        .declare

    val checkoutCount =
      declarations
        .inferred(_.checkoutCount)
        .filterable(FilterOperator.Range)
        .sortable(SortMode.Value)
        .payloadEligible
        .declare

    val translations =
      declarations
        .dynamicMap(_.translations, translationDefinitions)(_.localeCode)
        .text
        .searchable
        .declare

    // Gap G-3 (tracked in docs/gen2/BEAUTYQ_SEARCH_GEN2_IMPLEMENTATION_PLAN.md): a Vector[String]-valued
    // field has no expressible SearchFieldKind or FieldExtraction today. completeDocument's exhaustive
    // product coverage forces this decision to be explicit instead of silently missing.
    declarations.ignore(_.tags)

    val document = declarations.completeDocument(bookId)
  }

  val document = Fields.document
}

object LibraryTracerMaterialization {
  import LibraryTracerModel.*
  import LibraryTracerSnapshot.*
  import LibraryTracerProjection.*
  import LibraryTracerSearchDomain.*

  final case class LibrarySnapshotLoadError(message: String)

  private given Error2[Either] = BioEither

  final class FixedLibrarySnapshotSource(result: Either[LibrarySnapshotLoadError, VersionedSnapshot[LibrarySnapshot]])
    extends SearchSnapshotSource[Either, LibrarySnapshotLoadError, LibrarySnapshot] {
    def load: Either[LibrarySnapshotLoadError, VersionedSnapshot[LibrarySnapshot]] = result
  }

  val documentEncodingVersion = "library-tracer-document-v1"
  val projectionFormatVersion = ProjectionFormatVersion("library-tracer-projection-v1")

  def projectedFingerprint(documents: Vector[BookDocument]): ProjectedDocumentsFingerprint =
    SearchProjectedDocumentsFingerprint.compute(documentEncodingVersion, projectionFormatVersion, document, documents)

  def materialize(
    snapshot: VersionedSnapshot[LibrarySnapshot]
  ): Either[SearchMaterializationError[LibrarySnapshotLoadError, LibraryProjectionError], MaterializedSearchDocuments[LibrarySnapshot, BookDocument]] =
    SearchMaterializer.materialize[Either, LibrarySnapshotLoadError, LibrarySnapshot, LibraryProjectionError, BookDocument](
      new FixedLibrarySnapshotSource(Right(snapshot)),
      project,
      projectedFingerprint,
      projectionFormatVersion,
    )
}
