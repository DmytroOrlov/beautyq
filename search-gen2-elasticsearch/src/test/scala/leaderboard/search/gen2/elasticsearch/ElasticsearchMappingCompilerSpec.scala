package leaderboard.search.gen2.elasticsearch

import leaderboard.search.gen2.contract.*
import org.scalatest.wordspec.AnyWordSpec

import io.circe.{Json, JsonObject}

/** Neutral calibration for the generic Elasticsearch mapping compiler, using the shared book/library
  * document fixture ([[ElasticsearchTestFixtures]]) unrelated to BeautyQ. `SearchGen2ModuleFirewallSpec`
  * already scans every generic Gen2 main source (including this module's) for forbidden BeautyQ
  * text/imports, so this spec does not repeat that scan.
  */
final class ElasticsearchMappingCompilerSpec extends AnyWordSpec {
  import ElasticsearchTestFixtures.*

  private def mappingOrFail(policy: ElasticsearchIndexPolicy[BookDocument, String] = ElasticsearchTestFixtures.policy): ElasticsearchMapping =
    ElasticsearchMappingCompiler.compile(policy) match {
      case Right(mapping) => mapping
      case Left(error)    => fail(s"expected a valid mapping, got $error")
    }

  private def propertiesOf(json: Json): JsonObject =
    json.asObject.flatMap(_.apply("properties")).flatMap(_.asObject).getOrElse(fail("expected a properties object"))

  // Built directly via Json.obj/Json.fromString - the same construction primitives the compiler itself
  // uses - rather than parsed from a JSON-text literal, so this is an exact structural expectation, not a
  // separately re-derived one.
  private val expectedMappingJson: Json =
    Json.obj(
      "properties" -> Json.obj(
        "isbn"          -> Json.obj("type" -> Json.fromString("keyword")),
        "title"         -> Json.obj("type" -> Json.fromString("text"), "analyzer" -> Json.fromString("standard")),
        "subtitle"      -> Json.obj("type" -> Json.fromString("text"), "analyzer" -> Json.fromString("standard")),
        "internalNote"  -> Json.obj("type" -> Json.fromString("text")),
        "genre"         -> Json.obj("type" -> Json.fromString("keyword")),
        "pageCount"     -> Json.obj("type" -> Json.fromString("integer")),
        "wordCount"     -> Json.obj("type" -> Json.fromString("long")),
        "price"         -> Json.obj("type" -> Json.fromString("double")),
        "inPrint"       -> Json.obj("type" -> Json.fromString("boolean")),
        "publishedAt"   -> Json.obj("type" -> Json.fromString("date"), "format" -> Json.fromString("strict_date_optional_time")),
        "storeLocation" -> Json.obj("type" -> Json.fromString("geo_point")),
        "stockFrom"     -> Json.obj("type" -> Json.fromString("double")),
        "stockTo"       -> Json.obj("type" -> Json.fromString("double")),
        "editionRatings" -> Json.obj(
          "properties" -> Json.obj(
            "hardcover" -> Json.obj("type" -> Json.fromString("integer")),
            "paperback" -> Json.obj("type" -> Json.fromString("integer")),
          )
        ),
      )
    )

  "ElasticsearchMappingCompiler.compile" should {
    "produce the exact mapping for all eight SearchFieldKinds, an analyzer only for assigned Text fields, and dotted dynamic nesting - in declared order" in {
      assert(mappingOrFail().json == expectedMappingJson)
    }

    // Json/JsonObject structural equality alone does not prove serialized property order (two JsonObjects
    // built with different insertion order can still compare equal), so property order is asserted
    // directly against JsonObject's own key vector, and repeated compilation is compared through exact
    // compact-JSON text (noSpaces), not just ADT equality.
    //
    // The declaration builder registers static fields and dynamic families into two separate buffers and
    // always emits static-declared-order followed by dynamic-declared-order (never true interleaved
    // declaration order), so `editionRatings` (the one dynamic family, declared before stockFrom/stockTo in
    // source) is expected last, after every static field.
    "preserve exact root and nested property key order, matching declared field order" in {
      val rootProperties = propertiesOf(mappingOrFail().json)
      assert(
        rootProperties.toVector.map(_._1) ==
          Vector(
            "isbn", "title", "subtitle", "internalNote", "genre", "pageCount", "wordCount", "price", "inPrint", "publishedAt", "storeLocation", "stockFrom",
            "stockTo", "editionRatings",
          )
      )

      val nestedProperties = propertiesOf(rootProperties("editionRatings").getOrElse(fail("expected editionRatings in the mapping")))
      assert(nestedProperties.toVector.map(_._1) == Vector("hardcover", "paperback"))
    }

    "produce byte-identical serialized output for repeated compilation of identical input" in {
      assert(mappingOrFail().json.noSpaces == mappingOrFail().json.noSpaces)
      assert(mappingOrFail().json.noSpaces == expectedMappingJson.noSpaces)
    }

    "report a typed path conflict when a leaf field's path is later reused as a nested object" in {
      final case class ConflictDocument(id: String, a: String, b: Int)

      val conflictId   = leaderboard.search.gen2.contract.field[ConflictDocument, String]("id", _.id).keyword
      val leafA        = leaderboard.search.gen2.contract.field[ConflictDocument, String]("a", _.a).keyword
      val nestedUnderA = computedField[ConflictDocument, Int]("bNested", "a.nested")(doc => Some(doc.b)).integer

      val conflictDocument =
        searchDocument[ConflictDocument]("conflict").id(conflictId).field(leafA).field(nestedUnderA).build match {
          case Right(value) => value
          case Left(error)  => fail(s"expected a valid conflict-fixture declaration, got $error")
        }

      val conflictPolicy = ElasticsearchIndexPolicy.unsafeFrom(conflictDocument, ElasticsearchPolicyVersion("conflict-es-v1"), Vector.empty)

      ElasticsearchMappingCompiler.compile(conflictPolicy) match {
        case Left(ElasticsearchMappingError(ElasticsearchPathConflict(fieldId, path))) =>
          assert(fieldId == nestedUnderA.id)
          assert(path == nestedUnderA.path)
        case other => fail(s"expected a path conflict, got $other")
      }
    }
  }
}
