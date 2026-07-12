package leaderboard

import izumi.distage.testkit.scalatest.{AssertZIO, SpecZIO}
import leaderboard.api.ServiceApi
import leaderboard.http.tapir.ServiceTapirEndpoints
import leaderboard.model.{QueryFailure, Service, ServiceCode, ServiceId}
import leaderboard.model.Category.CategoryId
import leaderboard.repo.Services
import org.http4s.Status
import zio.interop.catz.*
import zio.{IO, Ref, UIO, ZIO}

class ServiceApiHttpContractSuite extends SpecZIO with AssertZIO with HttpContractTestSupport {
  private def serviceApi(state: ServiceApiContractState): ServiceApi[IO] =
    new ServiceApi[IO](state.services, ServiceTapirEndpoints)

  private def testServiceCode(id: ServiceId): ServiceCode =
    ServiceCode.unsafeFromString(s"service_${id.toString.replace("-", "")}")

  "ServiceApi current http4s contracts" should {
    "return 200 and exact service json for an existing entity" in {
      val serviceId  = ServiceId.fromString("11111111-1111-1111-1111-111111111111")
      val categoryId = CategoryId.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
      val code       = testServiceCode(serviceId)
      val service    = Service(serviceId, code, categoryId, "Cut")

      for {
        state    <- ServiceApiContractState.make
        _        <- state.setGetServiceResult(Right(Some(service)))
        response <- observe(combineApis(serviceApi(state)), get(s"/service/$serviceId"))
        _        <- assertIO(response.status === Status.Ok)
        _        <- assertIO(response.body === s"""{"id":"$serviceId","code":"${code.value}","categoryId":"$categoryId","name":"Cut"}""")
      } yield ()
    }

    "return 404 and typed error json for a missing service" in {
      val serviceId = ServiceId.fromString("22222222-2222-2222-2222-222222222222")

      for {
        state    <- ServiceApiContractState.make
        _        <- state.setGetServiceResult(Right(None))
        response <- observe(combineApis(serviceApi(state)), get(s"/service/$serviceId"))
        _        <- assertIO(response.status === Status.NotFound)
        _        <- assertIO(response.body === s"""{"code":"not_found","message":"Service '$serviceId' was not found"}""")
      } yield ()
    }

    "return 200 and exact json array for the category endpoint" in {
      val categoryId = CategoryId.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb")
      val firstId    = ServiceId.fromString("00000000-0000-0000-0000-000000000001")
      val secondId   = ServiceId.fromString("00000000-0000-0000-0000-000000000002")
      val first      = Service(firstId, testServiceCode(firstId), categoryId, "Alpha")
      val second     = Service(secondId, testServiceCode(secondId), categoryId, "Beta")

      for {
        state    <- ServiceApiContractState.make
        _        <- state.setServicesByCategoryResult(categoryId, Right(List(first, second)))
        response <- observe(combineApis(serviceApi(state)), get(s"/service/category/$categoryId"))
        _        <- assertIO(response.status === Status.Ok)
        _        <- assertIO(
          response.body === s"""[{"id":"${first.id}","code":"${first.code.value}","categoryId":"$categoryId","name":"Alpha"},{"id":"${second.id}","code":"${second.code.value}","categoryId":"$categoryId","name":"Beta"}]"""
        )
      } yield ()
    }

    "return 200 with empty body and capture the posted service payload" in {
      val serviceId  = ServiceId.fromString("33333333-3333-3333-3333-333333333333")
      val categoryId = CategoryId.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc")
      val code       = testServiceCode(serviceId)
      val payload    = s"""{"id":"$serviceId","code":"${code.value}","categoryId":"$categoryId","name":"Color"}"""

      for {
        state    <- ServiceApiContractState.make
        _        <- state.setUpsertServiceResult(Right(()))
        response <- observe(combineApis(serviceApi(state)), postJson("/service", payload))
        upserts  <- state.upserts
        _        <- assertIO(response.status === Status.Ok)
        _        <- assertIO(response.body === "")
        _        <- assertIO(upserts === Vector(Service(serviceId, code, categoryId, "Color")))
      } yield ()
    }

    "return 400 Bad Request and capture nothing for an invalid service code" in {
      val serviceId  = ServiceId.fromString("44444444-5555-6666-7777-888888888888")
      val categoryId = CategoryId.fromString("99998888-7777-6666-5555-444433332222")
      val payload    = s"""{"id":"$serviceId","code":"Invalid Service","categoryId":"$categoryId","name":"Invalid"}"""

      for {
        state    <- ServiceApiContractState.make
        response <- observe(combineApis(serviceApi(state)), postJson("/service", payload))
        upserts  <- state.upserts
        _        <- assertIO(response.status === Status.BadRequest)
        _        <- assertIO(upserts.isEmpty)
      } yield ()
    }

    "return Tapir default bad-input response for malformed UUID path params" in {
      for {
        state    <- ServiceApiContractState.make
        response <- observe(combineApis(serviceApi(state)), get("/service/not-a-uuid"))
        _        <- assertIO(response.status === Status.BadRequest)
      } yield ()
    }

    "return Tapir default bad-input response and do not hit the repo on malformed JSON body" in {
      for {
        state    <- ServiceApiContractState.make
        response <- observe(combineApis(serviceApi(state)), postJson("/service", """{"id":"abc""""))
        upserts  <- state.upserts
        _        <- assertIO(response.status === Status.BadRequest)
        _        <- assertIO(upserts.isEmpty)
      } yield ()
    }

    "return current server failure semantics when the category endpoint fails" in {
      val categoryId = CategoryId.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd")

      for {
        state    <- ServiceApiContractState.make
        _        <- state.setServicesByCategoryResult(categoryId, Left(QueryFailure.fromThrowable("get-services-by-category", new RuntimeException("services-boom"))))
        response <- observe(combineApis(serviceApi(state)), get(s"/service/category/$categoryId"))
        _        <- assertIO(response.status === Status.InternalServerError)
        _        <- assertIO(response.body === "")
      } yield ()
    }
  }
}

class ServiceApiContractState private (
  private val upsertsRef: Ref[Vector[Service]],
  private val getServiceResultRef: Ref[Either[QueryFailure, Option[Service]]],
  private val servicesByCategoryResultsRef: Ref[Map[CategoryId, Either[QueryFailure, List[Service]]]],
  private val upsertServiceResultRef: Ref[Either[QueryFailure, Unit]],
) {
  val services: Services[IO] = new Services[IO] {
    def upsertService(service: Service): IO[QueryFailure, Unit] =
      upsertServiceResultRef.get.flatMap {
        case Right(_) =>
          upsertsRef.update(_ :+ service)
        case Left(error) =>
          ZIO.fail(error)
      }

    def getService(id: leaderboard.model.ServiceId): IO[QueryFailure, Option[Service]] =
      getServiceResultRef.get.flatMap(ZIO.fromEither(_))

    def getServiceByCode(code: ServiceCode): IO[QueryFailure, Option[Service]] =
      ZIO.succeed(None)

    def getServicesByCategory(categoryId: CategoryId): IO[QueryFailure, List[Service]] =
      servicesByCategoryResultsRef.get.flatMap {
        current =>
          ZIO.fromEither(current.getOrElse(categoryId, Right(Nil)))
      }
  }

  def upserts: UIO[Vector[Service]] =
    upsertsRef.get

  def setGetServiceResult(result: Either[QueryFailure, Option[Service]]): UIO[Unit] =
    getServiceResultRef.set(result)

  def setServicesByCategoryResult(categoryId: CategoryId, result: Either[QueryFailure, List[Service]]): UIO[Unit] =
    servicesByCategoryResultsRef.update(_ + (categoryId -> result))

  def setUpsertServiceResult(result: Either[QueryFailure, Unit]): UIO[Unit] =
    upsertServiceResultRef.set(result)
}

object ServiceApiContractState {
  def make: UIO[ServiceApiContractState] =
    for {
      upserts                   <- Ref.make(Vector.empty[Service])
      getServiceResult          <- Ref.make[Either[QueryFailure, Option[Service]]](Right(None))
      servicesByCategoryResults <- Ref.make(Map.empty[CategoryId, Either[QueryFailure, List[Service]]])
      upsertServiceResult       <- Ref.make[Either[QueryFailure, Unit]](Right(()))
    } yield new ServiceApiContractState(upserts, getServiceResult, servicesByCategoryResults, upsertServiceResult)
}
