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

final case class ValidatedSearchCursor(backendState: BackendCursorState)

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

  def issue[Document](
    plan: SearchPlan[Document],
    view: CanonicalPlanView[Document],
    backendState: BackendCursorState,
  ): Either[SearchCursorError, SearchCursor] =
    compileIdentity(plan, view).map { identity =>
      val hash = PlanIdentityHash.compute(identity)
      val state = encodeBackendState(backendState.opaqueValue)
      SearchCursor.fromOpaque(s"$EnvelopeVersion.${hash.value}.$state")
    }

  def validate[Document](
    plan: SearchPlan[Document],
    view: CanonicalPlanView[Document],
  ): Either[SearchCursorError, Option[ValidatedSearchCursor]] =
    plan.page.cursor match {
      case None => Right(None)
      case Some(cursor) =>
        compileIdentity(plan.withoutCursor, view).flatMap { identity =>
          decode(cursor.opaqueValue).flatMap { case (storedHash, backendState) =>
            val expected = PlanIdentityHash.compute(identity)
            if (expected == storedHash) Right(Some(ValidatedSearchCursor(backendState)))
            else Left(SearchCursorError.PlanIdentityMismatch(expected, storedHash))
          }
        }
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
