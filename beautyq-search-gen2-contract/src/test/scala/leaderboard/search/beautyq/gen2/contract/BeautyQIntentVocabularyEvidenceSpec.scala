package leaderboard.search.beautyq.gen2.contract

import org.scalatest.wordspec.AnyWordSpec

/** Current vocabulary/declaration regression golden for `BeautyQIntentVocabulary.rules`.
  *
  * The independent r087..r102 semantic checks below pin the live overlay/correction rules
  * that Gen2 owns directly. The literal trace golden in the final block pins the complete
  * current declaration - every alias, action, relation, mode and noise flag - byte-for-byte,
  * and must fail the moment any of them changes.
  */
final class BeautyQIntentVocabularyEvidenceSpec extends AnyWordSpec {

  "the Gen2 rule inventory" should {
    "declare dense sequential rule IDs in order, with the final sixteen rules explicitly Gen2-owned" in {
      val rules = BeautyQIntentVocabulary.rules
      assert(rules.map(_.id.value) == rules.indices.map(index => f"r${index + 1}%03d").toVector)
      val nearUserRule = rules.find(_.id.value == "r087").getOrElse(fail("expected r087 to exist"))
      assert(nearUserRule.semanticActions == Vector(BeautyIntentAction.NearUser))
      assert(nearUserRule.hardActions.isEmpty)
      assert(!nearUserRule.noise)
      assert(nearUserRule.mode == IntentRuleMode.SemanticOverlay)
      assert(nearUserRule.requires.isEmpty)
      assert(nearUserRule.excludes match {
        case Vector(BeautyIntentAction.Service(code)) => code.value == "hair_removal"
        case _ => false
      })
      val broadRule = rules.find(_.id.value == "r088").getOrElse(fail("expected r088 to exist"))
      assert(broadRule.mode == IntentRuleMode.Independent)
      assert(broadRule.hardActions match {
        case Vector(BeautyIntentAction.ServiceAny(codes)) =>
          codes.map(_.value) == Vector("manicure", "lashes", "brows", "facial")
        case _ => false
      })
      assert(broadRule.semanticActions.isEmpty)
      assert(broadRule.requires.isEmpty)
      assert(broadRule.excludes.isEmpty)
      assert(!broadRule.noise)
      val gelManicureRule = rules.find(_.id.value == "r089").getOrElse(fail("expected r089 to exist"))
      assert(gelManicureRule.aliases == Vector("gel-maniküre", "gel manicure"))
      assert(gelManicureRule.mode == IntentRuleMode.Independent)
      assert(gelManicureRule.hardActions match {
        case Vector(
              BeautyIntentAction.Service(service),
              BeautyIntentAction.EnumAttribute("nail_service_type", "manicure"),
              BeautyIntentAction.EnumAttribute("nail_coating_type", "gel_polish"),
            ) => service.value == "manicure"
        case _ => false
      })
      assert(gelManicureRule.semanticActions.isEmpty)
      assert(gelManicureRule.requires.isEmpty)
      assert(gelManicureRule.excludes.isEmpty)
      assert(!gelManicureRule.noise)
      val lashRemovalRule = rules.find(_.id.value == "r090").getOrElse(fail("expected r090 to exist"))
      assert(lashRemovalRule.aliases == Vector("снять"))
      assert(lashRemovalRule.mode == IntentRuleMode.Contextual)
      assert(lashRemovalRule.hardActions match {
        case Vector(
              BeautyIntentAction.Service(service),
              BeautyIntentAction.EnumAttribute("lash_service_type", "removal"),
              BeautyIntentAction.BooleanAttribute("with_removal", true),
            ) => service.value == "lashes"
        case _ => false
      })
      assert(lashRemovalRule.requires match {
        case Vector(BeautyIntentAction.Service(service)) => service.value == "lashes"
        case _ => false
      })
      assert(lashRemovalRule.semanticActions.isEmpty)
      assert(lashRemovalRule.excludes.isEmpty)
      assert(!lashRemovalRule.noise)
      val regularPolishRule = rules.find(_.id.value == "r091").getOrElse(fail("expected r091 to exist"))
      assert(regularPolishRule.aliases == Vector("regular polish", "ordinary polish", "обычный лак", "обычным лаком", "normaler lack"))
      assert(regularPolishRule.mode == IntentRuleMode.Independent)
      assert(regularPolishRule.hardActions == Vector(BeautyIntentAction.EnumAttribute("nail_coating_type", "regular_polish")))
      assert(regularPolishRule.requires.isEmpty)
      assert(regularPolishRule.excludes.isEmpty)
      val noDesignRule = rules.find(_.id.value == "r092").getOrElse(fail("expected r092 to exist"))
       assert(noDesignRule.aliases == Vector("без дизайна", "without design", "ohne design"))
      assert(noDesignRule.mode == IntentRuleMode.Contextual)
      assert(noDesignRule.hardActions == Vector(BeautyIntentAction.BooleanAttribute("with_design", false)))
      assert(noDesignRule.requires match {
        case Vector(BeautyIntentAction.ServiceAny(codes)) => codes.map(_.value) == Vector("manicure", "pedicure", "nail_modeling")
        case _ => false
      })
      val rotation3Rule = rules.find(_.id.value == "r093").getOrElse(fail("expected r093 to exist"))
      assert(rotation3Rule.mode == IntentRuleMode.Independent)
      val r099Rule = rules.find(_.id.value == "r099").getOrElse(fail("expected r099 to exist"))
      assert(r099Rule.aliases == Vector("3d volume lash", "3d volume lashes"))
      assert(r099Rule.mode == IntentRuleMode.Independent)
      assert(r099Rule.hardActions match {
        case Vector(
              BeautyIntentAction.Service(service),
              BeautyIntentAction.EnumAttribute("lash_service_type", "extension"),
              BeautyIntentAction.EnumAttribute("lash_volume", "volume3_d"),
            ) => service.value == "lashes"
        case _ => false
      })
      assert(r099Rule.semanticActions.isEmpty)
      assert(r099Rule.requires.isEmpty)
      assert(r099Rule.excludes.isEmpty)
      assert(!r099Rule.noise)
      val r100Rule = rules.find(_.id.value == "r100").getOrElse(fail("expected r100 to exist"))
      assert(r100Rule.aliases == Vector("permanent eyeliner"))
      assert(r100Rule.mode == IntentRuleMode.Independent)
      assert(r100Rule.hardActions match {
        case Vector(
              BeautyIntentAction.Service(service),
              BeautyIntentAction.EnumAttribute("pmu_area", "eyeliner"),
            ) => service.value == "pmu"
        case _ => false
      })
      assert(r100Rule.semanticActions.isEmpty)
      assert(r100Rule.requires.isEmpty)
      assert(r100Rule.excludes.isEmpty)
      assert(!r100Rule.noise)
      val r101Rule = rules.find(_.id.value == "r101").getOrElse(fail("expected r101 to exist"))
      assert(r101Rule.aliases == Vector("gel"))
      assert(r101Rule.mode == IntentRuleMode.Contextual)
      assert(r101Rule.hardActions == Vector(BeautyIntentAction.EnumAttribute("nail_coating_type", "gel")))
      assert(r101Rule.requires == Vector(BeautyIntentAction.EnumAttribute("nail_service_type", "removal")))
      assert(r101Rule.semanticActions.isEmpty)
      assert(r101Rule.excludes.isEmpty)
      assert(!r101Rule.noise)
    }

    // Current declaration regression golden, not derived from BeautyIntentRuleTrace or any other
    // production traversal helper: every string below is authored independently and compared
    // byte-for-byte against production output. This must fail the moment any alias, action, relation,
    // mode or noise flag changes for any rule.
    "render the exact literal declaration trace for every current rule" in {
      val expected = Vector(
        "rule id=r001 aliases=[маникюр,манекюр,уход для рук,уход за руками,hand nail care,care for hands,hand care,manicure] mode=Independent hard=[service(manicure),enum(nail_service_type=manicure)] semantic=[] requires=[] excludes=[] noise=false label=absent",
        "rule id=r002 aliases=[обычный маникюр] mode=Independent hard=[service(manicure),enum(nail_service_type=manicure)] semantic=[] requires=[] excludes=[] noise=false label=absent",
        "rule id=r003 aliases=[дешевый маникюр рядом] mode=Independent hard=[service(manicure),enum(nail_service_type=manicure)] semantic=[] requires=[] excludes=[] noise=false label=absent",
        "rule id=r004 aliases=[педикюр,pedicure,pediküre,fußpflege,foot nail care,pflege der fußnägel,fußnägel] mode=Independent hard=[service(pedicure),enum(nail_service_type=pedicure)] semantic=[] requires=[] excludes=[] noise=false label=absent",
        "rule id=r005 aliases=[наращивание и моделирование ногтей,наращивание ногтей,acrylic nails,nagelmodellage,künstliche nägel,artificial nails,nail extension,builder gel,nail modeling,nail modeling extension] mode=Independent hard=[service(nail_modeling),enum(nail_service_type=extension)] semantic=[] requires=[] excludes=[] noise=false label=absent",
        "rule id=r006 aliases=[ресницы,lashes,wimpern,lash] mode=Independent hard=[service(lashes)] semantic=[] requires=[] excludes=[] noise=false label=absent",
        "rule id=r007 aliases=[брови,augenbrauen] mode=Independent hard=[service(brows)] semantic=[] requires=[] excludes=[] noise=false label=absent",
        "rule id=r008 aliases=[pmu,permanent makeup,permanent make up,перманент,татуаж] mode=Independent hard=[service(pmu)] semantic=[] requires=[] excludes=[] noise=false label=absent",
        "rule id=r009 aliases=[удаление волос,hair removal,depilation,депиляция] mode=Independent hard=[service(hair_removal)] semantic=[] requires=[] excludes=[] noise=false label=absent",
        "rule id=r010 aliases=[косметология лица,facial,face treatment] mode=Independent hard=[service(facial)] semantic=[] requires=[] excludes=[] noise=false label=absent",
        "rule id=r011 aliases=[выездной уход и мини группы,выездной уход,выездной уход для двоих,beauty treatment at home,home beauty care,beauty at home,small group] mode=Independent hard=[service(mobile_beauty)] semantic=[] requires=[] excludes=[] noise=false label=absent",
        "rule id=r012 aliases=[ногти маникюр и педикюр,nails] mode=Independent hard=[category(nails)] semantic=[service(manicure),service(pedicure),service(nail_modeling)] requires=[] excludes=[] noise=false label=absent",
        "rule id=r013 aliases=[ногти] mode=Independent hard=[category(nails)] semantic=[service(manicure),service(pedicure),service(nail_modeling)] requires=[] excludes=[] noise=false label=absent",
        "rule id=r014 aliases=[ресницы брови и permanent make up] mode=Independent hard=[category(lashes_brows_pmu)] semantic=[] requires=[] excludes=[] noise=false label=absent",
        "rule id=r015 aliases=[косметология лица и уход] mode=Independent hard=[category(facial_care)] semantic=[] requires=[] excludes=[] noise=false label=absent",
        "rule id=r016 aliases=[удаление волос] mode=Contextual hard=[category(hair_removal)] semantic=[] requires=[service(hair_removal)] excludes=[] noise=false label=absent",
        "rule id=r017 aliases=[салон красоты] mode=Independent hard=[] semantic=[] requires=[] excludes=[] noise=true label=absent",
        "rule id=r018 aliases=[lashes and brows] mode=Independent hard=[service-any(lashes,brows)] semantic=[] requires=[] excludes=[] noise=false label=absent",
        "rule id=r019 aliases=[что то для лица рядом,что то для лица] mode=Independent hard=[service(facial)] semantic=[] requires=[] excludes=[] noise=false label=absent",
        "rule id=r020 aliases=[недорогие ногти рядом] mode=Independent hard=[service-any(manicure,pedicure)] semantic=[] requires=[] excludes=[] noise=false label=absent",
        "rule id=r021 aliases=[гель лак,гель лаком,gel polish,gel farbe,gel lack,гелевым покрытием] mode=Independent hard=[enum(nail_coating_type=gel_polish)] semantic=[] requires=[] excludes=[] noise=false label=absent",
        "rule id=r022 aliases=[shellac,шелак] mode=Independent hard=[enum(nail_coating_type=shellac)] semantic=[] requires=[] excludes=[] noise=false label=absent",
        "rule id=r023 aliases=[реснички 2д корр] mode=Independent hard=[service(lashes),enum(lash_volume=volume2_d),enum(lash_service_type=refill),bool(with_correction=true)] semantic=[] requires=[] excludes=[] noise=false label=absent",
        "rule id=r024 aliases=[с shellac и снятием] mode=Independent hard=[enum(nail_coating_type=shellac),bool(with_removal=true)] semantic=[] requires=[] excludes=[] noise=false label=absent",
        "rule id=r025 aliases=[shellac entfernen und neu] mode=Independent hard=[enum(nail_coating_type=shellac),bool(with_removal=true)] semantic=[] requires=[] excludes=[] noise=false label=absent",
        "rule id=r026 aliases=[снять гель с ногтей,снять гель] mode=Independent hard=[service(nail_modeling),enum(nail_service_type=removal),enum(nail_coating_type=gel),bool(with_removal=true)] semantic=[] requires=[] excludes=[] noise=false label=absent",
        "rule id=r027 aliases=[gel removal] mode=Independent hard=[service(nail_modeling),enum(nail_service_type=removal),enum(nail_coating_type=gel)] semantic=[] requires=[] excludes=[] noise=false label=absent",
        "rule id=r028 aliases=[коррекция гелевых ногтей с дизайном] mode=Independent hard=[service(nail_modeling),enum(nail_service_type=refill),enum(nail_coating_type=gel),bool(with_correction=true),bool(with_design=true)] semantic=[] requires=[] excludes=[] noise=false label=absent",
        "rule id=r029 aliases=[коррекция гелевых ногтей] mode=Independent hard=[service(nail_modeling),enum(nail_service_type=refill),enum(nail_coating_type=gel),bool(with_correction=true)] semantic=[] requires=[] excludes=[] noise=false label=absent",
        "rule id=r030 aliases=[не татуаж] mode=Independent hard=[] semantic=[] requires=[] excludes=[] noise=true label=absent",
        "rule id=r031 aliases=[без лака,без покрытия,no coating,без цветного покрытия] mode=Independent hard=[enum(nail_coating_type=no_coating)] semantic=[] requires=[] excludes=[] noise=false label=absent",
        "rule id=r032 aliases=[гель,gel] mode=Contextual hard=[enum(nail_coating_type=gel)] semantic=[] requires=[enum(nail_service_type=extension)] excludes=[] noise=false label=absent",
        "rule id=r033 aliases=[acrylic] mode=Contextual hard=[enum(nail_coating_type=acrylic)] semantic=[] requires=[enum(nail_service_type=extension)] excludes=[] noise=false label=absent",
        "rule id=r034 aliases=[классика,classic,1d,1 д,classic1_d] mode=Contextual hard=[enum(lash_volume=classic1_d)] semantic=[] requires=[service(lashes)] excludes=[] noise=false label=absent",
        "rule id=r035 aliases=[2д,2d,volume2_d] mode=Contextual hard=[enum(lash_volume=volume2_d)] semantic=[] requires=[service(lashes)] excludes=[] noise=false label=absent",
        "rule id=r036 aliases=[3д,3d,volume3_d] mode=Contextual hard=[enum(lash_volume=volume3_d)] semantic=[] requires=[service(lashes)] excludes=[] noise=false label=absent",
        "rule id=r037 aliases=[mega volume] mode=Contextual hard=[enum(lash_volume=mega_volume)] semantic=[] requires=[service(lashes)] excludes=[] noise=false label=absent",
        "rule id=r038 aliases=[extension,extensions,lash extension,lash extensions,наращивание ресниц,full set] mode=Contextual hard=[enum(lash_service_type=extension)] semantic=[] requires=[service(lashes)] excludes=[] noise=false label=absent",
        "rule id=r039 aliases=[коррекция ресниц 2д,коррекция ресниц 2d] mode=Independent hard=[enum(lash_volume=volume2_d),enum(lash_service_type=refill),bool(with_correction=true),service(lashes)] semantic=[] requires=[] excludes=[] noise=false label=absent",
        "rule id=r040 aliases=[коррекция ресниц] mode=Independent hard=[enum(lash_service_type=refill),bool(with_correction=true),service(lashes)] semantic=[] requires=[] excludes=[] noise=false label=absent",
        "rule id=r041 aliases=[lash lifting,lash lift] mode=Independent hard=[enum(lash_service_type=lifting),service(lashes)] semantic=[] requires=[] excludes=[] noise=false label=absent",
        "rule id=r042 aliases=[lash lifting mit färben] mode=Independent hard=[enum(lash_service_type=lifting),bool(with_tinting=true),service(lashes)] semantic=[] requires=[] excludes=[] noise=false label=absent",
        "rule id=r043 aliases=[lifting] mode=Contextual hard=[enum(lash_service_type=lifting)] semantic=[] requires=[service(lashes)] excludes=[] noise=false label=absent",
        "rule id=r044 aliases=[снять ресницы] mode=Independent hard=[service(lashes),enum(lash_service_type=removal),bool(with_removal=true)] semantic=[] requires=[] excludes=[] noise=false label=absent",
        "rule id=r045 aliases=[хна,henna] mode=Contextual hard=[enum(brow_service_type=henna),bool(with_tinting=true)] semantic=[] requires=[service(brows)] excludes=[] noise=false label=absent",
        "rule id=r046 aliases=[lamination,ламинирование] mode=Contextual hard=[enum(brow_service_type=lamination)] semantic=[] requires=[service(brows)] excludes=[] noise=false label=absent",
        "rule id=r047 aliases=[brow lamination] mode=Independent hard=[enum(brow_service_type=lamination),service(brows)] semantic=[] requires=[] excludes=[] noise=false label=absent",
        "rule id=r048 aliases=[augenbrauen färben,brow tint] mode=Independent hard=[service(brows),enum(brow_service_type=tinting),bool(with_tinting=true)] semantic=[] requires=[] excludes=[] noise=false label=absent",
        "rule id=r049 aliases=[брови ламинирование с окрашиванием] mode=Independent hard=[service(brows),enum(brow_service_type=lamination),bool(with_tinting=true)] semantic=[] requires=[] excludes=[] noise=false label=absent",
        "rule id=r050 aliases=[shape and tint brows] mode=Independent hard=[service(brows),enum-any(brow_service_type=shaping,tinting),bool(with_tinting=true)] semantic=[] requires=[] excludes=[] noise=false label=absent",
        "rule id=r051 aliases=[коррекция бровей] mode=Independent hard=[enum(brow_service_type=shaping),service(brows)] semantic=[] requires=[] excludes=[] noise=false label=absent",
        "rule id=r052 aliases=[shaping] mode=Contextual hard=[enum(brow_service_type=shaping)] semantic=[] requires=[service(brows)] excludes=[] noise=false label=absent",
        "rule id=r053 aliases=[färben,tint,окрашивание,tinting] mode=Contextual hard=[bool(with_tinting=true)] semantic=[] requires=[service(brows)] excludes=[] noise=false label=absent",
        "rule id=r054 aliases=[färben,tint,окрашивание,tinting] mode=Contextual hard=[bool(with_tinting=true)] semantic=[] requires=[service(lashes)] excludes=[] noise=false label=absent",
        "rule id=r055 aliases=[губы,губ,lips] mode=Contextual hard=[enum(pmu_area=lips)] semantic=[] requires=[service(pmu)] excludes=[] noise=false label=absent",
        "rule id=r056 aliases=[eyeliner] mode=Contextual hard=[enum(pmu_area=eyeliner)] semantic=[] requires=[service(pmu)] excludes=[] noise=false label=absent",
        "rule id=r057 aliases=[correction,коррекция] mode=Contextual hard=[bool(with_correction=true)] semantic=[] requires=[service(pmu)] excludes=[] noise=false label=absent",
        "rule id=r058 aliases=[powder brows,пудровый перманент бровей,брови с мягким пудровым эффектом надолго,перманентный макияж бровей] mode=Independent hard=[service(pmu),enum(pmu_area=brows)] semantic=[] requires=[] excludes=[] noise=false label=absent",
        "rule id=r059 aliases=[brows,eyebrows,бровей] mode=Contextual hard=[enum(pmu_area=brows)] semantic=[] requires=[service(pmu)] excludes=[] noise=false label=absent",
        "rule id=r060 aliases=[aquafacial,hydrafacial] mode=Independent hard=[service(facial),enum(facial_treatment_type=aquafacial),enum(body_area=face)] semantic=[] requires=[] excludes=[] noise=false label=absent",
        "rule id=r061 aliases=[microneedling] mode=Independent hard=[service(facial),enum(facial_treatment_type=microneedling),enum(body_area=face)] semantic=[] requires=[] excludes=[] noise=false label=absent",
        "rule id=r062 aliases=[bb glow,хочу чтобы тон лица выглядел ровнее без ежедневного макияжа] mode=Independent hard=[service(facial),enum(facial_treatment_type=bb_glow),enum(body_area=face)] semantic=[] requires=[] excludes=[] noise=false label=absent",
        "rule id=r063 aliases=[чистка лица,facial cleansing,gesichtsreinigung] mode=Independent hard=[service(facial),enum(facial_treatment_type=cleansing),enum(body_area=face)] semantic=[] requires=[] excludes=[] noise=false label=absent",
        "rule id=r064 aliases=[классический уход лицо] mode=Independent hard=[service(facial),enum(facial_treatment_type=classic),enum(body_area=face)] semantic=[] requires=[] excludes=[] noise=false label=absent",
        "rule id=r065 aliases=[увлажнение лица] mode=Independent hard=[service(facial),enum(facial_treatment_type=hydration),enum(body_area=face_neck_decollete)] semantic=[] requires=[] excludes=[] noise=false label=absent",
        "rule id=r066 aliases=[3 сеанса скидка] mode=Independent hard=[] semantic=[] requires=[] excludes=[] noise=true label=absent",
        "rule id=r067 aliases=[anti aging] mode=Independent hard=[service(facial),enum(facial_treatment_type=anti_aging),enum(body_area=face_neck_decollete)] semantic=[] requires=[] excludes=[] noise=false label=absent",
        "rule id=r068 aliases=[peeling] mode=Independent hard=[service(facial),enum(facial_treatment_type=peeling),enum(body_area=face)] semantic=[] requires=[] excludes=[] noise=false label=absent",
        "rule id=r069 aliases=[face,gesicht] mode=Contextual hard=[enum(body_area=face)] semantic=[] requires=[service(facial)] excludes=[enum(body_area=face_neck_decollete)] noise=false label=absent",
        "rule id=r070 aliases=[wax,waxing,воск,воском] mode=Independent hard=[service(hair_removal),enum(hair_removal_method=wax)] semantic=[] requires=[] excludes=[] noise=false label=absent",
        "rule id=r071 aliases=[sugaring] mode=Independent hard=[service(hair_removal),enum(hair_removal_method=sugaring)] semantic=[] requires=[] excludes=[] noise=false label=absent",
        "rule id=r072 aliases=[ipl] mode=Independent hard=[service(hair_removal),enum(hair_removal_method=laser)] semantic=[] requires=[] excludes=[] noise=false label=absent",
        "rule id=r073 aliases=[laser,лазер] mode=Independent hard=[service(hair_removal),enum(hair_removal_method=laser)] semantic=[] requires=[] excludes=[] noise=false label=absent",
        "rule id=r074 aliases=[6 сеансов,6 сеанса] mode=Independent hard=[service(hair_removal),int(session_count=6)] semantic=[] requires=[] excludes=[] noise=false label=absent",
        "rule id=r075 aliases=[threading] mode=Independent hard=[service(hair_removal),enum(hair_removal_method=threading)] semantic=[] requires=[] excludes=[] noise=false label=absent",
        "rule id=r076 aliases=[рядом] mode=Contextual hard=[] semantic=[] requires=[service(hair_removal)] excludes=[] noise=true label=absent",
        "rule id=r077 aliases=[beine,full legs] mode=Contextual hard=[enum(body_area=full_legs)] semantic=[] requires=[service(hair_removal)] excludes=[] noise=false label=absent",
        "rule id=r078 aliases=[upper lip,верхняя губа,lip,oberlippe] mode=Contextual hard=[enum(body_area=upper_lip)] semantic=[] requires=[service(hair_removal)] excludes=[] noise=false label=absent",
        "rule id=r079 aliases=[губа] mode=Contextual hard=[enum(body_area=upper_lip)] semantic=[] requires=[service(hair_removal)] excludes=[] noise=false label=absent",
        "rule id=r080 aliases=[chin] mode=Contextual hard=[enum(body_area=chin)] semantic=[] requires=[service(hair_removal)] excludes=[] noise=false label=absent",
        "rule id=r081 aliases=[подмышки] mode=Contextual hard=[enum(body_area=armpits)] semantic=[] requires=[service(hair_removal)] excludes=[] noise=false label=absent",
        "rule id=r082 aliases=[armpits,underarms] mode=Contextual hard=[enum(body_area=armpits)] semantic=[] requires=[service(hair_removal)] excludes=[] noise=false label=absent",
        "rule id=r083 aliases=[bikini] mode=Contextual hard=[enum(body_area=bikini)] semantic=[] requires=[service(hair_removal)] excludes=[] noise=false label=absent",
        "rule id=r084 aliases=[бикини] mode=Contextual hard=[enum(body_area=bikini)] semantic=[] requires=[service(hair_removal)] excludes=[] noise=false label=absent",
        "rule id=r085 aliases=[недорого] mode=Contextual hard=[] semantic=[] requires=[service(hair_removal)] excludes=[] noise=true label=absent",
        "rule id=r086 aliases=[lower legs] mode=Contextual hard=[enum(body_area=lower_legs)] semantic=[] requires=[service(hair_removal)] excludes=[] noise=false label=absent",
        "rule id=r087 aliases=[рядом,near me,nearby] mode=SemanticOverlay hard=[] semantic=[near-user] requires=[] excludes=[service(hair_removal)] noise=false label=absent",
        "rule id=r088 aliases=[хочу привести себя в порядок,привести себя в порядок] mode=Independent hard=[service-any(manicure,lashes,brows,facial)] semantic=[] requires=[] excludes=[] noise=false label=absent",
        "rule id=r089 aliases=[gel maniküre,gel manicure] mode=Independent hard=[service(manicure),enum(nail_service_type=manicure),enum(nail_coating_type=gel_polish)] semantic=[] requires=[] excludes=[] noise=false label=absent",
        "rule id=r090 aliases=[снять] mode=Contextual hard=[service(lashes),enum(lash_service_type=removal),bool(with_removal=true)] semantic=[] requires=[service(lashes)] excludes=[] noise=false label=absent",
        "rule id=r091 aliases=[regular polish,ordinary polish,обычный лак,обычным лаком,normaler lack] mode=Independent hard=[enum(nail_coating_type=regular_polish)] semantic=[] requires=[] excludes=[] noise=false label=absent",
        "rule id=r092 aliases=[без дизайна,without design,ohne design] mode=Contextual hard=[bool(with_design=false)] semantic=[] requires=[service-any(manicure,pedicure,nail_modeling)] excludes=[] noise=false label=absent",
        "rule id=r093 aliases=[снятие наращенных ногтей,nail extension removal,nail modeling removal,remove nail modeling] mode=Independent hard=[service(nail_modeling),enum(nail_service_type=removal),bool(with_removal=true)] semantic=[] requires=[] excludes=[] noise=false label=absent",
        "rule id=r094 aliases=[henna brows,henna brow] mode=Independent hard=[service(brows),enum(brow_service_type=henna),bool(with_tinting=true)] semantic=[] requires=[] excludes=[] noise=false label=absent",
        "rule id=r095 aliases=[brow shaping] mode=Independent hard=[service(brows),enum(brow_service_type=shaping)] semantic=[] requires=[] excludes=[] noise=false label=absent",
        "rule id=r096 aliases=[nail refill] mode=Independent hard=[service(nail_modeling),enum(nail_service_type=refill),bool(with_correction=true)] semantic=[] requires=[] excludes=[] noise=false label=absent",
        "rule id=r097 aliases=[gel] mode=Contextual hard=[enum(nail_coating_type=gel)] semantic=[] requires=[enum(nail_service_type=refill)] excludes=[] noise=false label=absent",
        "rule id=r098 aliases=[with design] mode=Contextual hard=[bool(with_design=true)] semantic=[] requires=[service-any(manicure,pedicure,nail_modeling)] excludes=[] noise=false label=absent",
        "rule id=r099 aliases=[3d volume lash,3d volume lashes] mode=Independent hard=[service(lashes),enum(lash_service_type=extension),enum(lash_volume=volume3_d)] semantic=[] requires=[] excludes=[] noise=false label=absent",
        "rule id=r100 aliases=[permanent eyeliner] mode=Independent hard=[service(pmu),enum(pmu_area=eyeliner)] semantic=[] requires=[] excludes=[] noise=false label=absent",
        "rule id=r101 aliases=[gel] mode=Contextual hard=[enum(nail_coating_type=gel)] semantic=[] requires=[enum(nail_service_type=removal)] excludes=[] noise=false label=absent",
        "rule id=r102 aliases=[remove gel nail modeling] mode=Independent hard=[service(nail_modeling),enum(nail_service_type=removal),bool(with_removal=true),enum(nail_coating_type=gel)] semantic=[] requires=[] excludes=[] noise=false label=absent",
      )
      val actual = BeautyQIntentVocabulary.rules.map(BeautyIntentRuleTrace.render)
      assert(actual == expected)
    }
  }
}
