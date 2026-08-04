package leaderboard.search.beautyq.gen2.contract

import java.util.Locale

/** The one shared normalization/tokenization contract for BeautyQ Gen2 intent matching. Both
  * [[BeautyQIntentVocabulary.validate]] (alias ambiguity detection) and the wiring parser (query and
  * alias phrase matching) must use this exact implementation, so a query and a vocabulary alias are
  * guaranteed to be compared on the same normalized ground - never two independently maintained
  * normalizers that could silently drift apart.
  *
  * Deliberately:
  *  - lowercase through `Locale.ROOT` (never the platform default locale);
  *  - Unicode-safe (operates on the full string, not byte-level);
  *  - `ё` normalized to `е` (a common Russian input variation, not a distinct phoneme for matching);
  *  - deterministic punctuation separation/removal (hyphen, en/em dash, slash, backslash, brackets,
  *    comma, period, colon, semicolon, `!`, `?`, quotes all become a separating space);
  *  - deterministic whitespace collapse (any run of whitespace becomes one space, trimmed at the ends).
  *
  * Deliberately not:
  *  - transliteration (Cyrillic stays Cyrillic; no Latin/Cyrillic script folding);
  *  - stemming or lemmatization;
  *  - a backend search analyzer of any kind.
  */
object BeautyQIntentTextGen2 {

  private val IntentCarrierTokens = Set(
    "аккуратно",
    "для",
    "ищу",
    "какого",
    "либо",
    "любого",
    "нужен",
    "нужна",
    "нужно",
    "пожалуйста",
    "процедура",
    "процедуру",
    "совсем",
    "вместе",
    "хочу",
  )

  private val CanonicalIntentTokens = Map(
    "acryl" -> "acrylic",
    "аквафейшл" -> "aquafacial",
    "аквафэйшл" -> "aquafacial",
    "бровная" -> "брови",
    "бровей" -> "брови",
    "ламинация" -> "ламинирование",
    "маникур" -> "маникюр",
    "маникюрную" -> "маникюр",
    "коррекцией" -> "коррекция",
    "лазерное" -> "лазер",
    "окраской" -> "окрашивание",
    "окрашиванием" -> "окрашивание",
    "окрашивания" -> "окрашивание",
    "педикур" -> "педикюр",
    "педикюрные" -> "педикюр",
    "подмышек" -> "подмышки",
  )

  def normalize(value: String): String =
    value
      .toLowerCase(Locale.ROOT)
      .replace('ё', 'е')
      .replace('–', ' ')
      .replace('—', ' ')
      .replace('-', ' ')
      .replace('/', ' ')
      .replace('\\', ' ')
      .replace('(', ' ')
      .replace(')', ' ')
      .replace('[', ' ')
      .replace(']', ' ')
      .replace('{', ' ')
      .replace('}', ' ')
      .replace(',', ' ')
      .replace('.', ' ')
      .replace(':', ' ')
      .replace(';', ' ')
      .replace('!', ' ')
      .replace('?', ' ')
      .replace('"', ' ')
      .replace('\'', ' ')
      .trim
      .replaceAll("\\s+", " ")

  def tokenizeNormalized(value: String): Vector[String] =
    normalize(value).split(' ').toVector.map(_.trim).filter(_.nonEmpty)

  /** BeautyQ-owned mechanical lexical equivalence used only for intent declaration matching. Public
    * query normalization remains lossless; semantic candidate text still uses [[normalize]]. This
    * boundary removes finite request carriers and canonicalizes one-token spelling, inflection and
    * transliteration variants of the same semantic atom. Multi-token service and attribute meaning
    * belongs exclusively to [[BeautyQIntentVocabulary]]. */
  def tokenizeForIntentMatching(value: String): Vector[String] = {
    val canonical = tokenizeNormalized(value).flatMap { token =>
      if (IntentCarrierTokens.contains(token)) Vector.empty
      else Vector(CanonicalIntentTokens.getOrElse(token, token))
    }
    canonical.foldLeft(Vector.empty[String]) { (acc, token) =>
      (acc.lastOption, token) match {
        case (Some("аква"), "фейшл" | "фэйшл") => acc.dropRight(1) :+ "aquafacial"
        case _ => acc :+ token
      }
    }
  }

  def intentMatchingKey(value: String): String =
    tokenizeForIntentMatching(value).mkString(" ")
}
