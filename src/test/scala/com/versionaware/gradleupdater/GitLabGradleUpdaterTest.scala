package com.versionaware.gradleupdater

import java.util.Base64

import com.versionaware.gradleupdater.GitLabUpdaterResult._
import org.gitlab4j.api.GitLabApi
import org.gitlab4j.api.models.{CommitAction, Project}

import scala.collection.JavaConverters._

class GitLabGradleUpdaterTest extends IntegrationSpec {

  it must "detect missing Gradle Wrapper for empty project" in { f =>
    val target = new GitLabGradleUpdater(f.api, GradleVersion("4.6"), None)
    val emptyProject =
      f.api.getProjectApi.createProject(new Project().withPath("empty"))
    target.tryUpdate(emptyProject) shouldBe GradleWrapperNotDetected
  }

  it must "do nothing for up-to-date project" in { f =>
    val toUpdateVersion  = GradleVersion("4.6")
    val distributionType = GradleDistributionType.Bin
    val target =
      new GitLabGradleUpdater(f.api, toUpdateVersion, Some(distributionType))
    val p = createProject(f.api, toUpdateVersion, distributionType)
    target.tryUpdate(p) shouldBe UpToDate
  }

  it must "detect archived project" in { f =>
    val toUpdateVersion = GradleVersion("4.6")
    val target          = new GitLabGradleUpdater(f.api, toUpdateVersion, None)
    val p = f.api.getProjectApi
      .createProject(new Project().withPath("project-to-archive"))
    val archivedProject = f.api.getProjectApi.archiveProject(p.getId)
    target.tryUpdate(archivedProject) shouldBe ArchivedProject
  }

  it must "detect project without merge requests" in { f =>
    val toUpdateVersion = GradleVersion("4.6")
    val target          = new GitLabGradleUpdater(f.api, toUpdateVersion, None)
    val p = f.api.getProjectApi
      .createProject(new Project().withPath("test-project").withMergeRequestsEnabled(false))
    target.tryUpdate(p) shouldBe DoesNotSupportMergeRequests
  }

  it must "update the project" in { f =>
    val toUpdateVersion  = GradleVersion("4.6")
    val target           = new GitLabGradleUpdater(f.api, toUpdateVersion, None)
    val distributionType = GradleDistributionType.Bin
    val p                = createProject(f.api, GradleVersion("4.5.1"), distributionType)
    val actual = target.tryUpdate(p) match {
      case u: Updated => u
      case other      => sys.error(s"Updated result expected but $other found")
    }
    val newFile = f.api.getRepositoryFileApi
      .getFile("gradle/wrapper/gradle-wrapper.properties", p.getId, actual.mergeRequest.getSourceBranch)
    new String(Base64.getDecoder.decode(newFile.getContent)) should include(
      GradleDistributionUrlProvider(toUpdateVersion)
        .get(distributionType)
        .toString
        .replace(":", "\\:"))
  }

  it must "detected outdated project in dry-run" in { f =>
    val toUpdateVersion  = GradleVersion("4.6")
    val target           = new GitLabGradleUpdater(f.api, toUpdateVersion, None)
    val distributionType = GradleDistributionType.Bin
    val p                = createProject(f.api, GradleVersion("4.5.1"), distributionType)
    target.tryUpdateDryRun(p) shouldBe WouldBeUpdated
  }

  private def createProject(api: GitLabApi,
                            version: GradleVersion,
                            distributionType: GradleDistributionType): Project = {
    val p       = api.getProjectApi.createProject(new Project().withPath("test-project"))
    val updater = new GitLabGradleUpdater(api, version, None)
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
  }
}
