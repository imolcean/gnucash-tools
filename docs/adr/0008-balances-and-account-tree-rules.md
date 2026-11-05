# Balances and account tree follow desktop's Present column

Status: Accepted, 2026-11-04

## Context

Mobile GnuCash summed balances wrongly, and gnucash-web loaded every split to compute them. The user compares the phone with desktop's account tree, so the numbers must match desktop's **Present** column, not its Total (which includes future-dated entries such as the 200.00 € *Concert tickets (maybe?)* dated 2026-11-10 in the sample).

The sample book has hidden accounts (`accounts.hidden = 1`; *Visible nested* is hidden only through its parent *Hidden*), a USD account under a EUR parent, a `prices` row (USD→EUR 13/10), and the book option *Use Trading Accounts* = `t`, so cross-currency transactions carry four splits through `Trading:CURRENCY:EUR` / `USD`.

## Decision

- **Present balance** = sum of `quantity` of splits whose transaction `post_date` ≤ end of today. One grouped SQL aggregate per call (ADR-0003), rolled up in Java.
- **Sign:** reversed for INCOME, LIABILITY, CREDIT, EQUITY and PAYABLE, as desktop shows them (Salary 2 000.00 €, not −2 000.00). TRADING is not reversed, matching desktop's `gnc_configure_reverse_balance` (Trading:CURRENCY:EUR −25.00 €).
- **Hidden** accounts and their whole subtree are left out of display and pickers but **counted** in ancestor totals.
- **Mixed-currency rollup:** a child in another commodity is converted into the parent's commodity with the latest `prices` row (direct, or inverse when only that exists), by exact rational multiplication. As desktop does, each descendant's own balance is converted straight into the ancestor's commodity, then rounded to its fraction with banker's rounding (`GNC_HOW_RND_ROUND`). This display rounding is the one place amounts are rounded. With no price, the parent shows a "no price" marker instead of desktop's silent zero.
- The **Trading** tree is shown collapsed and never offered in pickers.
- **Editable** transactions are only those with exactly two splits, same commodity, no split reconciled (`reconcile_state = 'y'`), and no placeholder or hidden account. Everything else is read-only in the app; desktop handles it.

## Consequences

- Phone and desktop totals match, including hidden money.
- Cross-currency (four-split) and multi-split transactions are visible but not editable.
- Expected Present balances on 2026-11-04 for the sample (e.g. Cash 1 585.00 €, Misc 0.00 €) are pinned in tests.
