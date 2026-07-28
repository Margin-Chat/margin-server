package org.margin.server.architecture;

import org.junit.jupiter.api.Test;
import org.margin.server.MarginServerApplication;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.core.Violations;
import org.springframework.modulith.docs.Documenter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Ratchet for the ongoing modulith migration.
 * <p>
 * {@link ApplicationModules#verify()} rejects cycles unconditionally, so an
 * {@code allowedDependencies} allowlist cannot express the current state of the codebase.
 * Instead the known violations are checked in as a baseline: the build fails on anything
 * new, and fails with an explicit instruction when the baseline has grown stale. Every
 * migration phase deletes lines from the baseline until it is empty and this test can be
 * replaced by a plain {@code verify()}.
 * <p>
 * Regenerate after a phase with {@code ./mvnw test -Dtest=ModularityTest -Dmodulith.baseline.regenerate=true}.
 */
class ModularityTest {

    private static final Path BASELINE = Path.of("src/test/resources/modulith-violations-baseline.txt");
    private static final Path REPORT = Path.of("target/modulith-report.txt");

    private final ApplicationModules modules = ApplicationModules.of(MarginServerApplication.class);

    @Test
    void writesModuleReport() throws IOException {
        Files.createDirectories(REPORT.getParent());
        Files.writeString(REPORT, modules.toString());

        assertThat(modules).isNotEmpty();
    }

    @Test
    void writesDocumentation() {
        new Documenter(modules).writeDocumentation();
    }

    @Test
    void hasNoViolationsBeyondTheBaseline() throws IOException {
        List<String> actual = currentViolations();

        if (Boolean.getBoolean("modulith.baseline.regenerate")) {
            Files.createDirectories(BASELINE.getParent());
            Files.write(BASELINE, actual);
            fail("Baseline regenerated with %d violation(s). Re-run without the flag.", actual.size());
        }

        List<String> baseline = Files.exists(BASELINE) ? Files.readAllLines(BASELINE) : List.of();

        List<String> added = new ArrayList<>(actual);
        added.removeAll(baseline);

        List<String> resolved = new ArrayList<>(baseline);
        resolved.removeAll(actual);

        assertThat(added)
                .as("New module violations were introduced. Fix them, or accept them by regenerating the baseline.")
                .isEmpty();

        assertThat(resolved)
                .as("%d baseline violation(s) are now fixed. Regenerate the baseline so the ratchet holds.",
                        resolved.size())
                .isEmpty();
    }

    private List<String> currentViolations() {
        try {
            modules.verify();
            return List.of();
        } catch (Violations violations) {
            return violations.getMessages().stream()
                    .flatMap(ModularityTest::normalize)
                    .distinct()
                    .sorted()
                    .toList();
        }
    }

    private static final Pattern SLICE = Pattern.compile("Slice (\\w+)");

    /**
     * Reduces a violation to a stable, comparable claim.
     * <p>
     * For dependency violations that means dropping the reference sites after the "!" — Modulith
     * reports one violation per field, parameter, return type and call site, so keeping them would
     * make the baseline thousands of lines long and churn on any edit inside an offending file.
     * <p>
     * Cycles cannot be baselined individually at all. The module graph is tangled enough to contain
     * combinatorially many distinct cycles, and ArchUnit stops after the first 100 it happens to
     * walk — a different, equally valid subset on every run. What is stable is <em>which modules
     * are caught in a cycle</em>, so each cycle contributes one entry per participant. A module
     * drops out of the baseline once it is no longer part of any tangle, which is exactly the
     * signal each migration phase is trying to produce.
     */
    private static Stream<String> normalize(String message) {
        String collapsed = message.replaceAll("\\s+", " ").trim();

        if (collapsed.startsWith("Cycle detected:")) {
            String header = collapsed.split(" 1\\. Dependencies", 2)[0];
            Matcher matcher = SLICE.matcher(header);
            SortedSet<String> participants = new TreeSet<>();

            while (matcher.find()) {
                participants.add(matcher.group(1));
            }

            return participants.stream().map("Module '%s' is part of a dependency cycle."::formatted);
        }

        int endOfClaim = collapsed.indexOf("! ");

        return Stream.of(endOfClaim < 0 ? collapsed : collapsed.substring(0, endOfClaim + 1));
    }
}
