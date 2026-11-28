package org.duckdns.gory26.gnucash.book;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** ADR-0015: whole transactions read back exactly, desktop-written or not. */
@Tag("docker")
class TransactionReadTest {

    private static final String GUID = "fedcba9876543210fedcba9876543210";

    private final DataSource db = SampleBook.freshCopy();
    private final GnuCashBook book = SampleBook.unlocked(db);
    private final Sql sql = new Sql(db);

    @Test
    void aDesktopWrittenMultiSplitTransaction() {
        String salary = txByDescription("Salary");

        StoredTransaction tx = book.transaction(salary).orElseThrow();

        assertThat(tx.guid()).isEqualTo(salary);
        assertThat(tx.currencyGuid()).isEqualTo(SampleBook.EUR);
        assertThat(tx.datePosted()).isEqualTo(LocalDate.of(2026, 11, 3));
        assertThat(tx.entered()).isEqualTo(Instant.parse("2026-11-04T10:32:40Z"));
        assertThat(tx.description()).isEqualTo("Salary");
        assertThat(tx.notes()).isEmpty();
        assertThat(tx.slots()).isEmpty();
        assertThat(tx.splits()).extracting(StoredSplit::accountGuid, StoredSplit::value, StoredSplit::memo)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(SampleBook.CASH, new GncNumeric(150000, 100), ""),
                        org.assertj.core.groups.Tuple.tuple(SampleBook.RENT, new GncNumeric(50000, 100), "Company-paid flat"),
                        org.assertj.core.groups.Tuple.tuple(SampleBook.SALARY, new GncNumeric(-200000, 100), ""));
        assertThat(tx.splits()).extracting(StoredSplit::guid).isSorted();
    }

    @Test
    void notesAndAReconciledSplit() {
        StoredTransaction tx = book.transaction(txByDescription("[Tag 1] Rewe again")).orElseThrow();

        assertThat(tx.notes()).isEqualTo("Forgot something");
        StoredSplit cash = split(tx, SampleBook.CASH);
        assertThat(cash.reconcileState()).isEqualTo(ReconcileState.RECONCILED);
        assertThat(cash.reconcileDate()).isEqualTo(Instant.parse("2026-11-02T22:59:59Z"));
        StoredSplit groceries = split(tx, SampleBook.GROCERIES);
        assertThat(groceries.reconcileState()).isEqualTo(ReconcileState.NOT_RECONCILED);
        assertThat(groceries.reconcileDate()).isNull();
    }

    @Test
    void crossCurrencyQuantitiesAreExact() {
        StoredTransaction tx = book.transaction(txByDescription("Buy USD @ 1.2")).orElseThrow();

        assertThat(tx.splits()).hasSize(4);
        assertThat(split(tx, SampleBook.CASH_USD).value()).isEqualTo(new GncNumeric(30000, 100));
        assertThat(split(tx, SampleBook.CASH_USD).quantity()).isEqualTo(new GncNumeric(25000, 100));
        assertThat(split(tx, SampleBook.TRADING_USD).quantity()).isEqualTo(new GncNumeric(-25000, 100));
    }

    @Test
    void whatInsertTransactionWroteReadsBack() {
        book.insertTransaction(new NewTransaction(GUID, SampleBook.EUR, LocalDate.of(2026, 11, 5), "Round trip", "a note",
                Map.of("source", "fincore", "id", "42"), List.of(
                new NewSplit(SampleBook.CASH, new GncNumeric(-1234, 100), new GncNumeric(-1234, 100), "out",
                        ReconcileState.CLEARED, null, Map.of("leg", "1")),
                new NewSplit(SampleBook.CASH_USD, new GncNumeric(1234, 100), new GncNumeric(1000, 100), "in",
                        ReconcileState.RECONCILED, Instant.parse("2026-11-05T22:59:59Z"), Map.of()))));

        StoredTransaction tx = book.transaction(GUID).orElseThrow();

        assertThat(tx.datePosted()).isEqualTo(LocalDate.of(2026, 11, 5));
        assertThat(tx.entered()).isEqualTo(SampleBook.TODAY.instant());
        assertThat(tx.description()).isEqualTo("Round trip");
        assertThat(tx.notes()).isEqualTo("a note");
        assertThat(tx.slots()).isEqualTo(Map.of("source", "fincore", "id", "42"));
        StoredSplit cash = split(tx, SampleBook.CASH);
        assertThat(cash).usingRecursiveComparison().ignoringFields("guid").isEqualTo(new StoredSplit(null, SampleBook.CASH,
                new GncNumeric(-1234, 100), new GncNumeric(-1234, 100), "out", ReconcileState.CLEARED, null, Map.of("leg", "1")));
        StoredSplit usd = split(tx, SampleBook.CASH_USD);
        assertThat(usd.quantity()).isEqualTo(new GncNumeric(1000, 100));
        assertThat(usd.reconcileDate()).isEqualTo(Instant.parse("2026-11-05T22:59:59Z"));
        assertThat(usd.slots()).isEmpty();
    }

    @Test
    void aMissingTransactionIsEmpty() {
        assertThat(book.transaction("0".repeat(32))).isEmpty();
    }

    @Test
    void transactionsPostedInARangeBothEndsIncludedInDesktopOrder() {
        assertThat(book.transactions(LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 3)))
                .extracting(StoredTransaction::description)
                .containsExactly("Salary", "Storage room", "Buy USD @ 1.2");
        assertThat(book.transactions(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30))).hasSize(8)
                .allSatisfy(tx -> assertThat(tx.splits()).isNotEmpty());
        assertThat(book.transactions(LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 31))).isEmpty();
        assertThatThrownBy(() -> book.transactions(LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theDatePostedSlotWinsOverThePostDateTimestamp() {
        // as an older GnuCash wrote it: local midnight in Berlin, the previous day in UTC
        String storage = txByDescription("Storage room");
        sql.update("UPDATE transactions SET post_date = '2026-11-02 23:00:00' WHERE guid = ?", storage);

        assertThat(book.transaction(storage).orElseThrow().datePosted()).isEqualTo(LocalDate.of(2026, 11, 3));
        assertThat(book.transactions(LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 3)))
                .extracting(StoredTransaction::guid).contains(storage);
    }

    private String txByDescription(String description) {
        return sql.one(String.class, "SELECT guid FROM transactions WHERE description = ?", description);
    }

    private static StoredSplit split(StoredTransaction tx, String account) {
        return tx.splits().stream().filter(s -> s.accountGuid().equals(account)).findFirst().orElseThrow();
    }
}
