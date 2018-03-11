package com.versionaware.gradleupdater

import java.util.regex.Pattern

import com.typesafe.scalalogging.StrictLogging
import org.gitlab4j.api.GitLabApi

import scala.collection.JavaConverters._
import scala.util.control.NonFatal

object Program extends StrictLogging {
  case class CommandLineArgs(gradleVersion: Option[String],
                             gradleDistribution: Option[GradleDistributionType],
                             gitlabUri: String,
                             gitlabUsername: Option[String],
                             gitlabPassword: Option[String],
                             gitLabPrivateToken: Option[String],
                             filter: Option[Pattern])
  private val cmdParser =
    new scopt.OptionParser[CommandLineArgs](
      "docker run --rm versionaware/gradle-gitlab-updater") {

      head("Gradle GitLab Updater by VersionAware")
      head(
        "Iterates all the projects accessible for the specified user, with optional filter.")
      head(
        "If outdated Gradle Wrapper is detected then it creates a new Merge Request with the specified Gradle Wrapper version.")
      head(
        "The user must have at least Developer permission to create a new MR.")
      head("")

      help("help")
      opt[String]('g', "gitlab-uri")
        .action((v, args) => args.copy(gitlabUri = v))
        .text("GitLab Uri, like http://mygitlab.com")
        .required()
      opt[String]('u', "gitlab-username")
        .action((v, args) => args.copy(gitlabUsername = Some(v)))
        .text("Username to access GitLab.")
      opt[String]('p', "gitlab-password")
        .action((v, args) => args.copy(gitlabPassword = Some(v)))
        .text("Password to access GitLab.")
      opt[String]('t', "gitlab-token")
        .action((v, args) => args.copy(gitLabPrivateToken = Some(v)))
        .text("Private token to access GitLab.")
      opt[String]('f', "filter")
        .action((v, args) => args.copy(filter = Some(Pattern.compile(v))))
        .text("Regular expression that must match for project ID, like 'my-group/my-project'.")
      opt[String]('v', "gradle-version")
        .action((v, args) => args.copy(gradleVersion = Some(v)))
        .text("Gradle version to update to. If not specified then the latest stable version is used.")
      opt[String]('d', "gradle-distribution")
        .action((v, args) =>
          args.copy(gradleDistribution = Some(parseGradleDitribution(v))))
        .text("Gradle distribution to use - 'all' or 'bin'. If not specified then the distribution is not changed.")
    }

  private def parseGradleDitribution(v: String): GradleDistributionType =
    v.toLowerCase.trim match {
      case "all" => GradleDistributionType.All
      case "bin" => GradleDistributionType.Bin
      case _     => sys.error(s"Unknown Gradle distribution type: '$v'")
    }

  def main(args: Array[String]): Unit = {
    if (args.isEmpty) {
      cmdParser.showUsage()
    } else {
      val cmdArgs =
        cmdParser.parse(
          args,
          CommandLineArgs(None, None, "", None, None, None, None)) match {
          case Some(ca) => ca
          case None     => sys.error("Invalid arguments")
        }
      val gradleVersion = cmdArgs.gradleVersion match {
        case Some(gv) => GradleVersion(gv)
        case None     => GradleCurrentVersion.get()
      }
      val gitLabApi = cmdArgs.gitLabPrivateToken match {
        case Some(privateToken) =>
          new GitLabApi(cmdArgs.gitlabUri, privateToken)
        case None =>
          GitLabApi.login(cmdArgs.gitlabUri,
                          cmdArgs.gitlabUsername.getOrElse(""),
                          cmdArgs.gitlabPassword.getOrElse(""))
      }
      val gradleUpdater = new GitLabGradleUpdater(gitLabApi,
                                                  gradleVersion,
                                                  cmdArgs.gradleDistribution)
      try {
        gitLabApi.getProjectApi
          .getProjects(0, Integer.MAX_VALUE)
          .asScala
          .filter(p =>
            cmdArgs.filter.forall(_.asPredicate().test(p.getPathWithNamespace)))
          .foreach(p => {
            print(s"Going to check ${p.getNameWithNamespace}...")
            try {
              val r = gradleUpdater.tryUpdate(p)
              println(r.toString)
            } catch {
              case NonFatal(e) =>
                println(e.getMessage)
                e.printStackTrace(sys.process.stdout)
            }
          })
      } finally {
        gradleUpdater.close()
      }
    }
  }
}
