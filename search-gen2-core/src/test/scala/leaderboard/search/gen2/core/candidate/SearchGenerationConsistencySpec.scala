package leaderboard.search.gen2.core.candidate

import org.scalatest.wordspec.AnyWordSpec

final class SearchGenerationConsistencySpec extends AnyWordSpec {

  private val fixtureA = new SearchGenerationEvidence {
    def sourceContentFingerprint: String = "src-a"
    def projectedDocumentsFingerprint: String = "proj-a"
    def projectionFormatVersion: String = "fmt-a"
    def documentCount: Long = 100
  }

  private val fixtureB = new SearchGenerationEvidence {
    def sourceContentFingerprint: String = "src-b"
    def projectedDocumentsFingerprint: String = "proj-b"
    def projectionFormatVersion: String = "fmt-b"
    def documentCount: Long = 200
  }

  private val fixtureC = new SearchGenerationEvidence {
    def sourceContentFingerprint: String = "src-a"
    def projectedDocumentsFingerprint: String = "proj-a"
    def projectionFormatVersion: String = "fmt-a"
    def documentCount: Long = 100
  }

  "SearchGenerationConsistency.verify" should {
    "accept equal evidence" in {
      assert(SearchGenerationConsistency.verify(fixtureA, fixtureC).isRight)
    }

    "reject source-content fingerprint mismatch" in {
      SearchGenerationConsistency.verify(fixtureA, fixtureB) match {
        case Left(SearchGenerationConsistencyError.SourceContentMismatch("src-a", "src-b")) =>
        case other => fail(s"expected SourceContentMismatch, got $other")
      }
    }

    "reject projected-documents fingerprint mismatch" in {
      val differentProjected = new SearchGenerationEvidence {
        def sourceContentFingerprint: String = "src-a"
        def projectedDocumentsFingerprint: String = "proj-different"
        def projectionFormatVersion: String = "fmt-a"
        def documentCount: Long = 100
      }
      SearchGenerationConsistency.verify(fixtureA, differentProjected) match {
        case Left(SearchGenerationConsistencyError.ProjectedDocumentsMismatch("proj-a", "proj-different")) =>
        case other => fail(s"expected ProjectedDocumentsMismatch, got $other")
      }
    }

    "reject projection-format mismatch" in {
      val differentFormat = new SearchGenerationEvidence {
        def sourceContentFingerprint: String = "src-a"
        def projectedDocumentsFingerprint: String = "proj-a"
        def projectionFormatVersion: String = "fmt-different"
        def documentCount: Long = 100
      }
      SearchGenerationConsistency.verify(fixtureA, differentFormat) match {
        case Left(SearchGenerationConsistencyError.ProjectionFormatMismatch("fmt-a", "fmt-different")) =>
        case other => fail(s"expected ProjectionFormatMismatch, got $other")
      }
    }

    "reject document-count mismatch" in {
      val differentCount = new SearchGenerationEvidence {
        def sourceContentFingerprint: String = "src-a"
        def projectedDocumentsFingerprint: String = "proj-a"
        def projectionFormatVersion: String = "fmt-a"
        def documentCount: Long = 999
      }
      SearchGenerationConsistency.verify(fixtureA, differentCount) match {
        case Left(SearchGenerationConsistencyError.DocumentCountMismatch(100, 999)) =>
        case other => fail(s"expected DocumentCountMismatch, got $other")
      }
    }

    "report first mismatch in deterministic order" in {
      val allDifferent = new SearchGenerationEvidence {
        def sourceContentFingerprint: String = "src-diff"
        def projectedDocumentsFingerprint: String = "proj-diff"
        def projectionFormatVersion: String = "fmt-diff"
        def documentCount: Long = 0
      }
      SearchGenerationConsistency.verify(fixtureA, allDifferent) match {
        case Left(SearchGenerationConsistencyError.SourceContentMismatch(_, _)) =>
        case other => fail(s"expected SourceContentMismatch (first check), got $other")
      }
    }
  }
}
