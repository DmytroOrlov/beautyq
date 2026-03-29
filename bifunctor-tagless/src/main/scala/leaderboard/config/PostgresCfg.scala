package leaderboard.config

case class PostgresCfg(
  jdbcDriver: String,
  url: String,
  user: String,
  password: String,
)
