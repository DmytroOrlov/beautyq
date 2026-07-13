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
}
