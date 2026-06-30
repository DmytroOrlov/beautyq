package leaderboard.search.elasticsearch

import leaderboard.model.QueryFailure
import leaderboard.search.document.BeautySearchReadyCatalogDocuments
import leaderboard.search.dsl.BeautySearchSpec
import zio.{IO, ZIO}

final case class ElasticsearchSeedIndexReadiness(
  indexName: String,
  source: String,
  documentCount: Int,
) {
  def lifecycleMetadata: ElasticsearchSeedLifecycleMetadata =
    ElasticsearchSeedLifecycleMetadata(
      indexName = indexName,
      source = source,
      documentCount = documentCount,
      preparationMode = ElasticsearchSeedPreparationMode.EagerSeedIndexPreparation,
      lifecycleStatus = ElasticsearchSeedLifecycleStatus.SeedOnlyNotProductionLifecycle,
    )
}

final case class ElasticsearchSeedLifecycleMetadata(
  indexName: String,
  source: String,
  documentCount: Int,
  preparationMode: ElasticsearchSeedPreparationMode,
  lifecycleStatus: ElasticsearchSeedLifecycleStatus,
)

sealed trait ElasticsearchSeedPreparationMode

object ElasticsearchSeedPreparationMode {
  case object EagerSeedIndexPreparation extends ElasticsearchSeedPreparationMode
}

sealed trait ElasticsearchSeedLifecycleStatus

object ElasticsearchSeedLifecycleStatus {
  case object SeedOnlyNotProductionLifecycle extends ElasticsearchSeedLifecycleStatus
}

final class ElasticsearchSeedIndexInitializer(
  spec: BeautySearchSpec,
  client: ElasticsearchJsonClient,
) {
  def prepare(
    ready: BeautySearchReadyCatalogDocuments,
  ): IO[QueryFailure, ElasticsearchSeedIndexReadiness] =
    for {
      _ <- if (ready.source.trim.isEmpty) {
        ZIO.fail(ElasticsearchSeedIndexInitializer.failure("seed index readiness source is empty"))
      } else {
        ZIO.unit
      }
      _ <- if (ready.documents.isEmpty) {
        ZIO.fail(ElasticsearchSeedIndexInitializer.failure("seed index readiness documents are empty"))
      } else {
        ZIO.unit
      }
      indexName = spec.variantDocument.indexName
      mapping = ElasticsearchMappingInterpreter.mapping(spec.variantDocument)
      _ <- client.putJson(s"/$indexName", mapping)
      payload = ElasticsearchIngestionInterpreter.bulkPayload(spec.variantDocument, ready.documents)
      _ <- client.postNdjson(s"/$indexName/_bulk", payload)
      _ <- client.post(s"/$indexName/_refresh")
    } yield ElasticsearchSeedIndexReadiness(
      indexName = indexName,
      source = ready.source,
      documentCount = ready.documents.size,
    )
}

object ElasticsearchSeedIndexInitializer {
  val OperationName: String = "elasticsearch-seed-index-readiness"

  def failure(message: String): QueryFailure =
    QueryFailure.operation(OperationName, message)
}
