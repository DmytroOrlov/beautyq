package leaderboard.plugins

import distage.{ModuleDef, Scene}
import izumi.distage.docker.ContainerDef
import izumi.distage.docker.healthcheck.ContainerHealthCheck
import izumi.distage.docker.model.Docker.DockerPort
import izumi.distage.docker.modules.DockerSupportModule
import izumi.distage.plugins.PluginDef
import izumi.reflect.TagKK
import leaderboard.config.ElasticsearchPortCfg
import zio.IO

object ElasticsearchDocker extends ContainerDef {
  val primaryPort: DockerPort = DockerPort.TCP(9200)

  override def config: Config =
    Config(
      registry = Some("docker.elastic.co"),
      image = "elasticsearch/elasticsearch:8.14.3",
      ports = Seq(primaryPort),
      env = Map(
        "discovery.type" -> "single-node",
        "xpack.security.enabled" -> "false",
        "ES_JAVA_OPTS" -> "-Xms512m -Xmx512m",
      ),
      healthCheck = ContainerHealthCheck.httpGetCheck(primaryPort),
      healthCheckMaxAttempts = 180,
    )
}

object ElasticsearchDockerPlugin extends PluginDef {
  include(dockerModule[IO])

  def dockerModule[F[+_, +_]: TagKK]: ModuleDef = new ModuleDef {
    tag(Scene.Managed)

    include(DockerSupportModule[F[Throwable, _]])

    make[ElasticsearchDocker.Container]
      .fromResource(ElasticsearchDocker.make[F[Throwable, _]])

    make[ElasticsearchPortCfg].from {
      (docker: ElasticsearchDocker.Container) =>
        val knownAddress = docker.availablePorts.first(DockerPort.TCP(9200))
        ElasticsearchPortCfg(knownAddress.hostString, knownAddress.port)
    }
  }
}
