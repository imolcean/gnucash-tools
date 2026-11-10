package org.duckdns.gory26.gnucash.book;

import org.duckdns.gory26.gnucash.book.InvalidAmountException.Reason;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Typed amounts with GnuCash Desktop's calculator: {@code 12.30 + 4.50}, {@code 4.50 × 2 + 1.25}, {@code 10.00 ÷ 3}.
 * Operators are {@code + - * /} and their typographic forms {@code − × ÷}; {@code ×} and {@code ÷} bind first, and
 * there are no parentheses. Amounts follow {@link AmountParser}; a number after {@code ×} or {@code ÷} is a whole
 * number. The arithmetic is exact, and the result is rounded once, half-even, to the commodity fraction: the only
 * rounding of a typed value (ADR-0003). Without an operator this is {@link AmountParser#parse}.
 */
public final class AmountExpression {

    private static final Pattern OPERATOR = Pattern.compile("\\s*([-+*/])\\s*");
    private static final Pattern WHOLE = Pattern.compile("[0-9]+");

    private AmountExpression() {
    }

    /**
     * Whether {@code text} has no operator between two terms, so {@link #evaluate} is {@link AmountParser#parse}.
     * A leading or trailing {@code +} or {@code -} is a sign, not an operator.
     */
    public static boolean isPlain(String text) {
        String s = text.strip();
        return s.length() <= 2 || !OPERATOR.matcher(normalized(s.substring(1, s.length() - 1))).find();
    }

    /**
     * @param fraction the commodity fraction, e.g. 100 for EUR
     * @return the result, which may be zero or negative
     * @throws InvalidAmountException if a term can't be taken literally, or for a division by zero
     */
    public static BigDecimal evaluate(String text, long fraction) {
        if (isPlain(text)) {
            return AmountParser.parse(text, fraction); // a plain amount, or a sign the parser refuses
        }
        String s = normalized(text.strip());

        List<String> terms = new ArrayList<>();
        List<Character> operators = new ArrayList<>();
        Matcher m = OPERATOR.matcher(s);
        int start = 0;
        while (m.find()) {
            terms.add(s.substring(start, m.start()));
            operators.add(m.group(1).charAt(0));
            start = m.end();
        }
        terms.add(s.substring(start));
        if (terms.getFirst().isEmpty() || terms.getLast().isEmpty()) {
            return AmountParser.parse(text, fraction); // leading or trailing operator: a sign
        }

        // Σ ±(amount × factors ÷ divisors), kept as one exact fraction num/den
        BigDecimal num = BigDecimal.ZERO;
        BigDecimal den = BigDecimal.ONE;
        BigDecimal productNum = amount(terms.getFirst(), fraction, text);
        BigDecimal productDen = BigDecimal.ONE;
        int sign = 1;
        for (int i = 0; i < operators.size(); i++) {
            char op = operators.get(i);
            String term = terms.get(i + 1);
            if (op == '*' || op == '/') {
                BigDecimal factor = whole(term, text);
                if (op == '*') {
                    productNum = productNum.multiply(factor);
                } else if (factor.signum() == 0) {
                    throw new InvalidAmountException(Reason.DIVISION_BY_ZERO, text);
                } else {
                    productDen = productDen.multiply(factor);
                }
            } else {
                num = num.multiply(productDen).add(productNum.multiply(den).multiply(BigDecimal.valueOf(sign)));
                den = den.multiply(productDen);
                sign = op == '+' ? 1 : -1;
                productNum = amount(term, fraction, text);
                productDen = BigDecimal.ONE;
            }
        }
        num = num.multiply(productDen).add(productNum.multiply(den).multiply(BigDecimal.valueOf(sign)));
        den = den.multiply(productDen);
        return rounded(num, den, fraction, text);
    }

    private static String normalized(String s) {
        return s.replace('−', '-').replace('×', '*').replace('÷', '/');
    }

    private static BigDecimal amount(String term, long fraction, String text) {
        if (term.isEmpty()) {
            throw new InvalidAmountException(Reason.MALFORMED, text); // two operators in a row
        }
        return AmountParser.parse(term, fraction);
    }

    private static BigDecimal whole(String term, String text) {
        if (!WHOLE.matcher(term).matches()) {
            throw new InvalidAmountException(Reason.MALFORMED, text);
        }
        return new BigDecimal(term);
    }

    /** num/den rounded half-even to 1/fraction. */
    private static BigDecimal rounded(BigDecimal num, BigDecimal den, long fraction, String text) {
        if (den.compareTo(BigDecimal.ONE) == 0) {
            return num; // no division: exact already, every term fits the fraction
        }
        BigDecimal units = num.multiply(BigDecimal.valueOf(fraction)).divide(den, 0, RoundingMode.HALF_EVEN);
        try {
            return units.divide(BigDecimal.valueOf(fraction), MathContext.UNLIMITED); // exact, or throws
        } catch (ArithmeticException e) {
            throw new InvalidAmountException(Reason.TOO_PRECISE, text); // 1/fraction has no decimal form
        }
    }
}
