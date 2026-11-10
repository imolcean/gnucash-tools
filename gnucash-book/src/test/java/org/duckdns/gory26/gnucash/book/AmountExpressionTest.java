package org.duckdns.gory26.gnucash.book;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** GnuCash Desktop's amount calculator (Tutorial and Concepts Guide, 2.9.2.4) for the phone. */
class AmountExpressionTest {

    private static final long CENTS = 100;

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "12.30 + 4.50       | 16.80",
            "12.30+4.50         | 16.80",
            "12,30 + 4,50       | 16.80",
            "4.50 × 2 + 1.25    | 10.25",
            "1.25 + 4.50 × 2    | 10.25",
            "4.50 * 2           | 9.00",
            "20.00 − 5.50       | 14.50",
            "20.00 - 5.50       | 14.50",
            "1 − 2              | -1",
            "10.00 ÷ 3          | 3.33",
            "10.00 / 3          | 3.33",
            "20.00 ÷ 3          | 6.67",
            "0.05 ÷ 2           | 0.02",
            "0.15 ÷ 2           | 0.08",
            "10.00 ÷ 3 × 3      | 10.00",
            "1.00 + 10.00 ÷ 4   | 3.50",
            "12.34              | 12.34",
            "1 234,5            | 1234.5",
    })
    void calculatesLikeDesktopAndRoundsOnlyOnceAtTheEnd(String input, String expected) {
        assertThat(AmountExpression.evaluate(input, CENTS)).isEqualByComparingTo(new BigDecimal(expected));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "5 ÷ 0          | DIVISION_BY_ZERO",
            "5.00 ÷ 2 ÷ 0   | DIVISION_BY_ZERO",
            "5 × 1.5        | MALFORMED",
            "5 ÷ 0,5        | MALFORMED",
            "5 ++ 3         | MALFORMED",
            "5 + × 3        | MALFORMED",
            "+5             | SIGNED",
            "-12,34         | SIGNED",
            "5 +            | SIGNED",
            "12-            | SIGNED",
            "1.234 + 1      | AMBIGUOUS",
            "0,001 + 1      | TOO_PRECISE",
            "abc + 1        | MALFORMED",
            "''             | MALFORMED",
    })
    void refusesWhatItCannotTakeLiterally(String input, InvalidAmountException.Reason reason) {
        assertThatThrownBy(() -> AmountExpression.evaluate(input, CENTS))
                .isInstanceOfSatisfying(InvalidAmountException.class, e -> assertThat(e.reason()).isEqualTo(reason));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "1 | 10 ÷ 3   | 3",
            "1 | 10 ÷ 4   | 2",
            "1000 | 1.0 ÷ 3    | 0.333",
            "1000 | 0.005 ÷ 2  | 0.002",
    })
    void roundsToTheCommodityFractionHalfEven(long fraction, String input, String expected) {
        assertThat(AmountExpression.evaluate(input, fraction)).isEqualByComparingTo(new BigDecimal(expected));
    }
}
