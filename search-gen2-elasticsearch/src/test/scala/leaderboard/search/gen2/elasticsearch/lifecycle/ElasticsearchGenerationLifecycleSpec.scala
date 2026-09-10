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

  private def compiledGeneration: CompiledElasticsearchGeneration[BookDocument, String] = compiledGeneration(fullPolicy)

  private def compiledGeneration(policy: ElasticsearchPolicy[BookDocument, String]): CompiledElasticsearchGeneration[BookDocument, String] = {
    val materialized = MaterializedSearchDocuments(
      VersionedSnapshot((), ContentFingerprint("source"), Instant.parse("2026-01-01T00:00:00Z")),
      Vector(bookA, bookA.copy(isbn = "978-1-11-111111-1")),
      ProjectedDocumentsFingerprint("projected"),
      ProjectionFormatVersion("projection-v1"),
    )
    ElasticsearchGenerationCompiler.compile(policy, materialized).getOrElse(fail("expected compiled generation"))
  }

  private def compiledGenerationWithPolicyVersion(version: String): CompiledElasticsearchGeneration[BookDocument, String] = {
    val index = ElasticsearchIndexPolicy.unsafeFrom(document, ElasticsearchPolicyVersion(version), bothTextFields)
    val policy = ElasticsearchPolicy.unsafeFrom(
      planContractVersion,
      index,
      queryTextFields,
      ElasticsearchTextOperator.Or,
      Some(geoScoringPolicy),
      ElasticsearchTotalHitsPolicy.ExactRequired,
      defaultSortPolicy,
    )
    compiledGeneration(policy)
  }

  private def ownedListing(prefix: String, generation: CompiledElasticsearchGeneration[BookDocument, String]): (String, Json) = {
    val persisted = ElasticsearchPersistedGenerationIdentity.fromTrusted(generation.identity)
    val id = ElasticsearchGenerationNaming.generationId(persisted)
    val name = ElasticsearchGenerationNaming.physicalIndexName(prefix, persisted).map(_.value).getOrElse(fail("expected owned generation name"))
    val metadata = ElasticsearchGenerationMetadata(
      ElasticsearchGenerationMetadataSchemaVersion.Current,
      id,
      Instant.parse("2026-01-01T00:00:00Z"),
      generation.documents.length.toLong,
      persisted,
    )
    name -> Json.obj("mappings" -> Json.obj("_meta" -> ElasticsearchGenerationMetadataCodec.encode(metadata)))
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
      val config = ElasticsearchGenerationLifecycleConfig.create("books", "books_", batching).getOrElse(fail("expected config"))
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

    "decode present activation action results in the real wire shape" in {
      val generation = compiledGeneration
      val setup = creationSetup(generation, liveCount = 2L)
      val result = Json.obj(
        "action" -> Json.obj(
          "type" -> Json.fromString("add"),
          "indices" -> Json.arr(Json.fromString(setup.name)),
          "aliases" -> Json.arr(Json.fromString(setup.alias)),
        ),
        "status" -> Json.fromInt(200),
      )
      val client = new ScriptedClient(setup.provisionExchanges ++ Vector(
        Get(s"/_alias/${setup.alias}", Left(ElasticsearchGen2TransportError.HttpFailure("GET", s"/_alias/${setup.alias}", 404, "missing"))),
        Get(s"/_alias/${setup.alias}--superseded", Left(ElasticsearchGen2TransportError.HttpFailure("GET", s"/_alias/${setup.alias}--superseded", 404, "missing"))),
        PostJson("/_aliases", setup.aliasBody, Right(Json.obj("acknowledged" -> Json.True, "errors" -> Json.False, "action_results" -> Json.arr(result)))),
        Get(s"/_alias/${setup.alias}--superseded", Left(ElasticsearchGen2TransportError.HttpFailure("GET", s"/_alias/${setup.alias}--superseded", 404, "missing"))),
      ))
      assert(setup.lifecycle(client).activate(generation).isRight)
      client.assertComplete()
    }

    "delete every superseded exact generation after a later activation" in {
      val first = compiledGeneration
      val second = compiledGenerationWithPolicyVersion("book-elasticsearch-v2")
      val firstSetup = creationSetup(first, liveCount = 2L)
      val secondSetup = creationSetup(second, liveCount = 2L)
      val firstClient = new ScriptedClient(firstSetup.successExchanges)
      assert(firstSetup.lifecycle(firstClient).activate(first).isRight)
      firstClient.assertComplete()

      val aliasResponse = Json.obj(firstSetup.name -> aliasEntry(firstSetup.alias))
      val aliasBody = Json.obj("actions" -> Json.arr(
        removeAlias(firstSetup.name, firstSetup.alias),
        addAlias(firstSetup.name, s"${firstSetup.alias}--superseded"),
        addAlias(secondSetup.name, secondSetup.alias),
      ))
      val secondClient = new ScriptedClient(secondSetup.provisionExchanges ++ Vector(
        Get(s"/_alias/${secondSetup.alias}", Right(aliasResponse)),
        Get(s"/_alias/${secondSetup.alias}--superseded", Left(ElasticsearchGen2TransportError.HttpFailure("GET", s"/_alias/${secondSetup.alias}--superseded", 404, "missing"))),
        PostJson("/_aliases", aliasBody, Right(Json.obj("acknowledged" -> Json.True, "errors" -> Json.False))),
        Get(s"/_alias/${secondSetup.alias}--superseded", Right(Json.obj(firstSetup.name -> aliasEntry(s"${secondSetup.alias}--superseded")))),
        Get(s"/${firstSetup.name}/_mapping", Right(Json.obj(firstSetup.name -> ownedListing("books_", first)._2))),
        Get(s"/_alias/${secondSetup.alias}", Right(Json.obj(secondSetup.name -> aliasEntry(secondSetup.alias)))),
        PostJson("/_aliases", guardedCleanupBody(secondSetup.name, Vector(firstSetup.name)), Right(Json.obj("acknowledged" -> Json.True, "errors" -> Json.False))),
      ))
      assert(secondSetup.lifecycle(secondClient).activate(second).isRight)
      secondClient.assertComplete()
    }

    "remove a stale superseded marker when reactivating a generation" in {
      val generationA = compiledGeneration
      val generationB = compiledGenerationWithPolicyVersion("book-elasticsearch-reactivation-b")
      val setupA = creationSetup(generationA, liveCount = 2L)
      val setupB = creationSetup(generationB, liveCount = 2L)
      val expectedActivation = Json.obj("actions" -> Json.arr(
        removeAlias(setupA.name, s"${setupA.alias}--superseded"),
        removeAlias(setupB.name, setupB.alias),
        addAlias(setupB.name, s"${setupB.alias}--superseded"),
        addAlias(setupA.name, setupA.alias),
      ))
      val client = new ScriptedClient(Vector(
        Get(s"/${setupA.name}/_mapping", Right(setupA.mappingResponse)),
        PostJson(setupA.countPath, setupA.countBody, Right(countResponse(2L))),
        Get(s"/_alias/${setupA.alias}", Right(Json.obj(setupB.name -> aliasEntry(setupB.alias)))),
        Get(s"/_alias/${setupA.alias}--superseded", Right(Json.obj(setupA.name -> aliasEntry(s"${setupA.alias}--superseded")))),
        PostJson("/_aliases", expectedActivation, Right(Json.obj("acknowledged" -> Json.True, "errors" -> Json.False))),
        Get(s"/_alias/${setupA.alias}--superseded", Right(Json.obj(setupB.name -> aliasEntry(s"${setupA.alias}--superseded")))),
        Get(s"/${setupB.name}/_mapping", Right(setupB.mappingResponse)),
        Get(s"/_alias/${setupA.alias}", Right(Json.obj(setupA.name -> aliasEntry(setupA.alias)))),
        PostJson("/_aliases", guardedCleanupBody(setupA.name, Vector(setupB.name)), Right(Json.obj("acknowledged" -> Json.True, "errors" -> Json.False))),
      ))
      assert(setupA.lifecycle(client).activate(generationA).isRight)
      client.assertComplete()
    }

    "leave a valid in-progress candidate untouched because it has no superseded alias" in {
      val first = compiledGeneration
      val second = compiledGenerationWithPolicyVersion("book-elasticsearch-in-progress-b")
      val candidate = compiledGenerationWithPolicyVersion("book-elasticsearch-in-progress-c")
      val firstSetup = creationSetup(first, liveCount = 2L)
      val secondSetup = creationSetup(second, liveCount = 2L)
      val candidateEntry = ownedListing("books_", candidate)
      val firstClient = new ScriptedClient(firstSetup.successExchanges)
      assert(firstSetup.lifecycle(firstClient).activate(first).isRight)
      firstClient.assertComplete()

      val activationBody = Json.obj("actions" -> Json.arr(
        removeAlias(firstSetup.name, firstSetup.alias),
        addAlias(firstSetup.name, s"${firstSetup.alias}--superseded"),
        addAlias(secondSetup.name, secondSetup.alias),
      ))
      val client = new ScriptedClient(secondSetup.provisionExchanges ++ Vector(
        Get(s"/_alias/${secondSetup.alias}", Right(Json.obj(firstSetup.name -> aliasEntry(firstSetup.alias)))),
        Get(s"/_alias/${secondSetup.alias}--superseded", Left(ElasticsearchGen2TransportError.HttpFailure("GET", s"/_alias/${secondSetup.alias}--superseded", 404, "missing"))),
        PostJson("/_aliases", activationBody, Right(Json.obj("acknowledged" -> Json.True, "errors" -> Json.False))),
        Get(s"/_alias/${secondSetup.alias}--superseded", Right(Json.obj(firstSetup.name -> aliasEntry(s"${secondSetup.alias}--superseded")))),
        Get(s"/${firstSetup.name}/_mapping", Right(Json.obj(firstSetup.name -> ownedListing("books_", first)._2))),
        Get(s"/_alias/${secondSetup.alias}", Right(Json.obj(secondSetup.name -> aliasEntry(secondSetup.alias)))),
        PostJson("/_aliases", guardedCleanupBody(secondSetup.name, Vector(firstSetup.name)), Right(guardedCleanupResponse(secondSetup.name, Vector(cleanupActionResult("remove_index", firstSetup.name, 200, None)), errors = false))),
      ))
      assert(candidateEntry._2.hcursor.downField("mappings").downField("_meta").focus.nonEmpty)
      assert(secondSetup.lifecycle(client).activate(second).isRight)
      client.assertComplete()
    }

    "reject malformed superseded-alias state before any destructive action" in {
      val generation = compiledGeneration
      val setup = creationSetup(generation, liveCount = 2L)
      val client = new ScriptedClient(setup.provisionExchanges ++ Vector(
        Get(s"/_alias/${setup.alias}", Left(ElasticsearchGen2TransportError.HttpFailure("GET", s"/_alias/${setup.alias}", 404, "missing"))),
        Get(s"/_alias/${setup.alias}--superseded", Left(ElasticsearchGen2TransportError.HttpFailure("GET", s"/_alias/${setup.alias}--superseded", 404, "missing"))),
        PostJson("/_aliases", setup.aliasBody, Right(Json.obj("acknowledged" -> Json.True, "errors" -> Json.False))),
        Get(s"/_alias/${setup.alias}--superseded", Right(Json.obj("not-a-generation" -> Json.obj("aliases" -> Json.obj("wrong" -> Json.obj()))))),
      ))
      setup.lifecycle(client).activate(generation) match {
        case Left(_: ElasticsearchGenerationLifecycleError.SupersededGenerationAliasStateInvalid) => succeed
        case other => fail(s"expected superseded-alias state rejection, got $other")
      }
      client.assertComplete()
    }

    "fail closed when the active target is also attached to the superseded alias" in {
      val generation = compiledGeneration
      val setup = creationSetup(generation, liveCount = 2L)
      val client = new ScriptedClient(Vector(
        Get(s"/${setup.name}/_mapping", Right(setup.mappingResponse)),
        PostJson(setup.countPath, setup.countBody, Right(countResponse(2L))),
        Get(s"/_alias/${setup.alias}", Right(Json.obj(setup.name -> aliasEntry(setup.alias)))),
        Get(s"/_alias/${setup.alias}--superseded", Right(Json.obj(setup.name -> aliasEntry(s"${setup.alias}--superseded")))),
        PostJson("/_aliases", Json.obj("actions" -> Json.arr(removeAlias(setup.name, s"${setup.alias}--superseded"))), Right(Json.obj("acknowledged" -> Json.True, "errors" -> Json.False))),
        Get(s"/_alias/${setup.alias}--superseded", Right(Json.obj(setup.name -> aliasEntry(s"${setup.alias}--superseded")))),
      ))
      setup.lifecycle(client).activate(generation) match {
        case Left(ElasticsearchGenerationLifecycleError.SupersededGenerationActiveConflict(active, superseded)) =>
          assert(active == setup.name)
          assert(superseded == Vector(setup.name))
        case other => fail(s"expected active/superseded conflict, got $other")
      }
      client.assertComplete()
    }

    "reject malformed metadata attached to a superseded target" in {
      val generation = compiledGeneration
      val setup = creationSetup(generation, liveCount = 2L)
      val old = "books_0000000000000000000000000000000000000000000000000000000000000001"
      val client = new ScriptedClient(Vector(
        Get(s"/${setup.name}/_mapping", Right(setup.mappingResponse)),
        PostJson(setup.countPath, setup.countBody, Right(countResponse(2L))),
        Get(s"/_alias/${setup.alias}", Right(Json.obj(setup.name -> aliasEntry(setup.alias)))),
        Get(s"/_alias/${setup.alias}--superseded", Right(Json.obj(old -> aliasEntry(s"${setup.alias}--superseded")))),
        Get(s"/_alias/${setup.alias}--superseded", Right(Json.obj(old -> aliasEntry(s"${setup.alias}--superseded")))),
        Get(
          s"/$old/_mapping",
          Right(Json.obj(old -> Json.obj("mappings" -> Json.obj("_meta" -> Json.fromString("malformed"))))),
        ),
      ))
      setup.lifecycle(client).activate(generation) match {
        case Left(ElasticsearchGenerationLifecycleError.SupersededGenerationAliasStateInvalid(target, message)) =>
          assert(target == old)
          assert(message.contains("invalid mappings._meta"))
        case other => fail(s"expected malformed metadata rejection, got $other")
      }
      client.assertComplete()
    }

    "preserve mapping lookup transport failures and accept a disappeared superseded mapping" in {
      val generation = compiledGeneration
      val setup = creationSetup(generation, liveCount = 2L)
      val oldGeneration = compiledGenerationWithPolicyVersion("book-elasticsearch-mapping-race")
      val oldEntry = ownedListing("books_", oldGeneration)
      val transportError = ElasticsearchGen2TransportError.ConnectionFailed("GET", s"/${oldEntry._1}/_mapping", "mapping unavailable")
      val transportClient = new ScriptedClient(Vector(
        Get(s"/${setup.name}/_mapping", Right(setup.mappingResponse)),
        PostJson(setup.countPath, setup.countBody, Right(countResponse(2L))),
        Get(s"/_alias/${setup.alias}", Right(Json.obj(setup.name -> aliasEntry(setup.alias)))),
        Get(s"/_alias/${setup.alias}--superseded", Right(Json.obj(oldEntry._1 -> aliasEntry(s"${setup.alias}--superseded")))),
        Get(s"/_alias/${setup.alias}--superseded", Right(Json.obj(oldEntry._1 -> aliasEntry(s"${setup.alias}--superseded")))),
        Get(s"/${oldEntry._1}/_mapping", Left(transportError)),
      ))
      setup.lifecycle(transportClient).activate(generation) match {
        case Left(ElasticsearchGenerationLifecycleError.SupersededGenerationMappingLookupFailed(target, error)) =>
          assert(target == oldEntry._1)
          assert(error == transportError)
        case other => fail(s"expected typed mapping transport failure, got $other")
      }
      transportClient.assertComplete()

      val disappearedClient = new ScriptedClient(Vector(
        Get(s"/${setup.name}/_mapping", Right(setup.mappingResponse)),
        PostJson(setup.countPath, setup.countBody, Right(countResponse(2L))),
        Get(s"/_alias/${setup.alias}", Right(Json.obj(setup.name -> aliasEntry(setup.alias)))),
        Get(s"/_alias/${setup.alias}--superseded", Right(Json.obj(oldEntry._1 -> aliasEntry(s"${setup.alias}--superseded")))),
        Get(s"/_alias/${setup.alias}--superseded", Right(Json.obj(oldEntry._1 -> aliasEntry(s"${setup.alias}--superseded")))),
        Get(s"/${oldEntry._1}/_mapping", Left(ElasticsearchGen2TransportError.HttpFailure("GET", s"/${oldEntry._1}/_mapping", 404, "already removed"))),
      ))
      assert(setup.lifecycle(disappearedClient).activate(generation).isRight)
      disappearedClient.assertComplete()
    }

    "decode only the real alias action-result shape" in {
      val generation = compiledGeneration
      val setup = creationSetup(generation, liveCount = 2L)
      val oldGeneration = compiledGenerationWithPolicyVersion("book-elasticsearch-real-action-shape")
      val old = ownedListing("books_", oldGeneration)
      val fakeResult = Json.obj(
        "action" -> Json.obj("remove_index" -> Json.obj("index" -> Json.fromString(old._1))),
        "status" -> Json.fromInt(200),
      )
      val client = new ScriptedClient(setup.provisionExchanges ++ Vector(
        Get(s"/_alias/${setup.alias}", Left(ElasticsearchGen2TransportError.HttpFailure("GET", s"/_alias/${setup.alias}", 404, "missing"))),
        Get(s"/_alias/${setup.alias}--superseded", Left(ElasticsearchGen2TransportError.HttpFailure("GET", s"/_alias/${setup.alias}--superseded", 404, "missing"))),
        PostJson("/_aliases", setup.aliasBody, Right(Json.obj("acknowledged" -> Json.True, "errors" -> Json.False))),
        Get(s"/_alias/${setup.alias}--superseded", Right(Json.obj(old._1 -> aliasEntry(s"${setup.alias}--superseded")))),
        Get(s"/${old._1}/_mapping", Right(Json.obj(old._1 -> old._2))),
        Get(s"/_alias/${setup.alias}", Right(Json.obj(setup.name -> aliasEntry(setup.alias)))),
        PostJson("/_aliases", guardedCleanupBody(setup.name, Vector(old._1)), Right(guardedCleanupResponse(setup.name, Vector(fakeResult), errors = false))),
      ))
      setup.lifecycle(client).activate(generation) match {
        case Left(ElasticsearchGenerationLifecycleError.InvalidSupersededGenerationCleanupResponse(_, message)) =>
          assert(message.contains("does not match requested remove_index"))
        case other => fail(s"expected fake action-result rejection, got $other")
      }
      client.assertComplete()
    }

    "reject flat action-result compatibility shapes" in {
      val generation = compiledGeneration
      val setup = creationSetup(generation, liveCount = 2L)
      val old = ownedListing("books_", compiledGenerationWithPolicyVersion("book-elasticsearch-flat-action-shape"))
      val fakeResult = Json.obj(
        "action_type" -> Json.fromString("remove_index"),
        "index" -> Json.fromString(old._1),
        "status" -> Json.fromInt(200),
      )
      val client = new ScriptedClient(setup.provisionExchanges ++ Vector(
        Get(s"/_alias/${setup.alias}", Left(ElasticsearchGen2TransportError.HttpFailure("GET", s"/_alias/${setup.alias}", 404, "missing"))),
        Get(s"/_alias/${setup.alias}--superseded", Left(ElasticsearchGen2TransportError.HttpFailure("GET", s"/_alias/${setup.alias}--superseded", 404, "missing"))),
        PostJson("/_aliases", setup.aliasBody, Right(Json.obj("acknowledged" -> Json.True, "errors" -> Json.False))),
        Get(s"/_alias/${setup.alias}--superseded", Right(Json.obj(old._1 -> aliasEntry(s"${setup.alias}--superseded")))),
        Get(s"/${old._1}/_mapping", Right(Json.obj(old._1 -> old._2))),
        Get(s"/_alias/${setup.alias}", Right(Json.obj(setup.name -> aliasEntry(setup.alias)))),
        PostJson("/_aliases", guardedCleanupBody(setup.name, Vector(old._1)), Right(guardedCleanupResponse(setup.name, Vector(fakeResult), errors = false))),
      ))
      setup.lifecycle(client).activate(generation) match {
        case Left(_: ElasticsearchGenerationLifecycleError.InvalidSupersededGenerationCleanupResponse) => succeed
        case other => fail(s"expected flat action-result rejection, got $other")
      }
      client.assertComplete()
    }

    "classify an exact transport alias guard failure separately" in {
      val generation = compiledGeneration
      val setup = creationSetup(generation, liveCount = 2L)
      val old = "books_0000000000000000000000000000000000000000000000000000000000000001"
      val body = Json.obj(
        "error" -> Json.obj(
          "type" -> Json.fromString("aliases_not_found_exception"),
          "reason" -> Json.fromString(s"aliases [${setup.alias}] missing"),
          "resource.type" -> Json.fromString("aliases"),
          "resource.id" -> Json.fromString(setup.alias),
        ),
        "status" -> Json.fromInt(404),
      ).noSpaces
      val client = new ScriptedClient(Vector(
        Get(s"/${setup.name}/_mapping", Right(setup.mappingResponse)),
        PostJson(setup.countPath, setup.countBody, Right(countResponse(2L))),
        Get(s"/_alias/${setup.alias}", Right(Json.obj(old -> aliasEntry(setup.alias)))),
        Get(s"/_alias/${setup.alias}--superseded", Left(ElasticsearchGen2TransportError.HttpFailure("GET", s"/_alias/${setup.alias}--superseded", 404, "missing"))),
        PostJson("/_aliases", Json.obj("actions" -> Json.arr(
          removeAlias(old, setup.alias),
          addAlias(old, s"${setup.alias}--superseded"),
          addAlias(setup.name, setup.alias),
        )), Left(ElasticsearchGen2TransportError.HttpFailure("POST", "/_aliases", 404, body))),
      ))
      setup.lifecycle(client).activate(generation) match {
        case Left(ElasticsearchGenerationLifecycleError.SupersededGenerationActivationGuardFailed(expected, Some(404), Some(error))) =>
          assert(expected == setup.name)
          assert(error.hcursor.get[String]("type").toOption.contains("aliases_not_found_exception"))
        case other => fail(s"expected typed activation guard failure, got $other")
      }
      client.assertComplete()
    }

    "keep non-structural alias failures as ordinary transport errors" in {
      val generation = compiledGeneration
      val setup = creationSetup(generation, liveCount = 2L)
      val old = "books_0000000000000000000000000000000000000000000000000000000000000001"
      val actionBody = Json.obj("actions" -> Json.arr(
        removeAlias(old, setup.alias),
        addAlias(old, s"${setup.alias}--superseded"),
        addAlias(setup.name, setup.alias),
      ))
      val cases = Vector(
        Json.obj(
          "error" -> Json.obj(
            "type" -> Json.fromString("aliases_not_found_exception"),
            "reason" -> Json.fromString("aliases [other] missing"),
            "resource.type" -> Json.fromString("aliases"),
            "resource.id" -> Json.fromString("other"),
          ),
          "status" -> Json.fromInt(404),
        ).noSpaces,
        Json.obj(
          "error" -> Json.obj(
            "type" -> Json.fromString("illegal_argument_exception"),
            "reason" -> Json.fromString(s"aliases [${setup.alias}] missing"),
          ),
          "status" -> Json.fromInt(400),
        ).noSpaces,
        "not-json",
      )
      cases.foreach { body =>
        val client = new ScriptedClient(Vector(
          Get(s"/${setup.name}/_mapping", Right(setup.mappingResponse)),
          PostJson(setup.countPath, setup.countBody, Right(countResponse(2L))),
          Get(s"/_alias/${setup.alias}", Right(Json.obj(old -> aliasEntry(setup.alias)))),
          Get(s"/_alias/${setup.alias}--superseded", Left(ElasticsearchGen2TransportError.HttpFailure("GET", s"/_alias/${setup.alias}--superseded", 404, "missing"))),
          PostJson("/_aliases", actionBody, Left(ElasticsearchGen2TransportError.HttpFailure("POST", "/_aliases", 404, body))),
        ))
        setup.lifecycle(client).activate(generation) match {
          case Left(ElasticsearchGenerationLifecycleError.Transport("update-alias", ElasticsearchGen2TransportError.HttpFailure("POST", "/_aliases", 404, actual))) =>
            assert(actual == body)
          case other => fail(s"expected ordinary transport failure for '$body', got $other")
        }
        client.assertComplete()
      }
    }

    "refuse destructive cleanup when a concurrent activation now owns the alias" in {
      val generationA = compiledGeneration
      val generationB = compiledGenerationWithPolicyVersion("book-elasticsearch-concurrent-b")
      val setupA = creationSetup(generationA, liveCount = 2L)
      val setupB = creationSetup(generationB, liveCount = 2L)
      val aliasOnB = Json.obj(setupB.name -> aliasEntry(setupB.alias))
      val activationBody = Json.obj("actions" -> Json.arr(
        removeAlias(setupB.name, setupA.alias),
        addAlias(setupB.name, s"${setupA.alias}--superseded"),
        addAlias(setupA.name, setupA.alias),
      ))
      val client = new ScriptedClient(Vector(
        Get(s"/${setupA.name}/_mapping", Right(setupA.mappingResponse)),
        PostJson(setupA.countPath, setupA.countBody, Right(countResponse(2L))),
        Get(s"/_alias/${setupA.alias}", Right(aliasOnB)),
        Get(s"/_alias/${setupA.alias}--superseded", Left(ElasticsearchGen2TransportError.HttpFailure("GET", s"/_alias/${setupA.alias}--superseded", 404, "missing"))),
        PostJson("/_aliases", activationBody, Right(Json.obj("acknowledged" -> Json.True, "errors" -> Json.False))),
        Get(s"/_alias/${setupA.alias}--superseded", Right(Json.obj(setupB.name -> aliasEntry(s"${setupA.alias}--superseded")))),
        Get(s"/${setupB.name}/_mapping", Right(setupB.mappingResponse)),
        Get(s"/_alias/${setupA.alias}", Right(aliasOnB)),
      ))
      setupA.lifecycle(client).activate(generationA) match {
        case Left(ElasticsearchGenerationLifecycleError.SupersededGenerationAliasGuardFailed(expected, actual)) =>
          assert(expected == setupA.name)
          assert(actual == Vector(setupB.name))
        case other => fail(s"expected concurrent-alias guard failure, got $other")
      }
      client.assertComplete()
    }

    "fail closed when reactivation races after the cleanup read-side guard" in {
      val generation = compiledGeneration
      val setup = creationSetup(generation, liveCount = 2L)
      val old = ownedListing("books_", compiledGenerationWithPolicyVersion("book-elasticsearch-delayed-cleanup-old"))
      val guardBody = Json.obj(
        "error" -> Json.obj(
          "type" -> Json.fromString("aliases_not_found_exception"),
          "reason" -> Json.fromString(s"aliases [${setup.alias}] missing"),
          "resource.type" -> Json.fromString("aliases"),
          "resource.id" -> Json.fromString(setup.alias),
        ),
        "status" -> Json.fromInt(404),
      ).noSpaces
      val client = new ScriptedClient(Vector(
        Get(s"/${setup.name}/_mapping", Right(setup.mappingResponse)),
        PostJson(setup.countPath, setup.countBody, Right(countResponse(2L))),
        Get(s"/_alias/${setup.alias}", Right(Json.obj(setup.name -> aliasEntry(setup.alias)))),
        Get(s"/_alias/${setup.alias}--superseded", Right(Json.obj(old._1 -> aliasEntry(s"${setup.alias}--superseded")))),
        // The reactivation happens after this read-side observation; the atomic guard below is the safety boundary.
        Get(s"/_alias/${setup.alias}--superseded", Right(Json.obj(old._1 -> aliasEntry(s"${setup.alias}--superseded")))),
        Get(s"/${old._1}/_mapping", Right(Json.obj(old._1 -> old._2))),
        Get(s"/_alias/${setup.alias}", Right(Json.obj(setup.name -> aliasEntry(setup.alias)))),
        PostJson("/_aliases", guardedCleanupBody(setup.name, Vector(old._1)), Left(ElasticsearchGen2TransportError.HttpFailure("POST", "/_aliases", 404, guardBody))),
      ))
      setup.lifecycle(client).activate(generation) match {
        case Left(ElasticsearchGenerationLifecycleError.SupersededGenerationCleanupGuardFailed(expected, status, error)) =>
          assert(expected == setup.name)
          assert(status == 404)
          assert(error.hcursor.get[String]("type").toOption.contains("aliases_not_found_exception"))
        case other => fail(s"expected cleanup guard failure, got $other")
      }
      client.assertComplete()
    }

    "classify a present cleanup guard action failure separately from old-index failure" in {
      val generation = compiledGeneration
      val setup = creationSetup(generation, liveCount = 2L)
      val old = ownedListing("books_", compiledGenerationWithPolicyVersion("book-elasticsearch-guard-action"))
      val guardError = Json.obj("type" -> Json.fromString("alias_guard_failed"), "reason" -> Json.fromString("active relation changed"))
      val client = new ScriptedClient(Vector(
        Get(s"/${setup.name}/_mapping", Right(setup.mappingResponse)),
        PostJson(setup.countPath, setup.countBody, Right(countResponse(2L))),
        Get(s"/_alias/${setup.alias}", Right(Json.obj(setup.name -> aliasEntry(setup.alias)))),
        Get(s"/_alias/${setup.alias}--superseded", Right(Json.obj(old._1 -> aliasEntry(s"${setup.alias}--superseded")))),
        Get(s"/_alias/${setup.alias}--superseded", Right(Json.obj(old._1 -> aliasEntry(s"${setup.alias}--superseded")))),
        Get(s"/${old._1}/_mapping", Right(Json.obj(old._1 -> old._2))),
        Get(s"/_alias/${setup.alias}", Right(Json.obj(setup.name -> aliasEntry(setup.alias)))),
        PostJson("/_aliases", guardedCleanupBody(setup.name, Vector(old._1)), Right(cleanupResponse(
          Vector(
            cleanupActionResult("remove", setup.name, 409, Some(guardError)),
            cleanupActionResult("add", setup.name, 200, None),
            cleanupActionResult("remove_index", old._1, 200, None),
          ),
          errors = true,
        ))),
      ))
      setup.lifecycle(client).activate(generation) match {
        case Left(ElasticsearchGenerationLifecycleError.SupersededGenerationCleanupGuardActionFailed(expected, Vector(failure))) =>
          assert(expected == setup.name)
          assert(failure.action == "remove")
          assert(failure.target == setup.name)
          assert(failure.alias.contains(setup.alias))
          assert(failure.status.contains(409))
          assert(failure.error.contains(guardError))
        case other => fail(s"expected typed cleanup guard action failure, got $other")
      }
      client.assertComplete()
    }

    "report cleanup failure and retry it on a subsequent activation" in {
      val generation = compiledGeneration
      val setup = creationSetup(generation, liveCount = 2L)
      val oldGeneration = compiledGenerationWithPolicyVersion("book-elasticsearch-old")
      val oldEntry = ownedListing("books_", oldGeneration)
      val old = oldEntry._1
      val cleanupError = Json.obj("type" -> Json.fromString("cleanup_unavailable"), "reason" -> Json.fromString("cleanup unavailable"))
      val firstClient = new ScriptedClient(setup.provisionExchanges ++ Vector(
        Get(s"/_alias/${setup.alias}", Left(ElasticsearchGen2TransportError.HttpFailure("GET", s"/_alias/${setup.alias}", 404, "missing"))),
        Get(s"/_alias/${setup.alias}--superseded", Left(ElasticsearchGen2TransportError.HttpFailure("GET", s"/_alias/${setup.alias}--superseded", 404, "missing"))),
        PostJson("/_aliases", setup.aliasBody, Right(Json.obj("acknowledged" -> Json.True, "errors" -> Json.False))),
        Get(s"/_alias/${setup.alias}--superseded", Right(Json.obj(old -> aliasEntry(s"${setup.alias}--superseded")))),
        Get(s"/$old/_mapping", Right(Json.obj(old -> oldEntry._2))),
        Get(s"/_alias/${setup.alias}", Right(Json.obj(setup.name -> aliasEntry(setup.alias)))),
        PostJson("/_aliases", guardedCleanupBody(setup.name, Vector(old)), Right(guardedCleanupResponse(setup.name,
          Vector(cleanupActionResult("remove_index", old, 500, Some(cleanupError))),
          errors = true,
        ))),
      ))
      setup.lifecycle(firstClient).activate(generation) match {
        case Left(ElasticsearchGenerationLifecycleError.SupersededGenerationCleanupFailed(active, failures)) =>
          assert(active == setup.name)
          assert(failures == Vector(ElasticsearchGenerationLifecycleError.SupersededGenerationCleanupFailure("remove_index", old, Some(500), Some(cleanupError))))
        case other => fail(s"expected typed cleanup failure, got $other")
      }
      firstClient.assertComplete()

      val retryClient = new ScriptedClient(Vector(
        Get(s"/${setup.name}/_mapping", Right(setup.mappingResponse)),
        PostJson(setup.countPath, setup.countBody, Right(countResponse(2L))),
        Get(s"/_alias/${setup.alias}", Right(Json.obj(setup.name -> aliasEntry(setup.alias)))),
        Get(s"/_alias/${setup.alias}--superseded", Right(Json.obj(old -> aliasEntry(s"${setup.alias}--superseded")))),
        Get(s"/_alias/${setup.alias}--superseded", Right(Json.obj(old -> aliasEntry(s"${setup.alias}--superseded")))),
        Get(s"/$old/_mapping", Right(Json.obj(old -> oldEntry._2))),
        Get(s"/_alias/${setup.alias}", Right(Json.obj(setup.name -> aliasEntry(setup.alias)))),
        PostJson("/_aliases", guardedCleanupBody(setup.name, Vector(old)), Right(guardedCleanupResponse(setup.name, Vector(cleanupActionResult("remove_index", old, 200, None)), errors = false))),
      ))
      assert(setup.lifecycle(retryClient).activate(generation).isRight)
      retryClient.assertComplete()
    }

    "treat an already removed old generation as idempotent cleanup success" in {
      val generation = compiledGeneration
      val setup = creationSetup(generation, liveCount = 2L)
      val oldGeneration = compiledGenerationWithPolicyVersion("book-elasticsearch-idempotent-old")
      val oldEntry = ownedListing("books_", oldGeneration)
      val alreadyAbsent = Json.obj("type" -> Json.fromString("index_not_found_exception"))
      val client = new ScriptedClient(Vector(
        Get(s"/${setup.name}/_mapping", Right(setup.mappingResponse)),
        PostJson(setup.countPath, setup.countBody, Right(countResponse(2L))),
        Get(s"/_alias/${setup.alias}", Right(Json.obj(setup.name -> aliasEntry(setup.alias)))),
        Get(s"/_alias/${setup.alias}--superseded", Right(Json.obj(oldEntry._1 -> aliasEntry(s"${setup.alias}--superseded")))),
        Get(s"/_alias/${setup.alias}--superseded", Right(Json.obj(oldEntry._1 -> aliasEntry(s"${setup.alias}--superseded")))),
        Get(s"/${oldEntry._1}/_mapping", Right(Json.obj(oldEntry._1 -> oldEntry._2))),
        Get(s"/_alias/${setup.alias}", Right(Json.obj(setup.name -> aliasEntry(setup.alias)))),
        PostJson("/_aliases", guardedCleanupBody(setup.name, Vector(oldEntry._1)), Right(guardedCleanupResponse(setup.name,
          Vector(cleanupActionResult("remove_index", oldEntry._1, 404, Some(alreadyAbsent))),
          errors = true,
        ))),
      ))
      assert(setup.lifecycle(client).activate(generation).isRight)
      client.assertComplete()
    }

    "preserve a superseded index removal failure with its raw error" in {
      val generation = compiledGeneration
      val setup = creationSetup(generation, liveCount = 2L)
      val oldGeneration = compiledGenerationWithPolicyVersion("book-elasticsearch-guard-failure")
      val oldEntry = ownedListing("books_", oldGeneration)
      val cleanupError = Json.obj("type" -> Json.fromString("cleanup_unavailable"), "reason" -> Json.fromString("cleanup unavailable"))
      val client = new ScriptedClient(Vector(
        Get(s"/${setup.name}/_mapping", Right(setup.mappingResponse)),
        PostJson(setup.countPath, setup.countBody, Right(countResponse(2L))),
        Get(s"/_alias/${setup.alias}", Right(Json.obj(setup.name -> aliasEntry(setup.alias)))),
        Get(s"/_alias/${setup.alias}--superseded", Right(Json.obj(oldEntry._1 -> aliasEntry(s"${setup.alias}--superseded")))),
        Get(s"/_alias/${setup.alias}--superseded", Right(Json.obj(oldEntry._1 -> aliasEntry(s"${setup.alias}--superseded")))),
        Get(s"/${oldEntry._1}/_mapping", Right(Json.obj(oldEntry._1 -> oldEntry._2))),
        Get(s"/_alias/${setup.alias}", Right(Json.obj(setup.name -> aliasEntry(setup.alias)))),
        PostJson("/_aliases", guardedCleanupBody(setup.name, Vector(oldEntry._1)), Right(guardedCleanupResponse(setup.name,
          Vector(cleanupActionResult("remove_index", oldEntry._1, 409, Some(cleanupError))),
          errors = true,
        ))),
      ))
      setup.lifecycle(client).activate(generation) match {
        case Left(ElasticsearchGenerationLifecycleError.SupersededGenerationCleanupFailed(active, Vector(ElasticsearchGenerationLifecycleError.SupersededGenerationCleanupFailure("remove_index", target, Some(409), Some(error))))) =>
          assert(active == setup.name)
          assert(target == oldEntry._1)
          assert(error == cleanupError)
        case other => fail(s"expected typed alias-guard action failure, got $other")
      }
      client.assertComplete()
    }

    "reject a cleanup response whose action results do not follow the requested order" in {
      val generation = compiledGeneration
      val setup = creationSetup(generation, liveCount = 2L)
      val oldGeneration = compiledGenerationWithPolicyVersion("book-elasticsearch-result-order")
      val oldEntry = ownedListing("books_", oldGeneration)
      val client = new ScriptedClient(Vector(
        Get(s"/${setup.name}/_mapping", Right(setup.mappingResponse)),
        PostJson(setup.countPath, setup.countBody, Right(countResponse(2L))),
        Get(s"/_alias/${setup.alias}", Right(Json.obj(setup.name -> aliasEntry(setup.alias)))),
        Get(s"/_alias/${setup.alias}--superseded", Right(Json.obj(oldEntry._1 -> aliasEntry(s"${setup.alias}--superseded")))),
        Get(s"/_alias/${setup.alias}--superseded", Right(Json.obj(oldEntry._1 -> aliasEntry(s"${setup.alias}--superseded")))),
        Get(s"/${oldEntry._1}/_mapping", Right(Json.obj(oldEntry._1 -> oldEntry._2))),
        Get(s"/_alias/${setup.alias}", Right(Json.obj(setup.name -> aliasEntry(setup.alias)))),
        PostJson("/_aliases", guardedCleanupBody(setup.name, Vector(oldEntry._1)), Right(guardedCleanupResponse(setup.name,
          Vector(cleanupActionResult("remove_index", oldEntry._1 + "-wrong", 200, None)),
          errors = false,
        ))),
      ))
      setup.lifecycle(client).activate(generation) match {
        case Left(ElasticsearchGenerationLifecycleError.InvalidSupersededGenerationCleanupResponse(_, message)) =>
          assert(message.contains("does not match requested remove"))
        case other => fail(s"expected action-order rejection, got $other")
      }
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
      assert(setup.lifecycle(client).activate(generation) ==
        Left(ElasticsearchGenerationLifecycleError.LiveDocumentCountMismatch(expected = 2L, metadata = 2L, live = 3L)))
      client.assertComplete()
    }

    "report a persisted metadata count mismatch without inventing a live count" in {
      val generation = compiledGeneration
      val persisted = ElasticsearchPersistedGenerationIdentity.fromTrusted(generation.identity)
      val id = ElasticsearchGenerationNaming.generationId(persisted)
      val name = ElasticsearchGenerationNaming.physicalIndexName("books_", persisted).map(_.value).getOrElse(fail("expected name"))
      val metadata = ElasticsearchGenerationMetadata(
        ElasticsearchGenerationMetadataSchemaVersion.Current,
        id,
        Instant.parse("2026-01-01T00:00:00Z"),
        documentCount = 3L,
        persisted,
      )
      val mapping = generation.mapping.json.asObject.getOrElse(fail("expected mapping"))
      val mappingResponse = Json.obj(name -> Json.obj("mappings" -> Json.fromJsonObject(mapping.add("_meta", ElasticsearchGenerationMetadataCodec.encode(metadata)))))
      val client = new ScriptedClient(Vector(Get(s"/$name/_mapping", Right(mappingResponse))))
      val batching = ElasticsearchBulkBatchingPolicy.create(10, 10000L).getOrElse(fail("expected batching"))
      val config = ElasticsearchGenerationLifecycleConfig.create("books", "books_", batching).getOrElse(fail("expected config"))
      val lifecycle = new ElasticsearchGenerationLifecycle(client, config, Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC))

      assert(lifecycle.activate(generation) ==
        Left(ElasticsearchGenerationLifecycleError.MetadataDocumentCountMismatch(expected = 2L, metadata = 3L)))
      client.assertComplete()
    }

    "retain a validated generation when alias activation fails" in {
      val generation = compiledGeneration
      val setup = creationSetup(generation, liveCount = 2L)
      val aliasFailure = Vector(
        Get(s"/_alias/${setup.alias}", Left(ElasticsearchGen2TransportError.HttpFailure("GET", s"/_alias/${setup.alias}", 404, "missing"))),
        Get(s"/_alias/${setup.alias}--superseded", Left(ElasticsearchGen2TransportError.HttpFailure("GET", s"/_alias/${setup.alias}--superseded", 404, "missing"))),
        PostJson("/_aliases", setup.aliasBody, Left(ElasticsearchGen2TransportError.ConnectionFailed("POST", "/_aliases", "alias unavailable"))),
      )
      val client = new ScriptedClient(setup.provisionExchanges ++ aliasFailure)
      assert(setup.lifecycle(client).activate(generation).isLeft)
      client.assertComplete()
    }

    "remove only sorted old alias targets when the new target is already active" in {
      val generation = compiledGeneration
      val setup = creationSetup(generation, liveCount = 2L)
      val oldGenerationA = compiledGenerationWithPolicyVersion("book-elasticsearch-old-a")
      val oldGenerationB = compiledGenerationWithPolicyVersion("book-elasticsearch-old-b")
      val oldA = ownedListing("books_", oldGenerationA)._1
      val oldB = ownedListing("books_", oldGenerationB)._1
      val aliasResponse = Json.obj(
        oldB -> aliasEntry(setup.alias),
        setup.name -> aliasEntry(setup.alias),
        oldA -> aliasEntry(setup.alias),
      )
      val sortedOldAliasTargets = Vector(oldA, oldB).sorted
      val expectedBody = Json.obj("actions" -> Json.arr(
        sortedOldAliasTargets.flatMap(target => Vector(removeAlias(target, setup.alias), addAlias(target, s"${setup.alias}--superseded")))*
      ))
      val mappingExchanges = sortedOldAliasTargets.foldLeft(Vector.empty[Exchange]) { (exchanges, target) =>
        val mapping = if (target == oldA) ownedListing("books_", oldGenerationA)._2 else ownedListing("books_", oldGenerationB)._2
        exchanges :+ Get(s"/$target/_mapping", Right(Json.obj(target -> mapping)))
      }
      val client = new ScriptedClient(setup.provisionExchanges ++ Vector(
        Get(s"/_alias/${setup.alias}", Right(aliasResponse)),
        Get(s"/_alias/${setup.alias}--superseded", Left(ElasticsearchGen2TransportError.HttpFailure("GET", s"/_alias/${setup.alias}--superseded", 404, "missing"))),
        PostJson("/_aliases", expectedBody, Right(Json.obj("acknowledged" -> Json.True, "errors" -> Json.False))),
        Get(s"/_alias/${setup.alias}--superseded", Right(Json.obj(
          oldA -> aliasEntry(s"${setup.alias}--superseded"),
          oldB -> aliasEntry(s"${setup.alias}--superseded"),
        ))),
      ) ++ mappingExchanges ++ Vector(
        Get(s"/_alias/${setup.alias}", Right(Json.obj(setup.name -> aliasEntry(setup.alias)))),
        PostJson("/_aliases", guardedCleanupBody(setup.name, sortedOldAliasTargets), Right(guardedCleanupResponse(setup.name, sortedOldAliasTargets.map(target => cleanupActionResult("remove_index", target, 200, None)), errors = false))),
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
        Vector(oldA) -> Vector(removeAlias(oldA, setup.alias), addAlias(oldA, s"${setup.alias}--superseded"), addAlias(setup.name, setup.alias)),
        Vector(oldB, oldA) -> Vector(removeAlias(oldA, setup.alias), addAlias(oldA, s"${setup.alias}--superseded"), removeAlias(oldB, setup.alias), addAlias(oldB, s"${setup.alias}--superseded"), addAlias(setup.name, setup.alias)),
        Vector(oldA, setup.name) -> Vector(removeAlias(oldA, setup.alias), addAlias(oldA, s"${setup.alias}--superseded")),
        Vector(setup.name) -> Vector.empty,
      ).foreach { case (targets, expectedActions) =>
        val aliasResponse = Json.obj(targets.map(target => target -> aliasEntry(setup.alias))*)
        val update = if (expectedActions.isEmpty) Vector.empty else Vector(
          PostJson("/_aliases", Json.obj("actions" -> Json.fromValues(expectedActions)), Right(Json.obj("acknowledged" -> Json.True, "errors" -> Json.False)))
        )
        val client = new ScriptedClient(setup.provisionExchanges ++ Vector(
          Get(s"/_alias/${setup.alias}", Right(aliasResponse)),
          Get(s"/_alias/${setup.alias}--superseded", Left(ElasticsearchGen2TransportError.HttpFailure("GET", s"/_alias/${setup.alias}--superseded", 404, "missing"))),
        ) ++ update ++ setup.cleanupExchanges)
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
        Get(s"/_alias/${setup.alias}--superseded", Left(ElasticsearchGen2TransportError.HttpFailure("GET", s"/_alias/${setup.alias}--superseded", 404, "missing"))),
        PostJson("/_aliases", setup.aliasBody, Right(Json.obj("acknowledged" -> Json.True, "errors" -> Json.False))),
      ) ++ setup.cleanupExchanges)
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
      assert(ElasticsearchGenerationLifecycleConfig.create("books", "", batching) ==
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

    "report a pinned generation whose physical index was deleted as stale" in {
      val setup = resolutionSetup()
      val failure = ElasticsearchGen2TransportError.HttpFailure("GET", s"/${setup.name}/_mapping", 404, "missing")
      val client = new ScriptedClient(Vector(Get(s"/${setup.name}/_mapping", Left(failure))))
      assert(setup.lifecycle(client).authorize(preparedPinned(setup.name)) ==
        Left(ElasticsearchGenerationLifecycleError.StaleGeneration(ElasticsearchGenerationReference(setup.name))))
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
      val config = ElasticsearchGenerationLifecycleConfig.create(alias, "books_", batching).getOrElse(fail("expected config"))
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
    cleanupExchanges: Vector[Exchange],
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
      Get(s"/_alias/$alias--superseded", Left(ElasticsearchGen2TransportError.HttpFailure("GET", s"/_alias/$alias--superseded", 404, "missing"))),
      PostJson("/_aliases", aliasBody, Right(Json.obj("acknowledged" -> Json.True, "errors" -> Json.False))),
    ) ++ Vector(
      Get(s"/_alias/$alias--superseded", Left(ElasticsearchGen2TransportError.HttpFailure("GET", s"/_alias/$alias--superseded", 404, "missing")))
    )
    val cleanup = Vector[Exchange](Get(s"/_alias/$alias--superseded", Left(ElasticsearchGen2TransportError.HttpFailure("GET", s"/_alias/$alias--superseded", 404, "missing"))))
    val lifecycleFactory = (client: ElasticsearchGen2JsonClient) => {
      val config = ElasticsearchGenerationLifecycleConfig.create(alias, "books_", batching).getOrElse(fail("expected config"))
      new ElasticsearchGenerationLifecycle(client, config, Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC))
    }
    CreationSetup(alias, name, createBody, aliasBody, mappingResponse, s"/$name/_count", Json.obj("query" -> Json.obj("match_all" -> Json.obj())), provision, cleanup, success, lifecycleFactory)
  }

  private def aliasEntry(alias: String): Json = Json.obj("aliases" -> Json.obj(alias -> Json.obj()))
  private def removeAlias(index: String, alias: String): Json = Json.obj("remove" -> Json.obj("index" -> Json.fromString(index), "alias" -> Json.fromString(alias), "must_exist" -> Json.True))
  private def addAlias(index: String, alias: String): Json = Json.obj("add" -> Json.obj("index" -> Json.fromString(index), "alias" -> Json.fromString(alias)))
  private def guardedCleanupBody(active: String, old: Vector[String]): Json =
    Json.obj("actions" -> Json.fromValues(
      Vector(removeAlias(active, "books"), addAlias(active, "books")) ++
        old.sorted.map(index => Json.obj("remove_index" -> Json.obj("index" -> Json.fromString(index))))
    ))

  private def cleanupActionResult(action: String, target: String, status: Int, error: Option[Json]): Json = {
    val aliases = if (action == "remove_index") Json.arr() else Json.arr(Json.fromString("books"))
    val base = Json.obj(
      "action" -> Json.obj(
        "type" -> Json.fromString(action),
        "indices" -> Json.arr(Json.fromString(target)),
        "aliases" -> aliases,
      ),
      "status" -> Json.fromInt(status),
    )
    error.fold(base)(value => base.deepMerge(Json.obj("error" -> value)))
  }

  private def cleanupResponse(results: Vector[Json], errors: Boolean): Json =
    Json.obj(
      "acknowledged" -> Json.True,
      "errors" -> Json.fromBoolean(errors),
      "action_results" -> Json.fromValues(results),
    )

  private def guardedCleanupResponse(active: String, results: Vector[Json], errors: Boolean): Json =
    cleanupResponse(
      Vector(
        cleanupActionResult("remove", active, 200, None),
        cleanupActionResult("add", active, 200, None),
      ) ++ results,
      errors,
    )

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
      case value if value == s"/_alias/$alias--superseded" => Left(ElasticsearchGen2TransportError.HttpFailure("GET", value, 404, "missing"))
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
