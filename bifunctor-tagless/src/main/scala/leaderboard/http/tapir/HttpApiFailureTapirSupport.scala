package leaderboard.http.tapir

import leaderboard.http.HttpApiFailure
import sttp.model.StatusCode
import sttp.tapir.{EndpointOutput, PublicEndpoint, statusCode}

object HttpApiFailureTapirSupport {
  val errorOutput: EndpointOutput[HttpApiFailure] =
    statusCode.map[HttpApiFailure]((_: StatusCode) => HttpApiFailure.InternalServerError)(_ => StatusCode.InternalServerError)

  val endpointBase: PublicEndpoint[Unit, HttpApiFailure, Unit, Any] =
    sttp.tapir.endpoint.errorOut(errorOutput)
}
