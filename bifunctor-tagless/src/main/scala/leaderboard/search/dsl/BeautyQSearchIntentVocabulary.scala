package leaderboard.search.dsl

/** BeautyQ-specific intent vocabulary.
  *
  * These rules are structured intent aliases and residual-noise phrases, not lexical Elasticsearch analyzer
  * synonyms. A [[SearchIntentRule.StructuredAlias]] maps a phrase to hard service/category/attribute
  * constraints, soft boosts, and contextual `requires`/`excludes`. A [[SearchIntentRule.QueryNoisePhrase]]
  * carries no constraints/boosts and only consumes residual query text (optionally gated by `requires`).
  *
  * Existing misspellings/translations inside service/category/attribute aliases are kept as structured alias
  * tokens because they map to filters, not just lexical recall.
  */
object BeautyQSearchIntentVocabulary {

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

  val vocabulary: SearchIntentVocabulary[SearchConstraint] =
    SearchIntentVocabulary(
      rules = List(
        servicePhrase(Set(ManicureService, "маникюр", "манекюр"), ManicureService, Some("nail_service_type" -> "manicure")),
        alias(Set("обычный маникюр"), List(SearchConstraint.ServiceAny(Set(ManicureService)), enumConstraint("nail_service_type", "manicure"))),
        alias(Set("дешевый маникюр рядом"), List(SearchConstraint.ServiceAny(Set(ManicureService)), enumConstraint("nail_service_type", "manicure"))),
        servicePhrase(Set(PedicureService, "педикюр", "pedicure", "fußpflege"), PedicureService, Some("nail_service_type" -> "pedicure")),
        servicePhrase(Set(NailExtensionService, "наращивание ногтей", "acrylic nails", "nagelmodellage"), NailExtensionService, Some("nail_service_type" -> "extension")),
        servicePhrase(Set(LashesService, "ресницы", "lashes", "wimpern"), LashesService, None),
        alias(Set(BrowsService, "брови", "augenbrauen"), List(SearchConstraint.ServiceAny(Set(BrowsService)))),
        servicePhrase(Set(PmuService, "pmu", "permanent makeup", "permanent make up", "permanent make-up", "перманент", "татуаж"), PmuService, None),
        servicePhrase(Set(HairRemovalService, "удаление волос", "hair removal", "depilation", "депиляция"), HairRemovalService, None),
        servicePhrase(Set(FaceService, "косметология лица", "facial", "face treatment"), FaceService, None),
        servicePhrase(Set(HomeVisitService, "выездной уход", "выездной уход для двоих", "beauty treatment at home", "home beauty care", "beauty at home", "small group"), HomeVisitService, None),
        alias(Set(NailsCategory, "nails"), List(SearchConstraint.CategoryAny(Set(NailsCategory))), softBoosts = List(SearchConstraint.ServiceAny(Set(ManicureService, PedicureService, NailExtensionService)))),
        alias(Set("ногти"), List(SearchConstraint.CategoryAny(Set(NailsCategory))), softBoosts = List(SearchConstraint.ServiceAny(Set(ManicureService, PedicureService, NailExtensionService)))),
        alias(Set(EyesCategory), List(SearchConstraint.CategoryAny(Set(EyesCategory)))),
        alias(Set(FaceCategory), List(SearchConstraint.CategoryAny(Set(FaceCategory)))),
        alias(Set(HairRemovalCategory), List(SearchConstraint.CategoryAny(Set(HairRemovalCategory)))),
        noise(Set("салон красоты")),
        alias(Set("lashes and brows"), List(SearchConstraint.ServiceAny(Set(LashesService, BrowsService)))),
        alias(Set("что-то для лица рядом", "что то для лица рядом", "что-то для лица", "что то для лица"), List(SearchConstraint.ServiceAny(Set(FaceService)))),
        alias(Set("недорогие ногти рядом"), List(SearchConstraint.ServiceAny(Set(ManicureService, PedicureService)))),
        alias(Set("гель лак", "гель лак", "gel polish"), List(enumConstraint("nail_coating_type", "gel_polish"))),
        alias(Set("shellac", "шелак"), List(enumConstraint("nail_coating_type", "shellac"))),
        alias(Set("реснички 2д корр"), List(SearchConstraint.ServiceAny(Set(LashesService)), enumConstraint("lash_volume", "volume2_d"), enumConstraint("lash_service_type", "refill"), boolConstraint("with_correction", true))),
        alias(Set("с shellac и снятием"), List(enumConstraint("nail_coating_type", "shellac"), boolConstraint("with_removal", true))),
        alias(Set("shellac entfernen und neu"), List(enumConstraint("nail_coating_type", "shellac"), boolConstraint("with_removal", true))),
        alias(Set("снять гель с ногтей", "снять гель"), List(SearchConstraint.ServiceAny(Set(NailExtensionService)), enumConstraint("nail_service_type", "removal"), enumConstraint("nail_coating_type", "gel"), boolConstraint("with_removal", true))),
        alias(Set("gel removal"), List(SearchConstraint.ServiceAny(Set(NailExtensionService)), enumConstraint("nail_service_type", "removal"), enumConstraint("nail_coating_type", "gel"))),
        alias(
          Set("коррекция гелевых ногтей с дизайном"),
          List(
            SearchConstraint.ServiceAny(Set(NailExtensionService)),
            enumConstraint("nail_service_type", "refill"),
            enumConstraint("nail_coating_type", "gel"),
            boolConstraint("with_correction", true),
            boolConstraint("with_design", true),
          ),
        ),
        alias(
          Set("коррекция гелевых ногтей"),
          List(
            SearchConstraint.ServiceAny(Set(NailExtensionService)),
            enumConstraint("nail_service_type", "refill"),
            enumConstraint("nail_coating_type", "gel"),
            boolConstraint("with_correction", true),
          ),
        ),
        noise(Set("не татуаж")),
        alias(Set("без лака", "без покрытия", "no coating"), List(enumConstraint("nail_coating_type", "no_coating"))),
        alias(Set("гель", "gel"), List(enumConstraint("nail_coating_type", "gel")), requires = List(enumConstraint("nail_service_type", "extension"))),
        alias(Set("acrylic"), List(enumConstraint("nail_coating_type", "acrylic")), requires = List(enumConstraint("nail_service_type", "extension"))),
        alias(Set("классика", "classic", "1d", "1 д", "classic1_d"), List(enumConstraint("lash_volume", "classic1_d")), requires = List(SearchConstraint.ServiceAny(Set(LashesService)))),
        alias(Set("2д", "2d", "volume2_d"), List(enumConstraint("lash_volume", "volume2_d")), requires = List(SearchConstraint.ServiceAny(Set(LashesService)))),
        alias(Set("3д", "3d", "volume3_d"), List(enumConstraint("lash_volume", "volume3_d")), requires = List(SearchConstraint.ServiceAny(Set(LashesService)))),
        alias(Set("mega volume"), List(enumConstraint("lash_volume", "mega_volume")), requires = List(SearchConstraint.ServiceAny(Set(LashesService)))),
        alias(
          Set("extension", "extensions", "lash extension", "lash extensions", "наращивание ресниц", "full set"),
          List(enumConstraint("lash_service_type", "extension")),
          requires = List(SearchConstraint.ServiceAny(Set(LashesService))),
        ),
        alias(Set("коррекция ресниц 2д", "коррекция ресниц 2d"), List(enumConstraint("lash_volume", "volume2_d"), enumConstraint("lash_service_type", "refill"), boolConstraint("with_correction", true), SearchConstraint.ServiceAny(Set(LashesService)))),
        alias(Set("коррекция ресниц"), List(enumConstraint("lash_service_type", "refill"), boolConstraint("with_correction", true), SearchConstraint.ServiceAny(Set(LashesService)))),
        alias(Set("lash lifting"), List(enumConstraint("lash_service_type", "lifting"), SearchConstraint.ServiceAny(Set(LashesService)))),
        alias(Set("lash lifting mit färben"), List(SearchConstraint.ServiceAny(Set(LashesService)), enumConstraint("lash_service_type", "lifting"), boolConstraint("with_tinting", true))),
        alias(Set("lifting"), List(enumConstraint("lash_service_type", "lifting")), requires = List(SearchConstraint.ServiceAny(Set(LashesService)))),
        alias(Set("снять ресницы"), List(SearchConstraint.ServiceAny(Set(LashesService)), enumConstraint("lash_service_type", "removal"), boolConstraint("with_removal", true))),
        alias(Set("хна", "henna"), List(enumConstraint("brow_service_type", "henna"), boolConstraint("with_tinting", true)), requires = List(SearchConstraint.ServiceAny(Set(BrowsService)))),
        alias(Set("lamination", "ламинирование"), List(enumConstraint("brow_service_type", "lamination")), requires = List(SearchConstraint.ServiceAny(Set(BrowsService)))),
        alias(Set("brow lamination"), List(SearchConstraint.ServiceAny(Set(BrowsService)), enumConstraint("brow_service_type", "lamination"))),
        alias(Set("augenbrauen färben"), List(SearchConstraint.ServiceAny(Set(BrowsService)), enumConstraint("brow_service_type", "tinting"), boolConstraint("with_tinting", true))),
        alias(Set("брови ламинирование с окрашиванием"), List(SearchConstraint.ServiceAny(Set(BrowsService)), enumConstraint("brow_service_type", "lamination"), boolConstraint("with_tinting", true))),
        alias(Set("shape and tint brows"), List(SearchConstraint.ServiceAny(Set(BrowsService)), SearchConstraint.EnumAttr("brow_service_type", Set("shaping", "tinting")), boolConstraint("with_tinting", true))),
        alias(Set("коррекция бровей"), List(enumConstraint("brow_service_type", "shaping"), SearchConstraint.ServiceAny(Set(BrowsService)))),
        alias(Set("shaping"), List(enumConstraint("brow_service_type", "shaping")), requires = List(SearchConstraint.ServiceAny(Set(BrowsService)))),
        alias(Set("färben", "tint", "окрашивание"), List(boolConstraint("with_tinting", true)), requires = List(SearchConstraint.ServiceAny(Set(BrowsService)))),
        alias(Set("färben", "tint", "окрашивание"), List(boolConstraint("with_tinting", true)), requires = List(SearchConstraint.ServiceAny(Set(LashesService)))),
        alias(Set("губы", "губ", "lips"), List(enumConstraint("pmu_area", "lips")), requires = List(SearchConstraint.ServiceAny(Set(PmuService)))),
        alias(Set("eyeliner"), List(enumConstraint("pmu_area", "eyeliner")), requires = List(SearchConstraint.ServiceAny(Set(PmuService)))),
        alias(Set("correction", "коррекция"), List(boolConstraint("with_correction", true)), requires = List(SearchConstraint.ServiceAny(Set(PmuService)))),
        alias(Set("powder brows"), List(SearchConstraint.ServiceAny(Set(PmuService)), enumConstraint("pmu_area", "brows"))),
        alias(Set("brows"), List(enumConstraint("pmu_area", "brows")), requires = List(SearchConstraint.ServiceAny(Set(PmuService)))),
        alias(Set("aquafacial"), List(SearchConstraint.ServiceAny(Set(FaceService)), enumConstraint("facial_treatment_type", "aquafacial"), enumConstraint("body_area", "face"))),
        alias(Set("microneedling"), List(SearchConstraint.ServiceAny(Set(FaceService)), enumConstraint("facial_treatment_type", "microneedling"), enumConstraint("body_area", "face"))),
        alias(Set("bb glow"), List(SearchConstraint.ServiceAny(Set(FaceService)), enumConstraint("facial_treatment_type", "bb_glow"), enumConstraint("body_area", "face"))),
        alias(Set("чистка лица"), List(SearchConstraint.ServiceAny(Set(FaceService)), enumConstraint("facial_treatment_type", "cleansing"), enumConstraint("body_area", "face"))),
        alias(Set("классический уход лицо"), List(SearchConstraint.ServiceAny(Set(FaceService)), enumConstraint("facial_treatment_type", "classic"), enumConstraint("body_area", "face"))),
        alias(Set("увлажнение лица"), List(SearchConstraint.ServiceAny(Set(FaceService)), enumConstraint("facial_treatment_type", "hydration"), enumConstraint("body_area", "face_neck_decollete"))),
        noise(Set("3 сеанса скидка")),
        alias(Set("anti aging", "anti-aging"), List(SearchConstraint.ServiceAny(Set(FaceService)), enumConstraint("facial_treatment_type", "anti_aging"), enumConstraint("body_area", "face_neck_decollete"))),
        alias(Set("peeling"), List(SearchConstraint.ServiceAny(Set(FaceService)), enumConstraint("facial_treatment_type", "peeling"), enumConstraint("body_area", "face"))),
        SearchIntentRule.StructuredAlias(tokens = Set("face", "gesicht"), constraints = List(enumConstraint("body_area", "face")), requires = List(SearchConstraint.ServiceAny(Set(FaceService))), excludes = List(enumConstraint("body_area", "face_neck_decollete"))),
        alias(Set("wax", "воск", "воском"), List(SearchConstraint.ServiceAny(Set(HairRemovalService)), enumConstraint("hair_removal_method", "wax"))),
        alias(Set("sugaring"), List(SearchConstraint.ServiceAny(Set(HairRemovalService)), enumConstraint("hair_removal_method", "sugaring"))),
        alias(Set("ipl"), List(SearchConstraint.ServiceAny(Set(HairRemovalService)), enumConstraint("hair_removal_method", "laser"))),
        alias(Set("laser", "лазер"), List(SearchConstraint.ServiceAny(Set(HairRemovalService)), enumConstraint("hair_removal_method", "laser"))),
        alias(Set("6 сеансов", "6 сеанса"), List(SearchConstraint.ServiceAny(Set(HairRemovalService)), SearchConstraint.IntRange("session_count", Some(6), Some(6)))),
        alias(Set("threading"), List(SearchConstraint.ServiceAny(Set(HairRemovalService)), enumConstraint("hair_removal_method", "threading"))),
        noise(Set("рядом"), requires = List(SearchConstraint.ServiceAny(Set(HairRemovalService)))),
        alias(Set("beine", "full legs"), List(enumConstraint("body_area", "full_legs")), requires = List(SearchConstraint.ServiceAny(Set(HairRemovalService)))),
        alias(Set("upper lip", "верхняя губа"), List(enumConstraint("body_area", "upper_lip")), requires = List(SearchConstraint.ServiceAny(Set(HairRemovalService)))),
        alias(Set("губа"), List(enumConstraint("body_area", "upper_lip")), requires = List(SearchConstraint.ServiceAny(Set(HairRemovalService)))),
        alias(Set("chin"), List(enumConstraint("body_area", "chin")), requires = List(SearchConstraint.ServiceAny(Set(HairRemovalService)))),
        alias(Set("подмышки"), List(enumConstraint("body_area", "armpits")), requires = List(SearchConstraint.ServiceAny(Set(HairRemovalService)))),
        alias(Set("armpits"), List(enumConstraint("body_area", "armpits")), requires = List(SearchConstraint.ServiceAny(Set(HairRemovalService)))),
        alias(Set("bikini"), List(enumConstraint("body_area", "bikini")), requires = List(SearchConstraint.ServiceAny(Set(HairRemovalService)))),
        alias(Set("бикини"), List(enumConstraint("body_area", "bikini")), requires = List(SearchConstraint.ServiceAny(Set(HairRemovalService)))),
        noise(Set("недорого"), requires = List(SearchConstraint.ServiceAny(Set(HairRemovalService)))),
        alias(Set("lower legs"), List(enumConstraint("body_area", "lower_legs")), requires = List(SearchConstraint.ServiceAny(Set(HairRemovalService)))),
      )
    )

  private def servicePhrase(
    tokens: Set[String],
    serviceName: String,
    enumAttribute: Option[(String, String)],
  ): SearchIntentRule.StructuredAlias[SearchConstraint] =
    alias(
      tokens,
      List(SearchConstraint.ServiceAny(Set(serviceName))) ++ enumAttribute.toList.map { case (code, value) => enumConstraint(code, value) },
    )

  private def alias(
    tokens: Set[String],
    constraints: List[SearchConstraint],
    softBoosts: List[SearchConstraint] = Nil,
    requires: List[SearchConstraint] = Nil,
  ): SearchIntentRule.StructuredAlias[SearchConstraint] =
    SearchIntentRule.StructuredAlias(
      tokens = tokens,
      constraints = constraints,
      softBoosts = softBoosts,
      requires = requires,
    )

  private def noise(
    tokens: Set[String],
    requires: List[SearchConstraint] = Nil,
  ): SearchIntentRule.QueryNoisePhrase[SearchConstraint] =
    SearchIntentRule.QueryNoisePhrase(
      tokens = tokens,
      requires = requires,
    )

  private def enumConstraint(attributeCode: String, value: String): SearchConstraint =
    SearchConstraint.EnumAttr(attributeCode, Set(value))

  private def boolConstraint(attributeCode: String, value: Boolean): SearchConstraint =
    SearchConstraint.BoolAttr(attributeCode, value)
}
