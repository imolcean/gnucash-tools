package org.duckdns.gory26.gnucash.book;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransferDraftTest {

    private static final String CASH = "cash-guid";
    private static final String GROCERIES = "groceries-guid";

    @Test
    void transferBecomesTwoSplitsMovingTheAmountFromOneAccountToTheOther() {
        TransferDraft draft = new TransferDraft(LocalDate.of(2026, 11, 3), List.of("Tag 1"), "Rewe again",
                new GncNumeric(766, 100), CASH, GROCERIES, "Forgot something");

        Transaction tx = draft.toTransaction();

        assertThat(tx.postDate()).isEqualTo(LocalDate.of(2026, 11, 3));
        assertThat(tx.description()).isEqualTo("[Tag 1] Rewe again");
        assertThat(tx.notes()).isEqualTo("Forgot something");
        assertThat(tx.splits()).containsExactlyInAnyOrder(
                new Split(GROCERIES, new GncNumeric(766, 100), new GncNumeric(766, 100)),
                new Split(CASH, new GncNumeric(-766, 100), new GncNumeric(-766, 100)));
    }

    @Test
    void transferWithoutTagsOrNotes() {
        Transaction tx = fromCash(new GncNumeric(2000, 100), "rent-guid", null).toTransaction();

        assertThat(tx.description()).isEqualTo("Storage room");
        assertThat(tx.notes()).isEmpty();
    }

    @Test
    void amountMustBePositiveBecauseTheAccountsGiveTheDirection() {
        assertThatThrownBy(() -> fromCash(new GncNumeric(0, 100), GROCERIES, ""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> fromCash(new GncNumeric(-766, 100), GROCERIES, ""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cannotTransferToTheSameAccount() {
        assertThatThrownBy(() -> fromCash(new GncNumeric(766, 100), CASH, ""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static TransferDraft fromCash(GncNumeric amount, String to, String notes) {
        return new TransferDraft(LocalDate.of(2026, 11, 3), List.of(), "Storage room", amount, CASH, to, notes);
    }
}
