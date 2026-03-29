package leaderboard.http.tapir

import io.circe.Json
import leaderboard.model.Category
import leaderboard.model.Category.CategoryId
import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.circe.*

trait CategoryTapirEndpoints {
  def getCategory: PublicEndpoint[CategoryId, Unit, Json, Any]
  def upsertCategory: PublicEndpoint[Category, Unit, Unit, Any]
  def getChildren: PublicEndpoint[CategoryId, Unit, List[Category], Any]
  def getRootChildren: PublicEndpoint[Unit, Unit, List[Category], Any]

  final def all: List[AnyEndpoint] = List(
    getCategory,
    upsertCategory,
    getChildren,
    getRootChildren,
  )
}

object CategoryTapirEndpoints extends CategoryTapirEndpoints {
  private val base = endpoint.in("category")

  val getCategory = base.get
    .in(path[CategoryId]("id"))
    .out(jsonBody[Json])

  val upsertCategory = base.post
    .in(jsonBody[Category])
    .out(emptyOutput)

  val getChildren = base.get
    .in(path[CategoryId]("parentId"))
    .in("children")
    .out(jsonBody[List[Category]])

  val getRootChildren = base.get
    .in("root")
    .out(jsonBody[List[Category]])
}
