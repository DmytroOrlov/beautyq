package leaderboard.search.gen2.contract

import org.scalatest.wordspec.AnyWordSpec

final class PublicInputRegistrySpec extends AnyWordSpec {
  private final case class Input(field: PublicFieldName, operator: PublicOperator)
  private final case class Spec(name: PublicFieldName, acceptedOperators: Vector[PublicOperator], result: String) extends PublicInputSpec[Input, PublicFieldName, PublicOperator, String, String] {
    def decode(input: Input): Either[NonEmptyErrors[String], String] = Right(s"${result}:${input.operator}")
  }

  "PublicInputRegistry" should {
    "project field capabilities to deterministic public operators" in {
      assert(
        PublicOperator.fromFilterCapabilities(Set(FilterOperator.Range, FilterOperator.Equal)) ==
          Vector(PublicOperator.Equal, PublicOperator.GreaterThan, PublicOperator.GreaterThanOrEqual, PublicOperator.LessThan, PublicOperator.LessThanOrEqual, PublicOperator.Between),
      )
    }

    "derive ordered inventory and centralize lookup/operator gating for a neutral input" in {
      val service = PublicFieldName("service")
      val category = PublicFieldName("category")
      val registry = PublicInputRegistry[Input, PublicFieldName, PublicOperator, String, String](
        Vector(
          Spec(service, Vector(PublicOperator.Equal), "service"),
          Spec(category, Vector(PublicOperator.In), "category"),
        ),
        _.field,
        _.operator,
        name => s"unknown:${name.value}",
        (name, operator, _) => s"unsupported:${name.value}:${operator}",
      )

      assert(registry.fields.map(_.name) == Vector(service, category))
      assert(registry.decode(Input(service, PublicOperator.Equal)) == Right("service:Equal"))
      registry.decode(Input(service, PublicOperator.In)) match {
        case Left(errors) => assert(errors.toVector == Vector("unsupported:service:In"))
        case Right(value) => fail(s"expected unsupported operator, got $value")
      }
      registry.decode(Input(PublicFieldName("missing"), PublicOperator.Equal)) match {
        case Left(errors) => assert(errors.toVector == Vector("unknown:missing"))
        case Right(value) => fail(s"expected unknown field, got $value")
      }
    }
  }
}
