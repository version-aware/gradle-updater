package com.versionaware.gradleupdater

class ProgramTest extends IntegrationSpec {
  it must "not throw an exception for empty GitLab" in { _ =>
    val target = Program
    noException should be thrownBy target.main(
      Array("-g", GitLabUri, "-u", GitLabUsername, "-p", GitLabPassword))
  }
}
