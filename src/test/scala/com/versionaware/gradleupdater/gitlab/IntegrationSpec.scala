package com.versionaware.gradleupdater.gitlab

import org.gitlab4j.api.GitLabApi
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.{Matchers, Outcome}

@RunWith(classOf[JUnitRunner])
abstract class IntegrationSpec extends org.scalatest.fixture.FlatSpec with Matchers {
  val GitLabUsername = "root"
  val GitLabPassword = "12345678"
  val GitLabUri =
    s"http://${sys.env("GITLAB_HOST")}:${sys.env("GITLAB_TCP_80")}"

  case class FixtureParam(api: GitLabApi)

  def withFixture(test: OneArgTest): Outcome = {
    val api = GitLabApi.oauth2Login(GitLabUri, GitLabUsername, GitLabPassword)
    api.getGroupApi.getGroups.forEach(api.getGroupApi.deleteGroup(_))
    api.getProjectApi
      .getProjects(true, null, null, null, null, null, null, null, null, null, 100)
      .first()
      .forEach(api.getProjectApi.deleteProject(_))
    withFixture(test.toNoArgTest(FixtureParam(api)))
  }
}
