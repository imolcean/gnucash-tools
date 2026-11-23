# Plan: pp, an adapter for the Portfolio Performance file

**Status (2026-11-13):** design agreed in the grilling session, nothing implemented. Waiting for the fixture file (see "Needed from you") and the go. Update this line when a milestone finishes. 2026-11-23: still not implemented; the plan moved to the gnucash-tools repository.

## Context

[Portfolio Performance](https://www.portfolio-performance.info/) (PP) tracks securities, GnuCash keeps the books. Later a sync will keep the two in step, with **GnuCash as the owner**: the PP file is regenerated from the GnuCash book. This plan builds the PP side only: a library module `pp` that reads a PP file, exports a fresh one, and shows the difference between two PP-shaped states.

Out of scope here, part of the later sync work:
- the mapping between a GnuCash book and the PP model;
- reading securities transactions in `gnucash-book` (today it reads them only as read-only register rows);
- pairing records by content for the first reconciliation of an existing book with an existing PP file;
- any UI beyond the diff report, and any `gnucash-togo` integration.

Work happens in **milestones** on branch `pp`. After each one: run `./mvnw verify`, show the result, and ask before committing.

### Facts the design rests on

From the PP source at master `da2c059` (2026-11-10, release 0.90.0), repository <https://github.com/portfolio-performance/portfolio>, core bundle `name.abuchen.portfolio`:

- **Formats** (`model/ClientFileType.java`, `model/ClientFactory.java`): plain XML, XML with id attributes, zipped XML, AES-encrypted, and binary. The unprotected binary `.portfolio` file is a zip whose entry `data.portfolio` holds the 6-byte signature `PPPBV1` followed by a protobuf `PClient` message (`model/ProtobufWriter.java`). An encrypted file starts with the bytes `PORTFOLIO`; an XML file starts with `<`.
- **Schema:** `model/client.proto` (proto3). References are UUID strings, transactions are one flat list with 15 types, and a purchase is one record naming both accounts.
- **Scaling** (`money/Values.java`): money ×100 for every currency, shares and quotes ×10^8.
- **File version:** 70 since PP 0.86.0; the user runs PP 0.90. PP upgrades older files only in memory on load (`ClientFactory.upgradeModel`).
- **No lock, no change detection:** PP holds the file in memory, locks only while writing, and overwrites on save. Inferred from the absence of such code in the UI sources, not from a documented statement.
- **Reuse:** the PP core is an Eclipse/OSGi bundle, not on Maven Central, licensed EPL-1.0. We reuse only `client.proto`.

To confirm at M1 against the 0.90.0 tag and the fixture, because they were read from the research report and not yet from the files themselves: the exact field names and types in `client.proto`, and that the protobuf amounts use the same scaling as `Values.java`.

---

## Decisions

| # | Decision |
|---|---|
| D1 | **Direction:** GnuCash owns the data; the PP file is regenerated from it. The adapter reads first and exports second. |
| D2 | **Module:** `pp`, package `org.duckdns.gory26.pp`, facade `PortfolioFile`. A plain library with no dependency on `fincore-core` or `gnucash-book`, like `gnucash-book` (ADR-0001, ADR-0002). |
| D3 | **Format:** unprotected binary `.portfolio` only. XML and password-protected files are refused with a message saying how to re-save in PP. |
| D4 | **Schema reuse:** `client.proto` from the PP 0.90.0 tag is copied into the repo (EPL-1.0, with a licence note beside it) and Java classes are generated at build time. The generated classes stay internal. |
| D5 | **Model:** our own records in the protobuf shape: one flat transaction list with PP's 15 types; a purchase is one record naming the securities account and the cash account. |
| D6 | **Modelled scope:** securities (UUID, name, ISIN, WKN, ticker, currency, retired), cash accounts, securities accounts with their reference account, transactions with fee, tax and gross-value units including foreign amount and exchange rate, notes and source. Not modelled: historical quotes, feed settings, taxonomies, watchlists, savings plans, dashboards, settings. |
| D7 | **Amounts:** `BigDecimal` with fixed scale, 2 for money and 8 for shares and quotes. An ArchUnit rule forbids `float` and `double`. An amount finer than the scale is rejected, never rounded (ADR-0003). |
| D8 | **File versions:** only tested versions are accepted, starting with 70 alone. Older and newer files are refused. |
| D9 | **Strict reading:** anything not understood (unknown transaction type, a reference to a UUID missing from the file) fails the whole read and names the record. |
| D10 | **Export base:** an export starts from an existing PP file and replaces the cash accounts, securities accounts and transactions. Everything not modelled is carried over unchanged. |
| D11 | **Export identity:** the caller passes the complete wanted set, each record with its UUID. A UUID found in the base keeps its unmodelled fields; an unknown UUID is created; a base record absent from the wanted set is removed. |
| D12 | **Securities on export:** only added, never removed or edited. |
| D13 | **Dangling references:** if a carried-over part points at something the export removed, the export refuses and lists every such reference. |
| D14 | **`updatedAt`:** kept from the base when a record with the same UUID is unchanged in every modelled field; otherwise the current time from an injected `Clock`. |
| D15 | **Write safety:** the source file is never changed. A write goes to a path the caller names, refuses to replace an existing file without an explicit overwrite flag, and is written to a temporary file and then moved into place. No backup copies. |
| D16 | **Diff:** compares two PP-shaped states, paired by UUID, reporting added, removed and changed records with the changed fields. It covers cash accounts, securities accounts, transactions and the identity fields of securities; it ignores `updatedAt` and everything unmodelled. |
| D17 | **Report:** one self-contained HTML file from a command-line entry point taking two file paths and an output path: counts per account, a transaction table grouped by account with old and new values, and share holdings per security and securities account as a plain sum of shares, with no valuation. |

---

## M1: decision records, module skeleton, reading

**ADRs** (`docs/adr/`, short Nygard format, written with the `domain-modeling` skill). Numbers are the next free ones when M1 starts; 0010 is the last one used today, and the Sankey reports work may take 0011.
- pp reads and writes the unprotected binary PP file through classes generated from PP's `client.proto`; GnuCash owns the data (D1–D4).
- The pp model follows the protobuf shape, with exact fixed-scale amounts (D5–D7).
- Only tested file versions are accepted, and reading is strict (D8, D9).

**Glossary:** `pp/GLOSSARY.md` covering client, security, cash account, securities account, reference account, transaction and its 15 types, unit (fee, tax, gross value), delivery vs. purchase, shares, quote, file version, and how each term relates to the GnuCash and fincore-core terms.

**Module:**
- Root `pom.xml`: add `<module>pp</module>`.
- `pp/pom.xml`: `protobuf-java` plus a protobuf Maven plugin that generates from `pp/src/main/proto/client.proto`. Coordinates and versions are checked on Maven Central at M1; the Boot BOM is used where it manages them. Test scope: JUnit Jupiter, AssertJ, ArchUnit.
- `pp/src/main/proto/client.proto` copied unchanged from the PP 0.90.0 tag, with `pp/src/main/proto/NOTICE.md` naming the source, tag and EPL-1.0.
- `.github/workflows/gnucash-togo-image.yml` builds only on gnucash paths today; add `pp/**` to its `paths:` filter so `./mvnw -B verify` runs for pp changes. Rename nothing.

**Code** (TDD with the `tdd` skill, failing tests first):
- Value records in `org.duckdns.gory26.pp`: `Security`, `CashAccount`, `SecuritiesAccount`, `Transaction`, `TransactionType` (15 values), `Unit`, `UnitType`, `Money`, and `Portfolio` as the read result holding the four lists, the base currency and the file version. Final names are settled against the glossary.
- `PortfolioFile.read(Path)`:
  1. sniff the container: `PORTFOLIO` → refuse as password-protected; `<` → refuse as XML; zip without `data.portfolio` → refuse; missing `PPPBV1` → refuse;
  2. parse `PClient`; refuse a version other than 70;
  3. map to the records, resolving every UUID reference and failing on any that is missing or on an unknown type.
- Internal scaling helpers between the stored integers and `BigDecimal`.
- ArchUnit: no `float` or `double`; generated protobuf types do not appear in public signatures.

**Tests:**
- Fixture `pp/src/test/resources/fixtures/sample.portfolio` (from the user) read end to end: every account, security and transaction asserted against values the user confirms from PP's own screens.
- Refusals: XML file, encrypted header, wrong version, missing signature, dangling UUID, unknown transaction type. Small broken inputs are built in the tests from the generated classes.
- Scaling: 70 shares ↔ `7000000000`, 10.07 ↔ `1007`, and the edges of both scales.

**Done when:** `./mvnw verify` is green and the fixture's contents match what PP 0.90 shows.

---

## M2: export

**ADR:** export rules (D10–D15) in one record.

**Code:**
- `PortfolioFile.export(Path base, Portfolio wanted, Path target, overwrite flag)`, with a `Clock` given to the facade.
  1. read and validate the base as in M1, keeping the decoded `PClient`;
  2. validate `wanted`: unique UUIDs, every reference resolvable within `wanted` plus the base's securities, every amount within scale, each transaction's currency equal to its cash account's currency;
  3. build the new message: carry over everything unmodelled; replace accounts and transactions by the D11 rules; append new securities (D12); set `updatedAt` by D14;
  4. check the carried-over parts for references to removed records and refuse with the full list (D13);
  5. write zip + signature + message to a temporary file beside the target, then move it into place; refuse an existing target without the overwrite flag (D15).
- The source file is opened read-only and never written.

**Tests:**
- Round trip: read the fixture, export it unchanged, and require the decoded message to equal the original, field for field. This also proves the carry-over of quotes, taxonomy, watchlist and savings plan.
- Add, change and remove for each record kind, with `updatedAt` kept or stamped as D14 says under a fixed clock.
- A second export of unchanged data equals the first.
- Refusals: existing target without the flag, dangling reference into a removed account or transaction, amount finer than the scale, currency mismatch, duplicate UUID.
- The base file's bytes are unchanged after every export.

**Done when:** `./mvnw verify` is green and the manual check below passes.

---

## M3: diff and report

**ADR:** the diff works on PP-shaped states paired by UUID, and the sync reuses it (D16, D17).

**Code:**
- `PortfolioDiff.between(Portfolio left, Portfolio right)` → a plain data structure: per record kind, the added, removed and changed records, each change listing field, left value and right value. Holdings per security and securities account on both sides: purchases, inbound deliveries and transfers in, minus sales, outbound deliveries and transfers out.
- `DiffReport.render(PortfolioDiff)` → one HTML string with inline CSS and no scripts or external resources: summary counts per account, the transaction table grouped by account, the holdings comparison. Light and dark colours follow the browser setting.
- `DiffReportCli.main(left, right, out)`: reads two PP files, writes the report, and follows the D15 overwrite rule for the output. Run with `./mvnw -q -pl pp exec:java -Dexec.args="left.portfolio right.portfolio report.html"`.

**Tests:**
- Diff of the fixture with itself is empty.
- One test per kind of difference, asserting on the data structure, including a change in a unit and in an exchange rate.
- A base and its M2 export with known edits: the diff lists exactly those edits.
- Report: contains each expected row, escapes HTML in names and notes, and references nothing outside the file.

**Done when:** `./mvnw verify` is green and the report for "fixture against edited export" is opened in Firefox and reads correctly.

---

## Needed from you

- **Before M1:** an invented PP 0.90 file saved as unprotected binary, to be committed as `pp/src/test/resources/fixtures/sample.portfolio`, with:
  - two cash accounts, one in a foreign currency, and one securities account;
  - purchase, sale, dividend, deposit, removal, interest, fee, tax, a cash transfer, an inbound delivery, and one foreign-currency purchase with an exchange rate;
  - one taxonomy assignment, one watchlist, one savings plan and a few historical quotes.

  Leave out whatever is tedious; the gap is then noted in the tests.
- **At M1:** confirm the values the tests assert against PP's screens.
- **At M2:** open one exported file in PP 0.90 and confirm it loads and shows the expected holdings.

---

## Verification

- **Every milestone:** `./mvnw verify` from the root, all modules, including the ArchUnit rules.
- **M1:** fixture contents equal PP's own display; every refusal has a test.
- **M2:** unchanged round trip equals the original message; the base file is byte-identical afterwards; PP 0.90 opens an exported file (manual).
- **M3:** the diff of a known edit set lists exactly those edits; the HTML report is checked in Firefox.
