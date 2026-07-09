package leaderboard.search

import leaderboard.model.QueryFailure
import leaderboard.search.dsl.*
import org.scalatest.wordspec.AnyWordSpec

final class SearchQuerySchemaBuilderSpec extends AnyWordSpec {

  private final case class QueryDocument(
    name: String,
    location: SearchGeoPoint,
  )

  private final case class OtherDocument(name: String)

  private sealed trait QueryConstraint
  private object QueryConstraint {
    final case class Name(value: String) extends QueryConstraint
    case object Near extends QueryConstraint
  }

  private val nameField = SearchField.keyword[QueryDocument](_.name)
  private val locationField = SearchField.geoPoint[QueryDocument](_.location)
  private val otherNameField = SearchField.keyword[OtherDocument](_.name)

  private val resolver: QueryConstraint => Either[QueryFailure, ResolvedSearchConstraint[QueryDocument]] = {
    case QueryConstraint.Name(value) =>
      Right(ResolvedSearchConstraint.Terms(nameField, Set(value), SearchBoostRole("name")))
    case QueryConstraint.Near =>
      Right(ResolvedSearchConstraint.GeoDistance(locationField, SearchBoostRole("distance")))
  }

  private val facetResolver: (FacetField[QueryDocument], String) => Either[QueryFailure, QueryConstraint] =
    (facet, value) =>
      if (facet.field == nameField) {
        Right(QueryConstraint.Name(value))
      } else {
        Left(QueryFailure.domain(s"Unexpected facet ${facet.path}"))
      }

  private val builderSchema: SearchQuerySchema[QueryDocument, QueryConstraint] =
    searchQuery[QueryDocument, QueryConstraint]
      .field("publicName", nameField)
      .field("nearby", locationField)
      .geoScoring(locationField)
      .resolve(resolver)
      .facetConstraint(facetResolver)

  private val constructorSchema: SearchQuerySchema[QueryDocument, QueryConstraint] =
    SearchQuerySchema(
      fields = List(
        SearchQueryField("publicName", nameField),
        SearchQueryField("nearby", locationField),
      ),
      geoScoringField = Some(locationField),
      resolve = resolver,
      facetConstraint = facetResolver,
    )

  "SearchQuerySchema fluent builder" should {
    "preserve caller-supplied query names and field declaration order" in {
      assert(builderSchema.fields.map(_.name) == List("publicName", "nearby"))
      assert(builderSchema.fields.map(_.field) == List(nameField, locationField))
      builderSchema.fields match {
        case first :: _ =>
          assert(first.name != first.field.path)
        case Nil =>
          fail("Expected the builder schema to contain its declared fields")
      }
    }

    "preserve the geo-scoring field" in {
      assert(builderSchema.geoScoringField == Some(locationField))
    }

    "preserve the explicit constraint and facet resolvers" in {
      assert(builderSchema.resolve(QueryConstraint.Name("Alice")) == Right(ResolvedSearchConstraint.Terms(nameField, Set("Alice"), SearchBoostRole("name"))))
      assert(builderSchema.facetConstraint(FacetField(nameField, FacetFieldMode.Terms), "Alice") == Right(QueryConstraint.Name("Alice")))
    }

    "expose the same lookup behavior as the raw constructor" in {
      assert(builderSchema.fieldsByName == constructorSchema.fieldsByName)
      assert(builderSchema.field("publicName") == constructorSchema.field("publicName"))
      assert(builderSchema.field("nearby") == constructorSchema.field("nearby"))
      assert(builderSchema.field("missing") == constructorSchema.field("missing"))
    }

    "reject a field for a different document type at compile time" in {
      assert(otherNameField.path == "name")
      assertDoesNotCompile(
        """searchQuery[QueryDocument, QueryConstraint].field("wrongDocument", otherNameField)"""
      )
    }
  }
}
