package leaderboard

import cats.effect.Async
import cats.syntax.all.*
import com.comcast.ip4s.{Ipv4Address, Port}
import fs2.text
import fs2.io.net.Network
import leaderboard.api.HttpApi
import org.http4s.{HttpApp, Method, Request, Status, Uri}
import org.http4s.client.Client
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.headers.`Content-Type`
import org.http4s.MediaType
import zio.*
import zio.interop.catz.*

case class ObservedResponse(
  status: Status,
  body: String,
)

trait HttpContractTestSupport {
  protected final def combineApis(apis: HttpApi[IO]*): HttpApp[Task] =
    apis.map(_.http).toList.foldK.orNotFound

  protected final def get(path: String): Request[Task] =
    Request[Task](
      method = Method.GET,
      uri    = Uri.unsafeFromString(path),
    )

  protected final def postJson(path: String, body: String): Request[Task] =
    Request[Task](
      method = Method.POST,
      uri    = Uri.unsafeFromString(path),
    ).withEntity(body).putHeaders(`Content-Type`(MediaType.application.json))

  protected final def observe(app: HttpApp[Task], request: Request[Task]): Task[ObservedResponse] =
    withClientAndBaseUri(app) {
      (client, baseUri) =>
        client
          .run(request.withUri(baseUri.resolve(request.uri)))
          .use {
            response =>
              response.body
                .through(text.utf8.decode)
                .compile
                .string
                .map(body => ObservedResponse(response.status, body))
          }
    }

  private def withClientAndBaseUri[A](
    app: HttpApp[Task]
  )(use: (Client[Task], Uri) => Task[A]
  ): Task[A] =
    EmberServerBuilder
      .default[Task](using Async[Task], Network.forAsync[Task])
      .withHost(Ipv4Address.fromString("127.0.0.1").get)
      .withPort(Port.fromInt(0).get)
      .withHttpApp(app)
      .build
      .use {
        server =>
          EmberClientBuilder.default[Task](using Async[Task], Network.forAsync[Task]).build.use {
            client =>
              val baseUri = Uri.unsafeFromString(s"http://127.0.0.1:${server.address.getPort}")
              use(client, baseUri)
          }
      }
}
