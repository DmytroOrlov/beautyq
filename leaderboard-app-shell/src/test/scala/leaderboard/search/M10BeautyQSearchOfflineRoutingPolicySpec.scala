package leaderboard.search

import leaderboard.search.eval.{
  M10BeautyQSearchOfflineRetrievalStrategyIntent,
  M10BeautyQSearchOfflineRoutingPolicy,
  M10BeautyQSearchQueryCategory,
  M10BeautyQSearchQueryClassification,
}
import org.scalatest.wordspec.AnyWordSpec

import java.nio.charset.StandardCharsets
import scala.io.Source
import scala.util.Using

final class M10BeautyQSearchOfflineRoutingPolicySpec extends AnyWordSpec {

  private val fixturePath: String =
    "/leaderboard/search/eval/m10-beautyq-query-classification-routing-examples.md"

  private val forbiddenClaims: List[String] = List(
    "hybrid serving",
    "score fusion",
    "reranking",
    "route switch",
    "route activation",
    "quality green",
    "production ready",
    "production readiness",
    "serving approval",
    "activation approved",
    "/beauty-search",
  )

  private final case class FixtureRow(
    queryId: String,
    anchor: Boolean,
    category: String,
    strategyIntent: String,
  )

  "M10BeautyQSearchOfflineRoutingPolicy" should {

    "emit only offline retrieval strategy intents" in {
      assert(M10BeautyQSearchOfflineRoutingPolicy.RepresentativeDecisions.nonEmpty)
      M10BeautyQSearchOfflineRoutingPolicy.RepresentativeDecisions.foreach { decision =>
        assert(M10BeautyQSearchOfflineRetrievalStrategyIntent.stableOrder.contains(decision.strategyIntent))
        assert(decision.boundary.offlinePlanningOnly)
      }
    }

    "map mixed intent to offline combined comparison, never production hybrid serving" in {
      val mixed = M10BeautyQSearchOfflineRoutingPolicy.RepresentativeDecisions
        .filter(_.category == M10BeautyQSearchQueryCategory.MixedIntent)

      assert(mixed.nonEmpty)
      mixed.foreach { decision =>
        assert(decision.strategyIntent == M10BeautyQSearchOfflineRetrievalStrategyIntent.CombinedEsQdrantComparison)
        assert(!decision.boundary.hybridServingImplied)
        assert(!decision.boundary.productionRouteActivated)
        assert(!decision.boundary.defaultRouteSwitched)
      }
    }

    "route noisy/ambiguous/non-beauty queries to manual review or no-op, not backend execution" in {
      val noisy = M10BeautyQSearchOfflineRoutingPolicy.RepresentativeDecisions
        .filter(_.category == M10BeautyQSearchQueryCategory.NoisyAmbiguousNonBeautyIntent)

      assert(noisy.nonEmpty)
      noisy.foreach { decision =>
        assert(
          decision.strategyIntent == M10BeautyQSearchOfflineRetrievalStrategyIntent.ManualReviewBlocked ||
            decision.strategyIntent == M10BeautyQSearchOfflineRetrievalStrategyIntent.NoOpNoise,
        )
        assert(!decision.strategyIntent.isBackendCandidateRetrievalIntent)
      }
    }

    "keep all production and Qdrant activation boundaries false/not approved" in {
      M10BeautyQSearchOfflineRoutingPolicy.RepresentativeDecisions.foreach { decision =>
        val b = decision.boundary
        assert(b.defaultBeautySearchEsBacked)
        assert(b.qdrantOptInDisabledByDefault)
        assert(!b.qdrantProductionActivationApproved)
        assert(!b.productionRouteActivated)
        assert(!b.defaultRouteSwitched)
        assert(!b.routeActivationClaimed)
        assert(!b.servingApprovalClaimed)
        assert(!b.qualityGreenClaimed)
        assert(!b.productionReadinessClaimed)
      }
    }

    "implement and require no real ES/Qdrant/client/route/plugin/DI/HTTP path" in {
      M10BeautyQSearchOfflineRoutingPolicy.RepresentativeDecisions.foreach { decision =>
        val b = decision.boundary
        assert(!b.productionBeautySearchCalled)
        assert(!b.esClientCreated)
        assert(!b.qdrantClientCreated)
        assert(!b.esExecuted)
        assert(!b.qdrantExecuted)
        assert(!b.routePluginDiHttpInvolved)
        assert(!b.realBackendCallImplemented)
        assert(!b.realBackendCallRequired)
        assert(!b.fallbackImplied)
        assert(!b.scoreFusionImplied)
        assert(!b.rerankingImplied)
        assert(!b.productionTelemetryImplied)
      }
    }

    "carry no forbidden production/hybrid/fallback/fusion/reranking/telemetry claims" in {
      val rationales = M10BeautyQSearchOfflineRoutingPolicy.RepresentativeDecisions.map(_.rationale)
      val fixture = readResource(fixturePath).toLowerCase

      rationales.foreach { rationale =>
        val lower = rationale.toLowerCase
        forbiddenClaims.foreach(claim => assert(!lower.contains(claim), s"forbidden '$claim' in: $rationale"))
      }
      // The fixture states the `/beauty-search` boundary explicitly as a negation, so it is excluded here.
      forbiddenClaims
        .filterNot(_ == "/beauty-search")
        .foreach(claim => assert(!fixture.contains(claim), s"forbidden '$claim' in fixture"))
    }

    "match the checked-in classification/routing fixture rows deterministically" in {
      val rows = parseFixture(readResource(fixturePath))
      assert(rows.size == M10BeautyQSearchQueryClassification.RepresentativeResults.size)

      rows.foreach { row =>
        val classification = M10BeautyQSearchQueryClassification
          .resultFor(row.queryId)
          .getOrElse(fail(s"missing classification for ${row.queryId}"))
        val decision = M10BeautyQSearchOfflineRoutingPolicy
          .decisionFor(row.queryId)
          .getOrElse(fail(s"missing decision for ${row.queryId}"))

        assert(classification.category.render == row.category, s"category mismatch ${row.queryId}")
        assert(decision.strategyIntent.render == row.strategyIntent, s"strategy mismatch ${row.queryId}")

        val isAnchor = M10BeautyQSearchQueryClassification.AnchorQueryIds.contains(row.queryId)
        assert(isAnchor == row.anchor, s"anchor flag mismatch ${row.queryId}")
      }

      M10BeautyQSearchQueryCategory.stableOrder.foreach { category =>
        assert(rows.exists(_.category == category.render), s"fixture missing category ${category.render}")
      }
      M10BeautyQSearchOfflineRetrievalStrategyIntent.stableOrder.foreach { intent =>
        assert(rows.exists(_.strategyIntent == intent.render), s"fixture missing strategy ${intent.render}")
      }
    }
  }

  private def parseFixture(contents: String): List[FixtureRow] =
    contents.linesIterator
      .map(_.trim)
      .filter(line => line.startsWith("| q_"))
      .map { line =>
        val cells = line.split('|').map(_.trim).filter(_.nonEmpty).toList
        cells match {
          case queryId :: anchor :: category :: strategy :: Nil =>
            FixtureRow(queryId, anchor == "yes", category, strategy)
          case other =>
            fail(s"malformed fixture row: ${other.mkString(" | ")}")
        }
      }
      .toList

  private def readResource(path: String): String =
    Option(getClass.getResourceAsStream(path)) match {
      case Some(value) =>
        Using.resource(Source.fromInputStream(value, StandardCharsets.UTF_8.name()))(_.mkString)
      case None =>
        fail(s"missing checked-in resource $path")
    }
}
