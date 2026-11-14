# Sankey reports: label options, fixed income and expense colours, one-day default period

Status: Accepted, 2026-11-14. Supersedes the **Colours** bullet of ADR-0011.

## Context

The Sankey reports of ADR-0011 always labelled every node with name and amount, gave every branch its own palette colour, and opened with the standard reports' default period (the accounting period). Using them on a real book showed three problems:

- Labels crowd a large diagram, and some readers want the shape only, or full account names.
- Colours said nothing: income and expenses could get any slot, so a reader could not tell the sides apart at a glance.
- GnuCash runs a report as soon as it is opened. The Cash Flow Sankey on a large book, with all asset accounts over a whole accounting period, kept GnuCash busy for so long that it looked hung.

## Decision

**Label options** (both reports, Display tab): *Show account names* (default on), *Show Full Account Names* (default off in Income & Expense, on in Cash Flow, as before; Cash Flow's option moves from General to Display so both reports have the same set), *Show balances* (default on).

- Hiding names hides the names of **accounts** only. The hub (*Revenue*/*Total*), *Net income*/*Net loss* and *Other* keep theirs (user decision): without them the shape cannot be read.
- Hiding balances hides the amount of every node, including the hub, the net result and the middle `+in / −out`.
- A node with neither name nor amount to show has no label and takes no label room.
- Hover texts always carry name and amount, whatever the options; the name there follows *Show Full Account Names*.
- `sankey-svg.scm` takes `""` for a node's name or amount and an optional sixth node field, the hover text.

**Colours.** Fixed meaning, then a rule for everything further out:

- Income & Expense: the first level on the income side (and whatever else feeds the hub from that side: refunds, trading gains) is green, the first level on the expense side red. Net income is green, a net loss red. The hub stays neutral grey.
- Cash Flow: sources (money in) green, targets (money out) red.
- Option *Sub-account colours* (default *Same as parent*):
  - *Same as parent*: deeper accounts take their first-level colour. In Cash Flow the selected accounts in the middle, having no parent, are grey, and each ribbon takes the colour of its outer end, green in, red out.
  - *Gray*: deeper accounts, middle accounts and Cash Flow ribbons are neutral grey.
  - *Distinct colours*: each second-level account (Income & Expense) or each selected account (Cash Flow), largest first, takes the next slot of the palette **without its green and red** (user decision), so nothing looks like income or expense. Deeper accounts inherit their second-level ancestor's slot, Cash Flow ribbons their selected account's. *Other* below the first level stays grey.
  - In Income & Expense both sides draw from **one** sequence, income first. When the six slots are used up the sequence **starts over** (user decision, after a real book turned half its expense categories grey). This departs from ADR-0011's "never cycled": grey is kept for *Other* and the hub, and accounts that share a colour are told apart by their labels and hover texts. Neighbours in the caller's order never share a slot; the crossing reduction can still move two of a kind next to each other.
- Green and red are the validated palette's own slots `#008300` and `#e34948`; `sankey-sub-palette` is the remaining six in their order.

**Default period: one day, today.** Both reports set the default of *Start Date* and *End Date* to `(relative . today)` with `GncOption-set-default-value`, the way GnuCash 5.16's `income-gst-statement.scm` changes its defaults. A new report therefore opens at once, nearly empty, and the reader picks the period. The account selection keeps its defaults (user decision).

## Consequences

- Opening either report is fast whatever the book's size. The reader must choose a period before the diagram shows anything useful.
- The slowness itself is not fixed: a long period on a large book still takes long. The layout's crossing search (`order-columns` in `sankey-svg.scm`) is the likely cost and is the place to look if that matters.
- A saved Cash Flow Sankey configuration that set *Show Full Account Names* on the General tab loses that setting.
- With the *Distinct colours* rule six colours go round instead of eight; from the seventh account on, colours repeat.
- All screenshot baselines changed colour; new baselines cover *Distinct colours* in both reports (one with nine second-level accounts, so the colours start over) and the Income & Expense Sankey without names and balances.
