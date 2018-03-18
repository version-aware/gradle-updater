package com.versionaware.gradleupdater

import java.net.URL
import java.util.Base64

import com.typesafe.scalalogging.StrictLogging
import com.versionaware.gradleupdater.GitLabUpdaterResult._
import org.gitlab4j.api.models._
import org.gitlab4j.api.{GitLabApi, GitLabApiException}

import scala.collection.JavaConverters._
import scala.util.control.NonFatal

class GitLabGradleUpdater(gitLabApi: GitLabApi,
                          gradleVersion: GradleVersion,
                          gradleDistribution: Option[GradleDistributionType])
    extends StrictLogging {

  private val gradleWrapperFiles      = new GradleWrapperFiles(gradleVersion)
  private val distributionUrlProvider = new GradleDistributionUrlProvider(gradleVersion)
  private val branchName              = s"Gradle_${gradleVersion.version.replace('.', '_')}"

  def tryUpdateDryRun(project: Project): GitLabUpdaterDryRunResult =
    updateStrategy(project, (_, _) => WouldBeUpdated).asInstanceOf[GitLabUpdaterDryRunResult]

  def tryUpdate(project: Project): GitLabUpdaterResult =
    updateStrategy(project, (p, t) => Updated(performUpdate(p, t))).asInstanceOf[GitLabUpdaterResult]

  protected def updateStrategy[T <: GitLabUpdaterResultCore](
      project: Project,
      performUpdate: (Project, GradleDistributionType) => T): GitLabUpdaterResultCore =
    try {
      if (project.getArchived) ArchivedProject
      else if (!project.getMergeRequestsEnabled) DoesNotSupportMergeRequests
      else if (!haveSufficientAccessLevel(project)) TooLowAccessLevel
      else if (!isGradleWrapperPresent(project)) {
        GradleWrapperNotDetected
      } else {
        val propertiesFile = gitLabApi.getRepositoryFileApi.getFile(
          "gradle/wrapper/gradle-wrapper.properties",
          project.getId,
          Option(project.getDefaultBranch).getOrElse("master"))
        extractDistributionUrl(propertiesFile) match {
          case Some(currentDistributionUrl) =>
            val desiredType =
              gradleDistribution.getOrElse(GradleDistributionUrlProvider.getType(currentDistributionUrl))
            val desiredUrl = distributionUrlProvider.get(desiredType)
            if (desiredUrl.equals(currentDistributionUrl)) UpToDate
            else performUpdate(project, desiredType)
          case None => CannotFindDistributionUrl
        }
      }
    } catch {
      case e: GitLabApiException if e.getHttpStatus == 403 =>
        AccessDenied(e.getMessage)
      case NonFatal(e) => Failure(e)
    }

  private def extractDistributionUrl(propertiesFile: RepositoryFile): Option[URL] =
    new String(Base64.getDecoder.decode(propertiesFile.getContent)).lines
      .find(_.startsWith("distributionUrl="))
      .map(_.substring("distributionUrl=".length).replace("\\:", ":"))
      .map(new URL(_))

  def haveSufficientAccessLevel(p: Project): Boolean = {
    gitLabApi.getUserApi.getCurrentUser.getIsAdmin ||
    (Option(p.getPermissions)
      .flatMap(per => Option(per.getGroupAccess))
      .map(_.getAccessLevel.value.intValue())
      .getOrElse(0) >= AccessLevel.DEVELOPER.value) ||
    (Option(p.getPermissions)
      .flatMap(per => Option(per.getProjectAccess))
      .map(_.getAccessLevel.value.intValue())
      .getOrElse(0) >= AccessLevel.DEVELOPER.value)
  }

  def isGradleWrapperPresent(project: Project): Boolean =
    try {
      val rootTree = gitLabApi.getRepositoryApi
        .getTree(project.getId, null, project.getDefaultBranch)
        .asScala
      if (rootTree.exists(i => i.getType == TreeItem.Type.BLOB && i.getName == "gradlew.bat") &&
          rootTree.exists(i => i.getType == TreeItem.Type.BLOB && i.getName == "gradlew") &&
          rootTree.exists(i => i.getType == TreeItem.Type.TREE && i.getName == "gradle")) {
        val subTree = gitLabApi.getRepositoryApi
          .getTree(project.getId, "gradle/wrapper", project.getDefaultBranch)
          .asScala
        subTree.exists(i => i.getType == TreeItem.Type.BLOB && i.getName == "gradle-wrapper.properties") && subTree
          .exists(i => i.getType == TreeItem.Type.BLOB && i.getName == "gradle-wrapper.jar")
      } else {
        false
      }
    } catch {
      case e: GitLabApiException if e.getHttpStatus == 404 =>
        logger.debug("GitLab API returned 404 when checking Gradle Wrapper presence", e)
        false
    }

  def getCommitActions(distributionType: GradleDistributionType,
                       action: CommitAction.Action): Seq[CommitAction] =
    gradleWrapperFiles
      .get(distributionType)
      .map(
        f =>
          new CommitAction()
            .withAction(action)
            .withFilePath(f.path.iterator().asScala.mkString("/"))
            .withEncoding(CommitAction.Encoding.BASE64)
            .withContent(Base64.getEncoder.encodeToString(f.content)))

  private def performUpdate(project: Project, distributionType: GradleDistributionType): MergeRequest = {
    gitLabApi.getCommitsApi.createCommit(
      project.getId,
      branchName,
      s"Gradle updated to ${gradleVersion.version}",
      Option(project.getDefaultBranch).getOrElse("master"),
      null,
      "Gradle Updater",
      getCommitActions(distributionType, CommitAction.Action.UPDATE).asJava
    )
    gitLabApi.getMergeRequestApi.createMergeRequest(
      project.getId,
      branchName,
      Option(project.getDefaultBranch).getOrElse("master"),
      s"Update Gradle to ${gradleVersion.version}",
      s"Gradle updated to ${gradleVersion.version}, see [release notes](${gradleVersion.releaseNotes}).",
      null,
      null,
      null,
      null,
      true
    )
  }
}
