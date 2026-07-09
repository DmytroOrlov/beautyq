package leaderboard.model

import java.util.UUID

/** Dependency-free abstraction shared by every nominal (opaque) id that is a
  * UUID-backed wrapper, regardless of domain. Each id's companion extends
  * this once (`object ServiceId extends UuidBackedId[ServiceId]`) to get
  * `fromString` and the `.value` extension "for free" - the trait's own
  * `extension (id: A) def value` becomes an inherited member of each id's
  * companion object, so `someServiceId.value` resolves via ordinary
  * companion-object extension scope, no import needed anywhere. Each
  * companion also needs a `given UuidBackedId[X] = X` member (extending the
  * trait alone does not make a plain object an implicit candidate; it must
  * be registered separately), declared *inside* the companion, not as a
  * sibling, so it is part of that id's own companion-object implicit scope
  * and is found from every module without an import. Layer-local modules
  * (Circe wherever the id is declared; Doobie; Tapir; Scalacheck) each
  * derive one generic adapter from that evidence instead of repeating a
  * per-id adapter body.
  *
  * The primitive accessor is named `unwrap`, not `value`: a trait cannot
  * declare both an abstract `def value(id: A): UUID` and a concrete
  * `extension (id: A) def value: UUID` in the same body (same erased
  * signature, so it's a duplicate definition, not an override). Naming the
  * abstract member differently sidesteps that while keeping the public
  * call-site spelling (`id.value`) unchanged.
  *
  * Lives in `leaderboard-core` (not any one domain's model module) so a
  * future domain's own model module can depend on it directly, without
  * depending on an unrelated domain's model internals.
  */
trait UuidBackedId[A] {
  def apply(value: UUID): A
  def unwrap(id: A): UUID
  def fromString(value: String): A = apply(UUID.fromString(value))

  extension (id: A) def value: UUID = unwrap(id)
}
