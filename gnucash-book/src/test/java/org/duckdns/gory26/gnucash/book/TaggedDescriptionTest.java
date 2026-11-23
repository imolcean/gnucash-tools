package org.duckdns.gory26.gnucash.book;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("docker")
class TaggedDescriptionTest {

    @Test
    void parsesTagsFromTheSampleBookWithItsDoubleSpace() {
        TaggedDescription d = TaggedDescription.parse("[Tag 1] [Tag 2]  Rewe");

        assertThat(d.tags()).containsExactly("Tag 1", "Tag 2");
        assertThat(d.text()).isEqualTo("Rewe");
    }

    @Test
    void parsesAnySpacingBetweenAndAroundTags() {
        assertThat(TaggedDescription.parse("[Tag 1] Rewe again"))
                .isEqualTo(new TaggedDescription(List.of("Tag 1"), "Rewe again"));
        assertThat(TaggedDescription.parse("[A][B]Rewe"))
                .isEqualTo(new TaggedDescription(List.of("A", "B"), "Rewe"));
        assertThat(TaggedDescription.parse("  [A] \t [B]   Rewe  "))
                .isEqualTo(new TaggedDescription(List.of("A", "B"), "Rewe"));
    }

    @Test
    void descriptionWithoutTagsIsAllText() {
        assertThat(TaggedDescription.parse("Storage room"))
                .isEqualTo(new TaggedDescription(List.of(), "Storage room"));
    }

    @Test
    void onlyLeadingBracketsAreTags() {
        assertThat(TaggedDescription.parse("Concert tickets [maybe]"))
                .isEqualTo(new TaggedDescription(List.of(), "Concert tickets [maybe]"));
        assertThat(TaggedDescription.parse("[unclosed tag"))
                .isEqualTo(new TaggedDescription(List.of(), "[unclosed tag"));
    }

    @Test
    void composesTheCanonicalForm() {
        assertThat(new TaggedDescription(List.of("Tag 1", "Tag 2"), "Rewe").compose()).isEqualTo("[Tag 1] [Tag 2] Rewe");
        assertThat(new TaggedDescription(List.of(), "Storage room").compose()).isEqualTo("Storage room");
        assertThat(new TaggedDescription(List.of("Tag 1"), "").compose()).isEqualTo("[Tag 1]");
    }

    @Test
    void editingASampleDescriptionNormalisesItsSpacing() {
        assertThat(TaggedDescription.parse("[Tag 1] [Tag 2]  Rewe").compose()).isEqualTo("[Tag 1] [Tag 2] Rewe");
    }

    @Test
    void refusesTagsThatWouldNotReadBackTheSame() {
        assertThatThrownBy(() -> new TaggedDescription(List.of("a]b"), "x")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TaggedDescription(List.of("[a"), "x")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TaggedDescription(List.of("  "), "x")).isInstanceOf(IllegalArgumentException.class);
    }
}
