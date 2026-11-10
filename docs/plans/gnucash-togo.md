# Plan: gnucash-togo + multi-module fincore repo

**Status (2026-11-04):** M1 done (modules restructured, ADRs 0001–0009, glossary; `mvnw verify` green, 107 tests). M2 done (gnucash-book: GncNumeric, AmountParser + InvalidAmountException, TaggedDescription, Transaction/Split, TransferDraft, ArchUnit no-float rule; 65 tests, `mvnw clean verify` green, 172 tests). M3 done (`GnuCashBook` SQL adapter, Testcontainers `postgres:17.11` from the dump with one template copy per test; golden, balance, register, suggestion, guard and least-privilege tests; gnucash-book 124 tests, `mvnw verify` green, 231 tests; see "M3 findings"). Balance table confirmed against desktop (Trading −25.00 €). M4 done (login with per-session pools, entry form, account tree, register variant B, edit/delete, banners, PWA manifest; gnucash-togo 90 tests, gnucash-book 166, `mvnw verify` green, 362 tests; end-to-end run in Firefox at 390×844 against `postgres:17.11` with the app role; see "M4 findings"). M5 done except the desktop round trip (Dockerfile, CI workflow, compose with inline `db-init`, README; `mvnw verify` green, 313 tests; image run end to end through compose against `postgres:17.11`; see "M5 findings"). Open: first push/tag and the GnuCash desktop round trip (Verification step 3). Update this line when a milestone finishes.

## Context

Mobile GnuCash stored amounts 0.01 € off because of float arithmetic, and it summed account balances wrongly. gnucash-web gets amounts right but is too slow: it loads every split of an account through piecash. We're building **gnucash-togo**, a phone-first Spring Boot + HTMX app that reads and writes the GnuCash **Postgres 17** book directly. It's a bridge until fincore replaces GnuCash.

The design was settled in the grilling session (memory: `gnucash-togo-plan`; handoff: `C:\Users\igorm\AppData\Local\Temp\gnucash-replacement-handoff.md`). The repo becomes multi-module Maven: today's domain code goes to `fincore-core`, plus a GnuCash adapter library and the app.

Work happens in **milestones** on branch `gnucash-togo`. After each one: run `./mvnw verify`, show the result, and ask before committing.

---

## M1: decision records + module restructure (no behaviour change)

**ADRs** (`docs/adr/`, short Nygard format, written with the `domain-modeling` skill):
- 0001 gnucash-togo is a bridge app writing GnuCash SQL directly, with no dependency on fincore-core.
- 0002 Multi-module repo: fincore-core / gnucash-book / gnucash-togo / (later) gnucash-import.
- 0003 Exact amounts: integer rationals, no float or double, and **no rounding**: input more precise than the commodity fraction is rejected.
- 0004 Desktop lock: refuse writes while `gnclock` has rows, re-check inside the same DB transaction, and a Clear-lock button behind a confirmation naming the host and PID.
- 0005 Schema `versions` check: an unknown set puts the app in read-only mode with a banner.
- 0006 Auth: passthrough login to a dedicated least-privilege PG role; sessions in server memory, a 365-day cookie, ending after 90 days unused (changed 2026-11-06).
- 0007 Write fidelity: rows must match what GnuCash desktop 5.16 writes, enforced by golden tests against the sample book.
- 0008 Balances: Present = `post_date` ≤ today. Hidden subtrees are left out of display but counted in totals. Mixed currencies are converted with the latest price, as desktop does. The Trading tree is shown collapsed.
- 0009 Tags: descriptions carry a `[A] [B] text` prefix. The UI edits tags as chips and stores them in the description text.

**Glossary:** `gnucash-book/GLOSSARY.md` covering book, account, commodity/SCU, transaction, split, value vs. quantity, reconcile state, slot, lock, Present balance, trading account, and tag.

**Restructure:**
- Root `pom.xml` becomes the aggregator, with `packaging=pom` and parent `spring-boot-starter-parent` **4.1.2**. It contains `<modules>` for fincore-core, gnucash-book and gnucash-togo, and manages the jMolecules BOM.
- `fincore-core/`:
  - Move `src/` there with `git mv`, so history is kept.
  - Delete `FincoreApplication.java` and `FincoreApplicationTests.java`.
  - Dependencies: `jmolecules-hexagonal-architecture` and `jmolecules-starter-ddd`. Test scope: `jmolecules-starter-test`, JUnit Jupiter, AssertJ, ArchUnit (all versions from the Boot BOM).
  - **Drop** Batch, Data REST, Flyway, H2, SQLite, Thymeleaf and devtools (user approved), along with the configuration-processor setup and `application.properties`.
  - `ArchitectureTests`: change `@AnalyzeClasses(packagesOf = FincoreApplication.class)` to `packages = "org.duckdns.gory26.fincore.core"`.
- Skeletons for `gnucash-book/` (package `org.duckdns.gory26.gnucash.book`, depending on `spring-jdbc` only) and `gnucash-togo/` (package `org.duckdns.gory26.gnucash.togo`).
- Delete the stray `HELP.md` and `bash.exe.stackdump`; check first whether they are tracked.

**Done when:** `./mvnw verify` runs all existing fincore tests green in `fincore-core`, with the same count as before.

---

## M2: gnucash-book, amounts and descriptions first (TDD, no DB)

This uses the `tdd` skill. The failing tests are written before the code.

- `GncNumeric(long num, long denom)` value object:
  - sign on the numerator, denominator > 0;
  - exact `add`, `negate`, `multiply` and `compareTo` with `Math.*Exact`, so overflow throws;
  - `toBigDecimal()` only when exact;
  - `of(BigDecimal, long fraction)` rejects anything that doesn't fit the fraction.
- `AmountParser`: parses phone input `12,34`, `12.34`, `1 234,5` and `1.234,50` into a `BigDecimal`. It rejects ambiguous input, signs and more than *fraction* decimals. Tests include the float traps (`0.1+0.2`, `19.99`, `4.35`, `1.005`) and a sweep over all cent values from 0.00 to 99 999.99 that round-trips through num/denom.
- `TaggedDescription.parse/compose`: `[Tag 1] [Tag 2]  Rewe` ⇄ (tags `[Tag 1, Tag 2]`, text `Rewe`). Parsing tolerates any spacing (both forms occur in the sample); compose writes `[A] [B] text`.
- `Transaction`/`Split` read model, shaped like GnuCash: N splits with signed `value` and `quantity`; the sum of `value` is zero.
- `TransferDraft(date, tags, text, amount, from, to, notes)` maps to exactly two splits: `to` gets +amount and `from` gets −amount, with `value = quantity` because v1 is same-currency only.
- An ArchUnit rule in `gnucash-book` forbids `double` and `float` in fields, parameters and return types.

---

## M3: gnucash-book, the SQL adapter (Testcontainers `postgres:17.11`)

**Fixture (provided):** `sample-book.sql`, a pg_dump of a book written by GnuCash 5.16 on PG 17.11. It moves from the repo root to `gnucash-book/src/test/resources/fixtures/sample-book.sql`. It is loaded through the container's `/docker-entrypoint-initdb.d/`, because psql handles `COPY … FROM stdin` and `\restrict`, which JDBC can't. The dump still holds a `gnclock` row (Leopard/8300): the lock tests keep it, and the write tests delete it first.

**Facts the dump confirms**, which the writes must reproduce:
- `post_date` = date at `10:59:00`; `enter_date` = now in UTC; `num`, `memo` and `action` = `''`;
- unreconciled split: `reconcile_state='n'` and `reconcile_date='1970-01-01 00:00:00'` (not NULL);
- slot `date-posted` (type 10, `gdate_val`) on every transaction; slot `notes` (type 4, `string_val`) when notes are set. In both, the unused columns follow the dump: `int64_val=0`, `timespec_val='1970-01-01 00:00:00'`, `numeric_val=0/1`;
- `value_denom` and `quantity_denom` = 100 (the commodity fraction / account SCU);
- known `versions` set = the 22 table rows in the dump (`Gnucash` 5000016 and `Gnucash-Resave` ignored);
- the book uses Trading Accounts. Cross-currency transactions have four splits, so they are read-only in the app.

**`GnuCashBook` facade** over a `DataSource` (`JdbcClient`, no JPA):
- `schemaStatus()` → OK or UNKNOWN(diff).
- `lock()` → `Optional<LockHolder(hostname, pid)>`; `clearLock()` deletes the rows.
- `accountTree(asOf)`: one aggregate query, `SELECT account_guid, quantity_denom, SUM(quantity_num) … WHERE post_date < :endOfDay GROUP BY 1,2`, which is exact with no SQL division. The parts are combined as rationals and rolled up in Java.
  - Hidden accounts and their whole subtree are left out of display and pickers but counted in ancestor totals. In the sample, *Visible nested* is hidden through its parent *Hidden*.
  - The sign is reversed for INCOME, LIABILITY, CREDIT, EQUITY and PAYABLE. The Trading sign follows desktop and is confirmed at M3.
  - **Mixed-currency rollup**: a child in another commodity is converted into the parent's commodity with the latest `prices` row (direct, or inverse when only that exists), using exact rational multiplication. If no price exists, the parent shows a "no price" marker. The rounding rule is checked against desktop.
  - The Trading tree is shown collapsed and never offered in pickers.
- `register(account, cursor, 50)`: keyset pagination on `(post_date, enter_date, guid) DESC`. The running balance is the grouped SUM up to the newest row on the page, walked down in Java.
- `suggestDescriptions(text)` matches the text after the tags (`ILIKE`, distinct, most recent first, limit 10). `quickFill(description)` returns the latest two-split match: tags, accounts and amount. `suggestTags(prefix)` returns distinct tags. `suggestAccounts(query)` fuzzy-matches the full path, with accounts used in the last 90 days ranked by recency and frequency.
- `insert(TransferDraft)`, `update(guid, draft)` and `delete(guid)`, each in **one DB transaction**. The order is:
  1. `SELECT … FROM gnclock FOR SHARE` → abort if rows exist;
  2. guard: same commodity, not placeholder or hidden, exactly two splits, none reconciled;
  3. write `transactions`, `splits` and `slots` as above. GUIDs are `UUID` without dashes;
  4. delete also removes the slots of the transaction and of its splits.

**Golden cases**, taken from the dump. The app writes the same entry into a fresh DB, and the test compares rows (ignoring GUIDs, `enter_date` and slot ids):
- plain two-split: *Storage room*, Rent 20.00 ← Cash, 2026-11-03;
- with notes: *[Tag 1] Rewe again*, Groceries 7.66 ← Cash, note "Forgot something". The reconcile fields of the Cash split are ignored, because desktop reconciled it afterwards;
- future-dated: *Concert tickets (maybe?)*, Misc 200.00 ← Cash, 2026-11-10.

**Expected Present balances on 2026-11-04**, worked out from the dump. You confirm them against desktop at M3:

| Account | Expected |
|---|---|
| Cash | 1 585.00 € |
| Cash USD | 0.00 $ |
| Groceries | 20.00 € |
| Rent | 520.00 € |
| Misc | 0.00 € (the 200.00 is future-dated) |
| Salary | 2 000.00 € (sign reversed) |
| Opening Balances | 100.00 € |
| Trading:CURRENCY:EUR | −25.00 € (desktop does not reverse TRADING, see below) |
| Trading:CURRENCY:USD | 0 $ |

**M3 findings** (from the GnuCash `stable` branch source, pinned in tests):
- *Trading sign:* "Reverse balanced accounts: Credit accounts" flips only LIABILITY, PAYABLE, EQUITY, INCOME and CREDIT (`libgnucash/app-utils/gnc-ui-util.cpp`, `gnc_configure_reverse_balance`). Trading is shown raw, so −25.00 €.
- *Rollup rounding:* each descendant's own balance is converted straight into the ancestor's commodity (not through the intermediate parent) at the latest price. It is then rounded to that commodity's fraction with banker's rounding (`Account.cpp` `xaccAccountBalanceAsOfDateHelper`; `gnc-pricedb.cpp` `convert_amount_at_date`, `GNC_HOW_RND_ROUND`). 10.05 $ × 1.3 = 13.065 → 13.06 €. Desktop treats a missing price as 0; the app shows "no price" instead (ADR-0008).
- *Lock check:* `LOCK TABLE gnclock IN SHARE MODE`, then `SELECT`, replacing `SELECT … FOR SHARE`. A row lock cannot stop a new lock row being inserted after the check. The table lock makes a desktop that is opening the book wait for the commit. It needs only the planned `DELETE` grant (proved by the least-privilege test).
- *Transaction currency:* `currency_guid` = the accounts' commodity (always a currency). In this book that is the book currency.
- *Editable* also excludes frozen (`f`) splits and zero amounts. Cleared (`c`) stays editable, and update keeps reconcile states, GUIDs and `enter_date`.
- Added `GnuCashBook.draft(guid)` (edit-form starting point) and `GncNumeric.toFraction` / `roundToFraction` / `invert`.

---

## M4: gnucash-togo app

Decisions from the M4 grilling (2026-11-04) are folded in below.

- **Stack:** Spring Boot 4.1.2 web, Thymeleaf, Spring Security and Actuator. WebJars `org.webjars.npm:htmx.org:2.0.11` and `org.webjars.npm:picocss__pico:2.1.1`, with `org.webjars:webjars-locator-lite` (version from Boot), so paths carry no versions. These coordinates were checked on Maven Central on 2026-11-04.
  - **htmx 2, not 4.** 4.0.x is GA but changes inheritance, error swapping and events. Use plain `hx-headers` on `<body>`.
- **DB settings:** `GNUCASH_DB_HOST`, `GNUCASH_DB_PORT` (default 5432) and `GNUCASH_DB_NAME`, bound to `togo.db.*`. The login supplies the user and password. A `Clock` bean in the system zone (`TZ`) drives "today".
- **Auth:** a custom `AuthenticationProvider` opens a test connection with the login's username and password. A per-session Hikari pool (max 2) sits in a server-side registry with one `GnuCashBook` per pool, and is closed on logout or session destroy. The session cookie lasts 365 days and a session unused for 90 days ends (was `timeout=-1`, changed 2026-11-06, see ADR-0006). CSRF stays on, and htmx sends the token through `hx-headers` on `<body>`.
- **Formats:**
  - amounts `1 585.00 €`: narrow no-break space (U+202F) for thousands, a dot for decimals, a no-break space, then the symbol (`€`, `$`, falling back to the mnemonic);
  - negatives use U+2212 `−`;
  - dates are shown as `31.05.2026`; the date input is the native `<input type=date>`.
- **Screens** (strings in `messages.properties`, English):
  1. `/`, the entry form:
     - fields in the order date, from, to, amount, description, tags, notes; tags and notes are folded away in `<details>`; each label has a Lucide icon. Enter on date, from, to or amount moves to the next field (`enterkeyhint="next"` plus `entry-form.js`, since phones have no Tab key); Enter on the description saves (changed 2026-11-10);
     - the amount is typed as on a card terminal: digits only, the decimal point fixed at the from account's decimals, so 1, 2, 3 gives 1.23 (changed 2026-11-10);
     - the amount takes GnuCash Desktop's calculator: `12.30 + 4.50 × 2`. Phone keyboards have no operators, so a `+ − × ÷` strip shows under the field while it has focus; its buttons cancel `pointerdown`, so the keyboard stays up (tested on Android). A number after `×` or `÷` is a whole number. The server calculates (`AmountExpression`, half-even rounding of a division, ADR-0003): `/entry/amount` shows `= 16.80 €` while typing and puts the result in the field on leaving it (added 2026-11-10);
     - picking a description quick-fills only the tags, accounts and amount still empty (changed 2026-11-10);
     - date, with a "Today" button; the last-used date is kept after a save;
     - **tag chips**: an input with htmx suggestions from `suggestTags`. Enter, comma or picking a suggestion adds a chip; × removes it; Backspace in an empty input removes the last one. Brackets are refused. Each chip is a hidden `tags` input;
     - description text with htmx autocomplete (`suggestDescriptions`). Picking one quick-fills tags, accounts and amount (`quickFill`);
     - amount (`inputmode=decimal`, parsed by `AmountParser`);
     - from and to pickers: a search box with htmx suggestions from `suggestAccounts`, matching words anywhere in the full path. Focusing the empty box lists the top 10 recent accounts. Each suggestion shows the full path and currency, and a hidden field carries the GUID;
     - notes behind a `<details>` disclosure.

     After a save, everything except the date clears, and a fading line appears: "Saved: [Tag 1] Rewe · 12.34 € · Cash → Groceries". There is no undo.

     Errors:
     - field errors (bad amount, missing account, same from/to) show under the field with `aria-invalid` and keep what was typed;
     - `WriteRefusedException` reasons show as an alert above the form, also keeping the input.
  2. `/accounts`: the tree, built from `<details>`:
     - each row shows `displayTotal`, or "no price" when it's empty, plus the account's own balance only when it is not zero and differs;
     - hide-zero is on by default, applied **on the server** (`/accounts?hideZero=…`) and remembered in the session, so the tag-chip script stays the only hand-written JS (changed at implementation, 2026-11-04);
     - every account starts collapsed, Trading included (changed 2026-11-05);
     - tapping a **placeholder's** name expands its children; tapping any other account's name opens its register; the ▸ expands any account with children.
  3. `/accounts/{guid}`: the register, built as **variant B** of `../../gnucash-togo/prototypes` (the visual spec):
     - rows are grouped by day under sticky headers showing the weekday, `dd.MM.yyyy` and the end-of-day balance (the balance after that day's newest row); future days are dimmed and marked "future";
     - each row has two lines: tag chips and description, then the other account's last name as in the prototype, full path in the tooltip (or `— split (n) —`), and the notes;
     - on the right: the signed amount (`+` / `−`, desktop sign via `AccountType.display`), and under it the boxed reconcile marker (`n`/`c`/`y`/`f`) and a 🔒 for rows that can't be edited. The lock's space is kept blank on editable rows so the markers line up;
     - there are no letter badges;
     - "Load more" uses `hx-get` with `hx-swap=beforeend` and carries the last date shown, so a day that continues from the previous page gets no second header;
     - tapping an editable row opens `/transactions/{guid}/edit`, a full page with the same form fragment. Save or cancel returns to the register it came from. Delete sits on the edit page behind `hx-confirm`.
- **Banners on every page:** "Locked by HOST (PID n)" with a Clear-lock button behind `hx-confirm`: "Clear the lock held by HOST (PID n)? Desktop will not see the phone's writes." The second banner is "Unknown schema → read-only". Both disable the write buttons, and the server re-checks regardless. The only hand-written JS is the tag-chip script.
- **PWA:** `manifest.webmanifest` with no service worker. The icons are in `src/main/resources/static/icons/`:
  - `icon.svg`, the GnuCash icon with a palm tree;
  - `icon-192.png` and `icon-512.png` (purpose `any`, not maskable);
  - `apple-touch-icon.png` (180 px) and `favicon-32.png`.

  The icon is derived from GnuCash's GPL Tango-style art (`data/pixmaps/hicolor/scalable/apps/gnucash-icon.svg`); the generator is `../../gnucash-togo/prototypes`. The README credits it (M5).
- **Tests:**
  - `gnucash-book` publishes a test-jar so the app reuses `SampleBook`;
  - app tests log in as a role with the ADR-0006 grants;
  - MockMvc and Spring Security integration tests per screen and fragment.
- **End-to-end at the end of M4:**
  1. Run the app locally against `docker run postgres:17.11` loaded from the dump, with the app role.
  2. Use Firefox MCP at 390×844: log in, enter 12.34 with two tags, check the register and balances, edit, delete.
  3. Add a `gnclock` row, confirm writes are refused, then clear the lock.

  Compose and the desktop round trip are in M5.
- **Clean-up:** once variant B is built, `../../gnucash-togo/prototypes` goes to a throwaway branch (ask the user), not `master`.

**M4 findings:**
- *Connection leak (fixed in gnucash-book):* `JdbcClient…query(…).stream()` holds its connection until the stream is closed. `lock()` and `register()` leaked one connection per call, which the M3 tests (unpooled connections) never noticed; with the two-connection session pool the third page hung. They now use `.list()`, and `ConnectionLeakTest` counts unclosed connections across every facade call.
- *Facade addition:* `GnuCashBook.account(guid)` → `AccountSummary` (path, type, commodity, placeholder, hidden, Present balance). The register header uses it, and the entry form uses it for the from-account's fraction (`AmountParser`) and to label prefilled pickers.
- *htmx inheritance:* the entry form swaps itself with `hx-swap="outerHTML"`, which its children inherit. Every hx element inside the form sets its own `hx-swap`, pinned by a test (a suggestion list once removed itself on focusout).
- *Suggestion lists* close on focusout through a wrapper `hx-get=/suggest/none` (no JS). Typing in a picker after a pick clears the hidden GUID out of band, so a stale account can't be saved.
- *Test harness:* `gnucash-book` publishes a test-jar. `SampleBook.recreate(name)` and `grantAppRole(…)` give each app test a fresh `app_book` with the ADR-0006 grants under one cached Spring context.

---

## M5: delivery

- `gnucash-togo/Dockerfile`, copied from `J:\D\Workspaces\paperless-agents\Dockerfile`:
  - Temurin 25 Alpine, layered jar, non-root user, actuator healthcheck;
  - built from the repo root with `./mvnw -B -pl gnucash-togo -am package -DskipTests`.
- `.github/workflows/gnucash-togo-image.yml`, copied from paperless-agents `publish-image.yml`:
  - `paths:` filter on `gnucash-book/**`, `gnucash-togo/**`, `pom.xml` and `.mvn/**`;
  - the tests run `./mvnw -B verify`, including Testcontainers;
  - pushes `ghcr.io/imolcean/fincore-gnucash-togo` with the tags `latest`, `sha-*` and semver from git tags `gnucash-togo/vX.Y.Z` (metadata-action `type=match`);
  - amd64 only.
- `gnucash-togo/compose.yaml` (documentation, pasted into OMV):
  - `db-init` (`postgres:17.11`, one-shot) runs an **inline** idempotent `psql` script. It:
    - creates the app role if missing;
    - grants CONNECT, USAGE and SELECT on all tables;
    - grants INSERT, UPDATE and DELETE on `transactions`, `splits` and `slots`;
    - grants USAGE on `slots_id_seq` and DELETE on `gnclock`.
  - Its superuser credentials come from env.
  - `app` uses `:latest` with `pull_policy: always` (changed 2026-11-05: master head deploys on restart; semver tags stay as optional rollback points), port `8302:8080` and `TZ`, with DB host, port and name from env. It `depends_on` db-init with `service_completed_successfully`.
  - Both services join the existing DB stack's network as `external: true`.
- `README.md`: deploy steps and the release-tag convention.

**M5 findings:**
- *`mvnw` was not executable in git* (mode 100644), so `./mvnw` would fail on the runner. Fixed with `git update-index --chmod=+x`.
- *Dependency layer:* `dependency:go-offline -pl gnucash-togo -am` fails on our own snapshot modules, so it runs with `-DexcludeGroupIds=org.duckdns.gory26`. Every module's pom is copied (the reactor lists fincore-core), but only gnucash-book and gnucash-togo sources. `gnucash-togo/Dockerfile.dockerignore` keeps `target/`, `.git` and docs out of the root context.
- *db-init:* psql variables (`:'role'`, `:"role"`) do the quoting, so passwords with quotes work; `CREATE ROLE` goes through `\gexec` only when missing, and the password is reset on every run. Checked by running `up` twice with the password `it's "secret"`.
- *Compose:* the external network's name comes from `GNUCASH_DB_NETWORK`; the app runs `:latest` (the newest green master build), pulled on every start.
- *End to end through compose:* login, lock banner with Save disabled and nothing written, Clear lock, save 12.34 with two tags (rows as desktop writes them, `date-posted` slot), balances, edit, delete (slots removed). The container runs as `app` (non-root) and honours `TZ`.

---

## Needed from you

- Docker Desktop running before the M3 tests.
- At M3: compare the expected-balance table with desktop's Present column for the sample book, especially the Trading sign.

---

## Verification

- **Every milestone:** `./mvnw verify` from the root runs all modules, the ArchUnit rules, and (from M3) Testcontainers.
- **M2:** amount and tag tests, including the full cent sweep. The ArchUnit rule forbids `double` and `float`.
- **M3:** the golden row diffs against the desktop-written rows in `sample-book.sql`, and the balance table.
- **M4/M5 end to end:**
  1. Run the app with `docker compose` locally against `postgres:17.11` loaded from `sample-book.sql`.
  2. Drive it with the Firefox MCP at phone viewport (390×844): log in, enter 12.34 with two tags, check the register and balances, edit, delete. Then insert a `gnclock` row and confirm writes are refused, and that Clear lock works.
  3. Dump the DB, load it into GnuCash desktop through the SQLite or PG route, and confirm the entries and balances look identical.
