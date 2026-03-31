package leaderboard.model

import leaderboard.model.AttributeValueType.{BigDecimalValue, IntValue}

sealed trait MasterServiceOfferVariantAttributeDefinition[+A] extends Product with Serializable {
  def code: String
  def valueType: AttributeValueType
}

case class AttributeMapImpl[V, K <: MasterServiceOfferVariantAttributeDefinition[V]](map: Map[K, V]) {
  def updated(key: K, value: V): AttributeMapImpl[V, K] = new AttributeMapImpl(map.updated(key, value))
  def get(key: K): Option[V] = map.get(key)
  def iterator: Iterator[(K, V)] = map.iterator
  def keysIterator: Iterator[K] = map.keysIterator
  def keySet: Set[K] = map.keySet
  def nonEmpty: Boolean = map.nonEmpty
  def isEmpty: Boolean = map.isEmpty
  def toMap: Map[K, V] = map
}

object AttributeMap {
  def apply[V, K <: MasterServiceOfferVariantAttributeDefinition[V]](map: Map[K, V]): AttributeMapImpl[V, K] = new AttributeMapImpl(map)

  def empty[V, K <: MasterServiceOfferVariantAttributeDefinition[V]] = new AttributeMapImpl[V, K](Map.empty)
}

sealed trait IntAttributeDefinition extends MasterServiceOfferVariantAttributeDefinition[Int] {
  final val valueType: AttributeValueType = IntValue
}

sealed trait BigDecimalAttributeDefinition extends MasterServiceOfferVariantAttributeDefinition[BigDecimal] {
  final val valueType: AttributeValueType = BigDecimalValue
}

object MasterServiceOfferVariantAttributeDefinition {
  type AnyAttributeDefinition = MasterServiceOfferVariantAttributeDefinition[Any]

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

  val all: List[AnyAttributeDefinition] =
    intDefinitions ++ bigDecimalDefinitions

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
}
