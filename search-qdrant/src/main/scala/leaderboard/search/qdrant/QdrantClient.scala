package leaderboard.search.qdrant

import io.circe.{Json, JsonObject}
import io.circe.parser.parse
import leaderboard.model.QueryFailure
import zio.{IO, ZIO}

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.time.Duration

final case class QdrantSearchHit(
  id: String,
  payload: JsonObject,
  score: Double,
)

final class QdrantClient(host: String, port: Int) {
  private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
  private val baseUri = s"http://$host:$port"

  def createCollection(path: String, json: Json): IO[QueryFailure, Json] =
    request("PUT", path, Some(json.noSpaces), "application/json").flatMap(parseJson("create-qdrant-collection", _))

  def deleteCollection(path: String): IO[QueryFailure, Unit] =
    request("DELETE", path).flatMap { response =>
      val body = Option(response.body).getOrElse("")
      if (response.statusCode / 100 == 2) ZIO.unit
      else ZIO.fail(QueryFailure.operation("delete-qdrant-collection", s"Unexpected status ${response.statusCode}: $body"))
    }

  def collectionInfo(path: String): IO[QueryFailure, Json] =
    request("GET", path).flatMap(parseJson("get-qdrant-collection-info", _))

  def upsertPoint(path: String, json: Json): IO[QueryFailure, Json] =
    request("PUT", path, Some(json.noSpaces), "application/json").flatMap(parseJson("upsert-qdrant-point", _))

  def search(path: String, json: Json): IO[QueryFailure, List[QdrantSearchHit]] =
    request("POST", path, Some(json.noSpaces), "application/json")
      .flatMap(parseJson("search-qdrant-points", _))
      .flatMap(parseSearchHits)

  private def request(
    method: String,
    path: String,
    body: Option[String] = None,
    contentType: String = "application/json",
  ): IO[QueryFailure, HttpResponse[String]] =
    ZIO.attemptBlocking {
      val builder = HttpRequest.newBuilder(URI.create(baseUri + path)).timeout(Duration.ofSeconds(30))
      val withMethod = body match {
        case Some(payload) => builder.header("Content-Type", contentType).method(method, HttpRequest.BodyPublishers.ofString(payload))
        case None => builder.method(method, HttpRequest.BodyPublishers.noBody())
      }
      client.send(withMethod.build(), HttpResponse.BodyHandlers.ofString())
    }.mapError(QueryFailure.fromThrowable("execute-qdrant-http", _))

  private def parseJson(operationName: String, response: HttpResponse[String]): IO[QueryFailure, Json] = {
    val body = Option(response.body).getOrElse("")
    if (response.statusCode / 100 != 2) {
      ZIO.fail(QueryFailure.operation(operationName, s"Unexpected status ${response.statusCode}: $body"))
    } else {
      ZIO.fromEither(parse(body).left.map(error => QueryFailure.operation(operationName, error.getMessage)))
    }
  }

  private def parseSearchHits(json: Json): IO[QueryFailure, List[QdrantSearchHit]] =
    ZIO.fromEither(
      json.hcursor.downField("result").downField("points").as[List[Json]].orElse(json.hcursor.downField("result").as[List[Json]]).flatMap { hits =>
        hits.foldLeft[Either[io.circe.DecodingFailure, List[QdrantSearchHit]]](Right(Nil)) { (acc, hit) =>
          for {
            decoded <- acc
            id <- hit.hcursor.get[String]("id").orElse(hit.hcursor.get[Long]("id").map(_.toString))
            payload <- hit.hcursor.get[JsonObject]("payload")
            score <- hit.hcursor.get[Double]("score")
          } yield decoded :+ QdrantSearchHit(id, payload, score)
        }
      }.left.map(error => QueryFailure.operation("decode-qdrant-search-response", error.getMessage))
    )
}
