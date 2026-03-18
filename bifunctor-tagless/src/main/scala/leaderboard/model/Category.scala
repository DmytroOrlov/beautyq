package leaderboard.model

case class Category(id: CategoryId, parentId: CategoryId, depth: Int, name: String)
