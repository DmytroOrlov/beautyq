package leaderboard.search.eval

import izumi.functional.bio.{Error2, F, Functor2}
import leaderboard.model.QueryFailure
import leaderboard.search.lexical.{LexicalDocumentBackend, LexicalDocumentHit}
import leaderboard.search.semantic.{SemanticDocumentBackend, SemanticDocumentHit, SemanticDocumentLookup}
import leaderboard.search.{ParsedSearchIntent, UserSearchInput}

/**
 * M18A: offline/eval dual-engine execution surface.
 *
 * Runs ES-native lexical retrieval and Qdrant-native semantic retrieval over the same accepted eval
 * queries and emits separated, comparable candidate evidence rows per backend.
 *
 * Boundaries (deliberately enforced by this surface):
 *   - ES and Qdrant candidate outputs are kept structurally separate; there is no merged candidate
 *     list and no common accessor that collapses backend attribution.
 *   - Backend-native score and ES matched-field detail are preserved per candidate row.
 *   - No fusion, no reranking, no fallback, no production route, no serving decision is performed.
 *   - The Qdrant leg only executes when its prerequisites are satisfied; otherwise it emits an honest
 *     resource-gated result listing the exact missing prerequisites and zero candidates.
 *
 * This surface calls the existing ES-native ([[LexicalDocumentBackend]]) and Qdrant-native
 * ([[SemanticDocumentBackend]]) retrieval seams directly. It never calls the production
 * `/beauty-search` route.
 */

/** Which offline-eval backend leg a row/leg belongs to. Kept distinct from [[CandidateSource]] so
  * this surface owns its own honest attribution that cannot be confused with serving sources. */
enum M18OfflineEvalBackend {
  case Es
  case Qdrant

  def render: String =
    this match {
      case M18OfflineEvalBackend.Es     => "es"
      case M18OfflineEvalBackend.Qdrant => "qdrant"
    }

  def candidateSource: CandidateSource =
    this match {
      case M18OfflineEvalBackend.Es     => CandidateSource.Es
      case M18OfflineEvalBackend.Qdrant => CandidateSource.Qdrant
    }

  def servingMode: ServingMode =
    this match {
      case M18OfflineEvalBackend.Es     => ServingMode.EsOnly
      case M18OfflineEvalBackend.Qdrant => ServingMode.QdrantOnly
    }
}

object M18OfflineEvalBackend {
  val stableOrder: List[M18OfflineEvalBackend] = List(Es, Qdrant)
}

/** Honest tri-state expected/matched flag. Only populated from existing dataset expectations. */
enum M18ExpectedMatch {
  case Matched
  case NotExpected
  case ExpectationsUnavailable

  def render: String =
    this match {
      case M18ExpectedMatch.Matched                 => "matched"
      case M18ExpectedMatch.NotExpected             => "not_expected"
      case M18ExpectedMatch.ExpectationsUnavailable => "expectations_unavailable"
    }
}

/** Honest tri-state missing-lookup flag. Only populated from actual lookup/hydration evidence. */
enum M18MissingLookup {
  case Hydrated
  case MissingLookup
  case LookupNotEvaluated

  def render: String =
    this match {
      case M18MissingLookup.Hydrated           => "hydrated"
      case M18MissingLookup.MissingLookup       => "missing_lookup"
      case M18MissingLookup.LookupNotEvaluated => "lookup_not_evaluated"
    }
}

/** One comparable evidence row for a single backend candidate. Carries backend-native score and, for
  * ES, native matched-field detail. This is evidence only and does not imply fusion. */
final case class M18BackendCandidateRow(
  queryId: String,
  backend: M18OfflineEvalBackend,
  candidateId: String,
  rank: Int,
  backendScore: Option[BigDecimal],
  matchedFields: List[String],
  expectedMatch: M18ExpectedMatch,
  missingLookup: M18MissingLookup,
)

/** Per-query result for a single backend leg. */
final case class M18BackendQueryLegResult(
  queryId: String,
  backend: M18OfflineEvalBackend,
  candidates: List[M18BackendCandidateRow],
  latencyNanos: Option[Long],
  failure: Option[String],
) {
  def executed: Boolean = failure.isEmpty
}

/** Why a backend leg did not execute. */
enum M18LegSkipKind {
  case Blocked
  case ResourceGated

  def render: String =
    this match {
      case M18LegSkipKind.Blocked       => "blocked"
      case M18LegSkipKind.ResourceGated => "resource_gated"
    }
}

/** Outcome of a backend leg: either it executed (with per-query rows) or it was skipped/resource-gated
  * with an exact reason and no candidates. */
sealed trait M18BackendLegOutcome {
  def backend: M18OfflineEvalBackend
  def executed: Boolean
  def candidateRows: List[M18BackendCandidateRow]
}

object M18BackendLegOutcome {
  final case class Executed(
    backend: M18OfflineEvalBackend,
    queryResults: List[M18BackendQueryLegResult],
  ) extends M18BackendLegOutcome {
    override def executed: Boolean = true

    override def candidateRows: List[M18BackendCandidateRow] =
      queryResults.flatMap(_.candidates)

    def failedQueryResults: List[M18BackendQueryLegResult] =
      queryResults.filter(_.failure.nonEmpty)

    def measuredLatencies: List[Long] =
      queryResults.flatMap(_.latencyNanos)
  }

  final case class Skipped(
    backend: M18OfflineEvalBackend,
    skipKind: M18LegSkipKind,
    reason: String,
    missingPrerequisites: List[String],
  ) extends M18BackendLegOutcome {
    override def executed: Boolean = false

    override def candidateRows: List[M18BackendCandidateRow] = Nil
  }
}

/** Final dual-engine offline-eval result. ES and Qdrant outcomes are kept separate by construction.
  * There is intentionally no combined/fused candidate accessor. */
final case class M18DualEngineOfflineEvalResult(
  evalDatasetId: EvalDatasetId,
  catalogSnapshotId: CatalogSnapshotId,
  es: M18BackendLegOutcome,
  qdrant: M18BackendLegOutcome,
) {
  def esExecuted: Boolean = es.executed

  def qdrantExecuted: Boolean = qdrant.executed

  def esCandidateRows: List[M18BackendCandidateRow] = es.candidateRows

  def qdrantCandidateRows: List[M18BackendCandidateRow] = qdrant.candidateRows

  /** Structural separation invariant: every ES row is attributed to ES, every Qdrant row to Qdrant.
    * Returns the list of violations (empty means the separation holds). */
  def separationViolations: List[String] =
    esCandidateRows.collect {
      case row if row.backend != M18OfflineEvalBackend.Es =>
        s"es leg emitted row for query ${row.queryId} attributed to ${row.backend.render}"
    } ++ qdrantCandidateRows.collect {
      case row if row.backend != M18OfflineEvalBackend.Qdrant =>
        s"qdrant leg emitted row for query ${row.queryId} attributed to ${row.backend.render}"
    }
}

/** Adapter the runner uses to gather missing-lookup evidence without depending on a concrete document
  * type. Implementations wrap existing document lookups. */
trait M18CandidateLookup[F[_, _], Id] {
  def existing(ids: List[Id]): F[QueryFailure, Set[Id]]
}

object M18CandidateLookup {
  def fromSemanticDocumentLookup[F[+_, +_]: Functor2, Id, Doc](
    lookup: SemanticDocumentLookup[F, Id, Doc]
  ): M18CandidateLookup[F, Id] =
    new M18CandidateLookup[F, Id] {
      override def existing(ids: List[Id]): F[QueryFailure, Set[Id]] =
        F.map(lookup.lookup(ids))(_.keySet)
    }
}

/** Monotonic clock seam used to measure leg latency where it can be measured safely. Absence of a
  * clock means latency is explicitly unavailable (not faked). */
trait M18OfflineEvalLegClock[F[_, _]] {
  def monotonicNanos: F[Nothing, Long]
}

/** Builds the retrieval input/intent for a dataset query. */
trait M18EvalQueryInputBuilder {
  def build(query: M9OfflineEvalDatasetQuery): (UserSearchInput, ParsedSearchIntent)
}

object M18EvalQueryInputBuilder {
  val default: M18EvalQueryInputBuilder =
    new M18EvalQueryInputBuilder {
      override def build(query: M9OfflineEvalDatasetQuery): (UserSearchInput, ParsedSearchIntent) = {
        val text = query.normalizedQueryText.filter(_.trim.nonEmpty).getOrElse(query.rawQueryText)
        val tokens = text.split("\\s+").iterator.filter(_.nonEmpty).toList
        val input = UserSearchInput(query = query.rawQueryText, userLat = None, userLon = None, limit = 10)
        val intent = ParsedSearchIntent(
          originalQuery = query.rawQueryText,
          normalizedTokens = tokens,
          explicitConstraints = Nil,
          softBoosts = Nil,
          remainingText = text,
        )
        (input, intent)
      }
    }
}

/** ES offline-eval leg input. Either a connected ES-native lexical backend, or an explicit block. */
sealed trait M18EsLegInput[F[+_, +_], Id]

object M18EsLegInput {
  final case class Connected[F[+_, +_], Id](
    backend: LexicalDocumentBackend[F, Id],
    lookup: Option[M18CandidateLookup[F, Id]],
  ) extends M18EsLegInput[F, Id]

  final case class Blocked[F[+_, +_], Id](
    reason: String
  ) extends M18EsLegInput[F, Id]
}

/** Qdrant offline-eval leg input. Either a connected Qdrant-native semantic backend, or an honest
  * resource-gated result listing the exact missing prerequisites. */
sealed trait M18QdrantLegInput[F[+_, +_], Id]

object M18QdrantLegInput {
  final case class Connected[F[+_, +_], Id](
    backend: SemanticDocumentBackend[F, Id],
    lookup: Option[M18CandidateLookup[F, Id]],
  ) extends M18QdrantLegInput[F, Id]

  final case class ResourceGated[F[+_, +_], Id](
    reason: String,
    missingPrerequisites: List[String],
  ) extends M18QdrantLegInput[F, Id]

  /** Resolve a Qdrant leg input from concrete prerequisites: connect only when nothing is missing,
    * otherwise produce an honest resource-gated input with the exact missing prerequisites. */
  def fromPrerequisites[F[+_, +_], Id](
    prerequisites: M18QdrantLegPrerequisites
  )(connected: => M18QdrantLegInput.Connected[F, Id]): M18QdrantLegInput[F, Id] =
    M18QdrantLegResourceGate.missingPrerequisites(prerequisites) match {
      case Nil     => connected
      case missing => ResourceGated(M18QdrantLegResourceGate.reason(missing), missing)
    }
}

/** Concrete prerequisites required for the Qdrant offline-eval leg to execute. */
final case class M18QdrantLegPrerequisites(
  realBackendOfflineEvalEnabled: Boolean,
  embeddingClientConfigured: Boolean,
  qdrantClientConfigured: Boolean,
  collectionReadinessConfigured: Boolean,
)

object M18QdrantLegResourceGate {
  val EnablementEnvVar: String = M9OfflineEvalRealBackendResourceGate.EnablementEnvVar

  def fromEnv(
    env: Map[String, String],
    embeddingClientConfigured: Boolean,
    qdrantClientConfigured: Boolean,
    collectionReadinessConfigured: Boolean,
  ): M18QdrantLegPrerequisites =
    M18QdrantLegPrerequisites(
      realBackendOfflineEvalEnabled = env.get(EnablementEnvVar).exists(_.trim == "1"),
      embeddingClientConfigured = embeddingClientConfigured,
      qdrantClientConfigured = qdrantClientConfigured,
      collectionReadinessConfigured = collectionReadinessConfigured,
    )

  def missingPrerequisites(prerequisites: M18QdrantLegPrerequisites): List[String] =
    List(
      Option.when(!prerequisites.realBackendOfflineEvalEnabled)(
        s"$EnablementEnvVar=1 is required to execute the Qdrant offline eval leg"
      ),
      Option.when(!prerequisites.embeddingClientConfigured)(
        "embedding client (query vectorization) is not configured"
      ),
      Option.when(!prerequisites.qdrantClientConfigured)(
        "Qdrant search client (host/port) is not configured"
      ),
      Option.when(!prerequisites.collectionReadinessConfigured)(
        "Qdrant collection readiness/config (collection name + vector spec) is not available"
      ),
    ).flatten

  def reason(missing: List[String]): String =
    if (missing.isEmpty) "qdrant offline eval prerequisites satisfied"
    else s"qdrant offline eval leg resource-gated: missing ${missing.size} prerequisite(s): ${missing.mkString("; ")}"
}

/** Internal, backend-private hit carrier. It preserves backend-native score and ES matched fields; it
  * is not a public common interface and is never used to merge ES and Qdrant results. */
private[eval] final case class M18RawHit[Id](
  id: Id,
  score: Double,
  matchedFields: List[String],
)

/**
 * Effect-polymorphic dual-engine offline-eval runner.
 *
 * All backend failures are captured as data (a failed query-leg), so the run itself never fails:
 * the returned effect has error type `Nothing`.
 */
final class M18DualEngineOfflineEvalRunner[F[+_, +_], Id](
  inputBuilder: M18EvalQueryInputBuilder,
  renderId: Id => String,
  clock: Option[M18OfflineEvalLegClock[F]],
)(implicit F: Error2[F]) {

  def run(
    dataset: M9OfflineEvalDataset,
    esLeg: M18EsLegInput[F, Id],
    qdrantLeg: M18QdrantLegInput[F, Id],
  ): F[Nothing, M18DualEngineOfflineEvalResult] =
    F.flatMap(runEsLeg(dataset, esLeg)) { es =>
      F.map(runQdrantLeg(dataset, qdrantLeg)) { qdrant =>
        M18DualEngineOfflineEvalResult(
          evalDatasetId = dataset.evalDatasetId,
          catalogSnapshotId = dataset.catalogSnapshotId,
          es = es,
          qdrant = qdrant,
        )
      }
    }

  private def runEsLeg(
    dataset: M9OfflineEvalDataset,
    esLeg: M18EsLegInput[F, Id],
  ): F[Nothing, M18BackendLegOutcome] =
    esLeg match {
      case M18EsLegInput.Blocked(reason) =>
        F.pure(M18BackendLegOutcome.Skipped(M18OfflineEvalBackend.Es, M18LegSkipKind.Blocked, reason, Nil))
      case M18EsLegInput.Connected(backend, lookup) =>
        F.map(
          F.traverse(dataset.queries) { query =>
            runLegQuery(
              backend = M18OfflineEvalBackend.Es,
              query = query,
              lookup = lookup,
              fetch = (input, intent) =>
                F.map(backend.documentHits(input, intent))(_.map(hit => toRawHit(hit))),
            )
          }
        )(M18BackendLegOutcome.Executed(M18OfflineEvalBackend.Es, _))
    }

  private def runQdrantLeg(
    dataset: M9OfflineEvalDataset,
    qdrantLeg: M18QdrantLegInput[F, Id],
  ): F[Nothing, M18BackendLegOutcome] =
    qdrantLeg match {
      case M18QdrantLegInput.ResourceGated(reason, missing) =>
        F.pure(M18BackendLegOutcome.Skipped(M18OfflineEvalBackend.Qdrant, M18LegSkipKind.ResourceGated, reason, missing))
      case M18QdrantLegInput.Connected(backend, lookup) =>
        F.map(
          F.traverse(dataset.queries) { query =>
            runLegQuery(
              backend = M18OfflineEvalBackend.Qdrant,
              query = query,
              lookup = lookup,
              fetch = (input, intent) =>
                F.map(backend.documentHits(input, intent))(_.map(hit => toRawHit(hit))),
            )
          }
        )(M18BackendLegOutcome.Executed(M18OfflineEvalBackend.Qdrant, _))
    }

  private def runLegQuery(
    backend: M18OfflineEvalBackend,
    query: M9OfflineEvalDatasetQuery,
    lookup: Option[M18CandidateLookup[F, Id]],
    fetch: (UserSearchInput, ParsedSearchIntent) => F[QueryFailure, List[M18RawHit[Id]]],
  ): F[Nothing, M18BackendQueryLegResult] = {
    val (input, intent) = inputBuilder.build(query)
    F.flatMap(measure(fetch(input, intent))) { case (latency, attempt) =>
      attempt match {
        case Left(failure) =>
          F.pure(
            M18BackendQueryLegResult(
              queryId = query.queryId,
              backend = backend,
              candidates = Nil,
              latencyNanos = latency,
              failure = Some(renderFailure(failure)),
            )
          )
        case Right(hits) =>
          F.map(resolveLookup(lookup, hits.map(_.id))) { lookupFlag =>
            val expected = expectedIds(query)
            val rows = hits.zipWithIndex.map { case (hit, index) =>
              val candidateId = renderId(hit.id)
              M18BackendCandidateRow(
                queryId = query.queryId,
                backend = backend,
                candidateId = candidateId,
                rank = index + 1,
                backendScore = Some(BigDecimal.decimal(hit.score)),
                matchedFields = hit.matchedFields,
                expectedMatch = expectedMatch(expected, candidateId),
                missingLookup = lookupFlag(hit.id),
              )
            }
            M18BackendQueryLegResult(
              queryId = query.queryId,
              backend = backend,
              candidates = rows,
              latencyNanos = latency,
              failure = None,
            )
          }
      }
    }
  }

  private def measure[A](
    fa: F[QueryFailure, A]
  ): F[Nothing, (Option[Long], Either[QueryFailure, A])] =
    clock match {
      case None =>
        F.map(F.attempt(fa))(attempt => (None, attempt))
      case Some(legClock) =>
        F.flatMap(legClock.monotonicNanos) { start =>
          F.flatMap(F.attempt(fa)) { attempt =>
            F.map(legClock.monotonicNanos)(end => (Some(end - start), attempt))
          }
        }
    }

  private def resolveLookup(
    lookup: Option[M18CandidateLookup[F, Id]],
    ids: List[Id],
  ): F[Nothing, Id => M18MissingLookup] =
    lookup match {
      case None =>
        F.pure((_: Id) => M18MissingLookup.LookupNotEvaluated)
      case Some(candidateLookup) =>
        F.map(F.attempt(candidateLookup.existing(ids))) {
          case Right(present) =>
            (id: Id) => if (present.contains(id)) M18MissingLookup.Hydrated else M18MissingLookup.MissingLookup
          case Left(_) =>
            (_: Id) => M18MissingLookup.LookupNotEvaluated
        }
    }

  private def toRawHit(hit: LexicalDocumentHit[Id]): M18RawHit[Id] =
    M18RawHit(hit.documentId, hit.score, hit.matchedFields)

  private def toRawHit(hit: SemanticDocumentHit[Id]): M18RawHit[Id] =
    M18RawHit(hit.documentId, hit.score, Nil)

  private def expectedIds(query: M9OfflineEvalDatasetQuery): Set[String] =
    query.expectedResults.map(_.resultId).filter(_.trim.nonEmpty).toSet

  private def expectedMatch(expected: Set[String], candidateId: String): M18ExpectedMatch =
    if (expected.isEmpty) M18ExpectedMatch.ExpectationsUnavailable
    else if (expected.contains(candidateId)) M18ExpectedMatch.Matched
    else M18ExpectedMatch.NotExpected

  private def renderFailure(failure: QueryFailure): String =
    s"backend leg failed: ${failure.toString}"
}
