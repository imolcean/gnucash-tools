# gnucash-tools

Adapters and tooling for a GnuCash 5.16 book in Postgres. Base package `org.duckdns.gory26.gnucash`.

- `gnucash-book`: the book adapter (SQL, exact rationals). Test fixture `SampleBook` (Testcontainers Postgres). Terms: `gnucash-book/GLOSSARY.md`.
- `gnucash-togo`: phone front end (Spring Boot, Thymeleaf, htmx); depends on `gnucash-book`.
- `gnucash-reports`: Sankey reports for GnuCash desktop in Guile Scheme (`src/main/scheme/gnucash-tools-reports`); the tests render them in the `gnucash-headless` image named in its `pom.xml`.

## Tests

- `./mvnw verify -P fast` runs without Docker: it skips the classes tagged `@Tag("docker")`.
- `./mvnw verify` runs everything and needs Docker Desktop running.
- A new test class that starts a container (itself, or through `SampleBook`, `GnuCash` or by extending `AppTest`) gets `@Tag("docker")`.

## Working rules

- Each design decision ships with an ADR in `docs/adr` (number after the highest; `Status`, Context, Decision, Consequences) as part of the same work.
- Plans: `docs/plans/gnucash-togo.md`, `docs/plans/pp.md`. Read the status line first; update it when a milestone finishes.
