package leaderboard.search

import leaderboard.model.{MasterServiceOfferVariantId, QueryFailure}
import leaderboard.search.document.VariantSearchDocument
import leaderboard.search.hybrid.{
  BeautyQNonProductionHybridExperimentActivation,
  BeautyQNonProductionHybridExperimentConfig,
  BeautyQNonProductionHybridExperimentInvocation,
  BeautyQNonProductionHybridExperimentRouting,
  BeautyQNonProductionHybridResponseExperiment,
}
import leaderboard.search.lexical.{LexicalDocumentBackend, LexicalDocumentHit}
import leaderboard.search.semantic.{SemanticDocumentBackend, SemanticDocumentHit, SemanticDocumentLookup}
import org.scalatest.wordspec.AnyWordSpec
import zio.{IO, ZIO}

final class BeautyQNonProductionHybridExperimentActivationSpec extends AnyWordSpec {
  "BeautyQNonProductionHybridExperimentActivation" should {
    "default to disabled" in {
      assert(BeautyQNonProductionHybridExperimentActivation.default == BeautyQNonProductionHybridExperimentActivation.Disabled)
    }

    "build no runner when disabled" in {
      val built = BeautyQNonProductionHybridExperimentActivation.buildIfEnabled[IO](
        activation = BeautyQNonProductionHybridExperimentActivation.Disabled,
        lexicalBackend = fakeLexicalBackend,
        semanticBackend = fakeSemanticBackend,
        documentLookup = fakeDocumentLookup,
      )

      assert(built.isEmpty)
    }

    "build the response experiment runner when enabled" in {
      val built = BeautyQNonProductionHybridExperimentActivation.buildIfEnabled[IO](
        activation = BeautyQNonProductionHybridExperimentActivation.Enabled(createConfig("enabled-hybrid-experiment")),
        lexicalBackend = fakeLexicalBackend,
        semanticBackend = fakeSemanticBackend,
        documentLookup = fakeDocumentLookup,
      )

      assert(built.exists(_.isInstanceOf[BeautyQNonProductionHybridResponseExperiment[IO]]))
    }

    "fail clearly for an empty experiment id" in {
      val failure = BeautyQNonProductionHybridExperimentConfig.create(
        experimentId = "",
        invocation = BeautyQNonProductionHybridExperimentInvocation.ManualTask,
        routing = BeautyQNonProductionHybridExperimentRouting.ExplicitInvocationOnly,
      )

      failure match {
        case Left(QueryFailure.DomainFailure(message)) =>
          assert(message.contains("experimentId"))
          assert(message.contains("non-empty"))
        case other =>
          fail(s"Expected empty experimentId domain failure, got $other")
      }
    }

    "fail clearly for a whitespace experiment id" in {
      val failure = BeautyQNonProductionHybridExperimentConfig.create(
        experimentId = "   ",
        invocation = BeautyQNonProductionHybridExperimentInvocation.ManualTask,
        routing = BeautyQNonProductionHybridExperimentRouting.ExplicitInvocationOnly,
      )

      failure match {
        case Left(QueryFailure.DomainFailure(message)) =>
          assert(message.contains("experimentId"))
          assert(message.contains("non-empty"))
        case other =>
          fail(s"Expected whitespace experimentId domain failure, got $other")
      }
    }

    "preserve a valid experiment id exactly" in {
      val config = createConfig(" local-hybrid-experiment ")

      assert(config.experimentId == " local-hybrid-experiment ")
    }

    "allow only manual task test setup and local experiment invocation modes" in {
      assert(BeautyQNonProductionHybridExperimentInvocation.all == List(
        BeautyQNonProductionHybridExperimentInvocation.ManualTask,
        BeautyQNonProductionHybridExperimentInvocation.TestSetup,
        BeautyQNonProductionHybridExperimentInvocation.LocalExperiment,
      ))
    }

    "allow only explicit invocation routing" in {
      assert(BeautyQNonProductionHybridExperimentRouting.all == List(
        BeautyQNonProductionHybridExperimentRouting.ExplicitInvocationOnly,
      ))
    }

    "not model startup invocation" in {
      assert(!BeautyQNonProductionHybridExperimentInvocation.all.exists(_.productPrefix == "Startup"))
    }

    "not provide production or default enabled activation" in {
      assert(BeautyQNonProductionHybridExperimentActivation.default == BeautyQNonProductionHybridExperimentActivation.Disabled)
      assert(BeautyQNonProductionHybridExperimentActivation.default != BeautyQNonProductionHybridExperimentActivation.Enabled(createConfig("not-default")))
      assert(BeautyQNonProductionHybridExperimentActivation.default.productPrefix != "Production")
    }

    "not involve Mode.Test or Mode.Prod activation values" in {
      val modeledValues =
        BeautyQNonProductionHybridExperimentInvocation.all.map(_.productPrefix) ++
          BeautyQNonProductionHybridExperimentRouting.all.map(_.productPrefix) ++
          List(BeautyQNonProductionHybridExperimentActivation.default.productPrefix)

      assert(!modeledValues.exists(value => value == "Mode.Test" || value == "Test" || value == "Mode.Prod" || value == "Prod"))
    }

    "construct from injected seams without calling lexical semantic or lookup during build" in {
      val built = BeautyQNonProductionHybridExperimentActivation.buildIfEnabled[IO](
        activation = BeautyQNonProductionHybridExperimentActivation.Enabled(createConfig("pure-build")),
        lexicalBackend = fakeLexicalBackend,
        semanticBackend = fakeSemanticBackend,
        documentLookup = fakeDocumentLookup,
      )

      assert(built.nonEmpty)
    }
  }

  private def createConfig(
    experimentId: String,
    invocation: BeautyQNonProductionHybridExperimentInvocation = BeautyQNonProductionHybridExperimentInvocation.ManualTask,
    routing: BeautyQNonProductionHybridExperimentRouting = BeautyQNonProductionHybridExperimentRouting.ExplicitInvocationOnly,
  ): BeautyQNonProductionHybridExperimentConfig =
    BeautyQNonProductionHybridExperimentConfig.create(
      experimentId = experimentId,
      invocation = invocation,
      routing = routing,
    ).fold(failure => fail(s"Expected valid config, got $failure"), identity)

  private val fakeLexicalBackend: LexicalDocumentBackend[IO, MasterServiceOfferVariantId] =
    new LexicalDocumentBackend[IO, MasterServiceOfferVariantId] {
      override def documentHits(
        input: UserSearchInput,
        intent: ParsedSearchIntent,
      ): IO[QueryFailure, List[LexicalDocumentHit[MasterServiceOfferVariantId]]] =
        ZIO.dieMessage(s"lexical backend must not be called during activation build: $input $intent")
    }

  private val fakeSemanticBackend: SemanticDocumentBackend[IO, MasterServiceOfferVariantId] =
    new SemanticDocumentBackend[IO, MasterServiceOfferVariantId] {
      override def documentHits(
        input: UserSearchInput,
        intent: ParsedSearchIntent,
      ): IO[QueryFailure, List[SemanticDocumentHit[MasterServiceOfferVariantId]]] =
        ZIO.dieMessage(s"semantic backend must not be called during activation build: $input $intent")
    }

  private val fakeDocumentLookup: SemanticDocumentLookup[IO, MasterServiceOfferVariantId, VariantSearchDocument] =
    new SemanticDocumentLookup[IO, MasterServiceOfferVariantId, VariantSearchDocument] {
      override def lookup(
        ids: List[MasterServiceOfferVariantId]
      ): IO[QueryFailure, Map[MasterServiceOfferVariantId, VariantSearchDocument]] =
        ZIO.dieMessage(s"document lookup must not be called during activation build: $ids")
    }
}
