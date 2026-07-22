package leaderboard.config

/** Canonical endpoint view for the shared Distage-managed Qdrant 1.18.3 process. */
final case class QdrantGen2PortCfg(
  host: String,
  port: Int,
)
