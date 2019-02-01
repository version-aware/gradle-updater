package com.versionaware.gradleupdater

import java.net.URL
import java.nio.file.{FileVisitOption, Files, Path, StandardOpenOption}
import java.util.Comparator

import com.typesafe.scalalogging.StrictLogging

import scala.collection.JavaConverters._
import scala.sys.process

object GradleReferenceDirectory extends StrictLogging {

  def remove(referenceDirectory: Path): Unit =
    Files
      .walk(referenceDirectory, FileVisitOption.FOLLOW_LINKS)
      .sorted(Comparator.reverseOrder())
      .forEach(p => p.toFile.delete())

  def initialize(gradleVersion: GradleVersion): Path = {
    val d = Files.createTempDirectory("gradlewrapper")
    logger.debug(s"Initializing Gradle reference directory in $d")
    copySeedFromResources(d)
    replaceDistributionUrl(d.resolve("gradle/wrapper/gradle-wrapper.properties"),
                           GradleDistributionUrlProvider(gradleVersion).bin)
    executeWrapperTask(d)
    d
  }

  private def copySeedFromResources(targetDir: Path): Unit = {
    Seq("gradlew",
        "gradlew.bat",
        "gradle/wrapper/gradle-wrapper.jar",
        "gradle/wrapper/gradle-wrapper.properties").foreach { name =>
      val s = getClass.getClassLoader.getResourceAsStream(name)
      try {
        val t = targetDir.resolve(name)
        Files.createDirectories(t.getParent)
        Files.copy(s, t)
      } finally s.close()
    }
    if (!isWindows) {
      require(process
                .Process(Seq("chmod", "+x", "gradlew"), targetDir.toFile)
                .run()
                .exitValue() == 0,
              "Cannot set executable flag for gradlew")
    }
  }

  private def replaceDistributionUrl(propertiesFile: Path, distributionUrl: URL): Unit = {
    val newLines = Files.readAllLines(propertiesFile).asScala.map {
      case l if l.startsWith("distributionUrl=") =>
        s"distributionUrl=${distributionUrl.toString.replace(":", "\\:")}"
      case l => l
    }
    Files.write(propertiesFile, newLines.asJava, StandardOpenOption.TRUNCATE_EXISTING)
  }

  private def executeWrapperTask(referenceDir: Path): Unit = {
    val cmd =
      if (isWindows) Seq("gradlew.bat", "wrapper", "--no-daemon")
      else Seq("./gradlew", "wrapper", "--no-daemon")
    // We must execute the Wrapper twice to regenerate the scripts correctly.
    // The first call could fail on Windows (because the bat is overwritten during execution).
    process
      .Process(cmd, referenceDir.toFile)
      .run()
      .exitValue()
    require(process
              .Process(cmd, referenceDir.toFile)
              .run()
              .exitValue() == 0,
            "gradlew wrapper exited with non-zero exit value")
  }

  val isWindows: Boolean =
    System.getProperty("os.name").toLowerCase.startsWith("windows")
}
