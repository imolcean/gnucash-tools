package org.duckdns.gory26.gnucash.book;

import org.duckdns.gory26.gnucash.book.WriteRefusedException.Reason;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The GnuCash SQL book on Postgres: reads balances and registers, suggests entries, and writes two-split
 * transfers exactly as desktop 5.16 does. No JPA; one {@link JdbcClient} over the given pool.
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

    private static final RowMapper<LockHolder> LOCK_MAPPER =
            (rs, _) -> new LockHolder(rs.getString("hostname"), rs.getInt("pid"));

    private final JdbcClient jdbc;
    private final TransactionTemplate transactions;
    private final BookWriter writer;
    private final Clock clock;

    /**
     * @param clock gives "today" for Present balances and account usage, and "now" for {@code enter_date}
     */
    public GnuCashBook(DataSource dataSource, Clock clock) {
        this.jdbc = JdbcClient.create(dataSource);
        this.transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        this.writer = new BookWriter(jdbc);
        this.clock = clock;
    }

    // ---------------------------------------------------------------- schema and lock

    public SchemaStatus schemaStatus() {
        Map<String, Integer> found = new TreeMap<>();
        jdbc.sql("SELECT table_name, table_version FROM versions")
                .query(rs -> {
                    found.put(rs.getString(1), rs.getInt(2));
                });
        IGNORED_VERSIONS.forEach(found::remove);
        List<String> differences = new ArrayList<>();
        new TreeMap<>(KNOWN_VERSIONS).forEach((table, expected) -> {
            Integer actual = found.remove(table);
            if (actual == null) {
                differences.add(table + ": missing, expected version " + expected);
            } else if (!actual.equals(expected)) {
                differences.add(table + ": version " + actual + ", expected " + expected);
            }
        });
        found.forEach((table, version) -> differences.add(table + ": unexpected table, version " + version));
        return new SchemaStatus(differences);
    }

    /** Who holds the desktop lock, if anyone (ADR-0004). */
    public Optional<LockHolder> lock() {
        return jdbc.sql("SELECT hostname, pid FROM gnclock").query(LOCK_MAPPER).list().stream().findFirst();
    }

    /** Deletes the desktop lock rows; the UI asks first, naming the host and PID. */
    @SuppressWarnings("SqlWithoutWhere") // every lock row goes
    public void clearLock() {
        jdbc.sql("DELETE FROM gnclock").update();
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
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        GncNumeric balance = balanceOf(guid, row.commodity(), "t.post_date < :end",
                Map.of("end", today().plusDays(1).atStartOfDay()));
        return Optional.of(new AccountSummary(row.guid(), row.name(), accounts.path(guid), row.type(), row.commodity(),
                row.placeholder(), accounts.effectivelyHidden(guid), balance));
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
        for (Accounts.Row row : accounts.subtree(top.guid())) {
            GncNumeric own = balances.get(row.guid());
            if (own == null || own.isZero()) {
                continue;
            }
            if (row.commodity().guid().equals(target.guid())) {
                sum = sum.add(own);
                continue;
            }
            Optional<GncNumeric> rate = prices.rate(row.commodity().guid(), target.guid());
            if (rate.isEmpty()) {
                return Optional.empty();
            }
            sum = sum.add(own.multiply(rate.get()).roundToFraction(target.fraction()));
        }
        return Optional.of(sum);
    }

    /** One grouped aggregate, exact: integer sums per denominator, combined as rationals here. */
    private Map<String, GncNumeric> balancesBefore(LocalDateTime end) {
        Map<String, GncNumeric> balances = new HashMap<>();
        jdbc.sql("""
                        SELECT s.account_guid, s.quantity_denom, SUM(s.quantity_num) AS quantity
                        FROM splits s JOIN transactions t ON t.guid = s.tx_guid
                        WHERE t.post_date < :end
                        GROUP BY s.account_guid, s.quantity_denom""")
                .param("end", end)
                .query(rs -> {
                    GncNumeric part = new GncNumeric(rs.getBigDecimal("quantity").longValueExact(),
                            rs.getLong("quantity_denom"));
                    balances.merge(rs.getString("account_guid"), part, GncNumeric::add);
                });
        return balances;
    }

    /**
     * One account's balance over the splits matching {@code where}, exact: integer sums per denominator,
     * combined as rationals here.
     *
     * @param where an SQL condition on {@code s} (splits) and {@code t} (transactions), using {@code params}
     */
    private GncNumeric balanceOf(String accountGuid, Commodity commodity, String where, Map<String, ?> params) {
        GncNumeric balance = zero(commodity);
        for (GncNumeric part : jdbc.sql("SELECT s.quantity_denom, SUM(s.quantity_num) AS quantity "
                        + "FROM splits s JOIN transactions t ON t.guid = s.tx_guid "
                        + "WHERE s.account_guid = :account AND " + where + " "
                        + "GROUP BY s.quantity_denom")
                .param("account", accountGuid)
                .params(params)
                .query((rs, _) -> new GncNumeric(rs.getBigDecimal("quantity").longValueExact(), rs.getLong("quantity_denom")))
                .list()) {
            balance = balance.add(part);
        }
        return balance;
    }

    private static GncNumeric zero(Commodity commodity) {
        return new GncNumeric(0, commodity == null ? 1 : commodity.fraction());
    }

    // ---------------------------------------------------------------- register

    public RegisterPage register(String accountGuid, int limit) {
        return register(accountGuid, null, limit);
    }

    /**
     * One page of the account's splits, newest first, with running balances; future-dated rows included.
     *
     * @param after the previous page's {@link RegisterPage#next()}, or null for the first page
     */
    public RegisterPage register(String accountGuid, RegisterCursor after, int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be positive: " + limit);
        }
        Accounts accounts = Accounts.load(jdbc);
        Accounts.Row account = accounts.get(accountGuid);

        record Line(String splitGuid, String txGuid, LocalDateTime postDate, LocalDateTime enterDate,
                    String description, String currencyGuid, GncNumeric amount) {
        }
        JdbcClient.StatementSpec query = jdbc.sql("SELECT s.guid AS split_guid, t.guid AS tx_guid, "
                        + POST_DATE + " AS post_date, " + ENTER_DATE + " AS enter_date, "
                        + "t.description, t.currency_guid, s.quantity_num, s.quantity_denom "
                        + "FROM splits s JOIN transactions t ON t.guid = s.tx_guid "
                        + "WHERE s.account_guid = :account "
                        + (after == null ? "" : "AND (" + SORT_KEY + ") < (:post, :enter, :split) ")
                        + "ORDER BY " + NEWEST_FIRST + " LIMIT :limit")
                .param("account", accountGuid)
                .param("limit", limit + 1);
        if (after != null) {
            query = query.param("post", after.postDate()).param("enter", after.enterDate()).param("split", after.splitGuid());
        }
        List<Line> lines = query.query((rs, _) -> new Line(
                        rs.getString("split_guid"), rs.getString("tx_guid"),
                        rs.getObject("post_date", LocalDateTime.class), rs.getObject("enter_date", LocalDateTime.class),
                        rs.getString("description"), rs.getString("currency_guid"),
                        new GncNumeric(rs.getLong("quantity_num"), rs.getLong("quantity_denom"))))
                .list();
        if (lines.isEmpty()) {
            return new RegisterPage(List.of(), Optional.empty());
        }
        boolean more = lines.size() > limit;
        List<Line> page = more ? lines.subList(0, limit) : lines;

        Line newest = page.getFirst();
        GncNumeric balance = balanceOf(accountGuid, account.commodity(),
                "(" + SORT_KEY + ") <= (:post, :enter, :split)",
                Map.of("post", newest.postDate(), "enter", newest.enterDate(), "split", newest.splitGuid()));

        List<String> txGuids = page.stream().map(Line::txGuid).distinct().toList();
        Map<String, List<SplitRow>> splitsByTx = jdbc.sql("SELECT " + SplitRow.COLUMNS
                        + " FROM splits s WHERE s.tx_guid IN (:txs) ORDER BY s.guid")
                .param("txs", txGuids)
                .query(SplitRow.MAPPER).list().stream()
                .collect(Collectors.groupingBy(SplitRow::txGuid));
        Map<String, String> notes = notesOf(txGuids);

        List<RegisterRow> rows = new ArrayList<>();
        for (Line line : page) {
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

    private Map<String, String> notesOf(List<String> txGuids) {
        Map<String, String> notes = new HashMap<>();
        jdbc.sql("SELECT obj_guid, string_val FROM slots WHERE name = 'notes' AND obj_guid IN (:txs)")
                .param("txs", txGuids)
                .query(rs -> {
                    notes.put(rs.getString(1), rs.getString(2) == null ? "" : rs.getString(2));
                });
        return notes;
    }

    /**
     * Why the app may not edit or delete this transaction, or empty when it may (ADR-0008): exactly two
     * splits, none reconciled, both in transferable accounts of the transaction's currency, moving money.
     */
    static Optional<String> whyNotEditable(Accounts accounts, String currencyGuid, List<SplitRow> splits) {
        if (splits.size() != 2) {
            return Optional.of("it has " + splits.size() + " splits");
        }
        for (SplitRow split : splits) {
            if (split.reconciled()) {
                return Optional.of("a split is reconciled");
            }
            Optional<String> account = accounts.whyNotTransferable(split.accountGuid());
            if (account.isPresent()) {
                return account;
            }
            if (!accounts.get(split.accountGuid()).commodity().guid().equals(currencyGuid)
                    || split.value().compareTo(split.quantity()) != 0) {
                return Optional.of("it mixes commodities");
            }
            if (split.value().isZero()) {
                return Optional.of("it moves nothing");
            }
        }
        return Optional.empty();
    }

    /** The edit form's starting point, for a transaction the app may edit. */
    public Optional<TransferDraft> draft(String txGuid) {
        record Head(String currencyGuid, LocalDateTime postDate, String description) {
        }
        Optional<Head> head = jdbc.sql("SELECT currency_guid, post_date, description FROM transactions WHERE guid = :tx")
                .param("tx", txGuid)
                .query((rs, _) -> new Head(rs.getString(1), rs.getObject(2, LocalDateTime.class), rs.getString(3)))
                .optional();
        if (head.isEmpty()) {
            return Optional.empty();
        }
        List<SplitRow> splits = splitsOf(txGuid, false);
        if (whyNotEditable(Accounts.load(jdbc), head.get().currencyGuid(), splits).isPresent()) {
            return Optional.empty();
        }
        SplitRow in = splits.get(0).value().signum() > 0 ? splits.get(0) : splits.get(1);
        SplitRow out = in == splits.get(0) ? splits.get(1) : splits.get(0);
        TaggedDescription description = TaggedDescription.parse(head.get().description());
        return Optional.of(new TransferDraft(head.get().postDate().toLocalDate(), description.tags(), description.text(),
                in.value(), out.accountGuid(), in.accountGuid(), notesOf(List.of(txGuid)).getOrDefault(txGuid, "")));
    }

    private List<SplitRow> splitsOf(String txGuid, boolean forUpdate) {
        return jdbc.sql("SELECT " + SplitRow.COLUMNS + " FROM splits s WHERE s.tx_guid = :tx ORDER BY s.guid"
                        + (forUpdate ? " FOR UPDATE" : ""))
                .param("tx", txGuid)
                .query(SplitRow.MAPPER)
                .list();
    }

    // ---------------------------------------------------------------- suggestions

    /** Distinct description texts (tags stripped) containing {@code text}, most recent first. */
    public List<String> suggestDescriptions(String text) {
        String pattern = "%" + likeEscape(text.strip()) + "%";
        return jdbc.sql("SELECT d.text FROM (SELECT " + TEXT_AFTER_TAGS + " AS text, "
                        + "MAX(t.post_date) AS last_post, MAX(t.enter_date) AS last_enter "
                        + "FROM transactions t WHERE t.description ILIKE :pattern GROUP BY 1) d "
                        + "WHERE d.text ILIKE :pattern AND d.text <> '' "
                        + "ORDER BY d.last_post DESC, d.last_enter DESC LIMIT " + SUGGESTIONS)
                .param("pattern", pattern)
                .query(String.class)
                .list();
    }

    /** Tags, accounts and amount of the latest two-split transaction whose text (tags stripped) is {@code text}. */
    public Optional<QuickFill> quickFill(String text) {
        record Match(String guid, String description) {
        }
        Optional<Match> match = jdbc.sql("SELECT t.guid, t.description FROM transactions t "
                        + "WHERE " + TEXT_AFTER_TAGS + " = :text "
                        + "AND (SELECT COUNT(*) FROM splits s WHERE s.tx_guid = t.guid) = 2 "
                        + "ORDER BY t.post_date DESC, t.enter_date DESC LIMIT 1")
                .param("text", text.strip())
                .query((rs, _) -> new Match(rs.getString(1), rs.getString(2)))
                .optional();
        return match.map(m -> {
            List<SplitRow> splits = splitsOf(m.guid(), false);
            SplitRow in = splits.get(0).value().signum() > 0 ? splits.get(0) : splits.get(1);
            SplitRow out = in == splits.get(0) ? splits.get(1) : splits.get(0);
            TaggedDescription description = TaggedDescription.parse(m.description());
            return new QuickFill(description.tags(), description.text(), in.value(), out.accountGuid(), in.accountGuid());
        });
    }

    /** Distinct tags starting with {@code prefix}, most recently used first. */
    public List<String> suggestTags(String prefix) {
        return jdbc.sql("""
                        SELECT d.tag FROM (
                            SELECT btrim(m[1]) AS tag, MAX(t.post_date) AS last_post
                            FROM transactions t
                            CROSS JOIN LATERAL regexp_matches(
                                (regexp_match(t.description, '^((\\s*\\[[^][]+\\])+)'))[1], '\\[([^][]+)\\]', 'g') AS m
                            WHERE t.description ~ '^\\s*\\['
                            GROUP BY 1) d
                        WHERE d.tag ILIKE :pattern AND d.tag <> ''
                        ORDER BY d.last_post DESC, d.tag
                        LIMIT :limit""")
                .param("pattern", likeEscape(prefix.strip()) + "%")
                .param("limit", SUGGESTIONS)
                .query(String.class)
                .list();
    }

    /**
     * Accounts a transfer may use whose path contains every word of {@code query}, case-insensitively.
     * Accounts used in the last 90 days come first, by number of uses, then by last use.
     */
    public List<AccountChoice> suggestAccounts(String query) {
        Accounts accounts = Accounts.load(jdbc);
        record Usage(long uses, LocalDateTime lastUsed) {
        }
        Map<String, Usage> usage = new HashMap<>();
        jdbc.sql("""
                        SELECT s.account_guid, COUNT(*) AS uses, MAX(t.post_date) AS last_used
                        FROM splits s JOIN transactions t ON t.guid = s.tx_guid
                        WHERE t.post_date >= :since
                        GROUP BY s.account_guid""")
                .param("since", today().minusDays(ACCOUNT_USAGE_DAYS).atStartOfDay())
                .query(rs -> {
                    usage.put(rs.getString(1), new Usage(rs.getLong(2), rs.getObject(3, LocalDateTime.class)));
                });
        Usage unused = new Usage(0, LocalDateTime.MIN);
        List<String> words = Arrays.stream(query.strip().toLowerCase(Locale.ROOT).split("\\s+"))
                .filter(w -> !w.isEmpty()).toList();
        record Candidate(Accounts.Row row, String path, Usage usage) {
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

    private static String likeEscape(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    // ---------------------------------------------------------------- writes

    /**
     * Adds the transfer as a new transaction.
     *
     * @return the new transaction's GUID
     * @throws WriteRefusedException if the book is locked, the schema unknown, or the draft not allowed
     */
    public String insert(TransferDraft draft) {
        return write(accounts -> {
            Commodity currency = checkTransfer(accounts, draft);
            String guid = BookWriter.newGuid();
            writer.insert(guid, draft, currency, LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)
                    .truncatedTo(ChronoUnit.SECONDS));
            return guid;
        });
    }

    /** Replaces an editable transaction's date, description, notes, accounts and amount. */
    public void update(String txGuid, TransferDraft draft) {
        write(accounts -> {
            List<SplitRow> splits = lockEditable(accounts, txGuid);
            writer.update(txGuid, splits, draft, checkTransfer(accounts, draft));
            return txGuid;
        });
    }

    /** Deletes an editable transaction with its splits and slots. */
    public void delete(String txGuid) {
        write(accounts -> {
            writer.delete(txGuid, lockEditable(accounts, txGuid));
            return txGuid;
        });
    }

    /**
     * Runs a write in one DB transaction behind the lock and schema checks. {@code LOCK TABLE … IN SHARE MODE}
     * makes a desktop that opens the book meanwhile wait for the commit instead of slipping in after the check.
     */
    private <T> T write(Function<Accounts, T> work) {
        return transactions.execute(_ -> {
            jdbc.sql("LOCK TABLE gnclock IN SHARE MODE").update();
            Optional<LockHolder> holder = jdbc.sql("SELECT hostname, pid FROM gnclock").query(LOCK_MAPPER).list().stream().findFirst();
            if (holder.isPresent()) {
                throw new WriteRefusedException(Reason.LOCKED,
                        "Locked by " + holder.get().hostname() + " (PID " + holder.get().pid() + ")");
            }
            SchemaStatus schema = schemaStatus();
            if (!schema.known()) {
                throw new WriteRefusedException(Reason.UNKNOWN_SCHEMA,
                        "Unknown schema, read-only: " + String.join("; ", schema.differences()));
            }
            return work.apply(Accounts.load(jdbc));
        });
    }

    private List<SplitRow> lockEditable(Accounts accounts, String txGuid) {
        String currency = jdbc.sql("SELECT currency_guid FROM transactions WHERE guid = :tx FOR UPDATE")
                .param("tx", txGuid)
                .query(String.class)
                .optional()
                .orElseThrow(() -> new WriteRefusedException(Reason.NOT_FOUND, "no transaction " + txGuid));
        List<SplitRow> splits = splitsOf(txGuid, true);
        Optional<String> why = whyNotEditable(accounts, currency, splits);
        if (why.isPresent()) {
            throw new WriteRefusedException(Reason.NOT_EDITABLE, "this transaction is left to desktop: " + why.get());
        }
        return splits;
    }

    private static Commodity checkTransfer(Accounts accounts, TransferDraft draft) {
        Set<String> guids = new LinkedHashSet<>(List.of(draft.fromAccountGuid(), draft.toAccountGuid()));
        for (String guid : guids) {
            Optional<String> why = accounts.whyNotTransferable(guid);
            if (why.isPresent()) {
                throw new WriteRefusedException(Reason.ACCOUNT_NOT_ALLOWED, why.get());
            }
        }
        Commodity from = accounts.get(draft.fromAccountGuid()).commodity();
        Commodity to = accounts.get(draft.toAccountGuid()).commodity();
        if (!from.guid().equals(to.guid())) {
            throw new WriteRefusedException(Reason.MIXED_COMMODITIES,
                    "from is in " + from.mnemonic() + " but to is in " + to.mnemonic());
        }
        try {
            draft.amount().toFraction(from.fraction());
        } catch (ArithmeticException e) {
            throw new WriteRefusedException(Reason.AMOUNT_TOO_PRECISE,
                    draft.amount().num() + "/" + draft.amount().denom() + " is finer than " + from.mnemonic() + " allows");
        }
        return from;
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }
}
