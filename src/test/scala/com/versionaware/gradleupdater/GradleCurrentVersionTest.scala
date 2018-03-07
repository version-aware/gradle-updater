package com.versionaware.gradleupdater

class GradleCurrentVersionTest extends UnitSpec {
  it must "return a version with at least one dot" in {
    val actual = GradleCurrentVersion.get()
    actual.version should include(".")
  }
}
