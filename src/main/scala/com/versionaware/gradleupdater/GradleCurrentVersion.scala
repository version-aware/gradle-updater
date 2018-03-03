package com.versionaware.gradleupdater

import java.net.URL

import com.softwaremill.sttp._
import io.circe.parser.decode
import io.circe.Decoder
import io.circe.generic.auto._
import io.circe.parser._

import scala.util.Try

object GradleCurrentVersion {
  implicit val httpBackend = HttpURLConnectionBackend()
  implicit val urlDecoder: Decoder[URL] =
    Decoder.decodeString.emapTry(s => Try(new URL(s)))

  def get(): GradleVersion =
    sttp
      .get(uri"https://services.gradle.org/versions/current")
      .response(asString)
      .send()
      .body
      .flatMap(b => decode[GradleVersion](b)) match {
      case Left(msg) =>
        sys.error(s"Error when getting current Gradle version: $msg")
      case Right(value) => value
    }
}
