# Gradle GitLab Updater [![Build Status](https://travis-ci.org/version-aware/gradle-updater.svg)](https://travis-ci.org/version-aware/gradle-updater) [![GitHub release](https://img.shields.io/github/release/version-aware/gradle-updater.svg)](https://hub.docker.com/r/versionaware/gradle-gitlab-updater/)

Command-line application that updates [Gradle Wrapper](https://docs.gradle.org/current/userguide/gradle_wrapper.html) in GitLab projects.

It iterates all the projects accessible for the specified user, with optional filter.
 If outdated Gradle Wrapper is detected then it creates a new Merge Request with the specified Gradle Wrapper version.
 The user must have at least [Developer permission](https://docs.gitlab.com/ee/user/permissions.html) to create a new MR.

It's distributed as Linux Docker image and so can be used easily:
```cmd
# docker run --rm versionaware/gradle-gitlab-updater
Usage: docker run --rm versionaware/gradle-gitlab-updater [options]

  --help
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
  -f, --filter <value>     Regular expression that must match for project ID, like 'my-group/my-project'.
  -v, --gradle-version <value>
                           Gradle version to update to. If not specified then the latest stable version is used.
  -d, --gradle-distribution <value>
                           Gradle distribution to use - 'all' or 'bin'. If not specified then the distribution is not changed.
```
