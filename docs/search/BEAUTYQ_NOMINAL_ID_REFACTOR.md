# BeautyQ Nominal ID Migration

## Problem

`CategoryId`, `ServiceId`, `MasterId`, `MasterLocationId`, `MasterServiceOfferId`, and
`MasterServiceOfferVariantId` were all plain `type X = UUID` aliases. Plain type aliases dealias
everywhere, so to the compiler (and to the `CatalogRelationEvidenceDerivation`/`RepoOpDerivation`
macros, which match repo methods by exact parameter/result `TypeRepr` equality) all six were the
same type. Two edges in the BeautyQ catalog graph - `Master -> MasterServiceOffer` (by `MasterId`)
and `MasterServiceOffer -> MasterServiceOfferVariant` (by `MasterServiceOfferId`) - had a sibling
repo method keyed by a *different* one of these aliases but returning the same child type
(`getMasterServiceOffersByMaster`/`getMasterServiceOffersByService`,
`getMasterServiceOfferVariantsByOffer`/`getMasterServiceOfferVariantsByLocation`). Type/signature
matching alone could not disambiguate them, so `BeautyQCatalogGraph.Evidence` kept two explicit
`CatalogMany.Aux` givens, each calling a small explicit repo companion wrapper
(`MasterServiceOffers.byMaster`, `MasterServiceOfferVariants.byOffer`) - the last hand-wiring left
in an otherwise fully-derived catalog materialization layer.

## Chosen nominal ID representation

Scala 3 `opaque type` over `UUID`, one per id. A single dependency-free `UuidBackedId[A]`
abstraction (`beautyq-model`) captures what every id needs, so each id's own companion only
supplies the two primitive operations and picks up everything else (`fromString`, the `.value`
extension, and a typeclass-search registration) by extending it:

```scala
trait UuidBackedId[A] {
  def apply(value: UUID): A
  def unwrap(id: A): UUID
  def fromString(value: String): A = apply(UUID.fromString(value))
  extension (id: A) def value: UUID = unwrap(id)
}

opaque type MasterId = UUID
object MasterId extends UuidBackedId[MasterId] {
  def apply(value: UUID): MasterId = value
  def unwrap(id: MasterId): UUID = id
  implicit val codec: Codec[MasterId] = uuidBackedIdCodec(MasterId)
  given UuidBackedId[MasterId] = this
}
```

Layer-local modules (Doobie, Tapir, Scalacheck; Circe's own story is slightly different, see
below) each derive **one** generic adapter from `UuidBackedId[A]` evidence instead of repeating a
per-id adapter body - see "Generic helper design" below.

Opaque types were preferred over a case-class wrapper because they are erased to their underlying
representation at runtime (zero boxing, zero allocation) and because unqualified `.toString`/`==`
on an opaque id already behave exactly like the underlying `UUID` (opacity only affects what the
*type checker* can see, not what the JVM runs) - every place that already rendered or compared a
raw `UUID` (SQL literals, error messages, string interpolation, `Map` keys) kept working unchanged
once wrapped, with no `.value` unwrap needed except where a collaborator's own signature still
requires a literal `UUID` (e.g. a test double whose only job is a `.toString` comparison).

`CategoryId` stayed nested inside `object Category` (`Category.CategoryId`), preserving the
existing `import leaderboard.model.Category.CategoryId` ergonomics exactly - no compatibility
alias layer was needed since the opaque type *is* the same nested member it always was, just no
longer transparent outside `object Category`'s own body.

`UserId` was deliberately left as a plain `UUID` alias: it belongs to the unrelated
leaderboard/ladder domain, never appears anywhere in `BeautyQCatalogGraph.Repositories`, and
nothing in this migration forced a decision about it.

### Scoping gotchas worth recording

Opaque type transparency is scoped to the *statement sequence that declares the opaque type*, not
global. Two consequences of that showed up while building the generic `UuidBackedId[A]` design
(both empirically found via compiler feedback, not anticipated up front):

1. **A trait cannot declare both an abstract `def value(id: A): UUID` and a concrete
   `extension (id: A) def value: UUID` in the same body.** An extension method desugars to a plain
   method with the receiver as its first parameter, so the two declarations have the identical
   erased signature - the same-body combination is a duplicate definition, not an override
   (overriding only works across an inheritance boundary). Fix: name the abstract primitive
   `unwrap`, and have the trait's own `extension (id: A) def value: UUID = unwrap(id)` be the only
   thing named `value`. Callers never see `unwrap` - `id.value` still works everywhere.
2. **A plain `object X extends UuidBackedId[X]` is not, by itself, a candidate for
   `summon[UuidBackedId[X]]`.** Extending a trait makes `X` *an instance of* that trait; it does not
   register `X` for implicit/given search (this is the same reason Scala 2's `implicit object`
   needed the `implicit` modifier, not just the `extends`). Each companion therefore also declares
   `given UuidBackedId[X] = this` - and it must be a *member of the companion itself*, not a
   sibling statement in the enclosing package object, because cross-module implicit search for
   `UuidBackedId[MasterId]` only reaches `MasterId`'s own companion-object scope (found from every
   module without an import, exactly like the pre-existing per-id codecs already were), not
   arbitrary siblings declared nearby (those are only visible to other files in the *same* package,
   e.g. other files inside `beautyq-model` itself).

A third gotcha is specific to Circe and is why Circe does **not** use a fully generic `given`:
declaring each id's own `Codec[X]` by summoning `Decoder[UUID].map(apply)` *inside that id's own
companion* self-collides, because from inside `object MasterId`'s body, `MasterId.codec` (a
`Codec[MasterId]`) is *also* reachable as a `Codec[UUID]` (opaque transparency), and it is the
highest-priority (local-scope) candidate for a `Decoder[UUID]` search happening in that same
body - so the summon finds itself and loops infinitely. Worse, a fully generic
`given [A](using UuidBackedId[A]): Codec[A]` placed anywhere in `package object model` is *also* a
candidate `UuidBackedId[UUID]` for *all six* ids simultaneously from inside that same transparent
scope, which is ambiguous rather than infinite-looping, but equally fatal. Both are consequences of
being asked to define the ids' Circe support in the very file that declares the ids themselves,
where they are not yet opaque to the code around them. The fix for both: capture
`uuidDecoder`/`uuidEncoder` *once* at the top of `package object model`, and derive each id's codec
through a **plain function** (`uuidBackedIdCodec[A](id: UuidBackedId[A]): Codec[A]`, called once per
id: `uuidBackedIdCodec(MasterId)`), never a `given`. A plain function call is not an implicit
search, so it can neither self-collide nor create cross-id ambiguity - regardless of where it's
written. Doobie/Tapir/Scalacheck don't have this problem because they live outside
`beautyq-model`, where the ids are simply opaque, non-interchangeable types like any other.

## Generic helper design

| Layer | Module | Mechanism |
|---|---|---|
| Circe | `beautyq-model` | `implicit val codec: Codec[X] = uuidBackedIdCodec(X)` per id (one line, in the id's own companion) - `uuidBackedIdCodec[A](id: UuidBackedId[A]): Codec[A]` is the single shared `Codec.from(uuidDecoder.map(id.apply), uuidEncoder.contramap(id.unwrap))` body. Not a `given`, for the opaque-scope-ambiguity reason above. Encodes/decodes exactly as a UUID string, no JSON field name or shape change. |
| Doobie | `beautyq-search-repositories` | One `given [A](using id: UuidBackedId[A]): Meta[A] = Meta[UUID].timap(id.apply)(id.unwrap)` in the `leaderboard.repo` package object, replacing all six per-id declarations. SQL columns stay `uuid`; `rootCategoryIdSqlLiteral` renders `rootCategoryId.value`. |
| Tapir | `app-http` | One `given [A](using UuidBackedId[A]): Codec[String, A, CodecFormat.TextPlain]` and one `given [A](using UuidBackedId[A]): Schema[A]` in `leaderboard.http.tapir.BeautyQIdTapirSupport` (each a `.map` adaptation of Tapir's own `Codec.uuid`/`Schema.schemaForUUID`), imported into the six BeautyQ tapir endpoint files exactly as before (`import leaderboard.http.tapir.BeautyQIdTapirSupport.given`). Route paths and JSON wire shape unchanged. |
| Scalacheck | `leaderboard-app-shell` test tree | One `given [A](using id: UuidBackedId[A]): Arbitrary[A] = Arbitrary(Arbitrary.arbitrary[java.util.UUID].map(id.apply))` at the top level of `package leaderboard` in `Rnd.scala`, replacing all six per-id declarations. |

All four fully generic designs (Doobie/Tapir/Scalacheck, plus the Circe *helper function*)
compiled and passed on the first attempt after the two scoping gotchas above were fixed; no
fallback to one-line-per-id `given`s was needed for Doobie/Tapir/Scalacheck; Circe uses a
plain-function fallback deliberately, as designed for from the start once the opaque-scope
ambiguity was understood, not as a last resort. No new dependency was added anywhere in this
cleanup.

## Removed workaround

Once the six ids were nominally distinct, `CatalogMany.derivedFromRepositories` (repo-core,
unchanged) disambiguated both previously-ambiguous edges purely by matching each repositories
field's method against the requested key type - the same `uniqueRepositoryField`/
`singleArgCandidates` exact-type-match logic that already derived every other rootTree/rootAll/
many/valueEdge relation in the catalog. This let the last explicit wiring in
`BeautyQCatalogGraph.Evidence` be deleted:

* `CatalogMany.Aux[F, Repositories[F], Master, MasterServiceOffer, MasterId]`
* `CatalogMany.Aux[F, Repositories[F], MasterServiceOffer, MasterServiceOfferVariant, MasterServiceOfferId]`
* `MasterServiceOffers.byMaster` and `MasterServiceOfferVariants.byOffer` (the repo companion
  wrappers those givens called)

No method-name fallback and no selector-guided fallback were introduced anywhere to make this
work - the derivation macros themselves did not change at all; only the id types became precise
enough for the existing type/signature matching to succeed.
`repo-core/src/test/scala/leaderboard/repo/NominalIdCatalogManyDerivationSpec.scala` proves the
general mechanism with two local opaque ids sharing a UUID representation (mirroring the exact
pre-migration shape), independent of the real BeautyQ repositories.

## Non-goals

* No change to SQL storage column types (`uuid` throughout), JSON field names/shape, or HTTP route
  paths.
* No change to catalog declaration, full-loader, or seed-loader semantics (order, canonical
  missing-entity message, manual snapshot constructor, no dedup).
* No change to `UserId`.
* No projection/runtime/DI wiring changes - `BeautyQCatalogGraph.Nodes` and the search document
  projection layer were left exactly as they were (see the handoff doc's "Recommended next patch",
  Nodes/projection boundary audit, for whether that's worth revisiting next).
