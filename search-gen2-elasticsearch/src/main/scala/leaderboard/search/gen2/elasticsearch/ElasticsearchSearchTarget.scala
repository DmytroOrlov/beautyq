package leaderboard.search.gen2.elasticsearch

/** The physical Elasticsearch index name selected by the lifecycle owner for an authorized request.
  * It is runtime/lifecycle identity, never business policy and never part of `PlanIdentity`. Brick 5C owns
  * resolving an active generation and retaining pinned generations; the pure 5B compiler carries only an
  * untrusted generation reference until that lifecycle boundary authorizes this value. */
final case class ElasticsearchSearchTarget(value: String)
