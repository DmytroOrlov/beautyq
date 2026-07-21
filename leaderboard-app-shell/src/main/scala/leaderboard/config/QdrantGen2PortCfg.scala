package leaderboard.config

/** Dynamically allocated endpoint for the separately managed Gen2 Qdrant resource. */
final case class QdrantGen2PortCfg(
  host: String,
  port: Int,
)
