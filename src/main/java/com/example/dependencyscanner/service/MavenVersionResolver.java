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
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class MavenVersionResolver {
    private final RestClient repository;

    public MavenVersionResolver(@Value("${scanner.maven-repository:https://repo.maven.apache.org/maven2}") String repositoryUrl) {
        this.repository = RestClient.builder().baseUrl(repositoryUrl).build();
    }

    public List<Dependency> resolve(List<Dependency> dependencies, byte[] pomContent) {
        if (dependencies.stream().noneMatch(d -> "Maven".equals(d.ecosystem()))) return dependencies;
        try {
            Element pom = parse(pomContent).getDocumentElement();
            Map<String, String> properties = properties(pom);
            Coordinates parent = coordinates(child(pom, "parent"), properties);
            return dependencies.stream().map(dependency -> {
                if (dependency.version() != null || !"Maven".equals(dependency.ecosystem())) return dependency;
                String[] parts = dependency.name().split(":", 2);
                String version = findManagedVersionInDocument(parts[0], parts[1], pom, properties, new HashSet<>());
                if (version == null) version = findManagedVersion(parts[0], parts[1], parent, properties, new HashSet<>());
                return version == null ? dependency : dependency.withVersion(version);
            }).toList();
        } catch (Exception ignored) {
            return dependencies;
        }
    }

    private String findManagedVersionInDocument(String group, String artifact, Element root,
                                                Map<String, String> properties, Set<String> visited) {
        Node management = child(root, "dependencyManagement");
        if (management == null) return null;
        NodeList managed = ((Element) management).getElementsByTagNameNS("*", "dependency");
        for (int i = 0; i < managed.getLength(); i++) {
            Node dependency = managed.item(i);
            String managedGroup = value(dependency, "groupId", properties);
            String managedArtifact = value(dependency, "artifactId", properties);
            String managedVersion = value(dependency, "version", properties);
            if (group.equals(managedGroup) && artifact.equals(managedArtifact) && managedVersion != null)
                return managedVersion;
            if ("pom".equals(value(dependency, "type", properties))
                    && "import".equals(value(dependency, "scope", properties))
                    && managedGroup != null && managedArtifact != null && managedVersion != null) {
                String imported = findManagedVersion(group, artifact,
                        new Coordinates(managedGroup, managedArtifact, managedVersion), properties, visited);
                if (imported != null) return imported;
            }
        }
        return null;
    }

    private String findManagedVersion(String group, String artifact, Coordinates source,
                                      Map<String, String> inheritedProperties, Set<String> visited) {
        if (source == null || !visited.add(source.key())) return null;
        try {
            byte[] content = repository.get().uri(pomPath(source)).retrieve().body(byte[].class);
            Element root = parse(content).getDocumentElement();
            Map<String, String> properties = new HashMap<>(inheritedProperties);
            properties.putAll(properties(root));
            Node management = child(root, "dependencyManagement");
            if (management != null) {
                NodeList managed = ((Element) management).getElementsByTagNameNS("*", "dependency");
                for (int i = 0; i < managed.getLength(); i++) {
                    Node dependency = managed.item(i);
                    String managedGroup = value(dependency, "groupId", properties);
                    String managedArtifact = value(dependency, "artifactId", properties);
                    String managedVersion = value(dependency, "version", properties);
                    if (group.equals(managedGroup) && artifact.equals(managedArtifact) && managedVersion != null)
                        return managedVersion;
                    if ("pom".equals(value(dependency, "type", properties))
                            && "import".equals(value(dependency, "scope", properties))
                            && managedVersion != null) {
                        String imported = findManagedVersion(group, artifact,
                                new Coordinates(managedGroup, managedArtifact, managedVersion), properties, visited);
                        if (imported != null) return imported;
                    }
                }
            }
            return findManagedVersion(group, artifact, coordinates(child(root, "parent"), properties), properties, visited);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String pomPath(Coordinates coordinates) {
        return "/" + coordinates.group().replace('.', '/') + "/" + coordinates.artifact() + "/"
                + coordinates.version() + "/" + coordinates.artifact() + "-" + coordinates.version() + ".pom";
    }

    private Coordinates coordinates(Node node, Map<String, String> properties) {
        if (node == null) return null;
        String group = value(node, "groupId", properties);
        String artifact = value(node, "artifactId", properties);
        String version = value(node, "version", properties);
        return group == null || artifact == null || version == null ? null : new Coordinates(group, artifact, version);
    }

    private Map<String, String> properties(Element root) {
        Map<String, String> result = new HashMap<>();
        Node properties = child(root, "properties");
        if (properties != null) {
            NodeList children = properties.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node child = children.item(i);
                if (child.getNodeType() == Node.ELEMENT_NODE) result.put(child.getLocalName(), child.getTextContent().trim());
            }
        }
        return result;
    }

    private String value(Node node, String name, Map<String, String> properties) {
        Node child = child(node, name);
        if (child == null) return null;
        String value = child.getTextContent().trim();
        for (int i = 0; i < 5 && value.contains("${"); i++) {
            int start = value.indexOf("${"), end = value.indexOf('}', start);
            if (end < 0) break;
            String key = value.substring(start + 2, end);
            String replacement = properties.get(key);
            if (replacement == null && "project.version".equals(key)) replacement = properties.get("version");
            if (replacement == null) break;
            value = value.substring(0, start) + replacement + value.substring(end + 1);
        }
        return value;
    }

    private Node child(Node node, String name) {
        if (node == null) return null;
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (name.equals(child.getLocalName()) || name.equals(child.getNodeName())) return child;
        }
        return null;
    }

    private org.w3c.dom.Document parse(byte[] content) throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(content));
    }

    private record Coordinates(String group, String artifact, String version) {
        String key() { return group + ":" + artifact + ":" + version; }
    }
}
