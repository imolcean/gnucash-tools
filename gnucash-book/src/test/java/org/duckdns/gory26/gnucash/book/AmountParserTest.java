package org.duckdns.gory26.gnucash.book;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AmountParserTest {

    private static final long CENTS = 100;

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "12,34 | 12.34",
            "12.34 | 12.34",
            "12    | 12",
            "1 234,5 | 1234.5",
            "1.234,50 | 1234.50",
            "1,234.50 | 1234.50",
            "12 345 678,90 | 12345678.90",
            "1.234.567 | 1234567",
            "1 234,56 | 1234.56",
            "1 234,56 | 1234.56",
            "0,5 | 0.5",
            ",5 | 0.5",
    })
    void parsesPhoneInput(String input, String expected) {
        assertThat(AmountParser.parse(input, CENTS)).isEqualTo(new BigDecimal(expected));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "-12,34    | SIGNED",
            "+5        | SIGNED",
            "12-       | SIGNED",
            "1.234     | AMBIGUOUS",
            "1,234     | AMBIGUOUS",
            "999.000   | AMBIGUOUS",
            "12,3456   | TOO_PRECISE",
            "0,001     | TOO_PRECISE",
            "1.234,567 | TOO_PRECISE",
            "1 234,567 | TOO_PRECISE",
            "''        | MALFORMED",
            "'   '     | MALFORMED",
            "abc       | MALFORMED",
            "1e3       | MALFORMED",
            "12.       | MALFORMED",
            ",         | MALFORMED",
            "12 34     | MALFORMED",
            "12,34,5   | MALFORMED",
            "1 234.567.890,5 | MALFORMED",
    })
    void rejectsInputItCannotTakeLiterally(String input, InvalidAmountException.Reason reason) {
        assertThatThrownBy(() -> AmountParser.parse(input, CENTS))
                .isInstanceOfSatisfying(InvalidAmountException.class, e -> assertThat(e.reason()).isEqualTo(reason));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "19.99 | 1999",
            "4,35  | 435",
            "0.29  | 29",
            "1.10  | 110",
    })
    void floatTrapsBecomeExactCents(String input, long cents) {
        assertThat(cents(input)).isEqualTo(new GncNumeric(cents, CENTS));
    }

    @Test
    void tenAndTwentyCentsTypedAsTextAddUpToExactlyThirty() {
        assertThat(cents("0,1").add(cents("0,2"))).isEqualTo(new GncNumeric(30, CENTS));
    }

    @Test
    void halfACentIsRefusedNotRounded() {
        assertThatThrownBy(() -> cents("1.005")).isInstanceOf(InvalidAmountException.class);
        assertThatThrownBy(() -> cents("0,005")).isInstanceOf(InvalidAmountException.class);
    }

    @Test
    void everyCentValueUpTo99999_99RoundTripsThroughNumOverDenom() {
        for (long c = 0; c <= 9_999_999; c++) {
            String typed = typed(c);
            GncNumeric amount = cents(typed);
            assertThat(amount.num()).as(typed).isEqualTo(c);
            assertThat(amount.toBigDecimal()).as(typed).isEqualTo(BigDecimal.valueOf(c, 2));
        }
    }

    private static GncNumeric cents(String input) {
        return GncNumeric.of(AmountParser.parse(input, CENTS), CENTS);
    }

    /** Builds the text a user might type for {@code c} cents, varying the style, without any floating point. */
    private static String typed(long c) {
        long euros = c / 100;
        String cents = String.format("%02d", c % 100);
        return switch ((int) (c % 3)) {
            case 0 -> euros + "," + cents;
            case 1 -> euros + "." + cents;
            default -> grouped(euros, euros % 2 == 0 ? " " : ".") + "," + cents;
        };
    }

    private static String grouped(long euros, String separator) {
        String plain = Long.toString(euros);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < plain.length(); i++) {
            if (i > 0 && (plain.length() - i) % 3 == 0) {
                out.append(separator);
            }
            out.append(plain.charAt(i));
        }
        return out.toString();
    }
}
