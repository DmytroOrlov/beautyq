package leaderboard.model

import io.circe.{Codec, Decoder, Encoder}

// Direct references to the concrete named String Decoder/Encoder instances, not `Decoder[String]`/
// `Encoder[String]` implicit search: from inside an opaque type's own companion, the opaque type is
// transparently =:= String, so a search-based summon could resolve back to the codec value being
// defined there, producing an infinite self-reference. Taking the typeclass evidence as an explicit
// argument (never searched for) sidesteps that hazard for every canonical String-backed wrapper, not
// only the two declared in this file.
private[model] def canonicalStringCodec[A](
  value: CanonicalStringValue[A]
): Codec[A] =
  Codec.from(
    Decoder.decodeString.emap(value.decodeCanonical),
    Encoder.encodeString.contramap(value.encodeCanonical),
  )

/** Adds a Circe `Codec` on top of `leaderboard-core`'s reusable `StableCodeCompanion`, for the stable
  * codes declared in this module that need Circe JSON wire support. This is `beautyq-model`'s own
  * policy layer, not part of the reusable base: a future domain that wants a different JSON
  * representation, a different codec library, or no wire codec at all extends `StableCodeCompanion`
  * directly instead of this trait.
  */
private[model] trait StableCodeCirceSupport[A] extends StableCodeCompanion[A] {
  implicit final val codec: Codec[A] = canonicalStringCodec(this)
}

opaque type ServiceCode = String

object ServiceCode extends StableCodeCirceSupport[ServiceCode] {
  protected def codeType: String = "ServiceCode"
  protected def wrap(value: String): ServiceCode = value
  protected def unwrap(value: ServiceCode): String = value
  given CanonicalStringValue[ServiceCode] = this
}

opaque type CategoryCode = String

object CategoryCode extends StableCodeCirceSupport[CategoryCode] {
  protected def codeType: String = "CategoryCode"
  protected def wrap(value: String): CategoryCode = value
  protected def unwrap(value: CategoryCode): String = value
  given CanonicalStringValue[CategoryCode] = this
}
