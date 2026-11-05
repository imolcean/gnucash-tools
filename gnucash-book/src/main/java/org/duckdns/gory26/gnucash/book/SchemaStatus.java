package org.duckdns.gory26.gnucash.book;

import java.util.List;

/**
 * How the book's {@code versions} rows compare with the GnuCash 5.16 set this adapter writes for (ADR-0005).
 *
 * @param differences one line per mismatching table; empty when the schema is known
 */
public record SchemaStatus(List<String> differences) {

    public SchemaStatus {
        differences = List.copyOf(differences);
    }

    /** True when writes are allowed; false means the app runs read-only. */
    public boolean known() {
        return differences.isEmpty();
    }
}
