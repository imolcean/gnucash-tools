# Multi-module repo with one module per deployable app

Status: Accepted, 2026-11-04

## Context

The repo held one Spring Boot module with fincore's domain code. gnucash-togo needs a GnuCash adapter, and a later importer into fincore needs the same adapter. Putting everything into one app, or into a shared "fintools" module, would couple unrelated deployables and their dependencies.

## Decision

The root `pom.xml` becomes an aggregator (`packaging=pom`, parent `spring-boot-starter-parent` 4.1.2). Modules:

- **fincore-core**: plain library with the fincore domain (jMolecules, no Spring Boot app, no persistence).
- **gnucash-book**: the GnuCash adapter library. Depends on `spring-jdbc` only, uses `JdbcClient` (no JPA), and takes a `DataSource` from its caller. (Since 0.2.0 plain JDBC without Spring: ADR-0014.)
- **gnucash-togo**: the phone app (Spring Boot web). Depends on gnucash-book, not on fincore-core (ADR-0001).
- **gnucash-import** (later): GnuCash → fincore importer, a port of the 1C project's ADR-0023 split mapping. Depends on gnucash-book and fincore-core.

Rule: one module per deployable app, plus libraries with a clear single job. No grab-bag module.

## Consequences

- gnucash-book knows nothing about HTTP, sessions or connection pools; the app owns those.
- The importer can reuse the adapter without pulling in the web app.
- CI builds one app with `./mvnw -pl gnucash-togo -am`, and its image workflow triggers only on `gnucash-book/**`, `gnucash-togo/**`, `pom.xml` and `.mvn/**`.
- Dropping gnucash-togo later is deleting one module.
