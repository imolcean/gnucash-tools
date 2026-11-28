package org.duckdns.gory26.gnucash.book;

import org.duckdns.gory26.gnucash.book.WriteRefusedException.Reason;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

/**
 * The GnuCash SQL book on Postgres: reads balances, registers and whole transactions, suggests entries, and writes
 * two-split transfers, N-split transactions, accounts and currencies exactly as desktop 5.16 does. Plain JDBC over
 * the given pool (ADR-0014); a database failure comes out as {@link BookAccessException}.
 */
public final class GnuCashBook {

    /** The GnuCash 5.16 table versions this adapter writes for (ADR-0005). */
    static final Map<String, Integer> KNOWN_VERSIONS = Map.ofEntries(
            Map.entry("books", 1), Map.entry("commodities", 1), Map.entry("accounts", 1),
            Map.entry("budgets", 1), Map.entry("budget_amounts", 1), Map.entry("prices", 3),
            Map.entry("transactions", 4), Map.entry("splits", 5), Map.entry("slots", 4),
            Map.entry("recurrences", 2), Map.entry("schedxactions", 1), Map.entry("lots", 2),
            Map.entry("billterms", 2), Map.entry("customers", 2), Map.entry("employees", 2),
            Map.entry("entries", 4), Map.entry("invoices", 4), Map.entry("jobs", 1),
            Map.entry("orders", 1), Map.entry("taxtables", 2), Map.entry("taxtable_entries", 3),
            Map.entry("vendors", 1));

    /** Change with every desktop release without changing the schema. */
    private static final Set<String> IGNORED_VERSIONS = Set.of("Gnucash", "Gnucash-Resave");

    /** Postgres regex for the leading {@code [Tag]} groups, the shape {@link TaggedDescription#parse} reads. */
    private static final String TAG_PREFIX = "^(\\s*\\[[^][]+\\])*\\s*";
    private static final String TEXT_AFTER_TAGS = "btrim(regexp_replace(t.description, '" + TAG_PREFIX + "', ''))";

    /** Register order, newest first: desktop's date, then entry time; the split GUID breaks ties. */
    private static final String POST_DATE = "COALESCE(t.post_date, TIMESTAMP '1970-01-01')";
    private static final String ENTER_DATE = "COALESCE(t.enter_date, TIMESTAMP '1970-01-01')";
    private static final String SORT_KEY = POST_DATE + ", " + ENTER_DATE + ", s.guid";
    private static final String NEWEST_FIRST = POST_DATE + " DESC, " + ENTER_DATE + " DESC, s.guid DESC";

    private static final int SUGGESTIONS = 10;
    private static final int ACCOUNT_USAGE_DAYS = 90;

    /** The book options' slot names, full paths below the book's {@code options} frame (ADR-0015). */
    private static final String OPTION_TRADING = "options/Accounts/Use Trading Accounts";
    private static final String OPTION_RED_LINE = "options/Accounts/Day Threshold for Read-Only Transactions (red line)";

    private static final Jdbc.RowMapper<LockHolder> LOCK_MAPPER =
            rs -> new LockHolder(rs.getString("hostname"), rs.getInt("pid"));

    private static final Jdbc.RowMapper<Commodity> COMMODITY =
            rs -> new Commodity(rs.getString(1), rs.getString(2), rs.getString(3), rs.getLong(4));

    private final Jdbc jdbc;
    private final Clock clock;

    /**
     * @param clock gives "today" for Present balances and account usage, and "now" for {@code enter_date}
     */
    public GnuCashBook(DataSource dataSource, Clock clock) {
        this.jdbc = Jdbc.over(dataSource);
        this.clock = clock;
    }

    // ---------------------------------------------------------------- schema and lock

    public SchemaStatus schemaStatus() {
        return schemaStatus(jdbc);
    }

    /** Who holds the desktop lock, if anyone (ADR-0004). */
    public Optional<LockHolder> lock() {
        return jdbc.list("SELECT hostname, pid FROM gnclock", LOCK_MAPPER).stream().findFirst();
    }

    /** Deletes the desktop lock rows; the UI asks first, naming the host and PID. */
    @SuppressWarnings("SqlWithoutWhere") // every lock row goes
    public void clearLock() {
        jdbc.update("DELETE FROM gnclock");
    }

    // ---------------------------------------------------------------- account tree

    /**
     * The visible accounts below the root with Present balances as of {@code asOf}: splits dated on or
     * before that day (ADR-0008).
     */
    public List<AccountNode> accountTree(LocalDate asOf) {
        Accounts accounts = Accounts.load(jdbc);
        Map<String, GncNumeric> balances = balancesBefore(asOf.plusDays(1).atStartOfDay());
        Prices prices = Prices.load(jdbc);
        return visibleChildren(accounts, accounts.rootGuid(), balances, prices);
    }

    /** The tree as of today by the book's clock. */
    public List<AccountNode> accountTree() {
        return accountTree(today());
    }

    /** One account with its own Present balance as of today, hidden or not; empty when there is no such account. */
    public Optional<AccountSummary> account(String guid) {
        Accounts accounts = Accounts.load(jdbc);
        Accounts.Row row;
        try {
            row = accounts.get(guid);
        }
        catch(IllegalArgumentException e) {
            return Optional.empty();
        }
        return Optional.of(summary(accounts, row));
    }

    /**
     * The account at {@code path} below the root, e.g. {@code Expenses:Food}, hidden or not, with its own Present
     * balance as of today; empty when there is none.
     *
     * @throws IllegalStateException if siblings share a name, so the path names more than one account
     */
    public Optional<AccountSummary> accountByPath(String path) {
        Accounts accounts = Accounts.load(jdbc);
        List<Accounts.Row> rows = accounts.byPath(path);
        if(rows.size() > 1) {
            throw new IllegalStateException(path + " names " + rows.size() + " accounts");
        }
        return rows.stream().findFirst().map(row -> summary(accounts, row));
    }

    /** The root of the account tree: the parent of the top-level accounts. */
    public String rootAccountGuid() {
        return Accounts.load(jdbc).rootGuid();
    }

    public RegisterPage register(String accountGuid, int limit) {
        return register(accountGuid, null, limit);
    }

    /**
     * One page of the account's splits, newest first, with running balances; future-dated rows included.
     *
     * @param after the previous page's {@link RegisterPage#next()}, or null for the first page
     */
    public RegisterPage register(String accountGuid, RegisterCursor after, int limit) {
        if(limit < 1) {
            throw new IllegalArgumentException("limit must be positive: " + limit);
        }
        Accounts accounts = Accounts.load(jdbc);
        Accounts.Row account = accounts.get(accountGuid);

        record Line(String splitGuid,
                    String txGuid,
                    LocalDateTime postDate,
                    LocalDateTime enterDate,
                    String description,
                    String currencyGuid,
                    GncNumeric amount) {
        }
        List<Object> params = new ArrayList<>(List.of(accountGuid));
        if(after != null) {
            params.addAll(List.of(after.postDate(), after.enterDate(), after.splitGuid()));
        }
        params.add(limit + 1);
        List<Line> lines = jdbc.list("SELECT s.guid AS split_guid, t.guid AS tx_guid, "
                        + POST_DATE + " AS post_date, " + ENTER_DATE + " AS enter_date, "
                        + "t.description, t.currency_guid, s.quantity_num, s.quantity_denom "
                        + "FROM splits s JOIN transactions t ON t.guid = s.tx_guid "
                        + "WHERE s.account_guid = ? "
                        + (after == null ? "" : "AND (" + SORT_KEY + ") < (?, ?, ?) ")
                        + "ORDER BY " + NEWEST_FIRST + " LIMIT ?",
                rs -> new Line(
                        rs.getString("split_guid"), rs.getString("tx_guid"),
                        rs.getObject("post_date", LocalDateTime.class), rs.getObject("enter_date", LocalDateTime.class),
                        rs.getString("description"), rs.getString("currency_guid"),
                        new GncNumeric(rs.getLong("quantity_num"), rs.getLong("quantity_denom"))),
                params.toArray());
        if(lines.isEmpty()) {
            return new RegisterPage(List.of(), Optional.empty());
        }
        boolean more = lines.size() > limit;
        List<Line> page = more ? lines.subList(0, limit) : lines;

        Line newest = page.getFirst();
        GncNumeric balance = balanceOf(accountGuid, account.commodity(),
                "(" + SORT_KEY + ") <= (?, ?, ?)", newest.postDate(), newest.enterDate(), newest.splitGuid());

        List<String> txGuids = page.stream().map(Line::txGuid).distinct().toList();
        Map<String, List<SplitRow>> splitsByTx = jdbc.list("SELECT " + SplitRow.COLUMNS
                        + " FROM splits s WHERE s.tx_guid = ANY(?) ORDER BY s.guid", SplitRow.MAPPER, txGuids).stream()
                .collect(Collectors.groupingBy(SplitRow::txGuid));
        Map<String, String> notes = notesOf(txGuids);

        List<RegisterRow> rows = new ArrayList<>();
        for(Line line : page) {
            List<SplitRow> splits = splitsByTx.get(line.txGuid());
            SplitRow own = splits.stream().filter(s -> s.guid().equals(line.splitGuid())).findFirst().orElseThrow();
            rows.add(new RegisterRow(line.txGuid(), line.splitGuid(), line.postDate().toLocalDate(),
                    line.description(), notes.getOrDefault(line.txGuid(), ""), line.amount(), balance,
                    splits.stream().filter(s -> s != own).map(s -> accounts.path(s.accountGuid())).toList(),
                    own.reconcileState(),
                    whyNotEditable(accounts, line.currencyGuid(), splits).isEmpty()));
            balance = balance.add(line.amount().negate());
        }
        Line last = page.getLast();
        return new RegisterPage(rows, more
                ? Optional.of(new RegisterCursor(last.postDate(), last.enterDate(), last.splitGuid()))
                : Optional.empty());
    }

    /** The edit form's starting point, for a transaction the app may edit. */
    public Optional<TransferDraft> draft(String txGuid) {
        record Head(String currencyGuid,
                    LocalDateTime postDate,
                    String description) {
        }
        Optional<Head> head = jdbc.optional("SELECT currency_guid, post_date, description FROM transactions WHERE guid = ?",
                rs -> new Head(rs.getString(1), rs.getObject(2, LocalDateTime.class), rs.getString(3)), txGuid);
        if(head.isEmpty()) {
            return Optional.empty();
        }
        List<SplitRow> splits = splitsOf(jdbc, txGuid, false);
        if(whyNotEditable(Accounts.load(jdbc), head.get().currencyGuid(), splits).isPresent()) {
            return Optional.empty();
        }
        SplitRow in = splits.get(0).value().signum() > 0 ? splits.get(0) : splits.get(1);
        SplitRow out = in == splits.get(0) ? splits.get(1) : splits.get(0);
        TaggedDescription description = TaggedDescription.parse(head.get().description());
        return Optional.of(new TransferDraft(head.get().postDate().toLocalDate(), description.tags(), description.text(),
                in.value(), out.accountGuid(), in.accountGuid(), notesOf(List.of(txGuid)).getOrDefault(txGuid, "")));
    }

    /** Distinct description texts (tags stripped) containing {@code text}, most recent first. */
    public List<String> suggestDescriptions(String text) {
        String pattern = "%" + likeEscape(text.strip()) + "%";
        return jdbc.list("SELECT d.text FROM (SELECT " + TEXT_AFTER_TAGS + " AS text, "
                        + "MAX(t.post_date) AS last_post, MAX(t.enter_date) AS last_enter "
                        + "FROM transactions t WHERE t.description ILIKE ? GROUP BY 1) d "
                        + "WHERE d.text ILIKE ? AND d.text <> '' "
                        + "ORDER BY d.last_post DESC, d.last_enter DESC LIMIT " + SUGGESTIONS,
                rs -> rs.getString(1), pattern, pattern);
    }

    /** Tags, accounts and amount of the latest two-split transaction whose text (tags stripped) is {@code text}. */
    public Optional<QuickFill> quickFill(String text) {
        record Match(String guid,
                     String description) {
        }
        Optional<Match> match = jdbc.optional("SELECT t.guid, t.description FROM transactions t "
                        + "WHERE " + TEXT_AFTER_TAGS + " = ? "
                        + "AND (SELECT COUNT(*) FROM splits s WHERE s.tx_guid = t.guid) = 2 "
                        + "ORDER BY t.post_date DESC, t.enter_date DESC LIMIT 1",
                rs -> new Match(rs.getString(1), rs.getString(2)), text.strip());
        return match.map(m -> {
            List<SplitRow> splits = splitsOf(jdbc, m.guid(), false);
            SplitRow in = splits.get(0).value().signum() > 0 ? splits.get(0) : splits.get(1);
            SplitRow out = in == splits.get(0) ? splits.get(1) : splits.get(0);
            TaggedDescription description = TaggedDescription.parse(m.description());
            return new QuickFill(description.tags(), description.text(), in.value(), out.accountGuid(), in.accountGuid());
        });
    }

    // ---------------------------------------------------------------- register

    /** Distinct tags starting with {@code prefix}, most recently used first. */
    public List<String> suggestTags(String prefix) {
        return jdbc.list("""
                        SELECT d.tag FROM (
                            SELECT btrim(m[1]) AS tag, MAX(t.post_date) AS last_post
                            FROM transactions t
                            CROSS JOIN LATERAL regexp_matches(
                                (regexp_match(t.description, '^((\\s*\\[[^][]+\\])+)'))[1], '\\[([^][]+)\\]', 'g') AS m
                            WHERE t.description ~ '^\\s*\\['
                            GROUP BY 1) d
                        WHERE d.tag ILIKE ? AND d.tag <> ''
                        ORDER BY d.last_post DESC, d.tag
                        LIMIT ?""",
                rs -> rs.getString(1), likeEscape(prefix.strip()) + "%", SUGGESTIONS);
    }

    /**
     * Accounts a transfer may use whose path contains every word of {@code query}, case-insensitively.
     * Accounts used in the last 90 days come first, by number of uses, then by last use.
     */
    public List<AccountChoice> suggestAccounts(String query) {
        Accounts accounts = Accounts.load(jdbc);
        record Usage(long uses,
                     LocalDateTime lastUsed) {
        }
        Map<String, Usage> usage = new HashMap<>();
        jdbc.list("""
                                SELECT s.account_guid, COUNT(*) AS uses, MAX(t.post_date) AS last_used
                                FROM splits s JOIN transactions t ON t.guid = s.tx_guid
                                WHERE t.post_date >= ?
                                GROUP BY s.account_guid""",
                        rs -> Map.entry(rs.getString(1), new Usage(rs.getLong(2), rs.getObject(3, LocalDateTime.class))),
                        today().minusDays(ACCOUNT_USAGE_DAYS).atStartOfDay())
                .forEach(e -> usage.put(e.getKey(), e.getValue()));
        Usage unused = new Usage(0, LocalDateTime.MIN);
        List<String> words = Arrays.stream(query.strip().toLowerCase(Locale.ROOT).split("\\s+"))
                .filter(w -> !w.isEmpty()).toList();
        record Candidate(Accounts.Row row,
                         String path,
                         Usage usage) {
        }
        return accounts.all().stream()
                .filter(row -> accounts.whyNotTransferable(row.guid()).isEmpty())
                .map(row -> new Candidate(row, accounts.path(row.guid()), usage.getOrDefault(row.guid(), unused)))
                .filter(c -> words.stream().allMatch(c.path().toLowerCase(Locale.ROOT)::contains))
                .sorted(Comparator.comparingLong((Candidate c) -> c.usage().uses()).reversed()
                        .thenComparing((Candidate c) -> c.usage().lastUsed(), Comparator.reverseOrder())
                        .thenComparing(Candidate::path))
                .limit(SUGGESTIONS)
                .map(c -> new AccountChoice(c.row().guid(), c.path(), c.row().commodity().mnemonic()))
                .toList();
    }

    /**
     * Adds the transfer as a new transaction.
     *
     * @return the new transaction's GUID
     * @throws WriteRefusedException if the book is locked, the schema unknown, or the draft not allowed
     */
    public String insert(TransferDraft draft) {
        return write((db, accounts) -> {
            Commodity currency = checkTransfer(accounts, draft);
            String guid = BookWriter.newGuid();
            new BookWriter(db).insert(guid, draft, currency, now());
            return guid;
        });
    }

    /** Replaces an editable transaction's date, description, notes, accounts and amount. */
    public void update(String txGuid, TransferDraft draft) {
        write((db, accounts) -> {
            List<SplitRow> splits = lockEditable(db, accounts, txGuid);
            new BookWriter(db).update(txGuid, splits, draft, checkTransfer(accounts, draft));
            return txGuid;
        });
    }

    /** Deletes an editable transaction with its splits and the slots of both. */
    public void delete(String txGuid) {
        write((db, accounts) -> {
            new BookWriter(db).delete(txGuid, lockEditable(db, accounts, txGuid));
            return txGuid;
        });
    }

    // ---------------------------------------------------------------- whole transactions (ADR-0015)

    /**
     * Adds an N-split transaction under the caller's GUID, with its slots, in one DB transaction. Unlike a
     * {@link TransferDraft}, splits may mix commodities and use hidden and TRADING accounts.
     *
     * @throws WriteRefusedException if the book is locked or its schema unknown, the GUID is taken, the currency or an
     *                               account is unknown, an account is a placeholder, an amount is finer than its
     *                               fraction, or a split in the transaction's currency has value ≠ quantity
     */
    public void insertTransaction(NewTransaction tx) {
        write((db, accounts) -> {
            if(db.optional("SELECT guid FROM transactions WHERE guid = ?", rs -> rs.getString(1), tx.guid()).isPresent()) {
                throw new WriteRefusedException(Reason.DUPLICATE_GUID, "transaction " + tx.guid() + " already exists");
            }
            Commodity currency = commodityByGuid(db, tx.currencyGuid())
                    .filter(Commodity::isCurrency)
                    .orElseThrow(() -> new WriteRefusedException(Reason.UNKNOWN_COMMODITY,
                            "no currency " + tx.currencyGuid()));
            List<Long> scus = new ArrayList<>();
            for(NewSplit split : tx.splits()) {
                scus.add(checkSplit(accounts, currency, split));
            }
            new BookWriter(db).insert(tx, currency, scus, now());
            return tx.guid();
        });
    }

    /** The transaction with all its splits and string slots; empty when there is none. */
    public Optional<StoredTransaction> transaction(String guid) {
        return new TransactionReader(jdbc).byGuid(guid);
    }

    /**
     * Every transaction posted on a day from {@code from} to {@code to}, both included, ordered by date posted, then
     * entry time; each with all its splits and string slots.
     */
    public List<StoredTransaction> transactions(LocalDate from, LocalDate to) {
        if(to.isBefore(from)) {
            throw new IllegalArgumentException("to " + to + " is before from " + from);
        }
        return new TransactionReader(jdbc).postedBetween(from, to);
    }

    // ---------------------------------------------------------------- accounts, currencies, options (ADR-0015)

    /**
     * Adds an account under the caller's GUID as desktop's account dialog writes it: not hidden, no code or
     * description, the SCU from the commodity.
     *
     * @throws WriteRefusedException if the book is locked or its schema unknown, the GUID is taken, the parent or the
     *                               commodity is unknown, or the parent already has a child of that name
     */
    public void createAccount(NewAccount account) {
        write((db, accounts) -> {
            if(db.optional("SELECT guid FROM accounts WHERE guid = ?", rs -> rs.getString(1), account.guid()).isPresent()) {
                throw new WriteRefusedException(Reason.DUPLICATE_GUID, "account " + account.guid() + " already exists");
            }
            if(!accounts.inBook(account.parentGuid())) {
                throw new WriteRefusedException(Reason.ACCOUNT_NOT_ALLOWED, "unknown parent account " + account.parentGuid());
            }
            if(accounts.children(account.parentGuid()).stream().anyMatch(c -> c.name().equals(account.name()))) {
                String parent = accounts.path(account.parentGuid());
                throw new WriteRefusedException(Reason.DUPLICATE_NAME,
                        (parent.isEmpty() ? "the root" : parent) + " already has an account " + account.name());
            }
            Commodity commodity = commodityByGuid(db, account.commodityGuid())
                    .orElseThrow(() -> new WriteRefusedException(Reason.UNKNOWN_COMMODITY,
                            "no commodity " + account.commodityGuid()));
            new BookWriter(db).insert(account, commodity);
            return account.guid();
        });
    }

    /** The book's currency with this ISO 4217 code, such as {@code CURRENCY:EUR}; empty when the book has none yet. */
    public Optional<Commodity> currency(String isoCode) {
        return currencyByCode(jdbc, isoCode);
    }

    /**
     * The book's currency with this ISO 4217 code, added as desktop adds one when an account first uses it.
     *
     * @throws IllegalArgumentException if the code is not an ISO 4217 currency with a fixed fraction
     * @throws WriteRefusedException    if it has to be added and the book is locked or its schema unknown
     */
    public Commodity findOrCreateCurrency(String isoCode) {
        Currency iso = Currency.getInstance(isoCode);
        return currency(isoCode).orElseGet(() -> write((db, _) -> {
            // a second caller adding the same currency waits here until the first commits, then finds it
            db.single("SELECT pg_advisory_xact_lock(hashtext('gnucash-book:currencies'))", _ -> true);
            return currencyByCode(db, isoCode).orElseGet(() -> new BookWriter(db).insertCurrency(iso));
        }));
    }

    /** The book options the write path needs; a missing option reads as desktop's default (off). */
    public BookOptions bookOptions() {
        Map<String, String> options = new HashMap<>();
        // desktop stores the threshold as a double; it is read as an integer here (ADR-0003: no floating point)
        jdbc.list("""
                                SELECT name, CASE slot_type WHEN 1 THEN CAST(int64_val AS varchar)
                                                            WHEN 2 THEN CAST(CAST(round(double_val) AS bigint) AS varchar)
                                                            ELSE string_val END
                                FROM slots WHERE name = ANY(?)""",
                        rs -> Map.entry(rs.getString(1), Objects.requireNonNullElse(rs.getString(2), "")),
                        List.of(OPTION_TRADING, OPTION_RED_LINE))
                .forEach(e -> options.put(e.getKey(), e.getValue()));
        String days = options.getOrDefault(OPTION_RED_LINE, "");
        return new BookOptions("t".equals(options.get(OPTION_TRADING)), days.isEmpty() ? 0 : Integer.parseInt(days));
    }

    // ---------------------------------------------------------------- helpers

    private static SchemaStatus schemaStatus(Jdbc db) {
        Map<String, Integer> found = new TreeMap<>();
        db.list("SELECT table_name, table_version FROM versions", rs -> Map.entry(rs.getString(1), rs.getInt(2)))
                .forEach(e -> found.put(e.getKey(), e.getValue()));
        IGNORED_VERSIONS.forEach(found::remove);
        List<String> differences = new ArrayList<>();
        new TreeMap<>(KNOWN_VERSIONS).forEach((table, expected) -> {
            Integer actual = found.remove(table);
            if(actual == null) {
                differences.add(table + ": missing, expected version " + expected);
            }
            else if(!actual.equals(expected)) {
                differences.add(table + ": version " + actual + ", expected " + expected);
            }
        });
        found.forEach((table, version) -> differences.add(table + ": unexpected table, version " + version));
        return new SchemaStatus(differences);
    }

    private AccountSummary summary(Accounts accounts, Accounts.Row row) {
        GncNumeric balance = balanceOf(row.guid(), row.commodity(), "t.post_date < ?", today().plusDays(1).atStartOfDay());
        return new AccountSummary(row.guid(), row.name(), accounts.path(row.guid()), row.type(), row.commodity(),
                row.placeholder(), accounts.effectivelyHidden(row.guid()), balance);
    }

    /**
     * Desktop's rollup: each account in the subtree converts its own balance straight into the top account's
     * commodity with the latest price, rounded to that commodity's fraction ({@code Account.cpp},
     * {@code xaccAccountGetXxxBalanceAsOfDateInCurrencyRecursive}). A missing price gives no total rather
     * than desktop's silent zero.
     */
    private static Optional<GncNumeric> total(Accounts accounts, Accounts.Row top,
                                              Map<String, GncNumeric> balances, Prices prices) {
        Commodity target = top.commodity();
        GncNumeric sum = zero(target);
        for(Accounts.Row row : accounts.subtree(top.guid())) {
            GncNumeric own = balances.get(row.guid());
            if(own == null || own.isZero()) {
                continue;
            }
            if(row.commodity().guid().equals(target.guid())) {
                sum = sum.add(own);
                continue;
            }
            Optional<GncNumeric> rate = prices.rate(row.commodity().guid(), target.guid());
            if(rate.isEmpty()) {
                return Optional.empty();
            }
            sum = sum.add(own.multiply(rate.get()).roundToFraction(target.fraction()));
        }
        return Optional.of(sum);
    }

    private static GncNumeric zero(Commodity commodity) {
        return new GncNumeric(0, commodity == null ? 1 : commodity.fraction());
    }

    /**
     * Why the app may not edit or delete this transaction, or empty when it may (ADR-0008): exactly two
     * splits, none reconciled, both in transferable accounts of the transaction's currency, moving money.
     */
    static Optional<String> whyNotEditable(Accounts accounts, String currencyGuid, List<SplitRow> splits) {
        if(splits.size() != 2) {
            return Optional.of("it has " + splits.size() + " splits");
        }
        for(SplitRow split : splits) {
            if(split.reconciled()) {
                return Optional.of("a split is reconciled");
            }
            Optional<String> account = accounts.whyNotTransferable(split.accountGuid());
            if(account.isPresent()) {
                return account;
            }
            if(!accounts.get(split.accountGuid()).commodity().guid().equals(currencyGuid)
                    || split.value().compareTo(split.quantity()) != 0) {
                return Optional.of("it mixes commodities");
            }
            if(split.value().isZero()) {
                return Optional.of("it moves nothing");
            }
        }
        return Optional.empty();
    }

    private static String likeEscape(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static Commodity checkTransfer(Accounts accounts, TransferDraft draft) {
        Set<String> guids = new LinkedHashSet<>(List.of(draft.fromAccountGuid(), draft.toAccountGuid()));
        for(String guid : guids) {
            Optional<String> why = accounts.whyNotTransferable(guid);
            if(why.isPresent()) {
                throw new WriteRefusedException(Reason.ACCOUNT_NOT_ALLOWED, why.get());
            }
        }
        Commodity from = accounts.get(draft.fromAccountGuid()).commodity();
        Commodity to = accounts.get(draft.toAccountGuid()).commodity();
        if(!from.guid().equals(to.guid())) {
            throw new WriteRefusedException(Reason.MIXED_COMMODITIES,
                    "from is in " + from.mnemonic() + " but to is in " + to.mnemonic());
        }
        if(!fits(draft.amount(), from.fraction())) {
            throw new WriteRefusedException(Reason.AMOUNT_TOO_PRECISE,
                    draft.amount().num() + "/" + draft.amount().denom() + " is finer than " + from.mnemonic() + " allows");
        }
        return from;
    }

    /**
     * Checks one split of a new transaction against the book (ADR-0015).
     *
     * @return the account's SCU, the denominator its quantity is stored over
     */
    private static long checkSplit(Accounts accounts, Commodity currency, NewSplit split) {
        Optional<String> why = accounts.whyNotPostable(split.accountGuid());
        if(why.isPresent()) {
            throw new WriteRefusedException(Reason.ACCOUNT_NOT_ALLOWED, why.get());
        }
        Accounts.Row account = accounts.get(split.accountGuid());
        String path = accounts.path(account.guid());
        if(!fits(split.value(), currency.fraction())) {
            throw new WriteRefusedException(Reason.AMOUNT_TOO_PRECISE, "value " + split.value().num() + "/"
                    + split.value().denom() + " in " + path + " is finer than " + currency.mnemonic() + " allows");
        }
        if(!fits(split.quantity(), account.scu())) {
            throw new WriteRefusedException(Reason.AMOUNT_TOO_PRECISE, "quantity " + split.quantity().num() + "/"
                    + split.quantity().denom() + " is finer than " + path + " allows (1/" + account.scu() + ")");
        }
        if(account.commodity().guid().equals(currency.guid()) && split.value().compareTo(split.quantity()) != 0) {
            throw new WriteRefusedException(Reason.VALUE_QUANTITY_MISMATCH, path + " is in the transaction's currency "
                    + currency.mnemonic() + ", so its value and quantity must be equal");
        }
        return account.scu();
    }

    private static boolean fits(GncNumeric amount, long fraction) {
        try {
            amount.toFraction(fraction);
            return true;
        }
        catch(ArithmeticException e) {
            return false;
        }
    }

    private List<AccountNode> visibleChildren(Accounts accounts, String parentGuid,
                                              Map<String, GncNumeric> balances, Prices prices) {
        return accounts.children(parentGuid).stream()
                .filter(row -> !row.hidden())
                .map(row -> new AccountNode(row.guid(), row.name(), accounts.path(row.guid()), row.type(),
                        row.commodity(), row.placeholder(),
                        balances.getOrDefault(row.guid(), zero(row.commodity())),
                        total(accounts, row, balances, prices),
                        visibleChildren(accounts, row.guid(), balances, prices)))
                .toList();
    }

    /** One grouped aggregate, exact: integer sums per denominator, combined as rationals here. */
    private Map<String, GncNumeric> balancesBefore(LocalDateTime end) {
        Map<String, GncNumeric> balances = new HashMap<>();
        jdbc.list("""
                                SELECT s.account_guid, s.quantity_denom, SUM(s.quantity_num) AS quantity
                                FROM splits s JOIN transactions t ON t.guid = s.tx_guid
                                WHERE t.post_date < ?
                                GROUP BY s.account_guid, s.quantity_denom""",
                        rs -> Map.entry(rs.getString("account_guid"), new GncNumeric(
                                rs.getBigDecimal("quantity").longValueExact(), rs.getLong("quantity_denom"))),
                        end)
                .forEach(e -> balances.merge(e.getKey(), e.getValue(), GncNumeric::add));
        return balances;
    }

    /**
     * One account's balance over the splits matching {@code where}, exact: integer sums per denominator,
     * combined as rationals here.
     *
     * @param where an SQL condition on {@code s} (splits) and {@code t} (transactions), with {@code ?} for {@code params}
     */
    private GncNumeric balanceOf(String accountGuid, Commodity commodity, String where, Object... params) {
        List<Object> all = new ArrayList<>(List.of(accountGuid));
        all.addAll(Arrays.asList(params));
        GncNumeric balance = zero(commodity);
        for(GncNumeric part : jdbc.list("SELECT s.quantity_denom, SUM(s.quantity_num) AS quantity "
                        + "FROM splits s JOIN transactions t ON t.guid = s.tx_guid "
                        + "WHERE s.account_guid = ? AND " + where + " "
                        + "GROUP BY s.quantity_denom",
                rs -> new GncNumeric(rs.getBigDecimal("quantity").longValueExact(), rs.getLong("quantity_denom")),
                all.toArray())) {
            balance = balance.add(part);
        }
        return balance;
    }

    private Map<String, String> notesOf(List<String> txGuids) {
        Map<String, String> notes = new HashMap<>();
        jdbc.list("SELECT obj_guid, string_val FROM slots WHERE name = 'notes' AND obj_guid = ANY(?)",
                        rs -> Map.entry(rs.getString(1), Objects.requireNonNullElse(rs.getString(2), "")), txGuids)
                .forEach(e -> notes.put(e.getKey(), e.getValue()));
        return notes;
    }

    private static List<SplitRow> splitsOf(Jdbc db, String txGuid, boolean forUpdate) {
        return db.list("SELECT " + SplitRow.COLUMNS + " FROM splits s WHERE s.tx_guid = ? ORDER BY s.guid"
                + (forUpdate ? " FOR UPDATE" : ""), SplitRow.MAPPER, txGuid);
    }

    private static Optional<Commodity> commodityByGuid(Jdbc db, String guid) {
        return db.optional("SELECT guid, namespace, mnemonic, fraction FROM commodities WHERE guid = ?", COMMODITY, guid);
    }

    private static Optional<Commodity> currencyByCode(Jdbc db, String isoCode) {
        return db.optional("SELECT guid, namespace, mnemonic, fraction FROM commodities "
                + "WHERE namespace = 'CURRENCY' AND mnemonic = ?", COMMODITY, isoCode);
    }

    /**
     * Runs a write in one DB transaction behind the lock and schema checks. {@code LOCK TABLE … IN SHARE MODE}
     * makes a desktop that opens the book meanwhile wait for the commit instead of slipping in after the check.
     */
    private <T> T write(BiFunction<Jdbc, Accounts, T> work) {
        return jdbc.inTransaction(db -> {
            db.update("LOCK TABLE gnclock IN SHARE MODE");
            Optional<LockHolder> holder = db.list("SELECT hostname, pid FROM gnclock", LOCK_MAPPER).stream().findFirst();
            if(holder.isPresent()) {
                throw new WriteRefusedException(Reason.LOCKED,
                        "Locked by " + holder.get().hostname() + " (PID " + holder.get().pid() + ")");
            }
            SchemaStatus schema = schemaStatus(db);
            if(!schema.known()) {
                throw new WriteRefusedException(Reason.UNKNOWN_SCHEMA,
                        "Unknown schema, read-only: " + String.join("; ", schema.differences()));
            }
            return work.apply(db, Accounts.load(db));
        });
    }

    private static List<SplitRow> lockEditable(Jdbc db, Accounts accounts, String txGuid) {
        String currency = db.optional("SELECT currency_guid FROM transactions WHERE guid = ? FOR UPDATE",
                        rs -> rs.getString(1), txGuid)
                .orElseThrow(() -> new WriteRefusedException(Reason.NOT_FOUND, "no transaction " + txGuid));
        List<SplitRow> splits = splitsOf(db, txGuid, true);
        Optional<String> why = whyNotEditable(accounts, currency, splits);
        if(why.isPresent()) {
            throw new WriteRefusedException(Reason.NOT_EDITABLE, "this transaction is left to desktop: " + why.get());
        }
        return splits;
    }

    /** {@code enter_date}: now in UTC, to the second. */
    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS);
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }
}
