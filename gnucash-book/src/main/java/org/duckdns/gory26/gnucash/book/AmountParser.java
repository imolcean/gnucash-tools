package org.duckdns.gory26.gnucash.book;

import org.duckdns.gory26.gnucash.book.InvalidAmountException.Reason;

import java.math.BigDecimal;
import java.util.regex.Pattern;

/**
 * Parses an amount typed on a phone into an exact {@link BigDecimal}, never through floating point.
 *
 * <p>Accepts {@code .} or {@code ,} as the decimal mark and spaces (including no-break spaces) or the other mark
 * as thousands separators: {@code 12,34}, {@code 12.34}, {@code 1 234,5}, {@code 1.234,50}.
 * Refuses, rather than guesses: signs, a lone mark before exactly three digits ({@code 1.234}),
 * and anything finer than the commodity fraction.
 */
public final class AmountParser {

    private static final Pattern SPACES = Pattern.compile("[\\s\\u00a0\\u2007\\u202f]");
    private static final Pattern SIGNS = Pattern.compile(".*[+\\-\\u2212].*");
    private static final Pattern ALLOWED = Pattern.compile("[0-9 .,]+");
    private static final Pattern GROUPED = Pattern.compile("[0-9]{1,3}(?:[ .,][0-9]{3})*");
    private static final Pattern DIGITS = Pattern.compile("[0-9]*");
    private static final Pattern THOUSANDS_LOOKALIKE = Pattern.compile("[1-9][0-9]{0,2}[.,][0-9]{3}");

    private AmountParser() {
    }

    /**
     * @param fraction the commodity fraction, e.g. 100 for EUR
     * @throws InvalidAmountException if the input can't be taken literally
     */
    public static BigDecimal parse(String text, long fraction) {
        String s = SPACES.matcher(text.strip()).replaceAll(" ");
        if (SIGNS.matcher(s).matches()) {
            throw new InvalidAmountException(Reason.SIGNED, text);
        }
        if (!ALLOWED.matcher(s).matches()) {
            throw new InvalidAmountException(Reason.MALFORMED, text);
        }
        if (THOUSANDS_LOOKALIKE.matcher(s).matches()) {
            throw new InvalidAmountException(Reason.AMBIGUOUS, text);
        }

        int decimalMark = decimalMarkPosition(s);
        String integerPart = decimalMark < 0 ? s : s.substring(0, decimalMark);
        String fractionPart = decimalMark < 0 ? "" : s.substring(decimalMark + 1);
        boolean integerOk = DIGITS.matcher(integerPart).matches() || isGroupedWithOneSeparator(integerPart);
        boolean fractionOk = decimalMark < 0 || (!fractionPart.isEmpty() && DIGITS.matcher(fractionPart).matches());
        if (!integerOk || !fractionOk) {
            throw new InvalidAmountException(Reason.MALFORMED, text);
        }

        String digits = integerPart.replaceAll("[ .,]", "");
        BigDecimal amount = new BigDecimal((digits.isEmpty() ? "0" : digits) + (fractionPart.isEmpty() ? "" : "." + fractionPart));
        if (amount.multiply(BigDecimal.valueOf(fraction)).stripTrailingZeros().scale() > 0) {
            throw new InvalidAmountException(Reason.TOO_PRECISE, text);
        }
        return amount;
    }

    /** Index of the decimal mark, or -1 when the input has none. */
    private static int decimalMarkPosition(String s) {
        int lastDot = s.lastIndexOf('.');
        int lastComma = s.lastIndexOf(',');
        if (lastDot >= 0 && lastComma >= 0) {
            return Math.max(lastDot, lastComma);
        }
        int last = Math.max(lastDot, lastComma);
        if (last < 0 || s.indexOf(s.charAt(last)) != last) {
            return -1;
        }
        return last;
    }

    private static boolean isGroupedWithOneSeparator(String integerPart) {
        return GROUPED.matcher(integerPart).matches()
                && integerPart.chars().filter(c -> c == ' ' || c == '.' || c == ',').distinct().count() == 1;
    }
}
