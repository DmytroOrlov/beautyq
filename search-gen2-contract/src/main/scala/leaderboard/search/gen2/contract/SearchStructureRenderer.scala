package leaderboard.search.gen2.contract

/** A pure, domain-neutral node in a deterministic structural rendering tree. The only construction
  * path is the smart constructors on the companion object; concrete node shapes are contract-internal.
  */
sealed trait SearchStructureNode

final case class SearchStructureTree(
  rootLabel: String,
  children: Vector[SearchStructureNode],
)

object SearchStructureNode {
  private[contract] final case class Leaf(label: String) extends SearchStructureNode
  private[contract] final case class Branch(label: String, children: Vector[SearchStructureNode]) extends SearchStructureNode
  private[contract] final case class Indexed(label: String, values: Vector[String]) extends SearchStructureNode
  private[contract] final case class Embedded(label: String, renderedBlock: String) extends SearchStructureNode
  private[contract] final case class DocumentSection(label: String, structure: SearchDocumentStructure) extends SearchStructureNode

  /** A single leaf line with no children. */
  def leaf(label: String): SearchStructureNode = Leaf(label)

  /** A labeled line whose children are recursively rendered structure nodes. */
  def branch(label: String, children: Vector[SearchStructureNode]): SearchStructureNode = Branch(label, children)

  /** A labeled line whose children are `[index] value` leaf lines, in `values` order. */
  def indexed(label: String, values: Vector[String]): SearchStructureNode = Indexed(label, values)

  /** Grafts an already fully rendered multiline block - whose own first line is its root label and
    * whose remaining lines already carry correct relative connectors starting from column zero - at
    * this position, reindented as a single nested unit rather than rebuilt from raw structural data.
    */
  def embedded(label: String, renderedBlock: String): SearchStructureNode = Embedded(label, renderedBlock)

  /** The standard generic-document section shape: an `identity: <FieldId>` leaf, an indexed `Fields`
    * listing of `document.structure.identity +: document.structure.fields` as `<id> -> <path>` values,
    * and a detailed `document` subtree derived from the same structure. No rendered string or manually
    * rebuilt field metadata participates in this path.
    */
  def document[Document, Id](label: String, document: SearchDocumentDeclaration[Document, Id]): SearchStructureNode =
    DocumentSection(label, document.structure)

  private[contract] def documentTree(structure: SearchDocumentStructure): SearchStructureTree =
    SearchStructureTree(
      rootLabel = s"document ${structure.id.value}",
      children = Vector(
        branch(s"identity ${structure.identity.id.value}", fieldDetailNodes(structure.identity)),
        branch(
          "fields",
          structure.fields.zipWithIndex.map { case (field, index) =>
            branch(s"[$index] ${field.id.value}", fieldDetailNodes(field))
          },
        ),
      ),
    )

  private def fieldDetailNodes(field: SearchFieldStructure): Vector[SearchStructureNode] =
    Vector(
      leaf(s"path: ${field.path.value}"),
      leaf(s"kind: ${renderKind(field.kind)}"),
      leaf(s"valueType: ${field.valueType.value}"),
      leaf(s"semantic: ${field.semantic.map(_.value).getOrElse("-")}"),
      leaf(s"presence: ${renderPresence(field.presence)}"),
      leaf(s"capabilities: ${renderCapabilities(field.capabilities)}"),
    )

  private def renderKind(kind: SearchFieldKind): String =
    kind match {
      case SearchFieldKind.Keyword  => "keyword"
      case SearchFieldKind.Text     => "text"
      case SearchFieldKind.Integer  => "integer"
      case SearchFieldKind.Long     => "long"
      case SearchFieldKind.Decimal  => "decimal"
      case SearchFieldKind.Boolean  => "boolean"
      case SearchFieldKind.DateTime => "date-time"
      case SearchFieldKind.GeoPoint => "geo-point"
    }

  private def renderPresence(presence: FieldPresence): String =
    presence match {
      case FieldPresence.Required => "required"
      case FieldPresence.Optional => "optional"
    }

  private def renderCapabilities(capabilities: FieldCapabilities): String = {
    val filter = capabilities.filterOperators.toVector.sortBy(_.ordinal).map {
      case FilterOperator.Equal       => "equal"
      case FilterOperator.In          => "in"
      case FilterOperator.Range       => "range"
      case FilterOperator.GeoDistance => "geo-distance"
    }.mkString(",")
    val facet = capabilities.facetModes.toVector.sortBy(_.ordinal).map {
      case FacetMode.Terms => "terms"
      case FacetMode.Range => "range"
    }.mkString(",")
    val sort = capabilities.sortModes.toVector.sortBy(_.ordinal).map {
      case SortMode.Value    => "value"
      case SortMode.Distance => "distance"
    }.mkString(",")
    val group = capabilities.groupModes.toVector.sortBy(_.ordinal).map {
      case GroupMode.Terms => "terms"
    }.mkString(",")

    s"searchable=${capabilities.searchable}; filter=[$filter]; facet=[$facet]; sort=[$sort]; group=[$group]; payload=${capabilities.payloadEligible}"
  }
}

/** Generic deterministic structural-tree renderer, reused by every Gen2 domain root instead of a
  * domain-owned tree-rendering implementation. Has no catalog or other business-domain dependency.
  */
object SearchStructureRenderer {
  import SearchStructureNode.*

  def render(tree: SearchStructureTree): String = render(tree.rootLabel, tree.children)

  private[contract] def renderDocument(structure: SearchDocumentStructure): String =
    render(documentTree(structure))

  def render(rootLabel: String, children: Vector[SearchStructureNode]): String = {
    val childLines = children.zipWithIndex.flatMap {
      case (child, index) => renderNode(child, prefix = "", isLast = index == children.size - 1)
    }
    (rootLabel +: childLines).mkString("\n")
  }

  private def renderNode(node: SearchStructureNode, prefix: String, isLast: Boolean): Vector[String] =
    node match {
      case Leaf(label) =>
        Vector(connectorLine(prefix, isLast, label))

      case Branch(label, children) =>
        val childPrefix = childPrefixOf(prefix, isLast)
        val childLines = children.zipWithIndex.flatMap {
          case (child, index) => renderNode(child, childPrefix, index == children.size - 1)
        }
        connectorLine(prefix, isLast, label) +: childLines

      case Indexed(label, values) =>
        val childPrefix = childPrefixOf(prefix, isLast)
        val childLines = values.zipWithIndex.map {
          case (value, index) => connectorLine(childPrefix, index == values.size - 1, s"[$index] $value")
        }
        connectorLine(prefix, isLast, label) +: childLines

      case Embedded(label, renderedBlock) =>
        val childPrefix = childPrefixOf(prefix, isLast)
        connectorLine(prefix, isLast, label) +: graftBlock(renderedBlock, childPrefix)

      case DocumentSection(label, structure) =>
        val fieldStructures = structure.identity +: structure.fields
        val documentStructure = documentTree(structure)
        val children = Vector(
          leaf(s"identity: ${structure.identity.id.value}"),
          indexed("Fields", fieldStructures.map(f => s"${f.id.value} -> ${f.path.value}")),
          branch("document", Vector(branch(documentStructure.rootLabel, documentStructure.children))),
        )
        val childPrefix = childPrefixOf(prefix, isLast)
        val childLines = children.zipWithIndex.flatMap {
          case (child, index) => renderNode(child, childPrefix, index == children.size - 1)
        }
        connectorLine(prefix, isLast, label) +: childLines
    }

  // Grafts an already-rendered multi-line block - whose first line is its own root label and whose
  // remaining lines already carry correct relative connectors starting from column zero - as the sole
  // child at `childPrefix`, without rebuilding it from raw structural data.
  private def graftBlock(rawBlock: String, childPrefix: String): Vector[String] =
    rawBlock.linesIterator.toVector match {
      case head +: tail => (childPrefix + "└── " + head) +: tail.map(childPrefix + "    " + _)
      case _             => Vector(childPrefix + "└── ")
    }

  private def connectorLine(prefix: String, isLast: Boolean, label: String): String =
    prefix + (if (isLast) "└── " else "├── ") + label

  private def childPrefixOf(prefix: String, isLast: Boolean): String =
    prefix + (if (isLast) "    " else "│   ")
}
