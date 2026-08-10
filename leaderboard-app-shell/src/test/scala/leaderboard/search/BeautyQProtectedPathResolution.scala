package leaderboard.search

import java.nio.file.{Files, Path}
import scala.annotation.tailrec

private[search] object BeautyQProtectedPathResolution {
  @tailrec
  def locateRepositoryRoot(current: Path): Path =
    if (Files.isRegularFile(current.resolve("build.sbt"))) current
    else Option(current.getParent) match {
      case Some(parent) => locateRepositoryRoot(parent)
      case None => current
    }

  def resolveFrom(repositoryRoot: Path, path: Path): Path =
    if (path.isAbsolute) path.normalize else repositoryRoot.resolve(path).normalize
}
