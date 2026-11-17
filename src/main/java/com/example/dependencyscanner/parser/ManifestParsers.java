package com.example.dependencyscanner.parser;

import com.example.dependencyscanner.model.Dependency;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ManifestParsers {
    private static final Pattern GRADLE = Pattern.compile("(?:implementation|api|compileOnly|runtimeOnly|testImplementation|testRuntimeOnly|annotationProcessor)\\s*[( ]\\s*['\"]([^:'\"]+):([^:'\"]+):([^'\"]+)");
    private static final Pattern REQUIREMENT = Pattern.compile("^([A-Za-z0-9_.-]+)\\s*(?:==|===|~=|>=|<=|>|<)\\s*([A-Za-z0-9!+._-]+)");
    private final ObjectMapper objectMapper;

    public ManifestParsers(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<DependencyParser> parsers() {
        return List.of(new PomParser(), new GradleParser(), new NpmParser(), new RequirementsParser());
    }

    private static String text(Node node, String name) {
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (name.equals(child.getLocalName()) || name.equals(child.getNodeName())) return child.getTextContent().trim();
        }
        return null;
    }

    private class PomParser implements DependencyParser {
        public boolean supports(String filename) { return filename.toLowerCase().endsWith("pom.xml"); }
        public List<Dependency> parse(byte[] content) throws IOException {
            try {
                var factory = DocumentBuilderFactory.newInstance();
                factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
                factory.setNamespaceAware(true);
                var document = factory.newDocumentBuilder().parse(new ByteArrayInputStream(content));
                var nodes = document.getElementsByTagNameNS("*", "dependency");
                List<Dependency> result = new ArrayList<>();
                for (int i = 0; i < nodes.getLength(); i++) {
                    Node node = nodes.item(i);
                    String group = text(node, "groupId"), artifact = text(node, "artifactId"), version = text(node, "version");
                    if (group != null && artifact != null)
                        result.add(new Dependency("Maven", group + ":" + artifact,
                                version != null && !version.startsWith("${") ? version : null, true));
                }
                return result;
            } catch (Exception e) { throw new IOException("Invalid pom.xml", e); }
        }
    }

    private class GradleParser implements DependencyParser {
        public boolean supports(String filename) { return filename.endsWith(".gradle") || filename.endsWith(".gradle.kts"); }
        public List<Dependency> parse(byte[] content) {
            List<Dependency> result = new ArrayList<>();
            Matcher matcher = GRADLE.matcher(new String(content, StandardCharsets.UTF_8));
            while (matcher.find()) result.add(new Dependency("Maven", matcher.group(1) + ":" + matcher.group(2), matcher.group(3), true));
            return result;
        }
    }

    private class NpmParser implements DependencyParser {
        public boolean supports(String filename) { return filename.endsWith("package.json"); }
        public List<Dependency> parse(byte[] content) throws IOException {
            JsonNode root = objectMapper.readTree(content);
            List<Dependency> result = new ArrayList<>();
            for (String section : List.of("dependencies", "devDependencies", "peerDependencies", "optionalDependencies")) {
                JsonNode dependencies = root.path(section);
                dependencies.fields().forEachRemaining(entry -> result.add(new Dependency("npm", entry.getKey(), normalizeNpm(entry.getValue().asText()), true)));
            }
            return result;
        }
        private String normalizeNpm(String value) { return value.replaceFirst("^[~^<>= ]+", ""); }
    }

    private static class RequirementsParser implements DependencyParser {
        public boolean supports(String filename) { return filename.endsWith("requirements.txt") || filename.endsWith("requirements-dev.txt"); }
        public List<Dependency> parse(byte[] content) {
            List<Dependency> result = new ArrayList<>();
            for (String line : new String(content, StandardCharsets.UTF_8).lines().toList()) {
                Matcher matcher = REQUIREMENT.matcher(line.strip());
                if (matcher.find()) result.add(new Dependency("PyPI", matcher.group(1), matcher.group(2), true));
            }
            return result;
        }
    }
}
