package leaderboard.search.beautyq.gen2.contract

import leaderboard.model.{CategoryCode, ServiceCode}
import org.scalatest.wordspec.AnyWordSpec

/** Contractual + Blackbox + Atomic drift detector for the BeautyQ inbound vocabulary. */
final class BeautyQInputVocabularyLedgerSpec extends AnyWordSpec {

  private def code(value: String): ServiceCode = ServiceCode.fromString(value).getOrElse(fail(s"invalid ServiceCode fixture: $value"))
  private def service(value: String): BeautyIntentAction.Service = BeautyIntentAction.Service(code(value))
  private def category(value: String): CategoryCode = CategoryCode.fromString(value).getOrElse(fail(s"invalid CategoryCode fixture: $value"))

  "BeautyQ Gen2 inbound vocabulary" should {
    "pin the explicit public names and operator order" in {
      assert(BeautyQPublicFilterRegistry.fields.take(5).map(_.name.value) == Vector("service", "category", "price", "durationMinutes", "distanceMeters"))
      assert(BeautyQPublicFilterRegistry.fields(0).acceptedOperators == Vector(PublicOperator.Equal, PublicOperator.In))
      assert(BeautyQPublicFilterRegistry.fields(1).acceptedOperators == Vector(PublicOperator.Equal, PublicOperator.In))
      assert(BeautyQPublicFilterRegistry.fields(2).acceptedOperators == Vector(PublicOperator.GreaterThan, PublicOperator.GreaterThanOrEqual, PublicOperator.LessThan, PublicOperator.LessThanOrEqual, PublicOperator.Between))
      assert(BeautyQPublicFilterRegistry.fields(3).acceptedOperators == Vector(PublicOperator.GreaterThan, PublicOperator.GreaterThanOrEqual, PublicOperator.LessThan, PublicOperator.LessThanOrEqual, PublicOperator.Between))
      assert(BeautyQPublicFilterRegistry.fields(4).acceptedOperators == Vector(PublicOperator.WithinDistance))
      assert(BeautyQPublicSortRegistry.names.map(_.value) == Vector("price", "durationMinutes", "distanceMeters"))
      assert(BeautyQSearchPlanPolicy.facetRegistry.ids.map(_.value) == Vector("service", "category", "price", "durationMinutes"))
    }

    "pin dynamic naming prefixes and stable rule IDs" in {
      val dynamicNames = BeautyQPublicFilterRegistry.fields.drop(5).map(_.name.value)
      assert(dynamicNames.forall(name => name.startsWith("attribute.int.") || name.startsWith("attribute.decimal.") || name.startsWith("attribute.enum.") || name.startsWith("attribute.boolean.")))
      assert(BeautyQIntentVocabulary.rules.map(_.id.value) == (1 to 92).map(index => f"r$index%03d").toVector)
      assert(BeautyQIntentVocabulary.rules.flatMap(_.aliases).contains("маникюр"))
      assert(BeautyQIntentVocabulary.rules.flatMap(_.aliases).contains("салон красоты"))
    }
  }

  "BeautyQ intent matching text" should {
    "canonicalize reusable Russian inflections and AquaFacial transliterations without changing public normalization" in {
      assert(BeautyQIntentTextGen2.normalize("Нужно ламинирование бровей вместе с окрашиванием") == "нужно ламинирование бровей вместе с окрашиванием")
      assert(
        BeautyQIntentTextGen2.tokenizeForIntentMatching("Нужно ламинирование бровей вместе с окрашиванием") ==
          Vector("ламинирование", "брови", "с", "окрашивание")
      )
      assert(BeautyQIntentTextGen2.tokenizeForIntentMatching("процедуру аква-фэйшл для лица") == Vector("aquafacial", "лица"))
      assert(BeautyQIntentTextGen2.tokenizeForIntentMatching("Нужна процедура аквафейшл для лица") == Vector("aquafacial", "лица"))
    }

    "remove only finite request-carrier tokens while retaining semantic action words" in {
      assert(
        BeautyQIntentTextGen2.tokenizeForIntentMatching("Хочу аккуратно снять наращённые ресницы") ==
          Vector("снять", "наращенные", "ресницы")
      )
      assert(
        BeautyQIntentTextGen2.tokenizeForIntentMatching("Ищу педикюр без какого-либо покрытия") ==
          Vector("педикюр", "без", "покрытия")
      )
      assert(
        BeautyQIntentTextGen2.tokenizeForIntentMatching("Fußnägel mit Gel-Farbe behandeln") ==
          Vector("fußnägel", "mit", "gel", "farbe", "behandeln")
      )
      assert(
        BeautyQIntentTextGen2.tokenizeForIntentMatching("künstliche Nägel aus Acryl verlängern") ==
          Vector("künstliche", "nägel", "aus", "acrylic", "verlängern")
      )
    }

    "leave multi-token service and attribute meaning to the typed vocabulary" in {
      assert(BeautyQIntentTextGen2.tokenizeForIntentMatching("уход для рук с гель-лаком") == Vector("уход", "рук", "с", "гель", "лаком"))
      assert(BeautyQIntentTextGen2.tokenizeForIntentMatching("hand nail care with ordinary polish") == Vector("hand", "nail", "care", "with", "ordinary", "polish"))
      assert(BeautyQIntentTextGen2.tokenizeForIntentMatching("gesichtsreinigung behandlung") == Vector("gesichtsreinigung", "behandlung"))
      assert(BeautyQIntentTextGen2.tokenizeForIntentMatching("перманент губ с коррекцией") == Vector("перманент", "губ", "с", "коррекция"))
      assert(BeautyQIntentTextGen2.tokenizeForIntentMatching("лазерное удаление волос в зоне подмышек") == Vector("лазер", "удаление", "волос", "в", "зоне", "подмышки"))
      assert(BeautyQIntentTextGen2.tokenizeForIntentMatching("без цветного покрытия") == Vector("без", "цветного", "покрытия"))
    }

    "canonicalize mechanical matching tokens idempotently" in {
      val fixtures = Vector(
        "Нужно ламинирование бровей вместе с окрашиванием",
        "процедуру аква-фэйшл для лица",
        "künstliche Nägel aus Acryl verlängern",
        "лазерное удаление волос в зоне подмышек",
        "lash lifting mit färben",
      )
      fixtures.foreach { fixture =>
        val once = BeautyQIntentTextGen2.tokenizeForIntentMatching(fixture)
        assert(BeautyQIntentTextGen2.tokenizeForIntentMatching(once.mkString(" ")) == once)
      }
    }
  }

  "BeautyQIntentTextGen2.normalize" should {
    "lowercase mixed case through Locale.ROOT" in {
      assert(BeautyQIntentTextGen2.normalize("MaNiCuRe") == "manicure")
    }

    "collapse repeated whitespace" in {
      assert(BeautyQIntentTextGen2.normalize("gel   polish") == "gel polish")
    }

    "normalize Cyrillic ё to е" in {
      assert(BeautyQIntentTextGen2.normalize("ёлка") == "елка")
    }

    "separate on hyphen and em/en dash" in {
      assert(BeautyQIntentTextGen2.normalize("mini-group") == "mini group")
      assert(BeautyQIntentTextGen2.normalize("a—b") == "a b")
      assert(BeautyQIntentTextGen2.normalize("a–b") == "a b")
    }

    "separate on slash" in {
      assert(BeautyQIntentTextGen2.normalize("face/gesicht") == "face gesicht")
    }

    "separate on comma and period" in {
      assert(BeautyQIntentTextGen2.normalize("nails, manicure.") == "nails manicure")
    }

    "separate on brackets" in {
      assert(BeautyQIntentTextGen2.normalize("gel (polish) [nails] {extra}") == "gel polish nails extra")
    }

    "separate on apostrophe" in {
      assert(BeautyQIntentTextGen2.normalize("women's manicure") == "women s manicure")
    }

    "normalize multilingual text consistently" in {
      assert(BeautyQIntentTextGen2.normalize("Fußpflege") == "fußpflege")
      assert(BeautyQIntentTextGen2.normalize("Ламинирование") == "ламинирование")
      assert(BeautyQIntentTextGen2.normalize("PERMANENT MAKE-UP") == "permanent make up")
    }
  }

  "BeautyQIntentVocabulary.validate ambiguity detection" should {
    "reject two independent rules whose raw aliases differ but normalize to the same phrase" in {
      assert(BeautyQIntentTextGen2.normalize("gel-polish") == BeautyQIntentTextGen2.normalize("gel  polish"))
      val ruleA = BeautyIntentRule(IntentRuleId("test-a"), Vector("gel-polish"), IntentRuleMode.Independent, Vector.empty, Vector.empty, Vector.empty, Vector.empty, noise = false)
      val ruleB = BeautyIntentRule(IntentRuleId("test-b"), Vector("gel  polish"), IntentRuleMode.Independent, Vector.empty, Vector.empty, Vector.empty, Vector.empty, noise = false)
      BeautyQIntentVocabulary.validate(Vector(ruleA, ruleB)) match {
        case Left(errors) =>
          assert(errors.toVector.contains(BeautyIntentVocabularyError.AmbiguousIndependentAlias("gel polish", IntentRuleId("test-a"), IntentRuleId("test-b"))))
        case Right(value) => fail(s"expected ambiguity rejection, got: $value")
      }
    }
  }

  "BeautyIntentAction.covers" should {
    "match Service against Service by exact code equality" in {
      assert(BeautyIntentAction.covers(service("manicure"), service("manicure")))
      assert(!BeautyIntentAction.covers(service("manicure"), service("pedicure")))
    }

    "let a produced ServiceAny cover a required Service when the any-set contains it" in {
      val produced = BeautyIntentAction.ServiceAny(Vector(code("manicure"), code("pedicure")))
      assert(BeautyIntentAction.covers(produced, service("manicure")))
      assert(!BeautyIntentAction.covers(produced, service("lashes")))
    }

    "let a produced Service cover a required ServiceAny when the any-set contains it" in {
      val required = BeautyIntentAction.ServiceAny(Vector(code("manicure"), code("pedicure")))
      assert(BeautyIntentAction.covers(service("manicure"), required))
      assert(!BeautyIntentAction.covers(service("lashes"), required))
    }

    "cover a required ServiceAny from a produced ServiceAny only when every required code is in the produced set (accepted any-of semantics)" in {
      val produced = BeautyIntentAction.ServiceAny(Vector(code("manicure"), code("pedicure"), code("nail_modeling")))
      assert(BeautyIntentAction.covers(produced, BeautyIntentAction.ServiceAny(Vector(code("manicure"), code("pedicure")))))
      assert(BeautyIntentAction.covers(produced, BeautyIntentAction.ServiceAny(Vector(code("manicure")))))
      assert(!BeautyIntentAction.covers(produced, BeautyIntentAction.ServiceAny(Vector(code("manicure"), code("lashes")))))
    }

    "match Category by exact code equality" in {
      assert(BeautyIntentAction.covers(BeautyIntentAction.Category(category("nails")), BeautyIntentAction.Category(category("nails"))))
      assert(!BeautyIntentAction.covers(BeautyIntentAction.Category(category("nails")), BeautyIntentAction.Category(category("lashes_brows_pmu"))))
    }

    "match EnumAttribute by exact code and value" in {
      assert(BeautyIntentAction.covers(BeautyIntentAction.EnumAttribute("nail_coating_type", "gel"), BeautyIntentAction.EnumAttribute("nail_coating_type", "gel")))
      assert(!BeautyIntentAction.covers(BeautyIntentAction.EnumAttribute("nail_coating_type", "gel"), BeautyIntentAction.EnumAttribute("nail_coating_type", "shellac")))
    }

    "let a produced EnumAttributeAny cover a required EnumAttribute when the any-set contains it" in {
      val produced = BeautyIntentAction.EnumAttributeAny("brow_service_type", Vector("shaping", "tinting"))
      assert(BeautyIntentAction.covers(produced, BeautyIntentAction.EnumAttribute("brow_service_type", "shaping")))
      assert(!BeautyIntentAction.covers(produced, BeautyIntentAction.EnumAttribute("brow_service_type", "lamination")))
    }

    "let a produced EnumAttribute cover a required EnumAttributeAny when the any-set contains it" in {
      val required = BeautyIntentAction.EnumAttributeAny("brow_service_type", Vector("shaping", "tinting"))
      assert(BeautyIntentAction.covers(BeautyIntentAction.EnumAttribute("brow_service_type", "shaping"), required))
      assert(!BeautyIntentAction.covers(BeautyIntentAction.EnumAttribute("brow_service_type", "lamination"), required))
    }

    "cover a required EnumAttributeAny from a produced EnumAttributeAny only when every required value is in the produced set" in {
      val produced = BeautyIntentAction.EnumAttributeAny("brow_service_type", Vector("shaping", "tinting", "lamination"))
      assert(BeautyIntentAction.covers(produced, BeautyIntentAction.EnumAttributeAny("brow_service_type", Vector("shaping", "tinting"))))
      assert(!BeautyIntentAction.covers(produced, BeautyIntentAction.EnumAttributeAny("brow_service_type", Vector("shaping", "henna"))))
    }

    "match BooleanAttribute, IntAttribute and NearUser by exact match only" in {
      assert(BeautyIntentAction.covers(BeautyIntentAction.BooleanAttribute("with_removal", true), BeautyIntentAction.BooleanAttribute("with_removal", true)))
      assert(!BeautyIntentAction.covers(BeautyIntentAction.BooleanAttribute("with_removal", true), BeautyIntentAction.BooleanAttribute("with_removal", false)))
      assert(BeautyIntentAction.covers(BeautyIntentAction.IntAttribute("session_count", 6), BeautyIntentAction.IntAttribute("session_count", 6)))
      assert(!BeautyIntentAction.covers(BeautyIntentAction.IntAttribute("session_count", 6), BeautyIntentAction.IntAttribute("session_count", 3)))
      assert(BeautyIntentAction.covers(BeautyIntentAction.NearUser, BeautyIntentAction.NearUser))
    }

    "never cover across mismatched action kinds" in {
      assert(!BeautyIntentAction.covers(service("manicure"), BeautyIntentAction.Category(category("nails"))))
      assert(!BeautyIntentAction.covers(BeautyIntentAction.EnumAttribute("nail_coating_type", "gel"), BeautyIntentAction.BooleanAttribute("with_removal", true)))
    }

    // r032/r033's restored Gen1 gate is the exact typed attribute value enum(nail_service_type=extension),
    // never service(nail_modeling)/category(nails)/a semantic nail_modeling. Only enum(nail_service_type=
    // extension) itself covers it - sibling values (refill/removal) and a same-domain service action do
    // not - so the extension gate cannot be silently satisfied by an unrelated nails match.
    "gate exactly on enum(nail_service_type=extension) for the restored r032/r033 requirement" in {
      val extensionRequirement = BeautyIntentAction.EnumAttribute("nail_service_type", "extension")
      assert(BeautyIntentAction.covers(BeautyIntentAction.EnumAttribute("nail_service_type", "extension"), extensionRequirement))
      assert(!BeautyIntentAction.covers(BeautyIntentAction.EnumAttribute("nail_service_type", "refill"), extensionRequirement))
      assert(!BeautyIntentAction.covers(BeautyIntentAction.EnumAttribute("nail_service_type", "removal"), extensionRequirement))
      assert(!BeautyIntentAction.covers(service("nail_modeling"), extensionRequirement))
      assert(!BeautyIntentAction.covers(BeautyIntentAction.Category(category("nails")), extensionRequirement))
    }
  }

  "BeautyQIntentVocabulary.validate completeness" should {
    "reject a blank rule ID" in {
      val rule = BeautyIntentRule(IntentRuleId("  "), Vector("alias"), IntentRuleMode.Independent, Vector.empty, Vector.empty, Vector.empty, Vector.empty, noise = false)
      BeautyQIntentVocabulary.validate(Vector(rule)) match {
        case Left(errors) => assert(errors.toVector.contains(BeautyIntentVocabularyError.BlankRuleId(IntentRuleId("  "))))
        case Right(value) => fail(s"expected rejection, got: $value")
      }
    }

    "reject a noise rule that carries a canonical semantic label" in {
      val label = CanonicalSemanticLabel.from("key", "text").getOrElse(fail("expected a valid label fixture"))
      val rule =
        BeautyIntentRule(IntentRuleId("noise-with-label"), Vector("alias"), IntentRuleMode.Contextual, Vector.empty, Vector.empty, Vector.empty, Vector.empty, noise = true, canonicalSemanticLabel = Some(label))
      BeautyQIntentVocabulary.validate(Vector(rule)) match {
        case Left(errors) => assert(errors.toVector.contains(BeautyIntentVocabularyError.NoiseHasCanonicalLabel(IntentRuleId("noise-with-label"))))
        case Right(value) => fail(s"expected rejection, got: $value")
      }
    }

    "reject an empty ServiceAny" in {
      val rule = BeautyIntentRule(IntentRuleId("empty-service-any"), Vector("alias"), IntentRuleMode.Independent, Vector(BeautyIntentAction.ServiceAny(Vector.empty)), Vector.empty, Vector.empty, Vector.empty, noise = false)
      BeautyQIntentVocabulary.validate(Vector(rule)) match {
        case Left(errors) => assert(errors.toVector.contains(BeautyIntentVocabularyError.EmptyServiceAny(IntentRuleId("empty-service-any"))))
        case Right(value) => fail(s"expected rejection, got: $value")
      }
    }

    "reject a duplicate ServiceAny code" in {
      val rule =
        BeautyIntentRule(
          IntentRuleId("dup-service-any"),
          Vector("alias"),
          IntentRuleMode.Independent,
          Vector(BeautyIntentAction.ServiceAny(Vector(code("manicure"), code("manicure")))),
          Vector.empty,
          Vector.empty,
          Vector.empty,
          noise = false,
        )
      BeautyQIntentVocabulary.validate(Vector(rule)) match {
        case Left(errors) => assert(errors.toVector.contains(BeautyIntentVocabularyError.DuplicateServiceAnyCode(IntentRuleId("dup-service-any"), code("manicure"))))
        case Right(value) => fail(s"expected rejection, got: $value")
      }
    }

    "reject an empty EnumAttributeAny" in {
      val rule =
        BeautyIntentRule(
          IntentRuleId("empty-enum-any"),
          Vector("alias"),
          IntentRuleMode.Independent,
          Vector(BeautyIntentAction.EnumAttributeAny("nail_coating_type", Vector.empty)),
          Vector.empty,
          Vector.empty,
          Vector.empty,
          noise = false,
        )
      BeautyQIntentVocabulary.validate(Vector(rule)) match {
        case Left(errors) => assert(errors.toVector.contains(BeautyIntentVocabularyError.EmptyEnumAttributeAny(IntentRuleId("empty-enum-any"), "nail_coating_type")))
        case Right(value) => fail(s"expected rejection, got: $value")
      }
    }

    "reject a duplicate EnumAttributeAny value" in {
      val rule =
        BeautyIntentRule(
          IntentRuleId("dup-enum-any"),
          Vector("alias"),
          IntentRuleMode.Independent,
          Vector(BeautyIntentAction.EnumAttributeAny("nail_coating_type", Vector("gel", "gel"))),
          Vector.empty,
          Vector.empty,
          Vector.empty,
          noise = false,
        )
      BeautyQIntentVocabulary.validate(Vector(rule)) match {
        case Left(errors) => assert(errors.toVector.contains(BeautyIntentVocabularyError.DuplicateEnumAttributeAnyValue(IntentRuleId("dup-enum-any"), "nail_coating_type", "gel")))
        case Right(value) => fail(s"expected rejection, got: $value")
      }
    }

    "reject an unresolved requires action" in {
      val rule = BeautyIntentRule(IntentRuleId("unresolved-requires"), Vector("alias"), IntentRuleMode.Contextual, Vector.empty, Vector.empty, Vector(service("manicure")), Vector.empty, noise = false)
      BeautyQIntentVocabulary.validate(Vector(rule)) match {
        case Left(errors) => assert(errors.toVector.contains(BeautyIntentVocabularyError.UnresolvedRequiresAction(IntentRuleId("unresolved-requires"), service("manicure"))))
        case Right(value) => fail(s"expected rejection, got: $value")
      }
    }

    "reject an unresolved excludes action" in {
      val rule = BeautyIntentRule(IntentRuleId("unresolved-excludes"), Vector("alias"), IntentRuleMode.Contextual, Vector.empty, Vector.empty, Vector.empty, Vector(service("manicure")), noise = false)
      BeautyQIntentVocabulary.validate(Vector(rule)) match {
        case Left(errors) => assert(errors.toVector.contains(BeautyIntentVocabularyError.UnresolvedExcludesAction(IntentRuleId("unresolved-excludes"), service("manicure"))))
        case Right(value) => fail(s"expected rejection, got: $value")
      }
    }

    "resolve a requires action when some other candidate rule produces a covering action" in {
      val provider = BeautyIntentRule(IntentRuleId("provider"), Vector("alias-a"), IntentRuleMode.Independent, Vector(service("manicure")), Vector.empty, Vector.empty, Vector.empty, noise = false)
      val consumer = BeautyIntentRule(IntentRuleId("consumer"), Vector("alias-b"), IntentRuleMode.Contextual, Vector.empty, Vector.empty, Vector(service("manicure")), Vector.empty, noise = false)
      assert(BeautyQIntentVocabulary.validate(Vector(provider, consumer)).isRight)
    }

    "report duplicate rule IDs before any per-rule content errors" in {
      val ruleA = BeautyIntentRule(IntentRuleId("dup"), Vector("alias-a"), IntentRuleMode.Independent, Vector.empty, Vector.empty, Vector.empty, Vector.empty, noise = false)
      val ruleB = BeautyIntentRule(IntentRuleId("dup"), Vector(""), IntentRuleMode.Independent, Vector.empty, Vector.empty, Vector.empty, Vector.empty, noise = false)
      BeautyQIntentVocabulary.validate(Vector(ruleA, ruleB)) match {
        case Left(errors) =>
          errors.toVector.headOption match {
            case Some(BeautyIntentVocabularyError.DuplicateRuleId(IntentRuleId("dup"))) => ()
            case other                                                                   => fail(s"expected DuplicateRuleId first, got: $other")
          }
        case Right(value) => fail(s"expected rejection, got: $value")
      }
    }

    "accumulate one rule's violations in exactly the declared sub-order" in {
      val rule =
        BeautyIntentRule(
          id = IntentRuleId(""),
          aliases = Vector(""),
          mode = IntentRuleMode.Contextual,
          hardActions = Vector(BeautyIntentAction.EnumAttribute("not_a_real_code", "x"), BeautyIntentAction.ServiceAny(Vector.empty)),
          semanticActions = Vector.empty,
          requires = Vector(service("manicure")),
          excludes = Vector(service("pedicure")),
          noise = true,
        )
      BeautyQIntentVocabulary.validate(Vector(rule)) match {
        case Left(errors) =>
          assert(
            errors.toVector ==
              Vector(
                BeautyIntentVocabularyError.BlankRuleId(IntentRuleId("")),
                BeautyIntentVocabularyError.BlankAlias(IntentRuleId(""), ""),
                BeautyIntentVocabularyError.NoiseHasActions(IntentRuleId("")),
                BeautyIntentVocabularyError.UnknownAttribute("not_a_real_code"),
                BeautyIntentVocabularyError.EmptyServiceAny(IntentRuleId("")),
                BeautyIntentVocabularyError.UnresolvedRequiresAction(IntentRuleId(""), service("manicure")),
                BeautyIntentVocabularyError.UnresolvedExcludesAction(IntentRuleId(""), service("pedicure")),
              )
          )
        case Right(value) => fail(s"expected rejection, got: $value")
      }
    }
  }
}
