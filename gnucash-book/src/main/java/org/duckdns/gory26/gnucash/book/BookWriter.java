package org.duckdns.gory26.gnucash.book;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Writes {@code transactions}, {@code splits} and {@code slots} rows the way GnuCash desktop 5.16 does (ADR-0007).
 * Callers run it inside one DB transaction after the lock, schema and eligibility checks.
 */
final class BookWriter {

    /** Desktop stores every posted date at this UTC time of day, so it reads as the same date in every zone. */
    static final LocalTime NEUTRAL_TIME = LocalTime.of(10, 59);

    /** Desktop writes the epoch, not NULL, into unused timestamp columns. */
    static final LocalDateTime EPOCH = LocalDateTime.of(1970, 1, 1, 0, 0);

    private final Jdbc jdbc;

    BookWriter(Jdbc jdbc) {
        this.jdbc = jdbc;
    }

    static String newGuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    void insert(String txGuid, TransferDraft draft, Commodity currency, LocalDateTime enterDate) {
        Transaction tx = draft.toTransaction();
        insertTransactionRow(txGuid, currency.guid(), tx.postDate(), enterDate, tx.description());
        for(Split split : tx.splits()) {
            insertSplit(newGuid(), txGuid, split.accountGuid(),
                    split.value().toFraction(currency.fraction()), split.quantity().toFraction(currency.fraction()));
        }
        insertDatePosted(txGuid, tx.postDate());
        insertNotes(txGuid, tx.notes());
    }

    /** Rewrites a two-split transfer in place, keeping its GUIDs, enter date and reconcile states. */
    void update(String txGuid, List<SplitRow> existing, TransferDraft draft, Commodity currency) {
        Transaction tx = draft.toTransaction();
        jdbc.update("UPDATE transactions SET currency_guid = ?, post_date = ?, description = ? WHERE guid = ?",
                currency.guid(), tx.postDate().atTime(NEUTRAL_TIME), tx.description(), txGuid);

        // toTransaction() lists the incoming (+) split first; match it to the old incoming split
        SplitRow oldIn = existing.get(0).value().signum() > 0 ? existing.get(0) : existing.get(1);
        SplitRow oldOut = oldIn == existing.get(0) ? existing.get(1) : existing.get(0);
        updateSplit(oldIn.guid(), tx.splits().get(0), currency.fraction());
        updateSplit(oldOut.guid(), tx.splits().get(1), currency.fraction());

        int dated = jdbc.update("UPDATE slots SET gdate_val = ? WHERE obj_guid = ? AND name = 'date-posted'",
                tx.postDate(), txGuid);
        if(dated == 0) {
            insertDatePosted(txGuid, tx.postDate());
        }
        jdbc.update("DELETE FROM slots WHERE obj_guid = ? AND name = 'notes'", txGuid);
        insertNotes(txGuid, tx.notes());
    }

    /** Removes the transaction, its splits, and the slots of both. */
    void delete(String txGuid, List<SplitRow> splits) {
        List<String> owners = new ArrayList<>();
        owners.add(txGuid);
        splits.forEach(s -> owners.add(s.guid()));
        jdbc.update("DELETE FROM slots WHERE obj_guid = ANY(?)", owners);
        jdbc.update("DELETE FROM splits WHERE tx_guid = ?", txGuid);
        jdbc.update("DELETE FROM transactions WHERE guid = ?", txGuid);
    }

    private void insertTransactionRow(String guid, String currencyGuid, LocalDate postDate, LocalDateTime enterDate,
                                      String description) {
        jdbc.update("""
                        INSERT INTO transactions (guid, currency_guid, num, post_date, enter_date, description)
                        VALUES (?, ?, '', ?, ?, ?)""",
                guid, currencyGuid, postDate.atTime(NEUTRAL_TIME), enterDate, description);
    }

    private void insertSplit(String guid, String txGuid, String accountGuid, GncNumeric value, GncNumeric quantity) {
        jdbc.update("""
                        INSERT INTO splits (guid, tx_guid, account_guid, memo, action, reconcile_state, reconcile_date,
                                            value_num, value_denom, quantity_num, quantity_denom, lot_guid)
                        VALUES (?, ?, ?, '', '', 'n', ?, ?, ?, ?, ?, NULL)""",
                guid, txGuid, accountGuid, EPOCH, value.num(), value.denom(), quantity.num(), quantity.denom());
    }

    private void updateSplit(String guid, Split split, long fraction) {
        GncNumeric value = split.value().toFraction(fraction);
        GncNumeric quantity = split.quantity().toFraction(fraction);
        jdbc.update("""
                        UPDATE splits SET account_guid = ?, value_num = ?, value_denom = ?, quantity_num = ?, quantity_denom = ?
                        WHERE guid = ?""",
                split.accountGuid(), value.num(), value.denom(), quantity.num(), quantity.denom(), guid);
    }

    private void insertDatePosted(String txGuid, LocalDate date) {
        jdbc.update("""
                        INSERT INTO slots (obj_guid, name, slot_type, int64_val, string_val, double_val, timespec_val,
                                           guid_val, numeric_val_num, numeric_val_denom, gdate_val)
                        VALUES (?, 'date-posted', 10, 0, NULL, NULL, ?, NULL, 0, 1, ?)""",
                txGuid, EPOCH, date);
    }

    private void insertNotes(String txGuid, String notes) {
        if(!notes.isBlank()) {
            insertStringSlot(txGuid, "notes", notes);
        }
    }

    /** A {@code slot_type = 4} slot, shaped like desktop's {@code notes} slot. */
    private void insertStringSlot(String owner, String name, String value) {
        jdbc.update("""
                        INSERT INTO slots (obj_guid, name, slot_type, int64_val, string_val, double_val, timespec_val,
                                           guid_val, numeric_val_num, numeric_val_denom, gdate_val)
                        VALUES (?, ?, 4, 0, ?, NULL, ?, NULL, 0, 1, NULL)""",
                owner, name, value, EPOCH);
    }
}
