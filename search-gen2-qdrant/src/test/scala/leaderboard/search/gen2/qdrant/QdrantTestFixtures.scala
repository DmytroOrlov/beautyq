package leaderboard.search.gen2.qdrant

import java.time.Instant
import java.util.UUID
import leaderboard.search.gen2.contract.*
import leaderboard.search.gen2.core.materialization.*

object QdrantTestFixtures {
  final case class NeutralDocument(
    id: UUID,
    title: String,
    count: Int,
    from: BigDecimal,
    to: BigDecimal,
    active: Boolean,
    at: Instant,
    location: GeoPoint,
  )

  private val declarations = searchFields[NeutralDocument]("neutral")

  val id       = declarations.keyword(_.id).payloadEligible.declare
  val title    = declarations.text(_.title).searchable.declare
  val count    = declarations.integer(_.count).filterable(FilterOperator.Range).payloadEligible.declare
  val from     = declarations.decimal(_.from).filterable(FilterOperator.Range).payloadEligible.declare
  val to       = declarations.decimal(_.to).filterable(FilterOperator.Range).payloadEligible.declare
  val active   = declarations.boolean(_.active).filterable(FilterOperator.Equal, FilterOperator.In).payloadEligible.declare
  val at       = declarations.dateTime(_.at).filterable(FilterOperator.Range).payloadEligible.declare
  val location = declarations.geoPoint(_.location).filterable(FilterOperator.GeoDistance).payloadEligible.declare

  val document = declarations.document(id)

  val model = QdrantEmbeddingModelIdentity("test-provider", "test-model", "v1", 3, "neutral-text-v1")
  val policy = QdrantPolicy.unsafeFrom(
    PlanContractVersion("neutral-v1"),
    document,
    id,
    title,
    QdrantVectorName.unsafeFrom("neutral-vector"),
    model,
    QdrantDistance.Cosine,
    QdrantRetrievalPolicy(2, 2, Some(0.4)),
  )

  val first = NeutralDocument(UUID.fromString("00000000-0000-0000-0000-000000000001"), "alpha", 2, BigDecimal("10"), BigDecimal("20"), true, Instant.parse("2025-01-01T00:00:00Z"), GeoPoint(BigDecimal("52.5"), BigDecimal("13.4")))
  val second = first.copy(id = UUID.fromString("00000000-0000-0000-0000-000000000002"), title = "beta", count = 3)

  val materialized = MaterializedSearchDocuments(
    VersionedSnapshot(Vector(first, second), ContentFingerprint("source"), None, Instant.parse("2025-01-01T00:00:00Z")),
    Vector(second, first),
    ProjectedDocumentsFingerprint("projected"),
    ProjectionFormatVersion("projection-v1"),
  )
}
