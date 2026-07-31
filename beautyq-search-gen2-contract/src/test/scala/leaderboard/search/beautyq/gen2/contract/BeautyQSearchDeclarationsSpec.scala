package leaderboard.search.beautyq.gen2.contract

import leaderboard.model.*
import leaderboard.model.Category.CategoryId
import leaderboard.search.gen2.contract.*
import org.scalatest.wordspec.AnyWordSpec

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import java.util.UUID
import scala.annotation.tailrec
import scala.jdk.CollectionConverters.*
import scala.util.Using

final class BeautyQSearchDeclarationsSpec extends AnyWordSpec {
  import BeautyQSearchDeclarations.*
  import BeautyQSearchDeclarations.variants.Fields

  private def attributePrefix(definition: AttributeDefinition[?]): String =
    definition match {
      case _: IntAttributeDefinition        => "intAttributes"
      case _: BigDecimalAttributeDefinition => "bigDecimalAttributes"
      case _: EnumAttributeDefinition[?]    => "enumAttributes"
      case _: BooleanAttributeDefinition    => "booleanAttributes"
    }

  private val expectedCatalogStepSummaries: Vector[String] = Vector(
    "root:category:tree:parentId",
    "many:category->service:categoryId",
    "value:service->serviceVariantSchema:serviceId",
    "root:master:all",
    "many:master->masterLocation:masterId",
    "many:master->masterServiceOffer:masterId",
    "many:masterServiceOffer->masterServiceOfferVariant:masterServiceOfferId",
  )

  // The single golden owner for the entire 44-field matrix. Every other structural fact (FieldPath,
  // FieldSemantic, the exact ID sequence used by the renderer test) is derived from this one table -
  // per the accepted BeautyQ rule that FieldId == FieldPath == FieldSemantic - rather than repeated as
  // a second independently maintained list.
  private final case class ExpectedField(
    id: String,
    kind: SearchFieldKind,
    valueType: String,
    presence: FieldPresence,
    capabilities: FieldCapabilities,
  )

  private val expectedStaticFields: Vector[ExpectedField] = Vector(
    ExpectedField("variantId", SearchFieldKind.Keyword, "MasterServiceOfferVariantId", FieldPresence.Required, FieldCapabilities(sortModes = Set(SortMode.Value), payloadEligible = true)),
    ExpectedField("masterServiceOfferId", SearchFieldKind.Keyword, "MasterServiceOfferId", FieldPresence.Required, FieldCapabilities()),
    ExpectedField("masterLocationId", SearchFieldKind.Keyword, "MasterLocationId", FieldPresence.Required, FieldCapabilities(groupModes = Set(GroupMode.Terms))),
    ExpectedField("masterId", SearchFieldKind.Keyword, "MasterId", FieldPresence.Required, FieldCapabilities()),
    ExpectedField("serviceId", SearchFieldKind.Keyword, "ServiceId", FieldPresence.Required, FieldCapabilities(groupModes = Set(GroupMode.Terms))),
    ExpectedField(
      "serviceCode",
      SearchFieldKind.Keyword,
      "ServiceCode",
      FieldPresence.Required,
      FieldCapabilities(filterOperators = Set(FilterOperator.Equal, FilterOperator.In), facetModes = Set(FacetMode.Terms), groupModes = Set(GroupMode.Terms), payloadEligible = true),
    ),
    ExpectedField("serviceName", SearchFieldKind.Keyword, "string", FieldPresence.Required, FieldCapabilities()),
    ExpectedField("categoryId", SearchFieldKind.Keyword, "CategoryId", FieldPresence.Required, FieldCapabilities()),
    ExpectedField(
      "categoryCode",
      SearchFieldKind.Keyword,
      "CategoryCode",
      FieldPresence.Required,
      FieldCapabilities(filterOperators = Set(FilterOperator.Equal, FilterOperator.In), facetModes = Set(FacetMode.Terms), payloadEligible = true),
    ),
    ExpectedField("categoryName", SearchFieldKind.Keyword, "string", FieldPresence.Required, FieldCapabilities()),
    ExpectedField("masterName", SearchFieldKind.Keyword, "string", FieldPresence.Required, FieldCapabilities()),
    ExpectedField("locationName", SearchFieldKind.Keyword, "string", FieldPresence.Required, FieldCapabilities()),
    ExpectedField("address", SearchFieldKind.Keyword, "string", FieldPresence.Required, FieldCapabilities()),
    ExpectedField("lat", SearchFieldKind.Decimal, "decimal", FieldPresence.Required, FieldCapabilities()),
    ExpectedField("lon", SearchFieldKind.Decimal, "decimal", FieldPresence.Required, FieldCapabilities()),
    ExpectedField(
      "priceFrom",
      SearchFieldKind.Decimal,
      "decimal",
      FieldPresence.Required,
      FieldCapabilities(filterOperators = Set(FilterOperator.Range), facetModes = Set(FacetMode.Range), sortModes = Set(SortMode.Value), payloadEligible = true),
    ),
    ExpectedField(
      "priceTo",
      SearchFieldKind.Decimal,
      "decimal",
      FieldPresence.Required,
      FieldCapabilities(filterOperators = Set(FilterOperator.Range), facetModes = Set(FacetMode.Range), payloadEligible = true),
    ),
    ExpectedField(
      "durationMin",
      SearchFieldKind.Integer,
      "int",
      FieldPresence.Required,
      FieldCapabilities(filterOperators = Set(FilterOperator.Range), facetModes = Set(FacetMode.Range), sortModes = Set(SortMode.Value), payloadEligible = true),
    ),
    ExpectedField(
      "location",
      SearchFieldKind.GeoPoint,
      "geo-point",
      FieldPresence.Required,
      FieldCapabilities(filterOperators = Set(FilterOperator.GeoDistance), sortModes = Set(SortMode.Distance), payloadEligible = true),
    ),
    ExpectedField("allText", SearchFieldKind.Text, "string", FieldPresence.Required, FieldCapabilities(searchable = true)),
    ExpectedField("serviceText", SearchFieldKind.Text, "string", FieldPresence.Required, FieldCapabilities(searchable = true)),
    ExpectedField("attributeText", SearchFieldKind.Text, "string", FieldPresence.Required, FieldCapabilities(searchable = true)),
    ExpectedField("providerText", SearchFieldKind.Text, "string", FieldPresence.Required, FieldCapabilities(searchable = true)),
    ExpectedField("locationText", SearchFieldKind.Text, "string", FieldPresence.Required, FieldCapabilities(searchable = true)),
  )

  private def expectedDynamicField(definition: AttributeDefinition[?], kind: SearchFieldKind, valueType: String, capabilities: FieldCapabilities): ExpectedField =
    ExpectedField(s"${attributePrefix(definition)}.${definition.code}", kind, valueType, FieldPresence.Optional, capabilities)

  private val numericDynamicCapabilities: FieldCapabilities =
    FieldCapabilities(filterOperators = Set(FilterOperator.Range), facetModes = Set(FacetMode.Range), sortModes = Set(SortMode.Value), payloadEligible = true)

  private val termsDynamicCapabilities: FieldCapabilities =
    FieldCapabilities(filterOperators = Set(FilterOperator.Equal, FilterOperator.In), facetModes = Set(FacetMode.Terms), payloadEligible = true)

  private val expectedDynamicFields: Vector[ExpectedField] =
    AttributeDefinition.intDefinitions.map(d => expectedDynamicField(d, SearchFieldKind.Integer, "int", numericDynamicCapabilities)).toVector ++
      AttributeDefinition.bigDecimalDefinitions.map(d => expectedDynamicField(d, SearchFieldKind.Decimal, "decimal", numericDynamicCapabilities)).toVector ++
      AttributeDefinition.enumDefinitions.map(d => expectedDynamicField(d, SearchFieldKind.Keyword, "string", termsDynamicCapabilities)).toVector ++
      AttributeDefinition.booleanDefinitions.map(d => expectedDynamicField(d, SearchFieldKind.Boolean, "boolean", termsDynamicCapabilities)).toVector

  private val expectedAllFields: Vector[ExpectedField] = expectedStaticFields ++ expectedDynamicFields

  // Derived, not separately hand-typed: the accepted rule is FieldId == FieldPath == FieldSemantic.
  private val expectedFieldIdSequence: Vector[String] = expectedAllFields.map(_.id)

  private val expectedStructures: Vector[SearchFieldStructure] =
    expectedAllFields.map {
      ef =>
        SearchFieldStructure(
          id = FieldId(ef.id),
          path = FieldPath(ef.id),
          kind = ef.kind,
          valueType = SearchValueTypeId(ef.valueType),
          semantic = Some(FieldSemantic(ef.id)),
          presence = ef.presence,
          capabilities = ef.capabilities,
        )
    }

  private def repoRoot: Path = {
    @tailrec
    def loop(path: Path): Path =
      if (Files.isRegularFile(path.resolve("build.sbt"))) {
        path
      } else {
        path.getParent match {
          case parent: Path => loop(parent)
          case null         => fail(s"Could not find repo root from ${Paths.get("").toAbsolutePath}; expected build.sbt")
        }
      }

    loop(Paths.get("").toAbsolutePath)
  }

  private val fixtureVariantId: MasterServiceOfferVariantId = MasterServiceOfferVariantId(UUID.fromString("00000000-0000-0000-0000-000000000001"))
  private val fixtureOfferId: MasterServiceOfferId           = MasterServiceOfferId(UUID.fromString("00000000-0000-0000-0000-000000000002"))
  private val fixtureLocationId: MasterLocationId             = MasterLocationId(UUID.fromString("00000000-0000-0000-0000-000000000003"))
  private val fixtureMasterId: MasterId                       = MasterId(UUID.fromString("00000000-0000-0000-0000-000000000004"))
  private val fixtureServiceId: ServiceId                     = ServiceId(UUID.fromString("00000000-0000-0000-0000-000000000005"))
  private val fixtureCategoryId: CategoryId                   = CategoryId(UUID.fromString("00000000-0000-0000-0000-000000000006"))
  private val fixtureServiceCode: ServiceCode                 = ServiceCode.fromString("manicure").getOrElse(fail("expected a valid ServiceCode fixture"))
  private val fixtureCategoryCode: CategoryCode               = CategoryCode.fromString("nails").getOrElse(fail("expected a valid CategoryCode fixture"))

  private val fixtureDocument: VariantSearchDocumentGen2 = VariantSearchDocumentGen2(
    variantId = fixtureVariantId,
    masterServiceOfferId = fixtureOfferId,
    masterLocationId = fixtureLocationId,
    masterId = fixtureMasterId,
    serviceId = fixtureServiceId,
    serviceCode = fixtureServiceCode,
    categoryId = fixtureCategoryId,
    categoryCode = fixtureCategoryCode,
    serviceName = "Manicure",
    categoryName = "Nails",
    masterName = "Jane Doe",
    locationName = "Downtown Studio",
    address = "1 Main St",
    location = GeoPoint(BigDecimal("40.7128"), BigDecimal("-74.0060")),
    lat = BigDecimal("40.7128"),
    lon = BigDecimal("-74.0060"),
    priceFrom = BigDecimal("20.00"),
    priceTo = BigDecimal("40.00"),
    durationMin = 45,
    enumAttributes = Map("nail_coating_type" -> "gel_polish"),
    booleanAttributes = Map("with_design" -> true),
    intAttributes = Map("session_count" -> 1),
    bigDecimalAttributes = Map("deposit_amount" -> BigDecimal("10.00")),
    allText = "manicure nails gel polish",
    serviceText = "manicure nails",
    attributeText = "gel polish",
    providerText = "jane doe downtown studio",
    locationText = "downtown studio 1 main st nails",
  )

  "BeautyQSearchDeclarations.catalog.topology" should {
    "be named beautyq" in {
      assert(catalog.topology.name == "beautyq")
    }

    "declare the exact seven-step topology in order" in {
      assert(catalog.topology.steps.map(_.summary) == expectedCatalogStepSummaries)
    }

    "render the root directly from its one structural tree" in {
      assert(renderStructure == SearchStructureRenderer.render(structure))
    }
  }

  "BeautyQSearchDeclarations root shape" should {
    "expose only the catalog and variants top-level branches" in {
      val lines               = renderStructure.linesIterator.toVector
      val topLevelBranchLines = lines.drop(1).filter(line => line.startsWith("├── ") || line.startsWith("└── "))
      assert(topLevelBranchLines == Vector("├── catalog", "└── variants"))
    }

    "render executable request, intent and plan branches without future placeholders" in {
      assert(renderStructure.contains("request"))
      assert(renderStructure.contains("intent-rules"))
      assert(renderStructure.contains("plan"))
      val forbiddenTokens = Vector("response", "backends", "quality")
      forbiddenTokens.foreach(token => assert(!renderStructure.contains(token), s"unexpected placeholder token '$token' in rendered output"))
    }
  }

  "field ownership" should {
    "make variants.identity the exact same handle as variants.Fields.variantId" in {
      assert(variants.identity eq variants.Fields.variantId)
    }

    "make variants.document.identity the exact same handle as variants.Fields.variantId" in {
      assert(variants.document.identity eq variants.Fields.variantId)
    }

    "keep every pair from document.allFields and Fields.all reference-equal" in {
      val pairs = variants.document.allFields.zip(Fields.all)
      assert(pairs.size == 44)
      pairs.foreach { case (fromDocument, fromFields) => assert(fromDocument eq fromFields) }
    }
  }

  "the static and dynamic field matrix" should {
    "declare exactly 24 static fields, 20 dynamic fields, 44 total fields, and 43 ordinary document fields" in {
      assert(Fields.staticFields.size == 24)
      assert(Fields.dynamicAttributeFields.size == 20)
      assert(Fields.all.size == 44)
      assert(variants.document.fields.size == 43)
    }

    "match the exact golden 44-field matrix - id, derived path/semantic, kind, value type, presence, and capabilities - in one comparison" in {
      val documentStructure = variants.document.structure
      assert(documentStructure.identity +: documentStructure.fields == expectedStructures)
    }

    "declare every field id exactly once (path/semantic uniqueness follows, since path == semantic == id)" in {
      val ids = Fields.all.map(_.id)
      assert(ids.distinct.size == ids.size)
    }

    "order dynamic fields exactly as AttributeDefinition.all orders their codes" in {
      val expectedOrder = AttributeDefinition.all.map(definition => s"${attributePrefix(definition)}.${definition.code}").toVector
      assert(Fields.dynamicAttributeFields.map(_.id.value) == expectedOrder)
    }
  }

  "stable identity and capability ownership" should {
    "give service public identity capabilities (filter/facet) to serviceCode, and group identity to serviceId" in {
      assert(Fields.serviceCode.capabilities.filterOperators == Set(FilterOperator.Equal, FilterOperator.In))
      assert(Fields.serviceCode.capabilities.facetModes == Set(FacetMode.Terms))
      assert(Fields.serviceName.capabilities == FieldCapabilities())
      assert(Fields.serviceId.capabilities == FieldCapabilities(groupModes = Set(GroupMode.Terms)))
    }

    "give category public identity capabilities (filter/facet) to categoryCode only, not categoryName or categoryId" in {
      assert(Fields.categoryCode.capabilities.filterOperators == Set(FilterOperator.Equal, FilterOperator.In))
      assert(Fields.categoryCode.capabilities.facetModes == Set(FacetMode.Terms))
      assert(Fields.categoryName.capabilities == FieldCapabilities())
      assert(Fields.categoryId.capabilities == FieldCapabilities())
    }

    "give group capability only to declared group keys" in {
      assert(Fields.masterLocationId.capabilities.groupModes == Set(GroupMode.Terms))
      assert(Fields.all.filterNot(_ eq Fields.masterLocationId).filterNot(_ eq Fields.serviceId).filterNot(_ eq Fields.serviceCode).forall(_.capabilities.groupModes.isEmpty))
    }

    "give the service group capability only to serviceId" in {
      assert(Fields.serviceId.capabilities.groupModes == Set(GroupMode.Terms))
    }

    "give the geo filter/sort capability only to location" in {
      assert(Fields.location.capabilities.filterOperators.contains(FilterOperator.GeoDistance))
      assert(Fields.location.capabilities.sortModes.contains(SortMode.Distance))
      val others = Fields.all.filterNot(_ eq Fields.location)
      assert(others.forall(f => !f.capabilities.filterOperators.contains(FilterOperator.GeoDistance)))
      assert(others.forall(f => !f.capabilities.sortModes.contains(SortMode.Distance)))
    }

    "give price range capability to both priceFrom and priceTo, and minimum-price sort only to priceFrom" in {
      assert(Fields.priceFrom.capabilities.filterOperators.contains(FilterOperator.Range))
      assert(Fields.priceTo.capabilities.filterOperators.contains(FilterOperator.Range))
      assert(Fields.priceFrom.capabilities.sortModes.contains(SortMode.Value))
      assert(!Fields.priceTo.capabilities.sortModes.contains(SortMode.Value))
    }

    "make exactly the expected static fields payload-eligible" in {
      val expected = Set("variantId", "serviceCode", "categoryCode", "priceFrom", "priceTo", "durationMin", "location")
      assert(Fields.staticFields.filter(_.capabilities.payloadEligible).map(_.id.value).toSet == expected)
    }

    "make every dynamic field payload-eligible, and never a presentation-only name/address/raw-coordinate field" in {
      assert(Fields.dynamicAttributeFields.forall(_.capabilities.payloadEligible))
      val presentationOnlyIds = Vector("serviceName", "categoryName", "masterName", "locationName", "address", "lat", "lon")
      presentationOnlyIds.foreach {
        id =>
          val presentationField = Fields.staticFields.find(_.id.value == id).getOrElse(fail(s"missing static field $id"))
          assert(!presentationField.capabilities.payloadEligible)
      }
    }
  }

  "field extraction" should {
    "extract a representative sample of static fields' exact values from a fixture document" in {
      assert(Fields.variantId.extract(fixtureDocument) == Some(fixtureVariantId))
      assert(Fields.masterId.extract(fixtureDocument) == Some(fixtureMasterId))
      assert(Fields.serviceCode.extract(fixtureDocument) == Some(fixtureServiceCode))
      assert(Fields.categoryCode.extract(fixtureDocument) == Some(fixtureCategoryCode))
      assert(Fields.serviceName.extract(fixtureDocument) == Some("Manicure"))
      assert(Fields.priceFrom.extract(fixtureDocument) == Some(BigDecimal("20.00")))
      assert(Fields.location.extract(fixtureDocument) == Some(GeoPoint(BigDecimal("40.7128"), BigDecimal("-74.0060"))))
      assert(Fields.allText.extract(fixtureDocument) == Some("manicure nails gel polish"))
    }

    "extract each present dynamic attribute's value, and None for a missing one" in {
      assert(Fields.intAttributesByCode.getOrElse("session_count", fail("missing session_count field")).extract(fixtureDocument) == Some(1))
      assert(Fields.decimalAttributesByCode.getOrElse("deposit_amount", fail("missing deposit_amount field")).extract(fixtureDocument) == Some(BigDecimal("10.00")))
      assert(Fields.enumAttributesByCode.getOrElse("nail_coating_type", fail("missing nail_coating_type field")).extract(fixtureDocument) == Some("gel_polish"))
      assert(Fields.booleanAttributesByCode.getOrElse("with_design", fail("missing with_design field")).extract(fixtureDocument) == Some(true))

      assert(Fields.intAttributesByCode.getOrElse("max_clients", fail("missing max_clients field")).extract(fixtureDocument) == None)
      assert(Fields.enumAttributesByCode.getOrElse("brow_service_type", fail("missing brow_service_type field")).extract(fixtureDocument) == None)
      assert(Fields.booleanAttributesByCode.getOrElse("with_removal", fail("missing with_removal field")).extract(fixtureDocument) == None)
    }
  }

  "the document declaration and structural views" should {
    "use the exact document id 'variants' and validate successfully" in {
      assert(variants.document.id == SearchDocumentId("variants"))
      assert(variants.document.allFields.size == 44)
    }

    "derive the root document section from the canonical document declaration" in {
      val documentStructure = variants.document.structure
      assert((documentStructure.identity +: documentStructure.fields).map(_.id.value) == expectedFieldIdSequence)
      assert(renderStructure == SearchStructureRenderer.render(structure))
    }

    "render the catalog, canonical document and executable inbound branches from one structure" in {
      assert(renderStructure.startsWith("BeautyQSearchDeclarations\n├── catalog"))
      assert(renderStructure.contains("document variants"))

      val lines = renderStructure.linesIterator.toVector
      val requestIndex = lines.indexWhere(_ == "    ├── request")
      val intentIndex = lines.indexWhere(_ == "    ├── intent-rules")
      val planIndex = lines.indexWhere(_ == "    └── plan")
      assert(requestIndex > 0)
      assert(intentIndex > requestIndex)
      assert(planIndex > intentIndex)

      val expectedPublicFilters = Vector(
        "service",
        "category",
        "price",
        "durationMinutes",
        "distanceMeters",
        "attribute.int.session_count",
        "attribute.int.included_corrections_count",
        "attribute.int.max_clients",
        "attribute.decimal.deposit_amount",
        "attribute.decimal.home_visit_surcharge",
        "attribute.decimal.materials_surcharge",
        "attribute.decimal.fixed_discount_amount",
        "attribute.enum.hair_removal_method",
        "attribute.enum.nail_coating_type",
        "attribute.enum.nail_service_type",
        "attribute.enum.lash_service_type",
        "attribute.enum.lash_volume",
        "attribute.enum.brow_service_type",
        "attribute.enum.pmu_area",
        "attribute.enum.facial_treatment_type",
        "attribute.enum.body_area",
        "attribute.boolean.with_removal",
        "attribute.boolean.with_design",
        "attribute.boolean.with_tinting",
        "attribute.boolean.with_correction",
      )
      val expectedRequest =
        Vector("    ├── request", "    │   ├── public-filters") ++
          expectedPublicFilters.zipWithIndex.map { case (name, index) =>
            val connector = if (index == expectedPublicFilters.size - 1) "└── " else "├── "
            s"    │   │   $connector[$index] $name"
          } ++
          Vector(
            "    │   ├── public-sorts",
            "    │   │   ├── [0] price",
            "    │   │   ├── [1] durationMinutes",
            "    │   │   └── [2] distanceMeters",
            "    │   └── public-facets",
            "    │       ├── [0] service",
            "    │       ├── [1] category",
            "    │       ├── [2] price",
            "    │       └── [3] durationMinutes",
          )
      assert(lines.slice(requestIndex, intentIndex) == expectedRequest)

      val expectedIntent = Vector("    ├── intent-rules") ++ (1 to 88).map { index =>
        val connector = if (index == 88) "└── " else "├── "
        val ruleId = f"r$index%03d"
        s"    │   $connector[${index - 1}] $ruleId"
      }
      assert(lines.slice(intentIndex, planIndex) == expectedIntent)

      val expectedPlan =
        Vector(
          "    └── plan",
          "        ├── contract-version: beautyq-variant-search-v1",
          "        ├── source-precedence",
          "        │   ├── [0] PublicRequest",
          "        │   └── [1] ParsedIntent",
          "        ├── geo-origin: request.userLocation",
          "        ├── facets",
          "        │   ├── [0] service",
          "        │   ├── [1] category",
          "        │   ├── [2] price",
          "        │   └── [3] durationMinutes",
          "        ├── groups",
          "        │   ├── [0] provider-carousel",
          "        │   └── [1] service-intent-carousel",
          "        ├── modes",
          "        │   ├── [0] SemanticSearch",
          "        │   ├── [1] StructuredBrowse",
          "        │   └── [2] DefaultBrowse",
          "        ├── default-browse-code: default-browse",
          "        └── candidate",
          "            ├── semanticTextParts",
          "            │   ├── [0] residual-text",
          "            │   └── [1] canonical-semantic-labels",
          "            └── eligibilityGates",
          "                ├── [0] semantic-query-text",
          "                ├── [1] first-page",
          "                └── [2] default-sort",
        )
      assert(lines.slice(planIndex, lines.size) == expectedPlan)
    }

    "expose variants.plan.candidate as the exact same typed candidate-policy values a reviewer would navigate to in BeautyQSemanticCandidatePolicy, not copies" in {
      assert(variants.plan.candidate.semanticTextParts eq BeautyQSemanticCandidatePolicy.semanticTextParts)
      assert(variants.plan.candidate.eligibilityGates eq BeautyQSemanticCandidatePolicy.eligibilityGates)
    }
  }

  "source-level anti-tautology proof" should {
    "keep BeautyQSearchDeclarations.scala free of manual codecs, low-level field declarations, and domain-owned rendering, with exactly one searchFields[VariantSearchDocumentGen2](\"variants\") registry" in {
      val mainRoot   = repoRoot.resolve("beautyq-search-gen2-contract/src/main/scala")
      val rootFile   = mainRoot.resolve("leaderboard/search/beautyq/gen2/contract/BeautyQSearchDeclarations.scala")
      val rootSource = Files.readString(rootFile, StandardCharsets.UTF_8)

      val forbiddenTokens = Vector(
        "uuidBackedCodec",
        "private given variantIdCodec",
        "private given masterServiceOfferIdCodec",
        "private given masterLocationIdCodec",
        "private given masterIdCodec",
        "private given serviceIdCodec",
        "private given categoryIdCodec",
        "private given serviceCodeCodec",
        "private given categoryCodeCodec",
        "field[",
        "computedField[",
        ".withSemantic(\"",
        "ordinaryStaticFields",
        "ordinaryFields",
        "RenderNode",
        "renderNode(",
        "graftRenderedBlock",
      )
      val violations = forbiddenTokens.filter(rootSource.contains)
      assert(violations.isEmpty, s"unexpected tokens in BeautyQSearchDeclarations.scala: $violations")

      val registryMarker = "searchFields[VariantSearchDocumentGen2](\"variants\")"
      val registryCount   = rootSource.sliding(registryMarker.length).count(_ == registryMarker)
      assert(registryCount == 1, s"expected exactly one '$registryMarker', found $registryCount")

      val documentFile   = mainRoot.resolve("leaderboard/search/beautyq/gen2/contract/VariantSearchDocumentGen2.scala")
      val documentSource = Files.readString(documentFile, StandardCharsets.UTF_8)
      assert(documentSource.contains("final case class VariantSearchDocumentGen2("), "VariantSearchDocumentGen2 must remain an explicit case class")
    }

    "declare no field outside BeautyQSearchDeclarations.scala within beautyq-search-gen2-contract's main sources, and retain no ModuleMarker" in {
      val mainRoot = repoRoot.resolve("beautyq-search-gen2-contract/src/main/scala")
      val files = Using.resource(Files.walk(mainRoot)) {
        stream => stream.iterator.asScala.toList.filter(path => Files.isRegularFile(path) && path.toString.endsWith(".scala"))
      }

      val fieldDeclarationViolations = files.flatMap {
        path =>
          val relativeName = mainRoot.relativize(path).toString
          val content       = Files.readString(path, StandardCharsets.UTF_8)
          val declaresField = content.contains("field[") || content.contains("computedField[")
          if (declaresField && path.getFileName.toString != "BeautyQSearchDeclarations.scala") List(relativeName) else Nil
      }
      assert(fieldDeclarationViolations.isEmpty, s"unexpected field[/computedField[ declarations outside the root: $fieldDeclarationViolations")

      val moduleMarkerViolations = files.filter(path => Files.readString(path, StandardCharsets.UTF_8).contains("ModuleMarker"))
      assert(moduleMarkerViolations.isEmpty, s"unexpected ModuleMarker references: $moduleMarkerViolations")
    }
  }
}
