package leaderboard.model

import org.scalatest.wordspec.AnyWordSpec

final class StableCodeSpec extends AnyWordSpec {

  private val acceptedServiceCodes: Vector[String] = Vector(
    "manicure",
    "pedicure",
    "nail_modeling",
    "lashes",
    "brows",
    "pmu",
    "hair_removal",
    "facial",
    "mobile_beauty",
  )

  private val acceptedCategoryCodes: Vector[String] = Vector(
    "all_services",
    "nails",
    "lashes_brows_pmu",
    "facial_care",
    "hair_removal",
  )

  private val grammarPositiveExamples: Vector[String] = Vector(
    "manicure",
    "pmu",
    "nail_modeling",
    "facial_care",
    "service_code_0123456789abcdef",
    "category2",
  )

  private val invalidExamples: Vector[String] = Vector(
    "",
    " ",
    "Manicure",
    "NAILS",
    "nail-modeling",
    "nail modeling",
    "1service",
    "_service",
    "service_",
    "service__code",
    "маникюр",
  )

  "ServiceCode" should {
    "accept every accepted seed code" in {
      acceptedServiceCodes.foreach {
        raw =>
          assert(ServiceCode.fromString(raw).isRight)
      }
    }

    "accept lowercase letters, digits after the first character, and multiple snake-case segments" in {
      grammarPositiveExamples.foreach {
        raw =>
          assert(ServiceCode.fromString(raw).isRight)
      }
    }

    "return the exact input from .value" in {
      ServiceCode.fromString("manicure") match {
        case Right(code) =>
          assert(code.value == "manicure")
        case Left(error) =>
          fail(s"expected acceptance, got: ${error.message}")
      }
    }

    "encode to the exact JSON string" in {
      ServiceCode.fromString("manicure") match {
        case Right(code) =>
          assert(ServiceCode.codec(code).noSpaces == "\"manicure\"")
        case Left(error) =>
          fail(s"expected acceptance, got: ${error.message}")
      }
    }

    "round-trip through Circe for every accepted code" in {
      acceptedServiceCodes.foreach {
        raw =>
          ServiceCode.fromString(raw) match {
            case Right(code) =>
              assert(ServiceCode.codec.decodeJson(ServiceCode.codec(code)) == Right(code))
            case Left(error) =>
              fail(s"expected acceptance for '$raw', got: ${error.message}")
          }
      }
    }

    "not trim or lowercase input before validating" in {
      assert(ServiceCode.fromString(" manicure").isLeft)
      assert(ServiceCode.fromString("manicure ").isLeft)
      assert(ServiceCode.fromString("MANICURE").isLeft)
      assert(ServiceCode.fromString("Manicure").isLeft)
    }

    "reject every invalid example, preserving the original input, exact codeType, and exact message" in {
      invalidExamples.foreach {
        invalid =>
          ServiceCode.fromString(invalid) match {
            case Left(error) =>
              assert(error.value == invalid)
              assert(error.codeType == "ServiceCode")
              assert(
                error.message ==
                  s"Invalid ServiceCode '$invalid': expected lowercase snake_case matching [a-z][a-z0-9]*(?:_[a-z0-9]+)*"
              )
            case Right(value) =>
              fail(s"expected rejection for '$invalid', got: $value")
          }
      }
    }
  }

  "CategoryCode" should {
    "accept every accepted seed code" in {
      acceptedCategoryCodes.foreach {
        raw =>
          assert(CategoryCode.fromString(raw).isRight)
      }
    }

    "accept lowercase letters, digits after the first character, and multiple snake-case segments" in {
      grammarPositiveExamples.foreach {
        raw =>
          assert(CategoryCode.fromString(raw).isRight)
      }
    }

    "return the exact input from .value" in {
      CategoryCode.fromString("nails") match {
        case Right(code) =>
          assert(code.value == "nails")
        case Left(error) =>
          fail(s"expected acceptance, got: ${error.message}")
      }
    }

    "encode to the exact JSON string" in {
      CategoryCode.fromString("nails") match {
        case Right(code) =>
          assert(CategoryCode.codec(code).noSpaces == "\"nails\"")
        case Left(error) =>
          fail(s"expected acceptance, got: ${error.message}")
      }
    }

    "round-trip through Circe for every accepted code" in {
      acceptedCategoryCodes.foreach {
        raw =>
          CategoryCode.fromString(raw) match {
            case Right(code) =>
              assert(CategoryCode.codec.decodeJson(CategoryCode.codec(code)) == Right(code))
            case Left(error) =>
              fail(s"expected acceptance for '$raw', got: ${error.message}")
          }
      }
    }

    "not trim or lowercase input before validating" in {
      assert(CategoryCode.fromString(" nails").isLeft)
      assert(CategoryCode.fromString("nails ").isLeft)
      assert(CategoryCode.fromString("NAILS").isLeft)
      assert(CategoryCode.fromString("Nails").isLeft)
    }

    "reject every invalid example, preserving the original input, exact codeType, and exact message" in {
      invalidExamples.foreach {
        invalid =>
          CategoryCode.fromString(invalid) match {
            case Left(error) =>
              assert(error.value == invalid)
              assert(error.codeType == "CategoryCode")
              assert(
                error.message ==
                  s"Invalid CategoryCode '$invalid': expected lowercase snake_case matching [a-z][a-z0-9]*(?:_[a-z0-9]+)*"
              )
            case Right(value) =>
              fail(s"expected rejection for '$invalid', got: $value")
          }
      }
    }
  }

  "Category.rootCategoryCode" should {
    "equal all_services" in {
      assert(Category.rootCategoryCode.value == "all_services")
    }
  }
}
