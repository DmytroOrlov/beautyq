package leaderboard.repo

import leaderboard.model.QueryFailure
import org.scalatest.wordspec.AnyWordSpec
import zio.{IO, Runtime, Unsafe, ZIO}

import java.util.UUID

/** Fixtures reproducing the exact shape of the pre-nominal-id BeautyQ bug:
  * two ids sharing the same underlying runtime representation (`UUID`), and
  * a repository with two methods returning the same child type, one keyed by
  * each id. Declared in their own object so the opaque types' transparency
  * (visible only within the statement sequence that declares them) does not
  * reach the derivation call site in [[NominalIdCatalogManyDerivationSpec]]
  * below - mirroring how `MasterId`/`MasterLocationId` are opaque in
  * `beautyq-model` while `CatalogMany.derivedFromRepositories`'s macro
  * expansion happens in the unrelated `beautyq-search-materialization`.
  */
object NominalIdCatalogManyFixtures {
  opaque type NominalParentId = UUID
  object NominalParentId {
    def apply(value: UUID): NominalParentId = value
  }

  // Shares NominalParentId's own underlying UUID representation, but is a
  // separate opaque type - the same relationship MasterId/MasterLocationId
  // have to each other after the nominal id migration.
  opaque type NominalOtherId = UUID
  object NominalOtherId {
    def apply(value: UUID): NominalOtherId = value
  }

  final case class NominalParent(id: NominalParentId, name: String)

  // NominalChild's own conventional id happens to be NominalOtherId - the
  // same id type the *wrong*, competing repo method below is keyed by -
  // stressing that disambiguation must come from the parameter type actually
  // matching the parent-edge foreign key, never from the child's own id.
  final case class NominalChild(id: NominalOtherId, parentId: NominalParentId, name: String)

  trait NominalChildRepo[F[_, _]] {
    /** The correct method: keyed by the parent-edge's own foreign key type. */
    def childrenByParent(parentId: NominalParentId): F[QueryFailure, List[NominalChild]]

    /** A competing method returning the same child type, keyed by an
      * unrelated nominal id. Under the old transparent-alias scheme (both
      * ids collapsing to plain `UUID`) this would be just as valid a match
      * as `childrenByParent` above - exactly the ambiguity
      * `CatalogMany.derivedFromRepositories` used to reject at compile time.
      */
    def childrenByOtherId(otherId: NominalOtherId): F[QueryFailure, List[NominalChild]]
  }

  final case class NominalRepositories[F[_, _]](childRepo: NominalChildRepo[F])
}

/** Proves that once two same-shaped BeautyQ-style ids are declared as opaque
  * types rather than transparent aliases, [[CatalogMany.derivedFromRepositories]]
  * derives the has-many edge unambiguously, selecting the repo method whose
  * parameter type matches the declared join key by type/signature alone -
  * never by method name, never via a selector-guided fallback.
  */
final class NominalIdCatalogManyDerivationSpec extends AnyWordSpec {
  import NominalIdCatalogManyFixtures.*

  private val parent  = NominalParent(NominalParentId(UUID.fromString("11111111-1111-1111-1111-111111111111")), "parent")
  private val otherId = NominalOtherId(UUID.fromString("22222222-2222-2222-2222-222222222222"))
  private val child   = NominalChild(otherId, parent.id, "child")

  private val repositories = NominalRepositories[IO](
    childRepo = new NominalChildRepo[IO] {
      def childrenByParent(parentId: NominalParentId): IO[QueryFailure, List[NominalChild]] =
        ZIO.succeed(if (parentId == parent.id) List(child) else Nil)

      def childrenByOtherId(otherId: NominalOtherId): IO[QueryFailure, List[NominalChild]] =
        ZIO.fail(QueryFailure.domain("wrong method selected: childrenByOtherId must never be called by parent-id-keyed derivation"))
    }
  )

  "CatalogMany.derivedFromRepositories" should {
    "select the repo method matching the parent's own nominal id type, not a same-shaped competing id" in {
      val declaration = catalog("nominalIdMany").branch[NominalParent].child[NominalChild](_.parentId)

      val materialized = declaration.materialize[IO, NominalRepositories[IO]](identity)
      val relationFactory = materialized
        .relationAs[NominalRepositories[IO] => Relation.HasMany[IO, NominalParent, NominalParentId, NominalChild, NominalOtherId]]
      val relation = relationFactory(repositories)

      assert(runIO(relation.load.run(parent.id)) == List(child))
    }
  }

  private def runIO[A](effect: IO[QueryFailure, A]): A =
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(effect).getOrThrowFiberFailure()
    }
}
