package org.duckdns.gory26.gnucash.book;

import java.util.List;

/** What the entry form pre-fills after a description is picked: taken from the latest two-split match. */
public record QuickFill(List<String> tags,
                        String text,
                        GncNumeric amount,
                        String fromAccountGuid,
                        String toAccountGuid) {

    public QuickFill {
        tags = List.copyOf(tags);
    }
}
