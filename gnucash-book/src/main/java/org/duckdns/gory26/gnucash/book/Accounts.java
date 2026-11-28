package org.duckdns.gory26.gnucash.book;

import java.util.*;
import java.util.stream.Collectors;

/** A snapshot of the whole account tree; small enough to load per request. */
final class Accounts {

    record Row(String guid,
               String name,
               AccountType type,
               String parentGuid,
               String code,
               boolean hidden,
               boolean placeholder,
               Commodity commodity,
               long scu) {
    }

    private static final Comparator<Row> DESKTOP_ORDER =
            Comparator.comparing((Row r) -> r.code() == null ? "" : r.code()).thenComparing(Row::name);

    private final String rootGuid;
    private final Map<String, Row> byGuid;
    private final Map<String, List<Row>> children;

    private Accounts(String rootGuid, List<Row> rows) {
        this.rootGuid = rootGuid;
        this.byGuid = rows.stream().collect(Collectors.toMap(Row::guid, r -> r));
        Map<String, List<Row>> kids = new HashMap<>();
        for(Row r : rows) {
            if(r.parentGuid() != null) {
                kids.computeIfAbsent(r.parentGuid(), _ -> new ArrayList<>()).add(r);
            }
        }
        kids.values().forEach(list -> list.sort(DESKTOP_ORDER));
        this.children = kids;
    }

    static Accounts load(Jdbc jdbc) {
        String root = jdbc.single("SELECT root_account_guid FROM books LIMIT 1", rs -> rs.getString(1));
        List<Row> rows = jdbc.list("""
                        SELECT a.guid, a.name, a.account_type, a.parent_guid, a.code, a.hidden, a.placeholder,
                               a.commodity_scu, c.guid AS c_guid, c.namespace, c.mnemonic, c.fraction
                        FROM accounts a LEFT JOIN commodities c ON c.guid = a.commodity_guid""",
                rs -> new Row(
                        rs.getString("guid"),
                        rs.getString("name"),
                        AccountType.valueOf(rs.getString("account_type")),
                        rs.getString("parent_guid"),
                        rs.getString("code"),
                        rs.getInt("hidden") != 0,
                        rs.getInt("placeholder") != 0,
                        rs.getString("c_guid") == null ? null : new Commodity(
                                rs.getString("c_guid"), rs.getString("namespace"),
                                rs.getString("mnemonic"), rs.getLong("fraction")),
                        rs.getLong("commodity_scu")));
        return new Accounts(root, rows);
    }

    String rootGuid() {
        return rootGuid;
    }

    Row get(String guid) {
        Row row = byGuid.get(guid);
        if(row == null) {
            throw new IllegalArgumentException("no such account: " + guid);
        }
        return row;
    }

    List<Row> children(String guid) {
        return children.getOrDefault(guid, List.of());
    }

    /** The account and all its descendants, hidden ones included. */
    List<Row> subtree(String guid) {
        List<Row> out = new ArrayList<>();
        collect(get(guid), out);
        return out;
    }

    private void collect(Row row, List<Row> out) {
        out.add(row);
        children(row.guid()).forEach(child -> collect(child, out));
    }

    /** Below the root, e.g. {@code Expenses:Food:Groceries}. */
    String path(String guid) {
        List<String> names = new ArrayList<>();
        for(Row r = byGuid.get(guid); r != null && !r.guid().equals(rootGuid); r = byGuid.get(r.parentGuid())) {
            names.addFirst(r.name());
        }
        return String.join(":", names);
    }

    /** Hidden itself or through an ancestor. */
    boolean effectivelyHidden(String guid) {
        for(Row r = byGuid.get(guid); r != null; r = byGuid.get(r.parentGuid())) {
            if(r.hidden()) {
                return true;
            }
        }
        return false;
    }

    /** Why a transfer may not use this account, or empty when it may. */
    Optional<String> whyNotTransferable(String guid) {
        Row row = byGuid.get(guid);
        if(row == null || row.guid().equals(rootGuid) || row.type() == AccountType.ROOT) {
            return Optional.of("unknown account " + guid);
        }
        String path = path(guid);
        if(row.placeholder()) {
            return Optional.of(path + " is a placeholder");
        }
        if(effectivelyHidden(guid)) {
            return Optional.of(path + " is hidden");
        }
        if(row.type() == AccountType.TRADING) {
            return Optional.of(path + " is a trading account");
        }
        if(row.commodity() == null || !row.commodity().isCurrency()) {
            return Optional.of(path + " does not hold a currency");
        }
        return Optional.empty();
    }

    /** Whether the account is the book's root or below it, rather than in the scheduled-transaction template tree. */
    boolean inBook(String guid) {
        for(Row r = byGuid.get(guid); r != null; r = byGuid.get(r.parentGuid())) {
            if(r.guid().equals(rootGuid)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The accounts in the book whose {@link #path} is {@code path}, hidden ones included. More than one only when
     * siblings share a name, which desktop's account dialog does not allow.
     */
    List<Row> byPath(String path) {
        return byGuid.values().stream()
                .filter(r -> !r.guid().equals(rootGuid) && inBook(r.guid()) && path(r.guid()).equals(path))
                .toList();
    }

    /** Why a new transaction may not have a split in this account, or empty when it may (ADR-0015). */
    Optional<String> whyNotPostable(String guid) {
        Row row = byGuid.get(guid);
        if(row == null || row.type() == AccountType.ROOT || !inBook(guid)) {
            return Optional.of("unknown account " + guid);
        }
        if(row.placeholder()) {
            return Optional.of(path(guid) + " is a placeholder");
        }
        if(row.commodity() == null) {
            return Optional.of(path(guid) + " has no commodity");
        }
        return Optional.empty();
    }

    List<Row> all() {
        return List.copyOf(byGuid.values());
    }
}
