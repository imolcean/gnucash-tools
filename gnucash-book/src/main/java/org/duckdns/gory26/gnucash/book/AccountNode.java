package org.duckdns.gory26.gnucash.book;

import java.util.List;
import java.util.Optional;

/**
 * One visible account in the tree, with stored (not sign-flipped) balances in the account's own commodity.
 *
 * @param path     full name without the root, e.g. {@code Expenses:Food:Groceries}
 * @param balance  Present balance of this account alone
 * @param total    Present balance including all descendants, hidden ones too, converted into this account's
 *                 commodity; empty when a descendant with money has no price ("no price" marker)
 * @param children visible children only; hidden accounts and their subtrees are left out (ADR-0008)
 */
public record AccountNode(String guid,
                          String name,
                          String path,
                          AccountType type,
                          Commodity commodity,
                          boolean placeholder,
                          GncNumeric balance,
                          Optional<GncNumeric> total,
                          List<AccountNode> children) {

    public AccountNode {
        children = List.copyOf(children);
    }

    public GncNumeric displayBalance() {
        return type.display(balance);
    }

    public Optional<GncNumeric> displayTotal() {
        return total.map(type::display);
    }

    /** Trading accounts are shown collapsed and never offered in pickers. */
    public boolean trading() {
        return type == AccountType.TRADING;
    }
}
