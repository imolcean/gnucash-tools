# gnucash-book

The adapter for a GnuCash 5.16 book stored in Postgres: reads the account tree, balances, registers and whole
transactions, and writes transactions, accounts and currencies the way GnuCash desktop does (ADR-0007). Amounts are
exact rationals (`GncNumeric`), never floating point (ADR-0003). The entry point is `GnuCashBook`, built from a
`DataSource` and a `Clock`. Plain JDBC: the only runtime dependency is the Postgres driver, no Spring (ADR-0014).
Terms: `GLOSSARY.md`.

## Usage

```java
GnuCashBook book = new GnuCashBook(dataSource, Clock.systemDefaultZone());

// Every write runs in one DB transaction, refused while desktop holds the lock or the schema is unknown
// (ADR-0004, ADR-0005). Refusals are WriteRefusedException with a Reason; database failures BookAccessException.
Commodity eur = book.findOrCreateCurrency("EUR");
String groceries = book.accountByPath("Expenses:Food:Groceries").orElseThrow().guid();
String cash = book.accountByPath("Assets:Cash").orElseThrow().guid();

book.insertTransaction(new NewTransaction(
        "0123456789abcdef0123456789abcdef",          // your GUID, 32 lowercase hex, not used yet
        eur.guid(), LocalDate.of(2026, 11, 3), "Rewe", "",  // currency, date posted, description, notes
        Map.of("source-id", "42"),                   // string slots on the transaction
        List.of(new NewSplit(groceries, new GncNumeric(766, 100), new GncNumeric(766, 100)),
                new NewSplit(cash, new GncNumeric(-766, 100), new GncNumeric(-766, 100), "memo",
                        ReconcileState.CLEARED, null, Map.of()))));

StoredTransaction back = book.transaction("0123456789abcdef0123456789abcdef").orElseThrow();
List<StoredTransaction> november = book.transactions(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30));
```

- Splits may be in other commodities than the transaction's currency (value in the currency, quantity in the
  account's commodity) and in hidden or TRADING accounts. When `bookOptions().useTradingAccounts()` is on, add the
  trading splits yourself, as in the sample book's *Buy USD @ 1.2*. The red line
  (`bookOptions().readOnlyThresholdDays()`) is reported, not enforced.
- `createAccount(new NewAccount(guid, name, parentGuid, type, commodityGuid, placeholder))` adds an account;
  `rootAccountGuid()` is the parent of the top-level ones.
- A role with only the gnucash-togo grants (ADR-0006) cannot create accounts or currencies; that needs `INSERT` on
  `accounts` and `commodities` too.
- What each call checks and writes: ADR-0015.

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
    <version>0.2.0</version>
</dependency>
```

The release list is on the repository's *Packages* page. The published pom has no parent and names its own
dependency versions, so nothing else is fetched from this repository. MIT licence. Java 25 or later.

### Tests against the sample book

The test-jar carries `SampleBook`: a Postgres 17 container (Testcontainers, so Docker must be running) loaded with a
small book written by GnuCash 5.16, with `freshCopy()` for a private database per test, `unlocked(dataSource)` for a
book without desktop's lock row, and the GUIDs of its accounts (root and trading tree included) and of its EUR and
USD commodities as constants. It needs the main jar, the Postgres driver and Testcontainers next to it, no Spring
(versions left out as for a project on Spring Boot's dependency management; anything else sets them):

```xml
<dependency>
    <groupId>org.duckdns.gory26</groupId>
    <artifactId>gnucash-book</artifactId>
    <version>0.2.0</version>
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
