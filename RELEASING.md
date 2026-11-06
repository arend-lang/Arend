# Releasing

A release is a tag. Pushing it starts the [release workflow](.github/workflows/release.yml), which builds every file
from the tagged commit, tests the files themselves, and publishes them once a maintainer approves. The plugin is
uploaded to JetBrains Marketplace by hand, from the files the workflow tested.

## Versions

`gradle.properties` holds the version, `arendVersion`, and `pluginRevision`, which is empty except in a release of the
plugin alone. `./gradlew check` fails unless the rest agrees with them:

- `langVersion` and `version` in `arend-lib/arend.yaml` are the major and minor version, such as `1.13`;
- `arend-lib/versions` ends with the line `1.13 -> 1.13.0`. Installed plugins read this file from `master` to find the
  library for their language version, so a release is merged into `master` before it is tagged.

`CHANGELOG.md` has a section for each release. It becomes the plugin's change notes and the body of the GitHub release.
Write each entry on one line: GitHub shows a line break in a release body as a line break.

## A release

1. Prepare the release on a branch, and merge it into `master`:
   - set `arendVersion=1.13.0`, and leave `pluginRevision` empty;
   - set `langVersion: 1.13` and `version: 1.13` in `arend-lib/arend.yaml`, and add `1.13 -> 1.13.0` to
     `arend-lib/versions`;
   - run `./gradlew patchChangelog`, which renames the `Unreleased` section of `CHANGELOG.md` to `1.13.0 - <date>`.
2. Tag the merge commit: `git tag v1.13.0 <commit> && git push origin v1.13.0`.
3. Wait until the `build` and `smoke` jobs of the run are green, and approve the `release` environment. The `publish`
   job then creates the GitHub release, and `post-check` checks the URLs that users and installed plugins download from.
4. Download the `release` artifact of the run, and upload `intellij-arend-1.13.0.zip` from it to JetBrains Marketplace,
   entering the token by hand. Paste `NOTES.md` from the artifact as the release notes if Marketplace asks for them.

`SHA256SUMS` in the GitHub release has the digest of the plugin zip too, and
`gh attestation verify intellij-arend-1.13.0.zip -R arend-lang/Arend` shows that the workflow built a file.

## A release of the plugin alone

Set `pluginRevision=1` and run `./gradlew patchChangelog`, which names the section `1.13.0.1 - <date>`. Merge, and push
the tag `v1.13.0.1`. The workflow builds and tests the plugin zip as above but creates no GitHub release; upload the zip
from the artifacts of the run. The tag records which commit each Marketplace upload came from.

## A rehearsal

A tag with a suffix, such as `v1.13.0-rc.1`, runs the same jobs, but `publish` creates a draft prerelease, which only
maintainers can see. Delete the draft and the tag afterwards.

## Building the files locally

`./gradlew releaseAssets` builds the files of a release into `build/release`. It fails if `arend-lib` has uncommitted
changes in what goes into `arend-lib.zip`.

## Setup

The `release` environment (Settings > Environments) must exist and require a reviewer. The workflow does not publish
without one, because a job that names an environment which does not exist creates it, unprotected.
