package com.example.dependencyscanner.parser;

import com.example.dependencyscanner.model.Dependency;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ManifestParsersTest {
    private final ManifestParsers parsers = new ManifestParsers(new ObjectMapper());

    @Test
    void parsesMavenDependenciesWithoutResolvingBuildFiles() throws Exception {
        String pom = """
                <project><dependencies><dependency>
                <groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-web</artifactId><version>3.4.5</version>
                </dependency></dependencies></project>
                """;

        List<Dependency> dependencies = parsers.parsers().stream()
                .filter(parser -> parser.supports("pom.xml")).findFirst().orElseThrow()
                .parse(pom.getBytes(StandardCharsets.UTF_8));

        assertThat(dependencies).containsExactly(new Dependency(
                "Maven", "org.springframework.boot:spring-boot-starter-web", "3.4.5", true));
    }

    @Test
    void parsesMultipleEcosystems() throws Exception {
        String packageJson = """
                {"dependencies":{"express":"^4.19.0"},"devDependencies":{"vitest":"~1.0.0"}}
                """;

        List<Dependency> dependencies = parsers.parsers().stream()
                .filter(parser -> parser.supports("package.json")).findFirst().orElseThrow()
                .parse(packageJson.getBytes(StandardCharsets.UTF_8));

        assertThat(dependencies).extracting(Dependency::name)
                .containsExactly("express", "vitest");
        assertThat(dependencies).extracting(Dependency::version)
                .containsExactly("4.19.0", "1.0.0");
    }
}
