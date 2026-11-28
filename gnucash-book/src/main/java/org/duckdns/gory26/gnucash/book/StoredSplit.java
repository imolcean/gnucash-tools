package org.duckdns.gory26.gnucash.book;

import java.time.Instant;
import java.util.Map;

/**
 * A split as the book stores it, read back exactly (ADR-0003).
 *
 * @param value         in the transaction's currency, as stored ({@code value_num/value_denom})
 * @param quantity      in the account's commodity, as stored ({@code quantity_num/quantity_denom})
 * @param reconcileDate when it was reconciled; null when unset (desktop stores the epoch)
 * @param slots         the split's string slots, by name
 */
public record StoredSplit(String guid,
                          String accountGuid,
                          GncNumeric value,
                          GncNumeric quantity,
                          String memo,
                          ReconcileState reconcileState,
                          Instant reconcileDate,
                          Map<String, String> slots) {

    public StoredSplit {
        slots = Map.copyOf(slots);
    }
}
