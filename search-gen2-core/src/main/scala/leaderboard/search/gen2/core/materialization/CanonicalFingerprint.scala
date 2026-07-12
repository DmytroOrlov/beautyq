package leaderboard.search.gen2.core.materialization

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/** Domain-neutral canonical token/row-key framing and SHA-256 hashing shared by every Gen2 fingerprint:
  * a length-prefixed writer (never case-class `toString`) so free-form text cannot be confused with a
  * field/part delimiter, plus one deterministic hex-digest implementation reused instead of being
  * copied between domain fingerprint implementations.
  */
object CanonicalFingerprint {
  final case class CanonicalRow(tokens: Vector[String], sortKey: String)

  def token(tag: String, value: String): String = s"$tag=${value.length}:$value"

  def rowKey(parts: String*): String = parts.iterator.map(part => s"${part.length}:$part").mkString("|")

  /** Builds one canonical row from the domain-selected `(tag, value)` fields. The same field
    * declaration drives both emitted tokens and the deterministic row sort key, avoiding the
    * parallel token/sort-value lists that otherwise tend to appear in every domain fingerprint.
    */
  def row(fields: (String, String)*): CanonicalRow =
    rowWithSortParts(fields.iterator.map(_._2).toVector, fields*)

  /** Variant for nested/dynamic fields whose historical canonical sort key intentionally groups
    * several emitted fields into one row fragment. The domain still selects those fragments, while
    * token framing and row-key construction remain generic and shared.
    */
  def rowWithSortParts(sortParts: Vector[String], fields: (String, String)*): CanonicalRow =
    CanonicalRow(
      tokens = fields.iterator.map { case (tag, value) => token(tag, value) }.toVector,
      sortKey = rowKey(sortParts*),
    )

  def orderedTokens(rows: IterableOnce[CanonicalRow]): Vector[String] =
    rows.iterator.toVector.sortBy(_.sortKey).flatMap(_.tokens)

  def block(tokens: IterableOnce[String]): String = tokens.iterator.mkString("\n")

  def sha256Hex(value: String): String = {
    val digestBytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))
    digestBytes.map(b => f"$b%02x").mkString
  }

  def sha256HexTokens(tokens: IterableOnce[String]): String = sha256Hex(block(tokens))
}
