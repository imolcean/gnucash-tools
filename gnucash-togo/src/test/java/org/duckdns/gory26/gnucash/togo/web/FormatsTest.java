package org.duckdns.gory26.gnucash.togo.web;

import org.duckdns.gory26.gnucash.book.Commodity;
import org.duckdns.gory26.gnucash.book.GncNumeric;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class FormatsTest {

    private static final Commodity EUR = new Commodity("e", "CURRENCY", "EUR", 100);
    private static final Commodity USD = new Commodity("u", "CURRENCY", "USD", 100);
    private static final Commodity CHF = new Commodity("c", "CURRENCY", "CHF", 100);
    private static final Commodity JPY = new Commodity("j", "CURRENCY", "JPY", 1);

    private final Formats fmt = new Formats();

    @Test
    void groupsThousandsWithANarrowNoBreakSpaceAndPutsTheSymbolAfter() {
        assertThat(fmt.amount(new GncNumeric(158500, 100), EUR)).isEqualTo("1 585.00 €");
        assertThat(fmt.amount(new GncNumeric(123456789, 100), EUR)).isEqualTo("1 234 567.89 €");
    }

    @Test
    void negativesUseTheMinusSign() {
        assertThat(fmt.amount(new GncNumeric(-2500, 100), EUR)).isEqualTo("−25.00 €");
    }

    @Test
    void zeroShowsAllDecimalsAndNoSign() {
        assertThat(fmt.amount(new GncNumeric(0, 1), USD)).isEqualTo("0.00 $");
        assertThat(fmt.signed(new GncNumeric(0, 100), USD)).isEqualTo("0.00 $");
    }

    @Test
    void unknownSymbolsFallBackToTheMnemonic() {
        assertThat(fmt.amount(new GncNumeric(1250, 100), CHF)).isEqualTo("12.50 CHF");
    }

    @Test
    void theFractionSetsTheDecimals() {
        assertThat(fmt.amount(new GncNumeric(1000, 1), JPY)).isEqualTo("1 000 JPY");
    }

    @Test
    void signedAddsAPlusToPositiveAmounts() {
        assertThat(fmt.signed(new GncNumeric(150000, 100), EUR)).isEqualTo("+1 500.00 €");
        assertThat(fmt.signed(new GncNumeric(-766, 100), EUR)).isEqualTo("−7.66 €");
    }

    @Test
    void finerAmountsAreShownExactlyNotRounded() {
        assertThat(fmt.amount(new GncNumeric(13065, 1000), EUR)).isEqualTo("13.065 €");
    }

    @Test
    void inputValueIsPlainDecimal() {
        assertThat(fmt.input(new GncNumeric(123456, 100), EUR)).isEqualTo("1234.56");
        assertThat(fmt.input(new GncNumeric(2, 1), EUR)).isEqualTo("2.00");
    }

    @Test
    void datesAreDayMonthYear() {
        assertThat(fmt.date(LocalDate.of(2026, 5, 31))).isEqualTo("31.05.2026");
        assertThat(fmt.weekday(LocalDate.of(2026, 11, 4))).isEqualTo("Wed");
    }
}
