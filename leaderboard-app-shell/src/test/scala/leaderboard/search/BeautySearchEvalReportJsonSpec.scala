package leaderboard.search

import leaderboard.model.{MasterLocationId, MasterServiceOfferVariantId, QueryFailure, ServiceId}
import leaderboard.search.eval.{BeautySearchEvalReport, BeautySearchEvalReportJson}
import org.scalatest.wordspec.AnyWordSpec

import java.util.UUID

final class BeautySearchEvalReportJsonSpec extends AnyWordSpec {

  private def variantId(slot: Int): MasterServiceOfferVariantId =
    MasterServiceOfferVariantId(UUID.fromString(f"00000000-0000-0000-0000-00000000${slot}%04x"))

  private def locationId(slot: Int): MasterLocationId =
    MasterLocationId(UUID.fromString(f"10000000-0000-0000-0000-00000000${slot}%04x"))

  private def serviceId(slot: Int): ServiceId =
    ServiceId(UUID.fromString(f"20000000-0000-0000-0000-00000000${slot}%04x"))

  private val v1: MasterServiceOfferVariantId = variantId(1)
  private val v2: MasterServiceOfferVariantId = variantId(2)
  private val l1: MasterLocationId = locationId(1)
  private val s1: ServiceId = serviceId(1)

  "BeautySearchEvalReportJson" should {

    "round-trip a non-empty list of reports" in {
      val reports = List(
        BeautySearchEvalReport(
          queryId = "q_rt_1",
          query = "fixture query 1",
          score = 10,
          failedAssertions = List("assertion a"),
          topVariantIds = List(v1, v2),
          topProviderLocationIds = List(l1),
          topServiceIds = List(s1),
        ),
        BeautySearchEvalReport(
          queryId = "q_rt_2",
          query = "fixture query 2",
          score = 5,
          failedAssertions = Nil,
          topVariantIds = List(v1),
          topProviderLocationIds = Nil,
          topServiceIds = Nil,
        ),
      )

      val decoded = BeautySearchEvalReportJson
        .decodeReportsString(BeautySearchEvalReportJson.encodeReportsString(reports))
        .fold(failure => fail(s"unexpected decode failure: $failure"), identity)

      assert(decoded == reports)
      assert(decoded.map(_.queryId) == List("q_rt_1", "q_rt_2"))
      assert(decoded.size == 2)
    }

    "round-trip an empty list" in {
      val reports = List.empty[BeautySearchEvalReport]
      val decoded = BeautySearchEvalReportJson
        .decodeReportsString(BeautySearchEvalReportJson.encodeReportsString(reports))
        .fold(failure => fail(s"unexpected decode failure: $failure"), identity)

      assert(decoded == reports)
      assert(decoded.isEmpty)
    }

    "fail clearly on invalid JSON string" in {
      val result = BeautySearchEvalReportJson.decodeReportsString("{")

      result match {
        case Left(QueryFailure.OperationFailure(operationName, message)) =>
          assert(operationName == "beauty-search-eval-report-json")
          assert(message.contains("Invalid BeautySearch eval report JSON"))
        case other =>
          fail(s"expected OperationFailure, got $other")
      }
    }
  }
}
