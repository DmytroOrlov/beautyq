package leaderboard.search

import io.circe.Json
import leaderboard.search.beautyq.gen2.wiring.*
import leaderboard.search.gen2.elasticsearch.*
import leaderboard.search.gen2.qdrant.*
import leaderboard.search.gen2.transport.*

/** Test-owned executable resource gate shared by pure and managed proofs.
  * The managed communication specs call these same readers before mutating resources; the
  * atomic spec exercises their failure taxonomy without Docker.
  */
object BeautyQSearchGen2ResourceSupport {
  type QdrantAliasEntry = SearchGen2ResourceInventorySupport.QdrantAliasEntry
  val QdrantAliasEntry = SearchGen2ResourceInventorySupport.QdrantAliasEntry

  sealed trait PreflightOutcome
  case object PreflightReady extends PreflightOutcome
  final case class PreflightBlocked(resources: Vector[String]) extends PreflightOutcome
  final case class PreflightBroken(resources: Vector[String]) extends PreflightOutcome

  def classifyPreflight(
    elasticsearch: Either[ElasticsearchGen2TransportError, Json],
    qdrant: Either[Gen2HttpTransportError, Json],
    embedding: Either[BeautyQEmbeddingRequestError, QdrantEmbeddingResult],
  ): PreflightOutcome = {
    val broken = Vector(
      elasticsearch match {
        case Left(_: ElasticsearchGen2TransportError.ConnectionFailed) => None
        case Left(error) => Some(s"Elasticsearch: $error")
        case Right(_) => None
      },
      qdrant match {
        case Left(_: Gen2HttpTransportError.ConnectionFailed) => None
        case Left(error) => Some(s"Qdrant: $error")
        case Right(root) =>
          root.hcursor.get[String]("version") match {
            case Right("1.18.3") => None
            case Right(version) => Some(s"Qdrant: expected version 1.18.3, got $version")
            case Left(_) => Some("Qdrant: missing or malformed version")
          }
      },
      embedding match {
        case Left(BeautyQEmbeddingRequestError.Unavailable(_)) => None
        case Left(error) => Some(s"embedding: $error")
        case Right(_) => None
      },
    ).flatten
    if (broken.nonEmpty) PreflightBroken(broken)
    else {
      val unavailable = Vector(
        elasticsearch match {
          case Left(_: ElasticsearchGen2TransportError.ConnectionFailed) => Some("Elasticsearch")
          case _ => None
        },
        qdrant match {
          case Left(_: Gen2HttpTransportError.ConnectionFailed) => Some("Qdrant")
          case _ => None
        },
        embedding match {
          case Left(BeautyQEmbeddingRequestError.Unavailable(_)) => Some("embedding")
          case _ => None
        },
      ).flatten
      if (unavailable.nonEmpty) PreflightBlocked(unavailable) else PreflightReady
    }
  }

  def reservedQdrantAliasTargets(entries: Vector[QdrantAliasEntry]): Vector[String] =
    entries.filter(_.alias == BeautyQSearchGen2ResourceNames.QdrantCollectionAlias).map(_.collection).distinct.sorted

  def readElasticsearchAliasTargets(
    client: ElasticsearchGen2JsonClient,
    alias: String,
  ): Either[String, Vector[String]] =
    client.getJson(s"/_alias/$alias") match {
      case Left(ElasticsearchGen2TransportError.HttpFailure(_, _, 404, _)) => Right(Vector.empty)
      case Left(error) => Left(error.toString)
      case Right(json) => json.asObject.toRight("response must be an object").map(_.keys.toVector.sorted)
    }

  def readQdrantAliases(client: QdrantGen2Client): Either[String, Vector[QdrantAliasEntry]] =
    client.listAliases().left.map(_.toString).flatMap(decodeQdrantAliases)

  def decodeQdrantAliases(raw: Json): Either[String, Vector[QdrantAliasEntry]] =
    SearchGen2ResourceInventorySupport.decodeQdrantAliases(raw)

  def decodeQdrantCollectionNames(raw: Json): Either[String, Vector[String]] =
    SearchGen2ResourceInventorySupport.decodeQdrantCollectionNames(raw)
}
