package leaderboard.search.gen2.elasticsearch

import leaderboard.search.gen2.contract.*
import org.scalatest.wordspec.AnyWordSpec

import io.circe.Json

import java.time.Instant

/** Neutral calibration for the generic Elasticsearch document/source compiler, using the shared
  * book/library document fixture ([[ElasticsearchTestFixtures]]) unrelated to BeautyQ. Every fixture
  * value below is deliberately free of trailing-zero decimal noise, so the expected `Json` built directly
  * from the same Scala value the compiler itself encodes is the exact byte-for-byte proof of precision
  * preservation, not a separately re-derived expectation.
  */
final class ElasticsearchDocumentCompilerSpec extends AnyWordSpec {
  import ElasticsearchTestFixtures.*

  private val bookNoRatings = bookA.copy(isbn = "978-1-59-327283-8", editionRatings = Map.empty)
  private val bookOneRating = bookA.copy(isbn = "978-0-59-652068-7", editionRatings = Map("hardcover" -> 5))
  private val bookEmptyId   = bookA.copy(isbn = "")

  private def compileOrFail(documents: Vector[BookDocument]): Vector[ElasticsearchIndexedDocument] =
    ElasticsearchDocumentCompiler.compile(policy, documents) match {
      case Right(compiled) => compiled
      case Left(error)     => fail(s"expected successful compilation, got $error")
    }

  private def compileOneOrFail(document: BookDocument): ElasticsearchIndexedDocument =
    compileOrFail(Vector(document)) match {
      case Vector(compiled) => compiled
      case other            => fail(s"expected exactly one compiled document, got $other")
    }

  private val expectedSourceA: Json =
    Json.obj(
      "isbn"           -> Json.fromString(bookA.isbn),
      "title"          -> Json.fromString(bookA.title),
      "subtitle"       -> Json.fromString(bookA.subtitle),
      "internalNote"   -> Json.fromString(bookA.internalNote),
      "genre"          -> Json.fromString(bookA.genre),
      "pageCount"      -> Json.fromInt(bookA.pageCount),
      "wordCount"      -> Json.fromLong(bookA.wordCount),
      "price"          -> Json.fromBigDecimal(bookA.price),
      "inPrint"        -> Json.fromBoolean(bookA.inPrint),
      "publishedAt"    -> Json.fromString(bookA.publishedAt.toString),
      "storeLocation"  -> Json.obj("lat" -> Json.fromBigDecimal(bookA.storeLocation.lat), "lon" -> Json.fromBigDecimal(bookA.storeLocation.lon)),
      "stockFrom"      -> Json.fromBigDecimal(bookA.stockFrom),
      "stockTo"        -> Json.fromBigDecimal(bookA.stockTo),
      "editionRatings" -> Json.obj("hardcover" -> Json.fromInt(5), "paperback" -> Json.fromInt(4)),
    )

  // A codec that is internally valid - decodeCanonical(encodeCanonical(value)) == Right(value) - but whose
  // one canonical text is not the standard Elasticsearch representation for the declared SearchFieldKind.
  // The mismatch this proves is between two independently-valid protocols (a field's own codec versus the
  // standard backend codec for its kind), never a defect in the fixture codec itself.
  private def customCodec[A](canonical: String, value: A): SearchValueCodec[A] =
    new SearchValueCodec[A] {
      def typeId: SearchValueTypeId = SearchValueTypeId("custom-fixture")
      def encodeCanonical(v: A): String = canonical
      def decodeCanonical(text: String): Either[SearchValueDecodeError, A] =
        if (text == canonical) Right(value)
        else Left(SearchValueDecodeError(typeId, text, "does not match this fixture's one canonical value"))
    }

  private def expectedStandardError[A](decoded: Either[SearchValueDecodeError, A]): SearchValueDecodeError =
    decoded match {
      case Left(error)  => error
      case Right(value) => fail(s"expected the standard codec to reject this fixture's canonical text, but it decoded to $value")
    }

  "ElasticsearchDocumentCompiler.compile" should {
    "derive the canonical _id from the declaration's exact identity field" in {
      assert(compileOneOrFail(bookA).id == bookA.isbn)
    }

    "reuse the same canonical identity representation for _id and _source" in {
      val compiled           = compileOneOrFail(bookA)
      val sourceIdentityValue = compiled.source.asObject.flatMap(_.apply("isbn")).flatMap(_.asString).getOrElse(fail("expected an isbn field in source"))
      assert(compiled.id == sourceIdentityValue)
      assert(compiled.id == bookA.isbn)
    }

    "encode exact scalar representations for every kind, with an ISO-8601 date-time string" in {
      assert(compileOneOrFail(bookA).source == expectedSourceA)
    }

    "preserve decimal precision without converting through Double" in {
      val preciseBook = bookA.copy(isbn = "978-0-32-176572-6", price = BigDecimal("19.999999999999999"))
      val compiled    = compileOneOrFail(preciseBook)
      val priceJson   = compiled.source.asObject.flatMap(_.apply("price")).getOrElse(fail("expected a price field in source"))
      assert(priceJson == Json.fromBigDecimal(BigDecimal("19.999999999999999")))
      assert(priceJson.asNumber.exists(_.toBigDecimal.contains(BigDecimal("19.999999999999999"))))
    }

    "encode GeoPoint as an exact {lat, lon} object using decimal JSON values" in {
      val compiled = compileOneOrFail(bookA)
      val geoJson   = compiled.source.asObject.flatMap(_.apply("storeLocation")).getOrElse(fail("expected a storeLocation field in source"))
      assert(geoJson == Json.obj("lat" -> Json.fromBigDecimal(BigDecimal("52.520008")), "lon" -> Json.fromBigDecimal(BigDecimal("13.404954"))))
    }

    "merge sibling dynamic attribute leaves into one parent object" in {
      val compiled    = compileOneOrFail(bookA)
      val ratingsJson = compiled.source.asObject.flatMap(_.apply("editionRatings")).getOrElse(fail("expected an editionRatings field in source"))
      assert(ratingsJson == Json.obj("hardcover" -> Json.fromInt(5), "paperback" -> Json.fromInt(4)))
    }

    "omit an absent optional dynamic leaf while keeping its present sibling" in {
      val compiled    = compileOneOrFail(bookOneRating)
      val ratingsJson = compiled.source.asObject.flatMap(_.apply("editionRatings")).getOrElse(fail("expected an editionRatings field in source"))
      assert(ratingsJson == Json.obj("hardcover" -> Json.fromInt(5)))
    }

    "omit the entire parent object when every optional dynamic child is absent" in {
      val compiled = compileOneOrFail(bookNoRatings)
      assert(compiled.source.asObject.exists(!_.contains("editionRatings")))
    }

    "preserve stable document order across the compiled vector" in {
      val compiled = compileOrFail(Vector(bookA, bookOneRating, bookNoRatings))
      assert(compiled.map(_.id) == Vector(bookA.isbn, bookOneRating.isbn, bookNoRatings.isbn))
    }

    // JsonObject structural equality alone does not prove serialized property order, so order is
    // asserted directly against the object's own key vector, and repeated compilation is compared through
    // exact compact-JSON text (noSpaces), not just ADT equality.
    //
    // The declaration builder registers static fields and dynamic families into two separate buffers and
    // always emits static-declared-order followed by dynamic-declared-order (never true interleaved
    // declaration order), so `editionRatings` (the one dynamic family, declared before stockFrom/stockTo in
    // source) is expected last, after every static field.
    "preserve exact root and nested source key order, matching declared field order" in {
      val compiled = compileOneOrFail(bookA)
      val rootKeys = compiled.source.asObject.getOrElse(fail("expected a source object")).toVector.map(_._1)
      assert(
        rootKeys ==
          Vector(
            "isbn", "title", "subtitle", "internalNote", "genre", "pageCount", "wordCount", "price", "inPrint", "publishedAt", "storeLocation", "stockFrom",
            "stockTo", "editionRatings",
          )
      )

      val nestedKeys =
        compiled.source.asObject.flatMap(_.apply("editionRatings")).flatMap(_.asObject).getOrElse(fail("expected editionRatings in source")).toVector.map(_._1)
      assert(nestedKeys == Vector("hardcover", "paperback"))
    }

    "produce byte-identical serialized source for repeated compilation of identical input" in {
      assert(compileOneOrFail(bookA).source.noSpaces == compileOneOrFail(bookA).source.noSpaces)
      assert(compileOneOrFail(bookA).source.noSpaces == expectedSourceA.noSpaces)
    }

    "report the first empty-document-id error deterministically, in document order" in {
      ElasticsearchDocumentCompiler.compile(policy, Vector(bookA, bookEmptyId, bookOneRating)) match {
        case Left(ElasticsearchDocumentCompileError.EmptyDocumentId(documentIndex, fieldId)) =>
          assert(documentIndex == 1)
          assert(fieldId == isbn.id)
        case other => fail(s"expected EmptyDocumentId, got $other")
      }
    }

    "return EmptyDocumentId for a keyword identity whose canonical value is empty" in {
      val actual = ElasticsearchDocumentCompiler.compile(policy, Vector(bookEmptyId))
      assert(actual == Left(ElasticsearchDocumentCompileError.EmptyDocumentId(documentIndex = 0, fieldId = isbn.id)))
    }

    // Emptiness is checked on the identity's extracted canonical value before backend-kind decoding ever
    // runs, so a structured identity's empty canonical value is EmptyDocumentId even though this fixture's
    // own custom codec is otherwise perfectly valid (it round-trips its own empty canonical text).
    "return EmptyDocumentId, not ValueEncoding, when a structured identity's valid custom codec produces an empty canonical value" in {
      final case class EmptyCanonicalIdentityDocument(value: Int, note: String)

      val emptyCanonicalCodec: SearchValueCodec[Int] =
        new SearchValueCodec[Int] {
          def typeId: SearchValueTypeId = SearchValueTypeId("empty-canonical-fixture")
          def encodeCanonical(value: Int): String = ""
          def decodeCanonical(text: String): Either[SearchValueDecodeError, Int] =
            if (text.isEmpty) Right(7) else Left(SearchValueDecodeError(typeId, text, "expected this fixture's one empty canonical value"))
        }

      val identityField: SearchField[EmptyCanonicalIdentityDocument, Int] = {
        given SearchValueCodec[Int] = emptyCanonicalCodec
        leaderboard.search.gen2.contract.field[EmptyCanonicalIdentityDocument, Int]("value", _.value).integer
      }
      val noteField: SearchField[EmptyCanonicalIdentityDocument, String] =
        leaderboard.search.gen2.contract.field[EmptyCanonicalIdentityDocument, String]("note", _.note).keyword

      val declaration =
        searchDocument[EmptyCanonicalIdentityDocument]("empty-canonical-identity").id(identityField).field(noteField).build match {
          case Right(value) => value
          case Left(error)  => fail(s"expected a valid empty-canonical-identity fixture declaration, got $error")
        }
      val fixturePolicy =
        ElasticsearchIndexPolicy.unsafeFrom(
          declaration,
          ElasticsearchPolicyVersion("empty-canonical-identity-es-v1"),
          Vector.empty,
        )

      val actual = ElasticsearchDocumentCompiler.compile(fixturePolicy, Vector(EmptyCanonicalIdentityDocument(7, "note")))
      assert(actual == Left(ElasticsearchDocumentCompileError.EmptyDocumentId(documentIndex = 0, fieldId = identityField.id)))
    }

    "return ValueEncoding, with the exact nested standard-codec decode error, for a non-empty structured identity incompatible with its standard kind" in {
      final case class MismatchedIdentityDocument(value: Int, note: String)

      val identityCanonical = "one"
      val identityField: SearchField[MismatchedIdentityDocument, Int] = {
        given SearchValueCodec[Int] = customCodec[Int](identityCanonical, 1)
        leaderboard.search.gen2.contract.field[MismatchedIdentityDocument, Int]("value", _.value).integer
      }
      val noteField: SearchField[MismatchedIdentityDocument, String] =
        leaderboard.search.gen2.contract.field[MismatchedIdentityDocument, String]("note", _.note).keyword

      val declaration =
        searchDocument[MismatchedIdentityDocument]("mismatched-identity").id(identityField).field(noteField).build match {
          case Right(value) => value
          case Left(error)  => fail(s"expected a valid mismatched-identity fixture declaration, got $error")
        }
      val fixturePolicy =
        ElasticsearchIndexPolicy.unsafeFrom(declaration, ElasticsearchPolicyVersion("mismatched-identity-es-v1"), Vector.empty)
      val expectedError = expectedStandardError(SearchValueCodec.int.decodeCanonical(identityCanonical))

      val actual = ElasticsearchDocumentCompiler.compile(fixturePolicy, Vector(MismatchedIdentityDocument(1, "note")))
      assert(
        actual ==
          Left(ElasticsearchDocumentCompileError.ValueEncoding(documentIndex = 0, fieldId = identityField.id, kind = identityField.kind, error = expectedError))
      )
    }

    // Every codec below is internally valid for the one value each entry uses (see customCodec's own
    // round-trip contract above); each entry's canonical text is independently confirmed incompatible with
    // the standard codec for its declared SearchFieldKind, and the exact nested decode error that standard
    // codec produces - not a wildcard - is what the compiler's ValueEncoding error must carry.
    "return a typed ValueEncoding error, with the exact nested standard-codec decode error, when a custom codec's canonical text is incompatible with its declared kind" in {
      val integerCanonical = "one"
      val integerField: SearchField[CustomCodecDocument, Int] = {
        given SearchValueCodec[Int] = customCodec[Int](integerCanonical, 1)
        computedField[CustomCodecDocument, Int]("customInt", "customInt")(_ => Some(1)).integer
      }
      val integerExpectedError = expectedStandardError(SearchValueCodec.int.decodeCanonical(integerCanonical))

      val longCanonical = "one-long"
      val longField: SearchField[CustomCodecDocument, Long] = {
        given SearchValueCodec[Long] = customCodec[Long](longCanonical, 1L)
        computedField[CustomCodecDocument, Long]("customLong", "customLong")(_ => Some(1L)).long
      }
      val longExpectedError = expectedStandardError(SearchValueCodec.long.decodeCanonical(longCanonical))

      val decimalCanonical = "one-decimal"
      val decimalField: SearchField[CustomCodecDocument, BigDecimal] = {
        given SearchValueCodec[BigDecimal] = customCodec[BigDecimal](decimalCanonical, BigDecimal(1))
        computedField[CustomCodecDocument, BigDecimal]("customDecimal", "customDecimal")(_ => Some(BigDecimal(1))).decimal
      }
      val decimalExpectedError = expectedStandardError(SearchValueCodec.bigDecimal.decodeCanonical(decimalCanonical))

      val booleanCanonical = "yes"
      val booleanField: SearchField[CustomCodecDocument, Boolean] = {
        given SearchValueCodec[Boolean] = customCodec[Boolean](booleanCanonical, true)
        computedField[CustomCodecDocument, Boolean]("customBoolean", "customBoolean")(_ => Some(true)).boolean
      }
      val booleanExpectedError = expectedStandardError(SearchValueCodec.boolean.decodeCanonical(booleanCanonical))

      val dateTimeCanonical = "the-epoch"
      val dateTimeField: SearchField[CustomCodecDocument, Instant] = {
        given SearchValueCodec[Instant] = customCodec[Instant](dateTimeCanonical, Instant.EPOCH)
        computedField[CustomCodecDocument, Instant]("customDateTime", "customDateTime")(_ => Some(Instant.EPOCH)).dateTime
      }
      val dateTimeExpectedError = expectedStandardError(SearchValueCodec.instant.decodeCanonical(dateTimeCanonical))

      val geoPointCanonical = "origin"
      val geoPointField: SearchField[CustomCodecDocument, GeoPoint] = {
        given SearchValueCodec[GeoPoint] = customCodec[GeoPoint](geoPointCanonical, GeoPoint(BigDecimal(0), BigDecimal(0)))
        computedField[CustomCodecDocument, GeoPoint]("customGeoPoint", "customGeoPoint")(_ => Some(GeoPoint(BigDecimal(0), BigDecimal(0)))).geoPoint
      }
      val geoPointExpectedError = expectedStandardError(SearchValueCodec.geoPoint.decodeCanonical(geoPointCanonical))

      Vector[(String, SearchField[CustomCodecDocument, ?], SearchValueDecodeError)](
        ("Integer", integerField, integerExpectedError),
        ("Long", longField, longExpectedError),
        ("Decimal", decimalField, decimalExpectedError),
        ("Boolean", booleanField, booleanExpectedError),
        ("DateTime", dateTimeField, dateTimeExpectedError),
        ("GeoPoint", geoPointField, geoPointExpectedError),
      ).foreach { case (label, field, expectedError) =>
        val declaration =
          searchDocument[CustomCodecDocument]("custom-codec").id(customCodecIdentity).field(field).build match {
            case Right(value) => value
            case Left(error)  => fail(s"$label: expected a valid custom-codec fixture declaration, got $error")
          }
        val fixturePolicy =
          ElasticsearchIndexPolicy.unsafeFrom(declaration, ElasticsearchPolicyVersion("custom-codec-es-v1"), Vector.empty)

        val actual = ElasticsearchDocumentCompiler.compile(fixturePolicy, Vector(CustomCodecDocument("fixture-id")))
        assert(
          actual ==
            Left(ElasticsearchDocumentCompileError.ValueEncoding(documentIndex = 0, fieldId = field.id, kind = field.kind, error = expectedError)),
          s"$label: expected exactly ValueEncoding(0, ${field.id}, ${field.kind}, $expectedError), got $actual",
        )
      }
    }
  }

  private final case class CustomCodecDocument(id: String)

  private val customCodecIdentity: SearchField[CustomCodecDocument, String] =
    leaderboard.search.gen2.contract.field[CustomCodecDocument, String]("id", _.id).keyword
}
