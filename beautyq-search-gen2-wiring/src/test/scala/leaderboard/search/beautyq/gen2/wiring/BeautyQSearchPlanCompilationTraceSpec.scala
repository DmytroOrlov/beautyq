package leaderboard.search.beautyq.gen2.wiring

import leaderboard.search.beautyq.gen2.contract.*
import leaderboard.search.gen2.contract.{PublicOperator as _, *}
import org.scalatest.wordspec.AnyWordSpec

/** Exact golden traces built exclusively from real, validated requests (`BeautySearchRequestGen2.validate`)
  * and real parsed intents (`BeautyQIntentParserGen2.parse`), covering every plan mode, every diagnostic
  * section (facets, suppressed filters, default-browse notice, soft signal, sort) and both single- and
  * multi-hard-constraint intents. Expected strings are literal, never re-derived from the renderer itself.
  */
final class BeautyQSearchPlanCompilationTraceSpec extends AnyWordSpec {

  private val vocabulary = BeautyQIntentVocabulary.value
  private val page = PageRequest(None, PageSize.from(20).getOrElse(fail("expected a valid PageSize")))
  private val berlin = GeoPoint(BigDecimal("52.5"), BigDecimal("13.4"))

  private def rawRequest(
    query: Option[String] = None,
    filters: Vector[PublicFilterInput] = Vector.empty,
    requestedFacets: Vector[FacetId] = Vector.empty,
    sort: Vector[BeautySortInput] = Vector.empty,
    userLocation: Option[GeoPoint] = None,
  ): BeautySearchRequestGen2 =
    BeautySearchRequestGen2(query, filters, requestedFacets, sort, page, userLocation)

  private def serviceFilter(value: String): PublicFilterInput = PublicFilterInput(PublicFieldName("service"), PublicOperator.Equal, PublicFilterValue.Scalar(value), None)
  private def distanceFilter(meters: String): PublicFilterInput = PublicFilterInput(PublicFieldName("distanceMeters"), PublicOperator.WithinDistance, PublicFilterValue.Scalar(meters), None)

  private def traceOf(request: BeautySearchRequestGen2): String = {
    val validated = BeautySearchRequestGen2.validate(request) match {
      case Right(value) => value
      case Left(errors) => fail(s"fixture request failed to validate: ${errors.toVector}")
    }
    val intent = BeautyQIntentParserGen2.parse(validated, vocabulary) match {
      case Right(value) => value
      case Left(errors) => fail(s"fixture intent failed to parse: ${errors.toVector}")
    }
    val result = BeautyQSearchPlanCompiler.compile(validated, intent) match {
      case Right(value) => value
      case Left(errors) => fail(s"fixture failed to compile: ${errors.toVector}")
    }
    BeautyQSearchPlanCompilationTrace.render(validated, intent, result)
  }

  "BeautyQSearchPlanCompilationTrace.render" should {
    "render an empty request as DefaultBrowse with its notice" in {
      assert(
        traceOf(rawRequest()) ==
          """=== request ===
            |request.query=absent
            |request.facets=[]
            |request.cursor=absent
            |request.page-size=20
            |request.user-location=absent
            |
            |=== intent ===
            |intent.normalized-query=absent
            |intent.matched-rules=[]
            |intent.residual=absent
            |intent.labels=[]
            |
            |=== policy ===
            |precedence=[PublicRequest,ParsedIntent]
            |geo-origin=request.userLocation
            |facets=[service,category,price,durationMinutes]
            |groups=[provider-carousel,service-intent-carousel]
            |default-browse-code=default-browse
            |
            |=== compilation ===
            |mode=DefaultBrowse
            |semantic-labels=[]
            |matched-rules=[]
            |
            |=== plan ===
            |plan.residual-text=absent
            |plan.page cursor=absent size=20
            |plan.group[0] group id=provider-carousel key=masterLocationId:MasterLocationId size=10 representative=fields[masterId:MasterId, masterName:string, masterLocationId:MasterLocationId, locationName:string, address:string] metrics=[best-score(best-score)] order=[metric(best-score Desc), matching-document-count(Desc), key(Asc)] precision=RequireExact
            |plan.group[1] group id=service-intent-carousel key=serviceId:ServiceId size=10 representative=fields[serviceId:ServiceId, serviceName:string, categoryId:CategoryId, categoryName:string] metrics=[best-score(best-score)] order=[metric(best-score Desc), matching-document-count(Desc), key(Asc)] precision=RequireExact
            |plan.diagnostics.notice[0] code=default-browse detail=absent""".stripMargin
      )
    }

    "render a public hard filter with a requested facet as StructuredBrowse" in {
      assert(
        traceOf(rawRequest(filters = Vector(serviceFilter("manicure")), requestedFacets = Vector(FacetId("service")))) ==
          """=== request ===
            |request.query=absent
            |request.filter[0] name=service provenance=ExplicitUi constraint.terms field=serviceCode:ServiceCode values=[manicure]
            |request.facets=[service]
            |request.cursor=absent
            |request.page-size=20
            |request.user-location=absent
            |
            |=== intent ===
            |intent.normalized-query=absent
            |intent.matched-rules=[]
            |intent.residual=absent
            |intent.labels=[]
            |
            |=== policy ===
            |precedence=[PublicRequest,ParsedIntent]
            |geo-origin=request.userLocation
            |facets=[service,category,price,durationMinutes]
            |groups=[provider-carousel,service-intent-carousel]
            |default-browse-code=default-browse
            |
            |=== compilation ===
            |mode=StructuredBrowse
            |semantic-labels=[]
            |matched-rules=[]
            |
            |=== plan ===
            |plan.residual-text=absent
            |plan.applied-filter[0] provenance=ExplicitUi constraint.terms field=serviceCode:ServiceCode values=[manicure]
            |plan.page cursor=absent size=20
            |plan.facet[0] facet.terms id=service field=serviceCode:ServiceCode size=10 order=CountDescThenKeyAsc counting=AllAppliedHardFilters
            |plan.group[0] group id=provider-carousel key=masterLocationId:MasterLocationId size=10 representative=fields[masterId:MasterId, masterName:string, masterLocationId:MasterLocationId, locationName:string, address:string] metrics=[best-score(best-score)] order=[metric(best-score Desc), matching-document-count(Desc), key(Asc)] precision=RequireExact
            |plan.group[1] group id=service-intent-carousel key=serviceId:ServiceId size=10 representative=fields[serviceId:ServiceId, serviceName:string, categoryId:CategoryId, categoryName:string] metrics=[best-score(best-score)] order=[metric(best-score Desc), matching-document-count(Desc), key(Asc)] precision=RequireExact""".stripMargin
      )
    }

    "render a public geo-radius filter resolved against the user location as StructuredBrowse" in {
      assert(
        traceOf(rawRequest(filters = Vector(distanceFilter("2000")), userLocation = Some(berlin))) ==
          """=== request ===
            |request.query=absent
            |request.filter[0] name=distanceMeters provenance=ExplicitUi geo-radius field=location:geo-point radius=2000
            |request.facets=[]
            |request.cursor=absent
            |request.page-size=20
            |request.user-location=52.5,13.4
            |
            |=== intent ===
            |intent.normalized-query=absent
            |intent.matched-rules=[]
            |intent.residual=absent
            |intent.labels=[]
            |
            |=== policy ===
            |precedence=[PublicRequest,ParsedIntent]
            |geo-origin=request.userLocation
            |facets=[service,category,price,durationMinutes]
            |groups=[provider-carousel,service-intent-carousel]
            |default-browse-code=default-browse
            |
            |=== compilation ===
            |mode=StructuredBrowse
            |semantic-labels=[]
            |matched-rules=[]
            |
            |=== plan ===
            |plan.residual-text=absent
            |plan.applied-filter[0] provenance=ExplicitUi constraint.geo-distance-filter field=location:geo-point origin=52.5,13.4 radius=2000
            |plan.page cursor=absent size=20
            |plan.group[0] group id=provider-carousel key=masterLocationId:MasterLocationId size=10 representative=fields[masterId:MasterId, masterName:string, masterLocationId:MasterLocationId, locationName:string, address:string] metrics=[best-score(best-score)] order=[metric(best-score Desc), matching-document-count(Desc), key(Asc)] precision=RequireExact
            |plan.group[1] group id=service-intent-carousel key=serviceId:ServiceId size=10 representative=fields[serviceId:ServiceId, serviceName:string, categoryId:CategoryId, categoryName:string] metrics=[best-score(best-score)] order=[metric(best-score Desc), matching-document-count(Desc), key(Asc)] precision=RequireExact""".stripMargin
      )
    }

    "render a public sort with no filters as StructuredBrowse" in {
      assert(
        traceOf(rawRequest(sort = Vector(BeautySortInput(PublicSortName("price"), SortDirection.Asc)))) ==
          """=== request ===
            |request.query=absent
            |request.facets=[]
            |request.sort[0] sort.field-value field=priceFrom:decimal direction=Asc
            |request.cursor=absent
            |request.page-size=20
            |request.user-location=absent
            |
            |=== intent ===
            |intent.normalized-query=absent
            |intent.matched-rules=[]
            |intent.residual=absent
            |intent.labels=[]
            |
            |=== policy ===
            |precedence=[PublicRequest,ParsedIntent]
            |geo-origin=request.userLocation
            |facets=[service,category,price,durationMinutes]
            |groups=[provider-carousel,service-intent-carousel]
            |default-browse-code=default-browse
            |
            |=== compilation ===
            |mode=StructuredBrowse
            |semantic-labels=[]
            |matched-rules=[]
            |
            |=== plan ===
            |plan.residual-text=absent
            |plan.sort[0] sort.field-value field=priceFrom:decimal direction=Asc
            |plan.page cursor=absent size=20
            |plan.group[0] group id=provider-carousel key=masterLocationId:MasterLocationId size=10 representative=fields[masterId:MasterId, masterName:string, masterLocationId:MasterLocationId, locationName:string, address:string] metrics=[best-score(best-score)] order=[metric(best-score Desc), matching-document-count(Desc), key(Asc)] precision=RequireExact
            |plan.group[1] group id=service-intent-carousel key=serviceId:ServiceId size=10 representative=fields[serviceId:ServiceId, serviceName:string, categoryId:CategoryId, categoryName:string] metrics=[best-score(best-score)] order=[metric(best-score Desc), matching-document-count(Desc), key(Asc)] precision=RequireExact""".stripMargin
      )
    }

    "render an ExplicitUi/FacetSelection equivalent duplicate as a suppressed diagnostic" in {
      val selected = PublicFilterInput(PublicFieldName("service"), PublicOperator.Equal, PublicFilterValue.Scalar("manicure"), Some(FacetSelectionId("sel-1")))
      assert(
        traceOf(rawRequest(filters = Vector(serviceFilter("manicure"), selected))) ==
          """=== request ===
            |request.query=absent
            |request.filter[0] name=service provenance=ExplicitUi constraint.terms field=serviceCode:ServiceCode values=[manicure]
            |request.filter[1] name=service provenance=FacetSelection(sel-1) constraint.terms field=serviceCode:ServiceCode values=[manicure]
            |request.facets=[]
            |request.cursor=absent
            |request.page-size=20
            |request.user-location=absent
            |
            |=== intent ===
            |intent.normalized-query=absent
            |intent.matched-rules=[]
            |intent.residual=absent
            |intent.labels=[]
            |
            |=== policy ===
            |precedence=[PublicRequest,ParsedIntent]
            |geo-origin=request.userLocation
            |facets=[service,category,price,durationMinutes]
            |groups=[provider-carousel,service-intent-carousel]
            |default-browse-code=default-browse
            |
            |=== compilation ===
            |mode=StructuredBrowse
            |semantic-labels=[]
            |matched-rules=[]
            |
            |=== plan ===
            |plan.residual-text=absent
            |plan.applied-filter[0] provenance=ExplicitUi constraint.terms field=serviceCode:ServiceCode values=[manicure]
            |plan.page cursor=absent size=20
            |plan.group[0] group id=provider-carousel key=masterLocationId:MasterLocationId size=10 representative=fields[masterId:MasterId, masterName:string, masterLocationId:MasterLocationId, locationName:string, address:string] metrics=[best-score(best-score)] order=[metric(best-score Desc), matching-document-count(Desc), key(Asc)] precision=RequireExact
            |plan.group[1] group id=service-intent-carousel key=serviceId:ServiceId size=10 representative=fields[serviceId:ServiceId, serviceName:string, categoryId:CategoryId, categoryName:string] metrics=[best-score(best-score)] order=[metric(best-score Desc), matching-document-count(Desc), key(Asc)] precision=RequireExact
            |plan.diagnostics.suppressed-filter[0] provenance=FacetSelection(sel-1) constraint.terms field=serviceCode:ServiceCode values=[manicure] reason=EquivalentDuplicate""".stripMargin
      )
    }

    "render a parsed-only single-hard-constraint query as SemanticSearch" in {
      assert(
        traceOf(rawRequest(query = Some("ресницы"))) ==
          """=== request ===
            |request.query=present="ресницы"
            |request.facets=[]
            |request.cursor=absent
            |request.page-size=20
            |request.user-location=absent
            |
            |=== intent ===
            |intent.normalized-query="ресницы"
            |intent.matched-rules=[r006]
            |intent.hard[0] provenance=ParsedHard constraint.terms field=serviceCode:ServiceCode values=[lashes]
            |intent.residual=absent
            |intent.labels=[service:lashes:lashes]
            |
            |=== policy ===
            |precedence=[PublicRequest,ParsedIntent]
            |geo-origin=request.userLocation
            |facets=[service,category,price,durationMinutes]
            |groups=[provider-carousel,service-intent-carousel]
            |default-browse-code=default-browse
            |
            |=== compilation ===
            |mode=SemanticSearch
            |semantic-labels=[service:lashes:lashes]
            |matched-rules=[r006]
            |
            |=== plan ===
            |plan.residual-text=absent
            |plan.applied-filter[0] provenance=ParsedHard constraint.terms field=serviceCode:ServiceCode values=[lashes]
            |plan.page cursor=absent size=20
            |plan.group[0] group id=provider-carousel key=masterLocationId:MasterLocationId size=10 representative=fields[masterId:MasterId, masterName:string, masterLocationId:MasterLocationId, locationName:string, address:string] metrics=[best-score(best-score)] order=[metric(best-score Desc), matching-document-count(Desc), key(Asc)] precision=RequireExact
            |plan.group[1] group id=service-intent-carousel key=serviceId:ServiceId size=10 representative=fields[serviceId:ServiceId, serviceName:string, categoryId:CategoryId, categoryName:string] metrics=[best-score(best-score)] order=[metric(best-score Desc), matching-document-count(Desc), key(Asc)] precision=RequireExact""".stripMargin
      )
    }

    "render a parsed constraint in a different slot from an unaffected public filter, both applied" in {
      assert(
        traceOf(rawRequest(query = Some("шелак"), filters = Vector(serviceFilter("manicure")))) ==
          """=== request ===
            |request.query=present="шелак"
            |request.filter[0] name=service provenance=ExplicitUi constraint.terms field=serviceCode:ServiceCode values=[manicure]
            |request.facets=[]
            |request.cursor=absent
            |request.page-size=20
            |request.user-location=absent
            |
            |=== intent ===
            |intent.normalized-query="шелак"
            |intent.matched-rules=[r022]
            |intent.hard[0] provenance=ParsedHard constraint.terms field=enumAttributes.nail_coating_type:string values=[shellac]
            |intent.residual=absent
            |intent.labels=[attribute.enum:nail_coating_type=shellac:nail coating type: shellac]
            |
            |=== policy ===
            |precedence=[PublicRequest,ParsedIntent]
            |geo-origin=request.userLocation
            |facets=[service,category,price,durationMinutes]
            |groups=[provider-carousel,service-intent-carousel]
            |default-browse-code=default-browse
            |
            |=== compilation ===
            |mode=SemanticSearch
            |semantic-labels=[attribute.enum:nail_coating_type=shellac:nail coating type: shellac]
            |matched-rules=[r022]
            |
            |=== plan ===
            |plan.residual-text=absent
            |plan.applied-filter[0] provenance=ExplicitUi constraint.terms field=serviceCode:ServiceCode values=[manicure]
            |plan.applied-filter[1] provenance=ParsedHard constraint.terms field=enumAttributes.nail_coating_type:string values=[shellac]
            |plan.page cursor=absent size=20
            |plan.group[0] group id=provider-carousel key=masterLocationId:MasterLocationId size=10 representative=fields[masterId:MasterId, masterName:string, masterLocationId:MasterLocationId, locationName:string, address:string] metrics=[best-score(best-score)] order=[metric(best-score Desc), matching-document-count(Desc), key(Asc)] precision=RequireExact
            |plan.group[1] group id=service-intent-carousel key=serviceId:ServiceId size=10 representative=fields[serviceId:ServiceId, serviceName:string, categoryId:CategoryId, categoryName:string] metrics=[best-score(best-score)] order=[metric(best-score Desc), matching-document-count(Desc), key(Asc)] precision=RequireExact""".stripMargin
      )
    }

    "render a NearUser-only query as SemanticSearch with a soft signal and no hard constraints" in {
      assert(
        traceOf(rawRequest(query = Some("рядом"), userLocation = Some(berlin))) ==
          """=== request ===
            |request.query=present="рядом"
            |request.facets=[]
            |request.cursor=absent
            |request.page-size=20
            |request.user-location=52.5,13.4
            |
            |=== intent ===
            |intent.normalized-query="рядом"
            |intent.matched-rules=[r087]
            |intent.soft[0] signal.geo-proximity field=location:geo-point origin=52.5,13.4
            |intent.residual=absent
            |intent.labels=[location:near-user:near user]
            |
            |=== policy ===
            |precedence=[PublicRequest,ParsedIntent]
            |geo-origin=request.userLocation
            |facets=[service,category,price,durationMinutes]
            |groups=[provider-carousel,service-intent-carousel]
            |default-browse-code=default-browse
            |
            |=== compilation ===
            |mode=SemanticSearch
            |semantic-labels=[location:near-user:near user]
            |matched-rules=[r087]
            |
            |=== plan ===
            |plan.residual-text=absent
            |plan.soft-signal[0] signal.geo-proximity field=location:geo-point origin=52.5,13.4
            |plan.page cursor=absent size=20
            |plan.group[0] group id=provider-carousel key=masterLocationId:MasterLocationId size=10 representative=fields[masterId:MasterId, masterName:string, masterLocationId:MasterLocationId, locationName:string, address:string] metrics=[best-score(best-score), min-geo-distance(min-distance field=location:geo-point origin=52.5,13.4)] order=[metric(best-score Desc), matching-document-count(Desc), metric(min-distance Asc), key(Asc)] precision=RequireExact
            |plan.group[1] group id=service-intent-carousel key=serviceId:ServiceId size=10 representative=fields[serviceId:ServiceId, serviceName:string, categoryId:CategoryId, categoryName:string] metrics=[best-score(best-score)] order=[metric(best-score Desc), matching-document-count(Desc), key(Asc)] precision=RequireExact""".stripMargin
      )
    }

    "render a fully unmatched query as SemanticSearch via residual text alone" in {
      assert(
        traceOf(rawRequest(query = Some("something entirely unmatched"))) ==
          """=== request ===
            |request.query=present="something entirely unmatched"
            |request.facets=[]
            |request.cursor=absent
            |request.page-size=20
            |request.user-location=absent
            |
            |=== intent ===
            |intent.normalized-query="something entirely unmatched"
            |intent.matched-rules=[]
            |intent.residual=something entirely unmatched
            |intent.labels=[]
            |
            |=== policy ===
            |precedence=[PublicRequest,ParsedIntent]
            |geo-origin=request.userLocation
            |facets=[service,category,price,durationMinutes]
            |groups=[provider-carousel,service-intent-carousel]
            |default-browse-code=default-browse
            |
            |=== compilation ===
            |mode=SemanticSearch
            |semantic-labels=[]
            |matched-rules=[]
            |
            |=== plan ===
            |plan.residual-text="something entirely unmatched"
            |plan.page cursor=absent size=20
            |plan.group[0] group id=provider-carousel key=masterLocationId:MasterLocationId size=10 representative=fields[masterId:MasterId, masterName:string, masterLocationId:MasterLocationId, locationName:string, address:string] metrics=[best-score(best-score)] order=[metric(best-score Desc), matching-document-count(Desc), key(Asc)] precision=RequireExact
            |plan.group[1] group id=service-intent-carousel key=serviceId:ServiceId size=10 representative=fields[serviceId:ServiceId, serviceName:string, categoryId:CategoryId, categoryName:string] metrics=[best-score(best-score)] order=[metric(best-score Desc), matching-document-count(Desc), key(Asc)] precision=RequireExact""".stripMargin
      )
    }

    "render a category match with multiple semantic-only labels as SemanticSearch, alongside its one hard filter" in {
      assert(
        traceOf(rawRequest(query = Some("ногти"))) ==
          """=== request ===
            |request.query=present="ногти"
            |request.facets=[]
            |request.cursor=absent
            |request.page-size=20
            |request.user-location=absent
            |
            |=== intent ===
            |intent.normalized-query="ногти"
            |intent.matched-rules=[r013]
            |intent.hard[0] provenance=ParsedHard constraint.terms field=categoryCode:CategoryCode values=[nails]
            |intent.residual=absent
            |intent.labels=[category:nails:nails,service:manicure:manicure,service:pedicure:pedicure,service:nail_modeling:nail modeling]
            |
            |=== policy ===
            |precedence=[PublicRequest,ParsedIntent]
            |geo-origin=request.userLocation
            |facets=[service,category,price,durationMinutes]
            |groups=[provider-carousel,service-intent-carousel]
            |default-browse-code=default-browse
            |
            |=== compilation ===
            |mode=SemanticSearch
            |semantic-labels=[category:nails:nails,service:manicure:manicure,service:pedicure:pedicure,service:nail_modeling:nail modeling]
            |matched-rules=[r013]
            |
            |=== plan ===
            |plan.residual-text=absent
            |plan.applied-filter[0] provenance=ParsedHard constraint.terms field=categoryCode:CategoryCode values=[nails]
            |plan.page cursor=absent size=20
            |plan.group[0] group id=provider-carousel key=masterLocationId:MasterLocationId size=10 representative=fields[masterId:MasterId, masterName:string, masterLocationId:MasterLocationId, locationName:string, address:string] metrics=[best-score(best-score)] order=[metric(best-score Desc), matching-document-count(Desc), key(Asc)] precision=RequireExact
            |plan.group[1] group id=service-intent-carousel key=serviceId:ServiceId size=10 representative=fields[serviceId:ServiceId, serviceName:string, categoryId:CategoryId, categoryName:string] metrics=[best-score(best-score)] order=[metric(best-score Desc), matching-document-count(Desc), key(Asc)] precision=RequireExact""".stripMargin
      )
    }

    "render budget-extracted text as an IntervalOverlap constraint alongside its matched rule's other hard constraints" in {
      assert(
        traceOf(rawRequest(query = Some("маникюр under 50"))) ==
          """=== request ===
            |request.query=present="маникюр under 50"
            |request.facets=[]
            |request.cursor=absent
            |request.page-size=20
            |request.user-location=absent
            |
            |=== intent ===
            |intent.normalized-query="маникюр"
            |intent.matched-rules=[r001]
            |intent.hard[0] provenance=ParsedHard constraint.terms field=serviceCode:ServiceCode values=[manicure]
            |intent.hard[1] provenance=ParsedHard constraint.terms field=enumAttributes.nail_service_type:string values=[manicure]
            |intent.hard[2] provenance=ParsedHard constraint.interval-overlap from=priceFrom:decimal to=priceTo:decimal bounds=[unbounded, inclusive(50)]
            |intent.residual=absent
            |intent.labels=[service:manicure:manicure,attribute.enum:nail_service_type=manicure:nail service type: manicure]
            |
            |=== policy ===
            |precedence=[PublicRequest,ParsedIntent]
            |geo-origin=request.userLocation
            |facets=[service,category,price,durationMinutes]
            |groups=[provider-carousel,service-intent-carousel]
            |default-browse-code=default-browse
            |
            |=== compilation ===
            |mode=SemanticSearch
            |semantic-labels=[service:manicure:manicure,attribute.enum:nail_service_type=manicure:nail service type: manicure]
            |matched-rules=[r001]
            |
            |=== plan ===
            |plan.residual-text=absent
            |plan.applied-filter[0] provenance=ParsedHard constraint.terms field=serviceCode:ServiceCode values=[manicure]
            |plan.applied-filter[1] provenance=ParsedHard constraint.terms field=enumAttributes.nail_service_type:string values=[manicure]
            |plan.applied-filter[2] provenance=ParsedHard constraint.interval-overlap from=priceFrom:decimal to=priceTo:decimal bounds=[unbounded, inclusive(50)]
            |plan.page cursor=absent size=20
            |plan.group[0] group id=provider-carousel key=masterLocationId:MasterLocationId size=10 representative=fields[masterId:MasterId, masterName:string, masterLocationId:MasterLocationId, locationName:string, address:string] metrics=[best-score(best-score)] order=[metric(best-score Desc), matching-document-count(Desc), key(Asc)] precision=RequireExact
            |plan.group[1] group id=service-intent-carousel key=serviceId:ServiceId size=10 representative=fields[serviceId:ServiceId, serviceName:string, categoryId:CategoryId, categoryName:string] metrics=[best-score(best-score)] order=[metric(best-score Desc), matching-document-count(Desc), key(Asc)] precision=RequireExact""".stripMargin
      )
    }

    "render an int-attribute hard constraint alongside a labeled service hard constraint, both applied" in {
      assert(
        traceOf(rawRequest(query = Some("6 сеансов"))) ==
          """=== request ===
            |request.query=present="6 сеансов"
            |request.facets=[]
            |request.cursor=absent
            |request.page-size=20
            |request.user-location=absent
            |
            |=== intent ===
            |intent.normalized-query="6 сеансов"
            |intent.matched-rules=[r074]
            |intent.hard[0] provenance=ParsedHard constraint.terms field=serviceCode:ServiceCode values=[hair_removal]
            |intent.hard[1] provenance=ParsedHard constraint.number-range field=intAttributes.session_count:int bounds=[inclusive(6), inclusive(6)]
            |intent.residual=absent
            |intent.labels=[service:hair_removal:hair removal]
            |
            |=== policy ===
            |precedence=[PublicRequest,ParsedIntent]
            |geo-origin=request.userLocation
            |facets=[service,category,price,durationMinutes]
            |groups=[provider-carousel,service-intent-carousel]
            |default-browse-code=default-browse
            |
            |=== compilation ===
            |mode=SemanticSearch
            |semantic-labels=[service:hair_removal:hair removal]
            |matched-rules=[r074]
            |
            |=== plan ===
            |plan.residual-text=absent
            |plan.applied-filter[0] provenance=ParsedHard constraint.terms field=serviceCode:ServiceCode values=[hair_removal]
            |plan.applied-filter[1] provenance=ParsedHard constraint.number-range field=intAttributes.session_count:int bounds=[inclusive(6), inclusive(6)]
            |plan.page cursor=absent size=20
            |plan.group[0] group id=provider-carousel key=masterLocationId:MasterLocationId size=10 representative=fields[masterId:MasterId, masterName:string, masterLocationId:MasterLocationId, locationName:string, address:string] metrics=[best-score(best-score)] order=[metric(best-score Desc), matching-document-count(Desc), key(Asc)] precision=RequireExact
            |plan.group[1] group id=service-intent-carousel key=serviceId:ServiceId size=10 representative=fields[serviceId:ServiceId, serviceName:string, categoryId:CategoryId, categoryName:string] metrics=[best-score(best-score)] order=[metric(best-score Desc), matching-document-count(Desc), key(Asc)] precision=RequireExact""".stripMargin
      )
    }

    "trace the broad self-care correction through r088 and its exact candidate allowlist" in {
      val trace = traceOf(rawRequest(query = Some("хочу привести себя в порядок")))
      assert(trace.contains("intent.matched-rules=[r088]"))
      assert(trace.contains("intent.hard[0] provenance=ParsedHard constraint.terms field=serviceCode:ServiceCode values=[brows, facial, lashes, manicure]"))
      assert(trace.contains("intent.labels=[service-any:manicure,lashes,brows,facial:manicure, lashes, brows, facial]"))
      assert(trace.contains("matched-rules=[r088]"))
      assert(!trace.contains("pmu_area"))
      assert(!trace.contains("r087"))
    }

    "trace the BB Glow paraphrase through r062 and all three declared constraints" in {
      val trace = traceOf(rawRequest(query = Some("хочу чтобы тон лица выглядел ровнее без ежедневного макияжа")))
      assert(trace.contains("intent.matched-rules=[r062]"))
      assert(trace.contains("field=serviceCode:ServiceCode values=[facial]"))
      assert(trace.contains("field=enumAttributes.facial_treatment_type:string values=[bb_glow]"))
      assert(trace.contains("field=enumAttributes.body_area:string values=[face]"))
      assert(!trace.contains("pmu_area"))
    }

    "trace the powder-brow paraphrase through r058 rather than generic r007" in {
      val trace = traceOf(rawRequest(query = Some("брови с мягким пудровым эффектом надолго")))
      assert(trace.contains("intent.matched-rules=[r058]"))
      assert(trace.contains("field=serviceCode:ServiceCode values=[pmu]"))
      assert(trace.contains("field=enumAttributes.pmu_area:string values=[brows]"))
      assert(!trace.contains("matched-rules=[r007]"))
      assert(!trace.contains("brow_service_type"))
    }
  }
}
