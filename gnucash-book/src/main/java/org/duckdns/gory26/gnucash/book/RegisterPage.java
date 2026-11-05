package org.duckdns.gory26.gnucash.book;

import java.util.List;
import java.util.Optional;

/**
 * @param next where the following page starts; empty on the last page
 */
public record RegisterPage(List<RegisterRow> rows, Optional<RegisterCursor> next) {

    public RegisterPage {
        rows = List.copyOf(rows);
    }
}
