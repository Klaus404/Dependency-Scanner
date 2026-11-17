package com.example.dependencyscanner.model;

public record Dependency(String ecosystem, String name, String version, boolean direct) {
    public Dependency withVersion(String resolvedVersion) {
        return new Dependency(ecosystem, name, resolvedVersion, direct);
    }
}
