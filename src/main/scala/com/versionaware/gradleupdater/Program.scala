package com.versionaware.gradleupdater

import java.util.regex.Pattern

import com.typesafe.scalalogging.StrictLogging
import org.gitlab4j.api.GitLabApi

import scala.collection.JavaConverters._
import scala.util.control.NonFatal

object Program extends StrictLogging {
  case class CommandLineArgs(gradleVersion: Option[String],
                             gitlabUri: String,
                             gitlabUsername: String,
                             gitlabPassword: String,
                             filter: Option[Pattern])
  private val cmdParser =
    new scopt.OptionParser[CommandLineArgs]("docker run --rm versionaware/gradle-gitlab-updater") {
      help("help")
      opt[String]('g', "gitlab-uri")
        .action((v, args) => args.copy(gitlabUri = v))
        .text("GitLab Uri, like http://mygitlab.com")
        .required()
      opt[String]('u', "gitlab-username")
        .action((v, args) => args.copy(gitlabUsername = v))
        .text("Username to access GitLab.")
        .required()
      opt[String]('p', "gitlab-password")
        .action((v, args) => args.copy(gitlabPassword = v))
        .text("Password to access GitLab.")
        .required()
      opt[String]('f', "filter")
        .action((v, args) => args.copy(filter = Some(Pattern.compile(v))))
        .text("Regular expression that must match for project ID, like 'my-group/my-project'.")
      opt[String]('v', "gradle-version")
        .action((v, args) => args.copy(gradleVersion = Some(v)))
        .text("Gradle version to update to. If not specified then the latest stable version is used.")
    }

  def main(args: Array[String]): Unit = {
    if (args.isEmpty) {
      cmdParser.showUsage()
    } else {
      val cmdArgs =
        cmdParser.parse(args, CommandLineArgs(None, "", "", "", None)) match {
          case Some(ca) => ca
          case None     => sys.error("Invalid arguments")
        }
      val gradleVersion = cmdArgs.gradleVersion match {
        case Some(gv) => GradleVersion(gv)
        case None     => GradleCurrentVersion.get()
      }
      val gitLabApi = GitLabApi.login(cmdArgs.gitlabUri,
                                      cmdArgs.gitlabUsername,
                                      cmdArgs.gitlabPassword)
      val gradleUpdater = new GitLabGradleUpdater(gitLabApi, gradleVersion)
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
