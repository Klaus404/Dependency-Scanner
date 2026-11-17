package com.example.dependencyscanner.service;

import com.example.dependencyscanner.model.Dependency;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class MavenDependencyGraphResolver {
    private final RestClient repository;
    private final MavenVersionResolver versions;
    private final int maxDependencies;

    public MavenDependencyGraphResolver(
            @Value("${scanner.maven-repository:https://repo.maven.apache.org/maven2}") String repositoryUrl,
            MavenVersionResolver versions,
            @Value("${scanner.max-dependencies:500}") int maxDependencies) {
        this.repository = RestClient.builder().baseUrl(repositoryUrl).build();
        this.versions = versions;
        this.maxDependencies = maxDependencies;
    }

    public List<Dependency> resolve(List<Dependency> roots) {
        if (roots.stream().noneMatch(d -> "Maven".equals(d.ecosystem()))) return roots;
        ArrayDeque<Dependency> queue = new ArrayDeque<>(roots);
        List<Dependency> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        while (!queue.isEmpty() && result.size() < maxDependencies) {
            Dependency dependency = queue.removeFirst();
            String key = dependency.ecosystem() + ":" + dependency.name() + ":" + dependency.version();
            if (!seen.add(key)) continue;
            result.add(dependency);
            if (!"Maven".equals(dependency.ecosystem()) || dependency.version() == null) continue;
            try {
                byte[] pom = repository.get().uri(pomPath(dependency)).retrieve().body(byte[].class);
                List<Dependency> children = versions.resolve(parseDependencies(pom), pom);
                queue.addAll(children);
            } catch (Exception ignored) {
                // A missing or malformed artifact POM should not discard the dependency itself.
            }
        }
        return result;
    }

    private List<Dependency> parseDependencies(byte[] content) throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setNamespaceAware(true);
        Element root = factory.newDocumentBuilder().parse(new ByteArrayInputStream(content)).getDocumentElement();
        Node dependencies = child(root, "dependencies");
        if (dependencies == null) return List.of();
        NodeList nodes = ((Element) dependencies).getElementsByTagNameNS("*", "dependency");
        List<Dependency> result = new ArrayList<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            String group = value(node, "groupId");
            String artifact = value(node, "artifactId");
            String version = value(node, "version");
            String scope = value(node, "scope");
            String optional = value(node, "optional");
            if (group != null && artifact != null && !"test".equals(scope) && !"provided".equals(scope)
                    && !"system".equals(scope) && !"true".equalsIgnoreCase(optional)) {
                result.add(new Dependency("Maven", group + ":" + artifact,
                        version != null && !version.startsWith("${") ? version : null, false));
            }
        }
        return result;
    }

    private String pomPath(Dependency dependency) {
        String[] parts = dependency.name().split(":", 2);
        return "/" + parts[0].replace('.', '/') + "/" + parts[1] + "/" + dependency.version()
                + "/" + parts[1] + "-" + dependency.version() + ".pom";
    }

    private String value(Node node, String name) {
        Node child = child(node, name);
        return child == null ? null : child.getTextContent().trim();
    }

    private Node child(Node node, String name) {
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (name.equals(child.getLocalName()) || name.equals(child.getNodeName())) return child;
        }
        return null;
    }
}
