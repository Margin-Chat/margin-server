package org.margin.server.architecture;

import org.junit.jupiter.api.Test;
import org.margin.server.MarginServerApplication;
import org.springframework.modulith.core.ApplicationModule;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class ModularityTest {

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
    void hasNoViolations() {
        assertThatCode(modules::verify).doesNotThrowAnyException();
    }

    @Test
    void hasNoCycles() {
        Map<String, Set<String>> graph = new HashMap<>();

        modules.forEach(module -> graph.put(module.getName(), module.getDirectDependencies(modules)
                .uniqueModules()
                .map(ApplicationModule::getName)
                .collect(Collectors.toSet())));

        assertThat(graph.keySet().stream().filter(module -> reaches(graph, module, module)).toList())
                .as("modules participating in a dependency cycle")
                .isEmpty();
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
}
