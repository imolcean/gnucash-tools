# Exact rational amounts, no floating point, no rounding

Status: Accepted, 2026-11-04

## Context

Mobile GnuCash stored amounts 0.01 € off what was typed, most likely float rounding in its PHP backend. This is the bug the new app exists to fix.

GnuCash stores every amount as an integer rational: `splits.value_num/value_denom`, `splits.quantity_num/quantity_denom`, `prices.value_num/value_denom`. The denominator normally equals the commodity fraction (`commodities.fraction`, `accounts.commodity_scu`); in the sample book every split has denom 100.

## Decision

- Amounts are `GncNumeric(long num, long denom)`: sign on the numerator, `denom > 0`, arithmetic with `Math.*Exact` so overflow throws. Conversion to `BigDecimal` only when exact.
- User input is parsed to `BigDecimal` and converted with `of(BigDecimal, fraction)`. **No rounding**: input finer than the commodity fraction (e.g. `12.345` for EUR) is rejected with a validation error.
- Balance sums are computed in SQL grouped by denominator (`SUM(quantity_num) … GROUP BY account_guid, quantity_denom`), with no division in SQL. The groups are combined as rationals in Java.
- An ArchUnit rule in gnucash-book forbids `double` and `float` in fields, parameters and return types.

## Consequences

- What is typed is what is stored, or the input is refused; never silently changed.
- One exception, added 2026-11-10: an amount typed as a sum, as GnuCash Desktop's calculator allows (`AmountExpression`:
  `+ − × ÷`, a whole number after `×` or `÷`), is calculated exactly and rounded once, half-even, to the commodity
  fraction. Only a division can need it (`10.00 ÷ 3` → `3.33`). It is not silent: the result shows under the field while
  typing and replaces the sum on leaving it, before anything is saved.
- Tests cover the float traps (`0.1+0.2`, `19.99`, `4.35`, `1.005`) and a round-trip sweep over every cent value from 0.00 to 99 999.99.
- Price conversion (ADR-0008) multiplies rationals; denominators can grow, and overflow fails loudly rather than corrupting a total.
