package leaderboard.search

import leaderboard.model.QueryFailure
import leaderboard.search.document.{BeautySearchCatalogSnapshot, BeautySearchReadyCatalogDocuments, VariantSearchDocumentBuilder}
import leaderboard.seed.{BeautyQSeedData, BeautyQSeedLoader}
import org.scalatest.wordspec.AnyWordSpec

final class BeautySearchReadyCatalogDocumentsSpec extends AnyWordSpec {

  "BeautySearchReadyCatalogDocuments.from" should {
    "return Right and preserve source/documents for non-empty source and non-empty documents" in {
      val seed      = loadSeedData()
      val snapshot  = BeautySearchCatalogSnapshot.fromSeedData(seed)
      val docs      = VariantSearchDocumentBuilder.build(snapshot) match {
        case Right(v) => v
        case Left(e) => throw new RuntimeException(e.message)
      }

      val result = BeautySearchReadyCatalogDocuments.from("test-source", docs)

      assert(result.isRight)
      result match {
        case Right(value) =>
          assert(value.source == "test-source")
          assert(value.documents eq docs)
        case Left(_) => fail("expected Right")
      }
    }

    "return Left QueryFailure for empty source string" in {
      val seed      = loadSeedData()
      val snapshot  = BeautySearchCatalogSnapshot.fromSeedData(seed)
      val docs      = VariantSearchDocumentBuilder.build(snapshot) match {
        case Right(v) => v
        case Left(e) => throw new RuntimeException(e.message)
      }

      val result = BeautySearchReadyCatalogDocuments.from("", docs)

      assert(result == Left(QueryFailure.domain("catalog document source is empty")))
    }

    "return Left QueryFailure for whitespace-only source string" in {
      val seed      = loadSeedData()
      val snapshot  = BeautySearchCatalogSnapshot.fromSeedData(seed)
      val docs      = VariantSearchDocumentBuilder.build(snapshot) match {
        case Right(v) => v
        case Left(e) => throw new RuntimeException(e.message)
      }

      val result = BeautySearchReadyCatalogDocuments.from("   ", docs)

      assert(result == Left(QueryFailure.domain("catalog document source is empty")))
    }

    "return Left QueryFailure for non-empty source and empty documents list" in {
      val result = BeautySearchReadyCatalogDocuments.from("test-source", Nil)

      assert(result == Left(QueryFailure.domain("test-source catalog documents are empty")))
    }
  }

  private def loadSeedData(): BeautyQSeedData =
    new BeautyQSeedLoader.ResourceLoader().load() match {
      case Right(value) => value
      case Left(error) => throw new RuntimeException(error.message)
    }
}
