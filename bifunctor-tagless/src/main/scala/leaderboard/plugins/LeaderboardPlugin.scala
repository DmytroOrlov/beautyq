package leaderboard.plugins

import distage.StandardAxis.Repo
import distage.config.ConfigModuleDef
import distage.{Mode, ModuleDef, Scene, TagKK}
import doobie.util.transactor.Transactor
import izumi.functional.bio.Error2
import izumi.distage.plugins.PluginDef
import izumi.distage.roles.bundled.BundledRolesModule
import izumi.distage.roles.model.definition.RoleModuleDef
import izumi.fundamentals.platform.integration.PortCheck
import izumi.fundamentals.platform.versions.Version
import leaderboard.api.{BeautySearchProductionIncludedApis, BeautySearchProductionInclusionActivation, BeautySearchProductionInclusionHandle, CategoryApi, HttpApi, LadderApi, MasterApi, MasterLocationApi, MasterServiceOfferApi, MasterServiceOfferVariantApi, ProfileApi, ServiceApi}
import leaderboard.config.{ElasticsearchPortCfg, PostgresCfg, PostgresPortCfg}
import leaderboard.http.HttpServer
import leaderboard.http.tapir.{CategoryTapirEndpoints, LadderTapirEndpoints, MasterLocationTapirEndpoints, MasterServiceOfferTapirEndpoints, MasterServiceOfferVariantTapirEndpoints, MasterTapirEndpoints, ProfileTapirEndpoints, ServiceTapirEndpoints}
import leaderboard.repo.{Categories, Ladder, MasterLocations, MasterServiceOfferVariants, MasterServiceOffers, Masters, Profiles, ServiceVariantSchemas, Services}
import leaderboard.seed.{BeautyQSeedInserter, BeautyQSeedLoader, BeautyQSeedReady}
import leaderboard.services.Ranks
import leaderboard.sql.{SQL, TransactorResource}
import leaderboard.{CategoryRole, LadderRole, LeaderboardRole, MasterLocationRole, MasterRole, MasterServiceOfferRole, MasterServiceOfferVariantRole, ProfileRole, ServiceRole}
import zio.IO

import scala.concurrent.duration.*

// AI-NOTE: For distage wiring, Lifecycle resources, weak set bindings, and BIO typeclasses, see docs/LOCAL_LLM_IZUMI_DISTAGE_BIO_REFERENCE.md
object LeaderboardPlugin extends PluginDef {
  include(modules.roles[IO])
  include(modules.apiBase[IO])
  include(BeautySearchRouteModules.apiElasticsearch)
  include(modules.repoDummy[IO])
  include(modules.repoProd[IO])
  include(modules.seed[IO])
  include(modules.seedProd[IO])
  include(modules.seedTest[IO])
  include(modules.configs)
  include(modules.prodConfigs)

  object modules {
    def roles[F[+_, +_]: TagKK]: RoleModuleDef = new RoleModuleDef {
      // The `ladder` role
      makeRole[LadderRole[F]]

      // The `category` role
      makeRole[CategoryRole[F]]

      // The `service` role
      makeRole[ServiceRole[F]]

      // The `master` role
      makeRole[MasterRole[F]]

      // The `master-location` role
      makeRole[MasterLocationRole[F]]

      // The `master-service-offer` role
      makeRole[MasterServiceOfferRole[F]]

      // The `master-service-offer-variant` role
      makeRole[MasterServiceOfferVariantRole[F]]

      // The `profile` role
      makeRole[ProfileRole[F]]

      // The composite `leaderboard` role that pulls in both `ladder` & `profile` roles
      makeRole[LeaderboardRole[F]]

      // Add bundled roles: `help` & `configwriter`
      include(BundledRolesModule[F[Throwable, _]](version = Version.parse("1.0.0")))
    }

    def apiBase[F[+_, +_]: TagKK]: ModuleDef = new ModuleDef {
      // The `ladder` API
      make[LadderTapirEndpoints].fromValue(LadderTapirEndpoints)
      make[LadderApi[F]]
      // The `category` API
      make[CategoryTapirEndpoints].fromValue(CategoryTapirEndpoints)
      make[CategoryApi[F]]
      // The `service` API
      make[ServiceTapirEndpoints].fromValue(ServiceTapirEndpoints)
      make[ServiceApi[F]]
      // The `master` API
      make[MasterTapirEndpoints].fromValue(MasterTapirEndpoints)
      make[MasterApi[F]]
      // The `master-location` API
      make[MasterLocationTapirEndpoints].fromValue(MasterLocationTapirEndpoints)
      make[MasterLocationApi[F]]
      // The `master-service-offer` API
      make[MasterServiceOfferTapirEndpoints].fromValue(MasterServiceOfferTapirEndpoints)
      make[MasterServiceOfferApi[F]]
      // The `master-service-offer-variant` API
      make[MasterServiceOfferVariantTapirEndpoints].fromValue(MasterServiceOfferVariantTapirEndpoints)
      make[MasterServiceOfferVariantApi[F]]
      // The `profile` API
      make[ProfileTapirEndpoints].fromValue(ProfileTapirEndpoints)
      make[ProfileApi[F]]
      // Disabled Beauty search inclusion boundary only; not route exposure.
      make[BeautySearchProductionInclusionActivation].fromValue(BeautySearchProductionInclusionActivation.default)
      make[BeautySearchProductionInclusionHandle[F]].fromValue(BeautySearchProductionInclusionHandle.disabled[F])
      make[BeautySearchProductionIncludedApis[F]].from { (handle: BeautySearchProductionInclusionHandle[F]) =>
        BeautySearchProductionIncludedApis.fromHandle(handle)
      }

      // A set of all APIs
      many[HttpApi[F]]
        .weak[LadderApi[F]] // add ladder API as a _weak reference_
        .weak[CategoryApi[F]] // add categories API as a _weak reference_
        .weak[ServiceApi[F]] // add services API as a _weak reference_
        .weak[MasterApi[F]] // add masters API as a _weak reference_
        .weak[MasterLocationApi[F]] // add master locations API as a _weak reference_
        .weak[MasterServiceOfferApi[F]] // add master service offers API as a _weak reference_
        .weak[MasterServiceOfferVariantApi[F]] // add master service offer variants API as a _weak reference_
        .weak[ProfileApi[F]] // add profiles API as a _weak reference_

      make[HttpServer].fromResource[HttpServer.Impl[F]]

      make[Ranks[F]].from[Ranks.Impl[F]]
    }

    def api[F[+_, +_]: TagKK: Error2]: ModuleDef = new ModuleDef {
      include(apiBase[F])
      include(BeautySearchRouteModules.seedCatalogInMemory[F])
    }

    def repoDummy[F[+_, +_]: TagKK]: ModuleDef = new ModuleDef {
      tag(Repo.Dummy)

      make[Ladder[F]].fromResource[Ladder.Dummy[F]]
      make[Categories[F]].fromResource[Categories.Dummy[F]]
      make[Masters[F]].fromResource[Masters.Dummy[F]]
      make[MasterLocations[F]].fromResource[MasterLocations.Dummy[F]]
      make[MasterServiceOffers[F]].fromResource[MasterServiceOffers.Dummy[F]]
      make[ServiceVariantSchemas[F]].fromResource[ServiceVariantSchemas.Dummy[F]]
      make[MasterServiceOfferVariants[F]].fromResource[MasterServiceOfferVariants.Dummy[F]]
      make[Services[F]].fromResource[Services.Dummy[F]]
      make[Profiles[F]].fromResource[Profiles.Dummy[F]]
    }

    def repoProd[F[+_, +_]: TagKK]: ModuleDef = new ModuleDef {
      tag(Repo.Prod)

      make[Ladder[F]].fromResource[Ladder.Postgres[F]]
      make[Categories[F]].fromResource[Categories.Postgres[F]]
      make[Masters[F]].fromResource[Masters.Postgres[F]]
      make[MasterLocations[F]].fromResource[MasterLocations.Postgres[F]]
      make[MasterServiceOffers[F]].fromResource[MasterServiceOffers.Postgres[F]]
      make[ServiceVariantSchemas[F]].fromResource[ServiceVariantSchemas.Postgres[F]]
      make[MasterServiceOfferVariants[F]].fromResource[MasterServiceOfferVariants.Postgres[F]]
      make[Services[F]].fromResource[Services.Postgres[F]]
      make[Profiles[F]].fromResource[Profiles.Postgres[F]]

      make[SQL[F]].from[SQL.Impl[F]]

      make[Transactor[F[Throwable, _]]].fromResource[TransactorResource[F[Throwable, _]]]
      make[PortCheck].from(new PortCheck(3.seconds))
    }

    def seed[F[+_, +_]: TagKK]: ModuleDef = new ModuleDef {
      make[BeautyQSeedLoader].from[BeautyQSeedLoader.ResourceLoader]
      make[BeautyQSeedInserter[F]].from[BeautyQSeedInserter.Impl[F]]
    }

    def seedProd[F[+_, +_]: TagKK]: ModuleDef = new ModuleDef {
      tag(Mode.Prod)
      make[BeautyQSeedReady].fromResource[BeautyQSeedReady.Noop[F]]
    }

    def seedTest[F[+_, +_]: TagKK]: ModuleDef = new ModuleDef {
      tag(Mode.Test)
      make[BeautyQSeedReady].fromResource[BeautyQSeedReady.LoadAndInsert[F]]
    }

    val configs: ConfigModuleDef = new ConfigModuleDef {
      makeConfig[PostgresCfg]("postgres")
      makeConfig[ElasticsearchPortCfg]("elasticsearch")
    }
    val prodConfigs: ConfigModuleDef = new ConfigModuleDef {
      // only use this if Scene axis is set to Provided
      tag(Scene.Provided)

      makeConfig[PostgresPortCfg]("postgres")
    }
  }
}
