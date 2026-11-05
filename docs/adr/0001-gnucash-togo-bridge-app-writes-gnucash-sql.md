# gnucash-togo is a bridge app that writes GnuCash SQL directly

Status: Accepted, 2026-11-04

## Context

The books live in GnuCash desktop 5.16 on a Postgres 17 database. Phone access is broken today:

- Mobile GnuCash (Android + PHP server) stored amounts 0.01 € off what was typed, most likely float rounding, and summed account balances wrongly.
- gnucash-web gets amounts right but loads every split of an account through piecash, so big accounts are slow or time out. piecash is barely maintained and has no GnuCash 5 support.

fincore will replace GnuCash eventually, but not soon. fincore-core models an Operation as Dr/Cr Split pairs. A GnuCash transaction has N signed splits, and turning it into Dr/Cr pairs is lossy: multi-split and cross-commodity cases need a NeedsReview path (the mapping in the 1C project's ADR-0023).

## Decision

Build **gnucash-togo**, a phone-first web app that reads and writes the GnuCash tables (`transactions`, `splits`, `slots`, …) directly over JDBC, in GnuCash's own shape. It does **not** depend on fincore-core and does not translate to Operations.

It is a bridge: it is removed when fincore replaces GnuCash. Desktop stays the tool for account creation, multi-split entries and reports.

## Consequences

- No lossy mapping in the write path: what the phone writes is exactly a GnuCash transaction.
- The app must reproduce desktop's row shapes byte for byte (ADR-0007) and respect desktop's lock and schema version (ADR-0004, ADR-0005).
- GnuCash knowledge sits in `gnucash-book` and can be reused by the later importer into fincore (ADR-0002).
- Throwaway by design: avoid investing in features fincore will own.
