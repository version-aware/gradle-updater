package com.versionaware.gradleupdater

import java.net.URL
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.Base64

import com.typesafe.scalalogging.StrictLogging
import com.versionaware.gradleupdater.GradleUpdateResult._
import org.gitlab4j.api.{GitLabApi, GitLabApiException}
import org.gitlab4j.api.models._

import scala.collection.JavaConverters._
import scala.util.control.NonFatal

class GitLabGradleUpdater(gitLabApi: GitLabApi,
                          gradleVersion: GradleVersion,
                          gradleDistribution: Option[GradleDistributionType])
    extends AutoCloseable
    with StrictLogging {

  private val referenceDirectory =
    GradleReferenceDirectory.initialize(gradleVersion)
  private val distributionUrlProvider = new GradleDistributionUrlProvider(
    gradleVersion)

  private val branchName = s"Gradle_${gradleVersion.version.replace('.', '_')}"

  def tryUpdate(project: Project): GradleUpdateResult =
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
            val desiredType = gradleDistribution.getOrElse(
              GradleDistributionUrlProvider.getType(currentDistributionUrl))
            val desiredUrl = distributionUrlProvider.get(desiredType)
            if (desiredUrl.equals(currentDistributionUrl)) UpToDate
            else Updated(performUpdate(project, desiredType))
          case None => CannotFindDistributionUrl
        }
      }
    } catch {
      case e: GitLabApiException if e.getHttpStatus == 403 =>
        AccessDenied(e.getMessage)
      case NonFatal(e) => Failure(e)
    }

  private def extractDistributionUrl(
      propertiesFile: RepositoryFile): Option[URL] =
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
      if (rootTree.exists(i =>
            i.getType == TreeItem.Type.BLOB && i.getName == "gradlew.bat") &&
          rootTree.exists(
            i => i.getType == TreeItem.Type.BLOB && i.getName == "gradlew") &&
          rootTree.exists(
            i => i.getType == TreeItem.Type.TREE && i.getName == "gradle")) {
        val subTree = gitLabApi.getRepositoryApi
          .getTree(project.getId, "gradle/wrapper", project.getDefaultBranch)
          .asScala
        subTree.exists(i =>
          i.getType == TreeItem.Type.BLOB && i.getName == "gradle-wrapper.properties") && subTree
          .exists(i =>
            i.getType == TreeItem.Type.BLOB && i.getName == "gradle-wrapper.jar")
      } else {
        false
      }
    } catch {
      case e: GitLabApiException if e.getHttpStatus == 404 =>
        logger.debug(
          "GitLab API returned 404 when checking Gradle Wrapper presence",
          e)
        false
    }

  def getCommitActions(distributionType: GradleDistributionType,
                       action: CommitAction.Action): Seq[CommitAction] = Seq(
    new CommitAction()
      .withAction(action)
      .withFilePath("gradlew")
      .withEncoding(CommitAction.Encoding.BASE64)
      .withContent(Base64.getEncoder.encodeToString(
        Files.readAllBytes(referenceDirectory.resolve("gradlew")))),
    new CommitAction()
      .withAction(action)
      .withFilePath("gradlew.bat")
      .withEncoding(CommitAction.Encoding.BASE64)
      .withContent(Base64.getEncoder.encodeToString(
        Files.readAllBytes(referenceDirectory.resolve("gradlew.bat")))),
    new CommitAction()
      .withAction(action)
      .withFilePath("gradle/wrapper/gradle-wrapper.jar")
      .withEncoding(CommitAction.Encoding.BASE64)
      .withContent(
        Base64.getEncoder.encodeToString(
          Files.readAllBytes(
            referenceDirectory
              .resolve("gradle")
              .resolve("wrapper")
              .resolve("gradle-wrapper.jar")))),
    new CommitAction()
      .withAction(action)
      .withFilePath("gradle/wrapper/gradle-wrapper.properties")
      .withEncoding(CommitAction.Encoding.BASE64)
      .withContent(Base64.getEncoder.encodeToString(replaceDistributionUrl(
        Files.readAllBytes(referenceDirectory
          .resolve("gradle")
          .resolve("wrapper")
          .resolve("gradle-wrapper.properties")),
        distributionUrlProvider.get(distributionType)
      )))
  )

  private def replaceDistributionUrl(bytes: Array[Byte],
                                     url: URL): Array[Byte] = {
    new String(bytes).lines
      .map(l => {
        if (l.startsWith("distributionUrl="))
          s"distributionUrl=${url.toString.replace(":", "\\:")}"
        else l
      })
      .mkString("\n")
      .getBytes(StandardCharsets.UTF_8)
  }

  private def performUpdate(
      project: Project,
      distributionType: GradleDistributionType): MergeRequest = {
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

  override def close(): Unit =
    GradleReferenceDirectory.remove(referenceDirectory)
}

sealed trait GradleUpdateResult
object GradleUpdateResult {
  case object ArchivedProject extends GradleUpdateResult
  case object DoesNotSupportMergeRequests extends GradleUpdateResult
  case object TooLowAccessLevel extends GradleUpdateResult
  case class AccessDenied(message: String) extends GradleUpdateResult
  case class Failure(e: Throwable) extends GradleUpdateResult
  case object GradleWrapperNotDetected extends GradleUpdateResult
  case object CannotFindDistributionUrl extends GradleUpdateResult
  case object UpToDate extends GradleUpdateResult
  case class Updated(mergeRequest: MergeRequest) extends GradleUpdateResult
}
