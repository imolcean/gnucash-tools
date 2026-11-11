package org.duckdns.gory26.gnucash.book;

import org.duckdns.gory26.gnucash.book.WriteRefusedException.Reason;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WritesTest {

    /** ADR-0007: the app's rows equal the rows desktop wrote for the same entry, minus GUIDs, enter date and slot ids. */
    @Nested
    class Golden {

        private final GnuCashBook book = SampleBook.unlocked(db);

        @Test
        void plainTwoSplitTransfer() {
            String desktop = txByDescription("Storage room");

            String app = book.insert(new TransferDraft(NOV_3, List.of(), "Storage room", eur("20.00"),
                    SampleBook.CASH, SampleBook.RENT, ""));

            assertThat(rows(app, null)).isEqualTo(rows(desktop, null));
        }

        @Test
        void withTagAndNotes() {
            String desktop = txByDescription("[Tag 1] Rewe again");

            String app = book.insert(new TransferDraft(LocalDate.of(2026, 11, 2), List.of("Tag 1"), "Rewe again",
                    eur("7.66"), SampleBook.CASH, SampleBook.GROCERIES, "Forgot something"));

            // desktop reconciled its Cash split afterwards
            assertThat(rows(app, SampleBook.CASH)).isEqualTo(rows(desktop, SampleBook.CASH));
        }

        @Test
        void futureDated() {
            String desktop = txByDescription("Concert tickets (maybe?)");

            String app = book.insert(new TransferDraft(LocalDate.of(2026, 11, 10), List.of(), "Concert tickets (maybe?)",
                    eur("200.00"), SampleBook.CASH, SampleBook.MISC, ""));

            assertThat(rows(app, null)).isEqualTo(rows(desktop, null));
        }

        @Test
        void enterDateIsNowInUtcAndGuidsAre32HexChars() {
            String app = book.insert(new TransferDraft(NOV_3, List.of(), "x", eur("1.00"), SampleBook.CASH, SampleBook.RENT, ""));

            assertThat(app).matches("[0-9a-f]{32}");
            assertThat(sql.sql("SELECT enter_date FROM transactions WHERE guid = :tx").param("tx", app)
                    .query(LocalDateTime.class).single()).isEqualTo(LocalDateTime.of(2026, 11, 4, 11, 0));
            assertThat(sql.sql("SELECT guid FROM splits WHERE tx_guid = :tx").param("tx", app).query(String.class).list())
                    .hasSize(2).allSatisfy(g -> assertThat(g).matches("[0-9a-f]{32}"));
        }

        @Test
        void anAmountWithAFinerDenominatorIsStoredOverTheCommodityFraction() {
            String app = book.insert(new TransferDraft(NOV_3, List.of(), "x", new GncNumeric(12340, 1000),
                    SampleBook.CASH, SampleBook.RENT, ""));

            assertThat(sql.sql("SELECT value_num, value_denom, quantity_num, quantity_denom FROM splits WHERE tx_guid = :tx AND account_guid = :rent")
                    .param("tx", app).param("rent", SampleBook.RENT).query().singleRow())
                    .containsValues(1234L, 100L).doesNotContainValue(12340L);
        }
    }

    @Nested
    class EditAndDelete {

        private final GnuCashBook book = SampleBook.unlocked(db);

        @Test
        void draftOfAnEditableTransactionRoundTripsTheForm() {
            String concert = txByDescription("Concert tickets (maybe?)");

            assertThat(book.draft(concert)).contains(new TransferDraft(LocalDate.of(2026, 11, 10), List.of(),
                    "Concert tickets (maybe?)", eur("200.00"), SampleBook.CASH, SampleBook.MISC, ""));
            assertThat(book.draft(txByDescription("Salary"))).isEmpty();
            assertThat(book.draft("0".repeat(32))).isEmpty();
        }

        @Test
        void updateRewritesInPlaceKeepingGuidsAndEnterDate() {
            String tx = book.insert(new TransferDraft(NOV_3, List.of("Old"), "Bakery", eur("3.00"),
                    SampleBook.CASH, SampleBook.GROCERIES, "first"));
            List<String> splitGuids = splitGuids(tx);

            book.update(tx, new TransferDraft(LocalDate.of(2026, 11, 1), List.of("New", "Tag 2"), "Bakery & café",
                    eur("4.50"), SampleBook.CHECKING, SampleBook.MISC, "second"));

            assertThat(splitGuids(tx)).isEqualTo(splitGuids);
            assertThat(book.draft(tx)).contains(new TransferDraft(LocalDate.of(2026, 11, 1), List.of("New", "Tag 2"),
                    "Bakery & café", eur("4.50"), SampleBook.CHECKING, SampleBook.MISC, "second"));
            assertThat(sql.sql("SELECT post_date, enter_date FROM transactions WHERE guid = :tx").param("tx", tx).query().singleRow())
                    .containsEntry("post_date", java.sql.Timestamp.valueOf("2026-11-01 10:59:00"))
                    .containsEntry("enter_date", java.sql.Timestamp.valueOf("2026-11-04 11:00:00"));
            assertThat(slotNames(tx)).containsExactlyInAnyOrder("date-posted", "notes");
            assertThat(sql.sql("SELECT gdate_val FROM slots WHERE obj_guid = :tx AND name = 'date-posted'")
                    .param("tx", tx).query(LocalDate.class).single()).isEqualTo(LocalDate.of(2026, 11, 1));
        }

        @Test
        void clearingNotesRemovesTheNotesSlot() {
            String tx = book.insert(new TransferDraft(NOV_3, List.of(), "x", eur("1.00"), SampleBook.CASH, SampleBook.RENT, "note"));

            book.update(tx, new TransferDraft(NOV_3, List.of(), "x", eur("1.00"), SampleBook.CASH, SampleBook.RENT, ""));

            assertThat(slotNames(tx)).containsExactly("date-posted");
        }

        @Test
        void deleteRemovesTheTransactionItsSplitsAndAllTheirSlots() {
            int slotsBefore = count("slots");
            String tx = book.insert(new TransferDraft(NOV_3, List.of(), "x", eur("1.00"), SampleBook.CASH, SampleBook.RENT, "note"));
            List<String> splits = splitGuids(tx);
            sql.sql("INSERT INTO slots (obj_guid, name, slot_type, int64_val) VALUES (:split, 'split-slot', 1, 7)")
                    .param("split", splits.getFirst()).update();

            book.delete(tx);

            assertThat(count("slots")).isEqualTo(slotsBefore);
            assertThat(sql.sql("SELECT COUNT(*) FROM splits WHERE tx_guid = :tx").param("tx", tx).query(Integer.class).single()).isZero();
            assertThat(sql.sql("SELECT COUNT(*) FROM transactions WHERE guid = :tx").param("tx", tx).query(Integer.class).single()).isZero();
        }

        @Test
        void aDesktopWrittenTransferCanBeDeletedAndBalancesFollow() {
            book.delete(txByDescription("Storage room"));

            AccountNode rent = AccountTreeTest.find(book.accountTree(LocalDate.of(2026, 11, 4)), "Expenses:Rent");
            assertThat(rent.displayBalance().toBigDecimal()).isEqualTo("500.00");
        }

        @Test
        void multiSplitReconciledAndMissingTransactionsAreLeftToDesktop() {
            TransferDraft any = new TransferDraft(NOV_3, List.of(), "x", eur("1.00"), SampleBook.CASH, SampleBook.RENT, "");

            assertRefused(() -> book.delete(txByDescription("Salary")), Reason.NOT_EDITABLE);
            assertRefused(() -> book.update(txByDescription("Sell USD @ 1.3"), any), Reason.NOT_EDITABLE);
            assertRefused(() -> book.delete(txByDescription("[Tag 1] Rewe again")), Reason.NOT_EDITABLE);
            assertRefused(() -> book.delete("0".repeat(32)), Reason.NOT_FOUND);
            assertThat(count("transactions")).isEqualTo(8);
        }
    }

    @Nested
    class Guards {

        private final TransferDraft lunch = new TransferDraft(NOV_3, List.of(), "Lunch", eur("9.90"),
                SampleBook.CASH, SampleBook.GROCERIES, "");

        @Test
        void writesAreRefusedWhileDesktopHoldsTheLock() {
            GnuCashBook book = new GnuCashBook(db, SampleBook.TODAY);

            assertThatThrownBy(() -> book.insert(lunch))
                    .isInstanceOf(WriteRefusedException.class)
                    .hasMessage("Locked by Leopard (PID 8300)")
                    .extracting(e -> ((WriteRefusedException) e).reason()).isEqualTo(Reason.LOCKED);
            assertRefused(() -> book.delete(txByDescription("Storage room")), Reason.LOCKED);
            assertThat(count("transactions")).isEqualTo(8);

            book.clearLock();
            book.insert(lunch);
            assertThat(count("transactions")).isEqualTo(9);
        }

        @Test
        void writesAreRefusedOnAnUnknownSchema() {
            GnuCashBook book = SampleBook.unlocked(db);
            sql.sql("UPDATE versions SET table_version = 6 WHERE table_name = 'splits'").update();

            assertRefused(() -> book.insert(lunch), Reason.UNKNOWN_SCHEMA);
            assertThat(count("transactions")).isEqualTo(8);
        }

        @Test
        void placeholderHiddenTradingAndUnknownAccountsAreRefused() {
            GnuCashBook book = SampleBook.unlocked(db);

            for(String account : List.of(SampleBook.FOOD, SampleBook.VISIBLE_NESTED, SampleBook.HIDDEN,
                    SampleBook.TRADING_EUR, "0".repeat(32))) {
                assertRefused(() -> book.insert(new TransferDraft(NOV_3, List.of(), "x", eur("1.00"),
                        SampleBook.CASH, account, "")), Reason.ACCOUNT_NOT_ALLOWED);
            }
            assertThat(count("transactions")).isEqualTo(8);
        }

        @Test
        void transfersBetweenCommoditiesAreRefused() {
            GnuCashBook book = SampleBook.unlocked(db);

            assertRefused(() -> book.insert(new TransferDraft(NOV_3, List.of(), "x", eur("1.00"),
                    SampleBook.CASH_USD, SampleBook.CASH, "")), Reason.MIXED_COMMODITIES);
        }

        @Test
        void anAmountFinerThanTheCommodityIsRefusedNotRounded() {
            GnuCashBook book = SampleBook.unlocked(db);

            assertRefused(() -> book.insert(new TransferDraft(NOV_3, List.of(), "x", new GncNumeric(1234, 1000),
                    SampleBook.CASH, SampleBook.RENT, "")), Reason.AMOUNT_TOO_PRECISE);
        }

        /** The grants planned for the compose {@code db-init} (M5) are enough for every read and write. */
        @Test
        void theLeastPrivilegeRoleCanDoEverythingTheAppNeeds() {
            String database = SampleBook.databaseOf(db);
            String role = "togo_" + database;
            SampleBook.grantAppRole(database, role, "togo");
            GnuCashBook book = new GnuCashBook(SampleBook.dataSource(database, role, "togo"), SampleBook.TODAY);

            assertThat(book.schemaStatus().known()).isTrue();
            assertThat(book.lock()).isPresent();
            book.clearLock();
            String tx = book.insert(lunch);
            book.update(tx, new TransferDraft(NOV_3, List.of("T"), "Lunch", eur("9.95"), SampleBook.CASH, SampleBook.GROCERIES, "n"));
            book.delete(tx);
            assertThat(book.accountTree()).isNotEmpty();
            assertThat(book.register(SampleBook.CASH, 2).rows()).hasSize(2);
            assertThat(book.suggestTags("")).isNotEmpty();
        }
    }
    private static final LocalDate NOV_3 = LocalDate.of(2026, 11, 3);
    private final DataSource db = SampleBook.freshCopy();
    private final JdbcClient sql = JdbcClient.create(db);

    // ---------------------------------------------------------------- helpers

    private static GncNumeric eur(String amount) {
        return GncNumeric.of(new BigDecimal(amount), 100);
    }

    private static void assertRefused(Runnable write, Reason reason) {
        assertThatThrownBy(write::run)
                .isInstanceOf(WriteRefusedException.class)
                .extracting(e -> ((WriteRefusedException) e).reason()).isEqualTo(reason);
    }

    private String txByDescription(String description) {
        return sql.sql("SELECT guid FROM transactions WHERE description = :d").param("d", description).query(String.class).single();
    }

    private List<String> splitGuids(String tx) {
        return sql.sql("SELECT guid FROM splits WHERE tx_guid = :tx ORDER BY guid").param("tx", tx).query(String.class).list();
    }

    private List<String> slotNames(String tx) {
        return sql.sql("SELECT name FROM slots WHERE obj_guid = :tx").param("tx", tx).query(String.class).list();
    }

    private int count(String table) {
        return sql.sql("SELECT COUNT(*) FROM " + table).query(Integer.class).single();
    }

    /**
     * Every column of the transaction's rows except GUIDs, {@code enter_date} and slot ids; slot owners become
     * "transaction" or "split:ACCOUNT".
     *
     * @param ignoreReconcileOf account whose split's reconcile fields are left out, or null
     */
    private Map<String, Object> rows(String tx, String ignoreReconcileOf) {
        return Map.of(
                "transaction", sql.sql("SELECT currency_guid, num, post_date, description FROM transactions WHERE guid = :tx")
                        .param("tx", tx).query().singleRow(),
                "splits", sql.sql("""
                                SELECT account_guid, memo, action,
                                       CASE WHEN account_guid = :ignore THEN NULL ELSE reconcile_state END AS reconcile_state,
                                       CASE WHEN account_guid = :ignore THEN NULL ELSE reconcile_date END AS reconcile_date,
                                       value_num, value_denom, quantity_num, quantity_denom, lot_guid
                                FROM splits WHERE tx_guid = :tx ORDER BY account_guid""")
                        .param("tx", tx).param("ignore", ignoreReconcileOf == null ? "" : ignoreReconcileOf)
                        .query().listOfRows(),
                "slots", sql.sql("""
                                SELECT CASE WHEN sl.obj_guid = :tx THEN 'transaction' ELSE 'split:' || sp.account_guid END AS owner,
                                       sl.name, sl.slot_type, sl.int64_val, sl.string_val, sl.double_val, sl.timespec_val,
                                       sl.guid_val, sl.numeric_val_num, sl.numeric_val_denom, sl.gdate_val
                                FROM slots sl LEFT JOIN splits sp ON sp.guid = sl.obj_guid
                                WHERE sl.obj_guid = :tx OR sp.tx_guid = :tx
                                ORDER BY owner, sl.name""")
                        .param("tx", tx).query().listOfRows());
    }
}
