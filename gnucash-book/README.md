# gnucash-book

The adapter for a GnuCash 5.16 book stored in Postgres: reads the account tree, balances and registers, and writes
two-split transactions the way GnuCash desktop does. Amounts are exact rationals (`GncNumeric`), never floating
point (ADR-0003). The entry point is `GnuCashBook`, built from a `DataSource` and a `Clock`. Terms: `GLOSSARY.md`.

## Using it from another repository

Releases are on GitHub Packages (ADR-0013). Add the repository and the dependency to the consuming `pom.xml`:

```xml
<repositories>
    <repository>
        <id>github</id>
        <url>https://maven.pkg.github.com/imolcean/gnucash-tools</url>
        <snapshots>
            <enabled>false</enabled>
        </snapshots>
    </repository>
</repositories>

<dependency>
    <groupId>org.duckdns.gory26</groupId>
    <artifactId>gnucash-book</artifactId>
    <version>0.1.2</version>
</dependency>
```

The release list is on the repository's *Packages* page. The published pom has no parent and names its own
dependency versions, so nothing else is fetched from this repository. MIT licence. Java 25 or later.

### Tests against the sample book

The test-jar carries `SampleBook`: a Postgres 17 container (Testcontainers, so Docker must be running) loaded with a
small book written by GnuCash 5.16, with `freshCopy()` for a private database per test and the account GUIDs as
constants. It needs the main jar, the Postgres driver and Testcontainers next to it (versions left out as for a
project on Spring Boot's dependency management; anything else sets them):

```xml
<dependency>
    <groupId>org.duckdns.gory26</groupId>
    <artifactId>gnucash-book</artifactId>
    <version>0.1.2</version>
    <type>test-jar</type>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers-postgresql</artifactId>
    <scope>test</scope>
</dependency>
```

### Authentication

GitHub Packages wants a token even for public packages. Locally, `~/.m2/settings.xml` needs a server whose id
matches the repository id above, with a classic personal access token that has `read:packages`:

```xml
<servers>
    <server>
        <id>github</id>
        <username>YOUR_GITHUB_LOGIN</username>
        <password>${env.GITHUB_PACKAGES_TOKEN}</password>
    </server>
</servers>
```

In GitHub Actions, give the job `permissions: packages: read`, grant the consuming repository read access in the
package's settings (*Manage Actions access*), and let `actions/setup-java` write the server entry:

```yaml
- uses: actions/setup-java@v6
  with:
    distribution: temurin
    java-version: '25'
    server-id: github
    server-username-env-var: GITHUB_ACTOR
    server-password-env-var: GITHUB_TOKEN
- run: ./mvnw -B verify
  env:
    GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
```

## Releasing

Push a tag `gnucash-book/v1.2.3`. `.github/workflows/gnucash-book-publish.yml` sets version 1.2.3, runs the tests
and deploys the jar, the test-jar and the flattened pom. A released version cannot be overwritten; a fix gets a new tag.
