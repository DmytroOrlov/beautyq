package leaderboard.search.gen2.contract

import org.scalatest.wordspec.AnyWordSpec

import java.util.UUID

/** Neutral inventory fixture proving the facet plan declaration/registry is independent of any one
  * domain's document shape or facet inventory.
  */
final class FacetPlanRegistrySpec extends AnyWordSpec {

  private final case class InventoryDocument(id: UUID, department: String, stockMin: Int, stockMax: Int)

  private val department = field[InventoryDocument, String]("department", _.department).keyword.facetable(FacetMode.Terms)
  private val notFacetableDepartment = field[InventoryDocument, String]("department", _.department).keyword
  private val stockMin = field[InventoryDocument, Int]("stockMin", _.stockMin).integer.facetable(FacetMode.Range)
  private val stockMax = field[InventoryDocument, Int]("stockMax", _.stockMax).integer.facetable(FacetMode.Range)

  private val validSize = FacetSize.from(10).getOrElse(fail("expected a valid FacetSize"))
  private val validBuckets: Vector[FacetBucket[Int]] = Vector(FacetBucket.HalfOpen(FacetBucketId("low"), 0, 10), FacetBucket.UpperUnbounded(FacetBucketId("high"), 10))

  private val departmentDeclaration =
    FacetPlanDeclaration(FacetRequest.Terms(FacetId("department"), department, validSize, TermsFacetOrder.CountDescThenKeyAsc, FacetCountingPolicy.AllAppliedHardFilters))

  private val stockDeclaration =
    FacetPlanDeclaration(FacetRequest.NumberRange(FacetId("stock"), stockMin, validBuckets, FacetCountingPolicy.AllAppliedHardFilters))

  private val intervalDeclaration =
    FacetPlanDeclaration(FacetRequest.IntervalOverlap(FacetId("stockRange"), stockMin, stockMax, validBuckets, FacetCountingPolicy.AllAppliedHardFilters))

  "FacetPlanRegistry.apply" should {
    "derive an ordered inventory from valid declarations" in {
      FacetPlanRegistry(Vector(departmentDeclaration, stockDeclaration, intervalDeclaration)) match {
        case Right(registry) => assert(registry.ids == Vector(FacetId("department"), FacetId("stock"), FacetId("stockRange")))
        case Left(errors)    => fail(s"expected success, got ${errors.toVector}")
      }
    }

    "reject a duplicate facet ID" in {
      val duplicate = FacetPlanDeclaration(FacetRequest.NumberRange(FacetId("department"), stockMin, validBuckets, FacetCountingPolicy.AllAppliedHardFilters))
      FacetPlanRegistry(Vector(departmentDeclaration, duplicate)) match {
        case Left(errors) => assert(errors.toVector.contains(FacetPlanRegistryError.DuplicateFacetId(FacetId("department"), firstIndex = 0, duplicateIndex = 1)))
        case Right(value) => fail(s"expected rejection, got $value")
      }
    }

    "report every later duplicate in declaration encounter order with both indexes" in {
      val duplicateDepartment1 = FacetPlanDeclaration(FacetRequest.NumberRange(FacetId("department"), stockMin, validBuckets, FacetCountingPolicy.AllAppliedHardFilters))
      val duplicateDepartment2 = FacetPlanDeclaration(FacetRequest.IntervalOverlap(FacetId("department"), stockMin, stockMax, validBuckets, FacetCountingPolicy.AllAppliedHardFilters))
      val duplicateStock       = FacetPlanDeclaration(FacetRequest.Terms(FacetId("stock"), department, validSize, TermsFacetOrder.CountDescThenKeyAsc, FacetCountingPolicy.AllAppliedHardFilters))
      FacetPlanRegistry(Vector(departmentDeclaration, stockDeclaration, duplicateDepartment1, duplicateDepartment2, duplicateStock)) match {
        case Left(errors) =>
          assert(
            errors.toVector.collect { case error: FacetPlanRegistryError.DuplicateFacetId => error } ==
              Vector(
                FacetPlanRegistryError.DuplicateFacetId(FacetId("department"), firstIndex = 0, duplicateIndex = 2),
                FacetPlanRegistryError.DuplicateFacetId(FacetId("department"), firstIndex = 0, duplicateIndex = 3),
                FacetPlanRegistryError.DuplicateFacetId(FacetId("stock"), firstIndex = 1, duplicateIndex = 4),
              )
          )
        case Right(value) => fail(s"expected rejection, got $value")
      }
    }

    "reject a declaration whose FacetRequest fails its own validation" in {
      val invalid = FacetPlanDeclaration(FacetRequest.Terms(FacetId("department"), notFacetableDepartment, validSize, TermsFacetOrder.CountDescThenKeyAsc, FacetCountingPolicy.AllAppliedHardFilters))
      FacetPlanRegistry(Vector(invalid)) match {
        case Left(errors) =>
          assert(
            errors.toVector ==
              Vector(FacetPlanRegistryError.InvalidFacetDeclaration(FacetId("department"), 0, FacetRequestError.UnsupportedFacetMode(FacetId("department"), notFacetableDepartment.id, SearchFieldKind.Keyword, FacetMode.Terms)))
          )
        case Right(value) => fail(s"expected rejection, got $value")
      }
    }
  }

  "FacetPlanRegistry.unsafeFrom" should {
    "return the same usable registry as apply on valid declarations" in {
      val registry = FacetPlanRegistry.unsafeFrom(Vector(departmentDeclaration, stockDeclaration))
      assert(registry.ids == Vector(FacetId("department"), FacetId("stock")))
    }

    "throw naming every offending declaration's facet ID, index and validation error" in {
      val invalid = FacetPlanDeclaration(FacetRequest.Terms(FacetId("department"), notFacetableDepartment, validSize, TermsFacetOrder.CountDescThenKeyAsc, FacetCountingPolicy.AllAppliedHardFilters))
      val thrown = intercept[IllegalStateException](FacetPlanRegistry.unsafeFrom(Vector(invalid)))
      assert(thrown.getMessage.contains("department"))
      assert(thrown.getMessage.contains("0"))
      assert(thrown.getMessage.contains("UnsupportedFacetMode"))
    }

    "include first and duplicate indexes for every duplicate in its error text" in {
      val duplicate = FacetPlanDeclaration(FacetRequest.NumberRange(FacetId("department"), stockMin, validBuckets, FacetCountingPolicy.AllAppliedHardFilters))
      val thrown = intercept[IllegalStateException](FacetPlanRegistry.unsafeFrom(Vector(departmentDeclaration, duplicate, duplicate)))
      assert(thrown.getMessage.contains("department"))
      assert(thrown.getMessage.contains("firstIndex=0"))
      assert(thrown.getMessage.contains("duplicateIndex=1"))
      assert(thrown.getMessage.contains("duplicateIndex=2"))
    }
  }

  "FacetPlanDeclaration.fieldHandles" should {
    "derive exactly the one field a Terms declaration's request already reads" in {
      assert(departmentDeclaration.fieldHandles == Vector(department))
    }

    "derive exactly the one field a NumberRange declaration's request already reads" in {
      assert(stockDeclaration.fieldHandles == Vector(stockMin))
    }

    "derive exactly the from/to fields an IntervalOverlap declaration's request already reads, in that order" in {
      assert(intervalDeclaration.fieldHandles == Vector(stockMin, stockMax))
    }
  }

  "FacetPlanRegistry.resolve" should {
    "preserve requested order, independent of declaration order" in {
      FacetPlanRegistry(Vector(departmentDeclaration, stockDeclaration, intervalDeclaration)) match {
        case Right(registry) =>
          registry.resolve(Vector(FacetId("stockRange"), FacetId("department"))) match {
            case Right(resolved) => assert(resolved == Vector(intervalDeclaration.request, departmentDeclaration.request))
            case Left(errors)    => fail(s"expected success, got ${errors.toVector}")
          }
        case Left(errors) => fail(s"expected valid registry, got ${errors.toVector}")
      }
    }

    "report an unknown requested facet ID as a typed error rather than silently dropping it" in {
      FacetPlanRegistry(Vector(departmentDeclaration)) match {
        case Right(registry) =>
          registry.resolve(Vector(FacetId("missing"))) match {
            case Left(errors) => assert(errors.toVector == Vector(FacetPlanRegistryError.UnknownRequestedFacet(FacetId("missing"))))
            case Right(value) => fail(s"expected rejection, got $value")
          }
        case Left(errors) => fail(s"expected valid registry, got ${errors.toVector}")
      }
    }
  }

  "FacetPlanRegistry.publicFacets" should {
    "derive the same ordered facet IDs a caller would otherwise need its own lookup map to compute" in {
      FacetPlanRegistry(Vector(departmentDeclaration, stockDeclaration)) match {
        case Right(registry) =>
          assert(registry.publicFacets.ids == registry.ids)
          assert(registry.contains(FacetId("department")))
          assert(!registry.contains(FacetId("nonexistent")))
        case Left(errors) => fail(s"expected valid registry, got ${errors.toVector}")
      }
    }
  }
}
