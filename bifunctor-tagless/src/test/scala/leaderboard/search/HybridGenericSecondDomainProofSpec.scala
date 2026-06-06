package leaderboard.search

import io.circe.Json
import leaderboard.model.QueryFailure
import leaderboard.search.dsl.{EmbeddingSpec, SearchDocumentSpec, SearchField, SearchFieldKind, SearchValue, VectorDistance}
import leaderboard.search.embedding.EmbeddingClient
import leaderboard.search.hybrid.{HybridDocumentRetrievalDiagnostics, HybridDocumentRetrievalResult}
import leaderboard.search.lexical.LexicalDocumentHit
import leaderboard.search.qdrant.{QdrantDocumentPointBuilder, QdrantPointId, QdrantPointUpsertClient, QdrantSearchDocumentIndexer}
import leaderboard.search.semantic.SemanticDocumentHit
import org.scalatest.wordspec.AnyWordSpec
import zio.{IO, Ref, Runtime, Unsafe, ZIO}

import java.util.UUID

final class HybridGenericSecondDomainProofSpec extends AnyWordSpec {
  "Generic search seams second-domain proof" should {
    "keep Article lexical and semantic hits in separate retrieval channels without implying policy" in {
      val articleA = articleId(1)
      val articleB = articleId(2)
      val articleC = articleId(3)
      val articleD = articleId(4)

      val retrieval: HybridDocumentRetrievalResult[ArticleId] =
        HybridDocumentRetrievalResult.fromHits(
          lexicalHits = List(
            LexicalDocumentHit(articleA, 1.0, matchedFields = List("title")),
            LexicalDocumentHit(articleB, 9.0, matchedFields = List("summary")),
          ),
          semanticHits = List(
            SemanticDocumentHit(articleC, 0.99),
            SemanticDocumentHit(articleB, 0.88),
            SemanticDocumentHit(articleD, 0.77),
          ),
        )

      val diagnostics: HybridDocumentRetrievalDiagnostics =
        HybridDocumentRetrievalResult.diagnostics(retrieval)

      assert(retrieval.lexicalHits.map(_.documentId) == List(articleA, articleB))
      assert(retrieval.semanticHits.map(_.documentId) == List(articleC, articleB, articleD))
      assert(retrieval.lexicalHits.map(_.score) == List(1.0, 9.0))
      assert(retrieval.semanticHits.map(_.score) == List(0.99, 0.88, 0.77))
      assert(diagnostics.lexicalHitCount == 2)
      assert(diagnostics.semanticHitCount == 3)
      assert(diagnostics.lexicalExecuted)
      assert(diagnostics.semanticExecuted)
      assert(HybridDocumentRetrievalResult.distinctDocumentIdsInChannelOrder(retrieval) == List(articleA, articleB, articleC, articleD))
    }

    "support a test-local Article lexical-first semantic supplement policy without fused scores or reranking" in {
      val articleA = articleId(11)
      val articleB = articleId(12)
      val articleC = articleId(13)
      val articleD = articleId(14)

      val retrieval =
        HybridDocumentRetrievalResult.fromHits(
          lexicalHits = List(
            LexicalDocumentHit(articleA, 1.0, matchedFields = List("title")),
            LexicalDocumentHit(articleB, 9.0, matchedFields = List("summary")),
            LexicalDocumentHit(articleA, 0.2, matchedFields = List("body")),
          ),
          semanticHits = List(
            SemanticDocumentHit(articleC, 0.99),
            SemanticDocumentHit(articleB, 0.88),
            SemanticDocumentHit(articleD, 0.77),
            SemanticDocumentHit(articleC, 0.33),
          ),
        )

      val result = ArticleHybridProofPolicy.lexicalFirstSemanticSupplement(retrieval)

      assert(result.candidates.map(_.articleId) == List(articleA, articleB, articleC, articleD))
      assert(result.candidates.map(_.sources) == List(
        Set(ArticleCandidateSource.Lexical),
        Set(ArticleCandidateSource.Lexical, ArticleCandidateSource.Semantic),
        Set(ArticleCandidateSource.Semantic),
        Set(ArticleCandidateSource.Semantic),
      ))
      assert(result.candidates.map(_.lexicalScore) == List(Some(1.0), Some(9.0), None, None))
      assert(result.candidates.map(_.semanticScore) == List(None, Some(0.88), Some(0.99), Some(0.77)))
      assert(!result.candidates.head.productElementNames.toSet.contains("score"))
      assert(!result.candidates.head.productElementNames.toSet.contains("fusedScore"))
      assert(result.diagnostics.lexicalInputCount == 3)
      assert(result.diagnostics.semanticInputCount == 4)
      assert(result.diagnostics.overlapCount == 1)
      assert(result.diagnostics.lexicalOnlyCount == 1)
      assert(result.diagnostics.semanticOnlyCount == 2)
    }

    "support Article-specific Qdrant point building and generic indexing without BeautyQ ids or runtime backends" in {
      val document = ArticleSearchDocument(
        articleId = articleId(21),
        slug = "vector-search-foundations",
        title = "  Vector Search Foundations  ",
        summary = "Tiny synthetic proof for reusable seams",
        topic = "architecture",
      )
      val vector = Vector(0.25, -0.5, 0.75)
      val textRef = runUio(Ref.make(Option.empty[String]))
      val pathRef = runUio(Ref.make(Option.empty[String]))
      val jsonRef = runUio(Ref.make(Option.empty[Json]))
      val indexer = new QdrantSearchDocumentIndexer[ArticleSearchDocument](
        embeddingClient = new FakeEmbeddingClient(textRef, Right(vector)),
        upsertClient = new FakeQdrantPointUpsertClient(pathRef, jsonRef, Right(Json.obj("status" -> Json.fromString("ok")))),
        documentSpec = articleDocumentSpec,
        embeddingSpec = articleEmbeddingSpec,
        pointBuilder = ArticlePointBuilder,
      )

      run(indexer.upsertDocument("article-second-domain-proof", document))

      val point = runUio(jsonRef.get).getOrElse(sys.error("expected point json")).hcursor.downField("points").downArray
      val payload = point.downField("payload")

      assert(runUio(textRef.get).contains("Vector Search Foundations Tiny synthetic proof for reusable seams"))
      assert(runUio(pathRef.get).contains("/collections/article-second-domain-proof/points?wait=true"))
      assert(point.downField("id").as[String] == Right(document.articleId.value.toString))
      assert(point.downField("vector").downField("article-semantic-vector").as[List[Double]] == Right(vector.toList))
      assert(payload.downField("articleSlug").as[String] == Right(document.slug))
      assert(payload.downField("topic").as[String] == Right(document.topic))
      assert(payload.downField("titleLength").as[Int] == Right(document.title.trim.length))
    }
  }

  private final case class ArticleId(value: UUID)

  private final case class ArticleSearchDocument(
    articleId: ArticleId,
    slug: String,
    title: String,
    summary: String,
    topic: String,
  )

  private enum ArticleCandidateSource {
    case Lexical, Semantic
  }

  private final case class ArticleSearchResultCandidate(
    articleId: ArticleId,
    lexicalScore: Option[Double],
    semanticScore: Option[Double],
    sources: Set[ArticleCandidateSource],
  )

  private final case class ArticleSearchResultDiagnostics(
    lexicalInputCount: Int,
    semanticInputCount: Int,
    overlapCount: Int,
    lexicalOnlyCount: Int,
    semanticOnlyCount: Int,
  )

  private final case class ArticleSearchResult(
    candidates: List[ArticleSearchResultCandidate],
    diagnostics: ArticleSearchResultDiagnostics,
  )

  private object ArticleHybridProofPolicy {
    def lexicalFirstSemanticSupplement(
      retrieval: HybridDocumentRetrievalResult[ArticleId]
    ): ArticleSearchResult = {
      val lexicalDistinct = distinctLexical(retrieval.lexicalHits)
      val semanticDistinct = distinctSemantic(retrieval.semanticHits)
      val lexicalIds = lexicalDistinct.map(_.documentId).toSet
      val semanticIds = semanticDistinct.map(_.documentId).toSet

      val lexicalCandidates =
        lexicalDistinct.map { hit =>
          val semanticScore = semanticDistinct.find(_.documentId == hit.documentId).map(_.score)
          ArticleSearchResultCandidate(
            articleId = hit.documentId,
            lexicalScore = Some(hit.score),
            semanticScore = semanticScore,
            sources =
              if (semanticScore.isDefined) Set(ArticleCandidateSource.Lexical, ArticleCandidateSource.Semantic)
              else Set(ArticleCandidateSource.Lexical),
          )
        }

      val semanticOnlyCandidates =
        semanticDistinct.collect {
          case hit if !lexicalIds(hit.documentId) =>
            ArticleSearchResultCandidate(
              articleId = hit.documentId,
              lexicalScore = None,
              semanticScore = Some(hit.score),
              sources = Set(ArticleCandidateSource.Semantic),
            )
        }

      ArticleSearchResult(
        candidates = lexicalCandidates ++ semanticOnlyCandidates,
        diagnostics = ArticleSearchResultDiagnostics(
          lexicalInputCount = retrieval.lexicalHits.size,
          semanticInputCount = retrieval.semanticHits.size,
          overlapCount = lexicalIds.intersect(semanticIds).size,
          lexicalOnlyCount = lexicalIds.diff(semanticIds).size,
          semanticOnlyCount = semanticIds.diff(lexicalIds).size,
        ),
      )
    }

    private def distinctLexical(hits: List[LexicalDocumentHit[ArticleId]]): List[LexicalDocumentHit[ArticleId]] =
      hits.foldLeft((Set.empty[ArticleId], List.empty[LexicalDocumentHit[ArticleId]])) {
        case ((seen, ordered), hit) if seen(hit.documentId) =>
          (seen, ordered)
        case ((seen, ordered), hit) =>
          (seen + hit.documentId, ordered :+ hit)
      }._2

    private def distinctSemantic(hits: List[SemanticDocumentHit[ArticleId]]): List[SemanticDocumentHit[ArticleId]] =
      hits.foldLeft((Set.empty[ArticleId], List.empty[SemanticDocumentHit[ArticleId]])) {
        case ((seen, ordered), hit) if seen(hit.documentId) =>
          (seen, ordered)
        case ((seen, ordered), hit) =>
          (seen + hit.documentId, ordered :+ hit)
      }._2
  }

  private object ArticlePointBuilder extends QdrantDocumentPointBuilder[ArticleSearchDocument] {
    override def qdrantPointId(document: ArticleSearchDocument): Either[QueryFailure, QdrantPointId] =
      QdrantPointId.fromUuidString(document.articleId.value.toString)

    override def payload(document: ArticleSearchDocument): Map[String, Json] =
      Map(
        "articleSlug" -> Json.fromString(document.slug),
        "topic" -> Json.fromString(document.topic),
        "titleLength" -> Json.fromInt(document.title.trim.length),
      )
  }

  private val articleDocumentSpec: SearchDocumentSpec[ArticleSearchDocument] =
    SearchDocumentSpec(
      indexName = "article-second-domain-proof",
      id = _.articleId.value.toString,
      fields = List(
        SearchField(
          path = "title",
          kind = SearchFieldKind.Text,
          extract = document => Some(SearchValue.Text(document.title)),
        ),
        SearchField(
          path = "summary",
          kind = SearchFieldKind.Text,
          extract = document => Some(SearchValue.Text(document.summary)),
        ),
      ),
    )

  private val articleEmbeddingSpec: EmbeddingSpec[ArticleSearchDocument] =
    EmbeddingSpec(
      vectorName = "article-semantic-vector",
      modelName = "article-fake-embedding",
      dimension = 3,
      distance = VectorDistance.Cosine,
      sourceTextFieldPaths = List("title", "summary"),
    )

  private final class FakeEmbeddingClient(
    textRef: Ref[Option[String]],
    result: Either[QueryFailure, Vector[Double]],
  ) extends EmbeddingClient {
    override def embed(text: String): IO[QueryFailure, Vector[Double]] =
      textRef.set(Some(text)) *> ZIO.fromEither(result)
  }

  private final class FakeQdrantPointUpsertClient(
    pathRef: Ref[Option[String]],
    jsonRef: Ref[Option[Json]],
    result: Either[QueryFailure, Json],
  ) extends QdrantPointUpsertClient {
    override def upsertPoint(path: String, json: Json): IO[QueryFailure, Json] =
      pathRef.set(Some(path)) *> jsonRef.set(Some(json)) *> ZIO.fromEither(result)
  }

  private def articleId(suffix: Int): ArticleId =
    ArticleId(UUID.fromString(f"00000000-0000-0000-0000-$suffix%012d"))

  private def run[A](effect: IO[QueryFailure, A]): A =
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(effect).getOrThrowFiberFailure()
    }

  private def runUio[A](effect: UIO[A]): A =
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(effect).getOrThrowFiberFailure()
    }

  private type UIO[A] = zio.UIO[A]
}
