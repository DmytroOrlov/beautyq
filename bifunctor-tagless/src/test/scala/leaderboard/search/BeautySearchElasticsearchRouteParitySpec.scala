package leaderboard.search

import cats.effect.Async
import cats.syntax.all.*
import distage.Injector
import fs2.text
import io.circe.Json
import io.circe.parser.parse
import izumi.distage.model.definition.{Activation, LocatorPrivacy}
import izumi.distage.model.plan.Roots
import leaderboard.api.{BeautySearchApi, HttpApi}
import leaderboard.http.tapir.{BeautySearchTapirEndpoints, TapirHttpSupport}
import leaderboard.model.QueryFailure
import leaderboard.plugins.BeautySearchRouteModules
import leaderboard.search.elasticsearch.ElasticsearchJsonClient
import leaderboard.{HttpContractTestSupport, ObservedResponse}
import org.http4s.{HttpApp, Request, Status}
import org.scalatest.wordspec.AnyWordSpec
import zio.interop.catz.*
import zio.{IO, Runtime, Task, Unsafe, ZIO}

final class BeautySearchElasticsearchRouteParitySpec extends AnyWordSpec with HttpContractTestSupport {
  "BeautySearchRouteModules.seedCatalogElasticsearch parity" should {
    "return OK JSON response for accepted query/limit/coordinate inputs" in {
      val probe = buildProbe()
      val apis  = probe.allHttpApis

      assert(apis.collect { case api: BeautySearchApi[IO] => api }.size == 1)

      val cases: List[(String, String, Int)] = List(
        ("normal query with normal coords and limit 3", """{"query":"nails","userLat":53.58,"userLon":10.08,"limit":3}""", 3),
        ("very long query", s"""{"query":"${"nails " * 1000}","userLat":53.58,"userLon":10.08,"limit":3}""", 3),
      )

      cases.foreach { case (label, body, limit) =>
        val response = runIO(
          observeRoute(apis, postJson("/beauty-search", body))
        )

        assert(response.status == Status.Ok, s"$label: expected 200 OK, got ${response.status}")

        val json = parse(response.body).getOrElse(fail(s"$label: invalid JSON: ${response.body}"))

        assert(fieldArraySize(json, "variantCarousel") <= limit, s"$label: variantCarousel size > limit")
        assert(fieldArraySize(json, "providerCarousel") <= limit, s"$label: providerCarousel size > limit")
        assert(fieldArraySize(json, "serviceIntentCarousel") <= limit, s"$label: serviceIntentCarousel size > limit")
        json.hcursor.downField("facets").focus.getOrElse(fail(s"$label: missing facets"))
        json.hcursor.downField("inferredFilters").focus.getOrElse(fail(s"$label: missing inferredFilters"))
      }
    }

    "return Tapir default bad-input responses for semantic-invalid requests" in {
      val probe = buildProbe()
      val apis  = probe.allHttpApis

      val cases = List(
        ("empty query", """{"query":"","userLat":53.58,"userLon":10.08,"limit":3}"""),
        ("whitespace query", """{"query":"     ","userLat":53.58,"userLon":10.08,"limit":3}"""),
        ("zero limit", """{"query":"nails","userLat":53.58,"userLon":10.08,"limit":0}"""),
        ("negative limit", """{"query":"nails","userLat":53.58,"userLon":10.08,"limit":-5}"""),
        ("huge limit", """{"query":"nails","userLat":53.58,"userLon":10.08,"limit":100000}"""),
        ("latitude 999.0", """{"query":"nails","userLat":999.0,"userLon":10.08,"limit":3}"""),
        ("longitude 999.0", """{"query":"nails","userLat":53.58,"userLon":999.0,"limit":3}"""),
        ("huge finite coords", """{"query":"nails","userLat":1.0e9,"userLon":-1.0e9,"limit":3}"""),
      )

      cases.foreach { case (label, body) =>
        val response = runIO(observeRoute(apis, postJson("/beauty-search", body)))
        assert(response.status == Status.BadRequest, s"$label: expected 400 Bad Request, got ${response.status}")
      }
    }

    "return Tapir default bad-input responses for invalid requests" in {
      val probe = buildProbe()
      val apis  = probe.allHttpApis

      val malformed = runIO(
        observeRoute(apis, postJson("/beauty-search", """{"query":"broken""""))
      )
      assert(malformed.status == Status.BadRequest)

      val emptyBody = runIO(
        observeRoute(
          apis,
          Request[Task](method = org.http4s.Method.POST, uri = org.http4s.Uri.unsafeFromString("/beauty-search")).putHeaders(org.http4s.headers.`Content-Type`(org.http4s.MediaType.application.json)),
        )
      )
      assert(emptyBody.status == Status.BadRequest)

      val wrongLimitType = runIO(
        observeRoute(apis, postJson("/beauty-search", """{"query":"маникюр","limit":"bad"}"""))
      )
      assert(wrongLimitType.status == Status.BadRequest)

      val missingQuery = runIO(
        observeRoute(apis, postJson("/beauty-search", """{"userLat":53.58,"userLon":10.08,"limit":3}"""))
      )
      assert(missingQuery.status == Status.BadRequest)
    }
  }

  private def fieldArraySize(json: Json, field: String): Int =
    json.hcursor.downField(field).focus.flatMap(_.asArray).map(_.size).getOrElse(fail(s"missing array field $field"))

  private def buildProbe(): BeautySearchElasticsearchRouteParityProbe = {
    val module = new distage.ModuleDef {
      include(BeautySearchRouteModules.seedCatalogElasticsearch)
      make[TapirHttpSupport[IO]].from(new TapirHttpSupport[IO])
      make[BeautySearchTapirEndpoints].fromValue(BeautySearchTapirEndpoints)
      make[Async[Task]].fromValue(Async[Task])
      make[ElasticsearchJsonClient].from {
        new ElasticsearchJsonClient {
          override def putJson(path: String, json: Json): IO[QueryFailure, Json]       = ZIO.succeed(Json.obj())
          override def post(path: String): IO[QueryFailure, Json]                      = ZIO.succeed(Json.obj())
          override def postJson(path: String, json: Json): IO[QueryFailure, Json]      =
            if (path.contains("_search")) ZIO.succeed(Json.obj("hits" -> Json.obj("hits" -> Json.arr())))
            else ZIO.succeed(Json.obj())
          override def postNdjson(path: String, payload: String): IO[QueryFailure, Json] = ZIO.succeed(Json.obj())
          override def getJson(path: String): IO[QueryFailure, Json]                   = ZIO.dieMessage(s"unexpected getJson($path)")
          override def delete(path: String): IO[QueryFailure, Unit]                    = ZIO.dieMessage(s"unexpected delete($path)")
        }
      }
      make[BeautySearchElasticsearchRouteParityProbe].from {
        (
          beautySearchApi: BeautySearchApi[IO],
          allHttpApis: Set[HttpApi[IO]],
        ) =>
          BeautySearchElasticsearchRouteParityProbe(beautySearchApi, allHttpApis)
      }
    }

    val locator = Injector().produce(
      bindings = module,
      roots = Roots.target[BeautySearchElasticsearchRouteParityProbe],
      activation = Activation.empty,
      locatorPrivacy = LocatorPrivacy.PublicByDefault,
    ).unsafeGet()

    locator.get[BeautySearchElasticsearchRouteParityProbe]
  }

  private def observeRoute(
    apis: Set[HttpApi[IO]],
    request: Request[Task],
  ): Task[ObservedResponse] = {
    val app: HttpApp[Task] = apis.map(_.http).toList.foldK.orNotFound

    app.run(request).flatMap {
      response =>
        response.body
          .through(text.utf8.decode)
          .compile
          .string
          .map(body => ObservedResponse(response.status, body))
    }
  }

  private final case class BeautySearchElasticsearchRouteParityProbe(
    beautySearchApi: BeautySearchApi[IO],
    allHttpApis: Set[HttpApi[IO]],
  )

  private def runIO[E, A](effect: ZIO[Any, E, A]): A =
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(effect).getOrThrowFiberFailure()
    }
}
