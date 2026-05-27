package leaderboard.model

trait CodedEnumValue extends Product {
  def intCode: Int

  final def stringCode: String =
    CodedEnumValue.camelToSnake(productPrefix)
}

object CodedEnumValue {
  def camelToSnake(value: String): String =
    value
      .replaceAll("([a-z0-9])([A-Z])", "$1_$2")
      .replaceAll("([A-Z]+)([A-Z][a-z])", "$1_$2")
      .toLowerCase
}


enum NailCoatingType(val intCode: Int) extends CodedEnumValue:
  case NoCoating extends NailCoatingType(1)
  case RegularPolish extends NailCoatingType(2)
  case GelPolish extends NailCoatingType(3)
  case Shellac extends NailCoatingType(4)
  case Gel extends NailCoatingType(5)
  case Acrylic extends NailCoatingType(6)

object NailCoatingType:
  private val byIntCode: Map[Int, NailCoatingType] =
    values.iterator.map(value => value.intCode -> value).toMap

  private val byStringCode: Map[String, NailCoatingType] =
    values.iterator.map(value => value.stringCode -> value).toMap

  def fromIntCode(code: Int): Option[NailCoatingType] =
    byIntCode.get(code)

  def fromStringCode(code: String): Option[NailCoatingType] =
    byStringCode.get(code)


enum HairRemovalMethod(val intCode: Int) extends CodedEnumValue:
  case Wax extends HairRemovalMethod(1)
  case Sugaring extends HairRemovalMethod(2)
  case Laser extends HairRemovalMethod(3)
  case Threading extends HairRemovalMethod(4)

object HairRemovalMethod:
  private val byIntCode: Map[Int, HairRemovalMethod] =
    values.iterator.map(value => value.intCode -> value).toMap

  private val byStringCode: Map[String, HairRemovalMethod] =
    values.iterator.map(value => value.stringCode -> value).toMap

  def fromIntCode(code: Int): Option[HairRemovalMethod] =
    byIntCode.get(code)

  def fromStringCode(code: String): Option[HairRemovalMethod] =
    byStringCode.get(code)
