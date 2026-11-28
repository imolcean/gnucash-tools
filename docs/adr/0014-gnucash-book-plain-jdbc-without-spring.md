# gnucash-book is plain JDBC, without Spring

Status: Accepted, 2026-11-28 (gnucash-book 0.2.0). Supersedes the `spring-jdbc` dependency named in ADR-0002.

## Context

gnucash-book used `spring-jdbc`: `JdbcClient` and `RowMapper` for about 48 queries, `DataSourceTransactionManager` and `TransactionTemplate` for the write transaction. Its flattened pom (ADR-0013) therefore pinned `spring-jdbc`, `spring-tx`, `spring-beans`, `spring-core`, `commons-logging` and `jspecify` at the versions Spring Boot resolved here. A consumer outside this repository, such as another application's adapter that posts transactions to the book, gets those Spring versions forced next to its own, or has to exclude them, although the library uses nothing of Spring beyond running SQL.

The library's SQL needs are small: positional parameters, rows mapped to records, one DB transaction per write with the lock, schema and `FOR UPDATE` checks inside it (ADR-0004, ADR-0005).

## Decision

- **No Spring in gnucash-book.** The only runtime dependency is the Postgres driver. An ArchUnit rule (`ArchitectureTests.noSpring`) keeps `org.springframework` out of the main code, and the published flattened pom names no Spring artifact.
- **A package-private `Jdbc` helper** does the work: `list`, `optional`, `single` and `update` with positional `?` parameters and a `RowMapper` lambda. Over the pool, every call takes a connection and closes it. A `Collection` parameter binds as a `text[]` for `= ANY(?)` (replacing `JdbcClient`'s `IN (:list)` expansion).
- **`Jdbc.inTransaction(Function<Jdbc, T>)`** takes one connection, turns auto-commit off, runs the work on a `Jdbc` bound to that connection, commits on return and rolls back on any exception. `GnuCashBook.write` runs `LOCK TABLE gnclock IN SHARE MODE`, the lock check and the schema check inside it, as before.
- **Database failures are `BookAccessException`** (public, unchecked), with the `SQLException` and its SQLSTATE as the cause, instead of Spring's `DataAccessException`/`TransactionException`. `WriteRefusedException` stays the refusal of a write the book could have done.
- The public constructor `GnuCashBook(DataSource, Clock)` is unchanged.
- **The test-jar's `SampleBook` needs no Spring either**: `PGSimpleDataSource` and plain JDBC. The book's own tests use a small `Sql` helper over `Jdbc`.

## Consequences

- A consumer adds gnucash-book and the Postgres driver and nothing else; its Spring, if any, is its own.
- gnucash-togo keeps Spring Boot. Its `BookFailureAdvice` now handles `BookAccessException` (the SQLSTATE walk in `BookFailure` is unchanged), and its tests declare `spring-jdbc` themselves for their raw SQL.
- The helper is ours to maintain, but it is about 150 lines and covers only what the book uses; batching, named parameters and streaming are not needed.
