package leaderboard.search

import java.util.UUID

import distage.{Injector, ModuleDef}
import izumi.distage.model.definition.{Activation, LocatorPrivacy}
import izumi.distage.model.plan.Roots
import leaderboard.model.{MasterServiceOfferVariantId, QueryFailure}
import leaderboard.search.document.VariantSearchDocument
import leaderboard.search.dsl.SearchGeoPoint
import leaderboard.search.hybrid.{
  BeautyQNonProductionHybridExperimentRunner,
  BeautyQNonProductionHybridResponseExperimentResult,
}
import leaderboard.search.lexical.{LexicalDocumentBackend, LexicalDocumentHit}
import leaderboard.search.semantic.{SemanticDocumentBackend, SemanticDocumentHit, SemanticDocumentLookup}
import org.scalatest.wordspec.AnyWordSpec
import zio.{IO, Runtime, Unsafe, ZIO}

final class BeautyQNonProductionHybridExperimentRunnerModuleSpec extends AnyWordSpec {
  "BeautyQNonProductionHybridExperimentRunner Distage module" should {
    "construction is side-effect-free: materializing the module does not call backends or lookup" in {
      val probe = buildConstructionProbe
      assert(probe.runner.isInstanceOf[BeautyQNonProductionHybridExperimentRunner[IO]])
    }

    "materialized runner works with lexical-only hits" in {
      val lexical = variantDocument(1)
      val probe = buildRunnerProbe(
        lexicalHits = List(LexicalDocumentHit(lexical.variantId, 10.0)),
        semanticHits = Nil,
        documents = Map(lexical.variantId -> lexical),
      )

      val result = runRunner(probe)

      assert(result.response.variantCarousel.map(_.variantId) == List(lexical.variantId))
    }

    "materialized runner works with semantic-only hits" in {
      val semantic = variantDocument(2)
      val probe = buildRunnerProbe(
        lexicalHits = Nil,
        semanticHits = List(SemanticDocumentHit(semantic.variantId, 0.85)),
        documents = Map(semantic.variantId -> semantic),
      )

      val result = runRunner(probe)

      assert(result.response.variantCarousel.map(_.variantId) == List(semantic.variantId))
    }

    "missing lookup document propagates QueryFailure" in {
      val lexical = variantDocument(3)
      val missingId = variantId(4)

      val probe = buildRunnerProbe(
        lexicalHits = List(
          LexicalDocumentHit(lexical.variantId, 1.0),
          LexicalDocumentHit(missingId, 0.5),
        ),
        semanticHits = Nil,
        documents = Map(lexical.variantId -> lexical),
      )

      val error = runRunnerEither(probe)

      error match {
        case Left(e) =>
          assert(e.message.contains("Missing VariantSearchDocument"))
          assert(e.message.contains(missingId.toString))
        case Right(_) => fail("expected QueryFailure")
      }
    }
  }

  private def buildConstructionProbe: RunnerProbe = {
    val module = new ModuleDef {
      make[LexicalDocumentBackend[IO, MasterServiceOfferVariantId]].from {
        new FailIfCalledLexicalBackend
      }

      make[SemanticDocumentBackend[IO, MasterServiceOfferVariantId]].from {
        new FailIfCalledSemanticBackend
      }

      make[SemanticDocumentLookup[IO, MasterServiceOfferVariantId, VariantSearchDocument]].from {
        new FailIfCalledDocumentLookup
      }

      make[BeautyQNonProductionHybridExperimentRunner[IO]].from {
        (
          lexicalBackend: LexicalDocumentBackend[IO, MasterServiceOfferVariantId],
          semanticBackend: SemanticDocumentBackend[IO, MasterServiceOfferVariantId],
          documentLookup: SemanticDocumentLookup[IO, MasterServiceOfferVariantId, VariantSearchDocument],
        ) =>
          BeautyQNonProductionHybridExperimentRunner[IO](
            lexicalBackend = lexicalBackend,
            semanticBackend = semanticBackend,
            documentLookup = documentLookup,
          )
      }

      make[RunnerProbe].from {
        (runner: BeautyQNonProductionHybridExperimentRunner[IO]) =>
          RunnerProbe(runner)
      }
    }

    val locator = Injector().produce(
      bindings = module,
      roots = Roots.target[RunnerProbe],
      activation = Activation.empty,
      locatorPrivacy = LocatorPrivacy.PublicByDefault,
    ).unsafeGet()

    locator.get[RunnerProbe]
  }

  private def buildRunnerProbe(
    lexicalHits: List[LexicalDocumentHit[MasterServiceOfferVariantId]],
    semanticHits: List[SemanticDocumentHit[MasterServiceOfferVariantId]],
    documents: Map[MasterServiceOfferVariantId, VariantSearchDocument],
  ): RunnerProbe = {
    val module = new ModuleDef {
      make[LexicalDocumentBackend[IO, MasterServiceOfferVariantId]].from {
        new ScriptedLexicalBackend(lexicalHits)
      }

      make[SemanticDocumentBackend[IO, MasterServiceOfferVariantId]].from {
        new ScriptedSemanticBackend(semanticHits)
      }

      make[SemanticDocumentLookup[IO, MasterServiceOfferVariantId, VariantSearchDocument]].from {
        new ScriptedDocumentLookup(documents)
      }

      make[BeautyQNonProductionHybridExperimentRunner[IO]].from {
        (
          lexicalBackend: LexicalDocumentBackend[IO, MasterServiceOfferVariantId],
          semanticBackend: SemanticDocumentBackend[IO, MasterServiceOfferVariantId],
          documentLookup: SemanticDocumentLookup[IO, MasterServiceOfferVariantId, VariantSearchDocument],
        ) =>
          BeautyQNonProductionHybridExperimentRunner[IO](
            lexicalBackend = lexicalBackend,
            semanticBackend = semanticBackend,
            documentLookup = documentLookup,
          )
      }

      make[RunnerProbe].from {
        (runner: BeautyQNonProductionHybridExperimentRunner[IO]) =>
          RunnerProbe(runner)
      }
    }

    val locator = Injector().produce(
      bindings = module,
      roots = Roots.target[RunnerProbe],
      activation = Activation.empty,
      locatorPrivacy = LocatorPrivacy.PublicByDefault,
    ).unsafeGet()

    locator.get[RunnerProbe]
  }

  private def runRunner(probe: RunnerProbe): BeautyQNonProductionHybridResponseExperimentResult =
    runIO(probe.runner.run(input, intent))

  private def runRunnerEither(probe: RunnerProbe): Either[QueryFailure, BeautyQNonProductionHybridResponseExperimentResult] =
    runIO(probe.runner.run(input, intent).either)

  private def runIO[E, A](effect: IO[E, A]): A =
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(effect).getOrThrowFiberFailure()
    }

  private final case class RunnerProbe(
    runner: BeautyQNonProductionHybridExperimentRunner[IO],
  )

  private final class FailIfCalledLexicalBackend extends LexicalDocumentBackend[IO, MasterServiceOfferVariantId] {
    override def documentHits(
      input: UserSearchInput,
      intent: ParsedSearchIntent,
    ): IO[QueryFailure, List[LexicalDocumentHit[MasterServiceOfferVariantId]]] =
      ZIO.suspendSucceed(
        ZIO.fail(QueryFailure.domain("FailIfCalledLexicalBackend.documentHits was unexpectedly called"))
      )
  }

  private final class FailIfCalledSemanticBackend extends SemanticDocumentBackend[IO, MasterServiceOfferVariantId] {
    override def documentHits(
      input: UserSearchInput,
      intent: ParsedSearchIntent,
    ): IO[QueryFailure, List[SemanticDocumentHit[MasterServiceOfferVariantId]]] =
      ZIO.suspendSucceed(
        ZIO.fail(QueryFailure.domain("FailIfCalledSemanticBackend.documentHits was unexpectedly called"))
      )
  }

  private final class FailIfCalledDocumentLookup extends SemanticDocumentLookup[IO, MasterServiceOfferVariantId, VariantSearchDocument] {
    override def lookup(
      ids: List[MasterServiceOfferVariantId]
    ): IO[QueryFailure, Map[MasterServiceOfferVariantId, VariantSearchDocument]] =
      ZIO.suspendSucceed(
        ZIO.fail(QueryFailure.domain("FailIfCalledDocumentLookup.lookup was unexpectedly called"))
      )
  }

  private final class ScriptedLexicalBackend(
    result: List[LexicalDocumentHit[MasterServiceOfferVariantId]],
  ) extends LexicalDocumentBackend[IO, MasterServiceOfferVariantId] {
    override def documentHits(
      input: UserSearchInput,
      intent: ParsedSearchIntent,
    ): IO[QueryFailure, List[LexicalDocumentHit[MasterServiceOfferVariantId]]] =
      ZIO.succeed(result)
  }

  private final class ScriptedSemanticBackend(
    result: List[SemanticDocumentHit[MasterServiceOfferVariantId]],
  ) extends SemanticDocumentBackend[IO, MasterServiceOfferVariantId] {
    override def documentHits(
      input: UserSearchInput,
      intent: ParsedSearchIntent,
    ): IO[QueryFailure, List[SemanticDocumentHit[MasterServiceOfferVariantId]]] =
      ZIO.succeed(result)
  }

  private final class ScriptedDocumentLookup(
    result: Map[MasterServiceOfferVariantId, VariantSearchDocument],
  ) extends SemanticDocumentLookup[IO, MasterServiceOfferVariantId, VariantSearchDocument] {
    override def lookup(
      ids: List[MasterServiceOfferVariantId]
    ): IO[QueryFailure, Map[MasterServiceOfferVariantId, VariantSearchDocument]] =
      ZIO.succeed(result)
  }

  private val input = UserSearchInput(query = "module spec test", userLat = None, userLon = None, limit = 10)
  private val intent = ParsedSearchIntent(
    originalQuery = input.query,
    normalizedTokens = List("module", "spec", "test"),
    explicitConstraints = Nil,
    softBoosts = Nil,
    remainingText = input.query,
  )

  private def variantDocument(
    index: Int,
    masterLocationIndex: Int = -1,
    serviceIndex: Int = -1,
  ): VariantSearchDocument = {
    val locationIndex = if (masterLocationIndex >= 0) masterLocationIndex else index + 200
    val resolvedServiceIndex = if (serviceIndex >= 0) serviceIndex else index + 400
    val categoryIndex = resolvedServiceIndex + 100
    val masterIndex = locationIndex + 1000

    VariantSearchDocument(
      variantId = variantId(index),
      masterServiceOfferId = variantId(index + 100),
      masterLocationId = variantId(locationIndex),
      masterId = variantId(masterIndex),
      serviceId = variantId(resolvedServiceIndex),
      categoryId = variantId(categoryIndex),
      serviceName = s"Service $resolvedServiceIndex",
      categoryName = s"Category $categoryIndex",
      masterName = s"Master $masterIndex",
      locationName = s"Location $locationIndex",
      address = s"Main street $locationIndex",
      location = SearchGeoPoint(lat = BigDecimal("52.5200"), lon = BigDecimal("13.4050")),
      lat = BigDecimal("52.5200"),
      lon = BigDecimal("13.4050"),
      priceFrom = BigDecimal("25.00"),
      priceTo = BigDecimal("40.00"),
      durationMin = 45,
      enumAttributes = Map("coverage" -> "gel"),
      booleanAttributes = Map("with_removal" -> true),
      intAttributes = Map("nails_count" -> 10),
      bigDecimalAttributes = Map("rating" -> BigDecimal("4.8")),
      allText = s"service $resolvedServiceIndex category master location",
      serviceText = s"service $resolvedServiceIndex",
      attributeText = "coverage gel with removal",
      providerText = s"master $masterIndex location $locationIndex",
      locationText = s"location $locationIndex main street $locationIndex",
    )
  }

  private def variantId(value: Int): MasterServiceOfferVariantId =
    UUID.fromString(f"00000000-0000-0000-0000-$value%012d")
}
