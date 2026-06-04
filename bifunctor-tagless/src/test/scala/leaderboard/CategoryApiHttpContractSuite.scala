package leaderboard

import izumi.distage.testkit.scalatest.{AssertZIO, SpecZIO}
import leaderboard.api.CategoryApi
import leaderboard.http.tapir.{CategoryTapirEndpoints, TapirHttpSupport}
import leaderboard.model.Category
import leaderboard.model.Category.{CategoryId, rootCategoryId}
import leaderboard.model.QueryFailure
import leaderboard.repo.Categories
import org.http4s.Status
import zio.interop.catz.*
import zio.{IO, Ref, UIO, ZIO}

import java.util.UUID

class CategoryApiHttpContractSuite extends SpecZIO with AssertZIO with HttpContractTestSupport {
  private def categoryApi(state: CategoryApiContractState): CategoryApi[IO] =
    new CategoryApi[IO](state.categories, CategoryTapirEndpoints, new TapirHttpSupport[IO])

  "CategoryApi current http4s contracts" should {
    "return 200 and exact category json for an existing entity" in {
      val categoryId = UUID.fromString("11111111-2222-3333-4444-555555555555")
      val parentId   = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee")
      val category   = Category(categoryId, parentId, 1, "Hair")

      for {
        state    <- CategoryApiContractState.make
        _        <- state.setGetCategoryResult(Right(Some(category)))
        response <- observe(combineApis(categoryApi(state)), get(s"/category/$categoryId"))
        _        <- assertIO(response.status === Status.Ok)
        _        <- assertIO(response.body === s"""{"id":"$categoryId","parentId":"$parentId","depth":1,"name":"Hair"}""")
      } yield ()
    }

    "return 404 and typed error json for a missing category" in {
      val categoryId = UUID.fromString("66666666-7777-8888-9999-aaaaaaaaaaaa")

      for {
        state    <- CategoryApiContractState.make
        _        <- state.setGetCategoryResult(Right(None))
        response <- observe(combineApis(categoryApi(state)), get(s"/category/$categoryId"))
        _        <- assertIO(response.status === Status.NotFound)
        _        <- assertIO(response.body === s"""{"code":"not_found","message":"Category '$categoryId' was not found"}""")
      } yield ()
    }

    "return 200 and exact json array for the children endpoint" in {
      val parentId = UUID.fromString("12345678-1234-1234-1234-123456789abc")
      val first    = Category(UUID.fromString("00000000-0000-0000-0000-000000000001"), parentId, 1, "Alpha")
      val second   = Category(UUID.fromString("00000000-0000-0000-0000-000000000002"), parentId, 1, "Beta")

      for {
        state    <- CategoryApiContractState.make
        _        <- state.setChildrenResult(parentId, Right(List(first, second)))
        response <- observe(combineApis(categoryApi(state)), get(s"/category/$parentId/children"))
        _        <- assertIO(response.status === Status.Ok)
        _        <- assertIO(
          response.body === s"""[{"id":"${first.id}","parentId":"$parentId","depth":1,"name":"Alpha"},{"id":"${second.id}","parentId":"$parentId","depth":1,"name":"Beta"}]"""
        )
      } yield ()
    }

    "return 200 and exact json array for the root endpoint" in {
      val rootChild = Category(UUID.fromString("99999999-0000-0000-0000-000000000001"), rootCategoryId, 0, "Root")

      for {
        state    <- CategoryApiContractState.make
        _        <- state.setChildrenResult(rootCategoryId, Right(List(rootChild)))
        response <- observe(combineApis(categoryApi(state)), get("/category/root"))
        _        <- assertIO(response.status === Status.Ok)
        _        <- assertIO(
          response.body === s"""[{"id":"${rootChild.id}","parentId":"$rootCategoryId","depth":0,"name":"Root"}]"""
        )
      } yield ()
    }

    "return 200 with empty body and capture the posted category payload" in {
      val categoryId = UUID.fromString("bbbbbbbb-cccc-dddd-eeee-ffffffffffff")
      val parentId   = UUID.fromString("01010101-0202-0303-0404-050505050505")
      val payload    = s"""{"id":"$categoryId","parentId":"$parentId","depth":2,"name":"Coloring"}"""

      for {
        state    <- CategoryApiContractState.make
        _        <- state.setUpsertCategoryResult(Right(()))
        response <- observe(combineApis(categoryApi(state)), postJson("/category", payload))
        upserts  <- state.upserts
        _        <- assertIO(response.status === Status.Ok)
        _        <- assertIO(response.body === "")
        _        <- assertIO(upserts === Vector(Category(categoryId, parentId, 2, "Coloring")))
      } yield ()
    }

    "return current 404 semantics for malformed UUID path params" in {
      for {
        state    <- CategoryApiContractState.make
        response <- observe(combineApis(categoryApi(state)), get("/category/not-a-uuid"))
        _        <- assertIO(response.status === Status.NotFound)
        _        <- assertIO(response.body === "Not found")
      } yield ()
    }

    "return current malformed-json semantics and do not hit the repo on malformed JSON body" in {
      for {
        state    <- CategoryApiContractState.make
        response <- observe(combineApis(categoryApi(state)), postJson("/category", """{"id":"abc""""))
        upserts  <- state.upserts
        _        <- assertIO(response.status === Status.InternalServerError)
        _        <- assertIO(response.body === "")
        _        <- assertIO(upserts.isEmpty)
      } yield ()
    }

    "return current server failure semantics when the root endpoint fails" in {
      for {
        state    <- CategoryApiContractState.make
        _        <- state.setChildrenResult(rootCategoryId, Left(QueryFailure.fromThrowable("get-root-children", new RuntimeException("children-boom"))))
        response <- observe(combineApis(categoryApi(state)), get("/category/root"))
        _        <- assertIO(response.status === Status.InternalServerError)
        _        <- assertIO(response.body === "")
      } yield ()
    }
  }
}

class CategoryApiContractState private (
  private val upsertsRef: Ref[Vector[Category]],
  private val getCategoryResultRef: Ref[Either[QueryFailure, Option[Category]]],
  private val childrenResultsRef: Ref[Map[CategoryId, Either[QueryFailure, List[Category]]]],
  private val upsertCategoryResultRef: Ref[Either[QueryFailure, Unit]],
) {
  val categories: Categories[IO] = new Categories[IO] {
    def upsertCategory(category: Category): IO[QueryFailure, Unit] =
      upsertCategoryResultRef.get.flatMap {
        case Right(_) =>
          upsertsRef.update(_ :+ category)
        case Left(error) =>
          ZIO.fail(error)
      }

    def getCategory(id: CategoryId): IO[QueryFailure, Option[Category]] =
      getCategoryResultRef.get.flatMap(ZIO.fromEither(_))

    def getChildren(parentId: CategoryId): IO[QueryFailure, List[Category]] =
      childrenResultsRef.get.flatMap {
        current =>
          ZIO.fromEither(current.getOrElse(parentId, Right(Nil)))
      }
  }

  def upserts: UIO[Vector[Category]] =
    upsertsRef.get

  def setGetCategoryResult(result: Either[QueryFailure, Option[Category]]): UIO[Unit] =
    getCategoryResultRef.set(result)

  def setChildrenResult(parentId: CategoryId, result: Either[QueryFailure, List[Category]]): UIO[Unit] =
    childrenResultsRef.update(_ + (parentId -> result))

  def setUpsertCategoryResult(result: Either[QueryFailure, Unit]): UIO[Unit] =
    upsertCategoryResultRef.set(result)
}

object CategoryApiContractState {
  def make: UIO[CategoryApiContractState] =
    for {
      upserts              <- Ref.make(Vector.empty[Category])
      getCategoryResult    <- Ref.make[Either[QueryFailure, Option[Category]]](Right(None))
      childrenResults      <- Ref.make(Map.empty[CategoryId, Either[QueryFailure, List[Category]]])
      upsertCategoryResult <- Ref.make[Either[QueryFailure, Unit]](Right(()))
    } yield new CategoryApiContractState(upserts, getCategoryResult, childrenResults, upsertCategoryResult)
}
