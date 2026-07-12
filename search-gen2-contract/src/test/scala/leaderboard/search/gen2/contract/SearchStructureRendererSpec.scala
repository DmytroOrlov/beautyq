package leaderboard.search.gen2.contract

import org.scalatest.wordspec.AnyWordSpec

import java.util.UUID

final class SearchStructureRendererSpec extends AnyWordSpec {

  private final case class ToyDocument(id: UUID, name: String)

  private val idField   = field[ToyDocument, UUID]("productId", _.id).keyword
  private val nameField = field[ToyDocument, String]("displayName", _.name).text.searchable

  private val declaration: SearchDocumentDeclaration[ToyDocument, UUID] =
    searchDocument[ToyDocument]("products").id(idField).field(nameField).build.getOrElse(fail("expected a valid declaration"))

  "SearchStructureRenderer.render" should {
    "render a single leaf child with the last-child connector" in {
      val rendered = SearchStructureRenderer.render("Root", Vector(SearchStructureNode.leaf("onlyChild")))
      assert(rendered == "Root\n└── onlyChild")
    }

    "render multiple leaf children with non-last/last connectors" in {
      val rendered = SearchStructureRenderer.render(
        "Root",
        Vector(SearchStructureNode.leaf("first"), SearchStructureNode.leaf("second"), SearchStructureNode.leaf("third")),
      )
      assert(
        rendered ==
          """Root
            |├── first
            |├── second
            |└── third""".stripMargin
      )
    }

    "render a branch with nested children, indenting by ancestor position" in {
      val rendered = SearchStructureRenderer.render(
        "Root",
        Vector(
          SearchStructureNode.branch("catalog", Vector(SearchStructureNode.leaf("topology"))),
          SearchStructureNode.leaf("last"),
        ),
      )
      assert(
        rendered ==
          """Root
            |├── catalog
            |│   └── topology
            |└── last""".stripMargin
      )
    }

    "render an indexed sequence as [index] value leaf lines in order" in {
      val rendered = SearchStructureRenderer.render("Root", Vector(SearchStructureNode.indexed("topology", Vector("first", "second", "third"))))
      assert(
        rendered ==
          """Root
            |└── topology
            |    ├── [0] first
            |    ├── [1] second
            |    └── [2] third""".stripMargin
      )
    }

    "graft an embedded multiline block without corrupting its own relative connectors" in {
      val embeddedBlock =
        """document products
          |├── identity productId
          |└── fields
          |    └── [0] displayName""".stripMargin

      val rendered = SearchStructureRenderer.render(
        "Root",
        Vector(SearchStructureNode.leaf("before"), SearchStructureNode.embedded("document", embeddedBlock)),
      )

      assert(
        rendered ==
          """Root
            |├── before
            |└── document
            |    └── document products
            |        ├── identity productId
            |        └── fields
            |            └── [0] displayName""".stripMargin
      )
    }

    "render the standard generic document section shape from the one document structure" in {
      val rendered    = SearchStructureRenderer.render("Root", Vector(SearchStructureNode.document("products", declaration)))
      val actualLines = rendered.linesIterator.toVector

      val expectedPrefix = Vector(
        "Root",
        "└── products",
        "    ├── identity: productId",
        "    ├── Fields",
        "    │   ├── [0] productId -> id",
        "    │   └── [1] displayName -> name",
        "    └── document",
      )
      assert(actualLines.take(expectedPrefix.size) == expectedPrefix)

      val embeddedLines = declaration.renderStructure.linesIterator.toVector
      val expectedEmbedded = embeddedLines match {
        case head +: tail => ("        └── " + head) +: tail.map("            " + _)
        case _             => Vector.empty
      }
      assert(actualLines.drop(expectedPrefix.size) == expectedEmbedded)
    }

    "render deterministically across repeated calls" in {
      val nodes = Vector(
        SearchStructureNode.branch("catalog", Vector(SearchStructureNode.leaf("topology"))),
        SearchStructureNode.document("products", declaration),
      )
      assert(SearchStructureRenderer.render("Root", nodes) == SearchStructureRenderer.render("Root", nodes))
    }
  }
}
