package com.example.dependencyscanner.model;

import java.util.List;

public record DependencyReport(Dependency dependency, List<VulnerabilityFinding> vulnerabilities) {
}
