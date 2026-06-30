package leaderboard.search.dsl

object BeautyQSearchFieldSemantics {
  val VariantId: SearchFieldSemantic = SearchFieldSemantic("variantId")
  val MasterServiceOfferId: SearchFieldSemantic = SearchFieldSemantic("masterServiceOfferId")
  val MasterLocationId: SearchFieldSemantic = SearchFieldSemantic("masterLocationId")
  val MasterId: SearchFieldSemantic = SearchFieldSemantic("masterId")
  val ServiceId: SearchFieldSemantic = SearchFieldSemantic("serviceId")
  val ServiceName: SearchFieldSemantic = SearchFieldSemantic("serviceName")
  val CategoryId: SearchFieldSemantic = SearchFieldSemantic("categoryId")
  val CategoryName: SearchFieldSemantic = SearchFieldSemantic("categoryName")
  val PriceFrom: SearchFieldSemantic = SearchFieldSemantic("priceFrom")
  val PriceTo: SearchFieldSemantic = SearchFieldSemantic("priceTo")
  val DurationMin: SearchFieldSemantic = SearchFieldSemantic("durationMin")
  val Location: SearchFieldSemantic = SearchFieldSemantic("location")
  val AllText: SearchFieldSemantic = SearchFieldSemantic("allText")
  val ServiceText: SearchFieldSemantic = SearchFieldSemantic("serviceText")
  val AttributeText: SearchFieldSemantic = SearchFieldSemantic("attributeText")
  val ProviderText: SearchFieldSemantic = SearchFieldSemantic("providerText")
  val LocationText: SearchFieldSemantic = SearchFieldSemantic("locationText")

  def EnumAttribute(attributeCode: String): SearchFieldSemantic =
    SearchFieldSemantic(s"enumAttributes.$attributeCode")

  def BooleanAttribute(attributeCode: String): SearchFieldSemantic =
    SearchFieldSemantic(s"booleanAttributes.$attributeCode")

  def IntAttribute(attributeCode: String): SearchFieldSemantic =
    SearchFieldSemantic(s"intAttributes.$attributeCode")

  def DecimalAttribute(attributeCode: String): SearchFieldSemantic =
    SearchFieldSemantic(s"bigDecimalAttributes.$attributeCode")
}
