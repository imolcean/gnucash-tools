package org.duckdns.gory26.gnucash.book;

import java.time.LocalDate;
import java.util.List;

/**
 * What the entry form submits: move {@code amount} from one account to another, both in the same commodity.
 */
public record TransferDraft(LocalDate date,
                            List<String> tags,
                            String text,
                            GncNumeric amount,
                            String fromAccountGuid,
                            String toAccountGuid,
                            String notes) {

    private static final GncNumeric ZERO = new GncNumeric(0, 1);

    public TransferDraft {
        if(amount.compareTo(ZERO) <= 0) {
            throw new IllegalArgumentException("amount must be positive: " + amount);
        }
        if(fromAccountGuid.equals(toAccountGuid)) {
            throw new IllegalArgumentException("from and to must be different accounts: " + fromAccountGuid);
        }
        tags = List.copyOf(tags);
        notes = notes == null ? "" : notes;
    }

    /**
     * The transfer as GnuCash stores it: {@code to} gets +amount, {@code from} gets −amount, value = quantity.
     */
    public Transaction toTransaction() {
        GncNumeric minus = amount.negate();
        return new Transaction(date, new TaggedDescription(tags, text).compose(), notes, List.of(
                new Split(toAccountGuid, amount, amount),
                new Split(fromAccountGuid, minus, minus)));
    }
}
