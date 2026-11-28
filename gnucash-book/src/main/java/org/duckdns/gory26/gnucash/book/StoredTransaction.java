package org.duckdns.gory26.gnucash.book;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * A transaction with all its splits as the book stores it, whoever wrote it.
 *
 * @param datePosted its {@code date-posted} slot, or the UTC date of {@code post_date} when it has none
 * @param entered    {@code enter_date}
 * @param notes      the {@code notes} slot; empty when there is none
 * @param slots      the transaction's string slots except {@code notes}, by name
 * @param splits     ordered by GUID
 */
public record StoredTransaction(String guid,
                                String currencyGuid,
                                LocalDate datePosted,
                                Instant entered,
                                String description,
                                String notes,
                                Map<String, String> slots,
                                List<StoredSplit> splits) {

    public StoredTransaction {
        slots = Map.copyOf(slots);
        splits = List.copyOf(splits);
    }
}
