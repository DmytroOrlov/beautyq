package leaderboard.search.gen2.core.supplement

/** Membership evidence produced by a trusted Gen2 backend decoder. Construction is restricted to
  * the package-private [[fromBackend]] factory so domain wiring cannot fabricate membership data. */
final class BaselineMembershipResult[Id] private[gen2] (
  val requestedIds: Vector[Id],
  val matchingIds: Vector[Id],
)

object BaselineMembershipResult {

  private[gen2] def fromBackend[Id](
    requestedIds: Vector[Id],
    matchingIds: Vector[Id],
  ): BaselineMembershipResult[Id] =
    new BaselineMembershipResult(requestedIds, matchingIds)
}

sealed trait AppendOnlySupplementPolicyError

object AppendOnlySupplementPolicyError {
  final case class InvalidMaxAppended(value: Int)
    extends AppendOnlySupplementPolicyError
}

final class AppendOnlySupplementPolicy private (
  val maxAppended: Int,
) {

  def select[Candidate, Id](
    baselinePageIds: Vector[Id],
    candidates: Vector[Candidate],
    membership: BaselineMembershipResult[Id],
  )(
    idOf: Candidate => Id,
  ): Either[
    AppendOnlySupplementSelectionError[Id],
    AppendOnlySupplementSelection[Candidate, Id],
  ] = {
    val candidateIds = candidates.map(idOf)
    if (candidateIds != membership.requestedIds)
      Left(AppendOnlySupplementSelectionError.MembershipInputMismatch(candidateIds, membership.requestedIds))
    else {
      val baselinePageSet = baselinePageIds.toSet
      val matchingSet = membership.matchingIds.toSet
      val result = candidates.zip(candidateIds).foldLeft(
        (Vector.empty[Candidate], Vector.empty[Id], Vector.empty[Id], Vector.empty[Id], 0)
      ) { case ((appended, pageDupes, members, excluded, count), (candidate, cid)) =>
        if (baselinePageSet.contains(cid))
          (appended, pageDupes :+ cid, members, excluded, count)
        else if (matchingSet.contains(cid))
          (appended, pageDupes, members :+ cid, excluded, count)
        else if (count < maxAppended)
          (appended :+ candidate, pageDupes, members, excluded, count + 1)
        else
          (appended, pageDupes, members, excluded :+ cid, count)
      }
      Right(new AppendOnlySupplementSelection(result._1, result._2, result._3, result._4))
    }
  }
}

object AppendOnlySupplementPolicy {
  def create(
    maxAppended: Int,
  ): Either[
    AppendOnlySupplementPolicyError.InvalidMaxAppended,
    AppendOnlySupplementPolicy,
  ] =
    if (maxAppended > 0) Right(new AppendOnlySupplementPolicy(maxAppended))
    else Left(AppendOnlySupplementPolicyError.InvalidMaxAppended(maxAppended))
}

sealed trait AppendOnlySupplementSelectionError[+Id]

object AppendOnlySupplementSelectionError {
  final case class MembershipInputMismatch[Id](
    candidateIds: Vector[Id],
    membershipRequestedIds: Vector[Id],
  ) extends AppendOnlySupplementSelectionError[Id]
}

final class AppendOnlySupplementSelection[Candidate, Id] private[supplement] (
  val appended: Vector[Candidate],
  val currentPageDuplicateIds: Vector[Id],
  val baselineMemberIds: Vector[Id],
  val budgetExcludedIds: Vector[Id],
)

object AppendOnlySupplementSelection {

  private[supplement] def apply[Candidate, Id](
    appended: Vector[Candidate],
    currentPageDuplicateIds: Vector[Id],
    baselineMemberIds: Vector[Id],
    budgetExcludedIds: Vector[Id],
  ): AppendOnlySupplementSelection[Candidate, Id] =
    new AppendOnlySupplementSelection(appended, currentPageDuplicateIds, baselineMemberIds, budgetExcludedIds)
}
