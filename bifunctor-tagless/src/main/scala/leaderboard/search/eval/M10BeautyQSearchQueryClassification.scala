package leaderboard.search.eval

/** M10 pure offline query classification contract over BeautyQ eval queries.
  *
  * This is an offline planning/eval contract only. It categorizes BeautyQ eval queries into stable
  * offline categories suitable for later offline retrieval policy work. It is a total pure function of
  * explicit offline signals: it never calls production `/beauty-search`, never creates an ES or Qdrant
  * client, never runs Elasticsearch or Qdrant, and never touches a route, plugin, DI, or HTTP source.
  * It makes no production/hybrid/fallback/fusion/reranking/telemetry/route-switch/quality/readiness/
  * activation/serving claim. Default `/beauty-search` remains ES-backed and Qdrant production activation
  * remains not approved; this contract neither changes nor depends on those facts.
  */
enum M10BeautyQSearchQueryCategory {
  case ProviderLookup
  case ServiceIntent
  case AttributeFilterIntent
  case LocationIntent
  case PriceBudgetIntent
  case AvailabilityTimeIntent
  case ComparisonExplorationIntent
  case NoisyAmbiguousNonBeautyIntent
  case MixedIntent

  def render: String =
    this match {
      case M10BeautyQSearchQueryCategory.ProviderLookup                => "provider_lookup"
      case M10BeautyQSearchQueryCategory.ServiceIntent                 => "service_intent"
      case M10BeautyQSearchQueryCategory.AttributeFilterIntent         => "attribute_filter_intent"
      case M10BeautyQSearchQueryCategory.LocationIntent                => "location_intent"
      case M10BeautyQSearchQueryCategory.PriceBudgetIntent             => "price_budget_intent"
      case M10BeautyQSearchQueryCategory.AvailabilityTimeIntent        => "availability_time_intent"
      case M10BeautyQSearchQueryCategory.ComparisonExplorationIntent   => "comparison_exploration_intent"
      case M10BeautyQSearchQueryCategory.NoisyAmbiguousNonBeautyIntent => "noisy_ambiguous_non_beauty_intent"
      case M10BeautyQSearchQueryCategory.MixedIntent                   => "mixed_intent"
    }
}

object M10BeautyQSearchQueryCategory {
  val stableOrder: List[M10BeautyQSearchQueryCategory] = List(
    ProviderLookup,
    ServiceIntent,
    AttributeFilterIntent,
    LocationIntent,
    PriceBudgetIntent,
    AvailabilityTimeIntent,
    ComparisonExplorationIntent,
    NoisyAmbiguousNonBeautyIntent,
    MixedIntent,
  )

  def fromRender(value: String): Option[M10BeautyQSearchQueryCategory] =
    stableOrder.find(_.render == value)
}

/** Explicit, deterministic offline intent signal derived from a query for classification.
  *
  * Signals are offline planning inputs only; they are not backend features, telemetry, or retrieval
  * results. `Provider`..`Comparison` are intent dimensions; `Ambiguous` and `NonBeauty` are noise
  * markers.
  */
enum M10BeautyQSearchQuerySignal {
  case Provider
  case Service
  case Attribute
  case Location
  case Price
  case Availability
  case Comparison
  case Ambiguous
  case NonBeauty

  def render: String =
    this match {
      case M10BeautyQSearchQuerySignal.Provider     => "provider"
      case M10BeautyQSearchQuerySignal.Service      => "service"
      case M10BeautyQSearchQuerySignal.Attribute    => "attribute"
      case M10BeautyQSearchQuerySignal.Location     => "location"
      case M10BeautyQSearchQuerySignal.Price        => "price"
      case M10BeautyQSearchQuerySignal.Availability => "availability"
      case M10BeautyQSearchQuerySignal.Comparison   => "comparison"
      case M10BeautyQSearchQuerySignal.Ambiguous    => "ambiguous"
      case M10BeautyQSearchQuerySignal.NonBeauty    => "non_beauty"
    }
}

object M10BeautyQSearchQuerySignal {
  val stableOrder: List[M10BeautyQSearchQuerySignal] = List(
    Provider,
    Service,
    Attribute,
    Location,
    Price,
    Availability,
    Comparison,
    Ambiguous,
    NonBeauty,
  )

  /** Single-dimension intent signals (noise markers excluded). */
  val intentDimensions: List[M10BeautyQSearchQuerySignal] = List(
    Provider,
    Service,
    Attribute,
    Location,
    Price,
    Availability,
    Comparison,
  )
}

final case class M10BeautyQSearchQueryClassificationInput(
  queryId: String,
  rawQueryText: String,
  signals: List[M10BeautyQSearchQuerySignal],
  /** Explicit offline marker that this row is an accepted negative control: a noisy/ambiguous anchor
    * deliberately excluded from backend-candidate study, not an unresolved manual-review row.
    */
  acceptedNegativeControl: Boolean = false,
)

final case class M10BeautyQSearchQueryClassificationResult(
  queryId: String,
  rawQueryText: String,
  category: M10BeautyQSearchQueryCategory,
  intentSignals: List[M10BeautyQSearchQuerySignal],
  /** True only for unresolved noise rows that still require future manual resolution. Accepted
    * negative controls set this to false.
    */
  manualReviewEligible: Boolean,
  /** True for noise rows explicitly accepted as negative-control exclusions. */
  acceptedNegativeControl: Boolean,
  isNoise: Boolean,
  rationale: String,
)

object M10BeautyQSearchQueryClassification {

  /** The three accepted M9 representative anchors carried forward as classification anchors. */
  val AnchorQueryIds: List[String] =
    M9BeautyQSearchEvalQueryDatasetStaticRows.RepresentativeQueryIds

  /** Pure, total, deterministic classification of a single offline query input. */
  def classify(input: M10BeautyQSearchQueryClassificationInput): M10BeautyQSearchQueryClassificationResult = {
    val signals = dedup(input.signals)
    val intent = signals.filter(M10BeautyQSearchQuerySignal.intentDimensions.contains)
    val nonBeauty = signals.contains(M10BeautyQSearchQuerySignal.NonBeauty) || signals.isEmpty
    val ambiguous = signals.contains(M10BeautyQSearchQuerySignal.Ambiguous)

    val category =
      if (nonBeauty || ambiguous) M10BeautyQSearchQueryCategory.NoisyAmbiguousNonBeautyIntent
      else
        intent match {
          case Nil          => M10BeautyQSearchQueryCategory.NoisyAmbiguousNonBeautyIntent
          case single :: Nil => singleDimensionCategory(single)
          case _            => M10BeautyQSearchQueryCategory.MixedIntent
        }

    val isNoise = category == M10BeautyQSearchQueryCategory.NoisyAmbiguousNonBeautyIntent
    // An accepted negative control is a noise row deliberately excluded from study; it is never an
    // unresolved manual-review row. Otherwise ambiguous-but-beauty noise is held for manual review,
    // and pure non-beauty/empty signal is no-op noise.
    val acceptedNegativeControl = isNoise && input.acceptedNegativeControl
    val manualReviewEligible = isNoise && ambiguous && !nonBeauty && !acceptedNegativeControl

    M10BeautyQSearchQueryClassificationResult(
      queryId = input.queryId,
      rawQueryText = input.rawQueryText,
      category = category,
      intentSignals = intent,
      manualReviewEligible = manualReviewEligible,
      acceptedNegativeControl = acceptedNegativeControl,
      isNoise = isNoise,
      rationale = rationale(category, intent, nonBeauty),
    )
  }

  private def singleDimensionCategory(signal: M10BeautyQSearchQuerySignal): M10BeautyQSearchQueryCategory =
    signal match {
      case M10BeautyQSearchQuerySignal.Provider     => M10BeautyQSearchQueryCategory.ProviderLookup
      case M10BeautyQSearchQuerySignal.Service      => M10BeautyQSearchQueryCategory.ServiceIntent
      case M10BeautyQSearchQuerySignal.Attribute    => M10BeautyQSearchQueryCategory.AttributeFilterIntent
      case M10BeautyQSearchQuerySignal.Location     => M10BeautyQSearchQueryCategory.LocationIntent
      case M10BeautyQSearchQuerySignal.Price        => M10BeautyQSearchQueryCategory.PriceBudgetIntent
      case M10BeautyQSearchQuerySignal.Availability => M10BeautyQSearchQueryCategory.AvailabilityTimeIntent
      case M10BeautyQSearchQuerySignal.Comparison   => M10BeautyQSearchQueryCategory.ComparisonExplorationIntent
      // Noise markers never reach single-dimension routing; fold to noise defensively.
      case M10BeautyQSearchQuerySignal.Ambiguous | M10BeautyQSearchQuerySignal.NonBeauty =>
        M10BeautyQSearchQueryCategory.NoisyAmbiguousNonBeautyIntent
    }

  private def rationale(
    category: M10BeautyQSearchQueryCategory,
    intent: List[M10BeautyQSearchQuerySignal],
    nonBeauty: Boolean,
  ): String =
    category match {
      case M10BeautyQSearchQueryCategory.NoisyAmbiguousNonBeautyIntent if nonBeauty =>
        "Offline classification: non-beauty or empty signal; held as no-op noise."
      case M10BeautyQSearchQueryCategory.NoisyAmbiguousNonBeautyIntent =>
        "Offline classification: ambiguous beauty signal; held for offline manual review."
      case M10BeautyQSearchQueryCategory.MixedIntent =>
        s"Offline classification: multiple intent dimensions (${intent.map(_.render).mkString(", ")})."
      case other =>
        s"Offline classification: single intent dimension maps to ${other.render}."
    }

  private def dedup(signals: List[M10BeautyQSearchQuerySignal]): List[M10BeautyQSearchQuerySignal] =
    signals.foldLeft(List.empty[M10BeautyQSearchQuerySignal]) {
      case (seen, value) if seen.contains(value) => seen
      case (seen, value)                         => seen :+ value
    }

  /** Representative offline classification inputs: the three accepted anchors plus enough additional
    * cases to cover every category. Deterministic and focused; this does not remap the full 63-query
    * dataset.
    */
  val RepresentativeInputs: List[M10BeautyQSearchQueryClassificationInput] =
    List(
      // Accepted M9 anchors.
      input("q_nails_001", "маникюр гель лак", List(M10BeautyQSearchQuerySignal.Service, M10BeautyQSearchQuerySignal.Attribute)),
      input("q_nails_003", "shellac entfernen und neu", List(M10BeautyQSearchQuerySignal.Attribute)),
      input("q_noise_005", "lifting", List(M10BeautyQSearchQuerySignal.Ambiguous)),
      // Additional cases covering the remaining categories.
      input("q_provider_001", "записаться к моему мастеру снова", List(M10BeautyQSearchQuerySignal.Provider)),
      input("q_service_001", "маникюр", List(M10BeautyQSearchQuerySignal.Service)),
      input("q_location_001", "салон рядом со мной", List(M10BeautyQSearchQuerySignal.Location)),
      input("q_price_001", "маникюр до 50 евро", List(M10BeautyQSearchQuerySignal.Service, M10BeautyQSearchQuerySignal.Price)),
      input("q_price_002", "до 30 евро", List(M10BeautyQSearchQuerySignal.Price)),
      input("q_time_001", "сегодня вечером есть свободное время", List(M10BeautyQSearchQuerySignal.Availability)),
      input("q_explore_001", "что можно попробовать для бровей", List(M10BeautyQSearchQuerySignal.Comparison)),
      input("q_nonbeauty_001", "прогноз погоды на завтра", List(M10BeautyQSearchQuerySignal.NonBeauty)),
      input("q_mixed_001", "маникюр рядом недорого сегодня", List(
        M10BeautyQSearchQuerySignal.Service,
        M10BeautyQSearchQuerySignal.Location,
        M10BeautyQSearchQuerySignal.Price,
        M10BeautyQSearchQuerySignal.Availability,
      )),
    )

  val RepresentativeResults: List[M10BeautyQSearchQueryClassificationResult] =
    RepresentativeInputs.map(classify)

  def resultFor(queryId: String): Option[M10BeautyQSearchQueryClassificationResult] =
    RepresentativeResults.find(_.queryId == queryId)

  private def input(
    queryId: String,
    rawQueryText: String,
    signals: List[M10BeautyQSearchQuerySignal],
  ): M10BeautyQSearchQueryClassificationInput =
    M10BeautyQSearchQueryClassificationInput(queryId, rawQueryText, signals)
}
