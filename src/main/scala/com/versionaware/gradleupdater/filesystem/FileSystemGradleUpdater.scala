package com.versionaware.gradleupdater.filesystem

import java.net.URL
import java.nio.file.{Files, Path, StandardOpenOption}

import com.typesafe.scalalogging.StrictLogging
import com.versionaware.gradleupdater.filesystem.FileSystemProjectResult._
import com.versionaware.gradleupdater.{
  GradleDistributionType,
  GradleDistributionUrlProvider,
  GradleVersion,
  GradleWrapperFiles
}

import scala.collection.JavaConverters._
import scala.util.control.NonFatal

class FileSystemGradleUpdater(gradleVersion: GradleVersion,
                              gradleDistribution: Option[GradleDistributionType])
    extends StrictLogging {

  private val gradleWrapperFiles      = new GradleWrapperFiles(gradleVersion)
  private val distributionUrlProvider = new GradleDistributionUrlProvider(gradleVersion)

  def tryUpdateDryRun(directory: Path): FileSystemProjectDryRunResult =
    tryUpdateStrategy[FileSystemProjectDryRunResult](
      directory,
      gradleDirs => {
        val results = gradleDirs.map[FileSystemDirectoryDryRunResult, Seq[FileSystemDirectoryDryRunResult]] {
          case DirectoryStatus.CannotFindDistributionUrl(path) =>
            FileSystemDirectoryResult.CannotFindDistributionUrl(path)
          case DirectoryStatus.UpToDate(path)       => FileSystemDirectoryResult.UpToDate(path)
          case DirectoryStatus.NeedsUpdate(path, _) => FileSystemDirectoryResult.WouldBeUpdated(path)
        }
        if (gradleDirs.collect { case r: DirectoryStatus.NeedsUpdate => r }.isEmpty)
          WouldNotBeUpdated(results)
        else WouldBeUpdated(results)
      }
    ).asInstanceOf[FileSystemProjectDryRunResult]

  def tryUpdate(directory: Path): FileSystemProjectResult =
    tryUpdateStrategy(
      directory,
      gradleDirs => {
        val results = gradleDirs.map[FileSystemDirectoryResult, Seq[FileSystemDirectoryResult]] {
          case DirectoryStatus.CannotFindDistributionUrl(path) =>
            FileSystemDirectoryResult.CannotFindDistributionUrl(path)
          case DirectoryStatus.UpToDate(path)       => FileSystemDirectoryResult.UpToDate(path)
          case DirectoryStatus.NeedsUpdate(path, _) => FileSystemDirectoryResult.Updated(path)
        }
        makeChangesIfRequired(gradleDirs) match {
          case Some(_) => FileSystemProjectResult.Updated(results).asInstanceOf[FileSystemProjectResult]
          case None    => FileSystemProjectResult.NotUpdated(results)
        }
      }
    ).asInstanceOf[FileSystemProjectResult]

  private def tryUpdateStrategy[T <: FileSystemProjectResultCore](
      directory: Path,
      doer: (Seq[DirectoryStatus]) => T): FileSystemProjectResultCore =
    try {
      val gradleDirs = getGradleDirectories(directory)
      if (gradleDirs.isEmpty) FileSystemProjectResult.GradleWrapperNotDetected
      else doer(gradleDirs.map(getDirectoryStatus))
    } catch {
      case NonFatal(e) => Failure(e)
    }

  private def getGradleDirectories(dir: Path): Seq[Path] = {
    val r = if (isDirectoryWithWrapper(dir)) Seq(dir) else Seq.empty
    r ++ dir.toFile.listFiles().filter(_.isDirectory).flatMap(f => getGradleDirectories(f.toPath))
  }

  private def isDirectoryWithWrapper(dir: Path): Boolean =
    gradleWrapperFiles
      .get(GradleDistributionType.Bin)
      .forall(wf => {
        val f = dir.resolve(wf.path).toFile
        f.exists() && f.isFile
      })

  private def getDirectoryStatus(path: Path): DirectoryStatus = {
    val propertiesFile = path.resolve("gradle/wrapper/gradle-wrapper.properties")
    extractDistributionUrl(propertiesFile) match {
      case Some(currentDistributionUrl) =>
        val desiredType =
          gradleDistribution.getOrElse(GradleDistributionUrlProvider.getType(currentDistributionUrl))
        val desiredUrl = distributionUrlProvider.get(desiredType)
        if (desiredUrl.equals(currentDistributionUrl)) DirectoryStatus.UpToDate(path)
        else DirectoryStatus.NeedsUpdate(path, desiredType)
      case None => DirectoryStatus.CannotFindDistributionUrl(path)
    }
  }

  private sealed trait DirectoryStatus { val path: Path }
  private object DirectoryStatus {
    case class CannotFindDistributionUrl(path: Path)                        extends DirectoryStatus
    case class UpToDate(path: Path)                                         extends DirectoryStatus
    case class NeedsUpdate(path: Path, desiredType: GradleDistributionType) extends DirectoryStatus
  }

  private def extractDistributionUrl(propertiesFile: Path): Option[URL] =
    Files
      .readAllLines(propertiesFile)
      .asScala
      .find(_.startsWith("distributionUrl="))
      .map(_.substring("distributionUrl=".length).replace("\\:", ":"))
      .map(new URL(_))

  private def makeChangesIfRequired(dirs: Seq[DirectoryStatus]): Option[Unit] = {
    val updated = dirs.collect {
      case d: DirectoryStatus.NeedsUpdate =>
        gradleWrapperFiles
          .get(d.desiredType)
          .foreach(wf =>
            Files.write(d.path.resolve(wf.path), wf.content, StandardOpenOption.TRUNCATE_EXISTING))
    }
    updated.headOption
  }
}
