package org.duckdns.gory26.gnucash.book;

/** A write the book refused; nothing was changed. */
public class WriteRefusedException extends RuntimeException {

    public enum Reason {
        /** Desktop holds the lock (ADR-0004). */
        LOCKED,
        /** The schema versions are not the known set (ADR-0005). */
        UNKNOWN_SCHEMA,
        /** No such transaction. */
        NOT_FOUND,
        /** The transaction is outside what the app edits: not two splits, reconciled, mixed commodities… */
        NOT_EDITABLE,
        /** An account that transfers may not use: placeholder, hidden, trading, not a currency, unknown. */
        ACCOUNT_NOT_ALLOWED,
        /** {@code from} and {@code to} hold different commodities; v1 is same-currency only. */
        MIXED_COMMODITIES,
        /** The amount is finer than the commodity's fraction. */
        AMOUNT_TOO_PRECISE
    }

    private final Reason reason;

    public WriteRefusedException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
