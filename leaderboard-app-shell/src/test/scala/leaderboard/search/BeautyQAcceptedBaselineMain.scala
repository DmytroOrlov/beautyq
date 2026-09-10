package leaderboard.search

import io.circe.Json
import leaderboard.search.beautyq.gen2.eval.{BeautyQAcceptedBaselineVerifier, BeautyQAcceptedEvaluationBaseline, BeautyQAcceptedEvaluationBaselineResource, BeautyQAcceptedEvaluationBaselineResourceError, BeautyQProtectedAcceptancePolicy}
import leaderboard.search.gen2.eval.AcceptedEvaluationBaseline

import java.nio.file.{Path, Paths}

/** Manual, non-discovered bootstrap/verification runner for the accepted baseline. */
object BeautyQAcceptedBaselineMain {
  final class Arguments private[search] (
    val mode: String,
    val protectedCorpus: Path,
    val protectedPolicy: Path,
    val outputDir: Path,
  )

  def parseArguments(args: Vector[String]): Either[String, Arguments] = {
    val allowed = Set("--mode", "--protected-corpus", "--protected-policy", "--output-dir")
    if (args.isEmpty || args.size % 2 != 0) Left("INVALID_ARGUMENTS")
    else {
      val pairs = args.grouped(2).toVector
      val keys = pairs.collect { case Vector(key, _) => key }
      if (keys.exists(key => !allowed.contains(key))) Left("INVALID_ARGUMENTS")
      else if (keys.distinct.size != keys.size) Left("INVALID_ARGUMENTS")
      else if (pairs.exists {
        case Vector(_, value) => value.isEmpty || value.trim != value
        case _ => true
      }) Left("INVALID_ARGUMENTS")
      else {
        val values = pairs.collect { case Vector(key, value) => key -> value }.toMap
        (values.get("--mode"), values.get("--protected-corpus"), values.get("--protected-policy"), values.get("--output-dir")) match {
          case (Some(mode), Some(corpus), Some(policy), Some(output))
              if mode == "bootstrap" || mode == "verify" =>
            Right(new Arguments(mode, Paths.get(corpus), Paths.get(policy), Paths.get(output)))
          case _ => Left("INVALID_ARGUMENTS")
        }
      }
    }
  }

  private[search] def postExecution(
    mode: String,
    candidate: AcceptedEvaluationBaseline,
    policy: BeautyQProtectedAcceptancePolicy,
    outputDir: Path,
    canonicalLoader: () => Either[BeautyQAcceptedEvaluationBaselineResourceError, AcceptedEvaluationBaseline],
    artifactWriter: (Path, Json) => Unit,
  ): Either[String, String] = {
    val candidateJson = BeautyQAcceptedEvaluationBaseline.encodeCandidate(candidate)
    artifactWriter(outputDir.resolve("beautyq-accepted-baseline-candidate.json"), candidateJson)
    mode match {
      case "bootstrap" =>
        val protectedReportDigest = candidate.provenanceComponents.collect {
          case component if component.id.value == "protected-report-digest" => component.value
        } match {
          case Vector(value) => value
          case _ => "missing"
        }
        Right(
          s"ACCEPTED_BASELINE_CANDIDATE_READY digest=${BeautyQAcceptedEvaluationBaseline.candidateDigest(candidate)} " +
            s"corpusFingerprint=${candidate.corpusFingerprint} " +
            s"protectedPolicyFingerprint=${policy.fingerprint} protectedReportDigest=$protectedReportDigest",
        )
      case "verify" =>
        canonicalLoader() match {
          case Left(error) => Left(s"BASELINE_PROVENANCE_INVALID: ${error.code}")
          case Right(canonical) =>
            BeautyQAcceptedBaselineVerifier.verify(candidate, canonical, policy) match {
              case Left(error) => Left(s"BASELINE_PROVENANCE_INVALID: ${error.code}")
              case Right(verification) =>
                artifactWriter(outputDir.resolve("beautyq-accepted-baseline-verification.json"), verification.toJson)
                val failedCodes = verification.checks.filterNot(_.passed).map(_.code)
                if (verification.passed)
                  Right(s"ACCEPTED_BASELINE_VERIFIED candidateDigest=${verification.candidateManifestDigest} canonicalDigest=${verification.canonicalManifestDigest}")
                else Left(s"ACCEPTED_BASELINE_VERIFICATION_FAILED: ${failedCodes.mkString(",")}")
            }
        }
      case _ => Left("INVALID_ARGUMENTS")
    }
  }

  private[search] def resolveArgumentsFrom(repositoryRoot: Path, arguments: Arguments): Arguments =
    new Arguments(
      arguments.mode,
      BeautyQProtectedPathResolution.resolveFrom(repositoryRoot, arguments.protectedCorpus),
      BeautyQProtectedPathResolution.resolveFrom(repositoryRoot, arguments.protectedPolicy),
      BeautyQProtectedPathResolution.resolveFrom(repositoryRoot, arguments.outputDir),
    )

  def main(args: Array[String]): Unit = {
    val arguments = parseArguments(args.toVector) match {
      case Right(value) => value
      case Left(error) => throw new IllegalArgumentException(error)
    }
    val root = BeautyQProtectedPathResolution.locateRepositoryRoot(Paths.get("").toAbsolutePath.normalize)
    val resolved = resolveArgumentsFrom(root, arguments)
    BeautyQSearchGen2EvaluationResourceHarness.executeProtectedAcceptance(
      resolved.protectedCorpus,
      resolved.protectedPolicy,
      resolved.outputDir,
    ) match {
      case Left(error) => throw new IllegalStateException(error)
      case Right(run) =>
        val candidate = BeautyQAcceptedEvaluationBaseline.fromAcceptedProtectedRun(
          run.visible,
          run.protectedRun,
          run.protectedCorpus,
          run.protectedPolicy,
          run.acceptance,
        ) match {
          case Right(value) => value
          case Left(error) => throw new IllegalStateException(s"BASELINE_PROVENANCE_INVALID: $error")
        }
        postExecution(
          resolved.mode,
          candidate,
          run.protectedPolicy,
          resolved.outputDir,
          () => BeautyQAcceptedEvaluationBaselineResource.loadCanonical(),
          BeautyQSearchGen2EvaluationResourceHarness.writeArtifact,
        ) match {
          case Right(message) => println(message)
          case Left(error) => throw new IllegalStateException(error)
        }
    }
  }
}
