package leaderboard.repo

/** Physical SQL naming strategy for the model-first repository graph.
  *
  * A single strategy maps Scala model/type/field names to physical SQL
  * identifiers. There are intentionally no per-table or per-column overrides:
  * the public entity API never asks for raw SQL strings for normal fields or
  * tables.
  */
sealed trait RepoNamingStrategy {

  /** Physical source/table name for a model type name (e.g. `Category`). */
  def table(typeName: String): String

  /** Physical column name for a model field label (e.g. `parentId`). */
  def column(fieldLabel: String): String
}

object RepoNamingStrategy {

  /** Lower snake_case strategy.
    *
    *   - `Category` -> `category`
    *   - `MasterServiceOfferVariant` -> `master_service_offer_variant`
    *   - `parentId` -> `parent_id`
    *   - `masterServiceOfferId` -> `master_service_offer_id`
    */
  object SnakeCase extends RepoNamingStrategy {
    override def table(typeName: String): String = toSnakeCase(typeName)

    override def column(fieldLabel: String): String = toSnakeCase(fieldLabel)

    private def toSnakeCase(name: String): String = {
      val builder = new StringBuilder(name.length + 8)
      var previousWasLower = false
      name.foreach {
        char =>
          if (char.isUpper) {
            if (previousWasLower) {
              builder.append('_')
            }
            builder.append(char.toLower)
            previousWasLower = false
          } else {
            builder.append(char)
            previousWasLower = char.isLetterOrDigit
          }
      }
      builder.toString
    }
  }
}
