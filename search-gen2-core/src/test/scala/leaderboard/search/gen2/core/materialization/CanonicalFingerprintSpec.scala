package leaderboard.search.gen2.core.materialization

import org.scalatest.wordspec.AnyWordSpec

final class CanonicalFingerprintSpec extends AnyWordSpec {

  private final case class SampleSource(id: String, count: Int)
  private final case class SampleItem(code: String, required: Boolean)

  "CanonicalFingerprint.sha256Hex" should {
    "produce deterministic output for equal input" in {
      assert(CanonicalFingerprint.sha256Hex("same-input") == CanonicalFingerprint.sha256Hex("same-input"))
    }

    "produce exactly 64 lowercase hexadecimal characters" in {
      val hex = CanonicalFingerprint.sha256Hex("any-value")
      assert(hex.length == 64)
      assert(hex.forall(c => c.isDigit || (c >= 'a' && c <= 'f')))
    }
  }

  "CanonicalFingerprint.token" should {
    // Without a length prefix, token("tag", "value:withcolon") and token("tag", "value") + ":withcolon"
    // would be indistinguishable once several tokens are joined into one block; the length prefix makes
    // every token's value boundary unambiguous regardless of delimiter-like characters inside it.
    "distinguish a value containing the block/row delimiter from a shorter value plus a literal suffix" in {
      val embeddedDelimiter = CanonicalFingerprint.token("tag", "value:withcolon")
      val shorterPlusSuffix = CanonicalFingerprint.token("tag", "value") + ":withcolon"
      assert(embeddedDelimiter != shorterPlusSuffix)
    }
  }

  "CanonicalFingerprint.rowKey" should {
    "distinguish parts whose naive concatenation would be ambiguous" in {
      val twoParts = CanonicalFingerprint.rowKey("ab", "c")
      val differentSplit = CanonicalFingerprint.rowKey("a", "bc")
      assert(twoParts != differentSplit)
    }
  }

  "CanonicalFingerprint.row" should {
    "derive emitted tokens and the row sort key from one field declaration" in {
      val row = CanonicalFingerprint.row("first" -> "a", "second" -> "b")
      assert(row.tokens == Vector(CanonicalFingerprint.token("first", "a"), CanonicalFingerprint.token("second", "b")))
      assert(row.sortKey == CanonicalFingerprint.rowKey("a", "b"))
    }

    "allow a domain to preserve a deliberate grouped sort fragment for nested fields" in {
      val row = CanonicalFingerprint.rowWithSortParts(
        Vector("owner", CanonicalFingerprint.rowKey("item", "required")),
        "owner.id" -> "owner",
        "item.code" -> "item",
        "item.required" -> "required",
      )
      assert(row.sortKey == CanonicalFingerprint.rowKey("owner", CanonicalFingerprint.rowKey("item", "required")))
      assert(row.tokens == Vector(
        CanonicalFingerprint.token("owner.id", "owner"),
        CanonicalFingerprint.token("item.code", "item"),
        CanonicalFingerprint.token("item.required", "required"),
      ))
    }

  }

  "canonicalRow" should {
    "derive direct field tags, canonical values and grouped ordering from one fluent declaration" in {
      val source = SampleSource("source-a", 2)
      val items  = Vector(SampleItem("first", required = true), SampleItem("second", required = false))

      val row = canonicalRow("sample", source)
        .field(_.id)
        .field(_.count)
        .group("item", items) { (group, item) =>
          group.field(item)(_.code).field(item)(_.required)
        }
        .build

      val expectedFields = Vector(
        "sample.id" -> "source-a",
        "sample.count" -> "2",
        "sample.item.code" -> "first",
        "sample.item.required" -> "true",
        "sample.item.code" -> "second",
        "sample.item.required" -> "false",
      )
      assert(row.tokens == expectedFields.map { case (tag, value) => CanonicalFingerprint.token(tag, value) })
      assert(
        row.sortKey == CanonicalFingerprint.rowKey(
          "source-a",
          "2",
          CanonicalFingerprint.rowKey("first", "true", "second", "false"),
        )
      )
    }

    "preserve an empty nested group without a parallel size declaration" in {
      val row = canonicalRow("sample", SampleSource("source-a", 2))
        .field(_.id)
        .group("item", Vector.empty[SampleItem]) { (group, item) =>
          group.field(item)(_.code)
        }
        .build

      assert(row.tokens == Vector(CanonicalFingerprint.token("sample.id", "source-a")))
      assert(row.sortKey == CanonicalFingerprint.rowKey("source-a", ""))
    }
  }

  "CanonicalFingerprint.block" should {
    "join tokens with exactly one newline" in {
      assert(CanonicalFingerprint.block(Vector("a", "b", "c")) == "a\nb\nc")
    }
  }

  "CanonicalFingerprint.sha256HexTokens" should {
    "change when token order changes" in {
      assert(CanonicalFingerprint.sha256HexTokens(Vector("a", "b")) != CanonicalFingerprint.sha256HexTokens(Vector("b", "a")))
    }

    "produce equal hashes for equal token sequences" in {
      assert(CanonicalFingerprint.sha256HexTokens(Vector("a", "b")) == CanonicalFingerprint.sha256HexTokens(List("a", "b")))
    }

    "produce exactly 64 lowercase hexadecimal characters" in {
      val hex = CanonicalFingerprint.sha256HexTokens(Vector("a", "b", "c"))
      assert(hex.length == 64)
      assert(hex.forall(c => c.isDigit || (c >= 'a' && c <= 'f')))
    }
  }
}
