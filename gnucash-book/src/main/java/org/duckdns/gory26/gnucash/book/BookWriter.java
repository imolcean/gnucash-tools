package org.duckdns.gory26.gnucash.book;

import org.springframework.jdbc.core.simple.JdbcClient;

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

    private final JdbcClient jdbc;

    BookWriter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    static String newGuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    void insert(String txGuid, TransferDraft draft, Commodity currency, LocalDateTime enterDate) {
        Transaction tx = draft.toTransaction();
        jdbc.sql("""
                        INSERT INTO transactions (guid, currency_guid, num, post_date, enter_date, description)
                        VALUES (:guid, :currency, '', :post, :enter, :description)""")
                .param("guid", txGuid)
                .param("currency", currency.guid())
                .param("post", tx.postDate().atTime(NEUTRAL_TIME))
                .param("enter", enterDate)
                .param("description", tx.description())
                .update();
        for(Split split : tx.splits()) {
            insertSplit(newGuid(), txGuid, split, currency.fraction());
        }
        insertDatePosted(txGuid, tx.postDate());
        insertNotes(txGuid, tx.notes());
    }

    /** Rewrites a two-split transfer in place, keeping its GUIDs, enter date and reconcile states. */
    void update(String txGuid, List<SplitRow> existing, TransferDraft draft, Commodity currency) {
        Transaction tx = draft.toTransaction();
        jdbc.sql("""
                        UPDATE transactions SET currency_guid = :currency, post_date = :post, description = :description
                        WHERE guid = :guid""")
                .param("guid", txGuid)
                .param("currency", currency.guid())
                .param("post", tx.postDate().atTime(NEUTRAL_TIME))
                .param("description", tx.description())
                .update();

        // toTransaction() lists the incoming (+) split first; match it to the old incoming split
        SplitRow oldIn = existing.get(0).value().signum() > 0 ? existing.get(0) : existing.get(1);
        SplitRow oldOut = oldIn == existing.get(0) ? existing.get(1) : existing.get(0);
        updateSplit(oldIn.guid(), tx.splits().get(0), currency.fraction());
        updateSplit(oldOut.guid(), tx.splits().get(1), currency.fraction());

        int dated = jdbc.sql("UPDATE slots SET gdate_val = :date WHERE obj_guid = :tx AND name = 'date-posted'")
                .param("date", tx.postDate())
                .param("tx", txGuid)
                .update();
        if(dated == 0) {
            insertDatePosted(txGuid, tx.postDate());
        }
        jdbc.sql("DELETE FROM slots WHERE obj_guid = :tx AND name = 'notes'").param("tx", txGuid).update();
        insertNotes(txGuid, tx.notes());
    }

    /** Removes the transaction, its splits, and the slots of both. */
    void delete(String txGuid, List<SplitRow> splits) {
        List<String> owners = new ArrayList<>();
        owners.add(txGuid);
        splits.forEach(s -> owners.add(s.guid()));
        jdbc.sql("DELETE FROM slots WHERE obj_guid IN (:owners)").param("owners", owners).update();
        jdbc.sql("DELETE FROM splits WHERE tx_guid = :tx").param("tx", txGuid).update();
        jdbc.sql("DELETE FROM transactions WHERE guid = :tx").param("tx", txGuid).update();
    }

    private void insertSplit(String guid, String txGuid, Split split, long fraction) {
        GncNumeric value = split.value().toFraction(fraction);
        GncNumeric quantity = split.quantity().toFraction(fraction);
        jdbc.sql("""
                        INSERT INTO splits (guid, tx_guid, account_guid, memo, action, reconcile_state, reconcile_date,
                                            value_num, value_denom, quantity_num, quantity_denom, lot_guid)
                        VALUES (:guid, :tx, :account, '', '', 'n', :epoch,
                                :valueNum, :valueDenom, :quantityNum, :quantityDenom, NULL)""")
                .param("guid", guid)
                .param("tx", txGuid)
                .param("account", split.accountGuid())
                .param("epoch", EPOCH)
                .param("valueNum", value.num())
                .param("valueDenom", value.denom())
                .param("quantityNum", quantity.num())
                .param("quantityDenom", quantity.denom())
                .update();
    }

    private void updateSplit(String guid, Split split, long fraction) {
        GncNumeric value = split.value().toFraction(fraction);
        GncNumeric quantity = split.quantity().toFraction(fraction);
        jdbc.sql("""
                        UPDATE splits SET account_guid = :account,
                                          value_num = :valueNum, value_denom = :valueDenom,
                                          quantity_num = :quantityNum, quantity_denom = :quantityDenom
                        WHERE guid = :guid""")
                .param("guid", guid)
                .param("account", split.accountGuid())
                .param("valueNum", value.num())
                .param("valueDenom", value.denom())
                .param("quantityNum", quantity.num())
                .param("quantityDenom", quantity.denom())
                .update();
    }

    private void insertDatePosted(String txGuid, LocalDate date) {
        jdbc.sql("""
                        INSERT INTO slots (obj_guid, name, slot_type, int64_val, string_val, double_val, timespec_val,
                                           guid_val, numeric_val_num, numeric_val_denom, gdate_val)
                        VALUES (:tx, 'date-posted', 10, 0, NULL, NULL, :epoch, NULL, 0, 1, :date)""")
                .param("tx", txGuid)
                .param("epoch", EPOCH)
                .param("date", date)
                .update();
    }

    private void insertNotes(String txGuid, String notes) {
        if(notes.isBlank()) {
            return;
        }
        jdbc.sql("""
                        INSERT INTO slots (obj_guid, name, slot_type, int64_val, string_val, double_val, timespec_val,
                                           guid_val, numeric_val_num, numeric_val_denom, gdate_val)
                        VALUES (:tx, 'notes', 4, 0, :notes, NULL, :epoch, NULL, 0, 1, NULL)""")
                .param("tx", txGuid)
                .param("notes", notes)
                .param("epoch", EPOCH)
                .update();
    }
}
