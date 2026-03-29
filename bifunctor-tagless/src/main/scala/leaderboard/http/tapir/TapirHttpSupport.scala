package leaderboard.http.tapir

import cats.effect.Async
import org.http4s.HttpRoutes
import sttp.capabilities.fs2.Fs2Streams
import sttp.model.StatusCode
import sttp.tapir.integ.cats.effect.CatsMonadError
import sttp.tapir.{EndpointIO, EndpointInput, statusCode}
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.http4s.{Http4sServerInterpreter, Http4sServerOptions}
import sttp.tapir.server.interceptor.DecodeFailureContext
import sttp.tapir.server.interceptor.decodefailure.{DecodeFailureHandler, DefaultDecodeFailureHandler}
import sttp.tapir.server.interceptor.exception.ExceptionHandler
import sttp.tapir.server.model.ValuedEndpointOutput

final class TapirHttpSupport[F[+_, +_]](implicit async: Async[F[Throwable, _]]) {
  def toRoutes(
    endpoints: List[ServerEndpoint[Fs2Streams[F[Throwable, _]], F[Throwable, _]]]
  ): HttpRoutes[F[Throwable, _]] =
    Http4sServerInterpreter[F[Throwable, _]](serverOptions).toRoutes(endpoints)

  // Keep current http4s contracts during the migration:
  // - malformed path params should still fall through to route-level 404
  // - malformed body decode should still surface as 500 with an empty body
  private val currentContractDecodeFailureHandler: DecodeFailureHandler[F[Throwable, _]] =
    DecodeFailureHandler { ctx =>
      if (isPathCaptureFailure(ctx)) {
        async.pure(None)
      } else if (isBodyDecodeFailure(ctx)) {
        async.pure(Some(TapirHttpSupport.internalServerErrorOutput))
      } else {
        DefaultDecodeFailureHandler[F[Throwable, _]](ctx)(new CatsMonadError[F[Throwable, _]])
      }
    }

  private val currentContractExceptionHandler: ExceptionHandler[F[Throwable, _]] =
    ExceptionHandler.pure(_ => Some(TapirHttpSupport.internalServerErrorOutput))

  private lazy val serverOptions: Http4sServerOptions[F[Throwable, _]] =
    Http4sServerOptions
      .customiseInterceptors[F[Throwable, _]]
      .rejectHandler(None)
      .decodeFailureHandler(currentContractDecodeFailureHandler)
      .exceptionHandler(currentContractExceptionHandler)
      .options

  private def isPathCaptureFailure(ctx: DecodeFailureContext): Boolean =
    ctx.failingInput match {
      case _: EndpointInput.PathCapture[_] => true
      case _                               => false
    }

  private def isBodyDecodeFailure(ctx: DecodeFailureContext): Boolean =
    ctx.failingInput match {
      case _: EndpointIO.Body[_, _]          => true
      case _: EndpointIO.OneOfBody[_, _]     => true
      case _: EndpointIO.StreamBodyWrapper[_, _] => true
      case _                                 => false
    }
}

object TapirHttpSupport {
  private val internalServerErrorOutput: ValuedEndpointOutput[StatusCode] =
    ValuedEndpointOutput(statusCode, StatusCode.InternalServerError)
}
