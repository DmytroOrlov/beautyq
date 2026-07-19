package leaderboard.search.gen2.qdrant

import io.circe.Json
import leaderboard.search.gen2.contract.*
import leaderboard.search.gen2.core.materialization.ProjectedDocumentsFingerprint
import leaderboard.search.gen2.transport.*
import org.scalatest.wordspec.AnyWordSpec

import java.time.Duration
import java.util.UUID

/** Default-local Qdrant 1.18.3 communication proof. */
final class QdrantLocalResourceSpec extends AnyWordSpec {
  "the local Qdrant 1.18.3 resource" should {
    "converge generations, authorize candidate execution and preserve the old collection" in {
      val endpoint = Gen2HttpEndpoint.fromString("http://localhost:6334").getOrElse(fail("expected endpoint"))
      val config = Gen2HttpTransportConfig.create(endpoint, Duration.ofMillis(400), Duration.ofSeconds(5)).getOrElse(fail("expected config"))
      val http = Gen2JsonHttpClient.jdk(config)
      http.getJson("/") match {
        case Left(_: Gen2HttpTransportError.ConnectionFailed) => cancel("separate Qdrant 1.18.3 resource is unavailable on localhost:6334")
        case Left(error) => fail(s"local Qdrant returned a transport failure: $error")
        case Right(root) =>
          val version = root.hcursor.get[String]("version").toOption
          if (!version.contains("1.18.3")) fail(s"expected Qdrant 1.18.3, got ${version.getOrElse("missing version")}")
          runScenario(http)
      }
    }
  }

  private def runScenario(http: Gen2JsonHttpClient): Unit = {
    val suffix = UUID.randomUUID().toString.replace("-", "")
    val aliasValue = s"gen2_neutral_$suffix"
    val prefix = s"${aliasValue}_"
    val alias = QdrantResourceName.from(aliasValue).getOrElse(fail("expected alias"))
    val lifecycleConfig = QdrantGenerationLifecycleConfig.create(alias, prefix).getOrElse(fail("expected lifecycle config"))
    val candidateConfig = QdrantCandidateServiceConfig.create(alias, prefix).getOrElse(fail("expected candidate config"))
    val client = QdrantGen2Client.fromTransport(http)
    val generationA = compiledGeneration(prefix, "resource-a")
    val generationB = compiledGeneration(prefix, "resource-b")
    val ownedTargets = Vector(generationA.physicalCollectionName, generationB.physicalCollectionName)
    try {
      val lifecycle = new QdrantGenerationLifecycle(client, lifecycleConfig)
      lifecycle.activate(generationA).getOrElse(fail("expected generation A activation"))
      val prepared = QdrantCandidateRequestCompiler.prepare(QdrantTestFixtures.policy, CandidatePlan(SemanticQueryText.from("alpha").getOrElse(fail("expected semantic text")), Vector.empty)).getOrElse(fail("expected candidate preparation"))
      val embedding = QdrantEmbeddingResult.from(prepared.embeddingInput, Vector(0.1, 0.2, 0.3)).getOrElse(fail("expected candidate embedding"))
      val request = QdrantCandidateRequestCompiler.complete(prepared, embedding).getOrElse(fail("expected candidate request"))
      val candidateService = new QdrantCandidateService(client, candidateConfig)
      val firstResult = candidateService.execute(QdrantTestFixtures.policy, request).getOrElse(fail("expected candidate result on A"))
      assert(firstResult.hits.nonEmpty)

      QdrantGen2Client.fromTransport(http).createCollection(
        QdrantResourceName.from(generationB.physicalCollectionName).getOrElse(fail("expected B name")),
        generationB.collectionJson,
      ).getOrElse(fail("expected partial B collection creation"))
      lifecycle.activate(generationB).getOrElse(fail("expected generation B activation"))

      client.listAliases().getOrElse(fail("expected aliases")).hcursor.downField("result").downField("aliases").as[Vector[Json]] match {
        case Right(values) =>
          val activeTargets = values.collect {
            case value if value.hcursor.get[String]("alias_name").toOption.contains(aliasValue) =>
              value.hcursor.get[String]("collection_name").toOption
          }.flatten
          assert(activeTargets == Vector(generationB.physicalCollectionName))
        case Left(error) => fail(s"expected aliases response, got $error")
      }
      assert(client.getCollection(QdrantResourceName.from(generationA.physicalCollectionName).getOrElse(fail("expected A name"))).isRight)
      (): Unit
    } finally {
      client.updateAliases(Json.obj(
        "actions" -> Json.arr(Json.obj("delete_alias" -> Json.obj("alias_name" -> Json.fromString(aliasValue))))
      ))
      ownedTargets.foreach { target =>
        http.delete(s"/collections/$target")
        (): Unit
      }
    }
  }

  private def compiledGeneration(prefix: String, marker: String): QdrantCompiledGeneration = {
    val materialized = QdrantTestFixtures.materialized.copy(projectedDocumentsFingerprint = ProjectedDocumentsFingerprint(marker))
    val prepared = QdrantGenerationCompiler.prepare(QdrantTestFixtures.policy, materialized).getOrElse(fail("expected prepared generation"))
    val embeddings = prepared.points.map(point => QdrantEmbeddingResult.from(point.embeddingInput, Vector(0.1, 0.2, 0.3)).getOrElse(fail("expected embedding")))
    QdrantGenerationCompiler.complete(prepared, embeddings, prefix).getOrElse(fail("expected compiled generation"))
  }
}
