package org.duckdns.gory26.gnucash.book;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

class RegisterTest {

    private final GnuCashBook book = new GnuCashBook(SampleBook.freshCopy(), SampleBook.TODAY);

    @Test
    void cashRegisterIsNewestFirstWithRunningBalances() {
        RegisterPage page = book.register(SampleBook.CASH, 50);

        assertThat(page.next()).isEmpty();
        assertThat(page.rows())
                .extracting(RegisterRow::description, r -> r.amount().toBigDecimal(), r -> r.balance().toBigDecimal(), RegisterRow::editable)
                .containsExactly(
                        tuple("Concert tickets (maybe?)", new BigDecimal("-200.00"), new BigDecimal("1385.00"), true),
                        tuple("Sell USD @ 1.3", new BigDecimal("325.00"), new BigDecimal("1585.00"), false),
                        tuple("Buy USD @ 1.2", new BigDecimal("-300.00"), new BigDecimal("1260.00"), false),
                        tuple("Storage room", new BigDecimal("-20.00"), new BigDecimal("1560.00"), true),
                        tuple("Salary", new BigDecimal("1500.00"), new BigDecimal("1580.00"), false),
                        tuple("[Tag 1] Rewe again", new BigDecimal("-7.66"), new BigDecimal("80.00"), false),
                        tuple("[Tag 1] [Tag 2]  Rewe", new BigDecimal("-12.34"), new BigDecimal("87.66"), false),
                        tuple("Opening Balance", new BigDecimal("100.00"), new BigDecimal("100.00"), false));
    }

    @Test
    void rowsCarryDateNotesReconcileStateAndTheOtherSide() {
        List<RegisterRow> rows = book.register(SampleBook.CASH, 50).rows();

        RegisterRow concert = rows.getFirst();
        assertThat(concert.date()).isEqualTo(LocalDate.of(2026, 11, 10));
        assertThat(concert.otherAccounts()).containsExactly("Expenses:Misc");
        assertThat(concert.reconcileState()).isEqualTo("n");

        RegisterRow reweAgain = rows.get(5);
        assertThat(reweAgain.notes()).isEqualTo("Forgot something");
        assertThat(reweAgain.reconcileState()).isEqualTo("y");

        assertThat(rows.get(4).otherAccounts()).containsExactly("Expenses:Rent", "Income:Salary");
    }

    @Test
    void pagesFollowTheCursorAndKeepTheRunningBalance() {
        RegisterPage page = book.register(SampleBook.CASH, 3);
        List<RegisterRow> all = new ArrayList<>(page.rows());
        int pages = 1;
        while (page.next().isPresent()) {
            String token = page.next().get().toToken();
            page = book.register(SampleBook.CASH, RegisterCursor.fromToken(token), 3);
            all.addAll(page.rows());
            pages++;
        }

        assertThat(pages).isEqualTo(3);
        assertThat(all).isEqualTo(book.register(SampleBook.CASH, 50).rows());
    }

    @Test
    void anAccountWithoutSplitsHasAnEmptyRegister() {
        assertThat(book.register(SampleBook.CHECKING, 50)).isEqualTo(new RegisterPage(List.of(), java.util.Optional.empty()));
    }

    @Test
    void aForgedCursorIsRejected() {
        assertThatThrownBy(() -> RegisterCursor.fromToken("2026-11-03T10:59_x_y")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RegisterCursor.fromToken("'; DROP TABLE splits")).isInstanceOf(IllegalArgumentException.class);
    }
}
