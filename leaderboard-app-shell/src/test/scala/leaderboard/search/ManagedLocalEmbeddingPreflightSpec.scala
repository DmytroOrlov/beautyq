package leaderboard.search

import com.typesafe.config.ConfigFactory
import distage.{Injector, Lifecycle, ModuleDef, Scene}
import izumi.distage.model.definition.{Activation, LocatorPrivacy}
import izumi.distage.model.plan.Roots
import izumi.logstage.api.IzLogger
import izumi.logstage.distage.LogIO2Module
import leaderboard.model.QueryFailure
import leaderboard.plugins.BeautySearchLocalQdrantSupplementLauncherModule
import leaderboard.search.document.{BeautySearchReadyCatalogDocuments, VariantSearchDocument}
import leaderboard.search.dsl.{BeautySearchSpec, BeautySearchSpecV1}
import leaderboard.search.elasticsearch.ElasticsearchJsonClient
import leaderboard.search.embedding.{EmbeddingClient, LlamaCppEmbeddingClientConfig}
import leaderboard.search.qdrant.QdrantClient
import leaderboard.search.startup.{BeautyQManagedLocalSearchBootstrap, BeautyQManagedLocalSearchDataReady}
import logstage.LogIO2
import org.scalatest.wordspec.AnyWordSpec
import zio.{IO, Runtime, Task, Unsafe, ZIO}

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Focused fail-fast contract for the local managed BeautyQ Qdrant bootstrap embedding preflight.
 *
 * It proves that when the configured embedding endpoint is unavailable or returns the wrong vector
 * dimension, the managed local search readiness resource fails with a clear, typed diagnostic (naming
 * the BeautyQ managed local search bootstrap, the embedding endpoint, and the expected dimension `1024`)
 * before any ES/Qdrant work runs, and that the downstream HTTP-bind stage (which mirrors how
 * `leaderboard.http.HttpServer` depends on `BeautyQManagedLocalSearchDataReady`) is never entered. There
 * is no ES-only fallback: an unavailable or incompatible embedding endpoint fails startup, full stop.
 */
final class ManagedLocalEmbeddingPreflightSpec extends AnyWordSpec {

  private val endpointLabel: String = {
    val config = ConfigFactory.load("common-reference.conf").resolve().getConfig("llama-cpp-embedding")
    LlamaCppEmbeddingClientConfig(
      baseUrl = config.getString("baseUrl"),
      endpointPath = config.getString("endpointPath"),
    ).baseUrl
  }
  private val expectedDimension: Int = BeautyQManagedLocalSearchBootstrap.ExpectedVectorDimension

  "BeautyQManagedLocalSearchBootstrap.embeddingPreflight" should {
    "accept an endpoint that returns a vector of the expected dimension" in {
      val client = ScriptedEmbeddingClient(ZIO.succeed(Vector.fill(expectedDimension)(0.01)))
      val dimension = run(BeautyQManagedLocalSearchBootstrap.embeddingPreflight(client, endpointLabel, expectedDimension))
      assert(dimension == expectedDimension)
    }

    "fail with a clear diagnostic when the embedding endpoint is unavailable" in {
      val client = ScriptedEmbeddingClient(
        ZIO.fail(QueryFailure.operation("execute-llama-cpp-http", "Connection refused: localhost:8081"))
      )
      val failure = runFail(BeautyQManagedLocalSearchBootstrap.embeddingPreflight(client, endpointLabel, expectedDimension))
      assert(failure.message.contains("BeautyQ managed local search"), failure.message)
      assert(failure.message.contains("embedding"), failure.message)
      assert(failure.message.contains(endpointLabel), failure.message)
      assert(failure.message.contains("Connection refused"), failure.message)
    }

    "fail with a clear diagnostic when the embedding endpoint returns an empty vector" in {
      val client = ScriptedEmbeddingClient(ZIO.succeed(Vector.empty[Double]))
      val failure = runFail(BeautyQManagedLocalSearchBootstrap.embeddingPreflight(client, endpointLabel, expectedDimension))
      assert(failure.message.contains("BeautyQ managed local search"), failure.message)
      assert(failure.message.contains("empty embedding"), failure.message)
      assert(failure.message.contains(endpointLabel), failure.message)
    }

    "fail naming the expected and actual dimension when the endpoint returns the wrong dimension" in {
      val wrongDimension = 512
      val client = ScriptedEmbeddingClient(ZIO.succeed(Vector.fill(wrongDimension)(0.01)))
      val failure = runFail(BeautyQManagedLocalSearchBootstrap.embeddingPreflight(client, endpointLabel, expectedDimension))
      assert(failure.message.contains("BeautyQ managed local search"), failure.message)
      assert(failure.message.contains("embedding"), failure.message)
      assert(failure.message.contains(s"expectedDimension=$expectedDimension"), failure.message)
      assert(failure.message.contains(s"actualDimension=$wrongDimension"), failure.message)
      assert(
        expectedDimension == leaderboard.search.beautyq.contract.BeautyQSearchRuntimeContract.ManagedLocalQdrantExpectedVectorDimension,
        s"expected dimension contract must match the canonical managed-local default, got $expectedDimension",
      )
    }
  }

  "BeautyQManagedLocalSearchBootstrap.run" should {
    "fail at the preflight before touching ES or Qdrant when the embedding endpoint is unavailable" in {
      val client = ScriptedEmbeddingClient(
        ZIO.fail(QueryFailure.operation("execute-llama-cpp-http", "Connection refused: localhost:8081"))
      )
      val failure = runFail(
        BeautyQManagedLocalSearchBootstrap.run(
          esClient = failIfCalledEs,
          qdrantClient = unusedQdrantClient,
          embeddingClient = client,
          spec = baseSpec,
          catalog = emptyCatalog,
          vectorSearchSpec = BeautySearchLocalQdrantSupplementLauncherModule.VectorSpec,
          embeddingEndpoint = endpointLabel,
        )
      )
      // The preflight short-circuits the for-comprehension, so the ES fail-if-called stub is never the
      // reported failure: the diagnostic is the embedding preflight one.
      assert(failure.message.contains("BeautyQ managed local search Qdrant bootstrap embedding preflight"), failure.message)
      assert(!failure.message.contains("ES-must-not-be-reached"), failure.message)
    }
  }

  "BeautyQManagedLocalSearchDataReady readiness resource" should {
    "fail and never enter the HTTP-bind stage when the embedding endpoint is unavailable" in {
      val httpBound = new AtomicBoolean(false)
      val client = ScriptedEmbeddingClient(
        ZIO.fail(QueryFailure.operation("execute-llama-cpp-http", "Connection refused: localhost:8081"))
      )
      val failure = produceFailure(client, httpBound)
      assert(failure.getMessage.contains("BeautyQ managed local search"), failure.getMessage)
      assert(failure.getMessage.contains("embedding"), failure.getMessage)
      assert(failure.getMessage.contains(endpointLabel), failure.getMessage)
      assert(!httpBound.get(), "HTTP bind stage must not be entered when embedding preflight fails")
    }

    "fail and never enter the HTTP-bind stage when the embedding endpoint returns the wrong dimension" in {
      val httpBound = new AtomicBoolean(false)
      val client = ScriptedEmbeddingClient(ZIO.succeed(Vector.fill(512)(0.01)))
      val failure = produceFailure(client, httpBound)
      assert(failure.getMessage.contains("expectedDimension=1024"), failure.getMessage)
      assert(failure.getMessage.contains("actualDimension=512"), failure.getMessage)
      assert(!httpBound.get(), "HTTP bind stage must not be entered when embedding preflight fails")
    }
  }

  /** Sentinel for the HTTP-bind stage that mirrors `leaderboard.http.HttpServer`'s dependency on readiness. */
  private final class HttpBindSentinel

  private def produceFailure(embeddingClient: EmbeddingClient, httpBound: AtomicBoolean): Throwable = {
    val module = new ModuleDef {
      include(LogIO2Module[IO]())
      make[IzLogger].fromValue(IzLogger())
      make[ElasticsearchJsonClient].fromValue(failIfCalledEs)
      make[QdrantClient].fromValue(unusedQdrantClient)
      make[EmbeddingClient].fromValue(embeddingClient)
      make[BeautySearchSpec].fromValue(baseSpec)
      make[BeautySearchReadyCatalogDocuments].fromValue(emptyCatalog)
      make[BeautyQManagedLocalSearchDataReady].fromResource {
        (
          esClient: ElasticsearchJsonClient,
          qdrantClient: QdrantClient,
          embedding: EmbeddingClient,
          spec: BeautySearchSpec,
          catalog: BeautySearchReadyCatalogDocuments,
          log: LogIO2[IO],
        ) =>
          new BeautyQManagedLocalSearchDataReady.Bootstrap(
            esClient,
            qdrantClient,
            embedding,
            spec,
            catalog,
            BeautySearchLocalQdrantSupplementLauncherModule.VectorSpec,
            endpointLabel,
            log,
          )
      }
      // Mirrors how `HttpServer` depends on `BeautyQManagedLocalSearchDataReady`: distage acquires the
      // readiness resource first, so a failing readiness means this stage is never entered/bound.
      make[HttpBindSentinel].fromResource {
        (searchDataReady: BeautyQManagedLocalSearchDataReady) =>
          val _ = searchDataReady
          Lifecycle.liftF(ZIO.attempt {
            httpBound.set(true)
            new HttpBindSentinel
          })
      }
    }

    val produced = Injector[Task]()
      .produce(
        bindings = module,
        roots = Roots.target[HttpBindSentinel],
        activation = Activation(Scene -> Scene.Managed),
        locatorPrivacy = LocatorPrivacy.PublicByDefault,
      )
      .use(_ => ZIO.unit)

    runTask(produced.either) match {
      case Left(error)  => error
      case Right(_)     => fail("expected managed local search readiness to fail when embedding preflight fails")
    }
  }

  private val baseSpec: BeautySearchSpec = BeautySearchSpecV1.spec
  private val emptyCatalog: BeautySearchReadyCatalogDocuments =
    BeautySearchReadyCatalogDocuments("managed-local-embedding-preflight-spec", List.empty[VariantSearchDocument])
  private val unusedQdrantClient: QdrantClient = new QdrantClient("127.0.0.1", 0)

  private val failIfCalledEs: ElasticsearchJsonClient = new ElasticsearchJsonClient {
    private def boom: IO[QueryFailure, Nothing] =
      ZIO.fail(QueryFailure.operation("elasticsearch-json-client", "ES-must-not-be-reached before embedding preflight passes"))
    override def putJson(path: String, json: io.circe.Json): IO[QueryFailure, io.circe.Json] = boom
    override def post(path: String): IO[QueryFailure, io.circe.Json]                         = boom
    override def postJson(path: String, json: io.circe.Json): IO[QueryFailure, io.circe.Json] = boom
    override def postNdjson(path: String, payload: String): IO[QueryFailure, io.circe.Json]   = boom
    override def getJson(path: String): IO[QueryFailure, io.circe.Json]                       = boom
    override def delete(path: String): IO[QueryFailure, Unit]                                  = boom
  }

  private final case class ScriptedEmbeddingClient(result: IO[QueryFailure, Vector[Double]]) extends EmbeddingClient {
    override def embed(text: String): IO[QueryFailure, Vector[Double]] = result
  }

  private def run[A](effect: IO[QueryFailure, A]): A =
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(effect).getOrThrowFiberFailure()
    }

  private def runFail[A](effect: IO[QueryFailure, A]): QueryFailure =
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(effect.either).getOrThrowFiberFailure() match {
        case Left(failure) => failure
        case Right(_)      => fail("expected the effect to fail")
      }
    }

  private def runTask[A](effect: Task[A]): A =
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(effect).getOrThrowFiberFailure()
    }
}
