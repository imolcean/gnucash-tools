package org.duckdns.gory26.gnucash.book;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

/** Reads whole transactions, splits and string slots included, exactly as stored (ADR-0015). */
final class TransactionReader {

    /** The posted date as desktop reads it: the {@code date-posted} slot, else the UTC date of {@code post_date}. */
    private static final String DATE_POSTED = "COALESCE(dp.gdate_val, CAST(t.post_date AS date))";

    private static final String HEADS = "SELECT t.guid, t.currency_guid, " + DATE_POSTED + " AS date_posted, "
            + "t.enter_date, t.description FROM transactions t "
            + "LEFT JOIN slots dp ON dp.obj_guid = t.guid AND dp.name = 'date-posted' ";

    private static final int SLOT_STRING = 4;

    private record Head(String guid,
                        String currencyGuid,
                        LocalDate datePosted,
                        Instant entered,
                        String description) {
    }

    private static final Jdbc.RowMapper<Head> HEAD = rs -> new Head(
            rs.getString("guid"), rs.getString("currency_guid"),
            rs.getObject("date_posted", LocalDate.class),
            utc(rs.getObject("enter_date", LocalDateTime.class)),
            Objects.requireNonNullElse(rs.getString("description"), ""));

    private final Jdbc jdbc;

    TransactionReader(Jdbc jdbc) {
        this.jdbc = jdbc;
    }

    Optional<StoredTransaction> byGuid(String guid) {
        return complete(jdbc.list(HEADS + "WHERE t.guid = ?", HEAD, guid)).stream().findFirst();
    }

    /** Posted on a day in {@code [from, to]}, by date, entry time and GUID. */
    List<StoredTransaction> postedBetween(LocalDate from, LocalDate to) {
        return complete(jdbc.list(HEADS + "WHERE " + DATE_POSTED + " BETWEEN ? AND ? "
                + "ORDER BY " + DATE_POSTED + ", t.enter_date, t.guid", HEAD, from, to));
    }

    private List<StoredTransaction> complete(List<Head> heads) {
        if(heads.isEmpty()) {
            return List.of();
        }
        List<String> txGuids = heads.stream().map(Head::guid).toList();

        record Row(String txGuid,
                   String guid,
                   String accountGuid,
                   GncNumeric value,
                   GncNumeric quantity,
                   String memo,
                   ReconcileState state,
                   Instant reconcileDate) {
        }
        Map<String, List<Row>> splits = jdbc.list("""
                                SELECT s.tx_guid, s.guid, s.account_guid, s.value_num, s.value_denom,
                                       s.quantity_num, s.quantity_denom, s.memo, s.reconcile_state, s.reconcile_date
                                FROM splits s WHERE s.tx_guid = ANY(?) ORDER BY s.guid""",
                        rs -> new Row(rs.getString("tx_guid"), rs.getString("guid"), rs.getString("account_guid"),
                                new GncNumeric(rs.getLong("value_num"), rs.getLong("value_denom")),
                                new GncNumeric(rs.getLong("quantity_num"), rs.getLong("quantity_denom")),
                                Objects.requireNonNullElse(rs.getString("memo"), ""),
                                ReconcileState.of(rs.getString("reconcile_state")),
                                reconcileDate(rs.getObject("reconcile_date", LocalDateTime.class))),
                        txGuids)
                .stream()
                .collect(Collectors.groupingBy(Row::txGuid, LinkedHashMap::new, Collectors.toList()));

        List<String> owners = new ArrayList<>(txGuids);
        splits.values().forEach(rows -> rows.forEach(r -> owners.add(r.guid())));
        Map<String, Map<String, String>> slots = new HashMap<>();
        jdbc.list("SELECT obj_guid, name, string_val FROM slots WHERE slot_type = " + SLOT_STRING
                                + " AND obj_guid = ANY(?)",
                        rs -> new String[]{rs.getString(1), rs.getString(2), rs.getString(3)}, owners)
                .forEach(s -> slots.computeIfAbsent(s[0], _ -> new TreeMap<>())
                        .put(s[1], Objects.requireNonNullElse(s[2], "")));

        return heads.stream().map(head -> {
            Map<String, String> own = new TreeMap<>(slots.getOrDefault(head.guid(), Map.of()));
            String notes = own.remove("notes");
            return new StoredTransaction(head.guid(), head.currencyGuid(), head.datePosted(), head.entered(),
                    head.description(), notes == null ? "" : notes, own,
                    splits.getOrDefault(head.guid(), List.of()).stream()
                            .map(r -> new StoredSplit(r.guid(), r.accountGuid(), r.value(), r.quantity(), r.memo(),
                                    r.state(), r.reconcileDate(), slots.getOrDefault(r.guid(), Map.of())))
                            .toList());
        }).toList();
    }

    private static Instant reconcileDate(LocalDateTime stored) {
        return stored == null || stored.equals(BookWriter.EPOCH) ? null : utc(stored);
    }

    private static Instant utc(LocalDateTime stored) {
        return stored == null ? null : stored.toInstant(ZoneOffset.UTC);
    }
}
