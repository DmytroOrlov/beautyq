package leaderboard.http

import io.circe.{Decoder, Json}
import leaderboard.search.beautyq.gen2.contract.{BeautySearchRequestGen2, BeautySortInput}
import leaderboard.search.gen2.contract.{FacetId, FacetSelectionId, GeoPoint, PageRequest, PageSize, PublicFieldName, PublicFilterInput, PublicFilterValue, PublicOperator, PublicSortName, SearchCursor, SortDirection}

/** The Gen2 wire codec is owned by the independent endpoint. It decodes into the native Gen2
  * request contract; it does not adapt the V1 request model.
  *
  * The response encoder reads the projector-owned DTO only: every field is the exact value the
  * BeautyQSearchResponseGen2Projector publishes. No constraint, provenance, suppression reason,
  * facet/group/carousel derivation, or backend mechanics is reconstructed in app-http. */
object BeautySearchGen2Json {
  def decodeRequest(json: Json): Either[String, BeautySearchRequestGen2] = {
    val c = json.hcursor
    for {
      query <- get[Option[String]](c, "query")
      filters <- get[Vector[Json]](c, "filters").flatMap(values => sequence(values.zipWithIndex.map { case (value, index) => decodeFilter(value).left.map(error => s"filters[$index]: $error") }))
      requestedFacets <- get[Vector[String]](c, "requestedFacets").map(_.map(FacetId.apply))
      sort <- get[Vector[Json]](c, "sort").flatMap(values => sequence(values.zipWithIndex.map { case (value, index) => decodeSort(value).left.map(error => s"sort[$index]: $error") }))
      page <- get[Json](c, "page").flatMap(decodePage)
      userLocation <- get[Option[Json]](c, "userLocation").flatMap(_.map(decodeLocation).fold[Either[String, Option[GeoPoint]]](Right(None))(value => value.map(Some(_))))
    } yield BeautySearchRequestGen2(query, filters, requestedFacets, sort, page, userLocation)
  }

  def encodeResponse(response: leaderboard.search.beautyq.gen2.wiring.BeautyQSearchResponseGen2): Json = {
    import response.*
    Json.obj(
      "hits" -> Json.fromValues(hits.map(hit => Json.obj(
        "id" -> Json.fromString(hit.id),
        "score" -> Json.fromBigDecimal(hit.score),
        "origin" -> Json.fromString(hit.origin.stableCode),
        "source" -> Json.fromJsonObject(hit.source),
      ))),
      "totalHits" -> Json.fromLong(totalHits),
      "totalRelation" -> Json.fromString(totalRelation),
      "facets" -> Json.fromValues(facets.map(facet => Json.obj(
        "id" -> Json.fromString(facet.id),
        "kind" -> Json.fromString(facet.kind),
        "buckets" -> Json.fromValues(facet.buckets.map(bucket => Json.obj(
          "key" -> bucket.key.fold(Json.Null)(Json.fromString),
          "id" -> bucket.id.fold(Json.Null)(Json.fromString),
          "count" -> Json.fromLong(bucket.count),
          "docCountErrorUpperBound" -> bucket.docCountErrorUpperBound.fold(Json.Null)(Json.fromLong),
        ))),
        "sumOtherDocCount" -> facet.sumOtherDocCount.fold(Json.Null)(Json.fromLong),
        "precision" -> Json.fromString(facet.precision),
      ))),
      "groups" -> Json.fromValues(groups.map(group => Json.obj(
        "id" -> Json.fromString(group.id),
        "buckets" -> Json.fromValues(group.buckets.map(bucket => Json.obj(
          "key" -> Json.fromString(bucket.key),
          "matchingDocumentCount" -> Json.fromLong(bucket.matchingDocumentCount),
          "representativeId" -> Json.fromString(bucket.representativeId),
          "representativeScore" -> Json.fromBigDecimal(bucket.representativeScore),
        ))),
        "precision" -> Json.fromString(group.precision),
      ))),
      "providerCarousel" -> Json.fromValues(providerCarousel.map(item => Json.obj(
        "masterId" -> Json.fromString(item.masterId),
        "masterName" -> Json.fromString(item.masterName),
        "masterLocationId" -> Json.fromString(item.masterLocationId),
        "locationName" -> Json.fromString(item.locationName),
        "address" -> Json.fromString(item.address),
        "matchingVariantCount" -> Json.fromLong(item.matchingVariantCount),
        "representativeVariantId" -> Json.fromString(item.representativeVariantId),
        "bestScore" -> Json.fromBigDecimal(item.bestScore),
        "distanceMeters" -> item.distanceMeters.fold(Json.Null)(Json.fromBigDecimal),
      ))),
      "serviceIntentCarousel" -> Json.fromValues(serviceIntentCarousel.map(item => Json.obj(
        "serviceId" -> Json.fromString(item.serviceId),
        "serviceName" -> Json.fromString(item.serviceName),
        "categoryId" -> Json.fromString(item.categoryId),
        "categoryName" -> Json.fromString(item.categoryName),
        "matchingVariantCount" -> Json.fromLong(item.matchingVariantCount),
        "representativeVariantId" -> Json.fromString(item.representativeVariantId),
        "bestScore" -> Json.fromBigDecimal(item.bestScore),
      ))),
      "appliedFilters" -> Json.fromValues(appliedFilters.map(filter => Json.obj(
        "fieldId" -> Json.fromString(filter.fieldId),
        "constraint" -> Json.fromString(filter.constraint),
        "provenance" -> Json.fromString(filter.provenance),
      ))),
      "suppressedFilters" -> Json.fromValues(suppressedFilters.map(filter => Json.obj(
        "fieldId" -> Json.fromString(filter.fieldId),
        "constraint" -> Json.fromString(filter.constraint),
        "provenance" -> Json.fromString(filter.provenance),
        "reason" -> Json.fromString(filter.reason),
      ))),
      "nextCursor" -> nextCursor.fold(Json.Null)(Json.fromString),
      "supplementCount" -> Json.fromInt(supplementCount),
      "supplementStatus" -> Json.fromString(supplementStatus.stableCode),
      "supplementStatusCode" -> Json.fromString(supplementStatusCode),
      "ineligibilityReason" -> ineligibilityReason.fold(Json.Null)(Json.fromString),
      "degradationReason" -> degradationReason.fold(Json.Null)(Json.fromString),
      "diagnostics" -> Json.obj(
        "timedOut" -> Json.fromBoolean(diagnostics.timedOut),
        "shardsTotal" -> Json.fromInt(diagnostics.shardsTotal),
        "shardsSuccessful" -> Json.fromInt(diagnostics.shardsSuccessful),
        "shardsFailed" -> Json.fromInt(diagnostics.shardsFailed),
      ),
    )
  }

  private def decodeFilter(json: Json): Either[String, PublicFilterInput] = {
    val c = json.hcursor
    for {
      field <- get[String](c, "field")
      operator <- get[String](c, "operator").flatMap(parseOperator)
      value <- get[Json](c, "value").flatMap(parseValue)
      presentationId <- get[Option[String]](c, "presentationId").map(_.map(FacetSelectionId.apply))
    } yield PublicFilterInput(PublicFieldName(field), operator, value, presentationId)
  }

  private def decodeSort(json: Json): Either[String, BeautySortInput] = {
    val c = json.hcursor
    for {
      name <- get[String](c, "name")
      direction <- get[String](c, "direction").flatMap(parseDirection)
    } yield BeautySortInput(PublicSortName(name), direction)
  }

  private def decodePage(json: Json): Either[String, PageRequest] = {
    val c = json.hcursor
    for {
      cursor <- get[Option[String]](c, "cursor")
      size <- get[Int](c, "size")
      pageSize <- PageSize.from(size).left.map(_.toString)
    } yield PageRequest(cursor.map(SearchCursor.fromTransport), pageSize)
  }

  private def decodeLocation(json: Json): Either[String, GeoPoint] = {
    val c = json.hcursor
    for {
      lat <- get[BigDecimal](c, "lat")
      lon <- get[BigDecimal](c, "lon")
    } yield GeoPoint(lat, lon)
  }

  private def parseOperator(value: String): Either[String, PublicOperator] = value match {
    case "equal" => Right(PublicOperator.Equal)
    case "in" => Right(PublicOperator.In)
    case "greater-than" => Right(PublicOperator.GreaterThan)
    case "greater-than-or-equal" => Right(PublicOperator.GreaterThanOrEqual)
    case "less-than" => Right(PublicOperator.LessThan)
    case "less-than-or-equal" => Right(PublicOperator.LessThanOrEqual)
    case "between" => Right(PublicOperator.Between)
    case "within-distance" => Right(PublicOperator.WithinDistance)
    case other => Left(s"unsupported operator '$other'")
  }

  private def parseDirection(value: String): Either[String, SortDirection] = value match {
    case "asc" => Right(SortDirection.Asc)
    case "desc" => Right(SortDirection.Desc)
    case other => Left(s"unsupported direction '$other'")
  }

  private def parseValue(json: Json): Either[String, PublicFilterValue] =
    json.asString match {
      case Some(value) => Right(PublicFilterValue.Scalar(value))
      case None =>
        json.asArray match {
          case Some(values) =>
            sequence(values.toVector.map(_.asString.toRight("array value must be a string"))).map(PublicFilterValue.Many.apply)
          case None =>
            json.asObject match {
              case Some(obj) =>
                for {
                  lower <- obj("lower").flatMap(_.asString).toRight("bounds.lower must be a string")
                  upper <- obj("upper").flatMap(_.asString).toRight("bounds.upper must be a string")
                  lowerInclusive <- obj("lowerInclusive").flatMap(_.asBoolean).toRight("bounds.lowerInclusive must be boolean")
                  upperInclusive <- obj("upperInclusive").flatMap(_.asBoolean).toRight("bounds.upperInclusive must be boolean")
                } yield PublicFilterValue.BetweenBounds(lower, upper, lowerInclusive, upperInclusive)
              case None => Left("value must be a string, string array, or bounds object")
            }
        }
    }

  private def sequence[A](values: Vector[Either[String, A]]): Either[String, Vector[A]] =
    values.foldLeft[Either[String, Vector[A]]](Right(Vector.empty)) {
      case (Right(done), Right(value)) => Right(done :+ value)
      case (Left(error), _) => Left(error)
      case (_, Left(error)) => Left(error)
    }

  private def get[A: Decoder](cursor: io.circe.HCursor, field: String): Either[String, A] =
    cursor.get[A](field).left.map(_.message)
}
