package com.versionaware.gradleupdater

import java.net.URL

import com.versionaware.gradleupdater.GradleDistributionType._

case class GradleDistributionUrlProvider(gradleVersion: GradleVersion) {
  val bin: URL = new URL(
    s"https://services.gradle.org/distributions/gradle-${gradleVersion.version}-bin.zip")
  val all: URL = new URL(
    s"https://services.gradle.org/distributions/gradle-${gradleVersion.version}-all.zip")
  def get(t: GradleDistributionType): URL = t match {
    case All => all
    case Bin => bin
  }
}

object GradleDistributionUrlProvider {
  def getType(url: URL): GradleDistributionType =
    if (url.toString.toLowerCase.endsWith("-all.zip")) All else Bin
}

sealed trait GradleDistributionType
object GradleDistributionType {
  case object All extends GradleDistributionType
  case object Bin extends GradleDistributionType
}
