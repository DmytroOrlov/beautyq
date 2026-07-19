package leaderboard.search.beautyq.gen2.wiring

import leaderboard.search.gen2.qdrant.*
import org.scalatest.wordspec.AnyWordSpec

final class BeautyQQdrantCompositionSpec extends AnyWordSpec {
  "BeautyQQdrantRuntime" should {
    "bind the canonical BeautyQ policy and resource declarations" in {
      assert(BeautyQQdrantRuntime.policy eq BeautyQQdrantPolicy.policy)
      assert(BeautyQQdrantRuntime.resources eq BeautyQSearchGen2ResourceNames)
      assert(BeautyQSearchGen2.qdrant.policy eq BeautyQQdrantPolicy.policy)
      assert(BeautyQSearchGen2.qdrant.resources eq BeautyQSearchGen2ResourceNames)
      assert(BeautyQSearchGen2.qdrant.runtime eq BeautyQQdrantRuntime)
    }

    "construct generic lifecycle and candidate owners from those declarations" in {
      val client = new QdrantGen2Client {
        def getCollection(collection: QdrantResourceName) = Left(leaderboard.search.gen2.transport.Gen2HttpTransportError.RequestFailed("GET", "unused", "unused"))
        def listAliases() = Left(leaderboard.search.gen2.transport.Gen2HttpTransportError.RequestFailed("GET", "unused", "unused"))
        def createCollection(collection: QdrantResourceName, body: io.circe.Json) = Left(leaderboard.search.gen2.transport.Gen2HttpTransportError.RequestFailed("PUT", "unused", "unused"))
        def createPayloadIndex(collection: QdrantResourceName, body: io.circe.Json) = Left(leaderboard.search.gen2.transport.Gen2HttpTransportError.RequestFailed("PUT", "unused", "unused"))
        def upsertPoints(collection: QdrantResourceName, body: io.circe.Json) = Left(leaderboard.search.gen2.transport.Gen2HttpTransportError.RequestFailed("PUT", "unused", "unused"))
        def countPoints(collection: QdrantResourceName) = Left(leaderboard.search.gen2.transport.Gen2HttpTransportError.RequestFailed("POST", "unused", "unused"))
        def updateAliases(body: io.circe.Json) = Left(leaderboard.search.gen2.transport.Gen2HttpTransportError.RequestFailed("POST", "unused", "unused"))
        def queryPoints(target: QdrantResourceName, body: io.circe.Json) = Left(leaderboard.search.gen2.transport.Gen2HttpTransportError.RequestFailed("POST", "unused", "unused"))
      }
      assert(BeautyQQdrantRuntime.lifecycle(client).isRight)
      assert(BeautyQQdrantRuntime.candidateService(client).isRight)
    }
  }
}
