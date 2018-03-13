package com.versionaware.gradleupdater

import java.net.URL

case class GradleVersion(version: String) {
  val releaseNotes: URL = new URL(s"https://docs.gradle.org/$version/release-notes.html")
}
