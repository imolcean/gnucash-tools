package org.duckdns.gory26.gnucash.book;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A transaction of N splits for {@link GnuCashBook#insertTransaction}, under a GUID the caller chooses (ADR-0015).
 * Splits may be in other commodities than the currency and in TRADING accounts; when the book uses trading
 * accounts, the caller adds the trading splits that balance each commodity, as desktop would.
 *
 * @param guid         32 lowercase hex characters, not yet used by a transaction
 * @param currencyGuid the transaction's currency, a {@code CURRENCY} commodity
 * @param notes        the {@code notes} slot; empty for none
 * @param slots        string slots on the transaction, by name; {@code notes} and {@code date-posted} are written
 *                     from the fields, and names with {@code /} (nested frames) are not supported
 * @param splits       at least one; their values sum to zero
 */
public record NewTransaction(String guid,
                             String currencyGuid,
                             LocalDate datePosted,
                             String description,
                             String notes,
                             Map<String, String> slots,
                             List<NewSplit> splits) {

    private static final GncNumeric ZERO = new GncNumeric(0, 1);

    public NewTransaction {
        Slots.checkedGuid(guid, "the transaction GUID");
        Objects.requireNonNull(currencyGuid, "currencyGuid");
        Objects.requireNonNull(datePosted, "datePosted");
        description = description == null ? "" : description;
        notes = notes == null ? "" : notes;
        slots = Slots.checked(slots);
        splits = List.copyOf(splits);
        if(splits.isEmpty()) {
            throw new IllegalArgumentException("a transaction needs at least one split");
        }
        GncNumeric sum = splits.stream().map(NewSplit::value).reduce(ZERO, GncNumeric::add);
        if(sum.compareTo(ZERO) != 0) {
            throw new IllegalArgumentException("split values must sum to zero, but sum to " + sum);
        }
    }
}
