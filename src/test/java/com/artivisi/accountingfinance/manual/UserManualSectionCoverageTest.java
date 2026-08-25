package com.artivisi.accountingfinance.manual;

import com.artivisi.accountingfinance.manual.UserManualGenerator.Section;
import com.artivisi.accountingfinance.manual.UserManualGenerator.SectionGroup;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the user manual against silently losing content.
 *
 * <p>The generator maps a {@link Section} onto an {@code ## H2} heading by exact title. When
 * two or more sections reference the same markdown file and every one of them claims an H2,
 * there is no aggregate section, so an unregistered H2 is rendered nowhere — no error, no
 * warning, the chapter simply never reaches the published page. Forty-one sections had been
 * lost that way, including the whole PPh chapter of the tax manual.
 *
 * <p>This test fails the build instead, so adding an H2 without registering it is caught at
 * commit time rather than by a reader noticing a missing chapter.
 */
@DisplayName("User Manual - Section Coverage")
class UserManualSectionCoverageTest {

    private static final List<String> SEARCH_DIRS = List.of(
            "docs/user-manual", "docs/tutorials", "docs",
            "docs/admin-guide", "docs/implementor-guide", "docs/feature-reference",
            "docs/developer-guide", "docs/developer-guide/api", "docs/developer-guide/extending");

    @Test
    @DisplayName("Every H2 in a multi-section markdown file is registered or swept by an aggregate")
    void everyHeadingIsRendered() throws IOException {
        Map<String, List<String>> titlesByFile = new LinkedHashMap<>();
        for (Section section : UserManualGenerator.getSections()) {
            titlesByFile.computeIfAbsent(section.markdownFile(), key -> new ArrayList<>())
                    .add(section.title());
        }

        List<String> unrendered = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : titlesByFile.entrySet()) {
            List<String> titles = entry.getValue();
            // A file with a single section is returned whole, so nothing can be dropped
            if (titles.size() < 2) {
                continue;
            }

            List<String> headings = headingsOf(entry.getKey());
            assertThat(headings)
                    .as("markdown file not found for registered section: " + entry.getKey())
                    .isNotNull();

            Set<String> claimed = new HashSet<>();
            boolean hasAggregate = false;
            for (String title : titles) {
                String hit = headings.stream().filter(h -> matches(title, h)).findFirst().orElse(null);
                if (hit == null) {
                    hasAggregate = true;
                } else {
                    claimed.add(hit);
                }
            }
            if (hasAggregate) {
                continue;
            }
            for (String heading : headings) {
                if (!claimed.contains(heading)) {
                    unrendered.add(entry.getKey() + " -> ## " + heading);
                }
            }
        }

        assertThat(unrendered)
                .as("H2 sections that would never be rendered; register each in "
                        + "UserManualGenerator.getSectionGroups() with its exact heading text")
                .isEmpty();
    }

    @Test
    @DisplayName("No two sections claim the same H2 heading")
    void noHeadingIsClaimedTwice() throws IOException {
        Map<String, List<String>> titlesByFile = new LinkedHashMap<>();
        for (Section section : UserManualGenerator.getSections()) {
            titlesByFile.computeIfAbsent(section.markdownFile(), key -> new ArrayList<>())
                    .add(section.title());
        }

        List<String> collisions = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : titlesByFile.entrySet()) {
            if (entry.getValue().size() < 2) {
                continue;
            }
            List<String> headings = headingsOf(entry.getKey());
            if (headings == null) {
                continue;
            }
            Map<String, List<String>> claims = new HashMap<>();
            int aggregates = 0;
            for (String title : entry.getValue()) {
                String hit = headings.stream().filter(h -> matches(title, h)).findFirst().orElse(null);
                if (hit == null) {
                    aggregates++;
                } else {
                    claims.computeIfAbsent(hit, key -> new ArrayList<>()).add(title);
                }
            }
            claims.forEach((heading, owners) -> {
                if (owners.size() > 1) {
                    collisions.add(entry.getKey() + " -> ## " + heading + " claimed by " + owners);
                }
            });
            // Two aggregates on one file each render the intro plus every unclaimed H2
            if (aggregates > 1) {
                collisions.add(entry.getKey() + " has " + aggregates + " aggregate sections");
            }
        }

        assertThat(collisions).as("sections rendering the same content twice").isEmpty();
    }

    @Test
    @DisplayName("Section ids are unique across all groups")
    void sectionIdsAreUnique() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (SectionGroup group : UserManualGenerator.getSectionGroups()) {
            for (Section section : group.sections()) {
                counts.merge(section.id(), 1, Integer::sum);
            }
        }

        List<String> duplicates = counts.entrySet().stream()
                .filter(e -> e.getValue() > 1)
                .map(Map.Entry::getKey)
                .toList();

        // all.html concatenates every group, so a reused id is a duplicate anchor there
        assertThat(duplicates).as("duplicate section ids produce colliding anchors in all.html").isEmpty();
    }

    /** Mirrors UserManualGenerator.titlesMatch: case-insensitive exact match. */
    private boolean matches(String sectionTitle, String heading) {
        return sectionTitle.trim().equalsIgnoreCase(heading.trim());
    }

    private List<String> headingsOf(String markdownFile) throws IOException {
        for (String dir : SEARCH_DIRS) {
            Path path = Paths.get(dir).resolve(markdownFile);
            if (Files.exists(path)) {
                return Files.readAllLines(path, StandardCharsets.UTF_8).stream()
                        .filter(line -> line.startsWith("## "))
                        .map(line -> line.substring(3).trim())
                        .toList();
            }
        }
        return null;
    }
}
