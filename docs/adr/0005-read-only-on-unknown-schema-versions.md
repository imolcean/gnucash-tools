# Read-only mode on an unknown schema `versions` set

Status: Accepted, 2026-11-04

## Context

GnuCash records per-table schema versions in `versions (table_name, table_version)`. piecash 1.2.1 hard-fails with "Unsupported table versions" unless the set matches one it knows; GnuCash 5.16 books pass because the table versions have not changed since 4.1. A future GnuCash release that bumps a table version may change columns the app writes.

The sample book (GnuCash 5.16, PG 17.11) has 22 table rows, e.g. `transactions 4`, `splits 5`, `slots 4`, `prices 3`, `accounts 1`, `commodities 1`, plus `Gnucash 5000016` and `Gnucash-Resave 19920`.

## Decision

- Compare the `versions` rows with the known 5.16 set, **ignoring** the `Gnucash` and `Gnucash-Resave` rows (they change with every application release without changing the schema).
- Match → normal operation. Any difference → **read-only mode** with a banner "Unknown schema → read-only" that shows the diff; write buttons are disabled and the server refuses writes regardless of the UI.
- Do not hard-fail: reading balances is still useful and low-risk.

## Consequences

- A GnuCash upgrade never silently corrupts the book through the phone; it degrades to read-only until the known set is updated after checking the new schema.
- Upgrading desktop alone (same table versions) needs no app change.
