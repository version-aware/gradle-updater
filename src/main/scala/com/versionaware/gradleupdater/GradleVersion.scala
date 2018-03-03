package com.versionaware.gradleupdater

import java.net.URL

case class GradleVersion(version: String, downloadUrl: URL) {
  val releaseNotes: URL = new URL(
    s"https://docs.gradle.org/$version/release-notes.html")
}

object GradleVersion {
  def apply(version: String): GradleVersion =
    GradleVersion(
      version,
      new URL(
        s"https://services.gradle.org/distributions/gradle-$version-bin.zip"))
}
