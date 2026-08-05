package leaderboard.search.beautyq.gen2.contract

import org.scalatest.wordspec.AnyWordSpec

/** Source-evidence ledger against Gen1's actual declaration source
  * (beautyq-search-contract/src/main/scala/leaderboard/search/dsl/BeautyQSearchIntentVocabulary.scala,
  * 86 rules, `rules = List(...)`, lines 33-139) and Gen1's parser normalization
  * (beautyq-search-wiring/src/main/scala/leaderboard/search/parser/BeautySearchIntentParser.scala).
  *
  * This replaces an earlier, tautological version of this spec (derive a disposition string from the
  * current Gen2 rule, then assert it is non-empty - true for any rule, proving nothing). Both tables
  * below are literal, hand-verified against the Gen1 source directly, never computed by traversing
  * `BeautyQIntentVocabulary.rules` or any other production helper - only the final assertions call
  * production code, to compare it against these independently authored expectations.
  *
  * Gen1's 86 declarations map to Gen2 r001..r086 index-for-index (Gen2 did not reorder them); r087
  * is the Gen2 NearUser overlay; r088 is the measured Gen2 broad self-care correction; r089 is the
  * first disclosed multilingual exact-intent correction; r090 is the contextual lash-removal action;
  * r091 and r092 own regular-polish and explicit no-design typed semantics.
  */
final class BeautyQIntentVocabularyEvidenceSpec extends AnyWordSpec {

  private final case class LedgerEntry(
    sourceIndex: Int,
    gen2RuleId: String,
    disposition: String,
    anchorAliases: Vector[String],
    hardActionTags: Vector[String],
    semanticActionTags: Vector[String],
    requiresTags: Vector[String],
    excludesTags: Vector[String],
  )

  // Four accepted dispositions. "intentionally-changed" always carries its own reason after ':' - a
  // deliberate Gen1->Gen2 representation change that is behaviorally equivalent given the current
  // vocabulary, not a translation defect.
  private val StableCodeHardActions = "stable-code-hard-actions"
  private val SemanticLabelOnly     = "semantic-label-only"
  private val Noise                 = "noise"

  // Every one of Gen1's 86 declarations, read directly from the Gen1 source file above. Anchor aliases
  // are a distinctive, non-exhaustive subset (the full ordered alias list is pinned exactly by the
  // 87-trace golden vector below, not repeated here).
  private val ledger: Vector[LedgerEntry] = Vector(
    LedgerEntry(1, "r001", StableCodeHardActions, Vector("маникюр", "манекюр"), Vector("service(manicure)", "enum(nail_service_type=manicure)"), Vector.empty, Vector.empty, Vector.empty),
    LedgerEntry(2, "r002", StableCodeHardActions, Vector("обычный маникюр"), Vector("service(manicure)", "enum(nail_service_type=manicure)"), Vector.empty, Vector.empty, Vector.empty),
    LedgerEntry(3, "r003", StableCodeHardActions, Vector("дешевый маникюр рядом"), Vector("service(manicure)", "enum(nail_service_type=manicure)"), Vector.empty, Vector.empty, Vector.empty),
    LedgerEntry(4, "r004", StableCodeHardActions, Vector("педикюр", "pedicure"), Vector("service(pedicure)", "enum(nail_service_type=pedicure)"), Vector.empty, Vector.empty, Vector.empty),
    LedgerEntry(5, "r005", StableCodeHardActions, Vector("наращивание и моделирование ногтей", "наращивание ногтей"), Vector("service(nail_modeling)", "enum(nail_service_type=extension)"), Vector.empty, Vector.empty, Vector.empty),
    LedgerEntry(6, "r006", StableCodeHardActions, Vector("ресницы", "lashes"), Vector("service(lashes)"), Vector.empty, Vector.empty, Vector.empty),
    LedgerEntry(7, "r007", StableCodeHardActions, Vector("брови", "augenbrauen"), Vector("service(brows)"), Vector.empty, Vector.empty, Vector.empty),
    LedgerEntry(8, "r008", StableCodeHardActions, Vector("pmu", "татуаж"), Vector("service(pmu)"), Vector.empty, Vector.empty, Vector.empty),
    LedgerEntry(9, "r009", StableCodeHardActions, Vector("удаление волос", "hair removal"), Vector("service(hair_removal)"), Vector.empty, Vector.empty, Vector.empty),
    LedgerEntry(10, "r010", StableCodeHardActions, Vector("косметология лица", "facial"), Vector("service(facial)"), Vector.empty, Vector.empty, Vector.empty),
    LedgerEntry(11, "r011", StableCodeHardActions, Vector("выездной уход и мини группы", "выездной уход"), Vector("service(mobile_beauty)"), Vector.empty, Vector.empty, Vector.empty),
    LedgerEntry(12, "r012", SemanticLabelOnly, Vector("ногти маникюр и педикюр", "nails"), Vector("category(nails)"), Vector("service(manicure)", "service(pedicure)", "service(nail_modeling)"), Vector.empty, Vector.empty),
    LedgerEntry(13, "r013", SemanticLabelOnly, Vector("ногти"), Vector("category(nails)"), Vector("service(manicure)", "service(pedicure)", "service(nail_modeling)"), Vector.empty, Vector.empty),
    LedgerEntry(14, "r014", StableCodeHardActions, Vector("ресницы брови и permanent make up"), Vector("category(lashes_brows_pmu)"), Vector.empty, Vector.empty, Vector.empty),
    LedgerEntry(15, "r015", StableCodeHardActions, Vector("косметология лица и уход"), Vector("category(facial_care)"), Vector.empty, Vector.empty, Vector.empty),
    LedgerEntry(
      16,
      "r016",
      "intentionally-changed:Gen1 declared this rule unconditionally (no requires), but it is token-identical to r009 (\"удаление волос\") and is always shadowed by it under Gen1's own declaration-order tie-break - dead code in Gen1. Gen2 makes the same practical unreachability an explicit, self-documenting contextual gate (requires=[service(hair_removal)]) instead of relying on accidental sort order.",
      Vector("удаление волос"),
      Vector("category(hair_removal)"),
      Vector.empty,
      Vector("service(hair_removal)"),
      Vector.empty,
    ),
    LedgerEntry(17, "r017", Noise, Vector("салон красоты"), Vector.empty, Vector.empty, Vector.empty, Vector.empty),
    LedgerEntry(18, "r018", StableCodeHardActions, Vector("lashes and brows"), Vector("service-any(lashes,brows)"), Vector.empty, Vector.empty, Vector.empty),
    LedgerEntry(19, "r019", StableCodeHardActions, Vector("что то для лица рядом", "что то для лица"), Vector("service(facial)"), Vector.empty, Vector.empty, Vector.empty),
    LedgerEntry(20, "r020", StableCodeHardActions, Vector("недорогие ногти рядом"), Vector("service-any(manicure,pedicure)"), Vector.empty, Vector.empty, Vector.empty),
    LedgerEntry(21, "r021", StableCodeHardActions, Vector("гель лак", "gel polish"), Vector("enum(nail_coating_type=gel_polish)"), Vector.empty, Vector.empty, Vector.empty),
    LedgerEntry(22, "r022", StableCodeHardActions, Vector("shellac", "шелак"), Vector("enum(nail_coating_type=shellac)"), Vector.empty, Vector.empty, Vector.empty),
    LedgerEntry(23, "r023", StableCodeHardActions, Vector("реснички 2д корр"), Vector("service(lashes)", "enum(lash_volume=volume2_d)", "enum(lash_service_type=refill)", "bool(with_correction=true)"), Vector.empty, Vector.empty, Vector.empty),
    LedgerEntry(24, "r024", StableCodeHardActions, Vector("с shellac и снятием"), Vector("enum(nail_coating_type=shellac)", "bool(with_removal=true)"), Vector.empty, Vector.empty, Vector.empty),
    LedgerEntry(25, "r025", StableCodeHardActions, Vector("shellac entfernen und neu"), Vector("enum(nail_coating_type=shellac)", "bool(with_removal=true)"), Vector.empty, Vector.empty, Vector.empty),
    LedgerEntry(26, "r026", StableCodeHardActions, Vector("снять гель с ногтей", "снять гель"), Vector("service(nail_modeling)", "enum(nail_service_type=removal)", "enum(nail_coating_type=gel)", "bool(with_removal=true)"), Vector.empty, Vector.empty, Vector.empty),
    LedgerEntry(27, "r027", StableCodeHardActions, Vector("gel removal"), Vector("service(nail_modeling)", "enum(nail_service_type=removal)", "enum(nail_coating_type=gel)"), Vector.empty, Vector.empty, Vector.empty),
    LedgerEntry(
      28,
      "r028",
      StableCodeHardActions,
      Vector("коррекция гелевых ногтей с дизайном"),
      Vector("service(nail_modeling)", "enum(nail_service_type=refill)", "enum(nail_coating_type=gel)", "bool(with_correction=true)", "bool(with_design=true)"),
      Vector.empty,
      Vector.empty,
      Vector.empty,
    ),
    LedgerEntry(
      29,
      "r029",
      StableCodeHardActions,
      Vector("коррекция гелевых ногтей"),
      Vector("service(nail_modeling)", "enum(nail_service_type=refill)", "enum(nail_coating_type=gel)", "bool(with_correction=true)"),
      Vector.empty,
      Vector.empty,
      Vector.empty,
    ),
    LedgerEntry(30, "r030", Noise, Vector("не татуаж"), Vector.empty, Vector.empty, Vector.empty, Vector.empty),
    LedgerEntry(31, "r031", StableCodeHardActions, Vector("без лака", "без покрытия", "no coating"), Vector("enum(nail_coating_type=no_coating)"), Vector.empty, Vector.empty, Vector.empty),
    LedgerEntry(
      32,
      "r032",
      StableCodeHardActions,
      Vector("гель", "gel"),
      Vector("enum(nail_coating_type=gel)"),
      Vector.empty,
      Vector("enum(nail_service_type=extension)"),
      Vector.empty,
    ),
    LedgerEntry(
      33,
      "r033",
      StableCodeHardActions,
      Vector("acrylic"),
      Vector("enum(nail_coating_type=acrylic)"),
      Vector.empty,
      Vector("enum(nail_service_type=extension)"),
      Vector.empty,
    ),
    LedgerEntry(34, "r034", StableCodeHardActions, Vector("классика", "classic"), Vector("enum(lash_volume=classic1_d)"), Vector.empty, Vector("service(lashes)"), Vector.empty),
    LedgerEntry(35, "r035", StableCodeHardActions, Vector("2д", "volume2_d"), Vector("enum(lash_volume=volume2_d)"), Vector.empty, Vector("service(lashes)"), Vector.empty),
    LedgerEntry(36, "r036", StableCodeHardActions, Vector("3д", "volume3_d"), Vector("enum(lash_volume=volume3_d)"), Vector.empty, Vector("service(lashes)"), Vector.empty),
    LedgerEntry(37, "r037", StableCodeHardActions, Vector("mega volume"), Vector("enum(lash_volume=mega_volume)"), Vector.empty, Vector("service(lashes)"), Vector.empty),
    LedgerEntry(38, "r038", StableCodeHardActions, Vector("extension", "наращивание ресниц"), Vector("enum(lash_service_type=extension)"), Vector.empty, Vector("service(lashes)"), Vector.empty),
    LedgerEntry(39, "r039", StableCodeHardActions, Vector("коррекция ресниц 2д"), Vector("enum(lash_volume=volume2_d)", "enum(lash_service_type=refill)", "bool(with_correction=true)", "service(lashes)"), Vector.empty, Vector.empty, Vector.empty),
    LedgerEntry(40, "r040", StableCodeHardActions, Vector("коррекция ресниц"), Vector("enum(lash_service_type=refill)", "bool(with_correction=true)", "service(lashes)"), Vector.empty, Vector.empty, Vector.empty),
    LedgerEntry(41, "r041", StableCodeHardActions, Vector("lash lifting"), Vector("enum(lash_service_type=lifting)", "service(lashes)"), Vector.empty, Vector.empty, Vector.empty),
    LedgerEntry(42, "r042", StableCodeHardActions, Vector("lash lifting mit färben"), Vector("enum(lash_service_type=lifting)", "bool(with_tinting=true)", "service(lashes)"), Vector.empty, Vector.empty, Vector.empty),
    LedgerEntry(43, "r043", StableCodeHardActions, Vector("lifting"), Vector("enum(lash_service_type=lifting)"), Vector.empty, Vector("service(lashes)"), Vector.empty),
    LedgerEntry(44, "r044", StableCodeHardActions, Vector("снять ресницы"), Vector("service(lashes)", "enum(lash_service_type=removal)", "bool(with_removal=true)"), Vector.empty, Vector.empty, Vector.empty),
    LedgerEntry(45, "r045", StableCodeHardActions, Vector("хна", "henna"), Vector("enum(brow_service_type=henna)", "bool(with_tinting=true)"), Vector.empty, Vector("service(brows)"), Vector.empty),
    LedgerEntry(46, "r046", StableCodeHardActions, Vector("lamination", "ламинирование"), Vector("enum(brow_service_type=lamination)"), Vector.empty, Vector("service(brows)"), Vector.empty),
    LedgerEntry(47, "r047", StableCodeHardActions, Vector("brow lamination"), Vector("enum(brow_service_type=lamination)", "service(brows)"), Vector.empty, Vector.empty, Vector.empty),
    LedgerEntry(48, "r048", StableCodeHardActions, Vector("augenbrauen färben"), Vector("service(brows)", "enum(brow_service_type=tinting)", "bool(with_tinting=true)"), Vector.empty, Vector.empty, Vector.empty),
    LedgerEntry(49, "r049", StableCodeHardActions, Vector("брови ламинирование с окрашиванием"), Vector("service(brows)", "enum(brow_service_type=lamination)", "bool(with_tinting=true)"), Vector.empty, Vector.empty, Vector.empty),
    LedgerEntry(50, "r050", StableCodeHardActions, Vector("shape and tint brows"), Vector("service(brows)", "enum-any(brow_service_type=shaping,tinting)", "bool(with_tinting=true)"), Vector.empty, Vector.empty, Vector.empty),
    LedgerEntry(51, "r051", StableCodeHardActions, Vector("коррекция бровей"), Vector("enum(brow_service_type=shaping)", "service(brows)"), Vector.empty, Vector.empty, Vector.empty),
    LedgerEntry(52, "r052", StableCodeHardActions, Vector("shaping"), Vector("enum(brow_service_type=shaping)"), Vector.empty, Vector("service(brows)"), Vector.empty),
    LedgerEntry(53, "r053", StableCodeHardActions, Vector("färben", "окрашивание"), Vector("bool(with_tinting=true)"), Vector.empty, Vector("service(brows)"), Vector.empty),
    LedgerEntry(54, "r054", StableCodeHardActions, Vector("färben", "окрашивание"), Vector("bool(with_tinting=true)"), Vector.empty, Vector("service(lashes)"), Vector.empty),
    LedgerEntry(55, "r055", StableCodeHardActions, Vector("губы", "lips"), Vector("enum(pmu_area=lips)"), Vector.empty, Vector("service(pmu)"), Vector.empty),
    LedgerEntry(56, "r056", StableCodeHardActions, Vector("eyeliner"), Vector("enum(pmu_area=eyeliner)"), Vector.empty, Vector("service(pmu)"), Vector.empty),
    LedgerEntry(57, "r057", StableCodeHardActions, Vector("correction", "коррекция"), Vector("bool(with_correction=true)"), Vector.empty, Vector("service(pmu)"), Vector.empty),
    LedgerEntry(58, "r058", StableCodeHardActions, Vector("powder brows"), Vector("service(pmu)", "enum(pmu_area=brows)"), Vector.empty, Vector.empty, Vector.empty),
    LedgerEntry(59, "r059", StableCodeHardActions, Vector("brows"), Vector("enum(pmu_area=brows)"), Vector.empty, Vector("service(pmu)"), Vector.empty),
    LedgerEntry(60, "r060", StableCodeHardActions, Vector("aquafacial"), Vector("service(facial)", "enum(facial_treatment_type=aquafacial)", "enum(body_area=face)"), Vector.empty, Vector.empty, Vector.empty),
    LedgerEntry(61, "r061", StableCodeHardActions, Vector("microneedling"), Vector("service(facial)", "enum(facial_treatment_type=microneedling)", "enum(body_area=face)"), Vector.empty, Vector.empty, Vector.empty),
    LedgerEntry(62, "r062", StableCodeHardActions, Vector("bb glow"), Vector("service(facial)", "enum(facial_treatment_type=bb_glow)", "enum(body_area=face)"), Vector.empty, Vector.empty, Vector.empty),
    LedgerEntry(63, "r063", StableCodeHardActions, Vector("чистка лица"), Vector("service(facial)", "enum(facial_treatment_type=cleansing)", "enum(body_area=face)"), Vector.empty, Vector.empty, Vector.empty),
    LedgerEntry(64, "r064", StableCodeHardActions, Vector("классический уход лицо"), Vector("service(facial)", "enum(facial_treatment_type=classic)", "enum(body_area=face)"), Vector.empty, Vector.empty, Vector.empty),
    LedgerEntry(65, "r065", StableCodeHardActions, Vector("увлажнение лица"), Vector("service(facial)", "enum(facial_treatment_type=hydration)", "enum(body_area=face_neck_decollete)"), Vector.empty, Vector.empty, Vector.empty),
    LedgerEntry(66, "r066", Noise, Vector("3 сеанса скидка"), Vector.empty, Vector.empty, Vector.empty, Vector.empty),
    LedgerEntry(67, "r067", StableCodeHardActions, Vector("anti aging"), Vector("service(facial)", "enum(facial_treatment_type=anti_aging)", "enum(body_area=face_neck_decollete)"), Vector.empty, Vector.empty, Vector.empty),
    LedgerEntry(68, "r068", StableCodeHardActions, Vector("peeling"), Vector("service(facial)", "enum(facial_treatment_type=peeling)", "enum(body_area=face)"), Vector.empty, Vector.empty, Vector.empty),
    LedgerEntry(69, "r069", StableCodeHardActions, Vector("face", "gesicht"), Vector("enum(body_area=face)"), Vector.empty, Vector("service(facial)"), Vector("enum(body_area=face_neck_decollete)")),
    LedgerEntry(70, "r070", StableCodeHardActions, Vector("wax", "воск"), Vector("service(hair_removal)", "enum(hair_removal_method=wax)"), Vector.empty, Vector.empty, Vector.empty),
    LedgerEntry(71, "r071", StableCodeHardActions, Vector("sugaring"), Vector("service(hair_removal)", "enum(hair_removal_method=sugaring)"), Vector.empty, Vector.empty, Vector.empty),
    LedgerEntry(72, "r072", StableCodeHardActions, Vector("ipl"), Vector("service(hair_removal)", "enum(hair_removal_method=laser)"), Vector.empty, Vector.empty, Vector.empty),
    LedgerEntry(73, "r073", StableCodeHardActions, Vector("laser", "лазер"), Vector("service(hair_removal)", "enum(hair_removal_method=laser)"), Vector.empty, Vector.empty, Vector.empty),
    LedgerEntry(
      74,
      "r074",
      "intentionally-changed:Gen1 modeled a fixed session count as a degenerate IntRange(min=6,max=6); Gen2's BeautyIntentAction vocabulary has no range case for int attributes, only a scalar IntAttribute, so this collapses to IntAttribute(session_count,6) - numerically equivalent (min=max=6 already meant \"exactly 6\").",
      Vector("6 сеансов", "6 сеанса"),
      Vector("service(hair_removal)", "int(session_count=6)"),
      Vector.empty,
      Vector.empty,
      Vector.empty,
    ),
    LedgerEntry(75, "r075", StableCodeHardActions, Vector("threading"), Vector("service(hair_removal)", "enum(hair_removal_method=threading)"), Vector.empty, Vector.empty, Vector.empty),
    LedgerEntry(76, "r076", Noise, Vector("рядом"), Vector.empty, Vector.empty, Vector("service(hair_removal)"), Vector.empty),
    LedgerEntry(77, "r077", StableCodeHardActions, Vector("beine", "full legs"), Vector("enum(body_area=full_legs)"), Vector.empty, Vector("service(hair_removal)"), Vector.empty),
    LedgerEntry(78, "r078", StableCodeHardActions, Vector("upper lip", "верхняя губа"), Vector("enum(body_area=upper_lip)"), Vector.empty, Vector("service(hair_removal)"), Vector.empty),
    LedgerEntry(79, "r079", StableCodeHardActions, Vector("губа"), Vector("enum(body_area=upper_lip)"), Vector.empty, Vector("service(hair_removal)"), Vector.empty),
    LedgerEntry(80, "r080", StableCodeHardActions, Vector("chin"), Vector("enum(body_area=chin)"), Vector.empty, Vector("service(hair_removal)"), Vector.empty),
    LedgerEntry(81, "r081", StableCodeHardActions, Vector("подмышки"), Vector("enum(body_area=armpits)"), Vector.empty, Vector("service(hair_removal)"), Vector.empty),
    LedgerEntry(82, "r082", StableCodeHardActions, Vector("armpits"), Vector("enum(body_area=armpits)"), Vector.empty, Vector("service(hair_removal)"), Vector.empty),
    LedgerEntry(83, "r083", StableCodeHardActions, Vector("bikini"), Vector("enum(body_area=bikini)"), Vector.empty, Vector("service(hair_removal)"), Vector.empty),
    LedgerEntry(84, "r084", StableCodeHardActions, Vector("бикини"), Vector("enum(body_area=bikini)"), Vector.empty, Vector("service(hair_removal)"), Vector.empty),
    LedgerEntry(85, "r085", Noise, Vector("недорого"), Vector.empty, Vector.empty, Vector("service(hair_removal)"), Vector.empty),
    LedgerEntry(86, "r086", StableCodeHardActions, Vector("lower legs"), Vector("enum(body_area=lower_legs)"), Vector.empty, Vector("service(hair_removal)"), Vector.empty),
  )

  private def actionTag(action: BeautyIntentAction): String =
    action match {
      case BeautyIntentAction.Service(code)                 => s"service(${code.value})"
      case BeautyIntentAction.ServiceAny(codes)              => s"service-any(${codes.map(_.value).mkString(",")})"
      case BeautyIntentAction.Category(code)                 => s"category(${code.value})"
      case BeautyIntentAction.EnumAttribute(code, value)     => s"enum($code=$value)"
      case BeautyIntentAction.EnumAttributeAny(code, values) => s"enum-any($code=${values.mkString(",")})"
      case BeautyIntentAction.BooleanAttribute(code, value)  => s"bool($code=$value)"
      case BeautyIntentAction.IntAttribute(code, value)      => s"int($code=$value)"
      case BeautyIntentAction.NearUser                       => "near-user"
    }

  "the Gen1 disposition ledger" should {
    "cover exactly 86 Gen1 declarations, index-for-index, with r087..r092 excluded as Gen2-only" in {
      assert(ledger.map(_.sourceIndex) == (1 to 86).toVector)
      assert(ledger.map(_.gen2RuleId) == (1 to 86).map(index => f"r$index%03d").toVector)
      assert(!ledger.exists(_.gen2RuleId == "r087"))
      assert(!ledger.exists(_.gen2RuleId == "r088"))
      assert(!ledger.exists(_.gen2RuleId == "r089"))
      assert(!ledger.exists(_.gen2RuleId == "r090"))
      assert(!ledger.exists(_.gen2RuleId == "r091"))
      assert(!ledger.exists(_.gen2RuleId == "r092"))
    }

    "match every ledgered rule's actual hard/semantic/requires/excludes action tags exactly" in {
      val rulesById = BeautyQIntentVocabulary.rules.map(rule => rule.id.value -> rule).toMap
      ledger.foreach { entry =>
        val rule = rulesById.getOrElse(entry.gen2RuleId, fail(s"missing rule ${entry.gen2RuleId}"))
        assert(rule.hardActions.map(actionTag) == entry.hardActionTags, s"hard action mismatch for ${entry.gen2RuleId}")
        assert(rule.semanticActions.map(actionTag) == entry.semanticActionTags, s"semantic action mismatch for ${entry.gen2RuleId}")
        assert(rule.requires.map(actionTag) == entry.requiresTags, s"requires mismatch for ${entry.gen2RuleId}")
        assert(rule.excludes.map(actionTag) == entry.excludesTags, s"excludes mismatch for ${entry.gen2RuleId}")
      }
    }

    "match every ledgered rule's disposition-implied structural invariant" in {
      val rulesById = BeautyQIntentVocabulary.rules.map(rule => rule.id.value -> rule).toMap
      ledger.foreach { entry =>
        val rule = rulesById.getOrElse(entry.gen2RuleId, fail(s"missing rule ${entry.gen2RuleId}"))
        if (entry.disposition == Noise) {
          assert(rule.noise, s"expected ${entry.gen2RuleId} to be noise")
          assert(rule.hardActions.isEmpty && rule.semanticActions.isEmpty, s"noise rule ${entry.gen2RuleId} must carry no actions")
        } else {
          assert(!rule.noise, s"expected ${entry.gen2RuleId} not to be noise")
        }
        if (entry.disposition == SemanticLabelOnly) {
          assert(rule.semanticActions.nonEmpty, s"expected ${entry.gen2RuleId} to carry semantic actions")
        } else ()
      }
    }

    "contain every declared anchor alias, normalized, among the rule's own normalized aliases" in {
      val rulesById = BeautyQIntentVocabulary.rules.map(rule => rule.id.value -> rule).toMap
      ledger.foreach { entry =>
        val rule = rulesById.getOrElse(entry.gen2RuleId, fail(s"missing rule ${entry.gen2RuleId}"))
        val normalizedAliases = rule.aliases.map(BeautyQIntentTextGen2.normalize)
        entry.anchorAliases.foreach { anchor =>
          assert(normalizedAliases.contains(anchor), s"anchor alias '$anchor' not found among ${entry.gen2RuleId}'s aliases: $normalizedAliases")
        }
      }
    }
  }

  "the Gen2 rule inventory" should {
    "contain exactly 92 rules, r001..r092 in order, with the final six rules explicitly Gen2-owned" in {
      val rules = BeautyQIntentVocabulary.rules
      assert(rules.map(_.id.value) == (1 to 92).map(index => f"r$index%03d").toVector)
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
      assert(gelManicureRule.aliases == Vector("gel-maniküre"))
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
      val noDesignRule = rules.find(_.id.value == "r092").getOrElse(fail("expected r092 to exist"))
      assert(noDesignRule.aliases == Vector("без дизайна", "without design", "ohne design"))
      assert(noDesignRule.mode == IntentRuleMode.Contextual)
      assert(noDesignRule.hardActions == Vector(BeautyIntentAction.BooleanAttribute("with_design", false)))
      assert(noDesignRule.requires match {
        case Vector(BeautyIntentAction.ServiceAny(codes)) => codes.map(_.value) == Vector("manicure", "pedicure", "nail_modeling")
        case _ => false
      })
    }

    // Independent evidence, not derived from BeautyIntentRuleTrace or any other production traversal
    // helper: every string below was authored by hand against the Gen1 source and the accepted Gen2
    // correction, then verified to match production output byte-for-byte. This must fail the moment any
    // alias, action, relation, mode or disposition changes for any of the 92 rules.
    "render the exact literal trace for every one of the 92 rules" in {
      val expected = Vector(
        "rule id=r001 aliases=[маникюр,манекюр,уход для рук,уход за руками,hand nail care,care for hands,hand care,manicure] mode=Independent hard=[service(manicure),enum(nail_service_type=manicure)] semantic=[] requires=[] excludes=[] noise=false label=absent",
        "rule id=r002 aliases=[обычный маникюр] mode=Independent hard=[service(manicure),enum(nail_service_type=manicure)] semantic=[] requires=[] excludes=[] noise=false label=absent",
        "rule id=r003 aliases=[дешевый маникюр рядом] mode=Independent hard=[service(manicure),enum(nail_service_type=manicure)] semantic=[] requires=[] excludes=[] noise=false label=absent",
        "rule id=r004 aliases=[педикюр,pedicure,pediküre,fußpflege,foot nail care,pflege der fußnägel,fußnägel] mode=Independent hard=[service(pedicure),enum(nail_service_type=pedicure)] semantic=[] requires=[] excludes=[] noise=false label=absent",
        "rule id=r005 aliases=[наращивание и моделирование ногтей,наращивание ногтей,acrylic nails,nagelmodellage,künstliche nägel,artificial nails,nail extension,builder gel] mode=Independent hard=[service(nail_modeling),enum(nail_service_type=extension)] semantic=[] requires=[] excludes=[] noise=false label=absent",
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
        "rule id=r048 aliases=[augenbrauen färben] mode=Independent hard=[service(brows),enum(brow_service_type=tinting),bool(with_tinting=true)] semantic=[] requires=[] excludes=[] noise=false label=absent",
        "rule id=r049 aliases=[брови ламинирование с окрашиванием] mode=Independent hard=[service(brows),enum(brow_service_type=lamination),bool(with_tinting=true)] semantic=[] requires=[] excludes=[] noise=false label=absent",
        "rule id=r050 aliases=[shape and tint brows] mode=Independent hard=[service(brows),enum-any(brow_service_type=shaping,tinting),bool(with_tinting=true)] semantic=[] requires=[] excludes=[] noise=false label=absent",
        "rule id=r051 aliases=[коррекция бровей] mode=Independent hard=[enum(brow_service_type=shaping),service(brows)] semantic=[] requires=[] excludes=[] noise=false label=absent",
        "rule id=r052 aliases=[shaping] mode=Contextual hard=[enum(brow_service_type=shaping)] semantic=[] requires=[service(brows)] excludes=[] noise=false label=absent",
        "rule id=r053 aliases=[färben,tint,окрашивание,tinting] mode=Contextual hard=[bool(with_tinting=true)] semantic=[] requires=[service(brows)] excludes=[] noise=false label=absent",
        "rule id=r054 aliases=[färben,tint,окрашивание,tinting] mode=Contextual hard=[bool(with_tinting=true)] semantic=[] requires=[service(lashes)] excludes=[] noise=false label=absent",
        "rule id=r055 aliases=[губы,губ,lips] mode=Contextual hard=[enum(pmu_area=lips)] semantic=[] requires=[service(pmu)] excludes=[] noise=false label=absent",
        "rule id=r056 aliases=[eyeliner] mode=Contextual hard=[enum(pmu_area=eyeliner)] semantic=[] requires=[service(pmu)] excludes=[] noise=false label=absent",
        "rule id=r057 aliases=[correction,коррекция] mode=Contextual hard=[bool(with_correction=true)] semantic=[] requires=[service(pmu)] excludes=[] noise=false label=absent",
        "rule id=r058 aliases=[powder brows,пудровый перманент бровей,брови с мягким пудровым эффектом надолго] mode=Independent hard=[service(pmu),enum(pmu_area=brows)] semantic=[] requires=[] excludes=[] noise=false label=absent",
        "rule id=r059 aliases=[brows,eyebrows] mode=Contextual hard=[enum(pmu_area=brows)] semantic=[] requires=[service(pmu)] excludes=[] noise=false label=absent",
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
        "rule id=r070 aliases=[wax,воск,воском] mode=Independent hard=[service(hair_removal),enum(hair_removal_method=wax)] semantic=[] requires=[] excludes=[] noise=false label=absent",
        "rule id=r071 aliases=[sugaring] mode=Independent hard=[service(hair_removal),enum(hair_removal_method=sugaring)] semantic=[] requires=[] excludes=[] noise=false label=absent",
        "rule id=r072 aliases=[ipl] mode=Independent hard=[service(hair_removal),enum(hair_removal_method=laser)] semantic=[] requires=[] excludes=[] noise=false label=absent",
        "rule id=r073 aliases=[laser,лазер] mode=Independent hard=[service(hair_removal),enum(hair_removal_method=laser)] semantic=[] requires=[] excludes=[] noise=false label=absent",
        "rule id=r074 aliases=[6 сеансов,6 сеанса] mode=Independent hard=[service(hair_removal),int(session_count=6)] semantic=[] requires=[] excludes=[] noise=false label=absent",
        "rule id=r075 aliases=[threading] mode=Independent hard=[service(hair_removal),enum(hair_removal_method=threading)] semantic=[] requires=[] excludes=[] noise=false label=absent",
        "rule id=r076 aliases=[рядом] mode=Contextual hard=[] semantic=[] requires=[service(hair_removal)] excludes=[] noise=true label=absent",
        "rule id=r077 aliases=[beine,full legs] mode=Contextual hard=[enum(body_area=full_legs)] semantic=[] requires=[service(hair_removal)] excludes=[] noise=false label=absent",
        "rule id=r078 aliases=[upper lip,верхняя губа,lip] mode=Contextual hard=[enum(body_area=upper_lip)] semantic=[] requires=[service(hair_removal)] excludes=[] noise=false label=absent",
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
        "rule id=r089 aliases=[gel maniküre] mode=Independent hard=[service(manicure),enum(nail_service_type=manicure),enum(nail_coating_type=gel_polish)] semantic=[] requires=[] excludes=[] noise=false label=absent",
        "rule id=r090 aliases=[снять] mode=Contextual hard=[service(lashes),enum(lash_service_type=removal),bool(with_removal=true)] semantic=[] requires=[service(lashes)] excludes=[] noise=false label=absent",
        "rule id=r091 aliases=[regular polish,ordinary polish,обычный лак,обычным лаком,normaler lack] mode=Independent hard=[enum(nail_coating_type=regular_polish)] semantic=[] requires=[] excludes=[] noise=false label=absent",
        "rule id=r092 aliases=[без дизайна,without design,ohne design] mode=Contextual hard=[bool(with_design=false)] semantic=[] requires=[service-any(manicure,pedicure,nail_modeling)] excludes=[] noise=false label=absent",
      )
      assert(expected.size == 92)
      val actual = BeautyQIntentVocabulary.rules.map(BeautyIntentRuleTrace.render)
      assert(actual == expected)
    }
  }
}
