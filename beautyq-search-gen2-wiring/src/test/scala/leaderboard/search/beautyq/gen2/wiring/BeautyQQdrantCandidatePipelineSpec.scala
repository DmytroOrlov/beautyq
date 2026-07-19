package leaderboard.search.beautyq.gen2.wiring

import io.circe.Json
import java.time.Instant
import leaderboard.search.beautyq.gen2.contract.*
import leaderboard.search.beautyq.gen2.materialization.{BeautyQSearchSnapshot, MaterializedBeautyQVariantDocuments}
import leaderboard.search.gen2.contract.*
import leaderboard.search.gen2.core.materialization.*
import leaderboard.search.gen2.qdrant.*
import leaderboard.search.gen2.transport.Gen2HttpTransportError
import org.scalatest.wordspec.AnyWordSpec

final class BeautyQQdrantCandidatePipelineSpec extends AnyWordSpec {
  private val page = PageRequest(None, PageSize.from(20).getOrElse(fail("expected page size")))
  private val request = BeautySearchRequestGen2(None, Vector.empty, Vector.empty, Vector.empty, page, None)

  private val evaluation: CompiledCandidateEvaluation = {
    val validated = BeautyQSearchRequestForTest.validated(request)
    val intent = BeautyQSearchRequestForTest.intent(validated)
    val compiled = BeautyQSearchRequestForTest.compiled(validated, intent)
    BeautyQCandidatePlanCompiler.compile(compiled).getOrElse(fail("expected candidate evaluation"))
  }

  private val materialized: MaterializedBeautyQVariantDocuments = MaterializedSearchDocuments(
    VersionedSnapshot(
      BeautyQSearchSnapshot(Vector.empty, Vector.empty, Vector.empty, Vector.empty, Vector.empty, Vector.empty, Vector.empty),
      ContentFingerprint("empty-source"),
      None,
      Instant.parse("2026-01-01T00:00:00Z"),
    ),
    Vector.empty,
    ProjectedDocumentsFingerprint("empty-projected"),
    ProjectionFormatVersion("empty-projection"),
  )

  "BeautyQQdrantCandidatePipeline" should {
    "preserve ineligibility and never call embedding or Qdrant for a browse evaluation" in {
      val alias = QdrantResourceName.from("neutral_alias").getOrElse(fail("expected alias"))
      val config = QdrantCandidateServiceConfig.create(alias, "neutral_alias_").getOrElse(fail("expected service config"))
      val embeddingPort = new QdrantQueryEmbeddingPort[String] {
        def embed(input: QdrantEmbeddingInput): Either[String, QdrantEmbeddingResult] = Left("must-not-call")
      }
      BeautyQQdrantCandidatePipeline.execute(evaluation, materialized, embeddingPort, new QdrantCandidateService(new NoCallClient, config)) match {
        case Right(result) =>
          assert(result.evaluation eq evaluation)
          result.outcome match {
            case Left(reason) => assert(reason == BeautyQCandidateIneligibility.NoSemanticQueryText)
            case Right(value) => fail(s"expected ineligible result, got $value")
          }
        case Left(error) => fail(s"expected ineligible result, got $error")
      }
    }
  }

  private final class NoCallClient extends QdrantGen2Client {
    def getCollection(collection: QdrantResourceName) = Left(Gen2HttpTransportError.RequestFailed("GET", "unused", "must not be called"))
    def listAliases() = Left(Gen2HttpTransportError.RequestFailed("GET", "unused", "must not be called"))
    def createCollection(collection: QdrantResourceName, body: Json) = Left(Gen2HttpTransportError.RequestFailed("PUT", "unused", "must not be called"))
    def createPayloadIndex(collection: QdrantResourceName, body: Json) = Left(Gen2HttpTransportError.RequestFailed("PUT", "unused", "must not be called"))
    def upsertPoints(collection: QdrantResourceName, body: Json) = Left(Gen2HttpTransportError.RequestFailed("PUT", "unused", "must not be called"))
    def countPoints(collection: QdrantResourceName) = Left(Gen2HttpTransportError.RequestFailed("POST", "unused", "must not be called"))
    def updateAliases(body: Json) = Left(Gen2HttpTransportError.RequestFailed("POST", "unused", "must not be called"))
    def queryPoints(target: QdrantResourceName, body: Json) = Left(Gen2HttpTransportError.RequestFailed("POST", "unused", "must not be called"))
  }
}

private object BeautyQSearchRequestForTest {
  def validated(request: BeautySearchRequestGen2): ValidatedBeautySearchRequestGen2 =
    BeautySearchRequestGen2.validate(request).getOrElse(throw new AssertionError("expected validated request"))

  def intent(request: ValidatedBeautySearchRequestGen2): ParsedBeautyIntentGen2 =
    BeautyQIntentParserGen2.parse(request, BeautyQIntentVocabulary.value).getOrElse(throw new AssertionError("expected parsed intent"))

  def compiled(request: ValidatedBeautySearchRequestGen2, intent: ParsedBeautyIntentGen2): CompiledBeautyQSearchPlan =
    BeautyQSearchPlanCompiler.compile(request, intent).getOrElse(throw new AssertionError("expected compiled plan"))
}
