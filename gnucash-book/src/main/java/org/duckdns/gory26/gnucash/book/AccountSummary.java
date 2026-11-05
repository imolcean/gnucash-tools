package org.duckdns.gory26.gnucash.book;

/**
 * One account on its own, as a register header or a prefilled picker needs it.
 *
 * @param path    full name without the root, e.g. {@code Expenses:Food:Groceries}
 * @param hidden  hidden itself or through an ancestor
 * @param balance stored (not sign-flipped) Present balance of this account alone, in its commodity
 */
public record AccountSummary(String guid,
                             String name,
                             String path,
                             AccountType type,
                             Commodity commodity,
                             boolean placeholder,
                             boolean hidden,
                             GncNumeric balance) {

    public GncNumeric displayBalance() {
        return type.display(balance);
    }
}
