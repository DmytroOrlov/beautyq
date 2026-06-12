package leaderboard.plugins

import distage.ModuleDef
import leaderboard.config.ElasticsearchPortCfg
import leaderboard.search.elasticsearch.{ElasticsearchHttpJsonClient, ElasticsearchJsonClient}

object ElasticsearchClientModules {
  def portConfigured: ModuleDef = new ModuleDef {
    make[ElasticsearchJsonClient].from {
      (cfg: ElasticsearchPortCfg) =>
        new ElasticsearchHttpJsonClient(cfg.host, cfg.port)
    }
  }
}
