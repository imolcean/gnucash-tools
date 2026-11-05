# Write rows exactly as GnuCash desktop 5.16 does, enforced by golden tests

Status: Accepted, 2026-11-04

## Context

GnuCash desktop is still the main tool. Rows written by the phone must look to desktop (and to reports, reconciliation and future imports) exactly like rows desktop writes itself. A "minimal insert" (one `transactions` row, two `splits`) loads in desktop, but differs in subtle ways: missing slots, NULL vs. epoch dates, different time of day.

`sample-book.sql` is a pg_dump of a book written by GnuCash 5.16 on PG 17.11 and is the reference.

## Decision

Writes reproduce these facts from the dump:

- `transactions.post_date` = the chosen date at `10:59:00`; `enter_date` = now in UTC; `num` = `''`; `currency_guid` = the book currency.
- `splits.memo` and `splits.action` = `''`; `value_denom` = `quantity_denom` = 100 (commodity fraction / account SCU).
- Unreconciled split: `reconcile_state = 'n'` and `reconcile_date = '1970-01-01 00:00:00'`, not NULL.
- Every transaction has a `slots` row `date-posted`, `slot_type = 10`, `gdate_val` = the date.
- Notes are a `slots` row `notes`, `slot_type = 4`, `string_val` = the text; only when notes are set.
- Unused slot columns: `int64_val = 0`, `timespec_val = '1970-01-01 00:00:00'`, `numeric_val_num/denom = 0/1`, others NULL.
- GUIDs are random UUIDs as 32 lowercase hex chars without dashes.
- Delete removes the transaction's slots and its splits' slots too.

Golden tests (Testcontainers `postgres:17.11` loaded from the dump) write the same entry into a fresh DB and compare rows with the desktop-written ones, ignoring GUIDs, `enter_date` and slot ids:

- *Storage room*: Rent 20.00 ← Cash, 2026-11-03 (plain two-split).
- *[Tag 1] Rewe again*: Groceries 7.66 ← Cash, note "Forgot something" (Cash reconcile fields ignored; desktop reconciled it afterwards).
- *Concert tickets (maybe?)*: Misc 200.00 ← Cash, 2026-11-10 (future-dated).

## Consequences

- Any new write shape needs a new desktop-written fixture row first.
- A GnuCash upgrade that changes write shapes is caught by refreshing the dump and re-running the golden tests.
