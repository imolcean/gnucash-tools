package org.duckdns.gory26.gnucash.book;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * An exact GnuCash amount {@code num/denom}, as stored in {@code value_num/value_denom} and friends.
 * The sign is on the numerator; the denominator is always positive and is kept as given, not reduced,
 * because GnuCash stores the commodity fraction as the denominator.
 */
public record GncNumeric(long num, long denom) implements Comparable<GncNumeric> {

    public GncNumeric {
        if (denom <= 0) {
            throw new IllegalArgumentException("denominator must be positive: " + num + "/" + denom);
        }
    }

    /**
     * Converts a decimal amount to {@code num/fraction}.
     *
     * @throws ArithmeticException if the amount is finer than the fraction; it is never rounded
     */
    public static GncNumeric of(BigDecimal amount, long fraction) {
        BigDecimal scaled = amount.multiply(BigDecimal.valueOf(fraction));
        return new GncNumeric(scaled.longValueExact(), fraction);
    }

    /**
     * Exact sum over the least common multiple of both denominators.
     *
     * @throws ArithmeticException on overflow
     */
    public GncNumeric add(GncNumeric other) {
        long lcm = Math.multiplyExact(denom / gcd(denom, other.denom), other.denom);
        return new GncNumeric(
                Math.addExact(Math.multiplyExact(num, lcm / denom), Math.multiplyExact(other.num, lcm / other.denom)),
                lcm);
    }

    /**
     * Exact product, cross-reduced first so denominators grow no more than needed.
     *
     * @throws ArithmeticException on overflow
     */
    public GncNumeric multiply(GncNumeric other) {
        long g1 = gcd(Math.absExact(num), other.denom);
        long g2 = gcd(Math.absExact(other.num), denom);
        return new GncNumeric(
                Math.multiplyExact(num / g1, other.num / g2),
                Math.multiplyExact(denom / g2, other.denom / g1));
    }

    /**
     * The exact decimal value; with a power-of-ten denominator its scale matches the fraction ({@code 2000/100} is {@code 20.00}).
     *
     * @throws ArithmeticException if the value has no finite decimal form, like {@code 1/3}
     */
    public BigDecimal toBigDecimal() {
        int scale = 0;
        long rest = denom;
        while (rest % 10 == 0) {
            rest /= 10;
            scale++;
        }
        return rest == 1
                ? BigDecimal.valueOf(num, scale)
                : BigDecimal.valueOf(num).divide(BigDecimal.valueOf(denom), MathContext.UNLIMITED);
    }

    public GncNumeric negate() {
        return new GncNumeric(Math.negateExact(num), denom);
    }

    /**
     * The same value over {@code fraction}, as stored in a {@code *_num/*_denom} column pair.
     *
     * @throws ArithmeticException if the value does not fit the fraction exactly; it is never rounded
     */
    public GncNumeric toFraction(long fraction) {
        if (fraction <= 0) {
            throw new IllegalArgumentException("fraction must be positive: " + fraction);
        }
        long g = gcd(fraction, denom);
        long reducedDenom = denom / g;
        if (num % reducedDenom != 0) {
            throw new ArithmeticException(this + " does not fit fraction " + fraction);
        }
        return new GncNumeric(Math.multiplyExact(num / reducedDenom, fraction / g), fraction);
    }

    /**
     * Rounded to {@code fraction} with banker's rounding (half to even), as desktop does when it converts a
     * balance into another currency ({@code GNC_HOW_RND_ROUND} in {@code gnc-pricedb.cpp}). Only for display
     * rollups; stored amounts go through {@link #toFraction}.
     */
    public GncNumeric roundToFraction(long fraction) {
        BigDecimal scaled = new BigDecimal(BigInteger.valueOf(num).multiply(BigInteger.valueOf(fraction)))
                .divide(BigDecimal.valueOf(denom), 0, RoundingMode.HALF_EVEN);
        return new GncNumeric(scaled.longValueExact(), fraction);
    }

    /** {@code denom/num}, sign kept on the numerator. */
    public GncNumeric invert() {
        if (num == 0) {
            throw new ArithmeticException("cannot invert zero");
        }
        return num > 0 ? new GncNumeric(denom, num) : new GncNumeric(Math.negateExact(denom), Math.negateExact(num));
    }

    public boolean isZero() {
        return num == 0;
    }

    public int signum() {
        return Long.signum(num);
    }

    private static long gcd(long a, long b) {
        while (b != 0) {
            long t = a % b;
            a = b;
            b = t;
        }
        return a;
    }

    /** Compares by value, so {@code 1/2} equals {@code 50/100} here even though the records differ. */
    @Override
    public int compareTo(GncNumeric other) {
        return Long.compare(Math.multiplyExact(num, other.denom), Math.multiplyExact(other.num, denom));
    }
}
