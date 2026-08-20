package leaderboard.search.beautyq.gen2.contract

import leaderboard.model.{CategoryCode, ServiceCode}
import leaderboard.search.gen2.contract.*

final case class IntentRuleId(value: String)

/** A stable semantic identity plus a human-readable diagnostic label. The stable key is never a
  * display name; parser labels use stable service/category/attribute codes. */
final case class CanonicalSemanticLabel private (stableKey: String, text: String)
object CanonicalSemanticLabel {
  def from(stableKey: String, text: String): Either[String, CanonicalSemanticLabel] =
    if (stableKey.trim.isEmpty) Left("stable semantic label key must be non-blank")
    else if (text.trim.isEmpty) Left("canonical semantic label text must be non-blank")
    else Right(CanonicalSemanticLabel(stableKey, text))
}

enum IntentRuleMode {
  case Independent, Contextual, SemanticOverlay
}

sealed trait BeautyIntentAction
object BeautyIntentAction {
  final case class Service(code: ServiceCode) extends BeautyIntentAction
  final case class ServiceAny(codes: Vector[ServiceCode]) extends BeautyIntentAction
  final case class Category(code: CategoryCode) extends BeautyIntentAction
  final case class EnumAttribute(code: String, value: String) extends BeautyIntentAction
  final case class EnumAttributeAny(code: String, values: Vector[String]) extends BeautyIntentAction
  final case class BooleanAttribute(code: String, value: Boolean) extends BeautyIntentAction
  final case class IntAttribute(code: String, value: Int) extends BeautyIntentAction
  case object NearUser extends BeautyIntentAction

  /** Whether `produced` (an action already selected by an earlier-matched rule, or offered as
    * vocabulary-wide evidence) satisfies `required` (a `requires`/`excludes` reference declared by
    * another rule). Deliberately not case-class equality: an `Any` action stands in for any one of its
    * member values on either side of the comparison, per the accepted any-of semantics pinned in
    * BeautyIntentActionCoverageSpec. The parser's contextual matching and vocabulary validation's
    * unresolved-requires/excludes check both call this one function. */
  def covers(produced: BeautyIntentAction, required: BeautyIntentAction): Boolean = (produced, required) match {
    case (Service(left), Service(right))                                                   => left == right
    case (ServiceAny(left), Service(right))                                                 => left.contains(right)
    case (Service(left), ServiceAny(right))                                                 => right.contains(left)
    case (ServiceAny(left), ServiceAny(right))                                               => right.forall(left.contains)
    case (Category(left), Category(right))                                                   => left == right
    case (EnumAttribute(leftCode, leftValue), EnumAttribute(rightCode, rightValue))           => leftCode == rightCode && leftValue == rightValue
    case (EnumAttributeAny(leftCode, leftValues), EnumAttribute(rightCode, rightValue))       => leftCode == rightCode && leftValues.contains(rightValue)
    case (EnumAttribute(leftCode, leftValue), EnumAttributeAny(rightCode, rightValues))       => leftCode == rightCode && rightValues.contains(leftValue)
    case (EnumAttributeAny(leftCode, leftValues), EnumAttributeAny(rightCode, rightValues))   => leftCode == rightCode && rightValues.forall(leftValues.contains)
    case (BooleanAttribute(leftCode, leftValue), BooleanAttribute(rightCode, rightValue))     => leftCode == rightCode && leftValue == rightValue
    case (IntAttribute(leftCode, leftValue), IntAttribute(rightCode, rightValue))             => leftCode == rightCode && leftValue == rightValue
    case (NearUser, NearUser)                                                                  => true
    case _                                                                                      => false
  }
}

/** BeautyQ's explicit canonical-label policy for semantic text. Stable action identities are the
  * lookup keys; the readable label text is domain policy, not a storage path, public request name or
  * backend-derived value. Numeric intent bounds intentionally have no embedding label. */
object BeautyQSemanticLabelPolicy {
  def forAction(action: BeautyIntentAction): Option[CanonicalSemanticLabel] = action match {
    case BeautyIntentAction.Service(code) => label(s"service:${code.value}", humanize(code.value))
    case BeautyIntentAction.ServiceAny(codes) => if (codes.nonEmpty) label(s"service-any:${codes.map(_.value).mkString(",")}", codes.map(code => humanize(code.value)).mkString(", ")) else None
    case BeautyIntentAction.Category(code) => label(s"category:${code.value}", humanize(code.value))
    case BeautyIntentAction.EnumAttribute(code, value) => label(s"attribute.enum:$code=$value", s"${humanize(code)}: ${humanize(value)}")
    case BeautyIntentAction.EnumAttributeAny(code, values) => label(s"attribute.enum:$code=${values.mkString(",")}", s"${humanize(code)}: ${values.map(humanize).mkString(", ")}")
    case BeautyIntentAction.BooleanAttribute(code, value) => label(s"attribute.boolean:$code=$value", s"${humanize(code)}: $value")
    case BeautyIntentAction.IntAttribute(_, _) => None
    case BeautyIntentAction.NearUser => label("location:near-user", "near user")
  }

  private def humanize(value: String): String = value.replace('_', ' ')
  private def label(stableKey: String, text: String): Option[CanonicalSemanticLabel] = CanonicalSemanticLabel.from(stableKey, text).toOption
}

/** BeautyQ's explicit action-to-plan policy. The generic matcher never knows document fields; this
  * adapter is the one domain-owned boundary that turns selected hard actions into typed constraints. */
object BeautyQIntentActionCompiler {
  // The source vocabulary is constructed only after validateActionValue proves every attribute code
  // exists in the matching authoritative field inventory. A missing entry here is therefore a
  // broken source invariant, not a user-input case; fail loudly instead of silently dropping a hard
  // constraint.
  private def fieldForCode[A](fields: Map[String, SearchField[VariantSearchDocumentGen2, A]], code: String): SearchField[VariantSearchDocumentGen2, A] =
    fields.getOrElse(code, throw new IllegalStateException(s"validated BeautyQ intent refers to unknown attribute '$code'"))

  def hardConstraints(action: BeautyIntentAction): Vector[SourcedConstraint[VariantSearchDocumentGen2]] = {
    val fields = BeautyQSearchDeclarations.variants.Fields
    val constraint = action match {
      case BeautyIntentAction.Service(code) => Some(PlannedConstraint.Terms(fields.serviceCode, Set(code)))
      case BeautyIntentAction.ServiceAny(codes) => Some(PlannedConstraint.Terms(fields.serviceCode, codes.toSet))
      case BeautyIntentAction.Category(code) => Some(PlannedConstraint.Terms(fields.categoryCode, Set(code)))
      case BeautyIntentAction.EnumAttribute(code, value) => Some(PlannedConstraint.Terms(fieldForCode(fields.enumAttributesByCode, code), Set(value)))
      case BeautyIntentAction.EnumAttributeAny(code, values) => Some(PlannedConstraint.Terms(fieldForCode(fields.enumAttributesByCode, code), values.toSet))
      case BeautyIntentAction.BooleanAttribute(code, value) => Some(PlannedConstraint.Terms(fieldForCode(fields.booleanAttributesByCode, code), Set(value)))
      case BeautyIntentAction.IntAttribute(code, value) => Some(PlannedConstraint.NumberRange(fieldForCode(fields.intAttributesByCode, code), RangeBounds(Bound.Inclusive(value), Bound.Inclusive(value))))
      case BeautyIntentAction.NearUser => None
    }
    constraint.map(value => SourcedConstraint(value, ConstraintProvenance.ParsedHard)).toVector
  }
}

/** One executable rule value. `semanticActions` are the replacement for Gen1 non-geo soft boosts:
  * they preserve stable target identity without pretending that a generic scoring signal exists. */
final case class BeautyIntentRule(
  id: IntentRuleId,
  aliases: Vector[String],
  mode: IntentRuleMode,
  hardActions: Vector[BeautyIntentAction],
  semanticActions: Vector[BeautyIntentAction],
  requires: Vector[BeautyIntentAction],
  excludes: Vector[BeautyIntentAction],
  noise: Boolean,
  canonicalSemanticLabel: Option[CanonicalSemanticLabel] = None,
)

/** Adapter from the BeautyQ rule value to the generic matcher. It exposes only the rule facts the
  * matcher needs; action-to-constraint and label policy remain explicit BeautyQ concerns. */
object BeautyQIntentRuleView extends SearchIntentRuleView[BeautyIntentRule, BeautyIntentAction, CanonicalSemanticLabel] {
  def id(rule: BeautyIntentRule): String = rule.id.value
  def aliases(rule: BeautyIntentRule): Vector[String] = rule.aliases
  def mode(rule: BeautyIntentRule): SearchIntentRuleMode = rule.mode match {
    case IntentRuleMode.Independent     => SearchIntentRuleMode.Independent
    case IntentRuleMode.Contextual       => SearchIntentRuleMode.Contextual
    case IntentRuleMode.SemanticOverlay  => SearchIntentRuleMode.SemanticOverlay
  }
  def hardActions(rule: BeautyIntentRule): Vector[BeautyIntentAction] = rule.hardActions
  def semanticActions(rule: BeautyIntentRule): Vector[BeautyIntentAction] = rule.semanticActions
  def requires(rule: BeautyIntentRule): Vector[BeautyIntentAction] = rule.requires
  def excludes(rule: BeautyIntentRule): Vector[BeautyIntentAction] = rule.excludes
  def labels(rule: BeautyIntentRule): Vector[CanonicalSemanticLabel] = rule.canonicalSemanticLabel.toVector
}

sealed trait BeautyIntentVocabularyError
object BeautyIntentVocabularyError {
  final case class DuplicateRuleId(id: IntentRuleId) extends BeautyIntentVocabularyError
  final case class BlankRuleId(id: IntentRuleId) extends BeautyIntentVocabularyError
  final case class EmptyAliases(id: IntentRuleId) extends BeautyIntentVocabularyError
  final case class BlankAlias(id: IntentRuleId, alias: String) extends BeautyIntentVocabularyError
  final case class DuplicateAlias(id: IntentRuleId, alias: String) extends BeautyIntentVocabularyError
  final case class AmbiguousIndependentAlias(alias: String, first: IntentRuleId, second: IntentRuleId) extends BeautyIntentVocabularyError
  final case class UnknownAttribute(code: String) extends BeautyIntentVocabularyError
  final case class InvalidAttributeValue(code: String, value: String) extends BeautyIntentVocabularyError
  final case class InvalidServiceCode(value: String) extends BeautyIntentVocabularyError
  final case class InvalidCategoryCode(value: String) extends BeautyIntentVocabularyError
  final case class NoiseHasActions(id: IntentRuleId) extends BeautyIntentVocabularyError
  final case class NoiseHasCanonicalLabel(id: IntentRuleId) extends BeautyIntentVocabularyError
  final case class NearUserHasHardAction(id: IntentRuleId) extends BeautyIntentVocabularyError
  final case class InvalidCanonicalLabel(id: IntentRuleId, label: CanonicalSemanticLabel) extends BeautyIntentVocabularyError
  final case class EmptyServiceAny(id: IntentRuleId) extends BeautyIntentVocabularyError
  final case class DuplicateServiceAnyCode(id: IntentRuleId, code: ServiceCode) extends BeautyIntentVocabularyError
  final case class EmptyEnumAttributeAny(id: IntentRuleId, code: String) extends BeautyIntentVocabularyError
  final case class DuplicateEnumAttributeAnyValue(id: IntentRuleId, code: String, value: String) extends BeautyIntentVocabularyError
  final case class UnresolvedRequiresAction(id: IntentRuleId, action: BeautyIntentAction) extends BeautyIntentVocabularyError
  final case class UnresolvedExcludesAction(id: IntentRuleId, action: BeautyIntentAction) extends BeautyIntentVocabularyError
}

final case class BeautyQIntentVocabulary private (rules: Vector[BeautyIntentRule])

object BeautyQIntentVocabulary {
  /** The ordered BeautyQ intent vocabulary is the business policy: aliases, stable actions,
    * contextual requirements and semantic overlays. Matching/selection is generic; these rules and
    * the action-to-field compiler are not inferred from the document shape. */
  private val Fields = BeautyQSearchDeclarations.variants.Fields

  private def service(value: String): BeautyIntentAction.Service = codeService(value)
  private def category(value: String): BeautyIntentAction.Category = codeCategory(value)
  private def enumAttr(code: String, value: String): BeautyIntentAction.EnumAttribute = BeautyIntentAction.EnumAttribute(code, value)
  private def bool(code: String, value: Boolean): BeautyIntentAction.BooleanAttribute = BeautyIntentAction.BooleanAttribute(code, value)
  private def int(code: String, value: Int): BeautyIntentAction.IntAttribute = BeautyIntentAction.IntAttribute(code, value)
  private def codeService(value: String): BeautyIntentAction.Service = ServiceCode.fromString(value) match {
    case Right(code) => BeautyIntentAction.Service(code)
    case Left(_)     => throw new IllegalStateException(s"invalid source ServiceCode '$value'")
  }
  private def codeCategory(value: String): BeautyIntentAction.Category = CategoryCode.fromString(value) match {
    case Right(code) => BeautyIntentAction.Category(code)
    case Left(_)     => throw new IllegalStateException(s"invalid source CategoryCode '$value'")
  }
  private val nailServiceFamily = BeautyIntentAction.ServiceAny(
    Vector(
      codeService("manicure").code,
      codeService("pedicure").code,
      codeService("nail_modeling").code,
    )
  )

  private def rule(
    id: String,
    aliases: String*,
  )(hard: BeautyIntentAction*)(using mode: IntentRuleMode = IntentRuleMode.Independent, semantic: Vector[BeautyIntentAction] = Vector.empty, requires: Vector[BeautyIntentAction] = Vector.empty, excludes: Vector[BeautyIntentAction] = Vector.empty, noise: Boolean = false): BeautyIntentRule =
    BeautyIntentRule(IntentRuleId(id), aliases.toVector, mode, hard.toVector, semantic, requires, excludes, noise)

  private def noiseRule(id: String, aliases: String*)(using requires: Vector[BeautyIntentAction] = Vector.empty): BeautyIntentRule =
    BeautyIntentRule(
      IntentRuleId(id),
      aliases.toVector,
      if (requires.isEmpty) IntentRuleMode.Independent else IntentRuleMode.Contextual,
      Vector.empty,
      Vector.empty,
      requires,
      Vector.empty,
      noise = true,
    )

  // Business-facing intent policy starts here; keep aliases and stable codes readable in one list.
  private val sourceRules: Vector[BeautyIntentRule] = Vector(
    rule("r001", "маникюр", "манекюр", "уход для рук", "уход за руками", "hand nail care", "care for hands", "hand care", "manicure")(service("manicure"), enumAttr("nail_service_type", "manicure")),
    rule("r002", "обычный маникюр")(service("manicure"), enumAttr("nail_service_type", "manicure")),
    rule("r003", "дешевый маникюр рядом")(service("manicure"), enumAttr("nail_service_type", "manicure")),
    rule("r004", "педикюр", "pedicure", "pediküre", "fußpflege", "foot nail care", "pflege der fußnägel", "fußnägel")(service("pedicure"), enumAttr("nail_service_type", "pedicure")),
    rule("r005", "наращивание и моделирование ногтей", "наращивание ногтей", "acrylic nails", "nagelmodellage", "künstliche nägel", "artificial nails", "nail extension", "builder gel", "nail modeling", "nail modeling extension")(service("nail_modeling"), enumAttr("nail_service_type", "extension")),
    rule("r006", "ресницы", "lashes", "wimpern", "lash")(service("lashes")),
    rule("r007", "брови", "augenbrauen")(service("brows")),
    rule("r008", "pmu", "permanent makeup", "permanent make-up", "перманент", "татуаж")(service("pmu")),
    rule("r009", "удаление волос", "hair removal", "depilation", "депиляция")(service("hair_removal")),
    rule("r010", "косметология лица", "facial", "face treatment")(service("facial")),
    rule("r011", "выездной уход и мини-группы", "выездной уход", "выездной уход для двоих", "beauty treatment at home", "home beauty care", "beauty at home", "small group")(service("mobile_beauty")),
    rule("r012", "ногти, маникюр и педикюр", "nails")(category("nails"))(using semantic = Vector(service("manicure"), service("pedicure"), service("nail_modeling"))),
    rule("r013", "ногти")(category("nails"))(using semantic = Vector(service("manicure"), service("pedicure"), service("nail_modeling"))),
    rule("r014", "ресницы, брови и permanent make-up")(category("lashes_brows_pmu")),
    rule("r015", "косметология лица и уход")(category("facial_care")),
    rule("r016", "удаление волос")(category("hair_removal"))(using mode = IntentRuleMode.Contextual, requires = Vector(service("hair_removal"))),
    noiseRule("r017", "салон красоты"),
    rule("r018", "lashes and brows")(BeautyIntentAction.ServiceAny(Vector(codeService("lashes").code, codeService("brows").code))),
    rule("r019", "что-то для лица рядом", "что-то для лица")(service("facial")),
    rule("r020", "недорогие ногти рядом")(BeautyIntentAction.ServiceAny(Vector(codeService("manicure").code, codeService("pedicure").code))),
    rule("r021", "гель лак", "гель-лаком", "gel polish", "gel farbe", "gel lack", "гелевым покрытием")(enumAttr("nail_coating_type", "gel_polish")),
    rule("r022", "shellac", "шелак")(enumAttr("nail_coating_type", "shellac")),
    rule("r023", "реснички 2д корр")(service("lashes"), enumAttr("lash_volume", "volume2_d"), enumAttr("lash_service_type", "refill"), bool("with_correction", true)),
    rule("r024", "с shellac и снятием")(enumAttr("nail_coating_type", "shellac"), bool("with_removal", true)),
    rule("r025", "shellac entfernen und neu")(enumAttr("nail_coating_type", "shellac"), bool("with_removal", true)),
     rule("r026", "снять гель с ногтей", "снять гель")(service("nail_modeling"), enumAttr("nail_service_type", "removal"), enumAttr("nail_coating_type", "gel"), bool("with_removal", true)),
    rule("r027", "gel removal")(service("nail_modeling"), enumAttr("nail_service_type", "removal"), enumAttr("nail_coating_type", "gel")),
    rule("r028", "коррекция гелевых ногтей с дизайном")(service("nail_modeling"), enumAttr("nail_service_type", "refill"), enumAttr("nail_coating_type", "gel"), bool("with_correction", true), bool("with_design", true)),
    rule("r029", "коррекция гелевых ногтей")(service("nail_modeling"), enumAttr("nail_service_type", "refill"), enumAttr("nail_coating_type", "gel"), bool("with_correction", true)),
    noiseRule("r030", "не татуаж"),
    rule("r031", "без лака", "без покрытия", "no coating", "без цветного покрытия")(enumAttr("nail_coating_type", "no_coating")),
    rule("r032", "гель", "gel")(enumAttr("nail_coating_type", "gel"))(using mode = IntentRuleMode.Contextual, requires = Vector(enumAttr("nail_service_type", "extension"))),
    rule("r033", "acrylic")(enumAttr("nail_coating_type", "acrylic"))(using mode = IntentRuleMode.Contextual, requires = Vector(enumAttr("nail_service_type", "extension"))),
    rule("r034", "классика", "classic", "1d", "1 д", "classic1_d")(enumAttr("lash_volume", "classic1_d"))(using mode = IntentRuleMode.Contextual, requires = Vector(service("lashes"))),
    rule("r035", "2д", "2d", "volume2_d")(enumAttr("lash_volume", "volume2_d"))(using mode = IntentRuleMode.Contextual, requires = Vector(service("lashes"))),
    rule("r036", "3д", "3d", "volume3_d")(enumAttr("lash_volume", "volume3_d"))(using mode = IntentRuleMode.Contextual, requires = Vector(service("lashes"))),
    rule("r037", "mega volume")(enumAttr("lash_volume", "mega_volume"))(using mode = IntentRuleMode.Contextual, requires = Vector(service("lashes"))),
    rule("r038", "extension", "extensions", "lash extension", "lash extensions", "наращивание ресниц", "full set")(enumAttr("lash_service_type", "extension"))(using mode = IntentRuleMode.Contextual, requires = Vector(service("lashes"))),
    rule("r039", "коррекция ресниц 2д", "коррекция ресниц 2d")(enumAttr("lash_volume", "volume2_d"), enumAttr("lash_service_type", "refill"), bool("with_correction", true), service("lashes")),
    rule("r040", "коррекция ресниц")(enumAttr("lash_service_type", "refill"), bool("with_correction", true), service("lashes")),
    rule("r041", "lash lifting", "lash lift")(enumAttr("lash_service_type", "lifting"), service("lashes")),
    rule("r042", "lash lifting mit färben")(enumAttr("lash_service_type", "lifting"), bool("with_tinting", true), service("lashes")),
    rule("r043", "lifting")(enumAttr("lash_service_type", "lifting"))(using mode = IntentRuleMode.Contextual, requires = Vector(service("lashes"))),
    rule("r044", "снять ресницы")(service("lashes"), enumAttr("lash_service_type", "removal"), bool("with_removal", true)),
    rule("r045", "хна", "henna")(enumAttr("brow_service_type", "henna"), bool("with_tinting", true))(using mode = IntentRuleMode.Contextual, requires = Vector(service("brows"))),
    rule("r046", "lamination", "ламинирование")(enumAttr("brow_service_type", "lamination"))(using mode = IntentRuleMode.Contextual, requires = Vector(service("brows"))),
    rule("r047", "brow lamination")(enumAttr("brow_service_type", "lamination"), service("brows")),
    rule("r048", "augenbrauen färben", "brow tint")(service("brows"), enumAttr("brow_service_type", "tinting"), bool("with_tinting", true)),
    rule("r049", "брови ламинирование с окрашиванием")(service("brows"), enumAttr("brow_service_type", "lamination"), bool("with_tinting", true)),
    rule("r050", "shape and tint brows")(service("brows"), BeautyIntentAction.EnumAttributeAny("brow_service_type", Vector("shaping", "tinting")), bool("with_tinting", true)),
    rule("r051", "коррекция бровей")(enumAttr("brow_service_type", "shaping"), service("brows")),
    rule("r052", "shaping")(enumAttr("brow_service_type", "shaping"))(using mode = IntentRuleMode.Contextual, requires = Vector(service("brows"))),
    rule("r053", "färben", "tint", "окрашивание", "tinting")(bool("with_tinting", true))(using mode = IntentRuleMode.Contextual, requires = Vector(service("brows"))),
    rule("r054", "färben", "tint", "окрашивание", "tinting")(bool("with_tinting", true))(using mode = IntentRuleMode.Contextual, requires = Vector(service("lashes"))),
    rule("r055", "губы", "губ", "lips")(enumAttr("pmu_area", "lips"))(using mode = IntentRuleMode.Contextual, requires = Vector(service("pmu"))),
    rule("r056", "eyeliner")(enumAttr("pmu_area", "eyeliner"))(using mode = IntentRuleMode.Contextual, requires = Vector(service("pmu"))),
     rule("r057", "correction", "коррекция")(bool("with_correction", true))(using mode = IntentRuleMode.Contextual, requires = Vector(service("pmu"))),
    rule("r058", "powder brows", "пудровый перманент бровей", "брови с мягким пудровым эффектом надолго", "перманентный макияж бровей")(service("pmu"), enumAttr("pmu_area", "brows")),
    rule("r059", "brows", "eyebrows", "бровей")(enumAttr("pmu_area", "brows"))(using mode = IntentRuleMode.Contextual, requires = Vector(service("pmu"))),
    rule("r060", "aquafacial", "hydrafacial")(service("facial"), enumAttr("facial_treatment_type", "aquafacial"), enumAttr("body_area", "face")),
    rule("r061", "microneedling")(service("facial"), enumAttr("facial_treatment_type", "microneedling"), enumAttr("body_area", "face")),
    rule("r062", "bb glow", "хочу чтобы тон лица выглядел ровнее без ежедневного макияжа")(service("facial"), enumAttr("facial_treatment_type", "bb_glow"), enumAttr("body_area", "face")),
    rule("r063", "чистка лица", "facial cleansing", "gesichtsreinigung")(service("facial"), enumAttr("facial_treatment_type", "cleansing"), enumAttr("body_area", "face")),
    rule("r064", "классический уход лицо")(service("facial"), enumAttr("facial_treatment_type", "classic"), enumAttr("body_area", "face")),
    rule("r065", "увлажнение лица")(service("facial"), enumAttr("facial_treatment_type", "hydration"), enumAttr("body_area", "face_neck_decollete")),
    noiseRule("r066", "3 сеанса скидка"),
    rule("r067", "anti aging")(service("facial"), enumAttr("facial_treatment_type", "anti_aging"), enumAttr("body_area", "face_neck_decollete")),
    rule("r068", "peeling")(service("facial"), enumAttr("facial_treatment_type", "peeling"), enumAttr("body_area", "face")),
    rule("r069", "face", "gesicht")(enumAttr("body_area", "face"))(using mode = IntentRuleMode.Contextual, requires = Vector(service("facial")), excludes = Vector(enumAttr("body_area", "face_neck_decollete"))),
    rule("r070", "wax", "waxing", "воск", "воском")(service("hair_removal"), enumAttr("hair_removal_method", "wax")),
    rule("r071", "sugaring")(service("hair_removal"), enumAttr("hair_removal_method", "sugaring")),
    rule("r072", "ipl")(service("hair_removal"), enumAttr("hair_removal_method", "laser")),
    rule("r073", "laser", "лазер")(service("hair_removal"), enumAttr("hair_removal_method", "laser")),
    rule("r074", "6 сеансов", "6 сеанса")(service("hair_removal"), int("session_count", 6)),
    rule("r075", "threading")(service("hair_removal"), enumAttr("hair_removal_method", "threading")),
    noiseRule("r076", "рядом")(using requires = Vector(service("hair_removal"))),
    rule("r077", "beine", "full legs")(enumAttr("body_area", "full_legs"))(using mode = IntentRuleMode.Contextual, requires = Vector(service("hair_removal"))),
    rule("r078", "upper lip", "верхняя губа", "lip", "oberlippe")(enumAttr("body_area", "upper_lip"))(using mode = IntentRuleMode.Contextual, requires = Vector(service("hair_removal"))),
    rule("r079", "губа")(enumAttr("body_area", "upper_lip"))(using mode = IntentRuleMode.Contextual, requires = Vector(service("hair_removal"))),
    rule("r080", "chin")(enumAttr("body_area", "chin"))(using mode = IntentRuleMode.Contextual, requires = Vector(service("hair_removal"))),
    rule("r081", "подмышки")(enumAttr("body_area", "armpits"))(using mode = IntentRuleMode.Contextual, requires = Vector(service("hair_removal"))),
    rule("r082", "armpits", "underarms")(enumAttr("body_area", "armpits"))(using mode = IntentRuleMode.Contextual, requires = Vector(service("hair_removal"))),
    rule("r083", "bikini")(enumAttr("body_area", "bikini"))(using mode = IntentRuleMode.Contextual, requires = Vector(service("hair_removal"))),
    rule("r084", "бикини")(enumAttr("body_area", "bikini"))(using mode = IntentRuleMode.Contextual, requires = Vector(service("hair_removal"))),
    noiseRule("r085", "недорого")(using requires = Vector(service("hair_removal"))),
    rule("r086", "lower legs")(enumAttr("body_area", "lower_legs"))(using mode = IntentRuleMode.Contextual, requires = Vector(service("hair_removal"))),
    // Gen2-only NearUser rule. It is a semantic overlay: it may share a location token with a longer
    // hard alias such as "дешевый маникюр рядом", while its explicit hair-removal exclusion preserves
    // the inherited r076 noise exception for hair-removal queries.
    rule("r087", "рядом", "near me", "nearby")()(using mode = IntentRuleMode.SemanticOverlay, semantic = Vector(BeautyIntentAction.NearUser), excludes = Vector(service("hair_removal"))),
    rule(
      "r088",
      "хочу привести себя в порядок",
      "привести себя в порядок",
    )(
      BeautyIntentAction.ServiceAny(
        Vector(
          codeService("manicure").code,
          codeService("lashes").code,
          codeService("brows").code,
          codeService("facial").code,
        )
      )
    ),
    rule("r089", "gel-maniküre", "gel manicure")(
      service("manicure"),
      enumAttr("nail_service_type", "manicure"),
      enumAttr("nail_coating_type", "gel_polish"),
    ),
    rule("r090", "снять")(
      service("lashes"),
      enumAttr("lash_service_type", "removal"),
      bool("with_removal", true),
    )(using mode = IntentRuleMode.Contextual, requires = Vector(service("lashes"))),
    rule("r091", "regular polish", "ordinary polish", "обычный лак", "обычным лаком", "normaler lack")(
      enumAttr("nail_coating_type", "regular_polish"),
    ),
    rule("r092", "без дизайна", "without design", "ohne design")(
      bool("with_design", false),
    )(using mode = IntentRuleMode.Contextual, requires = Vector(nailServiceFamily)),
    rule("r093", "снятие наращенных ногтей", "nail extension removal", "nail modeling removal", "remove nail modeling")(
      service("nail_modeling"),
      enumAttr("nail_service_type", "removal"),
      bool("with_removal", true),
    ),
    rule("r094", "henna brows", "henna brow")(
      service("brows"),
      enumAttr("brow_service_type", "henna"),
      bool("with_tinting", true),
    ),
    rule("r095", "brow shaping")(
      service("brows"),
      enumAttr("brow_service_type", "shaping"),
    ),
    rule("r096", "nail refill")(
      service("nail_modeling"),
      enumAttr("nail_service_type", "refill"),
      bool("with_correction", true),
    ),
    rule("r097", "gel")(
      enumAttr("nail_coating_type", "gel"),
    )(using
      mode = IntentRuleMode.Contextual,
      requires = Vector(enumAttr("nail_service_type", "refill")),
    ),
    rule("r098", "with design")(
      bool("with_design", true),
    )(using
      mode = IntentRuleMode.Contextual,
      requires = Vector(nailServiceFamily),
    ),
    rule("r099", "3d volume lash", "3d volume lashes")(
      service("lashes"),
      enumAttr("lash_service_type", "extension"),
      enumAttr("lash_volume", "volume3_d"),
    ),
    rule("r100", "permanent eyeliner")(
      service("pmu"),
      enumAttr("pmu_area", "eyeliner"),
    ),
    rule("r101", "gel")(
      enumAttr("nail_coating_type", "gel"),
    )(using
      mode = IntentRuleMode.Contextual,
      requires = Vector(enumAttr("nail_service_type", "removal")),
    ),
    rule("r102", "remove gel nail modeling")(
      service("nail_modeling"),
      enumAttr("nail_service_type", "removal"),
      bool("with_removal", true),
      enumAttr("nail_coating_type", "gel"),
    ),
  )

  val validation: Either[NonEmptyErrors[BeautyIntentVocabularyError], BeautyQIntentVocabulary] = validate(sourceRules)

  val value: BeautyQIntentVocabulary = validation match {
    case Right(vocabulary) => vocabulary
    case Left(errors)      => throw new IllegalStateException(errors.toVector.mkString("BeautyQ Gen2 vocabulary is invalid: ", "; ", ""))
  }

  val rules: Vector[BeautyIntentRule] = value.rules

  // Validation order is fixed: (1) duplicate rule IDs; (2) each rule in declaration order - ID, aliases,
  // noise invariants, action values, alternative ("Any") collection invariants, label invariants,
  // unresolved requires, unresolved excludes; (3) cross-rule normalized independent-alias ambiguity.
  def validate(candidate: Vector[BeautyIntentRule]): Either[NonEmptyErrors[BeautyIntentVocabularyError], BeautyQIntentVocabulary] = {
    val duplicateIds = candidate.groupBy(_.id).collect { case (id, values) if values.size > 1 => BeautyIntentVocabularyError.DuplicateRuleId(id) }.toVector.sortBy(_.id.value)

    // The pool of actions the vocabulary can ever produce, used to prove every requires/excludes
    // reference is actually resolvable - independent of whether some other rule also has an error.
    val producedActions = candidate.flatMap(rule => rule.hardActions ++ rule.semanticActions)

    val ruleErrors = candidate.flatMap { rule =>
      val idErrors = if (rule.id.value.trim.isEmpty) Vector(BeautyIntentVocabularyError.BlankRuleId(rule.id)) else Vector.empty

      // Declaration hygiene stays lossless: two legitimate public aliases may collapse to the same
      // matching key after finite carrier removal. Cross-rule ambiguity below uses that matching key.
      val normalizedAliases = rule.aliases.map(BeautyQIntentTextGen2.normalize)
      val aliasErrors =
        (if (rule.aliases.isEmpty) Vector(BeautyIntentVocabularyError.EmptyAliases(rule.id)) else Vector.empty) ++
          rule.aliases.zip(normalizedAliases).flatMap { case (raw, normalized) => if (normalized.isEmpty) Vector(BeautyIntentVocabularyError.BlankAlias(rule.id, raw)) else Vector.empty } ++
          normalizedAliases.groupBy(identity).collect { case (alias, values) if values.size > 1 => BeautyIntentVocabularyError.DuplicateAlias(rule.id, alias) }.toVector

      val noiseErrors =
        (if (rule.noise && (rule.hardActions.nonEmpty || rule.semanticActions.nonEmpty)) Vector(BeautyIntentVocabularyError.NoiseHasActions(rule.id)) else Vector.empty) ++
          (if (rule.noise && rule.canonicalSemanticLabel.isDefined) Vector(BeautyIntentVocabularyError.NoiseHasCanonicalLabel(rule.id)) else Vector.empty) ++
          (if (rule.hardActions.contains(BeautyIntentAction.NearUser)) Vector(BeautyIntentVocabularyError.NearUserHasHardAction(rule.id)) else Vector.empty)

      val actions = rule.hardActions ++ rule.semanticActions ++ rule.requires ++ rule.excludes
      val actionValueErrors = actions.flatMap(validateActionValue)
      val alternativeCollectionErrors = actions.flatMap(action => validateAlternativeCollection(rule.id, action))

      val labelErrors = rule.canonicalSemanticLabel.toVector.flatMap { label =>
        if (label.stableKey.trim.isEmpty || label.text.trim.isEmpty) Vector(BeautyIntentVocabularyError.InvalidCanonicalLabel(rule.id, label)) else Vector.empty
      }

      val unresolvedRequiresErrors =
        rule.requires.filterNot(required => producedActions.exists(produced => BeautyIntentAction.covers(produced, required))).map(action => BeautyIntentVocabularyError.UnresolvedRequiresAction(rule.id, action))
      val unresolvedExcludesErrors =
        rule.excludes.filterNot(excluded => producedActions.exists(produced => BeautyIntentAction.covers(produced, excluded))).map(action => BeautyIntentVocabularyError.UnresolvedExcludesAction(rule.id, action))

      idErrors ++ aliasErrors ++ noiseErrors ++ actionValueErrors ++ alternativeCollectionErrors ++ labelErrors ++ unresolvedRequiresErrors ++ unresolvedExcludesErrors
    }

    val independentAliases = candidate.filter(_.mode == IntentRuleMode.Independent).flatMap(rule => rule.aliases.map(BeautyQIntentTextGen2.intentMatchingKey).map(_ -> rule.id))
    val ambiguous = independentAliases.groupBy(_._1).collect {
      case (alias, values) if values.map(_._2).distinct.size > 1 =>
        values match {
          case first +: rest => rest.map(_._2).find(_ != first._2) match {
            case Some(second) => BeautyIntentVocabularyError.AmbiguousIndependentAlias(alias, first._2, second)
            case None         => BeautyIntentVocabularyError.AmbiguousIndependentAlias(alias, first._2, first._2)
          }
          case _ => BeautyIntentVocabularyError.AmbiguousIndependentAlias(alias, IntentRuleId(""), IntentRuleId(""))
        }
    }.toVector.sortBy(_.alias)
    val errors = duplicateIds ++ ruleErrors ++ ambiguous
    NonEmptyErrors.fromVector(errors) match {
      case Some(value) => Left(value)
      case None        => Right(new BeautyQIntentVocabulary(candidate))
    }
  }

  // "Action values": is each individual code/value this action carries valid against the domain
  // vocabulary (ServiceCode/CategoryCode grammar, known attribute code, attribute's own codec)? Separate
  // from validateAlternativeCollection, which checks the "Any" collection's own shape instead.
  private def validateActionValue(action: BeautyIntentAction): Vector[BeautyIntentVocabularyError] = action match {
    case BeautyIntentAction.Service(code)     => if (ServiceCode.fromString(code.value).isRight) Vector.empty else Vector(BeautyIntentVocabularyError.InvalidServiceCode(code.value))
    case BeautyIntentAction.ServiceAny(codes) => codes.flatMap(code => if (ServiceCode.fromString(code.value).isRight) Vector.empty else Vector(BeautyIntentVocabularyError.InvalidServiceCode(code.value)))
    case BeautyIntentAction.Category(code)    => if (CategoryCode.fromString(code.value).isRight) Vector.empty else Vector(BeautyIntentVocabularyError.InvalidCategoryCode(code.value))
    case BeautyIntentAction.EnumAttribute(code, value) =>
      Fields.enumAttributesByCode.get(code) match {
        case None => Vector(BeautyIntentVocabularyError.UnknownAttribute(code))
        case Some(field) => if (field.codec.decodeCanonical(value).isRight) Vector.empty else Vector(BeautyIntentVocabularyError.InvalidAttributeValue(code, value))
      }
    case BeautyIntentAction.EnumAttributeAny(code, values) =>
      Fields.enumAttributesByCode.get(code) match {
        case None => Vector(BeautyIntentVocabularyError.UnknownAttribute(code))
        case Some(field) => values.flatMap(value => if (field.codec.decodeCanonical(value).isRight) Vector.empty else Vector(BeautyIntentVocabularyError.InvalidAttributeValue(code, value)))
      }
    case BeautyIntentAction.BooleanAttribute(code, value) =>
      Fields.booleanAttributesByCode.get(code) match {
        case None => Vector(BeautyIntentVocabularyError.UnknownAttribute(code))
        case Some(field) => if (field.codec.decodeCanonical(value.toString).isRight) Vector.empty else Vector(BeautyIntentVocabularyError.InvalidAttributeValue(code, value.toString))
      }
    case BeautyIntentAction.IntAttribute(code, value) =>
      Fields.intAttributesByCode.get(code) match {
        case None => Vector(BeautyIntentVocabularyError.UnknownAttribute(code))
        case Some(field) => if (field.codec.decodeCanonical(value.toString).isRight) Vector.empty else Vector(BeautyIntentVocabularyError.InvalidAttributeValue(code, value.toString))
      }
    case BeautyIntentAction.NearUser => Vector.empty
  }

  // "Alternative collections": is the ServiceAny/EnumAttributeAny collection itself well-formed
  // (non-empty, no duplicate member)? Every other action carries no such collection.
  private def validateAlternativeCollection(id: IntentRuleId, action: BeautyIntentAction): Vector[BeautyIntentVocabularyError] = action match {
    case BeautyIntentAction.ServiceAny(codes) =>
      (if (codes.isEmpty) Vector(BeautyIntentVocabularyError.EmptyServiceAny(id)) else Vector.empty) ++
        codes.groupBy(identity).collect { case (code, occurrences) if occurrences.size > 1 => code }.toVector.sortBy(_.value).map(code => BeautyIntentVocabularyError.DuplicateServiceAnyCode(id, code))
    case BeautyIntentAction.EnumAttributeAny(code, values) =>
      (if (values.isEmpty) Vector(BeautyIntentVocabularyError.EmptyEnumAttributeAny(id, code)) else Vector.empty) ++
        values.groupBy(identity).collect { case (value, occurrences) if occurrences.size > 1 => value }.toVector.sorted.map(value => BeautyIntentVocabularyError.DuplicateEnumAttributeAnyValue(id, code, value))
    case _ => Vector.empty
  }
}
