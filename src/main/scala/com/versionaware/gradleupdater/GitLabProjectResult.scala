package com.versionaware.gradleupdater

import org.gitlab4j.api.models.MergeRequest

sealed trait GitLabProjectResultCore
sealed trait GitLabProjectResult       extends GitLabProjectResultCore
sealed trait GitLabProjectDryRunResult extends GitLabProjectResultCore

object GitLabProjectResult {
  case object ArchivedProject                                               extends GitLabProjectResult with GitLabProjectDryRunResult
  case object DoesNotSupportMergeRequests                                   extends GitLabProjectResult with GitLabProjectDryRunResult
  case object UpdateBranchAlreadyExists                                     extends GitLabProjectResult with GitLabProjectDryRunResult
  case object TooLowAccessLevel                                             extends GitLabProjectResult with GitLabProjectDryRunResult
  case class AccessDenied(message: String)                                  extends GitLabProjectResult with GitLabProjectDryRunResult
  case class Failure(e: Throwable)                                          extends GitLabProjectResult with GitLabProjectDryRunResult
  case object GradleWrapperNotDetected                                      extends GitLabProjectResult with GitLabProjectDryRunResult
  case class Updated(mr: MergeRequest, results: Seq[GitLabDirectoryResult]) extends GitLabProjectResult
  case class NotUpdated(results: Seq[GitLabDirectoryResult])                extends GitLabProjectResult
  case class WouldBeUpdated(results: Seq[GitLabDirectoryDryRunResult])      extends GitLabProjectDryRunResult
  case class WouldNotBeUpdated(results: Seq[GitLabDirectoryDryRunResult])   extends GitLabProjectDryRunResult
}

sealed trait GitLabDirectoryResultCore {
  val path: String
}
sealed trait GitLabDirectoryDryRunResult extends GitLabDirectoryResultCore
sealed trait GitLabDirectoryResult       extends GitLabDirectoryResultCore
object GitLabDirectoryResult {
  case class CannotFindDistributionUrl(path: String)
      extends GitLabDirectoryResult
      with GitLabDirectoryDryRunResult
  case class UpToDate(path: String)       extends GitLabDirectoryResult with GitLabDirectoryDryRunResult
  case class Updated(path: String)        extends GitLabDirectoryResult
  case class WouldBeUpdated(path: String) extends GitLabDirectoryDryRunResult
}
