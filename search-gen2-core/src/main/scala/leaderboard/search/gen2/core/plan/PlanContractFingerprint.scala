package leaderboard.search.gen2.core.plan

import leaderboard.search.gen2.contract.*
import leaderboard.search.gen2.core.materialization.CanonicalFingerprint

/** The final contract fingerprint is framework-produced. Its only production construction path is
  * [[PlanContractFingerprint.compute]] in this package; backend modules can consume it but cannot forge
  * one from a string. */
opaque type ContractFingerprint = String

object ContractFingerprint {
  extension (fingerprint: ContractFingerprint)
    def value: String = fingerprint

  private[plan] def fromCanonicalHash(value: String): ContractFingerprint = value
}

/** Derives the plan contract identity from the executable document declaration, one explicit domain
  * version and typed backend/compiler compatibility contributions. It deliberately does not hash a
  * rendered diagnostic tree: rendering is a review view, while this value protects the contract used by
  * plan identity. Generation identity remains responsible for the complete physical index/collection
  * reuse decision. */
object PlanContractFingerprint {

  def compute[Document, Id](
    version: PlanContractVersion,
    declaration: SearchDocumentDeclaration[Document, Id],
    contributions: PlanContractContributions = Map.empty,
  ): ContractFingerprint =
    ContractFingerprint.fromCanonicalHash(
      CanonicalFingerprint.sha256HexTokens(
        Vector(CanonicalFingerprint.token("contract.version", version.value)) ++
          contributionTokens(contributions) ++
          structureTokens(declaration.structure)
      )
    )

  private def contributionTokens(contributions: PlanContractContributions): Vector[String] =
    contributions
      .toVector
      .sortBy { case (id, version) => (id.value, version.value) }
      .zipWithIndex
      .flatMap { case ((id, version), index) =>
        Vector(
          CanonicalFingerprint.token(s"contribution[$index].id", id.value),
          CanonicalFingerprint.token(s"contribution[$index].version", version.value),
        )
      }

  private def structureTokens(structure: SearchDocumentStructure): Vector[String] =
    Vector(CanonicalFingerprint.token("document.id", structure.id.value)) ++
      fieldTokens("identity", structure.identity) ++
      structure.fields.zipWithIndex.flatMap { case (field, index) =>
        fieldTokens(s"field[$index]", field)
      }

  private def fieldTokens(prefix: String, field: SearchFieldStructure): Vector[String] =
    Vector(
      CanonicalFingerprint.token(s"$prefix.id", field.id.value),
      CanonicalFingerprint.token(s"$prefix.path", field.path.value),
      CanonicalFingerprint.token(s"$prefix.kind", kindCode(field.kind)),
      CanonicalFingerprint.token(s"$prefix.value-type", field.valueType.value),
      CanonicalFingerprint.token(s"$prefix.semantic", field.semantic.map(_.value).getOrElse("")),
      CanonicalFingerprint.token(s"$prefix.presence", presenceCode(field.presence)),
      CanonicalFingerprint.token(s"$prefix.searchable", booleanCode(field.capabilities.searchable)),
      CanonicalFingerprint.token(s"$prefix.filter", field.capabilities.filterOperators.toVector.map(filterCode).sorted.mkString(",")),
      CanonicalFingerprint.token(s"$prefix.facet", field.capabilities.facetModes.toVector.map(facetCode).sorted.mkString(",")),
      CanonicalFingerprint.token(s"$prefix.sort", field.capabilities.sortModes.toVector.map(sortCode).sorted.mkString(",")),
      CanonicalFingerprint.token(s"$prefix.group", field.capabilities.groupModes.toVector.map(groupCode).sorted.mkString(",")),
      CanonicalFingerprint.token(s"$prefix.payload", booleanCode(field.capabilities.payloadEligible)),
    )

  private def kindCode(value: SearchFieldKind): String = value match {
    case SearchFieldKind.Keyword  => "keyword"
    case SearchFieldKind.Text     => "text"
    case SearchFieldKind.Integer  => "integer"
    case SearchFieldKind.Long     => "long"
    case SearchFieldKind.Decimal  => "decimal"
    case SearchFieldKind.Boolean  => "boolean"
    case SearchFieldKind.DateTime => "date-time"
    case SearchFieldKind.GeoPoint => "geo-point"
  }

  private def filterCode(value: FilterOperator): String = value match {
    case FilterOperator.Equal       => "equal"
    case FilterOperator.In          => "in"
    case FilterOperator.Range       => "range"
    case FilterOperator.GeoDistance => "geo-distance"
  }

  private def facetCode(value: FacetMode): String = value match {
    case FacetMode.Terms => "terms"
    case FacetMode.Range => "range"
  }

  private def sortCode(value: SortMode): String = value match {
    case SortMode.Value    => "value"
    case SortMode.Distance => "distance"
  }

  private def groupCode(value: GroupMode): String = value match {
    case GroupMode.Terms => "terms"
  }

  private def presenceCode(value: FieldPresence): String = value match {
    case FieldPresence.Required => "required"
    case FieldPresence.Optional => "optional"
  }

  private def booleanCode(value: Boolean): String = if (value) "true" else "false"
}
