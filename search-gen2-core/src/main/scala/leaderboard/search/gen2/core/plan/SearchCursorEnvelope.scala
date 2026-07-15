package leaderboard.search.gen2.core.plan

import leaderboard.search.gen2.contract.*

import java.nio.ByteBuffer
import java.nio.charset.{CharacterCodingException, CodingErrorAction, StandardCharsets}
import java.util.Base64

opaque type BackendCursorState = String

object BackendCursorState {
  extension (state: BackendCursorState)
    def opaqueValue: String = state

  private[gen2] def fromOpaque(value: String): BackendCursorState = value
}

/** One executable owner of a validated plan/cursor pair. The plan, identity and decoded backend state
  * are intentionally bound together so a caller cannot pair a cursor result with another plan. */
final class BoundSearchPlan[Document] private (
  val plan: SearchPlan[Document],
  val identity: PlanIdentity,
  val identityHash: PlanIdentityHash,
  val backendState: Option[BackendCursorState],
) {
  def isFirstPage: Boolean = backendState.isEmpty
}

object BoundSearchPlan {
  private[gen2] def create[Document](
    plan: SearchPlan[Document],
    identity: PlanIdentity,
    identityHash: PlanIdentityHash,
    backendState: Option[BackendCursorState],
  ): BoundSearchPlan[Document] =
    new BoundSearchPlan(plan, identity, identityHash, backendState)
}

sealed trait SearchCursorError extends Product with Serializable

object SearchCursorError {
  final case class InvalidPlan(errors: NonEmptyErrors[SearchPlanError]) extends SearchCursorError
  final case class MalformedEnvelope(rawSegmentCount: Int) extends SearchCursorError
  final case class UnsupportedEnvelopeVersion(actual: String) extends SearchCursorError
  final case class InvalidPlanIdentityHash(value: String) extends SearchCursorError
  final case class InvalidBackendStateEncoding(value: String) extends SearchCursorError
  final case class PlanIdentityMismatch(expected: PlanIdentityHash, actual: PlanIdentityHash) extends SearchCursorError
}

object SearchCursorEnvelope {
  val EnvelopeVersion: String = "search-cursor-envelope-v1"

  def bind[Document](
    plan: SearchPlan[Document],
    view: CanonicalPlanView[Document],
  ): Either[SearchCursorError, BoundSearchPlan[Document]] =
    compileIdentity(plan, view).flatMap { identity =>
      val expected = PlanIdentityHash.compute(identity)
      plan.page.cursor match {
        case None => Right(BoundSearchPlan.create(plan, identity, expected, None))
        case Some(cursor) =>
          decode(cursor.opaqueValue).flatMap { case (storedHash, backendState) =>
            if (expected == storedHash) Right(BoundSearchPlan.create(plan, identity, expected, Some(backendState)))
            else Left(SearchCursorError.PlanIdentityMismatch(expected, storedHash))
          }
      }
    }

  /** Issues a cursor from the exact identity previously bound by [[bind]]. The backend state remains
    * an opaque string; only the backend that produced it may interpret its contents. */
  def issue[Document](bound: BoundSearchPlan[Document], backendState: String): SearchCursor = {
    val state = encodeBackendState(backendState)
    SearchCursor.fromOpaque(s"$EnvelopeVersion.${bound.identityHash.value}.$state")
  }

  private def compileIdentity[Document](
    plan: SearchPlan[Document],
    view: CanonicalPlanView[Document],
  ): Either[SearchCursorError, PlanIdentity] =
    PlanIdentityCompiler.compile(plan.withoutCursor, view) match {
      case Left(errors) => Left(SearchCursorError.InvalidPlan(errors))
      case Right(identity) => Right(identity)
    }

  private def encodeBackendState(value: String): String =
    Base64.getUrlEncoder.withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8))

  private def decode(raw: String): Either[SearchCursorError, (PlanIdentityHash, BackendCursorState)] = {
    val segments = raw.split("\\.", -1)
    if (segments.length != 3) Left(SearchCursorError.MalformedEnvelope(segments.length))
    else if (segments(0) != EnvelopeVersion) Left(SearchCursorError.UnsupportedEnvelopeVersion(segments(0)))
    else {
      val hash = PlanIdentityHash.parse(segments(1)) match {
        case Some(value) => Right(value)
        case None        => Left(SearchCursorError.InvalidPlanIdentityHash(segments(1)))
      }

      hash.flatMap { parsedHash =>
        decodeBackendState(segments(2)).map(value => parsedHash -> BackendCursorState.fromOpaque(value))
      }
    }
  }

  private def decodeBackendState(value: String): Either[SearchCursorError, String] = {
    val isUrlAlphabetWithoutPadding = value.forall { char =>
      (char >= 'A' && char <= 'Z') ||
        (char >= 'a' && char <= 'z') ||
        (char >= '0' && char <= '9') ||
        char == '_' || char == '-'
    }

    if (!isUrlAlphabetWithoutPadding) Left(SearchCursorError.InvalidBackendStateEncoding(value))
    else {
      try {
        val bytes = Base64.getUrlDecoder.decode(value)
        val reencoded = Base64.getUrlEncoder.withoutPadding().encodeToString(bytes)
        if (reencoded != value) Left(SearchCursorError.InvalidBackendStateEncoding(value))
        else decodeUtf8(bytes).toRight(SearchCursorError.InvalidBackendStateEncoding(value))
      } catch {
        case _: IllegalArgumentException => Left(SearchCursorError.InvalidBackendStateEncoding(value))
      }
    }
  }

  private def decodeUtf8(bytes: Array[Byte]): Option[String] = {
    val decoder = StandardCharsets.UTF_8
      .newDecoder()
      .onMalformedInput(CodingErrorAction.REPORT)
      .onUnmappableCharacter(CodingErrorAction.REPORT)

    try Some(decoder.decode(ByteBuffer.wrap(bytes)).toString)
    catch { case _: CharacterCodingException => None }
  }
}
