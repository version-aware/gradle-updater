package com.versionaware.gradleupdater

import java.nio.file.Paths
import java.time.Duration
import java.util.regex.Pattern

import ch.qos.logback.classic.Level
import com.typesafe.scalalogging.StrictLogging
import com.versionaware.gradleupdater.filesystem.FileSystemGradleUpdater
import com.versionaware.gradleupdater.gitlab.GitLabGradleUpdater
import com.versionaware.gradleupdater.gitlab.GitLabProjectResult._
import org.gitlab4j.api.GitLabApi
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.JavaConverters._
import scala.util.control.NonFatal

object Program extends StrictLogging {
  case class CommandLineArgs(logLevel: Level = Level.INFO,
                             dryRun: Boolean = false,
                             gradleVersion: Option[String] = None,
                             gradleDistribution: Option[GradleDistributionType] = None,
                             directory: String = "",
                             gitlabUri: String = "",
                             gitlabUsername: Option[String] = None,
                             gitlabPassword: Option[String] = None,
                             gitlabPasswordFromStdIn: Boolean = false,
                             gitlabPrivateToken: Option[String] = None,
                             gitlabPrivateTokenFromStdIn: Boolean = false,
                             gitlabFilter: Option[Pattern] = None)
  private val cmdParser =
    new scopt.OptionParser[CommandLineArgs]("docker run --rm versionaware/gradle-updater") {

      head("Gradle Updater by VersionAware")
      head(
        "if 'gitlab-uri' parameter is specified then iterates all the GitLab projects accessible for the specified user, with optional filter.")
      head(
        "If outdated Gradle Wrapper is detected then it creates a new Merge Request with the specified Gradle Wrapper version.")
      head("The user must have at least Developer permission to create a new MR.")
      head("")
      head(
        "It's also possible to specify the 'directory' - then this directory is searched for Gradle projects to update.")
      head("")

      help("help")
      opt[Unit]("dry-run")
        .action((v, args) => args.copy(dryRun = true))
        .text("Doesn't create merge requests.")
      opt[String]("dir")
        .action((v, args) => args.copy(directory = v))
        .text("Path to directory that will be searched for Gradle projects to update")
      opt[String]('g', "gitlab-uri")
        .action((v, args) => args.copy(gitlabUri = v))
        .text("GitLab Uri, like http://mygitlab.com")
      opt[String]('u', "gitlab-username")
        .action((v, args) => args.copy(gitlabUsername = Some(v)))
        .text("Username to access GitLab.")
      opt[String]('p', "gitlab-password")
        .action((v, args) => args.copy(gitlabPassword = Some(v)))
        .text("Password to access GitLab.")
      opt[Unit]("gitlab-password-stdin")
        .action((v, args) => args.copy(gitlabPasswordFromStdIn = true))
        .text("Password to access GitLab will be read from stdin.")
      opt[String]('t', "gitlab-token")
        .action((v, args) => args.copy(gitlabPrivateToken = Some(v)))
        .text("Private token to access GitLab.")
      opt[Unit]("gitlab-token-stdin")
        .action((v, args) => args.copy(gitlabPrivateTokenFromStdIn = true))
        .text("Private token to access GitLab will be read from stdin.")
      opt[String]("gitlab-filter")
        .action((v, args) => args.copy(gitlabFilter = Some(Pattern.compile(v))))
        .text("Regular expression that must match for project ID, like 'my-group/my-project'.")
      opt[String]('v', "gradle-version")
        .action((v, args) => args.copy(gradleVersion = Some(v)))
        .text("Gradle version to update to. If not specified then the latest stable version is used.")
      opt[String]('d', "gradle-distribution")
        .action((v, args) => args.copy(gradleDistribution = Some(parseGradleDistribution(v))))
        .text("Gradle distribution to use - 'all' or 'bin'. If not specified then the distribution is not changed.")
      opt[String]('l', "log-level")
        .action((v, args) => args.copy(logLevel = Level.valueOf(v)))
        .text("Log-level to use, default is INFO. Other values: OFF, ERROR, WARN, DEBUG, TRACE, ALL")
    }

  private def parseGradleDistribution(v: String): GradleDistributionType =
    v.toLowerCase.trim match {
      case "all" => GradleDistributionType.All
      case "bin" => GradleDistributionType.Bin
      case _     => sys.error(s"Unknown Gradle distribution type: '$v'")
    }

  def main(args: Array[String]): Unit = {
    if (args.isEmpty) {
      cmdParser.showUsage()
      sys.exit(0)
    }
    val cmdArgs =
      cmdParser.parse(args, CommandLineArgs()) match {
        case Some(ca) => ca
        case None     => sys.error("Invalid arguments")
      }
    val root = LoggerFactory
      .getLogger(Logger.ROOT_LOGGER_NAME)
      .asInstanceOf[ch.qos.logback.classic.Logger]
    root.setLevel(Level.INFO)

    val gradleVersion = cmdArgs.gradleVersion match {
      case Some(gv) => GradleVersion(gv)
      case None =>
        val v = GradleCurrentVersion.get()
        logger.info(s"Will try to update to ${v.version}")
        v
    }
    val fails = scala.collection.mutable.ArrayBuffer.empty[Boolean]
    if (cmdArgs.gitlabUri.nonEmpty) {
      fails.append(tryUpdateInGitLab(cmdArgs, gradleVersion))
    }
    if (cmdArgs.directory.nonEmpty) {
      fails.append(tryUpdateOnFileSystem(cmdArgs, gradleVersion))
    }
    if (fails.contains(true)) sys.exit(1)
  }

  private def tryUpdateInGitLab(cmdArgs: CommandLineArgs, gradleVersion: GradleVersion): Boolean = {
    val gitLabApi     = createGitLabApi(cmdArgs)
    val gradleUpdater = new GitLabGradleUpdater(gitLabApi, gradleVersion, cmdArgs.gradleDistribution)
    val failedProjects = gitLabApi.getProjectApi
      .getProjects()
      .asScala
      .filter(p => {
        val toProcess = cmdArgs.gitlabFilter.forall(_.asPredicate().test(p.getPathWithNamespace))
        if (!toProcess) logger.info(s"Skipping project ${p.getPathWithNamespace}")
        toProcess
      })
      .count(p => {
        logger.debug(s"Going to check ${p.getPathWithNamespace}...")
        val start = System.nanoTime()
        try {
          val r       = if (cmdArgs.dryRun) gradleUpdater.tryUpdateDryRun(p) else gradleUpdater.tryUpdate(p)
          val seconds = Duration.ofNanos(System.nanoTime() - start).getSeconds
          logger.info(s"Check of ${p.getPathWithNamespace} ends with ${r.toString} in $seconds seconds")
          r match {
            case _: Failure | _: AccessDenied | TooLowAccessLevel | UpdateBranchAlreadyExists |
                DoesNotSupportMergeRequests =>
              true
            case _ => false
          }
        } catch {
          case NonFatal(e) =>
            val seconds = Duration.ofNanos(System.nanoTime() - start).getSeconds
            logger.error(
              s"Check of ${p.getPathWithNamespace} ends with ${e.getClass.getSimpleName} in $seconds seconds: ${e.getMessage}",
              e
            )
            true
        }
      })
    failedProjects > 0
  }

  private def createGitLabApi(cmdArgs: CommandLineArgs): GitLabApi =
    if (cmdArgs.gitlabPrivateTokenFromStdIn) {
      new GitLabApi(cmdArgs.gitlabUri, new String(System.console().readPassword()))
    } else if (cmdArgs.gitlabPasswordFromStdIn) {
      GitLabApi.oauth2Login(cmdArgs.gitlabUri,
                            cmdArgs.gitlabUsername.getOrElse(""),
                            System.console().readPassword())
    } else
      cmdArgs.gitlabPrivateToken match {
        case Some(privateToken) =>
          new GitLabApi(cmdArgs.gitlabUri, privateToken)
        case None =>
          GitLabApi.oauth2Login(cmdArgs.gitlabUri,
                                cmdArgs.gitlabUsername.getOrElse(""),
                                cmdArgs.gitlabPassword.getOrElse(""))
      }

  private def tryUpdateOnFileSystem(cmdArgs: CommandLineArgs, gradleVersion: GradleVersion): Boolean = {
    val dir   = Paths.get(cmdArgs.directory)
    val start = System.nanoTime()
    try {
      val gradleUpdater = new FileSystemGradleUpdater(gradleVersion, cmdArgs.gradleDistribution)
      val r             = if (cmdArgs.dryRun) gradleUpdater.tryUpdateDryRun(dir) else gradleUpdater.tryUpdate(dir)
      val seconds       = Duration.ofNanos(System.nanoTime() - start).getSeconds
      logger.info(s"Check of $dir ends with ${r.toString} in $seconds seconds")
      r match {
        case _: Failure => true
        case _          => false
      }
    } catch {
      case NonFatal(e) =>
        val seconds = Duration.ofNanos(System.nanoTime() - start).getSeconds
        logger.error(
          s"Check of $dir ends with ${e.getClass.getSimpleName} in $seconds seconds: ${e.getMessage}",
          e
        )
        true
    }
  }
}
