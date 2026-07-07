package leaderboard.http

import cats.effect.Async
import cats.syntax.all.*
import com.comcast.ip4s.Port
import fs2.io.net.Network
import izumi.distage.model.definition.Lifecycle
import leaderboard.api.HttpApi
import leaderboard.search.startup.BeautyQManagedLocalSearchDataReady
import leaderboard.seed.BeautyQSeedReady
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.Server

import scala.annotation.unused

case class HttpServer(
  server: Server
)

object HttpServer {

  class Impl[F[+_, +_]](
    @unused seedReady: BeautyQSeedReady,
    @unused searchDataReady: BeautyQManagedLocalSearchDataReady,
    allHttpApis: Set[HttpApi[F]]
  )(implicit
    async: Async[F[Throwable, _]]
  ) extends Lifecycle.Of[F[Throwable, _], HttpServer](
      Lifecycle.fromCats {
        val combinedApis = allHttpApis.map(_.http).toList.foldK

        EmberServerBuilder
          .default(using async, Network.forAsync)
          .withHttpApp(combinedApis.orNotFound)
          .withPort(Port.fromInt(8080).get)
          .build
          .map(HttpServer(_))
      }
    )

}
