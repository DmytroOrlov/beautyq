package leaderboard.search.beautyq.gen2.contract

import leaderboard.search.gen2.contract.*
import org.scalatest.wordspec.AnyWordSpec

import java.net.URLClassLoader
import java.nio.charset.StandardCharsets
import java.nio.file.Paths

private object BeautyQInitializationOrderProbe {
  private val expectedFacetIds = Vector(FacetId("service"), FacetId("category"), FacetId("price"), FacetId("durationMinutes"))

  def verify(): Unit = {
    require(BeautyQSearchPlanPolicy.facetRegistry.ids == expectedFacetIds)
    require(BeautyQSearchDeclarations.variants.plan.sourcePrecedence == Vector("PublicRequest", "ParsedIntent"))
    require(BeautyQSearchDeclarations.structure.rootLabel == "BeautyQSearchDeclarations")
    require(BeautyQSearchDeclarations.structure.children.size == 2)
    require(BeautyQSearchDeclarations.renderStructure.startsWith("BeautyQSearchDeclarations\n├── catalog"))
  }
}

object BeautyQPolicyFirstTouchMain {
  def main(args: Array[String]): Unit = BeautyQInitializationOrderProbe.verify()
}

object BeautyQDeclarationsFirstTouchMain {
  def main(args: Array[String]): Unit = {
    require(BeautyQSearchDeclarations.structure.rootLabel == "BeautyQSearchDeclarations")
    BeautyQInitializationOrderProbe.verify()
  }
}

/** Runs each order in a separate JVM so no earlier test or singleton access can mask initialization
  * re-entry. The child mains intentionally touch only the requested first value before verifying both
  * the policy and the derived tree. */
final class BeautyQInitializationOrderSpec extends AnyWordSpec {

  private def codeSourceOf(clazz: Class[?]): Option[String] =
    Option(clazz.getProtectionDomain)
      .flatMap(domain => Option(domain.getCodeSource))
      .flatMap(source => Option(source.getLocation))
      .map(_.toURI)
      .map(Paths.get(_).toString)

  private def freshJvmClasspath: String = {
    val classLoaderEntries = getClass.getClassLoader match {
      case urls: URLClassLoader => urls.getURLs.toVector.map(_.toURI).map(Paths.get(_).toString)
      case _                    => Vector.empty
    }
    val anchorEntries = Vector(
      codeSourceOf(getClass),
      codeSourceOf(BeautyQSearchPlanPolicy.getClass),
      codeSourceOf(BeautyQSearchDeclarations.getClass),
      codeSourceOf(classOf[ConstraintPrecedenceError]),
      codeSourceOf(Class.forName("scala.Predef$")),
      codeSourceOf(Class.forName("scala.deriving.Mirror$Product")),
      codeSourceOf(classOf[org.scalatest.wordspec.AnyWordSpec]),
    ).flatten
    (anchorEntries ++ classLoaderEntries ++ System.getProperty("java.class.path").split(java.io.File.pathSeparator).toVector)
      .distinct
      .mkString(java.io.File.pathSeparator)
  }

  private def freshJvm(mainClass: String): String = {
    val javaBinary = Paths.get(System.getProperty("java.home"), "bin", "java").toString
    val process = new ProcessBuilder(javaBinary, "-cp", freshJvmClasspath, mainClass).redirectErrorStream(true).start()
    val output = new String(process.getInputStream.readAllBytes(), StandardCharsets.UTF_8)
    val exitCode = process.waitFor()
    assert(exitCode == 0, s"fresh JVM $mainClass failed with exit code $exitCode:\n$output")
    output
  }

  "BeautyQSearchPlanPolicy first touch" should {
    "initialize the exact policy and derived tree in a fresh JVM" in {
      assert(freshJvm("leaderboard.search.beautyq.gen2.contract.BeautyQPolicyFirstTouchMain").isEmpty)
    }
  }

  "BeautyQSearchDeclarations.structure first touch" should {
    "initialize the exact tree and derived policy in a fresh JVM" in {
      assert(freshJvm("leaderboard.search.beautyq.gen2.contract.BeautyQDeclarationsFirstTouchMain").isEmpty)
    }
  }
}
