package leaderboard.search.beautyq.gen2.eval

import org.scalatest.wordspec.AnyWordSpec
import leaderboard.search.gen2.eval.{AcceptedBaselineCodec, AcceptedEvaluationBaseline, EvaluationProvenanceId, ProvenanceComponent}

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import scala.util.Using

final class BeautyQAcceptedEvaluationBaselineResourceSpec extends AnyWordSpec {
  "BeautyQAcceptedEvaluationBaselineResource" should {
    "reject a missing canonical resource" in {
      val result = BeautyQAcceptedEvaluationBaselineResource.loadCanonical(
        new ClassLoader(ClassLoader.getPlatformClassLoader) {},
      )
      assert(result == Left(BeautyQAcceptedEvaluationBaselineResourceError.Missing))
    }

    "strictly decode and deterministically round-trip a synthetic aggregate-only manifest" in {
      val baseline = AcceptedEvaluationBaseline.create(
        "a" * 64,
        "metric-v1",
        "evaluation-v1",
        Vector(ProvenanceComponent.from(EvaluationProvenanceId.from("fixture").fold(error => fail(error), identity), "value").fold(error => fail(error), identity)),
        "b" * 64,
        Vector.empty,
      ).fold(error => fail(error), identity)
      val bytes = AcceptedBaselineCodec.encode(baseline).noSpaces.getBytes(StandardCharsets.UTF_8)
      val loader = resourceLoader(bytes)
      val loaded = BeautyQAcceptedEvaluationBaselineResource.loadCanonical(loader) match {
        case Right(value) => value
        case Left(error) => fail(s"expected valid resource, got ${error.code}")
      }
      assert(AcceptedBaselineCodec.encode(loaded) == AcceptedBaselineCodec.encode(baseline))
    }

    "prefer the thread context loader for the ordinary no-argument call" in {
      val original = Thread.currentThread().getContextClassLoader
      val loader = resourceLoader("not-json".getBytes(StandardCharsets.UTF_8))
      try {
        Thread.currentThread().setContextClassLoader(loader)
        assert(BeautyQAcceptedEvaluationBaselineResource.loadCanonical() ==
          Left(BeautyQAcceptedEvaluationBaselineResourceError.InvalidJson))
      } finally {
        Thread.currentThread().setContextClassLoader(original)
      }
    }

    "give an explicit loader precedence over the thread context loader" in {
      val original = Thread.currentThread().getContextClassLoader
      val contextLoader = resourceLoader("not-json".getBytes(StandardCharsets.UTF_8))
      val explicitLoader = resourceLoader("{}".getBytes(StandardCharsets.UTF_8))
      try {
        Thread.currentThread().setContextClassLoader(contextLoader)
        val result = BeautyQAcceptedEvaluationBaselineResource.loadCanonical(explicitLoader)
        result match {
          case Left(BeautyQAcceptedEvaluationBaselineResourceError.CodecFailure(_)) => ()
          case other => fail(s"expected explicit loader resource, got $other")
        }
      } finally {
        Thread.currentThread().setContextClassLoader(original)
      }
    }

    "fall back to the defining loader that resolves the promoted canonical resource" in {
      val original = Thread.currentThread().getContextClassLoader
      val defining = BeautyQAcceptedEvaluationBaselineResource.getClass.getClassLoader
      try {
        Thread.currentThread().setContextClassLoader(Option.empty[ClassLoader].orNull)
        assert(BeautyQAcceptedEvaluationBaselineResource.resolveClassLoader(None) == defining)
        val canonicalText = new String(resourceBytes(defining), StandardCharsets.UTF_8)
        assert(canonicalText.contains("\"average\" : 0E-12"))
        val result = BeautyQAcceptedEvaluationBaselineResource.loadCanonical(Option.empty[ClassLoader].orNull)
        val loaded = result match {
          case Right(value) => value
          case Left(error) => fail(s"expected the promoted canonical resource to decode, got ${error.code}")
        }
        val averages = loaded.aggregateObservations.flatMap(_._2.metricObservations).map(_.average)
        assert(averages.exists(_.toString == "0E-12"))
        assert(averages.forall(_.scale == 12))
        val encoded = AcceptedBaselineCodec.encode(loaded)
        val redecoded = AcceptedBaselineCodec.decode(encoded) match {
          case Right(value) => value
          case Left(error) => fail(s"expected deterministic re-decode, got $error")
        }
        assert(AcceptedBaselineCodec.encode(redecoded).noSpaces == encoded.noSpaces)
      } finally {
        Thread.currentThread().setContextClassLoader(original)
      }
    }

    "preserve strict decode failure without a filesystem fallback" in {
      val loader = new ClassLoader(ClassLoader.getPlatformClassLoader) {
        override def getResourceAsStream(name: String) =
          if (name == BeautyQAcceptedEvaluationBaselineResource.ResourcePath)
            new ByteArrayInputStream("{}".getBytes(StandardCharsets.UTF_8))
          else super.getResourceAsStream(name)
      }
      val result = BeautyQAcceptedEvaluationBaselineResource.loadCanonical(loader)
      result match {
        case Left(BeautyQAcceptedEvaluationBaselineResourceError.CodecFailure(code)) =>
          assert(code == "unexpected_fields" || code == "missing_or_invalid_field")
        case other => fail(s"expected strict malformed-resource error, got $other")
      }
    }

    "distinguish read failure, invalid UTF-8 and invalid JSON" in {
      val readFailure = BeautyQAcceptedEvaluationBaselineResource.loadCanonical(new ClassLoader(ClassLoader.getPlatformClassLoader) {
        override def getResourceAsStream(name: String): InputStream = new InputStream {
          override def read(): Int = -1
          override def close(): Unit = throw new IOException("close")
        }
      })
      assert(readFailure == Left(BeautyQAcceptedEvaluationBaselineResourceError.ReadFailure))

      val invalidUtf8 = BeautyQAcceptedEvaluationBaselineResource.loadCanonical(resourceLoader(Array(0xc3.toByte, 0x28)))
      assert(invalidUtf8 == Left(BeautyQAcceptedEvaluationBaselineResourceError.InvalidUtf8))

      val invalidJson = BeautyQAcceptedEvaluationBaselineResource.loadCanonical(resourceLoader("not-json".getBytes(StandardCharsets.UTF_8)))
      assert(invalidJson == Left(BeautyQAcceptedEvaluationBaselineResourceError.InvalidJson))
    }

    "expose a strict codec category for a valid JSON object with invalid fields" in {
      val result = BeautyQAcceptedEvaluationBaselineResource.loadCanonical(resourceLoader("{}".getBytes(StandardCharsets.UTF_8)))
      result match {
        case Left(BeautyQAcceptedEvaluationBaselineResourceError.CodecFailure(code)) => assert(code == "unexpected_fields")
        case other => fail(s"expected codec failure, got $other")
      }
    }
  }

  private def resourceLoader(bytes: Array[Byte]): ClassLoader = new ClassLoader(ClassLoader.getPlatformClassLoader) {
    override def getResourceAsStream(name: String): InputStream =
      new ByteArrayInputStream(bytes)
  }

  private def resourceBytes(loader: ClassLoader): Array[Byte] =
    Option(loader.getResourceAsStream(BeautyQAcceptedEvaluationBaselineResource.ResourcePath)) match {
      case None => fail(s"promoted canonical resource ${BeautyQAcceptedEvaluationBaselineResource.ResourcePath} must be on the test classpath")
      case Some(stream) =>
        Using(stream)(_.readAllBytes()).fold(error => fail(s"failed to read the promoted canonical resource: $error"), identity)
    }
}
