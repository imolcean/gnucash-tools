package org.duckdns.gory26.gnucash.book;

/**
 * Typed input that {@link AmountParser} refuses rather than guess at.
 */
public class InvalidAmountException extends IllegalArgumentException {

    public enum Reason {
        /** Not a number in any accepted form. */
        MALFORMED,
        /** Carries a sign; direction comes from the from/to accounts instead. */
        SIGNED,
        /** One {@code .} or {@code ,} before exactly three digits: thousands or decimals can't be told apart. */
        AMBIGUOUS,
        /** Finer than the commodity fraction; amounts are never rounded. */
        TOO_PRECISE
    }

    private final Reason reason;

    public InvalidAmountException(Reason reason, String input) {
        super(reason + ": '" + input + "'");
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
