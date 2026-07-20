package leaderboard.search.beautyq.gen2.wiring.testkit

import io.circe.{Json, JsonObject}
import leaderboard.model.MasterServiceOfferVariantId
import leaderboard.search.beautyq.gen2.contract.*
import leaderboard.search.beautyq.gen2.materialization.MaterializedBeautyQVariantDocuments
import leaderboard.search.beautyq.gen2.wiring.*
import leaderboard.search.gen2.contract.*
import leaderboard.search.gen2.elasticsearch.*
import leaderboard.search.gen2.elasticsearch.lifecycle.*
import leaderboard.search.gen2.qdrant.*
import leaderboard.search.gen2.transport.Gen2HttpTransportError

import java.time.{Clock, Instant}

/** Test-only composition of the real BeautyQ declaration, generation compiler,
  * lifecycle authorization, baseline service and candidate pipeline.  No
  * trusted ES or orchestration result is constructed by this fixture.
  */
object BeautyQOrchestrationTestKit {
  val page: PageRequest = PageRequest(None, PageSize.from(20).getOrElse(throw new AssertionError("expected page size")))
  val document = BeautyQElasticsearchTestFixtures.document
  val materialized: MaterializedBeautyQVariantDocuments = BeautyQElasticsearchTestFixtures.materialized

  enum QdrantMode {
    case NoCall
    case Eligible
    case EmbeddingTimeout
  }

  final case class Context(
    compiled: CompiledBeautyQSearchPlan,
    evaluation: CompiledCandidateEvaluation,
    baseline: BoundElasticsearchBaselineResult[VariantSearchDocumentGen2, MasterServiceOfferVariantId],
    qdrant: QdrantCandidateService,
    embedding: QdrantQueryEmbeddingPort[BeautyQEmbeddingRequestError],
    baselineService: ElasticsearchBaselineService,
  )

  def eligible(baselineIds: Vector[MasterServiceOfferVariantId] = Vector.empty): Context =
    build(
      BeautySearchRequestGen2(
        Some("relaxing appointment"),
        Vector(PublicFilterInput(PublicFieldName("service"), leaderboard.search.gen2.contract.PublicOperator.Equal, PublicFilterValue.Scalar("manicure"), None)),
        Vector.empty,
        Vector.empty,
        page,
        None,
      ),
      baselineIds,
      QdrantMode.Eligible,
      membershipFailure = false,
    )

  def ineligible: Context =
    build(
      BeautySearchRequestGen2(
        None,
        Vector(PublicFilterInput(PublicFieldName("service"), leaderboard.search.gen2.contract.PublicOperator.Equal, PublicFilterValue.Scalar("manicure"), None)),
        Vector.empty,
        Vector.empty,
        page,
        None,
      ),
      Vector(document.variantId),
      QdrantMode.NoCall,
      membershipFailure = false,
    )

  def timedOut: Context =
    build(
      BeautySearchRequestGen2(
        Some("relaxing appointment"),
        Vector(PublicFilterInput(PublicFieldName("service"), leaderboard.search.gen2.contract.PublicOperator.Equal, PublicFilterValue.Scalar("manicure"), None)),
        Vector.empty,
        Vector.empty,
        page,
        None,
      ),
      Vector(document.variantId),
      QdrantMode.EmbeddingTimeout,
      membershipFailure = false,
    )

  def membershipFailure: Context =
    build(
      BeautySearchRequestGen2(
        Some("relaxing appointment"),
        Vector(PublicFilterInput(PublicFieldName("service"), leaderboard.search.gen2.contract.PublicOperator.Equal, PublicFilterValue.Scalar("manicure"), None)),
        Vector.empty,
        Vector.empty,
        page,
        None,
      ),
      Vector.empty,
      QdrantMode.Eligible,
      membershipFailure = true,
    )

  def build(
    request: BeautySearchRequestGen2,
    baselineIds: Vector[MasterServiceOfferVariantId],
    qdrantMode: QdrantMode,
    membershipFailure: Boolean,
  ): Context = {
    val validated = BeautySearchRequestGen2.validate(request).getOrElse(throw new AssertionError("expected valid BeautyQ request"))
    val intent = BeautyQIntentParserGen2.parse(validated, BeautyQIntentVocabulary.value).getOrElse(throw new AssertionError("expected parsed BeautyQ intent"))
    val compiled = BeautyQSearchPlanCompiler.compile(validated, intent).getOrElse(throw new AssertionError("expected compiled BeautyQ plan"))
    val evaluation = BeautyQCandidatePlanCompiler.compile(compiled).getOrElse(throw new AssertionError("expected candidate evaluation"))
    val generation = BeautyQElasticsearchGeneration.compile(materialized).getOrElse(throw new AssertionError("expected ES generation"))
    val persisted = ElasticsearchPersistedGenerationIdentity.fromTrusted(generation.identity)
    val generationId = ElasticsearchGenerationNaming.generationId(generation.identity)
    val target = ElasticsearchGenerationNaming
      .physicalIndexName(BeautyQSearchGen2ResourceNames.ElasticsearchPhysicalIndexPrefix, generation.identity)
      .getOrElse(throw new AssertionError("expected deterministic ES target"))
    val metadata = ElasticsearchGenerationMetadata(
      ElasticsearchGenerationMetadataSchemaVersion.Current,
      generationId,
      Instant.parse("2024-01-01T00:00:00Z"),
      generation.documents.size.toLong,
      persisted,
    )
    val batching = ElasticsearchBulkBatchingPolicy.create(10, 10000L).getOrElse(throw new AssertionError("expected batching"))
    val lifecycleConfig = ElasticsearchGenerationLifecycleConfig
      .create(BeautyQSearchGen2ResourceNames.ElasticsearchAlias, BeautyQSearchGen2ResourceNames.ElasticsearchPhysicalIndexPrefix, batching)
      .getOrElse(throw new AssertionError("expected lifecycle config"))
    val esClient = new ScriptedElasticsearchClient(
      lifecycleConfig.alias,
      target.value,
      generation.mapping.json,
      metadata,
      baselineIds,
      membershipFailure,
    )
    val baselineService = new ElasticsearchBaselineService(new ElasticsearchGenerationLifecycle(esClient, lifecycleConfig, Clock.systemUTC()))
    val prepared = BeautyQElasticsearchBaseline.compileRequest(compiled).getOrElse(throw new AssertionError("expected prepared baseline request"))
    val baseline = baselineService.searchBound(prepared) match {
      case Right(bound) => bound
      case Left(error)  => throw new AssertionError(s"expected bound baseline, got $error")
    }
    val qdrantGeneration = QdrantGenerationCompiler.prepare(BeautyQQdrantPolicy.policy, materialized).flatMap { preparedGeneration =>
      val embeddings = preparedGeneration.points.map { point =>
        QdrantEmbeddingResult.from(point.embeddingInput, Vector.fill(point.embeddingInput.modelValue.dimension)(0.1)).getOrElse(throw new AssertionError("expected embedding"))
      }
      QdrantGenerationCompiler.complete(preparedGeneration, embeddings, BeautyQSearchGen2ResourceNames.QdrantPhysicalCollectionPrefix)
    }.getOrElse(throw new AssertionError("expected Qdrant generation"))
    val alias = QdrantResourceName.from(BeautyQSearchGen2ResourceNames.QdrantCollectionAlias).getOrElse(throw new AssertionError("expected Qdrant alias"))
    val qdrantConfig = QdrantCandidateServiceConfig.create(alias, BeautyQSearchGen2ResourceNames.QdrantPhysicalCollectionPrefix).getOrElse(throw new AssertionError("expected Qdrant config"))
    val qdrant = qdrantMode match {
      case QdrantMode.NoCall          => new QdrantCandidateService(new NoCallQdrantClient, qdrantConfig)
      case QdrantMode.Eligible        => new QdrantCandidateService(new EligibleQdrantClient(qdrantGeneration, alias.value), qdrantConfig)
      case QdrantMode.EmbeddingTimeout => new QdrantCandidateService(new NoCallQdrantClient, qdrantConfig)
    }
    val embedding: QdrantQueryEmbeddingPort[BeautyQEmbeddingRequestError] = qdrantMode match {
      case QdrantMode.EmbeddingTimeout => new QdrantQueryEmbeddingPort[BeautyQEmbeddingRequestError] {
        def embed(input: QdrantEmbeddingInput): Either[BeautyQEmbeddingRequestError, QdrantEmbeddingResult] =
          Left(BeautyQEmbeddingRequestError.Timeout("embedding timed out"))
      }
      case _ => new QdrantQueryEmbeddingPort[BeautyQEmbeddingRequestError] {
        def embed(input: QdrantEmbeddingInput): Either[BeautyQEmbeddingRequestError, QdrantEmbeddingResult] =
          QdrantEmbeddingResult.from(input, Vector.fill(input.modelValue.dimension)(0.1)).left.map(error => BeautyQEmbeddingRequestError.Transport(error.toString))
      }
    }
    Context(compiled, evaluation, baseline, qdrant, embedding, baselineService)
  }

  private final class ScriptedElasticsearchClient(
    alias: String,
    target: String,
    mapping: Json,
    metadata: ElasticsearchGenerationMetadata,
    baselineIds: Vector[MasterServiceOfferVariantId],
    membershipFailure: Boolean,
  ) extends ElasticsearchGen2JsonClient {
    private val mappingWithMetadata = mapping.asObject match {
      case Some(obj) => Json.fromJsonObject(obj.add("_meta", ElasticsearchGenerationMetadataCodec.encode(metadata)))
      case None      => mapping
    }

    def postJson(path: String, body: Json): Either[ElasticsearchGen2TransportError, Json] = {
      val isMembership = body.hcursor.downField("_source").as[Boolean].toOption.contains(false)
      val hasGroups = body.hcursor.downField("aggs").focus.nonEmpty
      if (path.endsWith("/_count"))
        Right(Json.obj("count" -> Json.fromLong(metadata.documentCount), "_shards" -> Json.obj("total" -> Json.fromInt(1), "successful" -> Json.fromInt(1), "failed" -> Json.fromInt(0))))
      else if (membershipFailure && isMembership)
        Left(Gen2HttpTransportError.RequestFailed("POST", path, "scripted membership failure"))
      else if (hasGroups)
        Right(groupResponse(body))
      else if (isMembership)
        Right(membershipResponse)
      else
        Right(baselineResponse(body))
    }

    def putJson(path: String, body: Json) = Left(Gen2HttpTransportError.RequestFailed("PUT", path, "must not be called"))
    def post(path: String) = Left(Gen2HttpTransportError.RequestFailed("POST", path, "must not be called"))
    def postNdjson(path: String, body: String) = Left(Gen2HttpTransportError.RequestFailed("POST", path, "must not be called"))
    def delete(path: String) = Left(Gen2HttpTransportError.RequestFailed("DELETE", path, "must not be called"))

    def getJson(path: String): Either[ElasticsearchGen2TransportError, Json] = {
      if (path == s"/_alias/$alias") Right(Json.obj(target -> Json.obj("aliases" -> Json.obj(alias -> Json.obj()))))
      else if (path == s"/$target/_mapping") Right(Json.obj(target -> Json.obj("mappings" -> Json.obj("_meta" -> ElasticsearchGenerationMetadataCodec.encode(metadata), "properties" -> mappingWithMetadata.hcursor.downField("properties").focus.getOrElse(Json.obj())))))
      else Left(Gen2HttpTransportError.RequestFailed("GET", path, "unexpected"))
    }

    private def baselineResponse(body: Json): Json = {
      val sortSize = body.hcursor.downField("sort").focus.flatMap(_.asArray).map(_.size).getOrElse(0)
      val hits = baselineIds.map { id =>
        Json.obj(
          "_id" -> Json.fromString(id.value.toString),
          "_score" -> Json.fromBigDecimal(BigDecimal("1.0")),
          "_source" -> Json.obj("variantId" -> Json.fromString(id.value.toString)),
          "sort" -> Json.fromValues(Vector.fill(sortSize)(Json.fromString(id.value.toString))),
        )
      }
      Json.obj(
        "timed_out" -> Json.fromBoolean(false),
        "_shards" -> Json.obj("total" -> Json.fromInt(1), "successful" -> Json.fromInt(1), "failed" -> Json.fromInt(0)),
        "hits" -> Json.obj("total" -> Json.obj("value" -> Json.fromLong(baselineIds.size.toLong), "relation" -> Json.fromString("eq")), "hits" -> Json.fromValues(hits)),
      )
    }

    private val membershipResponse: Json = Json.obj(
      "hits" -> Json.obj(
        "hits" -> Json.fromValues(baselineIds.map(id => Json.obj("_id" -> Json.fromString(id.value.toString)))),
      ),
    )

    private def groupResponse(body: Json): Json = {
      val names = body.hcursor.downField("aggs").focus.flatMap(_.asObject).map(_.keys.toVector).getOrElse(Vector.empty)
      val aggregations = JsonObject.fromIterable(names.map(name => name -> Json.obj("buckets" -> Json.arr())))
      Json.obj(
        "timed_out" -> Json.fromBoolean(false),
        "_shards" -> Json.obj("total" -> Json.fromInt(1), "successful" -> Json.fromInt(1), "failed" -> Json.fromInt(0)),
        "aggregations" -> Json.fromJsonObject(aggregations),
      )
    }
  }

  private class NoCallQdrantClient extends QdrantGen2Client {
    def getCollection(collection: QdrantResourceName) = Left(Gen2HttpTransportError.RequestFailed("GET", "unused", "must not be called"))
    def listAliases() = Left(Gen2HttpTransportError.RequestFailed("GET", "unused", "must not be called"))
    def createCollection(collection: QdrantResourceName, body: Json) = Left(Gen2HttpTransportError.RequestFailed("PUT", "unused", "must not be called"))
    def createPayloadIndex(collection: QdrantResourceName, body: Json) = Left(Gen2HttpTransportError.RequestFailed("PUT", "unused", "must not be called"))
    def upsertPoints(collection: QdrantResourceName, body: Json) = Left(Gen2HttpTransportError.RequestFailed("PUT", "unused", "must not be called"))
    def countPoints(collection: QdrantResourceName) = Left(Gen2HttpTransportError.RequestFailed("POST", "unused", "must not be called"))
    def updateAliases(body: Json) = Left(Gen2HttpTransportError.RequestFailed("POST", "unused", "must not be called"))
    def queryPoints(target: QdrantResourceName, body: Json) = Left(Gen2HttpTransportError.RequestFailed("POST", "unused", "must not be called"))
  }

  private final class EligibleQdrantClient(generation: QdrantCompiledGeneration, alias: String) extends NoCallQdrantClient {
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
      Right(Json.obj("status" -> Json.fromString("ok"), "result" -> Json.obj("config" -> Json.obj("params" -> Json.obj("vectors" -> vectors), "metadata" -> metadata), "payload_schema" -> payload, "points_count" -> Json.fromInt(generation.metadata.pointCount))))
    }
    override def queryPoints(target: QdrantResourceName, body: Json) = generation.points match {
      case point +: _ => Right(Json.obj("status" -> Json.fromString("ok"), "result" -> Json.obj("points" -> Json.arr(Json.obj("id" -> QdrantPointId.json(point.id), "score" -> Json.fromBigDecimal(BigDecimal("0.9")))))))
      case _ => Right(Json.obj("status" -> Json.fromString("ok"), "result" -> Json.obj("points" -> Json.arr())))
    }
  }
}
