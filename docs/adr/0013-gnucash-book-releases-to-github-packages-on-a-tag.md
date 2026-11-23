# gnucash-book releases go to GitHub Packages on a tag

Status: Accepted, 2026-11-23 (flattened pom added the same day: 0.1.1, with every runtime dependency pinned from 0.1.2). Decided in the split of the fincore repository into gnucash-tools, finhub and the parked fincore.

## Context

`gnucash-book` is the one module here that other code is meant to build on: the planned `pp` sync and importers outside this repository need the book adapter without the apps. Inside the repository the modules use it from the reactor and need nothing published.

## Decision

- **GitHub Packages** (`https://maven.pkg.github.com/imolcean/gnucash-tools`, server id `github`) is the Maven repository: no extra account, and the workflow authenticates with `GITHUB_TOKEN`.
- **A tag `gnucash-book/v1.2.3` releases version 1.2.3**, in the same scheme as `gnucash-togo/v*` and `gnucash-reports/v*`. `.github/workflows/gnucash-book-publish.yml` sets the version from the tag (`versions:set`), runs `gnucash-book`'s tests and deploys it (jar and test-jar, which carries `SampleBook`) by `deploy -pl gnucash-book`. `gnucash-togo` and `gnucash-reports` ship as an image and a zip and are not deployed. The poms on `master` stay at a `-SNAPSHOT` version.
- **The deployed pom is flattened** (`flatten-maven-plugin`, mode `clean`, dependency mode `all`): no parent, every runtime dependency with the version this build resolved (Spring Boot pins some, e.g. `commons-logging`), so a consumer runs what the tests ran, and only name, description, url, scm and licence. A consumer fetches `gnucash-book` alone, and nothing that `spring-boot-starter-parent` declares about Spring (developers, its licence) ends up in it. The licence is MIT, declared in the root pom with the `LICENSE` file.
- `pp` joins the same scheme when it exists.

## Consequences

- GitHub Packages requires a token with `read:packages` even for a public repository's packages, so every consumer needs one in `settings.xml` under server id `github`.
- All modules share the root version, so a `gnucash-book` release number says nothing about the apps' versions, which have their own tags.
- A released version cannot be overwritten; a fix gets a new tag.
