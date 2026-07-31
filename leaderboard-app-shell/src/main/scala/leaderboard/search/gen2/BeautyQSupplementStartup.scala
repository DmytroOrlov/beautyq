package leaderboard.search.gen2

import distage.Axis

object BeautyQSupplementStartup extends Axis {
  case object Required extends AxisChoiceDef
  case object Preferred extends AxisChoiceDef
  case object Disabled extends AxisChoiceDef
}
