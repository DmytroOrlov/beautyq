package leaderboard.search.beautyq.contract

import leaderboard.search.contract.{IntentSection, NoiseControl, SearchVocabulary, SearchVocabularyGroup, SearchVocabularyId}
import leaderboard.search.dsl.{BeautyQSearchIntentVocabulary, SearchConstraint, SearchIntentRule}

/** Generic BeautyQ `IntentSection` derived from the existing search-core
  * `BeautyQSearchIntentVocabulary`. Old `SearchIntentRule.StructuredAlias`
  * scaladoc explicitly says structured aliases are not lexical backend
  * analyzer synonyms, so `SearchVocabulary.synonyms` stays `Map.empty` here -
  * inventing synonyms from alias token sets would misrepresent them.
  * `constraints`/`softBoosts`/`requires`/`excludes` have no generic
  * `SearchVocabulary` fields to carry them, so they are intentionally
  * dropped from this mapping.
  */
object BeautyQSearchIntentSectionContract {
  private val rules: List[SearchIntentRule[SearchConstraint]] =
    BeautyQSearchIntentVocabulary.vocabulary.rules

  val structuredAliasVocabularies: List[SearchVocabulary] =
    rules.collect {
      case SearchIntentRule.StructuredAlias(tokens, _, _, _, _, _, _) =>
        tokens.toList.sorted
    }.zipWithIndex.map {
      case (terms, index) =>
        SearchVocabulary(
          id = SearchVocabularyId(s"structured-alias-${index + 1}"),
          terms = terms,
          synonyms = Map.empty,
        )
    }

  val structuredAliasVocabularyGroup: SearchVocabularyGroup =
    SearchVocabularyGroup(
      name = "structured-aliases",
      vocabularies = structuredAliasVocabularies,
    )

  val noiseControls: List[NoiseControl] =
    rules.collect {
      case SearchIntentRule.QueryNoisePhrase(tokens, _, _, _) =>
        tokens.toList.sorted
    }.zipWithIndex.map {
      case (terms, index) =>
        NoiseControl(
          description = s"BeautyQ query noise phrase ${index + 1}",
          excludedTerms = terms,
        )
    }

  val section: IntentSection =
    IntentSection(
      languages = BeautyQSearchLanguageContract.supported,
      vocabularies = structuredAliasVocabularies,
      vocabularyGroups = List(structuredAliasVocabularyGroup),
      noiseControls = noiseControls,
    )
}
