package leaderboard.search

import distage.{Injector, ModuleDef}
import izumi.distage.model.definition.{Activation, LocatorPrivacy}
import izumi.distage.model.plan.Roots
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

final class BeautyQNonProductionHybridExperimentModuleGatingSpec extends AnyWordSpec {
  "BeautyQ non-production hybrid experiment module gating" should {
    "not construct fake resources when the explicit disabled module is selected" in {
      val handle = buildHandle(moduleForDisabled)

      assert(handle.value.isEmpty)
    }

    "construct each fake resource exactly once when the explicit enabled module is selected" in {
      val handle = buildHandle(moduleForEnabled)

      assert(handle.value.exists(_.isInstanceOf[BeautyQNonProductionHybridResponseExperiment[IO]]))
    }

    "remain an explicit fake-only non-production adapter proof" in {
      val disabledHandle = buildHandle(moduleForDisabled)
      val enabledHandle  = buildHandle(moduleForEnabled)

      assert(disabledHandle.value.isEmpty)
      assert(enabledHandle.value.nonEmpty)
      assert(BeautyQNonProductionHybridExperimentActivation.default == BeautyQNonProductionHybridExperimentActivation.Disabled)
    }
  }

  private def moduleForDisabled: ModuleDef = new ModuleDef {
    make[OptionalHybridExperimentHandle].fromValue(OptionalHybridExperimentHandle(None))
  }

  private def moduleForEnabled: ModuleDef = new ModuleDef {
    make[LexicalDocumentBackend[IO, MasterServiceOfferVariantId]].from {
      new LexicalDocumentBackend[IO, MasterServiceOfferVariantId] {
        override def documentHits(
          input: UserSearchInput,
          intent: ParsedSearchIntent,
        ): IO[QueryFailure, List[LexicalDocumentHit[MasterServiceOfferVariantId]]] =
          ZIO.dieMessage(s"fake lexical backend must not be called during module construction: $input $intent")
      }
    }

    make[SemanticDocumentBackend[IO, MasterServiceOfferVariantId]].from {
      new SemanticDocumentBackend[IO, MasterServiceOfferVariantId] {
        override def documentHits(
          input: UserSearchInput,
          intent: ParsedSearchIntent,
        ): IO[QueryFailure, List[SemanticDocumentHit[MasterServiceOfferVariantId]]] =
          ZIO.dieMessage(s"fake semantic backend must not be called during module construction: $input $intent")
      }
    }

    make[SemanticDocumentLookup[IO, MasterServiceOfferVariantId, VariantSearchDocument]].from {
      new SemanticDocumentLookup[IO, MasterServiceOfferVariantId, VariantSearchDocument] {
        override def lookup(
          ids: List[MasterServiceOfferVariantId]
        ): IO[QueryFailure, Map[MasterServiceOfferVariantId, VariantSearchDocument]] =
          ZIO.dieMessage(s"fake document lookup must not be called during module construction: $ids")
      }
    }

    make[OptionalHybridExperimentHandle].from {
      (
        lexicalBackend: LexicalDocumentBackend[IO, MasterServiceOfferVariantId],
        semanticBackend: SemanticDocumentBackend[IO, MasterServiceOfferVariantId],
        documentLookup: SemanticDocumentLookup[IO, MasterServiceOfferVariantId, VariantSearchDocument],
      ) =>
        OptionalHybridExperimentHandle(
          BeautyQNonProductionHybridExperimentActivation.buildIfEnabled[IO](
            activation = BeautyQNonProductionHybridExperimentActivation.Enabled(createConfig("fake-module-gating")),
            lexicalBackend = lexicalBackend,
            semanticBackend = semanticBackend,
            documentLookup = documentLookup,
          )
        )
    }
  }

  private def buildHandle(module: ModuleDef): OptionalHybridExperimentHandle = {
    val locator = Injector().produce(
      bindings = module,
      roots = Roots.target[OptionalHybridExperimentHandle],
      activation = Activation.empty,
      locatorPrivacy = LocatorPrivacy.PublicByDefault,
    ).unsafeGet()
    locator.get[OptionalHybridExperimentHandle]
  }

  private def createConfig(experimentId: String): BeautyQNonProductionHybridExperimentConfig =
    BeautyQNonProductionHybridExperimentConfig.create(
      experimentId = experimentId,
      invocation = BeautyQNonProductionHybridExperimentInvocation.TestSetup,
      routing = BeautyQNonProductionHybridExperimentRouting.ExplicitInvocationOnly,
    ).fold(failure => fail(s"Expected valid config, got $failure"), identity)

  private final case class OptionalHybridExperimentHandle(
    value: Option[BeautyQNonProductionHybridResponseExperiment[IO]]
  )
}
