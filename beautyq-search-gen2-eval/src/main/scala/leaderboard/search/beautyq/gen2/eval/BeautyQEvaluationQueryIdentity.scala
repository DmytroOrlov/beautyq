package leaderboard.search.beautyq.gen2.eval

import java.text.Normalizer
import java.util.Locale

object BeautyQEvaluationQueryIdentity {
  def normalize(value: String): String =
    Normalizer.normalize(value, Normalizer.Form.NFKC)
      .toLowerCase(Locale.ROOT)
      .trim
      .replaceAll("\\s+", " ")
}
