package leaderboard.search.dsl

import io.circe.syntax.*
import io.circe.{Codec, Decoder, DecodingFailure, Encoder, HCursor, Json}

sealed trait SearchConstraint extends Product with Serializable
object SearchConstraint {
  final case class ServiceAny(names: Set[String]) extends SearchConstraint
  final case class CategoryAny(names: Set[String]) extends SearchConstraint
  final case class EnumAttr(attributeCode: String, values: Set[String]) extends SearchConstraint
  final case class BoolAttr(attributeCode: String, value: Boolean) extends SearchConstraint
  final case class IntRange(attributeCode: String, min: Option[Int], max: Option[Int]) extends SearchConstraint
  final case class DecimalRange(attributeCode: String, min: Option[BigDecimal], max: Option[BigDecimal]) extends SearchConstraint
  final case class PriceRange(min: Option[BigDecimal], max: Option[BigDecimal]) extends SearchConstraint
  final case class DurationRange(min: Option[Int], max: Option[Int]) extends SearchConstraint
  case object NearUser extends SearchConstraint

  implicit val codec: Codec[SearchConstraint] = Codec.from(
    Decoder.instance {
      cursor =>
        cursor.get[String]("type").flatMap {
          case "service_any" =>
            cursor.get[Set[String]]("names").map(ServiceAny.apply)
          case "category_any" =>
            cursor.get[Set[String]]("names").map(CategoryAny.apply)
          case "enum_attr" =>
            for {
              attributeCode <- cursor.get[String]("attributeCode")
              values        <- cursor.get[Set[String]]("values")
            } yield EnumAttr(attributeCode, values)
          case "bool_attr" =>
            for {
              attributeCode <- cursor.get[String]("attributeCode")
              value         <- cursor.get[Boolean]("value")
            } yield BoolAttr(attributeCode, value)
          case "int_range" =>
            decodeRange[Int](cursor).map {
              case (attributeCode, min, max) => IntRange(attributeCode, min, max)
            }
          case "decimal_range" =>
            decodeRange[BigDecimal](cursor).map {
              case (attributeCode, min, max) => DecimalRange(attributeCode, min, max)
            }
          case "price_range" =>
            for {
              min <- cursor.get[Option[BigDecimal]]("min")
              max <- cursor.get[Option[BigDecimal]]("max")
            } yield PriceRange(min, max)
          case "duration_range" =>
            for {
              min <- cursor.get[Option[Int]]("min")
              max <- cursor.get[Option[Int]]("max")
            } yield DurationRange(min, max)
          case "near_user" =>
            Right(NearUser)
          case other =>
            Left(DecodingFailure(s"Unknown SearchConstraint type '$other'", cursor.history))
        }
    },
    Encoder.instance {
      case ServiceAny(names) =>
        Json.obj("type" -> "service_any".asJson, "names" -> names.asJson)
      case CategoryAny(names) =>
        Json.obj("type" -> "category_any".asJson, "names" -> names.asJson)
      case EnumAttr(attributeCode, values) =>
        Json.obj("type" -> "enum_attr".asJson, "attributeCode" -> attributeCode.asJson, "values" -> values.asJson)
      case BoolAttr(attributeCode, value) =>
        Json.obj("type" -> "bool_attr".asJson, "attributeCode" -> attributeCode.asJson, "value" -> value.asJson)
      case IntRange(attributeCode, min, max) =>
        Json.obj("type" -> "int_range".asJson, "attributeCode" -> attributeCode.asJson, "min" -> min.asJson, "max" -> max.asJson)
      case DecimalRange(attributeCode, min, max) =>
        Json.obj("type" -> "decimal_range".asJson, "attributeCode" -> attributeCode.asJson, "min" -> min.asJson, "max" -> max.asJson)
      case PriceRange(min, max) =>
        Json.obj("type" -> "price_range".asJson, "min" -> min.asJson, "max" -> max.asJson)
      case DurationRange(min, max) =>
        Json.obj("type" -> "duration_range".asJson, "min" -> min.asJson, "max" -> max.asJson)
      case NearUser =>
        Json.obj("type" -> "near_user".asJson)
    },
  )

  private def decodeRange[A: Decoder](
    cursor: HCursor
  ): Decoder.Result[(String, Option[A], Option[A])] =
    for {
      attributeCode <- cursor.get[String]("attributeCode")
      min           <- cursor.get[Option[A]]("min")
      max           <- cursor.get[Option[A]]("max")
    } yield (attributeCode, min, max)
}
