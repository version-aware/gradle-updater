package com.versionaware.gradleupdater

import java.net.URL
import java.util.Base64

import com.typesafe.scalalogging.StrictLogging
import com.versionaware.gradleupdater.GitLabProjectResult._
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

  def tryUpdateDryRun(project: Project): GitLabProjectDryRunResult =
    tryUpdateStrategy(
      project,
      gradleDirs => {
        val results: Seq[GitLabDirectoryDryRunResult] = gradleDirs.map {
          case DirectoryStatus.CannotFindDistributionUrl(path) =>
            GitLabDirectoryResult.CannotFindDistributionUrl(path)
          case DirectoryStatus.UpToDate(path)       => GitLabDirectoryResult.UpToDate(path)
          case DirectoryStatus.NeedsUpdate(path, _) => GitLabDirectoryResult.WouldBeUpdated(path)
        }
        if (gradleDirs.collect { case r: DirectoryStatus.NeedsUpdate => r }.isEmpty)
          WouldNotBeUpdated(results)
        else WouldBeUpdated(results)
      }
    ).asInstanceOf[GitLabProjectDryRunResult]

  def tryUpdate(project: Project): GitLabProjectResult =
    tryUpdateStrategy(
      project,
      gradleDirs => {
        val results: Seq[GitLabDirectoryResult] = gradleDirs.map {
          case DirectoryStatus.CannotFindDistributionUrl(path) =>
            GitLabDirectoryResult.CannotFindDistributionUrl(path)
          case DirectoryStatus.UpToDate(path)       => GitLabDirectoryResult.UpToDate(path)
          case DirectoryStatus.NeedsUpdate(path, _) => GitLabDirectoryResult.Updated(path)
        }
        createMergeRequestIfRequired(project, gradleDirs) match {
          case Some(mr) => GitLabProjectResult.Updated(mr, results).asInstanceOf[GitLabProjectResult]
          case None     => GitLabProjectResult.NotUpdated(results)
        }
      }
    ).asInstanceOf[GitLabProjectResult]

  private def tryUpdateStrategy[T <: GitLabProjectResultCore](
      project: Project,
      doer: (Seq[DirectoryStatus]) => T): GitLabProjectResultCore =
    if (project.getArchived) ArchivedProject
    else if (!project.getMergeRequestsEnabled) DoesNotSupportMergeRequests
    else if (!haveSufficientAccessLevel(project)) TooLowAccessLevel
    else if (updateBranchAlreadyExists(project)) {
      UpdateBranchAlreadyExists
    } else {
      try {
        val gradleDirs = getGradleDirectories(project)
        if (gradleDirs.isEmpty) GitLabProjectResult.GradleWrapperNotDetected
        else doer(gradleDirs.map(getDirectoryStatus(project, _)))
      } catch {
        case e: GitLabApiException if e.getHttpStatus == 403 =>
          AccessDenied(e.getMessage)
        case e: GitLabApiException if e.getHttpStatus == 404 =>
          logger.debug("GitLab API returned 404 when searching for Gradle Wrapper", e)
          GitLabProjectResult.GradleWrapperNotDetected
        case NonFatal(e) => Failure(e)
      }
    }

  def updateBranchAlreadyExists(project: Project): Boolean =
    gitLabApi.getRepositoryApi.getBranches(project.getId).asScala.exists(_.getName == branchName)

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

  private def getGradleDirectories(project: Project): Seq[String] = {
    val gradleFiles = gitLabApi.getRepositoryApi
      .getTree(project.getId, null, null, true)
      .asScala
      .filter(i => i.getType == TreeItem.Type.BLOB && i.getName.startsWith("gradle"))
    gradleFiles
      .filter(i =>
        i.getName == "gradlew" && isDirectoryWithWrapper(gradleFiles, i.getPath.stripSuffix("gradlew")))
      .map(_.getPath.stripSuffix("gradlew"))
  }

  private def isDirectoryWithWrapper(gradleFiles: Seq[TreeItem], directoryPath: String): Boolean =
    gradleWrapperFiles
      .get(GradleDistributionType.Bin)
      .forall(wf => {
        val p = directoryPath + wf.path.iterator().asScala.mkString("/")
        gradleFiles.exists(ti => ti.getPath == p)
      })

  private def getDirectoryStatus(project: Project, path: String): DirectoryStatus = {
    val propertiesFile = gitLabApi.getRepositoryFileApi.getFile(
      s"${path}gradle/wrapper/gradle-wrapper.properties",
      project.getId,
      Option(project.getDefaultBranch).getOrElse("master"))
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

  private sealed trait DirectoryStatus { val path: String }
  private object DirectoryStatus {
    case class CannotFindDistributionUrl(path: String)                        extends DirectoryStatus
    case class UpToDate(path: String)                                         extends DirectoryStatus
    case class NeedsUpdate(path: String, desiredType: GradleDistributionType) extends DirectoryStatus
  }

  private def extractDistributionUrl(propertiesFile: RepositoryFile): Option[URL] =
    new String(Base64.getDecoder.decode(propertiesFile.getContent)).lines
      .find(_.startsWith("distributionUrl="))
      .map(_.substring("distributionUrl=".length).replace("\\:", ":"))
      .map(new URL(_))

  def getCommitActions(dir: String,
                       distributionType: GradleDistributionType,
                       action: CommitAction.Action): Seq[CommitAction] =
    gradleWrapperFiles
      .get(distributionType)
      .map(
        f =>
          new CommitAction()
            .withAction(action)
            .withFilePath(dir + f.path.iterator().asScala.mkString("/"))
            .withEncoding(CommitAction.Encoding.BASE64)
            .withContent(Base64.getEncoder.encodeToString(f.content)))

  private def createMergeRequestIfRequired(project: Project,
                                           dirs: Seq[DirectoryStatus]): Option[MergeRequest] = {
    val toUpdate = dirs.collect { case r: DirectoryStatus.NeedsUpdate => r }
    if (toUpdate.nonEmpty) {
      gitLabApi.getCommitsApi.createCommit(
        project.getId,
        branchName,
        s"Gradle updated to ${gradleVersion.version}",
        Option(project.getDefaultBranch).getOrElse("master"),
        null,
        "Gradle Updater",
        toUpdate.flatMap(d => getCommitActions(d.path, d.desiredType, CommitAction.Action.UPDATE)).asJava
      )
      Some(
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
        ))
    } else None
  }

}
