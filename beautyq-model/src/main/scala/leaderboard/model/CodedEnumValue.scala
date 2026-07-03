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

enum NailServiceType(val intCode: Int) extends CodedEnumValue:
  case Manicure extends NailServiceType(1)
  case Pedicure extends NailServiceType(2)
  case Extension extends NailServiceType(3)
  case Refill extends NailServiceType(4)
  case Removal extends NailServiceType(5)
  case Repair extends NailServiceType(6)

object NailServiceType:
  private val byIntCode: Map[Int, NailServiceType] =
    values.iterator.map(value => value.intCode -> value).toMap

  private val byStringCode: Map[String, NailServiceType] =
    values.iterator.map(value => value.stringCode -> value).toMap

  def fromIntCode(code: Int): Option[NailServiceType] =
    byIntCode.get(code)

  def fromStringCode(code: String): Option[NailServiceType] =
    byStringCode.get(code)

enum LashServiceType(val intCode: Int) extends CodedEnumValue:
  case Extension extends LashServiceType(1)
  case Refill extends LashServiceType(2)
  case Lifting extends LashServiceType(3)
  case Tinting extends LashServiceType(4)
  case Removal extends LashServiceType(5)

object LashServiceType:
  private val byIntCode: Map[Int, LashServiceType] =
    values.iterator.map(value => value.intCode -> value).toMap

  private val byStringCode: Map[String, LashServiceType] =
    values.iterator.map(value => value.stringCode -> value).toMap

  def fromIntCode(code: Int): Option[LashServiceType] =
    byIntCode.get(code)

  def fromStringCode(code: String): Option[LashServiceType] =
    byStringCode.get(code)

enum LashVolume(val intCode: Int) extends CodedEnumValue:
  case Classic1D extends LashVolume(1)
  case Volume2D extends LashVolume(2)
  case Volume3D extends LashVolume(3)
  case MegaVolume extends LashVolume(4)

object LashVolume:
  private val byIntCode: Map[Int, LashVolume] =
    values.iterator.map(value => value.intCode -> value).toMap

  private val byStringCode: Map[String, LashVolume] =
    values.iterator.map(value => value.stringCode -> value).toMap

  def fromIntCode(code: Int): Option[LashVolume] =
    byIntCode.get(code)

  def fromStringCode(code: String): Option[LashVolume] =
    byStringCode.get(code)

enum BrowServiceType(val intCode: Int) extends CodedEnumValue:
  case Shaping extends BrowServiceType(1)
  case Tinting extends BrowServiceType(2)
  case Lamination extends BrowServiceType(3)
  case Henna extends BrowServiceType(4)

object BrowServiceType:
  private val byIntCode: Map[Int, BrowServiceType] =
    values.iterator.map(value => value.intCode -> value).toMap

  private val byStringCode: Map[String, BrowServiceType] =
    values.iterator.map(value => value.stringCode -> value).toMap

  def fromIntCode(code: Int): Option[BrowServiceType] =
    byIntCode.get(code)

  def fromStringCode(code: String): Option[BrowServiceType] =
    byStringCode.get(code)

enum PmuArea(val intCode: Int) extends CodedEnumValue:
  case Brows extends PmuArea(1)
  case Lips extends PmuArea(2)
  case Eyeliner extends PmuArea(3)

object PmuArea:
  private val byIntCode: Map[Int, PmuArea] =
    values.iterator.map(value => value.intCode -> value).toMap

  private val byStringCode: Map[String, PmuArea] =
    values.iterator.map(value => value.stringCode -> value).toMap

  def fromIntCode(code: Int): Option[PmuArea] =
    byIntCode.get(code)

  def fromStringCode(code: String): Option[PmuArea] =
    byStringCode.get(code)

enum FacialTreatmentType(val intCode: Int) extends CodedEnumValue:
  case Classic extends FacialTreatmentType(1)
  case Cleansing extends FacialTreatmentType(2)
  case Hydration extends FacialTreatmentType(3)
  case AntiAging extends FacialTreatmentType(4)
  case Peeling extends FacialTreatmentType(5)
  case Microneedling extends FacialTreatmentType(6)
  case BbGlow extends FacialTreatmentType(7)
  case Aquafacial extends FacialTreatmentType(8)

object FacialTreatmentType:
  private val byIntCode: Map[Int, FacialTreatmentType] =
    values.iterator.map(value => value.intCode -> value).toMap

  private val byStringCode: Map[String, FacialTreatmentType] =
    values.iterator.map(value => value.stringCode -> value).toMap

  def fromIntCode(code: Int): Option[FacialTreatmentType] =
    byIntCode.get(code)

  def fromStringCode(code: String): Option[FacialTreatmentType] =
    byStringCode.get(code)

enum BodyArea(val intCode: Int) extends CodedEnumValue:
  case UpperLip extends BodyArea(1)
  case Chin extends BodyArea(2)
  case Face extends BodyArea(3)
  case Armpits extends BodyArea(4)
  case Bikini extends BodyArea(5)
  case Brazilian extends BodyArea(6)
  case LowerLegs extends BodyArea(7)
  case FullLegs extends BodyArea(8)
  case Arms extends BodyArea(9)
  case Back extends BodyArea(10)
  case FaceNeckDecollete extends BodyArea(11)

object BodyArea:
  private val byIntCode: Map[Int, BodyArea] =
    values.iterator.map(value => value.intCode -> value).toMap

  private val byStringCode: Map[String, BodyArea] =
    values.iterator.map(value => value.stringCode -> value).toMap

  def fromIntCode(code: Int): Option[BodyArea] =
    byIntCode.get(code)

  def fromStringCode(code: String): Option[BodyArea] =
    byStringCode.get(code)
