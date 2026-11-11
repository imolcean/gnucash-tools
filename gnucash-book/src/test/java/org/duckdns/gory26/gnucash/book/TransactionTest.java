package org.duckdns.gory26.gnucash.book;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransactionTest {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 3);

    private static Split split(String account, long valueCents, long quantityCents) {
        return new Split(account, new GncNumeric(valueCents, 100), new GncNumeric(quantityCents, 100));
    }

    @Test
    void splitsWhoseValuesDoNotSumToZeroAreRefused() {
        List<Split> unbalanced = List.of(
                split("rent", 2000, 2000),
                split("cash", -1999, -1999));

        assertThatThrownBy(() -> new Transaction(DAY, "Storage room", "", unbalanced))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anyNumberOfSplitsMayBalanceAndQuantitiesMayDifferFromValues() {
        // Cross-currency purchase with trading accounts: 20 EUR value, 25 USD quantity on the USD side.
        List<Split> fourSplits = List.of(
                split("cash-usd", -2000, -2500),
                split("trading-usd", 2000, 2500),
                split("trading-eur", -2000, -2000),
                split("cash", 2000, 2000));

        assertThat(new Transaction(DAY, "Exchange", "", fourSplits).splits()).hasSize(4);
    }
}
