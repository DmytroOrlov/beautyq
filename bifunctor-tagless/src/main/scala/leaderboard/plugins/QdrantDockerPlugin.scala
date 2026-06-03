package leaderboard.plugins

import distage.{ModuleDef, Scene}
import izumi.distage.docker.ContainerDef
import izumi.distage.docker.healthcheck.ContainerHealthCheck
import izumi.distage.docker.model.Docker.DockerPort
import izumi.distage.docker.modules.DockerSupportModule
import izumi.distage.plugins.PluginDef
import izumi.reflect.TagKK
import leaderboard.config.QdrantPortCfg
import zio.IO

object QdrantDocker extends ContainerDef {
  val primaryPort: DockerPort = DockerPort.TCP(6333)

  override def config: Config =
    Config(
      image = "qdrant/qdrant:v1.15.4",
      ports = Seq(primaryPort),
      healthCheck = ContainerHealthCheck.httpGetCheck(primaryPort),
      healthCheckMaxAttempts = 180,
    )
}

object QdrantDockerPlugin extends PluginDef {
  include(dockerModule[IO])

  def dockerModule[F[+_, +_]: TagKK]: ModuleDef = new ModuleDef {
    tag(Scene.Managed)

    include(DockerSupportModule[F[Throwable, _]])

    make[QdrantDocker.Container]
      .fromResource(QdrantDocker.make[F[Throwable, _]])

    make[QdrantPortCfg].from {
      (docker: QdrantDocker.Container) =>
        val knownAddress = docker.availablePorts.first(DockerPort.TCP(6333))
        QdrantPortCfg(knownAddress.hostString, knownAddress.port)
    }
  }
}
