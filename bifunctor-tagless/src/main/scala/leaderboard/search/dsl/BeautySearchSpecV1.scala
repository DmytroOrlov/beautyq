package leaderboard.search.dsl

import leaderboard.model.*
import leaderboard.search.document.VariantSearchDocument

object BeautySearchSpecV1 {
  private val ManicureService = "Маникюр"
  private val PedicureService = "Педикюр"
  private val NailExtensionService = "Наращивание и моделирование ногтей"
  private val LashesService = "Ресницы"
  private val BrowsService = "Брови"
  private val PmuService = "Permanent Make-Up"
  private val HairRemovalService = "Удаление волос"
  private val FaceService = "Косметология лица"

  private val NailsCategory = "Ногти, маникюр и педикюр"
  private val EyesCategory = "Ресницы, брови и permanent make-up"
  private val FaceCategory = "Косметология лица и уход"
  private val HairRemovalCategory = "Удаление волос"

  lazy val spec: BeautySearchSpec = BeautySearchSpec(
    variantDocument = SearchDocumentSpec(
      indexName = "beautyq_variant_v1",
      id = _.variantId.toString,
      fields = baseFields ++ dynamicAttributeFields,
    ),
    synonyms = dictionary,
    carouselSpec = CarouselSpec(
      variantSize = 10,
      providerSize = 10,
      serviceIntentSize = 10,
      providerGroupField = "masterLocationId",
      serviceIntentGroupField = "serviceId",
      ranking = RankingSpec(
        textScoreWeight = 1.0,
        serviceBoostWeight = 2.0,
        attributeBoostWeight = 1.5,
        providerDistanceWeight = 1.25,
        providerMatchingVariantCountWeight = 0.5,
      ),
    ),
    facetSpec = FacetSpec(
      enabled = true,
      fields = facetFields,
      inferredFilterDominanceThreshold = BigDecimal("0.70"),
      inferredFilterMinCount = 2,
    ),
    requestSpec = SearchRequestSpec(
      hitWindowSize = 256,
      textOperator = TextOperator.And,
      aggregationSize = 20,
      geoDistanceScale = "5km",
      geoDistanceOffset = "0km",
      geoDistanceDecay = 0.5d,
    ),
  )

  private val baseFields: List[SearchField[VariantSearchDocument]] =
    List(
      SearchField(
        path = "variantId",
        kind = SearchFieldKind.Keyword,
        extract = document => Some(SearchValue.Keyword(document.variantId.toString)),
        semantic = Some(SearchFieldSemantic.VariantId),
        filterable = true,
        sortable = true,
      ),
      SearchField(
        path = "masterServiceOfferId",
        kind = SearchFieldKind.Keyword,
        extract = document => Some(SearchValue.Keyword(document.masterServiceOfferId.toString)),
        semantic = Some(SearchFieldSemantic.MasterServiceOfferId),
        filterable = true,
      ),
      SearchField(
        path = "masterLocationId",
        kind = SearchFieldKind.Keyword,
        extract = document => Some(SearchValue.Keyword(document.masterLocationId.toString)),
        semantic = Some(SearchFieldSemantic.MasterLocationId),
        filterable = true,
        facetable = true,
      ),
      SearchField(
        path = "masterId",
        kind = SearchFieldKind.Keyword,
        extract = document => Some(SearchValue.Keyword(document.masterId.toString)),
        semantic = Some(SearchFieldSemantic.MasterId),
        filterable = true,
      ),
      SearchField(
        path = "serviceId",
        kind = SearchFieldKind.Keyword,
        extract = document => Some(SearchValue.Keyword(document.serviceId.toString)),
        semantic = Some(SearchFieldSemantic.ServiceId),
        filterable = true,
        facetable = true,
      ),
      SearchField(
        path = "serviceName",
        kind = SearchFieldKind.Keyword,
        extract = document => Some(SearchValue.Keyword(document.serviceName)),
        semantic = Some(SearchFieldSemantic.ServiceName),
        filterable = true,
        facetable = true,
      ),
      SearchField(
        path = "categoryId",
        kind = SearchFieldKind.Keyword,
        extract = document => Some(SearchValue.Keyword(document.categoryId.toString)),
        semantic = Some(SearchFieldSemantic.CategoryId),
        filterable = true,
        facetable = true,
      ),
      SearchField(
        path = "categoryName",
        kind = SearchFieldKind.Keyword,
        extract = document => Some(SearchValue.Keyword(document.categoryName)),
        semantic = Some(SearchFieldSemantic.CategoryName),
        filterable = true,
        facetable = true,
      ),
      SearchField(
        path = "masterName",
        kind = SearchFieldKind.Keyword,
        extract = document => Some(SearchValue.Keyword(document.masterName)),
      ),
      SearchField(
        path = "locationName",
        kind = SearchFieldKind.Keyword,
        extract = document => Some(SearchValue.Keyword(document.locationName)),
      ),
      SearchField(
        path = "address",
        kind = SearchFieldKind.Keyword,
        extract = document => Some(SearchValue.Keyword(document.address)),
      ),
      SearchField(
        path = "lat",
        kind = SearchFieldKind.Decimal,
        extract = document => Some(SearchValue.Decimal(document.lat)),
      ),
      SearchField(
        path = "lon",
        kind = SearchFieldKind.Decimal,
        extract = document => Some(SearchValue.Decimal(document.lon)),
      ),
      SearchField(
        path = "priceFrom",
        kind = SearchFieldKind.Decimal,
        extract = document => Some(SearchValue.Decimal(document.priceFrom)),
        semantic = Some(SearchFieldSemantic.PriceFrom),
        filterable = true,
        facetable = true,
        sortable = true,
      ),
      SearchField(
        path = "priceTo",
        kind = SearchFieldKind.Decimal,
        extract = document => Some(SearchValue.Decimal(document.priceTo)),
        semantic = Some(SearchFieldSemantic.PriceTo),
        filterable = true,
        sortable = true,
      ),
      SearchField(
        path = "durationMin",
        kind = SearchFieldKind.Integer,
        extract = document => Some(SearchValue.Integer(document.durationMin)),
        semantic = Some(SearchFieldSemantic.DurationMin),
        filterable = true,
        facetable = true,
        sortable = true,
      ),
      SearchField(
        path = "location",
        kind = SearchFieldKind.GeoPoint,
        extract = document => Some(SearchValue.GeoPoint(document.location)),
        semantic = Some(SearchFieldSemantic.Location),
        sortable = true,
      ),
      SearchField(
        path = "allText",
        kind = SearchFieldKind.Text,
        extract = document => Some(SearchValue.Text(document.allText)),
        semantic = Some(SearchFieldSemantic.AllText),
        searchable = true,
        boost = 4.0,
      ),
      SearchField(
        path = "serviceText",
        kind = SearchFieldKind.Text,
        extract = document => Some(SearchValue.Text(document.serviceText)),
        semantic = Some(SearchFieldSemantic.ServiceText),
        searchable = true,
        boost = 5.0,
      ),
      SearchField(
        path = "attributeText",
        kind = SearchFieldKind.Text,
        extract = document => Some(SearchValue.Text(document.attributeText)),
        semantic = Some(SearchFieldSemantic.AttributeText),
        searchable = true,
        boost = 4.0,
      ),
      SearchField(
        path = "providerText",
        kind = SearchFieldKind.Text,
        extract = document => Some(SearchValue.Text(document.providerText)),
        semantic = Some(SearchFieldSemantic.ProviderText),
        searchable = true,
        boost = 2.0,
      ),
      SearchField(
        path = "locationText",
        kind = SearchFieldKind.Text,
        extract = document => Some(SearchValue.Text(document.locationText)),
        semantic = Some(SearchFieldSemantic.LocationText),
        searchable = true,
        boost = 2.5,
      ),
    )

  private val dynamicAttributeFields: List[SearchField[VariantSearchDocument]] =
    AttributeDefinition.all.flatMap {
      case definition: EnumAttributeDefinition[?] =>
        List(
          SearchField(
            path = s"enumAttributes.${definition.code}",
            kind = SearchFieldKind.Keyword,
            extract = document => document.enumAttributes.get(definition.code).map(SearchValue.Keyword.apply),
            semantic = Some(SearchFieldSemantic.EnumAttribute(definition.code)),
            filterable = true,
            facetable = true,
          )
        )
      case definition: BooleanAttributeDefinition =>
        List(
          SearchField(
            path = s"booleanAttributes.${definition.code}",
            kind = SearchFieldKind.Boolean,
            extract = document => document.booleanAttributes.get(definition.code).map(SearchValue.Boolean.apply),
            semantic = Some(SearchFieldSemantic.BooleanAttribute(definition.code)),
            filterable = true,
            facetable = true,
          )
        )
      case definition: IntAttributeDefinition =>
        List(
          SearchField(
            path = s"intAttributes.${definition.code}",
            kind = SearchFieldKind.Integer,
            extract = document => document.intAttributes.get(definition.code).map(SearchValue.Integer.apply),
            semantic = Some(SearchFieldSemantic.IntAttribute(definition.code)),
            filterable = true,
            facetable = true,
            sortable = true,
          )
        )
      case definition: BigDecimalAttributeDefinition =>
        List(
          SearchField(
            path = s"bigDecimalAttributes.${definition.code}",
            kind = SearchFieldKind.Decimal,
            extract = document => document.bigDecimalAttributes.get(definition.code).map(SearchValue.Decimal.apply),
            semantic = Some(SearchFieldSemantic.DecimalAttribute(definition.code)),
            filterable = true,
            facetable = true,
            sortable = true,
          )
        )
      case _ =>
        Nil
    }

  private val facetFields: List[FacetField] =
    List(
      FacetField("serviceName", FacetFieldMode.Terms, limit = 10),
      FacetField("categoryName", FacetFieldMode.Terms, limit = 10),
      FacetField(
        "priceFrom",
        FacetFieldMode.Ranges(
          List(
            FacetRangeBucket("0-30", max = Some(BigDecimal(30))),
            FacetRangeBucket("30-50", min = Some(BigDecimal(30)), max = Some(BigDecimal(50))),
            FacetRangeBucket("50-80", min = Some(BigDecimal(50)), max = Some(BigDecimal(80))),
            FacetRangeBucket("80-120", min = Some(BigDecimal(80)), max = Some(BigDecimal(120))),
            FacetRangeBucket("120+", min = Some(BigDecimal(120))),
          )
        )
      ),
      FacetField(
        "durationMin",
        FacetFieldMode.Ranges(
          List(
            FacetRangeBucket("0-30", max = Some(BigDecimal(30))),
            FacetRangeBucket("30-60", min = Some(BigDecimal(30)), max = Some(BigDecimal(60))),
            FacetRangeBucket("60-90", min = Some(BigDecimal(60)), max = Some(BigDecimal(90))),
            FacetRangeBucket("90-120", min = Some(BigDecimal(90)), max = Some(BigDecimal(120))),
            FacetRangeBucket("120+", min = Some(BigDecimal(120))),
          )
        )
      ),
    ) ++ AttributeDefinition.enumDefinitions.map { definition =>
      FacetField(s"enumAttributes.${definition.code}", FacetFieldMode.Terms, limit = definition.values.size)
    } ++ AttributeDefinition.booleanDefinitions.map { definition =>
      FacetField(s"booleanAttributes.${definition.code}", FacetFieldMode.Terms, limit = 2)
    }

  private val dictionary: List[SearchSynonym] =
    List(
      servicePhrase(Set(ManicureService, "маникюр"), ManicureService, Some("nail_service_type" -> "manicure")),
      servicePhrase(Set(PedicureService, "педикюр", "pedicure", "fußpflege"), PedicureService, Some("nail_service_type" -> "pedicure")),
      servicePhrase(Set(NailExtensionService, "наращивание ногтей", "acrylic nails", "nagelmodellage"), NailExtensionService, Some("nail_service_type" -> "extension")),
      servicePhrase(Set(LashesService, "ресницы", "lashes", "wimpern"), LashesService, None),
      servicePhrase(Set(BrowsService, "брови", "brows", "augenbrauen"), BrowsService, None),
      servicePhrase(Set(PmuService, "pmu", "permanent makeup", "permanent make up", "permanent make-up", "перманент"), PmuService, None),
      servicePhrase(Set(HairRemovalService, "удаление волос", "hair removal", "depilation", "депиляция"), HairRemovalService, None),
      servicePhrase(Set(FaceService, "косметология лица", "facial", "face treatment"), FaceService, None),
      phrase(Set(NailsCategory, "nails"), List(SearchConstraint.CategoryAny(Set(NailsCategory))), softBoosts = List(SearchConstraint.ServiceAny(Set(ManicureService, PedicureService, NailExtensionService)))),
      phrase(Set(EyesCategory), List(SearchConstraint.CategoryAny(Set(EyesCategory)))),
      phrase(Set(FaceCategory), List(SearchConstraint.CategoryAny(Set(FaceCategory)))),
      phrase(Set(HairRemovalCategory), List(SearchConstraint.CategoryAny(Set(HairRemovalCategory)))),
      phrase(Set("гель лак", "гель лак", "gel polish"), List(enumConstraint("nail_coating_type", "gel_polish"))),
      phrase(Set("shellac", "шелак"), List(enumConstraint("nail_coating_type", "shellac"))),
      phrase(Set("снять гель с ногтей", "снять гель"), List(SearchConstraint.ServiceAny(Set(NailExtensionService)), enumConstraint("nail_service_type", "removal"), enumConstraint("nail_coating_type", "gel"), boolConstraint("with_removal", true))),
      phrase(Set("gel removal"), List(SearchConstraint.ServiceAny(Set(NailExtensionService)), enumConstraint("nail_service_type", "removal"), enumConstraint("nail_coating_type", "gel"))),
      phrase(Set("не татуаж"), Nil),
      phrase(Set("без лака", "без покрытия", "no coating"), List(enumConstraint("nail_coating_type", "no_coating"))),
      phrase(Set("гель", "gel"), List(enumConstraint("nail_coating_type", "gel")), requires = List(enumConstraint("nail_service_type", "extension"))),
      phrase(Set("acrylic"), List(enumConstraint("nail_coating_type", "acrylic")), requires = List(enumConstraint("nail_service_type", "extension"))),
      phrase(Set("классика", "classic", "1d", "1 д", "classic1_d"), List(enumConstraint("lash_volume", "classic1_d"), enumConstraint("lash_service_type", "extension")), requires = List(SearchConstraint.ServiceAny(Set(LashesService)))),
      phrase(Set("2д", "2d", "volume2_d"), List(enumConstraint("lash_volume", "volume2_d"), enumConstraint("lash_service_type", "extension")), requires = List(SearchConstraint.ServiceAny(Set(LashesService)))),
      phrase(Set("3д", "3d", "volume3_d"), List(enumConstraint("lash_volume", "volume3_d"), enumConstraint("lash_service_type", "extension")), requires = List(SearchConstraint.ServiceAny(Set(LashesService)))),
      phrase(Set("mega volume"), List(enumConstraint("lash_volume", "mega_volume"), enumConstraint("lash_service_type", "extension")), requires = List(SearchConstraint.ServiceAny(Set(LashesService)))),
      phrase(Set("коррекция ресниц 2д", "коррекция ресниц 2d"), List(enumConstraint("lash_volume", "volume2_d"), enumConstraint("lash_service_type", "refill"), boolConstraint("with_correction", true), SearchConstraint.ServiceAny(Set(LashesService)))),
      phrase(Set("коррекция ресниц"), List(enumConstraint("lash_service_type", "refill"), boolConstraint("with_correction", true), SearchConstraint.ServiceAny(Set(LashesService)))),
      phrase(Set("lash lifting"), List(enumConstraint("lash_service_type", "lifting"), SearchConstraint.ServiceAny(Set(LashesService)))),
      phrase(Set("lifting"), List(enumConstraint("lash_service_type", "lifting")), requires = List(SearchConstraint.ServiceAny(Set(LashesService)))),
      phrase(Set("снять ресницы"), List(SearchConstraint.ServiceAny(Set(LashesService)), enumConstraint("lash_service_type", "removal"), boolConstraint("with_removal", true))),
      phrase(Set("хна", "henna"), List(enumConstraint("brow_service_type", "henna"), boolConstraint("with_tinting", true)), requires = List(SearchConstraint.ServiceAny(Set(BrowsService)))),
      phrase(Set("lamination", "ламинирование"), List(enumConstraint("brow_service_type", "lamination")), requires = List(SearchConstraint.ServiceAny(Set(BrowsService)))),
      phrase(Set("brow lamination"), List(SearchConstraint.ServiceAny(Set(BrowsService)), enumConstraint("brow_service_type", "lamination"))),
      phrase(Set("augenbrauen färben"), List(SearchConstraint.ServiceAny(Set(BrowsService)), enumConstraint("brow_service_type", "tinting"), boolConstraint("with_tinting", true))),
      phrase(Set("shape and tint brows"), List(SearchConstraint.ServiceAny(Set(BrowsService)), SearchConstraint.EnumAttr("brow_service_type", Set("shaping", "tinting")), boolConstraint("with_tinting", true))),
      phrase(Set("коррекция бровей"), List(enumConstraint("brow_service_type", "shaping"), SearchConstraint.ServiceAny(Set(BrowsService)))),
      phrase(Set("shaping"), List(enumConstraint("brow_service_type", "shaping")), requires = List(SearchConstraint.ServiceAny(Set(BrowsService)))),
      phrase(Set("färben", "tint", "окрашивание"), List(boolConstraint("with_tinting", true)), requires = List(SearchConstraint.ServiceAny(Set(BrowsService)))),
      phrase(Set("färben", "tint", "окрашивание"), List(boolConstraint("with_tinting", true)), requires = List(SearchConstraint.ServiceAny(Set(LashesService)))),
      phrase(Set("губы", "lips"), List(enumConstraint("pmu_area", "lips")), requires = List(SearchConstraint.ServiceAny(Set(PmuService)))),
      phrase(Set("eyeliner"), List(enumConstraint("pmu_area", "eyeliner")), requires = List(SearchConstraint.ServiceAny(Set(PmuService)))),
      phrase(Set("powder brows", "brows"), List(enumConstraint("pmu_area", "brows")), requires = List(SearchConstraint.ServiceAny(Set(PmuService)))),
      phrase(Set("aquafacial"), List(SearchConstraint.ServiceAny(Set(FaceService)), enumConstraint("facial_treatment_type", "aquafacial"), enumConstraint("body_area", "face"))),
      phrase(Set("microneedling"), List(SearchConstraint.ServiceAny(Set(FaceService)), enumConstraint("facial_treatment_type", "microneedling"), enumConstraint("body_area", "face"))),
      phrase(Set("bb glow"), List(SearchConstraint.ServiceAny(Set(FaceService)), enumConstraint("facial_treatment_type", "bb_glow"), enumConstraint("body_area", "face"))),
      phrase(Set("чистка лица"), List(SearchConstraint.ServiceAny(Set(FaceService)), enumConstraint("facial_treatment_type", "cleansing"), enumConstraint("body_area", "face"))),
      phrase(Set("классический уход лицо"), List(SearchConstraint.ServiceAny(Set(FaceService)), enumConstraint("facial_treatment_type", "classic"), enumConstraint("body_area", "face"))),
      phrase(Set("face"), List(enumConstraint("body_area", "face")), requires = List(SearchConstraint.ServiceAny(Set(FaceService)))),
      phrase(Set("wax", "воск"), List(SearchConstraint.ServiceAny(Set(HairRemovalService)), enumConstraint("hair_removal_method", "wax"))),
      phrase(Set("sugaring"), List(SearchConstraint.ServiceAny(Set(HairRemovalService)), enumConstraint("hair_removal_method", "sugaring"))),
      phrase(Set("laser", "лазер"), List(SearchConstraint.ServiceAny(Set(HairRemovalService)), enumConstraint("hair_removal_method", "laser"))),
      phrase(Set("threading"), List(SearchConstraint.ServiceAny(Set(HairRemovalService)), enumConstraint("hair_removal_method", "threading"))),
      phrase(Set("upper lip", "верхняя губа"), List(enumConstraint("body_area", "upper_lip")), requires = List(SearchConstraint.ServiceAny(Set(HairRemovalService)))),
      phrase(Set("губа"), List(enumConstraint("body_area", "upper_lip")), requires = List(SearchConstraint.ServiceAny(Set(HairRemovalService)))),
      phrase(Set("chin"), List(enumConstraint("body_area", "chin")), requires = List(SearchConstraint.ServiceAny(Set(HairRemovalService)))),
      phrase(Set("armpits"), List(enumConstraint("body_area", "armpits")), requires = List(SearchConstraint.ServiceAny(Set(HairRemovalService)))),
      phrase(Set("bikini"), List(enumConstraint("body_area", "bikini")), requires = List(SearchConstraint.ServiceAny(Set(HairRemovalService)))),
      phrase(Set("lower legs"), List(enumConstraint("body_area", "lower_legs")), requires = List(SearchConstraint.ServiceAny(Set(HairRemovalService)))),
    )

  private def servicePhrase(
    tokens: Set[String],
    serviceName: String,
    enumAttribute: Option[(String, String)],
  ): SearchSynonym =
    phrase(
      tokens,
      List(SearchConstraint.ServiceAny(Set(serviceName))) ++ enumAttribute.toList.map { case (code, value) => enumConstraint(code, value) },
    )

  private def phrase(
    tokens: Set[String],
    constraints: List[SearchConstraint],
    softBoosts: List[SearchConstraint] = Nil,
    requires: List[SearchConstraint] = Nil,
  ): SearchSynonym =
    SearchSynonym(
      tokens = tokens,
      constraints = constraints,
      softBoosts = softBoosts,
      requires = requires,
    )

  private def enumConstraint(attributeCode: String, value: String): SearchConstraint =
    SearchConstraint.EnumAttr(attributeCode, Set(value))

  private def boolConstraint(attributeCode: String, value: Boolean): SearchConstraint =
    SearchConstraint.BoolAttr(attributeCode, value)
}
