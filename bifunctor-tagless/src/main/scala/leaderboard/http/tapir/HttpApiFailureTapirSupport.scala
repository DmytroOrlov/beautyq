package leaderboard.http.tapir

import leaderboard.http.HttpApiFailure
import sttp.model.StatusCode
import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.circe.*

object HttpApiFailureTapirSupport {
  val errorOutput: EndpointOutput[HttpApiFailure] =
    oneOf[HttpApiFailure](
      oneOfVariantValueMatcher(
        statusCode(StatusCode.BadRequest)
          .and(jsonBody[HttpApiFailure.BadRequest])
      ) {
        case _: HttpApiFailure.BadRequest => true
        case _                            => false
      },
      oneOfVariantValueMatcher(
        statusCode.map[HttpApiFailure]((_: StatusCode) => HttpApiFailure.InternalServerError)(_ => StatusCode.InternalServerError)
      ) {
        case HttpApiFailure.InternalServerError => true
        case _                                  => false
      },
    )

  val singleEntityGetErrorOutput: EndpointOutput[HttpApiFailure] =
    oneOf[HttpApiFailure](
      oneOfVariantValueMatcher(
        statusCode(StatusCode.NotFound)
          .and(jsonBody[HttpApiFailure.NotFound])
      ) {
        case _: HttpApiFailure.NotFound => true
        case _                          => false
      },
      oneOfVariantValueMatcher(
        statusCode.map[HttpApiFailure]((_: StatusCode) => HttpApiFailure.InternalServerError)(_ => StatusCode.InternalServerError)
      ) {
        case HttpApiFailure.InternalServerError => true
        case _                                  => false
      },
    )

  val endpointBase: PublicEndpoint[Unit, HttpApiFailure, Unit, Any] =
    sttp.tapir.endpoint.errorOut(errorOutput)
}
