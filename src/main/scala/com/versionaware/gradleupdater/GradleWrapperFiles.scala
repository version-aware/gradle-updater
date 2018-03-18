package com.versionaware.gradleupdater

import java.net.URL
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}

import com.versionaware.gradleupdater.GradleDistributionType.{All, Bin}

import scala.collection.JavaConverters._

class GradleWrapperFiles(gradleVersion: GradleVersion) {
  private val distributionUrlProvider = new GradleDistributionUrlProvider(gradleVersion)

  private val (gradlew, gradlewBat, jar, binProperties, allProperties) = {
    val dir = GradleReferenceDirectory.initialize(gradleVersion)
    try {
      val propertiesLines = Files
        .readAllLines(dir.resolve("gradle").resolve("wrapper").resolve("gradle-wrapper.properties"))
        .asScala
      (Files.readAllBytes(dir.resolve("gradlew")),
       Files.readAllBytes(dir.resolve("gradlew.bat")),
       Files.readAllBytes(dir.resolve("gradle").resolve("wrapper").resolve("gradle-wrapper.jar")),
       replaceDistributionUrl(propertiesLines, distributionUrlProvider.bin),
       replaceDistributionUrl(propertiesLines, distributionUrlProvider.all))
    } finally GradleReferenceDirectory.remove(dir)
  }

  private def replaceDistributionUrl(lines: Seq[String], distributionUrl: URL): Array[Byte] = {
    val newLines = lines.map {
      case l if l.startsWith("distributionUrl=") =>
        s"distributionUrl=${distributionUrl.toString.replace(":", "\\:")}"
      case l => l
    }
    newLines.mkString("\n").getBytes(StandardCharsets.UTF_8)
  }

  def get(t: GradleDistributionType): Seq[GradleWrapperFile] = t match {
    case All =>
      Seq(
        GradleWrapperFile(Paths.get("gradlew"), gradlew),
        GradleWrapperFile(Paths.get("gradlew.bat"), gradlewBat),
        GradleWrapperFile(Paths.get("gradle", "wrapper", "gradle-wrapper.jar"), jar),
        GradleWrapperFile(Paths.get("gradle", "wrapper", "gradle-wrapper.properties"), allProperties)
      )
    case Bin =>
      Seq(
        GradleWrapperFile(Paths.get("gradlew"), gradlew),
        GradleWrapperFile(Paths.get("gradlew.bat"), gradlewBat),
        GradleWrapperFile(Paths.get("gradle", "wrapper", "gradle-wrapper.jar"), jar),
        GradleWrapperFile(Paths.get("gradle", "wrapper", "gradle-wrapper.properties"), binProperties)
      )
  }

}

case class GradleWrapperFile(path: Path, content: Array[Byte])
