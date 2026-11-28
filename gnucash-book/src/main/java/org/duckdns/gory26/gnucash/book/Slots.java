package org.duckdns.gory26.gnucash.book;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

/** Checks for the string slots a caller attaches to a new transaction or split (ADR-0015). */
final class Slots {

    /** Lowercase GUIDs as desktop writes them. */
    static final Pattern GUID = Pattern.compile("[0-9a-f]{32}");

    /** Slots the book writes itself from the transaction's own fields. */
    private static final Set<String> RESERVED = Set.of("notes", "date-posted");

    private Slots() {
    }

    /**
     * An unmodifiable copy, sorted by name. A name with {@code /} is a path into nested frames, which desktop stores
     * as frame slots and this adapter does not write.
     */
    static Map<String, String> checked(Map<String, String> slots) {
        if(slots == null || slots.isEmpty()) {
            return Map.of();
        }
        Map<String, String> copy = new TreeMap<>();
        slots.forEach((name, value) -> {
            if(name == null || name.isBlank()) {
                throw new IllegalArgumentException("a slot name must not be blank");
            }
            if(name.contains("/")) {
                throw new IllegalArgumentException("slot names with '/' are frame paths, not supported: " + name);
            }
            if(RESERVED.contains(name)) {
                throw new IllegalArgumentException("slot '" + name + "' is written from the transaction's own field");
            }
            if(value == null) {
                throw new IllegalArgumentException("slot '" + name + "' has no value");
            }
            copy.put(name, value);
        });
        return Collections.unmodifiableMap(new LinkedHashMap<>(copy));
    }

    static String checkedGuid(String guid, String what) {
        if(guid == null || !GUID.matcher(guid).matches()) {
            throw new IllegalArgumentException(what + " must be 32 lowercase hex characters: " + guid);
        }
        return guid;
    }
}
