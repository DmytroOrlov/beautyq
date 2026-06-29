package leaderboard.search.dsl

import leaderboard.model.*
import leaderboard.search.document.{BeautyQVariantSearchDocumentSchema, VariantSearchDocument}

object BeautySearchSpecV1 {
  private val Fields = BeautyQVariantSearchDocumentSchema.Fields

  private val ManicureService = "Маникюр"
  private val PedicureService = "Педикюр"
  private val NailExtensionService = "Наращивание и моделирование ногтей"
  private val LashesService = "Ресницы"
  private val BrowsService = "Брови"
  private val PmuService = "Permanent Make-Up"
  private val HairRemovalService = "Удаление волос"
  private val FaceService = "Косметология лица"
  private val HomeVisitService = "Выездной уход и мини-группы"

  private val NailsCategory = "Ногти, маникюр и педикюр"
  private val EyesCategory = "Ресницы, брови и permanent make-up"
  private val FaceCategory = "Косметология лица и уход"
  private val HairRemovalCategory = "Удаление волос"

  lazy val spec: BeautySearchSpec = BeautySearchSpec(
    variantDocument = BeautyQVariantSearchDocumentSchema.documentSpec,
    synonyms = dictionary,
    carouselSpec = CarouselSpec(
      variantSize = 10,
      providerSize = 10,
      serviceIntentSize = 10,
      providerGroupField = Fields.masterLocationId,
      serviceIntentGroupField = Fields.serviceId,
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
    querySchema = BeautyQVariantSearchDocumentSchema.querySchema,
  )

  private val facetFields: List[FacetField[VariantSearchDocument]] =
    List(
      FacetField(Fields.serviceName, FacetFieldMode.Terms, limit = 10),
      FacetField(Fields.categoryName, FacetFieldMode.Terms, limit = 10),
      FacetField(
        Fields.priceFrom,
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
        Fields.durationMin,
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
    ) ++ AttributeDefinition.enumDefinitions.flatMap { definition =>
      Fields.enumAttributesByCode.get(definition.code).map(FacetField(_, FacetFieldMode.Terms, limit = definition.values.size))
    } ++ AttributeDefinition.booleanDefinitions.flatMap { definition =>
      Fields.booleanAttributesByCode.get(definition.code).map(FacetField(_, FacetFieldMode.Terms, limit = 2))
    }

  private val dictionary: List[SearchSynonym] =
    List(
      servicePhrase(Set(ManicureService, "маникюр", "манекюр"), ManicureService, Some("nail_service_type" -> "manicure")),
      phrase(Set("обычный маникюр"), List(SearchConstraint.ServiceAny(Set(ManicureService)), enumConstraint("nail_service_type", "manicure"))),
      phrase(Set("дешевый маникюр рядом"), List(SearchConstraint.ServiceAny(Set(ManicureService)), enumConstraint("nail_service_type", "manicure"))),
      servicePhrase(Set(PedicureService, "педикюр", "pedicure", "fußpflege"), PedicureService, Some("nail_service_type" -> "pedicure")),
      servicePhrase(Set(NailExtensionService, "наращивание ногтей", "acrylic nails", "nagelmodellage"), NailExtensionService, Some("nail_service_type" -> "extension")),
      servicePhrase(Set(LashesService, "ресницы", "lashes", "wimpern"), LashesService, None),
      phrase(Set(BrowsService, "брови", "augenbrauen"), List(SearchConstraint.ServiceAny(Set(BrowsService)))),
      servicePhrase(Set(PmuService, "pmu", "permanent makeup", "permanent make up", "permanent make-up", "перманент", "татуаж"), PmuService, None),
      servicePhrase(Set(HairRemovalService, "удаление волос", "hair removal", "depilation", "депиляция"), HairRemovalService, None),
      servicePhrase(Set(FaceService, "косметология лица", "facial", "face treatment"), FaceService, None),
      servicePhrase(Set(HomeVisitService, "выездной уход", "выездной уход для двоих", "beauty treatment at home", "home beauty care", "beauty at home", "small group"), HomeVisitService, None),
      phrase(Set(NailsCategory, "nails"), List(SearchConstraint.CategoryAny(Set(NailsCategory))), softBoosts = List(SearchConstraint.ServiceAny(Set(ManicureService, PedicureService, NailExtensionService)))),
      phrase(Set("ногти"), List(SearchConstraint.CategoryAny(Set(NailsCategory))), softBoosts = List(SearchConstraint.ServiceAny(Set(ManicureService, PedicureService, NailExtensionService)))),
      phrase(Set(EyesCategory), List(SearchConstraint.CategoryAny(Set(EyesCategory)))),
      phrase(Set(FaceCategory), List(SearchConstraint.CategoryAny(Set(FaceCategory)))),
      phrase(Set(HairRemovalCategory), List(SearchConstraint.CategoryAny(Set(HairRemovalCategory)))),
      phrase(Set("салон красоты"), Nil),
      phrase(Set("lashes and brows"), List(SearchConstraint.ServiceAny(Set(LashesService, BrowsService)))),
      phrase(Set("что-то для лица рядом", "что то для лица рядом", "что-то для лица", "что то для лица"), List(SearchConstraint.ServiceAny(Set(FaceService)))),
      phrase(Set("недорогие ногти рядом"), List(SearchConstraint.ServiceAny(Set(ManicureService, PedicureService)))),
      phrase(Set("гель лак", "гель лак", "gel polish"), List(enumConstraint("nail_coating_type", "gel_polish"))),
      phrase(Set("shellac", "шелак"), List(enumConstraint("nail_coating_type", "shellac"))),
      phrase(Set("реснички 2д корр"), List(SearchConstraint.ServiceAny(Set(LashesService)), enumConstraint("lash_volume", "volume2_d"), enumConstraint("lash_service_type", "refill"), boolConstraint("with_correction", true))),
      phrase(Set("с shellac и снятием"), List(enumConstraint("nail_coating_type", "shellac"), boolConstraint("with_removal", true))),
      phrase(Set("shellac entfernen und neu"), List(enumConstraint("nail_coating_type", "shellac"), boolConstraint("with_removal", true))),
      phrase(Set("снять гель с ногтей", "снять гель"), List(SearchConstraint.ServiceAny(Set(NailExtensionService)), enumConstraint("nail_service_type", "removal"), enumConstraint("nail_coating_type", "gel"), boolConstraint("with_removal", true))),
      phrase(Set("gel removal"), List(SearchConstraint.ServiceAny(Set(NailExtensionService)), enumConstraint("nail_service_type", "removal"), enumConstraint("nail_coating_type", "gel"))),
      phrase(
        Set("коррекция гелевых ногтей с дизайном"),
        List(
          SearchConstraint.ServiceAny(Set(NailExtensionService)),
          enumConstraint("nail_service_type", "refill"),
          enumConstraint("nail_coating_type", "gel"),
          boolConstraint("with_correction", true),
          boolConstraint("with_design", true),
        ),
      ),
      phrase(
        Set("коррекция гелевых ногтей"),
        List(
          SearchConstraint.ServiceAny(Set(NailExtensionService)),
          enumConstraint("nail_service_type", "refill"),
          enumConstraint("nail_coating_type", "gel"),
          boolConstraint("with_correction", true),
        ),
      ),
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
      phrase(Set("lash lifting mit färben"), List(SearchConstraint.ServiceAny(Set(LashesService)), enumConstraint("lash_service_type", "lifting"), boolConstraint("with_tinting", true))),
      phrase(Set("lifting"), List(enumConstraint("lash_service_type", "lifting")), requires = List(SearchConstraint.ServiceAny(Set(LashesService)))),
      phrase(Set("снять ресницы"), List(SearchConstraint.ServiceAny(Set(LashesService)), enumConstraint("lash_service_type", "removal"), boolConstraint("with_removal", true))),
      phrase(Set("хна", "henna"), List(enumConstraint("brow_service_type", "henna"), boolConstraint("with_tinting", true)), requires = List(SearchConstraint.ServiceAny(Set(BrowsService)))),
      phrase(Set("lamination", "ламинирование"), List(enumConstraint("brow_service_type", "lamination")), requires = List(SearchConstraint.ServiceAny(Set(BrowsService)))),
      phrase(Set("brow lamination"), List(SearchConstraint.ServiceAny(Set(BrowsService)), enumConstraint("brow_service_type", "lamination"))),
      phrase(Set("augenbrauen färben"), List(SearchConstraint.ServiceAny(Set(BrowsService)), enumConstraint("brow_service_type", "tinting"), boolConstraint("with_tinting", true))),
      phrase(Set("брови ламинирование с окрашиванием"), List(SearchConstraint.ServiceAny(Set(BrowsService)), enumConstraint("brow_service_type", "lamination"), boolConstraint("with_tinting", true))),
      phrase(Set("shape and tint brows"), List(SearchConstraint.ServiceAny(Set(BrowsService)), SearchConstraint.EnumAttr("brow_service_type", Set("shaping", "tinting")), boolConstraint("with_tinting", true))),
      phrase(Set("коррекция бровей"), List(enumConstraint("brow_service_type", "shaping"), SearchConstraint.ServiceAny(Set(BrowsService)))),
      phrase(Set("shaping"), List(enumConstraint("brow_service_type", "shaping")), requires = List(SearchConstraint.ServiceAny(Set(BrowsService)))),
      phrase(Set("färben", "tint", "окрашивание"), List(boolConstraint("with_tinting", true)), requires = List(SearchConstraint.ServiceAny(Set(BrowsService)))),
      phrase(Set("färben", "tint", "окрашивание"), List(boolConstraint("with_tinting", true)), requires = List(SearchConstraint.ServiceAny(Set(LashesService)))),
      phrase(Set("губы", "губ", "lips"), List(enumConstraint("pmu_area", "lips")), requires = List(SearchConstraint.ServiceAny(Set(PmuService)))),
      phrase(Set("eyeliner"), List(enumConstraint("pmu_area", "eyeliner")), requires = List(SearchConstraint.ServiceAny(Set(PmuService)))),
       phrase(Set("correction", "коррекция"), List(boolConstraint("with_correction", true)), requires = List(SearchConstraint.ServiceAny(Set(PmuService)))),
      phrase(Set("powder brows"), List(SearchConstraint.ServiceAny(Set(PmuService)), enumConstraint("pmu_area", "brows"))),
      phrase(Set("brows"), List(enumConstraint("pmu_area", "brows")), requires = List(SearchConstraint.ServiceAny(Set(PmuService)))),
      phrase(Set("aquafacial"), List(SearchConstraint.ServiceAny(Set(FaceService)), enumConstraint("facial_treatment_type", "aquafacial"), enumConstraint("body_area", "face"))),
      phrase(Set("microneedling"), List(SearchConstraint.ServiceAny(Set(FaceService)), enumConstraint("facial_treatment_type", "microneedling"), enumConstraint("body_area", "face"))),
      phrase(Set("bb glow"), List(SearchConstraint.ServiceAny(Set(FaceService)), enumConstraint("facial_treatment_type", "bb_glow"), enumConstraint("body_area", "face"))),
      phrase(Set("чистка лица"), List(SearchConstraint.ServiceAny(Set(FaceService)), enumConstraint("facial_treatment_type", "cleansing"), enumConstraint("body_area", "face"))),
      phrase(Set("классический уход лицо"), List(SearchConstraint.ServiceAny(Set(FaceService)), enumConstraint("facial_treatment_type", "classic"), enumConstraint("body_area", "face"))),
      phrase(Set("увлажнение лица"), List(SearchConstraint.ServiceAny(Set(FaceService)), enumConstraint("facial_treatment_type", "hydration"), enumConstraint("body_area", "face_neck_decollete"))),
      phrase(Set("3 сеанса скидка"), Nil),
      phrase(Set("anti aging", "anti-aging"), List(SearchConstraint.ServiceAny(Set(FaceService)), enumConstraint("facial_treatment_type", "anti_aging"), enumConstraint("body_area", "face_neck_decollete"))),
      phrase(Set("peeling"), List(SearchConstraint.ServiceAny(Set(FaceService)), enumConstraint("facial_treatment_type", "peeling"), enumConstraint("body_area", "face"))),
      SearchSynonym(tokens = Set("face", "gesicht"), constraints = List(enumConstraint("body_area", "face")), requires = List(SearchConstraint.ServiceAny(Set(FaceService))), excludes = List(enumConstraint("body_area", "face_neck_decollete"))),
      phrase(Set("wax", "воск", "воском"), List(SearchConstraint.ServiceAny(Set(HairRemovalService)), enumConstraint("hair_removal_method", "wax"))),
      phrase(Set("sugaring"), List(SearchConstraint.ServiceAny(Set(HairRemovalService)), enumConstraint("hair_removal_method", "sugaring"))),
      phrase(Set("ipl"), List(SearchConstraint.ServiceAny(Set(HairRemovalService)), enumConstraint("hair_removal_method", "laser"))),
      phrase(Set("laser", "лазер"), List(SearchConstraint.ServiceAny(Set(HairRemovalService)), enumConstraint("hair_removal_method", "laser"))),
      phrase(Set("6 сеансов", "6 сеанса"), List(SearchConstraint.ServiceAny(Set(HairRemovalService)), SearchConstraint.IntRange("session_count", Some(6), Some(6)))),
      phrase(Set("threading"), List(SearchConstraint.ServiceAny(Set(HairRemovalService)), enumConstraint("hair_removal_method", "threading"))),
      phrase(Set("рядом"), Nil, requires = List(SearchConstraint.ServiceAny(Set(HairRemovalService)))),
      phrase(Set("beine", "full legs"), List(enumConstraint("body_area", "full_legs")), requires = List(SearchConstraint.ServiceAny(Set(HairRemovalService)))),
      phrase(Set("upper lip", "верхняя губа"), List(enumConstraint("body_area", "upper_lip")), requires = List(SearchConstraint.ServiceAny(Set(HairRemovalService)))),
      phrase(Set("губа"), List(enumConstraint("body_area", "upper_lip")), requires = List(SearchConstraint.ServiceAny(Set(HairRemovalService)))),
      phrase(Set("chin"), List(enumConstraint("body_area", "chin")), requires = List(SearchConstraint.ServiceAny(Set(HairRemovalService)))),
      phrase(Set("подмышки"), List(enumConstraint("body_area", "armpits")), requires = List(SearchConstraint.ServiceAny(Set(HairRemovalService)))),
      phrase(Set("armpits"), List(enumConstraint("body_area", "armpits")), requires = List(SearchConstraint.ServiceAny(Set(HairRemovalService)))),
      phrase(Set("bikini"), List(enumConstraint("body_area", "bikini")), requires = List(SearchConstraint.ServiceAny(Set(HairRemovalService)))),
      phrase(Set("бикини"), List(enumConstraint("body_area", "bikini")), requires = List(SearchConstraint.ServiceAny(Set(HairRemovalService)))),
      phrase(Set("недорого"), Nil, requires = List(SearchConstraint.ServiceAny(Set(HairRemovalService)))),
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
