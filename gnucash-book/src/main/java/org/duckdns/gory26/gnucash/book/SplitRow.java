package org.duckdns.gory26.gnucash.book;

import org.springframework.jdbc.core.RowMapper;

/** A {@code splits} row as the adapter needs it. */
record SplitRow(String guid,
                String txGuid,
                String accountGuid,
                String reconcileState,
                GncNumeric value,
                GncNumeric quantity) {

    static final String COLUMNS = "s.guid, s.tx_guid, s.account_guid, s.reconcile_state, "
            + "s.value_num, s.value_denom, s.quantity_num, s.quantity_denom";

    static final RowMapper<SplitRow> MAPPER = (rs, _) -> new SplitRow(
            rs.getString("guid"), rs.getString("tx_guid"), rs.getString("account_guid"),
            rs.getString("reconcile_state"),
            new GncNumeric(rs.getLong("value_num"), rs.getLong("value_denom")),
            new GncNumeric(rs.getLong("quantity_num"), rs.getLong("quantity_denom")));

    /** Reconciled ({@code y}) or frozen ({@code f}); cleared ({@code c}) still counts as editable. */
    boolean reconciled() {
        return "y".equals(reconcileState) || "f".equals(reconcileState);
    }
}
