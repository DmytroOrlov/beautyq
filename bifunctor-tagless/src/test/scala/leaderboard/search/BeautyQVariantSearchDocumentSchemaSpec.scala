package leaderboard.search

import leaderboard.model.*
import leaderboard.search.document.{BeautyQVariantSearchDocumentContract, BeautyQVariantSearchDocumentMaterialization, BeautySearchCatalogSnapshot, VariantSearchDocument}
import leaderboard.search.dsl.{BeautyQSearchFieldSemantics, SearchFieldKind, SearchFieldSemantic}
import leaderboard.seed.{BeautyQSeedData, BeautyQSeedLoader}
import org.scalatest.wordspec.AnyWordSpec

final class BeautyQVariantSearchDocumentSchemaSpec extends AnyWordSpec {
  import BeautyQSearchFieldSemantics.*

  private val seed = loadSeedData()

  "BeautyQVariantSearchDocumentContract.documentSpec" should {
    "declare the BeautyQ variant index and document id" in {
      val spec = BeautyQVariantSearchDocumentContract.documentSpec
      val document = projectSeedDocuments() match {
        case first :: _ => first
        case Nil        => fail("Expected seed projection to produce documents")
      }

      assert(spec.indexName == "beautyq_variant_v1")
      assert(spec.id(document) == document.variantId.toString)
    }

    "preserve static field paths and roles" in {
      val roles = BeautyQVariantSearchDocumentContract.documentSpec.fields.map(FieldRole.from)

      assert(roles.take(staticFieldRoles.size) == staticFieldRoles)
    }

    "expose contract-owned static field handles with existing paths and roles" in {
      val fields = BeautyQVariantSearchDocumentContract.Fields
      val roles = fields.staticFields.map(FieldRole.from)

      assert(roles == staticFieldRoles)
      assert(fields.variantId.path == "variantId")
      assert(fields.masterLocationId.path == "masterLocationId")
      assert(fields.serviceId.path == "serviceId")
      assert(fields.serviceName.path == "serviceName")
      assert(fields.categoryName.path == "categoryName")
      assert(fields.priceFrom.path == "priceFrom")
      assert(fields.durationMin.path == "durationMin")
      assert(fields.location.path == "location")
    }

    "include every dynamic attribute field with expected roles" in {
      val roles = BeautyQVariantSearchDocumentContract.documentSpec.fields.map(FieldRole.from)
      val dynamicRoles = AttributeDefinition.all.flatMap {
        case definition: EnumAttributeDefinition[?] =>
          List(FieldRole(
            path = s"enumAttributes.${definition.code}",
            kind = SearchFieldKind.Keyword,
            semantic = Some(EnumAttribute(definition.code)),
            searchable = false,
            filterable = true,
            facetable = true,
            sortable = false,
            boost = 1.0,
          ))
        case definition: BooleanAttributeDefinition =>
          List(FieldRole(
            path = s"booleanAttributes.${definition.code}",
            kind = SearchFieldKind.Boolean,
            semantic = Some(BooleanAttribute(definition.code)),
            searchable = false,
            filterable = true,
            facetable = true,
            sortable = false,
            boost = 1.0,
          ))
        case definition: IntAttributeDefinition =>
          List(FieldRole(
            path = s"intAttributes.${definition.code}",
            kind = SearchFieldKind.Integer,
            semantic = Some(IntAttribute(definition.code)),
            searchable = false,
            filterable = true,
            facetable = true,
            sortable = true,
            boost = 1.0,
          ))
        case definition: BigDecimalAttributeDefinition =>
          List(FieldRole(
            path = s"bigDecimalAttributes.${definition.code}",
            kind = SearchFieldKind.Decimal,
            semantic = Some(DecimalAttribute(definition.code)),
            searchable = false,
            filterable = true,
            facetable = true,
            sortable = true,
            boost = 1.0,
          ))
      }

      assert(roles.drop(staticFieldRoles.size) == dynamicRoles)
      assert(roles.map(_.path) == (staticFieldRoles ++ dynamicRoles).map(_.path))
    }

    "expose dynamic attribute handles through contract-owned maps" in {
      val fields = BeautyQVariantSearchDocumentContract.Fields

      AttributeDefinition.enumDefinitions.foreach { definition =>
        fields.enumAttributesByCode.get(definition.code) match {
          case Some(field) =>
            assert(field.path == s"enumAttributes.${definition.code}")
            assert(field.semantic.contains(EnumAttribute(definition.code)))
          case None =>
            fail(s"Missing enum field handle for ${definition.code}")
        }
      }
      AttributeDefinition.booleanDefinitions.foreach { definition =>
        fields.booleanAttributesByCode.get(definition.code) match {
          case Some(field) =>
            assert(field.path == s"booleanAttributes.${definition.code}")
            assert(field.semantic.contains(BooleanAttribute(definition.code)))
          case None =>
            fail(s"Missing boolean field handle for ${definition.code}")
        }
      }
      AttributeDefinition.intDefinitions.foreach { definition =>
        fields.intAttributesByCode.get(definition.code) match {
          case Some(field) =>
            assert(field.path == s"intAttributes.${definition.code}")
            assert(field.semantic.contains(IntAttribute(definition.code)))
          case None =>
            fail(s"Missing int field handle for ${definition.code}")
        }
      }
      AttributeDefinition.bigDecimalDefinitions.foreach { definition =>
        fields.decimalAttributesByCode.get(definition.code) match {
          case Some(field) =>
            assert(field.path == s"bigDecimalAttributes.${definition.code}")
            assert(field.semantic.contains(DecimalAttribute(definition.code)))
          case None =>
            fail(s"Missing decimal field handle for ${definition.code}")
        }
      }
    }
  }

  "BeautyQVariantSearchDocumentContract.qdrantPayloadSpec" should {
    "own exactly the Qdrant payload field paths" in {
      val payloadSpec = BeautyQVariantSearchDocumentContract.qdrantPayloadSpec

      assert(payloadSpec.fields == List(
        BeautyQVariantSearchDocumentContract.Fields.variantId,
        BeautyQVariantSearchDocumentContract.Fields.masterLocationId,
        BeautyQVariantSearchDocumentContract.Fields.serviceId,
        BeautyQVariantSearchDocumentContract.Fields.serviceName,
      ))
      assert(payloadSpec.fieldPaths == List(
        "variantId",
        "masterLocationId",
        "serviceId",
        "serviceName",
      ))
    }

    "reference only fields defined by the document spec" in {
      val payloadSpec = BeautyQVariantSearchDocumentContract.qdrantPayloadSpec

      payloadSpec.fieldPaths.foreach { path =>
        payloadSpec.documentSpec.fieldsByPath.get(path) match {
          case Some(_) =>
            (): Unit
          case None =>
            fail(s"Expected Qdrant payload field path '$path' to exist in documentSpec")
        }
      }
    }
  }

  "BeautyQVariantSearchDocumentContract.querySchema" should {
    "point at contract-owned field handles" in {
      val fields = BeautyQVariantSearchDocumentContract.Fields
      val querySchema = BeautyQVariantSearchDocumentContract.querySchema

      assert(querySchema.field("serviceName") == Right(fields.serviceName))
      assert(querySchema.field("categoryName") == Right(fields.categoryName))
      assert(querySchema.field("priceFrom") == Right(fields.priceFrom))
      assert(querySchema.field("durationMin") == Right(fields.durationMin))
      assert(querySchema.field("location") == Right(fields.location))
    }
  }

  "BeautyQVariantSearchDocumentMaterialization.project" should {
    "project seed variants in source order" in {
      val documents = projectSeedDocuments()

      assert(documents.size == seed.masterServiceOfferVariants.size)
      assert(documents.map(_.variantId) == seed.masterServiceOfferVariants.map(_.id))
    }

    "project enum attributes as stringCode values" in {
      val documentsById = projectSeedDocuments().iterator.map(document => document.variantId -> document).toMap

      seed.masterServiceOfferVariants.foreach {
        variant =>
          documentsById.get(variant.id) match {
            case Some(document) =>
              val expected = variant.enumAttributes.iterator.collect {
                case (definition: EnumAttributeDefinition[?], value) => definition.code -> value.stringCode
              }.toMap
              assert(document.enumAttributes == expected)
            case None =>
              fail(s"Missing projected document for ${variant.id}")
          }
      }
    }

    "preserve projected text fields for a deterministic seed document" in {
      val snapshot = BeautySearchCatalogSnapshot.fromSeedData(seed)
      val document = projectSeedDocuments() match {
        case first :: _ => first
        case Nil        => fail("Expected seed projection to produce documents")
      }
      val expected = expectedText(snapshot, document)

      assert(document.serviceText == expected.serviceText)
      assert(document.attributeText == expected.attributeText)
      assert(document.providerText == expected.providerText)
      assert(document.locationText == expected.locationText)
      assert(document.allText == expected.allText)
    }

    "fail with the canonical missing offer message" in {
      val (snapshot, variant) = firstVariantSnapshot()
      val broken = snapshot.copy(masterServiceOffers = snapshot.masterServiceOffers.filterNot(_.id == variant.masterServiceOfferId))

      assertProjectFails(
        broken,
        s"build-variant-search-documents: missing MasterServiceOffer ${variant.masterServiceOfferId} while building document for variant ${variant.id}",
      )
    }

    "fail with the canonical missing service message" in {
      val (snapshot, variant, offer) = firstVariantOfferSnapshot()
      val broken = snapshot.copy(services = snapshot.services.filterNot(_.id == offer.serviceId))

      assertProjectFails(
        broken,
        s"build-variant-search-documents: missing Service ${offer.serviceId} while building document for variant ${variant.id}",
      )
    }

    "fail with the canonical missing category message" in {
      val (snapshot, variant, service) = firstVariantServiceSnapshot()
      val broken = snapshot.copy(categories = snapshot.categories.filterNot(_.id == service.categoryId))

      assertProjectFails(
        broken,
        s"build-variant-search-documents: missing Category ${service.categoryId} while building document for variant ${variant.id}",
      )
    }

    "fail with the canonical missing master message" in {
      val (snapshot, variant, offer) = firstVariantOfferSnapshot()
      val broken = snapshot.copy(masters = snapshot.masters.filterNot(_.id == offer.masterId))

      assertProjectFails(
        broken,
        s"build-variant-search-documents: missing Master ${offer.masterId} while building document for variant ${variant.id}",
      )
    }

    "fail with the canonical missing location message" in {
      val (snapshot, variant) = firstVariantSnapshot()
      val broken = snapshot.copy(masterLocations = snapshot.masterLocations.filterNot(_.id == variant.masterLocationId))

      assertProjectFails(
        broken,
        s"build-variant-search-documents: missing MasterLocation ${variant.masterLocationId} while building document for variant ${variant.id}",
      )
    }

    "fail with the canonical cross-master message" in {
      val (snapshot, variant, offer) = firstVariantOfferSnapshot()
      val otherMaster = snapshot.masters.find(_.id != offer.masterId) match {
        case Some(value) => value
        case None        => fail("Expected seed to contain another master")
      }
      val brokenLocations = snapshot.masterLocations.map {
        location =>
          if (location.id == variant.masterLocationId) {
            location.copy(masterId = otherMaster.id)
          } else {
            location
          }
      }
      val broken = snapshot.copy(masterLocations = brokenLocations)

      assertProjectFails(
        broken,
        s"build-variant-search-documents: variant ${variant.id} joins offer ${offer.id} and location ${variant.masterLocationId} from different masters (${offer.masterId} != ${otherMaster.id})",
      )
    }

    "fail with the canonical schema validation message" in {
      val (snapshot, variant) = firstVariantSnapshot()
      val invalidVariant = MasterServiceOfferVariant.make(
        id = variant.id,
        masterServiceOfferId = variant.masterServiceOfferId,
        masterLocationId = variant.masterLocationId,
        priceFrom = variant.priceFrom,
        priceTo = variant.priceTo,
        durationMin = variant.durationMin,
      ) match {
        case Right(value) => value
        case Left(error)  => fail(s"Expected invalid schema fixture to satisfy base variant validation, got: ${error.message}")
      }
      val broken = snapshot.copy(masterServiceOfferVariants = replaceFirstVariant(snapshot.masterServiceOfferVariants, invalidVariant))
      val offer = snapshot.masterServiceOffers.find(_.id == variant.masterServiceOfferId) match {
        case Some(value) => value
        case None        => fail("Expected first variant offer to exist")
      }

      assertProjectFails(
        broken,
        s"build-variant-search-documents: variant ${variant.id} violates schema for service ${offer.serviceId}: MissingRequiredAttribute(NailServiceTypeAttribute)",
      )
    }

    "allow missing schema and preserve projection semantics" in {
      val (snapshot, variant, offer) = firstVariantOfferSnapshot()
      val broken = snapshot.copy(serviceVariantSchemas = snapshot.serviceVariantSchemas.filterNot(_.serviceId == offer.serviceId))

      project(broken) match {
        case Right(documents) =>
          assert(documents.map(_.variantId) == snapshot.masterServiceOfferVariants.map(_.id))
          assert(documents.exists(_.variantId == variant.id))
        case Left(failure) =>
          fail(s"Expected missing schema to remain allowed, got: ${failure.message}")
      }
    }
  }

  private def project(snapshot: BeautySearchCatalogSnapshot): Either[QueryFailure, List[VariantSearchDocument]] =
    BeautyQVariantSearchDocumentMaterialization.project(
      BeautySearchCatalogSnapshot.toMaterializationSnapshot(snapshot)
    )

  private def projectSeedDocuments(): List[VariantSearchDocument] =
    project(BeautySearchCatalogSnapshot.fromSeedData(seed)) match {
      case Right(value) => value
      case Left(error)  => fail(s"Expected seed projection to succeed, got: ${error.message}")
    }

  private def firstVariantSnapshot(): (BeautySearchCatalogSnapshot, MasterServiceOfferVariant) = {
    val snapshot = BeautySearchCatalogSnapshot.fromSeedData(seed)
    snapshot.masterServiceOfferVariants match {
      case variant :: _ => (snapshot.copy(masterServiceOfferVariants = List(variant)), variant)
      case Nil          => fail("Expected seed to contain variants")
    }
  }

  private def firstVariantOfferSnapshot(): (BeautySearchCatalogSnapshot, MasterServiceOfferVariant, MasterServiceOffer) = {
    val (snapshot, variant) = firstVariantSnapshot()
    val offer = snapshot.masterServiceOffers.find(_.id == variant.masterServiceOfferId) match {
      case Some(value) => value
      case None        => fail("Expected first variant offer to exist")
    }
    (snapshot, variant, offer)
  }

  private def firstVariantServiceSnapshot(): (BeautySearchCatalogSnapshot, MasterServiceOfferVariant, Service) = {
    val (snapshot, variant, offer) = firstVariantOfferSnapshot()
    val service = snapshot.services.find(_.id == offer.serviceId) match {
      case Some(value) => value
      case None        => fail("Expected first variant service to exist")
    }
    (snapshot, variant, service)
  }

  private def assertProjectFails(snapshot: BeautySearchCatalogSnapshot, expectedMessage: String): Unit =
    project(snapshot) match {
      case Left(failure) =>
        assert(failure.message == expectedMessage)
        (): Unit
      case Right(documents) =>
        fail(s"Expected projection failure, got ${documents.size} documents")
    }

  private def replaceFirstVariant(
    variants: List[MasterServiceOfferVariant],
    replacement: MasterServiceOfferVariant,
  ): List[MasterServiceOfferVariant] =
    variants match {
      case _ :: tail => replacement :: tail
      case Nil       => fail("Expected seed to contain variants")
    }

  private def expectedText(snapshot: BeautySearchCatalogSnapshot, document: VariantSearchDocument): ExpectedText = {
    val variant = snapshot.masterServiceOfferVariants.find(_.id == document.variantId) match {
      case Some(value) => value
      case None        => fail(s"Expected variant ${document.variantId} to exist")
    }
    val offer = snapshot.masterServiceOffers.find(_.id == variant.masterServiceOfferId) match {
      case Some(value) => value
      case None        => fail(s"Expected offer ${variant.masterServiceOfferId} to exist")
    }
    val service = snapshot.services.find(_.id == offer.serviceId) match {
      case Some(value) => value
      case None        => fail(s"Expected service ${offer.serviceId} to exist")
    }
    val category = snapshot.categories.find(_.id == service.categoryId) match {
      case Some(value) => value
      case None        => fail(s"Expected category ${service.categoryId} to exist")
    }
    val master = snapshot.masters.find(_.id == offer.masterId) match {
      case Some(value) => value
      case None        => fail(s"Expected master ${offer.masterId} to exist")
    }
    val location = snapshot.masterLocations.find(_.id == variant.masterLocationId) match {
      case Some(value) => value
      case None        => fail(s"Expected location ${variant.masterLocationId} to exist")
    }
    val enumAttributes = variant.enumAttributes.iterator.collect {
      case (definition: EnumAttributeDefinition[?], value) => definition.code -> value.stringCode
    }.toMap
    val booleanAttributes = variant.booleanAttributes.iterator.map {
      case (definition, value) => definition.code -> value
    }.toMap
    val intAttributes = variant.intAttributes.iterator.map {
      case (definition, value) => definition.code -> value
    }.toMap
    val bigDecimalAttributes = variant.bigDecimalAttributes.iterator.map {
      case (definition, value) => definition.code -> value
    }.toMap
    val attributeText = normalizeText(attributeTokens(enumAttributes, booleanAttributes, intAttributes, bigDecimalAttributes))
    val serviceText   = normalizeText(List(service.name, category.name))
    val providerText  = normalizeText(List(master.name, location.name))
    val locationText  = normalizeText(List(location.name, location.address, category.name))
    val allText       = normalizeText(List(serviceText, attributeText, providerText, locationText))

    ExpectedText(serviceText, attributeText, providerText, locationText, allText)
  }

  private def attributeTokens(
    enumAttributes: Map[String, String],
    booleanAttributes: Map[String, Boolean],
    intAttributes: Map[String, Int],
    bigDecimalAttributes: Map[String, BigDecimal],
  ): List[String] =
    enumAttributes.toList.flatMap {
      case (code, value) => List(code, value, humanize(code), humanize(value))
    } ++ booleanAttributes.toList.flatMap {
      case (code, value) => List(code, humanize(code), value.toString)
    } ++ intAttributes.toList.flatMap {
      case (code, value) => List(code, humanize(code), value.toString)
    } ++ bigDecimalAttributes.toList.flatMap {
      case (code, value) => List(code, humanize(code), value.toString())
    }

  private def humanize(value: String): String =
    value.replace('_', ' ')

  private def normalizeText(parts: Iterable[String]): String =
    parts.iterator.map(_.trim).filter(_.nonEmpty).mkString(" ")

  private def loadSeedData(): BeautyQSeedData =
    new BeautyQSeedLoader.ResourceLoader().load() match {
      case Right(value) => value
      case Left(error)  => throw new RuntimeException(error.message)
    }

  private val staticFieldRoles: List[FieldRole] =
    List(
      FieldRole("variantId", SearchFieldKind.Keyword, Some(VariantId), searchable = false, filterable = true, facetable = false, sortable = true, boost = 1.0),
      FieldRole("masterServiceOfferId", SearchFieldKind.Keyword, Some(MasterServiceOfferId), searchable = false, filterable = true, facetable = false, sortable = false, boost = 1.0),
      FieldRole("masterLocationId", SearchFieldKind.Keyword, Some(MasterLocationId), searchable = false, filterable = true, facetable = true, sortable = false, boost = 1.0),
      FieldRole("masterId", SearchFieldKind.Keyword, Some(MasterId), searchable = false, filterable = true, facetable = false, sortable = false, boost = 1.0),
      FieldRole("serviceId", SearchFieldKind.Keyword, Some(ServiceId), searchable = false, filterable = true, facetable = true, sortable = false, boost = 1.0),
      FieldRole("serviceName", SearchFieldKind.Keyword, Some(ServiceName), searchable = false, filterable = true, facetable = true, sortable = false, boost = 1.0),
      FieldRole("categoryId", SearchFieldKind.Keyword, Some(CategoryId), searchable = false, filterable = true, facetable = true, sortable = false, boost = 1.0),
      FieldRole("categoryName", SearchFieldKind.Keyword, Some(CategoryName), searchable = false, filterable = true, facetable = true, sortable = false, boost = 1.0),
      FieldRole("masterName", SearchFieldKind.Keyword, None, searchable = false, filterable = false, facetable = false, sortable = false, boost = 1.0),
      FieldRole("locationName", SearchFieldKind.Keyword, None, searchable = false, filterable = false, facetable = false, sortable = false, boost = 1.0),
      FieldRole("address", SearchFieldKind.Keyword, None, searchable = false, filterable = false, facetable = false, sortable = false, boost = 1.0),
      FieldRole("lat", SearchFieldKind.Decimal, None, searchable = false, filterable = false, facetable = false, sortable = false, boost = 1.0),
      FieldRole("lon", SearchFieldKind.Decimal, None, searchable = false, filterable = false, facetable = false, sortable = false, boost = 1.0),
      FieldRole("priceFrom", SearchFieldKind.Decimal, Some(PriceFrom), searchable = false, filterable = true, facetable = true, sortable = true, boost = 1.0),
      FieldRole("priceTo", SearchFieldKind.Decimal, Some(PriceTo), searchable = false, filterable = true, facetable = false, sortable = true, boost = 1.0),
      FieldRole("durationMin", SearchFieldKind.Integer, Some(DurationMin), searchable = false, filterable = true, facetable = true, sortable = true, boost = 1.0),
      FieldRole("location", SearchFieldKind.GeoPoint, Some(Location), searchable = false, filterable = false, facetable = false, sortable = true, boost = 1.0),
      FieldRole("allText", SearchFieldKind.Text, Some(AllText), searchable = true, filterable = false, facetable = false, sortable = false, boost = 4.0),
      FieldRole("serviceText", SearchFieldKind.Text, Some(ServiceText), searchable = true, filterable = false, facetable = false, sortable = false, boost = 5.0),
      FieldRole("attributeText", SearchFieldKind.Text, Some(AttributeText), searchable = true, filterable = false, facetable = false, sortable = false, boost = 4.0),
      FieldRole("providerText", SearchFieldKind.Text, Some(ProviderText), searchable = true, filterable = false, facetable = false, sortable = false, boost = 2.0),
      FieldRole("locationText", SearchFieldKind.Text, Some(LocationText), searchable = true, filterable = false, facetable = false, sortable = false, boost = 2.5),
    )

  private final case class ExpectedText(
    serviceText: String,
    attributeText: String,
    providerText: String,
    locationText: String,
    allText: String,
  )

  private final case class FieldRole(
    path: String,
    kind: SearchFieldKind,
    semantic: Option[SearchFieldSemantic],
    searchable: Boolean,
    filterable: Boolean,
    facetable: Boolean,
    sortable: Boolean,
    boost: Double,
  )

  private object FieldRole {
    def from(field: leaderboard.search.dsl.SearchField[VariantSearchDocument]): FieldRole =
      FieldRole(
        path = field.path,
        kind = field.kind,
        semantic = field.semantic,
        searchable = field.searchable,
        filterable = field.filterable,
        facetable = field.facetable,
        sortable = field.sortable,
        boost = field.boost,
      )
  }
}
