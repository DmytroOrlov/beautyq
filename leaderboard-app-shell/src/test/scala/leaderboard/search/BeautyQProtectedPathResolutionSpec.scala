package leaderboard.search

import org.scalatest.wordspec.AnyWordSpec

import java.nio.file.{Files, Paths}

final class BeautyQProtectedPathResolutionSpec extends AnyWordSpec {
  "BeautyQProtectedPathResolution" should {
    "locate the repository root from a nested working directory" in {
      val root = BeautyQProtectedPathResolution.locateRepositoryRoot(Paths.get(".").toAbsolutePath.normalize)
      assert(Files.isRegularFile(root.resolve("build.sbt")))
      val nested = root.resolve("leaderboard-app-shell").resolve("target")
      val found = BeautyQProtectedPathResolution.locateRepositoryRoot(nested)
      assert(found == root)
    }

    "resolve a relative protected policy path from the repository root" in {
      val root = BeautyQProtectedPathResolution.locateRepositoryRoot(Paths.get(".").toAbsolutePath.normalize)
      val relative = Paths.get(
        "beautyq-search-gen2-eval/src/test/resources/leaderboard/search/beautyq/gen2/eval/protected/beautyq-protected-acceptance-policy-v1.json"
      )
      val resolved = BeautyQProtectedPathResolution.resolveFrom(root, relative)
      assert(resolved == root.resolve(relative))
      assert(resolved.endsWith(
        "beautyq-search-gen2-eval/src/test/resources/leaderboard/search/beautyq/gen2/eval/protected/beautyq-protected-acceptance-policy-v1.json"
      ))
      assert(!resolved.toString.contains("leaderboard-app-shell"))
    }

    "resolve a relative protected corpus path from the repository root" in {
      val root = BeautyQProtectedPathResolution.locateRepositoryRoot(Paths.get(".").toAbsolutePath.normalize)
      val relative = Paths.get(
        "beautyq-search-gen2-eval/src/test/resources/leaderboard/search/beautyq/gen2/eval/protected/beautyq-protected-holdout-v1.json"
      )
      val resolved = BeautyQProtectedPathResolution.resolveFrom(root, relative)
      assert(resolved == root.resolve(relative))
      assert(resolved.endsWith(
        "beautyq-search-gen2-eval/src/test/resources/leaderboard/search/beautyq/gen2/eval/protected/beautyq-protected-holdout-v1.json"
      ))
      assert(!resolved.toString.contains("leaderboard-app-shell"))
    }

    "resolve a repository-relative output-dir to repository-root target" in {
      val root = BeautyQProtectedPathResolution.locateRepositoryRoot(Paths.get(".").toAbsolutePath.normalize)
      val relative = Paths.get("target/search-gen2/protected")
      val resolved = BeautyQProtectedPathResolution.resolveFrom(root, relative)
      assert(resolved == root.resolve("target/search-gen2/protected"))
      assert(!resolved.toString.contains("leaderboard-app-shell"))
    }

    "preserve absolute paths with normalization" in {
      val root = BeautyQProtectedPathResolution.locateRepositoryRoot(Paths.get(".").toAbsolutePath.normalize)
      val absolute = Paths.get("/tmp/some-artifact.json")
      val resolved = BeautyQProtectedPathResolution.resolveFrom(root, absolute)
      assert(resolved == absolute.normalize)
      assert(resolved.isAbsolute)
    }

    "resolve a relative path from a non-existent root without touching filesystem" in {
      val artificial = Paths.get("/nonexistent-root-12345")
      val relative = Paths.get("some/path.json")
      val resolved = BeautyQProtectedPathResolution.resolveFrom(artificial, relative)
      assert(resolved == artificial.resolve(relative).normalize)
    }
  }
}
