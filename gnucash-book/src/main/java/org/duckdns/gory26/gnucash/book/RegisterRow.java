package org.duckdns.gory26.gnucash.book;

import java.time.LocalDate;
import java.util.List;

/**
 * One split of the register's account, newest first. Amounts are stored values in the account's commodity;
 * flip them for display with {@link AccountType#display}.
 *
 * @param amount        this split's quantity
 * @param balance       running balance after this row
 * @param otherAccounts paths of the transaction's other accounts; more than one means a multi-split transaction
 * @param editable      whether the app may edit or delete it (ADR-0008); everything else is left to desktop
 */
public record RegisterRow(String txGuid,
                          String splitGuid,
                          LocalDate date,
                          String description,
                          String notes,
                          GncNumeric amount,
                          GncNumeric balance,
                          List<String> otherAccounts,
                          String reconcileState,
                          boolean editable) {

    public RegisterRow {
        otherAccounts = List.copyOf(otherAccounts);
    }
}
