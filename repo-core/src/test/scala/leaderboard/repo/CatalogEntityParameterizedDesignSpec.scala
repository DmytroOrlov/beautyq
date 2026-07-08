package leaderboard.repo

import org.scalatest.wordspec.AnyWordSpec

// --- Fixture domain, not BeautyQ ---
final case class ParamWidgetId(value: String)
final case class ParamWidget(id: ParamWidgetId, name: String)

final case class ParamGadgetId(value: String)
final case class ParamGadget(id: ParamGadgetId, widgetId: ParamWidgetId, label: String)

/** Phase C.2b design spike - NOT a production change (historical: as of
  * Phase C3, production `RootAllSpec`/`ManyEdgeSpec` carry their output key
  * types, matching `RootAllSpec2`/`ManyEdgeSpec2` below rather than
  * `RootAllSpecCurrent`/`ManyEdgeSpecCurrent` - see
  * `BeautyQCatalogGraph.Evidence` and the roadmap's Phase C3 status).
  *
  * Fairly tests whether `ParamCatalogEntity[A, K]` (`CatalogEntity2Derivation.scala`,
  * a directly-parameterized, *validating* entity-evidence encoding - "Candidate B")
  * resolves a free key type where `CatalogEntity.Aux[A, K]` could not (Phase C.1).
  * The Phase C.2 spike could not test this fairly: a validating macro and its call
  * site cannot share one file (Dotty: "Cyclic macro dependencies"), so it could only
  * test a non-validating stand-in that "resolved" for any `K` by checking nothing.
  * `CatalogEntity2Derivation.scala` fixes that by putting the validating macro in
  * its own file, separate from the call sites below.
  *
  * Reuses the free-key/key-carrying fixture shapes already declared in
  * `CatalogEntityKeyPropagationDesignSpec.scala` (same package):
  * `RootAllSpecCurrent[A]`/`ManyEdgeSpecCurrent[P, C, K]` (the free-key shape
  * production used before Phase C3) and `RootAllSpec2[A, K]`/
  * `ManyEdgeSpec2[P, C, PK, CK]` (key-carrying "Candidate A" shape, matching
  * what production specs carry as of Phase C3).
  *
  * Result, verified empirically below: parameterizing entity evidence does not, by
  * itself, solve the free-key problem. `summon[ParamKeyPropagationProof[
  * RootAllSpecCurrent[ParamWidget]]]` and `summon[ParamKeyPropagationProof[
  * ManyEdgeSpecCurrent[ParamWidget, ParamGadget, ParamWidgetId]]]` were both tried in
  * a throwaway scratch file during this spike (not committed - a permanently
  * failing file cannot live in the compiled test suite) and both failed with the
  * identical "No given instance ... but macro expansion was stopped" symptom Phase
  * C.1 found for `CatalogEntity.Aux[A, K]`. A fully generic, validating,
  * macro-backed given cannot help Scala's implicit search pin a free key type,
  * regardless of whether the entity-evidence encoding is path-dependent-refined
  * (`CatalogEntity.Aux`) or directly parameterized (`ParamCatalogEntity`). Phase C3
  * confirmed the same limitation applies even to a direct (non-`given`) method call
  * like `rootAll`/`child` when `K`/`CK` is only bound via a `using` clause on that
  * method itself; production instead pins the key type via a type-level
  * `Mirror`-based lookup at declaration time (see `ConventionalIdKey` in
  * `RepoGraph.scala`), not by adopting Candidate B.
  */
trait ParamKeyPropagationProof[Spec]

object ParamKeyPropagationProof {

  // Free-key case: mirrors production `MaterializeOne.rootAll`'s
  // `entity: CatalogEntity.Aux[A, K]` shape, but with `ParamCatalogEntity[A, K]`.
  // K is free here - RootAllSpecCurrent[A] carries no key at all. Declaring this
  // given typechecks fine (a generic method's own `using` clause is not resolved
  // at its declaration site), but summoning it does not - see the scaladoc above.
  given rootAllCurrentWithParamEntity[A, K](using entity: ParamCatalogEntity[A, K]): ParamKeyPropagationProof[RootAllSpecCurrent[A]] =
    new ParamKeyPropagationProof[RootAllSpecCurrent[A]] {}

  // Free-child-key case: mirrors production `MaterializeOne.manyEdge`'s
  // `childEntity: CatalogEntity.Aux[C, CK]` shape - CK is free,
  // ManyEdgeSpecCurrent[P, C, K] carries only the parent key.
  given manyEdgeCurrentChildWithParamEntity[P, C, K, CK](using childEntity: ParamCatalogEntity[C, CK]): ParamKeyPropagationProof[ManyEdgeSpecCurrent[P, C, K]] =
    new ParamKeyPropagationProof[ManyEdgeSpecCurrent[P, C, K]] {}

  // Control: key-carrying specs (Candidate A's shape), using ParamCatalogEntity
  // instead of CatalogEntity.Aux. K/CK are pinned by the spec's own type
  // parameters here, so both resolve successfully - proven by the passing tests
  // below.
  given rootAllFixedWithParamEntity[A, K](using entity: ParamCatalogEntity[A, K]): ParamKeyPropagationProof[RootAllSpec2[A, K]] =
    new ParamKeyPropagationProof[RootAllSpec2[A, K]] {}

  given manyEdgeFixedWithParamEntity[P, C, PK, CK](using
    parentEntity: ParamCatalogEntity[P, PK],
    childEntity: ParamCatalogEntity[C, CK],
  ): ParamKeyPropagationProof[ManyEdgeSpec2[P, C, PK, CK]] =
    new ParamKeyPropagationProof[ManyEdgeSpec2[P, C, PK, CK]] {}
}

final class CatalogEntityParameterizedDesignSpec extends AnyWordSpec {

  // The proof is compile-time summon resolution of the exact `A` requested -
  // if `A` were not resolvable, this call site would fail to compile. `succeed`
  // is only a placeholder return value; it asserts nothing on its own.
  private def compileTimeProof[A](using A): org.scalatest.Assertion =
    succeed

  "Parameterized ParamCatalogEntity[A, K] (Candidate B) with key-carrying specs" should {
    "resolve rootAll-like entity evidence automatically, same as Candidate A" in {
      compileTimeProof[ParamKeyPropagationProof[RootAllSpec2[ParamWidget, ParamWidgetId]]]
    }

    "resolve manyEdge-like parent and child entity evidence automatically, same as Candidate A" in {
      compileTimeProof[ParamKeyPropagationProof[ManyEdgeSpec2[ParamWidget, ParamGadget, ParamWidgetId, ParamGadgetId]]]
    }
  }
}
