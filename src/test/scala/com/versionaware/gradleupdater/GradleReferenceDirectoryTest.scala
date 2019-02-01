package com.versionaware.gradleupdater

class GradleReferenceDirectoryTest extends UnitSpec {
  it must "create the directory without any exception" in {
    val d = GradleReferenceDirectory.initialize(GradleVersion("4.10.3"))
    GradleReferenceDirectory.remove(d)
  }
}
