package leaderboard.search

import leaderboard.model.QueryFailure
import leaderboard.search.semantic.{SemanticCandidateAssembler, SemanticDocumentHit, SemanticDocumentLookup}
import org.scalatest.wordspec.AnyWordSpec

final class GenericSemanticCandidateAssemblerSpec extends AnyWordSpec {
  "Generic semantic candidate assembly" should {
    "preserve generic semantic hit order" in {
      val hits = List(
        SemanticDocumentHit("doc-2", 0.2),
        SemanticDocumentHit("doc-1", 0.8),
        SemanticDocumentHit("doc-3", 0.5),
      )

      val candidates = assemble(hits)

      assert(candidates.map(_.id) == List("doc-2", "doc-1", "doc-3"))
    }

    "deduplicate by document id with first hit wins" in {
      val hits = List(
        SemanticDocumentHit("doc-2", 0.91),
        SemanticDocumentHit("doc-1", 0.81),
        SemanticDocumentHit("doc-2", 0.31),
        SemanticDocumentHit("doc-3", 0.71),
        SemanticDocumentHit("doc-1", 0.21),
      )

      val candidates = assemble(hits)

      assert(candidates.map(_.id) == List("doc-2", "doc-1", "doc-3"))
      assert(candidates.map(_.score) == List(0.91, 0.81, 0.71))
    }

    "ignore unknown document ids" in {
      val hits = List(
        SemanticDocumentHit("missing", 0.99),
        SemanticDocumentHit("doc-1", 0.77),
      )

      val candidates = assemble(hits)

      assert(candidates.map(_.id) == List("doc-1"))
      assert(candidates.map(_.score) == List(0.77))
    }

    "preserve hit scores in projected candidates" in {
      val hits = List(
        SemanticDocumentHit("doc-1", 0.123),
        SemanticDocumentHit("doc-3", 0.987),
      )

      val candidates = assemble(hits)

      assert(candidates == List(
        TestSemanticCandidate("doc-1", "Alpha", 0.123),
        TestSemanticCandidate("doc-3", "Gamma", 0.987),
      ))
    }

    "return empty list when semantic hits are empty" in {
      val hits = List.empty[SemanticDocumentHit[String]]

      val candidates = assemble(hits)

      assert(candidates.isEmpty)
    }

    "return empty list when lookup returns empty document map" in {
      val hits = List(
        SemanticDocumentHit("missing-1", 0.5),
        SemanticDocumentHit("missing-2", 0.3),
      )
      val emptyDocumentsById = Map.empty[String, TestSemanticDocument]

      val result = SemanticCandidateAssembler.assembleUnique(hits, emptyDocumentsById) { (hit, doc) =>
        TestSemanticCandidate(doc.id, doc.title, hit.score)
      }

      assert(result.isEmpty)
    }

    "skip all hits when all document ids are missing from lookup" in {
      val hits = List(
        SemanticDocumentHit("missing-1", 0.9),
        SemanticDocumentHit("missing-2", 0.7),
        SemanticDocumentHit("missing-3", 0.5),
      )

      val candidates = assemble(hits)

      assert(candidates.isEmpty)
    }

    "handle duplicate hits with identical scores (first-wins)" in {
      val hits = List(
        SemanticDocumentHit("doc-1", 0.5),
        SemanticDocumentHit("doc-1", 0.5),
        SemanticDocumentHit("doc-2", 0.5),
      )

      val candidates = assemble(hits)

      assert(candidates.map(_.id) == List("doc-1", "doc-2"))
      assert(candidates.map(_.score) == List(0.5, 0.5))
      assert(candidates.size == 2)
    }

    "allow generic document lookup contracts without BeautyQ document types" in {
      val lookup: SemanticDocumentLookup[EitherQueryFailure, String, TestSemanticDocument] = new TestSemanticDocumentLookup(testDocuments)

      val result = lookup.lookup(List("doc-2", "missing", "doc-1"))

      assert(result == Right(Map(
        "doc-2" -> TestSemanticDocument("doc-2", "Beta", "bar"),
        "doc-1" -> TestSemanticDocument("doc-1", "Alpha", "foo"),
      )))
    }
  }

  private def assemble(hits: List[SemanticDocumentHit[String]]): List[TestSemanticCandidate] =
    SemanticCandidateAssembler.assembleUnique(hits, testDocumentsById) { (hit, document) =>
      TestSemanticCandidate(document.id, document.title, hit.score)
    }

  private val testDocuments = List(
    TestSemanticDocument("doc-1", "Alpha", "foo"),
    TestSemanticDocument("doc-2", "Beta", "bar"),
    TestSemanticDocument("doc-3", "Gamma", "foo"),
  )

  private val testDocumentsById = testDocuments.iterator.map(document => document.id -> document).toMap

  private type EitherQueryFailure[+E, +A] = Either[E, A]

  private final class TestSemanticDocumentLookup(documents: List[TestSemanticDocument])
    extends SemanticDocumentLookup[EitherQueryFailure, String, TestSemanticDocument] {
    private val documentsById = documents.iterator.map(document => document.id -> document).toMap

    override def lookup(ids: List[String]): Either[QueryFailure, Map[String, TestSemanticDocument]] =
      Right(ids.iterator.flatMap(id => documentsById.get(id).map(document => id -> document)).toMap)
  }

  private final case class TestSemanticDocument(id: String, title: String, category: String)
  private final case class TestSemanticCandidate(id: String, title: String, score: Double)
}
