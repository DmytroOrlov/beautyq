package leaderboard.http.tapir

import leaderboard.model.UuidBackedId
import sttp.tapir.{Codec, CodecFormat, Schema}

/** Reusable Tapir path/schema support for BeautyQ's nominal (opaque,
  * UUID-backed) id types, generically derived from `UuidBackedId[A]`
  * evidence (`beautyq-model`) instead of one block per id. `beautyq-model`
  * must not depend on Tapir, so this support lives here and is imported
  * wherever a Tapir endpoint captures one of these ids from a path segment
  * (`path[MasterId](...)`) or derives a `Schema` for a case class containing
  * one (`jsonBody[Master]` via `sttp.tapir.generic.auto.*`). Each instance is
  * a total, lossless `.map`/`.map` adaptation of Tapir's own built-in `UUID`
  * support ([[Codec.uuid]]/[[Schema.schemaForUUID]]), so wire representation
  * (a plain UUID string, format `"uuid"`) is unchanged from before the
  * nominal id migration.
  */
object BeautyQIdTapirSupport {
  given [A](using id: UuidBackedId[A]): Codec[String, A, CodecFormat.TextPlain] =
    Codec.uuid.map(id.apply)(id.unwrap)

  given [A](using id: UuidBackedId[A]): Schema[A] =
    Schema.schemaForUUID.map(uuid => Some(id.apply(uuid)))(id.unwrap)
}
