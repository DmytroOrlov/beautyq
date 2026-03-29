package leaderboard.api

import cats.effect.Async
import io.circe.Json
import io.circe.syntax.*
import leaderboard.http.tapir.{CategoryTapirEndpoints, TapirHttpSupport}
import leaderboard.model.Category.rootCategoryId
import leaderboard.repo.Categories
import org.http4s.HttpRoutes

final class CategoryApi[F[+_, +_]](
  categories: Categories[F],
  tapirEndpoints: CategoryTapirEndpoints,
  tapirHttpSupport: TapirHttpSupport[F],
)(implicit
  async: Async[F[Throwable, _]]
) extends HttpApi[F] {
  def http: HttpRoutes[F[Throwable, _]] =
    tapirHttpSupport.toRoutes {
      import tapirEndpoints.*
      List(
        getCategory.serverLogicSuccess[F[Throwable, _]](categoryId => async.map(categories.getCategory(categoryId))(_.fold[Json](Json.Null)(_.asJson))),
        upsertCategory.serverLogicSuccess[F[Throwable, _]](categories.upsertCategory),
        getChildren.serverLogicSuccess[F[Throwable, _]](parentId => categories.getChildren(parentId)),
        getRootChildren.serverLogicSuccess[F[Throwable, _]](_ => categories.getChildren(rootCategoryId)),
      )
    }
}
