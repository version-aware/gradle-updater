# Gradle Updater [![Build Status](https://travis-ci.org/version-aware/gradle-updater.svg)](https://travis-ci.org/version-aware/gradle-updater) [![GitHub release](https://img.shields.io/github/release/version-aware/gradle-updater.svg)](https://hub.docker.com/r/versionaware/gradle-updater/)

Command-line application that updates [Gradle Wrapper](https://docs.gradle.org/current/userguide/gradle_wrapper.html) in GitLab projects,
 or in projects on the disk.

## GitLab support
It iterates all the projects accessible for the specified user, with optional filter.
 If an outdated Gradle Wrapper is detected then it creates a new Merge Request with the specified Gradle Wrapper version.
 The user must have at least [Developer permission](https://docs.gitlab.com/ee/user/permissions.html) to create a new MR.

## Usage
It's distributed as Linux Docker image and so can be used easily:
```cmd
# docker run --rm versionaware/gradle-updater
Usage: docker run --rm versionaware/gradle-updater [options]

  --help
  --dry-run                Doesn't create merge requests.
  --dir <value>            Path to directory that will be searched for Gradle projects to update
  -g, --gitlab-uri <value>
                           GitLab Uri, like http://mygitlab.com
  -u, --gitlab-username <value>
                           Username to access GitLab.
  -p, --gitlab-password <value>
                           Password to access GitLab.
  --gitlab-password-stdin  Password to access GitLab will be read from stdin.
  -t, --gitlab-token <value>
                           Private token to access GitLab.
  --gitlab-token-stdin     Private token to access GitLab will be read from stdin.
  --gitlab-filter <value>  Regular expression that must match for project ID, like 'my-group/my-project'.
  -v, --gradle-version <value>
                           Gradle version to update to. If not specified then the latest stable version is used.
  -d, --gradle-distribution <value>
                           Gradle distribution to use - 'all' or 'bin'. If not specified then the distribution is not changed.
  -l, --log-level <value>  Log-level to use, default is INFO. Other values: OFF, ERROR, WARN, DEBUG, TRACE, ALL
```

When updating Gradle projects on disk then the directory must be mounted to the Docker image, e.g.:
```sh
docker run --rm -v /my/directory:/projects versionaware/gradle-updater --dir /projects
```

# How to use in real life
It's good idea to execute the updater periodically. E.g. you can schedule the updater in your CI (Jenkins, TeamCity, Bamboo, ...)
 to be executed e.g. once a week. Then you don't have to think about new Gradle versions, the merge requests with the update just appear.

If you automatically build merge requests then you can immediatelly see if the new Gradle version causes an issue.
