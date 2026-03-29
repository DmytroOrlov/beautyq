package leaderboard.http.tapir

import io.circe.Json
import leaderboard.http.HttpApiFailure
import leaderboard.model.Category.CategoryId
import leaderboard.model.{Service, ServiceId}
import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.circe.*

trait ServiceTapirEndpoints {
  def getService: PublicEndpoint[ServiceId, HttpApiFailure, Json, Any]
  def upsertService: PublicEndpoint[Service, HttpApiFailure, Unit, Any]
  def getServicesByCategory: PublicEndpoint[CategoryId, HttpApiFailure, List[Service], Any]

  final def all: List[AnyEndpoint] = List(
    getService,
    upsertService,
    getServicesByCategory,
  )
}

object ServiceTapirEndpoints extends ServiceTapirEndpoints {
  private val base = HttpApiFailureTapirSupport.endpointBase.in("service")

  val getService = base.get
    .in(path[ServiceId]("id"))
    .out(jsonBody[Json])

  val upsertService = base.post
    .in(jsonBody[Service])
    .out(emptyOutput)

  val getServicesByCategory = base.get
    .in("category")
    .in(path[CategoryId]("categoryId"))
    .out(jsonBody[List[Service]])
}
