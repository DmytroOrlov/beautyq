package leaderboard.http.tapir

import leaderboard.http.HttpApiFailure
import leaderboard.http.tapir.UuidBackedIdTapirSupport.given
import leaderboard.model.Category
import leaderboard.model.Category.CategoryId
import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.circe.*

trait CategoryTapirEndpoints {
  def getCategory: PublicEndpoint[CategoryId, HttpApiFailure, Category, Any]
  def upsertCategory: PublicEndpoint[Category, HttpApiFailure, Unit, Any]
  def getChildren: PublicEndpoint[CategoryId, HttpApiFailure, List[Category], Any]
  def getRootChildren: PublicEndpoint[Unit, HttpApiFailure, List[Category], Any]

  final def all: List[AnyEndpoint] = List(
    getRootChildren,
    getCategory,
    upsertCategory,
    getChildren,
  )
}

object CategoryTapirEndpoints extends CategoryTapirEndpoints {
  private val base = sttp.tapir.endpoint.in("category")

  val getCategory = base.get
    .errorOut(HttpApiFailureTapirSupport.singleEntityGetErrorOutput)
    .in(path[CategoryId]("id"))
    .out(jsonBody[Category])

  val upsertCategory = HttpApiFailureTapirSupport.endpointBase.in("category").post
    .in(jsonBody[Category])
    .out(emptyOutput)

  val getChildren = HttpApiFailureTapirSupport.endpointBase.in("category").get
    .in(path[CategoryId]("parentId"))
    .in("children")
    .out(jsonBody[List[Category]])

  val getRootChildren = HttpApiFailureTapirSupport.endpointBase.in("category").get
    .in("root")
    .out(jsonBody[List[Category]])
}
