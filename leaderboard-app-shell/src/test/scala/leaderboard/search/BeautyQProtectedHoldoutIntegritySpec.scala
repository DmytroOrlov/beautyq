package leaderboard.search

import io.circe.parser.parse
import leaderboard.search.beautyq.gen2.eval.*
import leaderboard.search.gen2.eval.{EvaluationPartition, EvaluationResultId, GradedGain, RankingJudgments}
import org.scalatest.wordspec.AnyWordSpec

import scala.io.Source

/** Ordinary integrity proof for the tracked protected holdout.
  *
  * The strict loaders already prove partition purity, case-count and required-slice
  * inventory, so loading is asserted rather than re-derived. The two relationships
  * below exist nowhere else: visible/protected query leakage and canonical-catalog
  * binding of protected judgment identities.
  */
final class BeautyQProtectedHoldoutIntegritySpec extends AnyWordSpec {
  private val ProtectedCorpusResource =
    "leaderboard/search/beautyq/gen2/eval/protected/beautyq-protected-holdout-v1.json"
  private val ProtectedPolicyResource =
    "leaderboard/search/beautyq/gen2/eval/protected/beautyq-protected-acceptance-policy-v1.json"

  private lazy val visible: BeautyQEvaluationCorpus =
    BeautyQEvaluationCorpus.loadCanonical() match {
      case Right(value) => value
      case Left(error)  => fail(s"expected canonical visible corpus, got $error")
    }

  private lazy val protectedCorpus: BeautyQProtectedEvaluationCorpus = {
    val policy = BeautyQProtectedAcceptancePolicy.fromJson(readJson(ProtectedPolicyResource)) match {
      case Right(value) => value
      case Left(error)  => fail(s"expected strict protected policy, got $error")
    }
    BeautyQProtectedEvaluationCorpus.fromJson(readJson(ProtectedCorpusResource), visible, policy) match {
      case Right(value) => value
      case Left(error)  => fail(s"expected strict protected corpus, got $error")
    }
  }

  "BeautyQ protected holdout" should {
    "strictly load the tracked protected policy and corpus inventory" in {
      val policy = BeautyQProtectedAcceptancePolicy.fromJson(readJson(ProtectedPolicyResource)) match {
        case Right(value) => value
        case Left(error)  => fail(s"expected strict protected policy, got $error")
      }
      val counts = protectedCorpus.orderedRequiredSliceCounts
      assert(policy.requiredSliceMinimums.nonEmpty)
      assert(policy.requiredMetricMinimums.nonEmpty)
      assert(protectedCorpus.caseCount == policy.expectedCaseCount)
      assert(counts.map(_._1) == policy.requiredSliceMinimums.map(_.sliceId))
      assert(counts.forall { case (sliceId, count) =>
        policy.requiredSliceMinimums.find(_.sliceId == sliceId).exists(requirement => count >= requirement.minimumCaseCount)
      })
      assert(protectedCorpus.corpus.cases.forall(_.partition == EvaluationPartition.ProtectedHoldout))
    }

    "keep protected queries out of the visible corpus exactly and after normalization" in {
      val exactVisible = visible.cases.map(_.query).toSet
      val normalizedVisible = visible.cases.map(current => BeautyQEvaluationQueryIdentity.normalize(current.query)).toSet
      val protectedQueries = protectedCorpus.corpus.cases.map(_.query)
      assert(protectedQueries.forall(query => !exactVisible.contains(query)))
      assert(protectedQueries.forall(query => !normalizedVisible.contains(BeautyQEvaluationQueryIdentity.normalize(query))))
      assert(protectedCorpus.corpus.cases.forall(current => !visible.cases.exists(_.caseId == current.caseId)))
    }

    "bind protected judgment identities to the canonical seed catalog" in {
      val catalog = BeautyQCanonicalSeedEvaluationCatalog.load() match {
        case Right(value) => value
        case Left(error)  => fail(s"expected canonical seed catalog, got $error")
      }
      assert(invalidJudgmentCount(_.variantJudgments, catalog.variantResultIds) == 0)
      assert(invalidJudgmentCount(_.providerJudgments, catalog.providerResultIds) == 0)
      assert(invalidJudgmentCount(_.serviceIntentJudgments, catalog.serviceIntentResultIds) == 0)
    }
  }

  private def invalidJudgmentCount(
    judgments: CorpusCase => RankingJudgments,
    catalogIds: Set[EvaluationResultId],
  ): Int = {
    def invalid(ids: Vector[EvaluationResultId]): Int = ids.count(id => !catalogIds.contains(id))
    protectedCorpus.corpus.cases.map { current =>
      val owned = judgments(current)
      invalid(owned.acceptableIds) + invalid(owned.forbiddenIds) + invalid(owned.neutralIds) +
        owned.gradedGains.count((gain: GradedGain) => !catalogIds.contains(gain.id))
    }.sum
  }

  private def readJson(path: String): io.circe.Json = {
    val stream = Option(getClass.getClassLoader.getResourceAsStream(path)).getOrElse(fail(s"missing resource: $path"))
    val raw = try Source.fromInputStream(stream, "UTF-8").mkString
    finally stream.close()
    parse(raw) match {
      case Right(json) => json
      case Left(error) => fail(s"$path is not valid JSON: $error")
    }
  }
}
