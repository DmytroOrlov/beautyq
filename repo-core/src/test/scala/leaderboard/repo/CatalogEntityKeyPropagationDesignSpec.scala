package leaderboard.repo

import leaderboard.repo.RepoOp.{AllValues, ManyByKey}
import org.scalatest.wordspec.AnyWordSpec
import zio.{IO, ZIO}

/** Phase C.2 design spike - NOT a production change (historical: production
  * `RootAllSpec`/`ManyEdgeSpec` now carry their output key types as of Phase
  * C3, so the five entities named below no longer need explicit
  * `CatalogEntity.Aux[...]` givens in `BeautyQCatalogGraph.Evidence` - see
  * that object and the roadmap's Phase C3 status for the current state).
  *
  * Phase C.1 found that `CatalogEntity.derivedFromId[A, K]` (a fully generic
  * `transparent inline given`) can only resolve when the requested `K` is
  * already concrete/pinned by something *other* than entity evidence itself
  * before the given search runs. That holds for `RootTreeSpec[A, K]` and a
  * `ManyEdgeSpec[P, C, K]`'s *parent* side (`K` is part of the spec's own
  * type), which is why `Category`'s explicit given could be removed first.
  * At the time this spike was written, that did NOT yet hold for
  * `RootAllSpec[A]` (carried no key at all) or a `ManyEdgeSpec[P, C, K]`'s
  * *child* side (`CK` did not appear in the spec), which is why
  * `Master`/`Service`/`MasterLocation`/`MasterServiceOffer`/
  * `MasterServiceOfferVariant` still needed explicit givens back then -
  * verified empirically in Phase C.1 by removing e.g. `Master`'s given and
  * observing the same "unresolved `???` / macro expansion was stopped"
  * failure. Phase C3 closed this gap in production specs (see below).
  *
  * This spike reproduces both shapes with local, BeautyQ-free fixtures, and
  * tests one candidate fix: specs carrying their own output key types
  * ("Candidate A"), plus whether a directly-parameterized entity-evidence
  * encoding ("Candidate B") changes anything on its own. It does not touch
  * production `RepoGraph.scala`/`CatalogEntityDerivation.scala`/
  * `BeautyQCatalogGraph.scala`, and it does not derive real relation-loading
  * evidence (`CatalogRootAll`/`CatalogMany` are untouched, unused here).
  * Candidate A's finding (key-carrying specs let entity evidence resolve
  * automatically) is exactly what Phase C3 built on for production, though
  * production pins the key type via a type-level `Mirror`-based lookup at
  * declaration time rather than via a `CatalogEntity.Aux[A, K]` `using`
  * clause on `rootAll`/`child` themselves (verified during C3: the latter
  * still leaves the method's own `K`/`CK` free and Scala defaults it to
  * `Any`, the same failure this spike reproduces for `RootAllSpecCurrent`/
  * `ManyEdgeSpecCurrent`).
  */

// --- Fixture domain, not BeautyQ ---
final case class SpikeWidgetId(value: String)
final case class SpikeWidget(id: SpikeWidgetId, name: String)

final case class SpikeGadgetId(value: String)
final case class SpikeGadget(id: SpikeGadgetId, widgetId: SpikeWidgetId, label: String)

// --- Current (broken) shape, reproduced locally for reference ---
// These two case classes are the SAME shape as production `RootAllSpec[A]`
// (no key at all) and `ManyEdgeSpec[P, C, K]` (parent key `K` only, no child
// key). They are declared here only so the comment above/below has a concrete
// name to point at; no `KeyPropagationProof` given is written for them,
// because writing one that resolves via a fully generic
// `CatalogEntity.derivedFromId[A, K]` is exactly what does not compile -
// reproduced and confirmed via a throwaway scratch file during this spike
// (same technique as Phase C.1's `Master` check), then deleted, since a
// permanently-failing file cannot live in the compiled test suite.
final case class RootAllSpecCurrent[A]()
final case class ManyEdgeSpecCurrent[P, C, K]()

// --- Candidate A: specs carry key types ---

/** Fixed rootAll-like spec: unlike `RootAllSpecCurrent[A]` above, this carries
  * the root key type `K` as its own type parameter - exactly how the
  * already-working `RootTreeSpec[A, K]` carries its key.
  */
final case class RootAllSpec2[A, K]()

/** Fixed manyEdge-like spec: unlike `ManyEdgeSpecCurrent[P, C, K]` above, this
  * carries BOTH the parent key `PK` and the child key `CK`.
  */
final case class ManyEdgeSpec2[P, C, PK, CK]()

/** Miniature stand-in for `MaterializeOne`: proves only whether entity
  * evidence *resolves* for a given spec shape. `KeyPropagationProof` itself
  * carries no relation-loading semantics - it is a marker, not a
  * `CatalogRootAll`/`CatalogMany` replacement.
  */
trait KeyPropagationProof[Spec]

object KeyPropagationProof {

  // Candidate A: rootAll fixed. K is pinned by the spec's own type parameter
  // before `CatalogEntity.Aux[A, K]` is searched, so the fully generic
  // `CatalogEntity.derivedFromId[A, K]` given (repo-core, untouched) resolves
  // it - no explicit `CatalogEntity.Aux[SpikeWidget, SpikeWidgetId]` given is
  // declared anywhere in this file.
  given rootAllFixed[A, K](using entity: CatalogEntity.Aux[A, K]): KeyPropagationProof[RootAllSpec2[A, K]] =
    new KeyPropagationProof[RootAllSpec2[A, K]] {}

  // Candidate A: manyEdge fixed. Both PK and CK are pinned by the spec's own
  // type parameters, so both parent and child entity evidence resolve
  // automatically - no explicit givens for SpikeWidget or SpikeGadget.
  given manyEdgeFixed[P, C, PK, CK](using
    parentEntity: CatalogEntity.Aux[P, PK],
    childEntity: CatalogEntity.Aux[C, CK],
  ): KeyPropagationProof[ManyEdgeSpec2[P, C, PK, CK]] =
    new KeyPropagationProof[ManyEdgeSpec2[P, C, PK, CK]] {}
}

// --- Candidate B: parameterized entity evidence (no path-dependent member) ---

/** A directly-parameterized alternative to `CatalogEntity[A] { type Key = K }`
  * / `CatalogEntity.Aux[A, K]`.
  *
  * A first attempt gave this a REAL validating macro given, mirroring
  * `CatalogEntity.derivedFromId` exactly (discover `A`'s conventional `id`
  * field via `quotes.reflect`, abort if `K` does not match it), to make sure
  * the Candidate B comparison was fair rather than testing a trivial stand-in
  * that "succeeds" for any `K` by performing no validation at all. That macro
  * (definition and `${ ... }` call site both in this one file) failed with:
  *
  * {{{
  * Cyclic macro dependencies in CatalogEntityKeyPropagationDesignSpec.scala.
  * Compilation stopped since no further progress can be made.
  * To fix this, place macros in one set of files and their callers in another.
  * }}}
  *
  * This is a genuine Dotty restriction, independent of the free-key question
  * this spike is about: a macro implementation and its `${ ... }` call site
  * cannot live in the same file. Properly answering "does a real, validating,
  * parameterized `CatalogEntity2[A, K]` resolve a free `K` any better than
  * `CatalogEntity.Aux[A, K]` does?" would need a separate macro-impl module
  * (like `CatalogEntityDerivation.scala` is for `CatalogEntity`) alongside its
  * caller - a fair validating Candidate B would require a separate macro
  * implementation file and was not assessed here; this design spike
  * intentionally keeps production code untouched.
  *
  * What is left, below, is a deliberately trivial (non-validating) given:
  * it resolves `CatalogEntity2[A, K]` for *any* `K`, including a completely
  * free one, precisely because it never checks `K` against anything. That is
  * not evidence that parameterizing entity evidence solves the free-key
  * problem - it only shows that a given which validates nothing has nothing
  * to fail on. Candidate B is therefore left unresolved by this spike, not
  * disproven: it would need the same real id-field-matching macro
  * `CatalogEntity.derivedFromId` already has, and that macro's own free-`K`
  * limitation (Phase C.1) is not obviously changed by switching from
  * `Aux`-refinement to direct parameterization - but this file cannot prove
  * that either way.
  */
trait CatalogEntity2[A, K]

object CatalogEntity2 {
  given fromMirror[A, K](using scala.deriving.Mirror.ProductOf[A]): CatalogEntity2[A, K] =
    new CatalogEntity2[A, K] {}
}

/** Mirrors `KeyPropagationProof`, but keyed on `CatalogEntity2` evidence
  * instead of `CatalogEntity.Aux`, to isolate whether the encoding itself
  * (parameterized vs. path-dependent-refined) changes anything.
  */
trait KeyPropagationProof2[Spec]

object KeyPropagationProof2 {
  given rootAllFixedWithEntity2[A, K](using entity: CatalogEntity2[A, K]): KeyPropagationProof2[RootAllSpec2[A, K]] =
    new KeyPropagationProof2[RootAllSpec2[A, K]] {}
}

final class CatalogEntityKeyPropagationDesignSpec extends AnyWordSpec {

  "Candidate A (specs carry key types)" should {

    "resolve rootAll-like entity evidence automatically once the spec carries its own K" in {
      val proof = summon[KeyPropagationProof[RootAllSpec2[SpikeWidget, SpikeWidgetId]]]
      assert(proof != null)
    }

    "resolve manyEdge-like parent and child entity evidence automatically once the spec carries both keys" in {
      val proof = summon[KeyPropagationProof[ManyEdgeSpec2[SpikeWidget, SpikeGadget, SpikeWidgetId, SpikeGadgetId]]]
      assert(proof != null)
    }

    "let the resolved entity evidence build a real Relation.All value, end to end" in {
      val entity = summon[CatalogEntity.Aux[SpikeWidget, SpikeWidgetId]]
      val relation: Relation.All[IO, SpikeWidget, SpikeWidgetId] =
        Relation.All(entity.node, AllValues[IO, SpikeWidget](() => ZIO.succeed(Nil)))

      assert(relation.node.key.label == "id")
    }

    "let the resolved entity evidence build a real Relation.HasMany value, end to end" in {
      val parentEntity = summon[CatalogEntity.Aux[SpikeWidget, SpikeWidgetId]]
      val childEntity  = summon[CatalogEntity.Aux[SpikeGadget, SpikeGadgetId]]
      val foreignKey   = RepoField.derived[SpikeGadget, SpikeWidgetId](_.widgetId)
      val loader       = ManyByKey[IO, SpikeWidgetId, SpikeGadget](_ => ZIO.succeed(Nil))

      val relation: Relation.HasMany[IO, SpikeWidget, SpikeWidgetId, SpikeGadget, SpikeGadgetId] =
        Relation.HasMany(parentEntity.node, childEntity.node, foreignKey, loader)

      assert(relation.parent.key.label == "id")
      assert(relation.child.key.label == "id")
      assert(relation.foreignKey.label == "widgetId")
    }
  }

  "Candidate B (parameterized CatalogEntity2[A, K])" should {
    "resolve once the spec carries the key, same as Candidate A - but this alone is not a fair test" in {
      // CatalogEntity2 (a different encoding from CatalogEntity.Aux) resolves
      // when the spec is ALSO fixed (RootAllSpec2 carries K) - the same
      // precondition Candidate A needed.
      val proof = summon[KeyPropagationProof2[RootAllSpec2[SpikeWidget, SpikeWidgetId]]]
      assert(proof != null)

      // A throwaway scratch check during this spike also confirmed:
      // `summon[KeyPropagationProof2[RootAllSpecCurrent[SpikeWidget]]]` (the
      // UNFIXED spec, free K) resolves too, via this file's trivial
      // `CatalogEntity2.fromMirror` given - it does NOT fail the way
      // Candidate A's broken shape does. That is not a point in Candidate
      // B's favor: `fromMirror` never checks `K` against anything, so it
      // "succeeds" unconditionally, proving nothing about whether a real,
      // validating parameterized entity evidence would fare any better - see
      // the scaladoc above `CatalogEntity2`: a fair validating Candidate B
      // would require a separate macro implementation file and was not
      // assessed here. Candidate B is left genuinely unresolved, not
      // disproven.
    }
  }
}
