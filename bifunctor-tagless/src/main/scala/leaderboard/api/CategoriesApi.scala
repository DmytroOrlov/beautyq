package leaderboard.api

import io.circe.syntax.*
import izumi.functional.bio.{Async2, Fork2, Primitives2}
import izumi.functional.bio.catz.*
import leaderboard.model.Category
import leaderboard.model.Category.rootCategoryId
import leaderboard.repo.Categories
import org.http4s.HttpRoutes
import org.http4s.circe.*
import org.http4s.dsl.Http4sDsl

final class CategoriesApi[F[+_, +_]: Async2: Fork2: Primitives2](
  dsl: Http4sDsl[F[Throwable, _]],
  categories: Categories[F],
) extends HttpApi[F] {

  import dsl.*

  override def http: HttpRoutes[F[Throwable, _]] = {
    HttpRoutes.of {
      case GET -> Root / "category" / UUIDVar(categoryId) =>
        Ok(categories.getCategory(categoryId).map(_.asJson))

      case rq @ POST -> Root / "category" =>
        Ok(for {
          category <- rq.decodeJson[Category]
          _        <- categories.upsertCategory(category)
        } yield ())

      case GET -> Root / "category" / UUIDVar(parentId) / "children" =>
        Ok(categories.getChildren(parentId).map(_.asJson))

      case GET -> Root / "category" / "root" =>
        Ok(categories.getChildren(rootCategoryId).map(_.asJson))
    }
  }
}
