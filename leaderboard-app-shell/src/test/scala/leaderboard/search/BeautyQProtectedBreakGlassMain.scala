package leaderboard.search

import leaderboard.search.beautyq.gen2.eval.BeautyQProtectedBreakGlassDisclosure

import java.nio.file.{Files, Path, Paths}
import scala.annotation.tailrec

/** Manual, one-shot, check-scoped disclosure owner for the authorised Q2 break-glass run. */
object BeautyQProtectedBreakGlassMain {
  final class Arguments private[search] (
    val protectedCorpus: Path,
    val protectedPolicy: Path,
    val runRoot: Path,
    val applicationRevision: String,
    val expectedFailedCheck: String,
    val authorizationId: String,
  ) {
    val aggregateOutput: Path = runRoot.resolve("aggregate-safe")
    val disclosureOutput: Path = runRoot.resolve("beautyq-protected-break-glass-disclosure.json")
  }

  private val OptionNames = Vector(
    "--protected-corpus",
    "--protected-policy",
    "--run-root",
    "--application-revision",
    "--expected-failed-check",
    "--authorization-id",
  )
  private val Allowed = OptionNames.toSet

  def parseArguments(args: Vector[String]): Either[String, Arguments] = {
    if (args.size != OptionNames.size * 2 || args.size % 2 != 0) Left("PRODUCT_INPUT_REQUIRED")
    else {
      val pairs = args.grouped(2).toVector
      val decoded = pairs.foldLeft[Either[String, Vector[(String, String)]]](Right(Vector.empty)) {
        case (acc, Vector(key, value)) => acc.flatMap { current =>
          if (!Allowed.contains(key) || value.isEmpty || value.trim != value) Left("INVALID_ARGUMENTS")
          else Right(current :+ (key -> value))
        }
        case _ => Left("INVALID_ARGUMENTS")
      }
      decoded.flatMap { values =>
        val keys = values.map(_._1)
        val byName = values.toMap
        if (keys.distinct.size != keys.size || keys.toSet != Allowed) Left("INVALID_ARGUMENTS")
        else for {
          corpus <- byName.get("--protected-corpus").toRight("PRODUCT_INPUT_REQUIRED")
          policy <- byName.get("--protected-policy").toRight("PRODUCT_INPUT_REQUIRED")
          runRoot <- byName.get("--run-root").toRight("PRODUCT_INPUT_REQUIRED")
          revision <- byName.get("--application-revision").toRight("PRODUCT_INPUT_REQUIRED")
          failed <- byName.get("--expected-failed-check").toRight("PRODUCT_INPUT_REQUIRED")
          authorization <- byName.get("--authorization-id").toRight("PRODUCT_INPUT_REQUIRED")
          _ <- Either.cond(BeautyQSearchGen2EvaluationResourceHarness.isValidApplicationRevision(revision), (), "INVALID_ARGUMENTS")
          record <- BeautyQProtectedBreakGlassDisclosure.authorizationById(authorization).toRight("BREAK_GLASS_AUTHORIZATION_MISMATCH")
          _ <- Either.cond(failed == record.failedCheckCode, (), "BREAK_GLASS_AUTHORIZATION_MISMATCH")
          _ <- Either.cond(revision == record.applicationRevision, (), "BREAK_GLASS_AUTHORIZATION_MISMATCH")
        } yield new Arguments(Paths.get(corpus), Paths.get(policy), Paths.get(runRoot), revision, failed, authorization)
      }
    }
  }

  private[search] def validateDestinations(arguments: Arguments, repositoryRoot: Path): Either[String, Unit] = {
    val permittedRoot = repositoryRoot.resolve("target/search-gen2/q2-break-glass").toAbsolutePath.normalize
    val runRoot = arguments.runRoot.toAbsolutePath.normalize
    val aggregateOutput = arguments.aggregateOutput.toAbsolutePath.normalize
    val disclosureOutput = arguments.disclosureOutput.toAbsolutePath.normalize
    if (!runRoot.startsWith(permittedRoot) || runRoot == permittedRoot) Left("invalid_break_glass_run_root")
    else if (Files.exists(runRoot)) Left("break_glass_run_root_already_exists")
    else if (!aggregateOutput.startsWith(runRoot) || !disclosureOutput.startsWith(runRoot)) Left("invalid_break_glass_output_path")
    else if (aggregateOutput == disclosureOutput) Left("invalid_break_glass_output_path")
    else Right(())
  }

  private[search] def locateRepositoryRoot(start: Path): Either[String, Path] = {
    @tailrec
    def loop(current: Path): Either[String, Path] =
      if (Files.isDirectory(current.resolve(".git")) && Files.isRegularFile(current.resolve("build.sbt"))) Right(current)
      else Option(current.getParent) match {
        case Some(parent) => loop(parent)
        case None => Left("repository_root_unavailable")
      }

    loop(start.toAbsolutePath.normalize)
  }

  def main(args: Array[String]): Unit = {
    val arguments = parseArguments(args.toVector) match {
      case Right(value) => value
      case Left(error) => throw new IllegalArgumentException(error)
    }
    val repositoryRoot = locateRepositoryRoot(Paths.get("")) match {
      case Right(value) => value
      case Left(error) => throw new IllegalStateException(error)
    }
    validateDestinations(arguments, repositoryRoot) match {
      case Right(()) => ()
      case Left(error) => throw new IllegalArgumentException(error)
    }
    val execution = BeautyQSearchGen2EvaluationResourceHarness.executeProtectedForBreakGlass(
      arguments.protectedCorpus,
      arguments.protectedPolicy,
      arguments.aggregateOutput,
      arguments.applicationRevision,
    ) match {
      case Right(value) => value
      case Left(error) => throw new IllegalStateException(error)
    }
    val disclosure = BeautyQProtectedBreakGlassDisclosure.derive(
      execution.acceptance,
      execution.protectedRun.report,
      execution.protectedCorpus.corpus,
      execution.protectedPolicy,
      arguments.applicationRevision,
      arguments.authorizationId,
      arguments.expectedFailedCheck,
    ) match {
      case Right(value) => value
      case Left(error) => throw new IllegalStateException(error)
    }
    BeautyQSearchGen2EvaluationResourceHarness.writeArtifact(arguments.disclosureOutput, disclosure.toJson)
    println(
      s"Q2_BREAK_GLASS_DISCLOSURE_READY disclosedCaseCount=${disclosure.disclosedCases.size} " +
        s"failedCheck=${arguments.expectedFailedCheck}"
    )
  }
}
