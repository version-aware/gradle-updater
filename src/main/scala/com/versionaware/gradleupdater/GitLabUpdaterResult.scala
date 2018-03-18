package com.versionaware.gradleupdater

import org.gitlab4j.api.models.MergeRequest

sealed trait GitLabUpdaterResultCore
sealed trait GitLabUpdaterResult       extends GitLabUpdaterResultCore
sealed trait GitLabUpdaterDryRunResult extends GitLabUpdaterResultCore

object GitLabUpdaterResult {
  case object ArchivedProject                    extends GitLabUpdaterResult with GitLabUpdaterDryRunResult
  case object DoesNotSupportMergeRequests        extends GitLabUpdaterResult with GitLabUpdaterDryRunResult
  case object TooLowAccessLevel                  extends GitLabUpdaterResult with GitLabUpdaterDryRunResult
  case class AccessDenied(message: String)       extends GitLabUpdaterResult with GitLabUpdaterDryRunResult
  case class Failure(e: Throwable)               extends GitLabUpdaterResult with GitLabUpdaterDryRunResult
  case object GradleWrapperNotDetected           extends GitLabUpdaterResult with GitLabUpdaterDryRunResult
  case object CannotFindDistributionUrl          extends GitLabUpdaterResult with GitLabUpdaterDryRunResult
  case object UpToDate                           extends GitLabUpdaterResult with GitLabUpdaterDryRunResult
  case object UpdateBranchAlreadyExists          extends GitLabUpdaterResult with GitLabUpdaterDryRunResult
  case class Updated(mergeRequest: MergeRequest) extends GitLabUpdaterResult
  case object WouldBeUpdated                     extends GitLabUpdaterDryRunResult
}
