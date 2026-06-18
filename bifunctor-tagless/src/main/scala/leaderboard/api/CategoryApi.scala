package leaderboard.api

import cats.effect.Async
import izumi.functional.bio.Error2
import leaderboard.http.HttpApiFailure
import leaderboard.http.tapir.{CategoryTapirEndpoints, TapirHttpSupport}
import leaderboard.model.Category.rootCategoryId
import leaderboard.repo.Categories
import org.http4s.HttpRoutes

class CategoryApi[F[+_, +_]: Error2](
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
        getRootChildren.serverLogic[F[Throwable, _]](_ => HttpApiFailure.fromQueryEffect(categories.getChildren(rootCategoryId))),
        getCategory.serverLogic[F[Throwable, _]](
          categoryId => async.map(HttpApiFailure.fromQueryEffect(categories.getCategory(categoryId))) {
            _.flatMap(_.toRight(HttpApiFailure.NotFound.category(categoryId)))
          }
        ),
        upsertCategory.serverLogic[F[Throwable, _]](category => HttpApiFailure.fromQueryEffect(categories.upsertCategory(category))),
        getChildren.serverLogic[F[Throwable, _]](parentId => HttpApiFailure.fromQueryEffect(categories.getChildren(parentId))),
      )
    }
}
