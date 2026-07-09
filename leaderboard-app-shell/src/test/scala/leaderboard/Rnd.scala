package leaderboard

import izumi.functional.bio.{F, IO2}
import leaderboard.model.UuidBackedId
import org.scalacheck.Gen.Parameters
import org.scalacheck.{Arbitrary, Prop}

trait Rnd[F[_, _]] {
  def apply[A: Arbitrary]: F[Nothing, A]
}

object Rnd {
  class Impl[F[+_, +_]: IO2] extends Rnd[F] {
    def apply[A: Arbitrary]: F[Nothing, A] = {
      F.sync {
        val (p, s) = Prop.startSeed(Parameters.default)
        Arbitrary.arbitrary[A].pureApply(p, s)
      }
    }
  }
}

// BeautyQ ids are opaque wrappers around UUID (not transparent aliases), so
// scalacheck's own `Arbitrary[UUID]` no longer satisfies `Arbitrary[CategoryId]`
// etc. by itself. One generic instance, derived from `UuidBackedId[A]`
// evidence (beautyq-model), replaces a per-id `Arbitrary` declaration.
// Declared at the top level of package `leaderboard` (not nested in `object
// Rnd`) so every test file that also declares `package leaderboard` finds it
// automatically, the same way it already finds `Rnd`/`rnd` unqualified.
given [A](using id: UuidBackedId[A]): Arbitrary[A] =
  Arbitrary(Arbitrary.arbitrary[java.util.UUID].map(id.apply))
