# N-split transactions, accounts, currencies and book options in gnucash-book

Status: Accepted, 2026-11-28 (gnucash-book 0.2.0)

## Context

gnucash-book wrote only the two-split, same-currency `TransferDraft` that gnucash-togo needs (ADR-0001, ADR-0008). A new consumer outside this repository will post double-entry transactions from its own model into the book and read them back to reconcile; that needs any number of splits, other commodities, trading accounts, its own identifiers on the rows, and accounts and currencies that the book may not have yet. gnucash-book must still know nothing about that consumer: the API speaks GnuCash terms only (`GLOSSARY.md`).

The sample book (`sample-book.sql`, desktop 5.16) shows what desktop writes for these shapes:

- *Salary*: three splits, one with the memo *Company-paid flat*.
- *Buy USD @ 1.2*: four splits, Cash USD value 300.00 € / quantity 250.00 $, balanced per commodity through `Trading:CURRENCY:EUR` and `Trading:CURRENCY:USD`. The `prices` row next to it comes from the transfer dialog, not from the transaction.
- *Opening Balance*: a reconciled split, `reconcile_state = 'y'`, `reconcile_date = 2026-11-02 22:59:59` (end of the statement day in Berlin, as UTC).
- Accounts: `code` and `description` are `''` unless typed, `non_std_scu = 0`, `commodity_scu` = the commodity's fraction, a placeholder has a string slot `placeholder = true`. The empty `balance-limit` frames on some accounts are left by the account dialog and are not written.
- Commodities: `CURRENCY:EUR` is `Euro`, `cusip` 978 (the ISO numeric code), fraction 100, `quote_flag` 1, `quote_source` `currency`, `quote_tz` `''`.
- Book options are slots under the book's `options` frame, by full path: `options/Accounts/Use Trading Accounts` (string `t`), `options/Accounts/Day Threshold for Read-Only Transactions (red line)` (a double, 0). The book holds no default-currency option.

## Decision

- **`insertTransaction(NewTransaction)`**: a caller-chosen GUID (32 lowercase hex), currency, date posted, description, notes, string slots, and N `NewSplit`s (account, value in the currency, quantity in the account's commodity, memo, reconcile state `n`/`c`/`y` with a date for `y`, string slots). Checked in the record: values sum to zero, GUID format, at least one split, slot names. Checked against the book inside the write transaction, refused as `WriteRefusedException`: GUID already used (`DUPLICATE_GUID`, naming it), unknown or non-currency currency (`UNKNOWN_COMMODITY`), unknown, root, template or placeholder account (`ACCOUNT_NOT_ALLOWED`), value finer than the currency's fraction or quantity finer than the account's SCU (`AMOUNT_TOO_PRECISE`), value ≠ quantity in an account of the transaction's currency (`VALUE_QUANTITY_MISMATCH`). Hidden and TRADING accounts are allowed; the caller writes the trading splits when the book uses them. Rows follow ADR-0007, with values over the currency fraction and quantities over the account SCU; split GUIDs are made by the book.
- **String slots** (`slot_type = 4`, shaped like desktop's `notes` slot) on the transaction and on each split, in the same DB transaction. `notes` and `date-posted` are written from the fields, and names with `/` (paths into nested frames) are refused. `delete` already removes the slots of the transaction and of its splits.
- **`transaction(guid)` and `transactions(from, to)`** return `StoredTransaction` with every split (`StoredSplit`: GUID, account, value, quantity, memo, reconcile state and date, string slots) and the transaction's string slots, notes and enter date, exact as stored. The date posted is the `date-posted` slot, falling back to the UTC date of `post_date`; the range filters on that date, both ends included.
- **`createAccount(NewAccount)`** under a caller-chosen GUID, as the account dialog writes it; refused on a used GUID, an unknown parent or commodity, or a sibling of the same name (`DUPLICATE_NAME`). Type compatibility with the parent is the caller's business.
- **`findOrCreateCurrency(iso)`** returns the book's `CURRENCY` commodity, adding one shaped like desktop's from `java.util.Currency` (English name, numeric code, default fraction digits) when missing; an advisory lock stops two callers adding the same one. `currency(iso)` only looks.
- **`accountByPath(path)`** finds an account by full name below the root, hidden ones included, and refuses an ambiguous path; `rootAccountGuid()` is the parent for top-level accounts.
- **`bookOptions()`** reads *Use Trading Accounts* and the red-line threshold, as an integer (no floating point, ADR-0003). The library does not enforce the red line; the caller decides.
- The test-jar's `SampleBook` names the root, the trading tree and the other accounts consumers need.

## Consequences

- Golden tests cover the three desktop shapes above, the account and currency rows, and the slot shape; a new write shape still needs a desktop-written fixture first (ADR-0007).
- A role that only has the ADR-0006 grants cannot create accounts or currencies: the consumer's role needs `INSERT` on `accounts` and `commodities` as well.
- The library trusts the caller on trading splits, account types and the red line; a wrong but balanced entry is written as given, as desktop's own scripting would.
