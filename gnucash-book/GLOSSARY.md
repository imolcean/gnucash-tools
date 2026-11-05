# GnuCash Book

The GnuCash 5.16 SQL book as gnucash-book reads and writes it. Terms here are GnuCash's; fincore-core has its own model (Operation, Dr/Cr Split) and the two must not be mixed.

## Language

### Structure

**Book**:
One GnuCash data file, here a Postgres database; `books` holds its single row pointing at the root account.
_Avoid_: ledger, database

**Account**:
A node in the account tree (`accounts`), with a type (`account_type`: ASSET, CASH, BANK, EXPENSE, INCOME, LIABILITY, CREDIT, EQUITY, TRADING, …) and one commodity. Same word as fincore-core's Account, but GnuCash types differ from fincore's TopType/BalanceSide.

**Placeholder account**:
An account that groups children and may not receive splits (`accounts.placeholder = 1`).
_Avoid_: folder, group

**Hidden account**:
An account flagged `accounts.hidden = 1`; it and its whole subtree are hidden from view but still count in ancestor totals.
_Avoid_: archived, inactive

**Commodity**:
A currency or security that amounts are counted in (`commodities`, e.g. `CURRENCY:EUR`).
_Avoid_: currency (when securities are meant too)

**Fraction / SCU**:
The smallest unit of a commodity as a denominator (`commodities.fraction`, per account `accounts.commodity_scu`); 100 for EUR and USD.
_Avoid_: precision, decimals

### Entries

**Transaction**:
One dated entry (`transactions`) with a currency, `post_date`, `enter_date` and description, made of splits whose values sum to zero. fincore-core calls its counterpart an **Operation**.
_Avoid_: operation, posting

**Split**:
One leg of a transaction (`splits`): an account and a signed amount (positive = debit). Not the same as fincore-core's Split, which is a Dr/Cr pair with an unsigned amount; N GnuCash splits do not map one-to-one onto fincore Splits.
_Avoid_: entry, leg, posting

**Value**:
A split's amount in the transaction's currency (`value_num/value_denom`); values of one transaction sum to zero.

**Quantity**:
A split's amount in its account's commodity (`quantity_num/quantity_denom`); equals value when both commodities are the same, and is what account balances sum.
_Avoid_: amount (ambiguous between value and quantity)

**Transfer**:
A transaction of exactly two splits in one commodity: +amount to the *to* account, −amount from the *from* account. The only shape gnucash-togo creates and edits.
_Avoid_: simple transaction, expense

**Reconcile state**:
Per-split status in `splits.reconcile_state`: `n` not reconciled, `c` cleared, `y` reconciled (with `reconcile_date`; epoch `1970-01-01` when unset).

**Tag**:
A label written as a bracketed prefix of the description, as in `[Tag 1] [Tag 2] Rewe`; GnuCash itself has no tag concept. fincore-core has a first-class Tag.
_Avoid_: category, label

**Slot**:
A key/value attribute attached to any object by GUID (`slots`, typed by `slot_type`), e.g. `date-posted` and `notes` on a transaction, `placeholder` on an account.
_Avoid_: KVP, property

**Notes**:
Free text on a transaction, stored as its `notes` slot; distinct from the split `memo`.

### Balances

**Present balance**:
Sum of an account's split quantities with `post_date` up to today, shown with the sign desktop uses for its type. Matches desktop's *Present* column.

**Total balance**:
Sum including future-dated transactions; desktop's *Total* column. Not what gnucash-togo shows.

**Trading account**:
An account of type TRADING under `Trading:CURRENCY:<mnemonic>`, auto-created when the book option *Use Trading Accounts* is on, that balances each commodity in a cross-currency transaction. Not the same as fincore-core's TradingAccounts, though the idea is the same.

**Price**:
An exchange rate between two commodities on a date (`prices`, rational `value_num/value_denom`); the latest one converts a child balance into its parent's commodity.
_Avoid_: rate (ambiguous with interest)

### Concurrency and schema

**Lock**:
A row in `gnclock (hostname, pid)` written by desktop while it has the book open.
_Avoid_: session, lease

**Schema versions**:
The per-table schema version rows in `versions`; the `Gnucash` and `Gnucash-Resave` rows record application versions, not table schemas.
