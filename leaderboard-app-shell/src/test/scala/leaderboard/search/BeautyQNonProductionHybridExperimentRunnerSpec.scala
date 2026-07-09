package leaderboard.search

import java.util.UUID

import leaderboard.model.{MasterId, MasterLocationId, MasterServiceOfferId, MasterServiceOfferVariantId, QueryFailure, ServiceId}
import leaderboard.model.Category.CategoryId
import leaderboard.search.document.VariantSearchDocument
import leaderboard.search.dsl.SearchGeoPoint
import leaderboard.search.hybrid.{BeautyQNonProductionHybridExperimentRunner, BeautyQNonProductionHybridResponseExperiment, BeautyQNonProductionHybridResponseExperimentResult}
import leaderboard.search.lexical.{LexicalDocumentBackend, LexicalDocumentHit}
import leaderboard.search.semantic.{SemanticDocumentBackend, SemanticDocumentHit, SemanticDocumentLookup}
import org.scalatest.wordspec.AnyWordSpec
import zio.{IO, Ref, Runtime, Unsafe, ZIO}

final class BeautyQNonProductionHybridExperimentRunnerSpec extends AnyWordSpec {
  "BeautyQNonProductionHybridExperimentRunner" should {
    "construction is side-effect-free: constructing does not call backends or lookup" in {
      val constructed = new BeautyQNonProductionHybridExperimentRunner[IO](
        new BeautyQNonProductionHybridResponseExperiment[IO](
          lexicalBackend = new LexicalDocumentBackend[IO, MasterServiceOfferVariantId] {
            override def documentHits(
              input: UserSearchInput,
              intent: ParsedSearchIntent,
            ): IO[QueryFailure, List[LexicalDocumentHit[MasterServiceOfferVariantId]]] =
              ZIO.dieMessage("lexical backend must not be called during construction")
          },
          semanticBackend = new SemanticDocumentBackend[IO, MasterServiceOfferVariantId] {
            override def documentHits(
              input: UserSearchInput,
              intent: ParsedSearchIntent,
            ): IO[QueryFailure, List[SemanticDocumentHit[MasterServiceOfferVariantId]]] =
              ZIO.dieMessage("semantic backend must not be called during construction")
          },
          documentLookup = new SemanticDocumentLookup[IO, MasterServiceOfferVariantId, VariantSearchDocument] {
            override def lookup(
              ids: List[MasterServiceOfferVariantId]
            ): IO[QueryFailure, Map[MasterServiceOfferVariantId, VariantSearchDocument]] =
              ZIO.dieMessage("document lookup must not be called during construction")
          },
        ),
      )

      assert(constructed.isInstanceOf[BeautyQNonProductionHybridExperimentRunner[IO]])
    }

    "run delegates to wrapped experiment and returns experiment result" in {
      val lexical = variantDocument(1)

      val (result, calls) = runSuccess(
        lexicalHits = List(LexicalDocumentHit(lexical.variantId, 10.0)),
        semanticHits = Nil,
        documents = List(lexical),
      )

      assert(result.response.variantCarousel.map(_.variantId) == List(lexical.variantId))
      assert(calls.collect { case Call.Lexical(input, intent) => input -> intent } == List(input -> intent))
      assert(result.diagnostics.lexicalHitCount == 1)
      assert(result.diagnostics.semanticHitCount == 0)
    }

    "run delegates to wrapped experiment: lexical + semantic with overlap" in {
      val lexicalOnly = variantDocument(2)
      val overlap = variantDocument(3)
      val semanticOnly = variantDocument(4)

      val (result, _) = runSuccess(
        lexicalHits = List(
          LexicalDocumentHit(lexicalOnly.variantId, 5.0),
          LexicalDocumentHit(overlap.variantId, 3.0),
        ),
        semanticHits = List(
          SemanticDocumentHit(overlap.variantId, 0.8),
          SemanticDocumentHit(semanticOnly.variantId, 0.7),
        ),
        documents = List(lexicalOnly, overlap, semanticOnly),
      )

      assert(result.response.variantCarousel.map(_.variantId) == List(lexicalOnly.variantId, overlap.variantId, semanticOnly.variantId))
      assert(result.diagnostics.distinctVariantIdCount == 3)
    }

    "semantic-only response still works through runner" in {
      val semantic = variantDocument(5)

      val (result, calls) = runSuccess(
        lexicalHits = Nil,
        semanticHits = List(SemanticDocumentHit(semantic.variantId, 0.85)),
        documents = List(semantic),
      )

      assert(result.response.variantCarousel.map(_.variantId) == List(semantic.variantId))
      assert(result.diagnostics.lexicalHitCount == 0)
      assert(result.diagnostics.semanticHitCount == 1)
      assert(calls.exists(_.isInstanceOf[Call.Lexical]))
      assert(calls.exists(_.isInstanceOf[Call.Semantic]))
    }

    "missing lookup document failure propagates through runner" in {
      val lexical = variantDocument(6)
      val missingId = MasterServiceOfferVariantId(indexUuid(7))

      val error = runFailure(
        lexicalResult = Right(List(LexicalDocumentHit(lexical.variantId, 1.0), LexicalDocumentHit(missingId, 0.5))),
        semanticResult = Right(Nil),
        lookupResult = Right(Map(lexical.variantId -> lexical)),
      )

      assert(error.isInstanceOf[QueryFailure])
      assert(error.message.contains("Missing VariantSearchDocument"))
      assert(error.message.contains(missingId.toString))
    }

    "lexical backend failure propagates through runner" in {
      val lexicalFailure = QueryFailure.operation("fake-lexical", "lexical failed")

      val (error, calls) = runFailureWithCalls(
        lexicalResult = Left(lexicalFailure),
        semanticResult = Right(Nil),
        lookupResult = Right(Map.empty),
      )

      assert(error == lexicalFailure)
      assert(calls == List(Call.Lexical(input, intent)))
    }

    "semantic backend failure propagates through runner and skips lookup" in {
      val lexical = variantDocument(8)
      val semanticFailure = QueryFailure.operation("fake-semantic", "semantic failed")

      val (error, calls) = runFailureWithCalls(
        lexicalResult = Right(List(LexicalDocumentHit(lexical.variantId, 1.0))),
        semanticResult = Left(semanticFailure),
        lookupResult = Right(Map(lexical.variantId -> lexical)),
      )

      assert(error == semanticFailure)
      assert(!calls.exists(_.isInstanceOf[Call.Lookup]))
    }

    "lookup failure propagates through runner" in {
      val lexical = variantDocument(9)
      val lookupFailure = QueryFailure.operation("fake-lookup", "lookup failed")

      val error = runFailure(
        lexicalResult = Right(List(LexicalDocumentHit(lexical.variantId, 1.0))),
        semanticResult = Right(Nil),
        lookupResult = Left(lookupFailure),
      )

      assert(error == lookupFailure)
    }

    "use only injected fake seams without Qdrant Elasticsearch llama or file IO" in {
      val lexical = variantDocument(10)

      val (result, calls) = runSuccess(
        lexicalHits = List(LexicalDocumentHit(lexical.variantId, 1.0)),
        semanticHits = Nil,
        documents = List(lexical),
      )

      assert(result.response.variantCarousel.map(_.variantId) == List(lexical.variantId))
      assert(calls.map(_.productPrefix) == List("Lexical", "Semantic", "Lookup"))
    }

    "populate diagnostics through runner" in {
      val lexical = variantDocument(11)
      val overlap = variantDocument(12)
      val semantic = variantDocument(13)

      val (result, _) = runSuccess(
        lexicalHits = List(LexicalDocumentHit(lexical.variantId, 2.0), LexicalDocumentHit(overlap.variantId, 1.0)),
        semanticHits = List(SemanticDocumentHit(overlap.variantId, 0.8), SemanticDocumentHit(semantic.variantId, 0.7)),
        documents = List(lexical, overlap, semantic),
      )

      assert(result.diagnostics.lexicalHitCount == 2)
      assert(result.diagnostics.semanticHitCount == 2)
      assert(result.diagnostics.distinctVariantIdCount == 3)
      assert(result.diagnostics.pipeline.policy.overlapCount == 1)
      assert(result.diagnostics.pipeline.responseAdapter.variantResultCount == 3)
    }
  }

  private def runSuccess(
    lexicalHits: List[LexicalDocumentHit[MasterServiceOfferVariantId]],
    semanticHits: List[SemanticDocumentHit[MasterServiceOfferVariantId]],
    documents: List[VariantSearchDocument],
    searchInput: UserSearchInput = input,
    parsedIntent: ParsedSearchIntent = intent,
  ): (BeautyQNonProductionHybridResponseExperimentResult, List[Call]) =
    run {
      Ref.make(List.empty[Call]).flatMap { callsRef =>
        runner(
          callsRef,
          lexicalResult = Right(lexicalHits),
          semanticResult = Right(semanticHits),
          lookupResult = Right(documents.iterator.map(document => document.variantId -> document).toMap),
        ).run(searchInput, parsedIntent).zip(callsRef.get.map(_.reverse))
      }
    }

  private def runFailure(
    lexicalResult: Either[QueryFailure, List[LexicalDocumentHit[MasterServiceOfferVariantId]]],
    semanticResult: Either[QueryFailure, List[SemanticDocumentHit[MasterServiceOfferVariantId]]],
    lookupResult: Either[QueryFailure, Map[MasterServiceOfferVariantId, VariantSearchDocument]],
  ): QueryFailure =
    runFailureWithCalls(lexicalResult, semanticResult, lookupResult)._1

  private def runFailureWithCalls(
    lexicalResult: Either[QueryFailure, List[LexicalDocumentHit[MasterServiceOfferVariantId]]],
    semanticResult: Either[QueryFailure, List[SemanticDocumentHit[MasterServiceOfferVariantId]]],
    lookupResult: Either[QueryFailure, Map[MasterServiceOfferVariantId, VariantSearchDocument]],
  ): (QueryFailure, List[Call]) =
    run {
      Ref.make(List.empty[Call]).flatMap { callsRef =>
        runner(callsRef, lexicalResult, semanticResult, lookupResult)
          .run(input, intent)
          .either
          .zip(callsRef.get.map(_.reverse))
          .map {
            case (Left(error), calls) => error -> calls
            case (Right(_), _) => fail("expected failure")
          }
      }
    }

  private def runner(
    callsRef: Ref[CallLog],
    lexicalResult: Either[QueryFailure, List[LexicalDocumentHit[MasterServiceOfferVariantId]]],
    semanticResult: Either[QueryFailure, List[SemanticDocumentHit[MasterServiceOfferVariantId]]],
    lookupResult: Either[QueryFailure, Map[MasterServiceOfferVariantId, VariantSearchDocument]],
  ): BeautyQNonProductionHybridExperimentRunner[IO] =
    new BeautyQNonProductionHybridExperimentRunner[IO](
      new BeautyQNonProductionHybridResponseExperiment[IO](
        lexicalBackend = new FakeLexicalBackend(callsRef, lexicalResult),
        semanticBackend = new FakeSemanticBackend(callsRef, semanticResult),
        documentLookup = new FakeDocumentLookup(callsRef, lookupResult),
      ),
    )

  private final class FakeLexicalBackend(
    callsRef: Ref[CallLog],
    result: Either[QueryFailure, List[LexicalDocumentHit[MasterServiceOfferVariantId]]],
  ) extends LexicalDocumentBackend[IO, MasterServiceOfferVariantId] {
    override def documentHits(
      input: UserSearchInput,
      intent: ParsedSearchIntent,
    ): IO[QueryFailure, List[LexicalDocumentHit[MasterServiceOfferVariantId]]] =
      callsRef.update(Call.Lexical(input, intent) :: _) *> ZIO.fromEither(result)
  }

  private final class FakeSemanticBackend(
    callsRef: Ref[CallLog],
    result: Either[QueryFailure, List[SemanticDocumentHit[MasterServiceOfferVariantId]]],
  ) extends SemanticDocumentBackend[IO, MasterServiceOfferVariantId] {
    override def documentHits(
      input: UserSearchInput,
      intent: ParsedSearchIntent,
    ): IO[QueryFailure, List[SemanticDocumentHit[MasterServiceOfferVariantId]]] =
      callsRef.update(Call.Semantic(input, intent) :: _) *> ZIO.fromEither(result)
  }

  private final class FakeDocumentLookup(
    callsRef: Ref[CallLog],
    result: Either[QueryFailure, Map[MasterServiceOfferVariantId, VariantSearchDocument]],
  ) extends SemanticDocumentLookup[IO, MasterServiceOfferVariantId, VariantSearchDocument] {
    override def lookup(
      ids: List[MasterServiceOfferVariantId]
    ): IO[QueryFailure, Map[MasterServiceOfferVariantId, VariantSearchDocument]] =
      callsRef.update(Call.Lookup(ids) :: _) *> ZIO.fromEither(result)
  }

  private sealed trait Call extends Product with Serializable
  private object Call {
    final case class Lexical(input: UserSearchInput, intent: ParsedSearchIntent) extends Call
    final case class Semantic(input: UserSearchInput, intent: ParsedSearchIntent) extends Call
    final case class Lookup(ids: List[MasterServiceOfferVariantId]) extends Call
  }
  private type CallLog = List[Call]

  private val input = UserSearchInput(query = "synthetic runner test", userLat = None, userLon = None, limit = 10)
  private val intent = intentFor(input)

  private def intentFor(searchInput: UserSearchInput): ParsedSearchIntent = ParsedSearchIntent(
    originalQuery = searchInput.query,
    normalizedTokens = List("synthetic", "runner", "test"),
    explicitConstraints = Nil,
    softBoosts = Nil,
    remainingText = searchInput.query,
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
      variantId = MasterServiceOfferVariantId(indexUuid(index)),
      masterServiceOfferId = MasterServiceOfferId(indexUuid(index + 100)),
      masterLocationId = MasterLocationId(indexUuid(locationIndex)),
      masterId = MasterId(indexUuid(masterIndex)),
      serviceId = ServiceId(indexUuid(resolvedServiceIndex)),
      categoryId = CategoryId(indexUuid(categoryIndex)),
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

  private def indexUuid(value: Int): UUID =
    UUID.fromString(f"00000000-0000-0000-0000-$value%012d")

  private def run[A](effect: IO[QueryFailure, A]): A =
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(effect).getOrThrowFiberFailure()
    }
}
