package leaderboard.search.gen2.core.tracer

import leaderboard.search.gen2.contract.*
import leaderboard.search.gen2.core.materialization.*
import org.scalatest.wordspec.AnyWordSpec

import java.time.Instant

/** Runtime calibration proof that the reusable Gen2 declaration and materialization kernel supports a
  * deliberately non-BeautyQ-shaped example through its complete local path: model -> canonical source
  * rows -> snapshot (including a [[CanonicalSnapshot.Single]] source) -> explicit projection ->
  * `searchFields`/`completeDocument` -> [[SearchMaterializer]] -> projected fingerprint. Neither this
  * file nor [[LibraryTracerDomain]] imports or depends on a BeautyQ production type. Catalog topology
  * is a separate `repo-core` boundary and is not part of this module-local proof.
  */
final class LibraryTracerDomainSpec extends AnyWordSpec {
  import LibraryTracerModel.*
  import LibraryTracerSnapshot.*
  import LibraryTracerProjection.*
  import LibraryTracerMaterialization.*
  import LibraryTracerSearchDomain.Fields

  private val branchId = BranchId(java.util.UUID.fromString("00000000-0000-0000-0000-000000000b01"))
  private val branch   = LibraryBranch(id = branchId, name = "Central")

  private val book1 = Book(
    id = BookId(java.util.UUID.fromString("00000000-0000-0000-0000-000000000001")),
    branchId = branchId,
    isbn = Isbn.fromString("978-0-13-468599-1").getOrElse(fail("expected a valid Isbn fixture")),
    title = "Programming in Scala",
    tags = Vector("scala", "fp"),
    publishedAt = Instant.parse("2021-05-01T00:00:00Z"),
    checkoutCount = 42L,
    translations = Map("en" -> "Programming in Scala", "uk" -> "Програмування на Scala"),
  )

  private val book2 = Book(
    id = BookId(java.util.UUID.fromString("00000000-0000-0000-0000-000000000002")),
    branchId = branchId,
    isbn = Isbn.fromString("978-1-4919-1889-0").getOrElse(fail("expected a valid Isbn fixture")),
    title = "Functional Programming in Scala",
    tags = Vector("scala"),
    publishedAt = Instant.parse("2019-01-01T00:00:00Z"),
    checkoutCount = 7L,
    translations = Map("en" -> "Functional Programming in Scala"),
  )

  private val config = CatalogConfig(schemaVersion = 1, curatedAt = Instant.parse("2026-01-01T00:00:00Z"))

  private val snapshot =
    LibrarySnapshot(books = Vector(book1, book2), branches = Vector(branch), config = CanonicalSnapshot.Single(config))

  "LibraryTracerSearchDomain.Fields.document" should {
    "keep the supported outer alias identical when Fields owns all of its initialization inputs" in {
      assert(LibraryTracerSearchDomain.document eq Fields.document)
    }

    "exhaustively cover every BookDocument product member as a declared field, a dynamic family, or an explicit ignore" in {
      assert(Fields.document.allFields.size == 8)
    }

    "keep the exact identity field" in {
      assert(Fields.document.identity eq Fields.bookId)
      assert(Fields.document.identity.id == FieldId("bookId"))
    }

    "infer Long for a raw Scala Long field and DateTime for a raw java.time.Instant field, the same way Int/BigDecimal/Boolean/GeoPoint are inferred" in {
      assert(Fields.checkoutCount.kind == SearchFieldKind.Long)
      assert(Fields.publishedAt.kind == SearchFieldKind.DateTime)
    }

    "declare a dynamic Text family, not only Keyword or numeric dynamic families" in {
      assert(Fields.translations.fields.nonEmpty)
      assert(Fields.translations.fields.forall(_.kind == SearchFieldKind.Text))
      assert(Fields.translations.fields.forall(_.capabilities.searchable))
    }
  }

  "the tracked multi-value gap (G-3)" should {
    "keep the ignored tags member out of the declared field set" in {
      assert(!Fields.document.allFields.exists(_.path.value == "tags"))
    }

    "reject document completion when tags is neither declared nor explicitly ignored" in {
      val declarationsMissingIgnore = searchFields[BookDocument]("books")
      val bookIdField                 = declarationsMissingIgnore.inferred(_.bookId).declare
      declarationsMissingIgnore.inferred(_.isbn).declare
      declarationsMissingIgnore.text(_.title).declare
      declarationsMissingIgnore.keyword(_.branchName).declare
      declarationsMissingIgnore.inferred(_.publishedAt).declare
      declarationsMissingIgnore.inferred(_.checkoutCount).declare
      declarationsMissingIgnore
        .dynamicMap(_.translations, Vector(TranslationDefinition("en")))(_.localeCode)
        .text
        .declare

      val error = intercept[IllegalStateException] {
        declarationsMissingIgnore.completeDocument(bookIdField)
      }
      assert(error.getMessage.contains("tags"))
    }
  }

  "LibraryTracerSnapshot.sourceFingerprint" should {
    "be independent of book iteration order" in {
      val reordered = snapshot.copy(books = Vector(book2, book1))
      assert(sourceFingerprint(snapshot) == sourceFingerprint(reordered))
    }

    "change when a book field changes" in {
      val mutated = snapshot.copy(books = Vector(book1.copy(checkoutCount = 43L), book2))
      assert(sourceFingerprint(snapshot) != sourceFingerprint(mutated))
    }

    "change when the singleton config source changes, proving CanonicalSnapshot.Single contributes to the hash" in {
      val mutated = snapshot.copy(config = CanonicalSnapshot.Single(config.copy(schemaVersion = 2)))
      assert(sourceFingerprint(snapshot) != sourceFingerprint(mutated))
    }

    "stay identical for unchanged content" in {
      assert(sourceFingerprint(snapshot) == sourceFingerprint(snapshot.copy(books = snapshot.books)))
    }
  }

  "LibraryTracerProjection.project" should {
    "produce one document per book, denormalizing branch name and carrying tags/translations through unchanged" in {
      project(snapshot) match {
        case Right(documents) =>
          assert(documents.size == 2)
          val projectedBook1 =
            documents
              .find(_.bookId == book1.id)
              .getOrElse(fail(s"expected a projected document for book ${book1.id.value}"))
          assert(projectedBook1.branchName == "Central")
          assert(projectedBook1.tags == Vector("scala", "fp"))
          assert(projectedBook1.translations == book1.translations)
        case Left(error) =>
          fail(s"expected a successful projection, got: $error")
      }
    }

    "fail with a typed error when a book references a branch absent from the snapshot" in {
      val orphanBranchId = BranchId(java.util.UUID.fromString("00000000-0000-0000-0000-0000000000ff"))
      val orphanSnapshot = snapshot.copy(books = Vector(book1.copy(branchId = orphanBranchId)))

      project(orphanSnapshot) match {
        case Left(error)  => assert(error.message.contains(book1.id.value.toString))
        case Right(value) => fail(s"expected a projection failure, got: $value")
      }
    }
  }

  "LibraryTracerMaterialization.materialize" should {
    "load, project, and fingerprint the snapshot through the generic SearchMaterializer" in {
      val versioned =
        VersionedSnapshot(
          value = snapshot,
          contentFingerprint = sourceFingerprint(snapshot),
          sourceRevision = None,
          capturedAt = Instant.parse("2026-01-01T00:00:00Z"),
        )

      materialize(versioned) match {
        case Right(materialized) =>
          assert(materialized.documents.size == 2)
          assert(materialized.sourceSnapshot == versioned)
          assert(materialized.projectionFormatVersion == projectionFormatVersion)
          assert(materialized.projectedDocumentsFingerprint == projectedFingerprint(materialized.documents))
        case Left(error) =>
          fail(s"expected a successful materialization, got: $error")
      }
    }

    "propagate a projection failure as SearchMaterializationError.Projection, not a silent partial result" in {
      val orphanBranchId = BranchId(java.util.UUID.fromString("00000000-0000-0000-0000-0000000000ff"))
      val orphanSnapshot = snapshot.copy(books = Vector(book1.copy(branchId = orphanBranchId)))
      val versioned =
        VersionedSnapshot(
          value = orphanSnapshot,
          contentFingerprint = sourceFingerprint(orphanSnapshot),
          sourceRevision = None,
          capturedAt = Instant.parse("2026-01-01T00:00:00Z"),
        )

      materialize(versioned) match {
        case Left(SearchMaterializationError.Projection(error)) =>
          assert(error.message.contains(book1.id.value.toString))
        case other =>
          fail(s"expected SearchMaterializationError.Projection, got: $other")
      }
    }
  }
}
