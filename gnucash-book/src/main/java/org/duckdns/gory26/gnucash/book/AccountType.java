package org.duckdns.gory26.gnucash.book;

/**
 * GnuCash account types as stored in {@code accounts.account_type}.
 */
public enum AccountType {
    NONE, BANK, CASH, CREDIT, ASSET, LIABILITY, STOCK, MUTUAL, CURRENCY, INCOME, EXPENSE, EQUITY,
    RECEIVABLE, PAYABLE, ROOT, TRADING, CHECKING, SAVINGS, MONEYMRKT, CREDITLINE;

    /**
     * Whether desktop shows this type's balance with the sign flipped, under its default
     * "Reverse balanced accounts: Credit accounts" setting ({@code gnc-ui-util.cpp}). TRADING is not flipped.
     */
    public boolean reversed() {
        return switch (this) {
            case INCOME, LIABILITY, CREDIT, EQUITY, PAYABLE -> true;
            default -> false;
        };
    }

    /** A stored balance as desktop shows it for this type (ADR-0008). */
    public GncNumeric display(GncNumeric stored) {
        return reversed() ? stored.negate() : stored;
    }
}
