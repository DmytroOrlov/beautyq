package leaderboard.http.tapir

import io.circe.Json
import leaderboard.model.Category.CategoryId
import leaderboard.model.{Service, ServiceId}
import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.circe.*

trait ServiceTapirEndpoints {
  def getService: PublicEndpoint[ServiceId, Unit, Json, Any]
  def upsertService: PublicEndpoint[Service, Unit, Unit, Any]
  def getServicesByCategory: PublicEndpoint[CategoryId, Unit, List[Service], Any]

  final def all: List[AnyEndpoint] = List(
    getService,
    upsertService,
    getServicesByCategory,
  )
}

object ServiceTapirEndpoints extends ServiceTapirEndpoints {
  private val base = endpoint.in("service")

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
