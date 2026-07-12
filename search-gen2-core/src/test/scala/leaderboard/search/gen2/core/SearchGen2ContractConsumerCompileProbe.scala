package leaderboard.search.gen2.core

import leaderboard.model.UuidBackedId
import leaderboard.search.gen2.contract.*

import java.util.UUID

// Compile-only proof that the public field/document declaration DSL is usable from a sibling Gen2
// module, not only from within leaderboard.search.gen2.contract itself. This file uses no ScalaTest API
// and asserts nothing at runtime - a successful searchGen2Core/Test/compile is the proof.
private final case class ConsumerDocument(
  id: UUID,
  name: String,
)

private val idField =
  field[ConsumerDocument, UUID]("documentId", _.id)
    .keyword
    .filterable(FilterOperator.Equal)
    .payloadEligible

private val nameField =
  field[ConsumerDocument, String]("displayName", _.name)
    .text
    .searchable

private val declaration =
  searchDocument[ConsumerDocument]("consumerDocuments")
    .id(idField)
    .field(nameField)
    .build

// Compile-only proof of the low-boilerplate `searchFields[Document]` authoring DSL used the way a real
// domain uses it: the opaque wrapper and denormalized document live in a separate model scope
// (ConsumerWidgetModel, mirroring a business module), and every search declaration below is authored
// outside that scope - the same model/search-contract boundary BeautyQ uses. No SearchValueCodec given
// is declared anywhere in this file.
private object ConsumerWidgetModel {
  opaque type WidgetId = UUID

  object WidgetId extends UuidBackedId[WidgetId] {
    def apply(value: UUID): WidgetId = value
    def unwrap(id: WidgetId): UUID   = id
    given UuidBackedId[WidgetId]     = this
  }

  final case class WidgetAttributeDefinition(code: String)

  final case class WidgetDocument(
    id: WidgetId,
    name: String,
    tags: Map[String, String],
  )
}

import ConsumerWidgetModel.*

private val widgetIdCodec: SearchValueCodec[WidgetId] = summon[SearchValueCodec[WidgetId]]

private val widgetDeclarations = searchFields[WidgetDocument]("widgets")

private val widgetIdField =
  widgetDeclarations
    .inferred(_.id)
    .payloadEligible
    .declare

private val widgetNameField =
  widgetDeclarations
    .text(_.name)
    .searchable
    .declare

private val widgetTagsFamily =
  widgetDeclarations
    .dynamicMap(_.tags, Vector(WidgetAttributeDefinition("color"), WidgetAttributeDefinition("size")))(_.code)
    .keyword
    .filterable(FilterOperator.Equal)
    .declare

private val widgetDocument = widgetDeclarations.document(widgetIdField)

private val renderedWidgetSection: String =
  SearchStructureRenderer.render("ConsumerRoot", Vector(SearchStructureNode.document("widgets", widgetDocument)))
