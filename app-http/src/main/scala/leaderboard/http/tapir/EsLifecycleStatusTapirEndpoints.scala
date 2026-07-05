package leaderboard.http.tapir

import leaderboard.http.HttpApiFailure
import leaderboard.search.elasticsearch.ElasticsearchStartupReadinessStatusResponse
import sttp.tapir.*
import sttp.tapir.json.circe.*

trait EsLifecycleStatusTapirEndpoints {
  def lifecycleStatus: PublicEndpoint[Unit, HttpApiFailure, ElasticsearchStartupReadinessStatusResponse, Any]

  final def all: List[AnyEndpoint] = List(lifecycleStatus)
}

object EsLifecycleStatusTapirEndpoints extends EsLifecycleStatusTapirEndpoints {
  given Schema[ElasticsearchStartupReadinessStatusResponse] = Schema.any[ElasticsearchStartupReadinessStatusResponse]

  val lifecycleStatus: PublicEndpoint[Unit, HttpApiFailure, ElasticsearchStartupReadinessStatusResponse, Any] =
    HttpApiFailureTapirSupport.endpointBase.in("ops" / "beauty-search" / "lifecycle").get
      .out(jsonBody[ElasticsearchStartupReadinessStatusResponse])
}
