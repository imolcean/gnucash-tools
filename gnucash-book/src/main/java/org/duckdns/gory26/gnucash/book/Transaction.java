package org.duckdns.gory26.gnucash.book;

import java.time.LocalDate;
import java.util.List;

/**
 * A GnuCash transaction: a dated description with N splits.
 *
 * @param notes the {@code notes} slot; empty when the transaction has none
 */
public record Transaction(LocalDate postDate,
                          String description,
                          String notes,
                          List<Split> splits) {

    private static final GncNumeric ZERO = new GncNumeric(0, 1);

    public Transaction {
        splits = List.copyOf(splits);
        GncNumeric sum = splits.stream().map(Split::value).reduce(ZERO, GncNumeric::add);
        if(sum.compareTo(ZERO) != 0) {
            throw new IllegalArgumentException("split values must sum to zero, but sum to " + sum);
        }
    }
}
