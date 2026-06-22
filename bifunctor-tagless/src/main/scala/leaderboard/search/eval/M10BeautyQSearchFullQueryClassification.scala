package leaderboard.search.eval

/** M10 full 63-query offline classification mapping over the accepted BeautyQ eval dataset.
  *
  * This expands the representative M10 classification foundation to the complete accepted eval dataset:
  * every query id in [[M9BeautyQSearchEvalQueryDatasetStaticRows.StaticQueryIds]] gets exactly one
  * classification row, derived offline from deterministic explicit signals. The three accepted M9 anchors
  * keep their accepted classifications. The remaining 60 static/placeholder rows are classified using the
  * same total, pure [[M10BeautyQSearchQueryClassification.classify]] function over hand-derived offline
  * signals.
  *
  * This is offline planning/eval work only. It never calls production `/beauty-search`, never creates an
  * ES or Qdrant client, never runs Elasticsearch or Qdrant, and never touches a route, plugin, DI, or
  * HTTP source. No JSON parser/dependency is introduced: signals are a checked-in deterministic table
  * derived offline from the read-only query dataset fixture. It makes no production/hybrid/fallback/
  * fusion/reranking/telemetry/route-switch/quality/readiness/activation/serving claim. Default
  * `/beauty-search` remains ES-backed and Qdrant production activation remains not approved.
  */
object M10BeautyQSearchFullQueryClassification {

  /** All accepted eval dataset query ids, in the dataset's stable order. */
  val DatasetQueryIds: List[String] =
    M9BeautyQSearchEvalQueryDatasetStaticRows.StaticQueryIds

  /** The three accepted M9 representative anchors carried forward unchanged. */
  val AnchorQueryIds: List[String] =
    M10BeautyQSearchQueryClassification.AnchorQueryIds

  /** Full offline classification inputs: one per accepted dataset query id, in dataset order.
    *
    * Signals are deterministic offline planning inputs derived from the read-only query dataset fixture
    * (`beautyq_search_eval_queries_v1.json`); they are not backend features, telemetry, or retrieval
    * results. The three anchors reuse the accepted anchor signals verbatim.
    */
  val FullInputs: List[M10BeautyQSearchQueryClassificationInput] = {
    import M10BeautyQSearchQuerySignal.*
    List(
      // Nails.
      in("q_nails_001", "маникюр гель лак", Service, Attribute),                  // accepted anchor: mixed
      in("q_nails_002", "маникюр с shellac и снятием", Service, Attribute),
      in("q_nails_003", "shellac entfernen und neu", Attribute),                  // accepted anchor: attribute/filter
      in("q_nails_004", "обычный маникюр без покрытия", Service, Attribute),
      in("q_nails_005", "дешевый маникюр рядом", Service, Price, Location),
      in("q_nails_006", "педикюр без лака", Service, Attribute),
      in("q_nails_007", "pedicure gel polish", Service, Attribute),
      in("q_nails_008", "Fußpflege Shellac Wandsbek", Service, Attribute, Location),
      in("q_nails_009", "наращивание ногтей гель", Service, Attribute),
      in("q_nails_010", "коррекция гелевых ногтей с дизайном", Attribute),
      in("q_nails_011", "снять гель с ногтей", Service, Attribute),
      in("q_nails_012", "acrylic nails Hamburg Wandsbek", Service, Attribute, Location),
      // Lashes.
      in("q_lashes_001", "ресницы классика", Service, Attribute),
      in("q_lashes_002", "ресницы 2д", Service, Attribute),
      in("q_lashes_003", "Wimpern 3D", Service, Attribute),
      in("q_lashes_004", "mega volume lashes", Service, Attribute),
      in("q_lashes_005", "коррекция ресниц 2д", Attribute),
      in("q_lashes_006", "lash lifting mit färben", Service, Attribute),
      in("q_lashes_007", "снять ресницы", Service, Attribute),
      in("q_lashes_008", "volume2_d Wandsbek lashes", Service, Attribute, Location),
      // Brows.
      in("q_brows_001", "брови ламинирование с окрашиванием", Service, Attribute),
      in("q_brows_002", "brow lamination Wandsbek", Service, Location),
      in("q_brows_003", "коррекция бровей", Service),
      in("q_brows_004", "Augenbrauen färben", Service, Attribute),
      in("q_brows_005", "брови хна", Service, Attribute),
      in("q_brows_006", "shape and tint brows", Service, Attribute),
      // Hair removal.
      in("q_hair_001", "депиляция верхняя губа воском", Service, Attribute),
      in("q_hair_002", "wax upper lip", Service, Attribute),
      in("q_hair_003", "threading chin", Service, Attribute),
      in("q_hair_004", "laser hair removal armpits", Service, Attribute),
      in("q_hair_005", "лазер подмышки 6 сеансов", Attribute),
      in("q_hair_006", "bikini laser", Service, Attribute),
      in("q_hair_007", "sugaring lower legs", Service, Attribute),
      in("q_hair_008", "sugaring upper lip рядом", Service, Attribute, Location),
      in("q_hair_009", "IPL Beine", Service, Attribute),
      in("q_hair_010", "бикини воск недорого", Service, Attribute, Price),
      // PMU.
      in("q_pmu_001", "перманент губы", Service, Attribute),
      in("q_pmu_002", "pmu lips Hamburg", Service, Attribute, Location),
      in("q_pmu_003", "permanent makeup brows correction included", Service, Attribute),
      in("q_pmu_004", "powder brows", Service),
      in("q_pmu_005", "eyeliner pmu", Service, Attribute),
      in("q_pmu_006", "татуаж губ коррекция", Service, Attribute),
      // Face.
      in("q_face_001", "aquafacial", Service),
      in("q_face_002", "microneedling face", Service, Attribute),
      in("q_face_003", "чистка лица", Service),
      in("q_face_004", "bb glow", Service),
      in("q_face_005", "anti aging facial face neck decollete", Attribute),
      in("q_face_006", "peeling Gesicht", Service),
      in("q_face_007", "увлажнение лица 3 сеанса скидка", Service, Attribute, Price),
      in("q_face_008", "классический уход лицо", Service),
      // Home visit.
      in("q_home_001", "выездной уход для двоих", Service, Attribute),
      in("q_home_002", "beauty treatment at home small group", Comparison),
      // Broad / exploration.
      in("q_broad_001", "салон красоты wandsbek ногти", Service, Location, Comparison),
      in("q_broad_002", "lashes and brows Wandsbek", Service, Location, Comparison),
      in("q_broad_003", "что-то для лица рядом", Comparison, Location),
      in("q_broad_004", "хочу привести себя в порядок рядом", Comparison, Location),
      in("q_broad_005", "недорогие ногти рядом", Service, Price, Location),
      in("q_broad_006", "beauty near Wandsbek Markt", Location),
      // Noise / hard negatives.
      in("q_noise_001", "манекюр шелак", Service, Attribute),
      in("q_noise_002", "реснички 2д корр", Service, Attribute),
      in("q_noise_003", "депиляция губа не татуаж", Service, Attribute),
      // gel removal = beauty service (gel) + attribute (removal); a beauty-domain candidate study input,
      // not noise, despite the q_noise_* id prefix. Raw offline signals decide the classification.
      in("q_noise_004", "gel removal", Service, Attribute),
      // lifting = accepted noisy/ambiguous anchor, kept as an accepted negative-control exclusion
      // rather than an unresolved manual-review row.
      negativeControl("q_noise_005", "lifting", Ambiguous),                        // accepted negative control
    )
  }

  /** Full classification results, in dataset order. */
  val FullResults: List[M10BeautyQSearchQueryClassificationResult] =
    FullInputs.map(M10BeautyQSearchQueryClassification.classify)

  /** Full offline routing decisions, in dataset order. */
  val FullDecisions: List[M10BeautyQSearchOfflineRoutingDecision] =
    FullResults.map(M10BeautyQSearchOfflineRoutingPolicy.decide)

  def resultFor(queryId: String): Option[M10BeautyQSearchQueryClassificationResult] =
    FullResults.find(_.queryId == queryId)

  def decisionFor(queryId: String): Option[M10BeautyQSearchOfflineRoutingDecision] =
    FullDecisions.find(_.queryId == queryId)

  /** Category counts in the stable category order; categories with no rows report zero explicitly. */
  val CategoryCounts: List[(M10BeautyQSearchQueryCategory, Int)] =
    M10BeautyQSearchQueryCategory.stableOrder.map { category =>
      category -> FullResults.count(_.category == category)
    }

  /** Offline strategy intent counts in the full-coverage intent order; intents with no rows report
    * zero. The full 63-query dataset also exercises the accepted negative-control exclusion intent.
    */
  val StrategyIntentCounts: List[(M10BeautyQSearchOfflineRetrievalStrategyIntent, Int)] =
    M10BeautyQSearchOfflineRetrievalStrategyIntent.fullCoverageStableOrder.map { intent =>
      intent -> FullDecisions.count(_.strategyIntent == intent)
    }

  /** Query ids whose offline decision is an accepted negative-control exclusion, in dataset order. */
  val AcceptedNegativeControlQueryIds: List[String] =
    FullDecisions
      .filter(_.strategyIntent == M10BeautyQSearchOfflineRetrievalStrategyIntent.AcceptedNegativeControlExcluded)
      .map(_.queryId)

  /** Query ids whose offline decision is an unresolved manual-review row, in dataset order. */
  val UnresolvedManualReviewQueryIds: List[String] =
    FullDecisions
      .filter(_.strategyIntent.isUnresolvedManualReview)
      .map(_.queryId)

  private def in(
    queryId: String,
    rawQueryText: String,
    signals: M10BeautyQSearchQuerySignal*
  ): M10BeautyQSearchQueryClassificationInput =
    M10BeautyQSearchQueryClassificationInput(queryId, rawQueryText, signals.toList)

  private def negativeControl(
    queryId: String,
    rawQueryText: String,
    signals: M10BeautyQSearchQuerySignal*
  ): M10BeautyQSearchQueryClassificationInput =
    M10BeautyQSearchQueryClassificationInput(
      queryId,
      rawQueryText,
      signals.toList,
      acceptedNegativeControl = true,
    )
}
