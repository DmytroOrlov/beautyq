package leaderboard

import distage.StandardAxis.Repo
import distage.plugins.PluginConfig
import distage.{Activation, Lifecycle, Module, ModuleDef}
import izumi.distage.model.definition.StandardAxis.Scene
import izumi.distage.roles.RoleAppMain
import izumi.distage.roles.bundled.{ConfigWriter, Help}
import izumi.distage.roles.model.{RoleDescriptor, RoleService}
import izumi.functional.bio.Applicative2
import izumi.fundamentals.platform.IzPlatform
import izumi.fundamentals.platform.cli.model.{EntrypointArgs, RawValue, RoleArgs}
import leaderboard.api.{CategoryApi, LadderApi, MasterApi, MasterLocationApi, MasterServiceOfferApi, MasterServiceOfferLocationApi, ProfileApi, ServiceApi}
import leaderboard.http.HttpServer
import leaderboard.plugins.{LeaderboardPlugin, PostgresDockerPlugin}
import logstage.LogIO2
import zio.IO

import scala.annotation.unused

/**
  * A role that exposes just the /ladder/ endpoints, it can be launched with
  *
  * {{{
  *   ./launcher :ladder
  * }}}
  *
  * Example session:
  *
  * {{{
  *   curl -X POST http://localhost:8080/ladder/50753a00-5e2e-4a2f-94b0-e6721b0a3cc4/100
  *   curl -X GET http://localhost:8080/ladder
  * }}}
  */
final class LadderRole[F[+_, +_]: Applicative2](
  @unused ladderApi: LadderApi[F],
  @unused runningServer: HttpServer,
  log: LogIO2[F],
) extends RoleService[F[Throwable, _]] {
  override def start(roleParameters: EntrypointArgs): Lifecycle[F[Throwable, _], Unit] = {
    Lifecycle.liftF(log.info("Ladder API started!"))
  }
}
object LadderRole extends RoleDescriptor {
  final val id = "ladder"
}

/**
  * A role that exposes just the /category/ endpoints, it can be launched with
  *
  * {{{
  *   ./launcher :category
  * }}}
  *
  * Example session:
  *
  * {{{
  *   curl -X POST http://localhost:8080/category -d '{"id":"50753a00-5e2e-4a2f-94b0-e6721b0a3cc4","parentId":"73ba445e-edf0-4ecf-a02b-91d0932e1f10","depth":0,"name":"Games"}'
  *   curl -X GET http://localhost:8080/category/73ba445e-edf0-4ecf-a02b-91d0932e1f10/children
  *   curl -X GET http://localhost:8080/category/root
  *   curl -X GET http://localhost:8080/category/50753a00-5e2e-4a2f-94b0-e6721b0a3cc4
  * }}}
  */
final class CategoryRole[F[+_, +_]: Applicative2](
  @unused categoryApi: CategoryApi[F],
  @unused runningServer: HttpServer,
  log: LogIO2[F],
) extends RoleService[F[Throwable, _]] {
  override def start(roleParameters: EntrypointArgs): Lifecycle[F[Throwable, _], Unit] = {
    Lifecycle.liftF(log.info("Category API started!"))
  }
}
object CategoryRole extends RoleDescriptor {
  final val id = "category"
}

/**
  * A role that exposes just the /service/ endpoints, it can be launched with
  *
  * {{{
  *   ./launcher :service
  * }}}
  *
  * Example session:
  *
  * {{{
  *   curl -X POST http://localhost:8080/service -d '{"id":"50753a00-5e2e-4a2f-94b0-e6721b0a3cc4","categoryId":"73ba445e-edf0-4ecf-a02b-91d0932e1f10","name":"Coaching"}'
  *   curl -X GET http://localhost:8080/service/50753a00-5e2e-4a2f-94b0-e6721b0a3cc4
  *   curl -X GET http://localhost:8080/service/category/73ba445e-edf0-4ecf-a02b-91d0932e1f10
  * }}}
  */
final class ServiceRole[F[+_, +_]: Applicative2](
  @unused serviceApi: ServiceApi[F],
  @unused runningServer: HttpServer,
  log: LogIO2[F],
) extends RoleService[F[Throwable, _]] {
  override def start(roleParameters: EntrypointArgs): Lifecycle[F[Throwable, _], Unit] = {
    Lifecycle.liftF(log.info("Service API started!"))
  }
}
object ServiceRole extends RoleDescriptor {
  final val id = "service"
}

/**
  * A role that exposes just the /master/ endpoints, it can be launched with
  *
  * {{{
  *   ./launcher :master
  * }}}
  *
  * Example session:
  *
  * {{{
  *   curl -X POST http://localhost:8080/master -d '{"id":"50753a00-5e2e-4a2f-94b0-e6721b0a3cc4","name":"Kai"}'
  *   curl -X GET http://localhost:8080/master/50753a00-5e2e-4a2f-94b0-e6721b0a3cc4
  *   curl -X GET http://localhost:8080/master
  * }}}
  */
final class MasterRole[F[+_, +_]: Applicative2](
  @unused masterApi: MasterApi[F],
  @unused runningServer: HttpServer,
  log: LogIO2[F],
) extends RoleService[F[Throwable, _]] {
  override def start(roleParameters: EntrypointArgs): Lifecycle[F[Throwable, _], Unit] = {
    Lifecycle.liftF(log.info("Master API started!"))
  }
}
object MasterRole extends RoleDescriptor {
  final val id = "master"
}

/**
  * A role that exposes just the /master-location/ endpoints, it can be launched with
  *
  * {{{
  *   ./launcher :master-location
  * }}}
  *
  * Example session:
  *
  * {{{
  *   curl -X POST http://localhost:8080/master-location -d '{"id":"50753a00-5e2e-4a2f-94b0-e6721b0a3cc4","masterId":"73ba445e-edf0-4ecf-a02b-91d0932e1f10","name":"Studio Mitte","address":"Torstrasse 1","lat":52.52,"lon":13.405}'
  *   curl -X GET http://localhost:8080/master-location/50753a00-5e2e-4a2f-94b0-e6721b0a3cc4
  *   curl -X GET http://localhost:8080/master-location/master/73ba445e-edf0-4ecf-a02b-91d0932e1f10
  * }}}
  */
final class MasterLocationRole[F[+_, +_]: Applicative2](
  @unused masterLocationApi: MasterLocationApi[F],
  @unused runningServer: HttpServer,
  log: LogIO2[F],
) extends RoleService[F[Throwable, _]] {
  override def start(roleParameters: EntrypointArgs): Lifecycle[F[Throwable, _], Unit] = {
    Lifecycle.liftF(log.info("MasterLocation API started!"))
  }
}
object MasterLocationRole extends RoleDescriptor {
  final val id = "master-location"
}

/**
  * A role that exposes just the /master-service-offer/ endpoints, it can be launched with
  *
  * {{{
  *   ./launcher :master-service-offer
  * }}}
  *
  * Example session:
  *
  * {{{
  *   curl -X POST http://localhost:8080/master-service-offer -d '{"id":"50753a00-5e2e-4a2f-94b0-e6721b0a3cc4","masterId":"73ba445e-edf0-4ecf-a02b-91d0932e1f10","serviceId":"63b53a00-5e2e-4a2f-94b0-e6721b0a3cc4"}'
  *   curl -X GET http://localhost:8080/master-service-offer/50753a00-5e2e-4a2f-94b0-e6721b0a3cc4
  *   curl -X GET http://localhost:8080/master-service-offer/master/73ba445e-edf0-4ecf-a02b-91d0932e1f10
  *   curl -X GET http://localhost:8080/master-service-offer/service/63b53a00-5e2e-4a2f-94b0-e6721b0a3cc4
  * }}}
  */
final class MasterServiceOfferRole[F[+_, +_]: Applicative2](
  @unused masterServiceOfferApi: MasterServiceOfferApi[F],
  @unused runningServer: HttpServer,
  log: LogIO2[F],
) extends RoleService[F[Throwable, _]] {
  override def start(roleParameters: EntrypointArgs): Lifecycle[F[Throwable, _], Unit] = {
    Lifecycle.liftF(log.info("MasterServiceOffer API started!"))
  }
}
object MasterServiceOfferRole extends RoleDescriptor {
  final val id = "master-service-offer"
}

/**
  * A role that exposes just the /master-service-offer-location/ endpoints, it can be launched with
  *
  * {{{
  *   ./launcher :master-service-offer-location
  * }}}
  *
  * Example session:
  *
  * {{{
  *   curl -X POST http://localhost:8080/master-service-offer-location -d '{"id":"50753a00-5e2e-4a2f-94b0-e6721b0a3cc4","masterServiceOfferId":"73ba445e-edf0-4ecf-a02b-91d0932e1f10","masterLocationId":"63b53a00-5e2e-4a2f-94b0-e6721b0a3cc4","priceFrom":50.0,"priceTo":80.0}'
  *   curl -X GET http://localhost:8080/master-service-offer-location/50753a00-5e2e-4a2f-94b0-e6721b0a3cc4
  *   curl -X GET http://localhost:8080/master-service-offer-location/offer/73ba445e-edf0-4ecf-a02b-91d0932e1f10
  *   curl -X GET http://localhost:8080/master-service-offer-location/location/63b53a00-5e2e-4a2f-94b0-e6721b0a3cc4
  * }}}
  */
final class MasterServiceOfferLocationRole[F[+_, +_]: Applicative2](
  @unused masterServiceOfferLocationApi: MasterServiceOfferLocationApi[F],
  @unused runningServer: HttpServer,
  log: LogIO2[F],
) extends RoleService[F[Throwable, _]] {
  override def start(roleParameters: EntrypointArgs): Lifecycle[F[Throwable, _], Unit] = {
    Lifecycle.liftF(log.info("MasterServiceOfferLocation API started!"))
  }
}
object MasterServiceOfferLocationRole extends RoleDescriptor {
  final val id = "master-service-offer-location"
}

/**
  * A role that exposes just the /profile/ endpoints, it can be launched with
  *
  * {{{
  *   ./launcher :profile
  * }}}
  *
  * Example session:
  *
  * {{{
  *   curl -X POST http://localhost:8080/profile/50753a00-5e2e-4a2f-94b0-e6721b0a3cc4 -d '{"name": "Kai", "description": "S C A L A"}'
  *   curl -X GET http://localhost:8080/profile/50753a00-5e2e-4a2f-94b0-e6721b0a3cc4
  * }}}
  */
final class ProfileRole[F[+_, +_]: Applicative2](
  @unused profileApi: ProfileApi[F],
  @unused runningServer: HttpServer,
  log: LogIO2[F],
) extends RoleService[F[Throwable, _]] {
  override def start(roleParameters: EntrypointArgs): Lifecycle[F[Throwable, _], Unit] = {
    Lifecycle.liftF(log.info("Profile API started!"))
  }
}
object ProfileRole extends RoleDescriptor {
  final val id = "profile"
}

/** A composite role that exposes all the endpoints, for convenience, it can be launched with
  *
  * {{{
  *   ./launcher :leaderboard
  * }}}
  *
  * Note that this will have the same effect as launching [[LadderRole]], [[CategoryRole]],
  * [[ServiceRole]], [[MasterRole]], [[MasterLocationRole]], [[MasterServiceOfferRole]],
  * [[MasterServiceOfferLocationRole]] and [[ProfileRole]] at the same time.
  *
  * {{{
  *   ./launcher :ladder :category :service :master :master-location :master-service-offer :master-service-offer-location :profile
  * }}}
  *
  * Example session:
  *
  * {{{
  *   curl -X POST http://localhost:8080/ladder/50753a00-5e2e-4a2f-94b0-e6721b0a3cc4/100
  *   curl -X POST http://localhost:8080/category -d '{"id":"50753a00-5e2e-4a2f-94b0-e6721b0a3cc4","parentId":"73ba445e-edf0-4ecf-a02b-91d0932e1f10","depth":0,"name":"Games"}'
  *   curl -X POST http://localhost:8080/service -d '{"id":"63b53a00-5e2e-4a2f-94b0-e6721b0a3cc4","categoryId":"50753a00-5e2e-4a2f-94b0-e6721b0a3cc4","name":"Coaching"}'
  *   curl -X POST http://localhost:8080/master -d '{"id":"7ab53a00-5e2e-4a2f-94b0-e6721b0a3cc4","name":"Kai"}'
  *   curl -X POST http://localhost:8080/master-location -d '{"id":"8ab53a00-5e2e-4a2f-94b0-e6721b0a3cc4","masterId":"7ab53a00-5e2e-4a2f-94b0-e6721b0a3cc4","name":"Studio Mitte","address":"Torstrasse 1","lat":52.52,"lon":13.405}'
  *   curl -X POST http://localhost:8080/master-service-offer -d '{"id":"9ab53a00-5e2e-4a2f-94b0-e6721b0a3cc4","masterId":"7ab53a00-5e2e-4a2f-94b0-e6721b0a3cc4","serviceId":"63b53a00-5e2e-4a2f-94b0-e6721b0a3cc4"}'
  *   curl -X POST http://localhost:8080/master-service-offer-location -d '{"id":"aab53a00-5e2e-4a2f-94b0-e6721b0a3cc4","masterServiceOfferId":"9ab53a00-5e2e-4a2f-94b0-e6721b0a3cc4","masterLocationId":"8ab53a00-5e2e-4a2f-94b0-e6721b0a3cc4","priceFrom":50.0,"priceTo":80.0}'
  *   curl -X GET http://localhost:8080/category/root
  *   curl -X POST http://localhost:8080/profile/50753a00-5e2e-4a2f-94b0-e6721b0a3cc4 -d '{"name": "Kai", "description": "S C A L A"}'
  *   # check leaderboard
  *   curl -X GET http://localhost:8080/ladder
  *   curl -X GET http://localhost:8080/category/73ba445e-edf0-4ecf-a02b-91d0932e1f10/children
  *   curl -X GET http://localhost:8080/service/category/50753a00-5e2e-4a2f-94b0-e6721b0a3cc4
  *   curl -X GET http://localhost:8080/master
  *   curl -X GET http://localhost:8080/master-location/master/7ab53a00-5e2e-4a2f-94b0-e6721b0a3cc4
  *   curl -X GET http://localhost:8080/master-service-offer/master/7ab53a00-5e2e-4a2f-94b0-e6721b0a3cc4
  *   curl -X GET http://localhost:8080/master-service-offer-location/offer/9ab53a00-5e2e-4a2f-94b0-e6721b0a3cc4
  *   # user profile now shows the rank in the ladder along with profile data
  *   curl -X GET http://localhost:8080/profile/50753a00-5e2e-4a2f-94b0-e6721b0a3cc4
  * }}}
  */
final class LeaderboardRole[F[+_, +_]: Applicative2](
  @unused ladderRole: LadderRole[F],
  @unused categoryRole: CategoryRole[F],
  @unused serviceRole: ServiceRole[F],
  @unused masterRole: MasterRole[F],
  @unused masterLocationRole: MasterLocationRole[F],
  @unused masterServiceOfferRole: MasterServiceOfferRole[F],
  @unused masterServiceOfferLocationRole: MasterServiceOfferLocationRole[F],
  @unused profileRole: ProfileRole[F],
  log: LogIO2[F],
) extends RoleService[F[Throwable, _]] {
  override def start(roleParameters: EntrypointArgs): Lifecycle[F[Throwable, _], Unit] = {
    Lifecycle.liftF(log.info("Ladder, Category, Service, Master, MasterLocation, MasterServiceOffer, MasterServiceOfferLocation & Profile APIs started!"))
  }
}
object LeaderboardRole extends RoleDescriptor {
  final val id = "leaderboard"
}

/**
  * Launch the service with dummy configuration.
  *
  * This will use in-memory repositories and not require an external postgres DB.
  *
  * Equivalent to:
  * {{{
  *   ./launcher -u repo:dummy :leaderboard
  * }}}
  */
object MainDummy extends MainBase(Activation(Repo -> Repo.Dummy), Vector(RoleArgs(LeaderboardRole.id)))

/**
  * Launch with production configuration and setup the required postgres DB inside docker.
  *
  * You will need docker daemon running in the background.
  *
  * Equivalent to:
  * {{{
  *   ./launcher -u scene:managed :leaderboard
  * }}}
  */
object MainProdDocker extends MainBase(Activation(Repo -> Repo.Prod, Scene -> Scene.Managed), Vector(RoleArgs(LeaderboardRole.id)))

/**
  * Launch with production configuration and external, not dockerized, services.
  *
  * You will need postgres to be available at `localhost:5432`.
  * To set it up with Docker, execute the following command:
  *
  * {{{
  *   docker run --rm -d -p 5432:5432 postgres:12.1
  * }}}
  *
  * Equivalent to:
  * {{{
  *   ./launcher :leaderboard
  * }}}
  */
object MainProd extends MainBase(Activation(Repo -> Repo.Prod, Scene -> Scene.Provided), Vector(RoleArgs(LeaderboardRole.id)))

/**
  * Launch just the `ladder` APIs with dummy repositories
  *
  * Equivalent to:
  * {{{
  *   ./launcher -u repo:dummy :ladder
  * }}}
  */
object MainLadderDummy extends MainBase(Activation(Repo -> Repo.Dummy), Vector(RoleArgs(LadderRole.id)))

/**
  * Launch just the `ladder` APIs with postgres repositories and dockerized postgres service
  *
  * Equivalent to:
  * {{{
  *   ./launcher -u scene:managed :ladder
  * }}}
  */
object MainLadderProdDocker extends MainBase(Activation(Repo -> Repo.Prod, Scene -> Scene.Managed), Vector(RoleArgs(LadderRole.id)))

/**
  * Launch just the `ladder` APIs with postgres repositories and external postgres service
  *
  * You will need postgres to be available at `localhost:5432`
  *
  * Equivalent to:
  * {{{
  *   ./launcher :ladder
  * }}}
  */
object MainLadderProd extends MainBase(Activation(Repo -> Repo.Prod, Scene -> Scene.Provided), Vector(RoleArgs(LadderRole.id)))

/**
  * Launch just the `category` APIs with dummy repositories
  *
  * Equivalent to:
  * {{{
  *   ./launcher -u repo:dummy :category
  * }}}
  */
object MainCategoryDummy extends MainBase(Activation(Repo -> Repo.Dummy), Vector(RoleArgs(CategoryRole.id)))

/**
  * Launch just the `category` APIs with postgres repositories and dockerized postgres service
  *
  * Equivalent to:
  * {{{
  *   ./launcher -u scene:managed :category
  * }}}
  */
object MainCategoryProdDocker extends MainBase(Activation(Repo -> Repo.Prod, Scene -> Scene.Managed), Vector(RoleArgs(CategoryRole.id)))

/**
  * Launch just the `category` APIs with postgres repositories and external postgres service
  *
  * Equivalent to:
  * {{{
  *   ./launcher :category
  * }}}
  */
object MainCategoryProd extends MainBase(Activation(Repo -> Repo.Prod, Scene -> Scene.Provided), Vector(RoleArgs(CategoryRole.id)))

/**
  * Launch just the `service` APIs with dummy repositories
  *
  * Equivalent to:
  * {{{
  *   ./launcher -u repo:dummy :service
  * }}}
  */
object MainServiceDummy extends MainBase(Activation(Repo -> Repo.Dummy), Vector(RoleArgs(ServiceRole.id)))

/**
  * Launch just the `service` APIs with postgres repositories and dockerized postgres service
  *
  * Equivalent to:
  * {{{
  *   ./launcher -u scene:managed :service
  * }}}
  */
object MainServiceProdDocker extends MainBase(Activation(Repo -> Repo.Prod, Scene -> Scene.Managed), Vector(RoleArgs(ServiceRole.id)))

/**
  * Launch just the `service` APIs with postgres repositories and external postgres service
  *
  * Equivalent to:
  * {{{
  *   ./launcher :service
  * }}}
  */
object MainServiceProd extends MainBase(Activation(Repo -> Repo.Prod, Scene -> Scene.Provided), Vector(RoleArgs(ServiceRole.id)))

/**
  * Launch just the `master` APIs with dummy repositories
  *
  * Equivalent to:
  * {{{
  *   ./launcher -u repo:dummy :master
  * }}}
  */
object MainMasterDummy extends MainBase(Activation(Repo -> Repo.Dummy), Vector(RoleArgs(MasterRole.id)))

/**
  * Launch just the `master` APIs with postgres repositories and dockerized postgres service
  *
  * Equivalent to:
  * {{{
  *   ./launcher -u scene:managed :master
  * }}}
  */
object MainMasterProdDocker extends MainBase(Activation(Repo -> Repo.Prod, Scene -> Scene.Managed), Vector(RoleArgs(MasterRole.id)))

/**
  * Launch just the `master` APIs with postgres repositories and external postgres service
  *
  * Equivalent to:
  * {{{
  *   ./launcher :master
  * }}}
  */
object MainMasterProd extends MainBase(Activation(Repo -> Repo.Prod, Scene -> Scene.Provided), Vector(RoleArgs(MasterRole.id)))

/**
  * Launch just the `master-location` APIs with dummy repositories
  *
  * Equivalent to:
  * {{{
  *   ./launcher -u repo:dummy :master-location
  * }}}
  */
object MainMasterLocationDummy extends MainBase(Activation(Repo -> Repo.Dummy), Vector(RoleArgs(MasterLocationRole.id)))

/**
  * Launch just the `master-location` APIs with postgres repositories and dockerized postgres service
  *
  * Equivalent to:
  * {{{
  *   ./launcher -u scene:managed :master-location
  * }}}
  */
object MainMasterLocationProdDocker extends MainBase(Activation(Repo -> Repo.Prod, Scene -> Scene.Managed), Vector(RoleArgs(MasterLocationRole.id)))

/**
  * Launch just the `master-location` APIs with postgres repositories and external postgres service
  *
  * Equivalent to:
  * {{{
  *   ./launcher :master-location
  * }}}
  */
object MainMasterLocationProd extends MainBase(Activation(Repo -> Repo.Prod, Scene -> Scene.Provided), Vector(RoleArgs(MasterLocationRole.id)))

/**
  * Launch just the `master-service-offer` APIs with dummy repositories
  *
  * Equivalent to:
  * {{{
  *   ./launcher -u repo:dummy :master-service-offer
  * }}}
  */
object MainMasterServiceOfferDummy extends MainBase(Activation(Repo -> Repo.Dummy), Vector(RoleArgs(MasterServiceOfferRole.id)))

/**
  * Launch just the `master-service-offer` APIs with postgres repositories and dockerized postgres service
  *
  * Equivalent to:
  * {{{
  *   ./launcher -u scene:managed :master-service-offer
  * }}}
  */
object MainMasterServiceOfferProdDocker extends MainBase(Activation(Repo -> Repo.Prod, Scene -> Scene.Managed), Vector(RoleArgs(MasterServiceOfferRole.id)))

/**
  * Launch just the `master-service-offer` APIs with postgres repositories and external postgres service
  *
  * Equivalent to:
  * {{{
  *   ./launcher :master-service-offer
  * }}}
  */
object MainMasterServiceOfferProd extends MainBase(Activation(Repo -> Repo.Prod, Scene -> Scene.Provided), Vector(RoleArgs(MasterServiceOfferRole.id)))

/**
  * Launch just the `master-service-offer-location` APIs with dummy repositories
  *
  * Equivalent to:
  * {{{
  *   ./launcher -u repo:dummy :master-service-offer-location
  * }}}
  */
object MainMasterServiceOfferLocationDummy extends MainBase(Activation(Repo -> Repo.Dummy), Vector(RoleArgs(MasterServiceOfferLocationRole.id)))

/**
  * Launch just the `master-service-offer-location` APIs with postgres repositories and dockerized postgres service
  *
  * Equivalent to:
  * {{{
  *   ./launcher -u scene:managed :master-service-offer-location
  * }}}
  */
object MainMasterServiceOfferLocationProdDocker extends MainBase(Activation(Repo -> Repo.Prod, Scene -> Scene.Managed), Vector(RoleArgs(MasterServiceOfferLocationRole.id)))

/**
  * Launch just the `master-service-offer-location` APIs with postgres repositories and external postgres service
  *
  * Equivalent to:
  * {{{
  *   ./launcher :master-service-offer-location
  * }}}
  */
object MainMasterServiceOfferLocationProd extends MainBase(Activation(Repo -> Repo.Prod, Scene -> Scene.Provided), Vector(RoleArgs(MasterServiceOfferLocationRole.id)))

/**
  * Launch just the `profile` APIs with dummy repositories
  *
  * Equivalent to:
  * {{{
  *   ./launcher -u repo:dummy :profile
  * }}}
  */
object MainProfileDummy extends MainBase(Activation(Repo -> Repo.Dummy), Vector(RoleArgs(ProfileRole.id)))

/**
  * Launch just the `profile` APIs with postgres repositories and dockerized postgres service
  *
  * Equivalent to:
  * {{{
  *   ./launcher -u scene:managed :profile
  * }}}
  */
object MainProfileProdDocker extends MainBase(Activation(Repo -> Repo.Prod, Scene -> Scene.Managed), Vector(RoleArgs(ProfileRole.id)))

/**
  * Launch just the `profile` APIs with postgres repositories and external postgres service
  *
  * Equivalent to:
  * {{{
  *   ./launcher :profile
  * }}}
  */
object MainProfileProd extends MainBase(Activation(Repo -> Repo.Prod, Scene -> Scene.Provided), Vector(RoleArgs(ProfileRole.id)))

/**
  * Display help message with all available launcher arguments
  * and command-line parameters for all roles
  *
  * Equivalent to:
  * {{{
  *   ./launcher :help
  * }}}
  */
object MainHelp extends MainBase(Activation(Repo -> Repo.Prod, Scene -> Scene.Provided), Vector(RoleArgs(Help.id)))

/**
  * Write the default configuration files for each role into JSON files in `./config`.
  * Configurations in @see {{{izumi.distage.config.ConfigModuleDef#makeConfig}}}
  * are read from resources:
  *
  *   - common-reference.conf - (configuration shared across all roles)
  *   - \${roleName}-reference.conf - (role-specific configuration, overrides `common`)
  *
  * Equivalent to:
  * {{{
  *   ./launcher :configwriter
  * }}}
  */
object MainWriteReferenceConfigs
  extends MainBase(
    activation = {
      Activation(Repo -> Repo.Prod, Scene -> Scene.Provided)
    },
    requiredRoles = {
      Vector(
        RoleArgs(
          role           = ConfigWriter.id,
          roleParameters = EntrypointArgs(
            flags = Vector.empty,
            // output configs in "hocon" format, instead of "json"
            values   = Vector(RawValue("format", "hocon")),
            raw      = Vector.empty,
            freeArgs = Vector.empty,
          ),
        )
      )
    },
  )

/**
  * Generic launcher not set to run a specific role by default,
  * use command-line arguments to choose one or multiple roles:
  *
  * {{{
  *
  *   # launch app with prod repositories
  *
  *   ./launcher :leaderboard
  *
  *   # launch app with dummy repositories
  *
  *   ./launcher -u repo:dummy :leaderboard
  *
  *   # launch just the ladder API, without profiles
  *
  *   ./launcher :ladder
  *
  *   # display help
  *
  *   ./launcher :help
  *
  *   # write configs in HOCON format to ./default-configs
  *
  *   ./launcher :configwriter -format hocon -t default-configs
  *
  *   # print help, dump configs and launch app with dummy repositories
  *
  *   ./launcher -u repo:dummy :help :configwriter :leaderboard
  *
  * }}}
  */
object GenericLauncher extends MainBase(Activation(Repo -> Repo.Prod, Scene -> Scene.Provided), Vector.empty)

sealed abstract class MainBase(
  activation: Activation,
  requiredRoles: Vector[RoleArgs],
) extends RoleAppMain.LauncherBIO[IO] {

  override def requiredRoles(argv: RoleAppMain.ArgV): Vector[RoleArgs] = {
    requiredRoles
  }

  override def pluginConfig: PluginConfig = {
    if (IzPlatform.isGraalNativeImage) {
      // Only this would work reliably for NativeImage
      PluginConfig.const(List(LeaderboardPlugin, PostgresDockerPlugin))
    } else {
      // Runtime discovery with PluginConfig.cached might be convenient for pure jvm projects during active development
      // Once the project gets to the maintenance stage it's a good idea to switch to PluginConfig.const
      PluginConfig.cached(pluginsPackage = "leaderboard.plugins")
    }
  }

  protected override def roleAppBootOverrides(argv: RoleAppMain.ArgV): Module = super.roleAppBootOverrides(argv) ++ new ModuleDef {
    make[Activation].named("default").fromValue(defaultActivation ++ activation)
  }

  private def defaultActivation = Activation(Scene -> Scene.Provided)

}
