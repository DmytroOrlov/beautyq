package leaderboard.search

import java.nio.file.{Path, Paths}

/** Manual, non-discovered runner for private protected acceptance evidence. */
object BeautyQProtectedAcceptanceMain {
  final case class Arguments(protectedCorpus: Path, protectedPolicy: Path, outputDir: Path)

  def parseArguments(args: Vector[String]): Either[String, Arguments] = {
    val allowed = Set("--protected-corpus", "--protected-policy", "--output-dir")
    if (args.isEmpty || args.size % 2 != 0) Left("PRODUCT_INPUT_REQUIRED: expected three option/value pairs")
    else {
      val pairs = args.grouped(2).toVector
      val keys = pairs.map {
        case Vector(key, _) => key
        case _ => ""
      }
      if (keys.exists(key => !allowed.contains(key))) Left("INVALID_ARGUMENTS: unknown option")
      else if (keys.distinct.size != keys.size) Left("INVALID_ARGUMENTS: duplicate option")
      else if (pairs.exists {
        case Vector(_, value) => value.isEmpty || value.trim != value
        case _ => true
      }) Left("INVALID_ARGUMENTS: option values must be non-empty and whitespace-free")
      else {
        val values = pairs.collect { case Vector(key, value) => key -> value }.toMap
        (values.get("--protected-corpus"), values.get("--protected-policy"), values.get("--output-dir")) match {
          case (Some(corpus), Some(policy), Some(output)) =>
            Right(Arguments(Paths.get(corpus), Paths.get(policy), Paths.get(output)))
          case _ => Left("PRODUCT_INPUT_REQUIRED: use --protected-corpus <path> --protected-policy <path> --output-dir <path>")
        }
      }
    }
  }

  private[search] def resolveArgumentsFrom(repositoryRoot: Path, arguments: Arguments): Arguments =
    Arguments(
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
    val result = BeautyQSearchGen2EvaluationResourceHarness.runProtectedAcceptance(
      resolved.protectedCorpus,
      resolved.protectedPolicy,
      resolved.outputDir,
    )
    if (result.startsWith("PRODUCT_INPUT_REQUIRED") || result.startsWith("VERIFICATION BLOCKED"))
      throw new IllegalStateException(result)
    if (result == "PROTECTED_ACCEPTANCE_RED")
      throw new IllegalStateException(result)
    println(result)
  }
}
