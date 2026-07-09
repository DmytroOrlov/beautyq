package leaderboard.search

import java.util.UUID

import leaderboard.model.{MasterId, MasterLocationId, MasterServiceOfferId, MasterServiceOfferVariantId, QueryFailure, ServiceId}
import leaderboard.model.Category.CategoryId
import leaderboard.search.document.VariantSearchDocument
import leaderboard.search.dsl.SearchGeoPoint
import leaderboard.search.hybrid.BeautyQNonProductionHybridResponseExperiment
import leaderboard.search.lexical.{LexicalDocumentBackend, LexicalDocumentHit}
import leaderboard.search.semantic.{SemanticDocumentBackend, SemanticDocumentHit, SemanticDocumentLookup}
import org.scalatest.wordspec.AnyWordSpec
import zio.{IO, Ref, Runtime, Unsafe, ZIO}

final class BeautyQNonProductionHybridResponseExperimentSpec extends AnyWordSpec {
  "BeautyQ non-production hybrid response experiment" should {
    "run lexical and semantic backends with the supplied input and intent" in {
      val lexical = variantDocument(1)
      val semantic = variantDocument(2)

      val (result, calls) = runSuccess(
        lexicalHits = List(LexicalDocumentHit(lexical.variantId, 10.0)),
        semanticHits = List(SemanticDocumentHit(semantic.variantId, 0.9)),
        documents = List(lexical, semantic),
      )

      assert(result.response.variantCarousel.map(_.variantId) == List(lexical.variantId, semantic.variantId))
      assert(calls.collect { case Call.Lexical(input, intent) => input -> intent } == List(input -> intent))
      assert(calls.collect { case Call.Semantic(input, intent) => input -> intent } == List(input -> intent))
    }

    "hydrate distinct ids in lexical-first channel order" in {
      val firstLexical = variantDocument(3)
      val secondLexical = variantDocument(4)
      val semanticOnly = variantDocument(5)

      val (_, calls) = runSuccess(
        lexicalHits = List(
          LexicalDocumentHit(firstLexical.variantId, 1.0),
          LexicalDocumentHit(secondLexical.variantId, 2.0),
          LexicalDocumentHit(firstLexical.variantId, 0.5),
        ),
        semanticHits = List(
          SemanticDocumentHit(semanticOnly.variantId, 0.91),
          SemanticDocumentHit(secondLexical.variantId, 0.81),
        ),
        documents = List(firstLexical, secondLexical, semanticOnly),
      )

      assert(calls.collect { case Call.Lookup(ids) => ids } == List(List(firstLexical.variantId, secondLexical.variantId, semanticOnly.variantId)))
    }

    "build BeautySearchResponse through the pipeline" in {
      val lexical = variantDocument(6)

      val (result, _) = runSuccess(
        lexicalHits = List(LexicalDocumentHit(lexical.variantId, 5.0)),
        semanticHits = Nil,
        documents = List(lexical),
      )

      assert(result.response.isInstanceOf[BeautySearchResponse])
      assert(result.diagnostics.pipeline.responseAdapter.variantResultCount == 1)
    }

    "preserve lexical-first order in the response variant carousel" in {
      val firstLexical = variantDocument(7)
      val secondLexical = variantDocument(8)
      val semantic = variantDocument(9)

      val (result, _) = runSuccess(
        lexicalHits = List(LexicalDocumentHit(firstLexical.variantId, 1.0), LexicalDocumentHit(secondLexical.variantId, 99.0)),
        semanticHits = List(SemanticDocumentHit(semantic.variantId, 0.99)),
        documents = List(firstLexical, secondLexical, semantic),
      )

      assert(result.response.variantCarousel.map(_.variantId) == List(firstLexical.variantId, secondLexical.variantId, semantic.variantId))
    }

    "append semantic-only candidates after lexical candidates" in {
      val lexical = variantDocument(10)
      val firstSemantic = variantDocument(11)
      val secondSemantic = variantDocument(12)

      val (result, _) = runSuccess(
        lexicalHits = List(LexicalDocumentHit(lexical.variantId, 3.0)),
        semanticHits = List(SemanticDocumentHit(firstSemantic.variantId, 0.9), SemanticDocumentHit(secondSemantic.variantId, 0.8)),
        documents = List(lexical, firstSemantic, secondSemantic),
      )

      assert(result.response.variantCarousel.map(_.variantId) == List(lexical.variantId, firstSemantic.variantId, secondSemantic.variantId))
    }

    "represent overlap once" in {
      val overlap = variantDocument(13)

      val (result, _) = runSuccess(
        lexicalHits = List(LexicalDocumentHit(overlap.variantId, 8.0)),
        semanticHits = List(SemanticDocumentHit(overlap.variantId, 0.91)),
        documents = List(overlap),
      )

      assert(result.response.variantCarousel.map(_.variantId) == List(overlap.variantId))
      assert(result.diagnostics.pipeline.policy.overlapCount == 1)
    }

    "populate provider and service carousels through the existing pure pipeline" in {
      val first = variantDocument(14, masterLocationIndex = 1000, serviceIndex = 2000)
      val sameProvider = variantDocument(15, masterLocationIndex = 1000, serviceIndex = 2001)
      val sameService = variantDocument(16, masterLocationIndex = 1001, serviceIndex = 2000)

      val (result, _) = runSuccess(
        lexicalHits = List(LexicalDocumentHit(first.variantId, 7.0), LexicalDocumentHit(sameProvider.variantId, 6.0)),
        semanticHits = List(SemanticDocumentHit(sameService.variantId, 0.85)),
        documents = List(first, sameProvider, sameService),
      )

      assert(result.response.providerCarousel.map(_.masterLocationId) == List(first.masterLocationId, sameService.masterLocationId))
      assert(result.response.providerCarousel.map(_.matchingVariantCount) == List(2, 1))
      assert(result.response.serviceIntentCarousel.map(_.serviceId) == List(first.serviceId, sameProvider.serviceId))
      assert(result.response.serviceIntentCarousel.map(_.matchingVariantCount) == List(2, 1))
    }

    "fail clearly when lookup returns a partial map and the pipeline sees a missing document" in {
      val present = variantDocument(17)
      val missingId = MasterServiceOfferVariantId(indexUuid(18))

      val error = runFailure(
        lexicalResult = Right(List(LexicalDocumentHit(present.variantId, 3.0), LexicalDocumentHit(missingId, 2.0))),
        semanticResult = Right(Nil),
        lookupResult = Right(Map(present.variantId -> present)),
      )

      assert(error.message.contains("Missing VariantSearchDocument"))
      assert(error.message.contains(missingId.toString))
    }

    "propagate lexical backend failure and not call semantic when lexical fails" in {
      val lexicalFailure = QueryFailure.operation("fake-lexical", "lexical failed")

      val (error, calls) = runFailureWithCalls(
        lexicalResult = Left(lexicalFailure),
        semanticResult = Right(Nil),
        lookupResult = Right(Map.empty),
      )

      assert(error == lexicalFailure)
      assert(calls == List(Call.Lexical(input, intent)))
    }

    "propagate semantic backend failure and not call lookup" in {
      val lexical = variantDocument(19)
      val semanticFailure = QueryFailure.operation("fake-semantic", "semantic failed")

      val (error, calls) = runFailureWithCalls(
        lexicalResult = Right(List(LexicalDocumentHit(lexical.variantId, 1.0))),
        semanticResult = Left(semanticFailure),
        lookupResult = Right(Map(lexical.variantId -> lexical)),
      )

      assert(error == semanticFailure)
      assert(!calls.exists(_.isInstanceOf[Call.Lookup]))
    }

    "propagate lookup failure" in {
      val lexical = variantDocument(20)
      val lookupFailure = QueryFailure.operation("fake-lookup", "lookup failed")

      val error = runFailure(
        lexicalResult = Right(List(LexicalDocumentHit(lexical.variantId, 1.0))),
        semanticResult = Right(Nil),
        lookupResult = Left(lookupFailure),
      )

      assert(error == lookupFailure)
    }

    "include lexical semantic distinct id and pipeline diagnostics" in {
      val lexical = variantDocument(21)
      val overlap = variantDocument(22)
      val semantic = variantDocument(23)

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

    "use only injected fake seams without Qdrant Elasticsearch llama or file IO" in {
      val lexical = variantDocument(24)

      val (result, calls) = runSuccess(
        lexicalHits = List(LexicalDocumentHit(lexical.variantId, 1.0)),
        semanticHits = Nil,
        documents = List(lexical),
      )

      assert(result.response.variantCarousel.map(_.variantId) == List(lexical.variantId))
      assert(calls.map(_.productPrefix) == List("Lexical", "Semantic", "Lookup"))
    }

    "derive explicit response limits from BeautySearchSpecV1 and input limit" in {
      val documents = (25 to 36).toList.map(index => variantDocument(index))
      val searchInput = input.copy(limit = 2)

      val (result, _) = runSuccess(
        lexicalHits = documents.map(document => LexicalDocumentHit(document.variantId, document.variantId.hashCode.toDouble)),
        semanticHits = Nil,
        documents = documents,
        searchInput = searchInput,
        parsedIntent = intentFor(searchInput),
      )

      assert(result.response.variantCarousel.map(_.variantId) == documents.take(2).map(_.variantId))
      assert(result.response.providerCarousel.map(_.masterLocationId) == documents.take(10).map(_.masterLocationId))
      assert(result.response.serviceIntentCarousel.map(_.serviceId) == documents.take(10).map(_.serviceId))
    }

    "construction is side-effect-free: constructing does not call backends or lookup" in {
      val _ = Unsafe.unsafe { implicit unsafe =>
        Runtime.default.unsafe.run(Ref.make(List.empty[Call])).getOrThrowFiberFailure()
      }

      val constructed = new BeautyQNonProductionHybridResponseExperiment[IO](
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
      )

      assert(constructed.isInstanceOf[BeautyQNonProductionHybridResponseExperiment[IO]])
    }

    "lexical backend called exactly once with input and intent" in {
      val lexical = variantDocument(30)

      val (_, calls) = runSuccess(
        lexicalHits = List(LexicalDocumentHit(lexical.variantId, 1.0)),
        semanticHits = Nil,
        documents = List(lexical),
      )

      val lexicalCalls = calls.collect { case Call.Lexical(i, _) => i }
      assert(lexicalCalls.size == 1)
      assert(lexicalCalls.head == input)
    }

    "semantic backend called exactly once with input and intent" in {
      val semantic = variantDocument(31)

      val (_, calls) = runSuccess(
        lexicalHits = Nil,
        semanticHits = List(SemanticDocumentHit(semantic.variantId, 0.9)),
        documents = List(semantic),
      )

      val semanticCalls = calls.collect { case Call.Semantic(i, _) => i }
      assert(semanticCalls.size == 1)
      assert(semanticCalls.head == input)
    }

    "semantic-only result produces response with semantic candidates" in {
      val semantic = variantDocument(32)

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

    "lookup receives distinct ids: duplicates collapsed, overlap represented once" in {
      val shared = variantDocument(33)
      val lexicalOnly = variantDocument(34)
      val semanticOnly = variantDocument(35)

      val (_, calls) = runSuccess(
        lexicalHits = List(
          LexicalDocumentHit(shared.variantId, 1.0),
          LexicalDocumentHit(lexicalOnly.variantId, 2.0),
          LexicalDocumentHit(shared.variantId, 0.5),
        ),
        semanticHits = List(
          SemanticDocumentHit(shared.variantId, 0.9),
          SemanticDocumentHit(semanticOnly.variantId, 0.8),
        ),
        documents = List(shared, lexicalOnly, semanticOnly),
      )

      val lookupIds = calls.collectFirst { case Call.Lookup(ids) => ids }.get
      assert(lookupIds == List(shared.variantId, lexicalOnly.variantId, semanticOnly.variantId))
      assert(lookupIds.size == 3)
    }

    "semantic-only ids passed to lookup when lexical is empty" in {
      val semantic1 = variantDocument(36)
      val semantic2 = variantDocument(37)

      val (_, calls) = runSuccess(
        lexicalHits = Nil,
        semanticHits = List(
          SemanticDocumentHit(semantic1.variantId, 0.9),
          SemanticDocumentHit(semantic2.variantId, 0.8),
        ),
        documents = List(semantic1, semantic2),
      )

      val lookupIds = calls.collectFirst { case Call.Lookup(ids) => ids }.get
      assert(lookupIds == List(semantic1.variantId, semantic2.variantId))
    }

    "missing lookup document propagates QueryFailure through pipeline" in {
      val lexical = variantDocument(38)
      val missingId = MasterServiceOfferVariantId(indexUuid(39))

      val error = runFailure(
        lexicalResult = Right(List(LexicalDocumentHit(lexical.variantId, 1.0), LexicalDocumentHit(missingId, 0.5))),
        semanticResult = Right(Nil),
        lookupResult = Right(Map(lexical.variantId -> lexical)),
      )

      assert(error.isInstanceOf[QueryFailure])
      assert(error.message.contains("Missing VariantSearchDocument"))
      assert(error.message.contains(missingId.toString))
    }
  }

  private def runSuccess(
    lexicalHits: List[LexicalDocumentHit[MasterServiceOfferVariantId]],
    semanticHits: List[SemanticDocumentHit[MasterServiceOfferVariantId]],
    documents: List[VariantSearchDocument],
    searchInput: UserSearchInput = input,
    parsedIntent: ParsedSearchIntent = intent,
  ): (leaderboard.search.hybrid.BeautyQNonProductionHybridResponseExperimentResult, List[Call]) =
    run {
      Ref.make(List.empty[Call]).flatMap { callsRef =>
        experiment(
          callsRef,
          lexicalResult = Right(lexicalHits),
          semanticResult = Right(semanticHits),
          lookupResult = Right(documents.iterator.map(document => document.variantId -> document).toMap),
        ).search(searchInput, parsedIntent).zip(callsRef.get.map(_.reverse))
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
        experiment(callsRef, lexicalResult, semanticResult, lookupResult)
          .search(input, intent)
          .either
          .zip(callsRef.get.map(_.reverse))
          .map {
            case (Left(error), calls) => error -> calls
            case (Right(_), _) => fail("expected failure")
          }
      }
    }

  private def experiment(
    callsRef: Ref[CallLog],
    lexicalResult: Either[QueryFailure, List[LexicalDocumentHit[MasterServiceOfferVariantId]]],
    semanticResult: Either[QueryFailure, List[SemanticDocumentHit[MasterServiceOfferVariantId]]],
    lookupResult: Either[QueryFailure, Map[MasterServiceOfferVariantId, VariantSearchDocument]],
  ): BeautyQNonProductionHybridResponseExperiment[IO] =
    new BeautyQNonProductionHybridResponseExperiment[IO](
      lexicalBackend = new FakeLexicalBackend(callsRef, lexicalResult),
      semanticBackend = new FakeSemanticBackend(callsRef, semanticResult),
      documentLookup = new FakeDocumentLookup(callsRef, lookupResult),
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

  private val input = UserSearchInput(query = "synthetic non-production hybrid", userLat = None, userLon = None, limit = 10)
  private val intent = intentFor(input)

  private def intentFor(searchInput: UserSearchInput): ParsedSearchIntent = ParsedSearchIntent(
    originalQuery = searchInput.query,
    normalizedTokens = List("synthetic", "non-production", "hybrid"),
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
