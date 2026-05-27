package leaderboard.model

sealed trait AttributeDefinition[+A] extends Product with Serializable {
  def code: String
  def valueType: String
}

sealed trait IntAttributeDefinition extends AttributeDefinition[Int] {
  final val valueType = "Int"
}

sealed trait BigDecimalAttributeDefinition extends AttributeDefinition[BigDecimal] {
  final val valueType = "BigDecimal"
}

sealed trait EnumAttributeDefinition[E <: CodedEnumValue] extends AttributeDefinition[E] {
  final val valueType = "Enum"
  def values: List[E]
  def fromIntCode(code: Int): Option[E]
  def fromStringCode(code: String): Option[E]
}

object AttributeDefinition {
  type AnyAttributeDefinition = AttributeDefinition[Any]

  case object SessionCount extends IntAttributeDefinition {
    val code = "session_count"
  }

  case object IncludedCorrectionsCount extends IntAttributeDefinition {
    val code = "included_corrections_count"
  }

  case object MaxClients extends IntAttributeDefinition {
    val code = "max_clients"
  }

  case object DepositAmount extends BigDecimalAttributeDefinition {
    val code = "deposit_amount"
  }

  case object HomeVisitSurcharge extends BigDecimalAttributeDefinition {
    val code = "home_visit_surcharge"
  }

  case object MaterialsSurcharge extends BigDecimalAttributeDefinition {
    val code = "materials_surcharge"
  }

  case object FixedDiscountAmount extends BigDecimalAttributeDefinition {
    val code = "fixed_discount_amount"
  }

  case object HairRemovalMethodAttribute extends EnumAttributeDefinition[HairRemovalMethod] {
    val code = "hair_removal_method"

    def values: List[HairRemovalMethod] =
      HairRemovalMethod.values.toList

    def fromIntCode(code: Int): Option[HairRemovalMethod] =
      HairRemovalMethod.fromIntCode(code)

    def fromStringCode(code: String): Option[HairRemovalMethod] =
      HairRemovalMethod.fromStringCode(code)
  }

  case object NailCoatingTypeAttribute extends EnumAttributeDefinition[NailCoatingType] {
    val code = "nail_coating_type"

    def values: List[NailCoatingType] =
      NailCoatingType.values.toList

    def fromIntCode(code: Int): Option[NailCoatingType] =
      NailCoatingType.fromIntCode(code)

    def fromStringCode(code: String): Option[NailCoatingType] =
      NailCoatingType.fromStringCode(code)
  }

  case object NailServiceTypeAttribute extends EnumAttributeDefinition[NailServiceType] {
    val code = "nail_service_type"

    def values: List[NailServiceType] =
      NailServiceType.values.toList

    def fromIntCode(code: Int): Option[NailServiceType] =
      NailServiceType.fromIntCode(code)

    def fromStringCode(code: String): Option[NailServiceType] =
      NailServiceType.fromStringCode(code)
  }

  case object LashServiceTypeAttribute extends EnumAttributeDefinition[LashServiceType] {
    val code = "lash_service_type"

    def values: List[LashServiceType] =
      LashServiceType.values.toList

    def fromIntCode(code: Int): Option[LashServiceType] =
      LashServiceType.fromIntCode(code)

    def fromStringCode(code: String): Option[LashServiceType] =
      LashServiceType.fromStringCode(code)
  }

  case object LashVolumeAttribute extends EnumAttributeDefinition[LashVolume] {
    val code = "lash_volume"

    def values: List[LashVolume] =
      LashVolume.values.toList

    def fromIntCode(code: Int): Option[LashVolume] =
      LashVolume.fromIntCode(code)

    def fromStringCode(code: String): Option[LashVolume] =
      LashVolume.fromStringCode(code)
  }

  case object BrowServiceTypeAttribute extends EnumAttributeDefinition[BrowServiceType] {
    val code = "brow_service_type"

    def values: List[BrowServiceType] =
      BrowServiceType.values.toList

    def fromIntCode(code: Int): Option[BrowServiceType] =
      BrowServiceType.fromIntCode(code)

    def fromStringCode(code: String): Option[BrowServiceType] =
      BrowServiceType.fromStringCode(code)
  }

  case object PmuAreaAttribute extends EnumAttributeDefinition[PmuArea] {
    val code = "pmu_area"

    def values: List[PmuArea] =
      PmuArea.values.toList

    def fromIntCode(code: Int): Option[PmuArea] =
      PmuArea.fromIntCode(code)

    def fromStringCode(code: String): Option[PmuArea] =
      PmuArea.fromStringCode(code)
  }

  case object FacialTreatmentTypeAttribute extends EnumAttributeDefinition[FacialTreatmentType] {
    val code = "facial_treatment_type"

    def values: List[FacialTreatmentType] =
      FacialTreatmentType.values.toList

    def fromIntCode(code: Int): Option[FacialTreatmentType] =
      FacialTreatmentType.fromIntCode(code)

    def fromStringCode(code: String): Option[FacialTreatmentType] =
      FacialTreatmentType.fromStringCode(code)
  }

  case object BodyAreaAttribute extends EnumAttributeDefinition[BodyArea] {
    val code = "body_area"

    def values: List[BodyArea] =
      BodyArea.values.toList

    def fromIntCode(code: Int): Option[BodyArea] =
      BodyArea.fromIntCode(code)

    def fromStringCode(code: String): Option[BodyArea] =
      BodyArea.fromStringCode(code)
  }

  val intDefinitions: List[IntAttributeDefinition] =
    List(
      SessionCount,
      IncludedCorrectionsCount,
      MaxClients,
    )

  val bigDecimalDefinitions: List[BigDecimalAttributeDefinition] =
    List(
      DepositAmount,
      HomeVisitSurcharge,
      MaterialsSurcharge,
      FixedDiscountAmount,
    )

  val enumDefinitions: List[EnumAttributeDefinition[?]] =
    List(
      HairRemovalMethodAttribute,
      NailCoatingTypeAttribute,
      NailServiceTypeAttribute,
      LashServiceTypeAttribute,
      LashVolumeAttribute,
      BrowServiceTypeAttribute,
      PmuAreaAttribute,
      FacialTreatmentTypeAttribute,
      BodyAreaAttribute,
    )

  val all: List[AnyAttributeDefinition] =
    intDefinitions ++ bigDecimalDefinitions ++ enumDefinitions

  val byCode: Map[String, AnyAttributeDefinition] =
    all.iterator.map(definition => definition.code -> definition).toMap

  def fromCode(code: String): Option[AnyAttributeDefinition] =
    byCode.get(code)

  def fromCodeAsInt(code: String): Option[IntAttributeDefinition] =
    fromCode(code).collect {
      case definition: IntAttributeDefinition =>
        definition
    }

  def fromCodeAsBigDecimal(code: String): Option[BigDecimalAttributeDefinition] =
    fromCode(code).collect {
      case definition: BigDecimalAttributeDefinition =>
        definition
    }

  def fromCodeAsEnum(code: String): Option[EnumAttributeDefinition[?]] =
    fromCode(code).collect {
      case definition: EnumAttributeDefinition[?] =>
        definition
    }
}
