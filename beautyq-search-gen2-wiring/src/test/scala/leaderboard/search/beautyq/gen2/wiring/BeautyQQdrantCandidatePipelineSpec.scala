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

    "execute one eligible request from validation through authorized Qdrant hydration" in {
      val request = BeautySearchRequestGen2(
        Some("relaxing appointment"),
        Vector(PublicFilterInput(PublicFieldName("service"), leaderboard.search.gen2.contract.PublicOperator.Equal, PublicFilterValue.Scalar("manicure"), None)),
        Vector.empty,
        Vector.empty,
        page,
        None,
      )
      val validated = BeautyQSearchRequestForTest.validated(request)
      val intent = BeautyQSearchRequestForTest.intent(validated)
      val compiled = BeautyQSearchRequestForTest.compiled(validated, intent)
      val candidateEvaluation = BeautyQCandidatePlanCompiler.compile(compiled).getOrElse(fail("expected eligible candidate evaluation"))
      candidateEvaluation.decision match {
        case CandidatePlanDecision.Eligible(plan) =>
          assert(plan.hardConstraints == Vector(
            PlannedConstraint.Terms(BeautyQSearchDeclarations.variants.Fields.serviceCode, Set(ServiceCodeOf("manicure"))),
          ))
          val generation = compiledGeneration
          val alias = QdrantResourceName.from(BeautyQSearchGen2ResourceNames.QdrantCollectionAlias).getOrElse(fail("expected alias"))
          val config = QdrantCandidateServiceConfig.create(alias, BeautyQSearchGen2ResourceNames.QdrantPhysicalCollectionPrefix).getOrElse(fail("expected service config"))
          val embeddingPort = new QdrantQueryEmbeddingPort[String] {
            def embed(input: QdrantEmbeddingInput): Either[String, QdrantEmbeddingResult] =
              QdrantEmbeddingResult.from(input, Vector.fill(input.modelValue.dimension)(0.1)).left.map(_.toString)
          }
          BeautyQQdrantCandidatePipeline.execute(candidateEvaluation, BeautyQElasticsearchTestFixtures.materialized, embeddingPort, new QdrantCandidateService(new AuthorizedBeautyQClient(generation, alias.value), config)) match {
            case Right(result) =>
              assert(result.evaluation eq candidateEvaluation)
              result.outcome match {
                case Right(hydrated) =>
                  assert(hydrated.candidates.map(_.id) == Vector(BeautyQElasticsearchTestFixtures.document.variantId))
                  assert(hydrated.candidates.map(_.score) == Vector(0.9))
                  assert(hydrated.candidates.map(_.provenance) == Vector(BeautyQCandidateProvenance.SemanticSupplement))
                  assert(hydrated.target.value == generation.physicalCollectionName)
                  assert(hydrated.metadata == generation.metadata)
                  assert(hydrated.diagnostics.rawCount == 1)
                case Left(reason) => fail(s"expected hydrated candidates, got ineligibility $reason")
              }
            case Left(error) => fail(s"expected eligible pipeline result, got $error")
          }
        case CandidatePlanDecision.Ineligible(reason) => fail(s"expected eligible candidate plan, got $reason")
      }
    }
  }

  private def ServiceCodeOf(value: String) =
    BeautyQSearchDeclarations.variants.Fields.serviceCode.codec.decodeCanonical(value).getOrElse(fail(s"invalid fixture ServiceCode '$value'"))

  private def compiledGeneration: QdrantCompiledGeneration = {
    val prepared = QdrantGenerationCompiler.prepare(BeautyQQdrantPolicy.policy, BeautyQElasticsearchTestFixtures.materialized).getOrElse(fail("expected prepared generation"))
    val embeddings = prepared.points.map(point => QdrantEmbeddingResult.from(point.embeddingInput, Vector.fill(point.embeddingInput.modelValue.dimension)(0.1)).getOrElse(fail("expected embedding")))
    QdrantGenerationCompiler.complete(prepared, embeddings, BeautyQSearchGen2ResourceNames.QdrantPhysicalCollectionPrefix).getOrElse(fail("expected compiled generation"))
  }

  private class NoCallClient extends QdrantGen2Client {
    def getCollection(collection: QdrantResourceName) = Left(Gen2HttpTransportError.RequestFailed("GET", "unused", "must not be called"))
    def listAliases() = Left(Gen2HttpTransportError.RequestFailed("GET", "unused", "must not be called"))
    def createCollection(collection: QdrantResourceName, body: Json) = Left(Gen2HttpTransportError.RequestFailed("PUT", "unused", "must not be called"))
    def createPayloadIndex(collection: QdrantResourceName, body: Json) = Left(Gen2HttpTransportError.RequestFailed("PUT", "unused", "must not be called"))
    def upsertPoints(collection: QdrantResourceName, body: Json) = Left(Gen2HttpTransportError.RequestFailed("PUT", "unused", "must not be called"))
    def countPoints(collection: QdrantResourceName) = Left(Gen2HttpTransportError.RequestFailed("POST", "unused", "must not be called"))
    def updateAliases(body: Json) = Left(Gen2HttpTransportError.RequestFailed("POST", "unused", "must not be called"))
    def queryPoints(target: QdrantResourceName, body: Json) = Left(Gen2HttpTransportError.RequestFailed("POST", "unused", "must not be called"))
  }

  private final class AuthorizedBeautyQClient(generation: QdrantCompiledGeneration, alias: String) extends NoCallClient {
    override def listAliases() = Right(Json.obj("status" -> Json.fromString("ok"), "result" -> Json.obj("aliases" -> Json.arr(Json.obj("alias_name" -> Json.fromString(alias), "collection_name" -> Json.fromString(generation.physicalCollectionName))))))

    override def getCollection(collection: QdrantResourceName) = {
      val vectors = generation.collectionJson.hcursor.downField("vectors").focus.getOrElse(Json.obj())
      val metadata = generation.collectionJson.hcursor.downField("metadata").focus.getOrElse(Json.obj())
      val payload = Json.obj(generation.payloadIndexRequests.flatMap { request =>
        for {
          obj <- request.asObject.toVector
          field <- obj("field_name").flatMap(_.asString).toVector
          schema <- obj("field_schema").flatMap(_.asString).toVector
        } yield field -> Json.obj("data_type" -> Json.fromString(schema))
      }*)
      Right(Json.obj(
        "status" -> Json.fromString("ok"),
        "result" -> Json.obj(
          "config" -> Json.obj("params" -> Json.obj("vectors" -> vectors), "metadata" -> metadata),
          "payload_schema" -> payload,
          "points_count" -> Json.fromInt(generation.metadata.pointCount),
        ),
      ))
    }

    override def queryPoints(target: QdrantResourceName, body: Json) = {
      assert(target.value == generation.physicalCollectionName)
      generation.points match {
        case point +: _ => Right(Json.obj("status" -> Json.fromString("ok"), "result" -> Json.obj("points" -> Json.arr(Json.obj("id" -> QdrantPointId.json(point.id), "score" -> Json.fromBigDecimal(BigDecimal("0.9")))))))
        case _ => fail("expected one compiled point")
      }
    }
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
