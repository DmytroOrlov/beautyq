package leaderboard.plugins

import distage.{ModuleDef, TagKK}
import izumi.functional.bio.Error2
import leaderboard.model.QueryFailure
import leaderboard.search.document.{BeautySearchCatalogSnapshot, BeautySearchReadyCatalogDocuments, VariantSearchDocumentBuilder}
import leaderboard.search.dsl.{BeautySearchSpec, BeautySearchSpecV1}
import leaderboard.search.elasticsearch.{
  ElasticsearchJsonClient,
  ElasticsearchProductionReadinessState,
  ElasticsearchSeedIndexReadiness,
  ElasticsearchSeedLifecycleMetadata,
  ElasticsearchSeedSearchComposition,
}
import leaderboard.search.inmemory.InMemorySearchBackend
import leaderboard.search.parser.BeautySearchIntentParser
import leaderboard.search.{BeautySearchBackend, BeautySearchService}
import leaderboard.seed.BeautyQSeedLoader
import zio.{IO, Runtime, Unsafe}

object BeautySearchCatalogBackendModules {
  def seedResourceInMemory[F[+_, +_]: TagKK: Error2]: ModuleDef = new ModuleDef {
    make[BeautySearchSpec].fromValue(BeautySearchSpecV1.spec)
    make[BeautySearchIntentParser].from((spec: BeautySearchSpec) => new BeautySearchIntentParser(spec))
    make[BeautySearchReadyCatalogDocuments].from {
      (loader: BeautyQSeedLoader) =>
        BeautySearchCatalogBackendFactory.fromSeedLoader(loader) match {
          case Right(value) => value
          case Left(error) => throw new IllegalStateException(error.message)
        }
    }
    make[BeautySearchBackend[F]].from {
      (spec: BeautySearchSpec, ready: BeautySearchReadyCatalogDocuments) =>
        BeautySearchCatalogBackendFactory.backend[F](spec, ready)
    }
    make[BeautySearchService[F]].from {
      (parser: BeautySearchIntentParser, backend: BeautySearchBackend[F]) =>
        new BeautySearchService.Impl[F](parser, backend)
    }
  }

  def seedResourceElasticsearch: ModuleDef = new ModuleDef {
    make[BeautySearchSpec].fromValue(BeautySearchSpecV1.spec)

    make[BeautySearchReadyCatalogDocuments].from {
      (loader: BeautyQSeedLoader) =>
        BeautySearchCatalogBackendFactory.fromSeedLoader(loader) match {
          case Right(value) => value
          case Left(error)  => throw new IllegalStateException(error.message)
        }
    }

    make[ElasticsearchSeedSearchComposition].from {
      (
        spec: BeautySearchSpec,
        client: ElasticsearchJsonClient,
        ready: BeautySearchReadyCatalogDocuments,
      ) =>
        Unsafe.unsafe { implicit unsafe =>
          Runtime.default.unsafe.run(ElasticsearchSeedSearchComposition.build(spec, client, ready)).getOrThrowFiberFailure()
        }
    }

    make[ElasticsearchSeedIndexReadiness].from {
      (composition: ElasticsearchSeedSearchComposition) => composition.readiness
    }

    make[ElasticsearchSeedLifecycleMetadata].from {
      (composition: ElasticsearchSeedSearchComposition) => composition.lifecycleMetadata
    }

    make[ElasticsearchProductionReadinessState].from {
      (composition: ElasticsearchSeedSearchComposition) => composition.productionReadinessState
    }

    make[BeautySearchBackend[IO]].from {
      (composition: ElasticsearchSeedSearchComposition) => composition.backend
    }

    make[BeautySearchService[IO]].from {
      (composition: ElasticsearchSeedSearchComposition) => composition.service
    }
  }
}

object BeautySearchCatalogBackendFactory {
  val SeedResourceLoaderSource: String = "seed-resource-loader"

  def fromSeedLoader(loader: BeautyQSeedLoader): Either[QueryFailure, BeautySearchReadyCatalogDocuments] =
    for {
      seed <- loader.load()
      documents <- VariantSearchDocumentBuilder.build(BeautySearchCatalogSnapshot.fromSeedData(seed))
      ready <- BeautySearchReadyCatalogDocuments.from(SeedResourceLoaderSource, documents)
    } yield ready

  def backend[F[+_, +_]: Error2](
    spec: BeautySearchSpec,
    ready: BeautySearchReadyCatalogDocuments,
  ): BeautySearchBackend[F] =
    new InMemorySearchBackend[F](spec, ready.documents)
}
