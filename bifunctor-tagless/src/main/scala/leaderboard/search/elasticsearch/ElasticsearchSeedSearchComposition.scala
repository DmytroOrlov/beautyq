package leaderboard.search.elasticsearch

import leaderboard.model.QueryFailure
import leaderboard.search.{BeautySearchBackend, BeautySearchService}
import leaderboard.search.document.BeautySearchReadyCatalogDocuments
import leaderboard.search.dsl.BeautySearchSpec
import leaderboard.search.parser.BeautySearchIntentParser
import zio.IO

final case class ElasticsearchSeedSearchComposition(
  readiness: ElasticsearchSeedIndexReadiness,
  backend: BeautySearchBackend[IO],
  service: BeautySearchService[IO],
) {
  def lifecycleMetadata: ElasticsearchSeedLifecycleMetadata = readiness.lifecycleMetadata
}

object ElasticsearchSeedSearchComposition {
  def build(
    spec: BeautySearchSpec,
    client: ElasticsearchJsonClient,
    ready: BeautySearchReadyCatalogDocuments,
  ): IO[QueryFailure, ElasticsearchSeedSearchComposition] = {
    val initializer = new ElasticsearchSeedIndexInitializer(spec, client)
    for {
      readiness <- initializer.prepare(ready)
    } yield {
      val backend = new ElasticsearchSearchBackend(spec, client)
      val parser = new BeautySearchIntentParser(spec)
      val service = new BeautySearchService.Impl[IO](parser, backend)
      ElasticsearchSeedSearchComposition(readiness, backend, service)
    }
  }
}
