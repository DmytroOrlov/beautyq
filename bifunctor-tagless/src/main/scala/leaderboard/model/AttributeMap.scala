package leaderboard.model

object AttributeMap {
  def empty[V, K <: AttributeDefinition[V]] = Impl[V, K](Map.empty)

  case class Impl[V, K <: AttributeDefinition[V]](map: Map[K, V]) {
    def updated(key: K, value: V): Impl[V, K] = Impl(map.updated(key, value))
    def get(key: K): Option[V]                = map.get(key)
    def iterator: Iterator[(K, V)]            = map.iterator
    def keysIterator: Iterator[K]             = map.keysIterator
    def keySet: Set[K]                        = map.keySet
    def nonEmpty: Boolean                     = map.nonEmpty
    def isEmpty: Boolean                      = map.isEmpty
    def toMap: Map[K, V]                      = map
  }
}

final case class AttributeValueName[A](name: String)

object AttributeValueName {
  def apply[A](implicit ev: AttributeValueName[A]): AttributeValueName[A] = ev

  implicit val intAttributeValueName: AttributeValueName[Int]               = AttributeValueName("Int")
  implicit val bigDecimalAttributeValueName: AttributeValueName[BigDecimal] = AttributeValueName("BigDecimal")
}
