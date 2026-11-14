# Sankey reports run inside GnuCash desktop, drawn as SVG by Scheme

Status: Accepted, 2026-11-13

## Context

GnuCash desktop 5.16 has no Sankey chart. Two are wanted: income and expenses with the numbers of the standard *Income Statement*, and cash flow with the rules of the standard *Cash Flow* report.

Facts that shaped the decision:

- The report viewer of the Windows build is WebKitGTK 2.4.11, an ES5-era engine. GnuCash ships Chart.js 2.9.4 and no Sankey plugin; today's Sankey libraries need a newer engine.
- A report may add a plain string to its document and it is written out unescaped, so inline SVG works.
- `gnucash-cli --report run --name <name>` renders a report to a file without a display. It cannot pass report options, but it loads `config-user.scm` and the saved report configurations.
- ADR-0007 pins behaviour to GnuCash 5.16. Ubuntu 26.10 packages exactly 5.16 (`1:5.16-2`, same build id as the Windows build); Fedora 43/44 are already on 5.17.

## Decision

**Where and how**

- **Desktop Scheme reports, not a web page.** Module `gnucash-reports` holds Guile Scheme reports that GnuCash loads from the user data folder. They sit in Reports > Income & Expense as two entries, *Income & Expense Sankey* and *Cash Flow Sankey*, next to the standard reports whose numbers they draw, with the same options.
- **SVG computed in Scheme, no JavaScript.** `sankey-svg.scm` (module `(fincore sankey svg)`, no GnuCash imports) lays out nodes and ribbons and returns one `<svg>`. It renders in the old WebKit, prints, and survives HTML export. Hover text is SVG `<title>`. Labels are painted twice (a white outline, then ink) because the old WebKit has no `paint-order`.
- **Files are loaded by path, not from a module tree.** `load.scm` loads `sankey-svg.scm` and then the reports from its own folder; loading the first file defines the module, which the reports then import by name. Nothing is added to Guile's load path. Proven on Guile 3.0 (Linux package) and Guile 2.2 (Windows build). Sources are ASCII only (`\u` escapes), so no reader has to guess an encoding.
- **Layout rules** (generic, in terms of columns and links, deterministic). Within a column nodes keep the caller's order unless another order lowers the crossings: two ribbons between the same two columns cross when their ends are in opposite order, weighted by the product of their amounts; the search moves single nodes and re-sorts the neighbouring columns by barycentre, and keeps only strict improvements, so a crossing-free order is never touched. A caller can pin nodes to the end of their column; the Cash Flow Sankey pins *Other*, which stays at the bottom even where another order would cross less (user decision of 2026-11-14: a reader looks for the remainder last). Nodes are then moved towards the nodes they are linked to (alternating sweeps, as d3-sankey does), solved per column as an ordered least-squares fit, so they never overlap and never leave the drawing. Every node or ribbon of a non-zero amount is at least 3 px thick; only the drawing is widened, labels, hover texts, totals and `data-*` keep the true amounts. Labels go where they touch no node, label or ribbon (beside, above or below a node); only when no such place exists do they settle where they touch fewest ribbons, and they are cut only when no place has room.
- **Colours** (superseded by [ADR-0012](0012-sankey-label-options-fixed-colours-one-day-default.md): income green, expenses red, a rule for sub-accounts)**.** Eight categorical slots in a fixed, colour-blind-validated order, never cycled: whoever needs a ninth gets neutral grey. Text is always ink, never a series colour; every node is labelled, and the totals are repeated as a table below the diagram.

**Numbers**

- **The standard reports define them.** The Income & Expense Sankey computes revenue, expenses, trading and net income with the same calls as `income-statement.scm`, including the closing-entries pattern. The Cash Flow Sankey follows `cash-flow-calc.scm` rule for rule in a local variant, with one exception below.
- **Trading accounts in the Cash Flow Sankey: the option means what it says** (user decision of 2026-11-14, replacing the first version, which copied the shipped behaviour). In 5.16's `cash-flow-calc.scm`, *Include Trading Accounts in report* works the wrong way round: trading splits are left out when it is on and counted when it is off, so with the default a currency purchase shows as money in from and out to `Trading.CURRENCY`. The user wants these flows hidden by default. Here off (the default) leaves trading splits out and on counts them. Consequence: the Sankey with the option off has the totals of the standard Cash Flow report with its option on, and the other way round; the tests compare both pairings.
- **Attribution rule (Cash Flow).** The shipped calculation does not keep which selected account a flow belongs to. Here a counter split is shared between the transaction's selected splits of the opposite sign in proportion to their values, or between all its selected splits when none has the opposite sign. Shares add up to the counter split, so Money In, Money Out and Difference are the standard report's.
- **Negative-account rule (Income & Expense).** A flow cannot be negative. An income or expense amount against its side's direction (an expense account with net refunds) is drawn on the opposite side, attached to the hub under the account's full name. The hub is then labelled *Total* instead of *Revenue*, because it is more than revenue. Net income is unchanged. The same holds for a net loss, which feeds the hub from the left.
- **Trading accounts**, when selected, are attached straight to the hub: the standard report adds their balance to net income as it stands, so a positive balance is drawn as money in and a negative one as money out.
- **Depth.** Accounts below the depth limit count into their ancestor at the limit (netted there). In the Cash Flow Sankey *Account Display Depth* groups the counter accounts the same way. This differs from the standard Cash Flow report, which lists every counter account whatever the depth; without grouping the diagram is unreadable on a real book.
- **Minimum share** (default 1 %): siblings below that share of the total are merged into one *Other* per parent; a single small sibling is left alone.
- **Cash Flow shape.** Every selected account is its own middle node; transfers between selected accounts stay hidden, as in the standard report, so a middle node's in and out need not match.

**Tests**

- **In a dockerized GnuCash 5.16.** Image `ghcr.io/imolcean/fincore-gnucash-cli:5.16-1`: Ubuntu 26.10 with the distribution's `gnucash=1:5.16-2`, `libdbd-pgsql`, Guile 3.0, DejaVu fonts, and Mozilla's Firefox ESR 153.7.0 tarball (checksum pinned; Ubuntu's own Firefox is a snap). Testcontainers starts it next to the `SampleBook` Postgres container and reaches that through the host, so `SampleBook` is unchanged.
- **The image name is the Maven property `gnucash.image`**, and its tag carries a Dockerfile revision after the GnuCash version. Baselines belong to one exact image, so a Dockerfile change takes a new tag instead of overwriting the old one.
- **Fixed dates through saved report configurations** in a test user data folder (`src/test/resources/gnucash-config`), run by name.
- **What is compared.** Totals against the standard reports run on the same book; flows against amounts worked out by hand; the SVG text exactly and a Firefox screenshot within a tolerance against committed baselines. Tolerance: a pixel differs when a colour channel is off by more than 48 of 255; at most 2 pixels in 1000 may differ. `-Dsankey.baselines.update=true` rewrites the baselines.
- **No floating point in the Java**, as in the other modules, and enforced by the same ArchUnit rules: amounts are `BigDecimal` or cents and the tolerance is counted in whole pixels. The layout in Scheme does use inexact numbers for pixel positions; amounts stay exact rationals until they are scaled to pixels.
- Richer cases (shared transactions, refunds, a loss, small accounts) are written into a test's own copy of the book. `sample-book.sql` is not changed.

**Build and release**

- **Maven, no scripts.** `./mvnw verify` runs everything; `package` builds `gnucash-reports-<version>.zip`; profile `install-desktop` copies `fincore-reports/` into `${gnucash.userdata}` (default `%APPDATA%/GnuCash`) in the `install` phase (`./mvnw -pl gnucash-reports -am install -Pinstall-desktop`; moved from `package` on 2026-11-14, since putting the build to use on this machine is what `install` is for).
- **One manual line.** Maven never edits `config-user.scm`. The user adds once: `(load ((@ (gnucash core-utils) gnc-build-userdata-path) "fincore-reports/load.scm"))`. The module prefix is needed because GnuCash evaluates `config-user.scm` where `gnc-build-userdata-path` is not bound.
- **Releases are tagged `gnucash-reports/v*`**, mirroring `gnucash-togo/v*`; the release workflow tests and attaches the zip.
- **CI.** `gnucash-reports-image.yml` publishes the test image. The jobs that run the tests pull that tag and, when it is not there, build it from the same Dockerfile, so neither workflow has to run before the other.

## Consequences

- Rendering in the Windows viewer (WebKit 2.4.11) is not covered by tests: the Linux package uses a current WebKit and the screenshots come from Firefox. That check stays manual.
- Because of the minimum thickness a small node or ribbon can look larger than its share; the amounts beside it are exact. Lowering crossings can move a node away from the caller's order, for example the largest selected account into the middle. *Other* in the Cash Flow Sankey is exempt and stays last, at the price of a heavier crossing in some books.
- Text cannot be measured in Scheme. Label room is estimated at 6.8 px per character; long names are cut with an ellipsis and stay whole in the hover text. In fonts much wider than DejaVu Sans or Segoe UI a label can run a few pixels into a neighbour.
- In a book with several currencies, each account is converted on its own, while the totals are converted as the standard report does (per commodity sum). Rounding can make the nodes differ from the totals by cents; the totals below the diagram are the standard report's.
- A change to `gnucash-reports/**` also triggers the gnucash-togo workflow, because that job is where the repository's tests run, and so rebuilds the togo image. Splitting the test job out is the fix if that becomes a nuisance.
- `gnucash-togo/Dockerfile` copies every module's pom for the reactor, so a new module means one more `COPY` line there.
- Ubuntu 26.10 leaves the main archive in July 2027. The published image keeps working; rebuilding it after that needs `old-releases.ubuntu.com` or a build of the 5.16 tag from source.
- A GnuCash upgrade means a new image tag, new baselines, and a re-read of `income-statement.scm` and `cash-flow-calc.scm` for changed rules.
- The image has no preferences backend, so GnuCash there separates account names with "." and formats amounts for the C locale. The desktop uses the user's separator and locale.
