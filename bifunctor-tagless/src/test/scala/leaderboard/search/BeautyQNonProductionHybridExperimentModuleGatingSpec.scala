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
      val counters = FakeResourceCounters()
      val handle   = buildHandle(moduleFor(BeautyQNonProductionHybridExperimentActivation.Disabled, counters))

      assert(handle.value.isEmpty)
      assert(counters.lexicalConstructed == 0)
      assert(counters.semanticConstructed == 0)
      assert(counters.lookupConstructed == 0)
      assert(counters.lexicalCalled == 0)
      assert(counters.semanticCalled == 0)
      assert(counters.lookupCalled == 0)
    }

    "construct each fake resource exactly once when the explicit enabled module is selected" in {
      val counters = FakeResourceCounters()
      val handle   = buildHandle(moduleFor(BeautyQNonProductionHybridExperimentActivation.Enabled(createConfig("fake-module-gating")), counters))

      assert(handle.value.exists(_.isInstanceOf[BeautyQNonProductionHybridResponseExperiment[IO]]))
      assert(counters.lexicalConstructed == 1)
      assert(counters.semanticConstructed == 1)
      assert(counters.lookupConstructed == 1)
      assert(counters.lexicalCalled == 0)
      assert(counters.semanticCalled == 0)
      assert(counters.lookupCalled == 0)
    }

    "remain an explicit fake-only non-production adapter proof" in {
      val disabledHandle = buildHandle(moduleFor(BeautyQNonProductionHybridExperimentActivation.Disabled, FakeResourceCounters()))
      val enabledHandle  = buildHandle(moduleFor(BeautyQNonProductionHybridExperimentActivation.Enabled(createConfig("fake-only-shape")), FakeResourceCounters()))

      assert(disabledHandle.value.isEmpty)
      assert(enabledHandle.value.nonEmpty)
      assert(BeautyQNonProductionHybridExperimentActivation.default == BeautyQNonProductionHybridExperimentActivation.Disabled)
    }
  }

  private def moduleFor(
    activation: BeautyQNonProductionHybridExperimentActivation,
    counters: FakeResourceCounters,
  ): ModuleDef =
    activation match {
      case BeautyQNonProductionHybridExperimentActivation.Disabled =>
        new ModuleDef {
          make[OptionalHybridExperimentHandle].fromValue(OptionalHybridExperimentHandle(None))
        }

      case enabled: BeautyQNonProductionHybridExperimentActivation.Enabled =>
        new ModuleDef {
          make[LexicalDocumentBackend[IO, MasterServiceOfferVariantId]].from {
            counters.lexicalConstructed += 1
            new LexicalDocumentBackend[IO, MasterServiceOfferVariantId] {
              override def documentHits(
                input: UserSearchInput,
                intent: ParsedSearchIntent,
              ): IO[QueryFailure, List[LexicalDocumentHit[MasterServiceOfferVariantId]]] = {
                counters.lexicalCalled += 1
                ZIO.dieMessage(s"fake lexical backend must not be called during module construction: $input $intent")
              }
            }
          }

          make[SemanticDocumentBackend[IO, MasterServiceOfferVariantId]].from {
            counters.semanticConstructed += 1
            new SemanticDocumentBackend[IO, MasterServiceOfferVariantId] {
              override def documentHits(
                input: UserSearchInput,
                intent: ParsedSearchIntent,
              ): IO[QueryFailure, List[SemanticDocumentHit[MasterServiceOfferVariantId]]] = {
                counters.semanticCalled += 1
                ZIO.dieMessage(s"fake semantic backend must not be called during module construction: $input $intent")
              }
            }
          }

          make[SemanticDocumentLookup[IO, MasterServiceOfferVariantId, VariantSearchDocument]].from {
            counters.lookupConstructed += 1
            new SemanticDocumentLookup[IO, MasterServiceOfferVariantId, VariantSearchDocument] {
              override def lookup(
                ids: List[MasterServiceOfferVariantId]
              ): IO[QueryFailure, Map[MasterServiceOfferVariantId, VariantSearchDocument]] = {
                counters.lookupCalled += 1
                ZIO.dieMessage(s"fake document lookup must not be called during module construction: $ids")
              }
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
                  activation = enabled,
                  lexicalBackend = lexicalBackend,
                  semanticBackend = semanticBackend,
                  documentLookup = documentLookup,
                )
              )
          }
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

  private final case class FakeResourceCounters(
    var lexicalConstructed: Int = 0,
    var semanticConstructed: Int = 0,
    var lookupConstructed: Int = 0,
    var lexicalCalled: Int = 0,
    var semanticCalled: Int = 0,
    var lookupCalled: Int = 0,
  )
}
