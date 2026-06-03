package leaderboard.search

import io.circe.Json
import org.scalatest.wordspec.AnyWordSpec

final class LlamaCppEmbeddingClientSpec extends AnyWordSpec {
  "LlamaCppEmbeddingClient" should {
    "decode a valid response with one embedding" in {
      val json = Json.obj(
        "data" -> Json.arr(
          Json.obj(
            "embedding" -> Json.arr(
              Json.fromDoubleOrNull(1.0),
              Json.fromDoubleOrNull(2.5),
              Json.fromDoubleOrNull(3.25),
            ),
          )
        )
      )

      val result = run(LlamaCppEmbeddingClient.decodeEmbeddingJson(json))
      assert(result == Vector(1.0, 2.5, 3.25))
    }

    "fail on empty data" in {
      val json = Json.obj("data" -> Json.arr())
      val error = runFail(LlamaCppEmbeddingClient.decodeEmbeddingJson(json))
      assert(error.message.contains("Missing embedding data"))
    }

    "fail on malformed embedding" in {
      val json = Json.obj(
        "data" -> Json.arr(
          Json.obj(
            "embedding" -> Json.fromString("bad"),
          )
        )
      )

      val error = runFail(LlamaCppEmbeddingClient.decodeEmbeddingJson(json))
      assert(error.message.nonEmpty)
    }
  }

  private def run[A](effect: zio.IO[leaderboard.model.QueryFailure, A]): A =
    zio.Unsafe.unsafe { implicit unsafe =>
      zio.Runtime.default.unsafe.run(effect).getOrThrowFiberFailure()
    }

  private def runFail[A](effect: zio.IO[leaderboard.model.QueryFailure, A]): leaderboard.model.QueryFailure =
    zio.Unsafe.unsafe { implicit unsafe =>
      zio.Runtime.default.unsafe.run(effect.either).getOrThrowFiberFailure().swap.getOrElse(sys.error("expected failure"))
    }
}
