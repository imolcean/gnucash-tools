package org.duckdns.gory26.gnucash.book;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * One split of a {@link NewTransaction}. Its GUID is made by the book.
 *
 * @param value         signed, in the transaction's currency (positive = debit); must fit the currency's fraction
 * @param quantity      signed, in the account's commodity; must fit the account's SCU, and equal {@code value} when
 *                      the account holds the transaction's currency
 * @param memo          {@code splits.memo}; empty for none
 * @param reconcileDate when the split was reconciled: required for {@link ReconcileState#RECONCILED}, null otherwise
 *                      (desktop stores the epoch then)
 * @param slots         string slots on the split, by name; see {@link NewTransaction#slots()}
 */
public record NewSplit(String accountGuid,
                       GncNumeric value,
                       GncNumeric quantity,
                       String memo,
                       ReconcileState reconcileState,
                       Instant reconcileDate,
                       Map<String, String> slots) {

    public NewSplit {
        Objects.requireNonNull(accountGuid, "accountGuid");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(quantity, "quantity");
        memo = memo == null ? "" : memo;
        reconcileState = reconcileState == null ? ReconcileState.NOT_RECONCILED : reconcileState;
        switch(reconcileState) {
            case NOT_RECONCILED, CLEARED -> {
                if(reconcileDate != null) {
                    throw new IllegalArgumentException("only a reconciled split has a reconcile date");
                }
            }
            case RECONCILED -> Objects.requireNonNull(reconcileDate, "a reconciled split needs a reconcile date");
            case FROZEN, VOIDED -> throw new IllegalArgumentException("cannot write a " + reconcileState + " split");
        }
        slots = Slots.checked(slots);
    }

    /** Not reconciled, no memo, no slots. */
    public NewSplit(String accountGuid, GncNumeric value, GncNumeric quantity) {
        this(accountGuid, value, quantity, "", ReconcileState.NOT_RECONCILED, null, Map.of());
    }
}
