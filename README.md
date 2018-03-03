# Gradle GitLab Updater [![Build Status](https://travis-ci.org/version-aware/gradle-updater.svg)](https://travis-ci.org/version-aware/gradle-updater) [![Docker Pulls](https://img.shields.io/docker/pulls/versionaware/gradle-gitlab-updater.svg)](https://hub.docker.com/r/versionaware/gradle-gitlab-updater/)

Command-line application that updates [Gradle Wrapper](https://docs.gradle.org/current/userguide/gradle_wrapper.html) in GitLab projects.

It iterates all the projects accessible for the specified user, with optional filter.
 If outdated Gradle Wrapper is detected then it creates a new Merge Request with the specified Gradle Wrapper version.

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
  -f, --filter <value>     Regular expression that must match for project ID, like 'my-group/my-project'.
  -v, --gradle-version <value>
                           Gradle version to update to. If not specified then the latest stable version is used.
```
