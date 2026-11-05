package org.duckdns.gory26.gnucash.book;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A transaction description split into its leading {@code [Tag]} prefix and the text after it (ADR-0009).
 */
public record TaggedDescription(List<String> tags, String text) {

    private static final Pattern LEADING_TAG = Pattern.compile("\\s*\\[([^\\[\\]]+)]");

    public TaggedDescription {
        for (String tag : tags) {
            if (tag.isBlank() || tag.contains("[") || tag.contains("]")) {
                throw new IllegalArgumentException("tag must be non-blank and free of brackets: '" + tag + "'");
            }
        }
        tags = List.copyOf(tags);
    }

    /** Writes the canonical form {@code [A] [B] text}, single-spaced. */
    public String compose() {
        StringBuilder out = new StringBuilder();
        for (String tag : tags) {
            out.append('[').append(tag).append("] ");
        }
        return out.append(text).toString().strip();
    }

    /** Reads tags from the leading bracket groups, tolerating any whitespace around them. */
    public static TaggedDescription parse(String description) {
        List<String> tags = new ArrayList<>();
        Matcher m = LEADING_TAG.matcher(description);
        int end = 0;
        while (m.find(end) && m.start() == end) {
            tags.add(m.group(1).strip());
            end = m.end();
        }
        return new TaggedDescription(tags, description.substring(end).strip());
    }
}
