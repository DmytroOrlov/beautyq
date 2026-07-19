package leaderboard.search.gen2.contract

import org.scalatest.wordspec.AnyWordSpec

final class PublicSortRegistrySpec extends AnyWordSpec {
  private final case class Input(name: PublicSortName)
  private final case class Spec(name: PublicSortName, result: String) extends PublicSortSpec[Input, PublicSortName, String, String] {
    def decode(input: Input): Either[NonEmptyErrors[String], String] = Right(s"$result:${input.name.value}")
  }

  "PublicSortRegistry" should {
    "preserve declaration order and centralize lookup" in {
      val registry = PublicSortRegistry.unsafeFrom[Input, PublicSortName, String, String](
        Vector(Spec(PublicSortName("price"), "price"), Spec(PublicSortName("distance"), "distance")),
        _.name,
        name => s"unknown:${name.value}",
      )
      assert(registry.names == Vector(PublicSortName("price"), PublicSortName("distance")))
      assert(registry.decode(Input(PublicSortName("distance"))) == Right("distance:distance"))
      registry.decode(Input(PublicSortName("missing"))) match {
        case Left(errors) => assert(errors.toVector == Vector("unknown:missing"))
        case Right(value) => fail(s"expected unknown sort, got $value")
      }
    }

    "reject duplicate names without last-wins lookup" in {
      PublicSortRegistry[Input, PublicSortName, String, String](
        Vector(Spec(PublicSortName("price"), "first"), Spec(PublicSortName("price"), "second")),
        _.name,
        name => s"unknown:${name.value}",
      ) match {
        case Left(errors) =>
          assert(errors.toVector == Vector(PublicDeclarationError.DuplicateName(PublicSortName("price"), 0, 1)))
        case Right(registry) => fail(s"expected duplicate rejection, got ${registry.names}")
      }
    }

    "render unsafe duplicate failures with first and duplicate indexes" in {
      val thrown = intercept[IllegalStateException] {
        PublicSortRegistry.unsafeFrom[Input, PublicSortName, String, String](
          Vector(Spec(PublicSortName("price"), "first"), Spec(PublicSortName("price"), "second")),
          _.name,
          name => s"unknown:${name.value}",
        )
      }
      assert(thrown.getMessage.contains("firstIndex=0"))
      assert(thrown.getMessage.contains("duplicateIndex=1"))
    }
  }
}
