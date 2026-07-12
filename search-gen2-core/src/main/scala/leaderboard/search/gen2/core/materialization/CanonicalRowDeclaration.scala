package leaderboard.search.gen2.core.materialization

import leaderboard.search.gen2.contract.{SearchValueCodec, directFieldPath}

/** Starts an immutable canonical-row declaration for one concrete source value. Direct stored fields
  * use selectors, so their token names and canonical encoders are derived. Nested/dynamic collections
  * are expressed as named groups whose sort fragment is derived from the fields actually emitted by
  * that group; callers never maintain a parallel vector of group sizes.
  */
def canonicalRow[Source](prefix: String, source: Source): CanonicalRowDeclaration[Source] =
  CanonicalRowDeclaration.empty(prefix, source)

final class CanonicalRowDeclaration[Source] private (
  prefix: String,
  source: Source,
  fields: Vector[(String, String)],
  sortParts: Vector[String],
) {
  inline def field[Value](inline selector: Source => Value)(using codec: SearchValueCodec[Value]): CanonicalRowDeclaration[Source] = {
    val path  = directFieldPath[Source, Value](selector)
    val value = codec.encodeCanonical(selector(source))
    appendField(s"$prefix.$path", value)
  }

  /** Adds one explicitly named value when no direct source selector exists, for example a value
    * extracted from a tuple-backed dynamic attribute entry. Prefer [[field]] for stored product
    * members; this method is the narrow nested/computed escape hatch.
    */
  def value[Value](name: String, value: Value)(using codec: SearchValueCodec[Value]): CanonicalRowDeclaration[Source] =
    appendField(s"$prefix.$name", codec.encodeCanonical(value))

  /** Adds one deterministic nested/dynamic group. Every emitted group field contributes to one
    * grouped row-key fragment in exactly encounter order; an empty group contributes the same
    * significant empty fragment as the lower-level canonical framing API.
    */
  def group[Item](name: String, items: Iterable[Item])(
    declare: (CanonicalRowGroupDeclaration, Item) => CanonicalRowGroupDeclaration
  ): CanonicalRowDeclaration[Source] = {
    val declared = items.foldLeft(CanonicalRowGroupDeclaration.empty(s"$prefix.$name")) {
      (group, item) => declare(group, item)
    }
    new CanonicalRowDeclaration(
      prefix,
      source,
      fields ++ declared.fields,
      sortParts :+ CanonicalFingerprint.rowKey(declared.fields.map(_._2)*),
    )
  }

  def build: CanonicalFingerprint.CanonicalRow =
    CanonicalFingerprint.rowWithSortParts(sortParts, fields*)

  private def appendField(tag: String, canonicalValue: String): CanonicalRowDeclaration[Source] =
    new CanonicalRowDeclaration(prefix, source, fields :+ (tag -> canonicalValue), sortParts :+ canonicalValue)
}

private object CanonicalRowDeclaration {
  def empty[Source](prefix: String, source: Source): CanonicalRowDeclaration[Source] =
    new CanonicalRowDeclaration(prefix, source, Vector.empty, Vector.empty)
}

final class CanonicalRowGroupDeclaration private[materialization] (
  private[materialization] val prefix: String,
  private[materialization] val fields: Vector[(String, String)],
) {
  inline def field[Owner, Value](owner: Owner)(inline selector: Owner => Value)(using
    codec: SearchValueCodec[Value]
  ): CanonicalRowGroupDeclaration = {
    val path = directFieldPath[Owner, Value](selector)
    value(path, selector(owner))
  }

  def value[Value](name: String, value: Value)(using codec: SearchValueCodec[Value]): CanonicalRowGroupDeclaration =
    new CanonicalRowGroupDeclaration(prefix, fields :+ (s"$prefix.$name" -> codec.encodeCanonical(value)))
}

private object CanonicalRowGroupDeclaration {
  def empty(prefix: String): CanonicalRowGroupDeclaration =
    new CanonicalRowGroupDeclaration(prefix, Vector.empty)
}
