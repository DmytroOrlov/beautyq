package leaderboard.search.gen2.contract

final case class SearchDocumentId(value: String)
final case class FieldId(value: String)
final case class FieldPath(value: String)
final case class FieldSemantic(value: String)
final case class SearchValueTypeId(value: String)

object SearchValueTypeId {
  inline def derived[A]: SearchValueTypeId =
    ${ SearchValueTypeIdDerivation.derivedImpl[A] }
}

// Shared stable-name validation policy for document/field identities, paths, semantics, and value
// type IDs: `segment ("." segment)*` where `segment := [A-Za-z][A-Za-z0-9_-]*`. Pure and total; no
// runtime reflection, JSON codecs, or backend names.
private[contract] object StableName {
  private val pattern = "^[A-Za-z][A-Za-z0-9_-]*(\\.[A-Za-z][A-Za-z0-9_-]*)*$".r

  def isValid(value: String): Boolean = pattern.matches(value)
}
