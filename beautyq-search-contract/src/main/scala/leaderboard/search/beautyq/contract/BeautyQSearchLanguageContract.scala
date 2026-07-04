package leaderboard.search.beautyq.contract

import leaderboard.search.contract.SearchLanguage

/** The explicit supported-language list `SearchDomainSpec.IntentSection`
  * requires. Order matches the eval validation report's language key order
  * (`de`, `en`, `ru`) for deterministic tests. `mixed_language` in that
  * report is eval tagging/metadata, not a language, and is intentionally not
  * declared here.
  */
object BeautyQSearchLanguageContract {
  val German: SearchLanguage = SearchLanguage("de")
  val English: SearchLanguage = SearchLanguage("en")
  val Russian: SearchLanguage = SearchLanguage("ru")

  val supported: List[SearchLanguage] =
    List(German, English, Russian)
}
