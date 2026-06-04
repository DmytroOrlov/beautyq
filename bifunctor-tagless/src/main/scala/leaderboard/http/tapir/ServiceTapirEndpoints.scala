package leaderboard.http.tapir

import leaderboard.http.HttpApiFailure
import leaderboard.model.Category.CategoryId
import leaderboard.model.{Service, ServiceId}
import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.circe.*

trait ServiceTapirEndpoints {
  def getService: PublicEndpoint[ServiceId, HttpApiFailure, Service, Any]
  def upsertService: PublicEndpoint[Service, HttpApiFailure, Unit, Any]
  def getServicesByCategory: PublicEndpoint[CategoryId, HttpApiFailure, List[Service], Any]

  final def all: List[AnyEndpoint] = List(
    getService,
    upsertService,
    getServicesByCategory,
  )
}

object ServiceTapirEndpoints extends ServiceTapirEndpoints {
  private val base = sttp.tapir.endpoint.in("service")

  val getService = base.get
    .errorOut(HttpApiFailureTapirSupport.serviceGetErrorOutput)
    .in(path[ServiceId]("id"))
    .out(jsonBody[Service])

  val upsertService = HttpApiFailureTapirSupport.endpointBase.in("service").post
    .in(jsonBody[Service])
    .out(emptyOutput)

  val getServicesByCategory = HttpApiFailureTapirSupport.endpointBase.in("service").get
    .in("category")
    .in(path[CategoryId]("categoryId"))
    .out(jsonBody[List[Service]])
}
