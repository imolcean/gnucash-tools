package org.duckdns.gory26.gnucash.book;

import java.time.LocalDateTime;

/**
 * Keyset position in a register: the sort key {@code (post_date, enter_date, split guid)} of the last row shown.
 */
public record RegisterCursor(LocalDateTime postDate, LocalDateTime enterDate, String splitGuid) {

    /** An opaque string for a "Load more" link. */
    public String toToken() {
        return postDate + "_" + enterDate + "_" + splitGuid;
    }

    /**
     * @throws IllegalArgumentException if the token was not made by {@link #toToken()}
     */
    public static RegisterCursor fromToken(String token) {
        String[] parts = token.split("_", -1);
        if (parts.length != 3 || !parts[2].matches("[0-9a-f]{32}")) {
            throw new IllegalArgumentException("not a register cursor: " + token);
        }
        try {
            return new RegisterCursor(LocalDateTime.parse(parts[0]), LocalDateTime.parse(parts[1]), parts[2]);
        } catch (java.time.format.DateTimeParseException e) {
            throw new IllegalArgumentException("not a register cursor: " + token, e);
        }
    }
}
