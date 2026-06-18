package leaderboard

import io.circe.Codec
import io.circe.generic.semiauto
import izumi.distage.testkit.scalatest.{AssertZIO, SpecZIO}
import leaderboard.http.tapir.TapirHttpSupport
import org.http4s.Status
import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.circe.*
import zio.interop.catz.*
import zio.{IO, ZIO}

class TapirHttpSupportContractSuite extends SpecZIO with AssertZIO with HttpContractTestSupport {
  private val tapirHttpSupport = new TapirHttpSupport[IO]

  private val malformedPathGetEndpoint = endpoint.get
    .in("tapir-support" / "path" / path[java.util.UUID]("id"))
    .out(stringBody)

  private val malformedPathPostEndpoint = endpoint.post
    .in("tapir-support" / "path" / path[java.util.UUID]("id"))
    .out(emptyOutput)

  private val malformedBodyEndpoint = endpoint.post
    .in("tapir-support" / "body")
    .in(jsonBody[TapirSupportBody])
    .out(emptyOutput)

  private val exceptionEndpoint = endpoint.get
    .in("tapir-support" / "exception")
    .out(stringBody)

  private val app = tapirHttpSupport
    .toRoutes(
      List(
        malformedPathGetEndpoint.serverLogicSuccess[IO[Throwable, _]](id => ZIO.succeed(id.toString)),
        malformedPathPostEndpoint.serverLogicSuccess[IO[Throwable, _]](_ => ZIO.succeed(())),
        malformedBodyEndpoint.serverLogicSuccess[IO[Throwable, _]](_ => ZIO.succeed(())),
        exceptionEndpoint.serverLogicSuccess[IO[Throwable, _]](_ => ZIO.fail(new RuntimeException("support-boom"))),
      )
    ).orNotFound

  "TapirHttpSupport defaults" should {
    "return Tapir default bad-input responses for malformed path captures across multiple methods on the same path" in {
      for {
        getResponse  <- observe(app, get("/tapir-support/path/not-a-uuid"))
        postResponse <- observe(app, postJson("/tapir-support/path/not-a-uuid", """{"ignored":true}"""))
        _            <- assertIO(getResponse.status === Status.BadRequest)
        _            <- assertIO(postResponse.status === Status.BadRequest)
      } yield ()
    }

    "return Tapir default bad-input response for malformed json body decode" in {
      for {
        response <- observe(app, postJson("/tapir-support/body", """{"name":"Kai""""))
        _        <- assertIO(response.status === Status.BadRequest)
      } yield ()
    }

    "return the Tapir default response for uncaught server logic exceptions" in {
      for {
        response <- observe(app, get("/tapir-support/exception"))
        _        <- assertIO(response.status === Status.InternalServerError)
        _        <- assertIO(response.body === "Internal server error")
      } yield ()
    }
  }
}

case class TapirSupportBody(name: String)

object TapirSupportBody {
  implicit val codec: Codec.AsObject[TapirSupportBody] = semiauto.deriveCodec
}
