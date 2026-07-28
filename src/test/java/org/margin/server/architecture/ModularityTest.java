package org.margin.server.architecture;

import org.junit.jupiter.api.Test;
import org.margin.server.MarginServerApplication;
import org.springframework.modulith.core.ApplicationModule;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.core.Violations;
import org.springframework.modulith.docs.Documenter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

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
        return Stream.concat(dependencyViolations(), cyclicModules())
                .distinct()
                .sorted()
                .toList();
    }

    private Stream<String> dependencyViolations() {
        try {
            modules.verify();
            return Stream.of();
        } catch (Violations violations) {
            return violations.getMessages().stream()
                    .filter(message -> !message.startsWith("Cycle detected:"))
                    .map(ModularityTest::normalize)
                    .toList()
                    .stream();
        }
    }

    private Stream<String> cyclicModules() {
        Map<String, Set<String>> graph = new HashMap<>();

        modules.forEach(module -> graph.put(module.getName(), module.getDirectDependencies(modules)
                .uniqueModules()
                .map(ApplicationModule::getName)
                .collect(Collectors.toSet())));

        return graph.keySet().stream()
                .filter(module -> reaches(graph, module, module))
                .map("Module '%s' is part of a dependency cycle."::formatted);
    }

    private static boolean reaches(Map<String, Set<String>> graph, String from, String target) {
        Deque<String> queue = new ArrayDeque<>(graph.getOrDefault(from, Set.of()));
        Set<String> seen = new HashSet<>();

        while (!queue.isEmpty()) {
            String current = queue.pop();

            if (current.equals(target)) {
                return true;
            }
            if (seen.add(current)) {
                queue.addAll(graph.getOrDefault(current, Set.of()));
            }
        }

        return false;
    }

    private static String normalize(String message) {
        String collapsed = message.replaceAll("\\s+", " ").trim();
        int endOfClaim = collapsed.indexOf("! ");

        return endOfClaim < 0 ? collapsed : collapsed.substring(0, endOfClaim + 1);
    }
}
