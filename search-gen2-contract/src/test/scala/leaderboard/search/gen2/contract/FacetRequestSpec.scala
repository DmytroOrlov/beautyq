package leaderboard.search.gen2.contract

import org.scalatest.wordspec.AnyWordSpec

import java.util.UUID

/** Neutral fixture proving the facet-request algebra, independent of any one domain's document shape.
  * CatalogDocument exercises Terms, NumberRange and IntervalOverlap over a non-price interval
  * (deliberately not BeautyQ's priceFrom/priceTo pair); OtherDocument is a second, structurally
  * unrelated shape used both to calibrate Terms beyond CatalogDocument and to prove document-type
  * safety at compile time.
  */
final class FacetRequestSpec extends AnyWordSpec {

  private final case class CatalogDocument(
    id: UUID,
    department: String,
    stockMin: Int,
    stockMax: Int,
  )

  private final case class OtherDocument(id: UUID, region: String)

  private val termsCapableRegion =
    field[OtherDocument, String]("region", _.region).keyword.facetable(FacetMode.Terms)

  // A second, independent SearchValueCodec[Int] instance - same Scala type as SearchValueCodec.int,
  // different logical identity - proving that two Range-capable fields are not guaranteed to share one
  // codec merely by sharing one Scala type parameter (mirrors PlannedConstraintSpec's own fixture).
  private val alternateIntCodec: SearchValueCodec[Int] =
    SearchValueCodec.int.imap(SearchValueTypeId("alternate-int"))(Right(_), identity)

  private val termsCapableDepartment =
    field[CatalogDocument, String]("department", _.department).keyword.facetable(FacetMode.Terms)

  private val notTermsCapableDepartment =
    field[CatalogDocument, String]("department", _.department).keyword

  private val rangeCapableStockMin =
    field[CatalogDocument, Int]("stockMin", _.stockMin).integer.facetable(FacetMode.Range)

  private val notRangeCapableStockMin =
    field[CatalogDocument, Int]("stockMin", _.stockMin).integer

  private val rangeCapableStockMax =
    field[CatalogDocument, Int]("stockMax", _.stockMax).integer.facetable(FacetMode.Range)

  private val notRangeCapableStockMax =
    field[CatalogDocument, Int]("stockMax", _.stockMax).integer

  private val rangeCapableStockMaxAlternateCodec =
    field[CatalogDocument, Int]("stockMax", _.stockMax)(using alternateIntCodec).integer.facetable(FacetMode.Range)

  private val notRangeCapableStockMaxAlternateCodec =
    field[CatalogDocument, Int]("stockMax", _.stockMax)(using alternateIntCodec).integer

  private val facetId = FacetId("catalogFacet")

  private val validFacetSize = FacetSize.from(10).getOrElse(fail("expected a valid FacetSize"))

  "FacetRequest.validate for Terms" should {
    "accept a field with Terms capability" in {
      val request = FacetRequest.Terms(facetId, termsCapableDepartment, validFacetSize, TermsFacetOrder.KeyAsc, FacetCountingPolicy.AllAppliedHardFilters)
      assert(FacetRequest.validate(request).isRight)
    }

    "reject a field without Terms capability" in {
      val request = FacetRequest.Terms(facetId, notTermsCapableDepartment, validFacetSize, TermsFacetOrder.KeyAsc, FacetCountingPolicy.AllAppliedHardFilters)
      FacetRequest.validate(request) match {
        case Left(errors) =>
          assert(
            errors.toVector ==
              Vector(FacetRequestError.UnsupportedFacetMode(facetId, notTermsCapableDepartment.id, SearchFieldKind.Keyword, FacetMode.Terms))
          )
        case Right(value) => fail(s"expected rejection, got: $value")
      }
    }
  }

  "FacetRequest.validate for NumberRange" should {
    val buckets: Vector[FacetBucket[Int]] =
      Vector(FacetBucket.HalfOpen(FacetBucketId("low"), 0, 10), FacetBucket.UpperUnbounded(FacetBucketId("high"), 10))

    "accept a field with Range capability and valid buckets" in {
      val request = FacetRequest.NumberRange(facetId, rangeCapableStockMin, buckets, FacetCountingPolicy.AllAppliedHardFilters)
      assert(FacetRequest.validate(request).isRight)
    }

    "reject a field without Range capability" in {
      val request = FacetRequest.NumberRange(facetId, notRangeCapableStockMin, buckets, FacetCountingPolicy.AllAppliedHardFilters)
      FacetRequest.validate(request) match {
        case Left(errors) =>
          assert(
            errors.toVector ==
              Vector(FacetRequestError.UnsupportedFacetMode(facetId, notRangeCapableStockMin.id, SearchFieldKind.Integer, FacetMode.Range))
          )
        case Right(value) => fail(s"expected rejection, got: $value")
      }
    }

    "reject an empty bucket vector" in {
      val request = FacetRequest.NumberRange(facetId, rangeCapableStockMin, Vector.empty, FacetCountingPolicy.AllAppliedHardFilters)
      FacetRequest.validate(request) match {
        case Left(errors) => assert(errors.toVector == Vector(FacetRequestError.EmptyFacetBuckets(facetId)))
        case Right(value) => fail(s"expected rejection, got: $value")
      }
    }

    "report a duplicated bucket ID exactly once, independent of how many times it repeats" in {
      val duplicateBuckets: Vector[FacetBucket[Int]] =
        Vector(
          FacetBucket.HalfOpen(FacetBucketId("b"), 0, 10),
          FacetBucket.HalfOpen(FacetBucketId("b"), 10, 20),
          FacetBucket.HalfOpen(FacetBucketId("b"), 20, 30),
        )
      val request = FacetRequest.NumberRange(facetId, rangeCapableStockMin, duplicateBuckets, FacetCountingPolicy.AllAppliedHardFilters)
      FacetRequest.validate(request) match {
        case Left(errors) => assert(errors.toVector == Vector(FacetRequestError.DuplicateFacetBucketId(facetId, FacetBucketId("b"))))
        case Right(value) => fail(s"expected rejection, got: $value")
      }
    }

    "accept an UpperUnbounded bucket as the final bucket" in {
      val request = FacetRequest.NumberRange(facetId, rangeCapableStockMin, buckets, FacetCountingPolicy.AllAppliedHardFilters)
      assert(FacetRequest.validate(request).isRight)
    }

    "reject an UpperUnbounded bucket that is not the final bucket" in {
      val misplacedBuckets: Vector[FacetBucket[Int]] =
        Vector(FacetBucket.UpperUnbounded(FacetBucketId("open"), 0), FacetBucket.HalfOpen(FacetBucketId("closed"), 10, 20))
      val request = FacetRequest.NumberRange(facetId, rangeCapableStockMin, misplacedBuckets, FacetCountingPolicy.AllAppliedHardFilters)
      FacetRequest.validate(request) match {
        case Left(errors) => assert(errors.toVector == Vector(FacetRequestError.UpperUnboundedBucketMustBeLast(facetId, FacetBucketId("open"))))
        case Right(value) => fail(s"expected rejection, got: $value")
      }
    }

    "interleave a duplicate-ID error and an out-of-position error in bucket declaration order" in {
      val mixedBuckets: Vector[FacetBucket[Int]] =
        Vector(
          FacetBucket.HalfOpen(FacetBucketId("b1"), 0, 10),
          FacetBucket.HalfOpen(FacetBucketId("b1"), 10, 20),
          FacetBucket.UpperUnbounded(FacetBucketId("mid"), 20),
          FacetBucket.HalfOpen(FacetBucketId("b2"), 30, 40),
        )
      val request = FacetRequest.NumberRange(facetId, rangeCapableStockMin, mixedBuckets, FacetCountingPolicy.AllAppliedHardFilters)
      FacetRequest.validate(request) match {
        case Left(errors) =>
          assert(
            errors.toVector ==
              Vector(
                FacetRequestError.DuplicateFacetBucketId(facetId, FacetBucketId("b1")),
                FacetRequestError.UpperUnboundedBucketMustBeLast(facetId, FacetBucketId("mid")),
              )
          )
        case Right(value) => fail(s"expected rejection, got: $value")
      }
    }
  }

  "FacetRequest.validate for IntervalOverlap" should {
    val buckets: Vector[FacetBucket[Int]] =
      Vector(FacetBucket.HalfOpen(FacetBucketId("low"), 0, 10), FacetBucket.UpperUnbounded(FacetBucketId("high"), 10))

    "accept both fields Range-capable and sharing one codec identity" in {
      val request = FacetRequest.IntervalOverlap(facetId, rangeCapableStockMin, rangeCapableStockMax, buckets, FacetCountingPolicy.AllAppliedHardFilters)
      assert(FacetRequest.validate(request).isRight)
    }

    "reject with UnsupportedFacetMode when only the to field lacks Range, without a spurious codec mismatch" in {
      val request = FacetRequest.IntervalOverlap(facetId, rangeCapableStockMin, notRangeCapableStockMax, buckets, FacetCountingPolicy.AllAppliedHardFilters)
      FacetRequest.validate(request) match {
        case Left(errors) =>
          assert(
            errors.toVector ==
              Vector(FacetRequestError.UnsupportedFacetMode(facetId, notRangeCapableStockMax.id, SearchFieldKind.Integer, FacetMode.Range))
          )
        case Right(value) => fail(s"expected rejection, got: $value")
      }
    }

    "reject with MismatchedIntervalFacetFields when both fields are Range-capable but use different codecs" in {
      val request =
        FacetRequest.IntervalOverlap(facetId, rangeCapableStockMin, rangeCapableStockMaxAlternateCodec, buckets, FacetCountingPolicy.AllAppliedHardFilters)
      FacetRequest.validate(request) match {
        case Left(errors) =>
          assert(
            errors.toVector ==
              Vector(
                FacetRequestError.MismatchedIntervalFacetFields(
                  facetId,
                  rangeCapableStockMin.id,
                  rangeCapableStockMaxAlternateCodec.id,
                  SearchValueTypeId("int"),
                  SearchValueTypeId("alternate-int"),
                )
              )
          )
        case Right(value) => fail(s"expected rejection, got: $value")
      }
    }

    "accumulate from-capability, to-capability, codec-mismatch and empty-buckets errors in exactly that order" in {
      val request =
        FacetRequest.IntervalOverlap(facetId, notRangeCapableStockMin, notRangeCapableStockMaxAlternateCodec, Vector.empty, FacetCountingPolicy.AllAppliedHardFilters)
      FacetRequest.validate(request) match {
        case Left(errors) =>
          assert(
            errors.toVector ==
              Vector(
                FacetRequestError.UnsupportedFacetMode(facetId, notRangeCapableStockMin.id, SearchFieldKind.Integer, FacetMode.Range),
                FacetRequestError.UnsupportedFacetMode(facetId, notRangeCapableStockMaxAlternateCodec.id, SearchFieldKind.Integer, FacetMode.Range),
                FacetRequestError.MismatchedIntervalFacetFields(
                  facetId,
                  notRangeCapableStockMin.id,
                  notRangeCapableStockMaxAlternateCodec.id,
                  SearchValueTypeId("int"),
                  SearchValueTypeId("alternate-int"),
                ),
                FacetRequestError.EmptyFacetBuckets(facetId),
              )
          )
        case Right(value) => fail(s"expected rejection, got: $value")
      }
    }
  }

  "FacetRequest.validate across a second, structurally unrelated document shape" should {
    "accept a Terms facet on OtherDocument, independent of CatalogDocument's own field shape" in {
      val request = FacetRequest.Terms(FacetId("regionFacet"), termsCapableRegion, validFacetSize, TermsFacetOrder.KeyAsc, FacetCountingPolicy.AllAppliedHardFilters)
      assert(FacetRequest.validate(request).isRight)
    }
  }

  "FacetBucket bounds derivation" should {
    "derive HalfOpen bounds as inclusive lower, exclusive upper" in {
      val bucket = FacetBucket.HalfOpen(FacetBucketId("mid"), 10, 20)
      assert(bucket.bounds == RangeBounds(Bound.Inclusive(10), Bound.Exclusive(20)))
    }

    "derive UpperUnbounded bounds as inclusive lower, unbounded upper" in {
      val bucket = FacetBucket.UpperUnbounded(FacetBucketId("open"), 20)
      assert(bucket.bounds == RangeBounds(Bound.Inclusive(20), Bound.Unbounded))
    }
  }

  "document-type safety" should {
    "reject a FacetRequest declared for one document type where another document type is expected, at compile time" in {
      assertDoesNotCompile(
        """
          |val wrongDocumentFacet: FacetRequest[OtherDocument] =
          |  FacetRequest.Terms(facetId, termsCapableDepartment, validFacetSize, TermsFacetOrder.KeyAsc, FacetCountingPolicy.AllAppliedHardFilters)
          |""".stripMargin
      )
    }
  }
}
