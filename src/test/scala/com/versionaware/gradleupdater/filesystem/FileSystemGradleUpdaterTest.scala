package com.versionaware.gradleupdater.filesystem

import java.nio.file.{Files, Path, StandardOpenOption}

import com.versionaware.gradleupdater.filesystem.FileSystemProjectResult._
import com.versionaware.gradleupdater._

import scala.collection.JavaConverters._

class FileSystemGradleUpdaterTest extends IntegrationSpec {

  it must "detect missing Gradle Wrapper for empty directory" in { f =>
    val target = new FileSystemGradleUpdater(GradleVersion("4.10.3"), None)
    target.tryUpdate(f.dir) shouldBe GradleWrapperNotDetected
  }

  it must "do nothing for up-to-date project" in { f =>
    val toUpdateVersion  = GradleVersion("4.10.3")
    val distributionType = GradleDistributionType.Bin
    val target           = new FileSystemGradleUpdater(toUpdateVersion, Some(distributionType))
    val dir              = createProject(f.dir, toUpdateVersion, distributionType)
    target.tryUpdate(dir) shouldBe NotUpdated(Seq(FileSystemDirectoryResult.UpToDate(dir)))
  }

  Seq("", "subdir").foreach(subDir => {
    it must s"update the project in '$subDir'" in {
      f =>
        val toUpdateVersion  = GradleVersion("4.10.3")
        val target           = new FileSystemGradleUpdater(toUpdateVersion, None)
        val distributionType = GradleDistributionType.Bin
        val dir              = createProject(f.dir.resolve(subDir), GradleVersion("4.8.1"), distributionType)
        target.tryUpdate(dir) match {
          case Updated(Seq(result)) =>
            result shouldBe FileSystemDirectoryResult.Updated(dir)
            Files
              .readAllLines(dir.resolve("gradle/wrapper/gradle-wrapper.properties"))
              .asScala
              .mkString("") should include(
              GradleDistributionUrlProvider(toUpdateVersion)
                .get(distributionType)
                .toString
                .replace(":", "\\:"))
          case other => fail(s"Updated result expected but $other found")
        }
    }
  })

  it must "detected outdated project in dry-run" in { f =>
    val toUpdateVersion  = GradleVersion("4.10.3")
    val target           = new FileSystemGradleUpdater(toUpdateVersion, None)
    val distributionType = GradleDistributionType.Bin
    val dir              = createProject(f.dir, GradleVersion("4.8"), distributionType)
    target.tryUpdateDryRun(dir) shouldBe WouldBeUpdated(Seq(FileSystemDirectoryResult.WouldBeUpdated(dir)))
  }

  private def createProject(dir: Path,
                            version: GradleVersion,
                            distributionType: GradleDistributionType): Path = {
    new GradleWrapperFiles(version)
      .get(distributionType)
      .foreach(wf => {
        Files.createDirectories(dir.resolve(wf.path).getParent)
        Files.write(dir.resolve(wf.path),
                    wf.content,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING)
      })
    dir
  }
}
