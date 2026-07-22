package leaderboard.plugins

import distage.{ModuleDef, Scene}
import izumi.distage.docker.ContainerDef
import izumi.distage.docker.healthcheck.ContainerHealthCheck
import izumi.distage.docker.model.Docker.DockerPort
import izumi.distage.docker.modules.DockerSupportModule
import izumi.distage.plugins.PluginDef
import izumi.reflect.TagKK
import leaderboard.config.{QdrantGen2PortCfg, QdrantPortCfg}
import zio.IO

/** Sole Distage-managed Qdrant process: the single `ContainerDef` shared by Gen1 and Gen2 clients. */
object QdrantGen2Docker extends ContainerDef {
  val primaryPort: DockerPort = DockerPort.TCP(6333)

  override def config: Config =
    Config(
      image = "qdrant/qdrant:v1.18.3",
      ports = Seq(primaryPort),
      healthCheck = ContainerHealthCheck.httpGetCheck(primaryPort),
      healthCheckMaxAttempts = 180,
    )
}

object QdrantGen2DockerPlugin extends PluginDef {
  include(dockerModule[IO])

  def dockerModule[F[+_, +_]: TagKK]: ModuleDef = new ModuleDef {
    tag(Scene.Managed)

    include(DockerSupportModule[F[Throwable, _]])

    make[QdrantGen2Docker.Container]
      .fromResource(QdrantGen2Docker.make[F[Throwable, _]])

    make[QdrantGen2PortCfg].from {
      (docker: QdrantGen2Docker.Container) =>
        val address =
          docker.availablePorts.first(QdrantGen2Docker.primaryPort)

        QdrantGen2PortCfg(
          host = address.hostString,
          port = address.port,
        )
    }

    make[QdrantPortCfg].from {
      (cfg: QdrantGen2PortCfg) =>
        QdrantPortCfg(
          host = cfg.host,
          port = cfg.port,
        )
    }
  }
}
