package leaderboard.search.elasticsearch

import io.circe.Json
import leaderboard.model.QueryFailure
import zio.IO

trait ElasticsearchJsonClient {
  def putJson(path: String, json: Json): IO[QueryFailure, Json]
  def post(path: String): IO[QueryFailure, Json]
  def postJson(path: String, json: Json): IO[QueryFailure, Json]
  def postNdjson(path: String, payload: String): IO[QueryFailure, Json]
  def getJson(path: String): IO[QueryFailure, Json]
  def delete(path: String): IO[QueryFailure, Unit]
}

object ElasticsearchJsonClient {
  val OperationName: String = "elasticsearch-json-client"

  def failure(message: String): QueryFailure =
    QueryFailure.operation(OperationName, message)
}
