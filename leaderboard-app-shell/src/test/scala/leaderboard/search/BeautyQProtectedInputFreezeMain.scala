package leaderboard.search

import leaderboard.search.beautyq.gen2.eval.{
  BeautyQEvaluationCorpus,
  BeautyQProtectedAcceptancePolicy,
  BeautyQProtectedAcceptancePolicyError,
  BeautyQProtectedEvaluationCorpus,
  BeautyQProtectedEvaluationCorpusError,
  BeautyQProtectedInputAudit,
}

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale
import scala.annotation.tailrec

/** Manual, non-discovered, no-search owner that validates and freezes protected inputs. */
object BeautyQProtectedInputFreezeMain {
  final class Arguments private[search] (
    val protectedCorpus: Path,
    val protectedPolicy: Path,
    val authorDraft: Path,
    val judgedDraft: Path,
    val auditOutput: Path,
    val sourceRevision: String,
    val authorPassId: String,
    val judgePassId: String,
    val auditPassId: String,
  )

  final class FreezeSummary private[search] (
    val sourceRevision: String,
    val protectedCorpusFingerprint: String,
    val protectedPolicyFingerprint: String,
    val protectedCaseCount: Int,
    val protectedCorpusSha256: String,
    val protectedPolicySha256: String,
    val authorDraftSha256: String,
    val judgedDraftSha256: String,
    val canonicalSourceFingerprint: String,
    val auditOutput: Path,
  ) {
    def successLine: String =
      s"PROTECTED_INPUTS_FROZEN sourceRevision=$sourceRevision " +
        s"protectedCorpusFingerprint=$protectedCorpusFingerprint " +
        s"protectedPolicyFingerprint=$protectedPolicyFingerprint " +
        s"protectedCaseCount=$protectedCaseCount " +
        s"protectedCorpusSha256=$protectedCorpusSha256 " +
        s"protectedPolicySha256=$protectedPolicySha256 " +
        s"authorDraftSha256=$authorDraftSha256 judgedDraftSha256=$judgedDraftSha256 " +
        s"canonicalSourceFingerprint=$canonicalSourceFingerprint " +
        s"auditOutput=${auditOutput.toString}"
  }

  private val RevisionPattern = "^[0-9a-f]{40}$".r
  private val OptionNames = Vector(
    "--protected-corpus",
    "--protected-policy",
    "--author-draft",
    "--judged-draft",
    "--audit-output",
    "--source-revision",
    "--author-pass-id",
    "--judge-pass-id",
    "--audit-pass-id",
  )
  private val AllowedOptions = OptionNames.toSet
  private val ExpectedAuditOutput = Paths.get("target/search-gen2/private/beautyq-protected-input-audit-v2.json")

  def parseArguments(args: Vector[String]): Either[String, Arguments] = {
    if (args.size != OptionNames.size * 2 || args.size % 2 != 0) Left("invalid_argument_count")
    else {
      val pairs = args.grouped(2).toVector
      val decoded = pairs.foldLeft[Either[String, Vector[(String, String)]]](Right(Vector.empty)) {
        case (acc, Vector(key, value)) => acc.flatMap { current =>
          if (!AllowedOptions.contains(key)) Left("unknown_or_positional_argument")
          else if (value.isEmpty || value.trim != value) Left("invalid_argument_value")
          else Right(current :+ (key -> value))
        }
        case _ => Left("invalid_argument_count")
      }

      decoded.flatMap { values =>
        val keys = values.map(_._1)
        if (keys.distinct.size != keys.size) Left("duplicate_argument")
        else if (keys.toSet != AllowedOptions) Left("missing_argument")
        else {
          val byName = values.toMap
          val required = OptionNames.foldLeft[Either[String, Vector[String]]](Right(Vector.empty)) {
            case (acc, name) => acc.flatMap(current => byName.get(name).toRight("missing_argument").map(current :+ _))
          }
          required.flatMap {
            case Vector(corpus, policy, authorDraft, judgedDraft, output, sourceRevision, authorPassId, judgePassId, auditPassId) =>
              val passIds = Vector(authorPassId, judgePassId, auditPassId)
              if (!RevisionPattern.matches(sourceRevision)) Left("invalid_source_revision")
              else if (passIds.distinct.size != passIds.size) Left("duplicate_pass_identity")
              else Right(new Arguments(
                Paths.get(corpus), Paths.get(policy), Paths.get(authorDraft), Paths.get(judgedDraft), Paths.get(output), sourceRevision,
                authorPassId, judgePassId, auditPassId,
              ))
            case _ => Left("missing_argument")
          }
        }
      }
    }
  }

  def normalizeQuery(value: String): String =
    Normalizer.normalize(value, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).trim.replaceAll("\\s+", " ")

  def freeze(arguments: Arguments): Either[String, FreezeSummary] = {
    val repositoryRoot = locateRepositoryRoot(Paths.get("").toAbsolutePath.normalize)
    val resolved = new Arguments(
      resolveFrom(repositoryRoot, arguments.protectedCorpus),
      resolveFrom(repositoryRoot, arguments.protectedPolicy),
      resolveFrom(repositoryRoot, arguments.authorDraft),
      resolveFrom(repositoryRoot, arguments.judgedDraft),
      resolveFrom(repositoryRoot, arguments.auditOutput),
      arguments.sourceRevision,
      arguments.authorPassId,
      arguments.judgePassId,
      arguments.auditPassId,
    )
    freezeAt(resolved, resolveFrom(repositoryRoot, ExpectedAuditOutput), currentRevision(repositoryRoot))
  }

  private[search] def freezeAt(
    arguments: Arguments,
    permittedAuditOutput: Path,
    revisionReader: () => Either[String, String],
  ): Either[String, FreezeSummary] = {
    val expectedOutput = permittedAuditOutput.toAbsolutePath.normalize
    val actualOutput = arguments.auditOutput.toAbsolutePath.normalize
    if (actualOutput != expectedOutput) Left("invalid_audit_output_path")
    else if (Files.exists(arguments.auditOutput)) Left("audit_output_already_exists")
    else for {
      actualRevision <- revisionReader().flatMap(value => Either.cond(RevisionPattern.matches(value), value, "invalid_current_revision"))
      _ <- Either.cond(actualRevision == arguments.sourceRevision, (), "source_revision_mismatch")
      draftHashes <- draftHashes(arguments.authorDraft, arguments.judgedDraft)
      visible <- BeautyQEvaluationCorpus.loadCanonical().left.map(_ => "visible_corpus_invalid")
      policy <- BeautyQProtectedAcceptancePolicy.load(arguments.protectedPolicy).left.map(protectedPolicyErrorCode)
      protectedCorpus <- BeautyQProtectedEvaluationCorpus.load(arguments.protectedCorpus, visible, policy)
        .left.map(error => protectedCorpusErrorCode(error, arguments.protectedCorpus, policy))
      catalog <- BeautyQCanonicalSeedEvaluationCatalog.load().left.map(_ => "canonical_catalog_unavailable")
      corpusBytes <- readBytes(arguments.protectedCorpus, "protected_corpus_read_failed")
      policyBytes <- readBytes(arguments.protectedPolicy, "protected_policy_read_failed")
      corpusHash = sha256(corpusBytes)
      policyHash = sha256(policyBytes)
      exactDuplicates = duplicateCount(protectedCorpus.corpus.cases.map(_.query), visible.cases.map(_.query), identity)
      normalizedDuplicates = duplicateCount(protectedCorpus.corpus.cases.map(_.query), visible.cases.map(_.query), normalizeQuery)
      internalExactDuplicates = internalDuplicateCount(protectedCorpus.corpus.cases.map(_.query), identity)
      internalNormalizedDuplicates = internalDuplicateCount(protectedCorpus.corpus.cases.map(_.query), normalizeQuery)
      visibleCaseIds = visible.cases.map(_.caseId).toSet
      caseIdOverlap = protectedCorpus.corpus.cases.count(current => visibleCaseIds.contains(current.caseId))
      catalogCounts = validateCatalog(protectedCorpus.corpus, catalog)
      audit <- BeautyQProtectedInputAudit.create(
        arguments.sourceRevision,
        BeautyQProtectedInputAudit.CurrentAuthoringMethod,
        arguments.authorPassId,
        arguments.judgePassId,
        arguments.auditPassId,
        corpusHash,
        policyHash,
        draftHashes._1,
        draftHashes._2,
        protectedCorpus,
        policy,
        catalog.sourceFingerprint.value,
        exactDuplicates,
        normalizedDuplicates,
        caseIdOverlap,
        internalExactDuplicates,
        internalNormalizedDuplicates,
        catalogCounts.exactIntentCaseCount,
        catalogCounts.exactIntentWithoutAcceptableVariantCount,
        catalogCounts.invalidVariantJudgmentIdentityCount,
        catalogCounts.invalidProviderJudgmentIdentityCount,
        catalogCounts.invalidServiceIntentJudgmentCount,
      ).left.map(_.stableCode)
      _ <- writeNew(arguments.auditOutput, audit.toJson.noSpaces + "\n")
      written <- readString(arguments.auditOutput, "audit_read_failed")
      decoded <- BeautyQProtectedInputAudit.decodeString(written).left.map(_.stableCode)
      _ <- Either.cond(decoded == audit, (), "audit_round_trip_failed")
    } yield new FreezeSummary(
      arguments.sourceRevision,
      protectedCorpus.corpusFingerprint,
      policy.fingerprint,
      protectedCorpus.caseCount,
      corpusHash,
      policyHash,
      draftHashes._1,
      draftHashes._2,
      catalog.sourceFingerprint.value,
      arguments.auditOutput,
    )
  }

  def main(args: Array[String]): Unit = {
    val result = for {
      arguments <- parseArguments(args.toVector)
      summary <- freeze(arguments)
    } yield summary
    result match {
      case Right(summary) => println(summary.successLine)
      case Left(error) => throw new IllegalStateException(error)
    }
  }

  private def duplicateCount(
    protectedQueries: Vector[String],
    visibleQueries: Vector[String],
    normalize: String => String,
  ): Int = {
    val visible = visibleQueries.map(normalize).toSet
    protectedQueries.count(query => visible.contains(normalize(query)))
  }

  private def internalDuplicateCount(values: Vector[String], normalize: String => String): Int =
    values.zipWithIndex.count { case (value, index) => values.take(index).exists(previous => normalize(previous) == normalize(value)) }

  private[search] def internalDuplicateCountForTest(values: Vector[String], normalized: Boolean): Int =
    internalDuplicateCount(values, if (normalized) normalizeQuery else identity)

  private final case class DraftHashes(author: String, judged: String)

  private def draftHashes(authorDraft: Path, judgedDraft: Path): Either[String, DraftHashes] = for {
    authorBytes <- readNonEmptyBytes(authorDraft, "author_draft_invalid")
    judgedBytes <- readNonEmptyBytes(judgedDraft, "judged_draft_invalid")
  } yield DraftHashes(sha256(authorBytes), sha256(judgedBytes))

  private def readNonEmptyBytes(path: Path, error: String): Either[String, Array[Byte]] =
    if (!Files.isRegularFile(path)) Left(error)
    else readBytes(path, error).flatMap(bytes => Either.cond(bytes.nonEmpty, bytes, error))

  private[search] final case class CatalogCounts(
    invalidVariantJudgmentIdentityCount: Int,
    invalidProviderJudgmentIdentityCount: Int,
    invalidServiceIntentJudgmentCount: Int,
    exactIntentCaseCount: Int,
    exactIntentWithoutAcceptableVariantCount: Int,
  )

  private[search] def catalogValidationForTest(corpus: BeautyQEvaluationCorpus): Either[String, CatalogCounts] =
    BeautyQCanonicalSeedEvaluationCatalog.load().map(catalog => validateCatalog(corpus, catalog))

  private def validateCatalog(
    corpus: BeautyQEvaluationCorpus,
    catalog: BeautyQCanonicalSeedEvaluationCatalog.Loaded,
  ): CatalogCounts = {
    def invalid(ids: Vector[leaderboard.search.gen2.eval.EvaluationResultId], valid: Set[String]): Int =
      ids.count(id => !valid.contains(id.value))
    def invalidGains(gains: Vector[leaderboard.search.gen2.eval.GradedGain], valid: Set[String]): Int =
      gains.count(gain => !valid.contains(gain.id.value))
    val invalidVariants = corpus.cases.map { current =>
      val judgments = current.variantJudgments
      invalid(judgments.acceptableIds, catalog.variantResultIds.map(_.value)) + invalid(judgments.forbiddenIds, catalog.variantResultIds.map(_.value)) +
        invalid(judgments.neutralIds, catalog.variantResultIds.map(_.value)) + invalidGains(judgments.gradedGains, catalog.variantResultIds.map(_.value))
    }.sum
    val invalidProviders = corpus.cases.map { current =>
      val judgments = current.providerJudgments
      invalid(judgments.acceptableIds, catalog.providerResultIds.map(_.value)) + invalid(judgments.forbiddenIds, catalog.providerResultIds.map(_.value)) +
        invalid(judgments.neutralIds, catalog.providerResultIds.map(_.value)) + invalidGains(judgments.gradedGains, catalog.providerResultIds.map(_.value))
    }.sum
    val invalidServiceIntents = corpus.cases.map { current =>
      val judgments = current.serviceIntentJudgments
      invalid(judgments.acceptableIds, catalog.serviceIntentResultIds.map(_.value)) + invalid(judgments.forbiddenIds, catalog.serviceIntentResultIds.map(_.value)) +
        invalid(judgments.neutralIds, catalog.serviceIntentResultIds.map(_.value)) + invalidGains(judgments.gradedGains, catalog.serviceIntentResultIds.map(_.value))
    }.sum
    val exactCases = corpus.cases.filter(_.slices.exists(_.value == "exact-intent"))
    val exactWithoutVariant = exactCases.count { current =>
      current.variantJudgments.acceptableIds.forall(id => !catalog.variantResultIds.exists(_.value == id.value))
    }
    CatalogCounts(invalidVariants, invalidProviders, invalidServiceIntents, exactCases.size, exactWithoutVariant)
  }

  private def currentRevision(repositoryRoot: Path): () => Either[String, String] = () =>
    try {
      val process = new ProcessBuilder("git", "rev-parse", "HEAD")
        .directory(repositoryRoot.toFile)
        .redirectErrorStream(true)
        .start()
      val output = new String(process.getInputStream.readAllBytes(), StandardCharsets.UTF_8).trim
      val exitCode = process.waitFor()
      if (exitCode != 0 || output.isEmpty) Left("source_revision_unavailable")
      else Right(output)
    } catch {
      case _: java.io.IOException => Left("source_revision_unavailable")
      case _: InterruptedException =>
        Thread.currentThread().interrupt()
        Left("source_revision_unavailable")
    }

  private def protectedPolicyErrorCode(error: BeautyQProtectedAcceptancePolicyError): String = error match {
    case BeautyQProtectedAcceptancePolicyError.InvalidDocument(_) => "protected_policy_invalid:invalid_document"
    case BeautyQProtectedAcceptancePolicyError.UnexpectedFields(context) => s"protected_policy_invalid:unexpected_fields:$context"
    case BeautyQProtectedAcceptancePolicyError.MissingField(context) => s"protected_policy_invalid:missing_field:$context"
    case BeautyQProtectedAcceptancePolicyError.InvalidValue(context) => s"protected_policy_invalid:invalid_value:$context"
    case BeautyQProtectedAcceptancePolicyError.EmptySliceMinimums => "protected_policy_invalid:empty_slice_minimums"
    case BeautyQProtectedAcceptancePolicyError.EmptyMetricMinimums => "protected_policy_invalid:empty_metric_minimums"
    case BeautyQProtectedAcceptancePolicyError.DuplicateRequirement(context) => s"protected_policy_invalid:duplicate_requirement:$context"
  }

  private def protectedCorpusErrorCode(
    error: BeautyQProtectedEvaluationCorpusError,
    corpusPath: Path,
    policy: BeautyQProtectedAcceptancePolicy,
  ): String = error match {
    case BeautyQProtectedEvaluationCorpusError.InvalidInput(_) => "protected_corpus_invalid:invalid_input"
    case BeautyQProtectedEvaluationCorpusError.EmptyProtectedCorpus => "protected_corpus_invalid:empty"
    case BeautyQProtectedEvaluationCorpusError.ContainsNonProtectedCase => "protected_corpus_invalid:non_protected_case"
    case BeautyQProtectedEvaluationCorpusError.OverlapsVisibleCorpus => "protected_corpus_invalid:visible_case_overlap"
    case BeautyQProtectedEvaluationCorpusError.FingerprintMismatch =>
      val actual = try {
        io.circe.parser.parse(Files.readString(corpusPath, StandardCharsets.UTF_8)) match {
          case Right(json) => BeautyQEvaluationCorpus.decodeFromJson(json) match {
            case Right(corpus) => corpus.corpusFingerprint
            case Left(_) => "unavailable"
          }
          case Left(_) => "unavailable"
        }
      } catch {
        case _: java.io.IOException => "unavailable"
      }
      s"protected_corpus_invalid:fingerprint_mismatch:actual=$actual:expected=${policy.expectedCorpusFingerprint}"
    case BeautyQProtectedEvaluationCorpusError.CaseCountMismatch => "protected_corpus_invalid:case_count_mismatch"
    case BeautyQProtectedEvaluationCorpusError.MissingRequiredSlice(sliceId) => s"protected_corpus_invalid:missing_slice:$sliceId"
    case BeautyQProtectedEvaluationCorpusError.SliceCountTooSmall(sliceId) => s"protected_corpus_invalid:slice_count:$sliceId"
  }

  @tailrec
  private def locateRepositoryRoot(current: Path): Path =
    if (Files.isRegularFile(current.resolve("build.sbt"))) current
    else Option(current.getParent) match {
      case Some(parent) => locateRepositoryRoot(parent)
      case None => current
    }

  private def resolveFrom(repositoryRoot: Path, path: Path): Path =
    if (path.isAbsolute) path.normalize else repositoryRoot.resolve(path).normalize

  private def sha256(bytes: Array[Byte]): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).map(byte => f"$byte%02x").mkString

  private def readBytes(path: Path, error: String): Either[String, Array[Byte]] =
    try Right(Files.readAllBytes(path))
    catch { case _: java.io.IOException => Left(error) }

  private def readString(path: Path, error: String): Either[String, String] =
    try Right(Files.readString(path, StandardCharsets.UTF_8))
    catch { case _: java.io.IOException => Left(error) }

  private def writeNew(path: Path, value: String): Either[String, Unit] =
    try {
      Option(path.getParent).foreach(parent => Files.createDirectories(parent): Unit)
      Files.writeString(path, value, StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.CREATE_NEW)
      Right(())
    } catch {
      case _: java.nio.file.FileAlreadyExistsException => Left("audit_output_already_exists")
      case _: java.io.IOException => Left("audit_write_failed")
    }
}
