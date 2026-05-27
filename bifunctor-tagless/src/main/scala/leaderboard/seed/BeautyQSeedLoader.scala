package leaderboard.seed

import io.circe.parser.decode
import leaderboard.model.QueryFailure

import java.nio.charset.StandardCharsets
import scala.util.Using

trait BeautyQSeedLoader {
  def load(): Either[QueryFailure, BeautyQSeedData]
}

object BeautyQSeedLoader {
  val DefaultResourcePath = "seed/wandsbek_hamburg_beauty_services_seed_ready.json"

  final class ResourceLoader extends BeautyQSeedLoader {
    private val resourcePath = DefaultResourcePath

    def load(): Either[QueryFailure, BeautyQSeedData] =
      for {
        content <- readResource(resourcePath)
        data <- decode[BeautyQSeedData](content).left.map {
          error =>
            QueryFailure.operation(
              "decode-beautyq-seed",
              s"Failed to decode seed resource '$resourcePath': ${error.getMessage}",
            )
        }
      } yield data

    private def readResource(path: String): Either[QueryFailure, String] = {
      val stream = Option(getClass.getClassLoader.getResourceAsStream(path))
      stream match {
        case None =>
          Left(
            QueryFailure.operation(
              "read-beautyq-seed-resource",
              s"Seed resource '$path' was not found on classpath",
            )
          )
        case Some(input) =>
          Using(input) {
            resource =>
              new String(resource.readAllBytes(), StandardCharsets.UTF_8)
          }.toEither.left.map {
            error =>
              QueryFailure.fromThrowable("read-beautyq-seed-resource", error)
          }
      }
    }
  }
}
