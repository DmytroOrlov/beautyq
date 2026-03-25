package leaderboard.model

import leaderboard.model.Category.CategoryId

case class Service(id: ServiceId, categoryId: CategoryId, name: String)
