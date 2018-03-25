package com.versionaware.gradleupdater.filesystem

import java.nio.file.Path

sealed trait FileSystemProjectResultCore
sealed trait FileSystemProjectResult       extends FileSystemProjectResultCore
sealed trait FileSystemProjectDryRunResult extends FileSystemProjectResultCore

object FileSystemProjectResult {
  case class Failure(e: Throwable)                               extends FileSystemProjectResult with FileSystemProjectDryRunResult
  case object GradleWrapperNotDetected                           extends FileSystemProjectResult with FileSystemProjectDryRunResult
  case class Updated(results: Seq[FileSystemDirectoryResult])    extends FileSystemProjectResult
  case class NotUpdated(results: Seq[FileSystemDirectoryResult]) extends FileSystemProjectResult
  case class WouldBeUpdated(results: Seq[FileSystemDirectoryDryRunResult])
      extends FileSystemProjectDryRunResult
  case class WouldNotBeUpdated(results: Seq[FileSystemDirectoryDryRunResult])
      extends FileSystemProjectDryRunResult
}

sealed trait FileSystemDirectoryResultCore {
  val path: Path
}
sealed trait FileSystemDirectoryDryRunResult extends FileSystemDirectoryResultCore
sealed trait FileSystemDirectoryResult       extends FileSystemDirectoryResultCore
object FileSystemDirectoryResult {
  case class CannotFindDistributionUrl(path: Path)
      extends FileSystemDirectoryResult
      with FileSystemDirectoryDryRunResult
  case class UpToDate(path: Path)       extends FileSystemDirectoryResult with FileSystemDirectoryDryRunResult
  case class Updated(path: Path)        extends FileSystemDirectoryResult
  case class WouldBeUpdated(path: Path) extends FileSystemDirectoryDryRunResult
}
