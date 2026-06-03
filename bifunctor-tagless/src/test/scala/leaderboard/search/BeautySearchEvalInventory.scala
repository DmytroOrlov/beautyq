package leaderboard.search

import leaderboard.search.eval.BeautySearchEvalLoader

import java.nio.file.Paths

object BeautySearchEvalInventory {
  val evalSuite = BeautySearchEvalLoader.load(Paths.get("beautyq_search_eval_queries_v1.json")) match {
    case Right(value) => value
    case Left(error) => throw new RuntimeException(error.message)
  }

  val firstMilestoneQueryIds: Set[String] = Set(
    "q_nails_001",
    "q_nails_006",
    "q_nails_009",
    "q_lashes_001",
    "q_lashes_002",
    "q_brows_005",
    "q_pmu_001",
    "q_pmu_005",
    "q_face_001",
    "q_face_002",
    "q_face_004",
    "q_face_008",
  )

  val secondMilestoneQueryIds: Set[String] = Set(
    "q_nails_007",
    "q_nails_012",
    "q_lashes_004",
    "q_hair_002",
    "q_hair_003",
    "q_hair_004",
    "q_hair_006",
    "q_hair_007",
  )

  val hardNegativeQueryIds: Set[String] = Set(
    "q_nails_011",
    "q_lashes_007",
    "q_noise_003",
    "q_noise_004",
    "q_noise_005",
  )

  def inventorySummary: String = {
    val allQueries = evalSuite.queries
    val covered = firstMilestoneQueryIds ++ secondMilestoneQueryIds ++ hardNegativeQueryIds
    val uncovered = allQueries.map(_.id).filterNot(covered.contains).sorted
    s"total=${allQueries.size} covered=${covered.size} uncovered=${uncovered.size} uncoveredIds=${uncovered.mkString("[", ",", "]")}"
  }
}
