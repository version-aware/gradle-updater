package com.versionaware.gradleupdater.filesystem

import java.nio.file.{Files, Path}
import java.util.Comparator

import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.{Matchers, Outcome}

@RunWith(classOf[JUnitRunner])
abstract class IntegrationSpec extends org.scalatest.fixture.FlatSpec with Matchers {
  case class FixtureParam(dir: Path)

  def withFixture(test: OneArgTest): Outcome = {
    val dir = Files.createTempDirectory("gradleupdater")
    try withFixture(test.toNoArgTest(FixtureParam(dir)))
    finally Files
      .walk(dir)
      .sorted(Comparator.reverseOrder())
      .forEach(p => p.toFile.delete())
  }
}
