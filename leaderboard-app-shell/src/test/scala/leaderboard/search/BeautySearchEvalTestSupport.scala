package leaderboard.search

import leaderboard.model.QueryFailure
import leaderboard.search.eval.BeautySearchEvalQuery
import zio.{IO, ZIO}

object BeautySearchEvalTestSupport {
  final case class EsDebug(
    intent: ParsedSearchIntent,
    requestJson: io.circe.Json,
    rawHitCount: Option[Long],
  )

  def assertEvalOutcome(
    query: BeautySearchEvalQuery,
    response: BeautySearchResponse,
    report: leaderboard.search.eval.BeautySearchEvalReport,
    check: String,
    debug: Option[EsDebug] = None,
  ): Unit = {
    assert(
      response.variantCarousel.take(3).exists(result => query.expectedVariantCarousel.acceptableVariantIds.contains(result.variantId)),
      diagnosticMessage(query, response, report, check, debug),
    )
    assert(
      response.providerCarousel.take(5).exists(result => query.expectedProviderCarousel.acceptableProviderLocationIds.contains(result.masterLocationId)),
      diagnosticMessage(query, response, report, check, debug),
    )
    assert(
      response.serviceIntentCarousel.take(3).exists(result => query.expectedServiceIntentCarousel.acceptableServiceIds.contains(result.serviceId)),
      diagnosticMessage(query, response, report, check, debug),
    )
    assert(report.failedAssertions.isEmpty, diagnosticMessage(query, response, report, check, debug))
  }

  def requireEvalOutcome(
    query: BeautySearchEvalQuery,
    response: BeautySearchResponse,
    report: leaderboard.search.eval.BeautySearchEvalReport,
    check: String,
    debug: Option[EsDebug] = None,
  ): IO[QueryFailure, Unit] =
    if (
      response.variantCarousel.take(3).exists(result => query.expectedVariantCarousel.acceptableVariantIds.contains(result.variantId)) &&
      response.providerCarousel.take(5).exists(result => query.expectedProviderCarousel.acceptableProviderLocationIds.contains(result.masterLocationId)) &&
      response.serviceIntentCarousel.take(3).exists(result => query.expectedServiceIntentCarousel.acceptableServiceIds.contains(result.serviceId)) &&
      report.failedAssertions.isEmpty
    ) then ZIO.unit
    else ZIO.fail(QueryFailure.operation("beautyq-search-eval", diagnosticMessage(query, response, report, check, debug)))

  def diagnosticMessage(
    query: BeautySearchEvalQuery,
    response: BeautySearchResponse,
    report: leaderboard.search.eval.BeautySearchEvalReport,
    check: String,
    debug: Option[EsDebug] = None,
  ): String =
    s"check=$check queryId=${query.id} query=${query.query} " +
      s"topVariantIds=${response.variantCarousel.take(3).map(_.variantId).mkString("[", ",", "]")} " +
      s"topProviderLocationIds=${response.providerCarousel.take(5).map(_.masterLocationId).mkString("[", ",", "]")} " +
      s"topServiceIds=${response.serviceIntentCarousel.take(3).map(_.serviceId).mkString("[", ",", "]")} " +
      s"failedAssertions=${report.failedAssertions.mkString("[", ",", "]")} " +
      debug.fold("")(d =>
        s"intent=${d.intent} explicitConstraints=${d.intent.explicitConstraints} softBoosts=${d.intent.softBoosts} remainingText=${d.intent.remainingText} rawHitCount=${d.rawHitCount} request=${d.requestJson.noSpaces} "
      )
}
