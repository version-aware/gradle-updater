package com.versionaware.gradleupdater

class GradleWrapperFilesTest extends UnitSpec {
  it must "returns 4 files for each distribution type" in {
    val target = new GradleWrapperFiles(GradleVersion("4.10.3"))
    target.get(GradleDistributionType.All).size shouldBe 4
    target.get(GradleDistributionType.Bin).size shouldBe 4
  }
}
