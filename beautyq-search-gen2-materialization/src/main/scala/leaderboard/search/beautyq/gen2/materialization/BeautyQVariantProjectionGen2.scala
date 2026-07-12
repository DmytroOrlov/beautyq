package leaderboard.search.beautyq.gen2.materialization

import leaderboard.model.*
import leaderboard.model.Category.CategoryId
import leaderboard.model.ServiceVariantSchemaValidationError.{DisallowedAttribute, MissingRequiredAttribute}
import BeautyQVariantProjectionError.*
import leaderboard.search.beautyq.gen2.contract.VariantSearchDocumentGen2
import leaderboard.search.gen2.contract.{GeoPoint, SearchValueCodec}
import leaderboard.search.gen2.core.materialization.{CanonicalIndex, ProjectionFormatVersion}

/** Explicit BeautyQ Gen2 projection: snapshot indexing, required joins, cross-owner invariants, schema
  * validation, attribute normalization and text composition. Reimplements the useful Gen1 variant-document
  * projection semantics (consulted only as behavior evidence, never imported) independently with Gen2
  * types, stable codes and an explicit, accumulating error model instead of a fail-fast `Either`.
  */
object BeautyQVariantProjectionGen2 {
  val projectionFormatVersion: ProjectionFormatVersion = ProjectionFormatVersion("beautyq-variant-projection-v1")

  def project(snapshot: BeautyQSearchSnapshot): Either[BeautyQVariantProjectionErrors, Vector[VariantSearchDocumentGen2]] = {
    val categoriesById = CanonicalIndex.preservingFirst(snapshot.categories)(_.id)(BeautyQSnapshotCanonicalRows.category(_).sortKey)
    val servicesById = CanonicalIndex.preservingFirst(snapshot.services)(_.id)(BeautyQSnapshotCanonicalRows.service(_).sortKey)
    val mastersById = CanonicalIndex.preservingFirst(snapshot.masters)(_.id)(BeautyQSnapshotCanonicalRows.master(_).sortKey)
    val locationsById = CanonicalIndex.preservingFirst(snapshot.masterLocations)(_.id)(BeautyQSnapshotCanonicalRows.location(_).sortKey)
    val offersById = CanonicalIndex.preservingFirst(snapshot.masterServiceOffers)(_.id)(BeautyQSnapshotCanonicalRows.offer(_).sortKey)
    val schemasByServiceId = CanonicalIndex.preservingFirst(snapshot.serviceVariantSchemas)(_.serviceId)(BeautyQSnapshotCanonicalRows.schema(_).sortKey)

    val indexErrors: Vector[BeautyQVariantProjectionError] =
      CanonicalIndex.duplicates(snapshot.categories.map(_.id))(id => idKey(id)).map(id => DuplicateEntityId("Category", idKey(id))) ++
        CanonicalIndex.duplicates(snapshot.services.map(_.id))(id => idKey(id)).map(id => DuplicateEntityId("Service", idKey(id))) ++
        CanonicalIndex.duplicates(snapshot.masters.map(_.id))(id => idKey(id)).map(id => DuplicateEntityId("Master", idKey(id))) ++
        CanonicalIndex.duplicates(snapshot.masterLocations.map(_.id))(id => idKey(id)).map(id => DuplicateEntityId("MasterLocation", idKey(id))) ++
        CanonicalIndex.duplicates(snapshot.masterServiceOffers.map(_.id))(id => idKey(id)).map(id => DuplicateEntityId("MasterServiceOffer", idKey(id))) ++
        CanonicalIndex.duplicates(snapshot.categories.map(_.code))(_.value).map(DuplicateCategoryCode.apply) ++
        CanonicalIndex.duplicates(snapshot.services.map(_.code))(_.value).map(DuplicateServiceCode.apply) ++
        CanonicalIndex.duplicates(snapshot.serviceVariantSchemas.map(_.serviceId))(id => idKey(id)).map(DuplicateServiceSchema.apply)

    val orderedVariants = snapshot.masterServiceOfferVariants.sortBy(BeautyQSnapshotCanonicalRows.variant(_).sortKey)
    val variantIdErrors =
      CanonicalIndex.duplicates(orderedVariants.map(_.id))(id => idKey(id)).map(id => DuplicateEntityId("MasterServiceOfferVariant", idKey(id)))

    val perVariantResults = orderedVariants.map {
      variant =>
        projectVariant(variant, categoriesById, servicesById, mastersById, locationsById, offersById, schemasByServiceId)
    }

    val variantErrors = perVariantResults.flatMap(_.errors)
    val documents = perVariantResults.flatMap(_.document)

    val allErrors = indexErrors ++ variantIdErrors ++ variantErrors

    BeautyQVariantProjectionErrors.fromVector(allErrors) match {
      case Some(errors) => Left(errors)
      case None         => Right(documents)
    }
  }

  private final case class VariantProjectionResult(
    errors: Vector[BeautyQVariantProjectionError],
    document: Option[VariantSearchDocumentGen2],
  )

  /** Resolves every independent join and invariant for one variant without short-circuiting: `offer`
    * and `location` are looked up unconditionally, `service`/`master` once `offer` is known, and
    * `category`/`schema` once `service` is known, so one invalid variant can report every violation
    * whose prerequisites are actually available rather than only the first. A document is only built
    * when the accumulated error vector is empty.
    */
  private def projectVariant(
    variant: MasterServiceOfferVariant,
    categoriesById: Map[CategoryId, Category],
    servicesById: Map[ServiceId, Service],
    mastersById: Map[MasterId, Master],
    locationsById: Map[MasterLocationId, MasterLocation],
    offersById: Map[MasterServiceOfferId, MasterServiceOffer],
    schemasByServiceId: Map[ServiceId, ServiceVariantSchema],
  ): VariantProjectionResult = {
    val offerOpt = offersById.get(variant.masterServiceOfferId)
    val locationOpt = locationsById.get(variant.masterLocationId)
    val serviceOpt = offerOpt.flatMap(offer => servicesById.get(offer.serviceId))
    val masterOpt = offerOpt.flatMap(offer => mastersById.get(offer.masterId))
    val categoryOpt = serviceOpt.flatMap(service => categoriesById.get(service.categoryId))
    val schemaOpt = serviceOpt.flatMap(service => schemasByServiceId.get(service.id))

    val presenceErrors: Vector[BeautyQVariantProjectionError] =
      Vector(
        Option.when(offerOpt.isEmpty)(MissingOffer(variant.id, variant.masterServiceOfferId)),
        Option.when(locationOpt.isEmpty)(MissingLocation(variant.id, variant.masterLocationId)),
        offerOpt.filter(_ => serviceOpt.isEmpty).map(offer => MissingService(variant.id, offer.serviceId)),
        offerOpt.filter(_ => masterOpt.isEmpty).map(offer => MissingMaster(variant.id, offer.masterId)),
        serviceOpt.filter(_ => categoryOpt.isEmpty).map(service => MissingCategory(variant.id, service.categoryId)),
        serviceOpt.filter(_ => schemaOpt.isEmpty).map(service => MissingServiceSchema(variant.id, service.id)),
      ).flatten

    val invariantErrors: Vector[BeautyQVariantProjectionError] =
      Vector(
        for {
          offer <- offerOpt
          location <- locationOpt
          if location.masterId != offer.masterId
        } yield OfferLocationMasterMismatch(variant.id, offer.masterId, location.masterId),
      ).flatten ++ schemaOpt.toVector.flatMap {
        schema =>
          schema.validate(variant.attributes).left.toOption.map {
            violation => SchemaViolation(variant.id, schema.serviceId, schemaViolationMessage(violation))
          }
      }

    val errors = presenceErrors ++ invariantErrors

    val document =
      if (errors.nonEmpty) {
        None
      } else {
        for {
          offer <- offerOpt
          location <- locationOpt
          service <- serviceOpt
          master <- masterOpt
          category <- categoryOpt
        } yield buildDocument(variant, service, category, master, location)
      }

    VariantProjectionResult(errors, document)
  }

  private def schemaViolationMessage(violation: ServiceVariantSchemaValidationError): String =
    violation match {
      case DisallowedAttribute(attribute)      => s"disallowed attribute ${attribute.code}"
      case MissingRequiredAttribute(attribute) => s"missing required attribute ${attribute.code}"
    }

  private def buildDocument(
    variant: MasterServiceOfferVariant,
    service: Service,
    category: Category,
    master: Master,
    location: MasterLocation,
  ): VariantSearchDocumentGen2 = {
    val enumAttributes = variant.enumAttributes.iterator.map { case (definition, value) => definition.code -> value.stringCode }.toMap
    val booleanAttributes = variant.booleanAttributes.iterator.map { case (definition, value) => definition.code -> value }.toMap
    val intAttributes = variant.intAttributes.iterator.map { case (definition, value) => definition.code -> value }.toMap
    val bigDecimalAttributes = variant.bigDecimalAttributes.iterator.map { case (definition, value) => definition.code -> value }.toMap

    val attributeTokens = buildAttributeTokens(enumAttributes, booleanAttributes, intAttributes, bigDecimalAttributes)

    val serviceText = normalizeText(Vector(service.name, category.name))
    val attributeText = normalizeText(attributeTokens)
    val providerText = normalizeText(Vector(master.name, location.name))
    val locationText = normalizeText(Vector(location.name, location.address, category.name))
    val allText = normalizeText(Vector(serviceText, attributeText, providerText, locationText))

    VariantSearchDocumentGen2(
      variantId = variant.id,
      masterServiceOfferId = variant.masterServiceOfferId,
      masterLocationId = variant.masterLocationId,
      masterId = master.id,
      serviceId = service.id,
      serviceCode = service.code,
      categoryId = category.id,
      categoryCode = category.code,
      serviceName = service.name,
      categoryName = category.name,
      masterName = master.name,
      locationName = location.name,
      address = location.address,
      location = GeoPoint(location.lat, location.lon),
      lat = location.lat,
      lon = location.lon,
      priceFrom = variant.priceFrom,
      priceTo = variant.priceTo,
      durationMin = variant.durationMin,
      enumAttributes = enumAttributes,
      booleanAttributes = booleanAttributes,
      intAttributes = intAttributes,
      bigDecimalAttributes = bigDecimalAttributes,
      allText = allText,
      serviceText = serviceText,
      attributeText = attributeText,
      providerText = providerText,
      locationText = locationText,
    )
  }

  private def buildAttributeTokens(
    enumAttributes: Map[String, String],
    booleanAttributes: Map[String, Boolean],
    intAttributes: Map[String, Int],
    bigDecimalAttributes: Map[String, BigDecimal],
  ): Vector[String] = {
    val enumTokens = enumAttributes.toVector.sortBy(_._1).flatMap {
      case (code, value) => Vector(code, value, humanize(code), humanize(value))
    }
    val booleanTokens = booleanAttributes.toVector.sortBy(_._1).flatMap {
      case (code, value) => Vector(code, humanize(code), value.toString)
    }
    val intTokens = intAttributes.toVector.sortBy(_._1).flatMap {
      case (code, value) => Vector(code, humanize(code), value.toString)
    }
    val decimalTokens = bigDecimalAttributes.toVector.sortBy(_._1).flatMap {
      case (code, value) => Vector(code, humanize(code), SearchValueCodec.bigDecimal.encodeCanonical(value))
    }

    enumTokens ++ booleanTokens ++ intTokens ++ decimalTokens
  }

  private def humanize(value: String): String = value.replace('_', ' ')

  private def normalizeText(parts: Iterable[String]): String =
    parts.iterator.map(_.trim).filter(_.nonEmpty).mkString(" ")

  private def idKey[A](id: A)(using idEvidence: UuidBackedId[A]): String = idEvidence.unwrap(id).toString

}
