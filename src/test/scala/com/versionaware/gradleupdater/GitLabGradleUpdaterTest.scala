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

  it must "update the project" in { f =>
    val toUpdateVersion = GradleVersion("4.6")
    val target = new GitLabGradleUpdater(f.api, toUpdateVersion, None)
    try {
      val distributionType = GradleDistributionType.Bin
      val p = createOutdatedProject(f.api, distributionType)
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

  private def createOutdatedProject(
      api: GitLabApi,
      distributionType: GradleDistributionType): Project = {
    val version = GradleVersion("4.5.1")
    val p = api.getProjectApi.createProject(new Project().withPath("outdated"))
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
