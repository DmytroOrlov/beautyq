package leaderboard.model

object AttributeMap {
  def apply[V, K <: MasterServiceOfferVariantAttributeDefinition[V]](map: Map[K, V]): AttributeMapImpl[V, K] = new AttributeMapImpl(map)

  def empty[V, K <: MasterServiceOfferVariantAttributeDefinition[V]] = AttributeMapImpl[V, K](Map.empty)
}

case class AttributeMapImpl[V, K <: MasterServiceOfferVariantAttributeDefinition[V]](map: Map[K, V]) {
  def updated(key: K, value: V): AttributeMapImpl[V, K] = AttributeMapImpl(map.updated(key, value))
  def get(key: K): Option[V]                            = map.get(key)
  def iterator: Iterator[(K, V)]                        = map.iterator
  def keysIterator: Iterator[K]                         = map.keysIterator
  def keySet: Set[K]                                    = map.keySet
  def nonEmpty: Boolean                                 = map.nonEmpty
  def isEmpty: Boolean                                  = map.isEmpty
  def toMap: Map[K, V]                                  = map
}

final case class AttributeValueName[A](name: String)

object AttributeValueName {
  def apply[A](implicit ev: AttributeValueName[A]): AttributeValueName[A] = ev

  implicit val intAttributeValueName: AttributeValueName[Int]               = AttributeValueName("Int")
  implicit val bigDecimalAttributeValueName: AttributeValueName[BigDecimal] = AttributeValueName("BigDecimal")
}
