package leaderboard.search.gen2.elasticsearch.lifecycle

import leaderboard.search.gen2.contract.*
import leaderboard.search.gen2.core.materialization.*
import leaderboard.search.gen2.core.plan.*
import leaderboard.search.gen2.elasticsearch.*
import org.scalatest.wordspec.AnyWordSpec

import io.circe.Json
import java.time.{Clock, Instant, ZoneOffset}

final class ElasticsearchGenerationLifecycleSpec extends AnyWordSpec {
  import ElasticsearchTestFixtures.*

  private def compiledGeneration: CompiledElasticsearchGeneration[BookDocument, String] = {
    val materialized = MaterializedSearchDocuments(
      VersionedSnapshot((), ContentFingerprint("source"), None, Instant.parse("2026-01-01T00:00:00Z")),
      Vector(bookA, bookA.copy(isbn = "978-1-11-111111-1")),
      ProjectedDocumentsFingerprint("projected"),
      ProjectionFormatVersion("projection-v1"),
    )
    ElasticsearchGenerationCompiler.compile(fullPolicy, materialized).getOrElse(fail("expected compiled generation"))
  }

  "ElasticsearchGenerationLifecycle" should {
    "reuse a fully validated existing generation and keep the alias stable" in {
      val generation = compiledGeneration
      val persisted = ElasticsearchPersistedGenerationIdentity.fromTrusted(generation.identity)
      val id = ElasticsearchGenerationNaming.generationId(persisted)
      val name = ElasticsearchGenerationNaming.physicalIndexName("books_", persisted).map(_.value).getOrElse(fail("expected name"))
      val metadata = ElasticsearchGenerationMetadata(ElasticsearchGenerationMetadataSchemaVersion.Current, id, Instant.parse("2026-01-01T00:00:00Z"), 2L, persisted)
      val mapping = generation.mapping.json.asObject.getOrElse(fail("expected mapping"))
      val mappingResponse = Json.obj(name -> Json.obj("mappings" -> Json.fromJsonObject(mapping.add("_meta", ElasticsearchGenerationMetadataCodec.encode(metadata)))))
      val client = new ExistingGenerationClient(name, "books", mappingResponse)
      val batching = ElasticsearchBulkBatchingPolicy.create(10, 10000L).getOrElse(fail("expected batching"))
      val config = ElasticsearchGenerationLifecycleConfig.create("books", "books_", ElasticsearchGenerationRetentionPolicy.KeepAll, batching).getOrElse(fail("expected config"))
      val lifecycle = new ElasticsearchGenerationLifecycle(client, config, Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC))

      val resolved = lifecycle.activate(generation).getOrElse(fail("expected activation"))
      assert(resolved.reference == ElasticsearchGenerationReference(name))
      assert(resolved.physicalTarget == ElasticsearchSearchTarget(name))
      assert(resolved.persistedMetadata == metadata)
    }

    "create, batch sequentially, refresh, validate and atomically activate in exact order" in {
      val generation = compiledGeneration
      val setup = creationSetup(generation, liveCount = 2L)
      val client = new ScriptedClient(setup.successExchanges)
      val resolved = setup.lifecycle(client).activate(generation).getOrElse(fail("expected activation"))
      assert(resolved.physicalTarget.value == setup.name)
      client.assertComplete()
    }

    "not delete after an unknown create outcome" in {
      val generation = compiledGeneration
      val setup = creationSetup(generation, liveCount = 2L)
      val client = new ScriptedClient(Vector(
        Get(s"/${setup.name}/_mapping", Left(ElasticsearchGen2TransportError.HttpFailure("GET", s"/${setup.name}/_mapping", 404, "missing"))),
        Put(s"/${setup.name}", setup.createBody, Left(ElasticsearchGen2TransportError.ConnectionFailed("PUT", s"/${setup.name}", "unknown outcome"))),
      ))
      assert(setup.lifecycle(client).activate(generation).isLeft)
      client.assertComplete()
    }

    "delete only the candidate created by this call when pre-alias validation fails" in {
      val generation = compiledGeneration
      val setup = creationSetup(generation, liveCount = 3L)
      val client = new ScriptedClient(setup.provisionExchanges :+ Delete(s"/${setup.name}", Right(())))
      setup.lifecycle(client).activate(generation) match {
        case Left(_: ElasticsearchGenerationLifecycleError.DocumentCountMismatch) => assert(client.isComplete)
        case other => fail(s"expected count mismatch and exact cleanup, got $other")
      }
    }

    "retain a validated generation when alias activation fails" in {
      val generation = compiledGeneration
      val setup = creationSetup(generation, liveCount = 2L)
      val aliasFailure = Vector(
        Get(s"/_alias/${setup.alias}", Left(ElasticsearchGen2TransportError.HttpFailure("GET", s"/_alias/${setup.alias}", 404, "missing"))),
        PostJson("/_aliases", setup.aliasBody, Left(ElasticsearchGen2TransportError.ConnectionFailed("POST", "/_aliases", "alias unavailable"))),
      )
      val client = new ScriptedClient(setup.provisionExchanges ++ aliasFailure)
      assert(setup.lifecycle(client).activate(generation).isLeft)
      client.assertComplete()
    }

    "remove only sorted old alias targets when the new target is already active" in {
      val generation = compiledGeneration
      val setup = creationSetup(generation, liveCount = 2L)
      val oldA = "books_0000000000000000000000000000000000000000000000000000000000000001"
      val oldB = "books_0000000000000000000000000000000000000000000000000000000000000002"
      val aliasResponse = Json.obj(
        oldB -> aliasEntry(setup.alias),
        setup.name -> aliasEntry(setup.alias),
        oldA -> aliasEntry(setup.alias),
      )
      val expectedBody = Json.obj("actions" -> Json.arr(
        removeAlias(oldA, setup.alias),
        removeAlias(oldB, setup.alias),
      ))
      val client = new ScriptedClient(setup.provisionExchanges ++ Vector(
        Get(s"/_alias/${setup.alias}", Right(aliasResponse)),
        PostJson("/_aliases", expectedBody, Right(Json.obj("acknowledged" -> Json.True))),
      ))
      assert(setup.lifecycle(client).activate(generation).isRight)
      client.assertComplete()
    }

    "emit one exact atomic alias update for every old-target state" in {
      val generation = compiledGeneration
      val setup = creationSetup(generation, liveCount = 2L)
      val oldA = "books_0000000000000000000000000000000000000000000000000000000000000001"
      val oldB = "books_0000000000000000000000000000000000000000000000000000000000000002"
      Vector(
        Vector(oldA) -> Vector(removeAlias(oldA, setup.alias), addAlias(setup.name, setup.alias)),
        Vector(oldB, oldA) -> Vector(removeAlias(oldA, setup.alias), removeAlias(oldB, setup.alias), addAlias(setup.name, setup.alias)),
        Vector(oldA, setup.name) -> Vector(removeAlias(oldA, setup.alias)),
        Vector(setup.name) -> Vector.empty,
      ).foreach { case (targets, expectedActions) =>
        val aliasResponse = Json.obj(targets.map(target => target -> aliasEntry(setup.alias))*)
        val update = if (expectedActions.isEmpty) Vector.empty else Vector(
          PostJson("/_aliases", Json.obj("actions" -> Json.fromValues(expectedActions)), Right(Json.obj("acknowledged" -> Json.True)))
        )
        val client = new ScriptedClient(setup.provisionExchanges ++ Vector(Get(s"/_alias/${setup.alias}", Right(aliasResponse))) ++ update)
        assert(setup.lifecycle(client).activate(generation).isRight)
        client.assertComplete()
      }
    }

    "reject a partial count and clean the unvalidated candidate before alias activation" in {
      val generation = compiledGeneration
      val setup = creationSetup(generation, liveCount = 2L)
      val failedCount = PostJson(setup.countPath, setup.countBody, Right(countResponse(2L, total = 2, successful = 1, failed = 1)))
      val client = new ScriptedClient(setup.provisionExchanges.dropRight(1) ++ Vector(failedCount, Delete(s"/${setup.name}", Right(()))))
      setup.lifecycle(client).activate(generation) match {
        case Left(_: ElasticsearchGenerationLifecycleError.PartialCountResponse) => assert(client.isComplete)
        case other => fail(s"expected partial count failure, got $other")
      }
    }

    "reject every malformed count/shard shape and clean the unvalidated candidate" in {
      val generation = compiledGeneration
      val setup = creationSetup(generation, liveCount = 2L)
      Vector(
        "missing shards" -> Json.obj("count" -> Json.fromLong(2L)),
        "negative count" -> countResponse(-1L),
        "negative shard" -> countResponse(2L, total = 1, successful = -1, failed = 0),
        "successful above total" -> countResponse(2L, total = 1, successful = 2, failed = 0),
        "failed above total" -> countResponse(2L, total = 1, successful = 0, failed = 2),
        "shard sum above total" -> countResponse(2L, total = 1, successful = 1, failed = 1),
      ).foreach { case (label, response) =>
        val invalidCount = PostJson(setup.countPath, setup.countBody, Right(response))
        val client = new ScriptedClient(setup.provisionExchanges.dropRight(1) ++ Vector(invalidCount, Delete(s"/${setup.name}", Right(()))))
        setup.lifecycle(client).activate(generation) match {
          case Left(_: ElasticsearchGenerationLifecycleError.InvalidCountResponse) => succeed
          case other => fail(s"expected invalid count for '$label', got $other")
        }
        client.assertComplete()
      }
    }

    "preserve failed bulk item context when the top-level errors flag is true" in {
      val generation = compiledGeneration
      val setup = creationSetup(generation, liveCount = 2L)
      val firstBulk = setup.provisionExchanges.collectFirst { case value: PostNdjson => value }.getOrElse(fail("expected bulk exchange"))
      val rawError = Json.obj("type" -> Json.fromString("mapper_parsing_exception"), "reason" -> Json.fromString("bad source"))
      val failedResponse = Json.obj(
        "errors" -> Json.True,
        "items" -> Json.arr(Json.obj("index" -> Json.obj(
          "_index" -> Json.fromString(setup.name),
          "_id" -> Json.fromString(bookA.isbn),
          "status" -> Json.fromInt(400),
          "error" -> rawError,
        ))),
      )
      val failedBulk = firstBulk.copy(result = Right(failedResponse))
      val client = new ScriptedClient(setup.provisionExchanges.take(2) ++ Vector(failedBulk, Delete(s"/${setup.name}", Right(()))))
      setup.lifecycle(client).activate(generation) match {
        case Left(ElasticsearchGenerationLifecycleError.InvalidBulkItem(0, 0, expectedId, _, expectedTarget, _, Some(400), Some(error))) =>
          assert(expectedId == bookA.isbn)
          assert(expectedTarget == setup.name)
          assert(error == rawError)
        case other => fail(s"expected typed bulk item failure, got $other")
      }
      client.assertComplete()
    }

    "recover only from the canonical resource-already-exists error envelope" in {
      val generation = compiledGeneration
      val setup = creationSetup(generation, liveCount = 2L)
      val raceBody = Json.obj("error" -> Json.obj("type" -> Json.fromString("resource_already_exists_exception"))).noSpaces
      val client = new ScriptedClient(Vector(
        Get(s"/${setup.name}/_mapping", Left(ElasticsearchGen2TransportError.HttpFailure("GET", s"/${setup.name}/_mapping", 404, "missing"))),
        Put(s"/${setup.name}", setup.createBody, Left(ElasticsearchGen2TransportError.HttpFailure("PUT", s"/${setup.name}", 400, raceBody))),
        Get(s"/${setup.name}/_mapping", Right(setup.mappingResponse)),
        PostJson(setup.countPath, setup.countBody, Right(countResponse(2L))),
        Get(s"/_alias/${setup.alias}", Left(ElasticsearchGen2TransportError.HttpFailure("GET", s"/_alias/${setup.alias}", 404, "missing"))),
        PostJson("/_aliases", setup.aliasBody, Right(Json.obj("acknowledged" -> Json.True))),
      ))
      assert(setup.lifecycle(client).activate(generation).isRight)
      client.assertComplete()
    }

    "not treat incidental race text as a create race" in {
      val generation = compiledGeneration
      val setup = creationSetup(generation, liveCount = 2L)
      val unrelated = Json.obj("message" -> Json.fromString("resource_already_exists_exception")).noSpaces
      val client = new ScriptedClient(Vector(
        Get(s"/${setup.name}/_mapping", Left(ElasticsearchGen2TransportError.HttpFailure("GET", s"/${setup.name}/_mapping", 404, "missing"))),
        Put(s"/${setup.name}", setup.createBody, Left(ElasticsearchGen2TransportError.HttpFailure("PUT", s"/${setup.name}", 400, unrelated))),
      ))
      assert(setup.lifecycle(client).activate(generation).isLeft)
      client.assertComplete()
    }

    "reject a count without shard diagnostics and preserve cleanup failure with the primary error" in {
      val generation = compiledGeneration
      val setup = creationSetup(generation, liveCount = 2L)
      val malformedCount = PostJson(setup.countPath, setup.countBody, Right(Json.obj("count" -> Json.fromLong(2L))))
      val cleanupError = ElasticsearchGen2TransportError.ConnectionFailed("DELETE", s"/${setup.name}", "cleanup unavailable")
      val client = new ScriptedClient(setup.provisionExchanges.dropRight(1) ++ Vector(malformedCount, Delete(s"/${setup.name}", Left(cleanupError))))
      setup.lifecycle(client).activate(generation) match {
        case Left(ElasticsearchGenerationLifecycleError.CleanupFailed(primary: ElasticsearchGenerationLifecycleError.InvalidCountResponse, cleanup)) =>
          assert(primary.target == setup.name)
          assert(cleanup == cleanupError)
        case other => fail(s"expected primary count plus cleanup failure, got $other")
      }
      client.assertComplete()
    }

    "reject an inconsistent bulk errors flag" in {
      val generation = compiledGeneration
      val setup = creationSetup(generation, liveCount = 2L)
      val firstBulk = setup.provisionExchanges.collectFirst { case value: PostNdjson => value }.getOrElse(fail("expected bulk exchange"))
      val successItem = Json.obj("index" -> Json.obj(
        "_index" -> Json.fromString(setup.name),
        "_id" -> Json.fromString(bookA.isbn),
        "status" -> Json.fromInt(201),
      ))
      val inconsistent = firstBulk.copy(result = Right(Json.obj("errors" -> Json.True, "items" -> Json.arr(successItem))))
      val client = new ScriptedClient(setup.provisionExchanges.take(2) ++ Vector(inconsistent, Delete(s"/${setup.name}", Right(()))))
      setup.lifecycle(client).activate(generation) match {
        case Left(_: ElasticsearchGenerationLifecycleError.InvalidBulkResponse) => assert(client.isComplete)
        case other => fail(s"expected inconsistent bulk response, got $other")
      }
    }

    "reject every inconsistent bulk flag, item envelope and item identity shape" in {
      val generation = compiledGeneration
      val setup = creationSetup(generation, liveCount = 2L)
      val firstBulk = setup.provisionExchanges.collectFirst { case value: PostNdjson => value }.getOrElse(fail("expected bulk exchange"))
      def item(
        target: String = setup.name,
        id: String = bookA.isbn,
        status: Option[Json] = Some(Json.fromInt(201)),
        error: Option[Json] = None,
      ): Json = {
        val fields = Vector(
          Some("_index" -> Json.fromString(target)),
          Some("_id" -> Json.fromString(id)),
          status.map("status" -> _),
          error.map("error" -> _),
        ).flatten
        Json.obj("index" -> Json.obj(fields*))
      }
      val rawError = Json.obj("type" -> Json.fromString("mapper_parsing_exception"))
      val cases = Vector(
        ("false with failed item", Json.obj("errors" -> Json.False, "items" -> Json.arr(item(status = Some(Json.fromInt(400))))), false),
        ("true with successful item", Json.obj("errors" -> Json.True, "items" -> Json.arr(item())), false),
        ("item count mismatch", Json.obj("errors" -> Json.False, "items" -> Json.arr()), false),
        ("missing errors", Json.obj("items" -> Json.arr(item())), false),
        ("wrong errors", Json.obj("errors" -> Json.fromString("false"), "items" -> Json.arr(item())), false),
        ("missing items", Json.obj("errors" -> Json.False), false),
        ("wrong items", Json.obj("errors" -> Json.False, "items" -> Json.obj()), false),
        ("wrong target", Json.obj("errors" -> Json.True, "items" -> Json.arr(item(target = "other"))), true),
        ("wrong id", Json.obj("errors" -> Json.True, "items" -> Json.arr(item(id = "other"))), true),
        ("missing status", Json.obj("errors" -> Json.True, "items" -> Json.arr(item(status = None))), true),
        ("non-integer status", Json.obj("errors" -> Json.True, "items" -> Json.arr(item(status = Some(Json.fromString("201"))))), true),
        ("fractional status", Json.obj("errors" -> Json.True, "items" -> Json.arr(item(status = Some(Json.fromDoubleOrNull(201.5))))), true),
        ("2xx with error", Json.obj("errors" -> Json.True, "items" -> Json.arr(item(error = Some(rawError)))), true),
        ("non-2xx without error", Json.obj("errors" -> Json.True, "items" -> Json.arr(item(status = Some(Json.fromInt(400))))), true),
      )
      cases.foreach { case (label, response, expectItemFailure) =>
        val client = new ScriptedClient(setup.provisionExchanges.take(2) ++ Vector(
          firstBulk.copy(result = Right(response)),
          Delete(s"/${setup.name}", Right(())),
        ))
        setup.lifecycle(client).activate(generation) match {
          case Left(_: ElasticsearchGenerationLifecycleError.InvalidBulkItem) if expectItemFailure => succeed
          case Left(_: ElasticsearchGenerationLifecycleError.InvalidBulkResponse) if !expectItemFailure => succeed
          case other => fail(s"expected classified bulk failure for '$label', got $other")
        }
        client.assertComplete()
      }
    }

    "reject every non-canonical create-race response without reuse or cleanup" in {
      val generation = compiledGeneration
      val setup = creationSetup(generation, liveCount = 2L)
      val canonical = Json.obj("error" -> Json.obj("type" -> Json.fromString("resource_already_exists_exception"))).noSpaces
      Vector(
        (400, "not-json"),
        (400, Json.obj("error" -> Json.obj("type" -> Json.fromString("other"))).noSpaces),
        (400, Json.obj("message" -> Json.fromString("resource_already_exists_exception")).noSpaces),
        (400, Json.obj("error" -> Json.obj("reason" -> Json.fromString("resource_already_exists_exception"))).noSpaces),
        (409, canonical),
      ).foreach { case (status, body) =>
        val failure = ElasticsearchGen2TransportError.HttpFailure("PUT", s"/${setup.name}", status, body)
        val client = new ScriptedClient(Vector(
          Get(s"/${setup.name}/_mapping", Left(ElasticsearchGen2TransportError.HttpFailure("GET", s"/${setup.name}/_mapping", 404, "missing"))),
          Put(s"/${setup.name}", setup.createBody, Left(failure)),
        ))
        assert(setup.lifecycle(client).activate(generation) == Left(ElasticsearchGenerationLifecycleError.Transport("create-index", failure)))
        client.assertComplete()
      }
    }

    "reject an empty physical prefix" in {
      val batching = ElasticsearchBulkBatchingPolicy.create(1, 1000L).getOrElse(fail("expected batching"))
      assert(ElasticsearchGenerationLifecycleConfig.create("books", "", ElasticsearchGenerationRetentionPolicy.KeepAll, batching) ==
        Left(ElasticsearchGenerationLifecycleConfigError.InvalidPhysicalIndexPrefix("")))
    }

    "resolve and authorize the one active generation with its complete persisted metadata" in {
      val setup = resolutionSetup()
      val client = new ScriptedClient(Vector(
        Get(s"/_alias/${setup.alias}", Right(Json.obj(setup.name -> aliasEntry(setup.alias)))),
        Get(s"/${setup.name}/_mapping", Right(setup.mappingResponse)),
        PostJson(s"/${setup.name}/_count", countQuery, Right(countResponse(setup.metadata.documentCount))),
      ))

      setup.lifecycle(client).authorize(preparedActive()) match {
        case Right(authorized) =>
          assert(authorized.target == ElasticsearchSearchTarget(setup.name))
          assert(authorized.generation.persistedMetadata == setup.metadata)
        case other => fail(s"expected active generation authorization, got $other")
      }
      client.assertComplete()
    }

    "return typed active-generation errors before mapping resolution" in {
      val setup = resolutionSetup()
      val missing = ElasticsearchGen2TransportError.HttpFailure("GET", s"/_alias/${setup.alias}", 404, "missing")
      val missingClient = new ScriptedClient(Vector(Get(s"/_alias/${setup.alias}", Left(missing))))
      assert(setup.lifecycle(missingClient).authorize(preparedActive()) ==
        Left(ElasticsearchGenerationLifecycleError.MissingActiveGeneration(setup.alias)))
      missingClient.assertComplete()

      val names = Vector(
        "books_0000000000000000000000000000000000000000000000000000000000000002",
        "books_0000000000000000000000000000000000000000000000000000000000000001",
      )
      val ambiguousClient = new ScriptedClient(Vector(
        Get(s"/_alias/${setup.alias}", Right(Json.obj(names.map(name => name -> aliasEntry(setup.alias))*))),
      ))
      assert(setup.lifecycle(ambiguousClient).authorize(preparedActive()) ==
        Left(ElasticsearchGenerationLifecycleError.AmbiguousActiveGeneration(setup.alias, names.sorted)))
      ambiguousClient.assertComplete()
    }

    "resolve and authorize an exact retained pinned generation" in {
      val setup = resolutionSetup()
      val client = new ScriptedClient(Vector(
        Get(s"/${setup.name}/_mapping", Right(setup.mappingResponse)),
        PostJson(s"/${setup.name}/_count", countQuery, Right(countResponse(setup.metadata.documentCount))),
      ))

      setup.lifecycle(client).authorize(preparedPinned(setup.name)) match {
        case Right(authorized) =>
          assert(authorized.generationReference == ElasticsearchGenerationReference(setup.name))
          assert(authorized.generation.persistedMetadata == setup.metadata)
        case other => fail(s"expected pinned generation authorization, got $other")
      }
      client.assertComplete()
    }

    "reject invalid pinned references before transport" in {
      Vector(
        "other_0000000000000000000000000000000000000000000000000000000000000000",
        "books_not-a-canonical-hash",
      ).foreach { reference =>
        val setup = resolutionSetup()
        val client = new ScriptedClient(Vector.empty)
        setup.lifecycle(client).authorize(preparedPinned(reference)) match {
          case Left(_: ElasticsearchGenerationLifecycleError.Naming) => succeed
          case other => fail(s"expected naming rejection for '$reference', got $other")
        }
        client.assertComplete()
      }
    }

    "preserve missing pinned mapping as a typed transport failure" in {
      val setup = resolutionSetup()
      val failure = ElasticsearchGen2TransportError.HttpFailure("GET", s"/${setup.name}/_mapping", 404, "missing")
      val client = new ScriptedClient(Vector(Get(s"/${setup.name}/_mapping", Left(failure))))
      assert(setup.lifecycle(client).authorize(preparedPinned(setup.name)) ==
        Left(ElasticsearchGenerationLifecycleError.Transport("get-mapping", failure)))
      client.assertComplete()
    }

    "reject pinned metadata and deterministic physical-name mismatches" in {
      val setup = resolutionSetup()
      val wrongIdMetadata = setup.metadata.copy(generationId = ElasticsearchGenerationId("0".repeat(64)))
      val wrongIdClient = new ScriptedClient(Vector(
        Get(s"/${setup.name}/_mapping", Right(mappingResponse(setup.name, setup.mapping, wrongIdMetadata))),
      ))
      setup.lifecycle(wrongIdClient).authorize(preparedPinned(setup.name)) match {
        case Left(ElasticsearchGenerationLifecycleError.Naming(_: ElasticsearchGenerationNamingError.GenerationReferenceMismatch)) => succeed
        case other => fail(s"expected generation-ID mismatch, got $other")
      }
      wrongIdClient.assertComplete()

      val otherName = "books_ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"
      val wrongNameClient = new ScriptedClient(Vector(
        Get(s"/$otherName/_mapping", Right(mappingResponse(otherName, setup.mapping, setup.metadata))),
      ))
      setup.lifecycle(wrongNameClient).authorize(preparedPinned(otherName)) match {
        case Left(ElasticsearchGenerationLifecycleError.Naming(_: ElasticsearchGenerationNamingError.GenerationReferenceMismatch)) => succeed
        case other => fail(s"expected deterministic physical-name mismatch, got $other")
      }
      wrongNameClient.assertComplete()
    }

    "flow a persisted contract mismatch through lifecycle authorization" in {
      val base = resolutionSetup()
      val otherIdentity = base.metadata.identity.copy(contractFingerprint = "other-contract")
      val otherMetadata = base.metadata.copy(
        generationId = ElasticsearchGenerationNaming.generationId(otherIdentity),
        identity = otherIdentity,
      )
      val otherName = ElasticsearchGenerationNaming.physicalIndexName("books_", otherIdentity).map(_.value).getOrElse(fail("expected other name"))
      val client = new ScriptedClient(Vector(
        Get(s"/$otherName/_mapping", Right(mappingResponse(otherName, base.mapping, otherMetadata))),
        PostJson(s"/$otherName/_count", countQuery, Right(countResponse(otherMetadata.documentCount))),
      ))

      base.lifecycle(client).authorize(preparedPinned(otherName)) match {
        case Left(ElasticsearchGenerationLifecycleError.Authorization(_: ElasticsearchSearchRequestAuthorizationError.GenerationContractFingerprintMismatch)) => succeed
        case other => fail(s"expected authorization fingerprint mismatch, got $other")
      }
      client.assertComplete()
    }

    "prevent a partial pinned count from producing an authorized request" in {
      val setup = resolutionSetup()
      val client = new ScriptedClient(Vector(
        Get(s"/${setup.name}/_mapping", Right(setup.mappingResponse)),
        PostJson(s"/${setup.name}/_count", countQuery, Right(countResponse(setup.metadata.documentCount, total = 2, successful = 1, failed = 1))),
      ))
      setup.lifecycle(client).authorize(preparedPinned(setup.name)) match {
        case Left(_: ElasticsearchGenerationLifecycleError.PartialCountResponse) => succeed
        case other => fail(s"expected partial count rejection, got $other")
      }
      client.assertComplete()
    }

    "accept a valid zero count for a retained empty generation" in {
      val base = resolutionSetup()
      val metadata = base.metadata.copy(documentCount = 0L)
      val response = mappingResponse(base.name, base.mapping, metadata)
      val client = new ScriptedClient(Vector(
        Get(s"/${base.name}/_mapping", Right(response)),
        PostJson(s"/${base.name}/_count", countQuery, Right(countResponse(0L))),
      ))
      base.lifecycle(client).authorize(preparedPinned(base.name)) match {
        case Right(authorized) => assert(authorized.generation.persistedMetadata == metadata)
        case other => fail(s"expected zero-count authorization, got $other")
      }
      client.assertComplete()
    }
  }

  private def searchPlan(cursor: Option[SearchCursor] = None): SearchPlan[BookDocument] =
    SearchPlan(
      None,
      Vector.empty,
      Vector.empty,
      Vector.empty,
      PageRequest(cursor, PageSize.from(2).getOrElse(fail("expected page size"))),
      Vector.empty,
      Vector.empty,
      PlanDiagnostics.empty,
    )

  private def bound(plan: SearchPlan[BookDocument]): BoundSearchPlan[BookDocument] =
    SearchCursorEnvelope.bind(plan, CanonicalPlanView[BookDocument](fullPolicy.contractFingerprint)).getOrElse(fail("expected bound plan"))

  private def preparedActive(): PreparedElasticsearchSearchRequest[BookDocument, String] =
    ElasticsearchSearchRequestCompiler.compile(fullPolicy, bound(searchPlan())).getOrElse(fail("expected active prepared request"))

  private def preparedPinned(reference: String): PreparedElasticsearchSearchRequest[BookDocument, String] = {
    val first = bound(searchPlan())
    val state = ElasticsearchCursorState(
      ElasticsearchSearchCompilerVersion.Current,
      ElasticsearchGenerationReference(reference),
      Vector(ElasticsearchSearchAfterValue.Text("978-1-11-111111-1")),
    )
    val issued = SearchCursorEnvelope.issue(first, ElasticsearchCursorStateCodec.encode(state))
    val next = bound(searchPlan(Some(SearchCursor.fromTransport(issued.opaqueValue))))
    ElasticsearchSearchRequestCompiler.compile(fullPolicy, next).getOrElse(fail("expected pinned prepared request"))
  }

  private final case class ResolutionSetup(
    alias: String,
    name: String,
    metadata: ElasticsearchGenerationMetadata,
    mapping: Json,
    mappingResponse: Json,
    lifecycle: ElasticsearchGen2JsonClient => ElasticsearchGenerationLifecycle,
  )

  private def resolutionSetup(): ResolutionSetup = {
    val generation = compiledGeneration
    val alias = "books"
    val persisted = ElasticsearchPersistedGenerationIdentity.fromTrusted(generation.identity)
    val metadata = ElasticsearchGenerationMetadata(
      ElasticsearchGenerationMetadataSchemaVersion.Current,
      ElasticsearchGenerationNaming.generationId(persisted),
      Instant.parse("2026-01-01T00:00:00Z"),
      generation.documents.length.toLong,
      persisted,
    )
    val name = ElasticsearchGenerationNaming.physicalIndexName("books_", persisted).map(_.value).getOrElse(fail("expected generation name"))
    val mapping = generation.mapping.json
    val response = mappingResponse(name, mapping, metadata)
    val batching = ElasticsearchBulkBatchingPolicy.create(1, 100000L).getOrElse(fail("expected batching"))
    val factory = (client: ElasticsearchGen2JsonClient) => {
      val config = ElasticsearchGenerationLifecycleConfig.create(alias, "books_", ElasticsearchGenerationRetentionPolicy.KeepAll, batching).getOrElse(fail("expected config"))
      new ElasticsearchGenerationLifecycle(client, config, Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC))
    }
    ResolutionSetup(alias, name, metadata, mapping, response, factory)
  }

  private def mappingResponse(name: String, mapping: Json, metadata: ElasticsearchGenerationMetadata): Json = {
    val mappings = mapping.asObject.getOrElse(fail("expected mapping object")).add("_meta", ElasticsearchGenerationMetadataCodec.encode(metadata))
    Json.obj(name -> Json.obj("mappings" -> Json.fromJsonObject(mappings)))
  }

  private val countQuery: Json = Json.obj("query" -> Json.obj("match_all" -> Json.obj()))

  private final case class CreationSetup(
    alias: String,
    name: String,
    createBody: Json,
    aliasBody: Json,
    mappingResponse: Json,
    countPath: String,
    countBody: Json,
    provisionExchanges: Vector[Exchange],
    successExchanges: Vector[Exchange],
    lifecycle: ElasticsearchGen2JsonClient => ElasticsearchGenerationLifecycle,
  )

  private def countResponse(count: Long, total: Int = 1, successful: Int = 1, failed: Int = 0): Json =
    Json.obj(
      "count" -> Json.fromLong(count),
      "_shards" -> Json.obj(
        "total" -> Json.fromInt(total),
        "successful" -> Json.fromInt(successful),
        "failed" -> Json.fromInt(failed),
      ),
    )

  private def creationSetup(
    generation: CompiledElasticsearchGeneration[BookDocument, String],
    liveCount: Long,
  ): CreationSetup = {
    val alias = "books"
    val persisted = ElasticsearchPersistedGenerationIdentity.fromTrusted(generation.identity)
    val id = ElasticsearchGenerationNaming.generationId(persisted)
    val name = ElasticsearchGenerationNaming.physicalIndexName("books_", persisted).map(_.value).getOrElse(fail("expected name"))
    val metadata = ElasticsearchGenerationMetadata(ElasticsearchGenerationMetadataSchemaVersion.Current, id, Instant.parse("2026-01-01T00:00:00Z"), 2L, persisted)
    val mapping = generation.mapping.json.asObject.getOrElse(fail("expected mapping"))
    val mappingWithMeta = Json.fromJsonObject(mapping.add("_meta", ElasticsearchGenerationMetadataCodec.encode(metadata)))
    val mappingResponse = Json.obj(name -> Json.obj("mappings" -> mappingWithMeta))
    val createBody = Json.obj("mappings" -> mappingWithMeta)
    val batching = ElasticsearchBulkBatchingPolicy.create(1, 100000L).getOrElse(fail("expected batching"))
    val batches = ElasticsearchBulkEncoder.encode(generation.documents, batching).getOrElse(fail("expected batches"))
    val bulk = batches.map { batch =>
      val items = batch.documents.map(document => Json.obj("index" -> Json.obj(
        "_index" -> Json.fromString(name),
        "_id" -> Json.fromString(document.id),
        "status" -> Json.fromInt(201),
      )))
      PostNdjson(s"/$name/_bulk", batch.body, Right(Json.obj("errors" -> Json.False, "items" -> Json.fromValues(items))))
    }
    val provision = Vector[Exchange](
      Get(s"/$name/_mapping", Left(ElasticsearchGen2TransportError.HttpFailure("GET", s"/$name/_mapping", 404, "missing"))),
      Put(s"/$name", createBody, Right(Json.obj("acknowledged" -> Json.True, "shards_acknowledged" -> Json.True, "index" -> Json.fromString(name)))),
    ) ++ bulk ++ Vector(
      Post(s"/$name/_refresh", Right(Json.obj("_shards" -> Json.obj("total" -> Json.fromInt(1), "successful" -> Json.fromInt(1), "failed" -> Json.fromInt(0))))),
      Get(s"/$name/_mapping", Right(mappingResponse)),
      PostJson(s"/$name/_count", Json.obj("query" -> Json.obj("match_all" -> Json.obj())), Right(countResponse(liveCount))),
    )
    val aliasBody = Json.obj("actions" -> Json.arr(Json.obj("add" -> Json.obj("index" -> Json.fromString(name), "alias" -> Json.fromString(alias)))))
    val success = provision ++ Vector(
      Get(s"/_alias/$alias", Left(ElasticsearchGen2TransportError.HttpFailure("GET", s"/_alias/$alias", 404, "missing"))),
      PostJson("/_aliases", aliasBody, Right(Json.obj("acknowledged" -> Json.True))),
    )
    val lifecycleFactory = (client: ElasticsearchGen2JsonClient) => {
      val config = ElasticsearchGenerationLifecycleConfig.create(alias, "books_", ElasticsearchGenerationRetentionPolicy.KeepAll, batching).getOrElse(fail("expected config"))
      new ElasticsearchGenerationLifecycle(client, config, Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC))
    }
    CreationSetup(alias, name, createBody, aliasBody, mappingResponse, s"/$name/_count", Json.obj("query" -> Json.obj("match_all" -> Json.obj())), provision, success, lifecycleFactory)
  }

  private def aliasEntry(alias: String): Json = Json.obj("aliases" -> Json.obj(alias -> Json.obj()))
  private def removeAlias(index: String, alias: String): Json = Json.obj("remove" -> Json.obj("index" -> Json.fromString(index), "alias" -> Json.fromString(alias)))
  private def addAlias(index: String, alias: String): Json = Json.obj("add" -> Json.obj("index" -> Json.fromString(index), "alias" -> Json.fromString(alias)))

  private sealed trait Exchange
  private final case class Get(path: String, result: Either[ElasticsearchGen2TransportError, Json]) extends Exchange
  private final case class Put(path: String, body: Json, result: Either[ElasticsearchGen2TransportError, Json]) extends Exchange
  private final case class Post(path: String, result: Either[ElasticsearchGen2TransportError, Json]) extends Exchange
  private final case class PostJson(path: String, body: Json, result: Either[ElasticsearchGen2TransportError, Json]) extends Exchange
  private final case class PostNdjson(path: String, body: String, result: Either[ElasticsearchGen2TransportError, Json]) extends Exchange
  private final case class Delete(path: String, result: Either[ElasticsearchGen2TransportError, Unit]) extends Exchange

  private final class ScriptedClient(exchanges: Vector[Exchange]) extends ElasticsearchGen2JsonClient {
    private var cursor = 0

    def isComplete: Boolean = cursor == exchanges.length
    def assertComplete(): Unit = {
      assert(isComplete, s"expected ${exchanges.length} exchanges, consumed $cursor")
      (): Unit
    }

    private def next(): Exchange =
      exchanges.lift(cursor) match {
        case Some(exchange) =>
          cursor += 1
          exchange
        case None => fail(s"unexpected exchange after ${exchanges.length} scripted calls")
      }

    def getJson(path: String) = next() match {
      case Get(expected, result) if expected == path => result
      case other => fail(s"expected $other, got getJson($path)")
    }
    def putJson(path: String, body: Json) = next() match {
      case Put(expectedPath, expectedBody, result) if expectedPath == path && expectedBody == body => result
      case other => fail(s"expected $other, got putJson($path, $body)")
    }
    def post(path: String) = next() match {
      case Post(expected, result) if expected == path => result
      case other => fail(s"expected $other, got post($path)")
    }
    def postJson(path: String, body: Json) = next() match {
      case PostJson(expectedPath, expectedBody, result) if expectedPath == path && expectedBody == body => result
      case other => fail(s"expected $other, got postJson($path, $body)")
    }
    def postNdjson(path: String, body: String) = next() match {
      case PostNdjson(expectedPath, expectedBody, result) if expectedPath == path && expectedBody == body => result
      case other => fail(s"expected $other, got postNdjson($path, $body)")
    }
    def delete(path: String) = next() match {
      case Delete(expected, result) if expected == path => result
      case other => fail(s"expected $other, got delete($path)")
    }
  }

  private final class ExistingGenerationClient(name: String, alias: String, mapping: Json) extends ElasticsearchGen2JsonClient {
    def getJson(path: String) = path match {
      case value if value == s"/$name/_mapping" => Right(mapping)
      case value if value == s"/_alias/$alias" => Right(Json.obj(name -> Json.obj("aliases" -> Json.obj(alias -> Json.obj()))))
      case other => fail(s"unexpected getJson($other)")
    }
    def postJson(path: String, body: Json) = path match {
      case value if value == s"/$name/_count" => Right(countResponse(2L))
      case other => fail(s"unexpected postJson($other, $body)")
    }
    def putJson(path: String, body: Json) = fail(s"unexpected putJson($path, $body)")
    def post(path: String) = fail(s"unexpected post($path)")
    def postNdjson(path: String, body: String) = fail(s"unexpected postNdjson($path, $body)")
    def delete(path: String) = fail(s"unexpected delete($path)")
  }
}
