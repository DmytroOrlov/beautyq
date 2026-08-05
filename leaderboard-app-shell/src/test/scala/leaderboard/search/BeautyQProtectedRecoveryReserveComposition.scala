package leaderboard.search

import leaderboard.search.beautyq.gen2.eval.{BeautyQEvaluationCorpus, BeautyQProtectedRecoveryReserve}

import scala.io.Source

object BeautyQProtectedRecoveryReserveComposition {
  final class Loaded private[search] (
    val recovery: BeautyQProtectedRecoveryReserve,
  )

  def load(): Either[String, Loaded] = {
    val reader = classpathReader()
    for {
      recovery <- BeautyQProtectedRecoveryReserve.load(reader)
      catalog <- BeautyQCanonicalSeedEvaluationCatalog.load().left.map(_ => "canonical_catalog_unavailable")
      _ <- validateCatalogSurfaces(recovery.judgedReserve.corpus, catalog)
      _ <- validateCatalogFingerprint(recovery.selectionAudit.canonicalCatalogFingerprint, catalog)
    } yield new Loaded(recovery)
  }

  private def classpathReader(): BeautyQProtectedRecoveryReserve.ResourceReader = { path =>
    val stream = Option(getClass.getClassLoader.getResourceAsStream(path))
    if (stream.isEmpty) Left(s"resource_not_found: $path")
    else {
      try Right(Source.fromInputStream(stream.get, "UTF-8").mkString)
      finally try { stream.get.close() } catch { case _: Exception => () }
    }
  }

  private[search] def validateCatalogSurfaces(
    corpus: BeautyQEvaluationCorpus,
    catalog: BeautyQCanonicalSeedEvaluationCatalog.Loaded,
  ): Either[String, Unit] = {
    val invalidVariants = corpus.cases.flatMap { c =>
      c.variantJudgments.acceptableIds.filterNot(catalog.variantResultIds.contains) ++
        c.variantJudgments.forbiddenIds.filterNot(catalog.variantResultIds.contains) ++
        c.variantJudgments.neutralIds.filterNot(catalog.variantResultIds.contains) ++
        c.variantJudgments.gradedGains.map(_.id).filterNot(catalog.variantResultIds.contains)
    }
    val invalidProviders = corpus.cases.flatMap { c =>
      c.providerJudgments.acceptableIds.filterNot(catalog.providerResultIds.contains) ++
        c.providerJudgments.forbiddenIds.filterNot(catalog.providerResultIds.contains) ++
        c.providerJudgments.neutralIds.filterNot(catalog.providerResultIds.contains) ++
        c.providerJudgments.gradedGains.map(_.id).filterNot(catalog.providerResultIds.contains)
    }
    val invalidServiceIntents = corpus.cases.flatMap { c =>
      c.serviceIntentJudgments.acceptableIds.filterNot(catalog.serviceIntentResultIds.contains) ++
        c.serviceIntentJudgments.forbiddenIds.filterNot(catalog.serviceIntentResultIds.contains) ++
        c.serviceIntentJudgments.neutralIds.filterNot(catalog.serviceIntentResultIds.contains) ++
        c.serviceIntentJudgments.gradedGains.map(_.id).filterNot(catalog.serviceIntentResultIds.contains)
    }
    if (invalidVariants.nonEmpty) Left(s"${invalidVariants.size} invalid variant identities in judged reserve")
    else if (invalidProviders.nonEmpty) Left(s"${invalidProviders.size} invalid provider identities in judged reserve")
    else if (invalidServiceIntents.nonEmpty) Left(s"${invalidServiceIntents.size} invalid service intent identities in judged reserve")
    else Right(())
  }

  private[search] def validateCatalogFingerprint(
    auditFingerprint: String,
    catalog: BeautyQCanonicalSeedEvaluationCatalog.Loaded,
  ): Either[String, Unit] =
    Either.cond(catalog.sourceFingerprint.value == auditFingerprint, (), "canonical_catalog_fingerprint_mismatch")
}
