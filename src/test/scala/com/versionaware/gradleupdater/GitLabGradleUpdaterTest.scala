package com.versionaware.gradleupdater

import java.util.Base64

import com.versionaware.gradleupdater.GradleUpdateResult._
import org.gitlab4j.api.GitLabApi
import org.gitlab4j.api.models.{CommitAction, Project}

import scala.collection.JavaConverters._

class GitLabGradleUpdaterTest extends IntegrationSpec {

  it must "detect missing Gradle Wrapper for empty project" in { f =>
    val target = new GitLabGradleUpdater(f.api, GradleVersion("4.6"), None)
    try {
      val emptyProject =
        f.api.getProjectApi.createProject(new Project().withPath("empty"))
      target.tryUpdate(emptyProject) shouldBe GradleWrapperNotDetected
    } finally target.close()
  }

  it must "do nothing for up-to-date project" in { f =>
    val toUpdateVersion = GradleVersion("4.6")
    val distributionType = GradleDistributionType.Bin
    val target =
      new GitLabGradleUpdater(f.api, toUpdateVersion, Some(distributionType))
    try {
      val p = createProject(f.api, toUpdateVersion, distributionType)
      target.tryUpdate(p) shouldBe UpToDate
    } finally target.close()
  }

  // GitLabApi doesn't support archiving
  ignore must "detect archived project" in { f =>
    val toUpdateVersion = GradleVersion("4.6")
    val target = new GitLabGradleUpdater(f.api, toUpdateVersion, None)
    try {
      val p = f.api.getProjectApi
        .createProject(new Project().withPath("test-project"))
      // f.api.getProjectApi.archiveProject(p.getId)
      target.tryUpdate(p) shouldBe ArchivedProject
    } finally target.close()
  }

  it must "detect project without merge requests" in { f =>
    val toUpdateVersion = GradleVersion("4.6")
    val target = new GitLabGradleUpdater(f.api, toUpdateVersion, None)
    try {
      val p = f.api.getProjectApi.createProject(
        new Project().withPath("test-project").withMergeRequestsEnabled(false))
      target.tryUpdate(p) shouldBe DoesNotSupportMergeRequests
    } finally target.close()
  }

  it must "update the project" in { f =>
    val toUpdateVersion = GradleVersion("4.6")
    val target = new GitLabGradleUpdater(f.api, toUpdateVersion, None)
    try {
      val distributionType = GradleDistributionType.Bin
      val p = createProject(f.api, GradleVersion("4.5.1"), distributionType)
      val actual = target.tryUpdate(p) match {
        case u: Updated => u
        case other      => sys.error(s"Updated result expected but $other found")
      }
      val newFile = f.api.getRepositoryFileApi.getFile(
        "gradle/wrapper/gradle-wrapper.properties",
        p.getId,
        actual.mergeRequest.getSourceBranch)
      new String(Base64.getDecoder.decode(newFile.getContent)) should include(
        GradleDistributionUrlProvider(toUpdateVersion)
          .get(distributionType)
          .toString
          .replace(":", "\\:"))
    } finally target.close()
  }

  private def createProject(
      api: GitLabApi,
      version: GradleVersion,
      distributionType: GradleDistributionType): Project = {
    val p =
      api.getProjectApi.createProject(new Project().withPath("test-project"))
    val updater = new GitLabGradleUpdater(api, version, None)
    try {
      api.getCommitsApi.createCommit(
        p.getId,
        Option(p.getDefaultBranch).getOrElse("master"),
        "Initial commit",
        Option(p.getDefaultBranch).getOrElse("master"),
        null,
        "test",
        updater
          .getCommitActions(distributionType, CommitAction.Action.CREATE)
          .asJava
      )
      p
    } finally updater.close()
  }
}
