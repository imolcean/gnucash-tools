package org.duckdns.gory26.gnucash.book;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GncNumericTest {

    @Test
    void amountThatFitsTheFractionBecomesNumOverFraction() {
        GncNumeric amount = GncNumeric.of(new BigDecimal("12.34"), 100);

        assertThat(amount.num()).isEqualTo(1234);
        assertThat(amount.denom()).isEqualTo(100);
    }

    @Test
    void amountFinerThanTheFractionIsRejectedNotRounded() {
        assertThatThrownBy(() -> GncNumeric.of(new BigDecimal("12.345"), 100))
                .isInstanceOf(ArithmeticException.class);
    }

    @Test
    void denominatorMustBePositive() {
        assertThatThrownBy(() -> new GncNumeric(1, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GncNumeric(1, -100)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void comparesByValueAcrossDenominators() {
        assertThat(new GncNumeric(1, 2)).isEqualByComparingTo(new GncNumeric(50, 100));
        assertThat(new GncNumeric(1, 3)).isLessThan(new GncNumeric(34, 100));
        assertThat(new GncNumeric(-1, 3)).isGreaterThan(new GncNumeric(-34, 100));
    }

    @Test
    void addingTenAndTwentyCentsIsExactlyThirtyCents() {
        assertThat(new GncNumeric(10, 100).add(new GncNumeric(20, 100))).isEqualTo(new GncNumeric(30, 100));
    }

    @Test
    void addingAcrossDenominatorsUsesTheirLeastCommonMultiple() {
        assertThat(new GncNumeric(1, 100).add(new GncNumeric(1, 3))).isEqualTo(new GncNumeric(103, 300));
        assertThat(new GncNumeric(5, 100).add(new GncNumeric(-1, 10))).isEqualTo(new GncNumeric(-5, 100));
    }

    @Test
    void negateFlipsTheSignOfTheNumerator() {
        assertThat(new GncNumeric(2000, 100).negate()).isEqualTo(new GncNumeric(-2000, 100));
    }

    @Test
    void multiplyIsExactForPriceConversion() {
        GncNumeric twentyDollars = new GncNumeric(2000, 100);

        assertThat(twentyDollars.multiply(new GncNumeric(92, 100))).isEqualByComparingTo(new GncNumeric(1840, 100));
        assertThat(new GncNumeric(3000, 100).multiply(new GncNumeric(1, 3))).isEqualByComparingTo(new GncNumeric(10, 1));
        assertThat(new GncNumeric(-100, 100).multiply(new GncNumeric(1, 3))).isEqualByComparingTo(new GncNumeric(-1, 3));
    }

    @Test
    void toBigDecimalKeepsTheScaleOfADecimalFraction() {
        assertThat(new GncNumeric(2000, 100).toBigDecimal().toPlainString()).isEqualTo("20.00");
        assertThat(new GncNumeric(-766, 100).toBigDecimal().toPlainString()).isEqualTo("-7.66");
        assertThat(new GncNumeric(1, 8).toBigDecimal().toPlainString()).isEqualTo("0.125");
    }

    @Test
    void toBigDecimalRefusesAValueWithoutAFiniteDecimalForm() {
        assertThatThrownBy(() -> new GncNumeric(1, 3).toBigDecimal()).isInstanceOf(ArithmeticException.class);
    }

    @Test
    void overflowThrowsInsteadOfWrappingAround() {
        GncNumeric max = new GncNumeric(Long.MAX_VALUE, 100);

        assertThatThrownBy(() -> max.add(new GncNumeric(1, 100))).isInstanceOf(ArithmeticException.class);
        assertThatThrownBy(() -> new GncNumeric(Long.MIN_VALUE, 100).negate()).isInstanceOf(ArithmeticException.class);
        assertThatThrownBy(() -> new GncNumeric(Long.MAX_VALUE, 1).multiply(new GncNumeric(2, 1))).isInstanceOf(ArithmeticException.class);
    }

    @Test
    void toFractionRewritesTheDenominatorWhenExact() {
        assertThat(new GncNumeric(1, 2).toFraction(100)).isEqualTo(new GncNumeric(50, 100));
        assertThat(new GncNumeric(-123400, 10000).toFraction(100)).isEqualTo(new GncNumeric(-1234, 100));
        assertThat(new GncNumeric(2000, 1000).toFraction(100)).isEqualTo(new GncNumeric(200, 100));
    }

    @Test
    void toFractionRefusesToRound() {
        assertThatThrownBy(() -> new GncNumeric(1, 3).toFraction(100)).isInstanceOf(ArithmeticException.class);
        assertThatThrownBy(() -> new GncNumeric(1, 1000).toFraction(100)).isInstanceOf(ArithmeticException.class);
    }

    @Test
    void roundToFractionUsesBankersRoundingLikeDesktopConversion() {
        // 10.05 USD at 13/10 is 13.065 EUR: banker's rounding gives 13.06, half-up would give 13.07
        GncNumeric converted = new GncNumeric(1005, 100).multiply(new GncNumeric(13, 10));

        assertThat(converted.roundToFraction(100)).isEqualTo(new GncNumeric(1306, 100));
        assertThat(converted.negate().roundToFraction(100)).isEqualTo(new GncNumeric(-1306, 100));
        assertThat(new GncNumeric(13075, 1000).roundToFraction(100)).isEqualTo(new GncNumeric(1308, 100));
        assertThat(new GncNumeric(1, 3).roundToFraction(100)).isEqualTo(new GncNumeric(33, 100));
    }

    @Test
    void invertSwapsAndKeepsTheSignOnTheNumerator() {
        assertThat(new GncNumeric(13, 10).invert()).isEqualTo(new GncNumeric(10, 13));
        assertThat(new GncNumeric(-13, 10).invert()).isEqualTo(new GncNumeric(-10, 13));
        assertThatThrownBy(() -> new GncNumeric(0, 1).invert()).isInstanceOf(ArithmeticException.class);
    }
}
