package com.versionaware.gradleupdater

import java.util.Base64

import com.versionaware.gradleupdater.GradleUpdateResult.Updated
import org.gitlab4j.api.GitLabApi
import org.gitlab4j.api.models.{CommitAction, Project}

import scala.collection.JavaConverters._

class GitLabGradleUpdaterTest extends IntegrationSpec {
  it must "update the project" in { f =>
    val toUpdateVersion = GradleVersion("4.6")
    val target = new GitLabGradleUpdater(f.api, toUpdateVersion)
    val p = createOutdatedProject(f.api)
    val actual = target.tryUpdate(p) match {
      case u: Updated => u
      case other      => sys.error(s"Updated result expected but $other found")
    }
    val newFile = f.api.getRepositoryFileApi.getFile(
      "gradle/wrapper/gradle-wrapper.properties",
      p.getId,
      actual.mergeRequest.getSourceBranch)
    new String(Base64.getDecoder.decode(newFile.getContent)) should include(
      toUpdateVersion.downloadUrl.toString.replace(":", "\\:"))
  }

  private def createOutdatedProject(api: GitLabApi): Project = {
    val version = GradleVersion("4.5.1")
    val p = api.getProjectApi.createProject(new Project().withPath("outdated"))
    val updater = new GitLabGradleUpdater(api, version)
    try {
      api.getCommitsApi.createCommit(
        p.getId,
        Option(p.getDefaultBranch).getOrElse("master"),
        "Initial commit",
        Option(p.getDefaultBranch).getOrElse("master"),
        null,
        "test",
        updater.getCommitActions(CommitAction.Action.CREATE).asJava
      )
      p
    } finally updater.close()
  }
}
